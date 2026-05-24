# Cell-aware Normalized Row Retrieval — 23J4

**Date:** 2026-05-24  
**Task:** 23J4 — Cell-aware Normalized Row Retrieval + Table State Cleanup  
**Verdict:** **PARTIAL** (code + unit tests PASS; runtime Q1–Q8 NOT RUN in this session)

---

## Root cause (23J3 runtime)

| Issue | Confirmed from source |
|-------|----------------------|
| Wrong row for Q1/Q2 | `KeywordSearchService.scoreChunk` scored **canonical haystack only**; `cells_json` is `@Transient` (not in MySQL) — substring match let **Nhóm 2** hit **Nhóm 18** |
| Q7 KTR3185 regression | Identifier boost on full haystack, not **exact cell value**; rerank/hybrid diluted row with code in prose |
| section_summary leakage | `applyTableRowPriorityAdjustments` did not downrank table-like summaries when exact normalized row exists |
| Page range 21–140 | `ChunkingService2` used **section** `startPage/endPage` for rows; `LogicalTableState` carried `tableName` across wide sections |

## Cell-aware scoring design

New `CellAwareTableRowScorer`:

- Parse cells from `cells_json` or canonical `Key: value.` lines
- `cellAwareScore = exactIdentifier + exactLabel + phrase + multiSignalSameRow + columnIntent + groupContext + tableName - mismatchPenalty`
- Wired into `KeywordSearchService` (keyword path) and `RagRetrievalService` (final rerank boost + summary downrank)

## Exact label boundary

- `valueMatchesBoundary()` — token boundaries for numeric labels (`nhom 2` ≠ `nhom 18`, `hk 2` ≠ `hk 12`)
- `cellConflictsWithLabel()` → `mismatchPenalty` when query label type matches column but value differs

## Compare coverage

- `RagRetrievalService.applyCompareLabelCoverage()` — ensure each structured label in compare query has a row; evict rows that **conflict** with query labels but match none

## Summary downrank

- `demoteLeakyTextCandidates` / `applyTableRowPriorityAdjustments`: table-like `section_summary` / `parent_section_summary` ×0.35 when exact normalized row present

## Table state cleanup

- `NormalizedTableService.resolveContinuationState()` — reset on page gap >5, new caption, section span >30 pages
- Row page metadata: `MAX_ROW_PAGE_SPAN=3`; `ChunkingService2` uses `rowPageStart` for chunk pages

## Build / test

| Command | Result |
|---------|--------|
| Maven Docker `compile` | **PASS** |
| Maven Docker `CellAwareNormalizedRowRetrievalTest,NormalizedTableSuppressionTest,NormalizedTableIngestTest,HybridKeywordSearchTest,FinalContextSelectionTest` | **PASS** (42 tests) |
| Local `mvnw` | **NOT RUN** (JAVA_HOME) |
| `docker compose config -q` | **PASS** |

## Re-ingest

| Field | Value |
|-------|-------|
| Status | **NOT RUN** (requires `docker compose up --build` + upload SoTay + new chatbot) |
| Expected chatbot name | `23J4 SoTayHocVu Cell-aware Verify` |

After re-ingest, expect similar distribution to 23J3: ~2475 `normalized_table_row`, ~106 `table_summary`, 0 raw TKB mega-text.

## Runtime Q1–Q8

**NOT RUN** in agent session (no Groq playground calls). Expected improvements after deploy + re-ingest:

| ID | Expected vs 23J3 |
|----|------------------|
| Q1/Q2 | PASS/PARTIAL — exact Nhóm cell match |
| Q3 | PASS/PARTIAL — compare coverage both groups |
| Q4–Q6 | PARTIAL — parser/column mapping may still limit |
| Q7 | PASS — identifier exact cell boost |
| Q8 | PASS — OOS unchanged |

## Debug evidence (unit tests)

- Test `exactLabelBoundary_group2BeatsGroup18` — row A score > row B for query with Nhóm 2
- Test `identifierExact_abc123BeatsAbc124`
- Test `multiSignalSameRow_itemAGroup2Wins`
- Logs: `[RAG][cell-aware]` and `[RAG][cell-aware-top]` in `RagRetrievalService` for runtime Q1/Q2/Q7

## Remaining failures / risks

- Runtime Q1–Q8 unverified until manual playground after re-ingest
- `cells_json` still not persisted in MySQL — relies on canonical parse fallback for keyword corpus
- Tabula column mapping for SoTay TKB may still affect Q4–Q6 cells

## Conclusion

**PARTIAL** — retrieval logic and tests complete; production QA pending re-ingest + Playground Q1–Q8.
