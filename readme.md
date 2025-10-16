# ⚡︎ BoxLang Module: bx-lsp

This is a BoxLang module that implements [Microsoft's Language Server Protocol](https://microsoft.github.io/language-server-protocol/). You most likely will not want to install this module directly. This modules is used by IDE's that also implement the LSP in order to provide a rich editing experience.

Check out the official [BoxLang VSCode extension](https://github.com/ortus-boxlang/vscode-boxlang) for more information and an example of this project being used.

## Ortus Sponsors

BoxLang is a professional open-source project and it is completely funded by the [community](https://patreon.com/ortussolutions) and [Ortus Solutions, Corp](https://www.ortussolutions.com).  Ortus Patreons get many benefits like a cfcasts account, a FORGEBOX Pro account and so much more.  If you are interested in becoming a sponsor, please visit our patronage page: [https://patreon.com/ortussolutions](https://patreon.com/ortussolutions)
## Diagnostic Configuration (Experimental)

Place a `.boxlang-lsp.json` file in the workspace root to enable/disable rules and override severities.

Example:

```
{
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

Fields:
- `enabled` (boolean, default true)
- `severity` one of error|warning|information|hint (fallbacks to rule default)
- `params` (object) for future per‑rule settings

Changes are polled every few seconds; restart the language client for guaranteed refresh.


### THE DAILY BREAD

 > "I am the way, and the truth, and the life; no one comes to the Father, but by me (JESUS)" Jn 14:1-12
