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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import app.rayik.music.player.PlaybackUiState
import app.rayik.music.player.PlayerViewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.rayik.music.R
import app.rayik.music.db.entities.Song
import app.rayik.music.innertube.models.AlbumItem
import app.rayik.music.innertube.models.ArtistItem
import app.rayik.music.innertube.models.PlaylistItem
import app.rayik.music.innertube.models.SongItem
import app.rayik.music.innertube.models.YTItem
import app.rayik.music.ui.theme.AppShapes
import app.rayik.music.ui.theme.RayikIcons
import app.rayik.music.ui.theme.spacing
import app.rayik.music.utils.isLocalMediaId
import java.time.LocalTime

/**
 * Raay home: greeting, jump-back-in tiles from your own history, an
 * opinionated Raay pick with its reason attached, then live shelves.
 */
data class RaayPick(
  val title: String,
  val reason: String,
  val videoId: String,
  val trackTitle: String,
  val trackArtist: String,
  /** Real artwork when the pick comes from history; else the video still. */
  val artworkUrl: String? = null,
)

@Composable
private fun defaultPicks(): List<RaayPick> = listOf(
  RaayPick(
    title = stringResource(R.string.home_pick_rain_title),
    reason = stringResource(R.string.home_pick_rain_reason),
    videoId = "MJyKN-8UncM",
    trackTitle = stringResource(R.string.home_pick_rain_track),
    trackArtist = stringResource(R.string.home_pick_rain_artist),
  ),
  RaayPick(
    title = stringResource(R.string.home_pick_focus_title),
    reason = stringResource(R.string.home_pick_focus_reason),
    videoId = "6mr4cYJ7yew",
    trackTitle = stringResource(R.string.home_pick_focus_track),
    trackArtist = stringResource(R.string.home_pick_focus_artist),
  ),
  RaayPick(
    title = stringResource(R.string.home_pick_drive_title),
    reason = stringResource(R.string.home_pick_drive_reason),
    videoId = "O5gwxm3NxFU",
    trackTitle = stringResource(R.string.home_pick_drive_track),
    trackArtist = stringResource(R.string.home_pick_drive_artist),
  ),
)

private fun pickArtwork(videoId: String): String = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

/**
 * Builds the living pick: same curated slot, but the reason line, track,
 * and artwork come from actual history. Local files can't stream by id,
 * so they never become the playable pick. Null when there is no
 * streamable history — the caller keeps the static slot.
 */
private fun historyPick(
  history: List<Song>,
  slot: RaayPick,
  dayparts: List<String>,
  unknownArtist: String,
): RaayPick {
  val candidates = history.filterNot { it.song.id.isLocalMediaId() }
  if (candidates.isEmpty()) return slot
  val top = candidates.maxByOrNull { it.song.totalPlayTime } ?: return slot
  val artistName = top.artists.firstOrNull()?.name?.ifBlank { null } ?: return slot
  val loopCount = candidates.count { candidate ->
    candidate.artists.any { it.name == artistName }
  }.coerceAtLeast(1)
  val hour = LocalTime.now().hour
  val daypart = when (hour) {
    in 5..11 -> dayparts[0]
    in 12..16 -> dayparts[1]
    else -> dayparts[2]
  }
  return slot.copy(
    reason = "$daypart • because you looped $artistName ${loopCount}×",
    videoId = top.song.id,
    trackTitle = top.song.title,
    trackArtist = top.artists.joinToString { it.name }.ifBlank { unknownArtist },
    artworkUrl = top.song.thumbnailUrl,
  )
}

@Composable
fun RaayHomeScreen(
  onPlayStarted: () -> Unit = {},
  player: PlayerViewModel = hiltViewModel(),
  home: HomeViewModel = hiltViewModel(),
  library: LibraryViewModel = hiltViewModel(),
) {
  var pickIndex by rememberSaveable { mutableIntStateOf(0) }
  var starting by rememberSaveable { mutableStateOf(false) }
  var startError by rememberSaveable { mutableStateOf<String?>(null) }

  val homeState by home.state.collectAsState()
  val recent by home.recent.collectAsState()
  val currentMediaId by player.currentMediaId.collectAsState()
  val playbackState by player.playbackState.collectAsState()
  val connected by player.connected.collectAsState()

  val picks = defaultPicks()
  val currentPick = picks[pickIndex % picks.size]
  // Alive pick: the slot keeps its curated title/mood, but the reason,
  // track, and art come from real listening history when there is any —
  // so the card is about *their* week, not a hardcoded Arijit loop.
  val libraryMostPlayed by library.mostPlayed.collectAsState()
  val dayparts = listOf(
    stringResource(R.string.home_daypart_morning),
    stringResource(R.string.home_daypart_afternoon),
    stringResource(R.string.home_daypart_evening),
  )
  val unknownArtist = stringResource(R.string.common_unknown_artist)
  val playerWait = stringResource(R.string.home_player_wait)
  val effectivePick = remember(currentPick, libraryMostPlayed, dayparts, unknownArtist) {
    historyPick(libraryMostPlayed.orEmpty(), currentPick, dayparts, unknownArtist)
  }
  val isPickPlaying = currentMediaId == effectivePick.videoId &&
    playbackState == PlaybackUiState.Playing

  fun playPick(pick: RaayPick) {
    if (starting) return
    if (currentMediaId == pick.videoId) {
      player.togglePlayPause()
      return
    }
    if (!connected) {
      startError = playerWait
      return
    }
    starting = true
    startError = null
    // The service resolves the stream (PO-token mint, decipher, fallback
    // walk); taps never resolve URLs on the UI thread anymore.
    player.playVideo(pick.videoId, pick.trackTitle, pick.trackArtist)
    starting = false
    onPlayStarted()
  }

  when (val feed = homeState) {
    HomeUiState.Loading -> ScreenScaffold(
      state = ScreenState.Loading,
      loadingText = stringResource(R.string.home_loading),
      onRetry = {},
    ) {}
    is HomeUiState.Unavailable -> ScreenScaffold(
      state = ScreenState.Unavailable(feed.reason),
      loadingText = "",
      onRetry = home::retry,
    ) {}
    is HomeUiState.Content -> {
      val content = feed.feed
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large),
        // Bottom clearance so the last shelf clears the inset sheet's
        // 28dp bottom curve instead of clipping into the dock.
        contentPadding = PaddingValues(
          bottom = DockSheetBottomRadius + MaterialTheme.spacing.large,
        ),
      ) {
        item {
          GreetingHeader()
        }

        val recents = recent.orEmpty()
        if (recents.isNotEmpty()) {
          item {
            Column {
              SectionTitleRow(stringResource(R.string.home_jump_title))
              Spacer(Modifier.height(MaterialTheme.spacing.small))
              QuickGrid(
                songs = recents,
                currentMediaId = currentMediaId,
                isPlaying = playbackState == PlaybackUiState.Playing,
                onPlay = { home.playLibrarySong(it, onPlayStarted) },
              )
            }
          }
        }

        item {
          RaayPickCard(
            pick = effectivePick,
            resolving = starting,
            isPlaying = isPickPlaying,
            error = startError,
            onPlayClick = { playPick(effectivePick) },
            onNextPick = {
              pickIndex = (pickIndex + 1) % picks.size
              startError = null
            },
            onRetry = { playPick(effectivePick) },
          )
        }

        if (content.chips.isNotEmpty()) {
          item {
            LazyRow(
              horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
            ) {
              item {
                FilterChip(
                  selected = content.selectedChip == null,
                  onClick = { home.load() },
                  label = { Text(stringResource(R.string.home_all)) },
                )
              }
              items(content.chips, key = { it.title }) { chip ->
                FilterChip(
                  selected = content.selectedChip == chip.title,
                  onClick = { home.load(params = chip.endpoint?.params, chip = chip.title) },
                  label = { Text(chip.title) },
                )
              }
            }
          }
        }

        items(
          count = content.sections.size,
          key = { index -> "$index-${content.sections[index].title}" },
        ) { index ->
          val section = content.sections[index]
          ShelfSection(
            title = section.title,
            items = section.items,
            onPlay = { home.play(it, onPlayStarted) },
          )
        }

        if (content.newReleases.isNotEmpty()) {
          item {
            ShelfSection(
              title = stringResource(R.string.home_new_title),
              items = content.newReleases,
              onPlay = { home.play(it, onPlayStarted) },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun GreetingHeader() {
  val hour = LocalTime.now().hour
  val greeting = stringResource(
    when (hour) {
      in 5..11 -> R.string.home_greeting_morning
      in 12..16 -> R.string.home_greeting_afternoon
      else -> R.string.home_greeting_evening
    },
  )
  Row(verticalAlignment = Alignment.CenterVertically) {
    RayikMark(modifier = Modifier.size(48.dp))
    Spacer(Modifier.width(MaterialTheme.spacing.medium))
    GradientHeadline(greeting, maxLines = 1)
  }
}

@Composable
private fun SectionTitleRow(title: String) {
  Text(
    title,
    style = MaterialTheme.typography.titleMedium,
    fontWeight = FontWeight.SemiBold,
  )
}

@Composable
private fun QuickGrid(
  songs: List<Song>,
  currentMediaId: String?,
  isPlaying: Boolean,
  onPlay: (Song) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
    songs.chunked(2).forEach { row ->
      Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
        row.forEach { song ->
          QuickTile(
            song = song,
            isCurrent = song.song.id == currentMediaId,
            isPlaying = isPlaying,
            onClick = { onPlay(song) },
            modifier = Modifier.weight(1f),
          )
        }
        if (row.size == 1) {
          Spacer(Modifier.weight(1f))
        }
      }
    }
  }
}

@Composable
private fun QuickTile(
  song: Song,
  isCurrent: Boolean,
  isPlaying: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    onClick = onClick,
    shape = AppShapes.cardShape,
    modifier = modifier,
  ) {
    Row(
      modifier = Modifier.padding(MaterialTheme.spacing.small),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      TrackArt(
        artworkUrl = song.song.thumbnailUrl.orEmpty(),
        fallbackUrl = publicArtFallback(song.song.id),
        corner = AppShapes.art,
        modifier = Modifier.size(56.dp),
      )
      Spacer(Modifier.width(MaterialTheme.spacing.small))
      Column(Modifier.weight(1f)) {
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
      if (isCurrent && isPlaying) {
        PlayingIndicator(modifier = Modifier.padding(end = MaterialTheme.spacing.smaller))
      }
    }
  }
}

@Composable
private fun RaayPickCard(
  pick: RaayPick,
  resolving: Boolean,
  isPlaying: Boolean,
  error: String?,
  onPlayClick: () -> Unit,
  onNextPick: () -> Unit,
  onRetry: () -> Unit,
) {
  val scheme = MaterialTheme.colorScheme
  Surface(
    shape = AppShapes.cardShape,
    border = androidx.compose.foundation.BorderStroke(
      1.dp,
      scheme.primary.copy(alpha = 0.24f),
    ),
    color = scheme.surface.copy(alpha = 0.94f),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(MaterialTheme.spacing.large),
    ) {
      // The stage: a concise reason above the recommendation title.
      Text(
        pick.reason,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = scheme.tertiary,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        pick.title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = MaterialTheme.spacing.extraSmall),
      )
      Spacer(Modifier.height(MaterialTheme.spacing.medium))
      Row(verticalAlignment = Alignment.CenterVertically) {
        TrackArt(
          artworkUrl = pick.artworkUrl ?: pickArtwork(pick.videoId),
          fallbackUrl = publicArtFallback(pick.videoId),
          corner = AppShapes.art,
          modifier = Modifier.size(116.dp),
        )
        Spacer(Modifier.width(MaterialTheme.spacing.medium))
        Column(Modifier.weight(1f)) {
          Text(
            pick.trackTitle,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            pick.trackArtist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Spacer(Modifier.height(MaterialTheme.spacing.small))
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
          ) {
            Surface(
              onClick = onPlayClick,
              shape = AppShapes.pill,
              color = scheme.primary,
              modifier = Modifier.size(48.dp),
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  imageVector = if (isPlaying) RayikIcons.Pause else RayikIcons.Play,
                  contentDescription = stringResource(
                    if (isPlaying) R.string.transport_pause else R.string.transport_play,
                  ),
                  tint = scheme.onPrimary,
                  modifier = Modifier.size(22.dp),
                )
              }
            }
            Surface(
              onClick = onNextPick,
              shape = AppShapes.pill,
              color = scheme.surfaceVariant.copy(alpha = 0.72f),
              modifier = Modifier.size(48.dp),
            ) {
              Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                  RayikIcons.Next,
                  contentDescription = stringResource(R.string.home_something_else),
                  tint = scheme.onSurfaceVariant,
                  modifier = Modifier.size(20.dp),
                )
              }
            }
          }
          if (resolving) {
            Text(
              stringResource(R.string.home_tuning),
              style = MaterialTheme.typography.labelMedium,
              color = scheme.onSurfaceVariant,
              modifier = Modifier.padding(top = MaterialTheme.spacing.extraSmall),
            )
          }
        }
      }
      if (error != null) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            error,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f),
          )
          if (!resolving) {
            TextButtonLite(onClick = onRetry)
          }
        }
      }
    }
  }
}

@Composable
private fun TextButtonLite(onClick: () -> Unit) {
  TextButton(onClick = onClick) {
    Text(stringResource(R.string.common_retry))
  }
}

@Composable
private fun ShelfSection(
  title: String,
  items: List<YTItem>,
  onPlay: (YTItem) -> Unit,
) {
  val playable = playableItems(items)
  if (playable.isEmpty()) return
  Column {
    SectionTitleRow(title)
    Spacer(Modifier.height(MaterialTheme.spacing.small))
    LazyRow(
      horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
      contentPadding = PaddingValues(end = MaterialTheme.spacing.medium),
    ) {
      items(playable, key = { it.id }) { item ->
        ShelfCard(item = item, onClick = { onPlay(item) })
      }
    }
  }
}

private fun playableItems(items: List<YTItem>): List<YTItem> {
  return items.filter {
    it is SongItem || it is AlbumItem || it is PlaylistItem || it is ArtistItem
  }
}

@Composable
private fun ShelfCard(
  item: YTItem,
  onClick: () -> Unit,
) {
  when (item) {
    is ArtistItem -> {
      Column(
        modifier = Modifier
          .width(112.dp)
          .clickable(onClick = onClick)
          .padding(MaterialTheme.spacing.small),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        TrackArt(
          artworkUrl = item.thumbnail.orEmpty(),
          corner = 56.dp,
          modifier = Modifier.size(96.dp),
        )
        Spacer(Modifier.height(MaterialTheme.spacing.small))
        Text(
          item.title,
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.Medium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          item.subscriberCountText ?: item.monthlyListenerCountText.orEmpty(),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
    else -> {
      val subtitle = when (item) {
        is SongItem -> item.artists.joinToString { it.name }
        is AlbumItem -> listOfNotNull(
          item.artists?.joinToString { it.name },
          item.year?.toString(),
        ).joinToString(" • ")
        is PlaylistItem -> listOfNotNull(
          item.author?.name,
          item.songCountText,
        ).joinToString(" • ")
        else -> ""
      }
      // Only songs own a video id, so only they get the public still as a
      // fallback — albums, playlists and artists stay CDN-only.
      val fallback = if (item is SongItem) publicArtFallback(item.id) else ""
      Column(
        modifier = Modifier
          .width(140.dp)
          .clickable(onClick = onClick),
      ) {
        TrackArt(
          artworkUrl = item.thumbnail.orEmpty(),
          fallbackUrl = fallback,
          corner = AppShapes.art,
          modifier = Modifier.size(140.dp),
        )
        Spacer(Modifier.height(MaterialTheme.spacing.small))
        Text(
          item.title,
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.Medium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        if (subtitle.isNotBlank()) {
          Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
  }
}
