package app.rayik.music.playback.haptics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HapticBeatDetectorTest {
  private fun frame(low: Float = 0.2f, mid: Float = 0.15f, high: Float = 0.12f, full: Float = 0.2f) =
    EnergyFrame(low, mid, high, full)

  private fun transientsOf(events: List<HapticEvent>) =
    events.filterIsInstance<HapticEvent.Transient>()

  @Test fun `silence never fires`() {
    val detector = HapticBeatDetector()
    repeat(60) { i ->
      assertTrue(detector.onFrame(frame(0.01f, 0.01f, 0.01f, 0.01f), i * 50L).isEmpty())
    }
  }

  @Test fun `kick spike fires KICK without SNARE`() {
    val detector = HapticBeatDetector()
    repeat(20) { i -> detector.onFrame(frame(), i * 50L) }
    val events = detector.onFrame(frame(low = 0.6f, full = 0.35f), 1000L)
    val transients = transientsOf(events)
    assertEquals(1, transients.size)
    assertEquals(HapticVoice.KICK, transients[0].voice)
    assertTrue(transients[0].strength in 0.15f..1f)
  }

  @Test fun `snare spike fires SNARE without KICK`() {
    val detector = HapticBeatDetector()
    repeat(20) { i -> detector.onFrame(frame(), i * 50L) }
    val events = detector.onFrame(frame(high = 0.5f, full = 0.3f), 1000L)
    val transients = transientsOf(events)
    assertEquals(1, transients.size)
    assertEquals(HapticVoice.SNARE, transients[0].voice)
  }

  @Test fun `kick and snare together both fire`() {
    val detector = HapticBeatDetector()
    repeat(20) { i -> detector.onFrame(frame(), i * 50L) }
    val voices = transientsOf(detector.onFrame(frame(low = 0.6f, high = 0.5f, full = 0.4f), 1000L))
      .map { it.voice }.toSet()
    assertEquals(setOf(HapticVoice.KICK, HapticVoice.SNARE), voices)
  }

  @Test fun `same-voice twin inside cooldown is swallowed`() {
    val detector = HapticBeatDetector()
    repeat(20) { i -> detector.onFrame(frame(), i * 50L) }
    assertEquals(1, transientsOf(detector.onFrame(frame(low = 0.6f), 1000L)).size)
    assertTrue(transientsOf(detector.onFrame(frame(low = 0.65f), 1100L)).isEmpty())
  }

  @Test fun `tempo learning shortens cooldown for fast songs`() {
    val detector = HapticBeatDetector()
    var t = 0L
    repeat(20) { detector.onFrame(frame(), t); t += 50L }
    // 300ms-spaced kicks: after learning, cooldown drops below the ~238ms default.
    repeat(8) {
      detector.onFrame(frame(low = 0.6f), t)
      t += 300L
      repeat(5) { detector.onFrame(frame(), t); t += 50L }
      t -= 250L // frames already advanced 250ms; net +300ms per kick
    }
    assertTrue("cooldown=${detector.cooldownMs()}", detector.cooldownMs() < 240L)
    // A kick 200ms after the last one now fires (default gate would swallow it).
    val last = t
    detector.onFrame(frame(low = 0.6f), last)
    assertEquals(1, transientsOf(detector.onFrame(frame(low = 0.6f), last + 200L)).size)
  }

  @Test fun `agc keeps quiet masters pulsing`() {
    val detector = HapticBeatDetector()
    // Quiet master: everything at 20% scale.
    repeat(40) { i -> detector.onFrame(frame(0.04f, 0.03f, 0.024f, 0.04f), i * 50L) }
    val events = detector.onFrame(frame(low = 0.12f, mid = 0.03f, high = 0.024f, full = 0.07f), 2000L)
    assertEquals(
      listOf(HapticVoice.KICK),
      transientsOf(events).map { it.voice },
    )
  }

  @Test fun `loud chorus after quiet verse emits texture`() {
    val detector = HapticBeatDetector()
    repeat(40) { i -> detector.onFrame(frame(), i * 50L) }
    var sawTexture = false
    repeat(30) { i ->
      val levels = detector.onFrame(frame(0.5f, 0.4f, 0.35f, 0.5f), 2000L + i * 50L)
        .filterIsInstance<HapticEvent.Texture>()
      if (levels.isNotEmpty()) {
        sawTexture = true
        assertTrue(levels[0].level in 0f..1f)
      }
    }
    assertTrue(sawTexture)
  }

  @Test fun `vocals mode fires on mid band and suppresses kick`() {
    val detector = HapticBeatDetector().apply { vocalsOnly = true }
    repeat(20) { i -> detector.onFrame(frame(), i * 50L) }
    assertTrue(transientsOf(detector.onFrame(frame(low = 0.6f, full = 0.35f), 1000L)).isEmpty())
    val vocals = transientsOf(detector.onFrame(frame(mid = 0.45f, full = 0.3f), 1300L))
    assertEquals(1, vocals.size)
    assertEquals(HapticVoice.VOCAL, vocals[0].voice)
  }

  @Test fun `reset clears state`() {
    val detector = HapticBeatDetector()
    repeat(20) { i -> detector.onFrame(frame(), i * 50L) }
    detector.onFrame(frame(low = 0.6f), 1000L)
    detector.reset()
    assertEquals(238L, detector.cooldownMs())
    assertTrue(transientsOf(detector.onFrame(frame(), 2000L)).isEmpty())
  }
}
