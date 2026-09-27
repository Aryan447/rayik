package app.rayik.music.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.rayik.music.player.formatMs
import app.rayik.music.ui.theme.BrandGradient
import app.rayik.music.ui.theme.spacing
import app.rayik.music.R
import app.rayik.music.innertube.models.SongItem

private data class BrowseMood(
  val label: String,
  val query: String,
  val start: Color,
  val end: Color,
)

private val BROWSE_MOODS = listOf(
  BrowseMood("Bollywood Hits", "bollywood hits", Color(0xFFE91E63), Color(0xFF7B1FA2)),
  BrowseMood("Punjabi", "punjabi songs", Color(0xFFFF9800), Color(0xFFF44336)),
  BrowseMood("Lo-Fi Chill", "lofi chill", Color(0xFF3F51B5), Color(0xFF00BCD4)),
  BrowseMood("Arijit Special", "arijit singh", Color(0xFF9C27B0), Color(0xFFE91E63)),
  BrowseMood("Workout", "workout music", Color(0xFFF44336), Color(0xFFFF5722)),
  BrowseMood("Rainy Day", "rainy day songs", Color(0xFF03A9F4), Color(0xFF3F51B5)),
  BrowseMood("Party", "party songs", Color(0xFFFF5722), Color(0xFFFFC107)),
  BrowseMood("2000s", "2000s hits", Color(0xFF009688), Color(0xFF8BC34A)),
)

/**
 * Search: ambient gradient header, pill field, recent searches, a Browse-all
 * mood grid when idle, and a top-result hero above song rows on results.
 */
@Composable
fun SearchScreen(
  onPlayStarted: () -> Unit,
  searchViewModel: SearchViewModel = hiltViewModel(),
) {
  var query by rememberSaveable { mutableStateOf("") }
  val state by searchViewModel.state.collectAsState()
  val recents by searchViewModel.recentSearches.collectAsState()
  val scheme = MaterialTheme.colorScheme

  fun submit() {
    searchViewModel.searchAndRemember(query)
  }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
  ) {
    item {
      Box(Modifier.fillMaxWidth()) {
        Box(
          Modifier.matchParentSize().background(
            BrandGradient.bloomBrush(
              color = scheme.primary,
              alpha = 0.22f,
              center = androidx.compose.ui.geometry.Offset(80f, 0f),
              radius = 520f,
            ),
          ),
        )
        Column {
          GradientHeadline(stringResource(R.string.search_title))
          Text(
            stringResource(R.string.search_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = MaterialTheme.spacing.small),
          )
        }
      }
    }

    item {
      OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(stringResource(R.string.search_hint)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
          if (query.isNotBlank()) {
            IconButton(onClick = { query = "" }) {
              Icon(Icons.Filled.Close, contentDescription = null)
            }
          }
        },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        colors = TextFieldDefaults.colors(
          focusedIndicatorColor = Color.Transparent,
          unfocusedIndicatorColor = Color.Transparent,
          focusedContainerColor = scheme.surfaceVariant.copy(alpha = 0.6f),
          unfocusedContainerColor = scheme.surfaceVariant.copy(alpha = 0.6f),
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { submit() }),
      )
    }

    when (val current = state) {
      SearchUiState.Idle -> {
        if (recents.isNotEmpty()) {
          item {
            Column {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                  stringResource(R.string.search_recents),
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.SemiBold,
                  modifier = Modifier.weight(1f),
                )
                TextButton(onClick = searchViewModel::clearRecents) {
                  Text(stringResource(R.string.search_clear))
                }
              }
              Spacer(Modifier.height(MaterialTheme.spacing.small))
              recents.forEach { recent ->
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                      query = recent
                      searchViewModel.searchAndRemember(recent)
                    }
                    .padding(vertical = MaterialTheme.spacing.small),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Icon(
                    Icons.Filled.History,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                  )
                  Spacer(Modifier.width(MaterialTheme.spacing.medium))
                  Text(
                    recent,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
              }
            }
          }
        }
        item {
          Column {
            Text(
              stringResource(R.string.search_browse_title),
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(MaterialTheme.spacing.small))
            LazyVerticalGrid(
              columns = GridCells.Fixed(2),
              modifier = Modifier
                .fillMaxWidth()
                .height(404.dp),
              horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
              verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
              userScrollEnabled = false,
            ) {
              items(BROWSE_MOODS) { mood ->
                MoodCard(
                  mood = mood,
                  onClick = {
                    query = mood.query
                    searchViewModel.searchAndRemember(mood.query)
                  },
                )
              }
            }
          }
        }
      }
      SearchUiState.Searching -> {
        item {
          ScreenScaffold(
            state = ScreenState.Loading,
            loadingText = stringResource(R.string.search_searching),
            onRetry = {},
          ) {}
        }
      }
      is SearchUiState.Results -> {
        if (current.tracks.isEmpty()) {
          item {
            ScreenScaffold(state = ScreenState.Ready, loadingText = "", onRetry = {}) {
              Text(
                stringResource(R.string.search_no_results),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        } else {
          item {
            TopResultCard(
              track = current.tracks.first(),
              onClick = {
                searchViewModel.play(current.tracks.first(), onPlayStarted)
              },
            )
          }
          item {
            Text(
              stringResource(R.string.search_songs_title),
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.SemiBold,
            )
          }
          items(current.tracks, key = { it.id }) { track ->
            SearchRow(
              track = track,
              onClick = { searchViewModel.play(track, onPlayStarted) },
            )
          }
        }
      }
      is SearchUiState.Unavailable -> {
        item {
          ScreenScaffold(
            state = ScreenState.Unavailable(current.reason),
            loadingText = "",
            onRetry = searchViewModel::retry,
          ) {}
        }
      }
    }
  }
}

@Composable
private fun MoodCard(
  mood: BrowseMood,
  onClick: () -> Unit,
) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(92.dp)
      .clip(RoundedCornerShape(20.dp))
      .background(Brush.linearGradient(listOf(mood.start, mood.end)))
      .clickable(onClick = onClick)
      .padding(MaterialTheme.spacing.medium),
  ) {
    Text(
      mood.label,
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
      color = Color.White,
      modifier = Modifier.align(Alignment.BottomStart),
    )
    Icon(
      Icons.Filled.MusicNote,
      contentDescription = null,
      tint = Color.White.copy(alpha = 0.35f),
      modifier = Modifier
        .size(64.dp)
        .align(Alignment.TopEnd)
        .offset(x = 12.dp, y = (-12).dp)
        .graphicsLayer { rotationZ = -20f },
    )
  }
}

@Composable
private fun TopResultCard(
  track: SongItem,
  onClick: () -> Unit,
) {
  Surface(
    onClick = onClick,
    tonalElevation = 2.dp,
    shape = RoundedCornerShape(24.dp),
    border = androidx.compose.foundation.BorderStroke(
      1.dp,
      MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
    ),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.padding(MaterialTheme.spacing.medium),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      TrackArt(artworkUrl = track.thumbnail, corner = 20.dp, modifier = Modifier.size(96.dp))
      Spacer(Modifier.width(MaterialTheme.spacing.medium))
      Column(Modifier.weight(1f)) {
        Text(
          stringResource(R.string.search_top_result),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.primary,
          fontWeight = FontWeight.Bold,
        )
        Text(
          track.title,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
          maxLines = 2,
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
      FilledIconButton(
        onClick = onClick,
        modifier = Modifier.size(56.dp),
        colors = IconButtonDefaults.filledIconButtonColors(
          containerColor = MaterialTheme.colorScheme.primary,
          contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
      ) {
        Icon(
          Icons.Filled.PlayArrow,
          contentDescription = stringResource(R.string.transport_play),
          modifier = Modifier.size(30.dp),
        )
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
      .clip(RoundedCornerShape(16.dp))
      .clickable(onClick = onClick)
      .padding(MaterialTheme.spacing.small),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    TrackArt(artworkUrl = track.thumbnail, corner = 14.dp, modifier = Modifier.size(52.dp))
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
