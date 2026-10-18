package io.github.tunlezah.watches.data.files

import kotlinx.cinterop.ExperimentalForeignApi
import okio.Path
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSystemFreeSize
import platform.Foundation.NSNumber

@OptIn(ExperimentalForeignApi::class)
internal actual fun freeSpace(path: Path): Long? =
    (NSFileManager.defaultManager.attributesOfFileSystemForPath(path.toString(), null)?.get(NSFileSystemFreeSize) as? NSNumber)?.longLongValue
