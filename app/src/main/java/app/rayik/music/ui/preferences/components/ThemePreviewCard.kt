package app.rayik.music.ui.preferences.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

  val borderWidth = if (isSelected) 3.dp else 1.dp
  val borderColor = if (isSelected) selectionColor else Color.Transparent
  val elevation = if (isSelected) 8.dp else 2.dp

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
        .shadow(
          elevation = elevation,
          shape = RoundedCornerShape(12.dp),
          ambientColor = if (isSelected) selectionColor.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.2f),
          spotColor = if (isSelected) selectionColor.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.2f),
        )
        .clip(RoundedCornerShape(12.dp))
        .background(Brush.verticalGradient(listOf(colorScheme.surface, colorScheme.surface)))
        .border(
          width = borderWidth,
          color = borderColor,
          shape = RoundedCornerShape(12.dp)
        ),
    ) {
      Column(
        modifier = Modifier
          .matchParentSize()
          .padding(if (isSelected) 3.dp else 1.dp)
          .clip(RoundedCornerShape(if (isSelected) 9.dp else 11.dp))
          .background(colorScheme.background)
          .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.SpaceBetween,
      ) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(16.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(colorScheme.surfaceVariant)
        )

        Surface(
          modifier = Modifier
            .fillMaxWidth()
            .height(32.dp),
          color = colorScheme.surfaceVariant,
          shape = RoundedCornerShape(6.dp),
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Box(
              modifier = Modifier
                .size(width = 24.dp, height = 12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(colorScheme.primary)
            )
            Box(
              modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(colorScheme.tertiary)
            )
          }
        }

        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(14.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(colorScheme.surfaceVariant)
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.Center,
        ) {
          Box(
            modifier = Modifier
              .size(10.dp)
              .clip(CircleShape)
              .background(colorScheme.secondary)
          )
        }
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
