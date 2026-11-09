# APB configuration

Each imported project gets a plain-text `apb.json` in its root. APB scans the folder, asks the user to confirm the detected project type, then generates the file. It remains editable with any file manager or text editor.

- `format` is `APB`; `format_version` identifies the schema.
- `project.type` describes project/build format, not import source.
- GitHub is an import source; repositories may contain any supported project type.
- Paths are relative to `source.root`.
- APB reloads and validates the JSON before each build.
- Invalid JSON or unsupported versions stop the build with a useful diagnostic.
- Treat configuration as declarative data, never as an implicit shell script.
- Normalize paths and ensure they remain inside the selected project.

Schema: `schemas/apb-schema-v1.json`. Templates: `templates/`.
