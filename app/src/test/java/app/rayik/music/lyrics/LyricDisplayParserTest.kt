package app.rayik.music.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricDisplayParserTest {
  @Test fun `word-timed karaoke lines parse clean with line times`() {
    val raw = "<01:02.310>गहरा <01:03.128>हुआ,\n<01:06.102>गहरा <01:06.996>हुआ"
    val lines = LyricDisplayParser.parseTimed(raw)
    assertEquals(2, lines.size)
    assertEquals("गहरा हुआ,", lines[0].text)
    assertEquals(62_310L, lines[0].time)
    assertEquals("गहरा हुआ", lines[1].text)
    assertEquals(66_102L, lines[1].time)
  }

  @Test fun `bracket stamps still parse`() {
    val raw = "[01:02.31]Hello\n[01:06.10]World"
    val lines = LyricDisplayParser.parseTimed(raw)
    assertEquals(2, lines.size)
    assertEquals("Hello", lines[0].text)
    assertEquals(62_310L, lines[0].time)
  }

  @Test fun `ttml never leaks markup`() {
    val raw = "<tt xmlns=\"http://www.w3.org/ns/ttml\" xml:lang=\"en\"><body><div>" +
      "<p begin=\"00:01.000\" end=\"00:04.000\"><span begin=\"00:01.000\" end=\"00:02.000\">Hello </span>" +
      "<span begin=\"00:02.000\" end=\"00:04.000\">world</span></p>" +
      "</div></body></tt>"
    val lines = LyricDisplayParser.parseTimed(raw)
    val text = if (lines.isNotEmpty()) {
      lines.forEach {
        assertTrue("no markup in '${it.text}'", '<' !in it.text && '>' !in it.text)
      }
      lines.joinToString(" ") { it.text }
    } else {
      LyricDisplayParser.plainText(raw)
    }
    assertTrue(text.contains("Hello"))
    assertTrue(text.contains("world"))
  }

  @Test fun `plain lyrics fall back to static text`() {
    val raw = "Just words\nMore words"
    assertTrue(LyricDisplayParser.parseTimed(raw).isEmpty())
    assertEquals("Just words\nMore words", LyricDisplayParser.plainText(raw))
  }

  @Test fun `not-found and blanks stay empty`() {
    assertTrue(LyricDisplayParser.parseTimed(null).isEmpty())
    assertTrue(LyricDisplayParser.parseTimed("LYRICS_NOT_FOUND").isEmpty())
    assertEquals("", LyricDisplayParser.plainText("LYRICS_NOT_FOUND"))
    assertEquals("", LyricDisplayParser.plainText("  "))
  }

  @Test fun `metadata-only input shows nothing timed`() {
    val raw = "[ti:Title]\n[ar:Artist]"
    assertTrue(LyricDisplayParser.parseTimed(raw).isEmpty())
  }
}
