package com.nuelious.android.apb.core

import com.nuelious.android.apb.runtime.LinuxRuntimeExecutor
import java.io.File

class ApkPostProcessor(
 private val runtime:File,
 private val project:File,
 private val linux:Boolean,
 private val onLog:(String)->Unit
) {
 fun process(input:File,align:Boolean,sign:Boolean):File {
  var current=input
  val outputDir=File(input.parentFile,"apb-final").apply{mkdirs()}
  if(align) {
   val aligned=File(outputDir,input.nameWithoutExtension+"-aligned.apk")
   runTool("zipalign",listOf("-f","-p","4",path(current),path(aligned)))
   current=aligned
  }
  if(sign) {
   val keystore=File(runtime,"apb-debug.keystore")
   if(!keystore.exists()) {
    val ks=if(linux)"/apb-runtime/apb-debug.keystore" else keystore.absolutePath
    runTool("keytool",listOf("-genkeypair","-noprompt","-keystore",ks,"-storepass","android","-keypass","android",
     "-alias","apb","-keyalg","RSA","-keysize","2048","-validity","10000","-dname","CN=APB Debug,O=APB,C=US"))
   }
   val signed=File(outputDir,input.nameWithoutExtension+"-signed.apk")
   val ks=if(linux)"/apb-runtime/apb-debug.keystore" else keystore.absolutePath
   runTool("apksigner",listOf("sign","--ks",ks,"--ks-key-alias","apb","--ks-pass","pass:android","--key-pass","pass:android","--out",path(signed),path(current)))
   runTool("apksigner",listOf("verify","--verbose",path(signed)))
   current=signed
  }
  return current
 }
 private fun path(file:File):String=if(linux)"/workspace/"+file.relativeTo(project).invariantSeparatorsPath else file.absolutePath
 private fun runTool(name:String,args:List<String>) {
  val process=if(linux) LinuxRuntimeExecutor(runtime,project).start(listOf(name)+args) else {
   val exe=runtime.walkTopDown().maxDepth(9).firstOrNull{it.isFile&&it.name==name&&it.canExecute()}
    ?:error("Missing required APK post-processing tool: $name")
   val pb=ProcessBuilder(listOf(exe.absolutePath)+args).directory(project).redirectErrorStream(true)
   val java=runtime.walkTopDown().maxDepth(9).firstOrNull{it.isFile&&it.name=="java"&&it.canExecute()}
   if(java!=null){val home=java.parentFile?.parentFile;pb.environment()["JAVA_HOME"]=home?.absolutePath;pb.environment()["PATH"]=File(home,"bin").absolutePath+File.pathSeparator+(pb.environment()["PATH"]?:"")}
   pb.start()
  }
  val output=process.inputStream.bufferedReader().use{it.readText()}
  if(output.isNotBlank())onLog(output)
  val code=process.waitFor()
  check(code==0){"$name failed with exit code $code"}
 }
}
