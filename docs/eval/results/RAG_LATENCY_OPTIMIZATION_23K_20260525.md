# 23K RAG Latency Optimization

Date: 2026-05-25

## Final Verdict

PARTIAL.

Instrumentation and low-risk optimization are implemented and tested. Corrected runtime smoke on an active 2745-chunk local widget preserved Q1/Q2/Q4/Q8 correctness, including Q8 refusal, but the measured p50 remained high and did not meet the 30% median improvement target. Phase logs show local retrieval dominates before first token, especially keyword scan and rerank/candidate scoring.

The requested widget `bf95583e-a4a2-41de-b772-f9ebdcaa1e73` is soft-deleted in this local DB (`active chunks=0`), so runtime smoke used active sibling widget `3299b4d1-9805-4473-a878-c3ab5d775bba` with 2745 active chunks.

## Files Changed

- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/RagLatencyTrace.java`
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/ChatService.java`
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/RagRetrievalService.java`
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/EmbeddingService.java`
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/PromptBuilderService.java`
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/dto/RetrievedContext.java`
- Tests: `ChatServiceLlmParamsTest`, `FinalContextSelectionTest`, `EmbeddingServiceCacheTest`, `PromptBuilderServiceTest`
- `scripts/run_23k_latency_eval.mjs`

## Baseline Latency Table

| Source | Median / Typical | Notes |
| --- | ---: | --- |
| User-observed 23K baseline | ~30000 ms | Manual UI observation before this task. |
| 23J5B notes | 4700-6500 context chars | Table questions; Q8 about 6200 chars. |
| 23J5B candidate pool | 1992 before / 240 scored | Example table-like query. |

No pre-change live baseline was rerun after edits; success is therefore not claimed.

## Optimized Runtime Smoke

Config: temperature 0.2, maxTokens 768 upper bound, Context Top-N 15 upper bound, Hybrid Search ON, playground debug sources ON.

| ID | Verdict | Total ms | EffectiveTopN | EffectiveMaxTokens | Context chars | Candidates |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| Q1 | PASS | 45118 | 15 | 512 | 3097 | 240/240 |
| Q2 | PASS | 24237 | 7 | 384 | 1451 | 240/240 |
| Q4 | PASS | 19601 | 7 | 384 | 1953 | 30/30 |
| Q8 | PASS | 11947 | 15 | 768 | 4511 | 2727/2727 |

Latency percentiles for this 4-question smoke: p50 19601 ms, p75 24237 ms, p95 45118 ms.

## Phase Timing Samples

| ID | Total ms | Query embed | Vector | Keyword | Scoring | Context select | Main bottleneck |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| Q1 | 43280 | 2279 | 239 | 19217 | 4367 | 155 | keyword + rerank/scoring |
| Q2 | 23397 | 1405 | 159 | 9961 | 2601 | 140 | keyword + rerank/scoring |
| Q4 | 18876 | 1153 | 134 | 16131 | 1144 | 12 | keyword scan |
| Q8 | 11358 | 827 | 181 | 8207 | 1388 | 0 | keyword scan |

Note: final code now defers SSE latency trace close until streaming completion so future logs include LLM first-token and total time. The smoke above was captured before that final trace-close patch, so LLM fields in those specific logs are incomplete.

## Optimizations Applied

- Structured request trace id and phase timing logs: `[RAG][latency] trace=...`.
- Adaptive maxTokens with request value treated as upper bound.
- Adaptive final context Top-N with request/UI value treated as upper bound.
- Accuracy guard: multi-attribute row lookup keeps requested context ceiling.
- Bounded in-memory query embedding cache: normalized query + embedding provider + Qdrant collection.
- Compact prompt context for `normalized_table_row` from table name, row index, group context, and non-empty `cells_json`.

## Skipped

- Parallel vector/keyword retrieval: skipped because keyword scan is currently CPU/DB heavy and ranking semantics should be preserved until phase timings are stable.
- Candidate scoring bound reduction below 240: skipped after Q1 regression risk; the current safe bound remains.
- Fresh ingest: not required; runtime/prompt/retrieval code only.

## Correctness

Corrected smoke: Q1, Q2, Q4, Q8 PASS. Earlier active-widget full run before the multi-attribute guard showed Q1 regression; that was fixed and covered by test.

Q1 answer after fix: correct lecturer, day, period, room.
Q8 answer: out-of-scope refusal.

## Context Before / After

23J baseline table context chars were roughly 4700-6500. Smoke after compaction:

- Q1: 3097 chars, 15 contexts
- Q2: 1451 chars, 7 contexts
- Q4: 1953 chars, 7 contexts
- Q8: 4511 chars, 15 contexts

## No-Hardcode Proof

No production logic adds document/course/domain-specific literals or answer-specific branches. No-hardcode tests passed:

- `NoHardcodedLexiconInCellAwareScorerTest`
- `NoHardcodedLexiconInTableNormalizerTest`

## Tests Run

Focused suite: 84 tests, 0 failures, 0 errors.

Accepted targeted suite: 122 tests, 0 failures, 0 errors.

Command:

```text
cd Backend
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
.\mvnw.cmd "-Dtest=HybridKeywordSearchTest,CellAwareNormalizedRowRetrievalTest,NoHardcodedLexiconInCellAwareScorerTest,NoHardcodedLexiconInTableNormalizerTest,NormalizedTableSuppressionTest,NormalizedTableIngestTest,FinalContextSelectionTest,RetrievalTopKTest,ChatServiceSourcePresentationTest,RagTokenAuditTest,ChatServiceLlmParamsTest,EmbeddingServiceCacheTest,PromptBuilderServiceTest" test
```

## Known Limitations

- Median latency target not met.
- Keyword branch scans 2745 chunks per request and dominates several questions.
- Rerank is called on candidate sets and can add multiple seconds.
- Full Q1-Q8 after the final SSE trace-close patch was not rerun.

## Next Recommended Task

Optimize the keyword branch with a per-widget in-memory lexical index or cached corpus/IDF snapshot, then rerun the full Q1-Q8 + manual latency set before changing scoring bounds.
