# BoxLang LSP Implementation Roadmap

A comprehensive plan for building a high-quality Language Server Protocol implementation for BoxLang, targeting VS Code, JetBrains, Zed, and Claude Code.

## Current State

- ✅ Basic application scaffold
- ✅ Access to official BoxLang AST
- ✅ lsp4j integration
- ✅ Document outline (document symbols)
- ✅ Basic diagnostics
- ✅ Basic autocompletion rules

## File Types

| Extension | Purpose |
|-----------|---------|
| `.bx` | Class files (OOP) |
| `.bxs` | Script files |
| `.bxm` | Template files (HTML-like) |

---

## Phase 1: Solid Foundation

2
**Goal:** Build the critical infrastructure that all other features depend on. Focus on indexing, caching, and core information features.

**Estimated Duration:** 4-6 weeks

---

### 1.1 Project Indexer - Core Infrastructure (Complete)

**Priority:** Critical  
**Complexity:** High  
**Dependencies:** BoxLang AST

Build a background indexing system that parses and caches metadata about all BoxLang files in the workspace. This is the foundation for nearly every other feature.

**Requirements:**

- Scan workspace on server initialization
- Parse all `.bx`, `.bxs`, and `.bxm` files
- Build in-memory symbol tables
- Support incremental updates (re-index only changed files)
- Handle file creation, deletion, and renames
- Respect `.gitignore` and custom exclude patterns

**Data to Index:**

- Classes: name, file path, extends, implements, modifiers
- Interfaces: name, file path, extends
- Methods: name, containing class, parameters, return type hint, modifiers, visibility
- Properties: name, containing class, type hint, default value, accessors
- Functions: name (for `.bxs` files), parameters, return type hint
- Variables: scope-aware tracking where feasible
- Imports: source file to target mapping
- Inheritance graph: parent/child relationships
- Interface implementations: which classes implement which interfaces

**Implementation Notes:**

- Use the BoxLang AST walker to extract metadata
- Consider a visitor pattern for clean extraction
- Store file modification timestamps to detect changes
- Fire custom events when index updates complete

---

### 1.2 Persistent Index Cache (Complete)

**Priority:** Critical  
**Complexity:** Medium  
**Dependencies:** 1.1 Project Indexer

Persist the index to disk so that server restarts don't require full re-indexing. This dramatically improves startup time for large projects.

**Requirements:**

- Serialize index to disk on shutdown or periodically
- Load cached index on startup
- Validate cache freshness against file modification times
- Invalidate stale entries and re-index only those files
- Handle cache corruption gracefully (fall back to full re-index)

**Storage Options:**

- SQLite database (recommended for query flexibility)
- JSON/MessagePack files (simpler but less queryable)
- Binary serialization (fastest but harder to debug)

**Cache Schema (if SQLite):**

```sql
CREATE TABLE files (
    path TEXT PRIMARY KEY,
    modified_at INTEGER,
    checksum TEXT
);

CREATE TABLE classes (
    id INTEGER PRIMARY KEY,
    file_path TEXT,
    name TEXT,
    fqn TEXT,
    extends_fqn TEXT,
    is_abstract BOOLEAN,
    is_interface BOOLEAN,
    start_line INTEGER,
    end_line INTEGER
);

CREATE TABLE methods (
    id INTEGER PRIMARY KEY,
    class_id INTEGER,
    name TEXT,
    visibility TEXT,
    is_static BOOLEAN,
    return_type TEXT,
    parameters TEXT, -- JSON array
    start_line INTEGER,
    end_line INTEGER
);

CREATE TABLE properties (
    id INTEGER PRIMARY KEY,
    class_id INTEGER,
    name TEXT,
    type_hint TEXT,
    default_value TEXT,
    has_getter BOOLEAN,
    has_setter BOOLEAN
);

-- Additional tables for functions, variables, imports, etc.
```

---

### 1.3 Index Query API (Complete)

**Priority:** Critical  
**Complexity:** Medium  
**Dependencies:** 1.1, 1.2

Create a clean API for other LSP features to query the index. This abstracts away the storage mechanism and provides convenient lookup methods.

**API Methods:**

```java
interface ProjectIndex {
    // Class lookups
    ClassInfo findClassByName(String name);
    ClassInfo findClassByFQN(String fullyQualifiedName);
    List<ClassInfo> findClassesImplementing(String interfaceName);
    List<ClassInfo> findClassesExtending(String className);
    List<ClassInfo> findAllClasses();
    
    // Method lookups
    MethodInfo findMethod(String className, String methodName);
    List<MethodInfo> findMethodsByName(String methodName);
    List<MethodInfo> findOverrides(String className, String methodName);
    
    // Property lookups
    PropertyInfo findProperty(String className, String propertyName);
    
    // Function lookups (for .bxs files)
    FunctionInfo findFunction(String name);
    List<FunctionInfo> findFunctionsInFile(String filePath);
    
    // Symbol search
    List<SymbolInfo> searchSymbols(String query, SymbolKind... kinds);
    
    // File lookups
    FileInfo getFileInfo(String path);
    List<String> getFilesInDirectory(String directory);
    
    // Dependency graph
    List<String> getFilesDependingOn(String filePath);
    List<String> getDependenciesOf(String filePath);
}
```

---

### 1.4 Expand Diagnostics - Syntax Errors (Complete)

**Priority:** High  
**Complexity:** Low  
**Dependencies:** BoxLang AST

Ensure all syntax errors from the parser are properly reported as diagnostics.

**Requirements:**

- Capture all parse errors from BoxLang AST
- Map error positions to LSP ranges correctly
- Set appropriate severity (Error for syntax issues)
- Include helpful error messages
- Handle partial/incomplete parses gracefully
- Support all three file types

**Error Categories:**

- Unexpected token
- Missing semicolon/brace/paren
- Invalid expression
- Malformed string/comment
- Invalid tag syntax (`.bxm`)
- Unclosed blocks

---

### 1.5 Expand Diagnostics - Semantic Errors (Complete)

**Priority:** High  
**Complexity:** Medium  
**Dependencies:** 1.1 Project Indexer

Add semantic analysis to catch errors that are syntactically valid but semantically incorrect.

**Requirements:**

- Undefined variable references (configurable - tricky with dynamic typing)
- Undefined function/method calls
- Undefined class/component references
- Wrong number of arguments in function calls
- Missing required arguments
- Duplicate function/method definitions
- Duplicate property definitions
- Duplicate class definitions in workspace
- Invalid `extends` (class not found)
- Invalid `implements` (interface not found)
- Abstract method not implemented
- Final method override attempt

**Configuration:**

Allow users to enable/disable specific checks since dynamic typing makes some checks prone to false positives:

```json
{
    "boxlang.diagnostics.undefinedVariables": "warning",
    "boxlang.diagnostics.undefinedFunctions": "error",
    "boxlang.diagnostics.strictMode": false
}
```

---

### 1.6 Expand Diagnostics - Warnings (Complete)

**Priority:** Medium  
**Complexity:** Medium  
**Dependencies:** 1.1 Project Indexer

Add warnings for code that is technically valid but potentially problematic.

**Warning Categories:**

- Unused local variables
- Unused function parameters
- Unused private methods
- Unused imports
- Deprecated BIF usage
- Deprecated method usage (if annotation/convention exists)
- Implicit variable creation (variable used before declaration)
- Missing return statement when return type hint is present
- Unreachable code after `return`, `throw`, `break`, `continue`
- Empty catch blocks
- Shadowed variables (local shadows parameter, etc.)

**Implementation:**

- Use diagnostic tags: `DiagnosticTag.Unnecessary` for unused code
- Use diagnostic tags: `DiagnosticTag.Deprecated` for deprecated usage
- Make severity configurable per warning type

---

### 1.7 Hover - Built-in Functions (BIFs) (Skipped)

**Priority:** High  
**Complexity:** Low  
**Dependencies:** None (can use static data)

Show rich documentation when hovering over BoxLang built-in functions.

**Requirements:**

- Identify BIF under cursor
- Display function signature
- Display parameter descriptions
- Display return type
- Display description/documentation
- Display examples where helpful
- Link to official documentation

**Data Source:**

- Create a static JSON/resource file containing all BIF documentation
- Structure:

```json
{
    "arrayAppend": {
        "signature": "arrayAppend(array, value) → Array",
        "description": "Appends a value to the end of an array.",
        "parameters": [
            {
                "name": "array",
                "type": "Array",
                "required": true,
                "description": "The array to modify"
            },
            {
                "name": "value",
                "type": "any",
                "required": true,
                "description": "The value to append"
            }
        ],
        "returnType": "Array",
        "returnDescription": "The modified array",
        "examples": [
            "arr = [1, 2, 3];\narrayAppend(arr, 4);\n// arr is now [1, 2, 3, 4]"
        ],
        "docUrl": "https://boxlang.ortusbooks.com/...",
        "since": "1.0.0",
        "deprecated": false
    }
}
```

**Display Format (Markdown):**

```markdown
**arrayAppend**(array, value) → Array

Appends a value to the end of an array.

**Parameters:**
- `array` (Array) — The array to modify
- `value` (any) — The value to append

**Returns:** Array — The modified array

**Example:**
\`\`\`boxlang
arr = [1, 2, 3];
arrayAppend(arr, 4);
// arr is now [1, 2, 3, 4]
\`\`\`

[Documentation ↗](https://boxlang.ortusbooks.com/...)
```

---

### 1.8 Hover - User-Defined Functions and Methods (Complete)

**Priority:** High  
**Complexity:** Medium  
**Dependencies:** 1.1 Project Indexer

Show documentation for user-defined functions and methods on hover.

**Requirements:**

- Identify function/method under cursor
- Look up definition in index
- Extract documentation comments (javadoc-style or equivalent)
- Display signature with type hints
- Display parameter info
- Display containing class (for methods)
- Display file location
- Handle method overrides (show parent info if relevant)

**Documentation Comment Parsing:**

Support BoxLang's documentation format (likely similar to CFDoc):

```java
/**
 * Retrieves a user by their unique identifier.
 *
 * @param id The user's unique ID
 * @return The User object if found, null otherwise
 * @throws UserNotFoundException If user doesn't exist
 */
function getUser(required numeric id) {
    // ...
}
```

Parse and display:

- Description
- `@param` tags
- `@return` tag
- `@throws` tags
- `@deprecated` tag
- `@since` tag
- `@author` tag

---

### 1.9 Hover - Variables (Complete)

**Priority:** Medium  
**Complexity:** Medium  
**Dependencies:** 1.1 Project Indexer, scope analysis

Show information about variables on hover.

**Requirements:**

- Identify variable under cursor
- Determine variable scope (local, arguments, this, variables, etc.)
- Find declaration/assignment site
- Show inferred type if possible
- Show scope information
- Link to declaration location

**Display:**

```markdown
**user** (local variable)

Type: `User` (inferred)  
Declared: line 15  
Scope: local
```

**Scope-Aware Analysis:**

BoxLang likely has scopes similar to CFML. Track variables in:

- `local` scope
- `arguments` scope
- `this` scope (instance)
- `variables` scope (instance)
- `static` scope (if supported)
- Global scope

---

### 1.10 Hover - Classes and Interfaces (Complete)

**Priority:** Medium  
**Complexity:** Low  
**Dependencies:** 1.1 Project Indexer

Show information about classes and interfaces on hover.

**Requirements:**

- Identify class/interface reference under cursor
- Look up in index
- Display class documentation
- Display inheritance chain
- Display implemented interfaces
- Display file location
- Display modifier info (abstract, final, etc.)

**Display:**

```markdown
**UserService** (class)

```boxlang
class UserService extends BaseService implements IUserService
```

Service class for user management operations.

**Extends:** `BaseService`  
**Implements:** `IUserService`  
**File:** `services/UserService.bx`

---

### 1.11 Signature Help (Complete)

**Priority:** High  
**Complexity:** Medium  
**Dependencies:** 1.7, 1.8 (Hover infrastructure)

Display parameter hints while typing function calls.

**Requirements:**

- Trigger on `(` after function name
- Trigger on `,` within function call
- Track cursor position to highlight active parameter
- Support BIFs and user-defined functions
- Support method calls
- Support constructor calls (`new ClassName(...)`)
- Handle named arguments
- Handle optional parameters
- Support function overloads (multiple signatures)

**Implementation:**

1. Parse the partial expression to identify:
   - Function name
   - Current argument index
   - Whether named arguments are being used

2. Look up function signature(s)

3. Return `SignatureHelp` with:
   - List of signatures (for overloads)
   - Active signature index
   - Active parameter index

**Display:**

```

arraySlice(array, start, [length]) → Array
          ~~~~~~
          │
          └── Currently filling this parameter

(1/1) Parameters:
• array (Array) — The source array
• start (Numeric) — Starting index (1-based)  ← active
• length (Numeric, optional) — Number of elements

```

**Named Argument Support:**

When user types `foo(name = |)`, show remaining available named parameters.

---

### 1.12 Document Sync Improvements (Complete)

**Priority:** Medium  
**Complexity:** Low  
**Dependencies:** None

Ensure robust document synchronization for all file types.

**Requirements:**

- Support incremental sync (TextDocumentSyncKind.Incremental)
- Handle all three file types correctly
- Properly manage document versions
- Handle rapid edits without losing updates
- Trigger re-parsing and diagnostic updates on change
- Debounce expensive operations

**Implementation Notes:**

- Use incremental sync to avoid resending full document on each keystroke
- Maintain internal document model that applies incremental changes
- Re-parse and re-analyze after a debounce period (e.g., 300ms of no edits)
- Ensure thread-safety if processing on background threads

---

## Phase 2: Navigation

**Goal:** Enable developers to quickly navigate through codebases with Go to Definition, Find References, and symbol search.

**Estimated Duration:** 3-4 weeks

---

### 2.1 Go to Definition - Local Variables (Complete)

**Priority:** High  
**Complexity:** Medium  
**Dependencies:** Scope analysis

Navigate from variable usage to its declaration.

**Requirements:**

- Identify variable at cursor position
- Determine variable scope
- Find declaration site within current function/scope
- Return location of first assignment or formal declaration
- Handle shadowed variables correctly (find the right scope's declaration)

**Scope Rules:**

- Function parameters are declared in the function signature
- Local variables are declared at first assignment
- Handle `var` keyword if BoxLang uses explicit declaration

---

### 2.2 Go to Definition - Functions and Methods (Complete)

**Priority:** High  
**Complexity:** Medium  
**Dependencies:** 1.1 Project Indexer

Navigate from function/method call to its definition.

**Requirements:**

- Identify function call at cursor position
- For BIFs: optionally open browser to documentation (or return no result)
- For user functions in same file: return location in file
- For user functions in other files: return location in that file
- For methods: resolve the class and find method definition
- Handle inherited methods (go to actual definition, not override)
- Handle static vs instance methods

**Method Resolution:**

1. Determine receiver type (if method call)
2. Look up method in that class
3. If not found, walk up inheritance chain
4. Return location of actual definition

---

### 2.3 Go to Definition - Classes and Interfaces (Complete)

**Priority:** High  
**Complexity:** Low  
**Dependencies:** 1.1 Project Indexer

Navigate from class/interface reference to its definition file.

**Requirements:**

- Identify class reference at cursor
- Handle references in:
  - `new ClassName()`
  - `extends ClassName`
  - `implements InterfaceName`
  - Type hints: `function foo(User user)`
  - Variable type hints: `User user = ...`
- Look up class in index
- Return location of class definition (the `class` or `interface` keyword line)

---

### 2.4 Go to Definition - Properties (Complete)

**Priority:** Medium  
**Complexity:** Medium  
**Dependencies:** 1.1 Project Indexer

Navigate from property access to its declaration.

**Requirements:**

- Identify property access at cursor (`variables.propertyName` or `propertyName`)
  - Properties are considered private within a BoxLang class so you shouldn't naviagte across files for properties unless it is inheritance based
- Resolve the object's type
- Find property declaration in class
- Handle inherited properties
- Handle accessor methods (getter/setter)

---

### 2.5 Go to Definition - Imports (Complete)

**Priority:** Medium
**Complexity:** Low
**Dependencies:** 1.1 Project Indexer

Navigate from import statement to the imported file.

**Requirements:**

- ✅ Identify import statement at cursor
- ✅ Resolve import path to actual file
- ✅ Handle simple imports (`import ClassName;`)
- ✅ Handle aliased imports (`import ClassName as Alias;`)
- ✅ Java imports return empty (no source to navigate to)

---

### 2.6 Find References - Core Implementation (Complete)

**Priority:** High  
**Complexity:** High  
**Dependencies:** 1.1 Project Indexer

Find all usages of a symbol across the workspace.

**Requirements:**

- Support finding references for:
  - Classes/Interfaces
  - Methods/Functions
  - Properties
  - Variables (within scope)
- Search across all `.bx`, `.bxs`, and `.bxm` files
- Include the declaration as a reference (optionally, per LSP params)
- Return accurate positions for each reference

**Implementation Approach:**

1. **On-demand scanning:** When references requested, scan relevant files
   - Slower but always accurate
   - May need progress reporting for large workspaces

2. **Pre-computed references:** Build reference graph during indexing
   - Faster queries but more memory
   - Need to keep graph updated on file changes

3. **Hybrid:** Index class/method references but compute variable references on-demand

**Performance Considerations:**

- Use parallel file scanning
- Implement cancellation support (user may request again or cancel)
- Consider limiting scope (current file, current directory, whole workspace)
- Show progress for large workspaces

---

### 2.7 Find References - Include BXM Templates (Complete)

**Priority:** Medium  
**Complexity:** Medium  
**Dependencies:** 2.6

Ensure Find References searches template files as well.

**Requirements:**

- Parse `.bxm` files for BoxLang expressions
- Find references within `##` expressions or equivalent
- Find references in tag attributes
- Handle the dual nature of BXM (HTML + BoxLang)

---

### 2.8 Workspace Symbols (Complete)

**Priority:** Medium  
**Complexity:** Medium  
**Dependencies:** 1.1 Project Indexer

Search for symbols across the entire workspace.

**Requirements:**

- Support fuzzy matching
- Return results quickly (use index)
- Include symbol kind in results
- Include container name (class for methods)
- Support filtering by kind
- Handle empty query (return all symbols, perhaps limited)

**Symbol Kinds to Include:**

- Classes
- Interfaces
- Methods
- Functions
- Properties
- Constants (if applicable)

**Scoring/Ranking:**

- Exact matches first
- Prefix matches before substring matches
- Shorter names may rank higher
- Recently accessed files may rank higher (if tracking)

---

### 2.9 Document Symbols - Hierarchical Improvements (Complete)

**Priority:** Medium  
**Complexity:** Low  
**Dependencies:** Existing implementation

Enhance the existing document outline with better hierarchy and detail.

**Requirements:**

- Ensure proper parent/child nesting:
  - Class contains methods, properties
  - Function contains local functions (if supported)
- Use appropriate `SymbolKind` for each element
- Include detail string (type hints, parameter summary)
- Ensure consistent ordering (properties, constructor, methods, etc.)
- Support all three file types

**Symbol Kinds:**

- `SymbolKind.Class` for classes
- `SymbolKind.Interface` for interfaces
- `SymbolKind.Method` for methods
- `SymbolKind.Property` for properties
- `SymbolKind.Function` for standalone functions
- `SymbolKind.Variable` for significant variables
- `SymbolKind.Constructor` for constructors

---

### 2.10 Go to Type Definition (Complete)

**Priority:** Medium  
**Complexity:** Medium  
**Dependencies:** 1.1 Project Indexer, type inference

Navigate from a variable to its type's definition.

**Requirements:**

- Identify variable at cursor
- Determine variable's type (from hint or inference)
- Navigate to that type's definition

**Example:**

```java
var user = userService.getUser(id);
//  ↑ cursor here
// Go to Type Definition → opens User.bx
```

**Implementation:**

1. Find variable
2. Get its type (from type hint or inferred from assignment)
3. Look up type in class index
4. Return location

---

### 2.11 Go to Implementation (Complete)

**Priority:** Medium
**Complexity:** Medium
**Dependencies:** 1.1 Project Indexer (with interface tracking)

Navigate from interface/abstract method to concrete implementations.

**Requirements:**

- ✅ From interface method → all implementing class methods
- ✅ From abstract method → all overriding methods
- ✅ From interface → all implementing classes
- ✅ Return multiple locations (client will show picker)

**Example:**

```java
interface IRepository {
    function findById(id);
    //       ↑ Go to Implementation
    // Shows: UserRepository.findById, ProductRepository.findById
}
```

---

## Phase 3: Completion Excellence

**Goal:** Build a world-class autocompletion system that understands context and provides highly relevant suggestions.

**Estimated Duration:** 4-6 weeks

---

### 3.1 Completion - Context Detection Framework (Complete)

**Priority:** Critical  
**Complexity:** High  
**Dependencies:** AST analysis

Build a system to accurately determine the completion context at cursor position.

**Completion Contexts:**

| Trigger | Context | What to Complete |
|---------|---------|------------------|
| `obj.` | Member access | Methods, properties of obj's type |
| `ClassName.` | Static access | Static methods, constants |
| `new` | Constructor | Class names |
| `extends` | Inheritance | Class names |
| `implements` | Implementation | Interface names |
| `import` | Import | Package/class paths |
| `func(` | Argument | Parameter hints, local vars |
| `<bx:` | BXM tag | Tag names |
| `#` | Template expr | Variables, functions |
| Plain identifier | General | Keywords, variables, functions, classes |

**Implementation:**

Create a `CompletionContext` class that analyzes the AST and cursor position:

```java
class CompletionContext {
    CompletionContextKind kind;  // MEMBER_ACCESS, STATIC_ACCESS, etc.
    String triggerText;          // Text before cursor
    TypeInfo receiverType;       // Type of object before dot (if applicable)
    MethodInfo containingMethod; // Method we're inside (for locals/params)
    ClassInfo containingClass;   // Class we're inside
    int argumentIndex;           // If in function call, which argument
    // ... etc
}
```

---

### 3.2 Completion - Member Access (Dot Completion) (Complete)

**Priority:** High  
**Complexity:** High  
**Dependencies:** 3.1, Type inference

Complete members after dot operator.

**Requirements:**

- Determine type of expression before dot
- List methods and properties of that type
- Include inherited members
- Filter by visibility (don't show private members of other classes)
- Sort by relevance (own members before inherited)
- Handle chained calls: `a.b.c.|`

**Type Inference Challenges:**

Since BoxLang is dynamic, type inference is best-effort:

- Use explicit type hints when available
- Infer from `new ClassName()`
- Infer from method return type hints
- Infer from parameter type hints
- Fall back to showing all possible completions for `any` type

---

### 3.3 Completion - Scope-Aware Variables (Complete)

**Priority:** High  
**Complexity:** Medium  
**Dependencies:** Scope analysis

Complete variables available in current scope.

**Requirements:**

- Local variables (declared before cursor in current function)
- Function parameters
- `this` properties (in class context)
- `variables` scope (if applicable)
- `arguments` scope
- Loop variables
- Catch block exception variables

**Scope Prefix Completion:**

When user types a scope prefix, complete only from that scope:

- `this.` → instance properties and methods
- `variables.` → variables scope
- `arguments.` → function arguments
- `local.` → local scope (if explicit)

---

### 3.4 Completion - Functions (BIFs and UDFs) (Complete)

**Priority:** High  
**Complexity:** Medium  
**Dependencies:** 1.7 BIF data, 1.1 Project Index

Complete function names.

**Requirements:**

- Complete BIF names with full detail
- Complete user-defined functions from current file
- Complete user-defined functions from imports/includes
- Show signature in completion detail
- Show documentation in completion documentation
- Handle function name conflicts (prefer local over imported over BIF)

**Completion Item Properties:**

```java
CompletionItem item = new CompletionItem("arrayAppend");
item.setKind(CompletionItemKind.Function);
item.setDetail("(array, value) → Array");
item.setDocumentation(markupContent); // Full docs
item.setInsertText("arrayAppend($1)");
item.setInsertTextFormat(InsertTextFormat.Snippet);
```

---

### 3.5 Completion - Classes and Types (Complete)

**Priority:** High  
**Complexity:** Medium  
**Dependencies:** 1.1 Project Index

Complete class and interface names in appropriate contexts.

**Contexts:**

- After `new` keyword
- After `extends` keyword
- After `implements` keyword
- In type hint positions
- In import statements

**Requirements:**

- Search all classes in index
- Include classes from dependencies (CommandBox)
- Show file path in detail (helps distinguish duplicates)
- Auto-add import on completion (if not already imported)

---

### 3.6 Completion - Keywords (Complete)

**Priority:** Medium  
**Complexity:** Low  
**Dependencies:** 3.1 Context Detection

Complete BoxLang keywords in appropriate contexts.

**Keywords by Context:**

- Top level: `class`, `interface`, `abstract`, `final`, `import`
- Class body: `function`, `property`, `static`, `private`, `public`, `remote`
- Function body: `var`, `if`, `else`, `for`, `while`, `do`, `switch`, `try`, `catch`, `finally`, `throw`, `return`, `break`, `continue`
- Expressions: `new`, `true`, `false`, `null`

**Implementation:**

Map contexts to applicable keywords and only suggest contextually appropriate ones.

---

### 3.7 Completion - Snippets (Incomplete)

**Priority:** Medium  
**Complexity:** Low  
**Dependencies:** None

Provide snippet completions for common patterns.

**Snippet Examples:**

```
// Function definition
fun → 
function ${1:name}(${2:params}) {
    $0
}

// Class definition
class →
class ${1:ClassName} {
    $0
}

// If statement
if →
if (${1:condition}) {
    $0
}

// For loop
for →
for (var ${1:i} = ${2:1}; $1 <= ${3:10}; $1++) {
    $0
}

// Try/catch
try →
try {
    $0
} catch (any e) {
    
}

// Property with getter/setter
prop →
property ${1:type} ${2:name};
```

---

### 3.8 Completion - Arguments in Function Calls (Incomplete)

**Priority:** High  
**Complexity:** Medium  
**Dependencies:** 3.1, Signature Help

Smart completion when inside function call parentheses.

**Requirements:**

- Suggest variables that match expected parameter type
- Suggest named argument syntax: `paramName:`
- Show remaining required parameters
- For boolean parameters, suggest `true`/`false`
- For enum-like parameters, suggest known values

**Named Argument Completion:**

When user types inside parens, offer named argument options:

```
myFunc(|)
       ↓
       • name: 
       • age: 
       • active: 
```

---

### 3.9 Completion - Import Paths (Incomplete)

**Priority:** Medium  
**Complexity:** Medium  
**Dependencies:** File system scanning, 1.1 Index

Complete import paths as user types.

**Requirements:**

- Complete directory names
- Complete file names (without extension)
- Support both relative and absolute paths
- Handle package-style imports
- Include CommandBox dependencies

---

### 3.10 Completion - BXM Tags and Attributes (Incomplete)

**Priority:** Medium  
**Complexity:** Medium  
**Dependencies:** BXM tag definitions

Complete BoxLang tags and their attributes in template files.

**Requirements:**

- Complete tag names after `<bx:`
- Complete attribute names within tags
- Complete attribute values where known
- Handle self-closing vs container tags
- Show attribute documentation

**Tag Database:**

```json
{
    "bx:output": {
        "description": "Outputs content to the page",
        "attributes": {
            "encodefor": {
                "type": "string",
                "values": ["html", "javascript", "css", "url"],
                "description": "Encoding type"
            }
        },
        "hasBody": true
    }
}
```

---

### 3.11 Completion - Template Expressions (BXM) (Incomplete)

**Priority:** Medium  
**Complexity:** Medium  
**Dependencies:** 3.1, scope analysis

Complete variables and functions inside BXM expressions.

**Requirements:**

- Detect when cursor is inside `##` or equivalent
- Complete variables in template scope
- Complete functions
- Handle nested expressions
- Context-aware (attribute vs content)

---

### 3.12 Completion Item Resolve (Incomplete)

**Priority:** Medium  
**Complexity:** Low  
**Dependencies:** 3.1-3.11

Implement lazy-loading of completion item details.

**Requirements:**

- Return minimal completion items initially (label, kind, basic detail)
- Load full documentation on `completionItem/resolve`
- Cache resolved items
- Load from disk/index as needed

**Benefits:**

- Faster initial completion response
- Less memory for large completion lists
- Only load docs for items user is interested in

---

### 3.13 Completion - Auto-Import (Incomplete)

**Priority:** Medium  
**Complexity:** Medium  
**Dependencies:** 3.5

Automatically add import statements when completing unimported classes.

**Requirements:**

- Detect when completed class is not imported
- Add import statement at appropriate location
- Handle existing imports (don't duplicate)
- Use `additionalTextEdits` in completion item
- Handle multiple classes with same name (prompt for which)

**Implementation:**

```java
CompletionItem item = new CompletionItem("UserService");
item.setKind(CompletionItemKind.Class);
item.setDetail("services.UserService");

// Add the import
TextEdit importEdit = new TextEdit(
    importInsertPosition,
    "import services.UserService;\n"
);
item.setAdditionalTextEdits(Arrays.asList(importEdit));
```

---

### 3.14 Completion - Sorting and Relevance (Incomplete)

**Priority:** Medium  
**Complexity:** Medium  
**Dependencies:** All completion features

Implement intelligent sorting of completion items.

**Sorting Criteria:**

1. Scope proximity (local > class > inherited > global > imported)
2. Type match (if expected type is known)
3. Usage frequency (if tracking)
4. Recently used
5. Alphabetical (as tiebreaker)

**Implementation:**

Use `sortText` property of CompletionItem:

- Prefix with priority digits: `"0_localVar"`, `"1_classMethod"`, `"2_inheritedMethod"`
- Use consistent padding for proper string sorting

**Filter Text:**

Set `filterText` to enable fuzzy matching:

- "getUserById" should match "gubi", "getUser", "ById"

---

## Phase 4: Refactoring

**Goal:** Enable safe, automated code transformations with rename and code actions.

**Estimated Duration:** 3-4 weeks

---

### 4.1 Rename - Prepare Rename (Incomplete)

**Priority:** High  
**Complexity:** Low  
**Dependencies:** None

Validate that a rename is possible and return the renameable range.

**Requirements:**

- Check if symbol under cursor can be renamed
- Return the range of the symbol to rename
- Reject renaming of:
  - Keywords
  - BIFs
  - Symbols from dependencies (external packages)
  - Special variables (if any)

**Response:**

- If renameable: return range (the client will show rename UI)
- If not renameable: return error with reason

---

### 4.2 Rename - Local Variables (Incomplete)

**Priority:** High  
**Complexity:** Medium  
**Dependencies:** Scope analysis, 2.6 Find References

Rename local variables within their scope.

**Requirements:**

- Find all references within scope
- Update all occurrences
- Validate new name:
  - Not a keyword
  - Not already in use in same scope
  - Valid identifier
- Handle shadowed variables correctly

---

### 4.3 Rename - Functions and Methods (Incomplete)

**Priority:** High  
**Complexity:** High  
**Dependencies:** 2.6 Find References

Rename functions and methods across the workspace.

**Requirements:**

- Find all call sites
- Find definition
- Update all occurrences
- Handle method overrides (rename in subclasses too?)
- Handle interface implementations
- Validate new name doesn't conflict

**Override Handling:**

Offer options via code action:

- Rename only this method
- Rename this method and all overrides
- Rename entire hierarchy

---

### 4.4 Rename - Classes (Incomplete)

**Priority:** High  
**Complexity:** High  
**Dependencies:** 2.6 Find References

Rename classes across the workspace.

**Requirements:**

- Update class definition
- Update all references:
  - `new ClassName()`
  - Type hints
  - Extends/implements
  - Imports
- Optionally rename file (if file name matches class name)
- Update constructor name (if applicable)

**File Rename:**

Use workspace edit with file rename operation:

```java
RenameFile renameOp = new RenameFile(
    oldUri,
    newUri
);
workspaceEdit.setDocumentChanges(Arrays.asList(renameOp, textEdits));
```

---

### 4.5 Rename - Properties (Incomplete)

**Priority:** Medium  
**Complexity:** Medium  
**Dependencies:** 2.6 Find References

Rename properties and update all usages.

**Requirements:**

- Update property declaration
- Update all property accesses
- Update getter/setter method names (if convention-based)
- Handle inherited property access

---

### 4.6 Code Actions - Quick Fix: Add Import (Incomplete)

**Priority:** High  
**Complexity:** Medium  
**Dependencies:** 1.5 Diagnostics, 1.1 Index

Suggest adding import for unresolved class references.

**Trigger:** Diagnostic "Unknown class 'UserService'"

**Action:**

```
Quick Fix: Import 'services.UserService'
```

**Requirements:**

- Find matching classes in index
- If multiple matches, offer multiple quick fixes
- Insert import at correct location
- Handle existing imports (don't duplicate)

---

### 4.7 Code Actions - Quick Fix: Create Function (Incomplete)

**Priority:** Medium  
**Complexity:** Medium  
**Dependencies:** 1.5 Diagnostics

Suggest creating a function when calling an undefined one.

**Trigger:** Diagnostic "Unknown function 'calculateTotal'"

**Action:**

```
Quick Fix: Create function 'calculateTotal'
```

**Implementation:**

- Infer parameter types from call arguments
- Insert function stub at appropriate location
- Use snippet placeholders for easy editing

**Generated Code:**

```java
function calculateTotal(items) {
    // TODO: Implement
    throw "Not implemented";
}
```

---

### 4.8 Code Actions - Quick Fix: Create Property (Incomplete)

**Priority:** Medium  
**Complexity:** Medium  
**Dependencies:** 1.5 Diagnostics

Suggest creating a property for undefined property access.

**Trigger:** Diagnostic "Unknown property 'userName' on class User"

**Action:**

```
Quick Fix: Create property 'userName' in User
```

---

### 4.9 Code Actions - Quick Fix: Fix Argument Count (Incomplete)

**Priority:** Medium  
**Complexity:** Low  
**Dependencies:** 1.5 Diagnostics

Help fix incorrect argument counts.

**Trigger:** Diagnostic "Function 'foo' expects 3 arguments, got 2"

**Actions:**

```
Quick Fix: Add missing argument
Quick Fix: View function signature
```

---

### 4.10 Code Actions - Refactor: Extract Variable (Incomplete)

**Priority:** Medium  
**Complexity:** Medium  
**Dependencies:** AST manipulation

Extract selected expression into a variable.

**Selection:** `user.getFullName().toUpperCase()`

**Result:**

```java
var fullNameUpper = user.getFullName().toUpperCase();
// ... use fullNameUpper where expression was
```

**Requirements:**

- Determine appropriate variable name
- Find all identical expressions in scope (optionally replace all)
- Insert variable declaration before first usage
- Use snippet for variable name editing

---

### 4.11 Code Actions - Refactor: Extract Method (Incomplete)

**Priority:** Medium  
**Complexity:** High  
**Dependencies:** AST manipulation, scope analysis

Extract selected statements into a new method.

**Requirements:**

- Identify variables used in selection (become parameters)
- Identify variables modified in selection (become return values)
- Create method with appropriate signature
- Replace selection with method call
- Handle multiple return values (return struct?)

---

### 4.12 Code Actions - Refactor: Inline Variable (Incomplete)

**Priority:** Low  
**Complexity:** Medium  
**Dependencies:** 2.6 Find References

Replace variable with its value at all usage sites.

**Requirements:**

- Variable must be assigned exactly once
- Replace all usages with the assigned expression
- Remove variable declaration
- Warn if expression has side effects

---

### 4.13 Code Actions - Source: Organize Imports (Incomplete)

**Priority:** Medium  
**Complexity:** Low  
**Dependencies:** None

Sort and clean up import statements.

**Actions:**

- Sort imports alphabetically
- Remove unused imports
- Remove duplicate imports
- Group imports by package (optional)

---

### 4.14 Code Actions - Source: Generate Constructor (Incomplete)

**Priority:** Medium  
**Complexity:** Medium  
**Dependencies:** Class analysis

Generate a constructor that initializes properties.

**Trigger:** Anywhere in class body, or from lightbulb menu

**Generated Code:**

```java
function init(name, age, email) {
    this.name = name;
    this.age = age;
    this.email = email;
    return this;
}
```

**Options:**

- Include all properties
- Select which properties to include
- Generate validation

---

### 4.15 Code Actions - Source: Implement Interface (Incomplete)

**Priority:** Medium  
**Complexity:** Medium  
**Dependencies:** 1.1 Index

Generate stubs for interface methods.

**Trigger:** On class that implements interface but is missing methods

**Requirements:**

- Find all methods required by interface
- Find which are already implemented
- Generate stubs for missing methods
- Include parameter signatures from interface

---

### 4.16 Code Actions - Source: Override Method (Incomplete)

**Priority:** Medium  
**Complexity:** Medium  
**Dependencies:** 1.1 Index

Generate method override from parent class.

**Trigger:** Inside class that extends another class

**Shows list of overridable methods from parent**

**Generated Code:**

```java
function process(data) {
    // Call parent implementation
    super.process(data);
    
    // Add custom logic
}
```

---

## Phase 5: Polish and Visual Enhancements

**Goal:** Add visual features that make the IDE experience feel premium and informative.

**Estimated Duration:** 4-6 weeks

---

### 5.1 Semantic Tokens - Token Provider (Incomplete)

**Priority:** High  
**Complexity:** Medium  
**Dependencies:** AST analysis

Implement semantic token provider for enhanced syntax highlighting.

**Token Types to Support:**

| Token Type | Usage |
|------------|-------|
| `namespace` | Package names |
| `class` | Class names |
| `interface` | Interface names |
| `enum` | Enum names (if supported) |
| `type` | Type names in hints |
| `parameter` | Function parameters |
| `variable` | Local variables |
| `property` | Class properties |
| `function` | Function names |
| `method` | Method names |
| `keyword` | Language keywords |
| `modifier` | public, private, static, etc. |
| `comment` | Comments |
| `string` | String literals |
| `number` | Numeric literals |
| `operator` | Operators |
| `decorator` | Annotations (if supported) |

**Token Modifiers:**

| Modifier | Meaning |
|----------|---------|
| `declaration` | Symbol is being declared |
| `definition` | Symbol is being defined |
| `readonly` | Read-only variable |
| `static` | Static member |
| `deprecated` | Deprecated symbol |
| `modification` | Variable is being modified |
| `documentation` | Documentation comment |
| `defaultLibrary` | Built-in function |

---

### 5.2 Semantic Tokens - Scope-Based Coloring (Incomplete)

**Priority:** Medium  
**Complexity:** Medium  
**Dependencies:** 5.1, Scope analysis

Use semantic tokens to differentiate variable kinds.

**Examples:**

- Parameters colored differently from locals
- Instance properties (`this.x`) colored differently from locals
- Static members have distinct color
- BIFs colored differently from user functions

**Implementation:**

Walk AST and emit tokens with appropriate types and modifiers:

```java
// For a parameter reference
emitToken(position, length, TokenType.PARAMETER, TokenModifier.NONE);

// For a property access
emitToken(position, length, TokenType.PROPERTY, TokenModifier.NONE);

// For a deprecated function call
emitToken(position, length, TokenType.FUNCTION, TokenModifier.DEPRECATED);
```

---

### 5.3 Semantic Tokens - Incremental Updates (Incomplete)

**Priority:** Medium  
**Complexity:** High  
**Dependencies:** 5.1

Support incremental semantic token updates for performance.

**Requirements:**

- Track previous token state
- On edit, compute delta
- Send only changed tokens
- Handle insertions, deletions, modifications

**Protocol:**

Use `textDocument/semanticTokens/full/delta` request.

---

### 5.4 Inlay Hints - Parameter Names (Incomplete)

**Priority:** High  
**Complexity:** Medium  
**Dependencies:** Signature Help, AST analysis

Show parameter names at call sites.

**Example:**

```java
// Written code:
createUser("John", 25, true);

// Displayed with hints:
createUser(/* name: */ "John", /* age: */ 25, /* active: */ true);
```

**Requirements:**

- Identify function calls
- Look up parameter names
- Position hints before each argument
- Make hints unobtrusive (use InlayHintKind.Parameter)
- Skip hints for obvious cases (argument name matches parameter name)

**Configuration:**

```json
{
    "boxlang.inlayHints.parameterNames.enabled": true,
    "boxlang.inlayHints.parameterNames.hideForSingleParameter": true,
    "boxlang.inlayHints.parameterNames.hideForLiterals": false
}
```

---

### 5.5 Inlay Hints - Inferred Types (Incomplete)

**Priority:** Medium  
**Complexity:** Medium  
**Dependencies:** Type inference

Show inferred types for variables without explicit type hints.

**Example:**

```java
// Written code:
var user = userService.getUser(id);

// Displayed with hint:
var user /* : User */ = userService.getUser(id);
```

**Requirements:**

- Identify variable declarations without type hints
- Infer type from assignment
- Display type after variable name
- Only show when type can be confidently inferred

**Configuration:**

```json
{
    "boxlang.inlayHints.variableTypes.enabled": false,
    "boxlang.inlayHints.variableTypes.hideForObviousTypes": true
}
```

---

### 5.6 Inlay Hints - Return Types (Incomplete)

**Priority:** Low  
**Complexity:** Medium  
**Dependencies:** Type inference

Show return types for functions without explicit return type hints.

**Example:**

```java
// Written code:
function getUser(id) {

// Displayed with hint:
function getUser(id) /* : User */ {
```

---

### 5.7 Code Lens - Reference Counts (Incomplete)

**Priority:** Medium  
**Complexity:** Medium  
**Dependencies:** 2.6 Find References

Show reference counts above functions and classes.

**Display:**

```java
            // 5 references
function processOrder(order) {
```

**Requirements:**

- Count references for functions, methods, classes
- Display above definition
- Make clickable (triggers Find References)
- Update on file changes (or debounce)
- Consider performance (don't count on every keystroke)

**Configuration:**

```json
{
    "boxlang.codeLens.referenceCounts.enabled": true
}
```

---

### 5.8 Code Lens - Implementations Count (Incomplete)

**Priority:** Low  
**Complexity:** Medium  
**Dependencies:** 2.11 Go to Implementation

Show implementation count for interfaces and abstract methods.

**Display:**

```java
            // 3 implementations
interface IRepository {
            // 3 implementations  
    function findById(id);
}
```

---

### 5.9 Code Lens - Run/Debug Test (Incomplete)

**Priority:** Medium  
**Complexity:** Medium  
**Dependencies:** Test framework detection

Show run/debug actions for test methods.

**Display:**

```java
            // ▶ Run | 🐛 Debug
function testUserCreation() {
```

**Requirements:**

- Detect test files/methods (by naming convention or annotation)
- Execute test via configured test runner
- Integrate with CommandBox test running if applicable

---

### 5.10 Document Highlight (Incomplete)

**Priority:** Medium  
**Complexity:** Low  
**Dependencies:** Scope analysis

Highlight all occurrences of symbol under cursor.

**Requirements:**

- Highlight all references in current document
- Use `DocumentHighlightKind.Read` for read access
- Use `DocumentHighlightKind.Write` for write access
- Scope-aware (don't highlight same-named variables in different scopes)

---

### 5.11 Folding Ranges (Incomplete)

**Priority:** Medium  
**Complexity:** Low  
**Dependencies:** AST analysis

Define foldable regions in documents.

**Foldable Elements:**

- Classes
- Functions/methods
- If/else blocks
- For/while loops
- Try/catch/finally blocks
- Switch statements
- Multi-line comments
- Documentation comments
- Import blocks
- Region markers: `// #region` / `// #endregion`
- BXM tags with content

**Implementation:**

Walk AST and emit FoldingRange for each foldable construct:

```java
FoldingRange range = new FoldingRange(startLine, endLine);
range.setKind(FoldingRangeKind.Region); // or Comment, Imports
```

---

### 5.12 Selection Range (Incomplete)

**Priority:** Low  
**Complexity:** Medium  
**Dependencies:** AST analysis

Support smart selection expansion.

**Selection Hierarchy (inner to outer):**

1. Word
2. String content (inside quotes)
3. String (including quotes)
4. Expression
5. Statement
6. Block
7. Function body
8. Function (including signature)
9. Class body
10. Class (including declaration)
11. File

**Implementation:**

Return nested SelectionRange objects representing the hierarchy.

---

### 5.13 Linked Editing Ranges (Incomplete)

**Priority:** Low  
**Complexity:** Low  
**Dependencies:** None

Edit paired elements together (primarily for BXM).

**Example:**

```html
<bx:output>  <!-- editing "output" here -->
    ...
</bx:output>  <!-- automatically updates here too -->
```

**Requirements:**

- Identify paired elements (opening/closing tags)
- Return ranges that should be edited together

---

### 5.14 Document Links (Incomplete)

**Priority:** Low  
**Complexity:** Low  
**Dependencies:** None

Make paths in code clickable.

**Linkable Elements:**

- Import paths
- Include paths
- File path strings
- URLs in comments
- Documentation URLs

**Implementation:**

Scan document for linkable patterns, return DocumentLink objects with target URIs.

---

## Phase 6: Advanced Features

**Goal:** Add power-user features that provide deep code intelligence.

**Estimated Duration:** Ongoing

---

### 6.1 Call Hierarchy - Incoming Calls (Incomplete)

**Priority:** Medium  
**Complexity:** High  
**Dependencies:** 2.6 Find References

Show all callers of a function.

**Requirements:**

- `textDocument/prepareCallHierarchy` - identify callable at position
- `callHierarchy/incomingCalls` - find all callers
- Support recursive expansion (callers of callers)
- Return call site locations

**Data Structure:**

```java
CallHierarchyItem item = new CallHierarchyItem();
item.setName("processOrder");
item.setKind(SymbolKind.Method);
item.setUri(fileUri);
item.setRange(methodRange);
item.setSelectionRange(nameRange);
// Store data for resolving calls
item.setData(methodIdentifier);
```

---

### 6.2 Call Hierarchy - Outgoing Calls (Incomplete)

**Priority:** Medium  
**Complexity:** Medium  
**Dependencies:** AST analysis

Show all functions called by a function.

**Requirements:**

- `callHierarchy/outgoingCalls` - find all callees
- Walk function body for call expressions
- Resolve called functions
- Return call site locations within the function

---

### 6.3 Type Hierarchy - Supertypes (Incomplete)

**Priority:** Medium  
**Complexity:** Medium  
**Dependencies:** 1.1 Index

Show inheritance chain upward.

**Requirements:**

- `textDocument/prepareTypeHierarchy` - identify type at position
- `typeHierarchy/supertypes` - return parent class and interfaces
- Support recursive expansion

---

### 6.4 Type Hierarchy - Subtypes (Incomplete)

**Priority:** Medium  
**Complexity:** Medium  
**Dependencies:** 1.1 Index (with inheritance tracking)

Show classes that extend/implement a type.

**Requirements:**

- `typeHierarchy/subtypes` - return subclasses and implementors
- Support recursive expansion
- Handle interface implementations

---

### 6.5 BXM Template - Full Language Support (Incomplete)

**Priority:** Medium  
**Complexity:** High  
**Dependencies:** Multiple features

Ensure all LSP features work properly in `.bxm` template files.

**Requirements:**

- Completion in expressions
- Hover in expressions
- Go to Definition from expressions
- Diagnostics in expressions
- Proper handling of embedded HTML
- Tag-specific features

**Implementation Approach:**

Parse BXM as a composite document:

1. Extract BoxLang expression regions
2. Map positions between template and virtual BoxLang document
3. Delegate LSP operations to BoxLang handlers with position mapping

---

### 6.6 CommandBox Integration - Dependency Indexing (Incomplete)

**Priority:** Medium  
**Complexity:** Medium  
**Dependencies:** 1.1 Index

Index classes from CommandBox dependencies.

**Requirements:**

- Parse `box.json` for dependencies
- Locate installed dependencies
- Index public classes/interfaces
- Make available for completion and navigation
- Handle dependency updates

---

### 6.7 CommandBox Integration - Dependency Suggestions (Incomplete)

**Priority:** Low  
**Complexity:** Medium  
**Dependencies:** 6.6

Suggest installing packages for unresolved imports.

**Trigger:** Import for class not in workspace but available in ForgeBox

**Action:**

```
Quick Fix: Install 'coldbox' from ForgeBox
```

---

### 6.8 Formatting Provider (Incomplete)

**Priority:** Medium  
**Complexity:** Medium-High  
**Dependencies:** AST analysis or external formatter

Implement document formatting.

**Options:**

1. **Wrap existing formatter:** If BoxLang has a CLI formatter, invoke it
2. **Build formatter:** Use AST to reprint code with formatting rules

**Configurable Rules:**

- Indent size and style (tabs/spaces)
- Brace style (same line, new line)
- Space around operators
- Line length
- Blank lines between methods
- Import ordering

**Implementation:**

```java
@Override
public List<TextEdit> formatting(DocumentFormattingParams params) {
    String formatted = formatter.format(document.getText(), params.getOptions());
    return computeEdits(document.getText(), formatted);
}
```

---

### 6.9 Range Formatting (Incomplete)

**Priority:** Low  
**Complexity:** Medium  
**Dependencies:** 6.8

Format only a selected range.

**Requirements:**

- Format selected lines
- Maintain consistent indentation with surrounding code
- Handle partial constructs gracefully

---

### 6.10 On-Type Formatting (Incomplete)

**Priority:** Low  
**Complexity:** Medium  
**Dependencies:** 6.8

Auto-format as the user types.

**Triggers:**

- After `}` - fix indentation
- After `;` - optional line break or spacing
- After `{` - add newline and indent

---

### 6.11 Workspace Configuration (Incomplete)

**Priority:** Medium  
**Complexity:** Low  
**Dependencies:** None

Support configuration via workspace settings.

**Configuration Schema:**

```json
{
    "boxlang.diagnostics.enabled": true,
    "boxlang.diagnostics.severity": {
        "unusedVariable": "warning",
        "undefinedVariable": "hint",
        "deprecatedUsage": "warning"
    },
    "boxlang.completion.autoImport": true,
    "boxlang.completion.showDeprecated": false,
    "boxlang.inlayHints.enabled": true,
    "boxlang.codeLens.enabled": true,
    "boxlang.format.indentSize": 4,
    "boxlang.format.useTabs": false,
    "boxlang.index.excludePatterns": [
        "**/node_modules/**",
        "**/vendor/**"
    ]
}
```

**Implementation:**

- Request configuration via `workspace/configuration`
- React to `workspace/didChangeConfiguration`
- Apply settings to respective features

---

### 6.12 File Watching and Workspace Events (Incomplete)

**Priority:** Medium  
**Complexity:** Medium  
**Dependencies:** 1.1 Index

React to file system changes outside the editor.

**Events to Handle:**

- `workspace/didChangeWatchedFiles` - external file changes
- `workspace/didCreateFiles` - new files
- `workspace/didRenameFiles` - renamed files (update imports?)
- `workspace/didDeleteFiles` - deleted files

**Requirements:**

- Update index on external changes
- Refresh diagnostics for affected files
- Handle file renames gracefully

---

### 6.13 Will Rename Files - Auto-Update Imports (Incomplete)

**Priority:** Low  
**Complexity:** High  
**Dependencies:** 6.12, 2.6 Find References

Update imports when files are renamed.

**Requirements:**

- Intercept `workspace/willRenameFiles`
- Find all imports of the renamed file
- Update import paths
- Return workspace edit with changes

---

### 6.14 Progress Reporting (Incomplete)

**Priority:** Medium  
**Complexity:** Low  
**Dependencies:** None

Report progress for long-running operations.

**Operations to Report:**

- Initial workspace indexing
- Full workspace re-index
- Large refactoring operations

**Implementation:**

```java
WorkDoneProgressBegin begin = new WorkDoneProgressBegin();
begin.setTitle("Indexing BoxLang files");
begin.setCancellable(true);
begin.setPercentage(0);

client.notifyProgress(new ProgressParams(token, begin));

// During indexing...
WorkDoneProgressReport report = new WorkDoneProgressReport();
report.setMessage("Processing file " + fileName);
report.setPercentage(percent);
client.notifyProgress(new ProgressParams(token, report));

// When complete
WorkDoneProgressEnd end = new WorkDoneProgressEnd();
end.setMessage("Indexed " + fileCount + " files");
client.notifyProgress(new ProgressParams(token, end));
```

---

### 6.15 Execute Command - Custom Commands (Incomplete)

**Priority:** Low  
**Complexity:** Low  
**Dependencies:** None

Implement custom commands for BoxLang-specific actions.

**Potential Commands:**

- `boxlang.reindex` - Force full re-index
- `boxlang.clearCache` - Clear persistent cache
- `boxlang.showAst` - Show AST for current file (debugging)
- `boxlang.runFile` - Execute current BoxLang file
- `boxlang.openDocs` - Open BoxLang documentation

---

## Appendix A: Testing Strategy

### Unit Tests (Incomplete)

- AST parsing for various constructs
- Index query operations
- Completion context detection
- Scope analysis
- Type inference

### Integration Tests (Incomplete)

- Full LSP message round-trips
- Multi-file scenarios
- Index persistence and loading

### Snapshot Tests (Incomplete)

- Completion results for standard scenarios
- Hover content formatting
- Signature help display

### Client Tests (Incomplete)

- Test in VS Code with extension
- Test in JetBrains with plugin
- Test with generic LSP client

---

## Appendix B: Performance Targets

| Operation | Target |
|-----------|--------|
| Initial indexing (1000 files) | < 30 seconds |
| Incremental re-index (1 file) | < 100ms |
| Completion response | < 100ms |
| Hover response | < 50ms |
| Go to Definition | < 50ms |
| Find References (1000 files) | < 2 seconds |
| Diagnostics (single file) | < 200ms |

---

## Appendix C: Error Handling

### Graceful Degradation (Incomplete)

- If indexing fails for a file, continue with others
- If completion fails, return empty list (don't crash)
- Log errors for debugging but don't surface to user unless actionable

### User Feedback (Incomplete)

- Show notifications for critical errors
- Provide "report issue" action
- Include relevant context in error reports

---

## Appendix D: Resources

- [LSP Specification 3.17](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/)
- [lsp4j Documentation](https://github.com/eclipse/lsp4j)
- [BoxLang Documentation](https://boxlang.ortusbooks.com/)
- [VS Code Extension API](https://code.visualstudio.com/api)
- [JetBrains LSP Support](https://plugins.jetbrains.com/docs/intellij/language-server-protocol.html)
