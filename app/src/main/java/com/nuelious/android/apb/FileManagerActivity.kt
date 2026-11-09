package com.nuelious.android.apb

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.io.File

class FileManagerActivity:ComponentActivity(){
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState)
  setContent { MaterialTheme {
   FileManagerScreen(Environment.getExternalStorageDirectory(),File(applicationInfo.dataDir),{Build.VERSION.SDK_INT<30||Environment.isExternalStorageManager()},{
    if(Build.VERSION.SDK_INT>=30) runCatching{startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,Uri.parse("package:$packageName")))}
     .onFailure{startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))}
   },::finish)
  }}
 }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileManagerScreen(externalRoot:File,privateRoot:File,hasAccess:()->Boolean,requestAccess:()->Unit,onClose:()->Unit){
 var tab by remember{mutableIntStateOf(0)}
 var extPath by remember{mutableStateOf(externalRoot)}
 var privatePath by remember{mutableStateOf(privateRoot)}
 var selected by remember{mutableStateOf<File?>(null)}
 var clipboard by remember{mutableStateOf<File?>(null)}
 var cut by remember{mutableStateOf(false)}
 var status by remember{mutableStateOf("Ready")}
 var showDialog by remember{mutableStateOf(false)}
 var dialogMode by remember{mutableStateOf("folder")}
 var name by remember{mutableStateOf("")}
 var replace by remember{mutableStateOf(false)}
 val root=if(tab==0)externalRoot else privateRoot
 val current=if(tab==0)extPath else privatePath
 fun navigate(f:File){if(tab==0)extPath=f else privatePath=f;selected=null}
 fun withinRoot(f:File):Boolean {val b=root.canonicalFile;val p=f.canonicalFile;return p==b||p.path.startsWith(b.path+File.separator)}
 fun copyTree(src:File,dst:File){
  if(src.isDirectory){check(!dst.canonicalPath.startsWith(src.canonicalPath+File.separator)){"Cannot copy a folder into itself"};check(dst.exists()||dst.mkdirs());src.listFiles()?.forEach{copyTree(it,File(dst,it.name))}}
  else {dst.parentFile?.mkdirs();src.inputStream().use{i->dst.outputStream().use{o->i.copyTo(o)}}}
 }
 Scaffold(topBar={TopAppBar(title={Text("APB File Manager")},navigationIcon={TextButton(onClick=onClose){Text("Close")}})}){padding->
  Column(Modifier.fillMaxSize().padding(padding).padding(12.dp)){
   TabRow(selectedTabIndex=tab){Tab(tab==0,{tab=0;selected=null},{Text("External storage")});Tab(tab==1,{tab=1;selected=null},{Text("APB private")})}
   Text(current.absolutePath,style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(vertical=8.dp))
   if(tab==0&&!hasAccess()){Text("Allow file access to manage /storage/emulated/0.");Button(onClick=requestAccess){Text("Grant file access")}}
   else {
    Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
     OutlinedButton(onClick={current.parentFile?.takeIf{withinRoot(it)}?.let(::navigate)},enabled=current!=root){Text("Up")}
     OutlinedButton(onClick={dialogMode="folder";name="";showDialog=true}){Text("New folder")}
     OutlinedButton(onClick={dialogMode="file";name="";showDialog=true}){Text("New file")}
    }
    selected?.let{f->
     Text("Selected: ${f.name}",modifier=Modifier.padding(vertical=6.dp))
     Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){
      OutlinedButton(onClick={clipboard=f;cut=false;status="Copied to clipboard"}){Text("Copy")}
      OutlinedButton(onClick={clipboard=f;cut=true;status="Ready to move"}){Text("Cut")}
      OutlinedButton(onClick={dialogMode="rename";name=f.name;showDialog=true}){Text("Rename")}
      OutlinedButton(onClick={if(f.deleteRecursively()){selected=null;status="Deleted"}else status="Delete failed"}){Text("Delete")}
     }
    }
    Row(horizontalArrangement=Arrangement.spacedBy(6.dp),modifier=Modifier.padding(vertical=6.dp)){
     Button(enabled=clipboard!=null,onClick={
      val src=clipboard?:return@Button
      runCatching{
       check(withinRoot(current))
       val dst=File(current,src.name)
       check(src.canonicalFile!=dst.canonicalFile)
       check(!src.isDirectory||!dst.canonicalPath.startsWith(src.canonicalPath+File.separator))
       if(dst.exists())replace=true else {copyTree(src,dst);if(cut)check(src.deleteRecursively());status=if(cut)"Moved ${src.name}" else "Copied ${src.name}";clipboard=null;selected=null}
      }.onFailure{status=it.message?: "Paste failed"}
     }){Text(if(cut)"Move here" else "Paste here")}
     OutlinedButton(enabled=clipboard!=null,onClick={clipboard=null;status="Clipboard cleared"}){Text("Clear")}
    }
    LazyColumn(Modifier.weight(1f).fillMaxWidth()){
     val entries=current.listFiles()?.sortedWith(compareBy<File>{!it.isDirectory}.thenBy{it.name.lowercase()}).orEmpty()
     items(entries,key={it.absolutePath}){f->
      Row(Modifier.fillMaxWidth().clickable{if(f.isDirectory)navigate(f)else selected=f}.padding(10.dp),horizontalArrangement=Arrangement.SpaceBetween){
       Column(Modifier.weight(1f)){Text((if(f.isDirectory)"📁 " else "📄 ")+f.name);Text(if(f.isDirectory)"Folder" else sizeText(f.length()),style=MaterialTheme.typography.bodySmall)}
       if(selected==f)Text("Selected")
      };HorizontalDivider()
     }
     if(entries.isEmpty())item{Text("Empty folder")}
    }
   }
   Text(status,style=MaterialTheme.typography.bodySmall)
   Text("Runtime: /storage/emulated/0/apps/ADB/.t/.h/.i/.s/.i/.s/.p/.r/.i/.v/.a/.t/.e",style=MaterialTheme.typography.labelSmall)
  }
 }
 if(showDialog)AlertDialog(onDismissRequest={showDialog=false},title={Text(dialogMode)},text={OutlinedTextField(name,{name=it},label={Text("Name")})},confirmButton={TextButton(onClick={
  runCatching{
   require(name.isNotBlank()&&name!="."&&name!=".."&&!name.contains("/")&&!name.contains("\\"))
   when(dialogMode){
    "rename"->{val src=selected?:error("Select an item");val dst=File(src.parentFile,name);check(!dst.exists()&&src.renameTo(dst));selected=null}
    "file"->check(File(current,name).createNewFile())
    else->check(File(current,name).mkdir())
   };status="Done"
  }.onFailure{status=it.message?: "Operation failed"};showDialog=false
 }){Text("Confirm")},dismissButton={TextButton(onClick={showDialog=false}){Text("Cancel")}})
 if(replace)AlertDialog(onDismissRequest={replace=false},title={Text("Replace existing item?")},text={Text("The destination already contains an item with this name.")},confirmButton={TextButton(onClick={
  runCatching{val src=clipboard?:error("Clipboard empty");val dst=File(current,src.name);check(dst.deleteRecursively());copyTree(src,dst);if(cut)check(src.deleteRecursively());clipboard=null;selected=null;status="Replaced ${dst.name}"}.onFailure{status=it.message?: "Replace failed"};replace=false
 }){Text("Replace")}},dismissButton={TextButton(onClick={replace=false}){Text("Cancel")}})
}
private fun sizeText(n:Long)=when{n<1024->"$n B";n<1048576->"${n/1024} KB";n<1073741824->"${n/1048576} MB";else->"${n/1073741824} GB"}
