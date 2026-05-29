# BoxLang Language Server Protocol (LSP)

The BoxLang LSP is a Java application that implements Microsoft's Language Server Protocol for providing IDE support for BoxLang/CFML languages. It serves as the backend for the BoxLang VSCode extension and other LSP-compatible editors.

Always reference these instructions first and fallback to search or bash commands only when you encounter unexpected information that does not match the info here.

## Working Effectively

### Prerequisites and Setup

- Install JDK 21+. **CRITICAL**: Project requires JDK 21 minimum as specified in `gradle.properties`
- `sudo apt update && sudo apt install -y openjdk-21-jdk` (Ubuntu/Debian)
- Set `JAVA_HOME=/usr/lib/jvm/temurin-21-jdk-amd64` (or appropriate path)
- Ensure `PATH=$JAVA_HOME/bin:$PATH`

### Build and Test Workflow

- Clean and build: `./gradlew clean build` - takes 7-8 seconds. NEVER CANCEL - Set timeout to 5+ minutes
- Run tests: `./gradlew test` - takes 1 second when cached, up to 3 seconds fresh. NEVER CANCEL - Set timeout to 10+ minutes
- Build without tests: `./gradlew build -x test` - takes 1 second when cached. NEVER CANCEL - Set timeout to 5+ minutes
- Create distribution: `./gradlew shadowJar` - builds fat JAR with all dependencies
- Format code: `./gradlew spotlessApply` - auto-fixes code formatting
- Check formatting: `./gradlew spotlessCheck` - validates code formatting (CI requirement)
- Generate config docs: `./gradlew generateConfigDocs` - generates `docs/config-reference.md` and `docs/config-reference.json`
- Generate lint schema: `./gradlew generateLintSchema` - generates `docs/bxlint.schema.json`
- Bump version (minor): `./gradlew bumpMinorVersion` - also available: `bumpMajorVersion`, `bumpPatchVersion`

### Development and Debugging

- The LSP server main class: `ortus.boxlang.lsp.App`
- CLI entry point: `ortus.boxlang.lsp.CLI` (picocli-based)
- Run LSP debug server: Use BoxLang runtime with module approach (see VSCode launch.json)
- Built JAR location: `build/libs/bx-lsp-{version}-all.jar`
- Module structure built to: `build/module/`
- Test resources project: `src/test/resources/test-bx-project/`

## Validation

### Complete Development Workflow

ALWAYS follow this sequence when making changes:

1. `./gradlew clean` - Clean previous build artifacts
2. `./gradlew spotlessApply` - Fix code formatting
3. `./gradlew generateConfigDocs generateLintSchema` - Regenerate documentation and schema
4. `./gradlew build` - Full build with tests (7-8 seconds)
5. `./gradlew spotlessCheck` - Verify formatting compliance
6. Verify build artifacts: `ls -la build/libs/` and `ls -la build/module/`

### Manual Testing Requirements

- ALWAYS run `./gradlew spotlessCheck` before committing - CI will fail otherwise
- ALWAYS run complete build and test cycle after making changes
- ALWAYS regenerate config docs (`generateConfigDocs`) and lint schema (`generateLintSchema`) when modifying annotated config classes or lint rules
- Test LSP functionality requires BoxLang runtime integration (see `.vscode/launch.json` for debug setup)
- Verify JAR builds correctly: `build/libs/bx-lsp-{version}-all.jar` should exist (~4.7MB)
- Verify module structure: `build/module/` should contain ModuleConfig.bx, box.json, readme.md, changelog.md, libs/ folder

### CI Validation

- CI runs on Ubuntu and Windows with JDK 21
- Pull requests trigger: tests (matrix), formatting checks (`spotlessCheck`), and generated docs verification
- `development` branch pushes trigger: tests, auto-format + commit, build snapshot releases, publish to S3/ForgeBox
- `main`/`master` branch pushes trigger: full release build, tagging, GitHub release, publish to S3/ForgeBox, version bump on development
- CI commands exactly match local development commands

## Critical Build Information

### Timing and Timeouts

- **NEVER CANCEL**: Build takes 7-8 seconds fresh, 1 second when cached. Set timeout to 5+ minutes minimum
- **NEVER CANCEL**: Tests take 1-3 seconds. Set timeout to 10+ minutes minimum
- **NEVER CANCEL**: Download dependencies can take 2+ minutes on fresh environment. Set timeout to 10+ minutes minimum
- Gradle daemon startup adds 1-2 seconds to first build

### Dependencies and Network

- BoxLang dependency: `io.boxlang:boxlang:1.13.0` (loads from local build or test resources)
- Download task `./gradlew downloadBoxLang` downloads BoxLang JAR to `src/test/resources/libs/` for environments without local BoxLang build
- Build works WITHOUT download task if `../boxlang/build/libs/boxlang-{version}.jar` exists locally
- LSP4J: `org.eclipse.lsp4j:org.eclipse.lsp4j:0.24.0`
- JSON parsing: `com.fasterxml.jackson.core:jackson-databind:2.20.0`
- CLI: `info.picocli:picocli:4.7.7`
- Guava: `com.google.guava:guava:33.5.0-jre`

## Common Tasks

### Repository Structure Overview

```
.
├── build.gradle              # Main build file with all tasks
├── gradle.properties         # Version and JDK settings
├── settings.gradle           # Root project name: bx-lsp
├── box.json                  # ForgeBox module descriptor
├── boxlang.json              # BoxLang runtime configuration
├── src/main/java/            # Java LSP implementation (118 files)
├── src/main/bx/              # BoxLang module configuration
├── src/test/java/            # JUnit tests (80 files)
├── src/test/resources/       # Test fixtures, test-bx-project, BoxLang JAR
├── docs/                     # Generated documentation (config-reference, bxlint schema)
├── .github/workflows/        # CI/CD pipeline definitions (4 workflows)
├── .vscode/                  # VSCode debug configuration
└── build/                    # Generated build artifacts
    ├── libs/                 # JAR files including shadow JAR
    ├── module/               # BoxLang module structure
    └── distributions/        # ZIP distributions
```

### Key Source Locations

**Core LSP Server:**

- Main entry point: `src/main/java/ortus/boxlang/lsp/App.java`
- Language server: `src/main/java/ortus/boxlang/lsp/LanguageServer.java`
- Text document service: `src/main/java/ortus/boxlang/lsp/BoxLangTextDocumentService.java`
- Workspace service: `src/main/java/ortus/boxlang/lsp/BoxLangWorkspaceService.java`
- CLI interface: `src/main/java/ortus/boxlang/lsp/CLI.java`
- Utility tools: `src/main/java/ortus/boxlang/lsp/LSPTools.java`
- Memory monitoring: `src/main/java/ortus/boxlang/lsp/MemoryThresholdMonitor.java`
- User settings: `src/main/java/ortus/boxlang/lsp/UserSettings.java`

**Configuration & Documentation (`config/`):**

- Config doc generator: `src/main/java/ortus/boxlang/lsp/config/ConfigDocGenerator.java`
- Lint schema generator: `src/main/java/ortus/boxlang/lsp/config/LintSchemaGenerator.java`
- Bxlint config generator: `src/main/java/ortus/boxlang/lsp/config/BxlintConfigGenerator.java`
- Annotations: `src/main/java/ortus/boxlang/lsp/config/annotation/` (`@ConfigGroup`, `@ConfigSetting`, `@LintRule`)

**Code Formatting (`formatting/`):**

- Capability coordination: `src/main/java/ortus/boxlang/lsp/formatting/FormattingCapabilityCoordinator.java`
- Settings resolution: `src/main/java/ortus/boxlang/lsp/formatting/FormattingSettingsResolver.java`
- Formatter config: `src/main/java/ortus/boxlang/lsp/formatting/FormatterConfigResolver.java`
- PrettyPrint adapter: `src/main/java/ortus/boxlang/lsp/formatting/PrettyPrintRuntimeAdapter.java`

**Linting Framework (`lint/`):**

- Lint config loader: `src/main/java/ortus/boxlang/lsp/lint/LintConfigLoader.java`
- Rule registry: `src/main/java/ortus/boxlang/lsp/lint/DiagnosticRuleRegistry.java`
- Rule settings: `src/main/java/ortus/boxlang/lsp/lint/RuleSettings.java`
- Rule implementations (14 rules): `src/main/java/ortus/boxlang/lsp/lint/rules/`
  - DuplicateMethodRule, DuplicatePropertyRule, EmptyCatchBlockRule, InvalidExtendsRule, InvalidImplementsRule, MissingQueryParamCfsqltypeRule, MissingReturnStatementRule, ShadowedVariableRule, UnescapedQueryParamRule, UnreachableCodeRule, UnscopedVariableRule, UnusedImportRule, UnusedPrivateMethodRule, UnusedVariableRule

**Workspace Features (`workspace/`):**

- Completion providers (22 files): `src/main/java/ortus/boxlang/lsp/workspace/completion/`
- Project index: `src/main/java/ortus/boxlang/lsp/workspace/index/` (ProjectIndex, InheritanceGraph, etc.)
- Code lens: `src/main/java/ortus/boxlang/lsp/workspace/codeLens/`
- Visitors (16 classes): `src/main/java/ortus/boxlang/lsp/workspace/visitors/` - FindDefinitionTargetVisitor, FindHoverTargetVisitor, FindReferenceTargetVisitor, FindSignatureHelpTargetVisitor, various diagnostic visitors, variable resolution, query extraction
- Diagnostic support: `src/main/java/ortus/boxlang/lsp/workspace/DiagnosticReport.java`, `DiagnosticSuppressionFilter.java`
- Symbol provider: `src/main/java/ortus/boxlang/lsp/workspace/SymbolProvider.java`
- Format service: `src/main/java/ortus/boxlang/lsp/workspace/FormatService.java`
- ColdBox detection: `src/main/java/ortus/boxlang/lsp/workspace/ColdBoxDetector.java`
- BLAST tools: `src/main/java/ortus/boxlang/lsp/workspace/BLASTTools.java`
- Document model: `src/main/java/ortus/boxlang/lsp/workspace/DocumentModel.java`

**Tests:**

- Main tests: `src/test/java/ortus/boxlang/lsp/`
- Config tests: `src/test/java/ortus/boxlang/lsp/config/`
- Formatting tests: `src/test/java/ortus/boxlang/lsp/formatting/`
- Index tests: `src/test/java/ortus/boxlang/lsp/index/`
- Project tests: `src/test/java/ortus/boxlang/lsp/project/`

### Version Management

- Current version in `gradle.properties`: `version=1.11.0`
- BoxLang version: `boxlangVersion=1.13.0`
- BoxLang minimum version in `box.json`: `1.13.0`
- JDK version requirement: `jdkVersion=21`

### Formatting and Code Standards

- Eclipse Java formatter configuration: `.ortus-java-style.xml`
- EditorConfig settings: `.editorconfig` (tabs, 4-space width)
- **MANDATORY**: Always run `./gradlew spotlessApply && ./gradlew spotlessCheck` before committing

### Module Development

- BoxLang module configuration: `src/main/bx/ModuleConfig.bx`
- Module mapping: `bxLSP`
- Module descriptor: `box.json` (name: `bx-lsp`, type: `boxlang-modules`)
- Main entry point handles `version` display and LSP startup via `ortus.boxlang.lsp.App`

### CI/CD Pipeline

- `pr.yml`: PRs and branch pushes (excluding main/master/development) run tests + format check + doc verification
- `snapshot.yml`: `development` branch pushes trigger tests, auto-format + commit, build snapshot, publish to S3/ForgeBox
- `release.yml`: `main`/`master` pushes trigger full release, tagging, GitHub release, S3 publish, ForgeBox publish, version bump on development
- `tests.yml`: Reusable test suite workflow. Matrix: Ubuntu + Windows, JDK 21. Runs `downloadBoxLang`, `shadowJar`, `test`, publishes results
- Gradle version in CI: 8.7
- Required checks: formatting (`spotlessCheck`), tests (`test`), build (`shadowJar`), config doc freshness

### Common File Extensions

- BoxLang/CFML files: `.bx`, `.bxs`, `.bxm`, `.cfc`, `.cfs`, `.cfm`
- Language detection in: `src/main/java/ortus/boxlang/lsp/LSPTools.java`
- Lint configuration: `.bxlint` JSON files (schema at `docs/bxlint.schema.json`)

## Troubleshooting

### Build Issues

- "Java 17 instead of 21": Verify `JAVA_HOME` and `PATH` settings - must use JDK 21+
- "BoxLang download fails": Run `./gradlew downloadBoxLang` or build BoxLang locally to `../boxlang/`
- "Module structure missing BoxLang JAR": Ensure BoxLang JAR is in `src/test/resources/libs/` or local build exists at `../boxlang/build/libs/`
- "Spotless formatting fails": Run `./gradlew spotlessApply` first to fix formatting
- "No main manifest attribute": Use `java -cp` with main class, not `java -jar`

### Development Setup

- Use VSCode with provided debug configuration (`.vscode/launch.json`)
- Debug config uses `ortus.boxlang.runtime.BoxRunner` with `module:bx-lsp --debug-server-port 7777`
- Working directory for debug: `src/test/resources/test-bx-project/`
- LSP testing requires BoxLang runtime environment setup
- Module builds to `build/module/` for BoxLang module system integration

### Performance Notes

- First build downloads all dependencies (Gradle, Maven artifacts)
- Subsequent builds are much faster due to caching
- Gradle daemon improves build times after first use

## Common Development Scenarios

### Adding New Language Features

1. Lint rules: Create new class in `src/main/java/ortus/boxlang/lsp/lint/rules/`, register in `DiagnosticRuleRegistry`
2. Completion providers: Create/modify rules in `src/main/java/ortus/boxlang/lsp/workspace/completion/`, update `CompletionProviderRuleBook`
3. Visitors: Add new visitor in `src/main/java/ortus/boxlang/lsp/workspace/visitors/`, wire into `BoxLangTextDocumentService`
4. Configuration: Add `@ConfigSetting` annotations to relevant classes, update `ConfigDocGenerator`
5. Add tests in corresponding test directory: `src/test/java/ortus/boxlang/lsp/`
6. Run validation workflow: `./gradlew clean spotlessApply generateConfigDocs generateLintSchema build spotlessCheck`

### Adding New Config Settings

1. Annotate fields with `@ConfigSetting` and `@ConfigGroup` in source classes
2. Run `./gradlew generateConfigDocs` to regenerate `docs/config-reference.md` and `docs/config-reference.json`
3. Commit the regenerated docs alongside your changes

### Adding New Lint Rules

1. Create rule class in `lint/rules/` extending `DiagnosticRule` and implementing `IRule`
2. Add `@LintRule` annotation with metadata
3. Register in `DiagnosticRuleRegistry` and `LintRuleCatalog`
4. Run `./gradlew generateLintSchema` to update `docs/bxlint.schema.json`
5. Add corresponding test in `src/test/java/`

### Fixing Build Issues

- **"cannot find symbol" errors**: Check BoxLang dependency version in `gradle.properties`
- **Test failures**: Run `./gradlew test --info` for detailed output
- **Formatting violations**: Run `./gradlew spotlessApply` to auto-fix
- **Config docs out of date**: Run `./gradlew generateConfigDocs generateLintSchema`

### Release Preparation

- Version is managed in `gradle.properties` (also reflected in `box.json` minimum version)
- Development branch automatically gets `-snapshot` suffix
- CI automatically handles snapshot builds on `development` branch
- `main` branch triggers stable releases with version tag, GitHub release, and ForgeBox publish
- After release, CI automatically bumps minor version on `development` branch

Always validate your changes by running the complete build and test cycle before committing.
