# Description

## Suggested PR title

`https://ortussolutions.atlassian.net/browse/BLIDE-313 Add quick fix for unused imports`

The PR title links Jira issue BLIDE-313. Per `CONTRIBUTING.md`, target the `development` branch.

## Summary and motivation

Adds an editor quick fix titled `Remove unused import` for active unused explicit imports in `.bx` and `.bxs` files, including aliased imports. The fix removes only the import statement and semicolon, preserving adjacent comments, other statements, and line endings. A blank line may remain. Wildcard imports and organize-imports/fix-all behavior are out of scope.

This extends the existing semantic-warning diagnostic and LSP code-action flow so developers can remove a reported unused import from the editor. The action is associated with its diagnostic, including diagnostic `data` as returned by an LSP client. Disabled or suppressed diagnostics do not offer a fix.

## Issues

- Issue 1: [BLIDE-313](https://ortussolutions.atlassian.net/browse/BLIDE-313), the unused-import quick-fix request.
- BoxLang Jira: https://ortussolutions.atlassian.net/browse/BL/issues
- Module issues: https://github.com/boxlang-modules/@MODULE_SLUG@/issues

## Dependencies

- No new application dependencies are introduced.
- Development and tests require JDK 21 or newer.
- Tests require the project's configured BoxLang runtime JAR. If it is not present locally, run `./gradlew downloadBoxLang`.

## Type of change

- [x] Bug Fix
- [ ] Improvement
- [ ] New Feature
- [ ] Breaking change
- [x] This change requires a documentation update

## Tests

Added service-level tests that request the action through `BoxLangTextDocumentService.codeAction()`, model client-returned diagnostic data, apply the edit, compare the resulting CRLF source including neighboring comments, and reparse to verify the targeted diagnostic is gone while a used import remains. Coverage includes `.bx`, `.bxs`, aliased imports, disabled rules, and suppressed diagnostics.

Validation completed with JDK 21:

```bash
./gradlew spotlessCheck test
```

The full suite passed. After recovering the changes onto `issue1fix`, `spotlessCheck` and the complete `SemanticWarningDiagnosticsTest` class also passed.

## Checklist

- [x] Java style check passes with `spotlessCheck` (no BoxLang source formatting changes were needed).
- [x] I have commented my code, particularly in hard-to-understand areas.
- [x] I have made corresponding documentation changes (`docs/boxlangissue1fixgroup5.md`).
- [x] I have added tests that prove the fix is effective.
- [x] New and existing tests pass locally with my changes.