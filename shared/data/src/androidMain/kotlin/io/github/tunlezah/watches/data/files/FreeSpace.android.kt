package io.github.tunlezah.watches.data.files

import android.os.StatFs
import okio.Path

internal actual fun freeSpace(path: Path): Long? = StatFs(path.toString()).availableBytes
