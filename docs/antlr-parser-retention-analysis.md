# ANTLR parser-cache retention analysis

## Conclusion

For the pinned ColdBox workload, the dominant live objects are retained by **generated parser static caches**, not by application AST nodes. There are two distinct owners:

```text
CFGrammar._decisionToDFA
  → DFA.states / DFAState.edges
  → DFAState.configs
  → ATNConfigSet.configs
  → ATNConfig

CFGrammar._sharedContextCache
  → PredictionContextCache.cache
  → prediction-context graph
```

These are intentional, process-wide prediction caches shared by parser instances. Discarding an AST, collecting a weak parse result, or evicting an LSP document does not release their static roots. The cleanup currently exposed by BoxLang replaces parser DFAs but leaves the shared prediction-context caches populated.

This explains retention in this fixture. It does **not** establish the cause of the original OOM on BoxLang 1.13.0 or quantify the earlier property-AST fix.

## Evidence and workload

- Real ColdBox `v8.2.0`, commit `97a2e9ec2ff463d38106f76f926981726fc121f5`, 674 candidate sources.
- Actual LSP subprocess, `-Xmx512m`, Temurin `21.0.10`, LSP `1.15.0-snapshot` with the property-retention fix, BoxLang `1.17.0-snapshot`.
- Runtime JAR SHA-256: `83c84e32357bcad2560933f29165a30c95002a0c21204a279213dfaea476c7c2`.
- Verified runtime behavior using the exact JAR's bytecode, not only adjacent BoxLang source.
- JFR `OldObjectSample` GC-root paths directly identify `CFGrammar._decisionToDFA` and `CFGrammar._sharedContextCache`. These are sampled retaining paths, not a complete dominator tree or retained-byte attribution.
- Local controlled experiments used a temporary sidecar delegating to the real `App.main`. It communicated through local files, inspecting cache occupancy and invoking existing cleanup. Reflection was confined to this disposable diagnostic: it froze automatic runtime eviction and separately emptied prediction-context maps while parsing was quiescent. No such reflection or cleanup policy was added to production or the normal integration test.

### Controlled release

One controlled run with a GC-only request control produced these **JFR heap-used values immediately after full GC**:

| Checkpoint | Heap used | Parser DFA states | CF prediction-cache entries |
| --- | ---: | ---: | ---: |
| Scan and measured requests complete | 249.96 MiB | 51,107 | 464,181 |
| Existing `Parser.clearParseCache()` | 63.09 MiB | 0 | 464,181 |
| Clear DFAs again after requests, then prediction contexts | 29.93 MiB | 0 | 0 |

The existing API released approximately **187 MiB**; clearing the remaining context maps released another **33 MiB**. An earlier controlled run produced the same direction and very similar live-object totals. These are intervention results, not a before/after comparison of shipped builds.

The initial histogram contained 3,939,425 `ATNConfig` instances / 120.2 MiB shallow size and DFA-state arrays / 39.6 MiB. After the DFA reset, the roughly 403,000 `SingletonPredictionContext` objects and context-map nodes remained. After the context reset, only about 1,300 singleton contexts remained. These class sizes alone are not retained sizes; the measured whole-heap deltas above establish the cleanup effect.

Lexer caches remained populated throughout. They were small here: 761 lexer DFA states combined. Clearing a context map also preserves its `HashMap` backing capacity; the final heap is not a claim that every parser-related byte was removed.

### Cleanup has a latency cost

Symbols, completions, and semantic tokens passed after each cleanup. In the run with the GC-only control:

| Request phase | Document symbols | Completion | Semantic tokens |
| --- | ---: | ---: | ---: |
| Full GC only; DFA cache still warm | 45.0 ms | 77.5 ms | 105.2 ms |
| DFA reset and full GC | 44.1 ms | 295.1 ms | 58.0 ms |
| DFA plus context reset and full GC | 42.8 ms | 344.3 ms | 62.2 ms |

The GC-only control matters because collecting weak AST references can itself cause reparsing. Completion was still materially slower after removing prediction caches. These are individual client-observed requests, including transport/queueing, not CPU measurements, distributions, or evidence of full semantic/concurrency correctness. Do not clear after every file or use forced GC in production.

## Existing automatic eviction changes the interpretation

The tested 1.17 runtime already has cache management, enabled by packaged `experimental.clearParserCache=true`:

- Estimate: 4,200 bytes per parser DFA state, summed across CF, Box, and Doc grammars.
- Delayed post-parse check: five seconds; clear when the estimate exceeds one-third of maximum heap.
- Watchdog: every 30 seconds; idle cleanup after three minutes, or age cleanup after ten minutes when the estimate exceeds 100 MB.
- All these paths use the same partial DFA cleanup. The estimate and reported state count exclude prediction-context-cache occupancy and lexer caches.

A separate run **left automatic eviction enabled**. At the initial checkpoint it had 51,107 parser states, a pending post-parse check, and 250.17 MiB post-GC heap. After a 12-second observation window:

- Runtime log: `Clearing DFA cache: estimated size exceeds 1/3 of max heap`.
- Parser states: zero; CF prediction-cache entries: still 464,181.
- Post-GC heap: **63.11 MiB**.
- Timing fields show the reset occurred approximately 1.3 seconds after the last parse, early in that observation window.

Consequently, the existing ~249.5 MiB cold-start baseline is an **early post-workload checkpoint, not steady-state idle retention**. A checkpoint taken after delayed cleanup can look much better without any code change. Record startup peak, immediate post-scan heap, and post-idle heap as separate phases when extending the workload; version that workload rather than silently changing the existing baseline.

The LSP's `MemoryThresholdMonitor` adds a five-minute periodic reset and usage thresholds on supported heap pools at 85%. It neither pauses background work nor evicts LSP caches, and its shared reset does not release interned prediction contexts. Pool thresholds are not an aggregate heap/allocation budget, and delayed cleanup cannot guarantee protection against growth during a single parse.

The available local `1.13.0-snapshot` JAR lacks the new runtime watchdog/post-parse machinery. That is evidence of version-dependent behavior, not proof that this snapshot is byte-identical to the incident's 1.13.0 release. Do not assume the newer policy existed in the failing deployment.

## Where cache growth concentrates

Quiescent inspection of the actual runtime's DFA maps identified:

| CF grammar decision | DFA states | Stored configurations |
| --- | ---: | ---: |
| 20, `function` | 24,218 | 1,939,721 |
| 41, `postAnnotation` | 10,296 | 786,093 |
| 181, `template_statements` | 4,557 | 321,206 |
| 152, `el2` | 3,821 | 289,366 |
| All CF decisions | 49,672 | 3,831,791 |

The first two decisions account for about **71% of CF configurations**. In corresponding generated grammar source, decision 20 chooses the optional function body; decision 41 chooses the optional annotation assignment/value. They are good targets for prediction/lookahead and grammar-ambiguity investigation. Occupancy does not, by itself, prove a grammar bug or justify changing accepted syntax.

## Recommended next work

1. **Fix cleanup at the runtime owner.** Provide a supported reset that covers the shared prediction-context caches as well as DFAs, and report both occupancies. Reuse that operation from existing runtime and LSP cleanup paths rather than adding another independent timer/cache framework. Test semantics, active-parser safety, and reset/refill behavior before adoption; do not ship the diagnostic reflection.
2. **Investigate the two grammar hotspots.** Profile lookahead/prediction and test syntax-preserving simplifications around optional function bodies and annotation values. Reducing cache creation can improve both memory and CPU; more aggressive eviction merely trades retention for repeated prediction work.
3. **Keep background headroom.** Coordinate scans and stop scheduling new background parses under sustained pressure. Prefer controlled/quiescent cleanup and recovery over reset/refill loops. Existing runtime cleanup helps, but it is delayed, partial, and does not bound a single large parse.
4. **Measure lifecycle separately.** Add post-idle and repeated edit/rescan phases to a new workload version. Track DFA states, stored configurations, context-cache entries, and startup versus idle heap. Compare identical runtime versions and repeat timings before choosing thresholds.

The AST-property fix remains valid independently. Bounded closed-document caches and stable file inventory remain worthwhile LSP work, but they will not remove these static parser roots.

## Local artifacts

Under `$HOME/.local/state/boxlang-lsp-profiles/retention-analysis/`:

- `run-7013972966103753326`: GC-root recording, text/JSON paths, normal profiled server logs.
- `run-4303849807906318140`: first controlled DFA/context cleanup experiment.
- `run-15307577218649305087`: controlled cleanup with GC-only request control; histograms, probe replies, JFR, heap-event JSON, metrics, `PASS`.
- `run-14628634630086757072`: automatic-eviction observation; runtime trace in `home/.boxlang/logs/runtime.log`, probe replies, histograms, JFR, heap-event JSON, metrics, `PASS`.
- Archived `CacheProbe.java`, `controlled-clear-driver.java`, and `auto-eviction-driver.java` document the disposable instrumentation. The normal test driver was restored afterward.

Analysis runs have distinct workload labels. Their production-classpath fingerprint does not include the temporary sidecar, so that fingerprint alone is insufficient for comparing these instrumented runs against ordinary baselines. Keep all artifacts local; recordings/logs contain local paths and potentially source-related information.
