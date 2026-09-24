package app.rayik.music.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import app.rayik.music.R
import app.rayik.music.ui.theme.spacing

private enum class Tab(val labelRes: Int, val icon: ImageVector) {
  Raay(R.string.tab_raay, Icons.Filled.Home),
  Search(R.string.tab_search, Icons.Filled.Search),
  Library(R.string.tab_library, Icons.Filled.LibraryMusic),
  Settings(R.string.tab_settings, Icons.Filled.Settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RayikNav() {
  var tab by rememberSaveable { mutableIntStateOf(0) }
  var playerSheetOpen by rememberSaveable { mutableStateOf(false) }
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val tabs = Tab.entries
  // No FOLDERS tab: local files are pinned-offline fallback only.
  // No player tab either: the mini-player opens the full player sheet.
  Scaffold(
    bottomBar = {
      Column {
        MiniPlayer(onOpenPlayer = { playerSheetOpen = true })
        NavigationBar {
          tabs.forEachIndexed { i, t ->
            NavigationBarItem(
              selected = tab == i,
              onClick = { tab = i },
              icon = { Icon(t.icon, contentDescription = null) },
              label = { Text(stringResource(t.labelRes)) },
            )
          }
        }
      }
    }
  ) { inner ->
    Column(Modifier.fillMaxSize().padding(inner).padding(MaterialTheme.spacing.medium)) {
      when (tabs[tab]) {
        Tab.Raay -> RaayHomeScreen(onPlayStarted = {})
        Tab.Search -> SearchScreen(onPlayStarted = {})
        Tab.Library -> LibraryScreen()
        Tab.Settings -> SettingsScreen()
      }
    }
  }

  if (playerSheetOpen) {
    ModalBottomSheet(
      onDismissRequest = { playerSheetOpen = false },
      sheetState = sheetState,
    ) {
      NowPlayingSheetContent(onCollapse = { playerSheetOpen = false })
    }
  }
}
