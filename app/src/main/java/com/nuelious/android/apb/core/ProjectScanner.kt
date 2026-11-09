package com.nuelious.android.apb.core
import java.io.File
import com.nuelious.android.apb.data.ProjectScannerResult
import com.nuelious.android.apb.data.ProjectType

class ProjectScanner {
 fun scan(root:File):ProjectScannerResult {
  require(root.isDirectory){"Selected folder unavailable"}
  val files=root.walkTopDown().maxDepth(8).toList()
  val manifest=files.firstOrNull{it.isFile&&it.name=="AndroidManifest.xml"}?.relativeTo(root)?.path
  val modules=files.filter{it.isFile&&(it.name=="build.gradle"||it.name=="build.gradle.kts")}.mapNotNull{it.parentFile?.relativeTo(root)?.path}.filter{it.isNotBlank()}.distinct()
  val smali=files.filter{it.isDirectory&&it.name.matches(Regex("smali(_classes\\d+)?"))}.map{it.relativeTo(root).path}
  return ProjectScannerResult(manifest,modules,smali,File(root,"res").isDirectory,File(root,"apktool.yml").isFile)
 }
 fun suggest(s:ProjectScannerResult):ProjectType=when {
  s.modules.isNotEmpty()->ProjectType.ANDROID_STUDIO
  s.apktool->ProjectType.APKTOOL
  s.smali.isNotEmpty()->ProjectType.SMALI
  else->ProjectType.CUSTOM
 }
}
