package com.devuloopers.knet.engine.session

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions

/** Owner-only filesystem policy shared by session payload implementations. */
internal object SessionFileSecurity {
    /** Creates and restricts one payload directory. */
    fun secureDirectory(directory: File): Boolean {
        if (!directory.exists() && !directory.mkdirs()) return false
        applyPortableOwnerFlags(directory, executable = true)
        runCatching {
            Files.setPosixFilePermissions(directory.toPath(), PosixFilePermissions.fromString("rwx------"))
        }
        return directory.isDirectory
    }

    /** Restricts one existing payload object to owner read/write access. */
    fun secureFile(file: File): Boolean {
        if (!file.exists()) return false
        applyPortableOwnerFlags(file, executable = false)
        runCatching {
            Files.setPosixFilePermissions(file.toPath(), PosixFilePermissions.fromString("rw-------"))
        }
        return file.isFile
    }

    /** Clears broad Java flags before granting owner access only. */
    private fun applyPortableOwnerFlags(file: File, executable: Boolean) {
        file.setReadable(false, false)
        file.setWritable(false, false)
        file.setExecutable(false, false)
        file.setReadable(true, true)
        file.setWritable(true, true)
        if (executable) file.setExecutable(true, true)
    }
}
