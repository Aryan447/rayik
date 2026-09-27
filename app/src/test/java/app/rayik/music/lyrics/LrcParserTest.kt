package app.rayik.music.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {
  @Test fun `word timestamps are stripped and time the line`() {
    val raw = "<01:02.310>गहरा <01:03.128>हुआ,\n<01:06.102>गहरा <01:06.996>हुआ"
    val lines = LrcParser.parseLyrics(raw)
    assertEquals(2, lines.size)
    assertEquals("गहरा हुआ,", lines[0].text)
    assertEquals(62_310L, lines[0].time)
    assertEquals("गहरा हुआ", lines[1].text)
    assertEquals(66_102L, lines[1].time)
  }

  @Test fun `bracket stamps still win and inline tags are cleaned`() {
    val raw = "[01:02.31]<01:02.310>गहरा <01:03.128>हुआ"
    val lines = LrcParser.parseLyrics(raw)
    assertEquals(1, lines.size)
    assertEquals("गहरा हुआ", lines[0].text)
    assertEquals(62_310L, lines[0].time)
  }

  @Test fun `display text strips both tag styles`() {
    val raw = "[01:02.31]<01:02.310>गहरा <01:03.128>हुआ"
    assertEquals("गहरा हुआ", LrcParser.displayLyricsText(raw))
  }

  @Test fun `plain lines still work`() {
    val raw = "Just words\nMore words"
    val lines = LrcParser.parseLyrics(raw)
    assertEquals(2, lines.size)
    assertTrue(lines.all { it.time == 0L })
  }

  @Test fun `metadata tags are ignored`() {
    val raw = "[ti:Title]\n[ar:Artist]\n[01:02.31]Hello"
    val lines = LrcParser.parseLyrics(raw)
    assertEquals(1, lines.size)
    assertEquals("Hello", lines[0].text)
  }
}
