package app.rayik.music.presentation

import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.rayik.music.player.formatMs
import app.rayik.music.ui.theme.spacing
import app.rayik.music.R
import app.rayik.music.innertube.models.SongItem

/**
 * Search backed by on-device InnerTube: loading while the
 * network runs, results with tap-to-play, designed empty and
 * Unavailable + retry states. Never blank.
 */
@Composable
fun SearchScreen(
  onPlayStarted: () -> Unit,
  searchViewModel: SearchViewModel = hiltViewModel(),
) {
  var query by rememberSaveable { mutableStateOf("") }
  val state by searchViewModel.state.collectAsState()

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

    Box(Modifier.weight(1f).fillMaxWidth()) {
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
                  onClick = { searchViewModel.play(track, onPlayStarted) },
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
    }
  }
}

@Composable
private fun SearchRow(
  track: SongItem,
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
    TrackArt(artworkUrl = track.thumbnail, modifier = Modifier.size(48.dp))
    Spacer(Modifier.width(MaterialTheme.spacing.medium))
    Column(Modifier.weight(1f)) {
      Text(
        track.title,
        style = MaterialTheme.typography.bodyLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        track.artists.joinToString { it.name },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
    val durationMs = (track.duration ?: 0) * 1_000L
    if (durationMs > 0) {
      Text(
        formatMs(durationMs),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
