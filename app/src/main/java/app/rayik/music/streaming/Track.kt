package app.rayik.music.streaming

/**
 * A playable track from any source. Source-agnostic: YouTube, Spotify
 * import, or pinned offline all resolve into this shape.
 */
data class Track(
  /** Source-scoped stable id (e.g. YouTube video id). */
  val id: String,
  val title: String,
  val artist: String,
  val durationMs: Long = 0L,
  val artworkUrl: String = "",
)
