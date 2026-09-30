package app.rayik.music.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.rayik.music.ui.theme.BrandGradient

/**
 * The one rāyik mark: five gold studio-EQ bars inside the acoustic ring on
 * the dark radial tile. Ported stop-for-stop from
 * `site/src/components/Logo.astro` (108 grid, 26-radius squircle, two
 * rings). Symmetric, so RTL-safe. Use everywhere — greeting, empty states,
 * share card placeholder — never a Material note icon.
 */
@Composable
fun RayikMark(
  modifier: Modifier = Modifier,
  size: Dp = 48.dp,
) {
  Canvas(
    modifier
      .size(size)
      .clip(RoundedCornerShape(percent = 24))
      .background(BrandGradient.markBackdropBrush),
  ) {
    val unit = size.toPx() / 108f
    drawCircle(
      brush = BrandGradient.ringDiagonalBrush,
      radius = 29f * unit,
      center = center,
      alpha = 0.35f,
      style = Stroke(width = 1.4f * unit),
    )
    drawCircle(
      brush = BrandGradient.ringDiagonalBrush,
      radius = 25f * unit,
      center = center,
      alpha = 0.2f,
      style = Stroke(width = 0.8f * unit),
    )
    val barWidth = 5.5f * unit
    val barXs = listOf(31.25f, 41.25f, 51.25f, 61.25f, 71.25f)
    val barHeights = listOf(20f, 34f, 48f, 34f, 20f)
    barXs.forEachIndexed { i, x ->
      val barHeight = barHeights[i] * unit
      drawRoundRect(
        brush = BrandGradient.goldVerticalBrush,
        topLeft = Offset(x * unit, center.y - barHeight / 2f),
        size = Size(barWidth, barHeight),
        cornerRadius = CornerRadius(2.75f * unit, 2.75f * unit),
      )
    }
  }
}
