package io.github.tunlezah.watches

import io.github.tunlezah.watches.core.TimeSource
import io.github.tunlezah.watches.core.settings.LockAfter
import io.github.tunlezah.watches.data.SettingsRepository
import io.github.tunlezah.watches.data.db.SettingDao
import io.github.tunlezah.watches.data.db.SettingEntity
import io.github.tunlezah.watches.ui.platform.DeviceAuth
import io.github.tunlezah.watches.ui.security.AppLock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** When the app locks and unlocks, without a phone or a biometric sensor. */
class AppLockTest {

    private class MemoryDao : SettingDao {
        val rows = MutableStateFlow<Map<String, String>>(emptyMap())
        override fun observeAll(): Flow<List<SettingEntity>> = rows.map { m -> m.map { SettingEntity(it.key, it.value) } }
        override suspend fun put(setting: SettingEntity) { rows.value = rows.value + (setting.key to setting.value) }
        override suspend fun remove(key: String) { rows.value = rows.value - key }
    }

    private class FakeAuth(var ok: Boolean = true, var availability: DeviceAuth.Availability = DeviceAuth.Availability.READY) : DeviceAuth {
        var prompts = 0
        var duringPrompt: (suspend () -> Unit)? = null
        override fun availability() = availability
        override suspend fun authenticate(title: String, subtitle: String): Boolean {
            prompts++
            duringPrompt?.invoke()
            return ok
        }
    }

    private var now = Instant.fromEpochMilliseconds(1_790_000_000_000)
    private fun pass(d: Duration) { now += d }
    private val settings = SettingsRepository(MemoryDao())
    private val auth = FakeAuth()
    private val lock = AppLock(settings, auth, TimeSource { now })

    private suspend fun lockOn(after: LockAfter = LockAfter.ONE_MINUTE) {
        assertTrue(lock.setEnabled(true))
        settings.update { it.copy(device = it.device.copy(lockAfter = after)) }
    }

    @Test fun offMeansNeverLocked() = runBlocking {
        lock.prime(settings.current().device)
        lock.onForeground()
        lock.onBackground()
        pass(1.minutes * 60)
        lock.onForeground()
        assertFalse(lock.locked.value)
    }

    @Test fun startsLockedAndLocksAgainAfterTheDelay() = runBlocking {
        lockOn()
        val fresh = AppLock(settings, auth, TimeSource { now })
        fresh.prime(settings.current().device)
        assertTrue("locked before the first frame", fresh.locked.value)
        fresh.onForeground()
        assertTrue(fresh.unlock())
        assertFalse(fresh.locked.value)

        fresh.onBackground()
        pass(30.seconds)
        fresh.onForeground()
        assertFalse("a quick trip to the file picker doesn't lock", fresh.locked.value)

        fresh.onBackground()
        pass(2.minutes)
        fresh.onForeground()
        assertTrue(fresh.locked.value)
    }

    @Test fun immediatelyLocksAsTheAppLeaves() = runBlocking {
        lockOn(LockAfter.IMMEDIATELY)
        lock.onForeground()
        lock.unlock()
        lock.onBackground()
        assertTrue("so the app switcher shows the lock screen", lock.locked.value)
    }

    @Test fun thePinScreenItselfDoesNotRelock() = runBlocking {
        lockOn(LockAfter.IMMEDIATELY)
        lock.prime(settings.current().device)
        lock.onForeground()
        // On Android the PIN screen is another activity, so the app stops and starts during the prompt.
        auth.duringPrompt = { lock.onBackground(); pass(5.seconds); lock.onForeground() }
        assertTrue(lock.unlock())
        assertFalse(lock.locked.value)
    }

    @Test fun turningOnNeedsAWorkingUnlock() = runBlocking {
        auth.ok = false
        assertFalse(lock.setEnabled(true))
        assertFalse(settings.current().device.appLock)
        auth.ok = true
        auth.availability = DeviceAuth.Availability.NOT_SET_UP
        assertFalse(lock.setEnabled(true))
        assertFalse(settings.current().device.appLock)
        auth.availability = DeviceAuth.Availability.READY
        assertTrue(lock.setEnabled(true))
        assertTrue(settings.current().device.appLock)
        assertTrue(lock.setEnabled(false))
        assertFalse(settings.current().device.appLock)
    }
}
