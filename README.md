# APB — Android Package Builder

**Application ID:** `com.nuelious.android.package.builder.apb`  
**Repository:** [Nuelious/APB-Android-Package-Builder](https://github.com/Nuelious/APB-Android-Package-Builder)

APB is a standalone Android application for importing, inspecting, configuring and building Android projects locally. The app itself does not require Termux or root.

## Implemented in this repository
- Kotlin + Jetpack Compose Android app.
- Import local folders through Android Storage Access Framework.
- Import public GitHub repositories by URL and branch.
- Choose project type: Android Studio/Gradle, Java, Kotlin, APKTool, Smali, Class2Smali, DEX or Custom.
- Scan Gradle modules, manifests, APKTool metadata, resources and Smali directories.
- Copy source into APB's private build workspace.
- Generate and validate an editable `apb.json`.
- Write `apb.json` into the original selected folder so a file manager can edit it; APB reloads it before building.
- In-app JSON editor.
- Build coordinator for Gradle and APKTool, including complete APKTool/Class2Smali decoded trees.
- Runtime Manager with SHA-256 verified ZIP installation and zip-slip/extraction-size protection.
- Optional PRoot-backed Linux ARM64 userspace runner.
- Export produced APK to a user-selected destination.
- GitHub Actions CI, JSON validation and unit tests.

## Build and runtime status
This is a **debugging-ready development foundation**, not yet a universal APK compiler. The Android app shell and import/configuration flows are implemented. The runtime installer and PRoot runner are implemented, but actual runtime archives are not included.

To build Android Studio projects on-device, install a compatible ARM64 Linux rootfs, Android-compatible PRoot binary, Linux ARM64 JDK, Gradle, Android SDK/build-tools and all project dependencies. Android SDK host tools are often x86_64-only; a compatible ARM64 toolchain must be verified rather than assumed. APKTool needs a compatible Java runtime and apktool.jar.

- Gradle runner: implemented; depends on a compatible runtime and SDK.
- APKTool runner: implemented; depends on Java and apktool.jar.
- Class2Smali: complete APKTool decoded projects with manifest/resources/apktool.yml can use APKTool. Smali-only/DEX-only folders cannot independently produce a complete APK.
- APK signing/zipalign for APKTool outputs: not yet end-to-end.
- Offline mode: possible only after all toolchains and project dependencies are cached.
- Private GitHub repositories: not supported by the current public ZIP importer.

A Linux userspace does not translate x86_64/glibc binaries into ARM64 binaries. Validate each executable against the target device ABI and libc.

## Build APB
Open in Android Studio with JDK 17 and Android SDK 35, or run the GitHub Actions workflow. On success, CI uploads an `APB-debug` APK artifact.

## Configuration
Each imported project gets an editable `apb.json` at its root. It can be edited in APB or externally with a file manager. See `schemas/apb-schema-v1.json`, `templates/` and `docs/APB-CONFIGURATION.md`.

## Security
Only build projects you own or are authorized to modify. Imported Gradle scripts are executable code. APB JSON is declarative and never acts as an arbitrary shell script. Install runtime archives only from trusted sources and verify their SHA-256 hashes.
