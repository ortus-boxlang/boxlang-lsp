# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

* * *

## [Unreleased]

### Fixed

- Unused imports incorrectly flagging static method call sites.

### Added

- Updated `build.gradle` to use modern Gradle features and dependencies
- Updated to use the BoxLang formatter
- New `AGENTS.md` to support opencode and AI coding assistants
- New Gradle wrapper

### Updates

- Upgraded Guava from 33.5.0-jre to 33.6.0-jre
- Upgraded Spotless from 8.4.0 to 8.6.0
- Upgraded Shadow plugin from 9.4.1 to 9.4.2
- Upgraded Jackson databind from 2.20.0 to 2.21.0
- Upgraded Gradle wrapper from 9.5.0 to 9.5.1
- Upgraded multiple CI action versions (upload-artifact, download-artifact, checkout, etc.)
- Embedded Gradle in CI instead of using setup action

## [1.10.0] - 2026-04-27

### Added

- ColdBox detection with implicit mapping support for workspace resolution
- User-setting mappings for additional workspace path resolution
- Workspace mapping reindexing capability
- Code formatting support via LSP (behind feature flag)
- Detection for functions used as arguments to improve unused-function analysis
- Diagnostic suppression annotation support (e.g., `@bxlint:disable`)
- Bxlint rule completion in `.bxlint` configuration files
- Lint rules for unescaped query parameters
- Lint rules for missing `cfsqltype` attribute
- Query parameter diagnostic fixes
- Configuration system improvements with automatic documentation generation

### Fixed

- Test failures on Windows
- Eager dependency loading for better startup reliability
- Improved initial parse pass for better performance
- CF transpiler skipped during parse for improved performance
- BLIDE-289 bug fix

### Updates

- Renamed Java project
- Bumped BoxLang minimum version
- VSCode mappings combined with workspace mappings

## [1.9.0] - 2026-04-02

### Added

- Go to Type Definition — navigate from variable to its type's class definition
- Go to Implementation — navigate from interface/abstract declarations to concrete implementations
- Workspace Symbols — search symbols across the entire workspace
- Semantic tokens for calls, member access, declarations, and declared properties
- Document Symbols hierarchical improvements (outline view with hierarchy and kind icons)
- Signature help for function calls
- BXM Tag completion with rich Markdown documentation
- Completion context detection framework for intelligent completion triggers
- Member access / dot completion (intelligent completions after `obj.`)
- Import path completion for BoxLang classes (package-style imports)
- Named argument completion in function calls
- Scope-aware variable completion
- Class and type completion with auto-import
- Function completion for both BIFs and UDFs
- Snippet completions for common BoxLang patterns
- Project index with persistent cache and freshness validation
- Hover support for variables, functions, classes, and interfaces
- Go-to-definition for variables, functions, classes, interfaces, and imports
- Find references for variables, functions, classes, and interfaces
- Variable type insights (BLIDE-224)
- Context-aware auto complete for keywords, references, arguments, and imports (BLIDE-213)
- Auto import support on save (BLIDE-270)
- API for querying the project index
- Improved LSP setting documentation (BLIDIE-277)

### Fixed

- Duplicate diagnostics in editor (BLIDE-272)
- Improved interface method diagnostics (BLIDE-271)
- Unreachable code warning for finally blocks after return
- Diagnostic range improvements for invalid extends/implements references
- Memory threshold notification handling improvements
- File lookup improvements for sub-folder completions
- Bxlint config exclusion verification and mapping bug fix
- Case-insensitive method lookup for BoxLang compatibility
- Go-to-definition for inherited methods and cross-file methods
- Go-to-definition for template-level variables in BXM files
- Package-qualified import resolution

### Updates

- Improved project indexing with persistent cache
- Enhanced variable type inference via VariableTypeCollectorVisitor

## [1.5.0] - 2025-10-16

## [1.3.0] - 2025-10-03

## [1.3.0] - 2025-10-03

## [1.2.0] - 2025-09-16

- Change the way background processing works to prevent overwhelming the system
- Add settings for the user to control how background processing happens
- Add additional logging

## [1.1.0] - 2025-09-03

- Add var scoping check for CFML files
- Add diagnostic for unused variables
- Lots of additional tests
- Coverted LSP to be a BoxLang module
- BLIDE-96 Added version info

[unreleased]: https://github.com/ortus-boxlang/boxlang-lsp/compare/v1.10.0...HEAD
[1.10.0]: https://github.com/ortus-boxlang/boxlang-lsp/compare/v1.9.0...v1.10.0
[1.9.0]: https://github.com/ortus-boxlang/boxlang-lsp/compare/v1.6.0...v1.9.0
[1.5.0]: https://github.com/ortus-boxlang/boxlang-lsp/compare/v1.3.0...v1.5.0
[1.3.0]: https://github.com/ortus-boxlang/boxlang-lsp/compare/v1.3.0...v1.3.0
[1.2.0]: https://github.com/ortus-boxlang/boxlang-lsp/compare/v1.1.0...v1.2.0
[1.1.0]: https://github.com/ortus-boxlang/boxlang-lsp/compare/1a1f359e5d1f2e330321218662a950a0a8321cb5...v1.1.0
