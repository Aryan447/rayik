/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package app.rayik.music.playback.haptics

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.content.getSystemService
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import app.rayik.music.constants.SyncedHapticsIntensity
import app.rayik.music.constants.SyncedHapticsIntensityKey
import app.rayik.music.constants.SyncedHapticsSpeakerOnlyKey
import app.rayik.music.constants.SyncedMusicHapticsKey
import app.rayik.music.extensions.toEnum
import app.rayik.music.models.ActiveOutputDevice
import app.rayik.music.models.PlayerOutputDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Drives synced haptics: RMS energy from [HapticTapProcessor] →
 * [HapticBeatDetector] onset → [Vibrator] pulse scaled by strength ×
 * intensity. Fires only while playing, on the phone speaker when
 * speaker-only is on. All vibrator calls are guarded + swallowed —
 * haptics must never crash playback.
 */
class SyncedHapticsController(
    context: Context,
    scope: CoroutineScope,
    private val tap: HapticTapProcessor,
    private val outputDevice: StateFlow<ActiveOutputDevice>,
    dataStore: DataStore<Preferences>,
) {
    private val appContext = context.applicationContext
    private val powerManager: PowerManager? = appContext.getSystemService()
    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager: VibratorManager? = appContext.getSystemService()
            runCatching { manager?.defaultVibrator }.getOrNull()
        } else {
            @Suppress("DEPRECATION")
            runCatching { appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator }.getOrNull()
        }

    private val detector = HapticBeatDetector()

    @Volatile
    private var playing = false

    @Volatile
    private var enabled = false

    @Volatile
    private var intensity = SyncedHapticsIntensity.MEDIUM

    @Volatile
    private var speakerOnly = true

    private var powerSaveSkips = 0

    init {
        tap.listener = ::onEnergy
        scope.launch(Dispatchers.IO) {
            dataStore.data
                .map { prefs ->
                    Triple(
                        prefs[SyncedMusicHapticsKey] ?: false,
                        prefs[SyncedHapticsIntensityKey].toEnum(SyncedHapticsIntensity.MEDIUM),
                        prefs[SyncedHapticsSpeakerOnlyKey] ?: true,
                    )
                }.distinctUntilChanged()
                .collect { (on, level, speaker) ->
                    enabled = on
                    intensity = level
                    speakerOnly = speaker
                    tap.enabled = on
                    if (!on) cancel()
                }
        }
    }

    fun setPlaying(isPlaying: Boolean) {
        playing = isPlaying
        if (!isPlaying) {
            synchronized(detector) { detector.reset() }
            cancel()
        }
    }

    fun reset() {
        synchronized(detector) { detector.reset() }
        cancel()
    }

    private fun onEnergy(rms: Float) {
        if (!enabled || !playing) return
        if (speakerOnly && !isPhoneSpeaker()) return
        // ponytail: halve the rate under battery saver instead of a settings knob
        if (powerManager?.isPowerSaveMode == true) {
            powerSaveSkips++
            if (powerSaveSkips % 2 == 1) return
        }
        val beat =
            synchronized(detector) { detector.onEnergy(rms, SystemClock.uptimeMillis()) } ?: return
        vibrate(beat.strength)
    }

    private fun isPhoneSpeaker(): Boolean =
        when (outputDevice.value.type) {
            PlayerOutputDevice.BuiltinSpeaker, PlayerOutputDevice.Unknown -> true
            else -> false
        }

    private fun vibrate(strength: Float) {
        val vib = vibrator ?: return
        runCatching {
            if (!vib.hasVibrator()) return
            val scale =
                when (intensity) {
                    SyncedHapticsIntensity.LOW -> 0.5f
                    SyncedHapticsIntensity.MEDIUM -> 0.8f
                    SyncedHapticsIntensity.HIGH -> 1f
                }
            val amplitude = (strength * scale * 255).toInt().coerceIn(1, 255)
            val durationMs =
                when (intensity) {
                    SyncedHapticsIntensity.LOW -> 25L
                    SyncedHapticsIntensity.MEDIUM -> 40L
                    SyncedHapticsIntensity.HIGH -> 55L
                }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(durationMs)
            }
        }
    }

    private fun cancel() {
        runCatching { vibrator?.cancel() }
    }
}
