package app.rayik.music.player

/**
 * On-device diagnostics bundle for playback failures. The 403-class
 * failures this app fights are network/IP-scoped and unreproducible from
 * anywhere else, so the error card offers "Copy details" — paste it into a
 * bug report instead of needing adb. Stream URLs are reduced to host +
 * container on purpose: query params carry IP-bound tokens with no
 * diagnostic value worth the privacy cost.
 *
 * Pure over strings so unit tests cover the redaction without Android.
 */
fun buildPlaybackDiagnostics(
  appVersion: String,
  gitSha: String,
  trackId: String,
  trackTitle: String,
  streamUrl: String,
  mimeType: String,
  errorMessage: String,
): String {
  val host = streamHost(streamUrl)
  return buildString {
    appendLine("rayik $appVersion ($gitSha)")
    appendLine("track: $trackId — $trackTitle")
    appendLine("stream: host=$host mime=${mimeType.ifBlank { "unknown" }}")
    append("error: $errorMessage")
  }
}

/**
 * Host of an `https://host/path?query` URL, hand-parsed (no android.net.Uri)
 * to stay JVM-testable. Returns `""` when there is nothing shaped like a URL.
 */
fun streamHost(url: String): String {
  val match = STREAM_HOST.find(url.trim())
  return match?.groupValues?.getOrNull(1).orEmpty()
}

private val STREAM_HOST = Regex("^https?://([^/?#\\s]+)")
