package io.github.tunlezah.watches.ui.platform

import io.github.tunlezah.watches.data.files.MacBridge
import io.github.tunlezah.watches.data.files.SystemImageLibraries

/*
 * Photos need image code that reads what phones write, HEIC above all. A packaged Mac app reaches
 * the Mac's own through the Mac bridge (docs/MACOS_PLAN.md, 8.2.6). On Linux, the server image has
 * the system's libheif and libwebp (docs/SELF_HOSTING_PLAN.md, srv-2). With either, photos come in
 * exactly as on the phones. A build with neither (run straight from the source tree) has only the
 * plain-Java image code, which reads no HEIC, so it shows photos but doesn't take new ones in.
 * Reminders and the app lock need more of the bridge (mac-4). The server has neither: its web
 * page's login stands in for the lock (docs/SELF_HOSTING.md).
 */
private val onMac = System.getProperty("os.name").orEmpty().startsWith("Mac")
private val here = if (onMac) "this Mac" else "this computer"

actual val platformLimits: PlatformLimits = PlatformLimits(
    photos = if (MacBridge.isLoaded || SystemImageLibraries.heic != null) {
        null
    } else {
        "This build can't add or crop photos: it is running without the image code that reads a phone's photos. Photos from a backup show as usual."
    },
    reminders = "Reminders don't appear on $here yet. The switch still travels to your phones in a backup.",
    appLock = if (onMac) "The app lock comes to the Mac in a later version." else "The app lock isn't available on this computer.",
)
