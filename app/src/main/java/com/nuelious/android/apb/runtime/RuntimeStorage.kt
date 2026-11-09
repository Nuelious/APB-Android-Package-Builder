package com.nuelious.android.apb.runtime

import android.content.Context
import android.os.Environment
import java.io.File

/** User-visible confidential/project directory. Executable Linux files never live here. */
object RuntimeStorage {
    fun directory(context: Context): File {
        val root = File(Environment.getExternalStorageDirectory(), "apps/ADB")
        val segments = listOf(".t", ".h", ".i", ".s", ".i", ".s", ".p", ".r", ".i", ".v", ".a", ".t", ".e")
        val destination = segments.fold(root) { parent, child -> File(parent, child) }
        check(destination.isDirectory || destination.mkdirs()) {
            "Cannot create APB confidential folder. Grant APB all-files access in Android settings."
        }
        return destination.canonicalFile
    }

    /** Private app storage supports executable files and Linux rootfs symlinks. */
    fun privateRuntime(context: Context): File =
        File(context.filesDir, "runtime").apply { check(isDirectory || mkdirs()) }
}
