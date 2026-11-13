package com.nuelious.android.apb.runtime

import android.content.Context
import java.io.File

class LinuxRuntimeExecutor(context: Context, private val project: File) {
 private val app = context.applicationContext
 private val nativeDir = File(app.applicationInfo.nativeLibraryDir)
 private val runtime = File(RuntimeStorage.privateRuntime(app), "active")

 fun start(command: List<String>): Process {
  val rootfs = File(runtime, "rootfs")
  require(File(rootfs, "usr/bin/env").isFile) { "Install the ARM64 build environment first." }
  val proot = File(nativeDir, "libproot.so")
  val loader = File(nativeDir, "libproot-loader.so")
  require(proot.isFile && proot.canExecute()) { "PRoot executable is missing from this APB build." }
  require(loader.isFile && loader.canExecute()) { "PRoot loader is missing from this APB build." }

  // Android does not provide a conventional host /tmp. PRoot uses PROOT_TMP_DIR
  // for its own glue-rootfs and temporary probe files, independently of Linux TMPDIR.
  val prootTmp = File(app.cacheDir, "proot-tmp")
  check(prootTmp.isDirectory || prootTmp.mkdirs()) { "Cannot create APB's private PRoot temporary directory." }
  check(prootTmp.setReadable(true, true) && prootTmp.setWritable(true, true) && prootTmp.setExecutable(true, true)) {
   "Cannot set permissions on APB's private PRoot temporary directory."
  }

  // Supply a real writable /tmp inside the guest rootfs. Binding the private
  // cache directory avoids relying on a possibly absent or read-only rootfs/tmp.
  val guestTmp = File(rootfs, "tmp")
  check(guestTmp.isDirectory || guestTmp.mkdirs()) { "Cannot create /tmp inside the runtime rootfs." }

  val args = mutableListOf(
   proot.absolutePath, "-0", "-r", rootfs.absolutePath,
   "-b", "/dev", "-b", "/proc", "-b", "/sys",
   "-b", "${prootTmp.absolutePath}:/tmp",
   "-b", "${project.absolutePath}:/workspace",
   "-b", "${runtime.absolutePath}:/apb-runtime",
   "-w", "/workspace", "/usr/bin/env",
   "HOME=/root", "TMPDIR=/tmp", "TMP=/tmp", "TEMP=/tmp",
   "JAVA_HOME=/apb-runtime/jdk",
   "GRADLE_USER_HOME=/apb-runtime/gradle-home",
   "ANDROID_HOME=/opt/android-sdk", "ANDROID_SDK_ROOT=/opt/android-sdk",
   "PATH=/apb-runtime/jdk/bin:/apb-runtime/gradle/bin:/usr/bin:/bin:/usr/sbin:/sbin"
  )
  args.addAll(command)
  val pb = ProcessBuilder(args).directory(project).redirectErrorStream(true)
  pb.environment()["PROOT_LOADER"] = loader.absolutePath
  pb.environment()["PROOT_NO_SECCOMP"] = "1"
  pb.environment()["PROOT_TMP_DIR"] = prootTmp.absolutePath
  return pb.start()
 }
}
