# CURSOR_REPORT_24D1_DOCX_SCHEDULE_ROW_MAPPING_DIAGNOSTIC

## 1. Mức độ hiểu task

- Hiểu task: **100%**.
- Chắc chắn:
  - Task là **diagnostic only**, chưa sửa production code.
  - Cần xác định stage sai mapping giữa POI -> RawTableModel -> normalize -> DB/Qdrant/UI source.
  - Cần kết luận theo 4 nhãn phân loại đã cho.
- Giả định:
  - Dữ liệu đang xét là bản DOCX đã ingest thành công gần nhất có cùng file name.
- Thiếu dữ kiện:
  - Không có snapshot UI hover trực tiếp từ frontend runtime ở thời điểm chạy (endpoint playground trả 500 với widget id thử nghiệm), nên đối chiếu UI qua payload nguồn tương đương (`text_segment`/`cells_json`) trong Qdrant.

## 2. Tóm tắt yêu cầu

- Diagnose mismatch mapping cho bảng lịch học trong mục `2.4. Phát hành thời khóa biểu chính thức`.
- Dump bắt buộc:
  - Raw POI cell-level trước RawTableModel.
  - RawTableModel row-level sau convert.
  - Normalized slot/value mapping + `cells_json` + canonical text.
  - DB `cells_json` vs Qdrant payload `cells_json` vs source text.
- Kết quả đầu ra dạng report, không fix code.

## 3. Hiện trạng trước khi sửa

- Dấu hiệu lỗi thực tế: nhiều dòng schedule có dạng:
  - `STT` chứa cả chuỗi dài (`STT + Mã HP + Tên lớp + Số TC + ... + Thứ`)
  - `Mã học phần` chứa tiết (`1 - 2`, `5 - 7`)
  - `Tên lớp học phần` chứa phòng (`E301`, `B301`, `H307`)
- Header 11 cột vẫn được detect đúng.

## 4. Nguyên nhân gốc xác nhận từ source

Root cause xác nhận:

- **Không phải Word file packed-cell**:
  - POI row có `getTableCells().size()=11`.
  - Mỗi cell chứa đúng giá trị tương ứng cột.
- **Không phải parser convert DOCX -> RawTableModel**:
  - `RawTableRow cellCount=11`, `physicalColIndex=0..10`, `x/xEnd` tuần tự đúng.
- **Sai ở NormalizedTableService (coordinate mapping stage)**:
  - Header slot đúng, nhưng `value -> slot` map sai: nhiều cell bị đẩy vào slot `STT`.
  - `finalCellsJsonMap` và `normalizedRow.cellsJson` đã sai ngay tại normalize output.
- DB và Qdrant ghi đúng theo output sai này, nên không phải lỗi hiển thị đơn thuần.

## 5. Chiến lược sửa đã chọn

- Không chỉnh sửa production logic.
- Tạo runner chẩn đoán tạm thời để dump chi tiết 3 tầng, chạy xong thì xóa runner.
- Đối chiếu thêm DB/Qdrant bằng query trực tiếp.
- Ghi report kết luận stage lỗi và phân loại.

## 6. Danh sách file đã đọc

| Path | Đọc để làm gì | Kết luận chính |
|---|---|---|
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/DocumentParserService.java` | xác định flow parse DOCX + convert raw table | POI cell text và RawTableModel DOCX mapping chạy theo logical grid |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/ChunkingService2.java` | xác định nơi normalize raw table được gọi | DOCX đi vào `normalizeRawTable()` |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/NormalizedTableService.java` | xác định slot inference + cell mapping | mismatch xuất hiện tại mapping theo coordinate slot |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/RawTableCell.java` | hiểu field tọa độ | có `physicalColIndex`, `x`, `xEnd`, `width` |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/RawTableRow.java` | hiểu row structure | row chứa list RawTableCell |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/RawTableModel.java` | hiểu metadata bảng raw | extractor DOCX dùng chung model |
| `docs/eval/results/_24d1_docx_row_mapping_dump.txt` | bằng chứng dump 3 tầng theo row mục tiêu | POI/Raw đúng, normalize sai |

## 7. Danh sách file đã sửa

| Path | Sửa để làm gì | Ảnh hưởng lớp |
|---|---|---|
| `docs/eval/results/DOCX_SCHEDULE_ROW_MAPPING_DIAGNOSTIC_24D1_20260527.md` | report kết quả diagnostic 24D1 | docs |
| `reports/refactor/CURSOR_REPORT_24D1_DOCX_SCHEDULE_ROW_MAPPING_DIAGNOSTIC.md` | report kỹ thuật 13 mục theo rule | docs |
| `docs/TASK_24D1_DOCX_SCHEDULE_ROW_MAPPING_DIAGNOSTIC_REPORT.md` | report tổng hợp trong `docs/` theo workspace rule | docs |

## 8. Diff thay đổi của từng file

### 8.1 `docs/eval/results/DOCX_SCHEDULE_ROW_MAPPING_DIAGNOSTIC_24D1_20260527.md`

- Hiện trạng cũ: chưa có report chẩn đoán 24D1.
- Đã sửa: tạo mới report chứa kết quả dump POI/Raw/Normalize/DB/Qdrant và kết luận phân loại.
- Vì sao sửa: đáp ứng file output bắt buộc của task.
- Ảnh hưởng: chỉ thêm tài liệu audit, không đổi runtime.

```diff
+++ docs/eval/results/DOCX_SCHEDULE_ROW_MAPPING_DIAGNOSTIC_24D1_20260527.md
@@
+- Raw DOCX row: 11 cells, values per-cell đúng kỳ vọng
+- RawTableModel: giữ nguyên 11 cột, không pack
+- normalizeRawTable: map value sai về slot STT
+- DB/Qdrant cells_json trùng nhau và đều sai mapping
+- Classification: NORMALIZER_MAPPING_BUG
```

### 8.2 `reports/refactor/CURSOR_REPORT_24D1_DOCX_SCHEDULE_ROW_MAPPING_DIAGNOSTIC.md`

- Hiện trạng cũ: chưa có report kỹ thuật 24D1 theo template.
- Đã sửa: tạo mới đầy đủ 13 mục, bao gồm root cause, edge cases, command log.
- Vì sao sửa: tuân thủ rule report-verification.
- Ảnh hưởng: docs only.

```diff
+++ reports/refactor/CURSOR_REPORT_24D1_DOCX_SCHEDULE_ROW_MAPPING_DIAGNOSTIC.md
@@
+- Xác nhận stage sai mapping: NormalizedTableService (coordinate mapping)
+- Loại trừ Word packed-cell và parser extraction bug
+- Đối chiếu DB/Qdrant: payload đồng nhất với output sai của normalizer
```

### 8.3 `docs/TASK_24D1_DOCX_SCHEDULE_ROW_MAPPING_DIAGNOSTIC_REPORT.md`

- Hiện trạng cũ: chưa có report tổng hợp tại `docs/`.
- Đã sửa: tạo report tóm tắt kết luận và trạng thái verify.
- Vì sao sửa: tuân thủ rule workspace bắt buộc có report trong `docs/`.
- Ảnh hưởng: docs only.

```diff
+++ docs/TASK_24D1_DOCX_SCHEDULE_ROW_MAPPING_DIAGNOSTIC_REPORT.md
@@
+- verdict: NORMALIZER_MAPPING_BUG
+- production code changed: No
+- references: detailed eval report + technical report
```

## 9. Ảnh hưởng sau sửa

- Behavior thay đổi: **không có** (không sửa production code).
- Behavior giữ nguyên: ingest/runtime hiện tại giữ nguyên như trước.
- Điều kiện kích hoạt: không có code path mới.
- Fallback giữ nguyên: toàn bộ fallback parser/normalizer hiện hữu giữ nguyên.
- Tài nguyên (RAM/CPU/disk): chỉ tăng nhẹ dung lượng docs và file evidence dump.
- Latency/token/API cost: không đổi cho runtime production.
- Dữ liệu MySQL/Qdrant cũ: không migration, không mutate dữ liệu hiện có.

## 10. Edge cases đã xem xét

- DOCX row có đủ 11 cell nhưng cell cuối trống (`Ghi chú`).
- Không có `gridSpan`/`vMerge` bất thường ở row mục tiêu.
- Các row target có pattern khác nhau (`LUA1012`, `TIN1093`, `KNM1013`) nhưng đều cùng lỗi mapping.
- Cases có phòng khác nhau (`E301`, `E302`, `H307`, `B301`) vẫn cùng symptom.
- Row có tiết dạng `1 - 2`, `5 - 7` bị map nhầm vào `Mã học phần`.

## 11. Kết quả kiểm tra

| Command | Kết quả | Ghi chú |
|---|---|---|
| `./mvnw ... exec:java ... DocxScheduleRowMappingDiagnostic24D1` | PASS | chạy runner diagnostic, xuất `docs/eval/results/_24d1_docx_row_mapping_dump.txt` |
| `docker exec ragchatbot-mysql mysql ... SELECT ... FROM document_chunks` | PASS | xác nhận DB `cells_json` đã sai mapping ở schedule rows |
| `curl POST /collections/documents/points/scroll` | PASS | xác nhận Qdrant `cells_json` trùng DB (sai giống nhau) |
| `curl POST /api/playground/chat` | FAIL | 500 tại thời điểm kiểm tra với id widget thử nghiệm; dùng `text_segment` từ Qdrant làm nguồn tương đương |
| `cd Backend && ./mvnw -DskipTests compile` | NOT RUN | không cần cho diagnostic docs-only |
| `cd Backend && ./mvnw test` | NOT RUN | không cần cho diagnostic docs-only |
| `cd Frontend && npm run lint` | NOT RUN | ngoài scope |
| `cd Frontend && npm run build` | NOT RUN | ngoài scope |
| `cd Frontend && npm run build:widget` | NOT RUN | ngoài scope |
| `docker compose config` | NOT RUN | không bắt buộc cho task này |

## 12. Rủi ro còn lại

- Lỗi mapping schedule vẫn tồn tại ở production flow ingest hiện tại.
- Các truy vấn runtime phụ thuộc row schedule có nguy cơ trả lời sai trường (thứ/tiết/phòng).
- Do chưa fix code, ingest mới của các DOCX cùng cấu trúc vẫn có thể tiếp tục sinh dữ liệu sai mapping.

## 13. Đề xuất tiếp theo

1. Tạo task fix riêng cho `NormalizedTableService` (không sửa parser DOCX).
2. Bổ sung test regression cho table schedule 11 cột có các row target nêu trên.
3. Re-ingest một DOCX mẫu sau fix và verify lại parity:
   - POI cell -> RawTableModel -> normalized `cells_json` -> DB -> Qdrant -> runtime source.

