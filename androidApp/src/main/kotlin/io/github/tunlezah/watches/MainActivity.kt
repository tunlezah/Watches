package io.github.tunlezah.watches

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import io.github.tunlezah.watches.data.SettingsRepository
import io.github.tunlezah.watches.platform.AndroidDeviceAuth
import io.github.tunlezah.watches.platform.AndroidNotificationAccess
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import io.github.tunlezah.watches.ui.WatchesApp
import io.github.tunlezah.watches.ui.nav.DeepLinks
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val notifications: AndroidNotificationAccess by inject()
    private val deviceAuth: AndroidDeviceAuth by inject()
    private val settings: SettingsRepository by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val ask = registerForActivityResult(ActivityResultContracts.RequestPermission()) { notifications.onResult(it) }
        notifications.launcher = ask
        deviceAuth.activity = this
        if (savedInstanceState == null) handle(intent)
        // With the app lock on, the app switcher shows a blank card instead of the collection (Android 13+).
        if (Build.VERSION.SDK_INT >= 33) {
            lifecycleScope.launch {
                settings.settings.map { it.device.appLock }.distinctUntilChanged().collect { setRecentsScreenshotEnabled(!it) }
            }
        }
        setContent { WatchesApp() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onDestroy() {
        notifications.launcher = null
        if (deviceAuth.activity === this) deviceAuth.activity = null
        super.onDestroy()
    }

    /** A tapped service reminder opens that watch's service history. */
    private fun handle(intent: Intent?) {
        intent?.getStringExtra(EXTRA_SERVICE_WATCH)?.let { DeepLinks.openServiceHistory(it) }
    }

    companion object {
        const val EXTRA_SERVICE_WATCH = "io.github.tunlezah.watches.SERVICE_WATCH"
    }
}
