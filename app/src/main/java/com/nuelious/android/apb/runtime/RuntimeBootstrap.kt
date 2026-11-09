package com.nuelious.android.apb.runtime

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.Paths
import java.security.MessageDigest

data class RuntimeProgress(
    val message: String,
    val downloaded: Long = 0,
    val total: Long = -1,
    val complete: Boolean = false
)

class RuntimeBootstrap(private val context: Context) {
    private val root = RuntimeStorage.privateRuntime(context)
    private val active get() = File(root, "active")

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

        onProgress(RuntimeProgress("Checking the latest APB ARM64 runtime release…"))
        val release = JSONObject(readUrl(RELEASES_LATEST))
        check(!release.optBoolean("draft") && !release.optBoolean("prerelease")) {
            "The latest APB runtime release is not publicly installable."
        }

        val assets = release.getJSONArray("assets")
        val assetByName = (0 until assets.length()).associate { index ->
            val asset = assets.getJSONObject(index)
            asset.getString("name") to asset
        }

        // Accept both archive formats already published by APB. Pair each archive
        // with its matching checksum; never mix an archive and another release's hash.
        val formats = listOf(
            "apb-runtime-arm64.tar.gz" to "gzip",
            "apb-runtime-arm64.tar.xz" to "xz"
        )
        val selected = formats.firstNotNullOfOrNull { (name, format) ->
            val archiveAsset = assetByName[name]
            val checksumAsset = assetByName["$name.sha256"]
            if (archiveAsset != null && checksumAsset != null) {
                Triple(archiveAsset.getString("browser_download_url"),
                    checksumAsset.getString("browser_download_url"), format)
            } else null
        } ?: error("Release ${release.optString("tag_name")} has no supported ARM64 archive and matching SHA-256 file.")

        val (archiveUrl, checksumUrl, format) = selected
        val expected = readUrl(checksumUrl).trim().split(Regex("\\s+")).firstOrNull()?.lowercase()
            ?: error("Runtime checksum file is empty.")
        require(expected.matches(Regex("[a-f0-9]{64}"))) { "Invalid runtime checksum file." }

        val archiveName = if (format == "xz") "apb-runtime-arm64.tar.xz" else "apb-runtime-arm64.tar.gz"
        val archive = File(context.cacheDir, "$archiveName.part")
        try {
            download(archiveUrl, archive, onProgress)
            val actual = sha256(archive)
            require(actual == expected) {
                "Runtime SHA-256 verification failed; nothing was installed."
            }

            val staging = File(root, ".runtime-staging").apply {
                deleteRecursively()
                check(mkdirs() || isDirectory) { "Cannot create runtime staging directory." }
            }
            try {
                onProgress(RuntimeProgress("Extracting verified ARM64 runtime…", archive.length(), archive.length()))
                extract(archive, staging, format)
                listOf("rootfs/usr/bin/env", "jdk/bin/java", "gradle/bin/gradle", "apktool.jar").forEach {
                    check(File(staging, it).isFile) { "Runtime archive is missing $it" }
                }

                File(staging, "runtime-ready.json").writeText(
                    JSONObject()
                        .put("release", release.optString("tag_name"))
                        .put("archive", archiveName)
                        .put("sha256", actual)
                        .put("installed_at", System.currentTimeMillis())
                        .toString()
                )

                val backup = File(root, ".runtime-previous")
                backup.deleteRecursively()
                if (active.exists()) {
                    Files.move(active.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
                try {
                    try {
                        Files.move(staging.toPath(), active.toPath(), StandardCopyOption.ATOMIC_MOVE)
                    } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                        Files.move(staging.toPath(), active.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    }
                } catch (failure: Throwable) {
                    if (backup.exists()) {
                        Files.move(backup.toPath(), active.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    }
                    throw failure
                }
                backup.deleteRecursively()
                onProgress(RuntimeProgress("ARM64 runtime installed and verified.", archive.length(), archive.length(), true))
            } catch (failure: Throwable) {
                staging.deleteRecursively()
                throw failure
            }
        } finally {
            archive.delete()
        }
    }

    private fun readUrl(address: String): String {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "APB-Android-Package-Builder")
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        try {
            check(connection.responseCode in 200..299) {
                "GitHub request failed: HTTP ${connection.responseCode}"
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun download(address: String, target: File, progress: (RuntimeProgress) -> Unit) {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 120_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "APB-Android-Package-Builder")
        }
        try {
            check(connection.responseCode in 200..299) {
                "Runtime download failed: HTTP ${connection.responseCode}"
            }
            val total = connection.contentLengthLong
            var count = 0L
            connection.inputStream.buffered().use { input ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(256 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        count += read
                        progress(RuntimeProgress("Downloading ARM64 runtime…", count, total))
                    }
                    output.fd.sync()
                }
            }
            if (total > 0) require(target.length() == total) { "Runtime download was incomplete." }
        } finally {
            connection.disconnect()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(256 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun extract(archive: File, destination: File, format: String) {
        val raw = BufferedInputStream(archive.inputStream())
        val decompressed = when (format) {
            "gzip" -> GzipCompressorInputStream(raw)
            "xz" -> XZCompressorInputStream(raw)
            else -> error("Unsupported runtime archive format: $format")
        }
        TarArchiveInputStream(decompressed).use { tar ->
            var count = 0
            while (true) {
                val entry = tar.nextTarEntry ?: break
                count++
                require(count <= 500_000) { "Runtime archive has too many entries." }
                val output = File(destination, entry.name).canonicalFile
                require(output.path.startsWith(destination.canonicalPath + File.separator) || output == destination) {
                    "Unsafe archive path: ${entry.name}"
                }
                when {
                    entry.isDirectory -> check(output.mkdirs() || output.isDirectory)
                    entry.isSymbolicLink -> {
                        output.parentFile?.mkdirs()
                        Files.createSymbolicLink(output.toPath(), Paths.get(entry.linkName))
                    }
                    entry.isLink -> error("Runtime archive must be created with hard links dereferenced.")
                    entry.isFile -> {
                        output.parentFile?.mkdirs()
                        FileOutputStream(output).use { tar.copyTo(it) }
                        output.setExecutable(entry.mode and 73 != 0, false)
                        output.setReadable(true, false)
                        output.setWritable(true, true)
                    }
                }
            }
        }
    }

    private companion object {
        const val RELEASES_LATEST =
            "https://api.github.com/repos/Nuelious/APB-Android-Package-Builder/releases/latest"
    }
}
