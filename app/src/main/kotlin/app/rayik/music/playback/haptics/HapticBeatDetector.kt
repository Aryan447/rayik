/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package app.rayik.music.playback.haptics

/**
 * Onset detector for synced haptics: feeds per-buffer RMS energy, emits a
 * [Beat] when energy jumps above its recent average. No FFT, no deps.
 *
 * Call [reset] on track change / seek so stale history can't false-trigger.
 */
class HapticBeatDetector(
    private val thresholdFactor: Float = 1.35f,
    private val cooldownMs: Long = 240L,
    private val historyWindowMs: Long = 1_000L,
    private val silenceFloor: Float = 0.04f,
) {
    private val history = ArrayDeque<EnergySample>()
    private var lastBeatMs: Long = Long.MIN_VALUE

    fun onEnergy(energy: Float, nowMs: Long): Beat? {
        prune(nowMs)
        val average = if (history.isEmpty()) 0f else history.sumOf { it.energy.toDouble() }.toFloat() / history.size
        history.addLast(EnergySample(nowMs, energy))
        if (energy < silenceFloor || average <= 0f) return null
        if (energy < average * thresholdFactor) return null
        if (lastBeatMs != Long.MIN_VALUE && nowMs - lastBeatMs < cooldownMs) return null
        lastBeatMs = nowMs
        // ponytail: linear strength from relative jump, per-song tempo tracking if feel is off
        val strength = ((energy / average - 1f) / 2f).coerceIn(0.15f, 1f)
        return Beat(strength)
    }

    fun reset() {
        history.clear()
        lastBeatMs = Long.MIN_VALUE
    }

    private fun prune(nowMs: Long) {
        while (history.isNotEmpty() && nowMs - history.first().atMs > historyWindowMs) {
            history.removeFirst()
        }
    }

    private data class EnergySample(val atMs: Long, val energy: Float)
}

@JvmInline
value class Beat(val strength: Float)
