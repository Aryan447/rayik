package app.rayik.music.preferences

import app.rayik.music.preferences.preference.PreferenceStore
import app.rayik.music.preferences.preference.getEnum
import app.rayik.music.ui.theme.AppTheme

/**
 * Streaming-first appearance prefs. Ported from mpvium pattern;
 * video-specific prefs (seekbar styles, player buttons) are intentionally dropped.
 * The one sanctioned deviation is [albumArtDynamic]: Palette -> primaryContainer.
 */
class AppearancePreferences(
  preferenceStore: PreferenceStore,
) {
  val darkMode = preferenceStore.getEnum("dark_mode", app.rayik.music.ui.theme.DarkMode.System)
  val appTheme = preferenceStore.getEnum("app_theme", AppTheme.Dynamic)
  val amoledMode = preferenceStore.getBoolean("amoled_mode", false)
  val albumArtDynamic = preferenceStore.getBoolean("album_art_dynamic", true)
  val onboardingCompleted = preferenceStore.getBoolean("onboarding_completed", false)
  val streamQuality = preferenceStore.getEnum("stream_quality", StreamQuality.Auto)
}
