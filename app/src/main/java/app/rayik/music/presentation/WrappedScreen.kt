package app.rayik.music.presentation

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.rayik.music.R
import app.rayik.music.ui.theme.spacing

private const val WRAPPED_PAGES = 5

/**
 * Raay Wrapped: story-style pages from your on-device history —
 * minutes, top song, top artist, rhythm, top 5 + share.
 */
@Composable
fun WrappedScreen(
  onClose: () -> Unit,
  stats: StatsViewModel = hiltViewModel(),
) {
  val data by stats.data.collectAsState()
  val context = LocalContext.current
  val pagerState = rememberPagerState(pageCount = { WRAPPED_PAGES })

  Surface(modifier = Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(onClick = onClose) {
          Icon(Icons.Filled.Close, contentDescription = null)
        }
        Text(
          stringResource(R.string.wrapped_title),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
      }

      Row(
        modifier = Modifier.padding(horizontal = MaterialTheme.spacing.large),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
      ) {
        FilterChip(
          selected = stats.window == StatsWindow.MONTHLY,
          onClick = { stats.selectWindow(StatsWindow.MONTHLY) },
          label = { Text(stringResource(R.string.wrapped_window_month)) },
        )
        FilterChip(
          selected = stats.window == StatsWindow.ALL_TIME,
          onClick = { stats.selectWindow(StatsWindow.ALL_TIME) },
          label = { Text(stringResource(R.string.wrapped_window_all)) },
        )
      }

      val wrapped = data
      if (wrapped == null) {
        ScreenScaffold(
          state = ScreenState.Loading,
          loadingText = stringResource(R.string.wrapped_loading),
          onRetry = {},
        ) {}
      } else if (wrapped.totals.totalPlayCount == 0) {
        ScreenScaffold(state = ScreenState.Ready, loadingText = "", onRetry = {}) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacing.extraLarge),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Text(
              stringResource(R.string.wrapped_empty_title),
              style = MaterialTheme.typography.titleMedium,
              textAlign = TextAlign.Center,
            )
            Text(
              stringResource(R.string.wrapped_empty_body),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              textAlign = TextAlign.Center,
              modifier = Modifier.padding(top = MaterialTheme.spacing.small),
            )
          }
        }
      } else {
        Box(Modifier.weight(1f)) {
          HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
          ) { page ->
            WrappedPage(
              page = page,
              data = wrapped,
              windowLabel = stringResource(
                if (stats.window == StatsWindow.MONTHLY) {
                  R.string.wrapped_window_month
                } else {
                  R.string.wrapped_window_all
                },
              ),
              onShare = {
                val text = buildWrappedShareText(wrapped)
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                  type = "text/plain"
                  putExtra(Intent.EXTRA_TEXT, text)
                }, null))
              },
            )
          }
        }
        DotsRow(current = pagerState.currentPage, total = WRAPPED_PAGES)
        Spacer(Modifier.height(MaterialTheme.spacing.medium))
      }
    }
  }
}

@Composable
private fun WrappedPage(
  page: Int,
  data: WrappedData,
  windowLabel: String,
  onShare: () -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(MaterialTheme.spacing.extraLarge),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    when (page) {
      0 -> {
        StoryKicker(windowLabel)
        StoryHeadline(formatMinutes(data.totals.totalTimeListened))
        StorySub(stringResource(R.string.wrapped_minutes_body))
      }
      1 -> {
        StoryKicker(stringResource(R.string.wrapped_top_song))
        val top = data.topSongs.firstOrNull()
        if (top != null) {
          TrackArt(
            artworkUrl = top.thumbnailUrl.orEmpty(),
            corner = 24.dp,
            modifier = Modifier.size(168.dp),
          )
          Spacer(Modifier.height(MaterialTheme.spacing.medium))
          StoryHeadline(top.title)
          StorySub(
            stringResource(R.string.wrapped_plays_count, top.songCountListened),
          )
        } else {
          StorySub(stringResource(R.string.wrapped_none_yet))
        }
      }
      2 -> {
        StoryKicker(stringResource(R.string.wrapped_top_artist))
        val top = data.topArtists.firstOrNull()
        if (top != null) {
          TrackArt(
            artworkUrl = top.thumbnailUrl.orEmpty(),
            corner = 84.dp,
            modifier = Modifier.size(168.dp),
          )
          Spacer(Modifier.height(MaterialTheme.spacing.medium))
          StoryHeadline(top.title)
          StorySub(
            stringResource(R.string.wrapped_artist_body, top.songCount),
          )
        } else {
          StorySub(stringResource(R.string.wrapped_none_yet))
        }
      }
      3 -> {
        StoryKicker(stringResource(R.string.wrapped_rhythm))
        StoryHeadline(
          stringResource(R.string.wrapped_plays_count, data.totals.totalPlayCount),
        )
        Spacer(Modifier.height(MaterialTheme.spacing.small))
        val peak = data.peakHour
        StorySub(
          if (peak != null) {
            stringResource(R.string.wrapped_peak_body, formatHour(peak))
          } else {
            stringResource(R.string.wrapped_none_yet)
          },
        )
      }
      else -> {
        StoryKicker(stringResource(R.string.wrapped_top_five))
        data.topSongs.take(5).forEachIndexed { index, song ->
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              "${index + 1}",
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary,
              modifier = Modifier.width(32.dp),
            )
            Column(Modifier.weight(1f)) {
              Text(
                song.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                stringResource(R.string.wrapped_plays_count, song.songCountListened),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
          Spacer(Modifier.height(MaterialTheme.spacing.small))
        }
        Spacer(Modifier.height(MaterialTheme.spacing.medium))
        Button(onClick = onShare, modifier = Modifier.fillMaxWidth()) {
          Icon(Icons.Filled.Share, contentDescription = null)
          Spacer(Modifier.width(MaterialTheme.spacing.small))
          Text(stringResource(R.string.wrapped_share))
        }
      }
    }
  }
}

@Composable
private fun StoryKicker(text: String) {
  Text(
    text.uppercase(),
    style = MaterialTheme.typography.labelLarge,
    fontWeight = FontWeight.Bold,
    color = MaterialTheme.colorScheme.primary,
    textAlign = TextAlign.Center,
  )
  Spacer(Modifier.height(MaterialTheme.spacing.small))
}

@Composable
private fun StoryHeadline(text: String) {
  Text(
    text,
    style = MaterialTheme.typography.displaySmall,
    fontWeight = FontWeight.Bold,
    textAlign = TextAlign.Center,
    maxLines = 3,
    overflow = TextOverflow.Ellipsis,
  )
  Spacer(Modifier.height(MaterialTheme.spacing.small))
}

@Composable
private fun StorySub(text: String) {
  Text(
    text,
    style = MaterialTheme.typography.bodyLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    textAlign = TextAlign.Center,
  )
}

@Composable
private fun DotsRow(current: Int, total: Int) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.Center,
  ) {
    repeat(total) { index ->
      Box(
        Modifier
          .padding(horizontal = 3.dp)
          .size(if (index == current) 20.dp else 8.dp, 8.dp)
          .clip(CircleShape)
          .background(
            if (index == current) {
              MaterialTheme.colorScheme.primary
            } else {
              MaterialTheme.colorScheme.surfaceVariant
            },
          ),
      )
    }
  }
}

internal fun formatMinutes(totalMs: Long): String {
  val totalMinutes = (totalMs / 60_000L).coerceAtLeast(0L)
  val hours = totalMinutes / 60
  val minutes = totalMinutes % 60
  return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

internal fun formatHour(slot: Int): String {
  val h = slot.mod(24)
  val amPm = if (h < 12) "AM" else "PM"
  val h12 = when (val remainder = h % 12) {
    0 -> 12
    else -> remainder
  }
  return "$h12 $amPm"
}

private fun buildWrappedShareText(data: WrappedData): String {
  val top = data.topSongs.firstOrNull()
  val artist = data.topArtists.firstOrNull()
  return buildString {
    append("My rāyik Wrapped: ${formatMinutes(data.totals.totalTimeListened)} of music")
    if (top != null) append(", top song ${top.title} (${top.songCountListened} plays)")
    if (artist != null) append(", top artist ${artist.title}")
    append(" — Music with opinions.")
  }
}
