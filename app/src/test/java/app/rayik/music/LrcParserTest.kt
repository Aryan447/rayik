package app.rayik.music.streaming.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {
  @Test fun `parses centisecond timestamps`() {
    val lines = LrcParser.parse("[00:12.34] Hello\n[01:02.50] World")
    assertEquals(2, lines.size)
    assertEquals(12_340L, lines[0].startMs)
    assertEquals("Hello", lines[0].text)
    assertEquals(62_500L, lines[1].startMs)
  }

  @Test fun `parses millisecond timestamps and skips metadata`() {
    val lrc = "[ar:Artist]\n[ti:Title]\n[00:05.123] Line\n\n[bad row]\n[00:10] Bare"
    val lines = LrcParser.parse(lrc)
    assertEquals(2, lines.size)
    assertEquals(5_123L, lines[0].startMs)
    assertEquals(10_000L, lines[1].startMs)
  }

  @Test fun `sorts out-of-order lines`() {
    val lines = LrcParser.parse("[00:10.00] B\n[00:05.00] A")
    assertEquals("A", lines[0].text)
    assertEquals("B", lines[1].text)
  }

  @Test fun `active index tracks position`() {
    val lines = LrcParser.parse("[00:05.00] A\n[00:10.00] B\n[00:15.00] C")
    assertEquals(-1, LrcParser.activeIndex(lines, 0L))
    assertEquals(0, LrcParser.activeIndex(lines, 5_000L))
    assertEquals(1, LrcParser.activeIndex(lines, 12_000L))
    assertEquals(2, LrcParser.activeIndex(lines, 99_000L))
  }

  @Test fun `empty input parses to empty`() {
    assertTrue(LrcParser.parse("").isEmpty())
  }
}
