package app.rayik.music.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/**
 * Shared artwork tile: themed note placeholder underneath, remote art on
 * top when present. Graceful when art fails — never a grey box.
 */
@Composable
fun TrackArt(
  artworkUrl: String,
  modifier: Modifier = Modifier,
  corner: Dp = 8.dp,
) {
  Box(
    modifier = modifier
      .clip(RoundedCornerShape(corner))
      .background(MaterialTheme.colorScheme.surfaceVariant),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      imageVector = Icons.Filled.MusicNote,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(10.dp),
    )
    if (artworkUrl.isNotBlank()) {
      AsyncImage(
        model = artworkUrl,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.matchParentSize().clip(RoundedCornerShape(corner)),
      )
    }
  }
}
