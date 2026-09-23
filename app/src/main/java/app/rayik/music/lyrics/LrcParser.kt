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

    fun parseLyrics(raw: String): List<LyricsEntry> {
        val out = mutableListOf<LyricsEntry>()
        var lastTime = 0L
        var pendingUntimed = mutableListOf<String>()
        for (line in raw.lines()) {
            val stamps = timestamp.findAll(line).toList()
            var text = timestamp.replace(line, "").trim()
            if (stamps.isEmpty()) {
                if (text.isBlank() || metadataTag.matches(line.trim())) continue
                if (lastTime == 0L && out.isEmpty()) {
                    pendingUntimed.add(text)
                } else {
                    out.add(LyricsEntry(time = lastTime, text = text))
                }
                continue
            }
            if (text.isBlank()) continue
            for (m in stamps) {
                val minutes = m.groupValues[1].toLongOrNull() ?: continue
                val seconds = m.groupValues[2].toLongOrNull() ?: continue
                val frac = m.groupValues[3]
                val millis =
                    when (frac.length) {
                        0 -> 0L
                        1 -> frac.toLongOrNull()?.times(100) ?: 0L
                        2 -> frac.toLongOrNull()?.times(10) ?: 0L
                        else -> frac.take(3).toLongOrNull() ?: 0L
                    }
                lastTime = minutes * 60_000L + seconds * 1_000L + millis
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
            .map { timestamp.replace(it, "").trim() }
            .filter { it.isNotBlank() && !metadataTag.matches(it) }
            .joinToString("\n")
}
