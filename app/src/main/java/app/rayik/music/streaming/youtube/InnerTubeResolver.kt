package app.rayik.music.streaming.youtube

import android.util.Log
import androidx.media3.common.MimeTypes
import app.rayik.music.preferences.StreamQuality
import app.rayik.music.streaming.AudioCandidate
import app.rayik.music.streaming.ResolvedStream
import app.rayik.music.streaming.StreamSelection
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
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
) {
  private val json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
  }

  suspend fun resolve(
    videoId: String,
    quality: StreamQuality,
    excludedUrls: Set<String> = emptySet(),
  ): Result<ResolvedStream> {
    if (videoId.isBlank()) return Result.failure(IOException("Empty video id"))
    return try {
      val body = post(playerUrl(apiKey), json.encodeToString(InnerTubeRequest.serializer(), request(videoId)))
      val response = json.decodeFromString(InnerTubePlayerResponse.serializer(), body)
      pick(response, quality, excludedUrls)?.let {
        Log.i(TAG, "resolve $videoId via direct (${it.bitrate}bps ${it.codec})")
        Result.success(it)
      }
        ?: Result.failure(IOException(describeFailure(response, excludedUrls)))
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(IOException("Direct resolve failed (${e.message})", e))
    }
  }

  /**
   * Pure pick over an already-parsed response so unit tests cover the rule
   * without network: direct audio renditions through [StreamSelection],
   * HLS manifest fallback, else null. [excludedUrls] (already-failed URLs)
   * never come back — a retry must hand ExoPlayer a different edge host.
   */
  fun pick(
    response: InnerTubePlayerResponse,
    quality: StreamQuality,
    excludedUrls: Set<String> = emptySet(),
  ): ResolvedStream? {
    val now = System.currentTimeMillis()
    val candidates = response.streamingData?.adaptiveFormats.orEmpty()
      .mapNotNull { it.toCandidate() }
      .filter { it.url !in excludedUrls }
    StreamSelection.select(candidates, quality)?.let { chosen ->
      return ResolvedStream(
        url = chosen.url,
        expiresAtEpochMs = ResolvedStream.expiryFromUrl(chosen.url, now),
        bitrate = chosen.bitrate,
        codec = chosen.codec,
        mimeType = chosen.mimeType.substringBefore(';').trim()
          .takeIf { it.startsWith("audio/") }.orEmpty(),
      )
    }
    val hls = response.streamingData?.hlsManifestUrl
      ?.takeIf { it.isNotBlank() && it !in excludedUrls } ?: return null
    // Explicit manifest MIME: the URL is extension-less, so without this
    // ExoPlayer sniffs it as progressive and buffers forever.
    return ResolvedStream(
      url = hls,
      expiresAtEpochMs = now + HLS_FALLBACK_TTL_MS,
      mimeType = MimeTypes.APPLICATION_M3U8,
    )
  }

  private fun request(videoId: String) = InnerTubeRequest(
    videoId = videoId,
    context = InnerTubeContext(
      client = InnerTubeClient(
        clientName = CLIENT_NAME,
        clientVersion = CLIENT_VERSION,
        androidSdkVersion = ANDROID_SDK_VERSION,
      ),
    ),
  )

  /** Honest, UI-safe failure copy: keeps YouTube's reason, trimmed. */
  private fun describeFailure(response: InnerTubePlayerResponse, excludedUrls: Set<String>): String {
    val status = response.playabilityStatus.status.ifBlank { "UNKNOWN" }
    val reason = response.playabilityStatus.reason.trim().takeIf { it.isNotBlank() }
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
    const val CALL_TIMEOUT_MS = 12_000L
    const val HLS_FALLBACK_TTL_MS = 6 * 60 * 60 * 1_000L
    const val MAX_REASON_CHARS = 140
    /** Shared with the player so resolve + playback logs read as one story. */
    const val TAG = "RayikPlayer"

    /**
     * Pure URL builder so unit tests cover the keyless case without
     * network: a configured key is appended, a blank key sends the
     * endpoint bare (which serves the ANDROID client regardless).
     */
    fun playerUrl(apiKey: String): String =
      if (apiKey.isBlank()) PLAYER_BASE_URL else "$PLAYER_BASE_URL&key=${apiKey.trim()}"

    /** ANDROID client key + version. Versions get deprecated by Google
     * (19.09.37 started returning 400), so bump this when direct resolve
     * starts failing with HTTP 400 across the board. */
    const val CLIENT_VERSION = "20.10.38"
    private const val CLIENT_NAME = "ANDROID"
    private const val ANDROID_SDK_VERSION = 34
    private const val PLAYER_BASE_URL =
      "https://www.youtube.com/youtubei/v1/player?prettyPrint=false"
    private const val USER_AGENT =
      "com.google.android.youtube/$CLIENT_VERSION (Linux; U; Android 11) gzip"
    private const val CLIENT_NAME_HEADER = "3"

    private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    private val CODECS_PARAM = Regex("codecs=\"([^\"]+)\"")

    fun InnerTubeFormat.toCandidate(): AudioCandidate? {
      if (url.isBlank()) return null // Ciphered entries need JS decipher — unusable.
      if (!mimeType.startsWith("audio/")) return null
      if (bitrate <= 0) return null
      val codec = CODECS_PARAM.find(mimeType)?.groupValues?.getOrNull(1).orEmpty()
      return AudioCandidate(url = url, bitrate = bitrate, codec = codec, mimeType = mimeType)
    }
  }
}
