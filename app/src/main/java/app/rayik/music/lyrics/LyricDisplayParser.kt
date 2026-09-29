package app.rayik.music.lyrics

/**
 * The single entry point for everything lyric UI renders. The vendored
 * engine speaks five timed formats (QRC, enhanced karaoke LRC,
 * line-synced LRC, millisecond lines, TTML) — routing through
 * [LyricsUtils] keeps provider output from ever leaking raw markup,
 * timestamps or metadata onto the screen. Never parse here; delegate.
 */
object LyricDisplayParser {
    /**
     * Timed lines sorted by start, or empty when there is nothing
     * follow-alongable (plain text and not-found included).
     */
    fun parseTimed(raw: String?): List<LyricsEntry> {
        if (raw.isNullOrBlank() || raw == "LYRICS_NOT_FOUND") return emptyList()
        return runCatching {
            val normalized = promoteWordTimedLines(raw)
            val parsed = if (LyricsUtils.isTtml(normalized)) {
                LyricsUtils.parseTtml(normalized)
            } else {
                LyricsUtils.parseLyrics(normalized)
            }
            parsed.filter { it.text.isNotBlank() }.sortedBy { it.time }
        }.getOrElse { emptyList() }
    }

    /**
     * Karaoke lines carry only `<mm:ss.ms>` word tags, which the engine's
     * line parsers skip (they require a `[...]` line stamp). Promote the
     * first word tag into a bracket stamp so the enhanced parser handles
     * the line natively — with word timings intact.
     */
    private val wordTag = Regex("""<(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?>""")

    private fun promoteWordTimedLines(raw: String): String =
        raw.lines().joinToString("\n") { line ->
            if (line.contains('[') || !wordTag.containsMatchIn(line)) {
                line
            } else {
                val stamp = wordTag.find(line)?.toBracketStamp()
                if (stamp == null) line else stamp + line
            }
        }

    private fun MatchResult.toBracketStamp(): String? {
        val minutes = groupValues[1].toLongOrNull() ?: return null
        val seconds = groupValues[2].toLongOrNull()?.takeIf { it in 0L..59L } ?: return null
        val millis = when (val frac = groupValues[3]) {
            "" -> 0L
            else -> frac.take(3).padEnd(3, '0').toLongOrNull() ?: return null
        }
        val totalMs = minutes * 60_000L + seconds * 1_000L + millis
        val mm = (totalMs / 60_000L).toString().padStart(2, '0')
        val ss = ((totalMs % 60_000L) / 1_000L).toString().padStart(2, '0')
        val mmm = (totalMs % 1_000L).toString().padStart(3, '0')
        return "[$mm:$ss.$mmm]"
    }

    /**
     * Static text for untimed lyrics. Empty when [parseTimed] already
     * covers the content or there is nothing to show.
     */
    fun plainText(raw: String?): String {
        if (raw.isNullOrBlank() || raw == "LYRICS_NOT_FOUND") return ""
        if (parseTimed(raw).isNotEmpty()) return ""
        return runCatching { LyricsUtils.displayLyricsText(raw) }.getOrDefault("")
    }
}
