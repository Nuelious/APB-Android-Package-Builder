package com.nuelious.android.apb.runtime
import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.security.MessageDigest

class RuntimeManagerActivity:ComponentActivity(){
 private val pick=registerForActivityResult(ActivityResultContracts.OpenDocument()){uri->
  if(uri!=null) runCatching {
   contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)
   RuntimeArchiveInstaller(this).install(uri,componentId,sha)
   message="Installed $componentId"
  }.onFailure{message="Install failed: ${it.message}"}
 }
 private var componentId by mutableStateOf("toolchain")
 private var sha by mutableStateOf("")
 private var message by mutableStateOf("Choose a trusted ZIP and provide its published SHA-256.")
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState)
  setContent { MaterialTheme { Column(Modifier.fillMaxSize().padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
   Text("APB Runtime Manager",style=MaterialTheme.typography.headlineSmall)
   Text("Install only runtime archives built for this Android ABI. The archive checksum must come from a trusted release manifest.")
   OutlinedTextField(componentId,{componentId=it},label={Text("Component ID")},singleLine=true)
   OutlinedTextField(sha,{sha=it},label={Text("Expected SHA-256")},singleLine=true)
   Button(onClick={if(sha.matches(Regex("[a-fA-F0-9]{64}"))&&componentId.matches(Regex("[A-Za-z0-9._-]+")))pick.launch(arrayOf("application/zip","application/octet-stream"))else message="Enter valid component ID and 64-character SHA-256"}){Text("Select and install ZIP")}
   Text(message)
   Text("Installed files are kept in APB private storage. Runtime components are not bundled with the app.")
   TextButton(onClick={finish()}){Text("Close")}
  }}}
 }
}
