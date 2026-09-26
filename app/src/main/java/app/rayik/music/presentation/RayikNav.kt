package app.rayik.music.presentation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
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
  // No FOLDERS tab: local files are pinned-offline fallback only.
  // No player tab either: the mini-player opens the full player sheet.
  Scaffold(
    bottomBar = {
      Column {
        MiniPlayer(onOpenPlayer = { playerSheetOpen = true })
        FloatingNavPill(
          selected = tab,
          onSelect = { tab = it },
        )
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

/**
 * Floating frosted nav pill: detached from the screen edges with a
 * translucent surface + hairline border, springy selection pills.
 * (True backdrop blur needs Haze — intentionally not vendored yet.)
 */
@Composable
private fun FloatingNavPill(
  selected: Int,
  onSelect: (Int) -> Unit,
) {
  val tabs = Tab.entries
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .navigationBarsPadding()
      .padding(horizontal = 20.dp, vertical = 10.dp),
    contentAlignment = Alignment.Center,
  ) {
    Surface(
      shape = RoundedCornerShape(30.dp),
      color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
      tonalElevation = 3.dp,
      shadowElevation = 12.dp,
      border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
      ) {
        tabs.forEachIndexed { index, tab ->
          val isSelected = selected == index
          val label = stringResource(tab.labelRes)
          val container by animateColorAsState(
            targetValue = if (isSelected) {
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
            onClick = { onSelect(index) },
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
                tint = if (isSelected) {
                  MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                  MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(24.dp),
              )
              Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) {
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
      }
    }
  }
}
