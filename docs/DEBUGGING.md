# Debugging guide

## CI
- Open Actions and inspect the latest APB Android CI run.
- First failure to inspect: Kotlin/Compose compilation, then unit tests, then APK assembly.
- Download the APB-debug artifact only after a successful workflow.

## Importer
Test empty folder, nested Gradle modules, APKTool folder, Smali tree, huge folder, Unicode filenames, provider permission revocation and low storage.

## Configuration
Edit apb.json externally, reopen APB, and confirm it reads the changed file. Test malformed JSON, unsupported schema version, absolute paths and ../ traversal.

## Runtime
Test checksum mismatch, zip-slip archive entry, interrupted install, disk-full, wrong ABI and missing executable. Test fully cached offline build with airplane mode.

## Known limitations
The app shell and project import/configuration path are implemented, but runtime payloads are not shipped. Gradle and APKTool runners need installed compatible tools. Smali/DEX complete APK packaging, Android SDK provisioning, APK signing/alignment, build cancellation and a verified offline toolchain bundle remain unfinished.
