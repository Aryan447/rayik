package app.rayik.music.presentation

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import app.rayik.music.ui.theme.AppShapes
import app.rayik.music.ui.theme.BrandGradient
import app.rayik.music.ui.theme.RayikIcons
import coil3.compose.AsyncImage

/**
 * Shared artwork tile: five bars on the theme sweep underneath, remote art
 * on top when present. Graceful when art fails — never a grey box.
 */
@Composable
fun TrackArt(
  artworkUrl: String,
  modifier: Modifier = Modifier,
  corner: Dp = AppShapes.art,
) {
  Box(
    modifier = modifier
      .clip(RoundedCornerShape(corner))
      .background(BrandGradient.artSweepBrush()),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      imageVector = RayikIcons.Raay,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f),
      modifier = Modifier.fillMaxSize(0.45f),
    )
    Crossfade(
      targetState = artworkUrl,
      animationSpec = tween(260, easing = FastOutSlowInEasing),
      label = "trackArtworkChange",
    ) { image ->
      if (image.isNotBlank()) {
        AsyncImage(
          model = image,
          contentDescription = null,
          contentScale = ContentScale.Crop,
          modifier = Modifier.matchParentSize().clip(RoundedCornerShape(corner)),
        )
      }
    }
  }
}
