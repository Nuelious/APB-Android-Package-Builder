# Build engine status

| Project input | Engine | Current status |
|---|---|---|
| Android Studio / Gradle | Gradle process runner | Dispatch implemented; compatible JDK, Gradle, Android SDK and dependencies must be installed |
| APKTool decoded project | APKTool | Dispatch implemented; compatible Java + apktool.jar required |
| Smali directories | Smali + DEX packaging | Not end-to-end yet; DEX assembly alone does not create a complete APK |
| DEX-only | APK packaging pipeline | Not end-to-end; manifest/resources/package metadata required |
| Custom | Reviewed engine plugin | Disabled; project JSON cannot run arbitrary shell commands |

## Build stages still required
1. Provision a tested ARM64 Java runtime and Gradle distribution.
2. Provision Android SDK platform/build-tools packages with license handling.
3. Add APKTool engine integration and dependency verification.
4. Add Smali assembler, DEX packaging and resource/manifest pipeline.
5. Add zipalign, apksigner, signature verification and export destination.
6. Add cancellation, structured logs, timeouts, process cleanup and disk-space checks.
7. Add fully offline dependency/runtime archive preparation.

Do not use desktop x86_64/glibc executables as if they were Android ARM64/Bionic binaries.
