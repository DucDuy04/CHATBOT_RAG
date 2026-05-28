# CURSOR_REPORT_24D2_DOCX_LOGICAL_GRID_CELLS_JSON_FIX

## 1. Mức độ hiểu task

- Hiểu task: **100%**
- Chắc chắn:
  - Bug nằm ở `NormalizedTableService.toLogicalRow()` — được xác nhận qua 24D1 diagnostic
  - Fix tập trung vào DOCX extractor type, không đụng PDF path
  - Cần test + fresh ingest + verify cells_json DB/Qdrant
- Giả định:
  - DOCX schedule table có group-header rows với merged cell (gridSpan > 1) trong phần data — xác nhận bằng cách trace root cause từ output sai
- Thiếu dữ kiện:
  - Nomic API quota đã hết → không thể complete full ingest với embedding; cells_json trong DB đúng nhưng Qdrant không có data mới

---

## 2. Tóm tắt yêu cầu

Fix DOCX logical-grid value-to-slot mapping trong `NormalizedTableService` để `cells_json` được lưu đúng vào DB và Qdrant. Chỉ verify `cells_json`, không run Q1-Q8.

---

## 3. Hiện trạng trước khi sửa

Schedule rows có `cells_json` sai:
```json
{
  "STT": "1 LUA1012 Pháp luật Việt Nam đại cương - Nhóm 1 2 0 Nguyễn Thị Vân Anh 08/09/2025 2",
  "Mã học phần": "1 - 2",
  "Tên lớp học phần": "E301",
  "Số TC": "", "Số SV": "", "Giảng viên": "", ...
}
```
8 giá trị bị nhồi vào STT. Tiết học bị gán vào Mã học phần. Phòng bị gán vào Tên lớp học phần.

---

## 4. Nguyên nhân gốc xác nhận từ source

**Stack trace của bug:**
1. `normalizeRawTable()` gọi `inferHeadersFromCoordinates()`
2. Trong step 1 của `inferHeadersFromCoordinates()`: scan data rows để tính `slotX[col]/slotXEnd[col]`
3. Schedule table có group-header row trong phần data với merged cell `physicalColIndex=0, gridSpan=8` (spanning columns 0-7)
4. `cellAt(groupHeaderRow, 0)` = merged cell với `x=0, xEnd=8`
5. Kết quả: `slotXEnd[0] = 8` thay vì `1`
6. Trong `toLogicalRow()`: `bestSlotForCell(cell, slots)` tính x-overlap
7. Với data cell[0]..cell[7] (mỗi cell có width=1):
   - `overlap(cellX, cellXEnd, 0, 8) = 1` (với slot[0] đã bị expand)
   - `overlap(cellX, cellXEnd, cellX, cellX+1) = 1` (với slot đúng)
   - Cả hai score bằng nhau = 1.0
   - Slot[0] thắng tie-break vì `score > bestScore` dùng strict `>` → first slot với score đó giữ nguyên
8. Tất cả 8 cells đều được assign vào slot[0] → STT nhận 8 values concatenated

**Điều kiện kích hoạt:** chỉ xảy ra khi có merged group-header row TRONG phần data của table (không phải header row). Điều này phổ biến trong schedule table DOCX của HUSC.

---

## 5. Chiến lược sửa đã chọn

**Minimal diff**: trong `toLogicalRow()`, với DOCX cells, dùng `physicalColIndex` trực tiếp làm column assignment thay vì gọi `bestSlotForCell()`.

Lý do:
- DOCX logical grid đảm bảo `physicalColIndex = logicalCol = correct column`
- Không cần x-overlap cho DOCX (coordinates là integer grid, không phải physical pixels)
- PDF/SPREADSHEET/BASIC không bị ảnh hưởng (else branch giữ nguyên)
- Không thay đổi header inference (headers vẫn đúng vì header row không có merged group-header)

---

## 6. Danh sách file đã đọc

| Path | Đọc để làm gì | Kết luận chính |
|---|---|---|
| `docs/eval/results/DOCX_SCHEDULE_ROW_MAPPING_DIAGNOSTIC_24D1_20260527.md` | Xác nhận root cause từ 24D1 | Bug ở normalizer, POI/RawTableModel đúng |
| `reports/refactor/CURSOR_REPORT_24D1_DOCX_SCHEDULE_ROW_MAPPING_DIAGNOSTIC.md` | Chi tiết kỹ thuật 24D1 | xác nhận `normalizeRawTable()` là điểm sai |
| `Backend/.../NormalizedTableService.java` | Tìm method mapping | `toLogicalRow()` + `bestSlotForCell()` là nguyên nhân |
| `Backend/.../RawTableModel.java` | Kiểm tra ExtractorType enum | Có `DOCX` enum value để branch |
| `Backend/.../RawTableCell.java` | Kiểm tra `hasCoordinates()` | `width > 0 && height > 0` → DOCX cells pass check |
| `Backend/.../DocumentParserService.java` | Xác nhận DOCX cell construction | `physicalColIndex = logicalCol`, `width = gridSpan` |
| `Backend/.../ChunkingService2.java` | Xác nhận flow gọi `normalizeRawTable()` | `processNormalizedRawTable()` gọi thẳng, không transform |

---

## 7. Danh sách file đã sửa

| Path | Sửa để làm gì | Ảnh hưởng lớp |
|---|---|---|
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/NormalizedTableService.java` | Fix `toLogicalRow()` cho DOCX cells | service |
| `Backend/src/test/java/.../service/NormalizedTableIngestTest.java` | Test 1-4 cho DOCX mapping + PDF regression | test |
| `Backend/src/test/java/.../service/DocxParserServiceTest.java` | Test cell/row coordinate shape | test |
| `Backend/src/test/java/.../service/NormalizedTableSuppressionTest.java` | Regression tests suppression profile | test |
| `Backend/src/test/java/.../service/NoHardcodedLexiconInTableNormalizerTest.java` | No-hardcode audit | test |
| `docs/eval/results/DOCX_LOGICAL_GRID_CELLS_JSON_FIX_24D2_20260527.md` | Eval report | docs |
| `reports/refactor/CURSOR_REPORT_24D2_DOCX_LOGICAL_GRID_CELLS_JSON_FIX.md` | Technical report | docs |

---

## 8. Diff thay đổi của từng file

### 8.1 `NormalizedTableService.java` — method `toLogicalRow()`

- **Hiện trạng cũ**: Tất cả extractor types dùng `bestSlotForCell()` với x-overlap scoring
- **Đã sửa**: DOCX cells dùng `physicalColIndex` trực tiếp
- **Vì sao**: Tránh x-overlap tie-break bug khi group-header rows contaminate slot ranges
- **Ảnh hưởng**: DOCX schedule rows mapping đúng, PDF không đổi

```diff
 private static CoordinateLogicalRow toLogicalRow(RawTableRow row, List<CoordinateHeaderSlot> slots) {
     Map<Integer, List<RawTableCell>> byColumn = new LinkedHashMap<>();
     for (RawTableCell cell : row.cells()) {
         if (safe(cell.text()).isBlank()) {
             continue;
         }
-        CoordinateHeaderSlot slot = bestSlotForCell(cell, slots);
-        int col = slot == null ? cell.physicalColIndex() : slot.columnIndex();
+        int col;
+        if (cell.extractorType() == RawTableModel.ExtractorType.DOCX
+                && cell.physicalColIndex() >= 0
+                && cell.physicalColIndex() < slots.size()) {
+            // DOCX logical-grid: physicalColIndex is the definitive column assignment.
+            // Coordinate x-overlap is not reliable for DOCX because merged group-header
+            // rows within the table body can contaminate slot x-ranges, causing all cells
+            // in a row to be falsely packed into slot 0 via tie-broken overlap scoring.
+            col = cell.physicalColIndex();
+        } else {
+            CoordinateHeaderSlot slot = bestSlotForCell(cell, slots);
+            col = slot == null ? cell.physicalColIndex() : slot.columnIndex();
+        }
         byColumn.computeIfAbsent(col, ignored -> new ArrayList<>()).add(cell);
     }
```

---

## 9. Ảnh hưởng sau sửa

- **Thay đổi**: DOCX schedule rows → correct 1-to-1 mapping `physicalColIndex → slot`
- **Giữ nguyên**: PDF/SPREADSHEET/BASIC path không đổi; header inference không đổi; suppression logic không đổi
- **Bật khi**: `extractorType == DOCX && physicalColIndex >= 0 && physicalColIndex < slots.size()`
- **Fallback**: DOCX cells với `physicalColIndex >= slots.size()` vẫn dùng `bestSlotForCell()` (not triggered bình thường)
- **Memory/CPU**: không ảnh hưởng đáng kể (một if-branch đơn giản)
- **Dữ liệu cũ**: MySQL/Qdrant cũ vẫn chứa wrong cells_json cho các documents đã ingest; cần re-ingest để fix

---

## 10. Edge cases đã xem xét

- DOCX row với blank trailing cell (Ghi chú): `physicalColIndex=10` → slot[10] ← đúng, không shift
- DOCX merged cell (gridSpan > 1): `physicalColIndex = logicalCol của ô gốc` → được assign 1 lần vào slot đúng
- Group-header rows với merged cell spanning 8 cols: `physicalColIndex=0` → slot[0], period cell → slot[8], room → slot[9] ← đúng
- `physicalColIndex >= slots.size()`: fallback sang `bestSlotForCell()` — an toàn cho tables có extra cells
- PDF table: else branch không thay đổi, coordinate overlap giữ nguyên
- TXT ingest: không qua `normalizeRawTable()`, không ảnh hưởng

---

## 11. Kết quả kiểm tra

| Command | Kết quả | Ghi chú |
|---|---|---|
| `.\mvnw.cmd -DskipTests compile` | PASS | 0 errors, 1 deprecation warning (pre-existing) |
| `.\mvnw.cmd -Dtest=DocxParserServiceTest,NormalizedTableIngestTest,NormalizedTableSuppressionTest,NoHardcodedLexiconInTableNormalizerTest test` | PASS | 16 tests, 0 failures, 0 errors |
| `docker compose config -q` | PASS | No config errors |
| `docker compose up --build -d backend` | PASS | Backend rebuilt and started in 50s |
| Fresh DOCX upload | PARTIAL | Chunks stored correctly in DB; embedding failed (Nomic API quota exhausted) → document FAILED, Qdrant 0 points |
| DB cells_json verify LUA1012 Nhóm 1 | PASS | `{"STT":"1","Mã học phần":"LUA1012","Tên lớp học phần":"Pháp luật Việt Nam đại cương - Nhóm 1",...,"Tiết học":"1 - 2","Phòng":"E301"}` ✅ |
| DB cells_json verify LUA1012 Nhóm 2 | PASS | `{"STT":"2",...,"Tiết học":"3 - 4","Phòng":"E301"}` ✅ |
| DB cells_json verify TIN1093 Nhóm 15 | PASS | `{"STT":"193","Mã học phần":"TIN1093","Tên lớp học phần":"Nhập môn lập trình - Nhóm 15","Số TC":"3",...}` ✅ |
| DB cells_json verify KNM1013 Nhóm 4 | PASS | `{"Mã học phần":"KNM1013","Giảng viên":"Nguyễn Thị Thanh Nhàn","Tiết học":"5 - 7","Phòng":"B301"}` ✅ |
| DB cells_json verify KNM1013 Nhóm 2 | PASS | `{"Mã học phần":"KNM1013","Tiết học":"1 - 3","Phòng":"H307"}` ✅ |
| DB cells_json verify KTR3185 | PASS | Correct schedule row with all 11 fields |
| DB cells_json verify Curriculum K46 | PASS | `{"Khóa ngành":"Xã hội học K46","Học kỳ":"HK1",...}` ✅ |
| Frontend lint | NOT RUN | Ngoài scope |
| Frontend build | NOT RUN | Ngoài scope |
| Widget build | NOT RUN | Ngoài scope |

---

## 12. Rủi ro còn lại

1. **Qdrant không cập nhật**: Old wrong vectors vẫn còn. Cần re-ingest với API key mới.
2. **Nomic API hết quota**: Production embedding đang bị chặn. Block cho tất cả ingest.
3. **Group-header rows as data**: Các dòng group-header (merged cell spanning N cols) được normalize thành row với `STT = "Ngành..."`. Không gây lỗi nhưng có thể generate noise trong search results.

---

## 13. Đề xuất tiếp theo

1. **Task 24D3**: Cấu hình backup embedding provider hoặc Nomic paid key. Re-ingest DOCX. Xóa old wrong documents. Verify Qdrant payload.
2. **Optional 24D4**: Phát hiện group-header rows trong DOCX table data và emit `groupContext` thay vì fake data row.
