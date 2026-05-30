# Cursor Report 23J6: Generic Header / Source Presentation Cleanup

Date: 2026-05-24

## Final Verdict

PASS.

Implemented a safe display-layer header cleanup for normalized table source evidence. Raw `cells_json`, stored chunks, retrieval, and Qdrant payloads were left unchanged. Backend rebuild, focused tests, targeted regression, existing DB/Qdrant audit, raw leakage audit, and runtime Q1-Q8 all passed.

## Source Code Change Statement

Production source changed:

- `TableHeaderDisplayCleaner.java`: generic structural header cleanup.
- `ChatService.java`: response-only display rendering for normalized table rows.
- `ChatResponse.java`: adds `displayCells`, `rawCells`, `rawChunkText`.
- `RetrievedContext.java` and `RagRetrievalService.java`: carry stored `cellsJson` into response mapping.

Test/source verification changed:

- `ChatServiceSourcePresentationTest.java`: synthetic cleanup and source presentation coverage.
- `NoHardcodedLexiconInTableNormalizerTest.java`: includes the new cleaner in literal audit.
- Runtime artifact: `_run_23j6_runtime_verify.mjs`.

## Implementation Details

The chosen path was Option A: display-only cleaned headers.

Cleaner behavior:

- `STT STT` -> `STT`
- `Code Code Name Name` -> `Code Name`
- `A B A C` -> `A B C`
- empty header -> `col_N`
- duplicate cleaned keys get suffixes such as `_2`
- long headers are bounded by token/character limits

No values are merged or dropped. Empty values stay in `displayCells`/`rawCells`; `chunkText` omits empty values the same way canonical display text already did.

## Raw Data Impact

- Raw DB `cells_json`: unchanged.
- Raw source text: available as `rawChunkText`.
- Raw source cells: available as `rawCells`.
- Display source cells: available as `displayCells`.
- Qdrant payload: unchanged.
- Re-ingest required: no.

## Verification

Focused tests:

```text
Tests run: 41
Failures: 0
Errors: 0
```

Targeted regression suite:

```text
Tests run: 94
Failures: 0
Errors: 0
```

Build:

```text
docker compose config -q: PASS
docker compose up --build -d backend: PASS
```

No-hardcode:

```text
NoHardcodedLexiconInTableNormalizerTest: PASS
```

## DB / Qdrant / Leakage Audit

Existing 23J5B document was reused:

```text
chatbotId: f62a6fb6-aa2f-4aa6-874d-5946d957ecd4
documentId: 131f2a64-5de0-4a34-8060-04e8ac3a7cae
DB status: COMPLETED
DB chunks: 2745
Qdrant points: 2745
normalized_table_row: 2490
table_summary: 120
text: 131
section_summary: 2
parent_section_summary: 2
rows with cells_json: 2490
table_row_group: 0
text_table_like: 0
long table-like text chunks: 0
```

Fresh re-ingest skipped because presentation-only code changed.

## Before / After Evidence

Q1 raw:

```json
{"STT STT":"9","Mã học phần Mã Tên lớp học phần học phần":"KNM1013","Số TC Số Thứ":"3"}
```

Q1 display:

```json
{"STT":"9","Mã học phần Tên lớp":"KNM1013","Số TC Thứ":"3"}
```

Q7 timetable display preserves values:

```json
{
  "STT": "461",
  "Mã học phần Tên lớp": "KTR3185",
  "Tên lớp học phần Số Giảng viên TC": "Đồ án kiến trúc công trình tổ hợp đa chức năng - Nhóm 1",
  "Số TC Thứ": "5",
  "Phòng": "E2.01_Xưởng kiến trúc 1-1"
}
```

## Runtime Q1-Q8

| ID | Verdict | Notes |
| --- | --- | --- |
| Q1 | PASS | Correct Nhóm 2 lecturer/day/period/room. |
| Q2 | PASS | Correct Nhóm 4 lecturer/day/period/room. |
| Q3 | PASS | Correct Nhóm 1 vs Nhóm 2 comparison. |
| Q4 | PASS | Correct date range 30/06/2025 to 06/07/2025; time not required by question. |
| Q5 | PASS | Correct Kiến trúc K46 học kỳ 2 list. |
| Q6 | PASS | Correct Công nghệ sinh học K46 học kỳ 2 list. |
| Q7 | PASS | Correct KTR3185 title and 5 credits. |
| Q8 | PASS | Out-of-scope refusal, no current exchange-rate hallucination. |

Runtime tally: 8/8 PASS.

Note: the Node runtime command wrapper timed out after writing `_23j6_runtime_raw.json`; all eight answers were present in the file, and backend logs confirmed Q8 completion.

## Performance / Risk

- Response-only transformation.
- Linear over each source row's small cell map.
- No parser rewrite.
- No retrieval semantic change.
- No source evidence suppression.

## Known Limitations

- The cleanup reduces duplicated/overlapping header text but does not fully reconstruct semantic columns for every wide PDF table.
- Some source rows from program tables remain noisy where extraction has already mixed cell/header fragments.
- Frontend should prefer `displayCells` when rendering source cells; raw fields are preserved for debug.

## Next Recommended Task

Frontend source evidence polish: render `displayCells` first, keep `rawCells` behind an expandable debug/detail affordance.
