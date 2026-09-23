/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package app.rayik.music.playback.queues

import androidx.media3.common.MediaItem
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext
import app.rayik.music.db.entities.AlbumWithSongs
import app.rayik.music.extensions.toMediaItem
import app.rayik.music.innertube.YouTube
import app.rayik.music.innertube.models.WatchEndpoint
import app.rayik.music.models.MediaMetadata

class LocalAlbumRadio(
    internal val albumWithSongs: AlbumWithSongs,
    internal val startIndex: Int = 0,
) : Queue {
    override val preloadItem: MediaMetadata? = null

    private lateinit var playlistId: String
    private val endpoint: WatchEndpoint
        get() =
            WatchEndpoint(
                playlistId = playlistId,
                params = "wAEB",
            )

    private var continuation: String? = null
    private var firstTimeLoaded: Boolean = false

    override suspend fun getInitialStatus(): Queue.Status =
        withContext(IO) {
            Queue.Status(
                title = albumWithSongs.album.title,
                items = albumWithSongs.songs.map { it.toMediaItem() },
                mediaItemIndex = startIndex,
            )
        }

    override fun hasNextPage(): Boolean = !firstTimeLoaded || continuation != null

    override suspend fun nextPage(): List<MediaItem> =
        withContext(IO) {
            if (!firstTimeLoaded) {
                playlistId =
                    YouTube
                        .album(albumWithSongs.album.id)
                        .getOrThrow()
                        .album.playlistId
                val nextResult = YouTube.next(endpoint, continuation).getOrThrow()
                continuation = nextResult.continuation
                firstTimeLoaded = true
                return@withContext nextResult.items
                    .subList(
                        albumWithSongs.songs.size,
                        nextResult.items.size,
                    ).map { it.toMediaItem() }
            }
            val nextResult = YouTube.next(endpoint, continuation).getOrThrow()
            continuation = nextResult.continuation
            nextResult.items.map { it.toMediaItem() }
        }
}
