package app.rayik.music.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.rayik.music.R
import app.rayik.music.lyrics.LyricDisplayParser
import app.rayik.music.lyrics.LyricsEntry
import app.rayik.music.lyrics.WordTimestamp
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
  isPlaying: Boolean = true,
  onSeek: ((Long) -> Unit)? = null,
) {
  val lines = remember(raw) { LyricDisplayParser.parseTimed(raw) }
  val plainText = remember(raw) {
    if (lines.isEmpty()) LyricDisplayParser.plainText(raw) else ""
  }
  val active = remember(lines, positionMs) { activeLyricIndex(lines, positionMs) }
  // 60fps interpolated clock: word karaoke would otherwise step at the
  // 500ms poll cadence. Only the active row reads this, so only it
  // recomposes per frame.
  val smoothPositionMs = rememberSmoothLyricPosition(positionMs, isPlaying)
  val listState = rememberLazyListState()

  LaunchedEffect(active) {
    // Never fight the user's finger: a skipped line centers on the next change.
    if (!listState.isScrollInProgress) listState.centerLyricOn(active)
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
              ImmersiveLyricRow(
                line = line,
                isActive = index == active,
                isPast = active >= 0 && index < active,
                // Tick only the active row; the rest keep stable props.
                smoothPositionMs = if (index == active) smoothPositionMs else 0L,
                onSeek = onSeek?.let { seek -> { seek(line.time) } },
                modifier = Modifier.animateItem(),
              )
            }
          }
        }
        Spacer(Modifier.height(MaterialTheme.spacing.extraLarge))
      }
    }
  }
}

@Composable
private fun ImmersiveLyricRow(
  line: LyricsEntry,
  isActive: Boolean,
  isPast: Boolean,
  smoothPositionMs: Long,
  onSeek: (() -> Unit)?,
  modifier: Modifier = Modifier,
) {
  // Continuous spring: the old code snapped 28sp <-> 34sp instantly,
  // remeasuring the list mid-scroll — that was the stanza jitter.
  val fontSize by animateFloatAsState(
    targetValue = if (isActive) 34f else 28f,
    animationSpec = spring(dampingRatio = 0.86f, stiffness = 340f),
    label = "immersiveLyricSize",
  )
  val color by animateColorAsState(
    targetValue = when {
      isActive -> Color.White
      isPast -> Color.White.copy(alpha = 0.42f)
      else -> Color.White.copy(alpha = 0.55f)
    },
    animationSpec = tween(
      durationMillis = 450,
      easing = FastOutSlowInEasing,
    ),
    label = "immersiveLyricColor",
  )
  val seekModifier = if (onSeek != null) Modifier.clickable(onClick = onSeek) else Modifier
  // RTL lyrics (Arabic/Hebrew/Urdu) flow right-to-left like Apple Music.
  val direction = if (line.isRtl == true) LayoutDirection.Rtl else LayoutDirection.Ltr
  CompositionLocalProvider(LocalLayoutDirection provides direction) {
    if (isActive && !line.words.isNullOrEmpty()) {
      WordKaraokeLine(
        words = line.words,
        positionMs = smoothPositionMs,
        fontSize = fontSize,
        modifier = modifier
          .fillMaxWidth()
          .then(seekModifier),
      )
    } else {
      Text(
        line.text,
        fontSize = fontSize.sp,
        lineHeight = (fontSize * 1.25f).sp,
        fontWeight = FontWeight.ExtraBold,
        textAlign = TextAlign.Start,
        color = color,
        style = MaterialTheme.typography.headlineSmall.copy(
          shadow = if (isActive) {
            Shadow(color = Color.White.copy(alpha = 0.55f), offset = Offset.Zero, blurRadius = 28f)
          } else {
            null
          },
        ),
        modifier = modifier
          .fillMaxWidth()
          .then(seekModifier),
      )
    }
  }
}

/**
 * Apple-style word-by-word fill for the active line. Each word lights up
 * as its timestamp passes on the interpolated clock — a crisp karaoke
 * step, not a fade, so timing feels locked to the vocal. Background
 * (duet) words cap dimmer. Falls back to the plain line above when the
 * provider ships no word timings.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WordKaraokeLine(
  words: List<WordTimestamp>,
  positionMs: Long,
  fontSize: Float,
  modifier: Modifier = Modifier,
) {
  FlowRow(modifier = modifier) {
    words.forEach { word ->
      val wordStartMs = (word.startTime * 1000).toLong()
      val sung = positionMs >= wordStartMs
      val cap = if (word.isBackground) 0.7f else 1f
      Text(
        text = word.text + " ",
        fontSize = fontSize.sp,
        lineHeight = (fontSize * 1.25f).sp,
        fontWeight = FontWeight.ExtraBold,
        textAlign = TextAlign.Start,
        color = if (sung) {
          Color.White.copy(alpha = cap)
        } else {
          Color.White.copy(alpha = 0.35f * cap)
        },
        style = MaterialTheme.typography.headlineSmall.copy(
          shadow = if (sung) {
            Shadow(color = Color.White.copy(alpha = 0.55f), offset = Offset.Zero, blurRadius = 28f)
          } else {
            null
          },
        ),
      )
    }
  }
}
