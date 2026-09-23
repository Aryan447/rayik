package app.rayik.music.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.rayik.music.player.PlayerViewModel
import app.rayik.music.ui.theme.spacing
import kotlinx.coroutines.flow.flowOf
import app.rayik.music.R
import app.rayik.music.lyrics.LrcParser
import app.rayik.music.lyrics.LyricsEntry

/**
 * Lyrics view: hero art, then lines. Synced lines highlight and follow
 * playback; plain lyrics scroll statically. Loading, empty, and
 * Unavailable + retry are all designed states.
 */
@Composable
fun LyricsScreen(
  player: PlayerViewModel = hiltViewModel(),
) {
  val connection by player.connection.collectAsState()
  val positionMs by player.positionMs.collectAsState()
  val conn = connection

  val metadata by remember(conn) {
    conn?.mediaMetadata ?: flowOf(null)
  }.collectAsState(initial = null)
  val entity by remember(conn) {
    conn?.currentLyrics ?: flowOf(null)
  }.collectAsState(initial = null)

  if (conn == null || metadata == null) {
    ScreenScaffold(state = ScreenState.Ready, loadingText = "", onRetry = {}) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.lyrics_empty_title), style = MaterialTheme.typography.titleMedium)
        Text(
          stringResource(R.string.lyrics_empty_body),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(top = MaterialTheme.spacing.small),
        )
      }
    }
    return
  }

  val title = metadata?.title?.toString().orEmpty()
  val artist = metadata?.artists?.joinToString(", ") { it.name }.orEmpty()
  val artwork = metadata?.thumbnailUrl.orEmpty()
  val raw = entity?.lyrics

  Column(
    modifier = Modifier.fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    TrackArt(artworkUrl = artwork, corner = 20.dp, modifier = Modifier.size(160.dp))
    Spacer(Modifier.height(MaterialTheme.spacing.medium))
    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    Text(
      artist,
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(MaterialTheme.spacing.medium))

    if (raw.isNullOrBlank() || raw == "LYRICS_NOT_FOUND") {
      ScreenScaffold(
        state = ScreenState.Unavailable(stringResource(R.string.lyrics_none)),
        loadingText = "",
        onRetry = {},
      ) {}
    } else {
      val lines = remember(raw) { LrcParser.parseLyrics(raw) }
      if (lines.isEmpty()) {
        Text(
          LrcParser.displayLyricsText(raw),
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(horizontal = MaterialTheme.spacing.medium),
        )
      } else {
        LyricLines(lines = lines, positionMs = positionMs)
      }
    }
  }
}

@Composable
private fun LyricLines(
  lines: List<LyricsEntry>,
  positionMs: Long,
) {
  val listState = rememberLazyListState()
  val active = remember(lines, positionMs) {
    lines.indexOfLast { it.time <= positionMs }.takeIf { it >= 0 } ?: -1
  }

  LaunchedEffect(active) {
    if (active >= 0) listState.animateScrollToItem(maxOf(0, active - 2))
  }

  LazyColumn(
    state = listState,
    modifier = Modifier.fillMaxSize(),
    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    itemsIndexed(lines, key = { index, line -> "$index-${line.time}" }) { index, line ->
      val isActive = index == active
      Text(
        line.text,
        style = if (isActive) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
        color = if (isActive) {
          MaterialTheme.colorScheme.primary
        } else {
          MaterialTheme.colorScheme.onSurfaceVariant
        },
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = MaterialTheme.spacing.medium),
      )
    }
  }
}
