package com.nuelious.android.apb.core
import android.content.Context
import com.nuelious.android.apb.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class BuildResult(val success:Boolean,val message:String,val output:String?=null)
class BuildCoordinator(context:Context) {
 private val runtime=File(context.filesDir,"runtime")
 suspend fun build(project:ApbProject,onLog:(String)->Unit):BuildResult=withContext(Dispatchers.IO) {
  runCatching {
   val c=ApbConfig.parse(project.configFile.readText());c.validate()
   val b=c.json.getJSONObject("build")
   val command=when(c.engine) {
    "gradle"->listOf("gradle","--no-daemon",if(b.optBoolean("offline"))"--offline" else "--stacktrace","-p",project.root.absolutePath,b.optString("task","assembleDebug"))
    "apktool"->listOf("java","-jar",findTool("apktool.jar").absolutePath,"b",project.root.absolutePath,"-o",File(project.root,c.output+"/apb-unsigned.apk").absolutePath)
    "smali"->error("Smali assembly alone is not a complete APK build; manifest/resources packaging engine is required")
    "dex"->error("DEX-only input lacks manifest/resources needed for APK packaging")
    else->error("Custom engine plugins are not installed; arbitrary commands are disabled")
   }
   val executable=findTool(command.first())
   val p=ProcessBuilder(listOf(executable.absolutePath)+command.drop(1)).directory(project.root).redirectErrorStream(true).start()
   p.inputStream.bufferedReader().useLines{lines->lines.forEach(onLog)}
   val code=p.waitFor();check(code==0){"Build process exited with code $code"}
   val apk=project.root.walkTopDown().firstOrNull{it.isFile&&it.extension.equals("apk",true)}
   BuildResult(true,"Build command completed",apk?.absolutePath)
  }.getOrElse{BuildResult(false,it.message?:"Build failed")}
 }
 private fun findTool(name:String):File {
  val candidates=runtime.walkTopDown().maxDepth(7).filter{it.isFile&&it.name==name}.toList()
  return candidates.firstOrNull{it.canExecute()||name.endsWith(".jar")}?:error("Missing APB runtime component: $name. Install a compatible toolchain archive in Runtime Manager.")
 }
}
