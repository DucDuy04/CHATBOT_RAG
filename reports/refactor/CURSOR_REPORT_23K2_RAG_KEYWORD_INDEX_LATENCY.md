# Cursor Report 23K2 - RAG Keyword Index Latency

## Verdict

PARTIAL.

Implemented a generic per-widget in-memory keyword index/cache. Tests pass and final live Q1-Q8 remains 8/8 PASS, including Q8 refusal. Warm keyword retrieval no longer scans all 2745 chunks and usually falls in the 1-3s target band. End-to-end p50 is still high, so this is not a PASS.

## Changed

- Added `KeywordIndexCache`.
- Updated `KeywordSearchService` to score only indexed posting candidates, with old scan fallback when the index is unavailable.
- Added keyword index latency fields to `RagLatencyTrace`.
- Added index invalidation from `DocumentService` after document indexed, retry hard-delete cleanup, and soft-delete.
- Added `KeywordIndexCacheTest` and `KeywordSearchIndexTest`.
- Expanded no-hardcode audit to include the new index file.
- Added M12 to `scripts/run_23k_latency_eval.mjs`.

## Index Design

The cache key is widget/chatbot id. Each index stores active chunks only, chunk references by id, normalized token and ngram postings, document frequency, total chunks, and approximate posting count. Query candidate selection uses existing `QuerySignalExtractor` signals:

- identifiers and dates as mandatory high-signal postings
- structured labels, ngrams, and content tokens only when document frequency is low enough
- no domain dictionary or answer-specific literals

The existing BM25-like/cell-aware `scoreChunk` path remains the scorer, preserving hybrid merge semantics and source labels.

## Lifecycle

- Lazy build on first query.
- Thread-safe `ConcurrentHashMap.compute`.
- TTL default 60 minutes.
- Max cached widgets default 25.
- LRU-style eviction when over bound.
- Invalidate on document indexed/retry/delete.
- Fallback scan logs when index unavailable.

## Verification

Focused suite:

```text
Tests run: 63, Failures: 0, Errors: 0
```

Accepted targeted suite:

```text
Tests run: 134, Failures: 0, Errors: 0
```

Runtime widget:

```text
3299b4d1-9805-4473-a878-c3ab5d775bba
active_chunks=2745
normalized_table_row=2490
table_summary=120
table_row_group=0
text_table_like=0
```

Index build:

```text
buildMs=2585
terms=65027
approxPostings=437648
```

## Runtime Results

Final Q1-Q8: 8/8 PASS.

| ID | Total ms | Keyword ms | Build ms | Candidates | Verdict |
| --- | ---: | ---: | ---: | ---: | --- |
| Q1 | 36481 | 5753 | 2585 | 324 | PASS |
| Q2 | 15683 | 1043 | 0 | 292 | PASS |
| Q3 | 57119 | 2439 | 0 | 355 | PASS |
| Q4 | 4889 | 2306 | 0 | 361 | PASS |
| Q5 | 52873 | 3417 | 0 | 470 | PASS |
| Q6 | 54234 | 1194 | 0 | 171 | PASS |
| Q7 | 22185 | 1488 | 0 | 174 | PASS |
| Q8 | 5331 | 883 | 0 | 296 | PASS |

Final Q1-Q8 e2e percentiles:

```text
p50=22185 ms
p75=52873 ms
p95=57119 ms
```

Warm keywordMs:

```text
p50=1488 ms
p75=2439 ms
p95=3417 ms
```

Fallback scan count: 0.

## No-Hardcode

No production code added document/course/domain-specific terms, page numbers, dates, or answer literals. The no-hardcode tests passed.

## Bottleneck After Optimization

The full-corpus keyword scan is no longer the hot path. Remaining latency is mainly downstream scoring/rerank over merged candidates and client-observed provider streaming time for long list/compare answers.

## Known Limitations

- End-to-end p50 did not improve enough for PASS.
- Some broad table/list queries still produce several hundred keyword candidates.
- M12 latency probe retrieved fast keyword candidates but produced a partial answer wording; Q1-Q8 acceptance correctness remained intact.

## Next

Optimize merged candidate scoring/rerank using high-confidence normalized-row matches and indexed candidate evidence so table/list queries do less work after retrieval.
