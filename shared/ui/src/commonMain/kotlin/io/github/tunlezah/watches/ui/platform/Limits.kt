package io.github.tunlezah.watches.ui.platform

/**
 * What this platform's build leaves out for now, each with the sentence a screen shows in its
 * place. Null means the feature is there, which is every feature on the phones. The Mac build
 * fills these in until its own milestones lift them (docs/MACOS_PLAN.md, 8.1.7).
 */
data class PlatformLimits(
    /** Adding, replacing and cropping photos, saving a Lookup picture, and photo documents. */
    val photos: String? = null,
    /** Service reminders as system notifications. */
    val reminders: String? = null,
    /** The app lock. */
    val appLock: String? = null,
)

expect val platformLimits: PlatformLimits
