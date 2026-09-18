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

  fun select(candidates: List<AudioCandidate>, quality: StreamQuality): AudioCandidate? {
    val usable = candidates.filter { it.url.isNotBlank() }
    if (usable.isEmpty()) return null
    return when (quality) {
      StreamQuality.Saver -> usable.minByOrNull { it.bitrate } ?: usable.first()
      StreamQuality.High -> usable.maxByOrNull { it.bitrate } ?: usable.first()
      StreamQuality.Auto -> {
        usable.filter { it.bitrate in 1..AUTO_BITRATE_CAP }
          .maxByOrNull { it.bitrate }
          ?: usable.minByOrNull { it.bitrate }
          ?: usable.first()
      }
    }
  }
}
