package io.github.tunlezah.watches.desktop

import java.io.File
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.StandardOpenOption

/**
 * One copy of the app per collection folder. The database and the restore journal assume a single
 * owner, and nothing on a desktop stops the app being started twice. The lock is held on a file in
 * the folder for as long as the process lives; the system releases it if the app is killed.
 */
internal class SingleInstance private constructor(private val channel: FileChannel, private val lock: FileLock) : AutoCloseable {

    override fun close() {
        runCatching { lock.release() }
        runCatching { channel.close() }
    }

    companion object {
        const val LOCK_FILE = ".lock"

        /** The lock, or null if another copy of the app holds it. */
        fun acquire(dir: File): SingleInstance? {
            dir.mkdirs()
            val channel = FileChannel.open(File(dir, LOCK_FILE).toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE)
            val lock = try {
                channel.tryLock()
            } catch (_: OverlappingFileLockException) {
                // Held by this same process: a second window's worth of app in one JVM.
                null
            }
            if (lock == null) {
                channel.close()
                return null
            }
            return SingleInstance(channel, lock)
        }
    }
}
