/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package app.rayik.music.innertube.pages

import app.rayik.music.innertube.models.Artist
import app.rayik.music.innertube.models.EpisodeItem
import app.rayik.music.innertube.models.MusicMultiRowListItemRenderer
import app.rayik.music.innertube.models.PodcastItem
import app.rayik.music.innertube.models.PODCAST_SHOW_BROWSE_PREFIX
import app.rayik.music.innertube.models.Run
import app.rayik.music.innertube.models.getContinuation
import app.rayik.music.innertube.models.response.BrowseResponse
import app.rayik.music.innertube.utils.parseTime

data class PodcastPage(
    val podcast: PodcastItem,
    val description: String?,
    val episodes: List<EpisodeItem>,
    val continuation: String?,
) {
    data class Continuation(
        val episodes: List<EpisodeItem>,
        val continuation: String?,
    )

    companion object {
        fun fromResponse(
            response: BrowseResponse,
            browseId: String,
        ): PodcastPage {
            val primarySection =
                response.contents
                    ?.twoColumnBrowseResultsRenderer
                    ?.tabs
                    ?.firstNotNullOfOrNull { it?.tabRenderer?.content?.sectionListRenderer }
                    ?: error("YouTube Music podcast response is missing its primary section")
            val header =
                primarySection.contents
                    .orEmpty()
                    .firstNotNullOfOrNull { it.musicResponsiveHeaderRenderer }
                    ?: error("YouTube Music podcast response is missing its header")
            val thumbnail = header.thumbnail?.musicThumbnailRenderer?.getBestThumbnail()
            val podcast =
                PodcastItem(
                    browseId = browseId,
                    playlistId =
                        header.buttons
                            .firstNotNullOfOrNull { it.musicPlayButtonRenderer?.playNavigationEndpoint?.anyWatchEndpoint?.playlistId }
                            ?: browseId.removePrefix(PODCAST_SHOW_BROWSE_PREFIX).takeIf(String::isNotBlank),
                    title = header.title.textOrNull() ?: error("YouTube Music podcast response is missing its title"),
                    author = (header.straplineTextOne?.runs ?: header.subtitle.runs).orEmpty().toPodcastArtist(),
                    thumbnail = thumbnail?.normalizedUrl,
                    thumbnailWidth = thumbnail?.width,
                    thumbnailHeight = thumbnail?.height,
                )
            val secondarySection =
                response.contents
                    ?.twoColumnBrowseResultsRenderer
                    ?.secondaryContents
                    ?.sectionListRenderer
            val shelf = secondarySection?.contents.orEmpty().firstNotNullOfOrNull { it.musicShelfRenderer }
            val episodes = shelf?.contents.orEmpty().mapNotNull { it.musicMultiRowListItemRenderer?.toEpisodeItem() }
            val showArtist = podcast.author ?: Artist(podcast.title, podcast.browseId)
            val description =
                header.description
                    ?.musicDescriptionShelfRenderer
                    ?.description
                    ?.textOrNull()
                    ?: primarySection.contents
                        .orEmpty()
                        .firstNotNullOfOrNull { it.musicDescriptionShelfRenderer?.description?.textOrNull() }
            return PodcastPage(
                podcast = podcast,
                description = description,
                episodes = episodes.map { episode -> episode.copy(podcast = episode.podcast ?: showArtist) },
                continuation = shelf?.continuations?.getContinuation() ?: shelf?.contents.orEmpty().getContinuation(),
            )
        }

        fun continuationFromResponse(response: BrowseResponse): Continuation {
            val actionContents =
                response.onResponseReceivedActions
                    ?.flatMap { it.appendContinuationItemsAction?.continuationItems.orEmpty() }
                    .orEmpty()
            val shelf = response.continuationContents?.musicShelfContinuation
            val playlistShelf = response.continuationContents?.musicPlaylistShelfContinuation
            val contents =
                actionContents.ifEmpty {
                    shelf?.contents.orEmpty().ifEmpty { playlistShelf?.contents.orEmpty() }
                }
            val episodes = contents.mapNotNull { it.musicMultiRowListItemRenderer?.toEpisodeItem() }
            return Continuation(
                episodes = episodes,
                continuation =
                    contents.getContinuation()
                        ?: shelf?.continuations?.getContinuation()
                        ?: playlistShelf?.continuations?.getContinuation(),
            )
        }
    }
}

internal fun MusicMultiRowListItemRenderer.toEpisodeItem(): EpisodeItem? {
    val endpoint =
        onTap?.anyWatchEndpoint
            ?: overlay
                ?.musicItemThumbnailOverlayRenderer
                ?.content
                ?.musicPlayButtonRenderer
                ?.playNavigationEndpoint
                ?.anyWatchEndpoint
    val videoId = endpoint?.videoId?.takeIf(String::isNotBlank) ?: return null
    val itemTitle = title?.textOrNull() ?: return null
    val bestThumbnail = thumbnail?.musicThumbnailRenderer?.getBestThumbnail() ?: return null
    val durationText =
        playbackProgress
            ?.musicPlaybackProgressRenderer
            ?.durationText
            ?.textOrNull()
    val subtitleRuns = subtitle?.runs.orEmpty()
    val podcast = subtitleRuns.toPodcastArtist()
    return EpisodeItem(
        id = videoId,
        browseId =
            title
                ?.runs
                ?.firstNotNullOfOrNull { it.navigationEndpoint?.browseEndpoint }
                ?.takeIf { it.isPodcastEpisodeEndpoint }
                ?.browseId,
        title = itemTitle,
        podcast = podcast,
        description = description?.textOrNull(),
        dateText = subtitleRuns.episodeDateText(podcast?.name),
        durationText = durationText,
        duration = durationText?.parseTime(),
        thumbnail = bestThumbnail.normalizedUrl,
        endpoint = endpoint,
        thumbnailWidth = bestThumbnail.width,
        thumbnailHeight = bestThumbnail.height,
    )
}

private fun app.rayik.music.innertube.models.Runs.textOrNull(): String? =
    runs?.joinToString(separator = "") { it.text }?.trim()?.takeIf(String::isNotBlank)

private fun List<Run>.toPodcastArtist(): Artist? =
    firstNotNullOfOrNull { run ->
        run.navigationEndpoint
            ?.browseEndpoint
            ?.takeIf { it.isPodcastShowEndpoint || it.isArtistEndpoint || it.browseId.startsWith("UC") }
            ?.let { Artist(name = run.text, id = it.browseId) }
    }

private fun List<Run>.episodeDateText(podcastName: String?): String? =
    firstOrNull { run ->
        val text = run.text.trim()
        text.isNotBlank() &&
            text.any(Char::isLetterOrDigit) &&
            text.parseTime() == null &&
            text != podcastName
    }?.text?.trim()
