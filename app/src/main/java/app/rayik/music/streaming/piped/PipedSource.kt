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
import kotlinx.coroutines.suspendCancellableCoroutine
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
    val encoded = java.net.URLEncoder.encode(query.trim(), "UTF-8")
    val errors = mutableListOf<String>()
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
        val stream = pickBest(response, excludedUrls)
        if (stream == null) {
          errors += "$base: ${pipedEmptyReason(body)}"
          continue
        }
        lastGoodInstance = base
        val now = System.currentTimeMillis()
        Log.i(TAG, "resolve ${track.id} via piped $base (${stream.bitrate}bps ${stream.codec})")
        return Result.success(
          ResolvedStream(
            url = stream.url,
            expiresAtEpochMs = ResolvedStream.expiryFromUrl(stream.url, now),
            bitrate = stream.bitrate,
            codec = stream.codec,
            // Manifest candidates already carry their MIME; progressive
            // renditions resolve through the codec/container lookup so
            // ExoPlayer skips type-sniffing on extension-less URLs.
            mimeType = stream.mimeType.takeIf { it.startsWith("application/") }
              ?: audioMimeType(stream.codec, stream.mimeType).orEmpty(),
          ),
        )
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

  /** Manifest-first fallback depth: direct audio, then HLS, then DASH. */
  internal fun pickBest(response: PipedStreamsResponse, excludedUrls: Set<String> = emptySet()): AudioCandidate? {
    val quality = preferences.streamQuality.get()
    val candidates = response.audioStreams.map {
      AudioCandidate(url = it.url, bitrate = it.bitrate, codec = it.codec, mimeType = it.format)
    }.filter { it.url !in excludedUrls }
    StreamSelection.select(candidates, quality)?.let { return it }
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
