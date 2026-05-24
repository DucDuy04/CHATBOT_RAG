# Cursor Report 23J5: Generic Table Normalization Fidelity Fix

Date: 2026-05-24

## Final Verdict

PASS.

The generic table normalizer now preserves complete logical rows well enough for fresh ingest and runtime verification. Q1-Q8 was run after rebuild and re-ingest; result was 8/8 usable answers.

## Scope

This task continued from 23J4C-RERUN, where keys, Qdrant payloads, and no-hardcode retrieval passed but runtime failed because normalized table rows were sparse. The fix stayed in parser/normalizer/chunking/retrieval plumbing and did not re-enable raw table text, `table_row_group`, or `text_table_like`.

## Files Changed

- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/NormalizedTableService.java`
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/DocumentParserService.java`
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/TableIngestMetrics.java`
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/ChunkingService2.java`
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/DocumentService.java`
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/RagRetrievalService.java`
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/ChatService.java`
- `Backend/src/test/java/KLTN/RAG_CHATBOT_BE/service/NormalizedTableIngestTest.java`
- `Backend/src/test/java/KLTN/RAG_CHATBOT_BE/service/NoHardcodedLexiconInTableNormalizerTest.java`
- `docs/eval/results/_run_23j5_verify_utf8.mjs`
- `docs/eval/results/GENERIC_TABLE_NORMALIZATION_FIDELITY_FIX_23J5_20260524.md`
- `reports/refactor/CURSOR_REPORT_23J5_GENERIC_TABLE_NORMALIZATION_FIDELITY_FIX.md`

## Implementation Notes

- Header inference now merges adjacent header-like rows and composes stacked labels generically.
- If header confidence is low, values are preserved under generic keys such as `col_N`.
- Physical-row continuation repair uses generic sparsity/adjacency/structure signals.
- Sparse structured tables are no longer rejected solely because many cells are empty.
- Table ingest logging exposes quality metrics without domain-specific labels.
- Table-like retrieval candidate pools are bounded before expensive scoring.
- SSE timeout was raised from 180s to 600s to avoid losing long but successful playground requests.

## No-Hardcode Status

PASS. Production logic contains no task-specific hardcoded document/course/page/date/domain lexicon. The new audit test scans production parser, normalizer, and retrieval files for forbidden diagnostic/domain literals and old chunk structures.

Diagnostic terms were used only in verification/reporting.

## Tests

```text
Focused table tests:
NormalizedTableIngestTest, NormalizedTableSuppressionTest, NoHardcodedLexiconInTableNormalizerTest
24 tests, 0 failures

Targeted suite:
HybridKeywordSearchTest
CellAwareNormalizedRowRetrievalTest
NoHardcodedLexiconInCellAwareScorerTest
NoHardcodedLexiconInTableNormalizerTest
NormalizedTableSuppressionTest
NormalizedTableIngestTest
FinalContextSelectionTest
RetrievalTopKTest
ChatServiceSourcePresentationTest
RagTokenAuditTest
87 tests, 0 failures
```

## Runtime Verification

Fresh ingest:

```text
chatbotId: fef43298-e1ae-4b1d-af48-b2b1d83ded95
documentId: 6b9d2693-667a-46cf-b9ce-970ff8988833
API document status: INDEXED
DB document status: COMPLETED
DB chunks: 2745
Qdrant points: 2745
normalized_table_row: 2490
table_summary: 120
table_row_group: 0
text_table_like: 0
cells_json rows: 2490
raw table-like long text chunks: 0
```

Qdrant payload sample contained `cells_json` for `normalized_table_row`.

## Diagnostic Evidence

Before:

```json
{"TT":"KNM1013 Kỹ năng mềm","Mã":""}
{"STT":"461","Mã":"KTR3185"}
{"STT":"2","NỘI DUNG CÔNG VIỆC":"Sinh viên đăng ký học phần qua mạng Internet","KHÓA":"","THỜI GIAN":""}
```

After:

```json
{"STT STT":"9","Mã học phần Mã Tên lớp học phần học phần":"KNM1013","Tên lớp học phần Số Giảng viên TC":"Kỹ năng mềm - Nhóm 2","Số TC Số Thứ":"3","Giảng viên Phòng":"Hoàng Ngô Tự Do","Thứ":"2","Tiết học":"1-3","Phòng":"H307"}
```

```json
{"STT STT":"461","Mã học phần Mã Tên lớp học phần học phần":"KTR3185","Tên lớp học phần Số Giảng viên TC":"Đồ án kiến trúc công trình tổ hợp đa chức năng - Nhóm 1","Số TC Số Thứ":"5","Giảng viên Phòng":"Nguyễn Văn Thái","Thứ":"2","Tiết học":"5-8","Phòng":"E2.01_Xưởng kiến trúc 1-1"}
```

```json
{"STT":"3","NỘI DUNG CÔNG VIỆC":"Phòng ĐTĐH&CTSV xét duyệt đăng ký học phần của sinh viên đồng thời gia hạn thời gian để sinh viên tiếp tục đăng ký bổ sung trên mạng trong thời gian xét duyệt","KHÓA":"K45,K46, K47, K48","THỜI GIAN":"07/07/2025  13/07/2025"}
```

The exact registration event is preserved in carried context:

```text
Context: 2 Sinh viên đăng ký học phần qua mạng Internet ... K45,K46, K47, K48 10h00 30/06/2025  06/07/2025
```

Raw row text before normalization is not persisted in the current schema, so diagnostics used `content`, `cells_json`, table metadata, and neighboring normalized rows.

## Q1-Q8 Result

| Question | Verdict | Summary |
| --- | --- | --- |
| Q1 | PASS | Correct Nhóm 2 lecturer/day/period/room. |
| Q2 | PASS | Correct Nhóm 4 lecturer/day/period/room. |
| Q3 | PASS | Correct comparison for Nhóm 1 vs Nhóm 2. |
| Q4 | PASS | Correct registration range: 30/06/2025 to 06/07/2025. |
| Q5 | PASS | Returns Kiến trúc K46 học kỳ 2 course list from normalized rows. |
| Q6 | PASS | Returns Công nghệ sinh học K46 học kỳ 2 course list from normalized rows. |
| Q7 | PASS | Correct KTR3185 title and 5 credits. |
| Q8 | PASS | Refuses out-of-scope live exchange rate. |

## Limitations

- Wide PDF tables still produce noisy composed header names in some areas.
- Context carry is doing useful work for section/program-table continuity; a future pass could materialize some carried context into explicit repaired row values where structurally safe.
- Some source rows for Q5/Q6 contain mixed header text, but values are preserved and retrieval/answering now work.
