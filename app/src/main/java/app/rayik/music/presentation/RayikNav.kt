package app.rayik.music.presentation

import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.rayik.music.ui.theme.spacing
import kotlinx.coroutines.delay

/** Emphasized-decelerate: fast lift, long gentle settle. No bounce. */
private val DockIntroEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private const val DOCK_INTRO_MS = 800

@Composable
fun RayikNav() {
  var tab by rememberSaveable { mutableIntStateOf(0) }
  var dockExpanded by rememberSaveable { mutableStateOf(false) }
  var playerOpen by rememberSaveable { mutableStateOf(false) }
  // Dock slide-up intro (once per cold start): content starts fullscreen
  // square, then the dock rises while the sheet shrinks onto it. One shared
  // progress drives both layers so they can never desync a frame.
  var introPlayed by rememberSaveable { mutableStateOf(false) }
  val context = LocalContext.current
  val animationsOff = remember {
    Settings.Global.getFloat(
      context.contentResolver,
      Settings.Global.ANIMATOR_DURATION_SCALE,
      1f,
    ) == 0f
  }
  val introStart = if (introPlayed || animationsOff) 1f else 0f
  val intro = remember { Animatable(introStart) }
  var contentReady by remember { mutableStateOf(false) }
  var dockHeightPx by remember { mutableIntStateOf(0) }
  var sheetHeightPx by remember { mutableIntStateOf(0) }
  // Fallback so Loading/Unavailable can never strand the dock hidden.
  LaunchedEffect(Unit) {
    delay(3000)
    contentReady = true
  }
  LaunchedEffect(contentReady, dockHeightPx) {
    if (introPlayed || animationsOff) return@LaunchedEffect
    if (!contentReady || dockHeightPx == 0) return@LaunchedEffect
    intro.animateTo(1f, tween(DOCK_INTRO_MS, easing = DockIntroEasing))
    introPlayed = true
  }
  val eased = intro.value
  // Invisible dock sits translated fully below the screen (off-screen =
  // untouchable, since graphicsLayer moves hit bounds too).
  val dockAlpha = (eased * 1.25f).coerceIn(0f, 1f)
  val scale0 = if (sheetHeightPx > 0 && dockHeightPx > 0) {
    (sheetHeightPx + dockHeightPx).toFloat() / sheetHeightPx.toFloat()
  } else {
    1f
  }
  val sheetScaleY = 1f + (scale0 - 1f) * (1f - eased)
  val sheetBottom = DockSheetBottomRadius * eased
  // Inset-sheet layout: content is a rounded sheet sitting on the single
  // morphing dock (transport <-> icon tabs). No floating pill, no FOLDERS
  // tab (local files are pinned-offline fallback only), no player tab (the
  // dock art opens the full player overlay).
  val dock = dockContainer()
  Scaffold(
    containerColor = dock,
    contentColor = MaterialTheme.colorScheme.onSurface,
    bottomBar = {
      Box(
        Modifier
          .onSizeChanged { dockHeightPx = it.height }
          .graphicsLayer {
            translationY = (1f - eased) * dockHeightPx.toFloat()
            alpha = dockAlpha
          },
      ) {
        MiniPlayer(
          onOpenPlayer = { playerOpen = true },
          expanded = dockExpanded,
          onExpandedChange = { dockExpanded = it },
          selectedTab = tab,
          onSelectTab = { tab = it },
        )
      }
    }
  ) { inner ->
    Box(
      Modifier.fillMaxSize().padding(inner)
        .onSizeChanged { sheetHeightPx = it.height },
    ) {
      Surface(
        // GPU-only shrink from the top: LazyColumn never remeasures
        // mid-flight, so the 800ms stays at a clean 60/120Hz.
        modifier = Modifier.fillMaxSize().graphicsLayer {
          scaleY = sheetScaleY
          transformOrigin = TransformOrigin(0.5f, 0f)
        },
        shape = RoundedCornerShape(
          bottomStart = sheetBottom,
          bottomEnd = sheetBottom,
        ),
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
            NavTab.Raay -> RaayHomeScreen(onPlayStarted = {}, onContentReady = { contentReady = true })
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
