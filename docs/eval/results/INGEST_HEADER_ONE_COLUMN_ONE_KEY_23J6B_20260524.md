# 23J6B Ingest Header One Column One Key

Date: 2026-05-24

## Final Verdict

PASS.

23J6 display-only cleanup was removed. Header cleanup now happens during normalized table ingest, so stored `cells_json`, canonical row text, and Qdrant payloads use the same corrected structure. Fresh re-ingest and runtime Q1-Q8 completed 8/8 PASS.

## Files Changed

- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/NormalizedTableService.java`
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/TableIngestMetrics.java`
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/ChunkingService2.java`
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/DocumentService.java`
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/ChatService.java`
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/dto/ChatResponse.java`
- deleted `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/TableHeaderDisplayCleaner.java`
- tests updated in `NormalizedTableIngestTest`, `ChatServiceSourcePresentationTest`, `NoHardcodedLexiconInTableNormalizerTest`

## 23J6 Rollback

Removed:

- `TableHeaderDisplayCleaner.java`
- `displayCells`, `rawCells`, `rawChunkText` from `ChatResponse.SourceDto`
- response-only source rendering in `ChatService`
- tests that validated display-only cleanup

Kept:

- `RetrievedContext.cellsJson`, because it is useful retrieval/debug plumbing and no longer feeds display cleanup.

API DTO shape changed back: response sources now expose `chunkText` only, without display/raw cell variants.

## Algorithm Summary

Ingest now builds per-column `HeaderSlot`s. Each slot selects one focused header for that column:

- use deepest/leaf aligned header when clear
- use parent only when child is missing
- dedupe same-column repeated fragments
- fall back to `col_N` when the selected header is structurally ambiguous
- never join sibling header fragments into the cell key
- preserve values exactly

Rejected colon-style header fragments that appear to carry table/group context are preserved as row `Context:` text, not as `cells_json` keys.

## Raw Examples

Before 23J5/23J6 raw examples had keys like:

```json
{"STT STT":"9","Mã học phần Mã Tên lớp học phần học phần":"KNM1013","Số TC Số Thứ":"3"}
```

After fresh 23J6B ingest, representative Qdrant `cells_json` uses focused or generic keys:

```json
{"STT":"17","col_2":"KNM1013","col_3":"Kỹ năng mềm - Nhóm 10","col_4":"3","col_5":"0","Giảng viên Phòng":"Nguyễn Đức Vũ Quyên","Ngày bắt đầu Ghi chú":"08/09/2025","Thứ":"6","Tiết học":"1-3","Phòng":"H201","Ghi chú":""}
```

KTR3185 representative row:

```json
{"STT":"461","col_2":"KTR3185","col_3":"Đồ án kiến trúc công trình tổ hợp đa chức năng - Nhóm 1","col_4":"5","col_5":"0","Giảng viên Phòng":"Nguyễn Văn Thái","Ngày bắt đầu Ghi chú":"17/11/2025","Thứ":"2","Tiết học":"5-8","Phòng":"E2.01_Xưởng kiến trúc 1-1","Ghi chú":""}
```

Some compact extracted headers remain imperfect, but the worst sibling-concatenated keys are replaced with `col_N` rather than persisted as raw keys.

## Synthetic Result

`A | B | C` / `v1 | v2 | v3` produces exactly:

```json
{"A":"v1","B":"v2","C":"v3"}
```

Covered by `rawCellsJsonItselfIsCleanWithoutDisplayCleanup`.

## Tests

Focused:

```text
NormalizedTableIngestTest, NormalizedTableSuppressionTest,
NoHardcodedLexiconInTableNormalizerTest, ChatServiceSourcePresentationTest
Tests run: 53, Failures: 0, Errors: 0
```

Targeted regression:

```text
HybridKeywordSearchTest, CellAwareNormalizedRowRetrievalTest,
NoHardcodedLexiconInCellAwareScorerTest, NoHardcodedLexiconInTableNormalizerTest,
NormalizedTableSuppressionTest, NormalizedTableIngestTest,
FinalContextSelectionTest, RetrievalTopKTest,
ChatServiceSourcePresentationTest, RagTokenAuditTest
Tests run: 97, Failures: 0, Errors: 0
```

No-hardcode audit: PASS. Production files contain no forbidden diagnostic/domain string literals.

## Fresh Re-Ingest

Verification artifact:

- `docs/eval/results/_23j6b_runtime_raw.json`

Fresh runtime document:

```text
chatbotId: 6b3255cc-89c9-48a6-8048-7ff8cda6ee63
documentId: 689aefcd-1740-4e4f-a466-598bd6891c44
API status: INDEXED
API chunkCount: 2745
```

API chunk audit:

```text
chunks: 2745
normalized_table_row inferred from content: 2490
table_summary inferred from content: 120
table_row_group: 0
text_table_like: 0
long table-like text chunks: 0
```

Qdrant audit:

```text
points: 2745
normalized_table_row: 2490
table_summary: 120
text: 131
section_summary: 2
parent_section_summary: 2
rows with cells_json payload: 2490
```

Direct `docker exec` MySQL audit was blocked by the approval system, so DB counts were verified through the DB-backed document/chunks API and compared with Qdrant HTTP scroll.

## Header Metrics

Metrics added:

- `headerSlotsCreated`
- `headerSlotsFallbackGeneric`
- `headerSiblingContaminationPrevented`
- `headerAmbiguousFallbackCount`
- `avgHeaderTokenCountBefore`
- `avgHeaderTokenCountAfter`
- `noisyComposedHeaderBeforeCount`
- `noisyComposedHeaderAfterCount`
- `valuesPreservedCount`
- `valuesDroppedCount`

Runtime audit showed values preserved in normalized rows and 2490 Qdrant payloads with `cells_json`.

## Runtime Q1-Q8

| ID | Verdict | Notes |
| --- | --- | --- |
| Q1 | PASS | Correct lecturer/day/period/room for Nhóm 2. |
| Q2 | PASS | Correct lecturer/day/period/room for Nhóm 4. |
| Q3 | PASS | Correct Nhóm 1 vs Nhóm 2 comparison. |
| Q4 | PASS | Correct date range 30/06/2025 to 06/07/2025. |
| Q5 | PASS | Returns Kiến trúc K46 học kỳ 2 course list. |
| Q6 | PASS | Returns Công nghệ sinh học K46 học kỳ 2 course list. |
| Q7 | PASS | Correct KTR3185 title and 5 credits. |
| Q8 | PASS | Out-of-scope refusal. |

## Known Limitations

- Some PDF-extracted rows still contain compact ambiguous headers such as `Giảng viên Phòng`; these are much narrower than the previous composed mega-headers.
- For very wide program tables, context salvage is still needed for cohort/group labels.
- Direct MySQL CLI audit could not be run due Docker approval denial; Qdrant and DB-backed API counts matched.

## Next Recommended Task

Continue a generic coordinate/span-aware PDF header alignment pass for compact two-label header cells, without reintroducing display-only cleanup or domain-specific label rules.
