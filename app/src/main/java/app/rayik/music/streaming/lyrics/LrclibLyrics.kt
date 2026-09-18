package app.rayik.music.streaming.lyrics

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Serializable
private data class LrclibResponse(
  val plainLyrics: String = "",
  val syncedLyrics: String? = null,
)

/**
 * Keyless lyrics over the public LRCLIB API: no keys, no registration,
 * plain HTTPS with the shared client. Synced LRC preferred, plain text
 * fallback, empty result when nothing matches.
 */
class LrclibLyrics(
  private val okHttp: OkHttpClient,
) {
  private val json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
  }

  suspend fun fetch(artist: String, title: String, durationMs: Long): Result<LyricsResult> {
    if (artist.isBlank() || title.isBlank()) return Result.success(LyricsResult.EMPTY)
    return try {
      val url = buildString {
        append("$BASE/api/get?artist_name=${enc(artist)}&track_name=${enc(title)}")
        if (durationMs > 0) append("&duration=${durationMs / 1_000}")
      }
      val body = get(url)
      val response = json.decodeFromString(LrclibResponse.serializer(), body)
      val synced = response.syncedLyrics?.takeIf { it.isNotBlank() }?.let(LrcParser::parse).orEmpty()
      if (synced.isNotEmpty()) {
        Result.success(LyricsResult(synced, synced = true))
      } else {
        val plain = response.plainLyrics.lineSequence()
          .map { it.trim() }
          .filter { it.isNotEmpty() }
          .map { LyricLine(-1L, it) }
          .toList()
        Result.success(LyricsResult(plain, synced = false))
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(IOException("Lyrics unreachable (${e.message})"))
    }
  }

  private suspend fun get(url: String): String =
    withTimeout(CALL_TIMEOUT_MS) {
      suspendCancellableCoroutine { cont ->
        val call = okHttp.newCall(
          Request.Builder().url(url).header("User-Agent", "rayik-preview").build(),
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
    const val BASE = "https://lrclib.net"
    const val CALL_TIMEOUT_MS = 12_000L

    private fun enc(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")
  }
}
