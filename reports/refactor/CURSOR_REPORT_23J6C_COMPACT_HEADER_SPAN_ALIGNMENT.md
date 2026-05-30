# Cursor Report 23J6C: Compact Header Span Alignment

Date: 2026-05-25

## Final Verdict

PASS.

Implemented a generic ingest-time compact-header rejection pass. Final fresh ingest improved raw `cells_json`: diagnostic compact keys fall back to `col_N`, values are preserved, DB/Qdrant counts match, and runtime Q1-Q8 passed 8/8.

## Implementation Summary

`NormalizedTableService` now enriches header slots with generic structural metadata and applies a span/alignment fallback step. With no coordinate model available, it uses column index, physical header fragments, token count, repeated adjacent candidates, cross-column token containment/overlap, and data-shaped header tokens. Ambiguous compact candidates are not split by vocabulary; they become `col_N`.

Metrics were extended through `TableIngestMetrics`, `ChunkingService2`, and `DocumentService` logs:

```text
compactHeaderSuspiciousCount
compactHeaderFallbackCount
spanAwareHeaderSelectedCount
multiColumnHeaderRejectedCount
headerFragmentsWithCoordinates
headerFragmentsWithoutCoordinates
avgHeaderTokenCountAfter
rowsWithGenericColumnKeys
valuesPreservedCount
valuesDroppedCount
```

## Files Changed

- `NormalizedTableService.java`
- `TableIngestMetrics.java`
- `ChunkingService2.java`
- `DocumentService.java`
- `NormalizedTableIngestTest.java`
- `NoHardcodedLexiconInTableNormalizerTest.java`

## Verification

Focused:

```text
Tests run: 57, Failures: 0, Errors: 0
```

Targeted:

```text
Tests run: 101, Failures: 0, Errors: 0
```

Fresh ingest:

```text
chatbotId=bf95583e-a4a2-41de-b772-f9ebdcaa1e73
documentId=76234da2-e0be-46d7-aa2e-a52f8f70c3ee
API status=INDEXED
DB status=COMPLETED
DB chunks=2745
Qdrant points=2745
normalized_table_row=2490
table_summary=120
table_row_group=0
text_table_like=0
```

Final ingest metrics:

```text
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

## Raw cells_json Evidence

Before:

```json
{"Giảng viên Phòng":"Nguyễn Văn Thái","Ngày bắt đầu Ghi chú":"17/11/2025"}
```

After:

```json
{"STT":"461","col_2":"KTR3185","col_3":"... - Nhóm 1","col_4":"5","col_5":"0","col_6":"Nguyễn Văn Thái","col_7":"17/11/2025","Thứ":"2","Tiết học":"5-8","Phòng":"E2.01_Xưởng kiến trúc 1-1","col_11":""}
```

Final diagnostic counts:

```text
Giảng viên Phòng: 0
Ngày bắt đầu Ghi chú: 0
```

## Runtime Q1-Q8

| ID | Verdict |
| --- | --- |
| Q1 | PASS |
| Q2 | PASS |
| Q3 | PASS |
| Q4 | PASS |
| Q5 | PASS |
| Q6 | PASS |
| Q7 | PASS |
| Q8 | PASS |

## No-Hardcode Proof

No production domain lexicon or file/course/date mapping was added. The audit scans modified table-pipeline production files and passed. Diagnostic terms appear only in verification/report text.

## Known Limitations

No x/y coordinate metadata is currently available from the markdown-normalization path. This pass is span-aware in the structural sense and records coordinate availability, but true x-overlap selection needs parser support.

## Next Recommended Task

Expose PDF cell coordinates/spans from parsing into normalized table ingest, then replace structural overlap fallback with coordinate overlap and nearest-center selection where available.
