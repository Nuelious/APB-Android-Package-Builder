# Runtime architecture

APB is a standalone Android app and does not require Termux or root.

## Runtime modes
- `apb-native`: run a verified Android-compatible executable directly.
- `apb-linux`: run Linux ARM64 userspace tools through an Android/ARM64 PRoot executable.

The PRoot runner binds the selected project as `/workspace`, binds APB's installed runtime as `/apb-runtime`, and exposes Android `/dev`, `/proc`, `/sys` and `/system` to the Linux rootfs. Java, Gradle and SDK paths are configured in the Linux process environment.

## Components
The example runtime manifest describes separate components:
- `proot-arm64`: native Android ARM64 PRoot executable.
- `linux-rootfs-arm64`: ARM64 Linux root filesystem with `/bin` and `/usr`.
- `jdk17-arm64`: Linux ARM64 JDK compatible with the rootfs.
- `gradle-8`: Gradle distribution.
- `android-sdk`: SDK platform/build tools compiled for a compatible host ABI.
- `apktool`: APKTool JAR.

Install each archive using Runtime Manager and a trusted SHA-256. The example manifest deliberately contains placeholder hashes; it is not a downloadable runtime release.

## Critical compatibility warning
A Linux userspace does not translate x86_64/glibc binaries into ARM64 binaries. Every native executable must match the phone ABI and its libc expectations. Android SDK host tools are often distributed only for x86_64; an ARM64-compatible SDK/build-tools set must be sourced or built separately. Do not assume the standard desktop SDK archive works on an ARM64 phone.

PRoot is not a virtual machine or a security boundary. Treat imported build scripts as untrusted code and build only projects you trust.

## Validation gate before calling the runtime complete
On a real ARM64 device:
1. Verify PRoot starts and mounts the rootfs.
2. Run Linux ARM64 `java -version` and `gradle --version`.
3. Install a matching Android SDK platform and build-tools.
4. Build a minimal Android project online, then offline with dependencies cached.
5. Build an APKTool project.
6. Verify APK alignment, signing and signature.
7. Test process cancellation, log capture, disk-full, low-memory and corrupted archive recovery.

Runtime payloads are not bundled with the source repository. The app-side installer and PRoot command runner are implemented, but a tested distributable toolchain bundle remains a separate deliverable.
