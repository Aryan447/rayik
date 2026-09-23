package app.rayik.music.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import app.rayik.music.innertube.YouTube
import app.rayik.music.innertube.models.SongItem
import app.rayik.music.models.toMediaMetadata
import app.rayik.music.playback.PlayerConnectionHolder
import app.rayik.music.playback.queues.YouTubeQueue
import app.rayik.music.innertube.models.WatchEndpoint
import javax.inject.Inject

sealed interface SearchUiState {
  data object Idle : SearchUiState
  data object Searching : SearchUiState
  data class Results(val tracks: List<SongItem>) : SearchUiState
  data class Unavailable(val reason: String) : SearchUiState
}

@HiltViewModel
class SearchViewModel @Inject constructor(
  private val holder: PlayerConnectionHolder,
) : ViewModel() {
  private val _state = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
  val state: StateFlow<SearchUiState> = _state.asStateFlow()

  private var lastQuery = ""
  private var job: Job? = null

  fun search(query: String) {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) {
      _state.value = SearchUiState.Idle
      return
    }
    lastQuery = trimmed
    job?.cancel()
    _state.value = SearchUiState.Searching
    job = viewModelScope.launch(Dispatchers.IO) {
      YouTube.search(trimmed, YouTube.SearchFilter.FILTER_SONG)
        .onSuccess { result ->
          val songs = result.items.filterIsInstance<SongItem>()
          _state.value = SearchUiState.Results(songs)
        }
        .onFailure { _state.value = SearchUiState.Unavailable(it.message ?: "Search failed — try again") }
    }
  }

  fun retry() {
    if (lastQuery.isNotBlank()) search(lastQuery)
  }

  /** Tap-to-play: radio queue from the tapped song, automix follows. */
  fun play(song: SongItem, onStarted: () -> Unit = {}) {
    val conn = holder.connection.value ?: return
    conn.playQueue(YouTubeQueue.radio(song.toMediaMetadata()))
    onStarted()
  }

  /** Tap-to-play from a bare video id (Raay picks share this path). */
  fun playVideo(videoId: String, onStarted: () -> Unit = {}) {
    val conn = holder.connection.value ?: return
    conn.playQueue(
      YouTubeQueue.playlist(
        WatchEndpoint(videoId = videoId),
        preloadItem = null,
      ),
    )
    onStarted()
  }
}
