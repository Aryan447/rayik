/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package app.rayik.music.models

import androidx.compose.runtime.Immutable
import app.rayik.music.db.entities.Song
import app.rayik.music.db.entities.SongEntity
import app.rayik.music.innertube.models.SongItem
import app.rayik.music.innertube.models.EpisodeItem
import app.rayik.music.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_OMV
import app.rayik.music.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_UGC
import app.rayik.music.ui.utils.YtimgResizePolicy
import app.rayik.music.ui.utils.resize
import java.io.Serializable
import java.time.LocalDateTime

@Immutable
data class MediaMetadata(
    val id: String,
    val title: String,
    val artists: List<Artist>,
    val duration: Int,
    val thumbnailUrl: String? = null,
    val album: Album? = null,
    val setVideoId: String? = null,
    val spotifyTrackId: String? = null,
    val explicit: Boolean = false,
    val liked: Boolean = false,
    val likedDate: LocalDateTime? = null,
    val inLibrary: LocalDateTime? = null,
    val isMusicVideo: Boolean = false,
    val isPodcast: Boolean = false,
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }

    data class Artist(
        val id: String?,
        val name: String,
        val thumbnailUrl: String? = null,
    ) : Serializable {
        companion object {
            private const val serialVersionUID = 1L
        }
    }

    data class Album(
        val id: String,
        val title: String,
    ) : Serializable {
        companion object {
            private const val serialVersionUID = 1L
        }
    }

    fun toSongEntity() =
        SongEntity(
            id = id,
            title = title,
            duration = duration,
            thumbnailUrl = thumbnailUrl,
            albumId = album?.id,
            albumName = album?.title,
            explicit = explicit,
            isMusicVideo = isMusicVideo,
            isPodcast = isPodcast,
            liked = liked,
            likedDate = likedDate,
            inLibrary = inLibrary,
        )
}

fun Song.toMediaMetadata() =
    MediaMetadata(
        id = song.id,
        title = song.title,
        artists =
            artists.map {
                MediaMetadata.Artist(
                    id = it.id,
                    name = it.name,
                    thumbnailUrl = it.thumbnailUrl,
                )
            },
        duration = song.duration,
        thumbnailUrl = song.thumbnailUrl,
        album =
            album?.let {
                MediaMetadata.Album(
                    id = it.id,
                    title = it.title,
                )
            } ?: song.albumId?.let { albumId ->
                MediaMetadata.Album(
                    id = albumId,
                    title = song.albumName.orEmpty(),
                )
            },
        explicit = song.explicit,
        isMusicVideo = song.isMusicVideo,
        isPodcast = song.isPodcast,
    )

fun EpisodeItem.toMediaMetadata() =
    MediaMetadata(
        id = id,
        title = title,
        artists =
            podcast?.let {
                listOf(
                    MediaMetadata.Artist(
                        id = it.id,
                        name = it.name,
                        thumbnailUrl = null,
                    ),
                )
            }.orEmpty(),
        duration = duration ?: -1,
        thumbnailUrl =
            thumbnail.resize(
                width = 1080,
                height = 1080,
                ytimgResizePolicy = YtimgResizePolicy.PreserveOriginal,
            ),
        album =
            podcast?.let { podcast ->
                podcast.id?.let { podcastId ->
                    MediaMetadata.Album(
                        id = podcastId,
                        title = podcast.name,
                    )
                }
            },
        setVideoId = endpoint.playlistSetVideoId,
        isPodcast = true,
    )

fun SongItem.toMediaMetadata() =
    MediaMetadata(
        id = id,
        title = title,
        artists =
            artists.map {
                MediaMetadata.Artist(
                    id = it.id,
                    name = it.name,
                    thumbnailUrl = null,
                )
            },
        duration = duration ?: -1,
        thumbnailUrl =
            thumbnail.resize(
                width = 1080,
                height = 1080,
                ytimgResizePolicy = YtimgResizePolicy.PreserveOriginal,
            ),
        album =
            album?.let {
                MediaMetadata.Album(
                    id = it.id,
                    title = it.name,
                )
            },
        explicit = explicit,
        setVideoId = setVideoId,
        isMusicVideo =
            endpoint?.watchEndpointMusicSupportedConfigs?.watchEndpointMusicConfig?.musicVideoType in
                listOf(MUSIC_VIDEO_TYPE_OMV, MUSIC_VIDEO_TYPE_UGC),
        isPodcast = isPodcast,
    )
