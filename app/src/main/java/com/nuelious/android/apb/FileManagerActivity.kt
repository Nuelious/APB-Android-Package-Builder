package com.nuelious.android.apb

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.zip.ZipFile
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.text.DateFormat
import java.util.Date
import java.util.Locale

class FileManagerActivity : ComponentActivity() {
    private var externalAccess by mutableStateOf(false)

    private fun updateAccess() {
        externalAccess = Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updateAccess()
        setContent {
            MaterialTheme {
                FileManagerScreen(
                    externalRoot = Environment.getExternalStorageDirectory(),
                    // Expose only APB's files/ sandbox, never databases, preferences or app code.
                    privateRoot = filesDir,
                    hasExternalAccess = externalAccess,
                    requestExternalAccess = {
                        if (Build.VERSION.SDK_INT >= 30) {
                            val appIntent = Intent(
                                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                Uri.parse("package:$packageName")
                            )
                            runCatching { startActivity(appIntent) }.onFailure {
                                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                            }
                        }
                    },
                    onClose = ::finish
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateAccess()
    }
}

private enum class SortMode(val label: String) {
    NAME("Name"), SIZE("Size"), MODIFIED("Date modified"), TYPE("Type")
}

private data class FileDetails(
    val name: String,
    val path: String,
    val type: String,
    val size: Long,
    val modified: Long,
    val readable: Boolean,
    val writable: Boolean
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun FileManagerScreen(
    externalRoot: File,
    privateRoot: File,
    hasExternalAccess: Boolean,
    requestExternalAccess: () -> Unit,
    onClose: () -> Unit
) {
    var tab by remember { mutableIntStateOf(0) }
    var externalPath by remember { mutableStateOf(externalRoot) }
    var privatePath by remember { mutableStateOf(privateRoot) }
    val base = if (tab == 0) externalRoot else privateRoot
    val current = if (tab == 0) externalPath else privatePath

    val selected = remember { mutableStateListOf<File>() }
    var clipboard by remember { mutableStateOf<List<File>>(emptyList()) }
    var clipboardIsCut by remember { mutableStateOf(false) }
    var showHidden by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf(SortMode.NAME) }
    var descending by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var actionMenu by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf("") }
    var inputName by remember { mutableStateOf("") }
    var extractArchive by remember { mutableStateOf<File?>(null) }
    var propertyFile by remember { mutableStateOf<File?>(null) }
    var replaceTarget by remember { mutableStateOf<File?>(null) }
    var pendingPaste by remember { mutableStateOf<List<File>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var progressPercent by remember { mutableIntStateOf(0) }
    var progressTitle by remember { mutableStateOf("") }
    var progressFile by remember { mutableStateOf("") }
    var progressKind by remember { mutableStateOf("copy") }
    var progressInfo by remember { mutableStateOf("") }
    var showProgressInfo by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Ready") }
    var refreshKey by remember { mutableIntStateOf(0) }

    fun canonical(file: File) = runCatching { file.canonicalFile }.getOrDefault(file.absoluteFile)
    fun within(root: File, file: File): Boolean {
        val r = canonical(root).path
        val p = canonical(file).path
        return p == r || p.startsWith(r + File.separator)
    }
    // Runtime protection is independent of the active tab so a cross-tab move
    // cannot bypass the read-only guard.
    fun protected(file: File): Boolean =
        within(File(privateRoot, "runtime"), file)

    val currentReadOnly = within(File(privateRoot, "runtime"), current)
    fun navigate(folder: File) {
        if (!within(base, folder) || !folder.isDirectory) return
        if (tab == 0) externalPath = folder else privatePath = folder
        selected.clear()
        search = ""
        refreshKey++
    }

    val accessGranted = tab == 1 || hasExternalAccess
    val entries = remember(current, showHidden, search, sortMode, descending, refreshKey, accessGranted) {
        if (!accessGranted) emptyList() else {
            val found = runCatching { current.listFiles()?.toList().orEmpty() }.getOrDefault(emptyList())
                .filter { it.name != "." && it.name != ".." }
                .filter { showHidden || !it.name.startsWith(".") }
                .filter { search.isBlank() || it.name.contains(search, ignoreCase = true) }
                .filter { within(base, it) }
            val sorted = when (sortMode) {
                SortMode.NAME -> found.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase(Locale.ROOT) })
                SortMode.SIZE -> found.sortedBy { if (it.isDirectory) directorySize(it) else it.length() }
                SortMode.MODIFIED -> found.sortedBy { it.lastModified() }
                SortMode.TYPE -> found.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.extension.lowercase(Locale.ROOT) }.thenBy { it.name.lowercase(Locale.ROOT) })
            }
            if (descending) sorted.reversed() else sorted
        }
    }

    fun runOperation(message: String, operation: suspend () -> String) {
        if (busy) return
        busy = true
        status = message
        // Use the Activity lifecycle scope via the composable's coroutine scope.
    }

    val scope = rememberCoroutineScope()
    fun perform(message: String, operation: suspend () -> String) {
        if (busy) return
        busy = true
        progressPercent = 0
        progressTitle = message
        progressFile = ""
        progressInfo = "Preparing operation…"
        progressKind = when { message.contains("Extract", true) -> "extract"; message.contains("Delet", true) -> "delete"; message.contains("Mov", true) -> "move"; message.contains("Renam", true) -> "rename"; else -> "copy" }
        status = message
        scope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { operation() } }
            status = result.getOrElse { it.message ?: "Operation failed" }
            if (result.isSuccess) { progressPercent = 100; progressInfo = status }
            busy = false
            refreshKey++
        }
    }

    fun copyToClipboard(cut: Boolean) {
        val candidates = selected.toList()
        if (candidates.isEmpty()) return
        if (cut && candidates.any { protected(it) }) {
            status = "APB runtime files are protected from moving."
            return
        }
        clipboard = candidates
        clipboardIsCut = cut
        status = if (cut) "${candidates.size} item(s) ready to move" else "${candidates.size} item(s) copied"
    }

    fun paste(replace: Boolean) {
        val sources = pendingPaste.ifEmpty { clipboard }
        if (sources.isEmpty()) return
        val targets = sources.map { File(current, it.name) }
        if (!replace) {
            val collision = targets.firstOrNull { it.exists() }
            if (collision != null) {
                replaceTarget = collision
                pendingPaste = sources
                return
            }
        }
        perform(if (clipboardIsCut) "Moving…" else "Copying…") {
            sources.forEach { source ->
                require(source.exists()) { "Source no longer exists: ${source.name}" }
                require(!within(File(privateRoot, "runtime"), File(current, source.name))) { "The APB runtime directory is read-only." }
                require(within(if (within(externalRoot, source)) externalRoot else privateRoot, source)) {
                    "Source is outside APB's allowed storage roots."
                }
                val destination = File(current, source.name)
                require(canonical(source) != canonical(destination)) { "Source and destination are identical." }
                require(!source.isDirectory || !within(source, destination)) { "Cannot paste a folder into itself." }
                if (clipboardIsCut) require(!(tab == 1 && protected(source))) { "APB runtime files are protected." }
                FileTransfer.copySafely(source, destination, replace)
                if (clipboardIsCut) {
                    check(FileTransfer.deleteSafely(source)) { "Copied, but could not remove original ${source.name}." }
                }
            }
            clipboard = emptyList()
            pendingPaste = emptyList()
            clipboardIsCut = false
            selected.clear()
            "Transfer completed."
        }
        replaceTarget = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column { Text("APB File Manager"); Text(if (tab == 0) "Shared storage" else "APB app files", style = MaterialTheme.typography.labelSmall) } },
                navigationIcon = { TextButton(onClick = onClose) { Text("Close") } },
                actions = {
                    Box {
                        TextButton(onClick = { sortMenu = true }) { Text("Sort") }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            SortMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text(if (sortMode == mode) "✓ ${mode.label}" else mode.label) },
                                    onClick = { sortMode = mode; sortMenu = false }
                                )
                            }
                            DropdownMenuItem(text = { Text(if (descending) "Ascending" else "Descending") }, onClick = { descending = !descending; sortMenu = false })
                        }
                    }
                    Box {
                        TextButton(onClick = { actionMenu = true }) { Text("More") }
                        DropdownMenu(expanded = actionMenu, onDismissRequest = { actionMenu = false }) {
                            DropdownMenuItem(text = { Text(if (showHidden) "Hide hidden files" else "Show hidden files") }, onClick = { showHidden = !showHidden; actionMenu = false })
                            DropdownMenuItem(text = { Text("Select all") }, onClick = { selected.clear(); selected.addAll(entries); actionMenu = false })
                            DropdownMenuItem(text = { Text("Invert selection") }, onClick = {
                                val old = selected.toSet()
                                selected.clear()
                                selected.addAll(entries.filterNot { it in old })
                                actionMenu = false
                            })
                            DropdownMenuItem(text = { Text("Refresh") }, onClick = { refreshKey++; actionMenu = false })
                        }
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 6.dp)) {
                    if (selected.isNotEmpty()) {
                        Text("${selected.size} selected", style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            TextButton(onClick = { copyToClipboard(false) }) { Text("Copy") }
                            TextButton(onClick = { copyToClipboard(true) }) { Text("Cut") }
                            TextButton(onClick = { dialog = "rename"; inputName = selected.singleOrNull()?.name.orEmpty() }, enabled = selected.size == 1 && !protected(selected.first())) { Text("Rename") }
                            TextButton(onClick = { propertyFile = selected.singleOrNull() }, enabled = selected.size == 1) { Text("Properties") }
                            TextButton(onClick = { selected.singleOrNull()?.let { archive -> extractArchive = archive; inputName = archive.name.removeSuffix(".zip").ifBlank { "extracted" }; dialog = "extract" } }, enabled = selected.size == 1 && selected.singleOrNull()?.extension.equals("zip", true) && !busy && !currentReadOnly) { Text("Extract") }
                            TextButton(onClick = { dialog = "delete" }, enabled = selected.none { protected(it) }) { Text("Delete") }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(if (clipboard.isEmpty()) status else "${clipboard.size} on clipboard · ${if (clipboardIsCut) "Move" else "Copy"}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = { paste(false) }, enabled = clipboard.isNotEmpty() && !busy && !currentReadOnly) { Text("Paste here") }
                        if (clipboard.isNotEmpty()) TextButton(onClick = { clipboard = emptyList(); status = "Clipboard cleared" }) { Text("Clear") }
                    }
                    if (busy) BucketProgressPanel(progressPercent, progressTitle, progressFile, progressInfo, 0, 0, progressKind, onInfo = { showProgressInfo = true })
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0; selected.clear(); search = "" }, text = { Text("External storage") })
                Tab(selected = tab == 1, onClick = { tab = 1; selected.clear(); search = "" }, text = { Text("APB private") })
            }

            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val root = canonical(base)
                val path = canonical(current)
                TextButton(onClick = { navigate(root) }) { Text(if (tab == 0) "Storage" else "APB files") }
                val relative = path.path.removePrefix(root.path).trim(File.separatorChar)
                var accumulated = root
                relative.split(File.separatorChar).filter { it.isNotBlank() }.forEach { segment ->
                    Text("›")
                    accumulated = File(accumulated, segment)
                    val crumb = accumulated
                    TextButton(onClick = { navigate(crumb) }) { Text(segment) }
                }
            }

            if (tab == 0 && !hasExternalAccess) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Storage permission required", style = MaterialTheme.typography.titleMedium)
                        Text("Grant APB all-files access to browse shared storage.")
                        Button(onClick = requestExternalAccess, modifier = Modifier.padding(top = 8.dp)) { Text("Grant access") }
                    }
                }
            } else {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Search this folder") }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                    OutlinedButton(onClick = { current.parentFile?.takeIf { within(base, it) }?.let(::navigate) }, enabled = canonical(current) != canonical(base)) { Text("Up") }
                    OutlinedButton(onClick = { dialog = "folder"; inputName = "" }, enabled = !busy && !currentReadOnly) { Text("New folder") }
                    OutlinedButton(onClick = { dialog = "file"; inputName = "" }, enabled = !busy && !currentReadOnly) { Text("New file") }
                    Text("${entries.size} items", modifier = Modifier.align(Alignment.CenterVertically), style = MaterialTheme.typography.labelSmall)
                }
                if (tab == 1 && within(File(privateRoot, "runtime"), current)) {
                    Text("Protected runtime directory · read-only in this file manager", style = MaterialTheme.typography.labelSmall)
                }
                LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                    items(entries, key = { it.absolutePath }) { file ->
                        val isSelected = selected.contains(file)
                        Row(
                            Modifier.fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        if (selected.isNotEmpty()) {
                                            if (isSelected) selected.remove(file) else selected.add(file)
                                        } else if (file.isDirectory) navigate(file) else selected.add(file)
                                    },
                                    onLongClick = { if (isSelected) selected.remove(file) else selected.add(file) }
                                )
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(if (file.isDirectory) "📁" else fileIcon(file), modifier = Modifier.padding(end = 12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(file.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    if (file.isDirectory) "Folder · ${dateText(file.lastModified())}" else "${formatSize(file.length())} · ${dateText(file.lastModified())}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            if (protected(file)) Text("🔒", modifier = Modifier.padding(horizontal = 5.dp))
                            if (isSelected) Text("✓", modifier = Modifier.padding(horizontal = 8.dp))
                        }
                        HorizontalDivider()
                    }
                    if (entries.isEmpty()) item { Text(if (search.isBlank()) "This folder is empty." else "No matching files.", modifier = Modifier.padding(20.dp)) }
                }
            }
        }
    }

    if (showProgressInfo) AlertDialog(onDismissRequest = { showProgressInfo = false }, title = { Text("Operation details") }, text = { Column { Text("Progress: $progressPercent%"); Text(progressTitle); if (progressFile.isNotBlank()) Text("Current: $progressFile"); Text(progressInfo) } }, confirmButton = { TextButton(onClick = { showProgressInfo = false }) { Text("Close") } })

    if (dialog.isNotBlank()) {
        AlertDialog(
            onDismissRequest = { dialog = "" },
            title = { Text(when (dialog) { "folder" -> "Create folder"; "file" -> "Create file"; "rename" -> "Rename item"; "extract" -> "Extract ZIP"; else -> "Delete selected items?" }) },
            text = {
                if (dialog == "delete") Text("Permanently delete ${selected.size} selected item(s)? This cannot be undone.")
                else if (dialog == "extract") Column { Text("Extract ${extractArchive?.name ?: "archive"} into a new folder in the current directory.") ; OutlinedTextField(value = inputName, onValueChange = { inputName = it }, label = { Text("Destination folder") }, singleLine = true) }
                else OutlinedTextField(
                    value = inputName,
                    onValueChange = { inputName = it },
                    label = { Text("Name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None)
                )
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    when (dialog) {
                        "delete" -> {
                            val targets = selected.toList()
                            perform("Deleting…") {
                                targets.forEach { check(!protected(it)) { "APB runtime files are protected." }; check(FileTransfer.deleteSafely(it)) { "Could not delete ${it.name}" } }
                                selected.clear()
                                "Deleted ${targets.size} item(s)."
                            }
                        }
                        "rename" -> {
                            val source = selected.singleOrNull()
                            val newName = inputName.trim()
                            if (source == null) status = "Select one item to rename."
                            else perform("Renaming…") {
                                FileTransfer.validateName(newName)
                                check(!protected(source)) { "APB runtime files are protected." }
                                val destination = File(source.parentFile, newName)
                                check(!destination.exists()) { "An item with that name already exists." }
                                check(source.renameTo(destination)) { "Rename failed." }
                                selected.clear()
                                "Renamed to $newName"
                            }
                        }
                        "extract" -> {
                            val archive = extractArchive
                            val folderName = inputName.trim()
                            if (archive == null) status = "Select a ZIP archive."
                            else perform("Extracting ${archive.name}…") {
                                FileTransfer.validateName(folderName)
                                check(!protected(archive)) { "APB runtime files cannot be extracted from the file manager." }
                                val destination = File(current, folderName)
                                check(within(base, destination) && !protected(destination)) { "Invalid or protected extraction destination." }
                                check(!destination.exists()) { "Destination already exists: $folderName" }
                                FileTransfer.extractZipSafely(archive, destination)
                                selected.clear()
                                "Extracted ${archive.name} to $folderName."
                            }
                            extractArchive = null
                        }
                        "folder", "file" -> {
                            val newName = inputName.trim()
                            perform("Creating…") {
                                FileTransfer.validateName(newName)
                                val target = File(current, newName)
                                check(!target.exists()) { "An item with that name already exists." }
                                val made = if (dialog == "folder") target.mkdir() else target.createNewFile()
                                check(made) { "Could not create $newName" }
                                "Created $newName"
                            }
                        }
                    }
                    dialog = ""
                }) { Text(if (dialog == "delete") "Delete" else "Confirm") }
            },
            dismissButton = { TextButton(onClick = { dialog = "" }) { Text("Cancel") } }
        )
    }

    replaceTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { replaceTarget = null; pendingPaste = emptyList() },
            title = { Text("Replace existing item?") },
            text = { Text("${target.name} already exists. Replace it after the new copy has been prepared?") },
            confirmButton = {
                TextButton(onClick = { paste(true) }) { Text("Replace") }
            },
            dismissButton = { TextButton(onClick = { replaceTarget = null; pendingPaste = emptyList() }) { Text("Cancel") } }
        )
    }

    propertyFile?.let { file ->
        AlertDialog(
            onDismissRequest = { propertyFile = null },
            title = { Text("Properties") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Name: ${file.name}")
                    Text("Type: ${if (file.isDirectory) "Folder" else file.extension.ifBlank { "File" }}")
                    Text("Location: ${file.parent}")
                    Text("Size: ${formatSize(if (file.isDirectory) directorySize(file) else file.length())}")
                    Text("Modified: ${dateText(file.lastModified())}")
                    Text("Readable: ${file.canRead()} · Writable: ${file.canWrite()}")
                }
            },
            confirmButton = { TextButton(onClick = { propertyFile = null }) { Text("Close") } }
        )
    }
}

private object FileTransfer {
    /** Extracts an ordinary ZIP into a sibling staging folder, rejecting traversal, symlinks and special files. */
    fun extractZipSafely(archive: File, destination: File) {
        require(archive.isFile && archive.extension.equals("zip", true)) { "Select a valid ZIP archive." }
        val parent = destination.parentFile ?: error("Invalid extraction destination.")
        check(parent.isDirectory || parent.mkdirs()) { "Cannot access destination folder." }
        val staging = File(parent, ".apb-extract-${System.nanoTime()}")
        check(staging.mkdirs()) { "Cannot create extraction staging folder." }
        var expanded = 0L
        var count = 0
        try {
            ZipFile.builder().setFile(archive).get().use { zip ->
                val entries = zip.entries
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    count++
                    require(count <= 200_000) { "ZIP contains too many entries." }
                    val name = entry.name.replace('\\', '/')
                    require(name.isNotBlank() && !name.startsWith("/") && !Regex("^[A-Za-z]:").containsMatchIn(name)) { "Unsafe ZIP path: ${entry.name}" }
                    val output = File(staging, name).canonicalFile
                    require(output.path.startsWith(staging.canonicalPath + File.separator)) { "ZIP path traversal blocked: ${entry.name}" }
                    val mode = entry.unixMode
                    require(!entry.isUnixSymlink && (mode == 0 || mode and 0xF000 == 0x8000 || entry.isDirectory)) {
                        "ZIP contains a symbolic link or unsupported special file: ${entry.name}"
                    }
                    if (entry.isDirectory) {
                        check(output.mkdirs() || output.isDirectory) { "Cannot create folder: ${entry.name}" }
                    } else {
                        output.parentFile?.let { check(it.mkdirs() || it.isDirectory) { "Cannot create parent folder." } }
                        zip.getInputStream(entry).use { input ->
                            FileOutputStream(output).use { out ->
                                val buffer = ByteArray(128 * 1024)
                                while (true) {
                                    val n = input.read(buffer)
                                    if (n < 0) break
                                    expanded += n
                                    require(expanded <= 4L * 1024 * 1024 * 1024) { "Expanded ZIP exceeds 4 GiB limit." }
                                    out.write(buffer, 0, n)
                                }
                                out.fd.sync()
                            }
                        }
                        output.setLastModified(entry.time.takeIf { it > 0 } ?: output.lastModified())
                    }
                }
            }
            check(staging.renameTo(destination)) { "Could not finish extraction into destination." }
        } catch (failure: Throwable) {
            deleteSafely(staging)
            throw failure
        }
    }

    fun validateName(name: String) {
        require(name.isNotBlank() && name != "." && name != "..") { "Enter a valid name." }
        require(!name.contains('/') && !name.contains('\\') && name.none { it.code < 32 }) { "Name contains invalid characters." }
    }

    fun deleteSafely(file: File): Boolean {
        if (!file.exists()) return true
        if (Files.isSymbolicLink(file.toPath())) return file.delete()
        if (file.isDirectory) {
            file.listFiles()?.forEach { child ->
                if (!deleteSafely(child)) return false
            }
        }
        return file.delete()
    }

    fun copySafely(source: File, destination: File, replace: Boolean) {
        require(source.exists()) { "Source does not exist." }
        require(!Files.isSymbolicLink(source.toPath())) { "Symbolic links are not copied for safety." }
        require(!source.isDirectory || !isWithin(source.canonicalFile, destination.canonicalFile)) { "Cannot copy a folder into itself." }
        destination.parentFile?.let { check(it.exists() || it.mkdirs()) { "Cannot create destination folder." } }
        val staging = File(destination.parentFile, ".apb-transfer-${System.nanoTime()}")
        val backup = File(destination.parentFile, ".apb-backup-${System.nanoTime()}")
        try {
            copyTree(source, staging)
            if (destination.exists()) {
                require(replace) { "Destination already exists: ${destination.name}" }
                check(destination.renameTo(backup)) { "Could not preserve existing destination." }
            }
            try {
                check(staging.renameTo(destination)) { "Could not activate copied item." }
            } catch (failure: Throwable) {
                if (backup.exists()) backup.renameTo(destination)
                throw failure
            }
            if (backup.exists()) check(deleteSafely(backup)) { "New copy completed, but old backup could not be removed." }
        } finally {
            if (staging.exists()) deleteSafely(staging)
            if (backup.exists() && !destination.exists()) backup.renameTo(destination)
        }
    }

    private fun copyTree(source: File, destination: File) {
        require(!Files.isSymbolicLink(source.toPath())) { "Symbolic link found: ${source.name}" }
        if (source.isDirectory) {
            check(destination.mkdirs() || destination.isDirectory) { "Cannot create folder ${destination.name}" }
            val children = source.listFiles() ?: error("Cannot read folder ${source.name}")
            children.forEach { child -> copyTree(child, File(destination, child.name)) }
        } else {
            FileInputStream(source).use { input ->
                FileOutputStream(destination).use { output ->
                    input.copyTo(output, 256 * 1024)
                    output.fd.sync()
                }
            }
            destination.setLastModified(source.lastModified())
        }
    }

    private fun isWithin(parent: File, child: File): Boolean {
        val p = parent.canonicalPath
        val c = child.canonicalPath
        return c == p || c.startsWith(p + File.separator)
    }
}

private fun fileIcon(file: File): String = when (file.extension.lowercase(Locale.ROOT)) {
    "apk", "apks", "xapk" -> "📦"
    "zip", "7z", "rar", "tar", "gz", "xz" -> "🗜️"
    "jpg", "jpeg", "png", "webp", "gif", "bmp" -> "🖼️"
    "mp4", "mkv", "webm", "avi", "mp3", "wav", "flac" -> "🎵"
    "kt", "java", "smali", "js", "py", "xml", "json", "gradle" -> "🧩"
    "pdf" -> "📕"
    else -> "📄"
}

private fun dateText(time: Long): String =
    if (time <= 0) "Unknown date" else DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(time))

private fun directorySize(file: File): Long {
    if (!file.isDirectory) return file.length()
    return file.listFiles()?.sumOf { child ->
        if (Files.isSymbolicLink(child.toPath())) 0L else directorySize(child)
    } ?: 0L
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024L * 1024 -> String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024))
    else -> String.format(Locale.ROOT, "%.2f GB", bytes / (1024.0 * 1024 * 1024))
}
