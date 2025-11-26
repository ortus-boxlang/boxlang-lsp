---
applyTo: 'src/test/**'
---

Tests are written in Java using JUnit.

# Example Tests

The best test examples are 
- `src\test\java\ortus\boxlang\lsp\UnusedVariablesTest.java`
- `src\test\java\ortus\boxlang\lsp\SymbolProviderTest.java`
- `src\test\java\ortus\boxlang\lsp\UnscopedVariablesTest.java`

# Test Files

When you are implementing a test you may need to create a mock BoxLang file (`.bx`, `.bxs`, or `.bxm`) and then parse it within the test.

These test files should be placed in the `src\test\resources\files\` directory. You can create subdirectories as needed to organize your test files.