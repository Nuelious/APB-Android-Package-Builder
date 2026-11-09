# APB ARM64 runtime release

APB runtime releases are built for **Linux ARM64 (AArch64)** and installed into the app-private directory `/data/user/0/com.nuelious.android.package.builder.apb/files/runtime/active`.

The shared-storage confidential folder remains user-facing project storage only. The runtime is not extracted into shared storage because Linux rootfs symlinks and executable files are not reliably supported there.

Each runtime release contains:
- Debian ARM64 root filesystem, including ARM64 Android build utilities and Android platform 35 API stubs
- Eclipse Temurin JDK 17 for Linux ARM64
- Gradle 8.14.4
- APKTool standalone JAR
- SHA-256 checksum for the complete tar.xz archive

PRoot is supplied in the APK's ARM64 native library directory from the pinned Android/ARM64 PRoot release. APB's Gradle build verifies the upstream PRoot archive SHA-256 before packaging it.

## Publishing

Push a version tag such as `runtime-v1` and the runtime workflow builds and publishes `apb-runtime-arm64.tar.xz` plus its checksum to a GitHub Release. The app's first-launch bootstrap downloads the latest release, verifies the archive, extracts it with symlink preservation, and atomically activates it in app-private storage.

The archive contains `rootfs/`, `jdk/`, `gradle/`, and `apktool.jar` at its top level.

## Licensing

The PRoot Android build is based on Termux PRoot and is GPL-3.0 licensed. Debian packages retain their individual licenses. Temurin is GPLv2 with the Classpath Exception; Gradle is Apache-2.0; APKTool is Apache-2.0. Include the corresponding notices and source links in distributions.
