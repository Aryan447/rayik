package app.rayik.music.presentation

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import app.rayik.music.R
import app.rayik.music.db.entities.Song
import app.rayik.music.player.PlayerViewModel
import app.rayik.music.ui.theme.AppShapes
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
        MarkTile(size = 72.dp)
        Spacer(Modifier.height(MaterialTheme.spacing.medium))
        Text(
          stringResource(R.string.library_empty_title),
          style = MaterialTheme.typography.headlineSmall,
          textAlign = TextAlign.Center,
        )
        Text(
          stringResource(R.string.library_empty_body),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
          modifier = Modifier.padding(top = MaterialTheme.spacing.small),
        )
      }
    }
    return
  }

  var showWrapped by remember { mutableStateOf(false) }
  var showImport by remember { mutableStateOf(false) }
  var selectedShelf by rememberSaveable { mutableIntStateOf(0) }

  Box(Modifier.fillMaxSize()) {
    LazyColumn(
      state = listState,
      modifier = Modifier.fillMaxSize(),
      verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
      // Bottom clearance so the last row clears the inset sheet's
      // 28dp bottom curve instead of clipping into the dock.
      contentPadding = PaddingValues(
        bottom = DockSheetBottomRadius + MaterialTheme.spacing.large,
      ),
    ) {
    item {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
          GradientHeadline(stringResource(R.string.library_headline))
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
        TextButton(onClick = { showImport = true }) {
          Text(stringResource(R.string.import_open))
        }
        TextButton(onClick = { showWrapped = true }) {
          Text(stringResource(R.string.wrapped_open))
        }
      }
    }

    item {
      Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
        FilterChip(
          selected = selectedShelf == 0,
          onClick = {
            selectedShelf = 0
            scope.launch { listState.animateScrollToItem(0) }
          },
          label = { Text(stringResource(R.string.library_filter_all)) },
        )
        FilterChip(
          selected = selectedShelf == 1,
          onClick = {
            selectedShelf = 1
            scope.launch { listState.animateScrollToItem(2) }
          },
          label = { Text(stringResource(R.string.library_filter_liked)) },
        )
        FilterChip(
          selected = selectedShelf == 2,
          onClick = {
            selectedShelf = 2
            scope.launch { listState.animateScrollToItem(3) }
          },
          label = { Text(stringResource(R.string.library_filter_recent)) },
        )
        FilterChip(
          selected = selectedShelf == 3,
          onClick = {
            selectedShelf = 3
            scope.launch { listState.animateScrollToItem(4) }
          },
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
    if (showImport) {
      Surface(Modifier.fillMaxSize()) {
        ImportScreen(onClose = { showImport = false })
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
          shape = AppShapes.cardShape,
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
  Column(
    modifier = Modifier
      .width(132.dp)
      .clickable(onClick = onClick),
  ) {
    TrackArt(
      artworkUrl = song.song.thumbnailUrl.orEmpty(),
      corner = AppShapes.art,
      modifier = Modifier.size(132.dp),
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
      song.artists.joinToString { it.name }.ifBlank { stringResource(R.string.common_unknown_artist) },
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

/**
 * Spotify import: connect with cookies, then resolve liked songs and
 * playlists track-by-track through YTM search into the local library.
 * Every state renders — connect, loading, progress, done, error — never
 * a dead button or a silent drop.
 */
@Composable
fun ImportScreen(
  onClose: () -> Unit,
  vm: ImportViewModel = hiltViewModel(),
) {
  val session by vm.session.collectAsState()
  val playlists by vm.playlists.collectAsState()
  val refreshing by vm.refreshing.collectAsState()
  val spotifyError by vm.spotifyError.collectAsState()
  val progress by vm.progress.collectAsState()
  val connectError by vm.connectError.collectAsState()
  var spDc by rememberSaveable { mutableStateOf("") }
  var spKey by rememberSaveable { mutableStateOf("") }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(
        horizontal = MaterialTheme.spacing.medium,
        vertical = MaterialTheme.spacing.medium,
      ),
    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        GradientHeadline(stringResource(R.string.import_title))
      }
      IconButton(onClick = onClose) {
        Icon(
          RayikIcons.Close,
          contentDescription = stringResource(R.string.import_close),
        )
      }
    }

    if (!session.isAuthenticated) {
      Text(
        stringResource(R.string.import_howto_title),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
      )
      Text(
        stringResource(R.string.import_howto_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      OutlinedTextField(
        value = spDc,
        onValueChange = { spDc = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.import_spdc_label)) },
        singleLine = true,
      )
      OutlinedTextField(
        value = spKey,
        onValueChange = { spKey = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.import_spkey_label)) },
        singleLine = true,
      )
      if (connectError != null) {
        Text(
          connectError.orEmpty(),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.error,
        )
      }
      Button(onClick = { vm.connect(spDc, spKey) }) {
        Text(stringResource(R.string.import_connect))
      }
      return@Column
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Text(
          stringResource(R.string.import_connected_as, session.accountName.ifBlank { "Spotify" }),
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.SemiBold,
        )
      }
      TextButton(onClick = { vm.logout() }) {
        Text(stringResource(R.string.import_disconnect))
      }
    }

    ImportRow(
      title = stringResource(R.string.import_liked),
      busy = progress.running,
      onImport = { vm.importLiked() },
    )

    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        stringResource(R.string.import_playlists),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.weight(1f),
      )
      TextButton(onClick = { vm.refreshPlaylists() }, enabled = !refreshing) {
        Text(stringResource(R.string.import_refresh))
      }
    }

    if (refreshing && playlists.isEmpty()) {
      LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    } else if (playlists.isEmpty()) {
      Text(
        stringResource(R.string.import_empty_playlists),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    } else {
      playlists.forEach { playlist ->
        ImportRow(
          title = playlist.name.ifBlank { stringResource(R.string.import_untitled) },
          subtitle = playlist.tracks?.total?.let { pluralStringResource(R.plurals.import_tracks_count, it, it) },
          busy = progress.running,
          onImport = { vm.importPlaylist(playlist.id, playlist.name) },
        )
      }
    }

    if (spotifyError != null) {
      Text(
        spotifyError.orEmpty(),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
      )
    }

    if (progress.running || progress.done) {
      ImportProgressCard(
        progress = progress,
        onDismiss = { vm.dismissProgress() },
      )
    }
  }
}

@Composable
private fun ImportRow(
  title: String,
  subtitle: String? = null,
  busy: Boolean,
  onImport: () -> Unit,
) {
  Surface(
    shape = AppShapes.cardShape,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.padding(MaterialTheme.spacing.medium),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        Icons.Outlined.Download,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
      )
      Spacer(Modifier.width(MaterialTheme.spacing.small))
      Column(Modifier.weight(1f)) {
        Text(
          title,
          style = MaterialTheme.typography.bodyLarge,
          fontWeight = FontWeight.Medium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        if (subtitle != null) {
          Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      TextButton(onClick = onImport, enabled = !busy) {
        Text(stringResource(R.string.import_start))
      }
    }
  }
}

@Composable
private fun ImportProgressCard(
  progress: ImportProgress,
  onDismiss: () -> Unit,
) {
  Surface(
    shape = AppShapes.cardShape,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.padding(MaterialTheme.spacing.medium)) {
      val status = if (progress.done) {
        stringResource(R.string.import_done, progress.resolved - progress.failures.size, progress.total)
      } else {
        stringResource(R.string.import_running, progress.resolved, progress.total)
      }
      Text(
        status,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
      )
      Spacer(Modifier.height(MaterialTheme.spacing.small))
      if (progress.running) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
      }
      if (progress.failures.isNotEmpty()) {
        Spacer(Modifier.height(MaterialTheme.spacing.small))
        Text(
          stringResource(R.string.import_unmatched_title),
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        progress.failures.take(8).forEach { failure ->
          Text(
            failure,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
      if (progress.done) {
        Spacer(Modifier.height(MaterialTheme.spacing.small))
        TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
          Text(stringResource(R.string.import_dismiss))
        }
      }
    }
  }
}
