# Project formats

The importer uses Android Storage Access Framework folder selection and copies the selected source into APB's private working directory. The app also writes `apb.json` into the original selected folder through its persisted SAF permission, so the user can edit the configuration with a file manager. Before each build APB reloads the external JSON into its working copy.

Scanner indicators:
- `build.gradle` / `build.gradle.kts` and settings files
- `AndroidManifest.xml`
- `apktool.yml` and `res/`
- `smali`, `smali_classes2`, etc.
- DEX files

APB asks the user to choose/confirm a project type after scanning. The chosen type determines the generated build engine. A detected type is only a suggestion; the user can override it.

Imported Gradle scripts are executable code. Only build projects from sources you trust. Re-import after changing source code because the build workspace is a private copy; edits to the external folder are not automatically mirrored except for `apb.json`.
