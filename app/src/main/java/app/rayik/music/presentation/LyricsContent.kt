package app.rayik.music.presentation

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.rayik.music.R
import app.rayik.music.lyrics.LrcParser
import app.rayik.music.lyrics.LyricsEntry
import app.rayik.music.ui.theme.spacing

/** Index of the line playing at [positionMs], or -1 when none matches yet. */
fun activeLyricIndex(lines: List<LyricsEntry>, positionMs: Long): Int =
  lines.indexOfLast { it.time <= positionMs }.takeIf { it >= 0 } ?: -1

/**
 * Spotify-style lyrics card for the player: a 3-line synced preview that
 * expands to the full follow-along view inline. No separate tab needed.
 */
@Composable
fun LyricsPreviewCard(
  raw: String?,
  positionMs: Long,
  expanded: Boolean,
  onToggleExpand: () -> Unit,
  modifier: Modifier = Modifier,
  onShareCard: (() -> Unit)? = null,
) {
  Surface(
    tonalElevation = 0.dp,
    shape = RoundedCornerShape(26.dp),
    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
    modifier = modifier
      .fillMaxWidth()
      .border(
        1.dp,
        androidx.compose.ui.graphics.Color.White.copy(alpha = 0.13f),
        RoundedCornerShape(26.dp),
      ),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(
          horizontal = MaterialTheme.spacing.large,
          vertical = MaterialTheme.spacing.medium,
        ),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          stringResource(R.string.lyrics_preview_title),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.weight(1f),
        )
        if (onShareCard != null) {
          androidx.compose.material3.IconButton(
            onClick = onShareCard,
            modifier = Modifier.size(40.dp),
          ) {
            Icon(
              imageVector = Icons.Filled.Share,
              contentDescription = stringResource(R.string.action_share),
              modifier = Modifier.size(20.dp),
            )
          }
        }
      }
      Spacer(Modifier.height(MaterialTheme.spacing.small))

      if (raw.isNullOrBlank() || raw == "LYRICS_NOT_FOUND") {
        Text(
          stringResource(R.string.lyrics_none),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return@Column
      }

      val lines = remember(raw) { LrcParser.parseLyrics(raw) }
      if (lines.isEmpty()) {
        Text(
          LrcParser.displayLyricsText(raw),
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = if (expanded) Int.MAX_VALUE else 4,
          overflow = TextOverflow.Ellipsis,
        )
      } else if (!expanded) {
        val active = activeLyricIndex(lines, positionMs)
        val preview = when {
          active < 0 -> lines.take(3)
          else -> lines.drop(active).take(3)
        }
        Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.smaller)) {
          preview.forEachIndexed { i, line ->
            Text(
              line.text,
              style = if (i == 0) {
                MaterialTheme.typography.titleMedium
              } else {
                MaterialTheme.typography.bodyMedium
              },
              fontWeight = if (i == 0) FontWeight.Bold else FontWeight.Normal,
              color = if (i == 0) {
                MaterialTheme.colorScheme.onSurface
              } else {
                MaterialTheme.colorScheme.onSurfaceVariant
              },
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      } else {
        LyricLines(
          lines = lines,
          positionMs = positionMs,
          modifier = Modifier.heightIn(max = 320.dp),
        )
      }

      if (lines.isNotEmpty() || LrcParser.displayLyricsText(raw).isNotBlank()) {
        TextButton(
          onClick = onToggleExpand,
          modifier = Modifier.align(Alignment.Start),
        ) {
          Icon(
            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = null,
          )
          Text(
            stringResource(
              if (expanded) R.string.lyrics_show_less else R.string.lyrics_show_more,
            ),
          )
        }
      }
    }
  }
}

/** Synced lines: the active line highlights and the list follows playback. */
@Composable
fun LyricLines(
  lines: List<LyricsEntry>,
  positionMs: Long,
  modifier: Modifier = Modifier,
) {
  val listState = rememberLazyListState()
  val active = remember(lines, positionMs) { activeLyricIndex(lines, positionMs) }

  LaunchedEffect(active) {
    if (active >= 0) listState.animateScrollToItem(maxOf(0, active - 2))
  }

  LazyColumn(
    state = listState,
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    itemsIndexed(lines, key = { index, line -> "$index-${line.time}" }) { index, line ->
      val isActive = index == active
      Text(
        line.text,
        style = if (isActive) {
          MaterialTheme.typography.titleMedium
        } else {
          MaterialTheme.typography.bodyLarge
        },
        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
        color = if (isActive) {
          MaterialTheme.colorScheme.primary
        } else {
          MaterialTheme.colorScheme.onSurfaceVariant
        },
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
      )
    }
  }
}
