# APB — Android Package Builder

**Application ID:** `com.nuelious.android.package.builder.apb`

APB is a standalone Android app for importing and configuring Android source projects, with the goal of building APKs locally without Termux or root.

## Implemented in this repository
- Kotlin + Jetpack Compose Android app shell.
- Folder selection through Android Storage Access Framework.
- Project type confirmation: Android Studio/Gradle, Java, Kotlin, APKTool, Smali, DEX and Custom.
- Folder scanner for Gradle modules, manifests, APKTool files, resources and Smali directories.
- Private project workspace copy and generated `apb.json`.
- JSON validation and in-app editor; the JSON remains editable externally.
- Build coordinator with Gradle and APKTool command dispatch.
- Runtime manager screen and SHA-256 checked ZIP installer with path traversal protection.
- Runtime tool detection and status scaffolding.
- GitHub Actions CI, JSON checks and unit tests.

## Current limitations — read before testing
This is a debugging-oriented development build, **not yet a complete universal APK compiler**. Runtime archives are not included. A compatible Android ARM64 Java/Gradle/Android SDK toolchain must be assembled and installed. Gradle projects also need all dependencies cached for offline operation. APKTool requires Java and apktool.jar. Smali/DEX complete APK packaging, SDK provisioning, zipalign, APK signing, build cancellation and robust progress reporting are not yet end-to-end implemented.

A Linux userspace does not make desktop x86_64/glibc binaries compatible with Android ARM64/Bionic. Runtime payloads must be built and tested for each supported ABI.

## Build the APB app
Open in Android Studio with JDK 17 and Android SDK 35, or run the GitHub Actions workflow. CI uploads a debug APK artifact after a successful build.

## Configuration
Each imported project gets an editable `apb.json` at its root. See `schemas/apb-schema-v1.json`, `templates/` and `docs/APB-CONFIGURATION.md`.

## Security
Only build projects you own or are authorized to modify. Imported Gradle scripts are executable code. APB JSON is declarative and does not accept arbitrary shell commands. Runtime archives must come from trusted sources and match their published SHA-256.
