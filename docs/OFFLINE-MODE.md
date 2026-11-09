# Offline mode

Offline builds are possible only after all required runtime components, Android SDK packages, Gradle plugins and project dependencies have been cached.

APB should report:
- **Offline ready:** required runtime, SDK packages and dependencies are present.
- **Offline incomplete:** one or more required components/dependencies are missing.
- **Online preparation:** missing items may be fetched only with user consent.

Runtime archives must be versioned, ABI-labelled, licensed and SHA-256 verified. The runtime installer rejects path traversal and enforces extraction limits. Gradle offline mode must fail clearly when a dependency is absent; it must never silently use the network.

The repository includes the runtime manager and archive installer, but does not include official JDK, Gradle, Android SDK or APKTool runtime archives. Offline builds are not operational until those payloads are assembled and tested for the device ABI.
