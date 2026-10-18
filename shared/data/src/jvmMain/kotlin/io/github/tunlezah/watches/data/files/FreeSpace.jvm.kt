package io.github.tunlezah.watches.data.files

import okio.Path

internal actual fun freeSpace(path: Path): Long? = path.toFile().usableSpace.takeIf { it > 0 }
