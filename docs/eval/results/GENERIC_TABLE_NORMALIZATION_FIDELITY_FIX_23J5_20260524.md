# 23J5 Generic Table Normalization Fidelity Fix

Date: 2026-05-24

## Verdict

PASS.

Runtime Q1-Q8 was executed after a fresh backend rebuild and fresh re-ingest. Result: 8/8 usable answers, with Q1/Q2/Q7 no longer failing due to sparse normalized rows. Q8 correctly refused out-of-scope current exchange-rate data.

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

## Implementation Summary

- Added generic multi-row header inference and stacked header composition.
- Preserved values under generated `col_N` keys when headers are uncertain.
- Added continuation/sparse-row merge tracking so adjacent fragments can repair logical rows.
- Relaxed table usability rejection for sparse-but-structured tables using structural signals, not domain terms.
- Added table normalization quality metrics: rows with cells, one-cell rows, empty-cell ratio, generic column keys, continuation merges, multi-row headers, sparse repairs, and dropped fragments.
- Added a generic candidate bound for table-like retrieval pools to keep runtime verification from timing out.
- Increased SSE timeout to allow long RAG calls to finish.

## No-Hardcode Proof

Production logic was kept generic and data-driven. The new no-hardcode test scans production normalizer/parser/retrieval files for the forbidden diagnostic/domain literals and old structures. Diagnostic terms appear only in verification/reporting artifacts, not production parsing logic.

Test passed:

```text
NoHardcodedLexiconInTableNormalizerTest PASS
```

## Tests Run

Focused:

```text
NormalizedTableIngestTest, NormalizedTableSuppressionTest, NoHardcodedLexiconInTableNormalizerTest
24 tests, 0 failures
```

Targeted suite:

```text
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

Backend build:

```text
docker compose up --build -d backend
PASS after stale container was removed
```

## Fresh Re-Ingest

Verification file:

- `docs/eval/results/_23j5_verify_raw_utf8.json`

Fresh runtime document:

```text
chatbotId: fef43298-e1ae-4b1d-af48-b2b1d83ded95
documentId: 6b9d2693-667a-46cf-b9ce-970ff8988833
API status: INDEXED
DB status: COMPLETED
chunkCount: 2745
ingestDurationMs: 50830
```

## DB/Qdrant Audit

```text
DB chunks: 2745
Qdrant points: 2745
normalized_table_row: 2490
table_summary: 120
text: 131
section_summary: 2
parent_section_summary: 2
table_row_group: 0
text_table_like: 0
normalized rows with cells_json: 2490
Qdrant normalized_table_row payload sample includes cells_json: yes
long table-like text chunks: 0
```

Raw table mega-text did not return. No `table_row_group` or `text_table_like` chunks were generated.

## Before/After cells_json Evidence

Before 23J5, representative sparse rows from 23J4C-RERUN:

```json
{"TT":"KNM1013 Kỹ năng mềm","Mã":""}
{"STT":"461","Mã":"KTR3185"}
{"STT":"2","NỘI DUNG CÔNG VIỆC":"Sinh viên đăng ký học phần qua mạng Internet","KHÓA":"","THỜI GIAN":""}
```

After 23J5, fresh ingest preserves richer logical rows:

```json
{
  "STT STT": "9",
  "Mã học phần Mã Tên lớp học phần học phần": "KNM1013",
  "Tên lớp học phần Số Giảng viên TC": "Kỹ năng mềm - Nhóm 2",
  "Số TC Số Thứ": "3",
  "Giảng viên Phòng": "Hoàng Ngô Tự Do",
  "Ngày bắt đầu Ghi chú": "08/09/2025",
  "Thứ": "2",
  "Tiết học": "1-3",
  "Phòng": "H307"
}
```

```json
{
  "STT STT": "461",
  "Mã học phần Mã Tên lớp học phần học phần": "KTR3185",
  "Tên lớp học phần Số Giảng viên TC": "Đồ án kiến trúc công trình tổ hợp đa chức năng - Nhóm 1",
  "Số TC Số Thứ": "5",
  "Giảng viên Phòng": "Nguyễn Văn Thái",
  "Thứ": "2",
  "Tiết học": "5-8",
  "Phòng": "E2.01_Xưởng kiến trúc 1-1"
}
```

```json
{
  "STT": "3",
  "NỘI DUNG CÔNG VIỆC": "Phòng ĐTĐH&CTSV xét duyệt đăng ký học phần của sinh viên đồng thời gia hạn thời gian để sinh viên tiếp tục đăng ký bổ sung trên mạng trong thời gian xét duyệt",
  "KHÓA": "K45,K46, K47, K48",
  "THỜI GIAN": "07/07/2025  13/07/2025"
}
```

The registration target row itself is now preserved as neighboring logical context:

```text
Context: 2 Sinh viên đăng ký học phần qua mạng Internet ... K45,K46, K47, K48 10h00 30/06/2025  06/07/2025
```

## Runtime Q1-Q8

Config:

```text
temperature: 0.2
maxTokens: 768
Context Top-N: 15
Hybrid Search: ON
playgroundDebugSources: true
mode: Playground normal mode
delay: 25s between questions
```

| ID | Result | Evidence |
| --- | --- | --- |
| Q1 | PASS | Answer: Hoàng Ngô Tự Do, thứ 2, tiết 1-3, phòng H307. Sources include the exact normalized row. |
| Q2 | PASS | Answer: Nguyễn Thị Thanh Nhàn, thứ 6, tiết 5-7, phòng B301. Sources include the exact normalized row. |
| Q3 | PASS | Correctly compares Nhóm 1 and Nhóm 2: Nguyễn Chí Ngàn/H310 vs Hoàng Ngô Tự Do/H307. |
| Q4 | PASS | Answer: 30/06/2025 to 06/07/2025, recovered from normalized row context. |
| Q5 | PASS | Returns Kiến trúc K46 học kỳ 2 course list from normalized program-table rows. |
| Q6 | PASS | Returns Công nghệ sinh học K46 học kỳ 2 course list from normalized program-table rows. |
| Q7 | PASS | Answer: "Đồ án kiến trúc công trình tổ hợp đa chức năng", 5 tín chỉ. Sources include KTR3185 row with title and credit. |
| Q8 | PASS | Answer: "Tôi không tìm thấy thông tin này trong tài liệu." No hallucinated current exchange rate. |

## Known Limitations

- Some inferred header names remain noisy for wide PDF tables because the source extraction interleaves stacked header fragments.
- Some program-table rows rely on `Context:` to preserve group/table continuation metadata.
- KTR3185 has one page-20 program row with imperfect text order, but the timetable rows now preserve the full title and credit, and runtime Q7 succeeds.
- The registration row answer is recovered from context carried onto adjacent logical rows; future work could materialize a synthetic repaired row for that exact physical line without reintroducing raw table chunks.
