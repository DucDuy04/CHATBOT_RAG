# CURSOR REPORT — 23J4 Cell-aware Normalized Row Retrieval

**Date:** 2026-05-24

## Files read

| Path | Purpose |
|------|---------|
| `KeywordSearchService.java` | Confirmed haystack-only scoring, no cells_json |
| `RagRetrievalService.java` | Final selection, table row boosts |
| `QuerySignalExtractor.java` | Signals for identifiers/labels/ngrams |
| `NormalizedTableService.java` | Row ingest, page range |
| `LogicalTableState.java` | Continuation state |
| `DocumentChunk.java` | `cellsJson` transient |
| `ChunkingService2.java` | Row chunk page metadata |
| `PromptBuilderService.java` | Table context notes |
| `NORMALIZED_TABLE_SUPPRESSION_FIX_23J3_20260523.md` | Prior baseline |

## Files modified

| Path | Layer | Change |
|------|-------|--------|
| `CellAwareTableRowScorer.java` | service | New cell-aware scoring |
| `KeywordSearchService.java` | service | Cell boost; skip fuzzy label on normalized rows |
| `RagRetrievalService.java` | service | Cell rerank, summary downrank, compare coverage, debug logs |
| `NormalizedTableService.java` | service | State reset, row page span cap |
| `ChunkingService2.java` | service | Row-level page on chunks |
| `PromptBuilderService.java` | service | Normalized row guard note |
| `CellAwareNormalizedRowRetrievalTest.java` | test | 10 generic tests |

## Hardcode / migration

- **Hardcode:** None (generic Code/Name/Group/Room tests only)
- **Migration:** None

## Scoring logic (summary)

Parse cells → score identifiers/labels/phrases per cell with boundary rules → multi-signal boost → column intent from question → penalty on label mismatch → add to keyword + final hybrid score.

## Runtime QA

| Item | Status |
|------|--------|
| Re-ingest SoTay | NOT RUN |
| Q1–Q8 Playground | NOT RUN |
| Expected | Q1/Q2/Q7 improve vs 23J3 after deploy |

## Old vs new

| Metric | 23J3 | 23J4 (code) |
|--------|------|-------------|
| Row scoring | Canonical substring | Cell-level + boundary |
| Nhóm 2 vs 18 | Could match wrong | Boundary + mismatch penalty |
| section_summary vs row | Weak | Downrank when exact row |
| Page on row chunk | Section-wide | Row page when set |

## Conclusion

**PARTIAL** — implementation and unit tests complete; runtime acceptance pending operator re-ingest + Q1–Q8.
