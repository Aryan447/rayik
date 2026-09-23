package app.rayik.music.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.hilt.navigation.compose.hiltViewModel
import app.rayik.music.player.PlaybackUiState
import app.rayik.music.player.PlayerViewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.rayik.music.ui.theme.BrandGradient
import app.rayik.music.ui.theme.spacing

/**
 * Raay home: brand hero (site gradients + EQ mark) above the opinionated
 * morning pick with its reason attached. Recommendations resolve with the
 * streaming layer; the slot and its copy contract already live here.
 */
data class RaayPick(
  val title: String,
  val reason: String,
  val videoId: String,
  val trackTitle: String,
  val trackArtist: String,
  val mood: String,
)

private val RAAY_PICKS = listOf(
  RaayPick(
    title = "Mehfil Mix — Rain Edition",
    reason = "This morning • because you looped Arijit 12×",
    videoId = "BddP6PYo2gs",
    trackTitle = "Kesariya",
    trackArtist = "Pritam, Arijit Singh",
    mood = "Mehfil",
  ),
  RaayPick(
    title = "Monsoon Rain Session",
    reason = "Grey skies, wet earth, acoustic strings",
    videoId = "MJyKN-8UncM",
    trackTitle = "Shayad",
    trackArtist = "Pritam, Arijit Singh",
    mood = "Rain",
  ),
  RaayPick(
    title = "Deep Focus Flow",
    reason = "Zero distraction, repetitive cadence",
    videoId = "6mr4cYJ7yew",
    trackTitle = "Kesariya (Film Version)",
    trackArtist = "Pritam, Arijit Singh",
    mood = "Focus",
  ),
  RaayPick(
    title = "Late Night Drive",
    reason = "Empty highways, cool breeze",
    videoId = "O5gwxm3NxFU",
    trackTitle = "Best Of Arijit Singh",
    trackArtist = "Arijit Singh",
    mood = "Drive",
  ),
)

@Composable
fun RaayHomeScreen(
  onPlayStarted: () -> Unit = {},
  player: PlayerViewModel = hiltViewModel(),
) {
  var pickIndex by rememberSaveable { mutableIntStateOf(0) }
  var starting by rememberSaveable { mutableStateOf(false) }
  var startError by rememberSaveable { mutableStateOf<String?>(null) }

  val currentMediaId by player.currentMediaId.collectAsState()
  val playbackState by player.playbackState.collectAsState()
  val positionMs by player.positionMs.collectAsState()
  val durationMs by player.durationMs.collectAsState()
  val connected by player.connected.collectAsState()

  val currentPick = RAAY_PICKS[pickIndex % RAAY_PICKS.size]
  val isCurrentPlaying = currentMediaId == currentPick.videoId

  fun playPick(pick: RaayPick) {
    if (starting) return
    if (currentMediaId == pick.videoId) {
      player.togglePlayPause()
      return
    }
    if (!connected) {
      startError = "Player isn't connected yet — try again in a moment"
      return
    }
    starting = true
    startError = null
    // The service resolves the stream (PO-token mint, decipher, fallback
    // walk); taps never resolve URLs on the UI thread anymore.
    player.playVideo(pick.videoId, pick.trackTitle, pick.trackArtist)
    starting = false
    onPlayStarted()
  }

  ScreenScaffold(state = ScreenState.Ready, loadingText = "Tuning your morning mix…", onRetry = {}) {
    Column {
      HeroCard()
      Spacer(Modifier.height(MaterialTheme.spacing.medium))
      MixCard(
        pick = currentPick,
        resolving = starting,
        isPlaying = isCurrentPlaying && playbackState == PlaybackUiState.Playing,
        progress = if (isCurrentPlaying && durationMs > 0) {
          (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
        } else {
          0.62f
        },
        onPlayClick = { playPick(currentPick) },
        onNextPick = {
          pickIndex = (pickIndex + 1) % RAAY_PICKS.size
          startError = null
        },
        onSelectMood = { mood ->
          val found = RAAY_PICKS.indexOfFirst { it.mood.equals(mood, ignoreCase = true) }
          if (found >= 0) {
            pickIndex = found
            playPick(RAAY_PICKS[found])
          }
        },
      )
      startError?.let { err ->
        Spacer(Modifier.height(MaterialTheme.spacing.small))
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            err,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f),
          )
          // The service owns resolve + retry now; "Try again" just
          // re-issues the tap.
          if (!starting) {
            TextButton(onClick = { playPick(currentPick) }) {
              Text("Try again")
            }
          }
        }
      }
    }
  }
}

@Composable
private fun HeroCard() {
  Surface(
    shape = RoundedCornerShape(24.dp),
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .background(BrandGradient.heroGlowBrush())
        .padding(MaterialTheme.spacing.large),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        RayikMark(modifier = Modifier.size(56.dp))
        Spacer(Modifier.width(MaterialTheme.spacing.medium))
        Column {
          Text(
            "rāyik",
            style = MaterialTheme.typography.displaySmall.copy(
              brush = BrandGradient.goldVerticalBrush,
              fontWeight = FontWeight.Bold,
            ),
          )
          Text(
            "Music with opinions.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.tertiary,
            fontWeight = FontWeight.SemiBold,
          )
        }
      }
    }
  }
}

@Composable
private fun MixCard(
  pick: RaayPick,
  resolving: Boolean,
  isPlaying: Boolean,
  progress: Float,
  onPlayClick: () -> Unit,
  onNextPick: () -> Unit,
  onSelectMood: (String) -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(20.dp),
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    border = androidx.compose.foundation.BorderStroke(
      1.dp,
      MaterialTheme.colorScheme.outlineVariant,
    ),
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(20.dp)),
  ) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(MaterialTheme.spacing.large)) {
      val narrow = maxWidth < 560.dp
      if (narrow) {
        Column {
          MixArt(
            modifier = Modifier
              .fillMaxWidth()
              .height(150.dp)
              .clickable(onClick = onPlayClick),
          )
          Spacer(Modifier.height(MaterialTheme.spacing.medium))
          MixBody(
            pick = pick,
            resolving = resolving,
            isPlaying = isPlaying,
            progress = progress,
            onPlayClick = onPlayClick,
            onNextPick = onNextPick,
            onSelectMood = onSelectMood,
          )
        }
      } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
          MixArt(
            modifier = Modifier
              .size(120.dp)
              .clickable(onClick = onPlayClick),
          )
          Spacer(Modifier.width(MaterialTheme.spacing.medium))
          MixBody(
            modifier = Modifier.weight(1f),
            pick = pick,
            resolving = resolving,
            isPlaying = isPlaying,
            progress = progress,
            onPlayClick = onPlayClick,
            onNextPick = onNextPick,
            onSelectMood = onSelectMood,
          )
        }
      }
    }
  }
}

@Composable
private fun MixArt(modifier: Modifier = Modifier) {
  Box(
    modifier
      .clip(RoundedCornerShape(16.dp))
      .background(BrandGradient.artSweepBrush())
      .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      Icons.Filled.MusicNote,
      contentDescription = null,
      tint = Color(0xFF14100A).copy(alpha = 0.88f),
      modifier = Modifier.size(40.dp),
    )
  }
}

@Composable
private fun MixBody(
  modifier: Modifier = Modifier,
  pick: RaayPick,
  resolving: Boolean,
  isPlaying: Boolean,
  progress: Float,
  onPlayClick: () -> Unit,
  onNextPick: () -> Unit,
  onSelectMood: (String) -> Unit,
) {
  Column(modifier) {
    Text(
      pick.reason,
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.tertiary,
      fontWeight = FontWeight.Bold,
    )
    Text(
      pick.title,
      style = MaterialTheme.typography.headlineSmall,
      modifier = Modifier.padding(top = MaterialTheme.spacing.small),
    )
    LinearProgressIndicator(
      progress = { progress },
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = MaterialTheme.spacing.small),
      color = MaterialTheme.colorScheme.primary,
      trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    )
    MixChips(
      resolving = resolving,
      isPlaying = isPlaying,
      onPlayClick = onPlayClick,
      onNextPick = onNextPick,
      onSelectMood = onSelectMood,
    )
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MixChips(
  resolving: Boolean,
  isPlaying: Boolean,
  onPlayClick: () -> Unit,
  onNextPick: () -> Unit,
  onSelectMood: (String) -> Unit,
) {
  FlowRow(
    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
  ) {
    val playLabel = when {
      resolving -> "Tuning…"
      isPlaying -> "⏸ Pause"
      else -> "▶ Play"
    }
    Chip(label = playLabel, hot = true, onClick = onPlayClick)
    Chip(label = "Something else", onClick = onNextPick)
    Chip(label = "Mehfil", onClick = { onSelectMood("Mehfil") })
    Chip(label = "Rain", onClick = { onSelectMood("Rain") })
    Chip(label = "Focus", onClick = { onSelectMood("Focus") })
    Chip(label = "Drive", onClick = { onSelectMood("Drive") })
  }
}

@Composable
private fun Chip(
  label: String,
  hot: Boolean = false,
  onClick: () -> Unit = {},
) {
  if (hot) {
    Surface(
      shape = RoundedCornerShape(999.dp),
      color = MaterialTheme.colorScheme.primary,
      modifier = Modifier
        .clip(RoundedCornerShape(999.dp))
        .clickable(onClick = onClick),
    ) {
      Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.padding(
          horizontal = MaterialTheme.spacing.medium,
          vertical = MaterialTheme.spacing.small,
        ),
      )
    }
  } else {
    Surface(
      shape = RoundedCornerShape(999.dp),
      color = Color.Transparent,
      border = androidx.compose.foundation.BorderStroke(
        1.dp,
        MaterialTheme.colorScheme.outlineVariant,
      ),
      modifier = Modifier
        .clip(RoundedCornerShape(999.dp))
        .clickable(onClick = onClick),
    ) {
      Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(
          horizontal = MaterialTheme.spacing.medium,
          vertical = MaterialTheme.spacing.small,
        ),
      )
    }
  }
}

/**
 * Brand mark from `docs/index.html`: five gold studio-EQ bars inside the
 * acoustic ring on the dark radial tile. Symmetric, so RTL-safe.
 */
@Composable
private fun RayikMark(modifier: Modifier = Modifier) {
  Canvas(
    modifier
      .clip(RoundedCornerShape(16.dp))
      .background(BrandGradient.markBackdropBrush),
  ) {
    val unit = size.width / 108f
    drawCircle(
      brush = BrandGradient.ringDiagonalBrush,
      radius = 29f * unit,
      center = center,
      alpha = 0.35f,
      style = Stroke(width = 1.4f * unit),
    )
    val barWidth = 5.5f * unit
    val barXs = listOf(31.25f, 41.25f, 51.25f, 61.25f, 71.25f)
    val barHeights = listOf(20f, 34f, 48f, 34f, 20f)
    barXs.forEachIndexed { i, x ->
      val barHeight = barHeights[i] * unit
      drawRoundRect(
        brush = BrandGradient.goldVerticalBrush,
        topLeft = Offset(x * unit, center.y - barHeight / 2f),
        size = Size(barWidth, barHeight),
        cornerRadius = CornerRadius(2.75f * unit, 2.75f * unit),
      )
    }
  }
}
