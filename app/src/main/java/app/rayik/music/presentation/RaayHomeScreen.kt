package app.rayik.music.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import app.rayik.music.player.PlaybackUiState
import app.rayik.music.player.PlayerViewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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
import app.rayik.music.ui.theme.BrandGradient
import app.rayik.music.ui.theme.spacing
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Raay home: greeting, jump-back-in tiles from your own history, the
 * opinionated Raay pick with its reason attached, then live shelves from
 * the streaming layer (chips filter, new releases close the page).
 */
data class RaayPick(
  val title: String,
  val reason: String,
  val videoId: String,
  val trackTitle: String,
  val trackArtist: String,
  val mood: String,
)

private val RAAY_PICKS = listOf(
  RaayPick(
    title = "Mehfil Mix — Rain Edition",
    reason = "This morning • because you looped Arijit 12×",
    videoId = "BddP6PYo2gs",
    trackTitle = "Kesariya",
    trackArtist = "Pritam, Arijit Singh",
    mood = "Mehfil",
  ),
  RaayPick(
    title = "Monsoon Rain Session",
    reason = "Grey skies, wet earth, acoustic strings",
    videoId = "MJyKN-8UncM",
    trackTitle = "Shayad",
    trackArtist = "Pritam, Arijit Singh",
    mood = "Rain",
  ),
  RaayPick(
    title = "Deep Focus Flow",
    reason = "Zero distraction, repetitive cadence",
    videoId = "6mr4cYJ7yew",
    trackTitle = "Kesariya (Film Version)",
    trackArtist = "Pritam, Arijit Singh",
    mood = "Focus",
  ),
  RaayPick(
    title = "Late Night Drive",
    reason = "Empty highways, cool breeze",
    videoId = "O5gwxm3NxFU",
    trackTitle = "Best Of Arijit Singh",
    trackArtist = "Arijit Singh",
    mood = "Drive",
  ),
)

private fun pickArtwork(videoId: String): String = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

@Composable
fun RaayHomeScreen(
  onPlayStarted: () -> Unit = {},
  player: PlayerViewModel = hiltViewModel(),
  home: HomeViewModel = hiltViewModel(),
) {
  var pickIndex by rememberSaveable { mutableIntStateOf(0) }
  var starting by rememberSaveable { mutableStateOf(false) }
  var startError by rememberSaveable { mutableStateOf<String?>(null) }

  val homeState by home.state.collectAsState()
  val recent by home.recent.collectAsState()
  val currentMediaId by player.currentMediaId.collectAsState()
  val playbackState by player.playbackState.collectAsState()
  val connected by player.connected.collectAsState()

  val currentPick = RAAY_PICKS[pickIndex % RAAY_PICKS.size]
  val isPickPlaying = currentMediaId == currentPick.videoId &&
    playbackState == PlaybackUiState.Playing

  fun playPick(pick: RaayPick) {
    if (starting) return
    if (currentMediaId == pick.videoId) {
      player.togglePlayPause()
      return
    }
    if (!connected) {
      startError = "Player isn't connected yet — try again in a moment"
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
            pick = currentPick,
            resolving = starting,
            isPlaying = isPickPlaying,
            error = startError,
            onPlayClick = { playPick(currentPick) },
            onNextPick = {
              pickIndex = (pickIndex + 1) % RAAY_PICKS.size
              startError = null
            },
            onSelectMood = { mood ->
              val found = RAAY_PICKS.indexOfFirst { it.mood.equals(mood, ignoreCase = true) }
              if (found >= 0) {
                pickIndex = found
                playPick(RAAY_PICKS[found])
              }
            },
            onRetry = { playPick(currentPick) },
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
  val date = rememberDateLine()
  Row(verticalAlignment = Alignment.CenterVertically) {
    RayikMark(modifier = Modifier.size(48.dp))
    Spacer(Modifier.width(MaterialTheme.spacing.medium))
    Column {
      Text(
        greeting,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
      )
      Text(
        date,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun rememberDateLine(): String {
  return try {
    LocalDateTime.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d"))
  } catch (_: Exception) {
    ""
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
    tonalElevation = 2.dp,
    shape = RoundedCornerShape(16.dp),
    modifier = modifier,
  ) {
    Row(
      modifier = Modifier.padding(MaterialTheme.spacing.small),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      TrackArt(
        artworkUrl = song.song.thumbnailUrl.orEmpty(),
        corner = 12.dp,
        modifier = Modifier.size(48.dp),
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
          song.artists.joinToString { it.name }.ifBlank { "Unknown artist" },
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
  onSelectMood: (String) -> Unit,
  onRetry: () -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(24.dp),
    tonalElevation = 2.dp,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(MaterialTheme.spacing.large),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        TrackArt(
          artworkUrl = pickArtwork(pick.videoId),
          corner = 16.dp,
          modifier = Modifier.size(96.dp),
        )
        Spacer(Modifier.width(MaterialTheme.spacing.medium))
        Column(Modifier.weight(1f)) {
          Text(
            pick.reason,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.tertiary,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            pick.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = MaterialTheme.spacing.extraSmall),
          )
          Text(
            "${pick.trackTitle} • ${pick.trackArtist}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
      Spacer(Modifier.height(MaterialTheme.spacing.medium))
      Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
          onClick = onPlayClick,
          modifier = Modifier.size(48.dp),
        ) {
          Icon(
            imageVector = if (isPlaying) {
              Icons.Filled.Pause
            } else {
              Icons.Filled.PlayArrow
            },
            contentDescription = stringResource(
              if (isPlaying) R.string.transport_pause else R.string.transport_play,
            ),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(32.dp),
          )
        }
        IconButton(onClick = onNextPick, modifier = Modifier.size(48.dp)) {
          Icon(Icons.Filled.SkipNext, contentDescription = null)
        }
        Spacer(Modifier.width(MaterialTheme.spacing.small))
        if (resolving) {
          Text(
            "Tuning…",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      if (!resolving) {
        Spacer(Modifier.height(MaterialTheme.spacing.small))
        MoodChips(onSelectMood = onSelectMood)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MoodChips(onSelectMood: (String) -> Unit) {
  FlowRow(
    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
  ) {
    listOf("Mehfil", "Rain", "Focus", "Drive").forEach { mood ->
      Chip(label = mood, onClick = { onSelectMood(mood) })
    }
  }
}

@Composable
private fun TextButtonLite(onClick: () -> Unit) {
  TextButton(onClick = onClick) {
    Text("Try again")
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
          .clip(RoundedCornerShape(16.dp))
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
      Column(
        modifier = Modifier
          .width(140.dp)
          .clip(RoundedCornerShape(16.dp))
          .clickable(onClick = onClick)
          .padding(MaterialTheme.spacing.small),
      ) {
        TrackArt(
          artworkUrl = item.thumbnail.orEmpty(),
          corner = 16.dp,
          modifier = Modifier.size(124.dp),
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

@Composable
private fun Chip(
  label: String,
  hot: Boolean = false,
  onClick: () -> Unit = {},
) {
  if (hot) {
    Surface(
      shape = RoundedCornerShape(999.dp),
      color = MaterialTheme.colorScheme.primary,
      modifier = Modifier
        .clip(RoundedCornerShape(999.dp))
        .clickable(onClick = onClick),
    ) {
      Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.padding(
          horizontal = MaterialTheme.spacing.medium,
          vertical = MaterialTheme.spacing.small,
        ),
      )
    }
  } else {
    Surface(
      shape = RoundedCornerShape(999.dp),
      color = Color.Transparent,
      border = androidx.compose.foundation.BorderStroke(
        1.dp,
        MaterialTheme.colorScheme.outlineVariant,
      ),
      modifier = Modifier
        .clip(RoundedCornerShape(999.dp))
        .clickable(onClick = onClick),
    ) {
      Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(
          horizontal = MaterialTheme.spacing.medium,
          vertical = MaterialTheme.spacing.small,
        ),
      )
    }
  }
}

/**
 * Brand mark from `docs/index.html`: five gold studio-EQ bars inside the
 * acoustic ring on the dark radial tile. Symmetric, so RTL-safe.
 */
@Composable
private fun RayikMark(modifier: Modifier = Modifier) {
  Canvas(
    modifier
      .clip(RoundedCornerShape(16.dp))
      .background(BrandGradient.markBackdropBrush),
  ) {
    val unit = size.width / 108f
    drawCircle(
      brush = BrandGradient.ringDiagonalBrush,
      radius = 29f * unit,
      center = center,
      alpha = 0.35f,
      style = Stroke(width = 1.4f * unit),
    )
    val barWidth = 5.5f * unit
    val barXs = listOf(31.25f, 41.25f, 51.25f, 61.25f, 71.25f)
    val barHeights = listOf(20f, 34f, 48f, 34f, 20f)
    barXs.forEachIndexed { i, x ->
      val barHeight = barHeights[i] * unit
      drawRoundRect(
        brush = BrandGradient.goldVerticalBrush,
        topLeft = Offset(x * unit, center.y - barHeight / 2f),
        size = Size(barWidth, barHeight),
        cornerRadius = CornerRadius(2.75f * unit, 2.75f * unit),
      )
    }
  }
}
