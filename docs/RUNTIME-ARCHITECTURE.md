# Runtime architecture

## Requested storage layout
APB creates its runtime and confidential-file directory outside the APK at:

`/storage/emulated/0/apps/ADB/.t/.h/.i/.s/.i/.s/.p/.r/.i/.v/.a/.t/.e`

The hidden nested folders are intentional and stop at `.e`. Runtime components, build caches and confidential files can be stored there. The APB File Manager has an External Storage tab and an APB Private tab. The latter browses APB's own sandbox at `/data/user/0/com.nuelious.android.package.builder.apb/`.

On Android 11+, managing arbitrary shared-storage paths requires Android's all-files-access special permission. APB provides a button to open the system settings page; the user must grant access.

## Runtime modes
- `apb-native`: run a verified Android-compatible executable directly.
- `apb-linux`: run Linux ARM64 userspace tools through an Android/ARM64 PRoot executable.

## Required runtime components
- Android/ARM64 PRoot executable.
- ARM64 Linux root filesystem.
- Linux ARM64 JDK 17.
- Gradle 8.
- Android SDK/build-tools compiled for a compatible host ABI.
- APKTool JAR.

## Runtime readiness warning
The manual Runtime Manager screen has been removed. APB now points its runtime runner at the requested external folder, but the repository still does **not** contain real, verified ARM64 runtime archives or binaries. The app is therefore not yet a ready-to-use offline compiler. A compatible toolchain still needs to be supplied and tested.

Android SDK host tools are often x86_64-only. A Linux ARM64 rootfs does not translate x86_64/glibc executables into ARM64 binaries. Verify each native tool on the target device.

PRoot is not a security boundary. Imported Gradle scripts are executable code; build only trusted projects. Hidden folder names are not encryption.
