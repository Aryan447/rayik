package app.rayik.music.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.rayik.music.streaming.StreamSource
import app.rayik.music.streaming.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SearchUiState {
  data object Idle : SearchUiState
  data object Searching : SearchUiState
  data class Results(val tracks: List<Track>) : SearchUiState
  data class Unavailable(val reason: String) : SearchUiState
}

class SearchViewModel(
  private val source: StreamSource,
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
      source.search(trimmed)
        .onSuccess { _state.value = SearchUiState.Results(it) }
        .onFailure { _state.value = SearchUiState.Unavailable(it.message ?: "Search failed — try again") }
    }
  }

  fun retry() {
    if (lastQuery.isNotBlank()) search(lastQuery)
  }
}
