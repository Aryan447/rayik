package app.rayik.music.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import app.rayik.music.db.MusicDatabase
import app.rayik.music.db.entities.Song
import app.rayik.music.innertube.YouTube
import app.rayik.music.innertube.models.AlbumItem
import app.rayik.music.innertube.models.ArtistItem
import app.rayik.music.innertube.models.PlaylistItem
import app.rayik.music.innertube.models.SongItem
import app.rayik.music.innertube.models.WatchEndpoint
import app.rayik.music.innertube.models.YTItem
import app.rayik.music.innertube.pages.HomePage
import app.rayik.music.models.toMediaMetadata
import app.rayik.music.playback.PlayerConnectionHolder
import app.rayik.music.playback.queues.YouTubeQueue
import javax.inject.Inject

/** Live home feed: YT Music shelves + new releases, filtered by chip. */
data class HomeFeed(
  val chips: List<HomePage.Chip>,
  val sections: List<HomePage.Section>,
  val newReleases: List<AlbumItem>,
  val selectedChip: String?,
)

sealed interface HomeUiState {
  data object Loading : HomeUiState
  data class Content(val feed: HomeFeed) : HomeUiState
  data class Unavailable(val reason: String) : HomeUiState
}

@HiltViewModel
class HomeViewModel @Inject constructor(
  database: MusicDatabase,
  private val holder: PlayerConnectionHolder,
) : ViewModel() {
  private val _state =
    kotlinx.coroutines.flow.MutableStateFlow<HomeUiState>(HomeUiState.Loading)
  val state: StateFlow<HomeUiState> = _state

  /** Jump-back-in tiles from on-device history. Null while loading. */
  val recent: StateFlow<List<Song>?> =
    database.recentSongs(limit = 6)
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

  private var lastParams: String? = null
  private var lastChip: String? = null
  private var job: Job? = null

  init {
    load()
  }

  fun load(params: String? = null, chip: String? = null) {
    lastParams = params
    lastChip = chip
    job?.cancel()
    _state.value = HomeUiState.Loading
    job = viewModelScope.launch(Dispatchers.IO) {
      runCatching {
        val home = async { YouTube.home(params = params).getOrThrow() }
        val releases = async { YouTube.newReleaseAlbums().getOrThrow() }
        val page = home.await()
        HomeFeed(
          chips = page.chips.orEmpty(),
          sections = page.sections,
          newReleases = releases.await(),
          selectedChip = chip,
        )
      }.onSuccess { _state.value = HomeUiState.Content(it) }
        .onFailure {
          _state.value = HomeUiState.Unavailable(
            it.message?.takeIf { msg -> msg.isNotBlank() }
              ?: "Couldn't load home — try again",
          )
        }
    }
  }

  fun retry() {
    load(lastParams, lastChip)
  }

  /** Tap-to-play for any shelf item with a playable endpoint. */
  fun play(item: YTItem, onStarted: () -> Unit = {}) {
    val conn = holder.connection.value ?: return
    val queue = when (item) {
      is SongItem -> YouTubeQueue.radio(item.toMediaMetadata())
      is AlbumItem -> YouTubeQueue.playlist(WatchEndpoint(playlistId = item.playlistId))
      is PlaylistItem -> YouTubeQueue.playlist(
        item.playEndpoint ?: item.shuffleEndpoint ?: item.radioEndpoint
          ?: WatchEndpoint(playlistId = item.id),
      )
      is ArtistItem -> YouTubeQueue.playlist(
        item.radioEndpoint ?: item.shuffleEndpoint ?: item.playEndpoint ?: return,
      )
      else -> return
    }
    conn.playQueue(queue)
    onStarted()
  }

  /** Tap-to-play for a library recent. */
  fun playLibrarySong(song: Song, onStarted: () -> Unit = {}) {
    val conn = holder.connection.value ?: return
    conn.playQueue(YouTubeQueue.radio(song.toMediaMetadata()))
    onStarted()
  }
}
