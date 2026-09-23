package app.rayik.music.player

import androidx.media3.common.Player

/**
 * UI-facing playback state. Every screen renders one of these — loading,
 * unavailable (Error), or content — never blank, per AGENTS.md.
 */
sealed interface PlaybackUiState {
  data object Idle : PlaybackUiState
  data object Loading : PlaybackUiState
  data object Playing : PlaybackUiState
  data object Paused : PlaybackUiState
  data class Error(val message: String) : PlaybackUiState
}

/**
 * Pure mapper from ExoPlayer state to [PlaybackUiState] so the rule is
 * unit-tested and the UI just observes.
 */
fun mapPlaybackState(
  playerState: Int,
  playWhenReady: Boolean,
  errorMessage: String?,
): PlaybackUiState {
  if (errorMessage != null) {
    return PlaybackUiState.Error(errorMessage.takeIf { it.isNotBlank() } ?: "Can't play this right now")
  }
  return when (playerState) {
    Player.STATE_IDLE -> PlaybackUiState.Idle
    Player.STATE_BUFFERING -> PlaybackUiState.Loading
    Player.STATE_READY -> if (playWhenReady) PlaybackUiState.Playing else PlaybackUiState.Paused
    Player.STATE_ENDED -> PlaybackUiState.Paused
    else -> PlaybackUiState.Idle
  }
}
