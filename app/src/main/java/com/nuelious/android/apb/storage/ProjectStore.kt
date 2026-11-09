package com.nuelious.android.apb.storage
import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.nuelious.android.apb.core.ProjectScanner
import com.nuelious.android.apb.data.*
import java.io.File
import java.util.UUID

class ProjectStore(private val context:Context) {
 private val base=File(context.filesDir,"projects").apply{mkdirs()}
 fun listProjects():List<ApbProject> = base.listFiles()?.filter{it.isDirectory}?.mapNotNull{dir->
  runCatching { val cf=File(dir,"apb.json");val c=ApbConfig.parse(cf.readText());c.validate();ApbProject(dir.name,c.projectName,c.projectType,dir,cf) }.getOrNull()
 }?: emptyList()
 fun importTree(uri:Uri,typeOverride:ProjectType?=null):ApbProject {
  val doc=DocumentFile.fromTreeUri(context,uri)?:error("Cannot open folder")
  val id=UUID.randomUUID().toString();val target=File(base,id).apply{mkdirs()}
  copy(doc,target)
  val scan=ProjectScanner();val result=scan.scan(target);val type=typeOverride?:scan.suggest(result)
  val name=doc.name?.takeIf{it.isNotBlank()}?:"ImportedProject"
  val config=ApbConfig.create(name,type,result);config.validate()
  val cf=File(target,"apb.json");cf.writeText(config.pretty())
  return ApbProject(id,name,type,target,cf)
 }
 private fun copy(src:DocumentFile,dst:File) {
  src.listFiles().forEach { child ->
   val name=child.name?.replace("/","_")?.takeIf{it.isNotBlank()}?:"unnamed";val out=File(dst,name)
   if(child.isDirectory){out.mkdirs();copy(child,out)} else if(child.isFile) {
    context.contentResolver.openInputStream(child.uri)?.use{input->out.outputStream().use{input.copyTo(it)}}
   }
  }
 }
 fun delete(p:ApbProject){require(p.root.canonicalPath.startsWith(base.canonicalPath+File.separator));p.root.deleteRecursively()}
}
