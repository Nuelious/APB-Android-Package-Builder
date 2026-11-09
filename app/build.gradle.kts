import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipFile

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val generatedProot = layout.buildDirectory.dir("generated/proot")
val prepareArm64Proot by tasks.registering {
    val output = generatedProot
    outputs.dir(output)
    doLast {
        val root = output.get().asFile
        val zip = root.resolve("proot-aarch64.zip")
        root.mkdirs()
        val url = "https://github.com/ahmed-alnassif/proot/releases/download/v26.10.08-a179d3e/proot-aarch64.zip"
        val connection = URL(url).openConnection()
        connection.setRequestProperty("User-Agent", "APB-Android-Package-Builder")
        connection.getInputStream().use { input -> zip.outputStream().use { input.copyTo(it) } }
        val digest = MessageDigest.getInstance("SHA-256").digest(zip.readBytes()).joinToString("") { "%02x".format(it) }
        check(digest == "d621d71fb68bf07accbada37ffc4208cf4a963eed70916ee68f848cbd9555cc3") {
            "PRoot archive checksum mismatch"
        }
        val nativeDir = root.resolve("jni/arm64-v8a").apply { mkdirs() }
        
        ZipFile(zip).use { archive ->
            val binary = archive.getEntry("proot") ?: error("PRoot archive has no proot executable")
            archive.getInputStream(binary).use { it.copyTo(nativeDir.resolve("libproot.so").outputStream()) }
            val entries = archive.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (!entry.isDirectory && entry.name == "loader/loader") {
                    archive.getInputStream(entry).use { it.copyTo(nativeDir.resolve("libproot-loader.so").outputStream()) }
                }
            }
        }
        zip.delete()
    }
}

android {
    namespace = "com.nuelious.android.apb"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.nuelious.android.package.builder.apb"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "0.3.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    sourceSets["main"].jniLibs.srcDir(generatedProot.map { it.dir("jni") })
    tasks.named("preBuild").configure { dependsOn(prepareArm64Proot) }
    buildFeatures { compose = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.apache.commons:commons-compress:1.27.1")
    implementation("org.tukaani:xz:1.10")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
