package app.rayik.music.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import app.rayik.music.R
import app.rayik.music.streaming.youtube.InnerTubeResolver
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

  override fun onCreate() {
    super.onCreate()
    val mediaCache = RayikMediaCache.getInstance(this)
    val cacheSourceFactory =
      CacheDataSource.Factory()
        .setCache(mediaCache)
        .setUpstreamDataSourceFactory(
          OkHttpDataSource.Factory(okHttp).setUserAgent(PLAYER_USER_AGENT),
        )
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    val audioAttributes = AudioAttributes.Builder()
      .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
      .setUsage(C.USAGE_MEDIA)
      .build()

    val player =
      ExoPlayer.Builder(this)
        .setMediaSourceFactory(DefaultMediaSourceFactory(cacheSourceFactory))
        .setAudioAttributes(audioAttributes, true)
        .setWakeMode(C.WAKE_MODE_NETWORK)
        .setHandleAudioBecomingNoisy(true)
        .build()
    session = MediaSession.Builder(this, player).build()
    val notificationProvider = DefaultMediaNotificationProvider.Builder(this).build()
    notificationProvider.setSmallIcon(R.drawable.ic_launcher_monochrome)
    setMediaNotificationProvider(notificationProvider)
  }

  override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
    session

  override fun onDestroy() {
    session?.run {
      player.release()
      release()
    }
    session = null
    super.onDestroy()
  }

  companion object {
    /** Transient ExoPlayer cache cap. Pinned offline has its own quota ([app.rayik.music.domain.offline.DownloadQuotas]). */
    const val TRANSIENT_CACHE_BYTES = 500L * 1024 * 1024

    /**
     * User-Agent matching the InnerTube ANDROID client that resolved the
     * googlevideo stream URL. Google Video edge servers reject/403 requests
     * where the token's client (c=ANDROID) conflicts with the User-Agent.
     */
    const val PLAYER_USER_AGENT = InnerTubeResolver.USER_AGENT
  }
}

/**
 * Process-wide singleton for the transient ExoPlayer disk cache.
 * SimpleCache locks the directory with a file lock; keeping a single instance
 * prevents "Another SimpleCache instance already exists" crashes when
 * RayikPlaybackService restarts.
 */
object RayikMediaCache {
  @Volatile private var cache: SimpleCache? = null
  @Volatile private var databaseProvider: StandaloneDatabaseProvider? = null

  @Synchronized
  fun getInstance(context: Context): SimpleCache {
    return cache ?: run {
      val app = context.applicationContext
      val db = databaseProvider ?: StandaloneDatabaseProvider(app).also { databaseProvider = it }
      val cacheDir = File(app.cacheDir, "rayik-media")
      SimpleCache(
        cacheDir,
        LeastRecentlyUsedCacheEvictor(RayikPlaybackService.TRANSIENT_CACHE_BYTES),
        db,
      ).also { cache = it }
    }
  }
}
