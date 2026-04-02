# BoxLang LSP Configuration Reference

The BoxLang LSP is controlled through three separate configuration systems: **IDE Workspace Settings** (`boxlang.lsp.*`), **Lint Configuration** (`.bxlint.json`), and **Project Mappings** (`boxlang.json`). This document lists all available settings for each system.

## IDE Workspace Settings

**Config file:** `boxlang.lsp.*`

Configured in the IDE (VS Code: settings.json). These control LSP behavior and performance.

| Key | Type | Default | Since | Description |
| --- | ---- | ------- | ----- | ----------- |
| `enableBackgroundParsing` | boolean | `false` | 1.0.0 | When true, triggers a workspace-wide parse and index of all BoxLang files on startup and when this setting changes. Improves symbol discovery at the cost of startup time. |
| `processDiagnosticsInParallel` | boolean | `true` | 1.0.0 | When true, lint diagnostics for open documents are calculated in parallel threads. Disable if you experience threading issues. |

## Lint Configuration

**Config file:** `.bxlint.json`

Place .bxlint.json at the workspace root to control static analysis. Changes are detected and applied live without reloading the editor.

| Key | Type | Default | Since | Description |
| --- | ---- | ------- | ----- | ----------- |
| `diagnostics` | object{} | `{}` |  | Map of rule ID to rule settings. Keys are rule IDs (see Lint Rules section). Each value is an object with optional 'enabled' (boolean) and 'severity' (string) fields. |
| `include` | string[] | `[]` |  | Workspace-relative glob patterns. When non-empty, only matching files are analyzed. Supports * (segment), ** (recursive), ? (single char). Always use forward slashes. |
| `exclude` | string[] | `[]` |  | Workspace-relative glob patterns. Files matching any exclude pattern are never analyzed, even if they match an include pattern. Evaluated after include. |

## Project Mappings

**Config file:** `boxlang.json`

Place boxlang.json at the workspace root (or any ancestor directory) to define virtual paths, classpaths, and module directories. Supports // line comments.

| Key | Type | Default | Since | Description |
| --- | ---- | ------- | ----- | ----------- |
| `mappings` | object{} | `{}` |  | Map of virtual path prefix (e.g. "/models") to absolute or relative filesystem path. Supports ${user-dir}, ${boxlang-home}, and ${env.VAR:default} variable expansion. |
| `classPaths` | string[] | `[]` |  | List of directories to include in the classpath for type resolution. Paths may be absolute or relative to the boxlang.json file. |
| `modulesDirectory` | string[] | `["boxlang_modules"]` |  | List of directories containing BoxLang modules. Defaults to boxlang_modules/ relative to boxlang.json. Paths may be absolute or relative. |

## Lint Rules

| Rule ID | Default Severity | Since | Description |
| ------- | ---------------- | ----- | ----------- |
| `unusedVariable` | hint |  | Flags local variables that are declared but never used in the code. |
| `unscopedVariable` | warning |  | Flags variables that are used without an explicit scope prefix (e.g. variables.foo instead of foo). |
| `duplicateMethod` | error |  | Flags multiple method definitions with the same name within the same class. |
| `duplicateProperty` | error |  | Flags multiple property definitions with the same name within the same class. |
| `emptyCatchBlock` | warning |  | Flags catch blocks that contain no executable code, which silently swallows exceptions. |
| `invalidExtends` | error |  | Flags extends references to classes or interfaces that cannot be resolved. |
| `invalidImplements` | error |  | Flags implements references to interfaces that cannot be resolved. |
| `missingReturnStatement` | warning |  | Flags functions with a non-void return type that lack a return statement in all code paths. |
| `shadowedVariable` | warning |  | Flags local variables that share the same name as a function parameter, shadowing it. |
| `unreachableCode` | warning |  | Flags code appearing after control-flow statements like return, throw, or break that can never be executed. |
| `unusedImport` | warning |  | Flags import statements for classes or packages that are never referenced in the file. |
| `unusedPrivateMethod` | warning |  | Flags private methods that are never called within the class, indicating dead code. |

### Rule settings

Every rule supports the following fields in `.bxlint.json` under the `diagnostics` key:

- `enabled` (boolean) — set to `false` to disable the rule entirely.
- `severity` (string) — override the default severity: `"error"`, `"warning"`, `"information"`, or `"hint"`.
- `params` (object) — rule-specific parameters (if supported by the rule).
