/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package app.rayik.music.playback.haptics

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import app.rayik.music.constants.SyncedHapticsIntensity

/**
 * Pure mapping from [HapticEvent] × intensity to a renderable [HapticCommand]:
 * which Taptic-style primitive (or one-shot fallback) to play and how hard.
 * No Android calls — fully unit-testable. [HapticRenderer] executes it.
 */
object HapticSelector {
    fun select(event: HapticEvent, intensity: SyncedHapticsIntensity): HapticCommand? =
        when (event) {
            is HapticEvent.Transient ->
                when (event.voice) {
                    HapticVoice.KICK ->
                        HapticCommand(
                            primitive = CompositionPrimitives.THUMP,
                            scale = (event.strength * intensityScale(intensity)).coerceIn(0.1f, 1f),
                            fallbackDurationMs = fallbackDuration(intensity),
                            fallbackAmplitude = fallbackAmplitude(event.strength, intensity),
                        )
                    HapticVoice.SNARE ->
                        HapticCommand(
                            primitive = CompositionPrimitives.TICK,
                            scale = (event.strength * 0.8f * intensityScale(intensity)).coerceIn(0.1f, 1f),
                            fallbackDurationMs = (fallbackDuration(intensity) * 3) / 4,
                            fallbackAmplitude = fallbackAmplitude(event.strength * 0.8f, intensity),
                        )
                    HapticVoice.VOCAL ->
                        HapticCommand(
                            primitive = CompositionPrimitives.TICK,
                            scale = (event.strength * 0.5f * intensityScale(intensity)).coerceIn(0.1f, 1f),
                            fallbackDurationMs = (fallbackDuration(intensity) * 3) / 4,
                            fallbackAmplitude = fallbackAmplitude(event.strength * 0.5f, intensity),
                        )
                }
            is HapticEvent.Texture ->
                HapticCommand(
                    primitive = CompositionPrimitives.SWELL,
                    scale = (event.level * intensityScale(intensity)).coerceIn(0.1f, 1f),
                    // No one-shot equivalent for a sustained bed: old devices
                    // get transients only.
                    fallbackDurationMs = 0L,
                    fallbackAmplitude = 0,
                )
        }

    fun intensityScale(intensity: SyncedHapticsIntensity): Float =
        when (intensity) {
            SyncedHapticsIntensity.LOW -> 0.5f
            SyncedHapticsIntensity.MEDIUM -> 0.8f
            SyncedHapticsIntensity.HIGH -> 1f
        }

    private fun fallbackDuration(intensity: SyncedHapticsIntensity): Long =
        when (intensity) {
            SyncedHapticsIntensity.LOW -> 25L
            SyncedHapticsIntensity.MEDIUM -> 40L
            SyncedHapticsIntensity.HIGH -> 55L
        }

    private fun fallbackAmplitude(strength: Float, intensity: SyncedHapticsIntensity): Int =
        (strength * intensityScale(intensity) * 255).toInt().coerceIn(1, 255)
}

/**
 * Renderable vibration command. [primitive] selects the Composition
 * primitive on API 31+ devices; the fallback fields drive
 * `createOneShot` below that (or when primitives are unsupported).
 */
data class HapticCommand(
    val primitive: Int,
    val scale: Float,
    val fallbackDurationMs: Long,
    val fallbackAmplitude: Int,
)

/**
 * Composition primitive IDs as plain ints, so unit tests and old runtimes
 * never load the API-31 Composition class. Values mirror
 * VibrationEffect.Composition: CLICK=1, SLOW_RISE=6, THUD=7.
 */
object CompositionPrimitives {
    const val TICK = 1
    const val SWELL = 6
    const val THUMP = 7
}

/**
 * Executes [HapticCommand]s on a [Vibrator]: Taptic-style Composition
 * primitives where supported, plain one-shots elsewhere. Every call is
 * guarded — haptics must never crash playback.
 */
object HapticRenderer {
    fun supportsComposition(vibrator: Vibrator): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        return runCatching {
            vibrator.areAllPrimitivesSupported(
                VibrationEffect.Composition.PRIMITIVE_THUD,
                VibrationEffect.Composition.PRIMITIVE_CLICK,
                VibrationEffect.Composition.PRIMITIVE_SLOW_RISE,
            )
        }.getOrDefault(false)
    }

    fun render(vibrator: Vibrator, command: HapticCommand, useComposition: Boolean) {
        runCatching {
            if (useComposition) {
                val effect =
                    VibrationEffect.startComposition()
                        .addPrimitive(resolvePrimitive(command.primitive), command.scale)
                        .compose()
                vibrator.vibrate(effect)
            } else if (command.fallbackDurationMs > 0) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(
                        VibrationEffect.createOneShot(command.fallbackDurationMs, command.fallbackAmplitude),
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(command.fallbackDurationMs)
                }
            }
        }
    }

    private fun resolvePrimitive(primitive: Int): Int =
        when (primitive) {
            CompositionPrimitives.THUMP -> VibrationEffect.Composition.PRIMITIVE_THUD
            CompositionPrimitives.SWELL -> VibrationEffect.Composition.PRIMITIVE_SLOW_RISE
            else -> VibrationEffect.Composition.PRIMITIVE_CLICK
        }
}
