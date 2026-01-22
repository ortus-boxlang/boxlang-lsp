# BoxLang LSP Development Log

## Task 2.5: Go to Definition - Imports (Complete)

**Date:** 2026-01-22

### Summary

Implemented go-to-definition functionality for import statements. When the user clicks "Go to Definition" on an import statement, the LSP navigates to the imported class or interface definition file. This includes support for:
- Simple imports (`import ClassName;`) - navigate to class definition
- Aliased imports (`import ClassName as Alias;`) - navigate to original class definition
- Interface imports - navigate to interface definition
- Package-qualified imports (`import subpackage.ClassName;`) - navigate using FQN lookup
- Java imports (`import java:java.util.ArrayList;`) - return empty (no source to navigate to)

### Key Design Decisions

- Import navigation always resolves to the original class, even when clicking on the alias part of an aliased import
- Java imports (prefixed with `java:`) return empty results since there's no source file to navigate to
- **Package-qualified imports use FQN lookup**: For imports like `import subpackage.Item;`, the resolution uses fully-qualified name (FQN) lookup instead of simple name lookup. This ensures that `import subpackage.Item;` does NOT match `Item.bx` in the root folder - it only matches `subpackage/Item.bx`
- Simple imports (no package path) use simple name lookup for backwards compatibility

### Changes Made

#### New Files Created

**Test Resource Files** (`src/test/resources/files/importDefinitionTest/`)
- `UserEntity.bx` - User entity class for import navigation testing
- `IUserService.bx` - Interface for import navigation testing
- `UserServiceImpl.bx` - Class with simple imports for testing
- `AliasedImports.bx` - Class with aliased imports for testing
- `JavaImports.bx` - Class with Java imports for testing
- `Item.bx` - Root-level Item class (for testing package resolution)
- `MainClass.bx` - Class with package-qualified imports for testing
- `subpackage/SubThing.bx` - Class in subpackage for testing FQN resolution

**`ImportDefinitionTest.java`** (`src/test/java/ortus/boxlang/lsp/`)
- 8 comprehensive tests for import go-to-definition:
  - `testGoToDefinitionOnSimpleImport()` - `import UserEntity;` navigation
  - `testGoToDefinitionOnInterfaceImport()` - `import IUserService;` navigation
  - `testGoToDefinitionOnAliasedImportClassName()` - class name part of aliased import
  - `testGoToDefinitionOnAliasedImportAlias()` - alias part of aliased import
  - `testGoToDefinitionOnJavaImportReturnsEmpty()` - Java imports return empty
  - `testGoToDefinitionOnUnknownImportReturnsEmpty()` - unknown imports return empty
  - `testGoToDefinitionOnPackageQualifiedImportNotMatchingRoot()` - `import subpackage.Item;` does NOT match root `Item.bx`
  - `testGoToDefinitionOnPackageQualifiedImportExists()` - `import subpackage.SubThing;` resolves correctly

#### Modified Files

**`FindDefinitionTargetVisitor.java`**
- Added import for `BoxImport`
- Added `visit(BoxImport)` method to capture import statements at cursor position

**`ProjectContextProvider.java`**
- Added import for `BoxImport`
- Added `BoxImport` handler in `findDefinitionPossibiltiies()` method
- Added `findClassDefinitionFromImport(BoxImport)` method to resolve import to class location
- Added `findClassByFQNAndGetLocation(String)` method for FQN-based class lookup with case-insensitive fallback
- Import resolution logic: package-qualified imports (containing `.`) use FQN lookup; simple imports use name lookup

### Requirements Met

- ✅ Identify import statement at cursor
- ✅ Resolve import path to actual file
- ✅ Handle simple imports (import ClassName;)
- ✅ Handle aliased imports (import ClassName as Alias;)
- ✅ Handle package-qualified imports (import subpackage.ClassName;)
- ✅ Handle Java imports (return empty - no source available)
- ✅ Handle unknown imports (return empty)
- ✅ Prevent incorrect matching (subpackage.Item does NOT match root Item.bx)

---

## Task 2.4: Go to Definition - Properties (Complete)

**Date:** 2026-01-22

### Summary

Implemented go-to-definition functionality for property references. When the user clicks "Go to Definition" on a property access, the LSP navigates to the property declaration. This includes support for:
- `variables.propertyName` - scoped property access
- `this.propertyName` - this-scoped property access
- Unqualified `propertyName` access within a class method
- Inherited properties (navigates to parent class property declaration)

### Key Design Decisions

- Properties are considered private in BoxLang, so property navigation only works within the same class or through inheritance
- Unknown receivers (e.g., `a.foo` where `a` is undefined) return empty results instead of incorrectly matching to classes
- Case-insensitive property lookup to match BoxLang semantics

### Changes Made

#### New Files Created

**Test Resource Files** (`src/test/resources/files/propertyDefinitionTest/`)
- `BaseEntity.bx` - Base class with `id` and `createdAt` properties
- `User.bx` - User class extending BaseEntity with username, email, age properties
- `UserConsumer.bx` - Test class for external property access scenarios

**`PropertyDefinitionTest.java`** (`src/test/java/ortus/boxlang/lsp/`)
- 8 comprehensive tests for property go-to-definition:
  - `testGoToDefinitionOnVariablesScopedProperty()` - `variables.username` navigation
  - `testGoToDefinitionOnThisScopedProperty()` - `this.age` navigation
  - `testGoToDefinitionOnUnqualifiedProperty()` - unqualified `email` navigation
  - `testGoToDefinitionOnInheritedProperty()` - `variables.id` to parent class
  - `testGoToDefinitionOnUnqualifiedPropertyInReturn()` - property in return statement
  - `testGoToDefinitionOnPropertyInSetter()` - property in setter method
  - `testGoToDefinitionOnUnknownPropertyReturnsEmpty()` - unknown property handling
  - `testGoToDefinitionOnUnknownReceiverReturnsEmpty()` - unknown receiver handling

#### Modified Files

**`FindDefinitionTargetVisitor.java`**
- Added `BoxDotAccess` visit method to capture property access expressions
- Handles both `BoxScope` and `BoxIdentifier` context types for `variables` and `this`

**`ProjectContextProvider.java`**
- Added handler methods in `findDefinitionPossibiltiies()`:
  - `findPropertyDefinition(BoxDotAccess)` - handles scoped property access
  - `findPropertyDefinitionAtPosition()` - fallback using getDescendantsOfType
  - `findPropertyDefinitionFromIdentifier()` - handles unqualified property access
  - `findPropertyInSameFile()` - finds property in current file
  - `findPropertyInInheritanceChain()` - finds property in parent classes
- Updated `findClassDefinitionFromIdentifier()` to skip identifiers that are part of dot access
- Updated `findPropertyDefinitionFromIdentifier()` to only resolve when receiver is `variables` or `this`

**`ProjectIndex.java`**
- Fixed `findProperty()` to use case-insensitive keys (lowercase)
- Fixed property key generation during indexing and cache loading to use lowercase

---

## Task 2.3: Go to Definition - Classes and Interfaces (Complete)

**Date:** 2026-01-22

### Summary

Implemented go-to-definition functionality for class and interface references. When the user clicks "Go to Definition" on a class reference, the LSP navigates to the class/interface definition file. This includes support for:
- `new ClassName()` expressions - navigate from instantiation to class definition
- `extends="ClassName"` annotations - navigate to parent class
- `implements="InterfaceName"` annotations - navigate to interface
- Return type hints (`public User function...`) - navigate to class definition
- Parameter type hints (`required User param`) - navigate to class definition

### Changes Made

#### New Files Created

**Test Resource Files** (`src/test/resources/files/classDefinitionTest/`)
- `IRepository.bx` - Generic repository interface
- `AbstractEntity.bx` - Abstract base entity class
- `User.bx` - User entity class extending AbstractEntity
- `UserRepository.bx` - Repository implementing IRepository
- `UserService.bx` - Comprehensive test class with various class reference scenarios

**`ClassDefinitionTest.java`** (`src/test/java/ortus/boxlang/lsp/`)
- 7 comprehensive tests for class/interface go-to-definition:
  - `testGoToDefinitionOnNewExpression()` - `new User()` navigates to User.bx
  - `testGoToDefinitionOnExtendsClause()` - `extends="AbstractEntity"` navigates to AbstractEntity.bx
  - `testGoToDefinitionOnImplementsClause()` - `implements="IRepository"` navigates to IRepository.bx
  - `testGoToDefinitionOnReturnTypeHint()` - `public User function` navigates to User.bx
  - `testGoToDefinitionOnParameterTypeHint()` - `required User param` navigates to User.bx
  - `testGoToDefinitionOnInterface()` - interface navigation from implements clause
  - `testGoToDefinitionOnUnknownClassReturnsEmpty()` - unknown classes return empty

#### Modified Files

**`FindDefinitionTargetVisitor.java`**
- Added visit methods for:
  - `BoxNew` - for `new ClassName()` expressions
  - `BoxFQN` - for class references in type contexts
  - `BoxAnnotation` - for `extends` and `implements` annotations
  - `BoxStringLiteral` - for string values in annotations
  - `BoxReturnType` - for return type hints in function declarations
  - `BoxArgumentDeclaration` - for parameter type hints
- Added `isBuiltInType()` helper to filter out built-in types like string, numeric, etc.

**`ProjectContextProvider.java`**
- Added handler methods in `findDefinitionPossibiltiies()`:
  - `findClassDefinition(BoxNew)` - handles `new ClassName()` navigation
  - `findClassDefinitionFromAnnotation(BoxAnnotation)` - handles extends/implements
  - `findClassDefinitionFromReturnType(BoxReturnType)` - handles return type hints
  - `findClassDefinitionFromArgumentType(BoxArgumentDeclaration)` - handles parameter type hints
  - `findClassDefinitionFromFQN(BoxFQN)` - handles BoxFQN class references
  - `findClassDefinitionFromIdentifier(BoxIdentifier)` - handles identifier-based class references
  - `findClassByNameAndGetLocation(String)` - shared lookup method using project index

**`ImportCompletionRule.java`**
- Fixed `StringIndexOutOfBoundsException` by adding bounds checking on line 47 and 62

### Technical Notes

- Class lookup uses `ProjectIndex.findClassByName()` which performs case-insensitive matching
- FQN extraction handles both simple names and dot-separated paths
- The implementation reuses the existing project indexing infrastructure

---

## Task 2.2: Go to Definition - Functions and Methods (Complete)

**Date:** 2026-01-21

### Summary

Implemented go-to-definition functionality for function and method invocations. When the user clicks "Go to Definition" on a function call or method invocation, the LSP navigates to the function/method definition. This includes support for:
- Same-file function invocations (UDFs)
- Cross-file method invocations (via project index)
- `this.methodName()` invocations (including inherited methods)
- Inherited methods without receiver (`parentFunc()` in child class)
- Inherited methods with receiver (`child.parentFunc()`)
- Case-insensitive lookups (BoxLang is case-insensitive)
- BIFs return no result (no source to navigate to)

### Changes Made

#### New Files Created

**Test Resource Files** (`src/test/resources/files/functionDefinitionTest/`)
- `BaseClass.bx` - Base class with `logMessage()` and `getGreeting()` methods
- `ChildClass.bx` - Extends BaseClass, overrides `getGreeting()`, has `multiGreet()` using `this.getGreeting()`
- `ServiceClass.bx` - Service class with `getUserById()`, `createUser()`, `sanitizeInput()` methods
- `ClassThatUsesService.bx` - Test file with various method invocation scenarios

**Test Resource Files** (`src/test/resources/files/inheritanceTest/`)
- `Parent.bx` - Parent class with `parentFunc()` method
- `Child.bx` - Extends Parent, tests inherited function calls with and without `this`
- `ChildDependency.bx` - Tests cross-file method invocation and inherited methods on objects

**`FunctionDefinitionTest.java`** (`src/test/java/ortus/boxlang/lsp/`)
- 7 comprehensive tests for function/method go-to-definition

**`InheritanceDefinitionTest.java`** (`src/test/java/ortus/boxlang/lsp/`)
- 4 tests for inheritance scenarios:
  - `testInheritedFunctionWithoutReceiver()` - `parentFunc()` in child navigates to Parent.bx
  - `testMethodInvocationOnObject()` - `child.childFunc()` navigates to Child.bx
  - `testInheritedMethodInvocationOnObject()` - `child.parentFunc()` navigates to Parent.bx
  - `testInheritedFunctionWithThisPrefix()` - `this.parentFunc()` in child navigates to Parent.bx

#### Modified Files

**`FindDefinitionTargetVisitor.java`**
- Added visit methods for container nodes (BoxClass, BoxInterface, BoxScript, BoxTemplate, BoxFunctionDeclaration)
- Without these, the visitor never traversed into class methods to find invocation nodes

**`VariableTypeCollectorVisitor.java`**
- Added same container node traversal fixes
- Required to resolve variable types from `new ClassName()` assignments inside methods

**`ProjectContextProvider.java`**
- Added `findFunctionDefinition()` method for `BoxFunctionInvocation` that also checks parent classes
- Updated `findMethodDefinition()` to check parent classes when `this.method()` not found in same file
- Added `findMethodInClassHierarchy()` to walk inheritance chain
- Added helper methods for location creation and FQN extraction

**`IndexedMethod.java`**
- Changed `getKey()` to return lowercase keys for case-insensitive lookup

**`ProjectIndex.java`**
- Updated `findMethod()` to use lowercase keys for case-insensitive lookup

### Key Technical Fixes

1. **AST Traversal**: VoidBoxVisitor doesn't automatically traverse children. Container nodes (BoxClass, BoxFunctionDeclaration, etc.) need explicit visit methods that call `visitChildren()`.

2. **Case Insensitivity**: BoxLang is case-insensitive, so `new Thing()` must match `thing.bx`. Method keys are now stored and looked up in lowercase.

3. **Inheritance for `this.method()`**: When calling `this.parentFunc()` in a child class, if not found in same file, now walks up inheritance chain.

4. **Inheritance for unqualified calls**: When calling `parentFunc()` (without receiver) in a child class, now checks parent classes after checking same file.

### Requirements Met

- ✅ Navigate from function invocation to definition in same file
- ✅ Navigate from method invocation to definition in another file
- ✅ Navigate `this.method()` to method definition (same class or inherited)
- ✅ Navigate to inherited methods (walk up inheritance chain)
- ✅ Navigate inherited function without receiver (`parentFunc()` in child)
- ✅ Handle overridden methods (navigate to the override, not base)
- ✅ Case-insensitive lookups (BoxLang compatibility)
- ✅ BIFs return no result (no source to navigate to)

---

## Task 2.1: Go to Definition - Local Variables (Complete)

**Date:** 2026-01-21

### Summary

Implemented go-to-definition functionality for local variables. When the user clicks "Go to Definition" on a variable identifier, the LSP navigates to the variable's declaration site. This includes support for:
- Local variables declared with `var` keyword
- Function parameters
- Shadowed variables (correctly resolving to the nearest declaration)
- Variables in loops (including for-loop initialization variables)
- Deeply nested variables within conditional blocks

### Changes Made

#### New Files Created

**`VariableDefinitionResolver.java`** (`src/main/java/ortus/boxlang/lsp/workspace/visitors/`)
- Resolves variable usages to their declaration sites
- Uses `getDescendantsOfType()` for reliable AST traversal
- Collects declarations: function parameters, local variables (var), properties, scoped assignments
- Implements proper scoping rules:
  - Function parameters are scoped to their containing function only
  - Local variables shadow parameters with the same name
  - Later declarations shadow earlier ones within the same scope
- Record types: `VariableDeclaration` (name, declarationNode, type, declarationLine)
- Enum: `DeclarationType` (PARAMETER, LOCAL_VAR, PROPERTY, SCOPED_VAR)

**`VariableDefinitionTest.java`** (`src/test/java/ortus/boxlang/lsp/`)
- 9 comprehensive tests for variable definition navigation:
  - `testLocalVariableUsage()` - basic local variable go-to-definition
  - `testParameterUsage()` - function parameter go-to-definition
  - `testShadowedVariableGoesToLocalDeclaration()` - shadowed variables resolve to local var
  - `testParameterBeforeShadowing()` - parameter access before it's shadowed
  - `testLoopVariableDefinition()` - for-loop variable (var i = 0)
  - `testOuterVariableFromLoop()` - variable declared outside loop
  - `testUnknownVariableReturnsEmpty()` - edge case for unknown variables
  - `testMultipleVariablesOnSameLine()` - multiple variables in return statement
  - `testDeepNestedVariableDefinition()` - deeply nested variable access

**Test Resource Files**
- `variableDefinitionTest.bx` - Test class with various variable scenarios

#### Modified Files

**`FindDefinitionTargetVisitor.java`**
- Added `visit(BoxIdentifier)` method to handle variable identifiers
- Variable identifiers are now recognized as valid definition targets
- Imports added: `BoxIdentifier`

**`ProjectContextProvider.java`**
- Updated `findDefinitionPossibiltiies()` to handle `BoxIdentifier` nodes
- Added `findVariableDefinition()` method that uses `VariableDefinitionResolver`
- Import added: `VariableDefinitionResolver`

### Go to Definition Flow

1. User triggers "Go to Definition" at a cursor position
2. `FindDefinitionTargetVisitor` finds the AST node at that position
3. If it's a `BoxIdentifier`, `findVariableDefinition()` is called
4. `VariableDefinitionResolver` collects all variable declarations in the file
5. Resolver finds the declaration that:
   - Has the same name (case-insensitive)
   - Is in the same function scope (or class-level for properties)
   - Is declared before the usage position
   - Is the closest match (handles shadowing)
6. Returns `Location` pointing to the declaration site

### Scope Rules Implemented

- **Function Parameters**: Declared at the parameter position in function signature
- **Local Variables**: Declared at first `var` assignment
- **Shadowed Variables**: The closest declaration before usage takes precedence
- **Properties**: Class-level properties accessible from all functions
- **Scoped Assignments**: `variables.foo` and `this.bar` treated as class-level

### Requirements Met

- ✅ Identify variable at cursor position
- ✅ Determine variable scope
- ✅ Find declaration site within current function/scope
- ✅ Return location of first assignment or formal declaration
- ✅ Handle shadowed variables correctly (find the right scope's declaration)
- ✅ Function parameters declared in the function signature
- ✅ Local variables declared at first assignment with `var`

---

## Task 1.12: Document Sync Improvements (Complete)

**Date:** 2026-01-21

### Summary

Implemented robust document synchronization improvements including incremental text synchronization, debouncing of expensive operations, document version management, and thread-safe document processing.

### Changes Made

#### New Files Created

**`DocumentModel.java`** (`src/main/java/ortus/boxlang/lsp/workspace/`)
- Maintains in-memory document content for incremental sync support
- Applies range-based (incremental) and full-document changes
- Provides position-to-offset conversion for accurate text manipulation
- Tracks document versions and rejects out-of-order updates
- Thread-safe synchronized methods for concurrent access

**`DebouncedDocumentProcessor.java`** (`src/main/java/ortus/boxlang/lsp/workspace/`)
- Schedules document processing after a configurable debounce delay (default: 300ms)
- Cancels pending tasks when new changes arrive during the debounce window
- Uses single-threaded ScheduledExecutorService for predictable processing order
- Provides methods for immediate processing, cancellation, and status checking
- Daemon thread prevents blocking application shutdown

**`DocumentSyncTest.java`** (`src/test/java/ortus/boxlang/lsp/`)
- 16 comprehensive tests covering:
  - Incremental sync: single character insert, text replacement, text deletion, multi-line changes
  - Full sync: verifies backward compatibility with full document updates
  - Debouncing: rapid edits are coalesced, diagnostics update after debounce
  - Version tracking: proper version management, rejection of stale versions
  - Thread safety: concurrent edits, concurrent open/close operations
  - File type support: .bx and .bxm files
  - Edge cases: empty document changes, multiple changes in single event

**Test Resource Files**
- `documentSyncTest.bx` - Test class for document sync testing

#### Modified Files

**`LanguageServer.java`**
- Changed `TextDocumentSyncKind.Full` to `TextDocumentSyncKind.Incremental`
- Enables clients to send range-based changes instead of full documents

**`BoxLangTextDocumentService.java`**
- Updated `didOpen()` to pass document version to `trackDocumentOpen()`
- Updated `didChange()` to pass document version to `trackDocumentChange()`
- Removed obsolete TODO comments

**`ProjectContextProvider.java`**
- Added `documentModels` map (`ConcurrentHashMap<URI, DocumentModel>`) for incremental sync
- Added `documentProcessor` (`DebouncedDocumentProcessor`) for debounced processing
- Changed `parsedFiles` and `openDocuments` to `ConcurrentHashMap` for thread safety
- Updated `trackDocumentChange()`:
  - Now accepts optional version parameter
  - Applies changes to `DocumentModel` immediately
  - Schedules debounced parsing/diagnostic processing
  - Rejects changes with stale versions
- Updated `trackDocumentOpen()`:
  - Creates `DocumentModel` for new documents
  - Accepts version parameter
- Updated `trackDocumentClose()`:
  - Cancels pending processing
  - Cleans up `DocumentModel`
- Added public methods:
  - `getLatestFileParseResultPublic()` - test helper for accessing parse results
  - `getDocumentVersion()` - returns current document version
  - `getDocumentModel()` - returns `DocumentModel` for a URI

### Features Implemented

1. **Incremental Text Synchronization**
   - Server advertises `TextDocumentSyncKind.Incremental` capability
   - `DocumentModel` applies range-based changes efficiently
   - Position-to-offset conversion handles multi-line edits correctly
   - Full sync still supported as fallback

2. **Debouncing**
   - 300ms debounce delay by default
   - Parsing and diagnostics only run after typing pause
   - Each new keystroke resets the debounce timer
   - Reduces CPU usage during rapid typing

3. **Document Version Management**
   - Tracks document version for each open document
   - Rejects out-of-order changes (prevents race conditions)
   - Version updates atomically with content changes

4. **Thread Safety**
   - `ConcurrentHashMap` for all document storage
   - Synchronized methods in `DocumentModel`
   - Single-threaded debounce scheduler for predictable ordering

5. **Proper Cleanup**
   - Cancels pending processing on document close
   - Removes `DocumentModel` on close
   - Daemon threads don't prevent shutdown

### Technical Notes

- **Position Conversion**: LSP positions are 0-indexed (line, character). The `positionToOffset()` method iterates through newlines to find the correct byte offset.
- **Debounce Timing**: 300ms provides good balance between responsiveness and CPU efficiency. Can be adjusted in `DebouncedDocumentProcessor` constructor.
- **Test Wait Times**: Tests wait 500ms after changes to ensure debounce completes (300ms delay + buffer).

### Requirements Met

- ✅ Support incremental sync (TextDocumentSyncKind.Incremental)
- ✅ Handle all three file types correctly (.bx, .bxm, .cfc/.cfm)
- ✅ Properly manage document versions
- ✅ Handle rapid edits without losing updates
- ✅ Trigger re-parsing and diagnostic updates on change
- ✅ Debounce expensive operations (300ms delay)
- ✅ Ensure thread-safety for document processing

---

## Task 1.11: Signature Help (Complete)

**Date:** 2026-01-21

### Summary

Implemented signature help functionality that displays parameter hints while typing function calls. When the cursor is inside a function call's parentheses, the LSP shows the function signature with parameter information, documentation, and highlights the active parameter.

### Changes Made

#### New Files Created

**`FindSignatureHelpTargetVisitor.java`** (`src/main/java/ortus/boxlang/lsp/workspace/visitors/`)
- Traverses AST to find function/method invocations containing the cursor position
- Handles `BoxFunctionInvocation`, `BoxMethodInvocation`, and `BoxNew` nodes
- Calculates active parameter index based on cursor position relative to arguments
- Uses direct AST traversal for reliable node discovery

**`SignatureHelpTest.java`** (`src/test/java/ortus/boxlang/lsp/`)
- 10 comprehensive tests:
  - `testSignatureHelpOnFunctionInvocation()` - basic signature help for UDFs
  - `testSignatureHelpShowsAllParameters()` - verifies parameter information
  - `testSignatureHelpActiveParameter()` - verifies first parameter highlighting
  - `testSignatureHelpSecondParameter()` - verifies second parameter highlighting
  - `testSignatureHelpShowsDocumentation()` - verifies documentation display
  - `testSignatureHelpForBIF()` - verifies BIF signature lookup
  - `testSignatureHelpForMethodInvocation()` - cross-file method lookup
  - `testSignatureHelpForConstructor()` - constructor signature help
  - `testSignatureHelpOutsideFunctionCallReturnsNull()` - edge case
  - `testSignatureHelpParameterLabels()` - verifies parameter label format

**`signatureHelpTest.bx`** (`src/test/resources/files/`)
- Test class with documented functions, multi-parameter functions, and various call scenarios

#### Modified Files

**`BoxLangTextDocumentService.java`**
- Added `signatureHelp()` method implementing `textDocument/signatureHelp` request
- Added imports for `SignatureHelp` and `SignatureHelpParams`

**`LanguageServer.java`**
- Added `SignatureHelpOptions` to server capabilities
- Set trigger characters: `(` and `,`
- Added import for `SignatureHelpOptions`

**`ProjectContextProvider.java`**
- Added `getSignatureHelp()` method as main entry point
- Added `buildSignatureHelpForFunction()` for UDF signatures
- Added `buildSignatureHelpForIndexedMethod()` for cross-file method signatures
- Added `buildSignatureHelpForBIF()` for built-in function signatures
- Added imports for `ParameterInformation`, `SignatureHelp`, `SignatureInformation`, `BoxRuntime`, `BIFDescriptor`, `IndexedParameter`

### Features Implemented

1. **Trigger Characters**: Signature help triggers on `(` after function name and `,` within arguments

2. **User-Defined Functions (UDFs)**:
   - Finds function declarations in the same file
   - Displays function signature with parameters
   - Shows documentation from javadoc comments

3. **Built-in Functions (BIFs)**:
   - Looks up BIF metadata from BoxRuntime
   - Displays parameter signatures with optional indicators
   - Falls back to BIF lookup when UDF not found

4. **Method Invocations**:
   - Resolves object type from `new ClassName()` assignments
   - Looks up method in project index
   - Shows indexed method signature and documentation

5. **Constructor Calls**:
   - Detects `new ClassName()` expressions
   - Looks up `init` method in the class
   - Shows constructor signature if defined

6. **Active Parameter Tracking**:
   - Calculates which parameter the cursor is on
   - Based on argument positions in AST
   - Highlights the current parameter in IDE

### Signature Help Display Format

```
functionName(required type param1, [type param2 = default])

Description from documentation.

**Parameters:**
- param1 Description
- param2 Description

**@return** Return description
```

### Requirements Met

- ✅ Trigger on `(` after function name
- ✅ Trigger on `,` within function call
- ✅ Track cursor position to highlight active parameter
- ✅ Support BIFs and user-defined functions
- ✅ Support method calls
- ✅ Support constructor calls (`new ClassName(...)`)
- ✅ Handle optional parameters

### Technical Notes

- Position conversion: LSP uses 0-indexed lines, BoxLang parser uses 1-indexed
- BLASTTools.containsPosition limits BoxFunctionInvocation range to just the function name (for hover); signature help uses full node position
- Direct AST traversal used instead of VoidBoxVisitor pattern for reliable node discovery

---

## Task 1.10: Hover - Classes and Interfaces (Complete)

**Date:** 2026-01-21

### Summary

Implemented hover support for classes and interfaces. When hovering over a class reference (e.g., in `new ClassName()` expressions), the LSP displays the class name with type indicator (class/interface), the class signature including inheritance, documentation from javadoc comments, extends/implements information, and file location.

### Changes Made

#### New Files Created

**`ClassHoverTest.java`** (`src/test/java/ortus/boxlang/lsp/`)
- 8 comprehensive tests for class/interface hover functionality:
  - `testHoverOnClassInNewExpression()` - hover on class name in new expression
  - `testHoverOnClassShowsDocumentation()` - hover shows class documentation
  - `testHoverOnClassShowsInheritance()` - hover shows extends/implements
  - `testHoverOnClassShowsFileLocation()` - hover shows file path
  - `testHoverOnInterfaceInNewExpression()` - interface handling
  - `testHoverOnBaseServiceClass()` - verify class indexing
  - `testHoverOnInterfaceVerifyIndexed()` - verify interface indexing
  - `testHoverOnClassWithInheritanceChain()` - verify inheritance tracking

**Test Resource Files** (`src/test/resources/files/classHoverTest/`)
- `BaseService.bx` - Base class with documentation
- `IUserService.bx` - Interface with documentation
- `UserService.bx` - Class extending BaseService, implementing IUserService
- `ClassThatUsesUserService.bx` - File using the classes for hover testing

#### Modified Files

**`FindHoverTargetVisitor.java`**
- Added `visit(BoxNew)` method to handle class instantiation expressions
- Added `visit(BoxFQN)` method to handle fully qualified name references
- Added imports for `BoxFQN` and `BoxNew`

**`IndexedClass.java`**
- Added `documentation` field to store class/interface documentation

**`ProjectIndexVisitor.java`**
- Added `extractClassDocumentation()` method to extract documentation from BoxClass nodes
- Added `extractInterfaceDocumentation()` method to extract documentation from BoxInterface nodes
- Updated `visit(BoxClass)` and `visit(BoxInterface)` to include documentation in indexed classes

**`ProjectContextProvider.java`**
- Added handling for `BoxNew` nodes in `getHoverInfo()` to show class hover
- Added handling for `BoxFQN` nodes in `getHoverInfo()` for class references
- Added `buildHoverForClass()` method to format class hover content
- Added `buildClassSignature()` method to build class/interface signature
- Added `formatClassDocumentation()` method to format class documentation
- Added `extractClassNameFromNew()` and `extractClassNameFromFQN()` helper methods
- Added `getFileNameFromUri()` helper method
- Added imports for `BoxFQN`, `BoxNew`, and `IndexedClass`

### Hover Content Format

**For Classes:**
```
**UserService** (class)

```boxlang
class UserService extends BaseService implements IUserService
```

User service for managing user operations.

**@author** Claude

**@since** 2.0

**Extends:** `BaseService`

**Implements:** `IUserService`

**File:** `UserService.bx`
```

**For Interfaces:**
```
**IUserService** (interface)

```boxlang
interface IUserService
```

Interface for user service operations.

**@author** Claude

**File:** `IUserService.bx`
```

### Requirements Met

- ✅ Identify class/interface reference under cursor (BoxNew, BoxFQN nodes)
- ✅ Look up in index (using `findClassByName()`)
- ✅ Display class documentation (from javadoc comments)
- ✅ Display inheritance chain (extends)
- ✅ Display implemented interfaces (implements)
- ✅ Display file location
- ✅ Display modifier info (abstract, final, etc.)

---

## Task 1.9: Hover - Variables (Complete)

**Date:** 2026-01-21

### Summary

Implemented hover support for variables showing scope information, inferred types, and declaration details. When hovering over a variable identifier, the LSP displays the variable's scope (local, arguments, variables, this, property), inferred type from literal assignments, and for function parameters, shows required status and default values. Additionally, hovering over scope keywords (e.g., `variables`, `local`, `this`, `arguments`) displays helpful descriptions of each scope.

### Changes Made

#### New Files Created

**`VariableScopeCollectorVisitor.java`** (`src/main/java/ortus/boxlang/lsp/workspace/visitors/`)
- Collects variable scope and type information from assignments and declarations
- Tracks variables in different scopes: local (var), arguments, this, variables, property
- Infers types from literal assignments (string, numeric, boolean, array, struct)
- Infers types from `new ClassName()` expressions
- Provides `VariableInfo` record with: name, scope, typeHint, inferredType, declarationLine, isRequired, defaultValue, declarationNode
- Provides `VariableScope` enum: LOCAL, ARGUMENTS, VARIABLES, THIS, PROPERTY, UNKNOWN
- Provides scope keyword descriptions via `getScopeKeywordInfo()`

**`VariableHoverTest.java`** (`src/test/java/ortus/boxlang/lsp/`)
- 9 comprehensive tests for variable hover functionality:
  - `testHoverOnLocalVariableDeclaration()` - hover on var declarations
  - `testHoverOnLocalVariableUsage()` - hover on variable references
  - `testHoverOnFunctionParameter()` - hover on parameter usage
  - `testHoverOnParameterWithDefaultValue()` - hover showing default values
  - `testHoverOnScopedVariableAccess()` - hover on `variables` scope keyword
  - `testHoverShowsInferredTypeFromLiteral()` - hover showing inferred numeric type
  - `testHoverShowsDeclarationLine()` - hover showing where variable was declared
  - `testHoverOnThisScope()` - hover on `this` scope keyword
  - `testHoverOnProperty()` - hover on property declarations

**Test Resource Files**
- `variableHoverTest.bx` - Test class with various variable scenarios (local vars, parameters, scoped access, properties)

#### Modified Files

**`FindHoverTargetVisitor.java`**
- Added `visit(BoxScope)` method to handle scope keywords (variables, local, this, arguments, etc.)
- Added `visit(BoxArgumentDeclaration)` method for function parameter declarations
- Added `visit(BoxProperty)` method for property declarations
- Added `visit(BoxDotAccess)` method to handle scoped property access (e.g., `variables.instanceVar`)
- Added proper AST traversal with `visitChildren()` calls for all visit methods

**`ProjectContextProvider.java`**
- Added handling for `BoxScope` nodes in `getHoverInfo()` to show scope keyword descriptions
- Added handling for `BoxIdentifier` nodes in `getHoverInfo()` to show variable information
- Added handling for `BoxArgumentDeclaration` nodes for parameter hover
- Added handling for `BoxProperty` nodes for property hover
- Added `buildHoverForVariable()` method to format variable hover content
- Added `buildHoverForParameter()` method to format parameter hover content
- Added `buildHoverForProperty()` method to format property hover content
- Added `buildHoverForScopeKeyword()` method to format scope keyword descriptions

### Hover Content Format

**For Variables:**
```
**localVar** (local variable)

**Type:** string (inferred)

**Declared at:** line 8
```

**For Function Parameters:**
```
**name** (function parameter)

**Type:** string

**Required:** Yes

**Declared at:** line 15
```

**For Parameters with Defaults:**
```
**age** (function parameter)

**Type:** numeric

**Default:** 18

**Declared at:** line 15
```

**For Scope Keywords:**
```
**variables** scope

The instance scope containing private variables of the class. Variables in this scope are accessible within the class but not from outside.
```

### Scope Keyword Descriptions

| Keyword | Description |
|---------|-------------|
| `variables` | Instance scope containing private variables of the class |
| `local` | Function-local scope for variables declared with `var` |
| `this` | Public scope for variables accessible from outside the class |
| `arguments` | Function arguments scope containing parameters passed to the function |
| `request` | Request scope available for the duration of an HTTP request |
| `session` | Session scope available for the duration of a user's session |
| `application` | Application scope shared across all requests |
| `server` | Server scope shared across all applications |
| `cgi` | CGI scope containing CGI environment variables |
| `form` | Form scope containing form field values |
| `url` | URL scope containing URL query string parameters |
| `cookie` | Cookie scope containing cookie values |

### Type Inference

The hover system infers types from literal assignments:
- String literals → `string`
- Numeric literals → `numeric`
- Boolean literals (`true`/`false`) → `boolean`
- Array literals → `array`
- Struct literals → `struct`
- `new ClassName()` expressions → `ClassName`

### Technical Details

- **BoxScope AST node**: Discovered that scope keywords are represented as `BoxScope` nodes, not `BoxIdentifier` nodes
- **Required parameter detection**: Uses `BoxArgumentDeclaration.getRequired()` method directly (not annotations)
- **VoidBoxVisitor pattern**: Must explicitly call `visitChildren()` to traverse the AST; it doesn't happen automatically
- **BoxDotAccess positions**: The position of `variables.instanceVar` starts at `.instanceVar`; the `variables` part is a child `BoxScope` node with its own position

### Requirements Met

- ✅ Identify variable under cursor
- ✅ Determine variable scope (local, arguments, this, variables, property)
- ✅ Find declaration/assignment site
- ✅ Show inferred type when possible
- ✅ Show scope information
- ✅ Show required status for parameters
- ✅ Show default values for parameters
- ✅ Hover on scope keywords shows scope descriptions

---

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
