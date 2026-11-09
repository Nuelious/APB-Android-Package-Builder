package com.nuelious.android.apb
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nuelious.android.apb.data.ApbConfig
import com.nuelious.android.apb.storage.ProjectStore

class ConfigEditorActivity:ComponentActivity(){
 companion object{const val EXTRA_PROJECT_ID="project_id"}
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState)
  val store=ProjectStore(this)
  val p=store.listProjects().firstOrNull{it.id==intent.getStringExtra(EXTRA_PROJECT_ID)}?:run{finish();return}
  setContent { var text by remember{mutableStateOf(p.configFile.readText())};var status by remember{mutableStateOf("Edit project configuration")}
   MaterialTheme { Column(Modifier.fillMaxSize().padding(16.dp)) {
    Text("apb.json",style=MaterialTheme.typography.headlineSmall)
    OutlinedTextField(text,{text=it},Modifier.weight(1f).fillMaxWidth(),textStyle=MaterialTheme.typography.bodySmall)
    Text(status)
    Row { Button(onClick={runCatching{val c=ApbConfig.parse(text);c.validate();store.saveConfig(p,text);status="Saved and validated"}.onFailure{status="Invalid JSON: ${it.message}"}}){Text("Save")}
     TextButton(onClick={finish()}){Text("Close")} }
   } }
  }
 }
}
