package app.rayik.music.streaming

/**
 * A resolved, expiring stream URL. URLs are transient by design (TOS):
 * hand them to ExoPlayer, never persist them. [StreamResolver] expiry
 * helpers decide when to re-resolve.
 */
data class ResolvedStream(
  val url: String,
  /** Epoch millis after which the URL is expected to die. */
  val expiresAtEpochMs: Long,
  val bitrate: Int = 0,
  val codec: String = "",
  /** Container MIME (e.g. `audio/webm`) so the player skips type-sniffing. */
  val mimeType: String = "",
) {
  companion object {
    /**
     * googlevideo-style URLs carry `expire` as epoch *seconds*.
     * Parsed by hand (no android.net.Uri) so the rule stays JVM-testable.
     */
    private val EXPIRE_PARAM = Regex("[?&]expire=(\\d+)")

    fun expiryFromUrl(url: String, nowEpochMs: Long, fallbackTtlMs: Long = 6 * 60 * 60 * 1_000L): Long {
      val expireSec = EXPIRE_PARAM.find(url)?.groupValues?.getOrNull(1)?.toLongOrNull()
      return if (expireSec != null && expireSec > 0) expireSec * 1_000L else nowEpochMs + fallbackTtlMs
    }
  }
}
