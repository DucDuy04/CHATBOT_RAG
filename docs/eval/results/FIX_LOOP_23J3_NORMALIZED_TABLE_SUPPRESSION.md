# FIX LOOP — 23J3 Normalized Table Suppression

**Date:** 2026-05-23  
**Verdict:** PARTIAL

## Loop

1. 23J2 found raw TKB in text chunks + retrieval regression.
2. 23J3 implemented cell/header suppression + retrieval boost.
3. Re-ingest SoTay → suppressedRawChars=35746, 0 leaky mega text chunks.
4. Q1–Q8: sources improved (normalized_table_row dominant); answers Q1/Q2/Q7 still FAIL.

## Next loop candidates

- Timetable-specific normalized row ranking (group label column match).
- Suppress table-like content inside `section_summary` / `parent_section_summary`.
- Improve column mapping for SoTay TKB Tabula headers (Nhóm column).

## Files changed (code)

- `NormalizedTableService.java`
- `ChunkingService2.java`
- `DocumentParserService.java`
- `KeywordSearchService.java`
- `RagRetrievalService.java`
- `TableIngestMetrics.java`
- `DocumentService.java`
- `NormalizedTableSuppressionTest.java`

No migration. No hardcode SoTay.
