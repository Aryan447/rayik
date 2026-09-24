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
 * queue is non-empty; tap expands the full player sheet.
 */
@Composable
fun MiniPlayer(
  onOpenPlayer: () -> Unit,
  player: PlayerViewModel = hiltViewModel(),
) {
  val rows by player.queueRows.collectAsState()
  val playbackState by player.playbackState.collectAsState()
  val positionMs by player.positionMs.collectAsState()
  val durationMs by player.durationMs.collectAsState()

  val current = rows.firstOrNull { it.isCurrent } ?: return

  Surface(
    tonalElevation = 4.dp,
    shape = RoundedCornerShape(20.dp),
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.small),
  ) {
    Column {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(20.dp))
          .clickable(onClick = onOpenPlayer)
          .padding(
            horizontal = MaterialTheme.spacing.medium,
            vertical = MaterialTheme.spacing.smaller,
          ),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        TrackArt(artworkUrl = current.artworkUrl, corner = 12.dp, modifier = Modifier.size(48.dp))
        Spacer(Modifier.width(MaterialTheme.spacing.medium))
        Column(Modifier.weight(1f)) {
          Text(
            current.title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
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
        if (playbackState == PlaybackUiState.Playing) {
          PlayingIndicator(modifier = Modifier.padding(end = MaterialTheme.spacing.small))
        }
        when (playbackState) {
          PlaybackUiState.Loading -> CircularProgressIndicator(modifier = Modifier.size(28.dp))
          PlaybackUiState.Playing -> IconButton(
            onClick = player::togglePlayPause,
            modifier = Modifier.size(48.dp),
          ) {
            Icon(
              Icons.Filled.Pause,
              contentDescription = stringResource(R.string.transport_pause),
              modifier = Modifier.size(28.dp),
            )
          }
          else -> IconButton(
            onClick = player::togglePlayPause,
            // Idle with a queue means "tap to start" — togglePlayPause
            // prepares and plays; only Error stays behind retry.
            enabled = playbackState == PlaybackUiState.Paused ||
              playbackState == PlaybackUiState.Idle,
            modifier = Modifier.size(48.dp),
          ) {
            Icon(
              Icons.Filled.PlayArrow,
              contentDescription = stringResource(R.string.transport_play),
              modifier = Modifier.size(28.dp),
            )
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
