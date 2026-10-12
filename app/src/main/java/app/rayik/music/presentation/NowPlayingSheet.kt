package app.rayik.music.presentation

import android.content.Context
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import app.rayik.music.BuildConfig
import app.rayik.music.R
import app.rayik.music.constants.SyncedHapticsMode
import app.rayik.music.constants.SyncedHapticsModeKey
import app.rayik.music.constants.SyncedHapticsPausedKey
import app.rayik.music.constants.SyncedMusicHapticsKey
import app.rayik.music.playback.haptics.hasHapticMotor
import app.rayik.music.utils.rememberEnumPreference
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

/** Barber mirror-arch top, ticket-stub bottom. One shape for every theme; color does the theming. */
private val ArchTopRadius = 140.dp
private val ArchBottomRadius = 28.dp

private fun archTicketShape() = RoundedCornerShape(
  topStart = ArchTopRadius,
  topEnd = ArchTopRadius,
  bottomStart = ArchBottomRadius,
  bottomEnd = ArchBottomRadius,
)

/**
 * Rayik player: theme surface, arch-ticket art, tabbed Player | Queue |
 * Lyrics. Same [PlayerViewModel] contract as before — only the look changed.
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
  val scope = rememberCoroutineScope()
  var immersiveLyrics by remember { mutableStateOf(false) }
  var lyricsExpanded by rememberSaveable { mutableStateOf(false) }
  var tab by rememberSaveable { mutableIntStateOf(0) }
  val prefs = rayikPreferences()
  val streamQuality by prefs.streamQuality.collectAsState()
  val seekbarStyle by prefs.seekbarStyle.collectAsState()
  var syncedHaptics by rememberPreference(SyncedMusicHapticsKey, false)
  var hapticsPaused by rememberPreference(SyncedHapticsPausedKey, false)
  var hapticsMode by rememberEnumPreference(SyncedHapticsModeKey, SyncedHapticsMode.FULL_MIX)
  val hasHapticMotor = remember { context.hasHapticMotor() }

  val artwork = player.premiumArtworkUrl.collectAsState().value
  val artUrl = artwork.ifBlank { current?.artworkUrl.orEmpty() }
  val scheme = MaterialTheme.colorScheme

  // Drag-to-dismiss on the header zone: fling down or drag past threshold collapses.
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
      .background(scheme.surface)
      .graphicsLayer { translationY = dragOffset.value },
  ) {
    // Subtle tint from theme roles only — never sampled from art.
    Box(
      Modifier.matchParentSize().background(
        Brush.verticalGradient(
          0f to scheme.surfaceContainerHigh.copy(alpha = 0.55f),
          0.38f to scheme.surface,
        ),
      ),
    )

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
      Column(
        modifier = Modifier
          .widthIn(max = 560.dp)
          .fillMaxSize()
          .navigationBarsPadding()
          .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Spacer(Modifier.statusBarsPadding().height(10.dp))
        Box(
          Modifier
            .width(42.dp)
            .height(5.dp)
            .clip(CircleShape)
            .background(scheme.surfaceVariant)
            .then(headerDrag),
        )
        Spacer(Modifier.height(10.dp))
        Row(
          modifier = Modifier.fillMaxWidth().then(headerDrag),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          IconButton(onClick = onCollapse, modifier = Modifier.size(48.dp)) {
            Icon(
              Icons.Filled.KeyboardArrowDown,
              contentDescription = stringResource(R.string.action_collapse),
              tint = scheme.onSurface,
            )
          }
          Spacer(Modifier.weight(1f))
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
              stringResource(R.string.player_now_playing).uppercase(),
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.Bold,
              letterSpacing = 2.2.sp,
              color = scheme.onSurface,
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
                color = scheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.4.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
          Spacer(Modifier.weight(1f))
          IconButton(
            onClick = { shareTrack(context, current?.title.orEmpty(), current?.artist.orEmpty()) },
            enabled = current != null,
            modifier = Modifier.size(48.dp),
          ) {
            Icon(
              Icons.Filled.Share,
              contentDescription = stringResource(R.string.action_share),
              tint = scheme.onSurface,
            )
          }
        }

        TabRow(
          selectedTabIndex = tab,
          containerColor = Color.Transparent,
          contentColor = scheme.onSurface,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Tab(
            selected = tab == 0,
            onClick = { tab = 0 },
            text = { Text(stringResource(R.string.player)) },
          )
          Tab(
            selected = tab == 1,
            onClick = { tab = 1 },
            text = {
              Text(
                if (rows.isEmpty()) stringResource(R.string.queue)
                else "${stringResource(R.string.queue)} • ${rows.size}",
              )
            },
          )
          Tab(
            selected = tab == 2,
            onClick = { tab = 2 },
            text = { Text(stringResource(R.string.lyrics)) },
          )
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
          Crossfade(targetState = tab, label = "playerTabs") { page ->
            when (page) {
            0 -> PlayerTab(
              currentTitle = current?.title,
              currentArtist = current?.artist,
              isLiked = isLiked,
              playbackState = playbackState,
              positionMs = positionMs,
              durationMs = durationMs,
              seekbarStyle = seekbarStyle,
              repeatMode = repeatMode,
              shuffleEnabled = shuffleEnabled,
              syncedHaptics = syncedHaptics,
              hasHapticMotor = hasHapticMotor,
              artUrl = artUrl,
              onSeek = player::seekTo,
              onToggle = {
                if (playbackState is PlaybackUiState.Error) player.retry()
                else player.togglePlayPause()
              },
              onNext = player::next,
              onPrevious = player::previous,
              onToggleLike = player::toggleLike,
              onToggleShuffle = player::toggleShuffle,
              onCycleRepeat = player::cycleRepeat,
              onToggleHaptics = { syncedHaptics = !syncedHaptics },
              onOpenQueue = { tab = 1 },
              onOpenLyrics = { tab = 2 },
              onShare = { shareTrack(context, current?.title.orEmpty(), current?.artist.orEmpty()) },
              hapticsMode = if (syncedHaptics && hasHapticMotor) hapticsMode else null,
              onHapticsMode = { hapticsMode = it },
              errorBlock = {
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
              },
            )
            1 -> QueueTab(
              rows = rows,
              playbackState = playbackState,
              queueTitle = queueTitle,
              streamQuality = streamQuality,
              onQuality = {
                prefs.streamQuality.set(it)
                scope.launch(Dispatchers.IO) { context.mirrorStreamQualityChoice(it) }
              },
              onPlayRow = { player.playWindow(it) },
              onOpenPlayer = { tab = 0 },
            )
            else -> LyricsTab(
              raw = rawLyrics,
              positionMs = positionMs,
              isPlaying = playbackState == PlaybackUiState.Playing,
              expanded = lyricsExpanded,
              onToggleExpand = { lyricsExpanded = !lyricsExpanded },
              onOpenImmersive = { immersiveLyrics = true },
              onSeek = player::seekTo,
            )
          }
          }
        }
      }
    }

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

@Composable
private fun PlayerTab(
  currentTitle: String?,
  currentArtist: String?,
  isLiked: Boolean,
  playbackState: PlaybackUiState,
  positionMs: Long,
  durationMs: Long,
  seekbarStyle: SeekbarStyle,
  repeatMode: RepeatMode,
  shuffleEnabled: Boolean,
  syncedHaptics: Boolean,
  hasHapticMotor: Boolean,
  artUrl: String,
  onSeek: (Long) -> Unit,
  onToggle: () -> Unit,
  onNext: () -> Unit,
  onPrevious: () -> Unit,
  onToggleLike: () -> Unit,
  onToggleShuffle: () -> Unit,
  onCycleRepeat: () -> Unit,
  onToggleHaptics: () -> Unit,
  onOpenQueue: () -> Unit,
  onOpenLyrics: () -> Unit,
  onShare: () -> Unit,
  hapticsMode: SyncedHapticsMode?,
  onHapticsMode: (SyncedHapticsMode) -> Unit,
  errorBlock: @Composable () -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState()),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Spacer(Modifier.height(12.dp))
    ArchTicketArt(artUrl = artUrl)
    Spacer(Modifier.height(16.dp))

    if (currentTitle == null) {
      ScreenScaffold(state = ScreenState.Loading, loadingText = "", onRetry = {}) {}
    } else {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(Modifier.weight(1f)) {
          Text(
            currentTitle,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Spacer(Modifier.height(2.dp))
          Text(
            currentArtist.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        IconButton(onClick = onToggleLike, modifier = Modifier.size(48.dp)) {
          Icon(
            imageVector = if (isLiked) Icons.Filled.Star else Icons.Filled.StarBorder,
            contentDescription = stringResource(
              if (isLiked) R.string.action_unlike else R.string.action_like,
            ),
            tint = if (isLiked) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
          )
        }
        TitleOverflowMenu(
          onShare = onShare,
          onOpenQueue = onOpenQueue,
          hapticsMode = hapticsMode,
          onHapticsMode = onHapticsMode,
        )
      }
    }

    Spacer(Modifier.height(6.dp))
    SheetSlider(
      positionMs = positionMs,
      durationMs = durationMs,
      style = seekbarStyle,
      isPlaying = playbackState == PlaybackUiState.Playing,
      onSeek = onSeek,
    )
    Spacer(Modifier.height(4.dp))
    ControlDock(
      state = playbackState,
      onToggle = onToggle,
      onNext = onNext,
      onPrevious = onPrevious,
    )
    Spacer(Modifier.height(8.dp))
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
          tint = if (shuffleEnabled) MaterialTheme.colorScheme.primary
          else MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(22.dp),
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
          modifier = Modifier.size(22.dp),
        )
      }
      if (hasHapticMotor) {
        IconButton(onClick = onToggleHaptics, modifier = Modifier.size(48.dp)) {
          Icon(
            Icons.Filled.Vibration,
            contentDescription = stringResource(
              if (syncedHaptics) R.string.transport_haptics_on else R.string.transport_haptics_off,
            ),
            tint = if (syncedHaptics) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
          )
        }
      }
      IconButton(onClick = onOpenLyrics, modifier = Modifier.size(48.dp)) {
        Icon(
          Icons.Filled.FormatQuote,
          contentDescription = stringResource(R.string.lyrics),
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(22.dp),
        )
      }
      IconButton(onClick = onOpenQueue, modifier = Modifier.size(48.dp)) {
        Icon(
          Icons.Filled.QueueMusic,
          contentDescription = stringResource(R.string.action_open_queue),
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(22.dp),
        )
      }
    }
    errorBlock()
    Spacer(Modifier.height(MaterialTheme.spacing.extraLarge))
  }
}

@Composable
private fun QueueTab(
  rows: List<app.rayik.music.player.QueueRow>,
  playbackState: PlaybackUiState,
  queueTitle: String?,
  streamQuality: StreamQuality,
  onQuality: (StreamQuality) -> Unit,
  onPlayRow: (app.rayik.music.player.QueueRow) -> Unit,
  onOpenPlayer: () -> Unit,
) {
  val scheme = MaterialTheme.colorScheme
  Column(Modifier.fillMaxSize()) {
    Spacer(Modifier.height(12.dp))
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        stringResource(R.string.queue_title),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = scheme.onSurface,
      )
      Spacer(Modifier.width(10.dp))
      if (rows.isNotEmpty()) {
        Surface(shape = CircleShape, color = scheme.primaryContainer) {
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
      QualityMenu(quality = streamQuality, onSelect = onQuality)
    }
    Spacer(Modifier.height(MaterialTheme.spacing.small))
    if (rows.isEmpty()) {
      GlassCard {
        Column(
          modifier = Modifier.padding(18.dp).fillMaxWidth(),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Icon(
            Icons.Filled.QueueMusic,
            contentDescription = null,
            tint = scheme.onSurfaceVariant,
            modifier = Modifier.size(28.dp),
          )
          Spacer(Modifier.height(8.dp))
          Text(
            stringResource(R.string.queue_empty_title),
            style = MaterialTheme.typography.titleSmall,
            color = scheme.onSurface,
          )
        }
      }
    } else {
      LazyColumn(
        modifier = Modifier.weight(1f).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        items(rows, key = { it.mediaId }) { row ->
          GlassQueueWrapper(isCurrent = row.isCurrent) {
            UpNextRow(
              item = row,
              isPlaying = row.isCurrent && playbackState == PlaybackUiState.Playing,
              onClick = { onPlayRow(row) },
            )
          }
        }
      }
    }
    if (!queueTitle.isNullOrBlank()) {
      Spacer(Modifier.height(MaterialTheme.spacing.small))
      GlassCard {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(Modifier.weight(1f)) {
            Text(
              stringResource(R.string.playing_from, queueTitle),
              style = MaterialTheme.typography.bodyLarge,
              fontWeight = FontWeight.SemiBold,
              color = scheme.onSurface,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
          IconButton(onClick = onOpenPlayer, modifier = Modifier.size(44.dp)) {
            Icon(
              Icons.Filled.QueueMusic,
              contentDescription = stringResource(R.string.action_open_queue),
              tint = scheme.onSurfaceVariant,
              modifier = Modifier.size(22.dp),
            )
          }
        }
      }
    }
    Spacer(Modifier.height(MaterialTheme.spacing.medium))
  }
}

@Composable
private fun LyricsTab(
  raw: String?,
  positionMs: Long,
  isPlaying: Boolean,
  expanded: Boolean,
  onToggleExpand: () -> Unit,
  onOpenImmersive: () -> Unit,
  onSeek: (Long) -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState()),
  ) {
    Spacer(Modifier.height(12.dp))
    LyricsPreviewCard(
      raw = raw,
      positionMs = positionMs,
      expanded = expanded,
      onToggleExpand = onToggleExpand,
      isPlaying = isPlaying,
      onOpenImmersive = onOpenImmersive,
      onSeek = onSeek,
    )
    Spacer(Modifier.height(MaterialTheme.spacing.extraLarge))
  }
}

/**
 * Signature art: mirror-arch with a brass (tertiary) rim and two ticket
 * notches punched from the sides. Same geometry on every theme; the scheme
 * supplies the colors so light/dark/AMOLED stay free.
 */
@Composable
private fun ArchTicketArt(
  artUrl: String,
  modifier: Modifier = Modifier,
) {
  val scheme = MaterialTheme.colorScheme
  val shape = archTicketShape()
  Box(
    modifier = modifier
      .widthIn(max = 360.dp)
      .fillMaxWidth()
      .aspectRatio(0.92f),
    contentAlignment = Alignment.Center,
  ) {
    Surface(
      shape = shape,
      color = scheme.surfaceContainerHigh,
      tonalElevation = 2.dp,
      shadowElevation = 8.dp,
      modifier = Modifier
        .matchParentSize()
        .border(1.dp, scheme.tertiary.copy(alpha = 0.6f), shape),
    ) {
      Box(
        modifier = Modifier.fillMaxSize().clip(shape).background(scheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = Icons.Filled.MusicNote,
          contentDescription = null,
          tint = scheme.onSurfaceVariant,
          modifier = Modifier.size(64.dp),
        )
        if (artUrl.isNotBlank()) {
          AsyncImage(
            model = artUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize().clip(shape),
          )
        }
      }
    }
    // Ticket notches: surface discs straddling each edge read as punches.
    // Half outside the art so the rim breaks instead of a dot sitting on it.
    Box(
      Modifier
        .size(24.dp)
        .align(Alignment.CenterStart)
        .offset(x = (-12).dp)
        .clip(CircleShape)
        .background(scheme.surface)
        .border(1.dp, scheme.tertiary.copy(alpha = 0.4f), CircleShape),
    )
    Box(
      Modifier
        .size(24.dp)
        .align(Alignment.CenterEnd)
        .offset(x = 12.dp)
        .clip(CircleShape)
        .background(scheme.surface)
        .border(1.dp, scheme.tertiary.copy(alpha = 0.4f), CircleShape),
    )
  }
}

/** Overflow for the title row: share, jump to queue, and haptics mode. */
@Composable
private fun TitleOverflowMenu(
  onShare: () -> Unit,
  onOpenQueue: () -> Unit,
  hapticsMode: SyncedHapticsMode?,
  onHapticsMode: (SyncedHapticsMode) -> Unit,
) {
  var expanded by remember { mutableStateOf(false) }
  Box {
    IconButton(onClick = { expanded = true }, modifier = Modifier.size(48.dp)) {
      Icon(
        Icons.Filled.MoreVert,
        contentDescription = stringResource(R.string.action_open_queue),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(22.dp),
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
      if (hapticsMode != null) {
        SyncedHapticsMode.entries.forEach { mode ->
          DropdownMenuItem(
            text = {
              Text(
                stringResource(
                  if (mode == SyncedHapticsMode.VOCALS_ONLY) {
                    R.string.settings_haptics_vocals
                  } else {
                    R.string.settings_haptics_fullmix
                  },
                ),
              )
            },
            onClick = {
              expanded = false
              onHapticsMode(mode)
            },
            leadingIcon = {
              Icon(Icons.Filled.Vibration, contentDescription = null, modifier = Modifier.size(18.dp))
            },
            trailingIcon = if (mode == hapticsMode) {
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
}

/** In-player streaming quality chip trailing the Up-next header. */
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
      color = scheme.surfaceContainerHigh,
      modifier = Modifier.border(1.dp, scheme.outlineVariant, CircleShape),
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(
          Icons.Filled.HighQuality,
          contentDescription = stringResource(R.string.pref_quality_label),
          tint = scheme.onSurfaceVariant,
          modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
          stringResource(quality.titleRes),
          style = MaterialTheme.typography.labelMedium,
          color = scheme.onSurface,
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

/** Theme card for lyrics / error / empty states. */
@Composable
fun GlassCard(
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  Surface(
    tonalElevation = 2.dp,
    shape = RoundedCornerShape(26.dp),
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
    modifier = modifier
      .fillMaxWidth()
      .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(26.dp)),
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
      color = scheme.primaryContainer,
      modifier = Modifier
        .fillMaxWidth()
        .border(1.dp, scheme.primary.copy(alpha = 0.35f), RoundedCornerShape(18.dp)),
    ) {
      content()
    }
  } else {
    Surface(
      shape = RoundedCornerShape(18.dp),
      color = scheme.surfaceContainer,
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
  val scheme = MaterialTheme.colorScheme
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
    activeTrackColor = scheme.primary,
    inactiveTrackColor = scheme.surfaceVariant,
    thumbColor = scheme.primary,
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
      // Paused Wavy: straight track but the same `|` bar head as the
      // wavy slider (4x16dp rounded bar, 4dp track = squiggle stroke).
      style == SeekbarStyle.Wavy && !isPlaying -> {
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
                .size(width = 4.dp, height = 16.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(scheme.primary),
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
                .background(scheme.primary),
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
      // Standard style.
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
                .background(scheme.primary),
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
        color = scheme.onSurfaceVariant,
      )
      Spacer(Modifier.weight(1f))
      Text(
        "-" + formatMs(maxOf(durationMs - shownPosition, 0L)),
        style = MaterialTheme.typography.labelSmall,
        color = scheme.onSurfaceVariant,
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
  val scheme = MaterialTheme.colorScheme
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
        tint = scheme.onSurface,
        modifier = Modifier.size(36.dp),
      )
    }
    if (state == PlaybackUiState.Loading) {
      CircularProgressIndicator(
        modifier = Modifier.size(60.dp),
        color = scheme.primary,
        trackColor = scheme.surfaceVariant,
      )
    } else {
      Surface(
        onClick = onToggle,
        enabled = state == PlaybackUiState.Playing || state == PlaybackUiState.Paused ||
          state == PlaybackUiState.Idle || state is PlaybackUiState.Error,
        shape = CircleShape,
        color = scheme.primary,
        modifier = Modifier.size(76.dp),
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
            tint = scheme.onPrimary,
            modifier = Modifier.size(40.dp),
          )
        }
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
        tint = scheme.onSurface,
        modifier = Modifier.size(36.dp),
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
