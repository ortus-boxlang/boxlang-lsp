# Unused Import Quick Fix

## What changed

Implemented the unused-import quick fix within the existing diagnostic and code-action flow, rather than adding a separate cleanup feature.

In `SemanticWarningDiagnosticVisitor.java`, each reported unused import now has an associated `quickfix` titled `Remove unused import`. The action references that import's diagnostic and supplies a text edit over the import's AST range. This keeps the edit focused on the reported statement, removing its semicolon without consuming a neighboring comment or line ending. A blank line can remain. Aliased imports are supported; wildcard imports are explicitly excluded.

Unused-import diagnostics now include a stable ID in their `data`. The existing action-matching path in `ProjectContextProvider` uses that ID to match an action diagnostic to the copy received back from an LSP client. The visitor also checks that the rule is enabled before exposing its actions. Existing diagnostic suppression filtering removes actions tied to suppressed diagnostics, so no new suppression mechanism was needed.

In `SemanticWarningDiagnosticsTest.java`, added service-level tests that request actions through `BoxLangTextDocumentService.codeAction()` for both `.bx` and `.bxs` source. The test represents returned diagnostic data as a JSON object to exercise client-style matching, applies the resulting edit, checks the complete source (including CRLF and adjacent comments), and reparses to ensure the unused warning is gone while the used aliased import remains. Additional tests verify that disabled and suppressed rules expose no action.

## Why these choices

- Keeping the feature in the semantic-warning visitor reuses the existing diagnostic/action lifecycle and avoids a parallel import-cleanup system.
- Tying the action to its diagnostic and ID ensures the editor only receives a fix for a diagnostic it requested, including when diagnostic data has crossed the LSP boundary.
- Editing the import's range only avoids deleting nearby comments, other imports, or line endings. It intentionally does not organize imports or provide a fix-all.
- Gating the action on the existing rule setting and suppression filter keeps quick fixes consistent with whether the warning is active.
- Exercising the text-document service, applying the edit, and reparsing tests the user-facing behavior rather than only the visitor's internal action list.

## Validation

`./gradlew spotlessCheck test` passed with JDK 21, including the full test suite. The configured BoxLang test dependency was absent initially, so the repository's `downloadBoxLang` task was run to populate its expected local test-library path; this was environment setup, not an additional source change.

The requested shared DoD file, `docs/student-tickets/README.md`, was not present in this checkout, so its additional criteria could not be checked directly.