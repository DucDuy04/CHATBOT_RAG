# 23K2 RAG Keyword Index Latency

Date: 2026-05-25

## Final Verdict

PARTIAL.

The generic per-widget in-memory keyword index is implemented and tested. Final live Q1-Q8 on active widget `3299b4d1-9805-4473-a878-c3ab5d775bba` remained 8/8 PASS, including Q8 refusal. Warm keyword retrieval no longer scans all 2745 chunks and dropped from the 23K 8-19s range to mostly 0.9-2.4s on Q1-Q8 warm queries.

End-to-end latency is still not a PASS: final Q1-Q8 p50 was 22185 ms. The remaining bottleneck is outside the full-corpus keyword scan, mainly downstream scoring/rerank over the merged pool and provider/streaming wall time for long list/compare answers.

## Files Changed

- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/KeywordIndexCache.java`
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/KeywordSearchService.java`
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/RagLatencyTrace.java`
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/DocumentService.java`
- `Backend/src/test/java/KLTN/RAG_CHATBOT_BE/service/KeywordIndexCacheTest.java`
- `Backend/src/test/java/KLTN/RAG_CHATBOT_BE/service/KeywordSearchIndexTest.java`
- `Backend/src/test/java/KLTN/RAG_CHATBOT_BE/service/NoHardcodedLexiconInTableNormalizerTest.java`
- `scripts/run_23k_latency_eval.mjs`

## Implementation Summary

Added `KeywordIndexCache`, a bounded in-memory per-widget lexical index. It builds lazily from active chunks only, caches normalized token/ngram postings, document frequency, corpus size, and document-order chunk references. `KeywordSearchService` now uses postings to generate a candidate subset, then runs the existing generic `scoreChunk` logic over only that subset. The old scan path remains as fallback when the index is unavailable.

Candidate selection is generic: identifiers and dates are mandatory high-signal terms; structured labels, ngrams, and content terms are selected by low document frequency. No document/course/domain dictionary was added.

## Cache Lifecycle

- Lazy build on first query per widget.
- `ConcurrentHashMap.compute` prevents duplicate builds for the same widget.
- TTL: `rag.retrieval.keyword-index.ttl-ms`, default 60 minutes.
- LRU-ish bound: `rag.retrieval.keyword-index.max-widgets`, default 25 widgets.
- Invalidation added after document indexing, retry hard-delete cleanup, and document soft-delete.

## No-Hardcode Proof

Production index logic has no SoTayHocVu/course/cohort/date/page/answer-specific literals. The no-hardcode audit now includes `KeywordIndexCache.java` and passed.

## Tests Run

Focused suite with new tests:

```text
Tests run: 63, Failures: 0, Errors: 0
```

Accepted targeted suite with new tests:

```text
Tests run: 134, Failures: 0, Errors: 0
```

Command:

```text
cd Backend
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
.\mvnw.cmd "-Dtest=HybridKeywordSearchTest,CellAwareNormalizedRowRetrievalTest,NoHardcodedLexiconInCellAwareScorerTest,NoHardcodedLexiconInTableNormalizerTest,NormalizedTableSuppressionTest,NormalizedTableIngestTest,FinalContextSelectionTest,RetrievalTopKTest,ChatServiceSourcePresentationTest,RagTokenAuditTest,ChatServiceLlmParamsTest,EmbeddingServiceCacheTest,PromptBuilderServiceTest,KeywordIndexCacheTest,KeywordSearchIndexTest" test
```

## Active Widget Audit

Runtime used widget/chatbot:

```text
3299b4d1-9805-4473-a878-c3ab5d775bba
```

DB audit:

```text
active_chunks=2745
normalized_table_row=2490
table_summary=120
table_row_group=0
text_table_like=0
```

Index build:

```text
chunks=2745
buildMs=2585
terms=65027
approxPostings=437648
```

## Q1-Q8 Correctness

Config: temperature 0.2, maxTokens 768 upper bound, Context Top-N 15 upper bound, Hybrid Search ON, playground debug sources ON.

| ID | Verdict | Total ms | Keyword ms | Index hit | Build ms | Candidates from postings | Candidates scored | Notes |
| --- | --- | ---: | ---: | --- | ---: | ---: | ---: | --- |
| Q1 | PASS | 36481 | 5753 | true | 2585 | 324 | 324 | Correct lecturer/day/period/room; cold index. |
| Q2 | PASS | 15683 | 1043 | true | 0 | 292 | 292 | Correct period 5-7. |
| Q3 | PASS | 57119 | 2439 | true | 0 | 355 | 355 | Correct group comparison. |
| Q4 | PASS | 4889 | 2306 | true | 0 | 361 | 361 | Correct registration range. |
| Q5 | PASS | 52873 | 3417 | true | 0 | 470 | 470 | Correct architecture K46 list. |
| Q6 | PASS | 54234 | 1194 | true | 0 | 171 | 171 | Correct biotech K46 list. |
| Q7 | PASS | 22185 | 1488 | true | 0 | 174 | 174 | Correct title/5 credits. |
| Q8 | PASS | 5331 | 883 | true | 0 | 296 | 296 | Refused out-of-scope. |

Tally: 8/8 PASS.

## Latency Summary

Final Q1-Q8 end-to-end:

```text
p50=22185 ms
p75=52873 ms
p95=57119 ms
```

Warm keywordMs on Q1-Q8 excluding cold Q1:

```text
p50=1488 ms
p75=2439 ms
p95=3417 ms
```

23K keyword baseline samples were 8207-19217 ms. Final warm samples were 883-3417 ms, with no full keyword scan fallback.

## Runtime Latency Set

The 12-question latency set was covered by final Q1-Q8 plus focused M3/M5/M7/M12 runs. Representative final/probe keyword metrics:

| Probe | Total ms | Keyword ms | Index hit | Candidates | Scoring ms | Effective Top-N | Effective maxTokens | Verdict |
| --- | ---: | ---: | --- | ---: | ---: | ---: | ---: | --- |
| M3 room lookup | 30176 | 65 | true | 12 | 2113 | 7 | 384 | PASS |
| M5 code lookup | 3024 | 455 | true | 141 | 563 | 7 | 384 | PASS |
| M7 start time/date | 39568 | 2626 | true | 365 | 1266 | 15 | 512 | PASS |
| M12 day/period | 30712 | 326 | true | 75 | 2145 | 7 | 384 | PARTIAL answer wording |

Fallback scan count in final/probe logs: 0.

## Before / After

| Metric | 23K | 23K2 final |
| --- | ---: | ---: |
| Keyword corpus scan | 2745 chunks/request | 0 warm scan fallbacks |
| Keyword candidate scoring | up to active corpus | 75-470 indexed candidates in final/probe set |
| Q2 keywordMs | 9961 ms | 1043 ms |
| Q4 keywordMs | 16131 ms | 2306 ms |
| Q8 keywordMs | 8207 ms | 883 ms |
| Q1 keywordMs | 19217 ms | 5753 ms cold, includes 2585 ms build |

## Memory Notes

For the 2745-chunk widget, the index logged 65027 terms and 437648 postings. Cache size is bounded by widget count and TTL. Entries are invalidated on document lifecycle changes.

## Known Limitations

- Candidate preselection is much narrower but still generic; some broad table/list queries score 300-470 keyword candidates.
- Downstream rerank/scoring still takes several seconds on the 240-candidate merged pool.
- End-to-end p50 did not meet the 12-14s target.
- `llmFirstTokenMs` in application logs is lower than client-observed first token wall time; client timings remain the source for end-to-end totals.

## Next Recommended Task

Optimize final merged-pool scoring/rerank for table/list queries, probably by reusing the indexed candidate subset and cell-aware exact matches to avoid expensive rerank/scoring over all 240 merged candidates when high-confidence normalized rows are already present.
