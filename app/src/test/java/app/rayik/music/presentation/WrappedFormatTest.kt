package app.rayik.music.presentation

import org.junit.Assert.assertEquals
import org.junit.Test

class WrappedFormatTest {
  @Test fun `minutes under an hour`() {
    assertEquals("0m", formatMinutes(0L))
    assertEquals("5m", formatMinutes(300_000L))
    assertEquals("59m", formatMinutes(3_599_999L))
  }

  @Test fun `minutes split hours`() {
    assertEquals("1h 0m", formatMinutes(3_600_000L))
    assertEquals("42h 13m", formatMinutes((42 * 60 + 13) * 60_000L))
  }

  @Test fun `negative clamps`() {
    assertEquals("0m", formatMinutes(-1L))
  }

  @Test fun `hours format 12h clock`() {
    assertEquals("12 AM", formatHour(0))
    assertEquals("1 AM", formatHour(1))
    assertEquals("11 AM", formatHour(11))
    assertEquals("12 PM", formatHour(12))
    assertEquals("11 PM", formatHour(23))
  }

  @Test fun `hour wraps`() {
    assertEquals("1 AM", formatHour(25))
  }
}
