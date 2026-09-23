package app.rayik.music.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.rayik.music.player.PlaybackUiState
import app.rayik.music.player.PlayerViewModel
import app.rayik.music.ui.theme.spacing
import app.rayik.music.R

/**
 * Compact now-playing strip above the bottom nav. Visible whenever the
 * queue is non-empty; tap opens the Queue tab.
 */
@Composable
fun MiniPlayer(
  onOpenQueue: () -> Unit,
  player: PlayerViewModel = hiltViewModel(),
) {
  val rows by player.queueRows.collectAsState()
  val playbackState by player.playbackState.collectAsState()
  val positionMs by player.positionMs.collectAsState()
  val durationMs by player.durationMs.collectAsState()

  val current = rows.firstOrNull { it.isCurrent } ?: return

  Surface(
    tonalElevation = 3.dp,
    shape = RoundedCornerShape(16.dp),
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.small),
  ) {
    Column {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(16.dp))
          .clickable(onClick = onOpenQueue)
          .padding(MaterialTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        TrackArt(artworkUrl = current.artworkUrl, modifier = Modifier.size(44.dp))
        Spacer(Modifier.width(MaterialTheme.spacing.medium))
        Column(Modifier.weight(1f)) {
          Text(
            current.title,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            current.artist,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        when (playbackState) {
          PlaybackUiState.Loading -> CircularProgressIndicator(modifier = Modifier.size(24.dp))
          PlaybackUiState.Playing -> IconButton(onClick = player::togglePlayPause) {
            Icon(Icons.Filled.Pause, contentDescription = stringResource(R.string.transport_pause))
          }
          else -> IconButton(
            onClick = player::togglePlayPause,
            // Idle with a queue means "tap to start" — togglePlayPause
            // prepares and plays; only Error stays behind retry.
            enabled = playbackState == PlaybackUiState.Paused ||
              playbackState == PlaybackUiState.Idle,
          ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.transport_play))
          }
        }
      }
      if (durationMs > 0) {
        LinearProgressIndicator(
          progress = { (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) },
          modifier = Modifier.fillMaxWidth(),
        )
      }
    }
  }
}
