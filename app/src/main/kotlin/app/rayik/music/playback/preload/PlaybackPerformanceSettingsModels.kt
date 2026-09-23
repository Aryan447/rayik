/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package app.rayik.music.playback.preload

import app.rayik.music.constants.AudioQuality
import app.rayik.music.innertube.PlaybackAuthState

data class PlaybackPerformanceSettings(
    val lowDataModeEnabled: Boolean,
    val preloadNextSongEnabled: Boolean,
    val hasPersistedValue: Boolean,
)

data class PlaybackPreloadConfiguration(
    val enabled: Boolean,
    val quality: AudioQuality,
    val authState: PlaybackAuthState,
)
