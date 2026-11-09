package com.nuelious.android.apb

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.nuelious.android.apb.core.BuildCoordinator
import com.nuelious.android.apb.data.ApbProject
import com.nuelious.android.apb.data.ProjectType
import com.nuelious.android.apb.storage.ProjectStore
import kotlinx.coroutines.launch
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
 private var lastApkPath by mutableStateOf<String?>(null)
 private var pendingUri by mutableStateOf<Uri?>(null)
 private var showTypeDialog by mutableStateOf(false)
 private val exportPicker = registerForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.android.package-archive")) { uri ->
  val path = lastApkPath
  if (uri != null && path != null) runCatching {
   contentResolver.openOutputStream(uri)?.use { out -> FileInputStream(File(path)).use { it.copyTo(out) } } ?: error("Cannot open destination")
   status = "APK exported"
  }.onFailure { status = "Export failed: ${it.message}" }
 }
 private val picker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()){uri->
  if(uri!=null){
   contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
   pendingUri=uri;showTypeDialog=true
  }
 }
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState)
  store=ProjectStore(this);builder=BuildCoordinator(this);refresh()
  setContent { MaterialTheme {
   Scaffold(topBar={TopAppBar(title={Text("APB · Android Package Builder")})}){padding->
    LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
     item { Text("Project workspace",style=MaterialTheme.typography.headlineSmall)
      Text("Import Android Studio, APKTool, Smali and DEX project folders.")
      Row { Button(onClick={picker.launch(null)}){Text("Import folder")};Spacer(Modifier.width(8.dp));OutlinedButton(onClick={startActivity(Intent(this@MainActivity,GitHubImportActivity::class.java))}){Text("Import GitHub")} }
      OutlinedButton(onClick={refresh()}){Text("Refresh")}
      OutlinedButton(onClick={startActivity(Intent(this@MainActivity,com.nuelious.android.apb.runtime.RuntimeManagerActivity::class.java))}){Text("Runtime Manager")}
     }
     if(projects.isEmpty()) item { Text("No projects yet. Import a folder to create its apb.json configuration.") }
     items(projects,key={it.id}){p->Card(onClick={selected=p},modifier=Modifier.fillMaxWidth(),
      colors=CardDefaults.cardColors(containerColor=if(selected?.id==p.id)MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant)){
       Column(Modifier.padding(14.dp)){Text(p.name,style=MaterialTheme.typography.titleMedium);Text(p.type.label,style=MaterialTheme.typography.bodySmall)}
      }}
     selected?.let { p -> item {
      Text("Selected: ${p.name}",style=MaterialTheme.typography.titleLarge)
      Text("Project configuration: ${p.configFile.absolutePath}",style=MaterialTheme.typography.bodySmall)
      Row { Button(onClick={build(p)}){Text("Build APK")};Spacer(Modifier.width(6.dp))
       OutlinedButton(onClick={startActivity(Intent(this@MainActivity,ConfigEditorActivity::class.java).putExtra(ConfigEditorActivity.EXTRA_PROJECT_ID,p.id))}){Text("Edit apb.json")} }
      if(lastApkPath!=null) OutlinedButton(onClick={exportPicker.launch(File(lastApkPath!!).name)}){Text("Export APK")}
      TextButton(onClick={store.delete(p);selected=null;lastApkPath=null;refresh()}){Text("Remove project")}
     }}
     item { Text(status,style=MaterialTheme.typography.titleMedium);if(logs.isNotBlank())Card(Modifier.fillMaxWidth()){Text(logs.takeLast(14000),Modifier.padding(12.dp),style=MaterialTheme.typography.bodySmall)} }
     item { Text("Runtime status",style=MaterialTheme.typography.titleMedium)
      Text("APB does not require Termux or root. Gradle builds need a compatible Java, Gradle and Android SDK runtime.") }
    }
   }
   if(showTypeDialog) AlertDialog(
    onDismissRequest={showTypeDialog=false;pendingUri=null},
    title={Text("Select project type")},
    text={Column(verticalArrangement=Arrangement.spacedBy(6.dp)){
     Text("Choose the format APB should use to configure this folder.")
     ProjectType.entries.forEach { type ->
      OutlinedButton(modifier=Modifier.fillMaxWidth(),onClick={
       val uri=pendingUri;showTypeDialog=false;pendingUri=null
       if(uri!=null) lifecycleScope.launch { status="Importing as ${type.label}…"
        runCatching{store.importTree(uri,type)}.onSuccess{selected=it;refresh();status="Imported ${it.name}"}
         .onFailure{status="Import failed: ${it.message}"}
       }
      }){Text(type.label)}
     }
    }},
    confirmButton={TextButton(onClick={showTypeDialog=false;pendingUri=null}){Text("Cancel")}}
   )
  }}
 }
 private fun refresh(){projects=store.listProjects();selected=selected?.let{s->projects.firstOrNull{it.id==s.id}}}
 private fun build(p:ApbProject){lifecycleScope.launch{
  logs="";status="Building…"
  store.syncConfig(p)
  val r=builder.build(p){line->runOnUiThread{logs+=line+"\n"}}
  lastApkPath=r.output
  status=if(r.success)"Build complete" else "Build failed: ${r.message}"
  logs+="\n"+r.message+(r.output?.let{"\nAPK: $it"}?:"")
 }}
 override fun onResume(){super.onResume();if(::store.isInitialized)refresh()}
}
