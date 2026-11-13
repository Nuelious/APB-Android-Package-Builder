package com.nuelious.android.apb.runtime

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.zip.ZipFile
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.security.MessageDigest

data class RuntimeProgress(
    val message: String,
    val downloaded: Long = 0,
    val total: Long = -1,
    val complete: Boolean = false,
    val percent: Int = if (total > 0) ((downloaded * 100) / total).toInt().coerceIn(0, 100) else 0,
    val currentFile: String = "",
    val details: String = ""
)

/** Installs the ZIP embedded in the APK; no runtime network download is performed on-device. */
class RuntimeBootstrap(private val context: Context) {
    private val root = RuntimeStorage.privateRuntime(context)
    private val active get() = File(root, "active")
    private val assetName = "runtime/apb-runtime-arm64.zip"
    private val metadataName = "runtime/runtime-release.json"

    fun isInstalled(): Boolean =
        File(active, "runtime-ready.json").isFile &&
            File(active, "rootfs/usr/bin/env").isFile &&
            File(active, "jdk/bin/java").isFile &&
            File(active, "gradle/bin/gradle").isFile &&
            File(active, "apktool.jar").isFile

    suspend fun install(onProgress: (RuntimeProgress) -> Unit) = withContext(Dispatchers.IO) {
        check(android.os.Build.SUPPORTED_ABIS.any { it == "arm64-v8a" }) {
            "The APB Linux runtime currently requires a 64-bit ARM Android device."
        }
        val metadata = JSONObject(context.assets.open(metadataName).bufferedReader().use { it.readText() })
        val expected = metadata.getString("sha256").lowercase()
        require(expected.matches(Regex("[a-f0-9]{64}"))) { "Invalid bundled runtime checksum." }

        val archive = File(context.cacheDir, "apb-runtime-arm64.zip.part")
        try {
            onProgress(RuntimeProgress("Copying bundled ARM64 runtime from APK…"))
            context.assets.open(assetName).use { input ->
                FileOutputStream(archive).use { output ->
                    val buffer = ByteArray(256 * 1024)
                    var copied = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        copied += n
                        onProgress(RuntimeProgress("Copying bundled runtime…", copied, -1))
                    }
                    output.fd.sync()
                }
            }
            val actual = sha256(archive)
            require(actual == expected) { "Bundled runtime SHA-256 verification failed." }

            val staging = File(root, ".runtime-staging")
            staging.deleteRecursively()
            check(staging.mkdirs() || staging.isDirectory) { "Cannot create runtime staging directory." }
            try {
                onProgress(RuntimeProgress("Extracting and validating bundled runtime…", archive.length(), archive.length()))
                extractZip(archive, staging)
                listOf("rootfs/usr/bin/env", "jdk/bin/java", "gradle/bin/gradle", "apktool.jar").forEach {
                    check(File(staging, it).isFile) { "Bundled runtime is missing $it" }
                }
                File(staging, "runtime-ready.json").writeText(
                    JSONObject().put("release", metadata.optString("tag"))
                        .put("sha256", actual).put("installed_at", System.currentTimeMillis()).toString()
                )
                val backup = File(root, ".runtime-previous")
                backup.deleteRecursively()
                if (active.exists()) Files.move(active.toPath(), backup.toPath())
                try {
                    Files.move(staging.toPath(), active.toPath())
                } catch (failure: Throwable) {
                    if (backup.exists()) Files.move(backup.toPath(), active.toPath())
                    throw failure
                }
                backup.deleteRecursively()
                onProgress(RuntimeProgress("Bundled ARM64 runtime installed and verified.", archive.length(), archive.length(), true))
            } catch (failure: Throwable) {
                staging.deleteRecursively()
                throw failure
            }
        } finally {
            archive.delete()
        }
    }

    suspend fun installArchive(archive: File, onProgress: (RuntimeProgress) -> Unit) = withContext(Dispatchers.IO) {
        require(archive.isFile && archive.extension.equals("zip", true)) { "Select a ZIP runtime archive." }
        check(android.os.Build.SUPPORTED_ABIS.any { it == "arm64-v8a" }) { "The APB runtime requires a 64-bit ARM Android device." }
        val staging = File(root, ".runtime-selected-staging")
        staging.deleteRecursively()
        check(staging.mkdirs()) { "Cannot create runtime staging directory." }
        try {
            onProgress(RuntimeProgress("Inspecting selected runtime ZIP…"))
            extractZip(archive, staging) { file, done, total ->
                val percent = if (total > 0) ((done * 100) / total).toInt().coerceIn(0, 99) else 0
                onProgress(RuntimeProgress("Extracting selected runtime…", done, total, false, percent, file, "Current file: $file\\nExpanded: $done bytes"))
            }
            listOf("rootfs/usr/bin/env", "jdk/bin/java", "gradle/bin/gradle", "apktool.jar").forEach {
                check(File(staging, it).isFile) { "Selected ZIP is not a complete APB runtime. Missing $it" }
            }
            val digest = sha256(archive)
            File(staging, "runtime-ready.json").writeText(JSONObject().put("release", "user-selected").put("sha256", digest).put("installed_at", System.currentTimeMillis()).toString())
            val backup = File(root, ".runtime-previous")
            backup.deleteRecursively()
            if (active.exists()) Files.move(active.toPath(), backup.toPath())
            try { Files.move(staging.toPath(), active.toPath()) }
            catch (failure: Throwable) { if (backup.exists()) Files.move(backup.toPath(), active.toPath()); throw failure }
            backup.deleteRecursively()
            onProgress(RuntimeProgress("Selected runtime installed and verified.", archive.length(), archive.length(), true, 100, "", "SHA-256: $digest"))
        } catch (failure: Throwable) { staging.deleteRecursively(); throw failure }
    }

    private fun extractZip(archive: File, destination: File, onProgress: ((String, Long, Long) -> Unit)? = null) {
        ZipFile.builder().setFile(archive).get().use { zip ->
            val entries = zip.entries
            val scan = zip.entries
            var totalBytes = 0L
            while (scan.hasMoreElements()) { val item = scan.nextElement(); if (!item.isDirectory && !item.isUnixSymlink && item.size > 0) totalBytes += item.size }
            var count = 0
            var expanded = 0L
            var lastPercent = -1
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                count++
                require(count <= 500_000) { "Runtime ZIP has too many entries." }
                val output = File(destination, entry.name).canonicalFile
                require(output.path.startsWith(destination.canonicalPath + File.separator)) {
                    "Unsafe ZIP path: ${entry.name}"
                }
                val mode = entry.unixMode
                when {
                    entry.isDirectory -> check(output.mkdirs() || output.isDirectory)
                    entry.isUnixSymlink -> {
                        // ZIP symlinks are interpreted relative to the link's parent, never
                        // relative to the process working directory. Normalize both sides to
                        // absolute paths before checking containment (Path.startsWith is
                        // component-aware, unlike a string-prefix check).
                        val target = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                            .trimEnd('\n', '\r')
                        require(target.isNotBlank() && !target.contains('\u0000')) { "Invalid symlink target in ${entry.name}." }
                        val targetPath = Paths.get(target)
                        output.parentFile?.mkdirs()
                        // Absolute links in a Linux rootfs are interpreted inside that
                        // rootfs (the runtime executes in its own root namespace), not as
                        // Android host paths. Translate them to a rootfs-relative location
                        // only for validation; preserve the original absolute link text.
                        val runtimeRoot = destination.canonicalFile.toPath().normalize()
                        val rootFsRoot = File(destination, "rootfs").canonicalFile.toPath().normalize()
                        val resolved = if (targetPath.isAbsolute) {
                            require(entry.name.replace('\\', '/').startsWith("rootfs/")) {
                                "Absolute symlink target is only valid inside rootfs: ${entry.name} -> $target"
                            }
                            rootFsRoot.resolve(targetPath.toString().trimStart('/')).normalize().also {
                                require(it.startsWith(rootFsRoot)) {
                                    "Rootfs symlink escapes rootfs: ${entry.name} -> $target"
                                }
                            }
                        } else {
                            val linkParent = output.parentFile.canonicalFile.toPath().normalize()
                            linkParent.resolve(targetPath).normalize().also {
                                require(it.startsWith(runtimeRoot)) {
                                    "Symlink escapes runtime root: ${entry.name} -> $target"
                                }
                            }
                        }
                        require(resolved.startsWith(runtimeRoot)) {
                            "Symlink escapes runtime root: ${entry.name} -> $target (resolves to $resolved)"
                        }
                        Files.createSymbolicLink(output.toPath(), targetPath)
                    }
                    mode and 0xF000 == 0x8000 || mode == 0 -> {
                        output.parentFile?.mkdirs()
                        zip.getInputStream(entry).use { input ->
                            FileOutputStream(output).use { out ->
                                val buffer = ByteArray(256 * 1024)
                                while (true) {
                                    val n = input.read(buffer)
                                    if (n < 0) break
                                    expanded += n
                                    val percent = if (totalBytes > 0) ((expanded * 100) / totalBytes).toInt().coerceIn(0, 99) else 0
                                    if (percent != lastPercent) { lastPercent = percent; onProgress?.invoke(entry.name, expanded, totalBytes) }
                                    require(expanded <= 12L * 1024 * 1024 * 1024) { "Expanded runtime exceeds 12 GiB." }
                                    out.write(buffer, 0, n)
                                }
                            }
                        }
                        output.setExecutable(mode and 0x49 != 0, false)
                    }
                    else -> error("Unsupported ZIP entry type for ${entry.name}")
                }
            }
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(256 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
