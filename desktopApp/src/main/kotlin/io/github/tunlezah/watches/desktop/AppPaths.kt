package io.github.tunlezah.watches.desktop

import java.io.File

/** Where the collection lives on this computer. */
internal object AppPaths {

    /**
     * On a Mac: ~/Library/Application Support/Watches. That is outside iCloud Drive, and it stays when
     * the app is dragged to the Bin. `watches.dataDir` (a system property) or WATCHES_DATA_DIR points
     * it somewhere else, for tests and for trying a build without touching the real collection.
     */
    fun dataDir(
        os: String = System.getProperty("os.name").orEmpty(),
        home: String = System.getProperty("user.home").orEmpty(),
        env: (String) -> String? = System::getenv,
        override: String? = System.getProperty("watches.dataDir") ?: System.getenv("WATCHES_DATA_DIR"),
    ): File {
        if (!override.isNullOrBlank()) return File(override)
        return when {
            os.startsWith("Mac") -> File(home, "Library/Application Support/Watches")
            os.startsWith("Windows") -> File(env("APPDATA")?.takeIf { it.isNotBlank() } ?: "$home/AppData/Roaming", "Watches")
            else -> File(env("XDG_DATA_HOME")?.takeIf { it.isNotBlank() } ?: "$home/.local/share", "Watches")
        }
    }

    /** "macOS 27.0", for the line in a backup's manifest that says what made it. */
    fun platformName(): String {
        val os = System.getProperty("os.name").orEmpty()
        val name = if (os.startsWith("Mac")) "macOS" else os.ifBlank { "desktop" }
        return listOf(name, System.getProperty("os.version").orEmpty()).filter { it.isNotBlank() }.joinToString(" ")
    }
}
