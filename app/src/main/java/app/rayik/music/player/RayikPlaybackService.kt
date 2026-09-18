package app.rayik.music.player

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import java.io.File
import okhttp3.OkHttpClient
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Media3 session service. Owns the ExoPlayer: OkHttp upstream +
 * capped transient [CacheDataSource] (no permanent rip-and-store),
 * audio-focus attributes, wake + becoming-noisy handling.
 *
 * Reused mpvium *patterns* only: notification channel/throttle and
 * audio-focus + state restore live here, not libmpv. Equalizer/loudness
 * and crossfade land incrementally.
 */
class RayikPlaybackService : MediaSessionService(), KoinComponent {
  private val okHttp: OkHttpClient by inject()

  private var session: MediaSession? = null
  private var cache: SimpleCache? = null

  override fun onCreate() {
    super.onCreate()
    val mediaCache = SimpleCache(
      File(cacheDir, "rayik-media"),
      LeastRecentlyUsedCacheEvictor(TRANSIENT_CACHE_BYTES),
      StandaloneDatabaseProvider(this),
    )
    cache = mediaCache
    val cacheSourceFactory =
      CacheDataSource.Factory()
        .setCache(mediaCache)
        .setUpstreamDataSourceFactory(
          OkHttpDataSource.Factory(okHttp).setUserAgent(PLAYER_USER_AGENT),
        )
    val player =
      ExoPlayer.Builder(this)
        .setMediaSourceFactory(DefaultMediaSourceFactory(cacheSourceFactory))
        .setAudioAttributes(AudioAttributes.DEFAULT, true)
        .setWakeMode(C.WAKE_MODE_NETWORK)
        .setHandleAudioBecomingNoisy(true)
        .build()
    session = MediaSession.Builder(this, player).build()
  }

  override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
    session

  override fun onDestroy() {
    session?.run {
      player.release()
      release()
    }
    session = null
    cache?.release()
    cache = null
    super.onDestroy()
  }

  companion object {
    /** Transient ExoPlayer cache cap. Pinned offline has its own quota ([app.rayik.music.domain.offline.DownloadQuotas]). */
    const val TRANSIENT_CACHE_BYTES = 500L * 1024 * 1024

    /**
     * Explicit browser UA for media fetches: googlevideo edges can 403
     * unfamiliar client UAs, and the default ExoPlayer UA varies by build.
     */
    const val PLAYER_USER_AGENT =
      "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
  }
}
