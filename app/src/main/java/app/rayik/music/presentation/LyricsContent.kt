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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.rayik.music.R
import app.rayik.music.lyrics.LyricDisplayParser
import app.rayik.music.lyrics.LyricsEntry
import app.rayik.music.ui.theme.AppShapes
import app.rayik.music.ui.theme.BrandGradient
import app.rayik.music.ui.theme.RayikIcons
import app.rayik.music.ui.theme.spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first

/** Index of the line playing at [positionMs], or -1 when none matches yet. */
fun activeLyricIndex(lines: List<LyricsEntry>, positionMs: Long): Int =
  lines.indexOfLast { it.time <= positionMs }.takeIf { it >= 0 } ?: -1

/**
 * Interpolated lyric clock: position polls arrive every 500ms, which
 * quantizes karaoke to 2fps. Between polls this advances the last known
 * position every frame while playing, snapping back to truth on each
 * poll/seek/pause. Only rows that read the value recompose per frame.
 */
@Composable
fun rememberSmoothLyricPosition(positionMs: Long, isPlaying: Boolean): Long {
  var smooth by remember { mutableLongStateOf(positionMs) }
  LaunchedEffect(isPlaying, positionMs) {
    if (!isPlaying) {
      smooth = positionMs
      return@LaunchedEffect
    }
    val t0 = withFrameNanos { it }
    val p0 = positionMs
    while (true) {
      val now = withFrameNanos { it }
      smooth = p0 + (now - t0) / 1_000_000
    }
  }
  return smooth
}

/**
 * Glides [index] to just above the viewport's vertical center instead of
 * snapping its top edge into view. Late lines would otherwise sink to the
 * bottom when the list can't scroll past its end; the biased anchor plus
 * generous bottom padding keeps the active line readable up there. Far
 * jumps (seek/track change) land near the anchor instantly, then settle
 * exactly on the next frame — no fling, no overshoot.
 */
suspend fun LazyListState.centerLyricOn(index: Int) {
  if (index < 0) return
  if (layoutInfo.viewportSize.height <= 0) {
    snapshotFlow { layoutInfo.viewportSize.height }.filter { it > 0 }.first()
  }
  val viewportH = layoutInfo.viewportSize.height
  val anchor = (viewportH * LyricAnchorFraction).toInt()
  // Negative offset parks the item's top below the viewport top, i.e. the
  // item lands on the anchor: top at anchor - h/2.
  fun anchoredOffsetFor(size: Int) = -(anchor - size / 2)
  val visible = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
  if (visible != null) {
    animateScrollToItem(index, anchoredOffsetFor(visible.size))
  } else {
    animateScrollToItem(index, -(anchor - 120))
    withFrameNanos { }
    val settled = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
    if (settled != null) {
      animateScrollToItem(index, anchoredOffsetFor(settled.size))
    }
  }
}

/** Fraction of the viewport height where the active lyric line parks. */
private const val LyricAnchorFraction = 0.38f

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
  onOpenImmersive: (() -> Unit)? = null,
  onSeek: ((Long) -> Unit)? = null,
) {
  Surface(
    tonalElevation = 0.dp,
    shape = AppShapes.cardShape,
    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
    modifier = modifier
      .fillMaxWidth()
      .border(1.dp, BrandGradient.hairline(), AppShapes.cardShape),
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
        if (onOpenImmersive != null) {
          androidx.compose.material3.IconButton(
            onClick = onOpenImmersive,
            modifier = Modifier.size(40.dp),
          ) {
            Icon(
              imageVector = Icons.Outlined.Fullscreen,
              contentDescription = stringResource(R.string.lyrics_fullscreen),
              modifier = Modifier.size(20.dp),
            )
          }
        }
        if (onShareCard != null) {
          androidx.compose.material3.IconButton(
            onClick = onShareCard,
            modifier = Modifier.size(40.dp),
          ) {
            Icon(
              imageVector = RayikIcons.Share,
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

      val lines = remember(raw) { LyricDisplayParser.parseTimed(raw) }
      if (lines.isEmpty()) {
        Text(
          LyricDisplayParser.plainText(raw),
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
        // Crossfade between stanzas instead of hard-swapping the text.
        AnimatedContent(
          targetState = active,
          transitionSpec = {
            fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(220))
          },
          label = "lyricPreviewSwap",
        ) {
          Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.smaller)) {
            preview.forEachIndexed { i, line ->
              Text(
                line.text,
                style = if (i == 0) {
                  MaterialTheme.typography.titleMedium
                } else {
                  MaterialTheme.typography.bodyMedium
                },
                fontWeight = if (i == 0) FontWeight.SemiBold else FontWeight.Normal,
                color = if (i == 0) {
                  MaterialTheme.colorScheme.primary
                } else {
                  MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }
      } else {
        LyricLines(
          lines = lines,
          positionMs = positionMs,
          modifier = Modifier.heightIn(max = 320.dp),
          onSeek = onSeek,
        )
      }

      if (lines.isNotEmpty() || LyricDisplayParser.plainText(raw).isNotBlank()) {
        TextButton(
          onClick = onToggleExpand,
          modifier = Modifier.align(Alignment.Start),
        ) {
          Icon(
            imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
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

/**
 * Synced lines: the active line highlights and the list follows playback.
 * Tapping any line seeks the song there; style changes crossfade instead
 * of popping.
 */
@Composable
fun LyricLines(
  lines: List<LyricsEntry>,
  positionMs: Long,
  modifier: Modifier = Modifier,
  onSeek: ((Long) -> Unit)? = null,
) {
  val listState = rememberLazyListState()
  val active = remember(lines, positionMs) { activeLyricIndex(lines, positionMs) }

  var userScrolling by remember { mutableStateOf(false) }
  var resumePending by remember { mutableStateOf(false) }
  LaunchedEffect(listState) {
    listState.interactionSource.interactions.collect { interaction ->
      when (interaction) {
        is DragInteraction.Start -> {
          userScrolling = true
          resumePending = false
        }
        is DragInteraction.Stop, is DragInteraction.Cancel -> {
          userScrolling = false
          resumePending = true
        }
        else -> Unit
      }
    }
  }

  LaunchedEffect(resumePending) {
    if (resumePending) {
      delay(1_200)
      resumePending = false
    }
  }
  LaunchedEffect(active, userScrolling, resumePending) {
    if (!userScrolling && !resumePending) listState.centerLyricOn(active)
  }

  LazyColumn(
    state = listState,
    modifier = modifier.fillMaxWidth(),
    contentPadding = PaddingValues(bottom = 48.dp),
    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    itemsIndexed(lines, key = { index, line -> "$index-${line.time}" }) { index, line ->
      LyricRow(
        line = line,
        isActive = index == active,
        onSeek = onSeek?.let { seek -> { seek(line.time) } },
        modifier = Modifier.animateItem(),
      )
    }
  }
}

@Composable
private fun LyricRow(
  line: LyricsEntry,
  isActive: Boolean,
  onSeek: (() -> Unit)?,
  modifier: Modifier = Modifier,
) {
  val color by animateColorAsState(
    targetValue = if (isActive) {
      MaterialTheme.colorScheme.primary
    } else {
      MaterialTheme.colorScheme.onSurfaceVariant
    },
    animationSpec = LYRIC_FOLLOW_COLOR_SPEC,
    label = "lyricColor",
  )
  val scale by animateFloatAsState(
    targetValue = if (isActive) 1.04f else 1f,
    animationSpec = LYRIC_FOLLOW_FLOAT_SPEC,
    label = "lyricScale",
  )
  Text(
    line.text,
    style = if (isActive) {
      MaterialTheme.typography.titleMedium
    } else {
      MaterialTheme.typography.bodyLarge
    },
    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
    color = color,
    textAlign = TextAlign.Center,
    modifier = modifier
      .fillMaxWidth()
      .graphicsLayer {
        scaleX = scale
        scaleY = scale
      }
      .then(if (onSeek != null) Modifier.clickable(onClick = onSeek) else Modifier)
      .padding(horizontal = MaterialTheme.spacing.medium),
  )
}

private val LYRIC_FOLLOW_FLOAT_SPEC = tween<Float>(
  durationMillis = 450,
  easing = FastOutSlowInEasing,
)

private val LYRIC_FOLLOW_COLOR_SPEC = tween<Color>(
  durationMillis = 450,
  easing = FastOutSlowInEasing,
)
