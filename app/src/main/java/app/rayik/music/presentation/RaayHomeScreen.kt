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
import androidx.compose.runtime.Composable
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
@Composable
fun RaayHomeScreen() {
  ScreenScaffold(state = ScreenState.Ready, loadingText = "Tuning your morning mix…", onRetry = {}) {
    Column {
      HeroCard()
      Spacer(Modifier.height(MaterialTheme.spacing.medium))
      MixCard()
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

/**
 * Morning pick card mirroring the site's `.player-mock`: theme-aware conic
 * art tile ([BrandGradient.artSweepBrush] — the gradient that changes with
 * every theme), reason eyebrow, title, progress, and training chips.
 * Collapses to a column on narrow screens like the site's 560px breakpoint.
 */
@Composable
private fun MixCard() {
  Surface(
    shape = RoundedCornerShape(20.dp),
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    border = androidx.compose.foundation.BorderStroke(
      1.dp,
      MaterialTheme.colorScheme.outlineVariant,
    ),
    modifier = Modifier.fillMaxWidth(),
  ) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(MaterialTheme.spacing.large)) {
      val narrow = maxWidth < 560.dp
      if (narrow) {
        Column {
          MixArt(modifier = Modifier.fillMaxWidth().height(150.dp))
          Spacer(Modifier.height(MaterialTheme.spacing.medium))
          MixBody()
        }
      } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
          MixArt(modifier = Modifier.size(120.dp))
          Spacer(Modifier.width(MaterialTheme.spacing.medium))
          MixBody(modifier = Modifier.weight(1f))
        }
      }
    }
  }
}

/**
 * Art tile: conic `primary → secondary → tertiary → primary` sweep, so the
 * tile re-skins itself with the active theme (green-gold under Hacker,
 * wine-gold under Banarasi, …). Dark note glyph echoes the site's tile icon.
 */
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
private fun MixBody(modifier: Modifier = Modifier) {
  Column(modifier) {
    Text(
      "This morning • because you looped Arijit 12×",
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.tertiary,
      fontWeight = FontWeight.Bold,
    )
    Text(
      "Mehfil Mix — Rain Edition",
      style = MaterialTheme.typography.headlineSmall,
      modifier = Modifier.padding(top = MaterialTheme.spacing.small),
    )
    LinearProgressIndicator(
      progress = { 0.62f },
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = MaterialTheme.spacing.small),
      color = MaterialTheme.colorScheme.primary,
      trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    )
    MixChips()
  }
}

/**
 * Training chips as static labels (like the site's `.chip` spans): Play
 * reads hot in primary, the rest are outline chips. They become real actions
 * in plan.md Phase B when the pick wires to RaayRules — no dead buttons now.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MixChips() {
  FlowRow(
    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
  ) {
    Chip(label = "▶ Play", hot = true)
    Chip(label = "Something else")
    Chip(label = "Why this?")
    Chip(label = "More like this")
    Chip(label = "Never this")
  }
}

@Composable
private fun Chip(label: String, hot: Boolean = false) {
  if (hot) {
    Surface(
      shape = RoundedCornerShape(999.dp),
      color = MaterialTheme.colorScheme.primary,
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
