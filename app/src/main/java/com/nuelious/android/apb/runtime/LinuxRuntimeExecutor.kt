package com.nuelious.android.apb.runtime

import android.content.Context
import java.io.File

class LinuxRuntimeExecutor(context:Context,private val project:File) {
 private val nativeDir=File(context.applicationInfo.nativeLibraryDir)
 private val runtime=File(RuntimeStorage.privateRuntime(context),"active")
 fun start(command:List<String>):Process {
  require(File(runtime,"rootfs/usr/bin/env").isFile){"Install the ARM64 build environment first."}
  val proot=File(nativeDir,"libproot.so")
  val loader=File(nativeDir,"libproot-loader.so")
  require(proot.isFile&&proot.canExecute()){"PRoot executable is missing from this APB build."}
  require(loader.isFile&&loader.canExecute()){"PRoot loader is missing from this APB build."}
  val args=mutableListOf(
   proot.absolutePath,"-0","-r",File(runtime,"rootfs").absolutePath,
   "-b","/dev","-b","/proc","-b","/sys",
   "-b","${project.absolutePath}:/workspace",
   "-b","${runtime.absolutePath}:/apb-runtime",
   "-w","/workspace","/usr/bin/env",
   "HOME=/root","TMPDIR=/tmp","JAVA_HOME=/apb-runtime/jdk",
   "GRADLE_USER_HOME=/apb-runtime/gradle-home",
   "ANDROID_HOME=/opt/android-sdk","ANDROID_SDK_ROOT=/opt/android-sdk",
   "PATH=/apb-runtime/jdk/bin:/apb-runtime/gradle/bin:/usr/bin:/bin:/usr/sbin:/sbin"
  )
  args.addAll(command)
  val pb=ProcessBuilder(args).directory(project).redirectErrorStream(true)
  pb.environment()["PROOT_LOADER"]=loader.absolutePath
  pb.environment()["PROOT_NO_SECCOMP"]="1"
  return pb.start()
 }
}
