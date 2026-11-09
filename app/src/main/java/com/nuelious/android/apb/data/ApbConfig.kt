package com.nuelious.android.apb.data

import org.json.JSONArray
import org.json.JSONObject

enum class ProjectType(val id:String,val label:String) {
 ANDROID_STUDIO("android-studio","Android Studio / Gradle"),
 ANDROID_JAVA("android-java","Android Java"), ANDROID_KOTLIN("android-kotlin","Android Kotlin"),
 APKTOOL("apktool","APKTool decoded"), SMALI("smali","Smali source"), CLASS2SMALI("class2smali","Class2Smali decompiled"), DEX("dex","DEX"), CUSTOM("custom","Custom");
 companion object { fun fromId(id:String)=entries.firstOrNull{it.id==id}?:CUSTOM }
}
data class ApbProject(val id:String,val name:String,val type:ProjectType,val root:java.io.File,val configFile:java.io.File)
data class ApbConfig(val json:JSONObject) {
 val projectName get()=json.getJSONObject("project").getString("name")
 val projectType get()=ProjectType.fromId(json.getJSONObject("project").getString("type"))
 val engine get()=json.getJSONObject("build").getString("engine")
 val output get()=json.getJSONObject("build").getString("output")
 fun validate() {
  require(json.getString("format")=="APB"){"format must be APB"}
  require(json.getInt("format_version")==1){"Unsupported APB schema"}
  require(json.has("project")&&json.has("source")&&json.has("build")){"Missing required sections"}
  require(projectName.isNotBlank()){"Project name is empty"}
  require(engine in setOf("gradle","apktool","smali","dex","custom")){"Unknown engine"}
  require(output.isNotBlank()&&!output.startsWith("/")&&!output.split('/').contains("..")){"Unsafe output path"}
  val root=json.getJSONObject("source").optString("root",".")
  require(!root.startsWith("/")&&!root.split('/').contains("..")){"Unsafe source path"}
 }
 fun pretty()=json.toString(2)
 companion object {
  fun parse(text:String)=ApbConfig(JSONObject(text))
  fun create(name:String,type:ProjectType,s:ProjectScannerResult):ApbConfig {
   val engine=when(type){ProjectType.ANDROID_STUDIO,ProjectType.ANDROID_JAVA,ProjectType.ANDROID_KOTLIN->"gradle";ProjectType.APKTOOL->"apktool";ProjectType.SMALI->"smali";ProjectType.CLASS2SMALI->if(s.apktool)"apktool" else "smali";ProjectType.DEX->"dex";else->"custom"}
   val source=JSONObject().put("root",".").put("manifest",s.manifest?:JSONObject.NULL).put("modules",JSONArray(s.modules)).put("smali_directories",JSONArray(s.smali)).put("resources",if(s.resources)"res" else JSONObject.NULL).put("apktool_file",if(s.apktool)"apktool.yml" else JSONObject.NULL)
   val b=JSONObject().put("engine",engine).put("task",if(engine=="gradle")"assembleDebug" else JSONObject.NULL).put("output",if(engine=="gradle")"app/build/outputs/apk/debug" else "dist").put("filename",JSONObject.NULL).put("offline",false)
   return ApbConfig(JSONObject().put("format","APB").put("format_version",1).put("project",JSONObject().put("name",name).put("type",type.id)).put("source",source).put("runtime",JSONObject().put("environment","apb-linux").put("java_version","17")).put("build",b).put("options",JSONObject().put("sign_apk",true).put("zipalign",true)))
  }
 }
}
data class ProjectScannerResult(val manifest:String?,val modules:List<String>,val smali:List<String>,val resources:Boolean,val apktool:Boolean)
