package app.rayik.music.presentation

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import app.rayik.music.preferences.preference.collectAsState as collectPreferenceAsState
import app.rayik.music.player.PlaybackUiState
import app.rayik.music.player.PlayerViewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import java.time.LocalTime

/**
 * Raay home: greeting, jump-back-in tiles from your own history, then live
 * shelves from the streaming layer (chips filter, new releases close the page).
 */

@Composable
fun RaayHomeScreen(
  onPlayStarted: () -> Unit = {},
  onContentReady: () -> Unit = {},
  player: PlayerViewModel = hiltViewModel(),
  home: HomeViewModel = hiltViewModel(),
) {
  val homeState by home.state.collectAsState()
  val recent by home.recent.collectAsState()
  val currentMediaId by player.currentMediaId.collectAsState()
  val playbackState by player.playbackState.collectAsState()
  val floatingDock by rayikPreferences().floatingDock.collectPreferenceAsState()

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
      // Dock intro gate: release RayikNav's 800ms slide-up once the feed
      // is actually ready. Idempotent — RayikNav ignores repeats.
      LaunchedEffect(Unit) { onContentReady() }
      val content = feed.feed
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large),
        // Bottom clearance: floating pill needs DockFloatingClearance,
        // docked needs the sheet's 28dp curve + large to clear the bar.
        contentPadding = PaddingValues(
          bottom = if (floatingDock) DockFloatingClearance
          else DockSheetBottomRadius + MaterialTheme.spacing.large,
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
    GradientHeadline(greeting)
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

/**
 * Brand mark from `site/src/components/Logo.astro`: five gold studio-EQ bars inside the
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
