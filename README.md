# APB — Android Package Builder

**Application ID:** `com.nuelious.android.package.builder.apb`  
**Repository:** [Nuelious/APB-Android-Package-Builder](https://github.com/Nuelious/APB-Android-Package-Builder)

APB is a standalone Android app for importing, configuring and building Android projects locally.

## Features
- Import local project folders through Android Storage Access Framework.
- Import public GitHub repositories.
- Generate and edit `apb.json`.
- Gradle and APKTool build coordination.
- Two-tab file manager: shared external storage and APB's own private app data.
- Copy, cut, paste/move, replace confirmation, rename, delete, create folder and create file.
- Runtime and confidential files path:
  `/storage/emulated/0/apps/ADB/.t/.h/.i/.s/.i/.s/.p/.r/.i/.v/.a/.t/.e`

## File access
On Android 11+, APB requests the all-files-access special permission to manage the requested shared-storage folder. The APB Private tab can access only APB's own app sandbox, not other apps' private data.

## Runtime status
The manual Runtime Manager screen has been removed. The runner now uses the requested external runtime folder, but this repository does **not** include verified ARM64 PRoot, rootfs, JDK, Gradle and Android SDK payloads. The full toolchain is therefore not ready to use yet. In particular, x86_64 Android SDK host tools cannot be assumed to run on ARM64 devices.

## Build
Open in Android Studio with JDK 17 and Android SDK 35, or run the GitHub Actions workflow. CI uploads an APB debug APK when the build succeeds.

## Security
Hidden folder names are not encryption. Use Android device encryption and backups for confidential files. Imported build scripts are executable code; only build projects you trust.
