package app.rayik.music.presentation

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import app.rayik.music.BuildConfig
import app.rayik.music.R
import app.rayik.music.player.PlaybackUiState
import app.rayik.music.player.PlayerViewModel
import app.rayik.music.player.RepeatMode
import app.rayik.music.player.buildPlaybackDiagnostics
import app.rayik.music.player.formatMs
import app.rayik.music.preferences.StreamQuality
import app.rayik.music.preferences.preference.collectAsState
import app.rayik.music.ui.theme.AppShapes
import app.rayik.music.ui.theme.BrandGradient
import app.rayik.music.ui.theme.RayikIcons
import app.rayik.music.ui.theme.spacing

/** Lazy-list indices of the scroll targets; header sections above are always emitted. */
private const val LYRICS_SECTION_INDEX = 6
private const val UPNEXT_SECTION_INDEX = 7

/**
 * Art stage height: header floats over the cover, the title lands in the
 * lower scrim, and slider + transport sit on the melt into surface.
 */
private val StageHeight = 600.dp

/**
 * Immersive full-screen player: full-bleed cover art, glass control dock,
 * synced lyric pill, glass lyrics + Up next.
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
  var immersiveLyrics by remember { mutableStateOf(false) }
  val prefs = rayikPreferences()
  val streamQuality by prefs.streamQuality.collectAsState()

  val artwork = current?.artworkUrl.orEmpty()
  val scheme = MaterialTheme.colorScheme
  val surface = scheme.surface
  // Side inset for every item except the full-bleed cover.
  val contentInset = 20.dp

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .fillMaxHeight(0.94f)
      .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)),
  ) {
    // Apple Music stage: sharp cover art bleeds edge to edge behind the
    // header and controls, then melts into the sheet surface. Theme scrims
    // top and bottom keep the grab pill and title legible on any art.
    val backdrop = artwork.ifBlank { publicArtFallback(current?.mediaId.orEmpty()) }
    // Surface under everything so the lyrics/queue region below the art is
    // always on-theme, even before art loads.
    Box(Modifier.matchParentSize().background(surface)) {}
    if (backdrop.isNotBlank()) {
      AsyncImage(
        model = backdrop,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
          .fillMaxWidth()
          .height(StageHeight)
          .align(Alignment.TopCenter)
          .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)),
      )
      Box(
        Modifier
          .fillMaxWidth()
          .height(StageHeight)
          .align(Alignment.TopCenter)
          .background(
            Brush.verticalGradient(
              0f to surface.copy(alpha = 0.55f),
              0.22f to Color.Transparent,
              0.55f to Color.Transparent,
              0.85f to surface.copy(alpha = 0.88f),
              1f to surface,
            ),
          ),
      )
    } else {
      // No art, no hue: a neutral lift so the header band can never clash
      // the way a primary wash did.
      Box(
        Modifier.matchParentSize().background(
          Brush.verticalGradient(
            0f to scheme.surfaceContainerHighest,
            1f to surface,
          ),
        ),
      )
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
      LazyColumn(
        state = listState,
        modifier = Modifier
          .widthIn(max = 560.dp)
          .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        item {
          Box(Modifier.fillMaxWidth().padding(horizontal = contentInset)) {
          Spacer(Modifier.statusBarsPadding().height(10.dp))
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
                Icons.Outlined.KeyboardArrowDown,
                contentDescription = stringResource(R.string.action_collapse),
              )
            }
            Spacer(Modifier.weight(1f))
            // Keep the source label quiet; omit it when the queue has no name.
            if (!queueTitle.isNullOrBlank()) {
              Text(
                stringResource(R.string.playing_from, queueTitle!!),
                style = MaterialTheme.typography.titleSmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(2f),
                textAlign = TextAlign.Center,
              )
            } else {
              Spacer(Modifier.weight(2f))
            }
            Spacer(Modifier.weight(1f))
            GlassIconButton(
              onClick = {
                shareTrack(context, current?.title.orEmpty(), current?.artist.orEmpty())
              },
              enabled = current != null,
            ) {
              Icon(RayikIcons.Share, contentDescription = stringResource(R.string.action_share))
            }
          }
          }
        }

        // Clear stage: the cover behind carries this space, like the
        // Apple Music player — no card stacked on top of the artwork.
        item {
          if (current == null) {
            ScreenScaffold(state = ScreenState.Loading, loadingText = "", onRetry = {}) {}
          } else {
            Spacer(Modifier.height(240.dp))
          }
        }

        item {
          Box(Modifier.fillMaxWidth().padding(horizontal = contentInset)) {
          if (current != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Column(Modifier.weight(1f)) {
                Text(
                  current.title,
                  style = MaterialTheme.typography.titleLarge,
                  fontWeight = FontWeight.Bold,
                  maxLines = 2,
                  overflow = TextOverflow.Ellipsis,
                  textAlign = TextAlign.Start,
                  modifier = Modifier.fillMaxWidth(),
                )
                Text(
                  current.artist,
                  style = MaterialTheme.typography.bodyMedium,
                  color = scheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  textAlign = TextAlign.Start,
                  modifier = Modifier.fillMaxWidth(),
                )
              }
              Spacer(Modifier.width(MaterialTheme.spacing.small))
              // Icon-only actions: the Like pill crowded the title and clipped
              // the artist. Same toggles, a fraction of the width.
              GlassIconButton(onClick = player::toggleLike) {
                Icon(
                  imageVector = if (isLiked) RayikIcons.HeartFilled else RayikIcons.Heart,
                  contentDescription = stringResource(
                    if (isLiked) R.string.action_unlike else R.string.action_like,
                  ),
                  tint = if (isLiked) scheme.primary else scheme.onSurfaceVariant,
                  modifier = Modifier.size(20.dp),
                )
              }
              Spacer(Modifier.width(8.dp))
              GlassIconButton(
                onClick = { shareTrack(context, current.title, current.artist) },
              ) {
                Icon(
                  RayikIcons.Share,
                  contentDescription = stringResource(R.string.action_share),
                  modifier = Modifier.size(20.dp),
                )
              }
            }
          }
          }
        }

        item {
          Box(Modifier.fillMaxWidth().padding(horizontal = contentInset)) {
          Spacer(Modifier.height(6.dp))
          SheetSlider(
            positionMs = positionMs,
            durationMs = durationMs,
            onSeek = player::seekTo,
          )
          }
        }

        item {
          Box(Modifier.fillMaxWidth().padding(horizontal = contentInset)) {
          Spacer(Modifier.height(16.dp))
          ControlDock(
            state = playbackState,
            repeatMode = repeatMode,
            shuffleEnabled = shuffleEnabled,
            // On error the hero pill retries instead of toggling — the dock
            // never sits dead with a disabled button and no way forward.
            onToggle = {
              if (playbackState is PlaybackUiState.Error) player.retry()
              else player.togglePlayPause()
            },
            onNext = player::next,
            onPrevious = player::previous,
            onCycleRepeat = player::cycleRepeat,
            onToggleShuffle = player::toggleShuffle,
          )
          }
        }

        item {
          Box(Modifier.fillMaxWidth().padding(horizontal = contentInset)) {
          if (playbackState is PlaybackUiState.Error) {
            Spacer(Modifier.height(12.dp))
            GlassCard {
              ScreenScaffold(
                state = ScreenState.Unavailable(
                  (playbackState as PlaybackUiState.Error).message,
                ),
                loadingText = "",
                onRetry = player::retry,
                secondaryLabel = stringResource(R.string.common_copy_details),
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
                Icon(RayikIcons.Queue, contentDescription = null)
                Spacer(Modifier.width(MaterialTheme.spacing.smaller))
                Text(stringResource(R.string.action_open_queue))
              }
            }
          }
          }
        }

        item {
          Box(Modifier.fillMaxWidth().padding(horizontal = contentInset)) {
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
            onOpenImmersive = { immersiveLyrics = true },
            onSeek = player::seekTo,
          )
          Spacer(Modifier.height(MaterialTheme.spacing.medium))
          }
        }

        item {
          Box(Modifier.fillMaxWidth().padding(horizontal = contentInset)) {
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
            Spacer(Modifier.weight(1f))
            QualityMenu(
              quality = streamQuality,
              onSelect = {
                prefs.streamQuality.set(it)
                scope.launch(Dispatchers.IO) { context.mirrorStreamQualityChoice(it) }
              },
            )
          }
          Spacer(Modifier.height(MaterialTheme.spacing.small))
          }
        }

        if (rows.isEmpty()) {
          item {
            Box(Modifier.fillMaxWidth().padding(horizontal = contentInset)) {
            GlassCard {
              Text(
                stringResource(R.string.queue_empty_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(18.dp),
              )
            }
            }
          }
        } else {
          items(rows, key = { it.mediaId }) { row ->
            Box(Modifier.fillMaxWidth().padding(horizontal = contentInset)) {
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
        }

        if (!queueTitle.isNullOrBlank()) {
          item {
            Box(Modifier.fillMaxWidth().padding(horizontal = contentInset)) {
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
                    RayikIcons.Queue,
                    contentDescription = stringResource(R.string.action_open_queue),
modifier = Modifier.size(22.dp),
                )
              }
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

    // Animated in AND out: the overlay stays composed through exit so
    // closing glides back to the player instead of blinking away.
    AnimatedVisibility(
      visible = immersiveLyrics,
      enter = fadeIn(animationSpec = tween(350)) +
        scaleIn(initialScale = 0.96f, animationSpec = tween(350, easing = FastOutSlowInEasing)) +
        slideInVertically(initialOffsetY = { it / 12 }, animationSpec = tween(350, easing = FastOutSlowInEasing)),
      exit = fadeOut(animationSpec = tween(300)) +
        scaleOut(targetScale = 0.97f, animationSpec = tween(300, easing = FastOutSlowInEasing)) +
        slideOutVertically(targetOffsetY = { it / 10 }, animationSpec = tween(300, easing = FastOutSlowInEasing)),
    ) {
      Surface(Modifier.fillMaxSize()) {
        ImmersiveLyrics(
          raw = rawLyrics,
          positionMs = positionMs,
          artworkUrl = artwork,
          fallbackUrl = publicArtFallback(current?.mediaId.orEmpty()),
          title = current?.title.orEmpty(),
          artist = current?.artist.orEmpty(),
          onClose = { immersiveLyrics = false },
          isPlaying = playbackState == PlaybackUiState.Playing,
          onSeek = player::seekTo,
        )
      }
    }
  }
}

// ---------- Premium pieces ----------

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
      .border(1.dp, BrandGradient.hairline(), AppShapes.pill),
  ) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
      content()
    }
  }
}

/**
 * In-player streaming quality: compact glass chip trailing the Up-next
 * header, opening the same Saver / Auto / High choice as Settings.
 * Writes through the same preference + legacy-key mirror.
 */
@Composable
private fun QualityMenu(
  quality: StreamQuality,
  onSelect: (StreamQuality) -> Unit,
) {
  val scheme = MaterialTheme.colorScheme
  var expanded by remember { mutableStateOf(false) }
  Box {
    Surface(
      onClick = { expanded = true },
      shape = CircleShape,
      color = scheme.surface.copy(alpha = 0.5f),
      modifier = Modifier.border(1.dp, BrandGradient.hairline(), AppShapes.pill),
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(
          Icons.Outlined.HighQuality,
          contentDescription = stringResource(R.string.pref_quality_label),
          modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
          stringResource(quality.titleRes),
          style = MaterialTheme.typography.labelMedium,
        )
      }
    }
    DropdownMenu(
      expanded = expanded,
      onDismissRequest = { expanded = false },
      shape = AppShapes.cardShape,
    ) {
      StreamQuality.entries.forEach { option ->
        DropdownMenuItem(
          text = { Text(stringResource(option.titleRes)) },
          onClick = {
            expanded = false
            onSelect(option)
          },
          trailingIcon = if (option == quality) {
            {
              Icon(
                Icons.Outlined.Check,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
            }
          } else {
            null
          },
        )
      }
    }
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
    shape = AppShapes.cardShape,
    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
    modifier = modifier
      .fillMaxWidth()
      .border(1.dp, BrandGradient.hairline(), AppShapes.cardShape),
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
      shape = AppShapes.cardShape,
      color = scheme.primaryContainer.copy(alpha = 0.55f),
      modifier = Modifier
        .fillMaxWidth()
        .border(1.dp, scheme.primary.copy(alpha = 0.35f), AppShapes.cardShape),
    ) {
      content()
    }
  } else {
    Surface(
      shape = AppShapes.cardShape,
      color = scheme.surface.copy(alpha = 0.38f),
      modifier = Modifier.fillMaxWidth(),
    ) {
      content()
    }
  }
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
      // Apple Music: bare bar, no resting thumb — the knob only shows while
      // dragging, so it never sits on top of the artwork's focal point.
      thumb = {
        if (dragging) {
          Box(
            Modifier
              .size(14.dp)
              .shadow(6.dp, CircleShape)
              .clip(CircleShape)
              .background(activeSlider),
          )
        }
      },
      track = {
        val fraction = if (range.endInclusive > range.start) {
          ((sliderValue - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
        } else {
          0f
        }
        Box(
          Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(AppShapes.pill)
            .background(activeSlider.copy(alpha = 0.28f)),
        ) {
          Box(
            Modifier
              .fillMaxWidth(fraction)
              .fillMaxHeight()
              .clip(AppShapes.pill)
              .background(activeSlider)
              .align(Alignment.CenterStart),
          )
        }
      },
      modifier = Modifier.fillMaxWidth(),
    )
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
      // Tabular figures in fixed slots so elapsed/remaining never wobble the
      // row as digits change.
      val tabular = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum")
      Text(
        formatMs(if (dragging) dragValue.toLong() else positionMs),
        style = tabular,
        fontWeight = FontWeight.SemiBold,
        color = scheme.onSurface.copy(alpha = 0.9f),
        modifier = Modifier.widthIn(min = 52.dp),
      )
      Spacer(Modifier.weight(1f))
      // Apple Music counts down, not up to the end.
      val elapsed = (if (dragging) dragValue.toLong() else positionMs).coerceAtLeast(0L)
      Text(
        "-${formatMs((durationMs - elapsed).coerceAtLeast(0L))}",
        style = tabular,
        color = scheme.onSurfaceVariant,
        textAlign = TextAlign.End,
        modifier = Modifier.widthIn(min = 64.dp),
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
  // Apple Music transport: bare icons, no pills or surfaces. Prev, play and
  // next share one 64dp slot and one 40dp glyph so the row is symmetric by
  // construction instead of by eyeballed spacers.
  // Fixed total is 360dp — wider than a 360dp phone minus the 20dp side
  // insets, so the row used to center itself off the edges and clip the
  // repeat (and shuffle) button. Gaps flex down instead; capped so wide
  // screens keep the same 18dp rhythm.
  val scheme = MaterialTheme.colorScheme
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    IconButton(onClick = onToggleShuffle, modifier = Modifier.size(48.dp)) {
      Icon(
        imageVector = RayikIcons.Shuffle,
        contentDescription = stringResource(
          if (shuffleEnabled) R.string.transport_shuffle_on else R.string.transport_shuffle_off,
        ),
        tint = if (shuffleEnabled) scheme.primary else scheme.onSurfaceVariant.copy(alpha = 0.7f),
        modifier = Modifier.size(26.dp),
      )
    }
    DockGap()
    IconButton(
      onClick = onPrevious,
      enabled = state != PlaybackUiState.Loading,
      modifier = Modifier.size(64.dp),
    ) {
      Icon(
        RayikIcons.Previous,
        contentDescription = stringResource(R.string.transport_previous),
        tint = scheme.onSurface,
        modifier = Modifier.size(40.dp),
      )
    }
    DockGap()
    if (state == PlaybackUiState.Loading) {
      BarLoader(modifier = Modifier.size(width = 64.dp, height = 64.dp))
    } else {
      IconButton(
        onClick = onToggle,
        enabled = state == PlaybackUiState.Playing || state == PlaybackUiState.Paused ||
          state == PlaybackUiState.Idle || state is PlaybackUiState.Error,
        modifier = Modifier.size(64.dp),
      ) {
        Icon(
          imageVector = if (state == PlaybackUiState.Playing) {
            RayikIcons.Pause
          } else {
            RayikIcons.Play
          },
          contentDescription = stringResource(
            if (state == PlaybackUiState.Playing) {
              R.string.transport_pause
            } else {
              R.string.transport_play
            },
          ),
          tint = scheme.onSurface,
          modifier = Modifier.size(40.dp),
        )
      }
    }
    DockGap()
    IconButton(
      onClick = onNext,
      enabled = state != PlaybackUiState.Loading,
      modifier = Modifier.size(64.dp),
    ) {
      Icon(
        RayikIcons.Next,
        contentDescription = stringResource(R.string.transport_next),
        tint = scheme.onSurface,
        modifier = Modifier.size(40.dp),
      )
    }
    DockGap()
    IconButton(onClick = onCycleRepeat, modifier = Modifier.size(48.dp)) {
      Icon(
        imageVector = if (repeatMode == RepeatMode.ONE) RayikIcons.RepeatOne else RayikIcons.Repeat,
        contentDescription = stringResource(R.string.transport_repeat, repeatMode.name),
        tint = if (repeatMode == RepeatMode.OFF) {
          scheme.onSurfaceVariant.copy(alpha = 0.7f)
        } else {
          scheme.primary
        },
        modifier = Modifier.size(26.dp),
      )
    }
  }
}

/** Flexible transport gap: 18dp rhythm on wide screens, shrinks so the
 * 360dp dock never clips shuffle/repeat on narrow phones. */
@Composable
private fun RowScope.DockGap() {
  Spacer(Modifier.weight(1f).widthIn(max = 18.dp))
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
