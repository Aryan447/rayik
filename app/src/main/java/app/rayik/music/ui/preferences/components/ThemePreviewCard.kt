package app.rayik.music.ui.preferences.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.rayik.music.ui.theme.AppShapes
import app.rayik.music.ui.theme.AppTheme

/**
 * Mini app-UI preview card. Ported from mpvium pattern (Glass special-case
 * dropped — rayik has no Glass theme).
 */
@Composable
fun ThemePreviewCard(
  theme: AppTheme,
  isSelected: Boolean,
  isDarkMode: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colorScheme = if (isDarkMode) theme.getDarkColorScheme() else theme.getLightColorScheme()

  val selectionColor = MaterialTheme.colorScheme.primary

  // Constant geometry — selection changes color only, never layout.
  val borderWidth = if (isSelected) 2.dp else 1.dp
  val borderColor = if (isSelected) selectionColor else colorScheme.outlineVariant

  Column(
    modifier = modifier
      .width(100.dp)
      .pointerInput(Unit) {
        detectTapGestures {
          onClick()
        }
      },
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Box(
      modifier = Modifier
        .size(width = 90.dp, height = 140.dp)
        .shadow(elevation = 4.dp, shape = AppShapes.cardShape)
        .clip(AppShapes.cardShape)
        .background(colorScheme.surface)
        .border(width = borderWidth, color = borderColor, shape = AppShapes.cardShape),
    ) {
      // A tiny version of the real card: serif title, one art block, one
      // pill — so Gold versus Hacker previews the actual app.
      Column(
        modifier = Modifier
          .matchParentSize()
          .background(colorScheme.background)
          .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Text(
          text = "Aa",
          style = MaterialTheme.typography.headlineSmall,
          color = colorScheme.onSurface,
          maxLines = 1,
        )
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(AppShapes.artShape)
            .background(colorScheme.primary.copy(alpha = 0.35f)),
        )
        Box(
          modifier = Modifier
            .size(width = 40.dp, height = 14.dp)
            .clip(AppShapes.pill)
            .background(colorScheme.primary),
        )
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(AppShapes.pill)
            .background(colorScheme.onSurface.copy(alpha = 0.18f)),
        )
      }
    }

    Spacer(modifier = Modifier.height(6.dp))

    Text(
      text = stringResource(theme.titleRes),
      style = MaterialTheme.typography.bodySmall,
      fontSize = 11.sp,
      fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
      color = if (isSelected) {
        MaterialTheme.colorScheme.primary
      } else {
        MaterialTheme.colorScheme.onSurface
      },
      textAlign = TextAlign.Center,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.fillMaxWidth()
    )
  }
}
