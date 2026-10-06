package app.rayik.music.presentation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.palette.graphics.Palette
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.rayik.music.BuildConfig
import app.rayik.music.R
import app.rayik.music.constants.SyncedMusicHapticsKey
import app.rayik.music.utils.rememberPreference
import app.rayik.music.player.PlaybackUiState
import app.rayik.music.player.PlayerViewModel
import app.rayik.music.player.RepeatMode
import app.rayik.music.player.buildPlaybackDiagnostics
import app.rayik.music.player.formatMs
import app.rayik.music.preferences.StreamQuality
import app.rayik.music.preferences.SeekbarStyle
import app.rayik.music.preferences.preference.collectAsState
import app.rayik.music.ui.theme.spacing
import me.saket.squiggles.SquigglySlider

/** Lazy-list index of the scroll target; header sections above are always emitted. */
private const val UPNEXT_SECTION_INDEX = 7

/** Apple field: photo runs this far down, then dissolves into flat bedrock. */
private const val APPLE_PHOTO_FRACTION = 0.68f

/** Fallback field when sampling fails — deep teal-black. */
private val BedrockFallback = Color(0xFF0B2427)

/**
 * Apple-style flat bedrock sampled from the art: dark muted first, then
 * dark vibrant, then a darkened dominant. Darkened to V<=0.22 so white
 * controls always contrast. Cached per URL; falls back quietly.
 */
@Composable
private fun rememberSampledBedrock(artUrl: String): Color {
  val context = LocalContext.current
  var bedrock by remember(artUrl) { mutableStateOf(BedrockFallback) }
  LaunchedEffect(artUrl) {
    if (artUrl.isBlank()) {
      bedrock = BedrockFallback
      return@LaunchedEffect
    }
    bedrock = withContext(Dispatchers.IO) {
      runCatching {
        val request = ImageRequest.Builder(context)
          .data(artUrl)
          .size(128)
          .allowHardware(false)
          .build()
        val result = context.imageLoader.execute(request)
        val bitmap = (result as? SuccessResult)?.image?.toBitmap()
          ?: return@runCatching BedrockFallback
        val palette = Palette.from(bitmap).maximumColorCount(16).generate()
        val rgb = palette.darkMutedSwatch?.rgb
          ?: palette.darkVibrantSwatch?.rgb
          ?: palette.dominantSwatch?.rgb
          ?: return@runCatching BedrockFallback
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(rgb, hsv)
        hsv[2] = minOf(hsv[2], 0.22f)
        Color(android.graphics.Color.HSVToColor(hsv))
      }.getOrDefault(BedrockFallback)
    }
  }
  return bedrock
}

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
  var immersiveLyrics by remember { mutableStateOf(false) }
  val prefs = rayikPreferences()
  val streamQuality by prefs.streamQuality.collectAsState()
  val seekbarStyle by prefs.seekbarStyle.collectAsState()
  var syncedHaptics by rememberPreference(SyncedMusicHapticsKey, false)

  val artwork = player.premiumArtworkUrl.collectAsState().value
  // Bedrock samples the clean art; while home-album taps resolve, the
  // queue's frame tints the field so the screen is never a black void.
  val sampleUrl = artwork.ifBlank { current?.artworkUrl.orEmpty() }
  val bedrockTarget = rememberSampledBedrock(sampleUrl)
  val bedrock by animateColorAsState(
    targetValue = bedrockTarget,
    animationSpec = tween(600),
    label = "bedrockMelt",
  )
  val scheme = MaterialTheme.colorScheme

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
      // Apple field: flat sampled color, never black, never transparent —
      // home can never ghost through and no seam can open mid-screen.
      .background(bedrock)
      .graphicsLayer { translationY = dragOffset.value },
  ) {
    // ---- Apple artwork: full-bleed portrait Crop to ~68%, feathered into
    // the flat bedrock. No cloud-blur copy (neither reference has one), no
    // fit-width banner. Crossfade across tracks is the transition.
    // NOTE: Crossfade must own a real size (fillMaxSize) — a bare one
    // collapses to 0x0 and the art silently disappears.
    Crossfade(
      targetState = artwork,
      label = "artBackdrop",
      modifier = Modifier.fillMaxSize(),
    ) { art ->
      if (art.isNotBlank()) {
        AsyncImage(
          model = art,
          contentDescription = null,
          contentScale = ContentScale.Crop,
          alignment = Alignment.TopCenter,
          modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(APPLE_PHOTO_FRACTION)
            .drawWithContent {
              drawContent()
              drawRect(
                brush = Brush.verticalGradient(
                  0f to Color.Black,
                  0.72f to Color.Black,
                  1f to Color.Transparent,
                ),
                blendMode = BlendMode.DstIn,
              )
            },
        )
      }
    }
    // Bridge: bedrock transparent above the melt, opaque below — the photo
    // always lands on flat color with zero line on any aspect.
    Box(
      Modifier.matchParentSize().background(
        Brush.verticalGradient(
          0.52f to Color.Transparent,
          0.70f to bedrock,
        ),
      ),
    )
    // Whisper top scrim for status-bar legibility only.
    Box(
      Modifier.matchParentSize().background(
        Brush.verticalGradient(
          0f to Color.Black.copy(alpha = 0.30f),
          0.15f to Color.Transparent,
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

        // No hero card — the full-bleed art above IS the hero. This
        // spacer drops the title onto the melt, Apple-style.
        item {
          Spacer(Modifier.fillParentMaxHeight(0.42f))
        }

        item {
          if (current == null) {
            ScreenScaffold(state = ScreenState.Loading, loadingText = "", onRetry = {}) {}
          } else {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(Modifier.weight(1f)) {
                Text(
                  current.title,
                  style = MaterialTheme.typography.headlineSmall,
                  fontWeight = FontWeight.SemiBold,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  color = Color.White,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                  current.artist,
                  style = MaterialTheme.typography.bodyMedium,
                  color = Color.White.copy(alpha = 0.6f),
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
              Spacer(Modifier.width(8.dp))
              TitleIconButton(onClick = player::toggleLike) {
                Icon(
                  imageVector = if (isLiked) Icons.Filled.Star else Icons.Filled.StarBorder,
                  contentDescription = stringResource(
                    if (isLiked) R.string.action_unlike else R.string.action_like,
                  ),
                  tint = Color.White,
                  modifier = Modifier.size(20.dp),
                )
              }
              Spacer(Modifier.width(8.dp))
              TitleOverflowMenu(
                onShare = { shareTrack(context, current.title, current.artist) },
                onOpenQueue = { scope.launch { listState.animateScrollToItem(UPNEXT_SECTION_INDEX) } },
              )
            }
          }
        }

        item {
          Spacer(Modifier.height(6.dp))
          SheetSlider(
            positionMs = positionMs,
            durationMs = durationMs,
            style = seekbarStyle,
            isPlaying = playbackState == PlaybackUiState.Playing,
            onSeek = player::seekTo,
          )
        }

        item {
          Spacer(Modifier.height(4.dp))
          ControlDock(
            state = playbackState,
            // On error the hero retries instead of toggling — the dock
            // never sits dead with a disabled button and no way forward.
            onToggle = {
              if (playbackState is PlaybackUiState.Error) player.retry()
              else player.togglePlayPause()
            },
            onNext = player::next,
            onPrevious = player::previous,
          )
        }

        item {
          if (current != null) {
            Spacer(Modifier.height(8.dp))
            // Apple footer: quiet icon row. Shuffle/repeat moved here from
            // the transport so nothing is lost; lyrics + queue stay one tap.
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceEvenly,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              IconButton(onClick = player::toggleShuffle, modifier = Modifier.size(48.dp)) {
                Icon(
                  imageVector = Icons.Filled.Shuffle,
                  contentDescription = stringResource(
                    if (shuffleEnabled) R.string.transport_shuffle_on else R.string.transport_shuffle_off,
                  ),
                  tint = if (shuffleEnabled) Color.White else Color.White.copy(alpha = 0.5f),
                  modifier = Modifier.size(22.dp),
                )
              }
              IconButton(onClick = player::cycleRepeat, modifier = Modifier.size(48.dp)) {
                Icon(
                  imageVector = if (repeatMode == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                  contentDescription = stringResource(R.string.transport_repeat, repeatMode.name),
                  tint = if (repeatMode == RepeatMode.OFF) {
                    Color.White.copy(alpha = 0.5f)
                  } else {
                    Color.White
                  },
                  modifier = Modifier.size(22.dp),
                )
              }
              IconButton(
                onClick = { syncedHaptics = !syncedHaptics },
                modifier = Modifier.size(48.dp),
              ) {
                Icon(
                  Icons.Filled.Vibration,
                  contentDescription = stringResource(
                    if (syncedHaptics) R.string.transport_haptics_on else R.string.transport_haptics_off,
                  ),
                  tint = if (syncedHaptics) Color.White else Color.White.copy(alpha = 0.5f),
                  modifier = Modifier.size(22.dp),
                )
              }
              IconButton(
                onClick = { immersiveLyrics = true },
                modifier = Modifier.size(48.dp),
              ) {
                Icon(
                  Icons.Filled.FormatQuote,
                  contentDescription = stringResource(R.string.lyrics_fullscreen),
                  tint = Color.White.copy(alpha = 0.75f),
                  modifier = Modifier.size(22.dp),
                )
              }
              IconButton(
                onClick = { scope.launch { listState.animateScrollToItem(UPNEXT_SECTION_INDEX) } },
                modifier = Modifier.size(48.dp),
              ) {
                Icon(
                  Icons.Filled.QueueMusic,
                  contentDescription = stringResource(R.string.action_open_queue),
                  tint = Color.White.copy(alpha = 0.75f),
                  modifier = Modifier.size(22.dp),
                )
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
          }
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
 * Small dark circle for title-row actions (like star, overflow) floating
 * on the melt — Apple-sized, quieter than [ArtCircleButton].
 */
@Composable
private fun TitleIconButton(
  onClick: () -> Unit,
  content: @Composable () -> Unit,
) {
  Surface(
    onClick = onClick,
    shape = CircleShape,
    color = Color.White.copy(alpha = 0.12f),
    tonalElevation = 0.dp,
    modifier = Modifier
      .size(38.dp)
      .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape),
  ) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
      CompositionLocalProvider(LocalContentColor provides Color.White) {
        content()
      }
    }
  }
}

/** Overflow for the title row: share + jump to queue live here now. */
@Composable
private fun TitleOverflowMenu(
  onShare: () -> Unit,
  onOpenQueue: () -> Unit,
) {
  var expanded by remember { mutableStateOf(false) }
  Box {
    TitleIconButton(onClick = { expanded = true }) {
      Icon(
        Icons.Filled.MoreVert,
        contentDescription = stringResource(R.string.action_open_queue),
        tint = Color.White,
        modifier = Modifier.size(20.dp),
      )
    }
    DropdownMenu(
      expanded = expanded,
      onDismissRequest = { expanded = false },
      shape = RoundedCornerShape(16.dp),
    ) {
      DropdownMenuItem(
        text = { Text(stringResource(R.string.action_share)) },
        onClick = {
          expanded = false
          onShare()
        },
        leadingIcon = {
          Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
        },
      )
      DropdownMenuItem(
        text = { Text(stringResource(R.string.action_open_queue)) },
        onClick = {
          expanded = false
          onOpenQueue()
        },
        leadingIcon = {
          Icon(Icons.Filled.QueueMusic, contentDescription = null, modifier = Modifier.size(18.dp))
        },
      )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SheetSlider(
  positionMs: Long,
  durationMs: Long,
  style: SeekbarStyle,
  isPlaying: Boolean,
  onSeek: (Long) -> Unit,
) {
  // Apple slider: thin track, pinned 10dp dot, small grey times with
  // negative remaining. Always white: floats on artwork, never on theme.
  val activeSlider = Color.White
  var dragging by remember { mutableStateOf(false) }
  var dragValue by remember { mutableFloatStateOf(0f) }
  val range = 0f..maxOf(durationMs.toFloat(), 1f)
  val sliderValue = (if (dragging) dragValue else positionMs.toFloat()).coerceIn(range)
  val shownPosition = if (dragging) dragValue.toLong() else positionMs
  val onScrub = { value: Float ->
    dragging = true
    dragValue = value
  }
  val onScrubFinished = {
    onSeek(dragValue.toLong())
    dragging = false
  }
  val sliderColors = SliderDefaults.colors(
    activeTrackColor = activeSlider,
    inactiveTrackColor = activeSlider.copy(alpha = 0.28f),
    thumbColor = activeSlider,
  )

  Column(Modifier.fillMaxWidth()) {
    // Paused wavy flattens to a straight slider: SquigglesAnimator's
    // constructor is internal, so the cheapest way to stop the infinite
    // wave is to not compose SquigglySlider at all when paused.
    when {
      style == SeekbarStyle.Wavy && isPlaying -> {
        SquigglySlider(
          value = sliderValue,
          onValueChange = onScrub,
          onValueChangeFinished = onScrubFinished,
          valueRange = range,
          enabled = durationMs > 0,
          colors = sliderColors,
          modifier = Modifier.fillMaxWidth(),
        )
      }
      style == SeekbarStyle.Thick -> {
        Slider(
          value = sliderValue,
          onValueChange = onScrub,
          onValueChangeFinished = onScrubFinished,
          valueRange = range,
          enabled = durationMs > 0,
          colors = sliderColors,
          thumb = {
            Box(
              Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(Color.White),
            )
          },
          track = { sliderState ->
            SliderDefaults.Track(
              sliderState = sliderState,
              modifier = Modifier.height(10.dp),
              colors = sliderColors,
            )
          },
          modifier = Modifier.fillMaxWidth(),
        )
      }
      // Standard style + paused Wavy (flattened straight).
      else -> {
        Slider(
          value = sliderValue,
          onValueChange = onScrub,
          onValueChangeFinished = onScrubFinished,
          valueRange = range,
          enabled = durationMs > 0,
          colors = sliderColors,
          thumb = {
            Box(
              Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(Color.White),
            )
          },
          track = { sliderState ->
            SliderDefaults.Track(
              sliderState = sliderState,
              modifier = Modifier.height(4.dp),
              colors = sliderColors,
            )
          },
          modifier = Modifier.fillMaxWidth(),
        )
      }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
      Text(
        formatMs(shownPosition),
        style = MaterialTheme.typography.labelSmall,
        color = Color.White.copy(alpha = 0.6f),
      )
      Spacer(Modifier.weight(1f))
      Text(
        "-" + formatMs(maxOf(durationMs - shownPosition, 0L)),
        style = MaterialTheme.typography.labelSmall,
        color = Color.White.copy(alpha = 0.6f),
      )
    }
  }
}

@Composable
private fun ControlDock(
  state: PlaybackUiState,
  onToggle: () -> Unit,
  onNext: () -> Unit,
  onPrevious: () -> Unit,
) {
  // Apple transport: three bare white icons, no circle. Shuffle/repeat
  // live in the quiet footer row so nothing is lost.
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceEvenly,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    IconButton(
      onClick = onPrevious,
      enabled = state != PlaybackUiState.Loading,
      modifier = Modifier.size(64.dp),
    ) {
      Icon(
        Icons.Filled.SkipPrevious,
        contentDescription = stringResource(R.string.transport_previous),
        tint = Color.White,
        modifier = Modifier.size(42.dp),
      )
    }
    if (state == PlaybackUiState.Loading) {
      CircularProgressIndicator(
        modifier = Modifier.size(60.dp),
        color = Color.White,
        trackColor = Color.White.copy(alpha = 0.24f),
      )
    } else {
      IconButton(
        onClick = onToggle,
        // Idle with a queue means "tap to start"; Error means "tap to
        // retry". Only an unknown state sits disabled.
        enabled = state == PlaybackUiState.Playing || state == PlaybackUiState.Paused ||
          state == PlaybackUiState.Idle || state is PlaybackUiState.Error,
        modifier = Modifier.size(76.dp),
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
          tint = Color.White,
          modifier = Modifier.size(58.dp),
        )
      }
    }
    IconButton(
      onClick = onNext,
      enabled = state != PlaybackUiState.Loading,
      modifier = Modifier.size(64.dp),
    ) {
      Icon(
        Icons.Filled.SkipNext,
        contentDescription = stringResource(R.string.transport_next),
        tint = Color.White,
        modifier = Modifier.size(42.dp),
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
