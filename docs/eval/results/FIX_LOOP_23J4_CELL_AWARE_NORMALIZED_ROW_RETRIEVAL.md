# FIX LOOP — 23J4 Cell-aware Normalized Row Retrieval

**Date:** 2026-05-24  
**Verdict:** PARTIAL

## Loop

1. **23J3** — raw table suppression PASS; Q1/Q2/Q7 retrieval wrong row / missing identifier in top rows.
2. **23J4** — cell-aware scoring on parsed cells; label boundary; summary downrank; logical table state cleanup; compare coverage.
3. **Unit tests** — 10 new tests in `CellAwareNormalizedRowRetrievalTest` + existing 23J3/23I tests PASS (Maven Docker).
4. **Runtime** — NOT RUN; next: rebuild, re-ingest SoTay, Playground Q1–Q8 with `playgroundDebugSources=true`.

## Files changed

- `CellAwareTableRowScorer.java` (new)
- `KeywordSearchService.java`
- `RagRetrievalService.java`
- `NormalizedTableService.java`
- `ChunkingService2.java`
- `PromptBuilderService.java`
- `CellAwareNormalizedRowRetrievalTest.java` (new)

No migration. No SoTay hardcode.

## Next loop if PARTIAL persists

- Persist `cells_json` in MySQL (optional migration) for keyword corpus without canonical parse.
- Timetable column alias map from header row only (still generic).
- Rerank payload include cells excerpt for cross-encoder.
