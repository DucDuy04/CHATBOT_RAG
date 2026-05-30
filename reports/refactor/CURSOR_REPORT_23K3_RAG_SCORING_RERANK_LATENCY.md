# Cursor Report 23K3 - RAG Scoring/Rerank Latency

## Verdict

FAIL.

The scoring/rerank latency work is implemented and tested, and Q1-Q8 latency improved materially. However, runtime correctness is 7/8 because Q7 returns a noisy title fragment for the `KTR3185` lookup. Per the task rules, this is a FAIL.

## Changed

- Added post-merge two-stage scoring in `RagRetrievalService`.
- Added cheap pre-score counters and bounded expensive rerank/cell-aware scoring.
- Added exact identifier/date keyword injection widening in `KeywordSearchService`.
- Added exact-identifier source diversity and low-noise exact-row preference.
- Added detailed latency fields in `RagLatencyTrace`.
- Added a generic normalized-row prompt note about preferring cleaner same-identifier values.
- Added `scripts/run_23k3_latency_eval.mjs`.
- Added/updated tests in `FinalContextSelectionTest`.

## Verification

Accepted targeted suite:

```text
Tests run: 140, Failures: 0, Errors: 0
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

## Runtime Results

Final full Q1-Q8 run:

| ID | Verdict | Total ms |
| --- | --- | ---: |
| Q1 | PASS | 18915 |
| Q2 | PASS | 9083 |
| Q3 | PASS | 9547 |
| Q4 | PASS | 5769 |
| Q5 | PASS | 13263 |
| Q6 | PASS | 10772 |
| Q7 | FAIL | 9562 |
| Q8 | PASS | 4199 |

Q7 was rerun several times after mitigation attempts and still failed, with totals from 17.8s to 23.8s.

Latency percentiles for the full run:

```text
p50=9547 ms
p75=10772 ms
p95=18915 ms
```

23K2 baseline:

```text
p50=22185 ms
p75=52873 ms
p95=57119 ms
```

## Candidate / Scoring Summary

The final scorer now logs and uses:

- `cheapPreScoreCandidates`
- `expensiveCellAwareCandidates`
- `finalRerankCandidates`
- `keywordCandidatesDroppedByCheapGate`
- `cellAwareMs`
- `finalSortMs`
- `sourceDiversityMs`

Representative effect:

- Q1/Q2 exact row lookups: expensive candidates around 120 instead of 240.
- Q3 compare: expensive candidates 160.
- Q4 date/fact: expensive candidates 30.
- Q8 OOS: 2727 cheap candidates but only 220 expensive candidates.
- Q5/Q6 list: budget remains broad at 220 for correctness.

## No-Hardcode

No production document/course/cohort/page/date/answer literal was added. Logic remains generic around query signals, row noise, source diversity, and query shape. No-hardcode tests passed.

## Why It Failed

Q7 retrieves a normalized row whose cells are noisy:

```text
Tên học phần: KTR3185
col_4: ... tổ hợp đa chức 5
```

A cleaner same-identifier schedule row exists, but the final answer still copies the noisy curriculum row. This is a correctness failure, not a missing-index or fallback-scan failure.

## Next

Do a narrow generic normalized-row evidence-quality fix:

- detect rows where one logical value is split across adjacent generic columns
- prefer cleaner same-identifier rows in prompt/source ordering
- avoid selecting noisy multi-identifier aggregate rows as the primary answer source for single-code lookups

Do not continue latency pruning until Q7 is stable again.
