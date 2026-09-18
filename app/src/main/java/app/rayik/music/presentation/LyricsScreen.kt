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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.rayik.music.R
import app.rayik.music.player.PlayerViewModel
import app.rayik.music.streaming.lyrics.LrcParser
import app.rayik.music.streaming.lyrics.LrclibLyrics
import app.rayik.music.streaming.lyrics.LyricLine
import app.rayik.music.streaming.lyrics.LyricsResult
import app.rayik.music.ui.theme.spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Lyrics view: hero art, then lines. Synced lines highlight and follow
 * playback; plain lyrics scroll statically. Loading, empty, and
 * Unavailable + retry are all designed states.
 */
@Composable
fun LyricsScreen(
  player: PlayerViewModel = koinViewModel(),
  lyrics: LrclibLyrics = koinInject(),
) {
  val queue by player.queue.collectAsState()
  val currentIndex by player.currentIndex.collectAsState()
  val positionMs by player.positionMs.collectAsState()
  val durationMs by player.durationMs.collectAsState()
  val current = queue.getOrNull(currentIndex)
  val scope = rememberCoroutineScope()

  var fetchKey by rememberSaveable { mutableStateOf<String?>(null) }
  var loading by rememberSaveable { mutableStateOf(false) }
  // LyricsResult isn't parcelable — plain remember, refetched per track id.
  var result by remember { mutableStateOf<LyricsResult?>(null) }
  var error by rememberSaveable { mutableStateOf<String?>(null) }

  if (current == null) {
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

  val key = current.id
  if (fetchKey != key) {
    fetchKey = key
    loading = true
    result = null
    error = null
    scope.launch(Dispatchers.IO) {
      lyrics.fetch(current.artist, current.title, durationMs)
        .onSuccess {
          result = it
          loading = false
        }
        .onFailure {
          error = it.message
          loading = false
        }
    }
  }

  Column(
    modifier = Modifier.fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    TrackArt(artworkUrl = current.artworkUrl, corner = 20.dp, modifier = Modifier.size(160.dp))
    Spacer(Modifier.height(MaterialTheme.spacing.medium))
    Text(current.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    Text(
      current.artist,
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(MaterialTheme.spacing.medium))

    when {
      loading -> ScreenScaffold(
        state = ScreenState.Loading,
        loadingText = stringResource(R.string.lyrics_loading),
        onRetry = {},
      ) {}
      error != null -> ScreenScaffold(
        state = ScreenState.Unavailable(error ?: stringResource(R.string.lyrics_unavailable)),
        loadingText = "",
        onRetry = {
          fetchKey = null
          error = null
        },
      ) {}
      result != null -> {
        val lines = result?.lines.orEmpty()
        if (lines.isEmpty()) {
          ScreenScaffold(
            state = ScreenState.Unavailable(stringResource(R.string.lyrics_none)),
            loadingText = "",
            onRetry = {
              fetchKey = null
              error = null
            },
          ) {}
        } else {
          LyricLines(lines = lines, synced = result?.synced == true, positionMs = positionMs)
        }
      }
    }
  }
}

@Composable
private fun LyricLines(
  lines: List<LyricLine>,
  synced: Boolean,
  positionMs: Long,
) {
  val listState = rememberLazyListState()
  val active = if (synced) LrcParser.activeIndex(lines, positionMs) else -1

  LaunchedEffect(active) {
    if (active >= 0) listState.animateScrollToItem(maxOf(0, active - 2))
  }

  LazyColumn(
    state = listState,
    modifier = Modifier.fillMaxSize(),
    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    itemsIndexed(lines, key = { index, line -> "$index-${line.startMs}" }) { index, line ->
      val isActive = synced && index == active
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
