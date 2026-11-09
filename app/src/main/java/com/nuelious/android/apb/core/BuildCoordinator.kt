package com.nuelious.android.apb.core

import android.content.Context
import com.nuelious.android.apb.data.ApbConfig
import com.nuelious.android.apb.data.ApbProject
import com.nuelious.android.apb.runtime.LinuxRuntimeExecutor
import com.nuelious.android.apb.runtime.RuntimeBootstrap
import com.nuelious.android.apb.runtime.RuntimeStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class BuildResult(val success: Boolean, val message: String, val output: String? = null)

class BuildCoordinator(context: Context) {
    private val appContext = context.applicationContext
    private val runtimeRoot get() = File(RuntimeStorage.privateRuntime(appContext), "active")

    suspend fun build(project: ApbProject, onLog: (String) -> Unit): BuildResult = withContext(Dispatchers.IO) {
        runCatching {
            check(RuntimeBootstrap(appContext).isInstalled()) { "APB ARM64 runtime is not installed. Complete first-launch runtime setup." }
            val config = ApbConfig.parse(project.configFile.readText()).also { it.validate() }
            val build = config.json.getJSONObject("build")
            val output = File(project.root, config.output)
            check(output.canonicalPath.startsWith(project.root.canonicalPath + File.separator) || output.canonicalFile == project.root.canonicalFile) {
                "Unsafe output directory."
            }
            output.mkdirs()
            val command = when (config.engine) {
                "gradle" -> listOf(
                    "gradle", "--no-daemon",
                    if (build.optBoolean("offline")) "--offline" else "--stacktrace",
                    "-Pandroid.aapt2FromMavenOverride=/usr/bin/aapt2",
                    "-p", "/workspace", build.optString("task", "assembleDebug")
                )
                "apktool", "smali" -> {
                    require(File(project.root, "AndroidManifest.xml").isFile && File(project.root, "apktool.yml").isFile) {
                        "A complete APKTool project needs AndroidManifest.xml, resources and apktool.yml."
                    }
                    listOf("java", "-jar", "/apb-runtime/apktool.jar", "b", "/workspace", "-o", "/workspace/${config.output}/apb-unsigned.apk")
                }
                "dex" -> error("DEX-only input lacks manifest/resources needed for APK packaging.")
                else -> error("Custom engine plugins are not installed; arbitrary commands are disabled.")
            }
            val process = LinuxRuntimeExecutor(appContext, project.root).start(command)
            process.inputStream.bufferedReader().useLines { lines -> lines.forEach(onLog) }
            val code = process.waitFor()
            check(code == 0) { "Build process exited with code $code." }
            val apk = if (config.engine == "gradle") {
                output.walkTopDown().firstOrNull { it.isFile && it.extension.equals("apk", true) }
            } else File(output, "apb-unsigned.apk").takeIf { it.isFile }
            checkNotNull(apk) { "Build completed but no APK was found under ${output.absolutePath}." }
            BuildResult(true, "Build completed. APK: ${apk.absolutePath}", apk.absolutePath)
        }.getOrElse { BuildResult(false, it.message ?: "Build failed.") }
    }
}
