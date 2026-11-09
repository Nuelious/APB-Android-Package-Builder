package com.nuelious.android.apb.runtime

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.Paths
import java.security.MessageDigest

data class RuntimeProgress(val message:String,val downloaded:Long=0,val total:Long=-1,val complete:Boolean=false)

class RuntimeBootstrap(private val context:Context) {
 private val root=RuntimeStorage.privateRuntime(context)
 private val active get()=File(root,"active")
 fun isInstalled():Boolean=File(active,"runtime-ready.json").isFile &&
  File(active,"rootfs/usr/bin/env").isFile && File(active,"jdk/bin/java").isFile &&
  File(active,"gradle/bin/gradle").isFile && File(active,"apktool.jar").isFile

 suspend fun install(onProgress:(RuntimeProgress)->Unit)=withContext(Dispatchers.IO) {
  check(android.os.Build.SUPPORTED_ABIS.any{it=="arm64-v8a"}){"The APB Linux runtime currently requires a 64-bit ARM Android device."}
  onProgress(RuntimeProgress("Checking APB runtime release…"))
  val release=JSONObject(readUrl("https://api.github.com/repos/Nuelious/APB-Android-Package-Builder/releases/latest"))
  var archiveUrl:String?=null;var checksumUrl:String?=null
  val assets=release.getJSONArray("assets")
  for(i in 0 until assets.length()) {
   val a=assets.getJSONObject(i)
   when(a.getString("name")) {
    "apb-runtime-arm64.tar.gz"->archiveUrl=a.getString("browser_download_url")
    "apb-runtime-arm64.tar.gz.sha256"->checksumUrl=a.getString("browser_download_url")
   }
  }
  val url=archiveUrl?:error("No ARM64 runtime release is published yet. The runtime build must finish first.")
  val expected=readUrl(checksumUrl?:error("Runtime release has no SHA-256 file.")).trim().substringBefore(' ').lowercase()
  require(expected.matches(Regex("[a-f0-9]{64}"))){"Invalid runtime checksum."}
  val archive=File(context.cacheDir,"apb-runtime-arm64.tar.gz.part")
  try {
   download(url,archive,onProgress)
   val actual=sha256(archive)
   require(actual==expected){"Runtime SHA-256 verification failed; nothing was installed."}
   val staging=File(root,".runtime-staging").apply{deleteRecursively();mkdirs()}
   try {
    onProgress(RuntimeProgress("Extracting verified ARM64 runtime…",archive.length(),archive.length()))
    extract(archive,staging)
    listOf("rootfs/usr/bin/env","jdk/bin/java","gradle/bin/gradle","apktool.jar").forEach {
     check(File(staging,it).exists()){"Runtime archive is missing $it"}
    }
    File(staging,"runtime-ready.json").writeText(JSONObject()
     .put("release",release.optString("tag_name")).put("sha256",actual)
     .put("installed_at",System.currentTimeMillis()).toString())
    val backup=File(root,".runtime-previous")
    backup.deleteRecursively()
    if(active.exists()) Files.move(active.toPath(),backup.toPath(),StandardCopyOption.REPLACE_EXISTING)
    try {
     Files.move(staging.toPath(),active.toPath(),StandardCopyOption.ATOMIC_MOVE)
    } catch(t:Throwable) {
     if(backup.exists()) Files.move(backup.toPath(),active.toPath(),StandardCopyOption.REPLACE_EXISTING)
     throw t
    }
    backup.deleteRecursively()
    onProgress(RuntimeProgress("ARM64 runtime installed and verified.",archive.length(),archive.length(),true))
   } catch(t:Throwable) { staging.deleteRecursively();throw t }
  } finally { archive.delete() }
 }

 private fun readUrl(address:String):String {
  val c=(URL(address).openConnection() as HttpURLConnection).apply {
   connectTimeout=20000;readTimeout=30000;setRequestProperty("User-Agent","APB-Android-Package-Builder")
  }
  try{return c.inputStream.bufferedReader().use{it.readText()}}finally{c.disconnect()}
 }
 private fun download(address:String,target:File,progress:(RuntimeProgress)->Unit) {
  val c=(URL(address).openConnection() as HttpURLConnection).apply {
   connectTimeout=20000;readTimeout=60000;setRequestProperty("User-Agent","APB-Android-Package-Builder")
  }
  try {
   val total=c.contentLengthLong;var count=0L
   c.inputStream.buffered().use{input->FileOutputStream(target).use{output->
    val b=ByteArray(131072)
    while(true){val n=input.read(b);if(n<0)break;output.write(b,0,n);count+=n;progress(RuntimeProgress("Downloading ARM64 runtime…",count,total))}
    output.fd.sync()
   }}
  }finally{c.disconnect()}
 }
 private fun sha256(file:File):String {
  val d=MessageDigest.getInstance("SHA-256")
  file.inputStream().buffered().use{input->val b=ByteArray(131072);while(true){val n=input.read(b);if(n<0)break;d.update(b,0,n)}}
  return d.digest().joinToString(""){"%02x".format(it)}
 }
 private fun extract(archive:File,destination:File) {
  TarArchiveInputStream(GzipCompressorInputStream(BufferedInputStream(archive.inputStream()))).use{tar->
   var count=0
   while(true) {
    val e=tar.nextTarEntry?:break
    count++;require(count<500000){"Runtime archive has too many entries."}
    val out=File(destination,e.name).canonicalFile
    require(out.path.startsWith(destination.canonicalPath+File.separator)||out==destination){"Unsafe archive path: ${e.name}"}
    when {
     e.isDirectory->check(out.mkdirs()||out.isDirectory)
     e.isSymbolicLink->{out.parentFile?.mkdirs();Files.createSymbolicLink(out.toPath(),Paths.get(e.linkName))}
     e.isLink->error("Runtime archive must be created with hard links dereferenced.")
     e.isFile->{
      out.parentFile?.mkdirs()
      FileOutputStream(out).use{tar.copyTo(it)}
      out.setExecutable(e.mode and 73 != 0,false)
      out.setReadable(true,false)
      out.setWritable(true,true)
     }
    }
   }
  }
 }
}
