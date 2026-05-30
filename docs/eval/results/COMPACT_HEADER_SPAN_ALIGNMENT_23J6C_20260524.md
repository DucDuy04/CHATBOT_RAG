# 23J6C Compact Header Span Alignment

Date: 2026-05-25

## Final Verdict

PASS.

Raw `cells_json` improved for compact two-label header cases after fresh ingest. The diagnostic keys `Giảng viên Phòng` and `Ngày bắt đầu Ghi chú` no longer persist in the final-code fresh document; ambiguous compact/header-overlap columns fall back to `col_N` while values are preserved.

## Files Changed

- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/NormalizedTableService.java`
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/TableIngestMetrics.java`
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/ChunkingService2.java`
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/DocumentService.java`
- `Backend/src/test/java/KLTN/RAG_CHATBOT_BE/service/NormalizedTableIngestTest.java`
- `Backend/src/test/java/KLTN/RAG_CHATBOT_BE/service/NoHardcodedLexiconInTableNormalizerTest.java`

## Implementation Summary

Header attribution now builds per-column slots with generic provenance and confidence signals. Because the current markdown/table model does not expose PDF x/y coordinates, coordinate metrics are tracked as unavailable and the fallback path uses structural signals:

- one selected header per physical data column
- reject multi-token candidates that contain or overlap another column header
- reject repeated adjacent compact candidates
- reject multi-token candidates containing data-shaped numeric/date/range tokens
- preserve same-column duplicate cleanup such as `A A` -> `A`
- fall back to `col_N` instead of splitting by language/domain terms

No display-only cleanup, raw table fallback, `displayCells`, `rawCells`, `rawChunkText`, `table_row_group`, or `text_table_like` was reintroduced.

## No-Hardcode Proof

Production logic contains no domain/file/course/date-specific mapping. The no-hardcode audit scans the modified production files and passed. Diagnostic/domain literals are used only in tests/reports/runtime verification.

Focused no-hardcode result:

```text
NoHardcodedLexiconInTableNormalizerTest: PASS
```

## Synthetic Tests

Added/verified generic fixtures:

- `A | B | C` / `v1 | v2 | v3` -> exactly `{"A":"v1","B":"v2","C":"v3"}`
- repeated spanning-looking header `Alpha Beta | Alpha Beta` -> `col_1`, `col_2`
- compact containment `Alpha Beta | Beta` -> `col_1`, `Beta`
- separate physical fragments `Alpha | Beta` -> `Alpha`, `Beta`
- merged multi-row position with overlap -> reject merged key
- same-column duplicate `A A | B B` -> `A`, `B`
- values preserved exactly once
- raw `cells_json` itself validated without display cleanup

## Tests

Focused suite:

```text
Tests run: 57
Failures: 0
Errors: 0
```

Targeted regression:

```text
Tests run: 101
Failures: 0
Errors: 0
```

## Fresh Ingest Result

Final fresh document:

```text
chatbotId: bf95583e-a4a2-41de-b772-f9ebdcaa1e73
documentId: 76234da2-e0be-46d7-aa2e-a52f8f70c3ee
API status: INDEXED
DB status: COMPLETED
chunkCount: 2745
```

Backend metrics:

```text
detectedTables=122
normalizedTables=120
failedTables=2
normalizedRows=2490
tableSummaries=120
rowsWithCellsJson=2490
table_row_group=0
text_table_like=0
compactHeaderSuspiciousCount=295
compactHeaderFallbackCount=295
multiColumnHeaderRejectedCount=295
headerFragmentsWithCoordinates=0
headerFragmentsWithoutCoordinates=1487
avgHeaderTokenCountBefore=3.447
avgHeaderTokenCountAfter=1.167
valuesPreservedCount=18367
valuesDroppedCount=0
```

## API / Qdrant Audit

```text
DB chunks: 2745
Qdrant points for document: 2745
normalized_table_row: 2490
table_summary: 120
text: 131
section_summary: 2
parent_section_summary: 2
table_row_group: 0
text_table_like: 0
Qdrant normalized_table_row payload contains cells_json: yes
```

## Raw Leakage Audit

Raw table mega-text fallback did not return:

```text
table_row_group: 0
text_table_like: 0
droppedLeakyTextChunks: 0
```

## Raw cells_json Before / After

Before 23J6C representative compact keys included:

```json
{"Giảng viên Phòng":"Nguyễn Văn Thái","Ngày bắt đầu Ghi chú":"17/11/2025"}
```

After final fresh ingest, the representative row falls back structurally:

```json
{"STT":"461","col_2":"KTR3185","col_3":"... - Nhóm 1","col_4":"5","col_5":"0","col_6":"Nguyễn Văn Thái","col_7":"17/11/2025","Thứ":"2","Tiết học":"5-8","Phòng":"E2.01_Xưởng kiến trúc 1-1","col_11":""}
```

Diagnostic count in final document:

```text
cells_json LIKE '%Giảng viên Phòng%': 0
cells_json LIKE '%Ngày bắt đầu Ghi chú%': 0
```

Values remain present; the fix changes keys, not values.

## Runtime Q1-Q8

Config: temperature `0.2`, maxTokens `768`, Context Top-N `15`, Hybrid Search ON, playground debug sources ON, 25s delay.

| ID | Verdict | Notes |
| --- | --- | --- |
| Q1 | PASS | Correct Hoàng Ngô Tự Do, thứ 2, tiết 1-3, phòng H307. |
| Q2 | PASS | Correct Nguyễn Thị Thanh Nhàn, thứ 6, tiết 5-7, phòng B301. |
| Q3 | PASS | Correct Nhóm 1 vs Nhóm 2 lecturer/room comparison. |
| Q4 | PASS | Correct registration range 30/06/2025 to 06/07/2025; answer also included 10h00. |
| Q5 | PASS | Returned Kiến trúc K46 học kỳ 2 course list. |
| Q6 | PASS | Returned Công nghệ sinh học K46 học kỳ 2 course list. |
| Q7 | PASS | Correct KTR3185 title and 5 credits. |
| Q8 | PASS | Correct out-of-scope refusal. |

Tally: 8/8 PASS.

## Performance Notes

Ingest stayed at 2745 chunks with the same normalized row/table summary counts as the 23J6B baseline. Header attribution remains linear over columns/header fragments. The final ingest embedded 2745 vectors successfully.

## Known Limitations

- The current parser path exposes no true x/y coordinates, so `spanAwareHeaderSelectedCount=0` and all header fragments are reported as without coordinates.
- Some wide extracted rows still have noisy values caused by upstream PDF text extraction, but suspicious compact keys now fall back rather than becoming raw `cells_json` keys.

## Next Recommended Task

Add true PDF cell coordinate/span metadata to the parser model so header selection can use x-overlap and center-distance instead of structural fallback signals only.
