/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package app.rayik.music.playback.haptics

import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/** Per-band RMS energy snapshot, each 0..~1. */
data class EnergyFrame(
    val low: Float,
    val mid: Float,
    val high: Float,
    val full: Float,
)

/**
 * Zero-permission PCM tap for synced haptics. Sits in the ExoPlayer
 * [AudioProcessor] chain as pure passthrough — audio bytes are copied
 * untouched — and emits a band-split [EnergyFrame] (~20/s) while [enabled].
 *
 * Bands come from two one-pole filters (no FFT, no deps): lowpass ≈300Hz
 * isolates kicks, highpass ≈4kHz on the residual isolates snares/hats,
 * mid is whatever remains (vocal range). Disabled = one memcpy, no
 * computation, no callbacks.
 */
@UnstableApi
class HapticTapProcessor : BaseAudioProcessor() {
    @Volatile
    var enabled: Boolean = false

    var listener: ((EnergyFrame) -> Unit)? = null

    private var lastEmitMs = 0L
    private var lpAlpha = 0.038f
    private var hpAlpha = 0.657f
    private var lpY = 0f
    private var hpY = 0f
    private var hpPrevIn = 0f

    // Per-buffer accumulators as members: no allocation on the audio thread.
    private var sumLow = 0.0
    private var sumMid = 0.0
    private var sumHigh = 0.0
    private var sumFull = 0.0
    private var sumCount = 0

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        retune(inputAudioFormat.sampleRate)
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        if (enabled) {
            maybeSample(inputBuffer)
        }
        val out = replaceOutputBuffer(inputBuffer.remaining())
        out.put(inputBuffer)
        out.flip()
    }

    @Suppress("DEPRECATION")
    public override fun onFlush() {
        super.onFlush()
        lpY = 0f
        hpY = 0f
        hpPrevIn = 0f
    }

    private fun maybeSample(input: ByteBuffer) {
        val now = SystemClock.uptimeMillis()
        if (now - lastEmitMs < EMIT_INTERVAL_MS) return
        lastEmitMs = now
        val frame = frameOf(input) ?: return
        listener?.invoke(frame)
    }

    private fun frameOf(input: ByteBuffer): EnergyFrame? {
        val format = inputAudioFormat
        val channels = format.channelCount.takeIf { it > 0 } ?: 2
        val floats = format.encoding == C.ENCODING_PCM_FLOAT
        val view = input.duplicate().order(ByteOrder.nativeOrder())
        sumLow = 0.0
        sumMid = 0.0
        sumHigh = 0.0
        sumFull = 0.0
        sumCount = 0
        try {
            if (floats) {
                val buf = view.asFloatBuffer()
                var i = 0
                while (i + channels <= buf.limit()) {
                    var mono = 0f
                    for (c in 0 until channels) mono += buf.get(i + c)
                    accumulate(mono / channels)
                    i += channels
                }
            } else {
                val buf = view.asShortBuffer()
                var i = 0
                while (i + channels <= buf.limit()) {
                    var mono = 0f
                    for (c in 0 until channels) mono += buf.get(i + c) / 32768f
                    accumulate(mono / channels)
                    i += channels
                }
            }
        } catch (_: Exception) {
            return null
        }
        if (sumCount == 0) return null
        return EnergyFrame(
            low = sqrt(sumLow / sumCount).toFloat(),
            mid = sqrt(sumMid / sumCount).toFloat(),
            high = sqrt(sumHigh / sumCount).toFloat(),
            full = sqrt(sumFull / sumCount).toFloat(),
        )
    }

    private fun accumulate(x: Float) {
        lpY += lpAlpha * (x - lpY)
        val rest = x - lpY
        hpY = hpAlpha * (hpY + rest - hpPrevIn)
        hpPrevIn = rest
        val mid = rest - hpY
        sumLow += (lpY * lpY).toDouble()
        sumMid += (mid * mid).toDouble()
        sumHigh += (hpY * hpY).toDouble()
        sumFull += (x * x).toDouble()
        sumCount++
    }

    private fun retune(sampleRateHz: Int) {
        val rate = if (sampleRateHz > 0) sampleRateHz.toDouble() else 48000.0
        val dt = 1.0 / rate
        lpAlpha = (dt / (1.0 / (2 * Math.PI * LOW_CUTOFF_HZ) + dt)).toFloat()
        val rcHigh = 1.0 / (2 * Math.PI * HIGH_CUTOFF_HZ)
        hpAlpha = (rcHigh / (rcHigh + dt)).toFloat()
    }

    private companion object {
        const val EMIT_INTERVAL_MS = 50L
        const val LOW_CUTOFF_HZ = 300.0
        const val HIGH_CUTOFF_HZ = 4000.0
    }
}
