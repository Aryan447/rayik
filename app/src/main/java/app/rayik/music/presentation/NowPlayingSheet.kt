package app.rayik.music.presentation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.OpenInFull
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import app.rayik.music.BuildConfig
import app.rayik.music.R
import app.rayik.music.lyrics.LyricDisplayParser
import app.rayik.music.player.PlaybackUiState
import app.rayik.music.player.PlayerViewModel
import app.rayik.music.player.RepeatMode
import app.rayik.music.player.buildPlaybackDiagnostics
import app.rayik.music.player.formatMs
import app.rayik.music.preferences.StreamQuality
import app.rayik.music.preferences.preference.collectAsState
import app.rayik.music.ui.theme.spacing

/** Lazy-list indices of the scroll targets; header sections above are always emitted. */
private const val LYRICS_SECTION_INDEX = 7
private const val UPNEXT_SECTION_INDEX = 8

/**
 * Immersive full-screen player: the track artwork IS the screen —
 * full-bleed and sharp, with scrims top (status bar) and bottom
 * (title + transport). Title, slider, transport and pills float on the
 * art; lyrics + Up next live below the fold. Glass lyrics overlay kept.
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

  // The player is always a dark room over artwork: force light status +
  // nav icons while open, restore the theme-driven values on close.
  val view = LocalView.current
  DisposableEffect(view) {
    var ctx: Context? = view.context
    while (ctx is ContextWrapper && ctx !is Activity) ctx = ctx.baseContext
    val controller = (ctx as? Activity)?.window?.let { WindowCompat.getInsetsController(it, view) }
    val prevStatus = controller?.isAppearanceLightStatusBars
    val prevNav = controller?.isAppearanceLightNavigationBars
    controller?.isAppearanceLightStatusBars = false
    controller?.isAppearanceLightNavigationBars = false
    onDispose {
      prevStatus?.let { controller.isAppearanceLightStatusBars = it }
      prevNav?.let { controller.isAppearanceLightNavigationBars = it }
    }
  }

  // Drag-to-dismiss (replaces the old bottom-sheet gesture): the header
  // zone tracks the finger; fling down or drag past threshold collapses.
  val dragOffset = remember { Animatable(0f) }
  val density = LocalDensity.current
  val dismissThresholdPx = with(density) { 120.dp.toPx() }
  val headerDrag = Modifier.pointerInput(onCollapse) {
    val tracker = VelocityTracker()
    detectVerticalDragGestures(
      onDragStart = { tracker.resetTracking() },
      onDragCancel = { scope.launch { dragOffset.animateTo(0f) } },
      onDragEnd = {
        scope.launch {
          if (dragOffset.value > dismissThresholdPx ||
            tracker.calculateVelocity().y > 1200f
          ) {
            onCollapse()
          } else {
            dragOffset.animateTo(0f)
          }
        }
      },
      onVerticalDrag = { change, dragAmount ->
        change.consume()
        tracker.addPosition(change.uptimeMillis, change.position)
        scope.launch { dragOffset.snapTo(maxOf(0f, dragOffset.value + dragAmount)) }
      },
    )
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      // Opaque bedrock: the layer stack must never sum to transparent or
      // the home feed ghosts through mid-screen (seen between the sharp
      // art's bottom edge and the blur zone).
      .background(Color.Black)
      .graphicsLayer { translationY = dragOffset.value },
  ) {
    // ---- Artwork atmosphere, Apple-style: sharp fit-width composition up
    // top (never side-cropped), blurred continuation dissolving below.
    // Crossfade across tracks is the transition; same Coil URL/cache as
    // TrackArt, blurred copy downsampled so the blur stays cheap.
    // NOTE: Crossfade must own a real size (fillMaxSize) — a bare one
    // collapses to 0x0 and the art silently disappears.
    Crossfade(
      targetState = artwork,
      label = "artBackdrop",
      modifier = Modifier.fillMaxSize(),
    ) { art ->
      if (art.isNotBlank()) {
        Box(Modifier.fillMaxSize()) {
          // Blur-fill: full-screen crop behind, fading in toward the
          // bottom via a DstIn mask — the reference dissolve. The mask
          // reaches full strength well above the sharp art's bottom edge
          // on any aspect, so no transparent band can open up mid-screen.
          AsyncImage(
            model = ImageRequest.Builder(context)
              .data(art)
              .size(Size(360, 640))
              .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
              .fillMaxSize()
              .blur(56.dp)
              .drawWithContent {
                drawContent()
                drawRect(
                  brush = Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.30f to Color.Transparent,
                    0.50f to Color.Black,
                  ),
                  blendMode = BlendMode.DstIn,
                )
              },
          )
          // Sharp composition: fit-width, top-anchored, full width visible.
          AsyncImage(
            model = art,
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            alignment = Alignment.TopCenter,
            modifier = Modifier.fillMaxWidth(),
          )
        }
      } else {
        Box(Modifier.fillMaxSize().background(surface))
      }
    }
    // Top scrim: status bar + top controls over bright art.
    Box(
      Modifier.matchParentSize().background(
        Brush.verticalGradient(
          0f to Color.Black.copy(alpha = 0.45f),
          0.22f to Color.Transparent,
        ),
      ),
    )
    // Bottom scrim: gentle dim for controls — the blur already darkens.
    Box(
      Modifier.matchParentSize().background(
        Brush.verticalGradient(
          0.50f to Color.Transparent,
          0.78f to Color.Black.copy(alpha = 0.25f),
          1f to Color.Black.copy(alpha = 0.55f),
        ),
      ),
    )

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
      LazyColumn(
        state = listState,
        modifier = Modifier
          .widthIn(max = 560.dp)
          .fillMaxSize()
          .navigationBarsPadding()
          .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        item {
          Spacer(Modifier.statusBarsPadding().height(10.dp))
          Box(
            Modifier
              .width(42.dp)
              .height(5.dp)
              .clip(CircleShape)
              .background(Color.White.copy(alpha = 0.55f))
              .then(headerDrag),
          )
          Spacer(Modifier.height(10.dp))
          Row(
            modifier = Modifier.fillMaxWidth().then(headerDrag),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            ArtCircleButton(onClick = onCollapse) {
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
                color = Color.White,
              )
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 4.dp),
              ) {
                LiveDot(isPlaying = playbackState == PlaybackUiState.Playing)
                Text(
                  current?.artist?.takeIf { it.isNotBlank() }?.uppercase() ?: "RAYIK",
                  style = MaterialTheme.typography.labelSmall,
                  color = Color.White.copy(alpha = 0.8f),
                  fontWeight = FontWeight.SemiBold,
                  letterSpacing = 1.4.sp,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }
            Spacer(Modifier.weight(1f))
            ArtCircleButton(
              onClick = {
                shareTrack(context, current?.title.orEmpty(), current?.artist.orEmpty())
              },
              enabled = current != null,
            ) {
              Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.action_share))
            }
          }
        }

        // No hero card — the sharp fit-width art above IS the hero. This
        // spacer drops the lyric pill + title onto the blur dissolve.
        item {
          Spacer(Modifier.fillParentMaxHeight(0.34f))
        }

        item {
          if (current == null) {
            ScreenScaffold(state = ScreenState.Loading, loadingText = "", onRetry = {}) {}
          } else {
            LyricPill(
              raw = rawLyrics,
              positionMs = positionMs,
              isPlaying = playbackState == PlaybackUiState.Playing,
              onOpenImmersive = { immersiveLyrics = true },
            )
            Spacer(Modifier.height(14.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(Modifier.weight(1f)) {
                Text(
                  current.title,
                  style = MaterialTheme.typography.headlineMedium,
                  fontWeight = FontWeight.ExtraBold,
                  maxLines = 2,
                  overflow = TextOverflow.Ellipsis,
                  color = Color.White,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                  current.artist,
                  style = MaterialTheme.typography.bodyLarge,
                  color = Color.White.copy(alpha = 0.75f),
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
              Spacer(Modifier.width(12.dp))
              ArtCircleButton(onClick = player::toggleLike) {
                Icon(
                  imageVector = if (isLiked) Icons.Filled.Check else Icons.Filled.Add,
                  contentDescription = stringResource(
                    if (isLiked) R.string.action_unlike else R.string.action_like,
                  ),
                )
              }
            }
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

        item {
          if (current != null) {
            Spacer(Modifier.height(16.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              LikePill(
                isLiked = isLiked,
                onToggle = player::toggleLike,
              )
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
            onOpenImmersive = { immersiveLyrics = true },
            onSeek = player::seekTo,
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
          onClose = { immersiveLyrics = false },
          isPlaying = playbackState == PlaybackUiState.Playing,
          onSeek = player::seekTo,
        )
      }
    }
  }
}

// ---------- Premium pieces ----------

/**
 * Thin white outline circle for controls floating directly on artwork
 * (collapse, share, add). Below-the-fold cards keep scheme-tinted
 * [GlassIconButton] instead — white icons die on light surfaces.
 */
@Composable
private fun ArtCircleButton(
  onClick: () -> Unit,
  enabled: Boolean = true,
  size: Dp = 48.dp,
  content: @Composable () -> Unit,
) {
  Surface(
    onClick = onClick,
    enabled = enabled,
    shape = CircleShape,
    color = Color.White.copy(alpha = 0.14f),
    tonalElevation = 0.dp,
    modifier = Modifier
      .size(size)
      .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape),
  ) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
      CompositionLocalProvider(LocalContentColor provides Color.White) {
        content()
      }
    }
  }
}

/**
 * Synced lyric pill floating above the title, like the reference: the
 * active line in a frosted bar, expand opens the immersive lyrics.
 * Hidden when there are no timed lines — the below-fold card still shows
 * plain/unsynced lyrics. Reuses the preview's parser + smooth clock.
 */
@Composable
private fun LyricPill(
  raw: String?,
  positionMs: Long,
  isPlaying: Boolean,
  onOpenImmersive: () -> Unit,
  modifier: Modifier = Modifier,
) {
  if (raw.isNullOrBlank() || raw == "LYRICS_NOT_FOUND") return
  val lines = remember(raw) { LyricDisplayParser.parseTimed(raw) }
  if (lines.isEmpty()) return
  val smooth = rememberSmoothLyricPosition(positionMs, isPlaying)
  val active = activeLyricIndex(lines, smooth)
  val line = lines.getOrNull(active)?.text ?: lines.firstOrNull()?.text ?: return
  Surface(
    onClick = onOpenImmersive,
    shape = RoundedCornerShape(28.dp),
    color = Color.White.copy(alpha = 0.12f),
    tonalElevation = 0.dp,
    modifier = modifier
      .fillMaxWidth()
      .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(28.dp)),
  ) {
    Row(
      modifier = Modifier.padding(start = 20.dp, top = 7.dp, end = 7.dp, bottom = 7.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Crossfade(targetState = line, label = "lyricPillSwap", modifier = Modifier.weight(1f)) {
        Text(
          it,
          style = MaterialTheme.typography.bodyLarge,
          color = Color.White,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Spacer(Modifier.width(8.dp))
      ArtCircleButton(onClick = onOpenImmersive, size = 38.dp) {
        Icon(
          imageVector = Icons.Filled.OpenInFull,
          contentDescription = stringResource(R.string.lyrics_fullscreen),
          modifier = Modifier.size(17.dp),
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
      modifier = Modifier.border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape),
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(
          Icons.Filled.HighQuality,
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
      shape = RoundedCornerShape(16.dp),
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
                Icons.Filled.Check,
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

@Composable
private fun GlassPill(
  onClick: () -> Unit,
  content: @Composable () -> Unit,
) {
  Surface(
    onClick = onClick,
    shape = CircleShape,
    color = Color.White.copy(alpha = 0.12f),
    modifier = Modifier.border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
    ) {
      CompositionLocalProvider(LocalContentColor provides Color.White) {
        content()
      }
    }
  }
}

@Composable
private fun LikePill(
  isLiked: Boolean,
  onToggle: () -> Unit,
) {
  val container = if (isLiked) Color.White else Color.White.copy(alpha = 0.12f)
  val contentColor = if (isLiked) Color.Black else Color.White
  Surface(
    onClick = onToggle,
    shape = CircleShape,
    color = container,
    modifier = Modifier.border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape),
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
  // Always white: the slider floats on artwork, never on theme surfaces.
  val activeSlider = Color.White
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
      // Pinned 12dp dot: the stock expressive thumb morphs into a pill
      // mid-drag, which read as a rendering glitch on screenshots.
      thumb = {
        Box(
          Modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(Color.White),
        )
      },
      modifier = Modifier.fillMaxWidth(),
    )
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
      Text(
        formatMs(if (dragging) dragValue.toLong() else positionMs),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = Color.White.copy(alpha = 0.9f),
      )
      Spacer(Modifier.weight(1f))
      Text(
        formatMs(durationMs),
        style = MaterialTheme.typography.labelMedium,
        color = Color.White.copy(alpha = 0.7f),
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
  // Reference layout: bare white side icons, one large translucent play
  // circle. Everything floats on the art, so all-white, no theme tints.
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceEvenly,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    IconButton(onClick = onToggleShuffle, modifier = Modifier.size(48.dp)) {
      Icon(
        imageVector = Icons.Filled.Shuffle,
        contentDescription = stringResource(
          if (shuffleEnabled) R.string.transport_shuffle_on else R.string.transport_shuffle_off,
        ),
        tint = if (shuffleEnabled) Color.White else Color.White.copy(alpha = 0.55f),
        modifier = Modifier.size(24.dp),
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
        tint = Color.White,
        modifier = Modifier.size(34.dp),
      )
    }
    if (state == PlaybackUiState.Loading) {
      CircularProgressIndicator(
        modifier = Modifier.size(88.dp),
        color = Color.White,
        trackColor = Color.White.copy(alpha = 0.24f),
      )
    } else {
      Surface(
        onClick = onToggle,
        enabled = state == PlaybackUiState.Playing || state == PlaybackUiState.Paused ||
          state == PlaybackUiState.Idle || state is PlaybackUiState.Error,
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.18f),
        modifier = Modifier
          .size(88.dp)
          .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape),
      ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
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
            tint = Color.White,
            modifier = Modifier.size(44.dp),
          )
        }
      }
    }
    IconButton(
      onClick = onNext,
      enabled = state != PlaybackUiState.Loading,
      modifier = Modifier.size(56.dp),
    ) {
      Icon(
        Icons.Filled.SkipNext,
        contentDescription = stringResource(R.string.transport_next),
        tint = Color.White,
        modifier = Modifier.size(34.dp),
      )
    }
    IconButton(onClick = onCycleRepeat, modifier = Modifier.size(48.dp)) {
      Icon(
        imageVector = if (repeatMode == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
        contentDescription = stringResource(R.string.transport_repeat, repeatMode.name),
        tint = if (repeatMode == RepeatMode.OFF) {
          Color.White.copy(alpha = 0.55f)
        } else {
          Color.White
        },
        modifier = Modifier.size(24.dp),
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
