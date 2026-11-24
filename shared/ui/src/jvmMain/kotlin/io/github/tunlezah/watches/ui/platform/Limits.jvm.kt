package io.github.tunlezah.watches.ui.platform

import io.github.tunlezah.watches.data.files.MacBridge

/*
 * Photos need the Mac's own image code, which the packaged app reaches through the Mac bridge
 * (docs/MACOS_PLAN.md, 8.2.6). With it, the Mac takes photos in exactly as the phones do. A build
 * without it (on Linux, or run straight from the source tree) has only the plain-Java image code,
 * which reads no HEIC or WebP, so it shows photos but doesn't take new ones in.
 * Reminders and the app lock need more of the bridge (mac-4).
 */
actual val platformLimits: PlatformLimits = PlatformLimits(
    photos = if (MacBridge.isLoaded) null else "This build can't add or crop photos: it is running without the Mac's image code. Photos from a backup show as usual.",
    reminders = "Reminders don't appear on this Mac yet. The switch still travels to your phones in a backup.",
    appLock = "The app lock comes to the Mac in a later version.",
)
