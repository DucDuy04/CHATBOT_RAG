# 23J4C-RERUN Runtime Verify - No-hardcode Cell-aware Retrieval

**Date:** 2026-05-24
**Mode:** VERIFY ONLY
**Task:** 23J4C-RERUN - Runtime Verify No-hardcode Cell-aware Retrieval After API Keys Set
**Conclusion:** FAIL

## Environment

- Workspace: `E:\chatbot-rag-workspace\CHATBOT_RAG`
- Shell: Windows PowerShell
- Backend: Docker Compose service `backend`
- Qdrant collection: `documents`
- API key env in verification shell/User env: available, values not printed

| key | status |
|---|---|
| `GROQ_API_KEY` | SET |
| `NOMIC_API_KEY` | SET |
| `COHERE_API_KEY` | SET |

`docker compose config -q`: PASS, no blank-key warning.

## Files Read

Reports:

- `docs/eval/results/NO_HARDCODE_CELL_AWARE_RETRIEVAL_23J4B_20260524.md`
- `docs/eval/results/FIX_LOOP_23J4B_NO_HARDCODE_CELL_AWARE.md`
- `reports/refactor/CURSOR_REPORT_23J4B_NO_HARDCODE_CELL_AWARE_RETRIEVAL.md`
- `docs/eval/results/NO_HARDCODE_CELL_AWARE_RUNTIME_VERIFY_23J4C_20260524.md`
- `reports/refactor/CURSOR_REPORT_23J4C_NO_HARDCODE_CELL_AWARE_RUNTIME_VERIFY.md`

Production source read:

- `CellAwareTableRowScorer.java`
- `QuerySignalExtractor.java`
- `KeywordSearchService.java`
- `RagRetrievalService.java`
- `NormalizedTableService.java`
- `ChunkingService2.java`
- `DocumentChunk.java`
- `EmbeddingService.java`
- `PromptBuilderService.java`
- `QueryAnalyzerService.java`

## Build / Health

Command:

```powershell
docker compose up --build -d backend
```

Result: PASS.

- Backend image rebuilt.
- `chatbot-backend` container recreated and started.
- `GET /api/chatbots?page=0&size=1`: PASS after startup completed.
- `GET /collections` on Qdrant: PASS, collection `documents` exists.

Startup note: first backend health request while Spring was still starting closed unexpectedly; retry after startup succeeded.

## Targeted Tests

Command:

```powershell
cd Backend
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
.\mvnw.cmd "-Dtest=HybridKeywordSearchTest,CellAwareNormalizedRowRetrievalTest,NoHardcodedLexiconInCellAwareScorerTest,NormalizedTableSuppressionTest,NormalizedTableIngestTest,FinalContextSelectionTest,RetrievalTopKTest,ChatServiceSourcePresentationTest,RagTokenAuditTest" test
```

Result: PASS.

- Tests run: 81
- Failures: 0
- Errors: 0
- `NoHardcodedLexiconInCellAwareScorerTest`: PASS, 8 tests

## No-hardcode Audit

Production files searched:

- `CellAwareTableRowScorer.java`
- `QuerySignalExtractor.java`
- `KeywordSearchService.java`
- `RagRetrievalService.java`

Patterns searched:

- `STRUCTURED_LABEL`
- `COLUMN_INTENT_SYNONYMS`
- `labelTypeAliases`
- `CANONICAL_META_PREFIXES`
- fixed compare word markers
- fixed structured prefix markers
- old table-like query markers

Result: PASS. No removed hardcoded lexicon structures found in production scorer/retrieval files.

Static source confirmation:

- `CellAwareTableRowScorer.parseCells()` reads `cells_json`.
- `parseCellsFromCanonical()` returns empty.
- `EmbeddingService` writes `table_name`, `row_index`, `cells_json`, and `group_context` into Qdrant payload for `normalized_table_row`.

## Re-ingest

New chatbot:

- `NEW_CHATBOT_ID`: `72d9bb71-8c3e-4b22-b460-53ce0779393f`
- Name: `23J4C-RERUN SoTayHocVu No-hardcode Runtime Verify`

Uploaded file:

- `docs/eval/manual/SoTayHocVu-HocKy1-NamHoc20252026.pdf`

New document:

- `NEW_DOCUMENT_ID`: `c0938e8b-d161-4a56-81b1-cd02b7499bc7`
- Status: `INDEXED`
- Progress: 100
- Upload start: `2026-05-24T12:56:34.0387443+07:00`
- Upload/API return: `2026-05-24T12:58:42.7427359+07:00`
- Ingest duration: 128.70s
- `chunkCount`: 2734
- Model/provider in runtime logs: `meta-llama/llama-4-scout-17b-16e-instruct`, provider `GROQ`

Backend logs:

- Parse: sections=59, chunks=2734, detectedTables=106, normalizedTables=106, failedTables=0, normalizedRows=2474
- Embed start: `2026-05-24T05:58:15.361Z`
- Embed finish: `2026-05-24T05:58:42.497Z`, saved 2734 vectors

## Chunk Type Distribution

| chunk_type | count |
|---|---:|
| normalized_table_row | 2474 |
| text | 146 |
| table_summary | 106 |
| section_summary | 4 |
| parent_section_summary | 4 |
| table_row_group | 0 |
| text_table_like | 0 |

Expected structural checks:

- `normalized_table_row > 0`: PASS
- `table_summary > 0`: PASS
- `table_row_group = 0`: PASS
- `text_table_like = 0`: PASS

## Cells JSON / DB / Qdrant Audit

DB sample normalized rows:

- `cells_json`: persisted and non-empty.
- `table_name`: populated.
- `row_index`: populated.
- `page_start/page_end`: normalized row samples are narrow, commonly same-page.

Qdrant:

- DB chunks for new document: 2734
- Qdrant points filtered by `document_id`: 2734
- DB = Qdrant count: PASS

Qdrant normalized-row payload sample contains:

- `chunk_type`
- `table_name`
- `row_index`
- `cells_json`
- `document_id`
- `page_start`
- `page_end`
- `table_id`
- `section_id`
- `widgetId`

Note: sampled payload did not always include `group_context`; this is expected when DB `group_context` is null for that row. Rows with group context persist it in DB.

## Raw Table Text Leakage Audit

SQL audit for `chunk_type='text' AND CHAR_LENGTH(content)>1500`:

- Long text chunks: 1
- Long text chunks containing pipe/table marker: 0
- Sample was prose about thi kết thúc học phần, not raw timetable/table mega text.

Conclusion: raw table mega-text leakage did not return. PASS.

## Runtime Config

Playground API:

- Compare Mode: not used
- `temperature`: 0.2
- `maxTokens`: 768
- `topK`: 15
- `playgroundDebugSources`: enabled by controller
- Delays: 25s between Q2-Q8 requests; Q1 was run separately.

Captured logs:

- `[RAG][hybrid]`
- `[RAG][cell-aware]`
- `[RAG][cell-aware-top]`
- `[RAG][budget]`
- `[RAG][token-usage]`

## Runtime Q1-Q8

| id | question | topN | answer summary | source chunk types | top normalized rows | cells_json evidence | verdict | notes |
|---|---|---:|---|---|---|---|---|---|
| Q1 | Kỹ năng mềm Nhóm 2 học với giảng viên nào, thứ mấy, tiết nào, phòng nào? | 15 | Refused: không tìm thấy | `parent_section_summary`, `normalized_table_row` | Top rows were KNM1013 rows but not actual Nhóm 2 timetable row; top examples rowIndex 42/40/47/326 | `{"TT":"KNM1013 Kỹ năng mềm","Mã":""}` | FAIL | Wrong row; missing giảng viên/thứ/tiết/phòng |
| Q2 | Kỹ năng mềm Nhóm 4 học với giảng viên nào, thứ mấy, tiết nào, phòng nào? | 15 | Refused: không tìm thấy | `parent_section_summary`, `text`, `normalized_table_row` | Same KNM1013 generic rows; no Nhóm 4 row | `{"TT":"KNM1013 Kỹ năng mềm","Mã":""}` | FAIL | Wrong row |
| Q3 | So sánh Kỹ năng mềm Nhóm 1 và Nhóm 2 về giảng viên và phòng học. | 15 | Refused | `section_summary`, `parent_section_summary`, `text`, `normalized_table_row` | Generic KNM1013 rows, not Nhóm 1/2 timetable rows | `{"TT":"KNM1013 Kỹ năng mềm","Mã":""}` | FAIL | Missing both target rows |
| Q4 | K45-K48 đăng ký học phần qua mạng từ ngày nào đến ngày nào? | 15 | Refused | `parent_section_summary`, `normalized_table_row`, `text` | Correct schedule row surfaced at rank 1 | `{"STT":"2","NỘI DUNG CÔNG VIỆC":"Sinh viên đăng ký học phần qua mạng Internet","KHÓA":"","THỜI GIAN":""}` | PARTIAL | Source row relevant, but cells lack KHÓA/THỜI GIAN and answer refused |
| Q5 | Ngành Kiến trúc K46 có những học phần nào ở học kỳ 2? | 15 | Says not found; lists unrelated Kiến trúc K49 examples | `parent_section_summary`, `section_summary`, `normalized_table_row`, `text` | Top rows not Kiến trúc K46 HK2 | Mixed unrelated K46/K47 rows | FAIL | Does not meet expected HK2 retrieval |
| Q6 | Ngành Công nghệ sinh học K46 ở học kỳ 2 có các học phần gì? | 15 | Returns one item: MTR1022 | `normalized_table_row` | Correct high-level K46 row at rank 1, but only one row | `{"TT":"","Mã":"Mã T học phần hóa, ngành: Công nghệ sinh học K46"}` | PARTIAL | Has normalized row evidence but not complete HK2 list |
| Q7 | Trong ngành Kiến trúc K45, học phần KTR3185 tên là gì và có mấy tín chỉ? | 15 | Refused; says KTR3185 not found | `parent_section_summary`, `normalized_table_row` | KTR3185 not in top rows | DB has KTR3185 rows only as `{"STT":"463","Mã":"KTR3185 5"}` etc.; expected title not persisted/found | FAIL | Regression remains |
| Q8 | Tỷ giá USD/VND hôm nay là bao nhiêu? | 15 | Correct refusal | `normalized_table_row` | Irrelevant rows | N/A | PASS | OOS refusal works |

Runtime tally:

- PASS: 1/8
- PARTIAL: 2/8
- FAIL: 5/8

## Debug Evidence Q1/Q2/Q7

Q1 logs:

- Signals: `labels=[nhom 2, mem nhom 2, nang mem nhom 2]`
- Top normalized rows:
  - rank 1 rowIndex=42, table=`10 MTR1022...`, cells=`{"TT":"KNM1013 Kỹ năng mềm","Mã":""}`
  - rank 2 rowIndex=40, table=`10 MTR1022...`, cells=`{"TT":"KNM1013 Kỹ năng mềm","Mã":""}`
  - rank 3 rowIndex=47, table=`43 LIS4622...`, cells=`{"TT":"KNM1013 Kỹ năng mềm","Mã":""}`
- Verdict: FAIL, retrieval matched course text but not Nhóm 2 timetable row/cells.

Q2 logs:

- Signals: `labels=[nhom 4, mem nhom 4, nang mem nhom 4]`
- Top normalized rows:
  - rank 1 rowIndex=42, cells=`{"TT":"KNM1013 Kỹ năng mềm","Mã":""}`
  - rank 2 rowIndex=40, cells=`{"TT":"KNM1013 Kỹ năng mềm","Mã":""}`
  - rank 3 rowIndex=47, cells=`{"TT":"KNM1013 Kỹ năng mềm","Mã":""}`
- Verdict: FAIL, same generic KNM1013 rows, not Nhóm 4.

Q7 logs:

- Signals: `identifiers=[KTR3185]`, labels include `hoc phan ktr3185`, `nganh kien truc k45`
- Top normalized rows did not include KTR3185.
- DB query found KTR3185 rows, but only sparse cells:
  - `{"STT":"461","Mã":"KTR3185"}`
  - `{"STT":"463","Mã":"KTR3185 5"}`
- DB search did not find expected title `Đồ án kiến trúc công trình tổ hợp đa chức năng`.
- Verdict: FAIL, KTR3185 regression remains.

## Code Change Status

No Java, Frontend, Backend logic, parser/chunking, retrieval, PromptBuilder, QueryAnalyzer, source cap, dependency, migration/backfill, or `.gitignore` changes were made.

Only verification report files were updated/created.

## Final Conclusion

**FAIL**

Build, tests, no-hardcode audit, re-ingest, DB/Qdrant structural checks, `cells_json` persistence, forbidden chunk type checks, and raw table leakage audit pass.

Runtime fails acceptance because:

1. Q1/Q2 still do not retrieve the correct Nhóm 2/Nhóm 4 rows.
2. Q7 KTR3185 still regresses.
3. Q1-Q8 has only 1 PASS, below the required 5/8 PASS threshold.
4. Key target rows appear sparse or malformed in persisted `cells_json`, so runtime cell-aware scoring is using `cells_json`, but the available cells do not carry enough correct row structure for these queries.
