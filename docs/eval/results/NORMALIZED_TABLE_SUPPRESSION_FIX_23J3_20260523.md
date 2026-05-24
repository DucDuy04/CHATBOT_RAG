# Normalized Table Suppression Fix — 23J3

**Date:** 2026-05-23  
**Task:** 23J3 — Fix Raw Table Text Suppression + Normalized Row Retrieval Priority  
**Verdict:** **PARTIAL**

---

## Root cause (23J2)

- `suppressTableOverlappingText` required `looksLikeTableDataLine()` which only matched `\s{2,}` / pipe / tab — SoTay timetable uses **single-space** rows → overlap detected but line not removed.
- `suppressedRawChars=0` despite 2475 normalized rows.
- 51+ `text` chunks >1500 chars with `STT Tên lớp học phần...` raw TKB.
- Retrieval ranked mega `text` chunks over `normalized_table_row` for table queries.

## Fix design

1. **SuppressionProfile** — cell + header tokens from normalized tables.
2. **Cell-overlap + header-driven + single-space table-like line** detection.
3. **Section pre-scan** — build profile from all tables before chunking text segments.
4. **Post-chunk leakage filter** — drop high-density table-like text overlapping cells.
5. **Parser `[TableSuppress]` logs** per page.
6. **Retrieval** — boost `normalized_table_row` (1.8×), penalize leaky text mega-chunks; demote in `RagRetrievalService`; skip leaky text in final selection when normalized rows present.

## Build / test

| Command | Result |
|---------|--------|
| Maven Docker `NormalizedTableSuppressionTest,NormalizedTableIngestTest` | **PASS** (18/18) |
| Maven Docker full targeted 9-class suite | **PASS** (exit 0) |
| Local `mvnw.cmd` | **FAIL** (JAVA_HOME) |
| `docker compose up --build -d backend` | **PASS** |

## Re-ingest

| Field | Value |
|-------|-------|
| CHATBOT_ID | `32b563cf-79e4-4c2b-8bfc-9a63c8b7e65f` |
| DOCUMENT_ID | `4ccb1842-c0ce-402a-9828-e7d9dbfbf082` |
| Status | INDEXED |
| chunkCount | **2735** |
| Qdrant points | **2735** |
| Ingest duration | ~167s upload+parse+embed |

### Ingest metrics (logs)

```
suppressedRawChars=35746 suppressedLines=697 tableLikeLinesDropped=613 droppedLeakyTextChunks=0
normalizedRows=2475 text=146 tableSummaries=106 failedTables=0
```

## Chunk distribution (logs)

| chunk_type | 23J2 | 23J3 |
|------------|-----:|-----:|
| normalized_table_row | 2475 | 2475 |
| table_summary | 106 | 106 |
| text | 149 | **146** |
| parent_section_summary | 2 | 4 |
| section_summary | 2 | 4 |
| table_row_group | 0 | 0 |
| text_table_like | 0 | 0 |
| **total** | 2732 | **2735** |

## Raw leakage audit

| Metric | 23J2 | 23J3 |
|--------|-----:|-----:|
| text chunks >1500 chars + TKB pattern | **51+** | **0** |
| text chunks with `STT T... Giảng viên` | many | **0** (SQL) |
| suppressedRawChars | 0 | **35746** |
| droppedLeakyTextChunks | 0 | 0 |

## Qdrant

- DB chunks = Qdrant points: **2735 = 2735 PASS**
- `cells_json` present on `normalized_table_row` samples — PASS

## Runtime Q1–Q8

| id | verdict | normalized_row in source? | notes |
|----|---------|---------------------------|-------|
| Q1 | **FAIL** | **Yes (13)** | Refusal; sources improved vs 23J2 (was text-only) but wrong rows (CTĐT not TKB Nhóm 2) |
| Q2 | **FAIL** | **Yes (12)** | Same |
| Q3 | **FAIL** | Partial (5) | Refusal |
| Q4 | **PARTIAL** | Yes (4) | Refusal; section_summary has dates but not exact K45-K48 window |
| Q5 | **PARTIAL** | Yes (8) | Lists HK2 courses for Kiến trúc K46 |
| Q6 | **FAIL** | Yes (13) | Refusal despite CNS K46 row in sources |
| Q7 | **FAIL** | Yes (7) | Regression — refusal; KTR3185 not in top normalized rows |
| Q8 | **PASS** | n/a | Safe OOS |

**Scored:** 1 PASS / 2 PARTIAL / 5 FAIL  
**Retrieval source type:** Table queries now **prefer normalized_table_row** (major vs 23J2).

## Conclusion

**PARTIAL**

- **PASS:** Suppression (35746 chars), zero raw TKB text chunks, normalized ingest intact, Qdrant match, Q8, retrieval sources use normalized rows.
- **FAIL/gap:** Q1/Q2/Q7 answers still fail — wrong normalized row retrieved for timetable; section_summary still contains table-like prose; LLM refuses despite improved sources.

## Artifacts

- `docs/eval/results/_23j3_verify_raw.json`
- `docs/eval/results/_run_23j3_verify.mjs`
