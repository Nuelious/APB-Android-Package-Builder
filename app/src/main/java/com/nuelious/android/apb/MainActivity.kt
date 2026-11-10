package com.nuelious.android.apb

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import kotlinx.coroutines.withContext
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.ui.window.Dialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.nuelious.android.apb.runtime.RuntimeBootstrap
import com.nuelious.android.apb.runtime.RuntimeProgress
import com.nuelious.android.apb.core.BuildCoordinator
import com.nuelious.android.apb.data.ApbProject
import com.nuelious.android.apb.data.ProjectType
import com.nuelious.android.apb.storage.ProjectStore
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileInputStream

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity:ComponentActivity(){
 private lateinit var store:ProjectStore
 private lateinit var builder:BuildCoordinator
 private var projects by mutableStateOf<List<ApbProject>>(emptyList())
 private var selected by mutableStateOf<ApbProject?>(null)
 private var status by mutableStateOf("Ready")
 private var logs by mutableStateOf("")
 private var buildBusy by mutableStateOf(false)
 private var buildStartedAt by mutableLongStateOf(0L)
 private var buildElapsedSeconds by mutableIntStateOf(0)
 private var lastBuildOutputAt by mutableLongStateOf(0L)
 private var buildPhase by mutableStateOf("Preparing build…")
 private var lastApkPath by mutableStateOf<String?>(null)
 private var runtimeReady by mutableStateOf(false)
 private var runtimeBusy by mutableStateOf(false)
 private var runtimeProgress by mutableStateOf(RuntimeProgress("Runtime not installed"))
 private var runtimeError by mutableStateOf<String?>(null)
 private var pendingUri by mutableStateOf<Uri?>(null)
 private var runtimeDetails by mutableStateOf(false)
 private val runtimeZipPicker=registerForActivityResult(ActivityResultContracts.OpenDocument()){ uri -> if(uri!=null) installSelectedRuntime(uri) }
 private var showTypeDialog by mutableStateOf(false)
 private val exportPicker=registerForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.android.package-archive")){uri->
  val path=lastApkPath
  if(uri!=null&&path!=null)runCatching{contentResolver.openOutputStream(uri)?.use{out->FileInputStream(File(path)).use{it.copyTo(out)}}?:error("Cannot open destination");status="APK exported"}.onFailure{status="Export failed: ${it.message}"}
 }
 private val picker=registerForActivityResult(ActivityResultContracts.OpenDocumentTree()){uri->
  if(uri!=null){contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION);pendingUri=uri;showTypeDialog=true}
 }
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);store=ProjectStore(this);builder=BuildCoordinator(this);refresh();runtimeReady=RuntimeBootstrap(this).isInstalled()
  setContent{MaterialTheme{Scaffold(topBar={TopAppBar(title={Text("APB · Android Package Builder")})}){padding->
   LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
    item{Text("Project workspace",style=MaterialTheme.typography.headlineSmall);Text("Import Android Studio, APKTool, Smali and DEX project folders.")
     Row{Button(onClick={picker.launch(null)}){Text("Import folder")};Spacer(Modifier.width(8.dp));OutlinedButton(onClick={startActivity(Intent(this@MainActivity,GitHubImportActivity::class.java))}){Text("Import GitHub")}}
     OutlinedButton(onClick={refresh()}){Text("Refresh")};OutlinedButton(onClick={startActivity(Intent(this@MainActivity,FileManagerActivity::class.java))}){Text("Open File Manager")}
    }
    item {
     Card(Modifier.fillMaxWidth()) {
      Column(Modifier.padding(14.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) {
       Text("ARM64 build environment", style=MaterialTheme.typography.titleMedium)
       Text(if(runtimeReady) "Linux ARM64, JDK 17, Gradle and Android build tools are installed in APB private storage."
            else "The initial runtime download is several hundred MB and needs over 1 GB of free internal storage. Wi-Fi is recommended.")
       if(runtimeBusy) { BucketProgressPanel(runtimeProgress.percent, runtimeProgress.message, runtimeProgress.currentFile, runtimeProgress.details, runtimeProgress.downloaded, runtimeProgress.total, "install", onInfo = { runtimeDetails = true }) } else if(!runtimeReady) { Button(onClick = { installRuntime(force = false) }) { Text("Install build environment") }; OutlinedButton(onClick = { runtimeZipPicker.launch(arrayOf("application/zip", "application/octet-stream", "application/x-zip-compressed")) }) { Text("Choose runtime ZIP…") } }
       else {
        Text("Runtime verified and ready.")
        OutlinedButton(onClick = { installRuntime(force = true) }) { Text("Reinstall build environment") }
        OutlinedButton(onClick = { runtimeZipPicker.launch(arrayOf("application/zip", "application/octet-stream", "application/x-zip-compressed")) }) { Text("Install from ZIP…") }
       }
       runtimeError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
      }
     }
    }
    if(projects.isEmpty())item{Text("No projects yet. Import a folder to create its apb.json configuration.")}
    items(projects,key={it.id}){p->Card(onClick={selected=p},modifier=Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=if(selected?.id==p.id)MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant)){Column(Modifier.padding(14.dp)){Text(p.name,style=MaterialTheme.typography.titleMedium);Text(p.type.label,style=MaterialTheme.typography.bodySmall)}}}
    selected?.let{p->item{Text("Selected: ${p.name}",style=MaterialTheme.typography.titleLarge);Text("Project configuration: ${p.configFile.absolutePath}",style=MaterialTheme.typography.bodySmall)
     Row{Button(enabled=!buildBusy,onClick={build(p)}){Text(if(buildBusy) "Building…" else "Build APK")};Spacer(Modifier.width(6.dp));OutlinedButton(enabled=!buildBusy,onClick={startActivity(Intent(this@MainActivity,ConfigEditorActivity::class.java).putExtra(ConfigEditorActivity.EXTRA_PROJECT_ID,p.id))}){Text("Edit apb.json")}}
     if(lastApkPath!=null)OutlinedButton(onClick={exportPicker.launch(File(lastApkPath!!).name)}){Text("Export APK")}
     TextButton(onClick={store.delete(p);selected=null;lastApkPath=null;refresh()}){Text("Remove project")}
    }}
    item {
     if(buildBusy || logs.isNotBlank()) Card(Modifier.fillMaxWidth()) {
      Column(Modifier.padding(12.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) {
       Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) {
        Text(if(buildBusy) "Build running" else "Build output", style=MaterialTheme.typography.titleMedium)
        if(buildBusy) Text("%02d:%02d".format(buildElapsedSeconds/60, buildElapsedSeconds%60), style=MaterialTheme.typography.labelLarge)
       }
       if(buildBusy) {
        LinearProgressIndicator(modifier=Modifier.fillMaxWidth())
        Text(buildPhase, style=MaterialTheme.typography.bodyMedium)
        val silentFor = ((System.currentTimeMillis()-lastBuildOutputAt).coerceAtLeast(0L)/1000L)
        Text(if(silentFor < 10) "Receiving build output…" else "No new output for $silentFor seconds. Gradle may still be working; this does not mean the build has stopped.", style=MaterialTheme.typography.bodySmall)
       }
       Text(status, style=MaterialTheme.typography.bodyMedium)
       if(logs.isNotBlank()) Text(logs.takeLast(14000), style=MaterialTheme.typography.bodySmall)
      }
     }
    }
    item{Text("Confidential project folder ends at /storage/emulated/0/apps/ADB/.t/.h/.i/.s/.i/.s/.p/.r/.i/.v/.a/.t/.e",style=MaterialTheme.typography.labelSmall)}
   }
  }
  BackHandler(enabled = buildBusy) { /* Build progress is intentionally non-dismissible. */ }
  if(buildBusy) Dialog(onDismissRequest = {}, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
   Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    val scroll = rememberScrollState()
    LaunchedEffect(logs) { scroll.animateScrollTo(scroll.maxValue) }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
     Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      Text("Building project", style = MaterialTheme.typography.headlineSmall)
      Text("%02d:%02d".format(buildElapsedSeconds/60, buildElapsedSeconds%60), style = MaterialTheme.typography.titleMedium)
     }
     LinearProgressIndicator(Modifier.fillMaxWidth())
     Text(buildPhase, style = MaterialTheme.typography.titleMedium)
     val silentFor = ((System.currentTimeMillis()-lastBuildOutputAt).coerceAtLeast(0L)/1000L)
     Text(if(silentFor < 10) "Build output is updating…" else "Gradle has produced no new output for $silentFor seconds. The build remains active; some compilation steps are quiet.", style = MaterialTheme.typography.bodySmall)
     Text(status, style = MaterialTheme.typography.bodyMedium)
     Text("Live compiler / Gradle output", style = MaterialTheme.typography.titleSmall)
     Surface(Modifier.weight(1f).fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
      Text(if(logs.isBlank()) "Starting build process…" else logs.takeLast(50000), Modifier.fillMaxSize().verticalScroll(scroll).padding(12.dp), style = MaterialTheme.typography.bodySmall)
     }
     Text("Keep APB open while compilation runs. This screen closes automatically when the build finishes.", style = MaterialTheme.typography.bodySmall)
    }
   }
  }
  if(runtimeDetails) AlertDialog(
   onDismissRequest = { runtimeDetails = false },
   title = { Text("Runtime installation details") },
   text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text("Progress: ${runtimeProgress.percent}%")
    Text(runtimeProgress.message)
    if (runtimeProgress.currentFile.isNotBlank()) Text("Current file: ${runtimeProgress.currentFile}")
    if (runtimeProgress.downloaded > 0L || runtimeProgress.total > 0L) Text("Expanded: ${runtimeProgress.downloaded} / ${runtimeProgress.total} bytes")
    if (runtimeProgress.details.isNotBlank()) Text(runtimeProgress.details)
    if (runtimeBusy) LinearProgressIndicator(progress = { runtimeProgress.percent.coerceIn(0,100) / 100f }, modifier = Modifier.fillMaxWidth())
   } },
   confirmButton = { TextButton(onClick = { runtimeDetails = false }) { Text("Close") } }
  )
  if(showTypeDialog)AlertDialog(onDismissRequest={showTypeDialog=false;pendingUri=null},title={Text("Select project type")},text={Column(verticalArrangement=Arrangement.spacedBy(6.dp)){Text("Choose the format APB should use to configure this folder.");ProjectType.entries.forEach{type->OutlinedButton(modifier=Modifier.fillMaxWidth(),onClick={val uri=pendingUri;showTypeDialog=false;pendingUri=null;if(uri!=null)lifecycleScope.launch{status="Importing as ${type.label}…";runCatching{store.importTree(uri,type)}.onSuccess{selected=it;refresh();status="Imported ${it.name}"}.onFailure{status="Import failed: ${it.message}"}}}){Text(type.label)}}}},confirmButton={TextButton(onClick={showTypeDialog=false;pendingUri=null}){Text("Cancel")}})
 }}
 }
 private fun installRuntime(force: Boolean) {
  if(runtimeBusy || (runtimeReady && !force)) return
  val installer = RuntimeBootstrap(this)
  if(!force && installer.isInstalled()) { runtimeReady = true; return }
  if(filesDir.usableSpace < 1536L * 1024L * 1024L) {
   runtimeError = "At least 1.5 GB of free internal storage is recommended."
   return
  }
  runtimeBusy = true
  runtimeError = null
  lifecycleScope.launch {
   runCatching { installer.install { progress -> runOnUiThread { runtimeProgress = progress } } }
    .onSuccess { runtimeReady = installer.isInstalled(); if(!runtimeReady) runtimeError = "Runtime verification did not pass." }
    .onFailure { runtimeError = it.message ?: "Runtime installation failed." }
   runtimeBusy = false
  }
 }
 private fun installSelectedRuntime(uri: Uri) {
  if(runtimeBusy) return
  runtimeBusy=true; runtimeError=null; runtimeDetails=true
  lifecycleScope.launch {
   val archive=File(cacheDir,"apb-selected-runtime-${System.nanoTime()}.zip")
   runCatching {
    withContext(kotlinx.coroutines.Dispatchers.IO) {
     contentResolver.openInputStream(uri)?.use { input -> archive.outputStream().use { output -> input.copyTo(output) } } ?: error("Cannot read the selected ZIP.")
    }
    RuntimeBootstrap(this@MainActivity).installArchive(archive) { progress -> runOnUiThread { runtimeProgress=progress } }
   }.onSuccess { runtimeReady=RuntimeBootstrap(this@MainActivity).isInstalled(); status="Selected runtime installed and verified." }
    .onFailure { runtimeError=it.message ?: "Selected runtime installation failed." }
   archive.delete(); runtimeBusy=false
  }
 }
 private fun refresh(){projects=store.listProjects();selected=selected?.let{s->projects.firstOrNull{it.id==s.id}}}
 private fun build(p:ApbProject) {
  if(buildBusy) return
  buildBusy=true
  window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
  buildStartedAt=System.currentTimeMillis()
  lastBuildOutputAt=buildStartedAt
  buildElapsedSeconds=0
  buildPhase="Starting PRoot and Gradle…"
  logs=""
  status="Building…"
  lifecycleScope.launch {
   val timer=launch {
    while(buildBusy) {
     buildElapsedSeconds=((System.currentTimeMillis()-buildStartedAt)/1000L).toInt()
     delay(1000)
    }
   }
   val result=runCatching {
    store.syncConfig(p)
    builder.build(p) { line ->
     runOnUiThread {
      logs=(logs+line+"\n").takeLast(30000)
      lastBuildOutputAt=System.currentTimeMillis()
      val clean=line.trim()
      if(clean.isNotEmpty()) buildPhase=clean.take(150)
     }
    }
   }.getOrElse { com.nuelious.android.apb.core.BuildResult(false,it.message ?: "Build failed.") }
   timer.cancel()
   lastApkPath=result.output
   status=if(result.success) "Build complete" else "Build failed: "+result.message
   logs=(logs+"\n"+result.message+(result.output?.let{"\nAPK: "+it}?:"")).takeLast(30000)
   buildBusy=false
   window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
  }
 }
 override fun onResume(){super.onResume();if(::store.isInitialized)refresh()}
}
