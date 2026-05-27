# Task 23M - Structured PDF Table Ingest

Date: 2026-05-26

## Final Verdict

PARTIAL.

The production PDF Tabula ingest path no longer sends accepted Tabula tables through the Markdown bridge as the primary representation. Both Spreadsheet and Basic extractor results are converted to `RawTableModel`, carried as raw table references, and normalized through `normalizeRawTable(...)`.

The ingest corruption targeted by 23L is improved: the KTR3185 logical row no longer merges neighboring identifiers KTR3273, KTR4015, or KTR5022, page attribution for the matching curriculum rows is now page 22, raw table fallback leakage remains suppressed, and Q7 now answers the full title with 5 credits.

This is not a PASS because the required fresh runtime Q1-Q8 run was 4/8, not 8/8. Q1, Q2, Q3, and Q5 still need a follow-up retrieval/header-attribution fix.

## Files Changed

- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/record/Section.java`
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/RawTableCell.java`
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/RawTableRow.java`
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/RawTableModel.java`
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/RawTableBlock.java`
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/DocumentParserService.java`
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/ChunkingService2.java`
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/NormalizedTableService.java`
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/TableIngestMetrics.java`
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/DocumentService.java`
- `Backend/src/test/java/KLTN/RAG_CHATBOT_BE/service/NormalizedTableIngestTest.java`

## Markdown Bridge Status

Production PDF Tabula path:

```text
PDFBox page text
  -> Tabula Spreadsheet or Basic
  -> convertTabulaTableToRawTableModel(...)
  -> [RAW_TABLE_REF:<rawTableId>] marker plus sidecar RawTableModel
  -> Section.rawTableBlocks
  -> ChunkingService2.processNormalizedRawTable(...)
  -> NormalizedTableService.normalizeRawTable(...)
  -> normalized_table_row/table_summary chunks
```

Removed from the primary PDF Tabula path:

- `convertTableToMarkdown()`
- `parseMarkdownTable()`
- Markdown table serialization/deserialization for accepted Tabula tables
- Markdown-only header attribution for accepted Tabula tables
- Markdown-only row merge for accepted Tabula tables

Still present as legacy/debug surface:

- `convertTableToMarkdown(...)` remains in `DocumentParserService` for legacy/debug helpers.
- `parseMarkdownTable(...)` remains in `NormalizedTableService` for legacy Markdown table normalization.
- `[TABLE_START]` blocks can still be consumed by `ChunkingService2` for non-PDF legacy/debug flows.

Runtime metrics from the fresh ingest:

```text
structuredTablesNormalized=122
markdownTablesNormalizedLegacy=0
pdfTablesUsingMarkdownBridge=0
spreadsheetTablesUsingMarkdownBridge=0
basicTablesUsingMarkdownBridge=0
```

## RawTableModel Design

`RawTableModel` preserves:

- `tableId`
- `documentId`
- `pageNumber`
- `tableIndexOnPage`
- `extractorType` as `SPREADSHEET` or `BASIC`
- table bounding box
- `rows`
- raw page context
- section context
- title candidate

`RawTableCell` preserves:

- text
- physical row and column indexes
- page number
- x/y/width/height
- xEnd/yEnd
- table index on page
- extractor type
- provenance id

`RawTableRow` groups physical cells. `RawTableBlock` carries a raw table sidecar into `Section`.

## Extractor Proof

Spreadsheet path:

```text
SpreadsheetExtractionAlgorithm.extract(page)
  -> new ExtractedTable(table, RawTableModel.ExtractorType.SPREADSHEET)
  -> convertTabulaTableToRawTableModel(...)
  -> [RAW_TABLE_REF:<id>]
  -> normalizeRawTable(...)
```

Basic fallback path:

```text
BasicExtractionAlgorithm.extract(page)
  -> new ExtractedTable(table, RawTableModel.ExtractorType.BASIC)
  -> convertTabulaTableToRawTableModel(...)
  -> [RAW_TABLE_REF:<id>]
  -> normalizeRawTable(...)
```

The runtime corpus did not trigger Basic fallback:

```text
rawTableModelsCreatedFromSpreadsheet=122
rawTableModelsCreatedFromBasic=0
```

Synthetic tests cover both extractor types and assert both reach the structured path.

## Coordinate Preservation Proof

Fresh ingest metrics:

```text
rawTableModelsCreated=122
rawTableModelsCreatedFromSpreadsheet=122
rawTableModelsCreatedFromBasic=0
rawTableCellsWithCoordinates=29919
rawTableCellsMissingCoordinates=19086
headerFragmentsWithCoordinates=1491
headerFragmentsWithoutCoordinates=0
pageAttributionPhysicalCount=122
```

The missing coordinate count comes from empty/filler Tabula cells and span/blank positions. Non-empty table cells retain the Tabula geometry when available.

## Header Inference Algorithm

`inferHeadersFromCoordinates(...)` works from raw table geometry:

- identifies header candidate rows from the top physical rows and density/data-shape signals
- builds data column slots from x ranges
- assigns header cells to data columns by x-overlap, then nearest center
- prefers the deepest/leaf header aligned to a column
- uses a parent header only when a child header is missing
- falls back to `col_N` when confidence is insufficient
- avoids concatenating sibling headers into a single key

## Row Merge Guard Algorithm

`mergeWrappedRowsWithCoordinateGuard(...)` only merges a physical row into the previous logical row when generic continuation evidence is present:

- no new row-number-like value in the first column
- no new uppercase code-like identifier in an identifier-like column
- row shape is sparse/continuation-like
- vertical distance is compatible with wrapping
- text x-range overlaps a previous cell/data column
- the fragment maps to the same target column

Merge rejection reasons include new row number, new identifier, x-overlap failure, and independent multi-cell row shape.

## mapCellsFromCoordinates

Each data cell is assigned to exactly one header slot using best x-overlap and nearest center. Values are preserved as extracted and canonical row text is built from the resulting `cells_json`, so DB content and Qdrant payload describe the same normalized cells.

Generic invariant verified by test:

```json
{
  "A": "v1",
  "B": "v2",
  "C": "v3"
}
```

## Fresh Re-Ingest

Runtime target:

```text
chatbotId=51e21150-e884-43c6-aa74-77e06d68cdee
documentId=33f4f99a-70ab-46b9-9a81-ec72c686fd3a
file=docs/eval/manual/SoTayHocVu-HocKy1-NamHoc20252026.pdf
API status=INDEXED
API progress=100
API chunkCount=3417
```

Qdrant audit:

```text
Qdrant points=3417
DB chunks vs Qdrant points=matched by API chunkCount and Qdrant count
normalized_table_row=3160
table_summary=122
text=131
section_summary=2
parent_section_summary=2
payloadsWithCellsJson=3160
table_row_group=0
text_table_like=0
rawLeakTypes=0
```

Backend metrics:

```text
detectedTables=122
normalizedTables=122
failedTables=0
normalizedRows=3160
table_summary=122
rowsWithCellsJson=3160
valuesPreservedCount=15367
valuesDroppedCount=0
droppedLeakyTextChunks=0
suppressedRawChars=15012
suppressedLines=236
tableLikeLinesDropped=222
```

The chunk count is higher than the 23L baseline because the structured path preserves more physical/logical rows instead of collapsing rows through the Markdown bridge. This is acceptable for the ingest fix but needs follow-up retrieval tuning.

DB shell audit could not be run because sandbox escalation for `docker exec` was rejected. API status, backend metrics, and Qdrant parity were used instead.

## KTR3185 Diagnostic Check

Previous bad persisted shape from 23L:

```json
{
  "col_2": "KTR3185 1 2 3 4",
  "col_5": "KTR3273 KTR4015 KTR5022",
  "col_11": "3 5 2"
}
```

Fresh ingest result:

- matching normalized rows are on page 22
- matching `table_id` observed: `tbl_14_39`
- KTR3185 rows do not contain KTR3273, KTR4015, or KTR5022
- number of neighboring identifiers merged into the KTR3185 logical row: 0
- title is still split/imperfect in one curriculum row, but not corrupted by neighboring rows
- Q7 runtime answer recovered the full title and 5 credits from available structured evidence

Page attribution:

```text
old stored page=20
fresh structured row page=22
physical PDF page=22
```

## Q1-Q8 Runtime

Configuration:

```text
temperature=0.2
maxTokens=768
topK=15
Hybrid Search=ON
playgroundDebugSources=true
```

| Question | totalMs | Verdict | Note |
|---|---:|---|---|
| Q1 | 45858 | FAIL | Could not find group 2 even though structured evidence exists in Qdrant. |
| Q2 | 14690 | FAIL | Returned wrong weekday; structured row has weekday 6. |
| Q3 | 23128 | FAIL | Comparison failed because group 2 was missed. |
| Q4 | 9310 | PASS | Registration date range answered correctly. |
| Q5 | 17501 | FAIL | Could not list architecture K46 semester 2 courses. |
| Q6 | 13260 | PASS | Biotechnology K46 semester 2 list answered acceptably. |
| Q7 | 16662 | PASS | Correct full title and 5 credits. |
| Q8 | 5508 | PASS | Correctly refused out-of-scope exchange-rate question. |

Total: 4/8 PASS.

## Tests Run

Focused tests:

```powershell
cd Backend
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
.\mvnw.cmd "-Dtest=NormalizedTableIngestTest,NormalizedTableSuppressionTest,NoHardcodedLexiconInTableNormalizerTest,ChatServiceSourcePresentationTest" test
```

Result:

```text
Tests run: 62, Failures: 0, Errors: 0
```

Targeted regression suite:

```powershell
.\mvnw.cmd "-Dtest=HybridKeywordSearchTest,CellAwareNormalizedRowRetrievalTest,NoHardcodedLexiconInCellAwareScorerTest,NoHardcodedLexiconInTableNormalizerTest,NormalizedTableSuppressionTest,NormalizedTableIngestTest,FinalContextSelectionTest,RetrievalTopKTest,ChatServiceSourcePresentationTest,RagTokenAuditTest,ChatServiceLlmParamsTest,EmbeddingServiceCacheTest,PromptBuilderServiceTest,KeywordIndexCacheTest,KeywordSearchIndexTest" test
```

Result:

```text
Tests run: 145, Failures: 0, Errors: 0
```

Compile:

```text
.\mvnw.cmd -DskipTests compile
```

Result: success.

## Test Coverage Added

- RawTableModel preserves x/y/width/height.
- Structured table path reaches `normalizeRawTable(...)`.
- Spreadsheet and Basic synthetic inputs both use `RawTableModel`.
- A/B/C header mapping is exact.
- Wrapped continuation merges only by x-overlap.
- New identifier blocks merge.
- New row number blocks merge.
- Synthetic multi-identifier corruption prevention.
- Parent/child coordinate header attribution avoids sibling contamination.
- Page attribution preserves physical page number.
- Raw fallback output types remain suppressed.
- No-hardcode audit remains green.

## No-Hardcode Proof

No production rules were added for diagnostic/domain literals. `NoHardcodedLexiconInTableNormalizerTest` passed in both focused and targeted suites.

Additional production search found no forbidden diagnostic literals in table normalizer logic. Existing unrelated Vietnamese comments are still present in production files and were not introduced as matching rules.

## Known Limitations

- Q1, Q2, Q3, and Q5 still fail after fresh ingest.
- Basic fallback was covered by synthetic tests but not triggered by the runtime PDF corpus.
- Some header names still fall back to generic `col_N`.
- Some curriculum title continuations are still split, though neighboring identifiers are no longer merged.
- Coordinate merge rejection counters are defined, but some fine-grained rejection buckets need fuller runtime wiring.
- DB shell audit was blocked by sandbox escalation; API and Qdrant audits were used.
- Commented legacy snippets remain in `DocumentParserService`; they are inactive and should be cleaned up in a follow-up polish task.

## Next Recommended Task

23N should tune coordinate header inference and retrieval/source selection for schedule and curriculum list questions, using the now-structured rows as source of truth. The priority is restoring Q1, Q2, Q3, and Q5 while preserving the KTR3185 neighbor-merge fix and Q7 correctness.
