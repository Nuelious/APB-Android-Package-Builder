# Offline mode

Offline builds work only after all required runtime components, SDK packages and project dependencies have been cached.

APB reports one of three states:
- **Offline-ready:** all required components and dependencies are present.
- **Offline incomplete:** required components or dependencies are missing.
- **Online preparation:** missing items can be fetched with user awareness and consent.

Offline runtime archives need component IDs, versions, ABI support, hashes, licenses and installation layout. Verify integrity before extraction. Gradle offline mode is enabled only after dependencies are cached; never silently switch an offline build to network access.
