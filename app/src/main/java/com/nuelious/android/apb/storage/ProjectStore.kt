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
 private val prefs=context.getSharedPreferences("apb-project-links",Context.MODE_PRIVATE)

 fun listProjects():List<ApbProject> = base.listFiles()?.filter{it.isDirectory}?.mapNotNull{dir->
  runCatching {
   val cf=File(dir,"apb.json")
   val p=ApbProject(dir.name,"",ProjectType.CUSTOM,dir,cf)
   syncConfig(p)
   val c=ApbConfig.parse(cf.readText());c.validate()
   p.copy(name=c.projectName,type=c.projectType)
  }.getOrNull()
 }?: emptyList()

 fun importTree(uri:Uri,typeOverride:ProjectType?=null):ApbProject {
  val doc=DocumentFile.fromTreeUri(context,uri)?:error("Cannot open folder")
  val id=UUID.randomUUID().toString();val target=File(base,id).apply{mkdirs()}
  try {
   copy(doc,target)
   val scan=ProjectScanner();val result=scan.scan(target);val type=typeOverride?:scan.suggest(result)
   val name=doc.name?.takeIf{it.isNotBlank()}?:"ImportedProject"
   val cf=File(target,"apb.json")
   val existing=doc.findFile("apb.json")
   val config=if(existing!=null&&existing.isFile) {
    runCatching { val text=context.contentResolver.openInputStream(existing.uri)?.bufferedReader()?.use{it.readText()}?:error("Cannot read apb.json");val parsed=ApbConfig.parse(text);parsed.validate();cf.writeText(parsed.pretty());parsed }
     .getOrElse { ApbConfig.create(name,type,result).also{it.validate();cf.writeText(it.pretty())} }
   } else ApbConfig.create(name,type,result).also{it.validate();cf.writeText(it.pretty())}
   prefs.edit().putString("tree:$id",uri.toString()).apply()
   writeExternalConfig(id,config.pretty())
   return ApbProject(id,config.projectName,config.projectType,target,cf)
  } catch(e:Exception){target.deleteRecursively();throw e}
 }

 fun syncConfig(project:ApbProject) {
  val treeUri=prefs.getString("tree:${project.id}",null)?.let(Uri::parse)?:return
  val tree=DocumentFile.fromTreeUri(context,treeUri)?:return
  val doc=tree.findFile("apb.json")?:return
  if(doc.isFile) context.contentResolver.openInputStream(doc.uri)?.bufferedReader()?.use{project.configFile.writeText(it.readText())}
 }

 fun saveConfig(project:ApbProject,text:String) {
  val config=ApbConfig.parse(text);config.validate()
  project.configFile.writeText(config.pretty())
  writeExternalConfig(project.id,config.pretty())
 }

 private fun writeExternalConfig(id:String,text:String) {
  val treeUri=prefs.getString("tree:$id",null)?.let(Uri::parse)?:return
  val tree=DocumentFile.fromTreeUri(context,treeUri)?:return
  val doc=tree.findFile("apb.json")?:tree.createFile("application/json","apb.json")?:return
  context.contentResolver.openOutputStream(doc.uri,"wt")?.bufferedWriter()?.use{it.write(text)}
 }

 private fun copy(src:DocumentFile,dst:File) {
  src.listFiles().forEach { child ->
   val name=child.name?.replace("/","_")?.takeIf{it.isNotBlank()}?:"unnamed"
   if(name=="apb.json") return@forEach
   val out=File(dst,name)
   if(child.isDirectory){out.mkdirs();copy(child,out)} else if(child.isFile) {
    context.contentResolver.openInputStream(child.uri)?.use{input->out.outputStream().use{input.copyTo(it)}}
   }
  }
 }
 fun delete(p:ApbProject){require(p.root.canonicalPath.startsWith(base.canonicalPath+File.separator));p.root.deleteRecursively();prefs.edit().remove("tree:${p.id}").apply()}
}
