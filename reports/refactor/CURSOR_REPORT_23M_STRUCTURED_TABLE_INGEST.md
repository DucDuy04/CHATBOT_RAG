# Cursor Report 23M - Structured Table Ingest

## Verdict

PARTIAL.

The source ingest corruption was addressed at the parser/normalizer boundary: accepted PDF Tabula tables now enter normalization as `RawTableModel`, not Markdown. The KTR3185 row no longer absorbs neighboring identifiers, raw table leakage remains suppressed, and Q7 now returns the correct title and 5 credits.

The task cannot be marked PASS because the required Q1-Q8 runtime verification was 4/8 instead of 8/8.

## Main Refactor

New structured path:

```text
Upload
  -> PDFBox PDFTextStripper
  -> Tabula Spreadsheet or Basic fallback
  -> RawTableModel with cell text and coordinates
  -> RAW_TABLE_REF marker plus Section.rawTableBlocks sidecar
  -> ChunkingService2.processNormalizedRawTable()
  -> NormalizedTableService.normalizeRawTable()
  -> normalized_table_row/table_summary
  -> DocumentService.saveChunks()
  -> EmbeddingService.embedAndStore()
```

The old accepted-PDF path:

```text
Tabula Table -> convertTableToMarkdown() -> parseMarkdownTable()
```

is no longer the production path for accepted Tabula tables.

## RawTableModel

Added:

- `RawTableCell`
- `RawTableRow`
- `RawTableModel`
- `RawTableBlock`

The model carries physical row/column indexes, page number, x/y/width/height, xEnd/yEnd, table index on page, extractor type, and provenance id.

## Spreadsheet and Basic Proof

`DocumentParserService` wraps extractor output with an extractor type:

- Spreadsheet tables become `RawTableModel.ExtractorType.SPREADSHEET`
- Basic fallback tables become `RawTableModel.ExtractorType.BASIC`

Both routes call `convertTabulaTableToRawTableModel(...)`, register the raw table, and emit `[RAW_TABLE_REF:<id>]`.

`ChunkingService2` resolves the marker to `RawTableBlock` and calls `normalizedTableService.normalizeRawTable(...)`.

Runtime corpus:

```text
rawTableModelsCreated=122
rawTableModelsCreatedFromSpreadsheet=122
rawTableModelsCreatedFromBasic=0
structuredTablesNormalized=122
markdownTablesNormalizedLegacy=0
pdfTablesUsingMarkdownBridge=0
spreadsheetTablesUsingMarkdownBridge=0
basicTablesUsingMarkdownBridge=0
```

Basic fallback did not trigger in the runtime PDF, but synthetic tests assert Basic uses the same RawTableModel path.

## Normalization Changes

`NormalizedTableService.normalizeRawTable(...)` now performs:

- coordinate-aware header inference
- coordinate-guarded wrapped-row merge
- x-overlap/nearest-center cell-to-header mapping
- canonical row text generation from corrected `cells_json`

Legacy Markdown normalization remains for non-PDF/debug flows only.

## KTR3185 Result

Before 23M, the persisted row merged neighboring identifiers:

```json
{
  "col_2": "KTR3185 1 2 3 4",
  "col_5": "KTR3273 KTR4015 KTR5022",
  "col_11": "3 5 2"
}
```

After fresh ingest:

- KTR3185 rows were found on page 22.
- `table_id` observed: `tbl_14_39`.
- No KTR3185 row contains KTR3273, KTR4015, or KTR5022.
- The previous multi-identifier corruption shape was not reproduced.
- Q7 answered the full title and 5 credits.

## Runtime Audit

Fresh document:

```text
chatbotId=51e21150-e884-43c6-aa74-77e06d68cdee
documentId=33f4f99a-70ab-46b9-9a81-ec72c686fd3a
status=INDEXED
chunkCount=3417
```

Qdrant:

```text
points=3417
normalized_table_row=3160
table_summary=122
text=131
section_summary=2
parent_section_summary=2
cells_json payloads=3160
table_row_group=0
text_table_like=0
raw leak types=0
```

Backend ingest metrics:

```text
detectedTables=122
normalizedTables=122
failedTables=0
normalizedRows=3160
valuesPreservedCount=15367
valuesDroppedCount=0
rawTableCellsWithCoordinates=29919
rawTableCellsMissingCoordinates=19086
pageAttributionPhysicalCount=122
```

DB SQL audit was not available because `docker exec` escalation was rejected. API status, backend logs, and Qdrant parity were used.

## Q1-Q8

| Q | Verdict | totalMs | Note |
|---|---|---:|---|
| Q1 | FAIL | 45858 | Missed group 2 evidence. |
| Q2 | FAIL | 14690 | Wrong weekday. |
| Q3 | FAIL | 23128 | Depends on missed group 2. |
| Q4 | PASS | 9310 | Correct. |
| Q5 | FAIL | 17501 | Could not list requested curriculum rows. |
| Q6 | PASS | 13260 | Acceptable. |
| Q7 | PASS | 16662 | Correct full title and 5 credits. |
| Q8 | PASS | 5508 | Correct out-of-scope refusal. |

Total: 4/8 PASS.

## Tests

Focused suite:

```text
Tests run: 62, Failures: 0, Errors: 0
```

Targeted regression suite:

```text
Tests run: 145, Failures: 0, Errors: 0
```

Compile passed.

## No-Hardcode Audit

`NoHardcodedLexiconInTableNormalizerTest` passed. No production logic was added for the diagnostic terms. Diagnostic literals appear only in reports/scripts/tests where allowed.

## Remaining Work

- Restore Q1/Q2/Q3/Q5 by tuning coordinate header attribution and retrieval/source selection.
- Exercise Basic fallback on a runtime fixture that actually triggers Basic extraction.
- Wire fine-grained coordinate merge rejection counters into runtime stats.
- Clean inactive commented legacy snippets in `DocumentParserService`.
- Add a DB SQL audit route that does not depend on blocked `docker exec` escalation.
