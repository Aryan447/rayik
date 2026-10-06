package app.rayik.music.presentation

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Bottom radius of the inset content sheet — Pocket Casts-style curve over the dock. */
val DockSheetBottomRadius: Dp = 28.dp

/**
 * Bottom clearance for tab screens when the floating dock is on: the M3
 * expressive pill (~80dp) + ScreenOffset (16dp) + breathing room. Content
 * draws behind and beside the pill, so lists need this to clear it.
 */
val DockFloatingClearance: Dp = 112.dp

/** True when the active scheme is light (background luminance), drives dock inversion. */
@Composable
private fun isLightScheme(): Boolean =
  MaterialTheme.colorScheme.background.luminance() > 0.5f

/**
 * Dock container: dark in light mode, grey in dark mode — theme-derived.
 * Light -> inverseSurface (each theme's own dark), dark/AMOLED ->
 * surfaceContainerHighest (lifted grey, still visible on black).
 */
@Composable
fun dockContainer(): Color {
  val scheme = MaterialTheme.colorScheme
  return if (isLightScheme()) scheme.inverseSurface else scheme.surfaceContainerHighest
}

/** Primary content on the dock (title, icons). */
@Composable
fun onDock(): Color {
  val scheme = MaterialTheme.colorScheme
  return if (isLightScheme()) scheme.inverseOnSurface else scheme.onSurface
}

/** Secondary content on the dock (artist, hints). */
@Composable
fun onDockVariant(): Color = onDock().copy(alpha = 0.72f)

/** Inset content-sheet shape: straight top, curved bottom sitting on the dock. */
fun dockSheetShape() = RoundedCornerShape(
  bottomStart = DockSheetBottomRadius,
  bottomEnd = DockSheetBottomRadius,
)

/** Artwork tile size in the collapsed transport dock. */
val DockArtSize: Dp = 52.dp

/** Touch target of the hero play/pause slot (icon itself is oversized). */
val DockHeroTouch: Dp = 64.dp

/** Selected-tab pill wash on the dock. */
@Composable
fun dockSelectedPill(): Color = onDock().copy(alpha = 0.16f)

/** Dimmed dock icon/label (unselected tabs, hints). */
@Composable
fun onDockDim(): Color = onDock().copy(alpha = 0.6f)
