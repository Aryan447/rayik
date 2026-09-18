package app.rayik.music.streaming

import app.rayik.music.preferences.StreamQuality

/** One audio rendition offered for a track. */
data class AudioCandidate(
  val url: String,
  val bitrate: Int = 0,
  val codec: String = "",
  val mimeType: String = "",
)

/**
 * Pure rendition picker so the Settings quality toggle means something
 * and CI unit tests cover the rule without network:
 * - Saver: cheapest rendition (lowest bitrate).
 * - High: richest rendition (highest bitrate).
 * - Auto: best rendition at or under [AUTO_BITRATE_CAP], else the cheapest
 *   above the cap (never nothing when candidates exist).
 */
object StreamSelection {
  const val AUTO_BITRATE_CAP = 192_000

  fun select(candidates: List<AudioCandidate>, quality: StreamQuality): AudioCandidate? =
    order(candidates, quality).firstOrNull()

  /**
   * Full try-order for a quality setting, best first. The resolver walks
   * this list (verifying each URL is actually fetchable) instead of
   * blindly handing ExoPlayer the top pick — a dead edge host fails over
   * to the next rendition instead of the error screen.
   */
  fun order(candidates: List<AudioCandidate>, quality: StreamQuality): List<AudioCandidate> {
    val usable = candidates.filter { it.url.isNotBlank() }
    if (usable.isEmpty()) return emptyList()
    return when (quality) {
      StreamQuality.Saver -> usable.sortedBy { it.bitrate }
      StreamQuality.High -> usable.sortedByDescending { it.bitrate }
      StreamQuality.Auto -> {
        val under = usable.filter { it.bitrate in 1..AUTO_BITRATE_CAP }
          .sortedByDescending { it.bitrate }
        val over = usable.filter { it.bitrate <= 0 || it.bitrate > AUTO_BITRATE_CAP }
          .sortedBy { it.bitrate }
        under + over
      }
    }
  }
}
