package com.nuelious.android.apb.runtime

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths
import java.security.MessageDigest

data class RuntimeProgress(val message: String, val downloaded: Long = 0, val total: Long = -1, val complete: Boolean = false)

class RuntimeBootstrap(private val context: Context) {
    private val root = RuntimeStorage.privateRuntime(context)
    fun isInstalled(): Boolean = File(root, "runtime-ready.json").isFile &&
        File(root, "rootfs/usr").isDirectory && File(root, "jdk/bin/java").isFile &&
        File(root, "gradle/bin/gradle").isFile && File(root, "apktool.jar").isFile

    suspend fun install(onProgress: (RuntimeProgress) -> Unit) = withContext(Dispatchers.IO) {
        check(android.os.Build.SUPPORTED_ABIS.any { it == "arm64-v8a" }) {
            "APB's Linux runtime currently requires a 64-bit ARM Android device."
        }
        onProgress(RuntimeProgress("Checking APB runtime release…"))
        val release = JSONObject(readUrl("https://api.github.com/repos/Nuelious/APB-Android-Package-Builder/releases/latest"))
        val assets = release.getJSONArray("assets")
        var archiveUrl: String? = null
        var checksumUrl: String? = null
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            when (a.getString("name")) {
                "apb-runtime-arm64.tar.xz" -> archiveUrl = a.getString("browser_download_url")
                "apb-runtime-arm64.tar.xz.sha256" -> checksumUrl = a.getString("browser_download_url")
            }
        }
        val url = archiveUrl ?: error("No ARM64 runtime release is published yet. Please try again after APB publishes its runtime.")
        val checksum = checksumUrl ?: error("The runtime release is missing its SHA-256 file.")
        val expected = readUrl(checksum).trim().substringBefore(' ').lowercase()
        require(expected.matches(Regex("[a-f0-9]{64}"))) { "Invalid runtime checksum published by release." }

        val archive = File(context.cacheDir, "apb-runtime-arm64.tar.xz.part")
        download(url, archive, onProgress)
        onProgress(RuntimeProgress("Verifying runtime archive…", archive.length(), archive.length()))
        val actual = sha256(archive)
        require(actual == expected) { "Runtime SHA-256 verification failed. The downloaded file was not installed." }

        val staging = File(root, ".runtime-staging").apply { deleteRecursively(); mkdirs() }
        try {
            onProgress(RuntimeProgress("Extracting ARM64 Linux runtime…", archive.length(), archive.length()))
            extractTarXz(archive, staging)
            val required = listOf("rootfs/usr/bin", "jdk/bin/java", "gradle/bin/gradle", "apktool.jar")
            required.forEach { check(File(staging, it).exists()) { "Runtime archive is incomplete: missing $it" } }
            File(staging, "runtime-ready.json").writeText(
                JSONObject().put("release", release.optString("tag_name"))
                    .put("sha256", actual).put("installed_at", System.currentTimeMillis()).toString()
            )
            val active = File(root, "active")
            active.deleteRecursively()
            check(staging.renameTo(active)) { "Unable to activate runtime in app-private storage." }
            // Runtime is stored under active/ so an interrupted update never damages the previous install.
            onProgress(RuntimeProgress("ARM64 runtime installed.", archive.length(), archive.length(), true))
        } catch (t: Throwable) {
            staging.deleteRecursively()
            throw t
        } finally {
            archive.delete()
        }
    }

    private fun readUrl(address: String): String {
        val c = (URL(address).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20000; readTimeout = 30000
            setRequestProperty("User-Agent", "APB-Android-Package-Builder")
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        try { return c.inputStream.bufferedReader().use { it.readText() } } finally { c.disconnect() }
    }

    private fun download(address: String, target: File, progress: (RuntimeProgress) -> Unit) {
        val c = (URL(address).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20000; readTimeout = 60000
            setRequestProperty("User-Agent", "APB-Android-Package-Builder")
        }
        try {
            val total = c.contentLengthLong
            var count = 0L
            c.inputStream.buffered().use { input ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(128 * 1024)
                    while (true) {
                        val n = input.read(buffer); if (n < 0) break
                        output.write(buffer, 0, n); count += n
                        progress(RuntimeProgress("Downloading ARM64 runtime…", count, total))
                    }
                    output.fd.sync()
                }
            }
        } finally { c.disconnect() }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val b = ByteArray(128 * 1024)
            while (true) { val n = input.read(b); if (n < 0) break; digest.update(b, 0, n) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun extractTarXz(archive: File, destination: File) {
        TarArchiveInputStream(XZCompressorInputStream(BufferedInputStream(archive.inputStream()))).use { tar ->
            var entries = 0
            while (true) {
                val entry = tar.nextTarEntry ?: break
                entries++; require(entries < 500000) { "Runtime archive contains too many entries." }
                val out = File(destination, entry.name).canonicalFile
                require(out.path.startsWith(destination.canonicalPath + File.separator) || out == destination) {
                    "Unsafe path in runtime archive: ${entry.name}"
                }
                when {
                    entry.isDirectory -> check(out.mkdirs() || out.isDirectory)
                    entry.isSymbolicLink -> {
                        out.parentFile?.mkdirs()
                        val link = Paths.get(entry.linkName)
                        require(!link.isAbsolute) { "Absolute symlink in runtime archive." }
                        Files.createSymbolicLink(out.toPath(), link)
                    }
                    entry.isLink -> error("Hard links are not supported in APB runtime archives.")
                    entry.isFile -> {
                        out.parentFile?.mkdirs()
                        FileOutputStream(out).use { output -> tar.copyTo(output) }
                        out.setExecutable(entry.mode and 0b001001001 != 0, false)
                        out.setReadable(entry.mode and 0b100100100 != 0, false)
                        out.setWritable(entry.mode and 0b010010010 != 0, false)
                    }
                }
            }
        }
    }
}
