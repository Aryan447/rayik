package app.rayik.music.presentation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.rayik.music.player.PlaybackUiState
import app.rayik.music.player.PlayerViewModel
import app.rayik.music.player.QueueRow
import app.rayik.music.player.RepeatMode
import app.rayik.music.player.buildPlaybackDiagnostics
import app.rayik.music.player.formatMs
import app.rayik.music.ui.theme.spacing
import app.rayik.music.BuildConfig
import app.rayik.music.R

/**
 * Player queue: transport controls, seek, repeat/shuffle, up-next list.
 * Empty, loading, and error states are all designed — never blank.
 */
@Composable
fun QueueScreen(
  player: PlayerViewModel = hiltViewModel(),
) {
  val rows by player.queueRows.collectAsState()
  val playbackState by player.playbackState.collectAsState()
  val positionMs by player.positionMs.collectAsState()
  val durationMs by player.durationMs.collectAsState()
  val repeatMode by player.repeatMode.collectAsState()
  val shuffleEnabled by player.shuffleEnabled.collectAsState()
  val connected by player.connected.collectAsState()
  val context = LocalContext.current

  val current = rows.firstOrNull { it.isCurrent }

  Column(Modifier.fillMaxSize()) {
    TransportBlock(
      state = playbackState,
      positionMs = positionMs,
      durationMs = durationMs,
      repeatMode = repeatMode,
      shuffleEnabled = shuffleEnabled,
      onToggle = player::togglePlayPause,
      onNext = player::next,
      onPrevious = player::previous,
      onSeek = player::seekTo,
      onSeekForward = player::seekForward,
      onSeekBack = player::seekBack,
      onCycleRepeat = player::cycleRepeat,
      onToggleShuffle = player::toggleShuffle,
      onRetry = player::retry,
      secondaryLabel = "Copy details",
      onSecondary = {
        copyDiagnostics(
          context,
          buildPlaybackDiagnostics(
            appVersion = BuildConfig.VERSION_NAME,
            gitSha = "master",
            trackId = current?.mediaId.orEmpty(),
            trackTitle = current?.title.orEmpty(),
            streamUrl = "",
            mimeType = "",
            errorMessage = (playbackState as? PlaybackUiState.Error)?.message.orEmpty(),
          ),
        )
      },
    )

    Spacer(Modifier.height(MaterialTheme.spacing.medium))

    Text(
      stringResource(R.string.queue_title),
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(MaterialTheme.spacing.small))

    if (rows.isEmpty() && !connected) {
      ScreenScaffold(
        state = ScreenState.Loading,
        loadingText = stringResource(R.string.queue_connecting),
        onRetry = {},
      ) {}
    } else if (rows.isEmpty()) {
      ScreenScaffold(state = ScreenState.Ready, loadingText = "", onRetry = {}) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(vertical = MaterialTheme.spacing.large),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Text(stringResource(R.string.queue_empty_title), style = MaterialTheme.typography.titleSmall)
          Text(
            stringResource(R.string.queue_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = MaterialTheme.spacing.small),
          )
        }
      }
    } else {
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
      ) {
        items(rows, key = { it.mediaId }) { row ->
          QueueRow(
            item = row,
            isPlaying = row.isCurrent && playbackState == PlaybackUiState.Playing,
            onClick = { player.playWindow(row) },
          )
        }
      }
    }
  }
}

@Composable
private fun TransportBlock(
  state: PlaybackUiState,
  positionMs: Long,
  durationMs: Long,
  repeatMode: RepeatMode,
  shuffleEnabled: Boolean,
  onToggle: () -> Unit,
  onNext: () -> Unit,
  onPrevious: () -> Unit,
  onSeek: (Long) -> Unit,
  onSeekForward: () -> Unit,
  onSeekBack: () -> Unit,
  onCycleRepeat: () -> Unit,
  onToggleShuffle: () -> Unit,
  onRetry: () -> Unit,
  secondaryLabel: String? = null,
  onSecondary: (() -> Unit)? = null,
) {
  if (state is PlaybackUiState.Error) {
    ScreenScaffold(
      state = ScreenState.Unavailable(state.message),
      loadingText = "",
      onRetry = onRetry,
      secondaryLabel = secondaryLabel,
      onSecondary = onSecondary,
    ) {}
    return
  }

  var dragging by remember { mutableStateOf(false) }
  var dragValue by remember { mutableFloatStateOf(0f) }
  val range = 0f..maxOf(durationMs.toFloat(), 1f)
  val sliderValue = (if (dragging) dragValue else positionMs.toFloat()).coerceIn(range)

  Column(Modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        formatMs(if (dragging) dragValue.toLong() else positionMs),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.width(44.dp),
      )
      Slider(
        value = sliderValue,
        onValueChange = {
          dragging = true
          dragValue = it
        },
        onValueChangeFinished = {
          onSeek(dragValue.toLong())
          dragging = false
        },
        valueRange = range,
        enabled = durationMs > 0,
        modifier = Modifier.weight(1f),
      )
      Text(
        formatMs(durationMs),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.width(44.dp),
      )
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.Center,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(onClick = onToggleShuffle) {
        Icon(
          imageVector = Icons.Filled.Shuffle,
          contentDescription = stringResource(
            if (shuffleEnabled) R.string.transport_shuffle_on else R.string.transport_shuffle_off,
          ),
          tint = if (shuffleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      IconButton(onClick = onPrevious, enabled = state != PlaybackUiState.Loading) {
        Icon(Icons.Filled.SkipPrevious, contentDescription = stringResource(R.string.transport_previous))
      }
      IconButton(
        onClick = onToggle,
        enabled = state == PlaybackUiState.Playing || state == PlaybackUiState.Paused ||
          state == PlaybackUiState.Idle,
        modifier = Modifier.size(64.dp),
      ) {
        Icon(
          imageVector = if (state == PlaybackUiState.Playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
          contentDescription = stringResource(
            if (state == PlaybackUiState.Playing) R.string.transport_pause else R.string.transport_play,
          ),
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(40.dp),
        )
      }
      IconButton(onClick = onNext, enabled = state != PlaybackUiState.Loading) {
        Icon(Icons.Filled.SkipNext, contentDescription = stringResource(R.string.transport_next))
      }
      IconButton(onClick = onCycleRepeat) {
        Icon(
          imageVector = if (repeatMode == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
          contentDescription = stringResource(R.string.transport_repeat, repeatMode.name),
          tint = if (repeatMode == RepeatMode.OFF) {
            MaterialTheme.colorScheme.onSurfaceVariant
          } else {
            MaterialTheme.colorScheme.primary
          },
        )
      }
    }
  }
}

/** Copies diagnostics for a bug report — no adb needed on the reporter's side. */
private fun copyDiagnostics(context: Context, details: String) {
  val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
  clipboard.setPrimaryClip(ClipData.newPlainText("rayik diagnostics", details))
  Toast.makeText(context, "Details copied — paste them into your report", Toast.LENGTH_SHORT).show()
}

@Composable
private fun QueueRow(
  item: QueueRow,
  isPlaying: Boolean,
  onClick: () -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(12.dp))
      .clickable(onClick = onClick)
      .padding(MaterialTheme.spacing.small),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    TrackArt(artworkUrl = item.artworkUrl, modifier = Modifier.size(44.dp))
    Spacer(Modifier.width(MaterialTheme.spacing.medium))
    Column(Modifier.weight(1f)) {
      Text(
        item.title,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = if (item.isCurrent) FontWeight.SemiBold else FontWeight.Normal,
        color = if (item.isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
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
    if (isPlaying) {
      Text(
        "♪",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.primary,
      )
    }
  }
}
