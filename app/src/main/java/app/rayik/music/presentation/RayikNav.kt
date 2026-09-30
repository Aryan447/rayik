package app.rayik.music.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.rayik.music.ui.theme.spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RayikNav() {
  var tab by rememberSaveable { mutableIntStateOf(0) }
  var dockExpanded by rememberSaveable { mutableStateOf(false) }
  var playerSheetOpen by rememberSaveable { mutableStateOf(false) }
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  // Inset-sheet layout: content is a rounded sheet sitting on the single
  // morphing dock (transport <-> icon tabs). No floating pill, no FOLDERS
  // tab (local files are pinned-offline fallback only), no player tab (the
  // dock art opens the full player sheet).
  val dock = dockContainer()
  Scaffold(
    containerColor = dock,
    contentColor = MaterialTheme.colorScheme.onSurface,
    // Status bars excluded: the light content surface draws edge-to-edge
    // behind them, so dark icons stay legible in light mode instead of
    // sitting on a dark dock-colored strip. The dock keeps its own
    // navigationBarsPadding internally.
    contentWindowInsets = WindowInsets.navigationBars,
    bottomBar = {
      MiniPlayer(
        onOpenPlayer = { playerSheetOpen = true },
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
            .statusBarsPadding()
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

  if (playerSheetOpen) {
    // No system drag handle: the sheet content already draws its own grab
    // pill, and two handles stacked reads broken, not premium.
    // Explicit surface container + translucent scrim so the status-bar gap
    // above the 94%-height sheet never reads as opaque black.
    ModalBottomSheet(
      onDismissRequest = { playerSheetOpen = false },
      sheetState = sheetState,
      dragHandle = {},
      containerColor = MaterialTheme.colorScheme.surface,
      scrimColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f),
    ) {
      NowPlayingSheetContent(onCollapse = { playerSheetOpen = false })
    }
  }
}
