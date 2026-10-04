package app.rayik.music.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.rayik.music.ui.theme.spacing

@Composable
fun RayikNav() {
  var tab by rememberSaveable { mutableIntStateOf(0) }
  var dockExpanded by rememberSaveable { mutableStateOf(false) }
  var playerOpen by rememberSaveable { mutableStateOf(false) }
  // Inset-sheet layout: content is a rounded sheet sitting on the single
  // morphing dock (transport <-> icon tabs). No floating pill, no FOLDERS
  // tab (local files are pinned-offline fallback only), no player tab (the
  // dock art opens the full player overlay).
  val dock = dockContainer()
  Scaffold(
    containerColor = dock,
    contentColor = MaterialTheme.colorScheme.onSurface,
    bottomBar = {
      MiniPlayer(
        onOpenPlayer = { playerOpen = true },
        expanded = dockExpanded,
        onExpandedChange = { dockExpanded = it },
        selectedTab = tab,
        onSelectTab = { tab = it },
      )
    }
  ) { inner ->
    Box(
      Modifier.fillMaxSize().padding(inner),
    ) {
      Surface(
        modifier = Modifier.fillMaxSize(),
        shape = dockSheetShape(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
      ) {
        Column(
          Modifier.fillMaxSize()
            .padding(horizontal = MaterialTheme.spacing.medium)
            .padding(top = MaterialTheme.spacing.medium),
          // No bottom padding here: each tab screen owns bottom clearance
          // (DockSheetBottomRadius + large) so its last row clears the
          // sheet's 28dp bottom curve instead of clipping into the dock.
        ) {
          when (NavTab.entries[tab]) {
            NavTab.Raay -> RaayHomeScreen(onPlayStarted = {})
            NavTab.Search -> SearchScreen(onPlayStarted = {})
            NavTab.Library -> LibraryScreen()
            NavTab.Settings -> SettingsScreen()
          }
        }
      }
    }
  }

  // Full-screen player overlay in the activity window (true edge-to-edge:
  // artwork bleeds behind status + gesture bars). No ModalBottomSheet — its
  // dialog window can never go edge-to-edge, which painted the black band
  // above the old player.
  BackHandler(enabled = playerOpen) { playerOpen = false }
  AnimatedVisibility(
    visible = playerOpen,
    enter = slideInVertically(
      initialOffsetY = { it },
      animationSpec = tween(380, easing = FastOutSlowInEasing),
    ) + fadeIn(animationSpec = tween(280)),
    exit = slideOutVertically(
      targetOffsetY = { it },
      animationSpec = tween(320, easing = FastOutSlowInEasing),
    ) + fadeOut(animationSpec = tween(220)),
    label = "playerOverlay",
  ) {
    NowPlayingSheetContent(onCollapse = { playerOpen = false })
  }
}
