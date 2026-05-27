# CURSOR_REPORT_23P0 — CELLS_JSON Header Mapping Diagnostic Audit

## 1. Mức độ hiểu task

- Hiểu: **95%**
- Chắc chắn:
  - Pipeline structured ingest: `RawTableModel` → `inferHeadersFromCoordinates()` → `mapCellsFromCoordinates()` → `cells_json` → `buildCanonicalText()` → UI/source hover.
  - Key dạng `bắt đầu_3` là **header collision suffixing**, không phải mất/đổi value.
  - Có thể tái tạo stage A–F bằng PDF fixture mà không re-ingest.
- Giả định:
  - DB/Qdrant documentId 23M vẫn tồn tại nhưng **không bắt buộc** cho root-cause stage classification (vì raw cells/slots không lưu trong DB).
- Thiếu:
  - Nếu cần “chunkId/Qdrant payload parity” đúng theo runtime document 23M/23O, phải chạy thêm DB/Qdrant audit (test có đường enable nhưng mặc định không chạy).

## 2. Tóm tắt yêu cầu

Diagnostic-only: audit xem `cells_json` có map đúng header theo cột vật lý hay không, và nếu sai thì sai từ stage nào (RawTableModel, header detection, slot selection, x-overlap, row merge, mapCells, canonical text, source hover).

Output bắt buộc:

- Stage A–F trace cho schedule rows (Nhóm 1/2/4).
- Trace cho curriculum rows liên quan Q5.
- Phân loại correctness (VALUE_ALIGNMENT / HEADER_MAPPING / SOURCE_DISPLAY).
- Root cause table: Issue → Example row → Stage introduced → Evidence → Recommended fix layer.
- 2 report files.

Không được implement fix production.

## 3. Hiện trạng trước khi diagnostic

Theo report 23O:

- Q2 PARTIAL: row đúng nhưng key dạng `bắt đầu_3: 6` → LLM bỏ sót weekday.
- Q5 PARTIAL/FAIL: curriculum cohort/semester context bị trộn (K46/HK2 không isolate).

UI hover cho schedule row hiển thị nhiều key `bắt đầu_*`.

## 4. Nguyên nhân gốc xác nhận từ source

### 4.1 `cells_json` key = selected header string (coordinate path)

`NormalizedTableService.normalizeRawTable(...)`:

- gọi `inferHeadersFromCoordinates(...)` tạo `CoordinateHeaderSlot(header, x..xEnd, fragments...)`
- `mapCellsFromCoordinates(...)` tạo map `slot.header() -> value`
- `buildCanonicalText(...)` in key/value theo `cells.entrySet()`

=> Nếu header slot bị chọn sai hoặc trùng, `cells_json` key sẽ sai/trùng và được suffix (`_2`, `_3`, ...).

### 4.2 Root cause cho schedule: slot chọn “bắt đầu” từ fragment cuối dù overlap=0

Trong `inferHeadersFromCoordinates(...)`:

- với mỗi slot, collect fragments từ nhiều header rows
- chọn header cuối cùng hợp lệ (reverse iteration)
- fragment có thể đến từ `bestHeaderCellForSlot(...)` kể cả khi overlap=0 (nearest-center fallback)

Kết quả: fragment thấp ở header row cuối (ví dụ `bắt đầu`) bị chọn cho nhiều cột khác nhau → collision suffixing.

### 4.3 Root cause cho curriculum: 1 spanning header cell (cohort/context) overlap rất rộng → lấn hết slots

Một cell header dạng “hóa, ngành: …K45” span qua x-range rất lớn, overlap với nhiều slots, và vì là fragment cuối nên bị chọn làm header cho nhiều cột → tạo `...K45`, `...K45_2`, `...K45_3`, ...; cohort khác (K46) có thể chỉ còn embedded trong value.

## 5. Chiến lược diagnostic đã chọn

- **Không dùng DB/Qdrant** làm SoT để tái tạo stage A–E vì raw coordinates & header slots không lưu trong DB.
- Dùng **PDF fixture parse** (production parser path) để lấy `RawTableModel` có coordinates.
- Viết test diagnostic in stage A–F cho:
  - Schedule table page 67 (needle: KNM1013, Nhóm 1/2/4)
  - Curriculum table pages 22–24 (needle: Kiến trúc K45/K46, HK2/học kỳ 2)

## 6. Danh sách file đã đọc

- `docs/eval/results/STRUCTURED_TABLE_INGEST_23M_20260526.md`
  - Mục đích: baseline structured path + runtime issues.
  - Kết luận: structured path active; Q2/Q5 còn gap; UI hover có `bắt đầu_*`.
- `reports/refactor/CURSOR_REPORT_23M_STRUCTURED_TABLE_INGEST.md`
  - Mục đích: confirm architecture, header inference, merge guard.
  - Kết luận: coordinate-based header inference + collision suffixing exist.
- `docs/eval/results/STRUCTURED_TABLE_RUNTIME_RESTORE_23N_20260526.md`
  - Mục đích: biết 23N chỉ sửa retrieval/scorer; ingest unchanged.
  - Kết luận: Q2/Q5 còn do ingest/prompt, không phải retrieval missing row.
- `docs/eval/results/RUNTIME_VERIFY_23O_AFTER_23N_20260526.md`
  - Mục đích: runtime evidence của Q2/Q5.
  - Kết luận: Q2 row đúng nhưng weekday key ambiguous; Q5 cohort mix.
- `Backend/src/main/java/.../NormalizedTableService.java`
  - Mục đích: locate Stage C/D/E/F implementation.
  - Kết luận: `inferHeadersFromCoordinates`, `bestHeaderCellForSlot`, `mapCellsFromCoordinates`, `buildCanonicalText`.
- `Backend/src/main/java/.../DocumentParserService.java`
  - Mục đích: confirm `RawTableModel` coordinate extraction from Tabula.
  - Kết luận: `convertTabulaTableToRawTableModel` preserves x/y/width/height.
- `Backend/src/main/java/.../ChunkingService2.java`
  - Mục đích: confirm canonical text becomes chunk content.
  - Kết luận: `createNormalizedRowChunk` uses `row.canonicalText()`.
- `Backend/src/main/java/.../RagRetrievalService.java`
  - Mục đích: confirm UI sources show `RetrievedContext.content` = canonical text.
  - Kết luận: `toRetrievedContext()` sets `content=c.getContent()`, `cellsJson=c.getCellsJson()`.
- `Backend/src/main/java/.../service/RawTable*.java`
  - Mục đích: confirm coordinate model shape.

## 7. Danh sách file đã sửa

- `Backend/src/test/java/KLTN/RAG_CHATBOT_BE/diagnostic/HeaderMapping23P0DiagnosticTest.java`
  - Mục đích: diagnostic trace stage A–F (fixture parse + coordinate header slots evidence).
  - Layer: **test/diagnostic** (không đổi production behavior).

## 8. Diff thay đổi của từng file

### `Backend/src/test/java/KLTN/RAG_CHATBOT_BE/diagnostic/HeaderMapping23P0DiagnosticTest.java`

- Hiện trạng cũ: chưa có diagnostic trace A–F; không có cách tái tạo slot selection evidence.
- Đã sửa: thêm JUnit test parse PDF fixture và dump Stage A–F cho schedule + curriculum; thêm optional DB audit flag `-Ddiag23p0.db=true`.
- Vì sao sửa: cần “bằng chứng stage where introduced” mà DB không lưu raw coords/slots.

```diff
*** Add File: Backend/src/test/java/KLTN/RAG_CHATBOT_BE/diagnostic/HeaderMapping23P0DiagnosticTest.java
+// Parses fixture PDF → finds raw tables → reflects into NormalizedTableService private methods
+// Dumps Stage A raw cells, Stage B header candidates, Stage C slots w/ overlap evidence,
+// Stage D merge summary, Stage E per-cell mapping, Stage F canonical text.
+// Includes a gated @SpringBootTest DB audit when -Ddiag23p0.db=true.
```

## 9. Ảnh hưởng sau sửa

- Production behavior: **không đổi**.
- Chỉ thêm test diagnostic; khi chạy test sẽ parse PDF fixture (tốn CPU/RAM như parse bình thường).
- Không ảnh hưởng MySQL/Qdrant data.

## 10. Edge cases đã xem xét

- **Header fragment overlap=0** nhưng vẫn được chọn do nearest-center fallback → gây header sai.
- **Spanning header cell** overlap nhiều slots → lấn các child headers.
- **Row merge**: schedule rows bị chặn merge bởi row-number detection → loại trừ WRONG_ROW root cause cho schedule.

## 11. Kết quả kiểm tra

| Command | Kết quả | Ghi chú |
|---|---|---|
| `cd Backend && .\mvnw.cmd "-Dtest=KLTN.RAG_CHATBOT_BE.diagnostic.HeaderMapping23P0DiagnosticTest" test` | PASS | In stage A–F trace cho schedule + curriculum (fixture) |
| `cd Backend && .\mvnw.cmd "-Dtest=KLTN.RAG_CHATBOT_BE.diagnostic.HeaderMapping23P0DiagnosticTest$DbAudit" -Ddiag23p0.db=true test` | NOT RUN | Cần DB env; không chạy mặc định |

## 12. Rủi ro còn lại

- Chưa có “chunkId/Qdrant payload” parity bằng DB/Qdrant trong report này (có đường chạy optional).
- Fix thực tế cần làm ở Stage C (header-slot selection) và/hoặc group_context extraction; **task này không implement**.

## 13. Đề xuất tiếp theo

1. Làm task fix riêng cho `inferHeadersFromCoordinates`:
   - alignment-aware fragment acceptance (đừng chọn fragment overlap=0 làm header cuối)
   - xử lý spanning header parent/child để tránh reuse parent cho nhiều cột
2. Với curriculum: cân nhắc đưa spanning cohort header vào `group_context` thay vì dùng làm per-column key.
3. Sau khi fix, re-run runtime Q2/Q5 strict.

