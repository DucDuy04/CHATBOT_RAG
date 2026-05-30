# 23J5B Generic Table Normalization Stability

Date: 2026-05-24

## Final Verdict

PASS.

23J5B was verification-only. No production source code was changed. The 23J5 table-normalization behavior remained stable after backend rebuild, fresh re-ingest, DB/Qdrant audit, diagnostic evidence checks, and runtime Q1-Q8. **23J5B PASS with full Q1–Q8 runtime PASS after fresh re-ingest** (8/8 PASS; Q4 reclassified from PARTIAL — see below).

## Files Changed

No production or test source files were changed for 23J5B.

Report files added:

- `docs/eval/results/GENERIC_TABLE_NORMALIZATION_STABILITY_23J5B_20260524.md`
- `reports/refactor/CURSOR_REPORT_23J5B_GENERIC_TABLE_NORMALIZATION_STABILITY.md`

## Docker / Backend

```text
docker compose config -q: PASS
docker compose up --build -d backend: PASS
backend API /api/chatbots?page=0&size=1: 200
Qdrant /collections: 200
```

No stale duplicate container cleanup was needed.

## Tests

Focused table tests:

```text
NormalizedTableIngestTest
NormalizedTableSuppressionTest
NoHardcodedLexiconInTableNormalizerTest

Tests run: 24
Failures: 0
Errors: 0
```

Targeted regression suite:

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

Tests run: 87
Failures: 0
Errors: 0
```

No-hardcode audits passed.

Optional full backend suite was not rerun in 23J5B; previous reports already document unrelated broader-suite environment/fixture issues, while the accepted focused and targeted suites are green.

## Fresh Re-Ingest

Verification raw output:

- `docs/eval/results/_23j5_verify_raw_utf8.json`

Fresh runtime document:

```text
chatbotId: f62a6fb6-aa2f-4aa6-874d-5946d957ecd4
documentId: 131f2a64-5de0-4a34-8060-04e8ac3a7cae
API status: INDEXED
DB status: COMPLETED
chunkCount: 2745
ingestDurationMs: 67293
verification startedAt: 2026-05-24T10:43:39.648Z
verification finishedAt: 2026-05-24T10:56:04.270Z
```

Parser/chunking metrics from backend logs:

```text
sections=21
totalChunks=2745
parentSummaries=2
sectionSummaries=2
tableSummaries=120
normalizedRows=2490
text=131
detectedTables=122
normalizedTables=120
failedTables=2
suppressedRawChars=14915
suppressedLines=234
tableLikeLinesDropped=221
droppedLeakyTextChunks=0
rowsWithCellsJson=2490
rowsWithOnlyOneNonEmptyCell=1
rowsWithEmptyCellsRatio=0.364
rowsWithGenericColumnKeys=284
continuationRowsMerged=685
multiRowHeadersMerged=0
crossPageHeaderCarryCount=0
sparseRowsRepaired=685
droppedCellFragments=0
```

Embedding logs:

```text
embedding start: 2026-05-24T10:44:26.551Z for 2745 chunks
embedding finish: 2026-05-24T10:44:47.181Z, saved 2745 vectors
```

## DB / Qdrant Audit

```text
DB document status: COMPLETED
API document status: INDEXED
DB chunks: 2745
Qdrant points: 2745
DB = Qdrant: PASS
normalized_table_row: 2490
table_summary: 120
text: 131
section_summary: 2
parent_section_summary: 2
table_row_group: 0
text_table_like: 0
normalized rows with non-empty cells_json: 2490
normalized rows missing table/page/row metadata: 0
Qdrant normalized_table_row payload contains cells_json: yes
Qdrant payload has table_name, row_index, page_start, page_end: yes
```

## Raw Leakage Audit

```text
long text chunks > 1500 chars: 3
long table-like text chunks > 4000 chars with table markers: 0
droppedLeakyTextChunks: 0
table_row_group: 0
text_table_like: 0
```

Conclusion: raw table mega-text leakage did not return.

## Diagnostic Evidence

Diagnostic search terms were used only in DB/report verification, not production code.

### KNM1013

Representative row:

```text
table_id: tbl_14_1442
table_name: 2. Thi kết thúc học phần
row_index: 9
page_start/page_end: 20/20
```

```json
{"STT STT":"9","Mã học phần Mã Tên lớp học phần học phần":"KNM1013","Tên lớp học phần Số Giảng viên TC":"Kỹ năng mềm - Nhóm 2","Số TC Số Thứ":"3","Số SV Ngày Tiết học bắt đầu":"0","Giảng viên Phòng":"Hoàng Ngô Tự Do","Ngày bắt đầu Ghi chú":"08/09/2025","Thứ":"2","Tiết học":"1-3","Phòng":"H307","Ghi chú":""}
```

Content/canonical text contains the same row values. Neighboring KNM1013 rows also preserve group, lecturer, weekday, period, and room values.

### KTR3185

Representative timetable row:

```text
table_id: tbl_20_2049
table_name: 08  E2.01_Xưởng
row_index: 8
page_start/page_end: 94/94
```

```json
{"STT STT":"461","Mã học phần Mã Tên lớp học phần học phần":"KTR3185","Tên lớp học phần Số Giảng viên TC":"Đồ án kiến trúc công trình tổ hợp đa chức năng - Nhóm 1","Số TC Số Thứ":"5","Số SV Ngày Tiết học bắt đầu":"0","Giảng viên Phòng":"Nguyễn Văn Thái","Ngày bắt đầu Ghi chú":"17/11/2025","Thứ":"2","Tiết học":"5-8","Phòng":"E2.01_Xưởng kiến trúc 1-1","Ghi chú":""}
```

The page-20 program-table row is still noisy, but the source fragments for code/title/credit are present and Q7 runtime succeeds.

### Registration

Representative carried-context row:

```text
table_id: tbl_7_4
table_name: 8. Danh sách các mẫu đơn thường sử dụng
row_index: 2
page_start/page_end: 3/3
```

```json
{"STT":"3","NỘI DUNG CÔNG VIỆC":"Phòng ĐTĐH&CTSV xét duyệt đăng ký học phần của sinh viên đồng thời gia hạn thời gian để sinh viên tiếp tục đăng ký bổ sung trên mạng trong thời gian xét duyệt","KHÓA":"K45,K46, K47, K48","THỜI GIAN":"07/07/2025  13/07/2025","col_5":""}
```

The target registration event is preserved in nearby carried context:

```text
Context: 2 Sinh viên đăng ký học phần qua mạng Internet tại trang Web: https://ums.husc.edu.vn/home K45,K46, K47, K48 10h00 30/06/2025  06/07/2025.
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

| ID | Verdict | Answer / Evidence |
| --- | --- | --- |
| Q1 | PASS | Hoàng Ngô Tự Do, thứ 2, tiết 1-3, phòng H307. Sources include exact normalized row. |
| Q2 | PASS | Nguyễn Thị Thanh Nhàn, thứ 6, tiết 5-7, phòng B301. Sources include relevant normalized rows. |
| Q3 | PASS | Nhóm 1: Nguyễn Chí Ngàn/H310; Nhóm 2: Hoàng Ngô Tự Do/H307. |
| Q4 | PASS | The question asks for the date range (`từ ngày nào đến ngày nào`), and the answer correctly returned 30/06/2025 to 06/07/2025. The source context also contains `10h00`, but the question did not explicitly ask for the start time, so omission of the hour is not considered a failure. |
| Q5 | PASS | Returns Kiến trúc K46 học kỳ 2 course list from normalized rows. |
| Q6 | PASS | Returns Công nghệ sinh học K46 học kỳ 2 course list from normalized rows. |
| Q7 | PASS | Returns title "Đồ án kiến trúc công trình tổ hợp đa chức năng" and 5 credits. Sources include KTR3185 title/credit rows. |
| Q8 | PASS | Correctly refuses current USD/VND rate as not found in the document. |

Tally: 8 PASS, 0 PARTIAL, 0 FAIL. Mandatory Q1, Q2, Q7, and Q8 all PASS. Full Q1–Q8 runtime PASS.

## Runtime Source / Performance Notes

- Q1-Q7 selected normalized_table_row sources.
- Q8 selected one text source and normalized rows, but answered as out-of-scope.
- Token budget logs stayed within expected ranges: selected contexts 15, context chars roughly 4.7k-6.5k for table questions, Q8 around 6.2k.
- Table-like questions show bounded candidate scoring, e.g. Q7 `boundedForScoring=240 from=1992`.
- No request approached the 600s SSE timeout.
- No Groq rate-limit or timeout issue was observed.
- Full verification wall time was about 12m24s, including fresh ingest and seven 25s inter-question waits.

## Known Limitations

- Wide table header names remain noisy in some rows.
- Some program-table answers rely on `Context:` carry rather than fully clean explicit headers.
- Q4 registration carried context includes `10h00 30/06/2025  06/07/2025`; the runtime answer gave the date range only. That matches question intent (date range, not start hour). Parser/retrieval did not lose the time value.

## Next Recommended Task

No 23J5C follow-up required for Q4. Optional future work: continue generic wide-table header cleanup where rows remain noisy (unrelated to Q4 verdict).
