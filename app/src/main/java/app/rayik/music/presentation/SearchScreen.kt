package app.rayik.music.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.rayik.music.player.formatMs
import app.rayik.music.ui.theme.AppShapes
import app.rayik.music.ui.theme.BrandGradient
import app.rayik.music.ui.theme.RayikIcons
import app.rayik.music.ui.theme.spacing
import app.rayik.music.R
import app.rayik.music.innertube.models.SongItem

private val BROWSE_MOODS = listOf(
  R.string.search_browse_bollywood to R.string.search_browse_bollywood_q,
  R.string.search_browse_punjabi to R.string.search_browse_punjabi_q,
  R.string.search_browse_lofi to R.string.search_browse_lofi_q,
  R.string.search_browse_arijit to R.string.search_browse_arijit_q,
  R.string.search_browse_workout to R.string.search_browse_workout_q,
  R.string.search_browse_rainy to R.string.search_browse_rainy_q,
  R.string.search_browse_party to R.string.search_browse_party_q,
  R.string.search_browse_y2k to R.string.search_browse_y2k_q,
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
    // Bottom clearance so the last row clears the inset sheet's
    // 28dp bottom curve instead of clipping into the dock.
    contentPadding = PaddingValues(
      bottom = DockSheetBottomRadius + MaterialTheme.spacing.large,
    ),
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
        leadingIcon = { Icon(RayikIcons.SearchNav, contentDescription = null) },
        trailingIcon = {
          if (query.isNotBlank()) {
            IconButton(onClick = { query = "" }) {
              Icon(RayikIcons.Close, contentDescription = null)
            }
          }
        },
        singleLine = true,
        shape = AppShapes.pill,
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
                    .clip(AppShapes.cardShape)
                    .clickable {
                      query = recent
                      searchViewModel.searchAndRemember(recent)
                    }
                    .padding(vertical = MaterialTheme.spacing.small),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Icon(
                    RayikIcons.History,
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
            BoxWithConstraints(Modifier.fillMaxWidth()) {
              val minCellWidth = 144.dp
              val gap = MaterialTheme.spacing.small
              val columns = maxOf(1, ((maxWidth + gap) / (minCellWidth + gap)).toInt())
              val rows = (BROWSE_MOODS.size + columns - 1) / columns
              val gridHeight = 92.dp * rows.toFloat() + gap * (rows - 1).coerceAtLeast(0).toFloat()
              LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = minCellWidth),
                modifier = Modifier.fillMaxWidth().height(gridHeight),
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalArrangement = Arrangement.spacedBy(gap),
                userScrollEnabled = false,
              ) {
                items(BROWSE_MOODS.size) { index ->
                  val (labelRes, queryRes) = BROWSE_MOODS[index]
                  val label = stringResource(labelRes)
                  val queryText = stringResource(queryRes)
                  MoodCard(
                    index = index,
                    label = label,
                    onClick = {
                      query = queryText
                      searchViewModel.searchAndRemember(queryText)
                    },
                  )
                }
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
  index: Int,
  label: String,
  onClick: () -> Unit,
) {
  // Theme-colored sweep, rotated so neighbors differ — never fixed pinks.
  val scheme = MaterialTheme.colorScheme
  val contrastScrim = if (scheme.onPrimary.luminance() > 0.5f) Color.Black else Color.White
  val sweep = remember(index, scheme) {
    val stops = listOf(scheme.primary, scheme.secondary, scheme.tertiary, scheme.primary)
    val shift = index % stops.size
    Brush.sweepGradient(stops.drop(shift) + stops.take(shift))
  }
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(92.dp)
      .clip(AppShapes.cardShape)
      .background(sweep)
      .clickable(role = Role.Button, onClick = onClick),
  ) {
    Box(
      Modifier
        .matchParentSize()
        .background(contrastScrim.copy(alpha = 0.36f)),
    )
    Text(
      label,
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
      color = scheme.onPrimary,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier
        .align(Alignment.BottomStart)
        .padding(MaterialTheme.spacing.medium),
    )
    Icon(
      RayikIcons.MusicNote,
      contentDescription = null,
      tint = scheme.onPrimary.copy(alpha = 0.35f),
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
    shape = AppShapes.cardShape,
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
      TrackArt(
        artworkUrl = track.thumbnail,
        fallbackUrl = publicArtFallback(track.id),
        corner = AppShapes.art,
        modifier = Modifier.size(96.dp),
      )
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
          RayikIcons.Play,
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
      .clip(AppShapes.cardShape)
      .clickable(onClick = onClick)
      .padding(MaterialTheme.spacing.small),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    TrackArt(
      artworkUrl = track.thumbnail,
      fallbackUrl = publicArtFallback(track.id),
      corner = AppShapes.art,
      modifier = Modifier.size(52.dp),
    )
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
