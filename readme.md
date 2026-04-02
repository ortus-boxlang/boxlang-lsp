# ⚡︎ BoxLang Module: bx-lsp

This is a BoxLang module that implements [Microsoft's Language Server Protocol](https://microsoft.github.io/language-server-protocol/). You most likely will not want to install this module directly. This modules is used by IDE's that also implement the LSP in order to provide a rich editing experience.

Check out the official [BoxLang VSCode extension](https://github.com/ortus-boxlang/vscode-boxlang) for more information and an example of this project being used.

## Ortus Sponsors

BoxLang is a professional open-source project and it is completely funded by the [community](https://patreon.com/ortussolutions) and [Ortus Solutions, Corp](https://www.ortussolutions.com).  Ortus Patreons get many benefits like a cfcasts account, a FORGEBOX Pro account and so much more.  If you are interested in becoming a sponsor, please visit our patronage page: [https://patreon.com/ortussolutions](https://patreon.com/ortussolutions)

## Configuration

The BoxLang LSP is controlled through three separate configuration systems:

| System                 | File                                 | Purpose                                       |
| ---------------------- | ------------------------------------ | --------------------------------------------- |
| IDE Workspace Settings | `boxlang.lsp.*` in your IDE settings | LSP behavior & performance                    |
| Project Mappings       | `boxlang.json` at workspace root     | Virtual paths, classpaths, module directories |
| Lint Rules             | `.bxlint.json` at workspace root     | Static analysis rule toggles and severity     |

See the **[Configuration Reference](docs/config-reference.md)** for the complete list of all settings, types, defaults, and descriptions.

### Live Reload Behavior

- The server watches `.bxlint.json` using both LSP file events (if supported) and a fallback filesystem watcher.
- On change: cache invalidated → config reloaded → open documents reparsed → diagnostics republished.
- Watcher debounce is minimal; rapid saves may still trigger multiple reloads (acceptable for now).

### Troubleshooting

| Symptom              | Possible Cause                 | Action                                                                       |
| -------------------- | ------------------------------ | ---------------------------------------------------------------------------- |
| Config edits ignored | File not at workspace root     | Move `.bxlint.json` to root folder recognized by client                      |
| Rules not disabling  | Rule id typo                   | Check the rule ID in the [Configuration Reference](docs/config-reference.md) |
| Paths not filtered   | Globs mismatched OS separators | Always use forward slashes `/`                                               |


### THE DAILY BREAD

 > "I am the way, and the truth, and the life; no one comes to the Father, but by me (JESUS)" Jn 14:1-12
