# ⚡︎ BoxLang Module: bx-lsp

This is a BoxLang module that implements [Microsoft's Language Server Protocol](https://microsoft.github.io/language-server-protocol/). You most likely will not want to install this module directly. This modules is used by IDE's that also implement the LSP in order to provide a rich editing experience.

Check out the official [BoxLang VSCode extension](https://github.com/ortus-boxlang/vscode-boxlang) for more information and an example of this project being used.

## Ortus Sponsors

BoxLang is a professional open-source project and it is completely funded by the [community](https://patreon.com/ortussolutions) and [Ortus Solutions, Corp](https://www.ortussolutions.com).  Ortus Patreons get many benefits like a cfcasts account, a FORGEBOX Pro account and so much more.  If you are interested in becoming a sponsor, please visit our patronage page: [https://patreon.com/ortussolutions](https://patreon.com/ortussolutions)
## Diagnostic Configuration (Experimental)

Add a `.boxlang-lsp.json` file at the workspace root to control lint diagnostics:

Key capabilities:
- Enable / disable individual rules.
- Override rule severity.
- (New) Restrict which files are analyzed using `include` / `exclude` glob arrays.
- Live reload: changes trigger an automatic re-parse & diagnostic publish (no reload required).

### File Structure

```jsonc
{
	// Optional: restrict analysis scope (workspace‑relative globs)
	"include": [ "src/**" ],
	"exclude": [ "src/generated/**", "**/vendor/**" ],

	// Rule customization
	"diagnostics": {
		"unscopedVariable": {
			"enabled": true,
			"severity": "warning"
		},
		"unusedVariable": {
			"enabled": true,
			"severity": "hint"
		}
	}
}
```

### Include / Exclude Semantics
- Paths are workspace‑relative and use forward slashes.
- Globs supported: `*` (segment wildcard), `**` (recursive), `?` (single char).
- `include` empty or omitted => all files implicitly included.
- A file must match at least one `include` (if provided) AND must not match any `exclude`.
- Evaluation order: determine inclusion first, then exclusion.

Examples:
```jsonc
// Analyze only application code, skip tests and generated sources
{
	"include": [ "app/**" ],
	"exclude": [ "app/generated/**", "app/**/test-fixtures/**" ],
	"diagnostics": { "unusedVariable": { "severity": "information" } }
}

// Disable a rule entirely while experimenting
{
	"diagnostics": { "unscopedVariable": { "enabled": false } }
}

// Tighten severity
{
	"diagnostics": { "unusedVariable": { "severity": "error" } }
}
```

### Rule Configuration Fields
| Field    | Type    | Default      | Notes                                         |
|----------|---------|--------------|-----------------------------------------------|
| enabled  | boolean | true         | Turns rule on/off                             |
| severity | string  | rule default | One of `error` `warning` `information` `hint` |
| params   | object  | {}           | Reserved for future per‑rule options          |

### Current Implemented Rules
- `unscopedVariable` – Flags variable references lacking an explicit scope.
- `unusedVariable` – Flags declared but unused local variables.

More rules are planned; configuration format is forward‑compatible.

### Live Reload Behavior
- The server watches `.boxlang-lsp.json` using both LSP file events (if supported) and a fallback filesystem watcher.
- On change: cache invalidated → config reloaded → open documents reparsed → diagnostics republished.
- Watcher debounce is minimal; rapid saves may still trigger multiple reloads (acceptable for now).

### Troubleshooting
| Symptom              | Possible Cause                 | Action                                                       |
|----------------------|--------------------------------|--------------------------------------------------------------|
| Config edits ignored | File not at workspace root     | Move `.boxlang-lsp.json` to root folder recognized by client |
| Rules not disabling  | Rule id typo                   | Check the rule name (see list above)                         |
| Paths not filtered   | Globs mismatched OS separators | Always use forward slashes `/`                               |

### Minimal Starter
```json
{ "diagnostics": { "unscopedVariable": { "severity": "warning" } } }
```

### Complete Example
```json
{
	"include": [ "src/**", "scripts/**" ],
	"exclude": [ "src/generated/**", "scripts/archive/**" ],
	"diagnostics": {
		"unscopedVariable": { "enabled": true, "severity": "warning" },
		"unusedVariable": { "enabled": true, "severity": "hint" }
	}
}
```

Future roadmap items: rule parameterization (`params`), suppressions via inline comments, per‑folder overrides.


### THE DAILY BREAD

 > "I am the way, and the truth, and the life; no one comes to the Father, but by me (JESUS)" Jn 14:1-12
