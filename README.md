# APB — Android Package Builder

**Application ID:** `com.nuelious.android.package.builder.apb`

APB is a standalone Android application project intended to import, inspect, configure and build Android packages on-device without requiring Termux or root.

> **Status:** foundation/scaffold. This repository contains the Android shell, APB configuration schema/templates, architecture documentation and CI workflow. The embedded on-device build runtime and full import/build engines are not implemented yet.

## Planned capabilities
- Import Android Studio/Gradle projects and Git repositories.
- Import authorized APKTool, Smali and DEX projects.
- Scan a selected folder, let the user confirm the project type, and generate an editable `apb.json`.
- Reload and validate `apb.json` before each build. It can be edited with an external file manager.
- Hybrid runtime: Android-compatible tools first, optional isolated Linux userspace for compatible tools.
- Install runtime components individually or from a verified offline archive.

GitHub is an import source, not a project type. A GitHub repository can contain any supported project format.

## Project types
`android-studio`, `android-java`, `android-kotlin`, `apktool`, `smali`, `dex`, `custom`.

## Runtime caveat
A Linux userspace does not make x86_64/glibc binaries compatible with Android ARM64/Bionic. Each runtime component needs ABI, libc, API-level and device validation. See [runtime architecture](docs/RUNTIME-ARCHITECTURE.md) and [offline mode](docs/OFFLINE-MODE.md).

## Build the APB shell
The shell uses Kotlin, Jetpack Compose, JDK 17 and Android SDK 35. GitHub Actions attempts a debug APK build and uploads it as a workflow artifact. A successful shell build does not imply arbitrary projects can already be built on-device.

## Security
APB configuration is declarative, not a shell script. Never silently execute arbitrary commands from imported projects/configuration. Validate project-relative paths and require explicit user approval for custom steps. Only rebuild applications you own or are authorized to modify.
