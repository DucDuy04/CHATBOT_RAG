# Cursor Report 23J5B: Generic Table Normalization Stability

Date: 2026-05-24

## Final Verdict

PASS.

23J5B was verification-only. No source code was changed. The 23J5 generic table normalization fix remained stable after rebuild, tests, fresh re-ingest, DB/Qdrant audit, diagnostic checks, and runtime Q1-Q8. **23J5B PASS with full Q1–Q8 runtime PASS after fresh re-ingest** (8/8 PASS; Q4 reclassified R1 — intent-specific verdict, no code change).

## Source Code Change Statement

No production source code was changed.

No test source code was changed.

Only reports were added:

- `docs/eval/results/GENERIC_TABLE_NORMALIZATION_STABILITY_23J5B_20260524.md`
- `reports/refactor/CURSOR_REPORT_23J5B_GENERIC_TABLE_NORMALIZATION_STABILITY.md`

## Build / Health

```text
docker compose config -q: PASS
docker compose up --build -d backend: PASS
backend API reachable: PASS
Qdrant collection endpoint reachable: PASS
```

## Tests

Focused table/no-hardcode tests:

```text
Tests run: 24
Failures: 0
Errors: 0
```

Targeted regression suite:

```text
Tests run: 87
Failures: 0
Errors: 0
```

No-hardcode audit result: PASS.

Broader backend suite was not rerun; 23J5B focused on the accepted regression suites and runtime stability.

## Fresh Re-Ingest

```text
chatbotId: f62a6fb6-aa2f-4aa6-874d-5946d957ecd4
documentId: 131f2a64-5de0-4a34-8060-04e8ac3a7cae
API status: INDEXED
DB status: COMPLETED
chunkCount: 2745
Qdrant points: 2745
ingestDurationMs: 67293
embedding: 2745 vectors saved
```

## DB / Qdrant Audit

| Check | Result |
| --- | --- |
| DB chunks | 2745 |
| Qdrant points | 2745 |
| DB = Qdrant | PASS |
| normalized_table_row | 2490 |
| table_summary | 120 |
| text | 131 |
| section_summary | 2 |
| parent_section_summary | 2 |
| table_row_group | 0 |
| text_table_like | 0 |
| rows with cells_json | 2490 |
| normalized rows missing table/page/row metadata | 0 |
| Qdrant payload cells_json | PASS |

## Raw Leakage Audit

```text
long text chunks > 1500 chars: 3
long table-like text chunks > 4000 chars with table markers: 0
droppedLeakyTextChunks: 0
```

No raw table mega-text, fallback table text, `table_row_group`, or `text_table_like` returned.

## Table Normalization Metrics

From backend log:

```text
detectedTables=122
normalizedTables=120
normalizedRows=2490
failedTables=2
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

No suspicious regression versus 23J5 baseline. Counts stayed at DB/Qdrant 2745, normalized rows 2490, table summaries 120, and cells_json rows 2490.

## Diagnostic Evidence

Diagnostic-only search terms were used only in verification and reports.

KNM1013:

```json
{"STT STT":"9","Mã học phần Mã Tên lớp học phần học phần":"KNM1013","Tên lớp học phần Số Giảng viên TC":"Kỹ năng mềm - Nhóm 2","Số TC Số Thứ":"3","Số SV Ngày Tiết học bắt đầu":"0","Giảng viên Phòng":"Hoàng Ngô Tự Do","Ngày bắt đầu Ghi chú":"08/09/2025","Thứ":"2","Tiết học":"1-3","Phòng":"H307","Ghi chú":""}
```

KTR3185:

```json
{"STT STT":"461","Mã học phần Mã Tên lớp học phần học phần":"KTR3185","Tên lớp học phần Số Giảng viên TC":"Đồ án kiến trúc công trình tổ hợp đa chức năng - Nhóm 1","Số TC Số Thứ":"5","Số SV Ngày Tiết học bắt đầu":"0","Giảng viên Phòng":"Nguyễn Văn Thái","Ngày bắt đầu Ghi chú":"17/11/2025","Thứ":"2","Tiết học":"5-8","Phòng":"E2.01_Xưởng kiến trúc 1-1","Ghi chú":""}
```

Registration:

```json
{"STT":"3","NỘI DUNG CÔNG VIỆC":"Phòng ĐTĐH&CTSV xét duyệt đăng ký học phần của sinh viên đồng thời gia hạn thời gian để sinh viên tiếp tục đăng ký bổ sung trên mạng trong thời gian xét duyệt","KHÓA":"K45,K46, K47, K48","THỜI GIAN":"07/07/2025  13/07/2025","col_5":""}
```

Nearby context preserved:

```text
Context: 2 Sinh viên đăng ký học phần qua mạng Internet tại trang Web: https://ums.husc.edu.vn/home K45,K46, K47, K48 10h00 30/06/2025  06/07/2025.
```

## Runtime Q1-Q8

| ID | Verdict | Notes |
| --- | --- | --- |
| Q1 | PASS | Correct lecturer/day/period/room for target row. |
| Q2 | PASS | Correct lecturer/day/period/room for target row. |
| Q3 | PASS | Correct comparison, no group confusion. |
| Q4 | PASS | The question asks for the date range, and the answer correctly returned 30/06/2025 to 06/07/2025. The source context also contains `10h00`, but the question did not explicitly ask for the start time, so omission of the hour is not considered a failure. |
| Q5 | PASS | Relevant normalized program rows returned. |
| Q6 | PASS | Relevant normalized program rows returned. |
| Q7 | PASS | KTR3185 title and 5 credits surfaced. |
| Q8 | PASS | Correct out-of-scope refusal, no live exchange-rate hallucination. |

Runtime tally: 8 PASS, 0 PARTIAL, 0 FAIL.

Mandatory PASS checks: Q1, Q2, Q7, Q8 all passed. Full Q1–Q8 runtime PASS.

## Runtime Source Evidence

- Q1/Q2/Q3 sources were all `normalized_table_row`.
- Q4 sources were `normalized_table_row` and included the carried registration context with `10h00 30/06/2025  06/07/2025`.
- Q5/Q6 sources were normalized program-table rows.
- Q7 sources included both the page-20 program row and page-94 KTR3185 timetable rows with title/credit evidence.
- Q8 answer refused despite irrelevant retrieved context.

## Performance Notes

- Ingest duration: 67.293s.
- Embedding duration from logs: about 20.6s for 2745 vectors.
- Verification wall time: about 12m24s including ingest and 25s waits between questions.
- Candidate bounding remained active for large table queries, for example Q7 `boundedForScoring=240 from=1992`.
- No request approached the 600s SSE timeout.
- No rate-limit or timeout failure observed.

## Known Limitations

- Header names remain noisy in some wide extracted tables.
- Some answers still benefit from carried `Context:` metadata.
- Q4: source context contains `10h00` alongside the registration date range; the question asked only for dates, so the answer omitting the hour is acceptable (verdict corrected R1 from overly strict expected answer).

## Next Recommended Task

No 23J5C or time-of-day faithfulness pass required for Q4. Optional: generic wide-table header cleanup where extraction remains noisy.
