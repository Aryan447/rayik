package app.rayik.music.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import app.rayik.music.db.MusicDatabase
import app.rayik.music.db.entities.SearchHistory
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
  private val database: MusicDatabase,
) : ViewModel() {
  private val _state = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
  val state: StateFlow<SearchUiState> = _state.asStateFlow()

  /** Recent searches, newest first. */
  val recentSearches: StateFlow<List<String>> =
    database.searchHistory()
      .map { entries -> entries.map { it.query }.take(8) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  private var lastQuery = ""
  private var job: Job? = null

  /** Searches and remembers the query for the recents row. */
  fun searchAndRemember(query: String) {
    val trimmed = query.trim()
    if (trimmed.isNotEmpty()) {
      viewModelScope.launch(Dispatchers.IO) {
        // Unique index: re-searching keeps its original slot, never crashes.
        runCatching { database.insert(SearchHistory(query = trimmed)) }
      }
    }
    search(query)
  }

  fun clearRecents() {
    viewModelScope.launch(Dispatchers.IO) {
      runCatching { database.clearSearchHistory() }
    }
  }

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
