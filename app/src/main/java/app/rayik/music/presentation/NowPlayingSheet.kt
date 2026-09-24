package app.rayik.music.presentation

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import app.rayik.music.BuildConfig
import app.rayik.music.R
import app.rayik.music.lyrics.LrcParser
import app.rayik.music.player.PlaybackUiState
import app.rayik.music.player.PlayerViewModel
import app.rayik.music.player.RepeatMode
import app.rayik.music.player.buildPlaybackDiagnostics
import app.rayik.music.player.formatMs
import app.rayik.music.ui.theme.spacing

/** Lazy-list indices of the scroll targets; header sections above are always emitted. */
private const val LYRICS_SECTION_INDEX = 7
private const val UPNEXT_SECTION_INDEX = 8

/**
 * Immersive full-screen player: full-bleed artwork + scrim, hero art,
 * title + like, one synced lyric line, big circular play control,
 * share/queue jumps, lyrics preview card, and Up next — one surface.
 */
@Composable
fun NowPlayingSheetContent(
  onCollapse: () -> Unit,
  player: PlayerViewModel = hiltViewModel(),
) {
  val rows by player.queueRows.collectAsState()
  val playbackState by player.playbackState.collectAsState()
  val positionMs by player.positionMs.collectAsState()
  val durationMs by player.durationMs.collectAsState()
  val repeatMode by player.repeatMode.collectAsState()
  val shuffleEnabled by player.shuffleEnabled.collectAsState()
  val isLiked by player.isLiked.collectAsState()
  val connection by player.connection.collectAsState()
  val context = LocalContext.current

  val conn = connection
  val lyricsEntity by remember(conn) {
    conn?.currentLyrics ?: flowOf(null)
  }.collectAsState(initial = null)
  val rawLyrics = lyricsEntity?.lyrics

  val current = rows.firstOrNull { it.isCurrent }
  val listState = rememberLazyListState()
  val scope = rememberCoroutineScope()
  var lyricsExpanded by remember { mutableStateOf(false) }

  val artwork = current?.artworkUrl.orEmpty()
  val surface = MaterialTheme.colorScheme.surface

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .fillMaxHeight(0.94f),
  ) {
    if (artwork.isNotBlank()) {
      AsyncImage(
        model = artwork,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.matchParentSize().alpha(0.32f),
      )
    }
    Box(
      Modifier.matchParentSize().background(
        Brush.verticalGradient(
          0f to surface.copy(alpha = 0.55f),
          0.45f to surface.copy(alpha = 0.86f),
          1f to surface,
        ),
      ),
    )

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
      LazyColumn(
        state = listState,
        modifier = Modifier
          .widthIn(max = 560.dp)
          .fillMaxSize()
          .padding(horizontal = MaterialTheme.spacing.large),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        item {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            IconButton(onClick = onCollapse) {
              Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = stringResource(R.string.action_collapse),
              )
            }
            Spacer(Modifier.weight(1f))
            Text(
              stringResource(R.string.player_now_playing),
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            IconButton(
              onClick = {
                shareTrack(context, current?.title.orEmpty(), current?.artist.orEmpty())
              },
              enabled = current != null,
            ) {
              Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.action_share))
            }
          }
        }

        item {
          Spacer(Modifier.height(MaterialTheme.spacing.small))
          if (current == null) {
            ScreenScaffold(state = ScreenState.Loading, loadingText = "", onRetry = {}) {}
          } else {
            Surface(
              shape = RoundedCornerShape(24.dp),
              tonalElevation = 8.dp,
              modifier = Modifier.size(280.dp),
            ) {
              TrackArt(
                artworkUrl = artwork,
                corner = 24.dp,
                modifier = Modifier.fillMaxSize(),
              )
            }
          }
          Spacer(Modifier.height(MaterialTheme.spacing.medium))
        }

        item {
          if (current != null) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(Modifier.weight(1f)) {
                Text(
                  current.title,
                  style = MaterialTheme.typography.headlineSmall,
                  fontWeight = FontWeight.Bold,
                  maxLines = 2,
                  overflow = TextOverflow.Ellipsis,
                )
                Text(
                  current.artist,
                  style = MaterialTheme.typography.bodyLarge,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
              IconButton(
                onClick = player::toggleLike,
                modifier = Modifier.size(48.dp),
              ) {
                Icon(
                  imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                  contentDescription = stringResource(
                    if (isLiked) R.string.action_unlike else R.string.action_like,
                  ),
                  tint = if (isLiked) {
                    MaterialTheme.colorScheme.primary
                  } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                  },
                )
              }
            }
          }
        }

        item {
          val syncedLine = remember(rawLyrics, positionMs) {
            val lines = if (rawLyrics.isNullOrBlank()) emptyList() else LrcParser.parseLyrics(rawLyrics)
            val active = activeLyricIndex(lines, positionMs)
            lines.getOrNull(active)?.text.orEmpty()
          }
          if (syncedLine.isNotBlank()) {
            Text(
              "“$syncedLine”",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.SemiBold,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier
                .fillMaxWidth()
                .padding(top = MaterialTheme.spacing.small),
            )
          } else {
            Spacer(Modifier.height(MaterialTheme.spacing.small))
          }
        }

        item {
          SheetSlider(
            positionMs = positionMs,
            durationMs = durationMs,
            onSeek = player::seekTo,
          )
        }

        item {
          SheetControls(
            state = playbackState,
            repeatMode = repeatMode,
            shuffleEnabled = shuffleEnabled,
            onToggle = player::togglePlayPause,
            onNext = player::next,
            onPrevious = player::previous,
            onCycleRepeat = player::cycleRepeat,
            onToggleShuffle = player::toggleShuffle,
          )
        }

        item {
          if (playbackState is PlaybackUiState.Error) {
            ScreenScaffold(
              state = ScreenState.Unavailable(
                (playbackState as PlaybackUiState.Error).message,
              ),
              loadingText = "",
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
                    errorMessage = (playbackState as PlaybackUiState.Error).message,
                  ),
                )
              },
            ) {}
          } else {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.Center,
            ) {
              TextButton(
                onClick = {
                  scope.launch { listState.animateScrollToItem(LYRICS_SECTION_INDEX) }
                },
              ) {
                Text(stringResource(R.string.lyrics_preview_title))
              }
              TextButton(
                onClick = {
                  scope.launch { listState.animateScrollToItem(UPNEXT_SECTION_INDEX) }
                },
              ) {
                Icon(Icons.Filled.QueueMusic, contentDescription = null)
                Spacer(Modifier.width(MaterialTheme.spacing.smaller))
                Text(stringResource(R.string.action_open_queue))
              }
            }
          }
        }

        item {
          Spacer(Modifier.height(MaterialTheme.spacing.small))
          LyricsPreviewCard(
            raw = rawLyrics,
            positionMs = positionMs,
            expanded = lyricsExpanded,
            onToggleExpand = { lyricsExpanded = !lyricsExpanded },
          )
          Spacer(Modifier.height(MaterialTheme.spacing.medium))
        }

        item {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              stringResource(R.string.queue_title),
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            if (rows.isNotEmpty()) {
              Text(
                stringResource(R.string.player_up_next_count, rows.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
          Spacer(Modifier.height(MaterialTheme.spacing.small))
        }

        if (rows.isEmpty()) {
          item {
            Text(
              stringResource(R.string.queue_empty_title),
              style = MaterialTheme.typography.titleSmall,
            )
          }
        } else {
          items(rows, key = { it.mediaId }) { row ->
            UpNextRow(
              item = row,
              isPlaying = row.isCurrent && playbackState == PlaybackUiState.Playing,
              onClick = { player.playWindow(row) },
            )
          }
        }

        item {
          Spacer(Modifier.height(MaterialTheme.spacing.extraLarge))
        }
      }
    }
  }
}

@Composable
private fun SheetSlider(
  positionMs: Long,
  durationMs: Long,
  onSeek: (Long) -> Unit,
) {
  var dragging by remember { mutableStateOf(false) }
  var dragValue by remember { mutableFloatStateOf(0f) }
  val range = 0f..maxOf(durationMs.toFloat(), 1f)
  val sliderValue = (if (dragging) dragValue else positionMs.toFloat()).coerceIn(range)

  Column(Modifier.fillMaxWidth()) {
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
      modifier = Modifier.fillMaxWidth(),
    )
    Row(Modifier.fillMaxWidth()) {
      Text(
        formatMs(if (dragging) dragValue.toLong() else positionMs),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(Modifier.weight(1f))
      Text(
        formatMs(durationMs),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun SheetControls(
  state: PlaybackUiState,
  repeatMode: RepeatMode,
  shuffleEnabled: Boolean,
  onToggle: () -> Unit,
  onNext: () -> Unit,
  onPrevious: () -> Unit,
  onCycleRepeat: () -> Unit,
  onToggleShuffle: () -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = MaterialTheme.spacing.small),
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    IconButton(onClick = onToggleShuffle, modifier = Modifier.size(48.dp)) {
      Icon(
        imageVector = Icons.Filled.Shuffle,
        contentDescription = stringResource(
          if (shuffleEnabled) R.string.transport_shuffle_on else R.string.transport_shuffle_off,
        ),
        tint = if (shuffleEnabled) {
          MaterialTheme.colorScheme.primary
        } else {
          MaterialTheme.colorScheme.onSurfaceVariant
        },
      )
    }
    IconButton(
      onClick = onPrevious,
      enabled = state != PlaybackUiState.Loading,
      modifier = Modifier.size(56.dp),
    ) {
      Icon(
        Icons.Filled.SkipPrevious,
        contentDescription = stringResource(R.string.transport_previous),
        modifier = Modifier.size(36.dp),
      )
    }
    Spacer(Modifier.width(MaterialTheme.spacing.small))
    if (state == PlaybackUiState.Loading) {
      CircularProgressIndicator(modifier = Modifier.size(72.dp))
    } else {
      FilledIconButton(
        onClick = onToggle,
        enabled = state == PlaybackUiState.Playing || state == PlaybackUiState.Paused ||
          state == PlaybackUiState.Idle,
        modifier = Modifier.size(72.dp),
        colors = IconButtonDefaults.filledIconButtonColors(
          containerColor = MaterialTheme.colorScheme.primary,
          contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
      ) {
        Icon(
          imageVector = if (state == PlaybackUiState.Playing) {
            Icons.Filled.Pause
          } else {
            Icons.Filled.PlayArrow
          },
          contentDescription = stringResource(
            if (state == PlaybackUiState.Playing) {
              R.string.transport_pause
            } else {
              R.string.transport_play
            },
          ),
          modifier = Modifier.size(40.dp),
        )
      }
    }
    Spacer(Modifier.width(MaterialTheme.spacing.small))
    IconButton(
      onClick = onNext,
      enabled = state != PlaybackUiState.Loading,
      modifier = Modifier.size(56.dp),
    ) {
      Icon(
        Icons.Filled.SkipNext,
        contentDescription = stringResource(R.string.transport_next),
        modifier = Modifier.size(36.dp),
      )
    }
    IconButton(onClick = onCycleRepeat, modifier = Modifier.size(48.dp)) {
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

private fun shareTrack(context: Context, title: String, artist: String) {
  if (title.isBlank()) return
  val text = if (artist.isBlank()) title else "$title — $artist"
  val send = Intent(Intent.ACTION_SEND).apply {
    type = "text/plain"
    putExtra(Intent.EXTRA_TEXT, text)
  }
  context.startActivity(Intent.createChooser(send, null))
}
