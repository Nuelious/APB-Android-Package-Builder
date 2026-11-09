package com.nuelious.android.apb.runtime
import android.content.Context
import android.net.Uri
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipInputStream

class RuntimeArchiveInstaller(private val context:Context) {
 fun install(uri:Uri,component:String,expectedSha256:String):File {
  require(component.matches(Regex("[a-zA-Z0-9._-]+"))){"Invalid component id"}
  require(expectedSha256.matches(Regex("[a-fA-F0-9]{64}"))){"Invalid SHA-256"}
  val archive=File(context.cacheDir,"$component.zip")
  val digest=MessageDigest.getInstance("SHA-256")
  context.contentResolver.openInputStream(uri)?.use{input->archive.outputStream().use{out->
   val buf=ByteArray(65536);while(true){val n=input.read(buf);if(n<0)break;digest.update(buf,0,n);out.write(buf,0,n)}
  }}?:error("Cannot read archive")
  val hash=digest.digest().joinToString(""){"%02x".format(it)}
  require(hash.equals(expectedSha256,true)){"SHA-256 verification failed"}
  val root=File(context.filesDir,"runtime").apply{mkdirs()}
  val stage=File(root,".$component-staging").apply{deleteRecursively();mkdirs()}
  var total=0L;var count=0
  try {
   ZipInputStream(archive.inputStream().buffered()).use{zip->while(true){
    val e=zip.nextEntry?:break;count++;require(count<=100000){"Archive has too many entries"}
    val out=File(stage,e.name).canonicalFile
    require(out.path.startsWith(stage.canonicalPath+File.separator)||out==stage){"Zip path traversal blocked"}
    if(e.isDirectory)out.mkdirs() else {
     out.parentFile?.mkdirs()
     out.outputStream().use{stream->
      val buffer=ByteArray(65536);while(true){val n=zip.read(buffer);if(n<0)break;total+=n
       require(total<=8L*1024*1024*1024){"Runtime archive exceeds 8 GiB expanded limit"}
       stream.write(buffer,0,n)
      }
     }
     if(out.name in setOf("java","gradle","aapt","aapt2","zipalign","apksigner","d8","dx","keytool","adb")) out.setExecutable(true,false)
    }
    zip.closeEntry()
   }}
   val dest=File(root,component);dest.deleteRecursively()
   check(stage.renameTo(dest)){"Cannot activate runtime"}
   return dest
  } catch(e:Exception){stage.deleteRecursively();throw e}
 }
}
