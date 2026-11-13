package com.nuelious.android.apb.storage
import android.content.Context
import android.os.Build
import android.os.Environment
import com.nuelious.android.apb.runtime.RuntimeStorage
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.nuelious.android.apb.core.ProjectScanner
import com.nuelious.android.apb.data.*
import java.io.File
import java.util.UUID

class ProjectStore(private val context:Context) {
 private val legacyBase=File(context.filesDir,"projects").apply{mkdirs()}
 private val base:File get()=if(Build.VERSION.SDK_INT<30||Environment.isExternalStorageManager()) RuntimeStorage.directory(context) else legacyBase
 private val prefs=context.getSharedPreferences("apb-project-links",Context.MODE_PRIVATE)

 fun listProjects():List<ApbProject> {
  migrateLegacyProjects()
  return base.listFiles()?.filter{it.isDirectory}?.mapNotNull{dir->
  runCatching {
   val cf=File(dir,"apb.json")
   val p=ApbProject(dir.name,"",ProjectType.CUSTOM,dir,cf)
   syncConfig(p)
   val c=ApbConfig.parse(cf.readText());c.validate()
   p.copy(name=c.projectName,type=c.projectType)
  }.getOrNull()
 }?: emptyList()
 }

 private fun migrateLegacyProjects() {
  val destination=base
  if(destination.canonicalFile==legacyBase.canonicalFile)return
  legacyBase.listFiles()?.forEach { source ->
   val target=File(destination,source.name)
   if(!target.exists()) runCatching { source.copyRecursively(target,overwrite=false) }
   if(target.exists()) source.deleteRecursively()
  }
 }

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

 fun copyLocalToTree(source:File,treeUri:Uri) {
  require(source.isDirectory){"Downloaded repository folder is missing"}
  val destination=DocumentFile.fromTreeUri(context,treeUri)?:error("Cannot open destination folder")
  copyLocalContents(source,destination)
 }
 private fun copyLocalContents(source:File,destination:DocumentFile) {
  source.listFiles()?.forEach { file ->
   if(file.name==".git") return@forEach
   if(file.isDirectory) {
    val child=destination.findFile(file.name)?.also{it.delete()}?.let{destination.createDirectory(file.name)}
      ?:destination.createDirectory(file.name)
    if(child!=null)copyLocalContents(file,child)
   } else if(file.isFile) {
    destination.findFile(file.name)?.delete()
    val child=destination.createFile("application/octet-stream",file.name)?:error("Cannot create ${file.name}")
    file.inputStream().use{input->context.contentResolver.openOutputStream(child.uri)?.use{input.copyTo(it)}?:error("Cannot write ${file.name}")}
   }
  }
 }

 fun delete(p:ApbProject){require(p.root.canonicalPath.startsWith(base.canonicalPath+File.separator));p.root.deleteRecursively();prefs.edit().remove("tree:${p.id}").apply()}
}
