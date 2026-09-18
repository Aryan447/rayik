package app.rayik.music.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QueueRulesTest {
  @Test fun `off stops at end`() {
    assertEquals(1, QueueRules.nextIndex(3, 0, RepeatMode.OFF))
    assertNull(QueueRules.nextIndex(3, 2, RepeatMode.OFF))
  }

  @Test fun `one repeats current`() {
    assertEquals(2, QueueRules.nextIndex(3, 2, RepeatMode.ONE))
  }

  @Test fun `all wraps`() {
    assertEquals(0, QueueRules.nextIndex(3, 2, RepeatMode.ALL))
  }

  @Test fun `empty queue returns null`() {
    assertNull(QueueRules.nextIndex(0, 0, RepeatMode.ALL))
  }
}
