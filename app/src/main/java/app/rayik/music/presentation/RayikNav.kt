package app.rayik.music.presentation

import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.rayik.music.preferences.preference.collectAsState
import app.rayik.music.ui.theme.spacing
import kotlinx.coroutines.delay

/** Apple settle: 500ms beat after content, then a soft ~800ms spring rise. */
private const val DOCK_INTRO_HOLD_MS = 500L

@Composable
fun RayikNav() {
  var tab by rememberSaveable { mutableIntStateOf(0) }
  var dockExpanded by rememberSaveable { mutableStateOf(false) }
  var playerOpen by rememberSaveable { mutableStateOf(false) }
  // Dock slide-up intro (once per cold start): content loads fullscreen
  // square, holds a 500ms beat, then the dock springs up while the sheet
  // slides onto it. One shared progress drives both layers so they can
  // never desync a frame.
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
  // Measured stretch for the fullscreen-square hold, snapshotted once the
  // intro starts so a late remeasure (rotation, dock toggle) can never move
  // the sheet mid-flight. Before the snapshot the sheet follows the live
  // measurement, so the first visible frame is already at the right scale
  // and never slides down into it.
  var introScale0 by remember { mutableFloatStateOf(1f) }
  var introArmed by remember { mutableStateOf(false) }
  val layoutReady = sheetHeightPx > 0 && dockHeightPx > 0
  val liveScale0 = if (layoutReady) {
    (sheetHeightPx + dockHeightPx).toFloat() / sheetHeightPx.toFloat()
  } else {
    1f
  }
  // Fallback so Loading/Unavailable can never strand the dock hidden.
  LaunchedEffect(Unit) {
    delay(3000)
    contentReady = true
  }
  LaunchedEffect(contentReady, dockHeightPx, sheetHeightPx) {
    if (introPlayed || animationsOff) return@LaunchedEffect
    if (!contentReady || !layoutReady) return@LaunchedEffect
    introScale0 = liveScale0
    introArmed = true
    delay(DOCK_INTRO_HOLD_MS)
    intro.animateTo(
      1f,
      spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow),
    )
    introPlayed = true
  }
  // Floating dock: the bar detaches into a pill, so the Scaffold bed
  // behind it (and the gesture strip) goes home-surface — the pill
  // keeps dock colors and reads as floating in light and dark.
  val floatingDock by rayikPreferences().floatingDock.collectAsState()
  val eased = intro.value
  // Invisible dock sits translated fully below the screen (off-screen =
  // untouchable, since graphicsLayer moves hit bounds too).
  val dockAlpha = (eased * 1.25f).coerceIn(0f, 1f)
  // Frozen once armed; live before that so the hold state tracks measurement
  // instead of jumping 1f -> scale0 on the visible content.
  val startScale = if (introArmed) introScale0 else liveScale0
  val sheetScaleY = 1f + (startScale - 1f) * (1f - eased)
  val sheetBottom = if (floatingDock) 0.dp else DockSheetBottomRadius * eased
  // Inset-sheet layout: content is a rounded sheet sitting on the single
  // morphing dock (transport <-> icon tabs). No floating pill, no FOLDERS
  // tab (local files are pinned-offline fallback only), no player tab (the
  // dock art opens the full player overlay).
  val dock = dockContainer()
  Scaffold(
    containerColor = if (floatingDock) MaterialTheme.colorScheme.surface else dock,
    contentColor = MaterialTheme.colorScheme.onSurface,
    contentWindowInsets = ScaffoldDefaults.contentWindowInsets.exclude(WindowInsets.statusBars),
    bottomBar = {
      // Docked only: the bar reserves layout space and the sheet sits on
      // it. Floating renders as an overlay below so content draws behind
      // and beside the pill.
      if (!floatingDock) {
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
            floatingDock = false,
          )
        }
      }
    }
  ) { inner ->
    // Immersive status: the surface sheet runs full-bleed behind the
    // status bar (same home color in light + dark) instead of leaving the
    // dark dock container showing through. Only the status inset is
    // excluded; everything else stays so the sheet's bottom curve still
    // sits on the dock. Content position is unchanged: the Column
    // re-applies the status inset inside the surface.
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
            .statusBarsPadding()
            .padding(horizontal = MaterialTheme.spacing.medium)
            .padding(top = MaterialTheme.spacing.medium),
          // No bottom padding here: each tab screen owns bottom clearance
          // (DockSheetBottomRadius + large docked, DockFloatingClearance
          // floating) so its last row clears the dock instead of clipping.
        ) {
          // Hold tab content until the sheet + dock are measured: the first
          // visible frame is then already at the fullscreen-square scale and
          // the feed can never slide down into it. Costs 1-2 blank frames.
          if (layoutReady) {
            when (NavTab.entries[tab]) {
              NavTab.Raay -> RaayHomeScreen(onPlayStarted = {}, onContentReady = { contentReady = true })
              NavTab.Search -> SearchScreen(onPlayStarted = {})
              NavTab.Library -> LibraryScreen()
              NavTab.Settings -> SettingsScreen()
            }
          }
        }
      }
      // Floating dock: overlay pill above the content (M3 sample pattern:
      // BottomCenter + ScreenOffset + zIndex) so lists draw behind and
      // beside it. Same slide-up intro as the docked bar.
      if (floatingDock) {
        Box(
          Modifier
            .align(Alignment.BottomCenter)
            .offset(y = -FloatingToolbarDefaults.ScreenOffset)
            .zIndex(1f)
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
            floatingDock = true,
          )
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
