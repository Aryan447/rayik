package app.rayik.music.presentation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Branded loader: the five bars breathe on a stagger. Used by
 * [ScreenScaffold] loading instead of a lone spinner — the mark's family,
 * not a default progress widget.
 */
@Composable
fun BarLoader(
  modifier: Modifier = Modifier,
  width: Dp = 56.dp,
  height: Dp = 36.dp,
  color: Color = MaterialTheme.colorScheme.primary,
) {
  val transition = rememberInfiniteTransition(label = "bar-loader")
  val phases = (0..4).map { i ->
    transition.animateFloat(
      initialValue = 0.45f,
      targetValue = 1f,
      animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = 620, delayMillis = i * 110, easing = FastOutSlowInEasing),
        repeatMode = RepeatMode.Reverse,
      ),
      label = "bar-$i",
    )
  }
  Canvas(modifier.size(width, height)) {
    val slotW = size.width / 5f
    val barW = slotW * 0.52f
    val maxH = size.height
    val heights = listOf(0.42f, 0.7f, 1f, 0.7f, 0.42f)
    heights.forEachIndexed { i, frac ->
      val h = maxH * frac * phases[i].value
      val cx = slotW * i + slotW / 2f
      drawRoundRect(
        color = color,
        topLeft = Offset(cx - barW / 2f, (size.height - h) / 2f),
        size = Size(barW, h),
        cornerRadius = CornerRadius(barW / 2f, barW / 2f),
      )
    }
  }
}

/** Gold bars on the dark tile — for empty-state art, not loading. */
@Composable
fun MarkTile(
  modifier: Modifier = Modifier,
  size: Dp = 72.dp,
) {
  RayikMark(modifier = modifier, size = size)
}
