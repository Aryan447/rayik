package app.rayik.music.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import app.rayik.music.R
import app.rayik.music.db.entities.Song
import app.rayik.music.player.PlayerViewModel
import app.rayik.music.ui.theme.spacing

/**
 * Library: liked songs, recently played, and most-played from the
 * on-device database. The player stays one tap away on the mini-player.
 */
@Composable
fun LibraryScreen(
  library: LibraryViewModel = hiltViewModel(),
  player: PlayerViewModel = hiltViewModel(),
) {
  val liked by library.liked.collectAsState()
  val recent by library.recent.collectAsState()
  val mostPlayed by library.mostPlayed.collectAsState()
  val currentMediaId by player.currentMediaId.collectAsState()

  val listState = rememberLazyListState()
  val scope = rememberCoroutineScope()

  if (liked == null || recent == null || mostPlayed == null) {
    ScreenScaffold(
      state = ScreenState.Loading,
      loadingText = stringResource(R.string.library_loading),
      onRetry = {},
    ) {}
    return
  }

  val likedList = liked.orEmpty()
  val recentList = recent.orEmpty()
  val mostPlayedList = mostPlayed.orEmpty()

  if (likedList.isEmpty() && recentList.isEmpty() && mostPlayedList.isEmpty()) {
    ScreenScaffold(state = ScreenState.Ready, loadingText = "", onRetry = {}) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = MaterialTheme.spacing.extraLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Text(
          stringResource(R.string.library_empty_title),
          style = MaterialTheme.typography.titleMedium,
        )
        Text(
          stringResource(R.string.library_empty_body),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(top = MaterialTheme.spacing.small),
        )
      }
    }
    return
  }

  var showWrapped by remember { mutableStateOf(false) }

  Box(Modifier.fillMaxSize()) {
    LazyColumn(
      state = listState,
      modifier = Modifier.fillMaxSize(),
      verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
    ) {
    item {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
          Text(
            stringResource(R.string.library_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
          )
          Text(
            stringResource(
              R.string.library_counts,
              likedList.size,
              recentList.size,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = MaterialTheme.spacing.smaller),
          )
        }
        TextButton(onClick = { showWrapped = true }) {
          Text(stringResource(R.string.wrapped_open))
        }
      }
    }

    item {
      Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
        FilterChip(
          selected = false,
          onClick = { scope.launch { listState.animateScrollToItem(0) } },
          label = { Text(stringResource(R.string.library_filter_all)) },
        )
        FilterChip(
          selected = false,
          onClick = { scope.launch { listState.animateScrollToItem(2) } },
          label = { Text(stringResource(R.string.library_filter_liked)) },
        )
        FilterChip(
          selected = false,
          onClick = { scope.launch { listState.animateScrollToItem(3) } },
          label = { Text(stringResource(R.string.library_filter_recent)) },
        )
        FilterChip(
          selected = false,
          onClick = { scope.launch { listState.animateScrollToItem(4) } },
          label = { Text(stringResource(R.string.library_filter_most)) },
        )
      }
    }

    item {
      LibraryShelf(
        title = stringResource(R.string.library_liked_title),
        songs = likedList,
        currentMediaId = currentMediaId,
        emptyHint = stringResource(R.string.library_liked_hint),
        onPlay = library::play,
      )
    }

    item {
      LibraryShelf(
        title = stringResource(R.string.library_recent_title),
        songs = recentList,
        currentMediaId = currentMediaId,
        emptyHint = null,
        onPlay = library::play,
      )
    }

    item {
      LibraryShelf(
        title = stringResource(R.string.library_most_title),
        songs = mostPlayedList,
        currentMediaId = currentMediaId,
        emptyHint = null,
        onPlay = library::play,
      )
    }
    }
    if (showWrapped) {
      Surface(Modifier.fillMaxSize()) {
        WrappedScreen(onClose = { showWrapped = false })
      }
    }
  }
}

@Composable
private fun LibraryShelf(
  title: String,
  songs: List<Song>,
  currentMediaId: String?,
  emptyHint: String?,
  onPlay: (Song) -> Unit,
) {
  Column {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
      )
      Spacer(Modifier.weight(1f))
      if (songs.isNotEmpty()) {
        Text(
          songs.size.toString(),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    Spacer(Modifier.height(MaterialTheme.spacing.small))
    if (songs.isEmpty()) {
      if (emptyHint != null) {
        Surface(
          tonalElevation = 1.dp,
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            emptyHint,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(MaterialTheme.spacing.medium),
          )
        }
      }
    } else {
      LazyRow(
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        contentPadding = PaddingValues(end = MaterialTheme.spacing.medium),
      ) {
        items(songs, key = { it.song.id }) { song ->
          LibraryCard(
            song = song,
            isCurrent = song.song.id == currentMediaId,
            onClick = { onPlay(song) },
          )
        }
      }
    }
  }
}

@Composable
private fun LibraryCard(
  song: Song,
  isCurrent: Boolean,
  onClick: () -> Unit,
) {
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(16.dp),
    tonalElevation = if (isCurrent) 3.dp else 1.dp,
    modifier = Modifier.width(132.dp),
  ) {
    Column(modifier = Modifier.padding(MaterialTheme.spacing.small)) {
      TrackArt(
        artworkUrl = song.song.thumbnailUrl.orEmpty(),
        corner = 12.dp,
        modifier = Modifier.size(116.dp),
      )
      Spacer(Modifier.height(MaterialTheme.spacing.small))
      Text(
        song.song.title,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
        color = if (isCurrent) {
          MaterialTheme.colorScheme.primary
        } else {
          MaterialTheme.colorScheme.onSurface
        },
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        song.artists.joinToString { it.name }.ifBlank { "Unknown artist" },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}
