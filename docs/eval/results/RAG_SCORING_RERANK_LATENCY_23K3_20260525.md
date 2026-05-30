# 23K3 RAG Scoring/Rerank Latency

Date: 2026-05-26

## Final Verdict

FAIL.

The two-stage merged-candidate scorer, richer latency trace fields, and generic exact-identifier safety logic are implemented. Backend builds and the accepted targeted suite passes. End-to-end latency improved substantially versus 23K2, but Q7 regressed at runtime and therefore this task cannot be accepted.

The blocker is not missing retrieval of `KTR3185`; the top row is retrieved, but its normalized cells contain a noisy split title (`... tổ hợp đa chức 5`). A cleaner same-identifier row exists in the active widget, but the final answer still copied the noisy value. Per the task rules, this is a FAIL because Q7 did not remain correct.

## Files Changed

- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/RagRetrievalService.java`
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/KeywordSearchService.java`
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/RagLatencyTrace.java`
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/PromptBuilderService.java`
- `Backend/src/test/java/KLTN/RAG_CHATBOT_BE/service/FinalContextSelectionTest.java`
- `scripts/run_23k3_latency_eval.mjs`

## Implementation Summary

Implemented generic two-stage scoring after vector+keyword merge:

- cheap pre-score over merged candidates using vector presence, keyword score, BOTH-source agreement, chunk type, token/ngram overlap, exact identifier/date/label signals, and low-noise exact-identifier preference
- expensive rerank/cell-aware scoring only on an adaptive top subset
- broader budgets for compare/list; smaller budgets for fact-like rows
- skipped the old table pre-bound for exact identifier/date queries so exact evidence can reach the new scorer
- widened keyword injection for exact identifier/date queries while still capping expensive scoring
- added exact-identifier source diversity to keep a cleaner alternative table/section when selected exact evidence all comes from one source
- added a generic prompt guard to prefer cleaner/fuller same-identifier normalized-row values over broken fragments

## Scoring/Rerank Design

Default expensive scoring budgets:

| Query shape | Budget |
| --- | ---: |
| fact / weak exact | 80 |
| multi-attribute lookup | 220 |
| compare | 160 |
| broad list | 220 |
| OOS/weak | 60 |

The UI Context Top-N remains only the final context limit. It is not used as the expensive scoring limit.

## Instrumentation

`RagLatencyTrace` now logs:

```text
vectorCandidates
keywordCandidatesFromIndex
mergedCandidatesBeforeDedupe
mergedCandidatesAfterDedupe
cheapPreScoreCandidates
expensiveCellAwareCandidates
finalRerankCandidates
keywordCandidatesDroppedByCheapGate
scoringMs
cellAwareMs
finalSortMs
sourceDiversityMs
promptContextBuildMs
llmFirstTokenMs
llmTotalMs
totalMs
```

## No-Hardcode Proof

No production course/document/page/date/answer literals were added. The new logic is based on query-extracted identifiers/dates/labels, normalized row noise, source diversity, and generic table/query shape. No `SoTayHocVu`, `KNM1013`, `KTR3185`, cohort-specific answer, page number, or Vietnamese domain label branch was added.

No-hardcode tests passed:

```text
NoHardcodedLexiconInCellAwareScorerTest
NoHardcodedLexiconInTableNormalizerTest
```

## Tests Run

Focused final checks:

```text
Tests run: 20, Failures: 0, Errors: 0
```

Accepted targeted suite:

```text
Tests run: 140, Failures: 0, Errors: 0
```

Command:

```text
cd Backend
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
.\mvnw.cmd "-Dtest=HybridKeywordSearchTest,CellAwareNormalizedRowRetrievalTest,NoHardcodedLexiconInCellAwareScorerTest,NoHardcodedLexiconInTableNormalizerTest,NormalizedTableSuppressionTest,NormalizedTableIngestTest,FinalContextSelectionTest,RetrievalTopKTest,ChatServiceSourcePresentationTest,RagTokenAuditTest,ChatServiceLlmParamsTest,EmbeddingServiceCacheTest,PromptBuilderServiceTest,KeywordIndexCacheTest,KeywordSearchIndexTest" test
```

## Active Widget

Runtime used:

```text
chatbotId/widgetId=3299b4d1-9805-4473-a878-c3ab5d775bba
active_chunks=2745
normalized_table_row=2490
table_summary=120
table_row_group=0
text_table_like=0
```

## Q1-Q8 Correctness

Config: temperature 0.2, maxTokens 768 upper bound, Context Top-N 15 upper bound, Hybrid Search ON, playground debug sources ON.

| ID | Verdict | Total ms | Notes |
| --- | --- | ---: | --- |
| Q1 | PASS | 18915 | Correct lecturer/day/period/room. |
| Q2 | PASS | 9083 | Correct lecturer/day/period/room. |
| Q3 | PASS | 9547 | Correct group comparison. |
| Q4 | PASS | 5769 | Correct date range, included 10h00. |
| Q5 | PASS | 13263 | Returned architecture K46 semester-2 course list. |
| Q6 | PASS | 10772 | Returned biotech K46 semester-2 course list, though answer wording said not directly listed before listing items. |
| Q7 | FAIL | 9562; later probes 17858-23836 | Returned noisy title fragment `ồ án kiến trúc công trình tổ hợp đa chức 5` instead of the correct title. Credits were correct. |
| Q8 | PASS | 4199 | Refused out of scope. |

Tally: 7/8 PASS. Q8 refusal remained correct.

## Latency Comparison vs 23K2

23K2 Q1-Q8:

```text
p50=22185 ms
p75=52873 ms
p95=57119 ms
```

Best full 23K3 Q1-Q8 run before Q7-focused probes:

```text
p50=9547 ms
p75=10772 ms
p95=18915 ms
```

This is a strong latency improvement, but it is not acceptable because Q7 regressed.

## Candidate Counts

Representative final logs:

| ID | keyword postings | keyword injected | merged/deduped | cheapScored | cellAwareScored | finalRerank |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Q1 | 324 | 30 | 140 | 140 | 99 | 120 |
| Q2 | 324 | 30 | 122 | 122 | 97 | 120 |
| Q3 | 355 | 30 | 240 | 240 | 151 | 160 |
| Q4 | 361 | 200 cap path but merged 30 | 30 | 30 | 21 | 30 |
| Q5 | 470 | 30 | 240 | 240 | 210 | 220 |
| Q6 | 171 | 30 | 240 | 240 | 219 | 220 |
| Q7 | 174 | 174 | expanded pool, final expensive 220 | up to full expanded/merged | 219 | 220 |
| Q8 | 296 | 30 | 2727 | 2727 | 214 | 220 |

23K2 commonly scored 240 merged candidates and ran cell-aware scoring over broad pools. 23K3 reduces expensive scoring for Q1/Q2/Q3/Q4 and controls Q8 from 2727 cheap candidates to 220 expensive candidates.

## Bottleneck After Optimization

Local scoring improved on several questions, and end-to-end p50 improved by more than 50%. Remaining high times are a mix of:

- LLM/provider first-token wall time
- cell-aware scoring for broad list and exact multi-attribute pools
- Q7 correctness blocker caused by noisy normalized row evidence outranking or dominating cleaner same-identifier evidence

## Known Limitations

- Q7 still fails; do not claim PASS.
- Broad list Q5/Q6 need larger budgets to preserve completeness, so scoring remains several seconds.
- `llmFirstTokenMs` in backend logs can be lower than client-observed first-token time.
- The Q7 issue likely needs a generic normalized-row repair or evidence-quality model, not more latency pruning.

## Next Recommended Task

Fix generic normalized-row evidence quality for rows where a single logical row is split across adjacent `col_N` cells or contains multiple unrelated identifiers. The fix should either repair row cells generically before prompt construction or rank cleaner same-identifier rows ahead of noisy multi-identifier rows without using course/document-specific terms.
