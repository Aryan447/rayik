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
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.content.getSystemService
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import app.rayik.music.constants.SyncedHapticsIntensity
import app.rayik.music.constants.SyncedHapticsIntensityKey
import app.rayik.music.constants.SyncedHapticsMode
import app.rayik.music.constants.SyncedHapticsModeKey
import app.rayik.music.constants.SyncedHapticsPausedKey
import app.rayik.music.constants.SyncedHapticsSpeakerOnlyKey
import app.rayik.music.constants.SyncedMusicHapticsKey
import app.rayik.music.extensions.toEnum
import app.rayik.music.models.ActiveOutputDevice
import app.rayik.music.models.PlayerOutputDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Drives synced haptics: band-split energy from [HapticTapProcessor] →
 * [HapticBeatDetector] events → [HapticSelector]/[HapticRenderer] on the
 * [Vibrator]. Sharp kick/snare taps plus a sustained texture bed while the
 * song runs hot — Apple's taps/textures/refined-vibrations model, computed
 * live on-device.
 *
 * Fires only while playing and not session-paused. Renders are delayed by
 * the estimated audio output latency so taps land on audible beats. All
 * vibrator calls are guarded + swallowed — haptics must never crash
 * playback.
 */
class SyncedHapticsController(
    context: Context,
    private val scope: CoroutineScope,
    private val tap: HapticTapProcessor,
    private val outputDevice: StateFlow<ActiveOutputDevice>,
    private val dataStore: DataStore<Preferences>,
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
    private val useComposition = vibrator?.let(HapticRenderer::supportsComposition) ?: false

    private val detector = HapticBeatDetector()

    @Volatile
    private var playing = false

    @Volatile
    private var enabled = false

    @Volatile
    private var sessionPaused = false

    @Volatile
    private var intensity = SyncedHapticsIntensity.MEDIUM

    @Volatile
    private var vocalsOnly = false

    @Volatile
    private var speakerOnly = false

    @Volatile
    private var latencyOffsetMs = 60L

    private var powerSaveSkips = 0
    private var lastTextureRenderMs = 0L
    private var lastTextureLevel = 0f

    init {
        tap.listener = ::onFrame
        scope.launch(Dispatchers.IO) {
            dataStore.data
                .map { prefs ->
                    HapticsPrefs(
                        on = prefs[SyncedMusicHapticsKey] ?: false,
                        level = prefs[SyncedHapticsIntensityKey].toEnum(SyncedHapticsIntensity.MEDIUM),
                        vocals = prefs[SyncedHapticsModeKey].toEnum(SyncedHapticsMode.FULL_MIX) ==
                            SyncedHapticsMode.VOCALS_ONLY,
                        speaker = prefs[SyncedHapticsSpeakerOnlyKey] ?: false,
                        paused = prefs[SyncedHapticsPausedKey] ?: false,
                    )
                }.distinctUntilChanged()
                .collect { prefs ->
                    enabled = prefs.on
                    intensity = prefs.level
                    vocalsOnly = prefs.vocals
                    speakerOnly = prefs.speaker
                    sessionPaused = prefs.paused
                    tap.enabled = prefs.on
                    if (!prefs.on || prefs.paused) cancel()
                }
        }
        scope.launch(Dispatchers.IO) {
            outputDevice.collect { refreshLatencyEstimate() }
        }
    }

    fun setPlaying(isPlaying: Boolean) {
        playing = isPlaying
        if (!isPlaying) {
            synchronized(detector) { detector.reset() }
            cancel()
        }
    }

    /** Seek: drop stale detector state, keep the badge-pause as-is. */
    fun resetForSeek() {
        synchronized(detector) { detector.reset() }
        cancel()
    }

    /** Track change: fresh song resumes haptics unless the master is off. */
    fun resetForTrack() {
        sessionPaused = false
        synchronized(detector) { detector.reset() }
        cancel()
        scope.launch(Dispatchers.IO) {
            runCatching {
                dataStore.edit { it[SyncedHapticsPausedKey] = false }
            }
        }
    }

    private fun onFrame(frame: EnergyFrame) {
        if (!enabled || !playing || sessionPaused) return
        if (speakerOnly && !isPhoneSpeaker()) return
        // ponytail: halve the rate under battery saver instead of a settings knob
        if (powerManager?.isPowerSaveMode == true) {
            powerSaveSkips++
            if (powerSaveSkips % 2 == 1) return
        }
        val nowMs = SystemClock.uptimeMillis()
        val events =
            synchronized(detector) {
                detector.vocalsOnly = vocalsOnly
                detector.onFrame(frame, nowMs)
            }
        for (event in events) {
            when (event) {
                is HapticEvent.Transient -> renderTransient(event)
                is HapticEvent.Texture -> renderTexture(event, nowMs)
            }
        }
    }

    private fun renderTransient(event: HapticEvent.Transient) {
        val command = HapticSelector.select(event, intensity) ?: return
        // ponytail: fixed latency offset, per-route calibration if taps feel late on BT
        scope.launch(Dispatchers.Default) {
            delay(latencyOffsetMs)
            if (!playing || sessionPaused) return@launch
            vibrator?.let { HapticRenderer.render(it, command, useComposition) }
        }
    }

    private fun renderTexture(event: HapticEvent.Texture, nowMs: Long) {
        val due = nowMs - lastTextureRenderMs >= TEXTURE_REFRESH_MS
        val moved = kotlin.math.abs(event.level - lastTextureLevel) >= TEXTURE_DELTA
        if (!due && !moved) return
        lastTextureRenderMs = nowMs
        lastTextureLevel = event.level
        val command = HapticSelector.select(event, intensity) ?: return
        vibrator?.let { HapticRenderer.render(it, command, useComposition) }
    }

    private fun refreshLatencyEstimate() {
        // ponytail: fixed per-route table, measure per-device offsets if taps feel late on BT
        val outputLatency =
            when (outputDevice.value.type) {
                PlayerOutputDevice.Bluetooth -> 200L
                PlayerOutputDevice.Usb,
                PlayerOutputDevice.Headset,
                PlayerOutputDevice.Hdmi,
                -> 60L
                else -> 80L
            }
        latencyOffsetMs = (outputLatency - VIBRATOR_LATENCY_MS).coerceAtLeast(0L)
    }

    private fun isPhoneSpeaker(): Boolean =
        when (outputDevice.value.type) {
            PlayerOutputDevice.BuiltinSpeaker, PlayerOutputDevice.Unknown -> true
            else -> false
        }

    private fun cancel() {
        runCatching { vibrator?.cancel() }
    }

    private data class HapticsPrefs(
        val on: Boolean,
        val level: SyncedHapticsIntensity,
        val vocals: Boolean,
        val speaker: Boolean,
        val paused: Boolean,
    )

    private companion object {
        const val VIBRATOR_LATENCY_MS = 20L
        const val TEXTURE_REFRESH_MS = 600L
        const val TEXTURE_DELTA = 0.15f
    }
}

/** UI-side capability check (no service handle needed): hide haptics controls when false. */
fun Context.hasHapticMotor(): Boolean =
    runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator?.hasVibrator() == true
        } else {
            @Suppress("DEPRECATION")
            (getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)?.hasVibrator() == true
        }
    }.getOrDefault(false)
