package com.nuelious.android.apb.runtime
import android.content.Context
import android.net.Uri
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipInputStream

class RuntimeArchiveInstaller(private val context:Context) {
 fun install(uri:Uri,component:String,expectedSha256:String):File {
  require(component.matches(Regex("[a-zA-Z0-9._-]+"))){"Invalid component id"}
  val archive=File(context.cacheDir,"$component.zip")
  val digest=MessageDigest.getInstance("SHA-256")
  context.contentResolver.openInputStream(uri)?.use{input->archive.outputStream().use{out->
   val buf=ByteArray(65536);while(true){val n=input.read(buf);if(n<0)break;digest.update(buf,0,n);out.write(buf,0,n)}
  }}?:error("Cannot read archive")
  val hash=digest.digest().joinToString(""){"%02x".format(it)}
  require(hash.equals(expectedSha256,true)){"SHA-256 verification failed"}
  val root=File(context.filesDir,"runtime").apply{mkdirs()}
  val stage=File(root,".$component-staging").apply{deleteRecursively();mkdirs()}
  ZipInputStream(archive.inputStream().buffered()).use{zip->while(true){
   val e=zip.nextEntry?:break;val out=File(stage,e.name).canonicalFile
   require(out.path.startsWith(stage.canonicalPath+File.separator)||out==stage){"Zip path traversal blocked"}
   if(e.isDirectory)out.mkdirs() else {out.parentFile?.mkdirs();out.outputStream().use{zip.copyTo(it)}}
   zip.closeEntry()
  }}
  val dest=File(root,component);dest.deleteRecursively();check(stage.renameTo(dest)){"Cannot activate runtime"};return dest
 }
}
