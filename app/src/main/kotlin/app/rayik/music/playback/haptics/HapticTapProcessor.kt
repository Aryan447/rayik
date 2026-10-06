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

/**
 * Zero-permission PCM tap for synced haptics. Sits in the ExoPlayer
 * [AudioProcessor] chain as pure passthrough — audio bytes are copied
 * untouched — and emits an RMS energy sample (~20/s) while [enabled].
 *
 * Disabled = one memcpy, no computation, no callbacks.
 */
@UnstableApi
class HapticTapProcessor : BaseAudioProcessor() {
    @Volatile
    var enabled: Boolean = false

    var listener: ((Float) -> Unit)? = null

    private var lastEmitMs = 0L

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat =
        inputAudioFormat

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        if (enabled) {
            maybeSample(inputBuffer)
        }
        val out = replaceOutputBuffer(inputBuffer.remaining())
        out.put(inputBuffer)
        out.flip()
    }

    private fun maybeSample(input: ByteBuffer) {
        val now = SystemClock.uptimeMillis()
        if (now - lastEmitMs < EMIT_INTERVAL_MS) return
        lastEmitMs = now
        val rms = rmsOf(input) ?: return
        listener?.invoke(rms)
    }

    private fun rmsOf(input: ByteBuffer): Float? {
        val floats = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        val view = input.duplicate().order(ByteOrder.nativeOrder())
        var sum = 0.0
        var count = 0
        try {
            if (floats) {
                val floatsView = view.asFloatBuffer()
                var i = 0
                while (i < floatsView.limit()) {
                    val v = floatsView.get(i)
                    sum += (v * v).toDouble()
                    count++
                    i += SAMPLE_STRIDE
                }
            } else {
                val shorts = view.asShortBuffer()
                var i = 0
                while (i < shorts.limit()) {
                    val v = shorts.get(i) / 32768f
                    sum += (v * v).toDouble()
                    count++
                    i += SAMPLE_STRIDE
                }
            }
        } catch (_: Exception) {
            return null
        }
        if (count == 0) return null
        return sqrt(sum / count).toFloat()
    }

    private companion object {
        const val EMIT_INTERVAL_MS = 50L
        const val SAMPLE_STRIDE = 8
    }
}
