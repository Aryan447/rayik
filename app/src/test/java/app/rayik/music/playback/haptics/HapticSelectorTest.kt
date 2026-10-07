package app.rayik.music.playback.haptics

import app.rayik.music.constants.SyncedHapticsIntensity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HapticSelectorTest {
  @Test fun `kick maps to thump at full scale on HIGH`() {
    val command = HapticSelector.select(
      HapticEvent.Transient(HapticVoice.KICK, 1f),
      SyncedHapticsIntensity.HIGH,
    )
    assertNotNull(command)
    assertEquals(CompositionPrimitives.THUMP, command!!.primitive)
    assertEquals(1f, command.scale)
    assertEquals(55L, command.fallbackDurationMs)
    assertEquals(255, command.fallbackAmplitude)
  }

  @Test fun `low intensity halves scale`() {
    val command = HapticSelector.select(
      HapticEvent.Transient(HapticVoice.KICK, 1f),
      SyncedHapticsIntensity.LOW,
    )
    assertEquals(0.5f, command!!.scale)
    assertEquals(25L, command.fallbackDurationMs)
  }

  @Test fun `snare is softer and shorter than kick`() {
    val kick = HapticSelector.select(
      HapticEvent.Transient(HapticVoice.KICK, 0.8f),
      SyncedHapticsIntensity.MEDIUM,
    )!!
    val snare = HapticSelector.select(
      HapticEvent.Transient(HapticVoice.SNARE, 0.8f),
      SyncedHapticsIntensity.MEDIUM,
    )!!
    assertEquals(CompositionPrimitives.TICK, snare.primitive)
    assertTrue(snare.scale < kick.scale)
    assertTrue(snare.fallbackDurationMs < kick.fallbackDurationMs)
  }

  @Test fun `vocal is softest transient`() {
    val snare = HapticSelector.select(
      HapticEvent.Transient(HapticVoice.SNARE, 0.8f),
      SyncedHapticsIntensity.MEDIUM,
    )!!
    val vocal = HapticSelector.select(
      HapticEvent.Transient(HapticVoice.VOCAL, 0.8f),
      SyncedHapticsIntensity.MEDIUM,
    )!!
    assertTrue(vocal.scale < snare.scale)
    assertTrue(vocal.fallbackAmplitude < snare.fallbackAmplitude)
  }

  @Test fun `texture maps to swell with no one-shot fallback`() {
    val command = HapticSelector.select(
      HapticEvent.Texture(0.6f),
      SyncedHapticsIntensity.HIGH,
    )
    assertEquals(CompositionPrimitives.SWELL, command!!.primitive)
    assertEquals(0.6f, command.scale, 0.001f)
    assertEquals(0L, command.fallbackDurationMs)
  }

  @Test fun `scale never drops below floor`() {
    val command = HapticSelector.select(
      HapticEvent.Transient(HapticVoice.VOCAL, 0.05f),
      SyncedHapticsIntensity.LOW,
    )
    assertEquals(0.1f, command!!.scale)
  }
}
