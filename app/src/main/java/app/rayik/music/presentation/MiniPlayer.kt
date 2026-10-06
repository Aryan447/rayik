package app.rayik.music.presentation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.rayik.music.R
import app.rayik.music.player.PlaybackUiState
import app.rayik.music.player.PlayerViewModel
import app.rayik.music.ui.theme.spacing
import kotlinx.coroutines.delay
import kotlin.math.min

/** Morph clock: one shared duration for fade, size glide, stagger and chevron. */
private const val MORPH_MS = 450
private const val MORPH_EXIT_MS = 180

/** Bottom-nav destinations. Icon-only in the expanded dock; labels survive in TalkBack. */
internal enum class NavTab(val labelRes: Int, val icon: ImageVector) {
  Raay(R.string.tab_raay, Icons.Filled.Home),
  Search(R.string.tab_search, Icons.Filled.Search),
  Library(R.string.tab_library, Icons.Filled.LibraryMusic),
  Settings(R.string.tab_settings, Icons.Filled.Settings),
}

/**
 * Single morphing dock. Collapsed = transport (art, prev, hero play, next,
 * show-tabs); expanded = icon-only tabs + show-player. Art tap expands the
 * full player sheet. Empty queue forces the tab row so navigation can never
 * strand.
 */
@Composable
fun MiniPlayer(
  onOpenPlayer: () -> Unit,
  expanded: Boolean,
  onExpandedChange: (Boolean) -> Unit,
  selectedTab: Int,
  onSelectTab: (Int) -> Unit,
  floatingDock: Boolean = false,
  player: PlayerViewModel = hiltViewModel(),
) {
  val rows by player.queueRows.collectAsState()
  val playbackState by player.playbackState.collectAsState()
  val isPlaying = playbackState == PlaybackUiState.Playing

  val current = rows.firstOrNull { it.isCurrent }
  // No queue, no transport: tabs only, collapse hidden.
  val showTabs = current == null || expanded
  val container = dockContainer()

  // Buttons rest for exactly one morph clock so taps can't land mid-flight.
  var locked by remember { mutableStateOf(false) }
  LaunchedEffect(showTabs) {
    locked = true
    delay(MORPH_MS.toLong())
    locked = false
  }
  val buttonsEnabled = !locked

  if (floatingDock) {
    // M3 Expressive path: the pill owns shape, tonal color, elevation and
    // the expand/collapse motion. The default dock below stays custom (non-M3).
    FloatingDock(
      showTabs = showTabs,
      artworkUrl = current?.artworkUrl.orEmpty(),
      playbackState = playbackState,
      isPlaying = isPlaying,
      buttonsEnabled = buttonsEnabled,
      selectedTab = selectedTab,
      onSelectTab = onSelectTab,
      onArtClick = onOpenPlayer,
      onPrevious = player::previous,
      onNext = player::next,
      onToggle = {
        if (playbackState is PlaybackUiState.Error) player.retry()
        else player.togglePlayPause()
      },
      onShowTabs = { onExpandedChange(true) },
      onCollapse = { onExpandedChange(false) },
    )
    return
  }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .background(container)
      .navigationBarsPadding()
      .clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = { if (!showTabs) onOpenPlayer() },
      ),
  ) {
    AnimatedContent(
      targetState = showTabs,
      transitionSpec = {
        fadeIn(animationSpec = tween(MORPH_MS, easing = FastOutSlowInEasing))
          .togetherWith(fadeOut(animationSpec = tween(MORPH_EXIT_MS, easing = FastOutSlowInEasing)))
      },
      label = "dockMorph",
    ) { tabs ->
      if (tabs) {
        DockTabsRow(
          selected = selectedTab,
          onSelect = onSelectTab,
          onCollapse = { onExpandedChange(false) },
          showCollapse = current != null,
          isPlaying = isPlaying,
          buttonsEnabled = buttonsEnabled,
          modeKey = true,
        )
      } else {
        DockTransportRow(
          artworkUrl = current?.artworkUrl.orEmpty(),
          state = playbackState,
          onArtClick = onOpenPlayer,
          onPrevious = player::previous,
          onNext = player::next,
          // On error the hero retries instead of toggling — same contract
          // as the full player dock.
          onToggle = {
            if (playbackState is PlaybackUiState.Error) player.retry()
            else player.togglePlayPause()
          },
          onShowTabs = { onExpandedChange(true) },
          buttonsEnabled = buttonsEnabled,
          modeKey = false,
        )
      }
    }
  }
}

/**
 * Floating dock: M3 Expressive [HorizontalFloatingToolbar]. Transport is the
 * expanded state (art in leading, prev/play/next in content, show-tabs in
 * trailing); the tab row is the collapsed state (tabs + collapse live in
 * content, since leading/trailing hide when collapsed). Same controls and
 * morph clock as the default dock, but wrap-content rows so the pill hugs
 * its content instead of stretching full width.
 */
@Composable
private fun FloatingDock(
  showTabs: Boolean,
  artworkUrl: String,
  playbackState: PlaybackUiState,
  isPlaying: Boolean,
  buttonsEnabled: Boolean,
  selectedTab: Int,
  onSelectTab: (Int) -> Unit,
  onArtClick: () -> Unit,
  onPrevious: () -> Unit,
  onNext: () -> Unit,
  onToggle: () -> Unit,
  onShowTabs: () -> Unit,
  onCollapse: () -> Unit,
) {
  HorizontalFloatingToolbar(
    expanded = !showTabs,
    colors = FloatingToolbarDefaults.standardFloatingToolbarColors(
      toolbarContainerColor = dockContainer(),
      toolbarContentColor = onDock(),
    ),
    leadingContent = {
      if (!showTabs) {
        TrackArt(
          artworkUrl = artworkUrl,
          corner = 12.dp,
          modifier = Modifier
            .size(DockArtSize)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onArtClick),
        )
      }
    },
    trailingContent = {
      if (!showTabs) {
        IconButton(onClick = onShowTabs, enabled = buttonsEnabled, modifier = Modifier.size(52.dp)) {
          Icon(
            Icons.Filled.KeyboardArrowUp,
            contentDescription = stringResource(R.string.dock_show_navigation),
            modifier = Modifier.size(28.dp),
          )
        }
      }
    },
    content = {
      AnimatedContent(
        targetState = showTabs,
        transitionSpec = {
          fadeIn(animationSpec = tween(MORPH_MS, easing = FastOutSlowInEasing))
            .togetherWith(fadeOut(animationSpec = tween(MORPH_EXIT_MS, easing = FastOutSlowInEasing)))
        },
        label = "floatingDockMorph",
      ) { tabs ->
        if (tabs) {
          FloatingTabsContent(
            selected = selectedTab,
            onSelect = onSelectTab,
            onCollapse = onCollapse,
            isPlaying = isPlaying,
            buttonsEnabled = buttonsEnabled,
            modeKey = true,
          )
        } else {
          FloatingTransportContent(
            state = playbackState,
            onPrevious = onPrevious,
            onNext = onNext,
            onToggle = onToggle,
            buttonsEnabled = buttonsEnabled,
            modeKey = false,
          )
        }
      }
    },
  )
}

/** Transport middle for the floating pill: prev + hero + next, fixed sizes. */
@Composable
private fun FloatingTransportContent(
  state: PlaybackUiState,
  onPrevious: () -> Unit,
  onNext: () -> Unit,
  onToggle: () -> Unit,
  buttonsEnabled: Boolean,
  modeKey: Boolean,
) {
  val content = onDock()
  Row(
    horizontalArrangement = Arrangement.spacedBy(4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    MorphSlot(index = 1, modeKey = modeKey) {
      IconButton(onClick = onPrevious, enabled = buttonsEnabled, modifier = Modifier.size(52.dp)) {
        Icon(
          Icons.Filled.SkipPrevious,
          contentDescription = stringResource(R.string.transport_previous),
          tint = content,
          modifier = Modifier.size(30.dp),
        )
      }
    }
    MorphSlot(index = 2, modeKey = modeKey) {
      when (state) {
        PlaybackUiState.Loading -> CircularProgressIndicator(
          modifier = Modifier.size(36.dp),
          color = content,
          trackColor = content.copy(alpha = 0.24f),
        )
        PlaybackUiState.Playing -> IconButton(
          onClick = onToggle,
          enabled = buttonsEnabled,
          modifier = Modifier.size(DockHeroTouch),
        ) {
          Icon(
            Icons.Filled.Pause,
            contentDescription = stringResource(R.string.transport_pause),
            tint = content,
            modifier = Modifier.size(40.dp),
          )
        }
        else -> IconButton(
          onClick = onToggle,
          enabled = buttonsEnabled &&
            (state == PlaybackUiState.Paused || state == PlaybackUiState.Idle ||
              state is PlaybackUiState.Error),
          modifier = Modifier.size(DockHeroTouch),
        ) {
          Icon(
            Icons.Filled.PlayArrow,
            contentDescription = stringResource(R.string.transport_play),
            tint = content,
            modifier = Modifier.size(40.dp),
          )
        }
      }
    }
    MorphSlot(index = 3, modeKey = modeKey) {
      IconButton(onClick = onNext, enabled = buttonsEnabled, modifier = Modifier.size(52.dp)) {
        Icon(
          Icons.Filled.SkipNext,
          contentDescription = stringResource(R.string.transport_next),
          tint = content,
          modifier = Modifier.size(30.dp),
        )
      }
    }
  }
}

/** Tab row for the floating pill: icon-only tabs + collapse, fixed sizes. */
@Composable
private fun FloatingTabsContent(
  selected: Int,
  onSelect: (Int) -> Unit,
  onCollapse: () -> Unit,
  isPlaying: Boolean,
  buttonsEnabled: Boolean,
  modeKey: Boolean,
) {
  val content = onDock()
  val dim = onDockDim()
  val pill = dockSelectedPill()
  Row(
    horizontalArrangement = Arrangement.spacedBy(4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    NavTab.entries.forEachIndexed { index, tab ->
      val isSelected = selected == index
      MorphSlot(index = index, modeKey = modeKey) {
        Surface(
          onClick = {
            if (index == selected) onCollapse() else onSelect(index)
          },
          enabled = buttonsEnabled,
          shape = RoundedCornerShape(20.dp),
          color = if (isSelected) pill else Color.Transparent,
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
          ) {
            Icon(
              tab.icon,
              contentDescription = stringResource(tab.labelRes),
              tint = if (isSelected) content else dim,
              modifier = Modifier.size(26.dp),
            )
            if (isSelected && isPlaying) {
              Spacer(Modifier.width(6.dp))
              PlayingIndicator(color = content)
            }
          }
        }
      }
    }
    MorphSlot(index = 4, modeKey = modeKey) {
      IconButton(onClick = onCollapse, enabled = buttonsEnabled, modifier = Modifier.size(52.dp)) {
        Icon(
          Icons.Filled.KeyboardArrowDown,
          contentDescription = stringResource(R.string.dock_show_player),
          tint = content,
          modifier = Modifier.size(28.dp),
        )
      }
    }
  }
}

/** Collapsed: art + prev + hero play + next + show-tabs, spread full width. */
@Composable
private fun DockTransportRow(
  artworkUrl: String,
  state: PlaybackUiState,
  onArtClick: () -> Unit,
  onPrevious: () -> Unit,
  onNext: () -> Unit,
  onToggle: () -> Unit,
  onShowTabs: () -> Unit,
  buttonsEnabled: Boolean,
  modeKey: Boolean,
) {
  val content = onDock()
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.small),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    MorphSlot(index = 0, modeKey = modeKey) {
      TrackArt(
        artworkUrl = artworkUrl,
        corner = 12.dp,
        modifier = Modifier
          .size(DockArtSize)
          .clip(RoundedCornerShape(12.dp))
          .clickable(onClick = onArtClick),
      )
    }
    Spacer(Modifier.width(MaterialTheme.spacing.small))
    Row(
      modifier = Modifier.weight(1f),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      MorphSlot(index = 1, modeKey = modeKey, modifier = Modifier.weight(1f)) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
          IconButton(onClick = onPrevious, enabled = buttonsEnabled, modifier = Modifier.size(52.dp)) {
            Icon(
              Icons.Filled.SkipPrevious,
              contentDescription = stringResource(R.string.transport_previous),
              tint = content,
              modifier = Modifier.size(30.dp),
            )
          }
        }
      }
      MorphSlot(index = 2, modeKey = modeKey, modifier = Modifier.weight(1f)) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
          when (state) {
            PlaybackUiState.Loading -> CircularProgressIndicator(
              modifier = Modifier.size(36.dp),
              color = content,
              trackColor = content.copy(alpha = 0.24f),
            )
            PlaybackUiState.Playing -> IconButton(
              onClick = onToggle,
              enabled = buttonsEnabled,
              modifier = Modifier.size(DockHeroTouch),
            ) {
              Icon(
                Icons.Filled.Pause,
                contentDescription = stringResource(R.string.transport_pause),
                tint = content,
                modifier = Modifier.size(40.dp),
              )
            }
            else -> IconButton(
              onClick = onToggle,
              // Idle with a queue means "tap to start"; Error means "tap to
              // retry". Only an unknown state sits disabled.
              enabled = buttonsEnabled &&
                (state == PlaybackUiState.Paused || state == PlaybackUiState.Idle ||
                  state is PlaybackUiState.Error),
              modifier = Modifier.size(DockHeroTouch),
            ) {
              Icon(
                Icons.Filled.PlayArrow,
                contentDescription = stringResource(R.string.transport_play),
                tint = content,
                modifier = Modifier.size(40.dp),
              )
            }
          }
        }
      }
      MorphSlot(index = 3, modeKey = modeKey, modifier = Modifier.weight(1f)) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
          IconButton(onClick = onNext, enabled = buttonsEnabled, modifier = Modifier.size(52.dp)) {
            Icon(
              Icons.Filled.SkipNext,
              contentDescription = stringResource(R.string.transport_next),
              tint = content,
              modifier = Modifier.size(30.dp),
            )
          }
        }
      }
      MorphSlot(index = 4, modeKey = modeKey, modifier = Modifier.weight(1f)) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
          IconButton(onClick = onShowTabs, enabled = buttonsEnabled, modifier = Modifier.size(52.dp)) {
            Icon(
              Icons.Filled.KeyboardArrowUp,
              contentDescription = stringResource(R.string.dock_show_navigation),
              tint = content,
              modifier = Modifier.size(28.dp),
            )
          }
        }
      }
    }
  }
}

/** Expanded: icon-only tabs + show-player. No labels; TalkBack carries names. */
@Composable
private fun DockTabsRow(
  selected: Int,
  onSelect: (Int) -> Unit,
  onCollapse: () -> Unit,
  showCollapse: Boolean,
  isPlaying: Boolean,
  buttonsEnabled: Boolean,
  modeKey: Boolean,
) {
  val content = onDock()
  val dim = onDockDim()
  val pill = dockSelectedPill()
  val tabs = NavTab.entries
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = MaterialTheme.spacing.small, vertical = MaterialTheme.spacing.small),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    tabs.forEachIndexed { index, tab ->
      val isSelected = selected == index
      MorphSlot(index = index, modeKey = modeKey, modifier = Modifier.weight(1f)) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
          Surface(
            onClick = {
              if (index == selected) onCollapse() else onSelect(index)
            },
            enabled = buttonsEnabled,
            shape = RoundedCornerShape(20.dp),
            color = if (isSelected) pill else Color.Transparent,
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.Center,
            ) {
              Icon(
                tab.icon,
                contentDescription = stringResource(tab.labelRes),
                tint = if (isSelected) content else dim,
                modifier = Modifier.size(26.dp),
              )
              if (isSelected && isPlaying) {
                Spacer(Modifier.width(6.dp))
                PlayingIndicator(color = content)
              }
            }
          }
        }
      }
    }
    if (showCollapse) {
      MorphSlot(index = 4, modeKey = modeKey, modifier = Modifier.weight(1f)) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
          IconButton(onClick = onCollapse, enabled = buttonsEnabled, modifier = Modifier.size(52.dp)) {
            Icon(
              Icons.Filled.KeyboardArrowDown,
              contentDescription = stringResource(R.string.dock_show_player),
              tint = content,
              modifier = Modifier.size(28.dp),
            )
          }
        }
      }
    }
  }
}

/**
 * One morphing slot: outside-in stagger (edges first, 20ms apart) on every
 * mode change, no-bounce scale + short fade. Entrance also plays on first
 * appearance.
 */
@Composable
private fun MorphSlot(
  index: Int,
  modeKey: Boolean,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  var entered by remember(modeKey) { mutableStateOf(false) }
  LaunchedEffect(modeKey) { entered = true }
  val delayMs = min(index, 4 - index) * 20
  val alpha by animateFloatAsState(
    targetValue = if (entered) 1f else 0f,
    animationSpec = tween(
      durationMillis = 280,
      delayMillis = delayMs,
      easing = FastOutSlowInEasing,
    ),
    label = "slotAlpha",
  )
  val scale by animateFloatAsState(
    targetValue = if (entered) 1f else 0.92f,
    animationSpec = spring(
      dampingRatio = Spring.DampingRatioNoBouncy,
      stiffness = Spring.StiffnessMediumLow,
    ),
    label = "slotScale",
  )
  Box(
    modifier = modifier.graphicsLayer {
      this.alpha = alpha
      scaleX = scale
      scaleY = scale
    },
    contentAlignment = Alignment.Center,
  ) {
    content()
  }
}
