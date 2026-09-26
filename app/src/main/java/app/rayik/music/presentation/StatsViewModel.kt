package app.rayik.music.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import app.rayik.music.db.MusicDatabase
import app.rayik.music.db.entities.Artist
import app.rayik.music.db.entities.ListeningTotals
import app.rayik.music.db.entities.SongWithStats
import javax.inject.Inject

enum class StatsWindow(val days: Long?) {
  MONTHLY(30L),
  ALL_TIME(null),
}

/** Everything one Wrapped story needs. Null while queries run. */
data class WrappedData(
  val totals: ListeningTotals,
  val topSongs: List<SongWithStats>,
  val topArtists: List<Artist>,
  val peakHour: Int?,
)

@HiltViewModel
class StatsViewModel @Inject constructor(
  private val database: MusicDatabase,
) : ViewModel() {
  var window by mutableStateOf(StatsWindow.MONTHLY)
    private set

  fun selectWindow(next: StatsWindow) {
    window = next
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  val data: StateFlow<WrappedData?> =
    snapshotFlow { window }
      .flatMapLatest { selected ->
        val now = System.currentTimeMillis()
        val from = selected.days?.let { now - it * 86_400_000L } ?: 0L
        combine(
          database.listeningTotals(from, now),
          database.mostPlayedSongsStats(fromTimeStamp = from, limit = 5),
          database.mostPlayedMusicArtists(fromTimeStamp = from, limit = 5),
          database.listeningByHour(from, now),
        ) { totals, songs, artists, hours ->
          WrappedData(
            totals = totals,
            topSongs = songs,
            topArtists = artists,
            peakHour = hours.maxByOrNull { it.timeListened }?.slot,
          )
        }
      }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
