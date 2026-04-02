# `.bxlint.json` Path-Based Override Design

> **Status: Not yet implemented.** This document captures the design so it can be referenced when implementation begins.

## Motivation

The current `include` and `exclude` arrays in `.bxlint.json` are all-or-nothing: a file is either analyzed or it is not. There is no way to apply different rule configurations to different parts of the codebase. Common needs include:

- Stricter rules in `src/` (production code) than in `tests/` or `dependencies/`.
- Disabling noise-heavy rules (e.g. `unscopedVariable`) only for legacy or generated code without turning them off globally.
- Tightening severity for critical paths without affecting experimental or sandbox directories.

## Proposed JSON Shape

Add an optional `overrides` array at the top level of `.bxlint.json`. Each entry is an object with optional `include` (string array of glob patterns), optional `exclude` (string array), and optional `diagnostics` (same shape as the top-level `diagnostics` object).

```jsonc
{
  "diagnostics": {
    "unusedVariable": { "severity": "hint" }
  },
  "overrides": [
    {
      "include": ["src/**"],
      "diagnostics": {
        "unusedVariable": { "severity": "warning" }
      }
    },
    {
      "include": ["dependencies/**"],
      "diagnostics": {
        "unusedVariable": { "enabled": false },
        "unscopedVariable": { "enabled": false }
      }
    }
  ]
}
```

## Merge Semantics

1. The base config (top-level `diagnostics`) is the starting point for every file.
2. Overrides are evaluated in array order.
3. **All** overrides whose `include`/`exclude` patterns match the file's workspace-relative path are applied — not just the first matching one.
4. Later matching overrides win on a **per-field** basis, not per-rule. For example, if override A sets `unusedVariable.severity = "warning"` and override B sets `unusedVariable.enabled = false`, both fields are applied; the result has `severity = "warning"` AND `enabled = false`.
5. A field not mentioned in any matching override is inherited from the base config.

## Scope Constraints

`overrides` entries support only three keys:

- `include` — workspace-relative glob patterns. When non-empty, the entry only applies to matching files.
- `exclude` — workspace-relative glob patterns. Matching files are excluded from the override even if they matched `include`.
- `diagnostics` — per-rule settings, same shape as the top-level `diagnostics` object.

Explicitly **not** supported inside `overrides` entries:

- Nested `overrides` — no recursive override nesting.
- Top-level `include`/`exclude` — the global file-analysis filter remains unaffected by override entries. Overrides only vary rule settings; they do not control whether a file is analyzed at all.

## Implementation Notes

- Requires a new `LintConfig.resolveForFile(String relativePath)` method that returns a synthetic `LintConfig` representing the merged view for a specific file.
- The existing `shouldAnalyze(String relativePath)` and `forRule(String id)` methods will need per-file variants (or callers updated to pass the resolved config rather than using global state).
- The `RuleSettings` merge should be field-level: null/absent fields in an override do not overwrite non-null values from earlier in the merge chain.
- Deserialization: `LintConfig` would gain a new `List<OverrideEntry> overrides` field (not annotated with `@ConfigSetting` until the feature is implemented and stabilized).
- The glob matching logic already implemented in `LintConfig.shouldAnalyze` can be reused for override pattern matching.
