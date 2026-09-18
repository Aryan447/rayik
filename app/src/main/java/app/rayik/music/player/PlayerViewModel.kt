package app.rayik.music.player

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Repeat modes ported from mpvium PlayerViewModel pattern. */
enum class RepeatMode { OFF, ONE, ALL }

/** Minimal queue item for v1. Real metadata resolves via streaming layer. */
data class QueueItem(
  val id: String,
  val title: String,
  val artist: String,
  /** Empty until the streaming layer resolves a playable URL. */
  val streamUri: String = "",
  val artworkUrl: String = "",
  /** Container MIME (e.g. `audio/webm`) so ExoPlayer skips type-sniffing. */
  val mimeType: String = "",
)

/**
 * Queue + transport state bound to [RayikPlaybackService] through a
 * MediaController. The service owns the ExoPlayer; this owns what the UI
 * shows and what should play next. Items without a resolved [QueueItem.streamUri]
 * surface as [PlaybackUiState.Error], never silence.
 */
class PlayerViewModel(
  private val appContext: Context,
) : ViewModel() {
  private val _queue = MutableStateFlow<List<QueueItem>>(emptyList())
  val queue: StateFlow<List<QueueItem>> = _queue.asStateFlow()

  private val _repeatMode = MutableStateFlow(RepeatMode.OFF)
  val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

  private val _shuffleEnabled = MutableStateFlow(false)
  val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled.asStateFlow()

  private val _currentIndex = MutableStateFlow(0)
  val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

  private val _playbackState = MutableStateFlow<PlaybackUiState>(PlaybackUiState.Idle)
  val playbackState: StateFlow<PlaybackUiState> = _playbackState.asStateFlow()

  private val _positionMs = MutableStateFlow(0L)
  val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

  private val _durationMs = MutableStateFlow(0L)
  val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

  private val _connected = MutableStateFlow(false)
  val connected: StateFlow<Boolean> = _connected.asStateFlow()

  private var controller: MediaController? = null
  private var positionJob: Job? = null
  private var pendingPlay: Pair<List<QueueItem>, Int>? = null
  private var connectAttempts = 0

  private var controllerFuture = buildControllerFuture()

  private fun buildControllerFuture(): ListenableFuture<MediaController> {
    val token = SessionToken(appContext, ComponentName(appContext, RayikPlaybackService::class.java))
    return MediaController.Builder(appContext, token).buildAsync()
  }

  private val listener = object : Player.Listener {
    override fun onEvents(player: Player, events: Player.Events) {
      refreshFrom(player)
    }

    // Belt and suspenders: error and ready-state transitions must always
    // reach the UI even if event batching changes — a stuck Loading
    // spinner is the failure this guards against.
    override fun onPlayerError(error: PlaybackException) {
      controller?.let { refreshFrom(it) }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
      controller?.let { refreshFrom(it) }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
      val controller = controller ?: return
      val index = controller.currentMediaItemIndex
      if (index >= 0 && _queue.value.isNotEmpty()) {
        _currentIndex.value = index.coerceIn(0, _queue.value.size - 1)
      }
    }

    override fun onRepeatModeChanged(repeatMode: Int) {
      _repeatMode.value = fromExoRepeat(repeatMode)
    }

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
      _shuffleEnabled.value = shuffleModeEnabled
    }
  }

  init {
    controllerFuture.addListener({ onControllerReady() }, MoreExecutors.directExecutor())
  }

  /** Replace the queue and start playback at [startIndex]. */
  fun play(items: List<QueueItem>, startIndex: Int = 0) {
    val controller = controller
    if (controller == null) {
      pendingPlay = items to startIndex
      _playbackState.value = PlaybackUiState.Loading
      return
    }
    startOn(controller, items, startIndex)
  }

  fun togglePlayPause() {
    val controller = controller ?: return
    if (controller.playbackState == Player.STATE_IDLE && _queue.value.isNotEmpty()) {
      controller.prepare()
    }
    if (controller.isPlaying) controller.pause() else controller.play()
  }

  fun next() {
    controller?.seekToNext()
  }

  fun previous() {
    controller?.seekToPrevious()
  }

  fun seekTo(positionMs: Long) {
    controller?.seekTo(positionMs.coerceAtLeast(0L))
  }

  fun seekForward() {
    val controller = controller ?: return
    controller.seekTo((controller.currentPosition + SEEK_STEP_MS).coerceAtLeast(0L))
  }

  fun seekBack() {
    val controller = controller ?: return
    controller.seekTo((controller.currentPosition - SEEK_STEP_MS).coerceAtLeast(0L))
  }

  fun cycleRepeat() {
    _repeatMode.value = when (_repeatMode.value) {
      RepeatMode.OFF -> RepeatMode.ONE
      RepeatMode.ONE -> RepeatMode.ALL
      RepeatMode.ALL -> RepeatMode.OFF
    }
    controller?.repeatMode = toExoRepeat(_repeatMode.value)
  }

  fun toggleShuffle() {
    val controller = controller ?: return
    controller.shuffleModeEnabled = !controller.shuffleModeEnabled
  }

  fun setQueue(items: List<QueueItem>, startIndex: Int = 0) {
    _queue.value = items
    _currentIndex.value = startIndex.coerceIn(0, maxOf(0, items.size - 1))
  }

  /** Retry after [PlaybackUiState.Error]: re-prepare and play. */
  fun retry() {
    val controller = controller ?: return
    controller.prepare()
    controller.play()
  }

  /** Pure next-index helper — unit-tested, UI just observes. */
  fun nextIndex(queueSize: Int, current: Int, repeat: RepeatMode): Int? =
    QueueRules.nextIndex(queueSize, current, repeat)

  override fun onCleared() {
    positionJob?.cancel()
    connectJob?.cancel()
    controller?.removeListener(listener)
    releaseControllerFuture()
    controller = null
    super.onCleared()
  }

  private var connectJob: Job? = null

  /**
   * The service bind can lose a race on cold start; retry a few times
   * before surfacing Unavailable. State stays Loading meanwhile so the
   * UI can say "connecting" instead of failing silently.
   */
  private fun retryConnect() {
    connectAttempts++
    if (connectAttempts > MAX_CONNECT_ATTEMPTS) {
      pendingPlay = null
      _playbackState.value = PlaybackUiState.Error("Player unavailable — try again")
      return
    }
    connectJob?.cancel()
    connectJob = viewModelScope.launch {
      delay(CONNECT_RETRY_MS)
      releaseControllerFuture()
      controllerFuture = buildControllerFuture()
      controllerFuture.addListener({ onControllerReady() }, MoreExecutors.directExecutor())
    }
  }

  private fun releaseControllerFuture() {
    try {
      if (controllerFuture.isDone) {
        MediaController.releaseFuture(controllerFuture)
      } else {
        controllerFuture.cancel(true)
      }
    } catch (_: Exception) {
      // Releasing a half-built future must never crash the ViewModel.
    }
  }

  private fun onControllerReady() {
    val controller = try {
      controllerFuture.get()
    } catch (_: Exception) {
      retryConnect()
      return
    }
    this.controller = controller
    controller.addListener(listener)
    controller.repeatMode = toExoRepeat(_repeatMode.value)
    _connected.value = true
    val pending = pendingPlay
    pendingPlay = null
    if (pending != null) {
      startOn(controller, pending.first, pending.second)
    } else {
      refreshFrom(controller)
    }
  }

  private fun startOn(controller: MediaController, items: List<QueueItem>, startIndex: Int) {
    val playable = items.filter { it.streamUri.isNotBlank() }
    setQueue(items, startIndex)
    if (playable.isEmpty()) {
      _playbackState.value =
        PlaybackUiState.Error(if (items.isEmpty()) "Your queue is empty" else "No playable stream yet")
      return
    }
    // State first: the UI must leave Idle the moment a tap starts playback,
    // not only when the first player event arrives.
    _playbackState.value = PlaybackUiState.Loading
    val firstPlayable = maxOf(0, items.indexOfFirst { it.streamUri.isNotBlank() })
    val target = if (items.getOrNull(startIndex)?.streamUri.isNullOrBlank()) firstPlayable else startIndex
    val mediaItems = playable.map { it.toMediaItem() }
    val startInPlayable = playable.indexOfFirst { it.id == items[target].id }.takeIf { it >= 0 } ?: 0
    controller.setMediaItems(mediaItems, startInPlayable, 0L)
    controller.repeatMode = toExoRepeat(_repeatMode.value)
    controller.prepare()
    controller.play()
  }

  private fun refreshFrom(player: Player) {
    val error = player.playerError as? PlaybackException
    if (error != null) {
      Log.e(
        TAG,
        "playback failure code=${error.errorCode} " +
          "(${PlaybackException.getErrorCodeName(error.errorCode)}) " +
          "cause=${error.cause}",
        error,
      )
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
        val controller = controller ?: break
        _positionMs.value = controller.currentPosition.coerceAtLeast(0L)
        _durationMs.value = controller.duration.coerceAtLeast(0L)
      }
    }
  }

  private fun QueueItem.toMediaItem(): MediaItem =
    MediaItem.Builder()
      .setMediaId(id)
      .setUri(streamUri)
      .apply { if (mimeType.isNotBlank()) setMimeType(mimeType) }
      .setMediaMetadata(
        MediaMetadata.Builder()
          .setTitle(title)
          .setArtist(artist)
          .build(),
      )
      .build()

  companion object {
    const val TAG = "RayikPlayer"
    const val SEEK_STEP_MS = 10_000L
    const val POSITION_POLL_MS = 500L
    const val MAX_CONNECT_ATTEMPTS = 4
    const val CONNECT_RETRY_MS = 3_000L

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
}
