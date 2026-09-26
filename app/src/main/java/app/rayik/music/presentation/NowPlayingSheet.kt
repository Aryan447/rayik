package app.rayik.music.presentation

import android.content.Context
import android.content.Intent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
 * Immersive full-screen player: ambient blurred artwork, glowing hero art,
 * glass control dock, synced lyric pill, glass lyrics + Up next.
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
  val queueTitle by player.queueTitle.collectAsState()
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
  val scheme = MaterialTheme.colorScheme
  val surface = scheme.surface
  val primary = scheme.primary

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .fillMaxHeight(0.94f)
      .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)),
  ) {
    // ---- Ambient background: artwork sharp on top, melting into blur ----
    if (artwork.isNotBlank()) {
      // Blurred base covering the whole sheet.
      AsyncImage(
        model = artwork,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
          .matchParentSize()
          .blur(72.dp),
      )
      // Sharp twin on top, masked out toward the controls so the art
      // dissolves gradiently into the blur instead of cutting off.
      AsyncImage(
        model = artwork,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
          .matchParentSize()
          .graphicsLayer {
            compositingStrategy = CompositingStrategy.Offscreen
          }
          .drawWithContent {
            drawContent()
            drawRect(
              brush = Brush.verticalGradient(
                0f to Color.Black,
                0.40f to Color.Black,
                0.68f to Color.Transparent,
              ),
              blendMode = BlendMode.DstIn,
            )
          },
      )
    } else {
      Box(
        Modifier.matchParentSize().background(
          Brush.verticalGradient(
            0f to primary.copy(alpha = 0.35f),
            1f to surface,
          ),
        ),
      )
    }
    // Color bloom orbs for depth — premium mesh feel
    Box(
      Modifier.matchParentSize().background(
        Brush.radialGradient(
          0f to primary.copy(alpha = 0.38f),
          0.55f to Color.Transparent,
          center = androidx.compose.ui.geometry.Offset(200f, 120f),
          radius = 900f,
        ),
      ),
    )
    Box(
      Modifier.matchParentSize().background(
        Brush.radialGradient(
          0f to scheme.tertiary.copy(alpha = 0.28f),
          0.6f to Color.Transparent,
          center = androidx.compose.ui.geometry.Offset(900f, 1500f),
          radius = 1100f,
        ),
      ),
    )
    // Readability scrim over the blur
    Box(
      Modifier.matchParentSize().background(
        Brush.verticalGradient(
          0f to surface.copy(alpha = 0.42f),
          0.38f to surface.copy(alpha = 0.72f),
          0.7f to surface.copy(alpha = 0.92f),
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
          .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        item {
          Spacer(Modifier.height(10.dp))
          Box(
            Modifier
              .width(42.dp)
              .height(5.dp)
              .clip(CircleShape)
              .background(scheme.onSurfaceVariant.copy(alpha = 0.45f)),
          )
          Spacer(Modifier.height(10.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            GlassIconButton(onClick = onCollapse) {
              Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = stringResource(R.string.action_collapse),
              )
            }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text(
                stringResource(R.string.player_now_playing).uppercase(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.2.sp,
                color = scheme.onSurfaceVariant,
              )
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 4.dp),
              ) {
                LiveDot(isPlaying = playbackState == PlaybackUiState.Playing)
                Text(
                  if (playbackState == PlaybackUiState.Playing) "LIVE MIX" else "RAYIK",
                  style = MaterialTheme.typography.labelSmall,
                  color = scheme.onSurfaceVariant.copy(alpha = 0.8f),
                  fontWeight = FontWeight.SemiBold,
                  letterSpacing = 1.4.sp,
                )
              }
            }
            Spacer(Modifier.weight(1f))
            GlassIconButton(
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
          Spacer(Modifier.height(18.dp))
          if (current == null) {
            ScreenScaffold(state = ScreenState.Loading, loadingText = "", onRetry = {}) {}
          } else {
            HeroArtwork(artwork = artwork)
          }
          Spacer(Modifier.height(20.dp))
        }

        item {
          if (current != null) {
            Text(
              current.title,
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.ExtraBold,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
              textAlign = TextAlign.Center,
              modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Text(
              current.artist,
              style = MaterialTheme.typography.bodyLarge,
              color = scheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              textAlign = TextAlign.Center,
              modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            // Floating action chips: like pops, share/queue sit in glass
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.Center,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              LikePill(
                isLiked = isLiked,
                onToggle = player::toggleLike,
              )
              Spacer(Modifier.width(10.dp))
              GlassPill(
                onClick = {
                  shareTrack(context, current.title, current.artist)
                },
              ) {
                Icon(
                  Icons.Filled.Share,
                  contentDescription = stringResource(R.string.action_share),
                  modifier = Modifier.size(17.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text("Share", style = MaterialTheme.typography.labelLarge)
              }
              Spacer(Modifier.width(10.dp))
              GlassPill(
                onClick = {
                  scope.launch { listState.animateScrollToItem(UPNEXT_SECTION_INDEX) }
                },
              ) {
                Icon(
                  Icons.Filled.QueueMusic,
                  contentDescription = stringResource(R.string.action_open_queue),
                  modifier = Modifier.size(17.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text("Queue", style = MaterialTheme.typography.labelLarge)
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
            Spacer(Modifier.height(16.dp))
            LyricGlowPill(line = syncedLine)
          } else {
            Spacer(Modifier.height(12.dp))
          }
        }

        item {
          Spacer(Modifier.height(6.dp))
          SheetSlider(
            positionMs = positionMs,
            durationMs = durationMs,
            onSeek = player::seekTo,
          )
        }

        item {
          Spacer(Modifier.height(4.dp))
          ControlDock(
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
            Spacer(Modifier.height(12.dp))
            GlassCard {
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
            }
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
            onShareCard = rememberShareLyricCard(
              raw = rawLyrics,
              positionMs = positionMs,
              title = current?.title.orEmpty(),
              artist = current?.artist.orEmpty(),
              artworkUrl = artwork,
            ),
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
              fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(10.dp))
            if (rows.isNotEmpty()) {
              Surface(
                shape = CircleShape,
                color = scheme.primaryContainer.copy(alpha = 0.85f),
              ) {
                Text(
                  stringResource(R.string.player_up_next_count, rows.size),
                  style = MaterialTheme.typography.labelMedium,
                  fontWeight = FontWeight.SemiBold,
                  color = scheme.onPrimaryContainer,
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
              }
            }
          }
          Spacer(Modifier.height(MaterialTheme.spacing.small))
        }

        if (rows.isEmpty()) {
          item {
            GlassCard {
              Text(
                stringResource(R.string.queue_empty_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(18.dp),
              )
            }
          }
        } else {
          items(rows, key = { it.mediaId }) { row ->
            GlassQueueWrapper(isCurrent = row.isCurrent) {
              UpNextRow(
                item = row,
                isPlaying = row.isCurrent && playbackState == PlaybackUiState.Playing,
                onClick = { player.playWindow(row) },
              )
            }
            Spacer(Modifier.height(6.dp))
          }
        }

        if (!queueTitle.isNullOrBlank()) {
          item {
            Spacer(Modifier.height(MaterialTheme.spacing.small))
            GlassCard {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(Modifier.weight(1f)) {
                  Text(
                    stringResource(R.string.playing_from, queueTitle!!),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
                GlassIconButton(
                  onClick = {
                    scope.launch { listState.animateScrollToItem(UPNEXT_SECTION_INDEX) }
                  },
                ) {
                  Icon(
                    Icons.Filled.QueueMusic,
                    contentDescription = stringResource(R.string.action_open_queue),
                    modifier = Modifier.size(22.dp),
                  )
                }
              }
            }
          }
        }

        item {
          Spacer(Modifier.height(MaterialTheme.spacing.extraLarge))
        }
      }
    }
  }
}

// ---------- Premium pieces ----------

@Composable
private fun HeroArtwork(artwork: String) {
  val scheme = MaterialTheme.colorScheme
  Box(
    contentAlignment = Alignment.Center,
    modifier = Modifier.fillMaxWidth(),
  ) {
    // Glow: blurred twin of the art bleeding into the blur backdrop
    if (artwork.isNotBlank()) {
      AsyncImage(
        model = artwork,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
          .size(300.dp)
          .alpha(0.55f)
          .blur(48.dp),
      )
    }
    Surface(
      shape = RoundedCornerShape(32.dp),
      tonalElevation = 12.dp,
      shadowElevation = 32.dp,
      modifier = Modifier
        .size(292.dp)
        .shadow(
          40.dp,
          RoundedCornerShape(32.dp),
          spotColor = scheme.primary.copy(alpha = 0.45f),
        )
        .border(
          1.dp,
          Color.White.copy(alpha = 0.22f),
          RoundedCornerShape(32.dp),
        ),
    ) {
      Box {
        TrackArt(
          artworkUrl = artwork,
          corner = 32.dp,
          modifier = Modifier.fillMaxSize(),
        )
        // Top shine for a glassy vinyl-sleeve feel
        Box(
          Modifier
            .matchParentSize()
            .background(
              Brush.verticalGradient(
                0f to Color.White.copy(alpha = 0.16f),
                0.28f to Color.Transparent,
                0.8f to Color.Transparent,
                1f to Color.Black.copy(alpha = 0.22f),
              ),
            ),
        )
      }
    }
  }
}

@Composable
private fun GlassIconButton(
  onClick: () -> Unit,
  enabled: Boolean = true,
  content: @Composable () -> Unit,
) {
  val scheme = MaterialTheme.colorScheme
  Surface(
    onClick = onClick,
    enabled = enabled,
    shape = CircleShape,
    color = scheme.surface.copy(alpha = 0.5f),
    tonalElevation = 0.dp,
    modifier = Modifier
      .size(44.dp)
      .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape),
  ) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
      content()
    }
  }
}

@Composable
private fun GlassPill(
  onClick: () -> Unit,
  content: @Composable () -> Unit,
) {
  val scheme = MaterialTheme.colorScheme
  Surface(
    onClick = onClick,
    shape = CircleShape,
    color = scheme.surface.copy(alpha = 0.5f),
    modifier = Modifier.border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
    ) {
      content()
    }
  }
}

@Composable
private fun LikePill(
  isLiked: Boolean,
  onToggle: () -> Unit,
) {
  val scheme = MaterialTheme.colorScheme
  val container = if (isLiked) scheme.primary else scheme.surface.copy(alpha = 0.5f)
  val contentColor = if (isLiked) scheme.onPrimary else scheme.onSurfaceVariant
  Surface(
    onClick = onToggle,
    shape = CircleShape,
    color = container,
    modifier = Modifier
      .border(
        1.dp,
        if (isLiked) scheme.primary.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.16f),
        CircleShape,
      )
      .shadow(
        if (isLiked) 16.dp else 0.dp,
        CircleShape,
        spotColor = scheme.primary.copy(alpha = 0.5f),
      ),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
        contentDescription = stringResource(
          if (isLiked) R.string.action_unlike else R.string.action_like,
        ),
        tint = contentColor,
        modifier = Modifier.size(17.dp),
      )
      Spacer(Modifier.width(6.dp))
      Text(
        if (isLiked) "Liked" else "Like",
        style = MaterialTheme.typography.labelLarge,
        color = contentColor,
      )
    }
  }
}

@Composable
private fun LyricGlowPill(line: String) {
  val scheme = MaterialTheme.colorScheme
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(20.dp))
      .background(scheme.surface.copy(alpha = 0.45f))
      .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(20.dp))
      .padding(horizontal = 18.dp, vertical = 12.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      "“$line”",
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.SemiBold,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
      textAlign = TextAlign.Center,
      color = scheme.onSurface,
      modifier = Modifier.animateContentSize(),
    )
  }
}

/** Frosted card used for lyrics / error / empty states. */
@Composable
fun GlassCard(
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  Surface(
    tonalElevation = 2.dp,
    shape = RoundedCornerShape(26.dp),
    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
    modifier = modifier
      .fillMaxWidth()
      .border(1.dp, Color.White.copy(alpha = 0.13f), RoundedCornerShape(26.dp)),
  ) {
    content()
  }
}

@Composable
private fun GlassQueueWrapper(
  isCurrent: Boolean,
  content: @Composable () -> Unit,
) {
  val scheme = MaterialTheme.colorScheme
  if (isCurrent) {
    Surface(
      shape = RoundedCornerShape(18.dp),
      color = scheme.primaryContainer.copy(alpha = 0.55f),
      modifier = Modifier
        .fillMaxWidth()
        .border(1.dp, scheme.primary.copy(alpha = 0.35f), RoundedCornerShape(18.dp)),
    ) {
      content()
    }
  } else {
    Surface(
      shape = RoundedCornerShape(18.dp),
      color = scheme.surface.copy(alpha = 0.38f),
      modifier = Modifier.fillMaxWidth(),
    ) {
      content()
    }
  }
}

@Composable
private fun LiveDot(isPlaying: Boolean) {
  Box(
    Modifier
      .size(7.dp)
      .clip(CircleShape)
      .background(
        if (isPlaying) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
      ),
  )
}

@Composable
private fun SheetSlider(
  positionMs: Long,
  durationMs: Long,
  onSeek: (Long) -> Unit,
) {
  val scheme = MaterialTheme.colorScheme
  val dark = isSystemInDarkTheme()
  val activeSlider = if (dark) Color.White else scheme.primary
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
      colors = SliderDefaults.colors(
        activeTrackColor = activeSlider,
        inactiveTrackColor = activeSlider.copy(alpha = 0.28f),
        thumbColor = activeSlider,
      ),
      modifier = Modifier.fillMaxWidth(),
    )
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
      Text(
        formatMs(if (dragging) dragValue.toLong() else positionMs),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = scheme.onSurface.copy(alpha = 0.9f),
      )
      Spacer(Modifier.weight(1f))
      Text(
        formatMs(durationMs),
        style = MaterialTheme.typography.labelMedium,
        color = scheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun ControlDock(
  state: PlaybackUiState,
  repeatMode: RepeatMode,
  shuffleEnabled: Boolean,
  onToggle: () -> Unit,
  onNext: () -> Unit,
  onPrevious: () -> Unit,
  onCycleRepeat: () -> Unit,
  onToggleShuffle: () -> Unit,
) {
  val scheme = MaterialTheme.colorScheme
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    IconButton(onClick = onToggleShuffle, modifier = Modifier.size(44.dp)) {
      Icon(
        imageVector = Icons.Filled.Shuffle,
        contentDescription = stringResource(
          if (shuffleEnabled) R.string.transport_shuffle_on else R.string.transport_shuffle_off,
        ),
        tint = if (shuffleEnabled) scheme.primary else scheme.onSurfaceVariant.copy(alpha = 0.7f),
      )
    }
    Spacer(Modifier.width(10.dp))
    TransportPill(
      onClick = onPrevious,
      enabled = state != PlaybackUiState.Loading,
      containerColor = scheme.surface.copy(alpha = 0.5f),
      borderColor = Color.White.copy(alpha = 0.16f),
      modifier = Modifier.size(width = 76.dp, height = 64.dp),
    ) {
      Icon(
        Icons.Filled.SkipPrevious,
        contentDescription = stringResource(R.string.transport_previous),
        tint = scheme.onSurface,
        modifier = Modifier.size(30.dp),
      )
    }
    Spacer(Modifier.width(10.dp))
    if (state == PlaybackUiState.Loading) {
      CircularProgressIndicator(modifier = Modifier.size(76.dp))
    } else {
      TransportPill(
        onClick = onToggle,
        enabled = state == PlaybackUiState.Playing || state == PlaybackUiState.Paused ||
          state == PlaybackUiState.Idle,
        containerColor = scheme.primary,
        borderColor = scheme.primary.copy(alpha = 0.4f),
        shadowColor = scheme.primary.copy(alpha = 0.55f),
        modifier = Modifier.size(width = 116.dp, height = 76.dp),
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
          tint = scheme.onPrimary,
          modifier = Modifier.size(40.dp),
        )
      }
    }
    Spacer(Modifier.width(10.dp))
    TransportPill(
      onClick = onNext,
      enabled = state != PlaybackUiState.Loading,
      containerColor = scheme.surface.copy(alpha = 0.5f),
      borderColor = Color.White.copy(alpha = 0.16f),
      modifier = Modifier.size(width = 76.dp, height = 64.dp),
    ) {
      Icon(
        Icons.Filled.SkipNext,
        contentDescription = stringResource(R.string.transport_next),
        tint = scheme.onSurface,
        modifier = Modifier.size(30.dp),
      )
    }
    Spacer(Modifier.width(10.dp))
    IconButton(onClick = onCycleRepeat, modifier = Modifier.size(44.dp)) {
      Icon(
        imageVector = if (repeatMode == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
        contentDescription = stringResource(R.string.transport_repeat, repeatMode.name),
        tint = if (repeatMode == RepeatMode.OFF) {
          scheme.onSurfaceVariant.copy(alpha = 0.7f)
        } else {
          scheme.primary
        },
      )
    }
  }
}

@Composable
private fun TransportPill(
  onClick: () -> Unit,
  enabled: Boolean,
  containerColor: Color,
  borderColor: Color,
  shadowColor: Color = Color.Transparent,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  Surface(
    onClick = onClick,
    enabled = enabled,
    shape = RoundedCornerShape(24.dp),
    color = containerColor,
    modifier = modifier
      .shadow(20.dp, RoundedCornerShape(24.dp), spotColor = shadowColor)
      .border(1.dp, borderColor, RoundedCornerShape(24.dp)),
  ) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
      content()
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
