package io.github.tunlezah.watches

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import io.github.tunlezah.watches.platform.AndroidNotificationAccess
import io.github.tunlezah.watches.ui.WatchesApp
import io.github.tunlezah.watches.ui.nav.DeepLinks
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val notifications: AndroidNotificationAccess by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val ask = registerForActivityResult(ActivityResultContracts.RequestPermission()) { notifications.onResult(it) }
        notifications.launcher = ask
        if (savedInstanceState == null) handle(intent)
        setContent { WatchesApp() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onDestroy() {
        notifications.launcher = null
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
