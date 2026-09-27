package app.rayik.music.lyrics

/**
 * Minimal LRC parser owned by rāyik.
 *
 * Parses `[mm:ss.xx]` timestamped lines into [LyricsEntry] items
 * for follow-along highlighting. Untagged metadata lines (`[ti:]`,
 * `[ar:]`, …) are ignored; plain lines without a timestamp attach to the
 * most recent timestamp so they still render in order.
 */
object LrcParser {
    private val timestamp = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
    private val metadataTag = Regex("""\[[a-zA-Z]+:.*]""")

    /**
     * Word-level timestamps (`<mm:ss.ms>word`) shipped by karaoke
     * providers. Stripped for display; the first tag times the line so
     * follow-along still works.
     */
    private val wordTimestamp = Regex("""<(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?>""")

    fun parseLyrics(raw: String): List<LyricsEntry> {
        val out = mutableListOf<LyricsEntry>()
        var lastTime = 0L
        var pendingUntimed = mutableListOf<String>()
        for (line in raw.lines()) {
            val stamps = timestamp.findAll(line).toList()
            val text = wordTimestamp.replace(timestamp.replace(line, ""), "").trim()
            if (stamps.isEmpty()) {
                if (text.isBlank() || metadataTag.matches(line.trim())) continue
                val wordTime = wordTimestamp.find(line)?.let(::stampToMs)
                if (lastTime == 0L && wordTime == null && out.isEmpty()) {
                    pendingUntimed.add(text)
                } else {
                    val time = wordTime ?: lastTime
                    lastTime = time
                    out.add(LyricsEntry(time = time, text = text))
                }
                continue
            }
            if (text.isBlank()) continue
            for (m in stamps) {
                lastTime = stampToMs(m)
                out.add(LyricsEntry(time = lastTime, text = text))
            }
        }
        if (out.isEmpty() && pendingUntimed.isNotEmpty()) {
            return pendingUntimed.map { LyricsEntry(time = 0L, text = it) }
        }
        out.sortBy { it.time }
        return out
    }

    fun displayLyricsText(raw: String): String =
        raw.lines()
            .map { wordTimestamp.replace(timestamp.replace(it, ""), "").trim() }
            .filter { it.isNotBlank() && !metadataTag.matches(it) }
            .joinToString("\n")

    private fun stampToMs(m: MatchResult): Long {
        val minutes = m.groupValues[1].toLongOrNull() ?: return 0L
        val seconds = m.groupValues[2].toLongOrNull() ?: return 0L
        val frac = m.groupValues[3]
        val millis =
            when (frac.length) {
                0 -> 0L
                1 -> frac.toLongOrNull()?.times(100) ?: 0L
                2 -> frac.toLongOrNull()?.times(10) ?: 0L
                else -> frac.take(3).toLongOrNull() ?: 0L
            }
        return minutes * 60_000L + seconds * 1_000L + millis
    }
}
