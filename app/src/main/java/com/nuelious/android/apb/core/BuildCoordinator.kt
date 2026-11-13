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
   onLog("APB preflight")
   onLog("Project root: ${project.root.canonicalPath}")
   onLog("Config: ${project.configFile.canonicalPath}")
   onLog("Engine: ${c.engine}")
   onLog("Runtime: installed; ARM64 PRoot")
   val settingsKts=File(project.root,"settings.gradle.kts")
   val settingsGroovy=File(project.root,"settings.gradle")
   val buildKts=File(project.root,"build.gradle.kts")
   val buildGroovy=File(project.root,"build.gradle")
   onLog("Project settings.gradle.kts: ${settingsKts.isFile}")
   onLog("Project settings.gradle: ${settingsGroovy.isFile}")
   onLog("Root build.gradle.kts: ${buildKts.isFile}")
   onLog("Root build.gradle: ${buildGroovy.isFile}")
   if(c.engine=="gradle") {
    require(settingsKts.isFile || settingsGroovy.isFile) {
     "This folder has no settings.gradle(.kts). Import the Android project's actual root folder, not its app module or source subfolder."
    }
    require(buildKts.isFile || buildGroovy.isFile) {
     "This folder has no root build.gradle(.kts). Import the Android project's actual root folder."
    }
   }
   val command=when(c.engine) {
    "gradle"->{
     // The imported project's settings.gradle.kts controls its plugin repositories.
     // Inject a Gradle init script so APB can resolve Android plugin IDs directly
     // to their implementation modules even when the marker artifact is unavailable.
     val init=File(project.root,".apb-gradle-init.gradle")
     init.writeText("""
       beforeSettings { settings ->
         settings.pluginManagement {
           repositories {
             google()
             mavenCentral()
             gradlePluginPortal()
           }
           resolutionStrategy {
             eachPlugin {
               if (requested.id.id == "com.android.application" ||
                   requested.id.id == "com.android.library" ||
                   requested.id.id == "com.android.test" ||
                   requested.id.id == "com.android.dynamic-feature") {
                 useModule("com.android.tools.build:gradle:${requested.version}")
               } else if (requested.id.id == "org.jetbrains.kotlin.android" ||
                          requested.id.id == "org.jetbrains.kotlin.jvm" ||
                          requested.id.id == "org.jetbrains.kotlin.kapt") {
                 useModule("org.jetbrains.kotlin:kotlin-gradle-plugin:${requested.version}")
               }
             }
           }
         }
       }
     """.trimIndent())
     listOf("gradle","--no-daemon","--console=plain","--info","--stacktrace",
      "--init-script","/workspace/.apb-gradle-init.gradle",
      "-Pandroid.aapt2FromMavenOverride=/usr/bin/aapt2","-p","/workspace",b.optString("task","assembleDebug"))
    }
    "apktool","smali"->{
     require(File(project.root,"AndroidManifest.xml").isFile&&File(project.root,"apktool.yml").isFile){"A complete APKTool project requires AndroidManifest.xml, resources and apktool.yml."}
     listOf("java","-jar","/apb-runtime/apktool.jar","b","/workspace","-o","/workspace/${c.output}/apb-unsigned.apk")
    }
    "dex"->error("A DEX-only folder cannot produce a complete APK without a manifest and resources.")
    else->error("Custom build engines are not available.")
   }
   onLog("Command: ${command.joinToString(" ")}")
   onLog("Starting build process…")
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
