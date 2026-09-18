package app.rayik.music.streaming.piped

import android.util.Log
import androidx.media3.common.MimeTypes
import app.rayik.music.preferences.AppearancePreferences
import app.rayik.music.streaming.AudioCandidate
import app.rayik.music.streaming.ResolvedStream
import app.rayik.music.streaming.StreamSelection
import app.rayik.music.streaming.StreamSource
import app.rayik.music.streaming.Track
import app.rayik.music.streaming.audioMimeType
import app.rayik.music.streaming.youtube.InnerTubeResolver
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Keyless on-device YouTube source over the Piped protocol: plain HTTPS
 * search + stream JSON from public instances, no API keys, no backend.
 * Instances come and go, so every call walks [INSTANCES] in order and the
 * first success wins. Returned URLs stay transient — expiry is honored by
 * [ResolvedStream] and playback only ever pins through [DownloadQuotas].
 */
class PipedSource(
  private val okHttp: OkHttpClient,
  private val preferences: AppearancePreferences,
  private val direct: InnerTubeResolver,
) : StreamSource {
  private val json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
  }

  override suspend fun search(query: String): Result<List<Track>> {
    if (query.isBlank()) return Result.success(emptyList())
    // Primary: direct on-device InnerTube search (keyless, fast, no proxy bot blocks).
    val directResult = direct.search(query)
    if (directResult.isSuccess && !directResult.getOrNull().isNullOrEmpty()) {
      return directResult
    }
    // Fallback: public Piped instances
    val encoded = java.net.URLEncoder.encode(query.trim(), "UTF-8")
    val errors = mutableListOf<String>()
    directResult.exceptionOrNull()?.message?.let { errors += "direct: $it" }
    for (base in orderedInstances()) {
      try {
        val body = get("$base/search?q=$encoded&filter=videos")
        val response = json.decodeFromString(PipedSearchResponse.serializer(), body)
        lastGoodInstance = base
        return Result.success(response.items.mapNotNull { it.toTrack() })
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        errors += "$base: ${e.message}"
      }
    }
    return Result.failure(IOException("Search unreachable (${errors.take(2).joinToString("; ")})"))
  }

  override suspend fun resolve(track: Track, excludedUrls: Set<String>): Result<ResolvedStream> {
    // Primary: on-device InnerTube resolution. Public Piped instances
    // resolve server-side from datacenter IPs that YouTube routinely
    // bot-blocks while search keeps working, so a request from the
    // listener's own device succeeds where the proxy fails.
    val directResult = direct.resolve(track.id, preferences.streamQuality.get(), excludedUrls)
    if (directResult.isSuccess) return directResult
    return resolveViaPiped(track, directResult.exceptionOrNull()?.message, excludedUrls)
  }

  private suspend fun resolveViaPiped(
    track: Track,
    directError: String?,
    excludedUrls: Set<String>,
  ): Result<ResolvedStream> {
    val errors = mutableListOf<String>()
    if (directError != null) errors += "direct: $directError"
    for (base in orderedInstances()) {
      try {
        val body = get("$base/streams/${track.id}", STREAMS_CALL_TIMEOUT_MS)
        val response = json.decodeFromString(PipedStreamsResponse.serializer(), body)
        // Same verify-before-handover as direct: Piped hands out the same
        // googlevideo edge URLs, so probe each candidate from this device
        // and fail over instead of queuing a link that 403s on first fetch.
        val excluded = excludedUrls.toMutableSet()
        var lastStatus = -1
        var verifiedDead = false
        // A token minted during the direct pass (same video) applies here
        // too — Piped hands out the same GVS edge URLs, and `pot` travels
        // on googlevideo hosts only, never on proxied/manifest URLs.
        val pot = direct.poTokenFor(track.id)
        for (attempt in 0 until InnerTubeResolver.MAX_VERIFY_ATTEMPTS) {
          val stream = pickBest(response, excluded)
          if (stream == null) {
            errors += "$base: ${pipedEmptyReason(body)}"
            break
          }
          val url = InnerTubeResolver.withPot(stream.url, pot)
          val status = withContext(Dispatchers.IO) { direct.verifyUrl(url) }
          if (status in 200..299) {
            lastGoodInstance = base
            val now = System.currentTimeMillis()
            Log.i(TAG, "resolve ${track.id} via piped $base (${stream.bitrate}bps ${stream.codec})")
            return Result.success(
              ResolvedStream(
                url = url,
                expiresAtEpochMs = ResolvedStream.expiryFromUrl(url, now),
                bitrate = stream.bitrate,
                codec = stream.codec,
                // Manifest candidates already carry their MIME; muxed video
                // keeps its container MIME (ExoPlayer drops the video
                // track); progressive audio resolves through the
                // codec/container lookup so ExoPlayer skips type-sniffing
                // on extension-less URLs.
                mimeType = stream.mimeType.takeIf { it.startsWith("application/") || it.startsWith("video/") }
                  ?: audioMimeType(stream.codec, stream.mimeType).orEmpty(),
              ),
            )
          }
          lastStatus = status
          verifiedDead = true
          excluded += stream.url
          excluded += url
          Log.w(TAG, "resolve ${track.id} via piped $base candidate HTTP $status, failing over")
        }
        if (verifiedDead) errors += "$base: YouTube refused playback (HTTP $lastStatus)"
        continue
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        errors += "$base: ${e.message}"
      }
    }
    return Result.failure(IOException("No playable stream (${errors.take(2).joinToString("; ")})"))
  }

  /**
   * Piped returns failure payloads like `{"error": "...SignInConfirmNotBotException..."}`
   * with HTTP 200, which decode into an empty [PipedStreamsResponse]. Surface
   * the real reason (trimmed) instead of a blank "no streams".
   */
  private fun pipedEmptyReason(body: String): String {
    val envelope = runCatching {
      json.decodeFromString(PipedErrorEnvelope.serializer(), body)
    }.getOrNull()
    val detail = (envelope?.error ?: envelope?.message)?.trim()?.take(MAX_ERROR_CHARS)
    if (detail.isNullOrBlank()) return "no audio in response"
    return if (detail.contains("SignInConfirmNotBot") || detail.contains("not a bot")) {
      "YouTube bot-blocked this instance"
    } else {
      detail
    }
  }

  /** Last working instance first — repeat taps skip the dead-instance walk. */
  private fun orderedInstances(): List<String> {
    val last = lastGoodInstance
    return if (last == null) INSTANCES else listOf(last) + INSTANCES.filter { it != last }
  }

  /** Fallback depth: direct audio, then muxed-with-audio, then HLS, then DASH. */
  internal fun pickBest(response: PipedStreamsResponse, excludedUrls: Set<String> = emptySet()): AudioCandidate? {
    val quality = preferences.streamQuality.get()
    val candidates = response.audioStreams.map {
      AudioCandidate(url = it.url, bitrate = it.bitrate, codec = it.codec, mimeType = it.format)
    }.filter { it.url !in excludedUrls }
    StreamSelection.select(candidates, quality)?.let { return it }
    // Muxed itag-18 class: video container with an audio track, cheapest
    // first. PO-token exempt; ExoPlayer drops the video track.
    response.videoStreams
      .filter { it.url.isNotBlank() && it.url !in excludedUrls && (it.itag in MUXED_AUDIO_ITAGS) }
      .minByOrNull { pipedMuxedBitrate(it) }
      ?.let { return AudioCandidate(url = it.url, codec = it.codec, mimeType = "video/mp4") }
    // Explicit manifest MIME: these URLs are extension-less, so without
    // this ExoPlayer sniffs them as progressive and buffers forever.
    response.hls?.takeIf { it.isNotBlank() && it !in excludedUrls }?.let {
      return AudioCandidate(url = it, mimeType = MimeTypes.APPLICATION_M3U8)
    }
    response.dash?.takeIf { it.isNotBlank() && it !in excludedUrls }?.let {
      return AudioCandidate(url = it, mimeType = MimeTypes.APPLICATION_MPD)
    }
    return null
  }

  private suspend fun get(url: String, timeoutMs: Long = CALL_TIMEOUT_MS): String =
    withTimeout(timeoutMs) {
      suspendCancellableCoroutine { cont ->
        val call = okHttp.newCall(
          Request.Builder()
            .url(url)
            .header("User-Agent", "rayik-preview")
            .build(),
        )
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(
          object : Callback {
            override fun onFailure(call: Call, e: IOException) {
              if (!cont.isCompleted) cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
              response.use {
                if (!cont.isCompleted) {
                  if (!it.isSuccessful) {
                    cont.resumeWithException(IOException("HTTP ${it.code}"))
                  } else {
                    cont.resume(it.body?.string().orEmpty())
                  }
                }
              }
            }
          },
        )
      }
    }

  companion object {
    /** Shared with the player so resolve + playback logs read as one story. */
    const val TAG = "RayikPlayer"
    const val CALL_TIMEOUT_MS = 12_000L
    /**
     * Tighter budget for the `/streams` fallback walk: resolve() runs it
     * only after direct on-device resolution fails, and each hanging
     * instance otherwise holds the tap-to-play spinner, so fail fast and
     * let the UI report Unavailable + retry.
     */
    const val STREAMS_CALL_TIMEOUT_MS = 8_000L
    const val MAX_ERROR_CHARS = 140

    @Volatile
    private var lastGoodInstance: String? = null

    /**
     * Public instances drift; order is preference, not promise. Verified
     * 2026-09-17: only the first two still serve `/search` — the rest fail
     * fast (HTTP 301/502/525) and exist as fallback depth. Hanging hosts
     * are deliberately excluded so a tap never stalls on a dead server.
     * Direct on-device resolution ([InnerTubeResolver]) is the primary
     * resolve path, so this list matters mostly for search.
     */
    val INSTANCES = listOf(
      "https://api.piped.private.coffee",
      "https://pipedapi.ducks.party",
      "https://pipedapi.adminforge.de",
      "https://pipedapi.kavin.rocks",
      "https://pipedapi.reallyaweso.me",
      "https://pipedapi.leptons.xyz",
    )

    private val VIDEO_ID = Regex("[?&]v=([\\w-]{11})")

    /** Muxed itags known to carry an audio track (mirrors the direct resolver). */
    private val MUXED_AUDIO_ITAGS = setOf(18, 22, 17, 36)

    /** Cheapest muxed rendition first (144p 3GP < 360p < 720p). */
    private val MUXED_ITAG_RANK = mapOf(17 to 0, 18 to 1, 36 to 2, 22 to 3)

    private fun pipedMuxedBitrate(stream: PipedVideoStream): Int =
      MUXED_ITAG_RANK[stream.itag] ?: Int.MAX_VALUE

    fun PipedSearchItem.toTrack(): Track? {
      if (type.isNotBlank() && type != "stream") return null
      val id = VIDEO_ID.find(url)?.groupValues?.getOrNull(1) ?: return null
      if (title.isBlank()) return null
      return Track(
        id = id,
        title = title,
        artist = uploaderName.takeIf { it.isNotBlank() } ?: "Unknown artist",
        durationMs = ((duration ?: -1.0).takeIf { it > 0 } ?: 0.0).times(1_000).toLong(),
        artworkUrl = thumbnail,
      )
    }
  }
}
