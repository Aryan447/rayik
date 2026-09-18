package app.rayik.music.player

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeFormatTest {
  @Test fun `zero formats as 0 00`() {
    assertEquals("0:00", formatMs(0L))
  }

  @Test fun `seconds pad correctly`() {
    assertEquals("1:01", formatMs(61_000L))
  }

  @Test fun `minutes grow past an hour`() {
    assertEquals("59:59", formatMs(3_599_999L))
  }

  @Test fun `negative clamps to zero`() {
    assertEquals("0:00", formatMs(-5_000L))
  }
}
