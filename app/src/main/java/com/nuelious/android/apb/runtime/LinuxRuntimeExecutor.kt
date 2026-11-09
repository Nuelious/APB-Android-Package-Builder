package com.nuelious.android.apb.runtime

import android.content.Context
import java.io.File

/** Runs Linux ARM64 tools under the Android-native PRoot bundled in APB's APK. */
class LinuxRuntimeExecutor(private val context: Context, private val project: File) {
    fun start(command: List<String>): Process {
        require(android.os.Build.SUPPORTED_ABIS.any { it == "arm64-v8a" }) { "APB Linux runtime requires ARM64." }
        val runtime = File(RuntimeStorage.privateRuntime(context), "active")
        val rootfs = File(runtime, "rootfs")
        require(File(rootfs, "usr/bin/env").exists()) { "ARM64 rootfs is not installed. Complete runtime setup first." }
        val proot = File(context.applicationInfo.nativeLibraryDir, "libproot.so")
        require(proot.isFile && proot.canExecute()) { "APB PRoot binary is missing from this APK build." }
        val args = mutableListOf(
            proot.absolutePath, "-0", "-r", rootfs.absolutePath,
            "-b", "/dev", "-b", "/proc", "-b", "/sys",
            "-b", "${project.absolutePath}:/workspace",
            "-b", "${runtime.absolutePath}:/apb-runtime",
            "-w", "/workspace", "/usr/bin/env",
            "HOME=/root", "TMPDIR=/tmp",
            "JAVA_HOME=/apb-runtime/jdk",
            "GRADLE_USER_HOME=/apb-runtime/gradle-home",
            "ANDROID_HOME=/opt/android-sdk",
            "ANDROID_SDK_ROOT=/opt/android-sdk",
            "PATH=/apb-runtime/jdk/bin:/apb-runtime/gradle/bin:/usr/bin:/bin:/usr/sbin:/sbin"
        )
        args.addAll(command)
        return ProcessBuilder(args).directory(project).redirectErrorStream(true).start()
    }
}
