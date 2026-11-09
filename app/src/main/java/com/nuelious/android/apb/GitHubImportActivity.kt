package com.nuelious.android.apb

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.nuelious.android.apb.core.GitHubRepoImporter
import com.nuelious.android.apb.data.ProjectType
import com.nuelious.android.apb.storage.ProjectStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class GitHubImportActivity:ComponentActivity(){
 private var repository by mutableStateOf("")
 private var branch by mutableStateOf("main")
 private var status by mutableStateOf("Import a public GitHub repository into a folder you choose.")
 private var downloaded by mutableStateOf<File?>(null)
 private var destination by mutableStateOf<Uri?>(null)
 private var showTypeDialog by mutableStateOf(false)
 private val picker by lazy{registerForActivityResult(ActivityResultContracts.OpenDocumentTree()){uri->
  if(uri!=null){
   contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
   destination=uri
   lifecycleScope.launch {
    status="Copying repository into selected folder…"
    runCatching{withContext(Dispatchers.IO){ProjectStore(this@GitHubImportActivity).copyLocalToTree(downloaded?:error("Download missing"),uri)}}
     .onSuccess{status="Repository copied. Choose its project type.";showTypeDialog=true}
     .onFailure{status="Copy failed: ${it.message}"}
   }
  }
 }}
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState)
  setContent { MaterialTheme {
   Column(Modifier.fillMaxSize().padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
    Text("Import from GitHub",style=MaterialTheme.typography.headlineSmall)
    Text("Public repositories only. APB downloads the selected branch archive; private repositories are not supported yet.")
    OutlinedTextField(repository,{repository=it},Modifier.fillMaxWidth(),label={Text("Repository URL")},placeholder={Text("https://github.com/owner/repository")},singleLine=true)
    OutlinedTextField(branch,{branch=it},Modifier.fillMaxWidth(),label={Text("Branch")},singleLine=true)
    Button(onClick={
     lifecycleScope.launch {
      status="Downloading repository…"
      runCatching{withContext(Dispatchers.IO){GitHubRepoImporter(this@GitHubImportActivity).download(repository,branch)}}
       .onSuccess{downloaded=it;status="Download complete. Select an empty destination folder.";picker.launch(null)}
       .onFailure{status="Download failed: ${it.message}"}
     }
    },enabled=repository.isNotBlank()){Text("Download repository")}
    Text(status)
    Text("Choose an empty folder on shared storage. APB writes apb.json there so it can be edited with a file manager.")
    TextButton(onClick={finish()}){Text("Close")}
   }
   if(showTypeDialog) AlertDialog(
    onDismissRequest={showTypeDialog=false},
    title={Text("Select project type")},
    text={Column(verticalArrangement=Arrangement.spacedBy(6.dp)){
     ProjectType.entries.forEach{type->OutlinedButton(onClick={
      val uri=destination;showTypeDialog=false
      if(uri!=null)lifecycleScope.launch{
       status="Importing as ${type.label}…"
       runCatching{withContext(Dispatchers.IO){ProjectStore(this@GitHubImportActivity).importTree(uri,type)}}
        .onSuccess{status="Imported ${it.name}. You can return to APB.";setResult(RESULT_OK);finish()}
        .onFailure{status="Import failed: ${it.message}"}
      }
     },modifier=Modifier.fillMaxWidth()){Text(type.label)}}
    }},
    confirmButton={TextButton(onClick={showTypeDialog=false}){Text("Cancel")}}
   )
  }}
 }
}
