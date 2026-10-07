/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package app.rayik.music.playback.haptics

/** Which drum (or vocal accent) a transient belongs to. */
enum class HapticVoice {
    KICK,
    SNARE,
    VOCAL,
}

/** Apple-style event model: sharp taps plus a sustained texture bed. */
sealed interface HapticEvent {
    data class Transient(val voice: HapticVoice, val strength: Float) : HapticEvent

    data class Texture(val level: Float) : HapticEvent
}

/**
 * Beat/onset detector for synced haptics. Consumes band-split [EnergyFrame]s
 * and emits [HapticEvent]s: kick/snare transients from low/high-band onsets,
 * vocal accents from mid-band onsets (vocals mode), and a sustained texture
 * level while the song runs hot.
 *
 * Three adaptive layers, no FFT, no deps:
 * - per-song AGC: 3s rolling loudness feeds an audibility gate, so quiet
 *   masters still pulse while near-silence never fires;
 * - tempo-adaptive cooldown: median inter-onset interval drives the gate
 *   (fast songs stop feeling choked, slow ones stop double-firing);
 * - per-voice cooldowns, so kick and snare never mute each other.
 *
 * Pure Kotlin (timestamps passed in) — fully unit-testable.
 * Call [reset] on track change / seek.
 */
class HapticBeatDetector(
    private val onsetFactor: Float = 1.4f,
    private val silenceFloor: Float = 0.03f,
    private val bandWindowMs: Long = 1_000L,
    private val agcWindowMs: Long = 3_000L,
) {
    private val lowHistory = ArrayDeque<EnergySample>()
    private val highHistory = ArrayDeque<EnergySample>()
    private val midHistory = ArrayDeque<EnergySample>()
    private val agcHistory = ArrayDeque<EnergySample>()
    private val onsetGaps = ArrayDeque<Long>()

    private var lastTransientMs: Long = Long.MIN_VALUE
    private var beatPeriodMs: Long = DEFAULT_BEAT_PERIOD_MS
    private var smoothGain = 1f
    private val lastVoiceHit = mutableMapOf<HapticVoice, Long>()
    private var emaFull = 0f
    private var emaMid = 0f

    var vocalsOnly: Boolean = false

    fun onFrame(frame: EnergyFrame, nowMs: Long): List<HapticEvent> {
        // Texture keys off raw energy (absolute dynamics: choruses rumble,
        // verses don't). Transients key off raw band ratios — gain cancels
        // out, so AGC motion can never fake an onset; gain only feeds the
        // audibility gate, so quiet masters still pulse.
        emaFull += TEXTURE_SMOOTHING * (frame.full - emaFull)
        emaMid += TEXTURE_SMOOTHING * (frame.mid - emaMid)

        val gain = agcGain(frame.full, nowMs)

        val events = mutableListOf<HapticEvent>()
        if (vocalsOnly) {
            onset(frame.mid, midHistory, HapticVoice.VOCAL, gain, nowMs, factor = 1.5f)?.let { strength ->
                recordTransient(HapticVoice.VOCAL, nowMs)
                events += HapticEvent.Transient(HapticVoice.VOCAL, strength)
            }
            textureLevel(emaMid)?.let { events += HapticEvent.Texture(it) }
        } else {
            onset(frame.low, lowHistory, HapticVoice.KICK, gain, nowMs, factor = onsetFactor)?.let { strength ->
                recordTransient(HapticVoice.KICK, nowMs)
                events += HapticEvent.Transient(HapticVoice.KICK, strength)
            }
            onset(frame.high, highHistory, HapticVoice.SNARE, gain, nowMs, factor = onsetFactor + 0.1f)?.let { strength ->
                recordTransient(HapticVoice.SNARE, nowMs)
                events += HapticEvent.Transient(HapticVoice.SNARE, strength)
            }
            textureLevel(emaFull)?.let { events += HapticEvent.Texture(it) }
        }
        return events
    }

    fun reset() {
        lowHistory.clear()
        highHistory.clear()
        midHistory.clear()
        agcHistory.clear()
        onsetGaps.clear()
        lastVoiceHit.clear()
        smoothGain = 1f
        lastTransientMs = Long.MIN_VALUE
        beatPeriodMs = DEFAULT_BEAT_PERIOD_MS
        emaFull = 0f
        emaMid = 0f
    }

    /** Current gate in ms — exposed for tests and tuning. */
    fun cooldownMs(): Long = (beatPeriodMs * COOLDOWN_BEAT_FRACTION).toLong().coerceIn(MIN_COOLDOWN_MS, MAX_COOLDOWN_MS)

    private fun agcGain(full: Float, nowMs: Long): Float {
        while (agcHistory.isNotEmpty() && nowMs - agcHistory.first().atMs > agcWindowMs) {
            agcHistory.removeFirst()
        }
        agcHistory.addLast(EnergySample(nowMs, full))
        if (agcHistory.size < MIN_AGC_SAMPLES) return smoothGain
        val mean = agcHistory.sumOf { it.energy.toDouble() }.toFloat() / agcHistory.size
        // ponytail: fixed target + clamp, per-song loudness curve if masters still feel off
        val target = (AGC_TARGET / mean.coerceAtLeast(0.05f)).coerceIn(0.5f, 4f)
        // Ramp, don't step: a gain jump would read as a false onset.
        smoothGain += GAIN_SMOOTHING * (target - smoothGain)
        return smoothGain
    }

    private fun onset(
        energy: Float,
        history: ArrayDeque<EnergySample>,
        voice: HapticVoice,
        gain: Float,
        nowMs: Long,
        factor: Float,
    ): Float? {
        while (history.isNotEmpty() && nowMs - history.first().atMs > bandWindowMs) {
            history.removeFirst()
        }
        val average =
            if (history.isEmpty()) 0f else history.sumOf { it.energy.toDouble() }.toFloat() / history.size
        history.addLast(EnergySample(nowMs, energy))
        if (energy < silenceFloor || average <= 0f) return null
        if (energy < average * factor) return null
        if (energy * gain < AUDIBILITY) return null
        val lastHit = lastVoiceHit[voice]
        if (lastHit != null && nowMs - lastHit < cooldownMs()) return null
        return ((energy / average - 1f) / 2f).coerceIn(0.15f, 1f)
    }

    private fun recordTransient(voice: HapticVoice, nowMs: Long) {
        lastVoiceHit[voice] = nowMs
        if (lastTransientMs != Long.MIN_VALUE) {
            val gap = nowMs - lastTransientMs
            if (gap in MIN_BEAT_GAP_MS..MAX_BEAT_GAP_MS) {
                onsetGaps.addLast(gap)
                while (onsetGaps.size > MAX_GAP_SAMPLES) onsetGaps.removeFirst()
                if (onsetGaps.size >= MIN_GAP_SAMPLES) {
                    beatPeriodMs = onsetGaps.sorted()[onsetGaps.size / 2]
                }
            }
        }
        lastTransientMs = nowMs
    }

    private fun textureLevel(ema: Float): Float? {
        if (ema < TEXTURE_ON) return null
        return ((ema - TEXTURE_ON) / TEXTURE_RANGE).coerceIn(0.1f, 1f)
    }

    private data class EnergySample(val atMs: Long, val energy: Float)

    private companion object {
        const val DEFAULT_BEAT_PERIOD_MS = 530L
        const val COOLDOWN_BEAT_FRACTION = 0.45
        const val MIN_COOLDOWN_MS = 150L
        const val MAX_COOLDOWN_MS = 400L
        const val MIN_BEAT_GAP_MS = 200L
        const val MAX_BEAT_GAP_MS = 1500L
        const val MIN_GAP_SAMPLES = 4
        const val MAX_GAP_SAMPLES = 8
        const val MIN_AGC_SAMPLES = 10
        const val AGC_TARGET = 0.22f
        const val GAIN_SMOOTHING = 0.15f
        const val AUDIBILITY = 0.066f
        const val TEXTURE_SMOOTHING = 0.12f
        const val TEXTURE_ON = 0.3f
        const val TEXTURE_RANGE = 0.6f
    }
}
