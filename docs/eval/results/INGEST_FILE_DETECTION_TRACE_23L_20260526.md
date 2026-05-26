# Task 23L - Ingest / File Detection / Table Detection Diagnostic

Date: 2026-05-26

## Final Diagnostic Conclusion

The malformed KTR3185 source is created during ingest, before embedding, Qdrant retrieval, prompt construction, or frontend source rendering.

The first confirmed bad persisted chunk is:

- documentId: `1d683e73-2ab8-49f0-9ca6-2b0bcc2ffeb4`
- chunkId: `ee06acea-5fad-441d-92ba-0fb2de41079a`
- chunk_type: `normalized_table_row`
- chunk_index: `33`
- stored page_start/page_end: `20/20`
- physical PDF page containing the row: `22`
- table_id: `tbl_14_32`
- table_name: `2. Thi kết thúc học phần`
- row_index: `1`

Root cause classification:

- Parser receives text in partly interleaved reading order: true for PDFBox raw page text around the wide table. The title line is split, but still recoverable.
- PDF/table extraction loses reliable cell geometry at the normalized-table boundary: true. Tabula has x/y for physical cells, but production converts the table to Markdown and drops coordinates/spans before normalization.
- Table detector groups unrelated physical rows into one detected table: true and expected for the page-wide curriculum table.
- Physical row reconstruction / continuation merge merges too aggressively: true. The KTR3185 title continuation row and neighboring rows KTR3273/KTR4015/KTR5022 are merged into logical row 1.
- Header detector cannot infer exact columns after geometry is dropped: true. It falls back to `col_N` for many columns; this is defensive, but cannot fix already-misaligned values.
- Header attribution falls back correctly, but values are already misaligned: true.
- Context carry injects table/group text: partly true. `group_context` is polluted by header/group fragments, but the main answer corruption is in cell values.
- Qdrant payload serialization/retrieval/source rendering: not root cause. Qdrant payload equals DB content/cells_json; ChatService displays `RetrievedContext.content` unchanged.

Do not fix in this task. The next fix should target parser-to-normalizer table geometry and/or generic logical-row reconstruction, not retrieval.

## Source Files Inspected

- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/api/DocumentController.java`
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/DocumentService.java`
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/DocumentParserService.java`
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/ChunkingService2.java`
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/NormalizedTableService.java`
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/TableIngestMetrics.java`
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/EmbeddingService.java`
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/RagRetrievalService.java`
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/ChatService.java`
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/domain/document/DocumentChunk.java`
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/domain/document/DocumentTable.java`
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/domain/document/DocumentSection.java`
- `Backend/pom.xml`

Recent reports read:

- `docs/eval/results/COMPACT_HEADER_SPAN_ALIGNMENT_23J6C_20260524.md`
- `reports/refactor/CURSOR_REPORT_23J6C_COMPACT_HEADER_SPAN_ALIGNMENT.md`
- `docs/eval/results/RAG_SCORING_RERANK_LATENCY_23K3_20260525.md`
- `reports/refactor/CURSOR_REPORT_23K3_RAG_SCORING_RERANK_LATENCY.md`

Diagnostic-only file added:

- `Backend/src/test/java/KLTN/RAG_CHATBOT_BE/diagnostic/IngestTrace23LDiagnosticTest.java`
- Output was written to `Backend/target/INGEST_FILE_DETECTION_TRACE_23L_EVIDENCE.md`

No production logic was changed.

## Exact Ingest Flow Diagram

```text
DocumentController upload endpoint
  -> DocumentService.uploadAndProcess()
  -> validateUploadableFile()
  -> saveFile()
  -> DocumentService.executeProcessing()
  -> DocumentParserService.parse()
  -> parsePdf() for .pdf
  -> PDFBox PDFTextStripper per page
  -> Tabula SpreadsheetExtractionAlgorithm, then BasicExtractionAlgorithm fallback
  -> convertTableToMarkdown()
  -> suppress raw overlapping table text
  -> parseSections()
  -> ChunkingService2.processSections2()
  -> splitByTableBlocks()
  -> NormalizedTableService.normalize()
  -> parseMarkdownTable()
  -> inferHeaders()/composeHeaderSlots()
  -> mergeWrappedRowsWithStats()
  -> mapCells()
  -> NormalizedTableRow.of()
  -> canonical text + cells_json
  -> DocumentService.saveSections/saveTables/saveChunks()
  -> EmbeddingService.embedAndStore()
  -> Qdrant text_segment + payload
  -> RagRetrievalService.search/re-score/toRetrievedContext()
  -> ChatService.buildSourceDtosForResponse()
  -> frontend source hover displays chunkText
```

## Stage-by-Stage Trace

| Stage | Class/method | Input shape | Output shape | Coordinates? | Page? | Table id/name? | Row index? | Header/raw fragments? | Information dropped |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Upload | `DocumentController` -> `DocumentService.uploadAndProcess` | `MultipartFile` | `Document` row plus saved file | no | no | no | no | no | original bytes saved; no sniffed file signature |
| File validation | `DocumentService.validateUploadableFile` | filename + `MultipartFile.getContentType()` | accepted/rejected | no | no | no | no | no | no magic-byte/content sniffing |
| File type | `DocumentService.getFileType` | original filename | `PDF`/`TXT`/other string | no | no | no | no | no | extension only |
| Parser selection | `DocumentParserService.parse` | original filename | `parsePdf` or `parseTxt` | no | no | no | no | no | MIME not used for parser choice |
| Raw PDF text | `parsePdf` + `PDFTextStripper` | PDF bytes/page | plain page text | internal positions only, not returned | physical page loop exists | no | no | raw lines preserved temporarily | coordinates discarded immediately |
| Table detection | `parsePdf` + Tabula Spreadsheet/Basic | `technology.tabula.Page` | `List<Table>` | yes inside `RectangularTextContainer` | page loop exists | no stable id yet | physical rows only | physical cells exist | production later converts to Markdown |
| Table markdown | `convertTableToMarkdown` | Tabula `Table` | Markdown table string | dropped | page still wrapper context only | no | physical row order only | header text only | x/y, width/height, spans, cell object identity |
| Section detection | `parseSections` | page text with `[TABLE_START]` blocks | `List<Section>` | no | section start/end | no | no | no | physical page of individual table rows can become section page |
| Chunk table split | `ChunkingService2.splitByTableBlocks` | section content | table segments | no | section page | generated table_id later | no | Markdown only | no geometry |
| Header detection | `NormalizedTableService.inferHeaders` | parsed Markdown rows | header list + attribution stats | no | request page only | table name from section | no | raw header strings in local slots | attribution slots not persisted |
| Physical -> logical | `mergeWrappedRowsWithStats` | Markdown rows after header | merged logical row arrays | no | request page only | table request only | no | raw rows not persisted | merge provenance not persisted |
| cells_json | `mapCells` + `NormalizedTableRow.of` | headers + logical row cells | ordered JSON object | no | row pageStart/pageEnd | tableName | rowCounter | no | source physical rows/columns lost |
| Persistence | `DocumentService.saveChunks` | record chunks | `document_chunks` | no | columns persisted | tableId/name persisted | rowIndex persisted | cells_json persisted | no raw fragments/coords |
| Embedding payload | `EmbeddingService.embedAndStore` | DB chunks | Qdrant `text_segment` + payload | no | payload persisted | payload persisted | payload persisted | cells_json persisted | no mutation except heading/type prepended to text_segment |
| Retrieval | `RagRetrievalService.toRetrievedContext` | DB chunks after scoring | DTO context | no | copied | copied | copied | copied | no mutation |
| Source display | `ChatService.toSourceDto` | `RetrievedContext` | `SourceDto.chunkText` | no | page range string | no table metadata in source DTO | no | no | displays stored content only |

## File Type Detection Details

`DocumentService.validateUploadableFile()` accepts only `.pdf` and `.txt` extensions. It also allows MIME values `application/pdf`, `text/plain*`, `application/octet-stream`, and `binary/octet-stream`; blank MIME is allowed. It does not inspect magic bytes.

`DocumentService.getFileType()` stores the type using filename suffix. It can return `DOCX`/`DOC`, but validation rejects those uploads. `DocumentParserService.parse()` selects parser path by `file.getOriginalFilename().toLowerCase().endsWith(".pdf")` or `.txt`; MIME is not used.

For `SoTayHocVu-HocKy1-NamHoc20252026.pdf`, parser path is `parsePdf()`.

## PDF Extraction Capability Summary

Dependencies:

- PDFBox `3.0.2`
- Tabula `1.0.5`

`PDFTextStripper` is configured with:

- `setSortByPosition(true)`
- `setWordSeparator(" ")`
- `setLineSeparator("\n")`
- one physical page at a time

Tabula extraction:

- `SpreadsheetExtractionAlgorithm` first
- if no usable spreadsheet table, or zero usable tables, `BasicExtractionAlgorithm` fallback
- `RectangularTextContainer` contains x/y/width/height at this stage
- production `convertTableToMarkdown()` calls `cell.getText()` and drops the coordinate fields

Current normalized-table model has no coordinates, no x-overlap, no cell spans, and no physical-cell ids.

## Table Detection Flow

`DocumentParserService.parsePdf()` runs Tabula per page. `isUsableTable()` accepts tables with at least 3 rows, at least 2 columns, enough non-empty/text cells, and a usable inferred header. Sparse-but-structured tables can pass.

If Tabula spreadsheet extraction finds usable tables, raw-text pseudo-table detection is skipped for that page. For the problematic page, SpreadsheetExtractionAlgorithm finds many tables and the large page-wide curriculum table is accepted.

Physical PDF page evidence:

- PDF pages containing KTR3185/title: `[22, 97, 98, 99, 100, 101]`
- The stored malformed chunk says `Trang: 20`, but diagnostic extraction shows the curriculum row is on physical PDF page 22. This is a page attribution limitation caused by section/table page propagation, not the root value corruption.
- On physical page 22, SpreadsheetExtractionAlgorithm found `12` tables; table 1 had `43` rows and `21` max columns and contained KTR3185.
- BasicExtractionAlgorithm found one single-column page table; production keeps Spreadsheet output because it is usable.

## Header Detection Flow

There are two header passes:

1. `DocumentParserService.inferMarkdownHeader()` creates a Markdown header before normalization.
2. `NormalizedTableService.inferHeaders()` re-inferrs headers from Markdown rows and runs `composeHeaderSlots()`.

The page-22 table header is physically multi-line and sparse. Tabula emits many zero-sized blank placeholders and fragmented header cells. The normalizer sees only Markdown columns, not x/y. 23J6C added generic fallback: suspicious compact/multi-label header keys become `col_N` rather than domain-specific keys.

This prevents bad keys, but not bad values. In the malformed row, header attribution is mostly defensive (`col_2`, `col_4`, `col_5`, etc.); the cell values are already merged into the wrong logical row.

## Logical Row Reconstruction Flow

`NormalizedTableService.mergeWrappedRowsWithStats()` merges a row into the previous row when `isContinuationRow()` says:

- first cell is blank and previous row has content,
- row has fewer columns and begins blank,
- non-empty count is small and row is narrower,
- first fragment looks wrapped.

This is too aggressive for the Tabula sparse grid on physical page 22. It merges:

- KTR3185 base row,
- duplicated KTR3185 row emitted by Tabula,
- title continuation fragment `năng`,
- neighboring rows KTR3273, KTR4015, KTR5022 into later columns of the same logical row.

## KTR3185 Stage Evidence

### Stage A - Raw Extracted Page Text

Raw PDFBox physical page 22 has the correct semantic information, but line order is table-like and split:

```text
Khóa, ngành: Kiến trúc K45
Đồ án kiến trúc công trình tổ hợp đa chức
1 KTR3185 5   x Kiến trúc
năng
2 KTR3273 Đồ án Bảo tồn kiến trúc 3   x Kiến trúc
3 KTR4015 Thực tập tốt nghiệp 5   x Kiến trúc
4 KTR5022 Chuyên đề kiến trúc 2   x Kiến trúc
```

Conclusion: raw text preserves the title words, but the first title line appears before the row and the final word appears after the row. Columns are already reading-order interleaved.

### Stage B - Detected Table Model

Tabula Spreadsheet table:

- physical PDF page: 22
- table index in diagnostic: Spreadsheet table 1
- rows: 43
- max columns: 21
- x/y coordinates: available in `RectangularTextContainer`
- cells: separate physical cell objects before Markdown conversion

Critical physical rows:

```text
row 5: col1 text='hóa, ngành: Kiến trúc K45'
row 6: col0='1', col1='KTR3185', col3='ồ án kiến trúc công trình tổ hợp đa chức', col5='5', col7='x', col8='Kiến trúc'
row 7: col1='1', col2='KTR3185', col3='5', col5='x', col6='iến trúc'
row 8: col7='ăng'
row 9: col1='2', col4='KTR3273', col7='ồ án Bảo tồn kiến trúc', col10='3', col16='x', col19='iến trúc'
row 10: col1='3', col4='KTR4015', col7='hực tập tốt nghiệp', col10='5', col16='x', col19='iến trúc'
row 11: col1='4', col4='KTR5022', col7='huyên đề kiến trúc', col10='2', col16='x', col19='iến trúc'
```

The first letters missing in some Tabula cells (`ồ`, `ăng`, `iến`) are extraction artifacts from split glyph/cell geometry.

### Stage C - Header Candidates

Raw header rows in Markdown approximation:

```markdown
| TT | Mã học phần | Tên học phần | TC HK1 | TC HK2 |  | HP |  |  | Khoa/Trường | ... |
|  |  |  |  | Mã | TC | TC | ... |
|  |  | Tên học phần | bắt | phụ trách | ... |
|  |  |  |  | học phần | HK | HK2 | ... |
|  |  |  |  |  |  |  | ... | buộ | chuyên môn | ... |
```

After 23J6C, suspicious headers fall back to generic keys. In the live DB row, the selected keys include:

```text
TT, col_2, Tên học phần, col_4, col_5, col_6, col_7, col_8, col_9,
Khoa/Trường, col_11, ..., buộ, chuyên môn, col_20, col_21
```

Reason for `col_N` fallback: structural ambiguity and multi-fragment/compact header candidates without coordinates. Metrics confirm `headerFragmentsWithCoordinates=0`, `headerFragmentsWithoutCoordinates=1487`.

### Stage D - Physical Row Before Logical Repair

The physical row evidence above shows KTR3185 already split across multiple physical rows, but not yet semantically collapsed with KTR3273/KTR4015/KTR5022. This means the data is still recoverable if coordinates and row provenance are retained.

### Stage E - Logical Row After Repair

Diagnostic normalizer reproduction from the Tabula Markdown generated this KTR3185 logical row:

```text
Dòng: 1.
TT: 1.
col_2: KTR3185 1 2 3 4.
col_3/Tên học phần: KTR3185.
col_4: ồ án kiến trúc công trình tổ hợp đa chức 5.
col_5: KTR3273 KTR4015 KTR5022.
col_6: 5 x.
col_8: x ăng ồ án Bảo tồn kiến trúc hực tập tốt nghiệp huyên đề kiến trúc.
col_11: 3 5 2.
col_17/chuyên môn: x x x.
col_20: iến trúc iến trúc iến trúc.
```

This matches the live malformed DB/Qdrant row. Therefore the corruption point is `NormalizedTableService.mergeWrappedRowsWithStats()` after Markdown conversion, with upstream Tabula sparse rows as the enabling condition.

### Stage F - Persistence and Qdrant

DB `document_chunks.content` equals final canonical content. DB `cells_json` equals the malformed cell map. Qdrant payload for chunk `ee06acea-5fad-441d-92ba-0fb2de41079a` contains the same `cells_json`; `text_segment` is the same content with heading/type prepended by `EmbeddingService.buildEmbeddingText()`.

`RagRetrievalService.toRetrievedContext()` copies DB fields. `ChatService.toSourceDto()` sets source hover `chunkText` from `ctx.getContent()`. No retrieval/source-render mutation was found.

## Matching Rows / Chunks

Active document:

- documentId: `1d683e73-2ab8-49f0-9ca6-2b0bcc2ffeb4`
- widgetId: `3299b4d1-9805-4473-a878-c3ab5d775bba`
- file: `SoTayHocVu-HocKy1-NamHoc20252026.pdf`
- chunkCount: `2745`

KTR3185 matching normalized rows:

| chunk_index | chunkId | page | table_id | table_name | row_index | Cleanliness |
| ---: | --- | ---: | --- | --- | ---: | --- |
| 33 | `ee06acea-5fad-441d-92ba-0fb2de41079a` | 20 | `tbl_14_32` | `2. Thi kết thúc học phần` | 1 | malformed curriculum row |
| 2057 | `4ddd44d7-7511-4e48-bbb5-8a87b32521d9` | 94 | `tbl_20_2049` | `08 E2.01_Xưởng` | 8 | clean schedule row, group 1 |
| 2066 | `7cb17a49-69d4-43a3-8fe5-d57c41ca30db` | 94 | `tbl_20_2064` | `08 E2.01_Xưởng` | 2 | noisy schedule row, group 2 |
| 2067 | `a97f8325-7607-40d0-bef1-8fce50eef243` | 94 | `tbl_20_2064` | `08 E2.01_Xưởng` | 3 | partly noisy schedule row, group 3 |
| 2072 | `002c7b5b-a12e-4c39-b5e0-c9670c0d551e` | 94 | `tbl_20_2070` | `08 E2.01_Xưởng` | 2 | noisy schedule row, group 4 |
| 2073 | `5c917783-c0cf-42e3-91fa-cdf9551f517b` | 94 | `tbl_20_2070` | `08 E2.01_Xưởng` | 3 | partly noisy schedule row, group 5 |
| 2079 | `49af84d8-1d67-4ed4-bfb4-8f961c6c48c4` | 94 | `tbl_20_2077` | `08 E2.01_Xưởng` | 2 | noisy schedule row, group 6 |
| 2080 | `91ec95ab-b5d4-47ac-a2e4-c76b917816f7` | 94 | `tbl_20_2077` | `08 E2.01_Xưởng` | 3 | partly clean schedule row, group 7 |
| 2086 | `74155d57-4f00-4501-8dfd-05a96c89012b` | 94 | `tbl_20_2084` | `08 E2.01_Xưởng` | 2 | noisy schedule row, group 8 |

Malformed row stored cells_json:

```json
{"TT":"1","col_2":"KTR3185 1 2 3 4","Tên học phần":"KTR3185","col_4":"ồ án kiến trúc công trình tổ hợp đa chức 5","col_5":"KTR3273 KTR4015 KTR5022","col_6":"5 x","col_7":"iến trúc","col_8":"x ăng ồ án Bảo tồn kiến trúc hực tập tốt nghiệp huyên đề kiến trúc","col_9":"Kiến trúc","Khoa/Trường":"","col_11":"3 5 2","col_12":"","col_13":"","col_14":"","col_15":"","buộ":"","chuyên môn":"x x x","col_18":"","col_19":"","col_20":"iến trúc iến trúc iến trúc","col_21":""}
```

Cleaner row example:

```json
{"STT":"461","col_2":"KTR3185","col_3":"Đồ án kiến trúc công trình tổ hợp đa chức năng - Nhóm 1","col_4":"5","col_5":"0","col_6":"Nguyễn Văn Thái","col_7":"17/11/2025","Thứ":"2","Tiết học":"5-8","Phòng":"E2.01_Xưởng kiến trúc 1-1","col_11":""}
```

## Neighboring Row Evidence

Same table `tbl_14_32`:

- table_summary chunk_index `32`: 28 normalized rows; columns include `TT, col_2, Tên học phần, col_4, ... chuyên môn, col_20`.
- row 1 chunk_index `33`: malformed KTR3185 row.
- rows 2-6 chunk_indexes `34-38`: KQH3102, KTR3103, KTR3232, KTR3312, KTR3322; these rows are cleaner but inherit polluted context `1 KTR3319 hóa, ngành: Kiến trúc K46 Đồ án tốt nghiệp 10 x Kiến trúc`.
- rows 7-10 chunk_indexes `39-42`: KTR4014, KTR4022, KTR4031, LLCTTT2; cleaner row values.

There are no previous normalized rows in the same table before row 1; previous chunk is the table summary.

## Comparison With Cleaner Same-Identifier Rows

Malformed curriculum row:

- identifiers in row: 4 (`KTR3185`, `KTR3273`, `KTR4015`, `KTR5022`)
- non-empty cells: 12
- generic `col_N` keys: 14 of 21 keys
- title completeness: split/truncated; `Tên học phần` is incorrectly `KTR3185`; title fragment lacks final `năng`
- credit value: present as `5` but attached to `col_4` fragment and `col_6`
- unrelated tokens: high; neighboring course codes, titles, credits, x marks, department labels
- page/source type: curriculum table, stored page 20, physical page 22

Cleanest schedule row chunk `2057`:

- identifiers in row: 1
- non-empty cells: 10
- generic `col_N` keys: 6 of 11 keys
- title completeness: full title plus group suffix
- credit value: `col_4=5`
- unrelated tokens: low
- page/source type: timetable table, stored page 94

Why malformed row appears before cleaner row in Q7 after 23K3:

- It contains both exact identifier `KTR3185` and the query cohort label from `group_context`.
- The cleaner timetable rows contain `KTR3185` and the title/credits, but not `Kiến trúc K45`.
- Runtime logs show the malformed curriculum row ranked first with `cellScore=45.50`, final score `1.9588`; cleaner rows were retrieved but did not outrank the exact identifier + cohort context row.
- Final prompt order is document order after score selection, so the early curriculum row appears first among selected sources.

## Metrics

Current active DB aggregate:

```text
normalized_table_row: 2490
table_summary: 120
text: 131
section_summary: 2
parent_section_summary: 2
rowsWithCellsJson: 2490
rowsWithGenericColumnKeys: 2474
table_row_group: 0
text_table_like: 0
distinctTables/tableSummaries: 120/120
```

Latest accepted ingest metrics from 23J6C fresh ingest:

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

Malformed-row-specific metrics:

```text
identifiers in row: 4
non-empty cells: 12
generic col_N keys: 14
average non-empty value length: about 20 chars
values that look truncated: at least 5
values containing multiple unrelated tokens: at least 6
neighboring rows merged into logical row: at least 3 neighboring course rows plus continuation fragments
```

## What Data Is Available To Improve Header Mapping Later

Available before current Markdown conversion:

- physical page number
- Tabula table index
- physical row index
- physical column index
- cell text
- x/y/width/height from `RectangularTextContainer`
- rough cell boundaries from Tabula

Unavailable after current conversion:

- x/y/width/height
- header/data cell overlap
- multi-row header span relation
- physical row provenance for logical rows
- continuation merge provenance
- true PDF page for row-level chunks when section/page offsets differ
- raw row fragments in persisted `DocumentChunk`

Missing data needed for exact header mapping:

- per-cell x-range and y-range propagated into normalized table model
- header slot x-range and data cell x-overlap
- row baseline/y clustering before logical repair
- physical-row ids included in logical row repair diagnostics
- span/merged-cell metadata or at least detected zero-sized placeholder handling

## Recommended Fix Options Ranked By Safety

1. Diagnostic-friendly table model: carry `cellText`, physical row/column, x/y/width/height from Tabula into a non-production-normalized intermediate model before Markdown. Low behavior risk if initially diagnostic-only.
2. Generic row merge guard: prevent continuation merge from appending rows that introduce new strong identifiers or row numbers into an existing logical row unless x/y overlap proves they are continuations. Medium risk, targeted at the observed corruption.
3. Coordinate-based header attribution: map data cells to headers by x-overlap/center distance rather than Markdown column index. Higher implementation scope but best long-term correctness.
4. Persist optional diagnostic provenance fields for normalized rows, behind a non-user-facing debug path. Useful for future audits.
5. Retrieval-only demotion of noisy multi-identifier rows. Lowest ingest risk but treats symptoms; should not be the primary fix.

## Tests / Commands Run

Diagnostic helper:

```text
cd Backend
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
.\mvnw.cmd "-Dtest=KLTN.RAG_CHATBOT_BE.diagnostic.IngestTrace23LDiagnosticTest" test
```

Result:

```text
Tests run: 1, Failures: 0, Errors: 0
```

The first run failed due to a diagnostic test compile/write issue; it was fixed in the diagnostic-only test. No production code changed, so the large targeted production regression suite was not required by the task instructions.

## Known Limitations

- Diagnostic output in the terminal showed encoding mojibake in some places, but DB/Qdrant values preserve the same underlying UTF-8 strings used by the app.
- The diagnostic helper approximates production `convertTableToMarkdown()` but does not invoke its private method directly. The reproduced logical corruption matches the persisted DB/Qdrant row closely enough to localize the failure.
- Fresh ingest logs were not present in the latest container tail; metrics are taken from 23J6C accepted reports plus current DB aggregate counts.
- Physical PDF page 22 vs stored page 20 needs a separate page attribution audit; it is related but not the primary KTR3185 value corruption.
