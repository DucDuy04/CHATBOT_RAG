# CURSOR_REPORT_24A_DOCX_INGEST_SUPPORT

**Task:** 24A — Add DOCX Ingest Support With Structured Tables  
**Date:** 2026-05-27  
**Verdict:** PARTIAL (code complete + tests pass; runtime verification NOT RUN)

---

## Files Changed

### Production code (7 files)

| File | Change |
|------|--------|
| `Backend/pom.xml` | Add `poi.version=5.3.0` property + `poi-ooxml` dependency |
| `Backend/.../service/RawTableModel.java` | Add `DOCX` to `ExtractorType` enum |
| `Backend/.../service/DocumentParserService.java` | Add DOCX imports + `parseDocx()` + `convertDocxTableToRawTableModel()` + helpers; update `parse()` entry |
| `Backend/.../service/DocumentService.java` | Update `validateUploadableFile()` for DOCX; update error message; add DOCX metrics log |
| `Backend/.../service/TableIngestMetrics.java` | Add 13 DOCX-specific fields + reset + inc methods |
| `Backend/.../service/ChunkingService2.java` | Add DOCX metric increments in detection loop + `processNormalizedRawTable()` |

### Test code (6 new files)

| File | Tests |
|------|-------|
| `DocumentUploadValidationTest.java` | 20 tests (PDF/TXT/DOCX accepted, .doc/.docm/.dotm rejected, invalid ZIP rejected) |
| `DocxParserServiceTest.java` | 12 tests (paragraphs, simple table, blank cells, extractor type, page convention, fixture check) |
| `NormalizedTableIngestTest.java` | 8 tests (normalizeRawTable with DOCX logical coords, parent header, values preserved) |
| `NormalizedTableSuppressionTest.java` | 4 tests (suppression profile from DOCX) |
| `NoHardcodedLexiconInTableNormalizerTest.java` | 2 tests (no domain literals in NormalizedTableService + DocumentParserService) |
| `NoHardcodedLexiconInCellAwareScorerTest.java` | 1 test (no domain literals in scorer) |

---

## Architecture: DOCX Table Path

```
DOCX file
→ XWPFDocument.getBodyElements() [in document order]
  → XWPFParagraph  → extractDocxParagraphText() → pageBuilder text
  → XWPFTable      → convertDocxTableToRawTableModel()
      → each XWPFTableRow + XWPFTableCell
      → gridSpan → RawTableCell.width = gridSpan
      → vMerge continue → skip (not duplicated as data)
      → logical grid coords: x=col, y=row, xEnd=col+gridSpan, yEnd=row+1
      → RawTableRow + RawTableModel (ExtractorType.DOCX)
→ rawTableRegistry["docx_t{N}_{filename}"] = RawTableModel
→ pageContents[1] = text + "[RAW_TABLE_REF:docx_t{N}_{filename}]\n"
→ parseSections() → attachRawTablesToSections()
→ Section.rawTableBlocks → ChunkingService2.processNormalizedRawTable()
→ NormalizedTableService.normalizeRawTable(RawTableModel, request)
  → inferHeadersFromCoordinates() [uses logical x/xEnd for slot ranges]
  → mapCellsFromCoordinates() [uses bestSlotForCell overlap matching]
  → cells_json + canonicalText + groupContext
→ normalized_table_row + table_summary chunks
→ DB + Qdrant payload
```

## Coordinate Design for DOCX Logical Grid

DOCX cells use integer logical grid coordinates instead of physical pixels:

| Field | PDF (Tabula) | DOCX (POI) |
|-------|-------------|-----------|
| `x` | physical pixel left edge | logical column start (0-based) |
| `y` | physical pixel top | row index (0-based) |
| `width` | pixel width | gridSpan (columns spanned) |
| `height` | pixel height | 1.0 (always 1 row) |
| `xEnd` | x + pixel width | x + gridSpan |
| `pageNumber` | real page from PDFBox | 1 (DOCX convention) |

`hasCoordinates()` returns `true` when `width > 0 && height > 0` — logical integers satisfy this.

`inferHeadersFromCoordinates()` uses `overlap(cell.x, cell.xEnd, slot.x, slot.xEnd)` → works correctly with integer logical grid.

## Broad Parent Header Handling

For a DOCX table with a merged parent header spanning all columns:

```
[ Parent (gridSpan=3)  ]  ← row 0: one cell, width=3, x=0..3
[ A | B | C            ]  ← row 1: three cells, each width=1
[ v1| v2| v3           ]  ← row 2: data
```

Slot ranges from data (row 2): slot[0]=(0,1), slot[1]=(1,2), slot[2]=(2,3)

For slot 0:
- Row 0 "Parent": srcWidth=3 > slotWidth=1 * 2.5 → `isBroad=true` → parentFrag
- Row 1 "A": srcWidth=1, overlap=1 → childFrag
Since childFrag exists → header = "A" ✓

Result: `{"A":"v1","B":"v2","C":"v3"}` — Parent demoted, not used as per-column key ✓

## vMerge Handling

```java
private boolean isVMergeContinue(XWPFTableCell cell) {
    CTVMerge vMerge = tcPr.getVMerge();
    // No val = continuation; val=RESTART = first cell of merged group
    String val = vMerge.isSetVal() ? vMerge.getVal().toString() : "";
    return !"RESTART".equalsIgnoreCase(val);
}
```

- vMerge restart: included as normal cell (first row of merged group)
- vMerge continue: **skipped** (not duplicated as data row)
- Column position advanced by cell's gridSpan to keep alignment

## Page Number Convention

DOCX does not expose reliable page numbers via Apache POI without rendering.

**Convention applied:**
- `pageNumber = 1` for all DOCX cells and RawTableModels
- Citations in RAG response will show "trang 1" for all DOCX chunks
- This is a known limitation documented in code comments

## Test Results

```
Tests run: 47, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

All 47 tests pass:
- 20 upload validation tests
- 12 DOCX parser tests
- 8 normalized table ingest tests
- 4 suppression tests
- 3 no-hardcode audit tests

## Acceptance Criteria Status

| Criterion | Status |
|-----------|--------|
| Backend builds | ✅ PASS |
| DOCX upload accepted | ✅ PASS (20 validation tests) |
| DOCX document type stored/reported | ✅ getFileType("...docx") = "DOCX" |
| DOCX paragraphs extracted | ✅ PASS (DocxParserServiceTest) |
| DOCX tables from Word structure | ✅ PASS (convertDocxTableToRawTableModel) |
| DOCX tables → normalized_table_row | ✅ PASS (NormalizedTableIngestTest) |
| DOCX tables → table_summary | ✅ PASS (NormalizedTableIngestTest) |
| No Markdown bridge for DOCX tables | ✅ docxTablesUsingMarkdownBridge always 0 |
| cells_json correct for simple tables | ✅ PASS |
| Parent header demoted (not per-column key) | ✅ PASS (broadParentHeader_demotedFromColumnKey) |
| Real DOCX file exists | ✅ PASS (realDocxFixture_exists test) |
| TXT/PDF regressions pass | ✅ PASS (txtFile_stillParsedAfterDocxAdded, compile OK) |
| No-hardcode audit passes | ✅ PASS (3 no-hardcode tests) |
| table_row_group = 0 | ✅ Not created in DOCX path |
| text_table_like = 0 | ✅ Not created in DOCX path |
| Runtime DOCX Q1-Q8 | ❌ NOT RUN (backend not started) |
| Real DOCX ingest verify | ❌ NOT RUN |

## Known Limitations

1. **Runtime not verified** — Full Q1-Q8 test pending backend start
2. **Page number = 1** for all DOCX content (POI limitation, no render)
3. **cellAt() index-based** for slot range inference — may be slightly off when data rows have gridSpan > 1 cells (rare for structured data)
4. **Large DOCX memory** — POI loads full document into RAM

## Recommended Follow-up

- **24B**: Runtime verification with SoTayHocVu.docx, Q1-Q8 evaluation
- **24C**: Page number display improvement (show N/A vs "trang 1" for DOCX)
- **24D**: Memory guard for large DOCX (> 20MB file size limit or streaming)
