package io.github.tunlezah.watches.ui.platform

/*
 * The desktop's image code is still the test-grade one: it ignores a photo's rotation and leaves
 * its location in. Until the shared photo component replaces it (milestone mac-2), the Mac shows
 * photos but doesn't take new ones in, so nothing it writes to a backup is wrong on a phone.
 * Reminders and the app lock need the Mac bridge (mac-4).
 */
actual val platformLimits: PlatformLimits = PlatformLimits(
    photos = "Adding and cropping photos comes to the Mac in a later version. Photos from a backup show as usual.",
    reminders = "Reminders don't appear on this Mac yet. The switch still travels to your phones in a backup.",
    appLock = "The app lock comes to the Mac in a later version.",
)
