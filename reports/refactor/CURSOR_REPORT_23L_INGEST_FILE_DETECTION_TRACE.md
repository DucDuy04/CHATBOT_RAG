# Cursor Report 23L - Ingest / File Detection / Table Detection Trace

## Verdict

Diagnostic complete. No fix implemented.

The malformed KTR3185 source is produced during ingest in normalized table row construction. It is already malformed in `document_chunks.content` and `cells_json`; Qdrant and source rendering preserve that stored data.

## Main Finding

The bad row is caused by the combination of:

- Tabula Spreadsheet extracting a sparse 21-column physical table with duplicate/fragmented KTR3185 rows.
- Production converting Tabula cells to Markdown, dropping x/y/width/height and span information.
- `NormalizedTableService.mergeWrappedRowsWithStats()` merging continuation-looking sparse rows too aggressively.

The merge folds KTR3185 fragments plus neighboring course rows KTR3273, KTR4015, and KTR5022 into one logical row.

## Evidence

Bad DB chunk:

```text
documentId=1d683e73-2ab8-49f0-9ca6-2b0bcc2ffeb4
chunkId=ee06acea-5fad-441d-92ba-0fb2de41079a
chunk_index=33
chunk_type=normalized_table_row
table_id=tbl_14_32
table_name=2. Thi kết thúc học phần
row_index=1
stored page=20
```

Physical PDF page containing the row is page 22. PDFBox raw text has the correct title words but split around the row:

```text
Đồ án kiến trúc công trình tổ hợp đa chức
1 KTR3185 5 x Kiến trúc
năng
```

Tabula still has coordinates at extraction time, but production drops them when building Markdown. The normalizer then sees only sparse Markdown rows.

Qdrant payload for the bad chunk has the same malformed `cells_json` as MySQL and `text_segment` is the stored content with heading/type prepended. `ChatService.toSourceDto()` displays `ctx.getContent()` as `chunkText`, so source hover is not mutating the row.

## File Detection

Upload detection is extension plus MIME allow-list:

- accepted extensions: `.pdf`, `.txt`
- accepted MIME: `application/pdf`, `text/plain*`, `application/octet-stream`, `binary/octet-stream`, or blank
- parser choice: filename extension in `DocumentParserService.parse()`
- no magic-byte sniffing

The PDF uses `parsePdf()`.

## Current Metrics

Current active document aggregate:

```text
normalized_table_row=2490
table_summary=120
text=131
section_summary=2
parent_section_summary=2
rowsWithCellsJson=2490
rowsWithGenericColumnKeys=2474
table_row_group=0
text_table_like=0
```

Latest accepted ingest metrics:

```text
detectedTables=122
normalizedTables=120
failedTables=2
normalizedRows=2490
compactHeaderSuspiciousCount=295
compactHeaderFallbackCount=295
multiColumnHeaderRejectedCount=295
headerFragmentsWithCoordinates=0
headerFragmentsWithoutCoordinates=1487
valuesPreservedCount=18367
valuesDroppedCount=0
```

## Cleaner Row Comparison

Cleaner same-identifier schedule row exists:

```text
chunkId=4ddd44d7-7511-4e48-bbb5-8a87b32521d9
page=94
table=08 E2.01_Xưởng
cells: KTR3185, full title, credits=5
```

The malformed curriculum row outranks/appears first for Q7 because it contains both exact identifier `KTR3185` and the cohort label context, while cleaner timetable rows do not contain the K45 cohort label. Runtime logs show bad row rank 1 with `cellScore=45.50`.

## Code Changed

Production code: none.

Diagnostic-only code added:

- `Backend/src/test/java/KLTN/RAG_CHATBOT_BE/diagnostic/IngestTrace23LDiagnosticTest.java`

Diagnostic output:

- `Backend/target/INGEST_FILE_DETECTION_TRACE_23L_EVIDENCE.md`

Required report:

- `docs/eval/results/INGEST_FILE_DETECTION_TRACE_23L_20260526.md`

## Tests Run

```text
cd Backend
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
.\mvnw.cmd "-Dtest=KLTN.RAG_CHATBOT_BE.diagnostic.IngestTrace23LDiagnosticTest" test
```

Result:

```text
Tests run: 1, Failures: 0, Errors: 0
```

## Recommended Next Fix

Fix the ingest layer. Safest order:

1. Carry Tabula cell coordinates/provenance into a diagnostic/intermediate table model.
2. Add generic continuation-merge guards for new identifiers/row numbers without x-overlap proof.
3. Use coordinate overlap for header attribution.
4. Only then consider retrieval demotion of noisy multi-identifier rows as a secondary safeguard.
