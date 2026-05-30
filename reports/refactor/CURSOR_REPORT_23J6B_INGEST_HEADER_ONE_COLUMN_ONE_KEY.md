# Cursor Report 23J6B: Ingest Header One Column One Key

Date: 2026-05-24

## Final Verdict

PASS.

Replaced the 23J6 display-only source cleanup with ingest-time header attribution. Fresh re-ingest completed and runtime Q1-Q8 passed 8/8. Acceptance is based on raw `cells_json` and Qdrant payloads, not `displayCells`.

## Rollback Details

Removed:

- `TableHeaderDisplayCleaner.java`
- `displayCells`, `rawCells`, `rawChunkText`
- response-only normalized row source rebuilding
- display-cleaner unit tests

Kept:

- `RetrievedContext.cellsJson`, retained as useful retrieval/debug plumbing and not used for presentation cleanup.

## Implementation Summary

`NormalizedTableService` now creates per-column `HeaderSlot`s:

- one slot per column
- leaf/deepest header wins
- parent header only used when child is missing
- same-column repeated token cleanup is allowed
- ambiguous/noisy selected headers fall back to `col_N`
- values are mapped by column index and preserved
- rejected colon-style fragments can become row `Context:` evidence, not keys

The canonical content, DB-backed chunk API output, and Qdrant `cells_json` all reflect the corrected ingest output.

## Files Changed

- `NormalizedTableService.java`
- `TableIngestMetrics.java`
- `ChunkingService2.java`
- `DocumentService.java`
- `ChatService.java`
- `ChatResponse.java`
- deleted `TableHeaderDisplayCleaner.java`
- `NormalizedTableIngestTest.java`
- `ChatServiceSourcePresentationTest.java`
- `NoHardcodedLexiconInTableNormalizerTest.java`
- verification artifacts under `docs/eval/results`

## Raw Evidence

Synthetic raw `cells_json`:

```json
{"A":"v1","B":"v2","C":"v3"}
```

Representative KNM row:

```json
{"STT":"17","col_2":"KNM1013","col_3":"Kỹ năng mềm - Nhóm 10","col_4":"3","col_5":"0","Giảng viên Phòng":"Nguyễn Đức Vũ Quyên","Ngày bắt đầu Ghi chú":"08/09/2025","Thứ":"6","Tiết học":"1-3","Phòng":"H201","Ghi chú":""}
```

Representative KTR row:

```json
{"STT":"461","col_2":"KTR3185","col_3":"Đồ án kiến trúc công trình tổ hợp đa chức năng - Nhóm 1","col_4":"5","col_5":"0","Giảng viên Phòng":"Nguyễn Văn Thái","Ngày bắt đầu Ghi chú":"17/11/2025","Thứ":"2","Tiết học":"5-8","Phòng":"E2.01_Xưởng kiến trúc 1-1","Ghi chú":""}
```

## Verification

Focused tests:

```text
Tests run: 53
Failures: 0
Errors: 0
```

Targeted suite:

```text
Tests run: 97
Failures: 0
Errors: 0
```

Build and runtime:

```text
docker compose config -q: PASS
docker compose up --build -d backend: PASS
fresh ingest: PASS
runtime Q1-Q8: 8/8 PASS
```

Fresh document:

```text
chatbotId: 6b3255cc-89c9-48a6-8048-7ff8cda6ee63
documentId: 689aefcd-1740-4e4f-a466-598bd6891c44
API status: INDEXED
API chunks: 2745
Qdrant points: 2745
```

Chunk distribution:

```text
normalized_table_row: 2490
table_summary: 120
text: 131
section_summary: 2
parent_section_summary: 2
table_row_group: 0
text_table_like: 0
long table-like text chunks: 0
```

Direct MySQL `docker exec` audit was blocked by the approval system. The DB-backed document/chunks API and Qdrant scroll both reported 2745 items.

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

## Performance Notes

Header-slot attribution is linear over header rows and columns. No Context Top-N increase, scorer boost, raw table fallback, or answer patching was added.

## Known Limitations

Some compact two-label headers remain imperfect in very wide PDF tables. The critical improvement is that long sibling-composed mega-headers now fall back to `col_N`, and raw values stay aligned/preserved.

## Next Recommended Task

Generic coordinate/span-aware header alignment for compact wide-table headers.
