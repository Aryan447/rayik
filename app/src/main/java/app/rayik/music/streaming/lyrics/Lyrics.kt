package app.rayik.music.streaming.lyrics

// Lyric lines for one track. Synced lines highlight as the song plays;
// plain lines scroll statically.
data class LyricsResult(
  val lines: List<LyricLine>,
  val synced: Boolean
) {
  companion object {
    val EMPTY = LyricsResult(emptyList(), false)
  }
}

// Start time in millis. -1 for unsynced lines.
data class LyricLine(
  val startMs: Long,
  val text: String
)

// Pure LRC parser ([mm:ss.xx] line). Tolerates metadata tags,
// blank lines, and malformed rows: they are skipped, never fatal.
// JVM-testable, no framework types.
object LrcParser {
  private val LINE = Regex("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?\\](.*)")
  private val META = Regex("\\[[a-zA-Z]+:.*\\]")

  fun parse(lrc: String): List<LyricLine> {
    val lines = mutableListOf<LyricLine>()
    for (raw in lrc.lineSequence()) {
      val row = raw.trim()
      if (row.isEmpty() || META.matches(row)) continue
      val match = LINE.find(row) ?: continue
      val minutes = match.groupValues[1].toLongOrNull() ?: continue
      val seconds = match.groupValues[2].toLongOrNull() ?: continue
      val fraction = match.groupValues[3]
      val millis = if (fraction.isEmpty()) {
        0L
      } else if (fraction.length == 2) {
        fraction.toLongOrNull()?.times(10) ?: 0L
      } else {
        fraction.take(3).toLongOrNull() ?: 0L
      }
      val text = match.groupValues[4].trim()
      if (text.isEmpty()) continue
      lines.add(LyricLine(minutes * 60_000L + seconds * 1_000L + millis, text))
    }
    return lines.sortedBy { it.startMs }
  }

  // Index of the line playing at positionMs, or -1 when none yet.
  fun activeIndex(lines: List<LyricLine>, positionMs: Long): Int {
    var active = -1
    for (i in lines.indices) {
      if (lines[i].startMs <= positionMs) {
        active = i
      } else {
        break
      }
    }
    return active
  }
}
