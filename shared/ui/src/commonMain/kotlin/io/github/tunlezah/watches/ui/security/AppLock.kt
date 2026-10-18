package io.github.tunlezah.watches.ui.security

import io.github.tunlezah.watches.core.TimeSource
import io.github.tunlezah.watches.core.settings.DeviceSettings
import io.github.tunlezah.watches.data.SettingsRepository
import io.github.tunlezah.watches.ui.platform.DeviceAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Instant

/**
 * The optional app lock. The app starts locked when the lock is on, and locks again once it has
 * been in the background for longer than the chosen delay. Time spent in the system unlock prompt
 * itself never counts (the PIN screen sends the app to the background on Android).
 */
class AppLock(
    private val settings: SettingsRepository,
    private val auth: DeviceAuth,
    private val time: TimeSource,
) {
    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()
    private var leftAt: Instant? = null
    private var started = false
    private var authenticating = false

    /** Locks straight away at launch if the lock is on, before anything is drawn. */
    fun prime(device: DeviceSettings) {
        if (!started && device.appLock) _locked.value = true
    }

    /** The app came to the foreground (or launched). */
    suspend fun onForeground() {
        if (authenticating) return
        val device = settings.current().device
        if (!device.appLock) {
            _locked.value = false
        } else if (!started) {
            _locked.value = true
        } else {
            val away = leftAt?.let { time.now() - it }
            if (away != null && away.inWholeSeconds >= device.lockAfter.seconds) _locked.value = true
        }
        started = true
        leftAt = null
    }

    /** The app went to the background. With "Immediately" it locks now, so the app switcher shows the lock screen. */
    suspend fun onBackground() {
        if (authenticating) return
        leftAt = time.now()
        val device = settings.current().device
        if (device.appLock && device.lockAfter.seconds == 0) _locked.value = true
    }

    suspend fun unlock(): Boolean {
        if (authenticating) return false
        authenticating = true
        val ok = try {
            auth.authenticate("Unlock Watches", "Confirm it's you to open your collection")
        } finally {
            authenticating = false
        }
        if (ok) {
            _locked.value = false
            leftAt = null
        }
        return ok
    }

    /** Turning the lock on asks for the unlock once, so nobody locks themselves out with a sensor that doesn't work. */
    suspend fun setEnabled(on: Boolean): Boolean {
        if (on) {
            if (auth.availability() != DeviceAuth.Availability.READY) return false
            authenticating = true
            val ok = try {
                auth.authenticate("Turn on app lock", "Confirm it's you")
            } finally {
                authenticating = false
            }
            if (!ok) return false
        }
        settings.update { it.copy(device = it.device.copy(appLock = on)) }
        return true
    }

    fun availability(): DeviceAuth.Availability = auth.availability()

    suspend fun device(): DeviceSettings = settings.current().device
}
