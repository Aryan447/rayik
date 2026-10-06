package app.rayik.music.playback.haptics

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HapticBeatDetectorTest {
  @Test fun `silence never fires`() {
    val detector = HapticBeatDetector()
    repeat(50) { i ->
      assertNull(detector.onEnergy(0.01f, i * 20L))
    }
  }

  @Test fun `kick above average fires once then cools down`() {
    val detector = HapticBeatDetector()
    repeat(40) { i -> detector.onEnergy(0.2f, i * 20L) }
    val beat = detector.onEnergy(0.6f, 800L)
    assertNotNull(beat)
    assertTrue(beat!!.strength in 0.15f..1f)
    // Immediate twin onset inside cooldown is swallowed.
    assertNull(detector.onEnergy(0.7f, 850L))
    // After cooldown a second kick fires again.
    assertNotNull(detector.onEnergy(0.65f, 1200L))
  }

  @Test fun `reset clears history so old loudness cannot retrigger`() {
    val detector = HapticBeatDetector()
    repeat(40) { i -> detector.onEnergy(0.5f, i * 20L) }
    detector.reset()
    assertNull(detector.onEnergy(0.5f, 900L))
  }
}
