# NORMALIZED_TABLE_INGEST_23J — 20260523

## Architecture old vs new

| Stage | Before (23I) | After (23J) |
|-------|--------------|-------------|
| Table in PDF | Tabula → `[TABLE_START]` markdown → `table_row_group` (10 rows) + `table_summary` | Same detect path → **normalized_table_row** (1 row/chunk) + **table_summary** |
| Raw page text | Full page text + table markdown (duplicate) | Text **suppressed** where cells overlap accepted Tabula markdown |
| Failed normalize | `text_table_like` or raw table in `text` | **No ingest** for table region; `failedTables++` |
| Qdrant payload | `chunk_type`, `table_id` | + `table_name`, `row_index`, `cells_json`, `group_context` (transient → payload) |

## Normalized row schema

```json
{
  "tableName": "<caption|section|generic>",
  "rowIndex": 12,
  "cells": { "Column A": "value" },
  "pageStart": 64,
  "pageEnd": 64,
  "groupContext": "optional",
  "canonicalText": "Bảng: ...\nDòng: 12.\nColumn A: value.\nTrang: 64."
}
```

Chunk type: `normalized_table_row`. Qdrant: `chunk_type`, `table_name`, `row_index`, `cells_json`, `group_context`.

## Table detection

- Tabula Spreadsheet/Basic (unchanged, 23B cross-page merge unchanged)
- Pseudo-table → markdown `[TABLE_START]` (unchanged) then normalize
- Raw overlap suppression via cell-text match on page text layer

## Header propagation

- `LogicalTableState` carries headers, `rowIndexCounter`, `tableName`, `groupContext` across segments/pages in same section
- Repeated header rows: `headersMatch` → skip as data
- Continuation without header: column-count match + inherited headers from state

## Raw table suppression

- Parser: suppress overlapping lines before append text; tables appended after
- Chunker: strip `\|...\|` lines from text segments; pseudo-table reject → suppress chars (no fallback)

## Chunk counts (benchmark)

| Metric | Old (23H2 audit) | New (expected after re-ingest) |
|--------|------------------|--------------------------------|
| Runtime re-ingest SoTay | NOT RUN this session | Re-ingest required |
| `table_row_group` | ~517 | **0** (new ingest) |
| `normalized_table_row` | 0 | >> row count |
| `text_table_like` | 0 | **0** |

## Runtime QA Q1–Q8

| Q | Expected | Result |
|---|----------|--------|
| Q1–Q3 TKB Nhóm 2/4 | Correct row | **NOT RUN** (no live re-ingest) |
| Q4 Đăng ký mạng | Date range | NOT RUN |
| Q5–Q6 HK2 ngành | HK2 courses | NOT RUN |
| Q7 KTR3185 | No regress | NOT RUN |
| Q8 USD OOS | No hallucination | NOT RUN |

## Unit tests

`NormalizedTableIngestTest` (9 cases): **PASS**  
`DocumentParserCrossPageMergeTest`: **PASS**  
`HybridKeywordSearchTest`: **PASS**

## Conclusion

**PARTIAL** — Code + unit tests PASS; production re-ingest + runtime Q1–Q8 pending operator run.
