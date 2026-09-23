package app.rayik.music.preferences

import androidx.annotation.StringRes
import app.rayik.music.R

/** Streaming quality. Takes real effect once the streaming layer resolves URLs. */
enum class StreamQuality(
  @StringRes val titleRes: Int,
) {
  Auto(R.string.pref_quality_auto),
  High(R.string.pref_quality_high),
  Saver(R.string.pref_quality_saver),
}
