package app.rayik.music.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.DragInteraction
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import app.rayik.music.ui.theme.AppShapes
import app.rayik.music.ui.theme.BrandGradient
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
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
import app.rayik.music.ui.theme.RayikIcons
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
  fallbackUrl: String = "",
  title: String,
  artist: String,
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

  // True only while the finger is down: programmatic glides must never
  // count as "scrolling", or every line change during a glide gets skipped
  // and the active line sinks to the bottom.
  var userScrolling by remember { mutableStateOf(false) }
  var resumePending by remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()
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
          0f to Color.Black.copy(alpha = 0.34f),
          0.5f to Color.Black.copy(alpha = 0.48f),
          1f to Color.Black.copy(alpha = 0.68f),
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
            shape = AppShapes.pill,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
            modifier = Modifier
              .size(44.dp)
              .border(1.dp, BrandGradient.hairline(), AppShapes.pill),
          ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
              Icon(
                RayikIcons.Close,
                contentDescription = stringResource(R.string.action_collapse),
                tint = MaterialTheme.colorScheme.onSurface,
              )
            }
          }
          Spacer(Modifier.weight(1f))
          Column(
            modifier = Modifier.weight(3f),
            horizontalAlignment = Alignment.End,
          ) {
            Text(
              title,
              color = Color.White,
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.SemiBold,
              maxLines = 1,
            )
            Text(
              artist,
              color = Color.White.copy(alpha = 0.72f),
              style = MaterialTheme.typography.bodySmall,
              maxLines = 1,
            )
          }
          Spacer(Modifier.width(MaterialTheme.spacing.small))
          TrackArt(
            artworkUrl = artworkUrl,
            fallbackUrl = fallbackUrl,
            corner = AppShapes.art,
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
              start = MaterialTheme.spacing.extraLarge,
              top = edgePadding,
              end = MaterialTheme.spacing.extraLarge,
              // Deep tail so the last line can still climb to the anchor
              // instead of parking at the bottom when the list runs out
              // of road.
              bottom = maxHeight * 0.62f,
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
      // Manual re-sync: the list glides back on its own after a drag, but a
      // visible affordance beats waiting when you've scrolled far away.
      AnimatedVisibility(
        visible = userScrolling || resumePending,
        enter = fadeIn(animationSpec = tween(250)) +
          slideInVertically(initialOffsetY = { it / 2 }, animationSpec = tween(250)),
        exit = fadeOut(animationSpec = tween(200)),
        modifier = Modifier.align(Alignment.BottomCenter),
        label = "lyricResync",
      ) {
        Surface(
          onClick = {
            scope.launch {
              userScrolling = false
              resumePending = false
              listState.centerLyricOn(active)
            }
          },
          shape = AppShapes.pill,
          color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
          modifier = Modifier
            .padding(bottom = MaterialTheme.spacing.extraLarge)
            .border(1.dp, BrandGradient.hairline(), AppShapes.pill),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              RayikIcons.Resync,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.size(17.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
              stringResource(R.string.lyrics_resync),
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.onSurface,
            )
          }
        }
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
        fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
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
 * Apple-style word-by-word fill for the active line. Past words sit bright,
 * upcoming words dim — and the currently-sung word fills progressively: a
 * bright layer sweeps through its glyphs with playback progress, so a long
 * "aiiiir" visibly fills instead of flashing white at its first millisecond.
 * Falls back to whole-word steps for zero-duration words and to the plain
 * line above when the provider ships no word timings.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WordKaraokeLine(
  words: List<WordTimestamp>,
  positionMs: Long,
  fontSize: Float,
  modifier: Modifier = Modifier,
) {
  val density = LocalDensity.current
  val glow = Shadow(color = Color.White.copy(alpha = 0.55f), offset = Offset.Zero, blurRadius = 28f)
  FlowRow(modifier = modifier) {
    words.forEach { word ->
      val wordStartMs = (word.startTime * 1000).toLong()
      val wordEndMs = (word.endTime * 1000).toLong()
      val cap = if (word.isBackground) 0.7f else 1f
      val sung = positionMs >= wordStartMs
      val finished = positionMs >= wordEndMs || wordEndMs <= wordStartMs
      if (!sung || finished) {
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
            shadow = if (sung) glow else null,
          ),
        )
      } else {
        var wordWidthPx by remember(word.text) { mutableIntStateOf(0) }
        val fraction =
          ((positionMs - wordStartMs).toFloat() / (wordEndMs - wordStartMs)).coerceIn(0f, 1f)
        Box {
          Text(
            text = word.text + " ",
            fontSize = fontSize.sp,
            lineHeight = (fontSize * 1.25f).sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Start,
            color = Color.White.copy(alpha = 0.35f * cap),
            onTextLayout = { wordWidthPx = it.size.width },
          )
          // Overlay starts at the line-start edge (mirrored automatically
          // for RTL) and widens with progress, clipped to the glyphs.
          if (wordWidthPx > 0 && fraction > 0f) {
            Box(
              Modifier
                .width(with(density) { (wordWidthPx * fraction).toDp() })
                .clipToBounds(),
            ) {
              Text(
                text = word.text + " ",
                fontSize = fontSize.sp,
                lineHeight = (fontSize * 1.25f).sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Start,
                color = Color.White.copy(alpha = cap),
                style = MaterialTheme.typography.headlineSmall.copy(shadow = glow),
                softWrap = false,
              )
            }
          }
        }
      }
    }
  }
}
