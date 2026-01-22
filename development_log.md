# BoxLang LSP Development Log

## Task 1.8: Hover - User-Defined Functions and Methods (Complete)

**Date:** 2026-01-21

### Summary

Implemented hover support for user-defined functions and methods. When hovering over a function invocation, method call, or function declaration, the LSP displays the function signature and javadoc-style documentation. This includes cross-file support where hovering over a method call on an object resolves the object's type from `new ClassName()` assignments and looks up the method documentation from the project index.

### Changes Made

#### New Files Created

**`FindHoverTargetVisitor.java`** (`src/main/java/ortus/boxlang/lsp/workspace/visitors/`)
- AST visitor that finds the node under the cursor for hover information
- Handles `BoxFunctionInvocation`, `BoxMethodInvocation`, `BoxFunctionDeclaration`, and `BoxIdentifier` nodes
- Prioritizes more specific nodes (function invocations) over containing nodes (function declarations)

**`VariableTypeCollectorVisitor.java`** (`src/main/java/ortus/boxlang/lsp/workspace/visitors/`)
- Collects variable type information from assignments
- Tracks variables assigned via `new ClassName()` expressions
- Enables cross-file method lookup by resolving object types

**`HoverTest.java`** (`src/test/java/ortus/boxlang/lsp/`)
- 9 comprehensive tests for hover functionality:
  - `testHoverOnFunctionInvocation()` - hover on function calls
  - `testHoverOnDocumentedFunctionShowsDocumentation()` - documentation display
  - `testHoverOnSimpleFunctionShowsDescription()` - simple descriptions
  - `testHoverOnUndocumentedFunctionShowsSignatureOnly()` - undocumented functions
  - `testHoverOnFunctionDefinition()` - hover on declarations
  - `testHoverOnNonHoverablePositionReturnsNull()` - edge cases
  - `testHoverShowsContainingClassForMethod()` - class name display
  - `testHoverShowsAccessModifier()` - access modifier display
  - `testCrossFileMethodHover()` - cross-file method lookup

**Test Resource Files**
- `hoverTestClass.bx` - Test class with documented and undocumented functions
- `ClassWithDocFunc.bx` - Class with documented function for cross-file testing
- `ClassThatUsesDocFunc.bx` - Class that uses the documented function

#### Modified Files

**`IndexedMethod.java`**
- Added `documentation` field to store function documentation in the index

**`ProjectIndexVisitor.java`**
- Added `extractDocumentation()` method to extract javadoc-style documentation from function declarations
- Added `cleanDocCommentDescription()` to clean up comment text
- Added `cleanAnnotationValue()` to clean up annotation values
- Documentation now stored during indexing for cross-file lookups

**`BoxLangTextDocumentService.java`**
- Added `hover()` method implementing `textDocument/hover` request
- Returns hover information for the symbol at the cursor position

**`LanguageServer.java`**
- Enabled hover capability with `setHoverProvider(true)`

**`ProjectContextProvider.java`**
- Added `getHoverInfo()` method as the main entry point for hover
- Added `buildHoverForFunction()` to build hover from AST nodes
- Added `buildHoverForIndexedMethod()` to build hover from indexed methods
- Added `buildFunctionSignature()` and `buildSignatureFromIndexedMethod()` for signatures
- Added `formatDocumentationAnnotations()` and `formatIndexedMethodDocumentation()` for documentation formatting
- Added `cleanDocCommentDescription()` and `getAnnotationValue()` helpers
- Added `getClassNameFromUri()` to extract class names from file paths

**`SemanticWarningDiagnosticVisitor.java`**
- Fixed false positive where documentation comments were flagged as unreachable code
- Added `BoxComment` and `BoxDocumentationAnnotation` to the skip list in `checkForSiblingsAfterTerminal()`

### Hover Content Format

The hover displays:
```
```boxlang
(ClassName) accessModifier returnType function functionName(params)
```

Description from javadoc comment.

**Parameters:**
- param1 Description of param1
- param2 Description of param2

**@return** Description of return value

**@deprecated** Deprecation notice
```

### Documentation Tags Supported

- `@param` - Parameter descriptions
- `@return` / `@returns` - Return value description
- `@throws` / `@throw` - Exception descriptions
- `@deprecated` - Deprecation notice
- `@since` - Version information
- `@author` - Author information

### Cross-File Hover Flow

1. User hovers over `obj.methodName()` in File A
2. `VariableTypeCollectorVisitor` finds that `obj` was assigned via `new ClassName()`
3. `ProjectIndex.findMethod("ClassName", "methodName")` looks up the method
4. `buildHoverForIndexedMethod()` builds hover from the indexed method's documentation

### Requirements Met

- ✅ Identify function/method under cursor
- ✅ Look up definition in index
- ✅ Extract documentation comments (javadoc-style)
- ✅ Display signature with type hints
- ✅ Show parameter descriptions
- ✅ Show return type information
- ✅ Cross-file method lookup via variable type tracking

---

## Task 1.6: Expand Diagnostics - Warnings (Complete)

**Date:** 2026-01-21

### Summary

Implemented warning diagnostics for code that is technically valid but potentially problematic. This includes detection of empty catch blocks, unreachable code, shadowed variables, missing return statements, unused private methods, and unused imports. Each warning type has its own configurable rule ID.

### Changes Made

#### New Files Created

**`SemanticWarningDiagnosticVisitor.java`** (`src/main/java/ortus/boxlang/lsp/workspace/visitors/`)
- Extends `SourceCodeVisitor` to integrate with the existing diagnostic infrastructure
- Detects empty catch blocks in try-catch statements
- Detects unreachable code after `return`, `throw`, `break`, `continue` statements
- Detects shadowed variables (local variable with `var` keyword shadows function parameter)
- Detects missing return statements in functions with non-void return type hints
- Detects unused private methods within a class
- Detects unused imports (tracks import names vs identifier/FQN usage)
- Uses `DiagnosticTag.Unnecessary` for unused code warnings

**Individual Rule Files** (`src/main/java/ortus/boxlang/lsp/lint/rules/`)
- `EmptyCatchBlockRule.java` - Rule ID: `emptyCatchBlock`
- `UnreachableCodeRule.java` - Rule ID: `unreachableCode`
- `ShadowedVariableRule.java` - Rule ID: `shadowedVariable`
- `MissingReturnStatementRule.java` - Rule ID: `missingReturnStatement`
- `UnusedPrivateMethodRule.java` - Rule ID: `unusedPrivateMethod`
- `UnusedImportRule.java` - Rule ID: `unusedImport`

**`SemanticWarningDiagnosticsTest.java`** (`src/test/java/ortus/boxlang/lsp/`)
- 16 comprehensive tests covering all warning scenarios
- Tests for empty catch blocks and non-empty catch blocks
- Tests for unreachable code after return, throw, break, continue
- Tests for conditional returns (no false positives)
- Tests for shadowed variables and non-shadowed variables
- Tests for missing return statements and functions with returns
- Tests for void functions (no missing return warning)
- Tests for unused and used private methods
- Tests for unused and used imports

#### Modified Files

**`SourceCodeVisitorService.java`**
- Added import for `SemanticWarningDiagnosticVisitor`
- Registered `SemanticWarningDiagnosticVisitor` in static initializer

**`App.java`**
- Added imports for individual warning rule classes
- Registered all six warning rules in `DiagnosticRuleRegistry`

**`UnusedVariablesTest.java`**
- Updated test to expect 2 diagnostics (unused variable + unreachable code)
- The test file `unusedVariablesTest1.bx` has code after a return statement

### Diagnostics Implemented

| Rule ID | Severity | Message Pattern | Tags |
|---------|----------|-----------------|------|
| `emptyCatchBlock` | Warning | "Empty catch block. Consider logging or handling the exception." | - |
| `unreachableCode` | Warning | "Unreachable code after X statement." | `Unnecessary` |
| `shadowedVariable` | Warning | "Variable 'X' shadows a function parameter." | - |
| `missingReturnStatement` | Warning | "Function 'X' has return type 'Y' but may not return a value." | - |
| `unusedPrivateMethod` | Warning | "Private method 'X' is never called." | `Unnecessary` |
| `unusedImport` | Warning | "Import 'X' is never used." | `Unnecessary` |

### Configuration

Each rule can be configured independently via `.bxlint.json`:

```json
{
  "diagnostics": {
    "emptyCatchBlock": {
      "enabled": true,
      "severity": "warning"
    },
    "unreachableCode": {
      "enabled": true,
      "severity": "warning"
    },
    "shadowedVariable": {
      "enabled": true,
      "severity": "warning"
    },
    "missingReturnStatement": {
      "enabled": true,
      "severity": "warning"
    },
    "unusedPrivateMethod": {
      "enabled": true,
      "severity": "warning"
    },
    "unusedImport": {
      "enabled": true,
      "severity": "warning"
    }
  }
}
```

### Requirements Met

- ✅ Empty catch blocks
- ✅ Unreachable code after `return`, `throw`, `break`, `continue`
- ✅ Shadowed variables (local shadows parameter)
- ✅ Missing return statement when return type hint is present
- ✅ Unused private methods
- ✅ Unused imports

### Design Notes

- **BoxLang syntax**: Return types come BEFORE the `function` keyword (e.g., `string function getName()`)
- **Access modifiers**: BoxLang uses `getAccessModifier()` on `BoxFunctionDeclaration` for `public`/`private` keywords
- **Import tracking**: Tracks both `BoxIdentifier` nodes and `BoxFQN` nodes to detect import usage
- **New expressions**: `new ClassName()` is represented as `BoxNew` containing a `BoxFQN`

### Requirements Deferred

The following requirements were not implemented in this task:

- Unused local variables (already implemented by `UnusedVariableDiagnosticVisitor`)
- Unused function parameters (already implemented by `UnusedVariableDiagnosticVisitor`)
- Deprecated BIF usage (requires BIF metadata)
- Deprecated method usage (requires annotation support)
- Implicit variable creation (requires scope analysis)

---

## Task 1.5: Expand Diagnostics - Semantic Errors (Complete)

**Date:** 2026-01-21

### Summary

Implemented semantic error diagnostics that detect errors which are syntactically valid but semantically incorrect. This leverages the Project Index to validate class references and detect duplicate definitions. Each diagnostic type has its own configurable rule ID.

### Changes Made

#### New Files Created

**`SemanticErrorDiagnosticVisitor.java`** (`src/main/java/ortus/boxlang/lsp/workspace/visitors/`)
- Extends `SourceCodeVisitor` to integrate with the existing diagnostic infrastructure
- Validates `extends` annotations - reports error if parent class/interface not found in index
- Validates `implements` annotations - reports error if interfaces not found in index
- Detects duplicate method definitions within a class (case-insensitive comparison)
- Detects duplicate property definitions within a class (case-insensitive comparison)
- Supports comma-separated lists and array syntax for multiple `implements` values

**Individual Rule Files** (`src/main/java/ortus/boxlang/lsp/lint/rules/`)
- `InvalidExtendsRule.java` - Rule ID: `invalidExtends`
- `InvalidImplementsRule.java` - Rule ID: `invalidImplements`
- `DuplicateMethodRule.java` - Rule ID: `duplicateMethod`
- `DuplicatePropertyRule.java` - Rule ID: `duplicateProperty`

**`SemanticErrorDiagnosticsTest.java`** (`src/test/java/ortus/boxlang/lsp/`)
- 9 comprehensive tests covering all semantic error scenarios:
  - `testInvalidExtendsClassNotFound()` - verifies error for non-existent parent class
  - `testValidExtendsNoError()` - verifies no error when parent exists
  - `testInvalidImplementsInterfaceNotFound()` - verifies error for non-existent interface
  - `testValidImplementsNoError()` - verifies no error when interface exists
  - `testMultipleInvalidImplements()` - verifies errors for multiple missing interfaces
  - `testDuplicateMethodDefinition()` - verifies error for duplicate method names
  - `testNoDuplicateMethodsWithDifferentNames()` - verifies no false positives
  - `testDuplicatePropertyDefinition()` - verifies error for duplicate property names
  - `testNoDuplicatePropertiesWithDifferentNames()` - verifies no false positives

#### Modified Files

**`SourceCodeVisitorService.java`**
- Added import for `SemanticErrorDiagnosticVisitor`
- Registered `SemanticErrorDiagnosticVisitor` in static initializer

**`App.java`**
- Added imports for individual rule classes
- Registered all four rules in `DiagnosticRuleRegistry`

**`ProjectContextProvider.java`**
- Added `setIndex(ProjectIndex)` method for testing purposes

### Diagnostics Implemented

| Rule ID | Severity | Message Pattern |
|---------|----------|-----------------|
| `invalidExtends` | Error | "Class or interface 'X' not found (extends reference)" |
| `invalidImplements` | Error | "Interface 'X' not found (implements reference)" |
| `duplicateMethod` | Error | "Duplicate method definition: 'X' is already defined in this class" |
| `duplicateProperty` | Error | "Duplicate property definition: 'X' is already defined in this class" |

### Configuration

Each rule can be configured independently via `.bxlint.json`:

```json
{
  "diagnostics": {
    "invalidExtends": {
      "enabled": true,
      "severity": "error"
    },
    "invalidImplements": {
      "enabled": true,
      "severity": "error"
    },
    "duplicateMethod": {
      "enabled": true,
      "severity": "error"
    },
    "duplicateProperty": {
      "enabled": true,
      "severity": "error"
    }
  }
}
```

### Requirements Met

- ✅ Duplicate function/method definitions
- ✅ Duplicate property definitions
- ✅ Invalid `extends` (class not found)
- ✅ Invalid `implements` (interface not found)

### Design Decisions

- **No duplicate class detection**: BoxLang classes are defined by their filename, not in the class definition itself, so duplicate class detection was removed.
- **Individual rule IDs**: Each diagnostic type has its own rule ID for granular configuration control.

### Requirements Deferred

The following requirements were not implemented in this initial phase due to complexity with BoxLang's dynamic typing:

- Undefined variable references (prone to false positives)
- Undefined function/method calls (requires call site analysis)
- Wrong number of arguments in function calls (requires function signature tracking)
- Missing required arguments (requires parameter tracking)
- Abstract method not implemented (requires interface method tracking)
- Final method override attempt (requires modifier tracking)

These may be addressed in future tasks or as enhancements to this task.

---

## Task 1.3: Index Query API (Complete)

**Date:** 2026-01-21

### Summary

Implemented a clean API for other LSP features to query the project index. The API provides convenient lookup methods for classes, methods, properties, functions, and files, abstracting away the storage mechanism.

### Changes Made

#### `ProjectIndex.java`
Added 10 new query methods:

- `getAllFunctions()` - Returns all standalone functions (methods with null containingClass, typically from .bxs files)
- `findFunction(String name)` - Find a standalone function by name (case-insensitive)
- `findFunctionsInFile(String filePath)` - Find all standalone functions in a specific file
- `findOverrides(String className, String methodName)` - Find methods in subclasses that override a given method
- `getMethodsOfClass(String className)` - Get all methods belonging to a specific class (case-insensitive)
- `getIndexedFiles()` - Get all indexed file URIs
- `getFilesInDirectory(String directory)` - Get indexed files within a specific directory
- `getFilesDependingOn(String filePath)` - Placeholder for dependency tracking (returns empty list)
- `getDependenciesOf(String filePath)` - Placeholder for dependency tracking (returns empty list)

#### `ProjectIndexTest.java`
Added 17 new tests for the Index Query API:

**Function lookup tests:**
- `testFindFunction()` - verifies standalone function lookup
- `testFindFunctionCaseInsensitive()` - verifies case-insensitive search
- `testFindFunctionDoesNotFindClassMethods()` - ensures class methods are excluded
- `testFindFunctionsInFile()` - verifies file-scoped function lookup
- `testFindFunctionsInFileExcludesClassMethods()` - ensures only standalone functions returned
- `testGetAllFunctions()` - verifies retrieval of all standalone functions

**Override detection tests:**
- `testFindOverrides()` - verifies finding overriding methods in child classes
- `testFindOverridesDeepHierarchy()` - verifies override detection across multiple inheritance levels
- `testFindOverridesNoOverrides()` - verifies empty result when no overrides exist

**Class method tests:**
- `testGetMethodsOfClass()` - verifies class-scoped method retrieval
- `testGetMethodsOfClassCaseInsensitive()` - verifies case-insensitive class lookup

**File listing tests:**
- `testGetIndexedFiles()` - verifies retrieval of all indexed file URIs
- `testGetFilesInDirectory()` - verifies directory-scoped file listing

**Placeholder tests:**
- `testGetFilesDependingOnPlaceholder()` - verifies placeholder returns empty list
- `testGetDependenciesOfPlaceholder()` - verifies placeholder returns empty list

**Edge case tests:**
- `testFindFunctionEmptyInput()` - verifies graceful handling of null/empty input
- `testFindFunctionsInFileEmptyInput()` - verifies graceful handling of null/empty input
- `testFindOverridesEmptyInput()` - verifies graceful handling of null/empty input
- `testGetMethodsOfClassEmptyInput()` - verifies graceful handling of null/empty input
- `testGetFilesInDirectoryEmptyInput()` - verifies graceful handling of null/empty input

### API Summary

The complete Index Query API now includes:

| Method | Purpose |
|--------|---------|
| `findClassByName(String)` | Find class by simple name |
| `findClassByFQN(String)` | Find class by fully qualified name |
| `findAllClassesByName(String)` | Find all classes matching a name |
| `findClassesExtending(String)` | Find classes extending a parent |
| `findClassesImplementing(String)` | Find classes implementing an interface |
| `getAllClasses()` | Get all indexed classes |
| `findMethod(String, String)` | Find method by class and name |
| `findMethodsByName(String)` | Find methods by name |
| `findOverrides(String, String)` | Find method overrides in subclasses |
| `getMethodsOfClass(String)` | Get all methods of a class |
| `getAllMethods()` | Get all indexed methods |
| `findFunction(String)` | Find standalone function by name |
| `findFunctionsInFile(String)` | Find functions in a file |
| `getAllFunctions()` | Get all standalone functions |
| `findProperty(String, String)` | Find property by class and name |
| `findPropertiesOfClass(String)` | Get all properties of a class |
| `getAllProperties()` | Get all indexed properties |
| `searchSymbols(String)` | Search all symbols by query |
| `getIndexedFiles()` | Get all indexed file URIs |
| `getFilesInDirectory(String)` | Get files in a directory |
| `getFilesDependingOn(String)` | Get dependents (placeholder) |
| `getDependenciesOf(String)` | Get dependencies (placeholder) |

### Requirements Met

- ✅ Class lookups by name and FQN
- ✅ Inheritance-based class lookups (extends, implements)
- ✅ Method lookups by name and class
- ✅ Override detection using inheritance graph
- ✅ Property lookups
- ✅ Standalone function lookups (for .bxs files)
- ✅ Symbol search
- ✅ File listing and directory-scoped queries
- ✅ Dependency graph placeholders (ready for future implementation)

---

## Task 1.2: Persistent Index Cache (Complete)

**Date:** 2026-01-21

### Summary

Implemented full cache freshness validation and incremental re-indexing for the project index. The LSP now tracks file modification times and only re-indexes files that have changed since the last indexing, dramatically improving startup time for large projects.

### Changes Made

#### `ProjectIndex.java`
- Added `fileModifiedTimes` map to track when each file was last indexed
- Added `staleFiles` list to track files needing re-indexing after cache load
- Added `cacheCorrupted` flag for corruption handling
- Updated `indexFile()` to record file modification time when indexing
- Updated `removeFile()` to also remove from `fileModifiedTimes`
- Updated `clear()` to reset all tracking state
- Updated `loadCache()` to:
  - Load file modification times from cache
  - Call `validateCacheFreshness()` to identify stale files
  - Handle corruption with `handleCacheCorruption()`
- Updated `saveCache()` to persist `fileModifiedTimes` in cache JSON
- Added `validateCacheFreshness()` to compare cached vs current file modification times
- Added `handleCacheCorruption()` to clear state and mark for full re-index
- Added public methods: `getStaleFiles()`, `isCacheCorrupted()`, `needsReindexing()`

#### `ProjectContextProvider.java`
- Updated `parseWorkspace()` to use incremental re-indexing
- Files are only re-indexed if `index.needsReindexing()` returns true
- This skips files that are already in the cache and haven't changed

#### `ProjectIndexTest.java`
Added 7 new tests for persistent cache functionality:
- `testCachePersistence()` - verifies data survives save/load cycle
- `testCacheFreshnessValidation()` - verifies modified files are detected as stale
- `testCacheHandlesDeletedFiles()` - verifies deleted files are detected
- `testCacheCorruptionHandling()` - verifies corrupt cache triggers full re-index
- `testNeedsReindexingForNewFile()` - verifies new files need indexing
- `testCacheWithMultipleFiles()` - verifies only modified files need re-indexing

### Technical Details

The cache JSON structure now includes:
```json
{
  "fileModifiedTimes": {
    "file:///path/to/file.bx": "2026-01-21T16:00:00Z"
  },
  "classes": [...],
  "methods": [...],
  "properties": [...]
}
```

Cache freshness validation:
1. On load, compares cached modification time vs actual file modification time
2. Files modified after caching are marked stale and removed from in-memory index
3. Deleted files are detected and removed from index
4. `needsReindexing()` returns true for: new files, stale files, or if cache was corrupted

### Requirements Met

- ✅ Serialize index to disk on shutdown or periodically
- ✅ Load cached index on startup
- ✅ Validate cache freshness against file modification times
- ✅ Invalidate stale entries and re-index only those files
- ✅ Handle cache corruption gracefully (fall back to full re-index)
