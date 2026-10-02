/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package app.rayik.music.extensions

import android.net.Uri
import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata.MEDIA_TYPE_MUSIC
import androidx.media3.common.MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE
import app.rayik.music.db.entities.Song
import app.rayik.music.innertube.models.EpisodeItem
import app.rayik.music.innertube.models.SongItem
import app.rayik.music.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_OMV
import app.rayik.music.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_UGC
import app.rayik.music.models.MediaMetadata
import app.rayik.music.models.toMediaMetadata
import app.rayik.music.ui.utils.YTThumbQuality
import app.rayik.music.ui.utils.YtimgResizePolicy
import app.rayik.music.ui.utils.buildYTThumbnailUrl
import app.rayik.music.ui.utils.resize
import app.rayik.music.utils.NotificationArtworkSizePx
import app.rayik.music.utils.isLocalMediaId

const val ExtraIsMusicVideo = "app.rayik.music.extra.IS_MUSIC_VIDEO"
const val ExtraIsPodcast = "app.rayik.music.extra.IS_PODCAST"

val MediaItem.metadata: MediaMetadata?
    get() = localConfiguration?.tag as? MediaMetadata

private fun String?.toNotificationArtworkUri() =
    this
        ?.resize(
            width = NotificationArtworkSizePx,
            height = NotificationArtworkSizePx,
            ytimgResizePolicy = YtimgResizePolicy.PreserveOriginal,
        )?.toUri()

/**
 * Real YouTube Music art when we have it, otherwise the `i.ytimg.com` stand-in.
 * Upstream forced `hqdefault` for music videos, which threw away the genuine
 * thumbnail and then 404'd on videos without one — a guaranteed blank tile.
 */
private fun artworkUriFor(
    id: String,
    thumbnailUrl: String?,
): Uri? =
    thumbnailUrl
        ?.takeIf(String::isNotBlank)
        ?.toNotificationArtworkUri()
        ?: buildYTThumbnailUrl(id, YTThumbQuality.HQ).toUri()

private fun MediaItem.Builder.setCacheKeyIfRemote(mediaId: String): MediaItem.Builder {
    if (!mediaId.isLocalMediaId()) {
        setCustomCacheKey(mediaId)
    }
    return this
}

fun Song.toMediaItem() =
    MediaItem
        .Builder()
        .setMediaId(song.id)
        .setUri(song.id)
        .setCacheKeyIfRemote(song.id)
        .setTag(toMediaMetadata())
        .setMediaMetadata(
            androidx.media3.common.MediaMetadata
                .Builder()
                .setTitle(song.title)
                .setSubtitle(artists.joinToString { it.name })
                .setArtist(artists.joinToString { it.name })
                .setArtworkUri(artworkUriFor(song.id, song.thumbnailUrl))
                .setAlbumTitle(song.albumName)
                .setIsPlayable(true)
                .setMediaType(if (song.isPodcast) MEDIA_TYPE_PODCAST_EPISODE else MEDIA_TYPE_MUSIC)
                .setExtras(
                    Bundle().apply {
                        putBoolean(ExtraIsMusicVideo, song.isMusicVideo)
                        putBoolean(ExtraIsPodcast, song.isPodcast)
                    },
                )
                .build(),
        ).build()

fun SongItem.toMediaItem() =
    MediaItem
        .Builder()
        .setMediaId(id)
        .setUri(id)
        .setCacheKeyIfRemote(id)
        .setTag(toMediaMetadata())
        .setMediaMetadata(
            androidx.media3.common.MediaMetadata
                .Builder()
                .setTitle(title)
                .setSubtitle(artists.joinToString { it.name })
                .setArtist(artists.joinToString { it.name })
                .setArtworkUri(artworkUriFor(id, thumbnail))
                .setAlbumTitle(album?.name)
                .setIsPlayable(true)
                .setMediaType(MEDIA_TYPE_MUSIC)
                .setExtras(
                    Bundle().apply {
                        putBoolean(ExtraIsMusicVideo, isMusicVideo())
                        putBoolean(ExtraIsPodcast, false)
                    },
                )
                .build(),
        ).build()

fun EpisodeItem.toMediaItem() = toMediaMetadata().toMediaItem()

fun MediaMetadata.toMediaItem() =
    MediaItem
        .Builder()
        .setMediaId(id)
        .setUri(id)
        .setCacheKeyIfRemote(id)
        .setTag(this)
        .setMediaMetadata(
            androidx.media3.common.MediaMetadata
                .Builder()
                .setTitle(title)
                .setSubtitle(artists.joinToString { it.name })
                .setArtist(artists.joinToString { it.name })
                .setArtworkUri(artworkUriFor(id, thumbnailUrl))
                .setAlbumTitle(album?.title)
                .setIsPlayable(true)
                .setMediaType(if (isPodcast) MEDIA_TYPE_PODCAST_EPISODE else MEDIA_TYPE_MUSIC)
                .setExtras(
                    Bundle().apply {
                        putBoolean(ExtraIsMusicVideo, isMusicVideo)
                        putBoolean(ExtraIsPodcast, isPodcast)
                    },
                )
                .build(),
        ).build()

private fun SongItem.isMusicVideo(): Boolean {
    val musicVideoType = endpoint?.watchEndpointMusicSupportedConfigs?.watchEndpointMusicConfig?.musicVideoType
    return musicVideoType == MUSIC_VIDEO_TYPE_OMV || musicVideoType == MUSIC_VIDEO_TYPE_UGC
}
