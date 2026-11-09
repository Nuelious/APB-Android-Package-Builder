# APB configuration

Every imported project receives a plain-text `apb.json` in its selected source folder. APB copies the source into its private build workspace, scans it, asks the user to choose a project type, and generates the configuration.

## External editing
- APB writes `apb.json` into the original folder through the persisted Android Storage Access Framework permission.
- Edit that file with any file manager or text editor that can access the selected folder.
- APB reloads the external file before listing/building the project.
- The in-app editor saves to both the private build copy and original selected folder.
- If the JSON is malformed, APB reports the project as invalid; correct the file externally.

## Fields
- `format` must be `APB`; `format_version` identifies the schema.
- `project.type` describes the build/source format.
- `source.root` and `build.output` are relative paths confined to the imported project.
- `runtime.environment` is `apb-native` or `apb-linux`.
- `build.engine` selects Gradle, APKTool, Smali, DEX or Custom.

GitHub is an import source, not a compiler type. Public GitHub repositories can be downloaded by URL and branch; the user chooses an empty destination folder and then confirms the project type.

## Security
APB configuration is declarative data, never a shell script. APB rejects absolute paths and parent traversal. Imported Gradle build scripts are still executable code, so only build projects from trusted sources.

Schema: `schemas/apb-schema-v1.json`. Starter configurations: `templates/`.
