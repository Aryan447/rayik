package app.rayik.music.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import app.rayik.music.db.MusicDatabase
import app.rayik.music.db.entities.ArtistEntity
import app.rayik.music.db.entities.SongArtistMap
import app.rayik.music.db.entities.SongEntity
import app.rayik.music.innertube.YouTube
import app.rayik.music.innertube.models.SongItem
import app.rayik.music.spotify.Spotify
import app.rayik.music.spotify.SpotifyAccountSession
import app.rayik.music.spotify.SpotifyLibraryRepository
import app.rayik.music.spotify.models.SpotifyTrack
import java.time.LocalDateTime
import javax.inject.Inject

/** Progress of a running import: resolved count, failures, completion. */
data class ImportProgress(
  val label: String = "",
  val total: Int = 0,
  val resolved: Int = 0,
  val failures: List<String> = emptyList(),
  val running: Boolean = false,
  val done: Boolean = false,
)

/**
 * Spotify import: connect with sp_dc cookies, browse playlists + liked
 * songs, and resolve each track through YTM search into the local library
 * (liked). Sequential with a courtesy delay so InnerTube never sees a burst.
 */
@HiltViewModel
class ImportViewModel @Inject constructor(
  private val spotify: SpotifyLibraryRepository,
  private val database: MusicDatabase,
) : ViewModel() {
  private val _session = MutableStateFlow(SpotifyAccountSession())
  val session: StateFlow<SpotifyAccountSession> = _session.asStateFlow()

  val playlists = spotify.playlists
  val refreshing = spotify.isRefreshing
  val spotifyError = spotify.errorMessage

  private val _progress = MutableStateFlow(ImportProgress())
  val progress: StateFlow<ImportProgress> = _progress.asStateFlow()

  private val _connectError = MutableStateFlow<String?>(null)
  val connectError: StateFlow<String?> = _connectError.asStateFlow()

  init {
    viewModelScope.launch(Dispatchers.IO) {
      _session.value = spotify.restoreSession()
      if (_session.value.isAuthenticated) spotify.restoreCachedPlaylists()
    }
  }

  fun connect(spDc: String, spKey: String) {
    val dc = spDc.trim()
    if (dc.isEmpty()) {
      _connectError.value = "Paste your sp_dc cookie value first"
      return
    }
    viewModelScope.launch(Dispatchers.IO) {
      _connectError.value = null
      runCatching { spotify.connectWithCookies(dc, spKey.trim()) }
        .onSuccess {
          _session.value = it
          spotify.refreshPlaylists()
        }
        .onFailure { _connectError.value = it.message ?: "Could not connect — check the cookie and try again" }
    }
  }

  fun logout() {
    viewModelScope.launch(Dispatchers.IO) {
      spotify.logout()
      _session.value = SpotifyAccountSession()
    }
  }

  fun refreshPlaylists() {
    viewModelScope.launch(Dispatchers.IO) { spotify.refreshPlaylists() }
  }

  fun importLiked() {
    viewModelScope.launch(Dispatchers.IO) {
      val tracks = fetchAllLiked()
      importTracks(tracks, "Liked songs")
    }
  }

  fun importPlaylist(playlistId: String, name: String) {
    viewModelScope.launch(Dispatchers.IO) {
      val tracks = runCatching { spotify.playlistTracks(playlistId) }.getOrElse {
        _progress.value = ImportProgress(label = name, failures = listOf(it.message ?: "Could not load playlist"), done = true)
        return@launch
      }
      importTracks(tracks, name)
    }
  }

  fun dismissProgress() {
    _progress.value = ImportProgress()
  }

  private suspend fun fetchAllLiked(): List<SpotifyTrack> {
    // Warms the access token through the repository's authenticated path.
    spotify.refreshPlaylists()
    val tracks = ArrayList<SpotifyTrack>()
    var offset = 0
    while (true) {
      val page = Spotify.likedSongs(limit = 50, offset = offset).getOrElse { return tracks }
      tracks += page.items.map { it.track }
      offset += page.items.size
      if (offset >= page.total || page.items.size < 50) break
    }
    return tracks
  }

  private suspend fun importTracks(tracks: List<SpotifyTrack>, label: String) {
    _progress.value = ImportProgress(label = label, total = tracks.size, running = true)
    val failures = ArrayList<String>()
    tracks.forEachIndexed { index, track ->
      val query = "${track.name} ${track.artists.joinToString(" ") { it.name }}".trim()
      val match = runCatching {
        YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrThrow()
      }.getOrNull()?.items?.filterIsInstance<SongItem>()?.firstOrNull()
      if (match != null) {
        runCatching { saveMatch(match) }.onFailure {
          failures += "${track.name} — could not save"
        }
      } else {
        failures += "${track.name} — no match on YouTube Music"
      }
      _progress.value = _progress.value.copy(resolved = index + 1, failures = failures.toList())
      delay(IMPORT_SEARCH_DELAY_MS)
    }
    _progress.value = _progress.value.copy(running = false, done = true)
  }

  private suspend fun saveMatch(item: SongItem) {
    database.withTransaction {
      val now = LocalDateTime.now()
      val existing = getSongById(item.id)
      if (existing != null) {
        if (!existing.song.liked) {
          update(
            existing.song.copy(
              liked = true,
              likedDate = now,
              inLibrary = existing.song.inLibrary ?: now,
            ),
          )
        }
        return@withTransaction
      }
      insert(
        SongEntity(
          id = item.id,
          title = item.title,
          duration = item.duration ?: -1,
          thumbnailUrl = item.thumbnail,
          albumId = item.album?.id,
          albumName = item.album?.name,
          explicit = item.explicit,
          liked = true,
          likedDate = now,
          inLibrary = now,
        ),
      )
      item.artists.forEachIndexed { position, artist ->
        val artistId = artist.id ?: "import:${artist.name}"
        insert(ArtistEntity(id = artistId, name = artist.name))
        insert(SongArtistMap(songId = item.id, artistId = artistId, position = position))
      }
    }
  }

  private companion object {
    const val IMPORT_SEARCH_DELAY_MS = 250L
  }
}
