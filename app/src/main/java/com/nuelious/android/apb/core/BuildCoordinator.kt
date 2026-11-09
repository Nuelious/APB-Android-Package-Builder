package com.nuelious.android.apb.core
import android.content.Context
import com.nuelious.android.apb.data.*
import com.nuelious.android.apb.runtime.LinuxRuntimeExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class BuildResult(val success:Boolean,val message:String,val output:String?=null)
class BuildCoordinator(context:Context) {
 private val runtime=File(context.filesDir,"runtime")
 private val gradleHome=File(context.filesDir,"gradle-user-home").apply{mkdirs()}

 suspend fun build(project:ApbProject,onLog:(String)->Unit):BuildResult=withContext(Dispatchers.IO) {
  runCatching {
   val c=ApbConfig.parse(project.configFile.readText());c.validate()
   val b=c.json.getJSONObject("build")
   val environment=c.json.optJSONObject("runtime")?.optString("environment","apb-native")?:"apb-native"
   val command=when(c.engine) {
    "gradle"->listOf("gradle","--no-daemon",if(b.optBoolean("offline"))"--offline" else "--stacktrace",
      "-p",if(environment=="apb-linux")"/workspace" else project.root.absolutePath,b.optString("task","assembleDebug"))
    "apktool"->{
      val output=if(environment=="apb-linux")"/workspace/${c.output}/apb-unsigned.apk" else File(project.root,c.output+"/apb-unsigned.apk").absolutePath
      listOf("java","-jar",if(environment=="apb-linux")"/apb-runtime/apktool/apktool.jar" else findTool("apktool.jar").absolutePath,
       "b",if(environment=="apb-linux")"/workspace" else project.root.absolutePath,"-o",output)
    }
    "smali"->{
      require(File(project.root,"AndroidManifest.xml").isFile&&File(project.root,"apktool.yml").isFile) {
       "Smali-only/Class2Smali folder needs AndroidManifest.xml, resources and apktool.yml for a complete APK build."
      }
      val output=if(environment=="apb-linux")"/workspace/${c.output}/apb-unsigned.apk" else File(project.root,c.output+"/apb-unsigned.apk").absolutePath
      listOf("java","-jar",if(environment=="apb-linux")"/apb-runtime/apktool/apktool.jar" else findTool("apktool.jar").absolutePath,
       "b",if(environment=="apb-linux")"/workspace" else project.root.absolutePath,"-o",output)
    }
    "dex"->error("DEX-only input lacks manifest/resources needed for APK packaging")
    else->error("Custom engine plugins are not installed; arbitrary commands are disabled")
   }

   val process=if(environment=="apb-linux") {
    LinuxRuntimeExecutor(runtime,project.root).start(command)
   } else {
    val executable=findTool(command.first())
    val pb=ProcessBuilder(listOf(executable.absolutePath)+command.drop(1)).directory(project.root).redirectErrorStream(true)
    val javaHome=if(executable.name=="java")executable.parentFile?.parentFile else findTool("java").parentFile?.parentFile
    pb.environment()["JAVA_HOME"]=javaHome?.absolutePath.orEmpty()
    pb.environment()["PATH"]=File(javaHome,"bin").absolutePath+File.pathSeparator+(pb.environment()["PATH"]?:"")
    pb.environment()["GRADLE_USER_HOME"]=gradleHome.absolutePath
    val sdk=File(runtime,"android-sdk")
    if(sdk.isDirectory){pb.environment()["ANDROID_HOME"]=sdk.absolutePath;pb.environment()["ANDROID_SDK_ROOT"]=sdk.absolutePath}
    pb.start()
   }
   process.inputStream.bufferedReader().useLines{lines->lines.forEach(onLog)}
   val code=process.waitFor();check(code==0){"Build process exited with code $code"}
   val configuredOutput=File(project.root,c.output)
   val apk=if(c.engine=="gradle")configuredOutput.walkTopDown().firstOrNull{it.isFile&&it.extension.equals("apk",true)}
    else File(configuredOutput,"apb-unsigned.apk").takeIf{it.isFile}
      ?:configuredOutput.walkTopDown().firstOrNull{it.isFile&&it.extension.equals("apk",true)}
    ?:error("Build process succeeded but no APK was found in the configured output directory")
   val options=c.json.optJSONObject("options")
   val shouldSign=options?.optBoolean("sign_apk",true)?:true
   val shouldAlign=options?.optBoolean("zipalign",true)?:true
   val finalApk=if(c.engine=="gradle")apk else ApkPostProcessor(runtime,project.root,environment=="apb-linux",onLog).process(apk,shouldAlign,shouldSign)
   BuildResult(true,"Build completed and output processed",finalApk.absolutePath)
  }.getOrElse{BuildResult(false,it.message?:"Build failed")}
 }

 private fun findTool(name:String):File {
  val found=runtime.walkTopDown().maxDepth(8).firstOrNull{it.isFile&&it.name==name&&(it.canExecute()||name.endsWith(".jar"))}
  return found?:error("Missing APB runtime component: $name. Install a compatible toolchain archive in Runtime Manager.")
 }
}
