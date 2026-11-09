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

class FileManagerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                FileManagerScreen(
                    externalRoot = Environment.getExternalStorageDirectory(),
                    privateRoot = File(applicationInfo.dataDir),
                    hasAccess = { Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager() },
                    requestAccess = {
                        if (Build.VERSION.SDK_INT >= 30) {
                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName"))
                            runCatching { startActivity(intent) }.onFailure {
                                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                            }
                        }
                    },
                    onClose = ::finish
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileManagerScreen(
    externalRoot: File,
    privateRoot: File,
    hasAccess: () -> Boolean,
    requestAccess: () -> Unit,
    onClose: () -> Unit
) {
    var tab by remember { mutableIntStateOf(0) }
    var externalPath by remember { mutableStateOf(externalRoot) }
    var privatePath by remember { mutableStateOf(privateRoot) }
    var selected by remember { mutableStateOf<File?>(null) }
    var clipboard by remember { mutableStateOf<File?>(null) }
    var isCut by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Ready") }
    var dialog by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var replaceTarget by remember { mutableStateOf<File?>(null) }

    val base = if (tab == 0) externalRoot else privateRoot
    val current = if (tab == 0) externalPath else privatePath

    fun navigate(folder: File) {
        if (tab == 0) externalPath = folder else privatePath = folder
        selected = null
    }

    fun withinRoot(file: File): Boolean {
        val rootPath = base.canonicalPath
        val path = file.canonicalPath
        return path == rootPath || path.startsWith(rootPath + File.separator)
    }

    fun copyRecursively(source: File, destination: File) {
        require(!destination.canonicalPath.startsWith(source.canonicalPath + File.separator)) {
            "Cannot copy a folder into itself."
        }
        if (source.isDirectory) {
            check(destination.exists() || destination.mkdirs())
            source.listFiles()?.forEach { child -> copyRecursively(child, File(destination, child.name)) }
        } else {
            destination.parentFile?.mkdirs()
            source.inputStream().use { input ->
                destination.outputStream().use { output -> input.copyTo(output) }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("APB File Manager") },
                navigationIcon = { TextButton(onClick = onClose) { Text("Close") } }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(12.dp)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0; selected = null }, text = { Text("External storage") })
                Tab(selected = tab == 1, onClick = { tab = 1; selected = null }, text = { Text("APB private") })
            }

            Text(current.absolutePath, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))

            if (tab == 0 && !hasAccess()) {
                Text("Grant all-files access to browse shared storage.")
                Button(onClick = requestAccess) { Text("Grant access") }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        enabled = current != base,
                        onClick = { current.parentFile?.takeIf { withinRoot(it) }?.let(::navigate) }
                    ) { Text("Up") }
                    OutlinedButton(onClick = { dialog = "folder"; name = "" }) { Text("New folder") }
                    OutlinedButton(onClick = { dialog = "file"; name = "" }) { Text("New file") }
                }

                selected?.let { file ->
                    Text("Selected: ${file.name}", modifier = Modifier.padding(top = 8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedButton(onClick = { clipboard = file; isCut = false; status = "Copied to clipboard" }) { Text("Copy") }
                        OutlinedButton(onClick = { clipboard = file; isCut = true; status = "Ready to move" }) { Text("Cut") }
                        OutlinedButton(onClick = { dialog = "rename"; name = file.name }) { Text("Rename") }
                        OutlinedButton(onClick = {
                            status = if (file.deleteRecursively()) "Deleted" else "Delete failed"
                            selected = null
                        }) { Text("Delete") }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                    Button(enabled = clipboard != null, onClick = {
                        val source = clipboard
                        if (source != null) runCatching {
                            check(withinRoot(current))
                            val destination = File(current, source.name)
                            require(source.canonicalFile != destination.canonicalFile) { "Source and destination are identical." }
                            require(!source.isDirectory || !destination.canonicalPath.startsWith(source.canonicalPath + File.separator)) {
                                "Cannot paste a folder into itself."
                            }
                            if (destination.exists()) {
                                replaceTarget = destination
                            } else {
                                copyRecursively(source, destination)
                                if (isCut) check(source.deleteRecursively())
                                clipboard = null
                                selected = null
                                status = if (isCut) "Moved ${source.name}" else "Copied ${source.name}"
                            }
                        }.onFailure { status = it.message ?: "Paste failed" }
                    }) { Text(if (isCut) "Move here" else "Paste here") }

                    OutlinedButton(enabled = clipboard != null, onClick = { clipboard = null; status = "Clipboard cleared" }) {
                        Text("Clear")
                    }
                }

                val entries = current.listFiles()
                    ?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
                    .orEmpty()

                LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                    items(entries, key = { it.absolutePath }) { file ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { if (file.isDirectory) navigate(file) else selected = file }
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text((if (file.isDirectory) "📁 " else "📄 ") + file.name)
                                Text(
                                    if (file.isDirectory) "Folder" else formatSize(file.length()),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            if (selected == file) Text("Selected")
                        }
                        HorizontalDivider()
                    }
                    if (entries.isEmpty()) item { Text("Empty folder") }
                }
            }

            Text(status, style = MaterialTheme.typography.bodySmall)
            Text("Confidential folder ends at .e", style = MaterialTheme.typography.labelSmall)
        }
    }

    if (dialog.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { dialog = "" },
            title = { Text(dialog.replaceFirstChar { it.uppercase() }) },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") })
            },
            confirmButton = {
                TextButton(onClick = {
                    runCatching {
                        require(name.isNotBlank() && name !in setOf(".", "..") && !name.contains('/') && !name.contains('\\'))
                        when (dialog) {
                            "rename" -> {
                                val source = selected ?: error("Select an item first.")
                                val destination = File(source.parentFile, name)
                                check(!destination.exists() && source.renameTo(destination))
                                selected = null
                            }
                            "file" -> check(File(current, name).createNewFile())
                            else -> check(File(current, name).mkdir())
                        }
                        status = "Done"
                    }.onFailure { status = it.message ?: "Operation failed" }
                    dialog = ""
                }) { Text("Confirm") }
            },
            dismissButton = { TextButton(onClick = { dialog = "" }) { Text("Cancel") } }
        )
    }

    replaceTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { replaceTarget = null },
            title = { Text("Replace existing item?") },
            text = { Text("The destination already contains ${target.name}. Its contents will be replaced.") },
            confirmButton = {
                TextButton(onClick = {
                    runCatching {
                        val source = clipboard ?: error("Clipboard is empty.")
                        check(target.deleteRecursively())
                        copyRecursively(source, target)
                        if (isCut) check(source.deleteRecursively())
                        clipboard = null
                        selected = null
                        status = "Replaced ${target.name}"
                    }.onFailure { status = it.message ?: "Replace failed" }
                    replaceTarget = null
                }) { Text("Replace") }
            },
            dismissButton = { TextButton(onClick = { replaceTarget = null }) { Text("Cancel") } }
        )
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024L * 1024 -> "${bytes / 1024} KB"
    bytes < 1024L * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
    else -> "${bytes / (1024L * 1024 * 1024)} GB"
}
