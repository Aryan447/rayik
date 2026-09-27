package app.rayik.music.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.rayik.music.R
import app.rayik.music.lyrics.LrcParser
import app.rayik.music.ui.theme.spacing
import coil3.compose.AsyncImage

/**
 * Full-screen synced lyrics in the Apple Music spirit: blurred artwork
 * backdrop, small art thumbnail, and big bold lines where the active one
 * glows white while past/upcoming lines rest dimmed.
 */
@Composable
fun ImmersiveLyrics(
  raw: String?,
  positionMs: Long,
  artworkUrl: String,
  onClose: () -> Unit,
) {
  val lines = remember(raw) {
    if (raw.isNullOrBlank() || raw == "LYRICS_NOT_FOUND") {
      emptyList()
    } else {
      LrcParser.parseLyrics(raw)
    }
  }
  val plainText = remember(raw) {
    if (lines.isEmpty() && !raw.isNullOrBlank() && raw != "LYRICS_NOT_FOUND") {
      LrcParser.displayLyricsText(raw)
    } else {
      ""
    }
  }
  val active = remember(lines, positionMs) { activeLyricIndex(lines, positionMs) }
  val listState = rememberLazyListState()

  LaunchedEffect(active) {
    if (active >= 0) {
      listState.animateScrollToItem(maxOf(0, active - 1))
    }
  }
  BackHandler(onBack = onClose)

  Box(Modifier.fillMaxSize()) {
    // Blurred artwork bleed + legibility scrims.
    if (artworkUrl.isNotBlank()) {
      AsyncImage(
        model = artworkUrl,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
          .matchParentSize()
          .blur(48.dp),
      )
    }
    Box(
      Modifier.matchParentSize().background(
        Brush.verticalGradient(
          0f to Color.Black.copy(alpha = 0.45f),
          0.5f to Color.Black.copy(alpha = 0.55f),
          1f to Color.Black.copy(alpha = 0.72f),
        ),
      ),
    )

    BoxWithConstraints(Modifier.fillMaxSize()) {
      val edgePadding = maxHeight * 0.28f
      Column(Modifier.fillMaxSize()) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(
              horizontal = MaterialTheme.spacing.large,
              vertical = MaterialTheme.spacing.small,
            ),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Surface(
            onClick = onClose,
            shape = androidx.compose.foundation.shape.CircleShape,
            color = Color.White.copy(alpha = 0.18f),
            modifier = Modifier.size(44.dp),
          ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
              Icon(
                Icons.Filled.Close,
                contentDescription = stringResource(R.string.action_collapse),
                tint = Color.White,
              )
            }
          }
          Spacer(Modifier.weight(1f))
          TrackArt(
            artworkUrl = artworkUrl,
            corner = 16.dp,
            modifier = Modifier.size(64.dp),
          )
        }

        if (lines.isEmpty()) {
          Column(
            modifier = Modifier
              .weight(1f)
              .fillMaxWidth()
              .padding(horizontal = MaterialTheme.spacing.extraLarge),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
          ) {
            Text(
              plainText.ifBlank { stringResource(R.string.lyrics_none) },
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.Bold,
              color = Color.White,
              textAlign = TextAlign.Center,
            )
          }
        } else {
          LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(
              horizontal = MaterialTheme.spacing.extraLarge,
              vertical = edgePadding,
            ),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
          ) {
            itemsIndexed(lines, key = { index, line -> "$index-${line.time}" }) { index, line ->
              val isActive = index == active
              val isPast = active >= 0 && index < active
              Text(
                line.text,
                fontSize = if (isActive) 34.sp else 28.sp,
                lineHeight = if (isActive) 42.sp else 36.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Start,
                color = when {
                  isActive -> Color.White
                  isPast -> Color.White.copy(alpha = 0.42f)
                  else -> Color.White.copy(alpha = 0.55f)
                },
                style = MaterialTheme.typography.headlineSmall.copy(
                  shadow = if (isActive) {
                    Shadow(color = Color.White.copy(alpha = 0.55f), offset = Offset.Zero, blurRadius = 28f)
                  } else {
                    null
                  },
                ),
                modifier = Modifier.fillMaxWidth(),
              )
            }
          }
        }
        Spacer(Modifier.height(MaterialTheme.spacing.extraLarge))
      }
    }
  }
}
