# RAG — QueryAnalyzer generalized (21G2): no entity hardcode

**Ngày:** 2026-05-14  
**Phạm vi:** `QueryAnalyzerService` + unit test + tài liệu (không PromptBuilder / retrieval / ChatService).

---

## 1. Vấn đề hardcode từ 21G

- Helper `containsTierPackageMarker()` khớp literal **basic / pro / business** và biến thể `goi pro`, `dong basic`, …
- Không tổng quát cho tài liệu khác (Laptop X, SP001, Enterprise, …).
- Test golden có thể pass nhờ trùng tên với golden doc — không phải mục tiêu production.

---

## 2. Phân tích thiết kế tổng quát (trả lời brief §4)

| Câu hỏi phân tích | Trả lời ngắn |
|-------------------|---------------|
| 21G hardcode những từ nào? | Chuỗi literal `basic`, `pro`, `business`, `goi pro`, `dong basic`, … trong `containsTierPackageMarker`. |
| Vì sao `containsTierPackageMarker` không tổng quát? | Gắn tên hàng cụ thể của golden eval, không mô hình hóa “một hàng bất kỳ”. |
| Test 21G có quá khớp golden? | Có — assertion dùng câu có Basic/Pro/Business; không chứng minh tổng quát. |
| Cell lookup không biết tên row? | Dùng **khung ngôn ngữ**: `gói <tail>`, `sản phẩm <tail>`, `mã <tail>`, `dòng <tail>`, `SKU-…`, hoặc `… có giá / tồn kho / trạng thái / …`. |
| Cấu trúc câu thay tên entity? | Có — regex + cụm `co <field>`; không đọc nội dung tail. |
| Pattern an toàn? | Explicit COUNT chạy trước; `hang`/`dong` trong count dùng boundary hoặc cụm dài để tránh `thang`/`duong`. |
| Count dễ nhầm TABLE? | “bao nhiêu + category + trong bảng” không có field scalar → explicit COUNT. |
| Đổi thứ tự `analyze()`? | Giữ: explicit COUNT → TABLE cell → COUNT generic → … |
| PromptBuilder? | Không sửa (task này). |
| Minimal fix? | Xóa tier marker; thêm `hasTableCue` / `hasValueFieldCue` / `hasSpecificRowReference` + mở rộng explicit count. |

---

## 3. Code change summary

- Xóa `containsTierPackageMarker`.
- Thêm `SPECIFIC_ROW_CATEGORY_ENTITY`, `SPECIFIC_SKU_REFERENCE`, `ENTITY_THEN_CO_FIELD` (regex, không dấu sau `normalize()`).
- `hasTableCue`, `hasValueFieldCue`, `hasSpecificRowReference`, `hasCountTargetCategory` (chỉ **từ loại**, không tên riêng).
- `isTableCellLookupQuery`: `(tableCue && valueCue) || (specificRow && valueCue)`.
- `isExplicitItemCountQuery`: thêm cụm COUNT tổng quát (sản phẩm, nhân viên, dòng trong bảng, …).
- Khối TABLE keyword cuối `analyze()`: bỏ substring `hang` thô; dùng `TABLE_ROW_HANG`; `ma` → ` ma ` để giảm false positive.

---

## 4. Test cases (tóm tắt)

| Nhóm | Số case (ước) | Kết quả |
|------|-----------------|---------|
| Golden TABLE_LOOKUP | 5 | PASS |
| Golden COUNT | 6 | PASS |
| Generalization TABLE | 8 | PASS |
| Generalization COUNT | 5 | PASS |
| Negative (process/profile) | 2 | PASS |
| Edge Enterprise / dòng | 3 | PASS |

---

## 5. Compile / test

| Command | Kết quả |
|---------|---------|
| `.\mvnw.cmd -DskipTests compile` | PASS |
| `.\mvnw.cmd -q -Dtest=QueryAnalyzerServiceTest test` | PASS |

---

## 6. Runtime

**NOT RUN** — không bắt buộc; không đọc secret widget key trong phiên agent.

---

## 7. Verdict

**PASS** (mục tiêu 21G2: loại bỏ hardcode entity trong production + unit đủ).

---

## 8. Hardcode entity values còn lại trong production?

**Không** — grep `basic|business|enterprise|laptop` trên `QueryAnalyzerService.java` không còn match logic (chỉ comment / từ `process` trong SECTION keyword).

---

*Không chứa API key.*
