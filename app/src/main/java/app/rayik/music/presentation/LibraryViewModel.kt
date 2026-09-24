package app.rayik.music.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import app.rayik.music.constants.SongSortType
import app.rayik.music.db.MusicDatabase
import app.rayik.music.db.entities.Song
import app.rayik.music.models.toMediaMetadata
import app.rayik.music.playback.PlayerConnectionHolder
import app.rayik.music.playback.queues.YouTubeQueue
import javax.inject.Inject

/**
 * Library backed by the on-device database: liked songs, recent plays,
 * and all-time most-played. Null while the first query runs.
 */
@HiltViewModel
class LibraryViewModel @Inject constructor(
  database: MusicDatabase,
  private val holder: PlayerConnectionHolder,
) : ViewModel() {
  val liked: StateFlow<List<Song>?> =
    database.likedSongs(SongSortType.CREATE_DATE, descending = true)
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

  val recent: StateFlow<List<Song>?> =
    database.recentSongs(limit = 30)
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

  val mostPlayed: StateFlow<List<Song>?> =
    database.mostPlayedSongs(fromTimeStamp = 0L, limit = 20)
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

  /** Tap-to-play: radio queue from the tapped library song. */
  fun play(song: Song) {
    val conn = holder.connection.value ?: return
    conn.playQueue(YouTubeQueue.radio(song.toMediaMetadata()))
  }
}
