package com.nuelious.android.apb.runtime
import android.content.Context
import android.os.Environment
import java.io.File

/** Separates confidential project storage from executable app-private runtime files. */
object RuntimeStorage {
 fun directory(context: Context): File {
  val root=File(Environment.getExternalStorageDirectory(),"apps/ADB")
  val segments=listOf(".t",".h",".i",".s",".i",".s",".p",".r",".i",".v",".a",".t",".e")
  val destination=segments.fold(root){parent,child->File(parent,child)}
  check(destination.isDirectory||destination.mkdirs()) {
   "Cannot create APB runtime directory. Grant APB all-files access in Android settings."
  }
  return destination.canonicalFile
 }

 /** Linux rootfs, native tools, JDK, Gradle and caches stay in app-private storage. */
 fun privateRuntime(context: Context): File =
  File(context.filesDir, "runtime").apply {
   check(isDirectory || mkdirs()) { "Cannot create APB private runtime directory." }
  }
}
