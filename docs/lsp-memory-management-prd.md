# PRD: Predictable LSP Memory Usage

## Status and scope

- Status: property ownership, operation-local parse lifetimes, semantic-token reuse, bounded closed-file parse caching, inventory-based references, and shared startup/background indexing implemented. Lifecycle profiling covers idle, unsaved edits, and repeated scans. Before/after incident/build comparison, byte/oversized-file limits, and pressure backoff remain open.
- Scope: the Java language-server process in this repository.
- Companion work: less intrusive VS Code error handling is tracked separately and is not part of this PRD.
- Delivery: incremental red/green slices, starting with a proven retention path rather than a new memory-management framework.

## Problem

An incident reported on 2026-09-30 shows the managed language server starting with `-Xmx512m`, LSP `1.14.0+13`, BoxLang runtime `1.13.0`, and five workspace folders. Within roughly one minute, repeated memory-threshold notifications and parser-cache clears are followed by `OutOfMemoryError: Java heap space`. The editor then displays failures for document symbols and document-open/change notifications.

The logs establish sustained heap pressure, not the dominant retained object type. The final `IdentityHashMap` allocation is not enough to identify the root cause. Likewise, a log reporting discarded parser states does not measure bytes actually reclaimed by GC. The user's workspace size, file sizes, effective settings, and heap dump are not yet available. Five advertised roots do not prove that all five were scanned; current workspace scanning uses the first root plus configured mapping directories.

## Goals

1. Remove unnecessary strong references from cached metadata to full syntax trees.
2. Give expendable closed-document caches explicit bounds instead of relying on weak references alone.
3. Keep background analysis from exhausting the heap needed by interactive requests.
4. Preserve completion, symbols, diagnostics, definitions, and reference-search coverage as data is evicted or reparsed.
5. Measure retained memory and request responsiveness under the existing 512 MB heap cap before changing heap defaults.

## Non-goals

- VS Code notification suppression, restart policy, or other client UX changes.
- Treating a larger heap or repeated forced GC as the solution.
- A custom cache framework, new dependency, or speculative tuning settings.
- Guaranteeing full analysis of arbitrarily large files/workspaces within 512 MB.
- A multi-root indexing redesign; retain existing coverage while fixing memory behavior.
- Claiming that the first retention fix alone resolves the reported OOM.

## Findings

Paths below are relative to `src/main/java/ortus/boxlang/lsp/`. The table records the initial investigation; the checked delivery slices below describe remediation now implemented.

| Finding | Location | Consequence |
| --- | --- | --- |
| `parsedFiles` is an unbounded map of closed-file parse results. | `workspace/ProjectContextProvider.java` | Background diagnostics retain a result for every analyzed file. |
| `FileParseResult.properties` contains `ParsedProperty` records with a strong `BoxProperty` node reference. | `workspace/FileParseResult.java`, `workspace/types/ParsedProperty.java`, `workspace/visitors/PropertyVisitor.java` | A property node points to its parent class and the rest of the AST, bypassing the intended weak AST ownership. |
| No in-repository consumer reads `ParsedProperty.node()`. | `workspace/completion/PropertyCompletionRule.java` | Property completion needs only name/type metadata; retaining the node is unnecessary. |
| Initial parsing stores only a weak `ParsingResult` before subsequent processing retrieves it again. | `workspace/FileParseResult.java` | GC can cause unnecessary reparsing between processing stages. |
| Document-open index seeding walks the workspace independently of the background-parsing setting. | `workspace/ProjectContextProvider.java` | Cold startup can do substantial work on an interactive path and overlap background indexing. |
| Workspace passes submit one task/future per candidate and parse separately for indexing and diagnostics. | `workspace/ProjectContextProvider.java`, `workspace/index/ProjectIndex.java` | CPU-based worker limits do not bound queued work or retained memory. |
| Open-document semantic-token requests construct a new fully parsed result. | `workspace/ProjectContextProvider.java` | Token requests also regenerate diagnostics and metadata, increasing allocation pressure. |
| The memory monitor only clears ANTLR DFA caches. | `MemoryThresholdMonitor.java` | No LSP-cache eviction or background-work backoff occurs; shared prediction-context caches remain populated. |
| Generated parser statics retain the dominant ANTLR objects in the ColdBox fixture. | Runtime `CFGrammar._decisionToDFA`, `CFGrammar._sharedContextCache` | Existing DFA reset reduces post-GC heap from ~250 to ~63 MiB; a diagnostic context reset reduces it to ~30 MiB, with a reparse latency cost. |
| Reference searches enumerate `openDocuments` and `parsedFiles`. | `workspace/ProjectContextProvider.java` | Evicting closed files without changing enumeration would silently reduce search coverage. |

The property-retention path and relevant startup/cache behavior also exist in the `v1.14.0` tag. This is a confirmed code-level retention path, not yet a quantified explanation of the incident.

## Memory ownership requirements

- Workspace symbol/property metadata must not keep AST nodes alive.
- A parsing operation must keep the parse result alive until its dependent processing completes.
- Open-document content must remain authoritative, including unsaved changes, during eviction and reparsing.
- Closed-document parse results must be expendable and reloadable from disk; eviction must not discard the workspace's file inventory.
- Cached diagnostics must not require their originating AST to remain resident.
- Do not turn memory-pressure skips or an incomplete index into source-code errors such as false "class not found" diagnostics.
- Background work must yield before heap exhaustion; interactive work must not queue behind an unbounded workspace scan.
- Cleanup/backoff must avoid an immediate refill loop. Resume below a lower pressure threshold rather than oscillating at one threshold.

## Delivery slices and acceptance criteria

### 1. Remove property AST retention — completed

- [x] Add a regression test that extracts property metadata from a real parsed class, releases the AST, and observes its collection while metadata remains strongly reachable.
- [x] Demonstrate the regression is red against the existing implementation.
- [x] Remove the unused AST node from `ParsedProperty` and update `PropertyVisitor` construction.
- [x] Confirm retained metadata still produces property completions with the same label, insertion text, kind, and type detail after the source AST is collected.
- [x] Cover BoxLang and CFML class syntax in the same behavioral regression.
- [x] Run the targeted regression repeatedly, then the full build/formatting workflow.

No public LSP protocol, setting, or completion-output changes are intended. The internal record constructor/node accessor changes; no in-repository consumer needs the removed node.

### 2. Profile and stabilize parse lifetimes

- [x] Add an opt-in integration test that clones a pinned real ColdBox workspace and checks a separate 512 MB LSP process through the public protocol.
- [x] Add optional JFR recording, a post-scan live-object histogram, and comparable heap/GC/allocation/request-latency summaries for the current build.
- [x] Confirm dominant parser-cache retaining paths, quantify controlled cleanup, and measure request behavior with a GC-only control.
- [x] Observe existing runtime eviction without disabling it; distinguish immediate post-scan from idle retention.
- [ ] Reproduce the incident versions when a representative workspace is available; also measure the current build.
- [ ] Capture post-GC heap, allocation rate, dominant retained objects, scan progress, and interactive request latency before/after slice 1.
- [x] Add a failing regression for GC during initial parse processing, then keep the result strongly reachable for that operation.
- [x] Avoid redundant full parsing for semantic tokens, preserving current document versions and diagnostics.
- [x] Version the ColdBox workload to include a 12-second idle window, an unsaved method edit, and two completed rescans with per-phase profiling checkpoints.

Acceptance: metadata/diagnostic generation uses one live parse result per operation; on-demand reparse remains possible after collection. Profiling must distinguish retained ASTs, diagnostics/actions, index metadata, and ANTLR caches.

### 3. Bound closed-document caching without losing workspace coverage

- [x] Enumerate reference-search candidates from workspace/index file inventory, not only resident parse-result cache entries.
- [x] Prove references in uncached files are found, including references to unsaved open documents; searches do not retain a workspace-sized map of parse results.
- [x] Introduce a bounded cache using an existing library or standard collection, keeping open-document state separate.
- [x] Serve published/cached diagnostics without unnecessarily repopulating full parse results.
- [x] Preserve exclusions, document versions, config invalidation, and removal behavior.

Implementation: existing Guava cache with a fixed maximum of 256 closed-file parse results. Open-document models/results are separate and authoritative; requests flush a pending content version once instead of regenerating diagnostics on every token request. Persistent URI/index inventory and AST-free diagnostic reports remain separate from this evictable cache. Cold reference searches parse only ASTs, not full diagnostics/outline metadata. This is an entry-count ceiling, not a total-byte guarantee or a limit on one oversized file; the provisional cap is not a measured optimal setting.

Acceptance: exceeding the cache limit evicts expendable results, interactive access reparses correctly, and repeated scans cannot grow this cache without bound. Choose limits from measurements; a file-count limit alone does not bound a single oversized file.

### 4. Coordinate startup/background work

- [x] Make cold-index seeding and background indexing share the initial candidate/index operation rather than independently indexing the same workspace.
- [x] Bound in-flight/queued work; apply exclusions consistently to shared scan paths.

Concurrent workspace requests share the active completion future; forced config refreshes coalesce into a follow-up scan. Index seeding and the first background scan share a lock and candidate snapshot, with a complete index before diagnostics. Later scans refresh the inventory and check changed files. Each pass submits only one long-lived task per worker (at most four), rather than one task/future per file. Opening a cold document still waits for initial index completion to preserve inheritance diagnostics; eliminating that wait is separate work.
- [ ] Pause or cancel scheduling background analysis under sustained pressure, retaining interactive headroom.
- [ ] Test pressure recovery without repeated stop/start oscillation or missing-reference/source-error regressions.
- [ ] Handle oversized files/background workloads explicitly rather than exhausting the process.

Acceptance: opening a document during a cold scan does not launch another independent workspace scan, and background pressure results in reduced/deferred coverage rather than server failure or misleading diagnostics.

### 5. Integrate pressure handling and observability

- [ ] Coordinate a supported complete parser-cache reset with the runtime owner, including prediction-context caches and occupancy reporting; test semantics and concurrent parser safety.
- [ ] Connect pressure handling to expendable LSP caches and background scheduling, not just ANTLR DFA clearing.
- [ ] Log heap used/max, cache counts, scan progress, and the chosen action at useful transitions.
- [ ] Distinguish discarded entries/states from measured reclaimed memory; do not log state counts as freed bytes.
- [ ] Validate monitor/executor lifecycle and cleanup during shutdown.

Acceptance: a pressure event has an observable, bounded response, and normal analysis resumes when memory headroom is restored. No per-file notification flood is introduced.

## Validation plan

### Current regression

Test: `src/test/java/ortus/boxlang/lsp/workspace/visitors/PropertyVisitorTest.java`.

```bash
./gradlew test --tests 'ortus.boxlang.lsp.workspace.visitors.PropertyVisitorTest'
```

Use a real parser and `PropertyVisitor`, a weak AST reference registered with a reference queue, and bounded explicit GC requests. Keep metadata strongly reachable with `Reference.reachabilityFence`. Verify both AST collection and usable completion metadata without inspecting private fields or mocking parser behavior. The test requires a JVM that permits explicit GC; the project's standard test JVM does so.

### Opt-in real-workspace integration

Test: `src/test/java/ortus/boxlang/lsp/integration/ColdBoxMemoryIntegrationTest.java`.

```bash
./gradlew coldboxIntegrationTest
```

This network-dependent task shallow-clones the real [ColdBox platform](https://github.com/ColdBox/coldbox-platform) at `v8.2.0` and verifies commit `97a2e9ec2ff463d38106f76f926981726fc121f5`. It does not generate a workspace and is excluded from ordinary `test`/`build` runs. The task always executes when explicitly requested.

The test starts a separate LSP process with `-Xmx512m`, an isolated runtime home, and parallel background diagnostics enabled. It initializes the cloned workspace, opens `system/Bootstrap.cfc` during startup, waits for diagnostics from every scan candidate and the completed index-cache save, and checks document symbols, completions, and semantic tokens before and after the scan. It also verifies orderly shutdown. The current fixture has 674 source candidates; the test guards against accidentally scanning a tiny/empty workspace.

Each invocation keeps the clone, fixture/runtime manifest, result summary, `metrics.json`, server log, GC log, and any OOM heap dump in an ignored `build/coldbox-integration/run-*` directory. These artifacts remain local and are removed by `clean` unless an external output directory is selected below. The heap cap applies to the LSP subprocess, not just the Gradle test worker. An OOM terminates the subprocess and fails the test.

This is a repeatable cold-start smoke/stress check, not a reproduction of the original incident. Its bounded request timeouts detect hangs but do not establish interactive latency targets. The lifecycle workload now adds idle observation, an unsaved edit, and two rescans; routine regressions cover eviction/reload, uncached reference coverage, authoritative unsaved content, GC during processing, and shared scans. Warm process startup, broader reference/eviction combinations, byte bounds, and full dominator analysis remain follow-up work. Separate sampled retaining-path and controlled-cleanup findings are documented in [ANTLR parser-cache retention analysis](antlr-parser-retention-analysis.md).

### Profile and compare ColdBox runs

```bash
# Profiling is optional; omit -PcoldboxProfile for the ordinary smoke test.
./gradlew coldboxIntegrationTest -PcoldboxProfile

# Preserve baselines across clean/build cycles by writing outside build/.
./gradlew coldboxIntegrationTest -PcoldboxProfile \
  -PcoldboxArtifactsDir="$HOME/.local/state/boxlang-lsp-profiles"
```

Requires a full JDK 21+ with `jcmd`, as well as JFR support. The task records **the actual 512 MB LSP subprocess**, from JVM startup through orderly exit, using Java Flight Recorder's `profile` settings. Environment-variable, system-property, and unrelated-process inventory events are disabled. Recording and profiling artifacts still contain sensitive local paths and potentially source-related data; keep them local.

The current workload is `coldbox-lifecycle-v2`: cold startup and requests, a 12-second idle observation followed by requests, an unsaved `lspMemoryProbe` method edit, and two rescans triggered through public configuration notifications. The edited method must remain visible after both scans; disk content stays unchanged. Scan-completion cache samples must stay at or below 256 entries.

With profiling enabled, `jcmd GC.class_histogram` forces a full GC after each phase's measured requests while `Bootstrap.cfc` remains open. The first histogram is `live-objects.txt`; later files are `afterIdle-live-objects.txt`, `afterEdit-live-objects.txt`, and `afterRescan{1,2}-live-objects.txt`. They list shallow live class sizes, not dominator retained sizes. Ordered matching JFR GC IDs supply each phase's `heapAfterGcBytes` and `checkpointGcPauseMillis`. The legacy `heapAfterCheckpointGcBytes` alias refers to the final checkpoint. The idle checkpoint is after post-idle requests, which can refill parser caches; it is not a pure pre-request idle snapshot.

Diagnostic pauses do not fall within measured request durations, but earlier full GCs can affect later reparsing/cache behavior. `workloadMillis` includes the entire lifecycle and intermediate snapshot overhead; `coldWorkloadMillis` stops before the first snapshot. Always compare the same profiling mode and workload version; do not compare lifecycle totals against v1 cold-start totals.

Successful runs write a sorted `metrics.json` and `profile.jfr` in their printed `run-*` directory:

| Metric | Meaning |
| --- | --- |
| `gcObservedPeakHeapBytes` | Largest heap-used value seen at JFR GC boundaries; a lower bound on the true continuous peak. |
| `heapAfterCheckpointGcBytes` | Heap used immediately after the explicit end-of-workload full GC. |
| `gcCount`, `gcPauseMillis` | GC events and summed stop-the-world pause time, including the diagnostic full GC. |
| `checkpointGcPauseMillis` | Diagnostic GC pause; subtract it from `gcPauseMillis` to compare non-checkpoint pauses. |
| `estimatedAllocatedBytes`, `estimatedAllocationBytesPerSecond` | Weighted JFR allocation-sample estimates; the rate uses the observed recorded-event window, not an exact allocation counter. |
| `cold.*Millis`, `afterScan.*Millis`, `afterIdle.*Millis`, `afterEdit.*Millis`, `afterRescan{1,2}.*Millis` | Phase request durations; profiled phases also contain explicit GC heap/pause metrics. Rescan phases include `scanMillis`. Cold requests can include index-seeding work. |
| `initializeResponseMillis`, `workspaceReadyMillis`, `workloadMillis` | Time from process launch to initialize response, complete workspace readiness, and final measured request. Clone/network time is excluded. |
| Versions, revision, `lspClasspathSha256` | Fixture/JDK/runtime/LSP identity; the classpath fingerprint includes compiled production classes and dependency bytes, so uncommitted code and changed snapshot JARs are identifiable. |

Inspect the recording with JDK tools or open it in JDK Mission Control:

```bash
jfr view gc /path/to/run/profile.jfr
jfr view allocation-by-class /path/to/run/profile.jfr
jfr view allocation-by-site /path/to/run/profile.jfr
jfr view hot-methods /path/to/run/profile.jfr

diff -u /path/to/before/metrics.json /path/to/after/metrics.json
```

Only compare completed runs with `PASS` in `result.txt`. Keep the same workload ID, pinned fixture, runtime JAR, JDK, machine, settings, and profiling mode on both sides. Run each build several times and compare medians/ranges: GC scheduling, weak-reference collection, JIT warmup, parser-cache lifetime, and host load can materially change single-run results. Inspect `live-objects.txt`, `lsp.log`, and the JFR timeline alongside the numeric summary before attributing a difference to a code change. Profiling adds overhead; do not compare a profiled run against an unprofiled one.

### Current profiling baseline

Three completed runs of `coldbox-cold-start-v1`, with ColdBox `v8.2.0` (674 source files), Temurin `21.0.10`, BoxLang `1.17.0-snapshot`, and LSP `1.15.0-snapshot` including the property-retention fix, produced:

| Metric | Median | Range |
| --- | --- | --- |
| GC-observed peak heap | 424.9 MiB | 416.0–425.8 MiB |
| Heap after checkpoint full GC | 249.5 MiB | 249.2–249.7 MiB |
| Estimated allocations across recording | 6.88 GiB | 6.88–6.90 GiB |
| GC pause time, including checkpoint | 395 ms | 360–396 ms |
| Workload duration, excluding checkpoint | 9.58 s | 9.44–10.23 s |
| Cold document-symbol request | 6.66 s | 6.60–7.07 s |
| Post-scan document-symbol request | 45.3 ms | 44.6–45.5 ms |

All three runs have classpath fingerprint `cd2ee17d634d4503758ea52cf7e6e0e4e90925405a2b1f90e2d5a1fe13b2e331`. Local baselines were archived in `$HOME/.local/state/boxlang-lsp-profiles/` under `run-5537649689726537163`, `run-13777097834435437960`, and `run-14057108462023915211`. This is a current-build baseline, not a pre-fix comparison or an incident-version reproduction.

The live histogram's largest class is ANTLR `ATNConfig`: roughly 3.94 million objects / 120.2 MiB of shallow size. DFA-state arrays account for another 39.6 MiB. ANTLR parser structures are therefore a substantial part of this fixture's live footprint. Subsequent [retaining-path and controlled-cleanup analysis](antlr-parser-retention-analysis.md) confirmed generated-parser static ownership and a completion latency cost after cleanup. An unmodified-policy observation also showed delayed runtime DFA eviction reducing post-GC heap to ~63 MiB while leaving prediction contexts cached. The ~249.5 MiB baseline above is an early checkpoint, not steady-state idle retention; later snapshot timing alone can materially reduce this measurement without a code change. Cold symbol latency also exposes startup/index-seeding delay. Request durations include protocol transport and queueing, not just server CPU time.

### Lifecycle v2 validation

Three identical profiled runs completed three scans apiece with all editor checks passing at `-Xmx512m`. Production classpath SHA-256: `8c5c35798ac888e1730072238f6a069ed350a3d8401fffd1869da2d80b22bf1b`. Runtime/JDK/fixture remained the same as the v1 baseline, but workload and implementation differ, so these are a new baseline, not a direct pre/post savings claim.

| Metric | Median | Range |
| --- | ---: | ---: |
| GC-observed peak | 455.1 MiB | 445.1–458.1 MiB |
| Immediate after-scan full-GC heap | 248.5 MiB | 248.3–248.6 MiB |
| After idle and editor requests | 72.5 MiB | 72.4–72.6 MiB |
| After unsaved edit | 72.5 MiB | 72.5–72.6 MiB |
| After first rescan | 248.9 MiB | 248.8–248.9 MiB |
| After second rescan | 109.5 MiB | 61.9–249.4 MiB |
| Cold document symbols | 3405 ms | 3393–3719 ms |
| First/second rescan duration | 2471 / 832 ms | 2467–2920 / 830–1231 ms |
| Full lifecycle (includes snapshots/idle) | 24.1 s | 24.1–25.7 s |
| Sampled allocation estimate | 14.49 GiB | 14.18–14.58 GiB |
| Total recorded GC pauses (includes five inspection GCs) | 891 ms | 853–977 ms |

All scan-completion closed-result cache samples were 256. These changes bound LSP-owned closed parse entries but do not eliminate runtime prediction-cache growth: post-rescan heap remains sensitive to delayed automatic runtime eviction. In particular, the wide second-rescan range is not evidence of a stable low retained-heap floor. No core parser/grammar changes were made.

Artifacts under `$HOME/.local/state/boxlang-lsp-profiles/`: `run-15627006076491060746`, `run-12816528021458515574`, `run-13601863347285105621`. The unprofiled lifecycle run `run-6529185193813450163` also passed and produced neither JFR nor histogram files. Full clean/build/test/format gate passed: 726 routine tests, zero failures/errors, one existing skip; module and shadow JAR hashes match. Schema regeneration differed only in object-key order, restored to avoid unrelated generated-file churn.

### Later stress validation

Use the pinned real ColdBox workspace as the baseline fixture. Record runtime/LSP versions, settings, heap cap, and machine resources with each result. Extend coverage to cold/warm startup, opening files during indexing, edits, repeated scans, cache eviction, and reference searches.

Run the language-server/test workload with `-Xmx512m`; record retained heap after GC, cache occupancy, scan completion/defer state, and document-symbol/completion response times. Compare the same workload before and after each slice. Define numeric latency/headroom targets from the measured baseline rather than inventing them before profiling. Passing a small GC regression is not a substitute for this stress validation.

Heap dumps and source-bearing profiling artifacts must remain local unless the user approves sharing them.

### Repository gate

```bash
./gradlew clean
./gradlew spotlessApply
./gradlew generateConfigDocs generateLintSchema
./gradlew build
./gradlew spotlessCheck
./gradlew shadowJar
ls -la build/libs/ build/module/
```

## Open questions

- How many candidate files and what maximum file sizes were present in the reported workspace?
- Was background parsing enabled, and which external mappings/dependencies were scanned?
- What is the runtime's baseline retained heap under the reported/current versions?
- Do the confirmed ColdBox parser-cache owners also dominate the incident-version workload, and how do they grow across edits and rescans?
- What workload and latency/headroom targets should define supported operation at 512 MB?
- How should reduced background coverage be represented without misleading diagnostics or noisy notifications?

## Execution log

- Planning: incident reviewed; property-node ownership and consumers traced; first red/green slice selected.
- RED: both `CFSCRIPT` and `BOXSCRIPT` cases failed with "Cached property metadata must not keep its source AST alive"; neither AST reference was collected while metadata remained live.
- GREEN: removed `ParsedProperty.node` and its constructor argument in `PropertyVisitor`; both ASTs were collected and completion fields remained unchanged.
- Reliability: five additional fresh targeted runs passed (ten language-case executions).
- Repository validation: clean, formatting apply/check, config/schema generation, full build, and shadow JAR succeeded. Full suite: 720 tests, zero failures/errors, one existing skipped test. Verified module files and identical shadow/module JARs.
- Opt-in integration: the pinned real ColdBox workspace completed a cold startup and background scan of 674 source files, with document-symbol/completion/semantic-token requests passing before and after analysis in a separate `-Xmx512m` LSP process. GC and server logs remain local in ignored build output.
- Integration validation: the complete repository gate passed again (720 routine tests, zero failures/errors, one existing skip), as did the separate ColdBox integration test. Confirmed that routine tests exclude the integration class and that the subprocess GC log reports a 512 MB maximum heap. Restored unrelated generated-schema key-order churn.
- Profiling RED/GREEN: the integration failed first for a missing LSP recording, then for a missing live-object snapshot, then for a missing comparable summary. Each slice passed after its minimal implementation using native JFR/jcmd and existing JSON support.
- Profiling validation: the full repository gate passed (720 routine tests, zero failures/errors, one existing skip), as did an unprofiled integration and three profiled runs. Verified the recorded PID is the LSP subprocess, native tools can read the recording, sensitive inventory events are disabled, profiling-off creates neither recording nor full-GC snapshot, and external artifacts survive `clean`. All repeated profiles have identical classpath fingerprints.
- Retention analysis: sampled JFR GC-root paths confirmed static DFA and prediction-context-cache owners. Controlled cleanup with GC-only request control reduced post-GC heap from 249.96 to 63.09 to 29.93 MiB while symbols/completions/tokens remained usable; completion increased from 77.5 ms with warm DFAs after GC to 295.1 ms after DFA eviction. These are diagnostic interventions, not shipped-build comparisons.
- Runtime lifecycle observation: with automatic eviction enabled, the existing delayed runtime reset reduced post-GC heap from 250.17 to 63.11 MiB after the workload; 464,181 CF prediction-cache entries remained. Temporary sidecar/reflection/root-dump instrumentation was removed from the normal test, and artifacts were archived outside `build/`. See [full analysis](antlr-parser-retention-analysis.md).
- Post-analysis validation: the complete repository gate passed again (720 routine tests, zero failures/errors, one existing skip), generated docs/schema remained unchanged, and shadow/module JARs matched. The restored ordinary profiled integration also passed with the original workload label/classpath fingerprint and no diagnostic sidecar.
- Lifecycle slices RED/GREEN: repeated tokens failed the full-parse-count check; the GC visitor failed with weak-only operation ownership; closed-file reload failed while the cache was unbounded; concurrent scan requests returned different futures. These regressions passed after the targeted fixes. Integration failed for missing lifecycle heap phases, then passed with idle/edit/two-rescan checkpoints under the real 512 MB subprocess cap.
- Implemented: strong parse-result ownership through dependent processing, single-read/synchronized weak-result retrieval, reuse of current open-document parsing, pending-version synchronization, Guava closed-result eviction, URI/index reference enumeration, AST-only cold reference parsing, cached diagnostic publication, shared initial indexing, coalesced scan futures, and at most four worker tasks per pass. Full routine tests caught and verified fixes for lint-excluded diagnostic publication after cache invalidation.
- Remaining: no incident-version reproduction or pre-/post-AST-fix build comparison. Pressure backoff, coordinated complete runtime cache reset, byte/oversized-file limits, warm process startup, and broader workload/latency targets remain open.
