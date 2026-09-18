package app.rayik.music.streaming

/**
 * Container MIME lookup so ExoPlayer never has to sniff the content type
 * off an extension-less `videoplayback` URL. Pure and unit-tested.
 */
fun audioMimeType(codec: String, containerHint: String = ""): String? {
  val normalizedCodec = codec.substringBefore('.').trim().lowercase()
  if (normalizedCodec == "opus" || normalizedCodec == "vorbis") return "audio/webm"
  if (normalizedCodec == "mp4a") return "audio/mp4"
  return when (containerHint.substringBefore(';').trim().lowercase()) {
    "webm", "audio/webm" -> "audio/webm"
    "m4a", "mp4", "audio/mp4" -> "audio/mp4"
    else -> null
  }
}
