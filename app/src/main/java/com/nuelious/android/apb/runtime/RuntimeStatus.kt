package com.nuelious.android.apb.runtime
import android.content.Context
import java.io.File

data class RuntimeStatus(val abi:String,val java:Boolean,val gradle:Boolean,val apktool:Boolean,val freeBytes:Long)
class RuntimeStatusProvider(context:Context){
 private val root=File(context.filesDir,"runtime")
 fun inspect()=RuntimeStatus(android.os.Build.SUPPORTED_ABIS.firstOrNull()?:"unknown",
  File(root,"bin/java").exists()||File(root,"jdk/bin/java").exists(),
  File(root,"bin/gradle").exists()||File(root,"gradle/bin/gradle").exists(),
  File(root,"apktool.jar").exists(),root.usableSpace)
}
