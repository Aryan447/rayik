package app.rayik.music.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import app.rayik.music.innertube.models.WatchEndpoint
import app.rayik.music.models.MediaMetadata
import app.rayik.music.playback.PlayerConnection
import app.rayik.music.playback.PlayerConnectionHolder
import app.rayik.music.playback.queues.Queue
import app.rayik.music.playback.queues.YouTubeQueue
import javax.inject.Inject

/** Repeat modes ported from mpvium PlayerViewModel pattern. */
enum class RepeatMode { OFF, ONE, ALL }

/** One visible queue row, resolved from the connection's queue windows. */
data class QueueRow(
  val mediaId: String,
  val title: String,
  val artist: String,
  val artworkUrl: String,
  val isCurrent: Boolean,
)

/**
 * Queue + transport state bound to the vendored playback core through
 * [PlayerConnectionHolder]. The service owns the ExoPlayer; this owns what
 * the UI shows. Failures surface as [PlaybackUiState.Error], never silence.
 */
@HiltViewModel
class PlayerViewModel @Inject constructor(
  private val holder: PlayerConnectionHolder,
) : ViewModel() {
  val connection: StateFlow<PlayerConnection?> = holder.connection

  private val _playbackState = MutableStateFlow<PlaybackUiState>(PlaybackUiState.Idle)
  val playbackState: StateFlow<PlaybackUiState> = _playbackState

  private val _positionMs = MutableStateFlow(0L)
  val positionMs: StateFlow<Long> = _positionMs

  private val _durationMs = MutableStateFlow(0L)
  val durationMs: StateFlow<Long> = _durationMs

  @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
  val repeatMode: StateFlow<RepeatMode> =
    connection.flatMapLatest { conn ->
      if (conn == null) flowOf(RepeatMode.OFF) else conn.repeatMode.map { fromExoRepeat(it) }
    }.stateIn(viewModelScope, SharingStarted.Lazily, RepeatMode.OFF)

  @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
  val shuffleEnabled: StateFlow<Boolean> =
    connection.flatMapLatest { conn ->
      if (conn == null) flowOf(false) else conn.shuffleModeEnabled
    }.stateIn(viewModelScope, SharingStarted.Lazily, false)

  @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
  val connected: StateFlow<Boolean> =
    connection.map { it != null }
      .stateIn(viewModelScope, SharingStarted.Lazily, false)

  @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
  val currentMediaId: StateFlow<String?> =
    connection.flatMapLatest { conn ->
      if (conn == null) flowOf(null) else conn.mediaMetadata.map { it?.id }
    }.stateIn(viewModelScope, SharingStarted.Lazily, null)

  @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
  val queueRows: StateFlow<List<QueueRow>> =
    connection.flatMapLatest { conn ->
      if (conn == null) {
        flowOf(emptyList())
      } else {
        combine(conn.queueWindows, conn.currentWindowIndex) { windows, current ->
          windows.mapIndexed { index, window ->
            val item = window.mediaItem
            val meta = item.mediaMetadata
            QueueRow(
              mediaId = item.mediaId,
              title = meta.title?.toString().orEmpty(),
              artist = meta.artist?.toString().orEmpty(),
              artworkUrl = meta.artworkUri?.toString().orEmpty(),
              isCurrent = index == current,
            )
          }
        }
      }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

  private val positionListener = object : Player.Listener {
    override fun onEvents(player: Player, events: Player.Events) {
      refreshFrom(player)
    }

    override fun onPlayerErrorChanged(playbackError: PlaybackException?) {
      val player = attachedConnection?.player ?: return
      refreshFrom(player)
    }
  }

  private var positionJob: Job? = null
  private var attachedConnection: PlayerConnection? = null

  init {
    viewModelScope.launch {
      holder.connection.collect { conn ->
        attachedConnection?.player?.removeListener(positionListener)
        attachedConnection = conn
        if (conn == null) {
          _playbackState.value = PlaybackUiState.Idle
          positionJob?.cancel()
          positionJob = null
        } else {
          conn.player.addListener(positionListener)
          refreshFrom(conn.player)
        }
      }
    }
  }

  /** Replace the queue and start playback. */
  fun playQueue(queue: Queue) {
    val conn = holder.connection.value ?: return
    _playbackState.value = PlaybackUiState.Loading
    conn.playQueue(queue)
  }

  /** Play a bare video id (Raay picks, retries): radio queue with automix follow. */
  fun playVideo(videoId: String, title: String = "", artist: String = "") {
    val meta = MediaMetadata(
      id = videoId,
      title = title.ifBlank { videoId },
      artists = listOf(MediaMetadata.Artist(id = null, name = artist.ifBlank { "Unknown artist" })),
      duration = -1,
    )
    playQueue(YouTubeQueue.playlist(WatchEndpoint(videoId = videoId), preloadItem = meta))
  }

  fun playWindow(row: QueueRow) {
    val conn = holder.connection.value ?: return
    val player = conn.player
    val timeline = player.currentTimeline
    val window = Timeline.Window()
    for (i in 0 until timeline.windowCount) {
      timeline.getWindow(i, window)
      if (window.mediaItem.mediaId == row.mediaId) {
        player.seekTo(i, 0L)
        player.prepare()
        player.play()
        return
      }
    }
  }

  fun playNext(item: MediaItem) {
    holder.connection.value?.playNext(item)
  }

  fun addToQueue(item: MediaItem) {
    holder.connection.value?.addToQueue(item)
  }

  fun togglePlayPause() {
    val conn = holder.connection.value ?: return
    val player = conn.player
    if (player.playbackState == Player.STATE_IDLE && player.mediaItemCount > 0) {
      player.prepare()
    }
    if (player.isPlaying) player.pause() else player.play()
  }

  fun next() {
    holder.connection.value?.seekToNext()
  }

  fun previous() {
    holder.connection.value?.seekToPrevious()
  }

  fun seekTo(positionMs: Long) {
    holder.connection.value?.player?.seekTo(positionMs.coerceAtLeast(0L))
  }

  fun seekForward() {
    val player = holder.connection.value?.player ?: return
    player.seekTo((player.currentPosition + SEEK_STEP_MS).coerceAtLeast(0L))
  }

  fun seekBack() {
    val player = holder.connection.value?.player ?: return
    player.seekTo((player.currentPosition - SEEK_STEP_MS).coerceAtLeast(0L))
  }

  fun cycleRepeat() {
    val player = holder.connection.value?.player ?: return
    val next = when (fromExoRepeat(player.repeatMode)) {
      RepeatMode.OFF -> RepeatMode.ONE
      RepeatMode.ONE -> RepeatMode.ALL
      RepeatMode.ALL -> RepeatMode.OFF
    }
    player.repeatMode = toExoRepeat(next)
  }

  fun toggleShuffle() {
    val player = holder.connection.value?.player ?: return
    player.shuffleModeEnabled = !player.shuffleModeEnabled
  }

  fun toggleLike() {
    holder.connection.value?.toggleLike()
  }

  fun startRadio() {
    holder.connection.value?.startRadioSeamlessly()
  }

  /** Retry after [PlaybackUiState.Error]: re-prepare and play. */
  fun retry() {
    val player = holder.connection.value?.player ?: return
    player.prepare()
    player.play()
  }

  /** Pure next-index helper — unit-tested, UI just observes. */
  fun nextIndex(queueSize: Int, current: Int, repeat: RepeatMode): Int? =
    QueueRules.nextIndex(queueSize, current, repeat)

  override fun onCleared() {
    positionJob?.cancel()
    attachedConnection?.player?.removeListener(positionListener)
    attachedConnection = null
    super.onCleared()
  }

  private fun refreshFrom(player: Player) {
    val error = player.playerError as? PlaybackException
    if (error != null) {
      _playbackState.value = PlaybackUiState.Error(
        playbackErrorMessage(error.errorCode, error.cause?.message),
      )
    } else {
      _playbackState.value =
        mapPlaybackState(player.playbackState, player.playWhenReady, errorMessage = null)
    }
    _positionMs.value = player.currentPosition.coerceAtLeast(0L)
    _durationMs.value = player.duration.coerceAtLeast(0L)
    updatePositionPolling()
  }

  private fun updatePositionPolling() {
    val playing = _playbackState.value == PlaybackUiState.Playing
    if (!playing) {
      positionJob?.cancel()
      positionJob = null
      return
    }
    if (positionJob?.isActive == true) return
    positionJob = viewModelScope.launch {
      while (isActive) {
        delay(POSITION_POLL_MS)
        val player = attachedConnection?.player ?: break
        _positionMs.value = player.currentPosition.coerceAtLeast(0L)
        _durationMs.value = player.duration.coerceAtLeast(0L)
      }
    }
  }

  companion object {
    const val SEEK_STEP_MS = 10_000L
    const val POSITION_POLL_MS = 500L

    fun toExoRepeat(mode: RepeatMode): Int =
      when (mode) {
        RepeatMode.OFF -> Player.REPEAT_MODE_OFF
        RepeatMode.ONE -> Player.REPEAT_MODE_ONE
        RepeatMode.ALL -> Player.REPEAT_MODE_ALL
      }

    fun fromExoRepeat(mode: Int): RepeatMode =
      when (mode) {
        Player.REPEAT_MODE_ONE -> RepeatMode.ONE
        Player.REPEAT_MODE_ALL -> RepeatMode.ALL
        else -> RepeatMode.OFF
      }
  }
}

/** Pure queue/repeat rules so CI unit tests don't need Android. */
object QueueRules {
  fun nextIndex(queueSize: Int, current: Int, repeat: RepeatMode): Int? {
    if (queueSize <= 0) return null
    return when (repeat) {
      RepeatMode.ONE -> current.coerceIn(0, queueSize - 1)
      RepeatMode.ALL -> (current + 1) % queueSize
      RepeatMode.OFF -> if (current + 1 < queueSize) current + 1 else null
    }
  }

  /**
   * Index of [mediaId] in the full queue's ids, or -1. The ExoPlayer
   * timeline only contains playable items, so transitions must map back by
   * id instead of copying the controller index over.
   */
  fun indexOfId(queueIds: List<String>, mediaId: String): Int =
    queueIds.indexOfFirst { it == mediaId }
}
