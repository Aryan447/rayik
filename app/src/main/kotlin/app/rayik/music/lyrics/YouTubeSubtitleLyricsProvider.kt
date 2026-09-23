/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package app.rayik.music.lyrics

import android.content.Context
import app.rayik.music.innertube.YouTube
import app.rayik.music.utils.YTPlayerUtils

object YouTubeSubtitleLyricsProvider : LyricsProvider {
    override val name = "YouTube Subtitle"

    override fun isEnabled(context: Context) = true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
    ): Result<String> {
        val authState = YTPlayerUtils.ensureWebPoTokensForSubtitles(id)
        return YouTube.transcript(id, authState)
    }

    override suspend fun getAllLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
        callback: (String) -> Unit,
    ) {
        val authState = YTPlayerUtils.ensureWebPoTokensForSubtitles(id)
        YouTube.transcript(id, authState).onSuccess(callback)
    }
}
