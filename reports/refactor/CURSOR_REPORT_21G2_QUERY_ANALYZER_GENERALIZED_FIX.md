# CURSOR_REPORT_21G2 — QueryAnalyzer generalized (no entity value hardcode)

## 1. Mức độ hiểu task

- **~95%** — refactor heuristic theo cấu trúc câu; không dùng NLP/LLM/dependency mới.
- **Chắc chắn:** 21G đã hardcode `basic`/`pro`/`business`; `normalize()` bỏ dấu → pattern ASCII hợp lệ.
- **Giả định:** một số câu biên (tiếng Anh, từ tối nghĩa) vẫn có thể cần tinh chỉnh sau khi có corpus thực tế.

## 2. Tóm tắt yêu cầu

Loại bỏ mọi **literal tên gói/sản phẩm/dòng** trong logic production của `QueryAnalyzerService`; phân biệt TABLE_LOOKUP vs COUNT_QUERY bằng **table cue + value cue** hoặc **khung row reference + value cue** và **explicit count**; cập nhật unit test (golden + generalization + negative).

## 3. Phạm vi đã làm

- Refactor `QueryAnalyzerService`: xóa `containsTierPackageMarker`; thêm regex/cue helpers; mở rộng `isExplicitItemCountQuery`; chỉnh nhánh TABLE keyword cuối (`hang`/`ma`).
- Cập nhật `QueryAnalyzerServiceTest` đầy đủ nhóm A/B/C theo brief.
- Thêm `docs/eval/results/RAG_QUERY_ANALYZER_GENERALIZED_21G2_20260514.md`, `FIX_LOOP_21G2_*`, báo cáo này, `docs/CURSOR_REPORT_21G2_QUERY_ANALYZER_GENERALIZED_FIX.md`.

## 4. Phạm vi không làm

- Không sửa PromptBuilder, RagRetrievalService, ChatService, frontend, Docker, schema, migration, dependency, `.gitignore`.

## 5. Vấn đề hardcode trong 21G

- `containsTierPackageMarker(q)` chứa chuỗi cố định: `goi basic`, `goi pro`, `dong business`, …
- Không scalable; test có thể “khớp golden” mà không chứng minh tổng quát.

## 6. Phân tích QueryAnalyzer trước khi sửa (21G2)

- Collision gốc (21F): `bao nhieu` → COUNT trước TABLE.
- 21G đã sửa bằng explicit count + table cell, nhưng phần “cell” dựa vào **tên tier golden**.

## 7. Thiết kế heuristic tổng quát đã chọn

| Helper | Vai trò |
|--------|---------|
| `hasTableCue` | `trong bang`, `theo bang`, `bang …`, `dong ` (dòng), `TABLE_ROW_HANG` cho **hàng** (tránh `thang`), `cot`/`row`/`cell`/`table`. |
| `hasValueFieldCue | Scalar/attribute: `gia`, `vnd`, `luot`, `ton kho`, `trang thai`, `phong ban`, `thuoc`, `kenh`, `ho tro`, `la gi`, `gia tri cot`, … |
| `hasSpecificRowReference` | Regex category+tail (`goi X`, `san pham X`, `dich vu X`, `\bma\b`, `\bdong\b`, `\bhang\b`, `nhan vien`, `khach hang`, `sku-…`); loại tail generic `goi dich vu`; pattern `Entity co <field>`. |
| `isExplicitItemCountQuery` | Mở rộng cụm đếm (sản phẩm, nhân viên, dòng trong bảng, …) chạy **trước** table cell. |
| `isTableCellLookupQuery` | `(tableCue && valueCue) \|\| (specificRow && valueCue)`. |

## 8. Vì sao heuristic này tổng quát hơn

- Không đọc token entity; chỉ nhận **khung** (`gói` + tail không rỗng, `mã` + mã, `SKU-`, `X có giá`).
- Explicit COUNT tách “bao nhiêu **nhóm**” khỏi “**giá/trạng thái** của một dòng”.

## 9. Vì sao không sửa PromptBuilder/retrieval

- Contract giữ nguyên: `queryTypeHint` vẫn đến từ `analyze()`; chỉ làm sạch phân loại ở một lớp.

## 10. Danh sách file đã đọc

| Path | Mục đích |
|------|----------|
| `docs/eval/results/RAG_QUERY_ANALYZER_FIX_21G_20260514.md` | 21G baseline |
| `docs/eval/results/FIX_LOOP_21G_QUERY_ANALYZER_TABLE_COUNT_COLLISION.md` | Fix loop 21G |
| `reports/refactor/CURSOR_REPORT_21G_QUERY_ANALYZER_TABLE_LOOKUP_FIX.md` | Report 21G |
| `docs/eval/results/RAG_T01_GENERATION_SOURCE_DIAG_21F_20260514.md` | Diagnosis |
| `reports/refactor/CURSOR_REPORT_21F_T01_GENERATION_SOURCE_DIAGNOSIS.md` | Tóm tắt 21F |
| `docs/eval/RAG_GOLDEN_QUESTIONS.md` | Golden text |
| `docs/eval/RAG_BASELINE_CORE_CHECKLIST.md` | Gate |
| `docs/eval/RAG_BASELINE_CORE_REBUILD_PLAN.md` | Plan |
| `QueryAnalyzerService.java` | Sửa |
| `PromptBuilderService.java` | grep/skip sửa |
| `QueryAnalyzerServiceTest.java` | Sửa |

## 11. Danh sách file đã sửa

| Path | Lớp |
|------|-----|
| `QueryAnalyzerService.java` | service |
| `QueryAnalyzerServiceTest.java` | test |
| `docs/eval/results/RAG_QUERY_ANALYZER_GENERALIZED_21G2_20260514.md` | docs |
| `docs/eval/results/FIX_LOOP_21G2_QUERY_ANALYZER_GENERALIZED.md` | docs |
| `reports/refactor/CURSOR_REPORT_21G2_QUERY_ANALYZER_GENERALIZED_FIX.md` | report |
| `docs/CURSOR_REPORT_21G2_QUERY_ANALYZER_GENERALIZED_FIX.md` | report (rule 90) |

## 12. Diff từng file (tóm tắt block)

### `QueryAnalyzerService.java`

```diff
- containsTierPackageMarker + hardcoded dong basic / goi pro / …
+ Pattern SPECIFIC_ROW_CATEGORY_ENTITY, SPECIFIC_SKU_REFERENCE, ENTITY_THEN_CO_FIELD
+ hasTableCue / hasValueFieldCue / hasSpecificRowReference / hasCountTargetCategory
+ isTableCellLookupQuery: (table && value) || (row && value)
+ isExplicitItemCountQuery: thêm co bao nhieu san pham, nhan vien, dong trong bang, …
+ analyze() TABLE keyword block: hang → TABLE_ROW_HANG; ma → " ma "
```

### `QueryAnalyzerServiceTest.java`

- Thêm nhóm generalization + negative + edge; giữ golden regression.

## 13. Test cases đã thêm

| Nhóm | Nội dung |
|------|----------|
| Golden regression TABLE | 5 câu (có Basic/Pro/Business — chỉ trong **test**, không trong production). |
| Golden regression COUNT | 6 câu |
| Generalization TABLE | Laptop X, Máy in A, SP001, Nguyễn Văn A, Premium Plus, ABC, SKU-123, Enterprise lượt |
| Generalization COUNT | sản phẩm trong bảng, dòng bảng, nhân viên danh sách, đếm dịch vụ, số lượng dòng dữ liệu |
| Negative | Process…bước; Profile…trường |
| Edge | Có bao nhiêu gói Enterprise; Có bao nhiêu dòng trong bảng; Dòng Enterprise có giá |

## 14. Compile/test result

| Command | Kết quả |
|---------|---------|
| `mvnw -DskipTests compile` | PASS |
| `mvnw -Dtest=QueryAnalyzerServiceTest test` | PASS |

## 15. Runtime verification

**NOT RUN** (không bắt buộc; không gọi API trong phiên này).

## 16. Ảnh hưởng

| Mục | Ảnh hưởng |
|-----|-----------|
| TABLE_LOOKUP | Mở rộng cho mọi `gói <tên>` / `mã` / `SKU` / `dòng` + field phù hợp. |
| COUNT_QUERY | Giữ explicit + generic; mở rộng cụm đếm tổng quát. |
| False positive risk | Regex `goi` + tail có thể khớp câu hiếm; `ma`/`hang` đã giảm substring risk một phần. |
| RAM/CPU | Thêm vài `Pattern` compile-time + match O(n) trên câu hỏi ngắn. |

## 17. Còn hardcode entity values trong production không?

**Không** — không còn literal Basic/Pro/Business/Enterprise/Laptop/SP001 trong logic phân loại (grep xác nhận).

## 18. Case improved / regressed

- **Improved:** câu có entity tùy ý theo khung `gói X`, `sản phẩm X`, …
- **Regressed:** không phát hiện trong unit hiện tại.

## 19. Rủi ro còn lại

- Ngữ cảnh đa ngôn ngữ / viết tắt có thể cần mở rộng stoplist hoặc boundary.
- `gia tri` / `ma` vẫn có thể collision hiếm (đã giảm một phần với ` ma ` ở nhánh TABLE cuối).

## 20. Đề xuất prompt tiếp theo

- Nếu T01 vẫn flip sau hint đúng → task PromptBuilder 21H (không đổi trong 21G2).

---

## Resource / production analysis

| Câu hỏi | Trả lời |
|---------|---------|
| DB/LLM/Qdrant mới? | Không |
| Loop lớn? | Không |
| Latency | Không đo; chỉ regex ngắn trên query |
| Reindex / migration? | Không |

---

*Bản rule 90: `docs/CURSOR_REPORT_21G2_QUERY_ANALYZER_GENERALIZED_FIX.md` (copy nội dung tương đương).*
