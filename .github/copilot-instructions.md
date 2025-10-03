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
- Build without tests: `./gradlang build -x test` - takes 1 second when cached. NEVER CANCEL - Set timeout to 5+ minutes
- Create distribution: `./gradlew shadowJar` - builds fat JAR with all dependencies
- Format code: `./gradlew spotlessApply` - auto-fixes code formatting  
- Check formatting: `./gradlew spotlessCheck` - validates code formatting (CI requirement)

### Development and Debugging
- The LSP server main class: `ortus.boxlang.lsp.App`
- Run LSP debug server: Use BoxLang runtime with module approach (see VSCode launch.json)
- Built JAR location: `build/libs/bx-lsp-{version}-snapshot-all.jar`
- Module structure built to: `build/module/`

## Validation

### Complete Development Workflow  
ALWAYS follow this sequence when making changes:
1. `./gradlew clean` - Clean previous build artifacts
2. `./gradlew spotlessApply` - Fix code formatting 
3. `./gradlew build` - Full build with tests (7-8 seconds)
4. `./gradlew spotlessCheck` - Verify formatting compliance
5. Verify build artifacts: `ls -la build/libs/` and `ls -la build/module/`

### Manual Testing Requirements
- ALWAYS run `./gradlew spotlessCheck` before committing - CI will fail otherwise
- ALWAYS run complete build and test cycle after making changes
- Test LSP functionality requires BoxLang runtime integration (see `.vscode/launch.json` for debug setup)  
- Verify JAR builds correctly: `build/libs/bx-lsp-{version}-snapshot-all.jar` should exist (~4.7MB)
- Verify module structure: `build/module/` should contain ModuleConfig.bx, box.json, libs/ folder

### CI Validation
- CI runs on Ubuntu and Windows with JDK 21
- Pull requests trigger formatting checks and full test suite
- Main branch pushes trigger release builds
- CI commands exactly match local development commands

## Critical Build Information

### Timing and Timeouts
- **NEVER CANCEL**: Build takes 7-8 seconds fresh, 1 second when cached. Set timeout to 5+ minutes minimum
- **NEVER CANCEL**: Tests take 1-3 seconds. Set timeout to 10+ minutes minimum  
- **NEVER CANCEL**: Download dependencies can take 2+ minutes on fresh environment. Set timeout to 10+ minutes minimum
- Gradle daemon startup adds 1-2 seconds to first build

### Dependencies and Network
- BoxLang dependency: `io.boxlang:boxlang:1.5.0` (Maven Central)
- Download task `./gradlew downloadBoxLang` **FAILS** due to network restrictions - this is EXPECTED in many environments
- Build works WITHOUT download task as it uses Maven dependencies as fallback
- LSP4J: `org.eclipse.lsp4j:org.eclipse.lsp4j:0.24.0`

## Common Tasks

### Repository Structure Overview
```
/home/runner/work/boxlang-lsp/boxlang-lsp/
├── build.gradle              # Main build file with all tasks
├── gradle.properties         # Version and JDK settings  
├── src/main/java/            # Java LSP implementation (37 files)
├── src/main/bx/              # BoxLang module configuration
├── src/test/java/            # JUnit tests (4 files, 24 total tests)
├── .github/workflows/        # CI/CD pipeline definitions
├── .vscode/                  # VSCode debug configuration
└── build/                    # Generated build artifacts
    ├── libs/                 # JAR files including shadow JAR
    └── module/               # BoxLang module structure
```

### Key Source Locations
- Main LSP server: `src/main/java/ortus/boxlang/lsp/App.java`
- Language server: `src/main/java/ortus/boxlang/lsp/LanguageServer.java`
- Text document service: `src/main/java/ortus/boxlang/lsp/BoxLangTextDocumentService.java`
- Completion providers: `src/main/java/ortus/boxlang/lsp/workspace/completion/`
- Tests: `src/test/java/ortus/boxlang/lsp/`

### Version Management
- Current version in `gradle.properties`: `version=1.3.0`
- BoxLang version: `boxlangVersion=1.5.0`
- JDK version requirement: `jdkVersion=21`

### Formatting and Code Standards
- Eclipse Java formatter configuration: `.ortus-java-style.xml`
- EditorConfig settings: `.editorconfig` (tabs, 4-space width)
- **MANDATORY**: Always run `./gradlew spotlessApply && ./gradlew spotlessCheck` before committing

### Module Development
- BoxLang module configuration: `src/main/bx/ModuleConfig.bx`
- Module mapping: `bxLSP`
- Main entry point for module execution handles version display and LSP startup

### CI/CD Pipeline
- PR workflow: `.github/workflows/pr.yml` - runs tests and formatting checks
- Release workflow: `.github/workflows/release.yml` - builds, packages, and publishes
- Test matrix: Ubuntu + Windows, JDK 21
- Required checks: formatting (`spotlessCheck`), tests (`test`), build (`build`)

### Common File Extensions
- BoxLang/CFML files: `.bx`, `.bxs`, `.bxm`, `.cfc`, `.cfs`, `.cfm`
- Language detection in: `src/main/java/ortus/boxlang/lsp/LSPTools.java`

## Troubleshooting

### Build Issues
- "Java 17 instead of 21": Verify `JAVA_HOME` and `PATH` settings - must use JDK 21+
- "BoxLang download fails": **EXPECTED** in restricted networks, build will use Maven dependency instead
- "Spotless formatting fails": Run `./gradlew spotlessApply` first to fix formatting
- "No main manifest attribute": Use `java -cp` with main class, not `java -jar`

### Development Setup
- Use VSCode with provided debug configuration (`.vscode/launch.json`)
- LSP testing requires BoxLang runtime environment setup
- Module builds to `build/module/` for BoxLang module system integration

### Performance Notes
- First build downloads all dependencies (Gradle, Maven artifacts)
- Subsequent builds are much faster due to caching
- Gradle daemon improves build times after first use

## Common Development Scenarios

### Adding New Language Features
1. Modify completion providers in `src/main/java/ortus/boxlang/lsp/workspace/completion/`
2. Update rule books in `CompletionProviderRuleBook.java`
3. Add tests in `src/test/java/ortus/boxlang/lsp/`
4. Run validation workflow: `./gradlew clean spotlessApply build spotlessCheck`

### Fixing Build Issues
- **"cannot find symbol" errors**: Check BoxLang dependency version in `gradle.properties`
- **Test failures**: Run `./gradlew test --info` for detailed output
- **Formatting violations**: Run `./gradlew spotlessApply` to auto-fix

### Release Preparation
- Version is managed in `gradle.properties`
- CI automatically handles snapshot builds on development branch
- Main branch triggers stable releases

Always validate your changes by running the complete build and test cycle before committing.