package app.rayik.music.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.rayik.music.R
import app.rayik.music.player.PlayerViewModel
import app.rayik.music.player.QueueItem
import app.rayik.music.player.formatMs
import app.rayik.music.streaming.StreamSource
import app.rayik.music.streaming.Track
import app.rayik.music.ui.theme.spacing
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Search backed by the on-device streaming source: loading while the
 * network runs, results with tap-to-play, designed empty and
 * Unavailable + retry states. Never blank.
 */
@Composable
fun SearchScreen(
  onPlayStarted: () -> Unit,
  searchViewModel: SearchViewModel = koinViewModel(),
  player: PlayerViewModel = koinViewModel(),
  source: StreamSource = koinInject(),
) {
  var query by rememberSaveable { mutableStateOf("") }
  val state by searchViewModel.state.collectAsState()
  var resolvingId by rememberSaveable { mutableStateOf<String?>(null) }
  var resolveError by rememberSaveable { mutableStateOf<String?>(null) }
  val scope = rememberCoroutineScope()

  fun playTrack(track: Track) {
    if (resolvingId != null) return
    resolvingId = track.id
    resolveError = null
    // Main thread: resolve() is main-safe, and state writes stay on the UI thread.
    scope.launch {
      source.resolve(track)
        .onSuccess { resolved ->
          resolvingId = null
          player.play(listOf(QueueItem(track.id, track.title, track.artist, resolved.url, track.artworkUrl, resolved.mimeType)), 0)
          onPlayStarted()
        }
        .onFailure {
          resolvingId = null
          resolveError = it.message ?: "Couldn't resolve a stream — try again"
        }
    }
  }

  Column(Modifier.fillMaxSize()) {
    Text(stringResource(R.string.search_title), style = MaterialTheme.typography.headlineSmall)
    Text(
      stringResource(R.string.search_body),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(top = MaterialTheme.spacing.small),
    )
    Spacer(Modifier.height(MaterialTheme.spacing.medium))

    OutlinedTextField(
      value = query,
      onValueChange = { query = it },
      modifier = Modifier.fillMaxWidth(),
      placeholder = { Text(stringResource(R.string.search_hint)) },
      leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
      singleLine = true,
      keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
      keyboardActions = KeyboardActions(onSearch = { searchViewModel.search(query) }),
    )

    Spacer(Modifier.height(MaterialTheme.spacing.medium))

    when (val current = state) {
      SearchUiState.Idle -> Unit
      SearchUiState.Searching -> ScreenScaffold(
        state = ScreenState.Loading,
        loadingText = stringResource(R.string.search_searching),
        onRetry = {},
      ) {}
      is SearchUiState.Results -> {
        if (current.tracks.isEmpty()) {
          ScreenScaffold(state = ScreenState.Ready, loadingText = "", onRetry = {}) {
            Text(
              stringResource(R.string.search_no_results),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        } else {
          LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
          ) {
            items(current.tracks, key = { it.id }) { track ->
              SearchRow(
                track = track,
                resolving = resolvingId == track.id,
                onClick = { playTrack(track) },
              )
            }
          }
        }
      }
      is SearchUiState.Unavailable -> ScreenScaffold(
        state = ScreenState.Unavailable(current.reason),
        loadingText = "",
        onRetry = searchViewModel::retry,
      ) {}
    }

    resolveError?.let { message ->
      Spacer(Modifier.height(MaterialTheme.spacing.small))
      Text(
        message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
      )
    }
  }
}

@Composable
private fun SearchRow(
  track: Track,
  resolving: Boolean,
  onClick: () -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(12.dp))
      .clickable(onClick = onClick)
      .padding(MaterialTheme.spacing.small),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    TrackArt(artworkUrl = track.artworkUrl, modifier = Modifier.size(48.dp))
    Spacer(Modifier.width(MaterialTheme.spacing.medium))
    Column(Modifier.weight(1f)) {
      Text(
        track.title,
        style = MaterialTheme.typography.bodyLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        track.artist,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
    if (track.durationMs > 0) {
      Text(
        formatMs(track.durationMs),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    if (resolving) {
      Spacer(Modifier.width(MaterialTheme.spacing.small))
      CircularProgressIndicator(modifier = Modifier.size(20.dp))
    }
  }
}
