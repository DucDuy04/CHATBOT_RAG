# RAG — QueryAnalyzer COUNT vs TABLE_LOOKUP collision fix (21G)

**Ngày:** 2026-05-14  
**Task:** 21G — sửa `QueryAnalyzerService` (không đụng PromptBuilder / retrieval / ChatService).  
**Git HEAD (lúc ghi):** `a01435769aad71df7cb8fd35435f93496e449fe2`

---

## 1. Mục tiêu fix

- Câu tra **giá / ô bảng / dòng bảng / quota / kênh hỗ trợ** có cụm **“bao nhiêu”** phải được `TABLE_LOOKUP`, không còn `COUNT_QUERY` sớm.
- Câu **đếm số gói / chính sách / hàng** vẫn `COUNT_QUERY`.

---

## 2. Before (21F)

- `COUNT_QUERY` kiểm tra **trước** `TABLE_LOOKUP` và khớp `bao nhieu` → GQ-T01 gốc bị `COUNT_QUERY`.
- Hệ quả: `queryTypeHint` không kích hoạt block instruction `TABLE_LOOKUP` trong `PromptBuilderService` (task 21F diagnosis).

---

## 3. Phân tích QueryAnalyzer trước sửa (trích từ source)

| # | Câu hỏi phân tích | Kết luận |
|---|-------------------|----------|
| 1 | COUNT_QUERY ở đâu? | Ngay sau `normalize()`, block `containsAny(..., "bao nhieu", "co bao nhieu", ...)` (dòng ~70–75 cũ). |
| 2 | TABLE_LOOKUP ở đâu? | Block keyword `bang`, `cot`, `hang`, … sau LIST (dòng ~87–92 cũ). |
| 3 | Vì sao T01 = COUNT? | Chuỗi `bao nhieu` khớp trước khi tới nhánh `bang`. |
| 4 | Collision keywords | `bao nhiêu` + ngữ cảnh giá/bảng/gói; `VNĐ`/`vnd`; `lượt hỏi`; không phải xung đột với `gói` đơn thuần mà với **“bao nhiêu gói”** (đếm). |
| 5 | Phân biệt | Cell lookup: bảng/dòng/cột + field (giá, lượt, kênh, hỗ trợ) hoặc **gói cụ thể** (Basic/Pro/Business) + field. Count: **bao nhiêu gói/chính sách/hàng**, đếm, số lượng tổng. |
| 6 | Đổi thứ tự rule? | Có — ưu tiên **explicit count** rồi **table cell lookup** rồi COUNT generic. |
| 7 | Helper `isTableCellLookupQuery`? | Có — triển khai private + `isExplicitItemCountQuery` + `containsTierPackageMarker`. |
| 8 | Rủi ro “Có bao nhiêu gói dịch vụ?” → TABLE? | Giảm bằng `isExplicitItemCountQuery` (`co bao nhieu goi`, `bao nhieu goi dich vu`, …). |
| 9 | Sửa PromptBuilder? | **Không** trong 21G; nếu T01 vẫn flip sau hint đúng → task 21H. |
| 10 | Minimal fix | Chỉ `QueryAnalyzerService.java` + unit test nhẹ. |

---

## 4. Code change summary

- Thêm `isExplicitItemCountQuery(normalized)` trả về true cho đếm gói/chính sách/hàng, `dem`, `so luong` + domain.
- Thêm `isTableCellLookupQuery(normalized)` trả về true khi có cue bảng + (field giá/lượt/kênh/hỗ trợ **hoặc** tier Basic/Pro/Business), hoặc tier + field không cần chữ “bảng”.
- Thêm `containsTierPackageMarker` để tránh khớp `pro` trong từ tiếng Anh dài.
- Thứ tự trong `analyze`: explicit COUNT → TABLE cell → COUNT generic → LIST → …

---

## 5. Test result (host)

| Command | Kết quả |
|---------|---------|
| `Backend\.\mvnw.cmd -DskipTests compile -q` | **PASS** |
| `Backend\.\mvnw.cmd -q -Dtest=QueryAnalyzerServiceTest test` | **PASS** |

---

## 6. Runtime targeted (POST /api/chat)

| Mục | Kết quả | Ghi chú |
|-----|---------|---------|
| T01 ×3, F03, L02, T02, C01, O01/O02 | **NOT RUN** | Stack Docker **Up** nhưng phiên agent **không** nạp `X-Widget-Key` (không đọc `.env` chứa secret). Operator chạy lại theo runbook 21E2 + widget key local. |

---

## 7. T01 original ×3

**NOT RUN** (thiếu widget key trong phiên verify).

---

## 8. Count query regression (logic)

Unit test bao phủ 5 câu COUNT mẫu + GQ-C01; kỳ vọng vẫn `COUNT_QUERY`.

---

## 9. Verdict task 21G

| Tiêu chí | Verdict |
|-----------|---------|
| Phân loại T01-like → `TABLE_LOOKUP` (unit) | **PASS** |
| Count thật → `COUNT_QUERY` (unit) | **PASS** |
| Compile | **PASS** |
| Runtime T01 ổn định hơn | **PARTIAL** — cần operator rerun chat sau deploy; generation vẫn có thể cần 21H nếu hint đã đúng mà vẫn deny. |

---

## 10. Đề xuất task sau

- **21H PromptBuilder / table guard** nếu sau khi `queryTypeHint=TABLE_LOOKUP` đầy đủ mà T01 vẫn trả “không tìm thấy” (theo 21F: generation contradiction vẫn có thể xảy ra).

---

*Không chứa API key đầy đủ.*
