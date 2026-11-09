package com.nuelious.android.apb.core

import android.content.Context
import com.nuelious.android.apb.data.ApbConfig
import com.nuelious.android.apb.data.ApbProject
import com.nuelious.android.apb.runtime.LinuxRuntimeExecutor
import com.nuelious.android.apb.runtime.RuntimeBootstrap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class BuildResult(val success:Boolean,val message:String,val output:String?=null)

class BuildCoordinator(context:Context) {
 private val app=context.applicationContext
 suspend fun build(project:ApbProject,onLog:(String)->Unit):BuildResult=withContext(Dispatchers.IO) {
  runCatching {
   check(RuntimeBootstrap(app).isInstalled()){"Install APB's ARM64 build environment before building."}
   val c=ApbConfig.parse(project.configFile.readText()).also{it.validate()}
   val b=c.json.getJSONObject("build")
   val out=File(project.root,c.output)
   check(out.canonicalPath.startsWith(project.root.canonicalPath+File.separator)){"Unsafe build output path."}
   out.mkdirs()
   val command=when(c.engine) {
    "gradle"->listOf("gradle","--no-daemon",if(b.optBoolean("offline"))"--offline" else "--stacktrace",
     "-Pandroid.aapt2FromMavenOverride=/usr/bin/aapt2","-p","/workspace",b.optString("task","assembleDebug"))
    "apktool","smali"->{
     require(File(project.root,"AndroidManifest.xml").isFile&&File(project.root,"apktool.yml").isFile){"A complete APKTool project requires AndroidManifest.xml, resources and apktool.yml."}
     listOf("java","-jar","/apb-runtime/apktool.jar","b","/workspace","-o","/workspace/${c.output}/apb-unsigned.apk")
    }
    "dex"->error("A DEX-only folder cannot produce a complete APK without a manifest and resources.")
    else->error("Custom build engines are not available.")
   }
   val process=LinuxRuntimeExecutor(app,project.root).start(command)
   process.inputStream.bufferedReader().useLines{lines->lines.forEach(onLog)}
   val code=process.waitFor()
   check(code==0){"Build process exited with code $code."}
   val apk=if(c.engine=="gradle")out.walkTopDown().firstOrNull{it.isFile&&it.extension.equals("apk",true)}
    else File(out,"apb-unsigned.apk").takeIf{it.isFile}
   checkNotNull(apk){"Build finished but no APK was found in ${out.absolutePath}."}
   BuildResult(true,"Build completed. APK: ${apk.absolutePath}",apk.absolutePath)
  }.getOrElse{BuildResult(false,it.message?:"Build failed.")}
 }
}
