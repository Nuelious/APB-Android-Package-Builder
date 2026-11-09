package com.nuelious.android.apb.runtime

import java.io.File

/**
 * Starts a command inside APB's ARM64 Linux userspace using an Android/ARM64
 * PRoot executable. The rootfs and PRoot binary are separately installed,
 * versioned runtime components; neither is bundled into the base APK.
 */
class LinuxRuntimeExecutor(private val runtime:File, private val project:File) {
 fun start(command:List<String>, onEnvironment:(MutableMap<String,String>)->Unit = {}):Process {
  val rootfs=File(runtime,"linux-rootfs-arm64")
  require(File(rootfs,"usr").isDirectory&&File(rootfs,"bin").isDirectory) {
   "Linux rootfs is missing. Install linux-rootfs-arm64 in Runtime Manager."
  }
  val proot=listOf(
   File(runtime,"proot-arm64/proot"),
   File(runtime,"proot-arm64/bin/proot"),
   File(runtime,"proot")
  ).firstOrNull{it.isFile&&it.canExecute()}
   ?:error("PRoot executable is missing. Install proot-arm64 in Runtime Manager.")

  val workspace="/workspace"
  val runtimeMount="/apb-runtime"
  val args=mutableListOf(
   proot.absolutePath,"-0","-r",rootfs.absolutePath,
   "-b","/dev","-b","/proc","-b","/sys",
   "-b","/system:/android-system",
   "-b","${project.absolutePath}:$workspace",
   "-b","${runtime.absolutePath}:$runtimeMount",
   "-w",workspace,
   "/usr/bin/env",
   "HOME=/root","TMPDIR=/tmp",
   "JAVA_HOME=$runtimeMount/jdk17-arm64",
   "GRADLE_USER_HOME=/root/.gradle",
   "ANDROID_HOME=$runtimeMount/android-sdk",
   "ANDROID_SDK_ROOT=$runtimeMount/android-sdk",
   "PATH=$runtimeMount/jdk17-arm64/bin:$runtimeMount/gradle-8/bin:$runtimeMount/android-sdk/platform-tools:$runtimeMount/android-sdk/build-tools/35.0.0:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
  )
  args.addAll(command)
  val pb=ProcessBuilder(args).directory(project).redirectErrorStream(true)
  onEnvironment(pb.environment())
  return pb.start()
 }
}
