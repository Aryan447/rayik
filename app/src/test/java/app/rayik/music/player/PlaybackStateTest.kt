package app.rayik.music.player

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackStateTest {
  @Test fun `idle maps to Idle`() {
    assertEquals(
      PlaybackUiState.Idle,
      mapPlaybackState(Player.STATE_IDLE, playWhenReady = false, errorMessage = null),
    )
  }

  @Test fun `buffering maps to Loading`() {
    assertEquals(
      PlaybackUiState.Loading,
      mapPlaybackState(Player.STATE_BUFFERING, playWhenReady = true, errorMessage = null),
    )
  }

  @Test fun `ready and playing maps to Playing`() {
    assertEquals(
      PlaybackUiState.Playing,
      mapPlaybackState(Player.STATE_READY, playWhenReady = true, errorMessage = null),
    )
  }

  @Test fun `ready and paused maps to Paused`() {
    assertEquals(
      PlaybackUiState.Paused,
      mapPlaybackState(Player.STATE_READY, playWhenReady = false, errorMessage = null),
    )
  }

  @Test fun `ended maps to Paused`() {
    assertEquals(
      PlaybackUiState.Paused,
      mapPlaybackState(Player.STATE_ENDED, playWhenReady = false, errorMessage = null),
    )
  }

  @Test fun `error wins over player state and keeps message`() {
    val state = mapPlaybackState(Player.STATE_READY, playWhenReady = true, errorMessage = "timeout")
    assertTrue(state is PlaybackUiState.Error)
    assertEquals("timeout", (state as PlaybackUiState.Error).message)
  }

  @Test fun `blank error message falls back to default copy`() {
    val state = mapPlaybackState(Player.STATE_IDLE, playWhenReady = false, errorMessage = "")
    assertEquals(PlaybackUiState.Error("Can't play this right now"), state)
  }

  @Test fun `unknown state maps to Idle`() {
    assertEquals(
      PlaybackUiState.Idle,
      mapPlaybackState(-1, playWhenReady = false, errorMessage = null),
    )
  }

  @Test fun `repeat modes round-trip through Exo constants`() {
    assertEquals(RepeatMode.OFF, PlayerViewModel.fromExoRepeat(PlayerViewModel.toExoRepeat(RepeatMode.OFF)))
    assertEquals(RepeatMode.ONE, PlayerViewModel.fromExoRepeat(PlayerViewModel.toExoRepeat(RepeatMode.ONE)))
    assertEquals(RepeatMode.ALL, PlayerViewModel.fromExoRepeat(PlayerViewModel.toExoRepeat(RepeatMode.ALL)))
  }
}
