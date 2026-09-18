package app.rayik.music.streaming.youtube

import android.util.Log
import androidx.media3.common.MimeTypes
import app.rayik.music.preferences.StreamQuality
import app.rayik.music.streaming.AudioCandidate
import app.rayik.music.streaming.ResolvedStream
import app.rayik.music.streaming.StreamSelection
import app.rayik.music.streaming.Track
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * On-device YouTube resolution over the InnerTube `player` endpoint with
 * the ANDROID client, which returns direct (unciphered) audio URLs.
 *
 * Why this exists: public Piped instances resolve server-side from
 * datacenter IPs, which YouTube routinely bot-blocks (`LOGIN_REQUIRED:
 * "Sign in to confirm that you're not a bot"`), while search keeps
 * working. A request from the listener's own device IP passes the same
 * check, so this is the primary resolve path and Piped is the fallback.
 * Returned URLs stay transient — expiry is honored by [ResolvedStream]
 * and playback only ever pins through download quotas.
 *
 * The InnerTube API key is NOT hardcoded here (never commit credentials).
 * It is injected via the constructor from `BuildConfig.INNERTUBE_API_KEY`,
 * which Gradle fills from `YOUTUBE_INNERTUBE_API_KEY` /
 * `-PinnertubeApiKey` / `local.properties` (`innertube.apiKey`).
 * A blank key is fine: the `player` endpoint serves the ANDROID client
 * without one (verified live: status OK + direct audio URLs), and the key
 * — when present — is only ever appended as a query parameter.
 */
class InnerTubeResolver(
  private val okHttp: OkHttpClient,
  private val apiKey: String,
  /**
   * GVS proof-of-origin tokens (nullable: unit tests and keyless installs
   * construct the resolver without one, and resolution simply skips the
   * token pass). Wired by Koin in production.
   */
  private val poTokens: PoTokenProvider? = null,
) {
  private val json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    // The player request relies on defaulted playback checks
    // (contentCheckOk / racyCheckOk / html5Preference): without this they
    // silently never hit the wire and edge integrity checks keep an excuse
    // to 403 the stream at fetch time.
    encodeDefaults = true
  }

  suspend fun resolve(
    videoId: String,
    quality: StreamQuality,
    excludedUrls: Set<String> = emptySet(),
  ): Result<ResolvedStream> {
    if (videoId.isBlank()) return Result.failure(IOException("Empty video id"))
    return try {
      val token = poTokens?.cachedFor(videoId)
      val body = post(playerUrl(apiKey), json.encodeToString(InnerTubeRequest.serializer(), request(videoId, token)))
      val response = json.decodeFromString(InnerTubePlayerResponse.serializer(), body)
      // googlevideo edge hosts 403 URLs they didn't mint for this
      // network (per-region PO-token/bot enforcement, Aug 2026 arms race):
      // verify each candidate with an offset Range probe from this device —
      // the same path ExoPlayer will use — and fail over to the next
      // rendition instead of handing the player a dead link.
      val excluded = excludedUrls.toMutableSet()
      // The visitor identity that minted these URLs travels back on the
      // verify probe (X-Goog-Visitor-Id), falling back to the PO-token
      // session's identity — URL, visitor header, and token stay coherent.
      val verifyVisitorData = response.responseContext?.visitorData.orEmpty()
        .ifBlank { token?.visitorData.orEmpty() }
      var lastStatus = -1
      // Pass 1: URLs as minted (plus a cached PO token when one exists).
      verifyPass(videoId, response, quality, excluded, token?.value.orEmpty(), verifyVisitorData).let { (won, status) ->
        lastStatus = status
        if (won != null) return Result.success(won)
      }
      // Pass 2: full 403 cascade with no token attached yet — mint a GVS
      // proof-of-origin token for THIS video (WebView, BotGuard does the
      // attestation; tokens are video-bound) and retry the survivors with
      // `pot` appended. A mint takes seconds; playing at all beats fast.
      if (lastStatus == 403 && token == null && poTokens != null) {
        val fresh = try {
          poTokens.tokenFor(videoId)
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          Log.w(TAG, "resolve $videoId PO-token mint failed: ${e.message}")
          null
        }
        if (fresh != null) {
          verifyPass(videoId, response, quality, excluded, fresh.value, fresh.visitorData.ifBlank { verifyVisitorData }).let { (won, status) ->
            lastStatus = status
            if (won != null) return Result.success(won)
          }
        }
      }
      Result.failure(IOException(describeFailure(response, excluded, lastStatus, verifiedDead = lastStatus != -1)))
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(IOException("Direct resolve failed (${e.message})", e))
    }
  }

  /**
   * Cached PO-token value for [videoId], or empty. The Piped fallback pass
   * reuses a token minted during direct resolution (same video) — minting
   * itself only ever triggers in the direct pass after a 403 cascade.
   */
  fun poTokenFor(videoId: String): String = poTokens?.cachedFor(videoId)?.value.orEmpty()

  /**
   * One verify walk over [orderedCandidates]: first candidate whose offset
   * probe succeeds wins, the rest are added to [excluded]. Returns the
   * winner (or null) plus the last probe status for honest errors.
   */
  private suspend fun verifyPass(
    videoId: String,
    response: InnerTubePlayerResponse,
    quality: StreamQuality,
    excluded: MutableSet<String>,
    pot: String,
    visitorData: String,
  ): Pair<ResolvedStream?, Int> {
    var lastStatus = -1
    repeat(MAX_VERIFY_ATTEMPTS) {
      val next = orderedCandidates(response, quality, excluded).firstOrNull()
        ?: return null to lastStatus
      val url = withPot(next.url, pot)
      val status = withContext(Dispatchers.IO) { verifyUrl(url, visitorData) }
      if (status in 200..299) {
        val won = if (url == next.url) next else next.copy(url = url)
        Log.i(TAG, "resolve $videoId via direct (${won.bitrate}bps ${won.codec}${if (pot.isNotBlank()) " +pot" else ""})")
        return won to status
      }
      lastStatus = status
      // Exclude both spellings: the bare URL and the pot-attached one must
      // never come back on retry.
      excluded += next.url
      excluded += url
      Log.w(TAG, "resolve $videoId candidate HTTP $status, failing over (${excluded.size} dead)")
    }
    return null to lastStatus
  }

  suspend fun search(query: String): Result<List<Track>> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return Result.success(emptyList())
    return try {
      val req = InnerTubeSearchRequest(
        query = trimmed,
        context = InnerTubeContext(
          client = InnerTubeClient(
            clientName = CLIENT_NAME,
            clientVersion = CLIENT_VERSION,
            androidSdkVersion = ANDROID_SDK_VERSION,
          ),
        ),
      )
      val body = post(searchUrl(apiKey), json.encodeToString(InnerTubeSearchRequest.serializer(), req))
      val element = json.parseToJsonElement(body)
      val tracks = parseSearchTracks(element)
      Log.i(TAG, "InnerTube search '${trimmed.take(20)}' found ${tracks.size} tracks")
      Result.success(tracks)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.w(TAG, "InnerTube search failed: ${e.message}")
      Result.failure(e)
    }
  }

  /**
   * Pure pick over an already-parsed response so unit tests cover the rule
   * without network: direct audio renditions through [StreamSelection],
   * muxed-with-audio fallback (itag 18 class — PO-token exempt per yt-dlp,
   * plays audio fine with the video track dropped), HLS manifest fallback,
   * else null. [excludedUrls] (already-failed URLs) never come back — a
   * retry must hand ExoPlayer a different edge host.
   */
  fun pick(
    response: InnerTubePlayerResponse,
    quality: StreamQuality,
    excludedUrls: Set<String> = emptySet(),
  ): ResolvedStream? = orderedCandidates(response, quality, excludedUrls).firstOrNull()

  /**
   * Full try-order, best first: audio renditions in quality order, then
   * muxed-with-audio (cheapest first), then the HLS manifest. The resolve
   * loop walks this list verifying each URL, so every entry must be a
   * distinct playable candidate.
   */
  fun orderedCandidates(
    response: InnerTubePlayerResponse,
    quality: StreamQuality,
    excludedUrls: Set<String> = emptySet(),
  ): List<ResolvedStream> {
    val now = System.currentTimeMillis()
    val out = mutableListOf<ResolvedStream>()
    val candidates = response.streamingData?.adaptiveFormats.orEmpty()
      .mapNotNull { it.toCandidate() }
      .filter { it.url !in excludedUrls }
    for (chosen in StreamSelection.order(candidates, quality)) {
      out += ResolvedStream(
        url = chosen.url,
        expiresAtEpochMs = ResolvedStream.expiryFromUrl(chosen.url, now),
        bitrate = chosen.bitrate,
        codec = chosen.codec,
        mimeType = chosen.mimeType.substringBefore(';').trim()
          .takeIf { it.startsWith("audio/") }.orEmpty(),
      )
    }
    val formats = response.streamingData?.adaptiveFormats.orEmpty()
    val muxed = formats.mapNotNull { it.toMuxedCandidate() }
      .filter { it.url !in excludedUrls && out.none { have -> have.url == it.url } }
      .sortedBy { it.bitrate }
    for (chosen in muxed) {
      out += ResolvedStream(
        url = chosen.url,
        expiresAtEpochMs = ResolvedStream.expiryFromUrl(chosen.url, now),
        bitrate = chosen.bitrate,
        codec = chosen.codec,
        mimeType = chosen.mimeType.substringBefore(';').trim()
          .takeIf { it.contains('/') }.orEmpty(),
      )
    }
    response.streamingData?.hlsManifestUrl
      ?.takeIf { it.isNotBlank() && it !in excludedUrls && out.none { have -> have.url == it } }
      ?.let { hls ->
        // Explicit manifest MIME: the URL is extension-less, so without this
        // ExoPlayer sniffs it as progressive and buffers forever.
        out += ResolvedStream(
          url = hls,
          expiresAtEpochMs = now + HLS_FALLBACK_TTL_MS,
          mimeType = MimeTypes.APPLICATION_M3U8,
        )
      }
    return out
  }

  private fun request(videoId: String, token: PoToken? = null) = InnerTubeRequest(
    videoId = videoId,
    context = InnerTubeContext(
      client = InnerTubeClient(
        clientName = CLIENT_NAME,
        clientVersion = CLIENT_VERSION,
        androidSdkVersion = ANDROID_SDK_VERSION,
        // Identity the token was minted under: the player binds returned
        // URLs to it, so URL + visitor + token stay coherent to the byte.
        visitorData = token?.visitorData?.takeIf { it.isNotBlank() },
      ),
    ),
    serviceIntegrityDimensions = token?.takeIf { it.value.isNotBlank() }?.let {
      InnerTubeIntegrity(poToken = it.value)
    },
  )

  /** Honest, UI-safe failure copy: keeps YouTube's reason, trimmed. */
  private fun describeFailure(
    response: InnerTubePlayerResponse,
    excludedUrls: Set<String>,
    lastHttpStatus: Int = -1,
    verifiedDead: Boolean = false,
  ): String {
    val status = response.playabilityStatus.status.ifBlank { "UNKNOWN" }
    val reason = response.playabilityStatus.reason.trim().takeIf { it.isNotBlank() }
    // Every candidate verified dead at fetch time: the edge refused this
    // device/network (the per-network 403 arms race) — say exactly that.
    if (verifiedDead && lastHttpStatus in 400..599) {
      return "YouTube refused playback (HTTP $lastHttpStatus) — try again in a bit"
    }
    return if (excludedUrls.isNotEmpty()) {
      "Every stream for this track failed — try another track"
    } else if (status == "OK") {
      "YouTube returned no audio for this track — try another"
    } else if (status == "LOGIN_REQUIRED") {
      "YouTube blocked this request (bot check) — try again in a bit"
    } else if (reason != null) {
      "YouTube: $status — ${reason.take(MAX_REASON_CHARS)}"
    } else {
      "YouTube: $status — try another track"
    }
  }

  /**
   * Fetchability probe: is this URL playable from this device right now?
   * Same User-Agent ExoPlayer will send, so a 403 here predicts the exact
   * playback failure from the bug report. Returns the HTTP status, or -1
   * on transport failure. Blocking — callers must dispatch to IO.
   *
   * The Range starts past the 1 MiB cold-start allowance YouTube grants
   * uncredentialed GVS fetches before enforcing PO-token checks (Aug 2026:
   * serve ~1 MB, then 403 everything past it). A 0-byte probe passes on
   * such links and predicts nothing — the offset probe catches both
   * immediate refusals and allowance-then-403 enforcement. 416 means the
   * file is simply smaller than the offset (tiny manifests included), so
   * it counts as playable.
   */
  fun verifyUrl(url: String, visitorData: String = ""): Int {
    if (url.isBlank()) return -1
    return try {
      val call = okHttp.newCall(
        Request.Builder()
          .url(url)
          .header("User-Agent", USER_AGENT)
          .header("Range", "bytes=$ALLOWANCE_PROBE_OFFSET-${ALLOWANCE_PROBE_OFFSET + 1}")
          .apply { if (visitorData.isNotBlank()) header("X-Goog-Visitor-Id", visitorData) }
          .build(),
      )
      // Independent of the shared client's generous media timeouts: a
      // hanging edge must fail fast so failover stays under the spinner.
      call.timeout().timeout(VERIFY_TIMEOUT_MS, TimeUnit.MILLISECONDS)
      call.execute().use { if (it.code == 416) 200 else it.code }
    } catch (_: Exception) {
      -1
    }
  }

  private suspend fun post(url: String, jsonBody: String): String =
    withTimeout(CALL_TIMEOUT_MS) {
      suspendCancellableCoroutine { cont ->
        val call = okHttp.newCall(
          Request.Builder()
            .url(url)
            .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
            .header("User-Agent", USER_AGENT)
            .header("X-YouTube-Client-Name", CLIENT_NAME_HEADER)
            .header("X-YouTube-Client-Version", CLIENT_VERSION)
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
                    cont.resume(it.body.string())
                  }
                }
              }
            }
          },
        )
      }
    }

  companion object {
    const val CALL_TIMEOUT_MS = 12_000L
    const val HLS_FALLBACK_TTL_MS = 6 * 60 * 60 * 1_000L
    const val MAX_REASON_CHARS = 140
    /**
     * Verify-then-hand-over budget: each dead candidate costs one 1-byte
     * probe, so 4 covers quality-order failover + muxed + HLS without
     * holding the tap-to-play spinner hostage.
     */
    const val MAX_VERIFY_ATTEMPTS = 4
    /** Per-candidate fetchability probe budget (see [verifyUrl]). */
    const val VERIFY_TIMEOUT_MS = 5_000L
    /**
     * Probe offset past YouTube's ~1 MiB uncredentialed GVS allowance: links
     * under PO-token enforcement serve the first megabyte, then 403.
     */
    const val ALLOWANCE_PROBE_OFFSET = 1_048_576L
    /**
     * Append a GVS proof-of-origin token to a googlevideo URL (no-op when
     * blank, already present, or off googlevideo). Pure so unit tests cover
     * the rule without network.
     */
    fun withPot(url: String, pot: String): String {
      if (pot.isBlank() || url.isBlank()) return url
      if (url.contains("pot=")) return url
      val host = url.substringAfter("://").substringBefore('/').lowercase()
      if (host != "googlevideo.com" && !host.endsWith(".googlevideo.com")) return url
      return url + if (url.contains('?')) "&pot=$pot" else "?pot=$pot"
    }
    /** Shared with the player so resolve + playback logs read as one story. */
    const val TAG = "RayikPlayer"

    /**
     * Pure URL builder so unit tests cover the keyless case without
     * network: a configured key is appended, a blank key sends the
     * endpoint bare (which serves the ANDROID client regardless).
     */
    fun playerUrl(apiKey: String): String =
      if (apiKey.isBlank()) PLAYER_BASE_URL else "$PLAYER_BASE_URL&key=${apiKey.trim()}"

    fun searchUrl(apiKey: String): String =
      if (apiKey.isBlank()) SEARCH_BASE_URL else "$SEARCH_BASE_URL&key=${apiKey.trim()}"

    /** ANDROID client key + version. Versions get deprecated by Google
     * (19.09.37 started returning 400), so bump this when direct resolve
     * starts failing with HTTP 400 across the board. */
    const val CLIENT_VERSION = "20.10.38"
    private const val CLIENT_NAME = "ANDROID"
    private const val ANDROID_SDK_VERSION = 34
    private const val PLAYER_BASE_URL =
      "https://www.youtube.com/youtubei/v1/player?prettyPrint=false"
    private const val SEARCH_BASE_URL =
      "https://www.youtube.com/youtubei/v1/search?prettyPrint=false"
    const val USER_AGENT =
      "com.google.android.youtube/$CLIENT_VERSION (Linux; U; Android 11) gzip"
    private const val CLIENT_NAME_HEADER = "3"

    fun parseSearchTracks(root: JsonElement): List<Track> {
      val tracks = mutableListOf<Track>()
      val seenIds = mutableSetOf<String>()
      fun extract(element: JsonElement) {
        when (element) {
          is JsonObject -> {
            val compact = element["compactVideoRenderer"] as? JsonObject
              ?: element["videoRenderer"] as? JsonObject
            if (compact != null) {
              val videoId = compact["videoId"]?.jsonPrimitive?.contentOrNull
              val title = compact["title"]?.jsonObject?.get("runs")?.jsonArray?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
                ?: compact["title"]?.jsonObject?.get("simpleText")?.jsonPrimitive?.contentOrNull
              val artist = compact["shortBylineText"]?.jsonObject?.get("runs")?.jsonArray?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
                ?: compact["ownerText"]?.jsonObject?.get("runs")?.jsonArray?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
                ?: "Unknown artist"
              val thumbs = compact["thumbnail"]?.jsonObject?.get("thumbnails")?.jsonArray
              val thumbUrl = thumbs?.lastOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull.orEmpty()
              val lengthStr = compact["lengthText"]?.jsonObject?.get("runs")?.jsonArray?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
                ?: compact["lengthText"]?.jsonObject?.get("simpleText")?.jsonPrimitive?.contentOrNull
              val durationMs = parseDurationMs(lengthStr)
              if (!videoId.isNullOrBlank() && !title.isNullOrBlank() && seenIds.add(videoId)) {
                tracks.add(
                  Track(
                    id = videoId,
                    title = title,
                    artist = artist,
                    durationMs = durationMs,
                    artworkUrl = thumbUrl,
                  ),
                )
              }
            } else {
              for (child in element.values) {
                extract(child)
              }
            }
          }
          is JsonArray -> {
            for (item in element) {
              extract(item)
            }
          }
          else -> Unit
        }
      }
      extract(root)
      return tracks
    }

    fun parseDurationMs(duration: String?): Long {
      if (duration.isNullOrBlank()) return 0L
      val parts = duration.split(':').mapNotNull { it.trim().toLongOrNull() }
      return when (parts.size) {
        2 -> (parts[0] * 60 + parts[1]) * 1_000L
        3 -> (parts[0] * 3_600 + parts[1] * 60 + parts[2]) * 1_000L
        else -> 0L
      }
    }

    private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    private val CODECS_PARAM = Regex("codecs=\"([^\"]+)\"")

    fun InnerTubeFormat.toCandidate(): AudioCandidate? {
      if (url.isBlank()) return null // Ciphered entries need JS decipher — unusable.
      if (!mimeType.startsWith("audio/")) return null
      if (bitrate <= 0) return null
      val codec = CODECS_PARAM.find(mimeType)?.groupValues?.getOrNull(1).orEmpty()
      return AudioCandidate(url = url, bitrate = bitrate, codec = codec, mimeType = mimeType)
    }

    /**
     * Muxed-with-audio fallback (itag 18 class): video container carrying
     * an audio track. ExoPlayer drops the video track and plays the audio —
     * and this class is exempt from the GVS PO-token enforcement that 403s
     * audio-only renditions per-region (yt-dlp #17348). Cheapest first, so
     * the 360p muxed rendition wins over HD ones.
     */
    fun InnerTubeFormat.toMuxedCandidate(): AudioCandidate? {
      if (url.isBlank()) return null
      if (mimeType.startsWith("audio/")) return null // audio-only is handled by toCandidate
      if (!mimeType.startsWith("video/")) return null
      if (bitrate <= 0) return null
      // audioChannels is only present on audio-bearing formats; known muxed
      // itags backstop responses that omit it.
      if (audioChannels <= 0 && itag !in MUXED_AUDIO_ITAGS) return null
      val codec = CODECS_PARAM.find(mimeType)?.groupValues?.getOrNull(1).orEmpty()
      return AudioCandidate(url = url, bitrate = bitrate, codec = codec, mimeType = mimeType)
    }

    /** Muxed itags known to carry an audio track (360p/720p MP4, 144p 3GP). */
    private val MUXED_AUDIO_ITAGS = setOf(18, 22, 17, 36)
  }
}
