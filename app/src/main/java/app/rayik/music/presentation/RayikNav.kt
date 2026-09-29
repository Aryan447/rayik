package app.rayik.music.presentation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
  // Inset-sheet layout: content is a rounded sheet sitting on the dark/grey
  // dock. Player-only in dock (per plan); the floating nav pill stays over
  // the content, never on the dock.
  // No FOLDERS tab: local files are pinned-offline fallback only.
  // No player tab either: the mini-player opens the full player sheet.
  val dock = dockContainer()
  Scaffold(
    containerColor = dock,
    contentColor = MaterialTheme.colorScheme.onSurface,
    bottomBar = {
      MiniPlayer(onOpenPlayer = { playerSheetOpen = true })
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
        Box(Modifier.fillMaxSize()) {
          Column(
            Modifier.fillMaxSize().padding(MaterialTheme.spacing.medium)
              // Clearance so the floating pill never covers list content.
              .padding(bottom = 96.dp),
          ) {
            when (tabs[tab]) {
              Tab.Raay -> RaayHomeScreen(onPlayStarted = {})
              Tab.Search -> SearchScreen(onPlayStarted = {})
              Tab.Library -> LibraryScreen()
              Tab.Settings -> SettingsScreen()
            }
          }
          FloatingNavPill(
            selected = tab,
            onSelect = { tab = it },
            modifier = Modifier.align(Alignment.BottomCenter),
          )
        }
      }
    }
  }

  if (playerSheetOpen) {
    // No system drag handle: the sheet content already draws its own grab
    // pill, and two handles stacked reads broken, not premium.
    ModalBottomSheet(
      onDismissRequest = { playerSheetOpen = false },
      sheetState = sheetState,
      dragHandle = {},
    ) {
      NowPlayingSheetContent(onCollapse = { playerSheetOpen = false })
    }
  }
}

/**
 * The standard M3 Expressive floating dock: [HorizontalFloatingToolbar]
 * with the official container shape and opaque standard colors, so feed
 * content can never bleed through it. Springy per-tab selection kept in
 * our theme colors.
 */
@Composable
private fun FloatingNavPill(
  selected: Int,
  onSelect: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  val tabs = Tab.entries
  HorizontalFloatingToolbar(
    expanded = true,
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 20.dp, vertical = 10.dp),
    colors = FloatingToolbarDefaults.standardFloatingToolbarColors(),
    shape = FloatingToolbarDefaults.ContainerShape,
  ) {
    tabs.forEachIndexed { index, tab ->
      DockTab(
        selected = selected == index,
        tab = tab,
        onClick = { onSelect(index) },
      )
    }
  }
}

@Composable
private fun RowScope.DockTab(
  selected: Boolean,
  tab: Tab,
  onClick: () -> Unit,
) {
  val label = stringResource(tab.labelRes)
  val container by animateColorAsState(
    targetValue = if (selected) {
      MaterialTheme.colorScheme.primaryContainer
    } else {
      Color.Transparent
    },
    animationSpec = spring(
      dampingRatio = Spring.DampingRatioMediumBouncy,
      stiffness = Spring.StiffnessMediumLow,
    ),
    label = "navPill",
  )
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(22.dp),
    color = container,
    modifier = Modifier.weight(1f),
  ) {
    Column(
      modifier = Modifier.padding(vertical = 8.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Icon(
        tab.icon,
        contentDescription = label,
        tint = if (selected) {
          MaterialTheme.colorScheme.onPrimaryContainer
        } else {
          MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier.size(24.dp),
      )
      Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        color = if (selected) {
          MaterialTheme.colorScheme.onPrimaryContainer
        } else {
          MaterialTheme.colorScheme.onSurfaceVariant
        },
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}
