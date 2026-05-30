# Cursor Report 23K - RAG Latency Optimization

## Verdict

PARTIAL.

Implemented latency instrumentation plus safe optimizations, and kept targeted tests green. Runtime smoke confirms correctness for Q1/Q2/Q4/Q8 after a multi-attribute lookup guard, but latency target is not met. The bottleneck is now clearly measured: keyword scan plus rerank/scoring dominate before first token.

## Changed

- Added `RagLatencyTrace` request trace/timing logs.
- Added adaptive maxTokens: fact 384, compare/multi-attribute 512, list 768, capped by user request.
- Added adaptive effective Top-N: fact 7, compare 10, list 15, multi-attribute lookup keeps requested ceiling.
- Added query embedding cache with normalized query/provider/collection key.
- Added compact normalized-row prompt context from non-empty `cells_json`.
- Added eval script `scripts/run_23k_latency_eval.mjs`.

## Runtime Findings

The provided 23J6C widget is soft-deleted locally, so the first run returned zero active contexts. Active sibling widget `3299b4d1-9805-4473-a878-c3ab5d775bba` has 2745 active chunks and was used for smoke.

Corrected smoke:

| ID | Verdict | ms | EffectiveTopN | EffectiveMaxTokens |
| --- | --- | ---: | ---: | ---: |
| Q1 | PASS | 45118 | 15 | 512 |
| Q2 | PASS | 24237 | 7 | 384 |
| Q4 | PASS | 19601 | 7 | 384 |
| Q8 | PASS | 11947 | 15 | 768 |

p50 19601 ms, p75 24237 ms, p95 45118 ms.

Representative phase timings:

- Q1: keyword 19217 ms, scoring 4367 ms, context chars 3097.
- Q2: keyword 9961 ms, scoring 2601 ms, context chars 1451.
- Q4: keyword 16131 ms, scoring 1144 ms, context chars 1953.
- Q8: keyword 8207 ms, scoring 1388 ms, context chars 4511.

## Tests

Accepted targeted suite: 122 tests, 0 failures, 0 errors.

## No-Hardcode

No production domain/course/date/file-specific literals were added. Existing no-hardcode tests pass.

## Skipped

- Parallel retrieval.
- Candidate bound reduction below 240.
- Fresh re-ingest.
- Final full Q1-Q8 after the SSE trace-close patch.

## Next

Build a generic per-widget keyword index/cache so keyword search does not scan and score the entire 2745-chunk corpus on each request. That is the highest-confidence latency target without touching accepted normalized-row retrieval accuracy.
