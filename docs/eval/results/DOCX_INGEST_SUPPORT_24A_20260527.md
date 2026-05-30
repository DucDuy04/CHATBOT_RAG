# DOCX_INGEST_SUPPORT_24A_20260527

**Task:** 24A — Add DOCX Ingest Support With Structured Tables  
**Date:** 2026-05-27  
**Verdict:** **PARTIAL** (code complete, runtime verification NOT RUN — backend not started)

---

## 1. Mức độ hiểu task

- Hiểu task: **95%**
- Chắc chắn: cấu trúc parser, RawTableModel logical coordinates, validation DOCX, tests, metrics
- Giả định: runtime quality so sánh PDF vs DOCX phụ thuộc vào cấu trúc thật của file SoTayHocVu.docx — chưa chạy production
- Thiếu dữ kiện: Q1-Q8 runtime chưa được run (backend chưa start)

---

## 2. Tóm tắt yêu cầu

Thêm hỗ trợ file `.docx` là định dạng ingest đầu tiên, bao gồm:
- Parse paragraphs/headings từ cấu trúc Word thật
- Parse tables thành `RawTableModel` với logical grid coordinates (gridSpan, vMerge)
- Normalize qua pipeline `NormalizedTableService.normalizeRawTable()` giống PDF
- Không dùng Markdown bridge cho DOCX tables
- Upload validation cho .docx (MIME + OOXML signature)
- Reject .doc/.docm/.dotm
- Metrics DOCX-specific
- Tests synthetic + real fixture check

---

## 3. Hiện trạng trước khi sửa

- `DocumentParserService.parse()` chỉ hỗ trợ `.pdf` và `.txt`
- `validateUploadableFile()` reject tất cả file không phải PDF/TXT
- `RawTableModel.ExtractorType` chỉ có `SPREADSHEET` và `BASIC`
- Không có Apache POI dependency
- Không có DOCX-specific metrics trong `TableIngestMetrics`

---

## 4. Nguyên nhân gốc

Thiếu parser path cho DOCX. Cần thêm:
1. Dependency Apache POI poi-ooxml 5.3.0
2. ExtractorType.DOCX
3. `parseDocx()` method với `XWPFDocument` → paragraphs + tables
4. `convertDocxTableToRawTableModel()` với logical grid coordinates
5. Validation cập nhật
6. Metrics

---

## 5. Chiến lược sửa

- **Minimal diff**: thêm `parseDocx()` và các helpers mà không sửa PDF/TXT path
- **Logical grid coordinates** cho DOCX: x=logicalCol, width=gridSpan, y=rowIndex, height=1.0 — tương thích với `inferHeadersFromCoordinates()` của NormalizedTableService
- **vMerge continue**: skip các continuation cell (không duplicate data)
- **gridSpan**: một RawTableCell với width=N (không duplicate N lần)
- **Page convention**: pageNumber=1 (không fabricate page numbers)
- Reuse `parseSections()` + `attachRawTablesToSections()` cho DOCX

---

## 6. Danh sách file đã đọc

| File | Đọc để làm gì | Kết luận |
|------|---------------|----------|
| `pom.xml` | Kiểm tra dependencies hiện có | Chưa có POI, cần thêm poi-ooxml 5.3.0 |
| `DocumentParserService.java` | Hiểu entry point parse(), parsePdf(), parseTxt() | Cần thêm parseDocx() sau parseTxt() |
| `DocumentService.java` | Hiểu validateUploadableFile(), getFileType(), metrics log | Cần update validation và thêm DOCX log |
| `RawTableModel.java` | Hiểu ExtractorType và record fields | Cần thêm DOCX enum value |
| `RawTableCell.java` | Hiểu coordinate fields (x, y, width, height, xEnd, yEnd) | Logical integers work as coordinates |
| `NormalizedTableService.java` | Hiểu inferHeadersFromCoordinates(), cellAt(), bestSlotForCell() | Logical grid coordinates compatible |
| `TableIngestMetrics.java` | Hiểu cấu trúc metrics hiện tại | Cần thêm DOCX-specific fields và inc methods |
| `ChunkingService2.java` | Hiểu nơi xử lý RAW_TABLE_REF và normalizeRawTable | Cần thêm DOCX metric increments |
| `Section.java` | Hiểu rawTableBlocks field | Không cần sửa |
| `DocumentChunk.java` | Hiểu cells_json, group_context fields | Không cần sửa — đã có sẵn |
| `BytesMultipartFile.java` | Support class cho tests | Reuse in test |
| Latest eval reports | Hiểu context 23P2 | Baseline đã verified PDF |

---

## 7. Danh sách file đã sửa

| File | Sửa gì | Ảnh hưởng lớp |
|------|--------|---------------|
| `pom.xml` | Thêm `<poi.version>5.3.0</poi.version>` + `poi-ooxml` dependency | config/deploy |
| `RawTableModel.java` | Thêm `DOCX` vào `ExtractorType` enum | service |
| `DocumentParserService.java` | Thêm `parseDocx()`, `convertDocxTableToRawTableModel()`, DOCX helpers; update `parse()` entry point | service |
| `DocumentService.java` | Update `validateUploadableFile()` để accept .docx + OOXML signature check; reject .doc/.docm/.dotm; update `UNSUPPORTED_UPLOAD_FILE_MSG`; thêm DOCX metrics log | api/service |
| `TableIngestMetrics.java` | Thêm 13 DOCX-specific fields, reset, inc methods | service |
| `ChunkingService2.java` | Thêm DOCX metric increments trong `processNormalizedRawTable()` và detection loop | service |

**Test files mới:**
| File | Mục đích |
|------|----------|
| `DocumentUploadValidationTest.java` | 20 tests: PDF/TXT/DOCX accepted, .doc/.docm/.dotm rejected, invalid ZIP rejected |
| `DocxParserServiceTest.java` | 12 tests: paragraph extraction, simple table, blank cells, extractor type, page convention, fixture existence |
| `NormalizedTableIngestTest.java` | 8 tests: normalizeRawTable với DOCX logical coords, parent header demotion, values preserved |
| `NormalizedTableSuppressionTest.java` | 4 tests: suppression profile from DOCX tables |
| `NoHardcodedLexiconInTableNormalizerTest.java` | 2 tests: audit NormalizedTableService + DocumentParserService không có domain literals |
| `NoHardcodedLexiconInCellAwareScorerTest.java` | 1 test: audit CellAwareTableRowScorer |

---

## 8. Diff thay đổi

### pom.xml

```diff
+ <poi.version>5.3.0</poi.version>

+ <!-- Apache POI — đọc file DOCX -->
+ <dependency>
+     <groupId>org.apache.poi</groupId>
+     <artifactId>poi-ooxml</artifactId>
+     <version>${poi.version}</version>
+ </dependency>
```

### RawTableModel.java

```diff
  public enum ExtractorType {
      SPREADSHEET,
-     BASIC
+     BASIC,
+     DOCX
  }
```

### DocumentParserService.java (thêm imports + parse entry + parseDocx)

```diff
+ import org.apache.poi.xwpf.usermodel.XWPFDocument;
+ import org.apache.poi.xwpf.usermodel.XWPFParagraph;
+ import org.apache.poi.xwpf.usermodel.XWPFTable;
+ import org.apache.poi.xwpf.usermodel.XWPFTableCell;
+ import org.apache.poi.xwpf.usermodel.XWPFTableRow;
+ import org.apache.poi.xwpf.usermodel.IBodyElement;
+ import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcPr;
+ import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTVMerge;

  // parse() entry:
- if (fileName.toLowerCase().endsWith(".pdf")) {
+ String lowerName = fileName.toLowerCase();
+ if (lowerName.endsWith(".pdf")) {
      return parsePdf(file);
- } else if (fileName.toLowerCase().endsWith(".txt")) {
+ } else if (lowerName.endsWith(".txt")) {
      return parseTxt(file);
+ } else if (lowerName.endsWith(".docx")) {
+     return parseDocx(file);
  } else {
-     throw new IllegalArgumentException("Chỉ hỗ trợ file PDF và TXT...");
+     throw new IllegalArgumentException("Chỉ hỗ trợ file PDF, TXT và DOCX...");
  }

+ // New methods: parseDocx(), extractDocxParagraphText(), convertDocxTableToRawTableModel(),
+ // extractDocxCellText(), getGridSpan(), isVMergeContinue(), sanitizeForId()
```

### DocumentService.java (validation)

```diff
- public static final String UNSUPPORTED_UPLOAD_FILE_MSG =
-         "Định dạng file chưa được hỗ trợ. Hiện chỉ hỗ trợ PDF và TXT.";
+ public static final String UNSUPPORTED_UPLOAD_FILE_MSG =
+         "Định dạng file chưa được hỗ trợ. Hiện chỉ hỗ trợ PDF, TXT và DOCX.";

  static void validateUploadableFile(MultipartFile file) {
      ...
-     if (!lower.endsWith(".pdf") && !lower.endsWith(".txt")) {
+     boolean isPdf  = lower.endsWith(".pdf");
+     boolean isTxt  = lower.endsWith(".txt");
+     boolean isDocx = lower.endsWith(".docx");
+
+     if (lower.endsWith(".doc") || lower.endsWith(".docm") || lower.endsWith(".dotm")) {
+         throw new IllegalArgumentException("Định dạng .doc/.docm/.dotm không được hỗ trợ...");
+     }
+     if (!isPdf && !isTxt && !isDocx) {
          throw new IllegalArgumentException(UNSUPPORTED_UPLOAD_FILE_MSG);
      }
+     // MIME check: thêm DOCX MIME type
+     boolean ok = m.equals("application/pdf") || m.startsWith("text/plain")
+             || m.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
+             || m.equals("application/octet-stream") || m.equals("binary/octet-stream");
+
+     // OOXML ZIP signature check for .docx
+     if (isDocx) {
+         byte[] header = file.getBytes();
+         if (header[0] != 0x50 || header[1] != 0x4B || header[2] != 0x03 || header[3] != 0x04) {
+             throw new IllegalArgumentException("File .docx không hợp lệ...");
+         }
+     }
  }
```

### TableIngestMetrics.java

```diff
+ // DOCX-specific fields
+ private int docxTablesDetected;
+ private int docxTablesNormalized;
+ private int docxTablesUsingMarkdownBridge;
+ private int docxTablesUsingRawTableModel;
+ private int docxTableRowsNormalized;
+ // ... + 8 more fields

+ // inc methods:
+ public void incDocxTablesDetected()            { docxTablesDetected++; }
+ public void incDocxTablesNormalized()          { docxTablesNormalized++; }
+ public void incDocxTablesUsingRawTableModel()  { docxTablesUsingRawTableModel++; }
+ // ...
```

### ChunkingService2.java

```diff
  // In processSections2 loop (RAW_TABLE_REF detection):
+ if (block.table().extractorType() == RawTableModel.ExtractorType.DOCX) {
+     lastIngestMetrics.incDocxTablesDetected();
+ }

  // In processNormalizedRawTable:
+ if (rawTable.extractorType() == RawTableModel.ExtractorType.DOCX) {
+     lastIngestMetrics.incDocxTablesNormalized();
+     lastIngestMetrics.addDocxTableRowsNormalized(result.rows().size());
+     lastIngestMetrics.incDocxTablesUsingRawTableModel();
+ }
```

---

## 9. Ảnh hưởng sau sửa

**Thay đổi:**
- `.docx` files được chấp nhận upload và ingest
- DOCX tables parsed từ Word table structure (không qua Markdown bridge)
- DOCX normalized rows sử dụng logical grid coordinates
- `docxTablesUsingMarkdownBridge` = 0 luôn (không có Markdown bridge path)

**Giữ nguyên:**
- PDF ingest path không thay đổi
- TXT ingest path không thay đổi
- API contract không thay đổi
- DB schema không thay đổi
- Qdrant schema không thay đổi
- normalized_table_row chunk type giữ nguyên
- table_summary chunk type giữ nguyên
- table_row_group KHÔNG được tạo ra
- text_table_like KHÔNG được tạo ra

**Memory/CPU impact:**
- POI `XWPFDocument` load toàn bộ file vào RAM
- Với file 100-200 trang DOCX: ~30-80MB heap usage tạm thời
- Production với 1.5GB RAM: cần giám sát nếu file lớn
- Parsing speed: POI nhanh hơn PDFBox cho structured content

---

## 10. Edge cases đã xem xét

| Edge case | Xử lý |
|-----------|-------|
| `.doc` file | Rejected với message rõ ràng |
| `.docm` / `.dotm` | Rejected trước khi mở file |
| Invalid ZIP signature | Checked trong validateUploadableFile |
| Empty DOCX | validateUploadableFile rejects empty file |
| DOCX với 0 tables | Parser vẫn extract paragraphs, trả về sections text |
| DOCX với vMerge continue cells | Skip (không duplicate data) |
| DOCX với gridSpan > 1 | Single cell với width=gridSpan |
| Empty cells trong table | Text = "", không shift columns |
| Nested paragraphs trong cell | Join với space |
| Page number | Convention = 1 (không fabricate) |
| null fileName | `parse()` throws IllegalArgumentException |
| Corrupt OOXML | POI throws IOException caught in service |

---

## 11. Kết quả kiểm tra

| Command | Kết quả | Ghi chú |
|---------|---------|---------|
| `.\mvnw.cmd -DskipTests compile` | **PASS** | 0 errors, 1 warning (deprecated annotation unrelated to this task) |
| `.\mvnw.cmd -Dtest=DocumentUploadValidationTest,...` | **PASS** | 47/47 tests pass |
| Backend runtime / Docker | **NOT RUN** | Backend không start trong task này |
| Real DOCX ingest | **NOT RUN** | Runtime verification pending |
| Q1-Q8 DOCX runtime | **NOT RUN** | Pending runtime |
| Docker compose config | **NOT RUN** | |

**Test breakdown:**
- `DocumentUploadValidationTest`: 20 tests PASS
- `DocxParserServiceTest`: 12 tests PASS
- `NoHardcodedLexiconInCellAwareScorerTest`: 1 test PASS
- `NoHardcodedLexiconInTableNormalizerTest`: 2 tests PASS
- `NormalizedTableIngestTest`: 8 tests PASS
- `NormalizedTableSuppressionTest`: 4 tests PASS

---

## 12. So sánh DOCX vs PDF

**Chưa thể so sánh runtime** vì backend chưa start. Dự kiến:

| Tiêu chí | PDF (23P2 baseline) | DOCX (24A) |
|----------|---------------------|------------|
| Table structure source | Tabula layout detection | Word XML table structure (exact) |
| cells_json key quality | Có col_N do header alignment fail | Dự kiến ít col_N hơn (logical grid exact) |
| Header mapping | Coordinate-based, có noise | Logical grid chính xác, ít noise |
| gridSpan handling | Tabula cần infer từ pixel | POI exposes gridSpan directly |
| vMerge handling | Không có | vMerge continue cells được skip |
| group_context | Từ row-group detection | Từ vMerge restart cells |
| Page number | Real page từ PDFBox | 1 (convention, không fabricate) |

---

## 13. Rủi ro còn lại

1. **Runtime chưa verify**: Real DOCX ingest chưa được test với SoTayHocVu.docx
2. **Memory với large DOCX**: POI load toàn bộ file → cần test với file lớn (>50MB)
3. **vMerge edge cases**: Một số DOCX dùng vMerge không theo spec chuẩn → có thể bị missed
4. **Page number = 1 convention**: Citation trong RAG sẽ không có page number — có thể nhầm lẫn với "trang 1"
5. **Q1-Q8 DOCX quality**: Chưa verify schedule header mapping với real SoTay DOCX
6. **Large tables với many spans**: `inferHeadersFromCoordinates` dùng `cellAt(row, col)` index-based — span cells trong data rows có thể gây slot range offset

---

## 14. Đề xuất tiếp theo

1. **24B**: Runtime verify với real SoTayHocVu.docx — run Q1-Q8
2. **24C**: Fix page convention display — thay "trang 1" bằng "N/A" hoặc omit page citation cho DOCX
3. **24D**: Handle large DOCX memory safety — stream-based parsing nếu file > 20MB
4. **24E**: Cross-table continuation detection cho DOCX (khi 1 logical table split thành nhiều Word tables)
