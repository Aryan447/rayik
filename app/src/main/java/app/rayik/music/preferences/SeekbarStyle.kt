package app.rayik.music.preferences

import androidx.annotation.StringRes
import app.rayik.music.R

/** Player seekbar style. Standard is the default; all three scrub identically. */
enum class SeekbarStyle(
  @StringRes val titleRes: Int,
) {
  Standard(R.string.pref_seekbar_standard),
  Thick(R.string.pref_seekbar_thick),
  Wavy(R.string.pref_seekbar_wavy),
}
