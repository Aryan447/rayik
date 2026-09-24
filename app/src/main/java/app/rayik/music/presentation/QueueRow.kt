package app.rayik.music.presentation

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.rayik.music.player.QueueRow as QueueRowData
import app.rayik.music.ui.theme.spacing

/**
 * One Up-Next row: 56dp art, title/artist, animated equalizer while
 * playing. Shared by the Raay tab and the full player sheet.
 */
@Composable
fun UpNextRow(
  item: QueueRowData,
  isPlaying: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(16.dp))
      .clickable(onClick = onClick)
      .padding(
        horizontal = MaterialTheme.spacing.small,
        vertical = MaterialTheme.spacing.smaller,
      ),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    TrackArt(artworkUrl = item.artworkUrl, corner = 12.dp, modifier = Modifier.size(56.dp))
    Spacer(Modifier.width(MaterialTheme.spacing.medium))
    Column(Modifier.weight(1f)) {
      Text(
        item.title,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = if (item.isCurrent) FontWeight.SemiBold else FontWeight.Normal,
        color = if (item.isCurrent) {
          MaterialTheme.colorScheme.primary
        } else {
          MaterialTheme.colorScheme.onSurface
        },
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        item.artist,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
    Spacer(Modifier.width(MaterialTheme.spacing.small))
    if (isPlaying) {
      PlayingIndicator()
    } else if (item.isCurrent) {
      Text(
        "❚❚",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

/** Three-bar equalizer that bounces while the row is playing. */
@Composable
fun PlayingIndicator(
  modifier: Modifier = Modifier,
  color: Color = MaterialTheme.colorScheme.primary,
) {
  val transition = rememberInfiniteTransition(label = "eq")
  val bar1 by transition.animateFloat(
    initialValue = 0.35f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(tween(520), RepeatMode.Reverse),
    label = "eq1",
  )
  val bar2 by transition.animateFloat(
    initialValue = 1f,
    targetValue = 0.3f,
    animationSpec = infiniteRepeatable(tween(640), RepeatMode.Reverse),
    label = "eq2",
  )
  val bar3 by transition.animateFloat(
    initialValue = 0.5f,
    targetValue = 0.9f,
    animationSpec = infiniteRepeatable(tween(460), RepeatMode.Reverse),
    label = "eq3",
  )
  Row(
    modifier = modifier.height(18.dp),
    horizontalArrangement = Arrangement.spacedBy(2.dp),
    verticalAlignment = Alignment.Bottom,
  ) {
    for (scale in listOf(bar1, bar2, bar3)) {
      Box(
        Modifier
          .width(3.dp)
          .height((5 + 13 * scale).dp)
          .clip(RoundedCornerShape(2.dp))
          .background(color),
      )
    }
  }
}
