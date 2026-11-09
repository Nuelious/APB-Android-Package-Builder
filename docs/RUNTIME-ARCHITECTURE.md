# Runtime architecture

APB is intended to build from a standalone Android app without Termux or root.

1. Prefer Android-compatible tools where practical.
2. Use an isolated Linux userspace only for tools that require it.
3. Linux userspace does not translate x86_64/glibc binaries into Android ARM64/Bionic binaries. Check ABI, libc, Android API level and device behavior for every component.
4. Keep toolchains outside the base APK; install verified components individually or from an offline archive.
5. Put Gradle, APKTool, Smali, signing and alignment behind modular engine interfaces.

The runtime manager detects ABI/storage, installs and verifies components, reports progress/logs, supports cancellation and tracks licenses. Prefer app-private storage. Never execute binaries supplied by an untrusted project.

A PRoot-style environment is not a VM or sandbox escape. Test process execution, filesystem semantics, signals, symlinks and executable permissions on real ARM64 devices.

Acceptance test: run Java and Gradle, resolve dependencies offline, build a minimal APK, verify/sign/align it, and demonstrate logs, cancellation and repeatable builds.
