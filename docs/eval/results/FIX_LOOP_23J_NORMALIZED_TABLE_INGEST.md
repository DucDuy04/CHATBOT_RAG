# FIX_LOOP_23J_NORMALIZED_TABLE_INGEST

## Loop

1. Implement `NormalizedTableService` + `ChunkingService2` integration  
2. Suppress raw table text in parser + chunker  
3. Qdrant payload fields via `@Transient` on entity  
4. Retrieval boost `normalized_table_row`  
5. Unit tests 1–9  

## Fixes applied

| Issue | Fix |
|-------|-----|
| Duplicate raw + table | Parser suppress before text append; chunker strip `\|` lines |
| `table_row_group` noise | Replaced by `normalized_table_row` per data row |
| `text_table_like` | Removed from new ingest path |
| Cross-page no header | `LogicalTableState` + continuation detect |
| Wrapped rows | `mergeWrappedRows` with row-id guard |
| Test merge A1/A2 | `looksLikeWrappedFragment` excludes short codes |

## Remaining

- Re-ingest `SoTayHocVu-HocKy1-NamHoc20252026.pdf` and run Q1–Q8  
- Monitor `failedTables` on large PDFs  

## Status

**PARTIAL** (code complete, runtime verify pending)
