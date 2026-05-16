# RAG — PromptBuilder table guard (task 21H)

**Ngày:** 2026-05-15  
**Scope:** `PromptBuilderService` — instruction TABLE_LOOKUP + nhắn khi context có chunk/table-like.  
**Không sửa:** QueryAnalyzer, RagRetrievalService, ChatService, frontend, dependency, migration.

---

## 1. Mục tiêu fix

Giảm **generation contradiction**: câu tra cứu bảng dạng **lựa chọn/so sánh giá trị** (ví dụ T01 v4 trong 21G3) bị LLM trả “Tôi không tìm thấy…” dù context vẫn có bảng/evidence.

---

## 2. Before (từ 21G3)

Theo `docs/eval/results/RAG_QUERY_ANALYZER_RUNTIME_VERIFY_21G3_20260514.md`:

- T01 gốc ×5: **PASS** (`queryType=TABLE_LOOKUP`, sourceCount=3).
- T01 v4 `Giá của gói Basic là 99000 hay 199000?`: **FAIL** — deny dù source có bảng mục 4 (**GENERATION_CONTRADICTION**).
- Regression F03/L02/T02/C01/O01/O02: **PASS**.

---

## 3. Phân tích PromptBuilder trước sửa (tóm tắt)

| # | Câu hỏi | Kết luận |
|---|---------|------------|
| 1 | System prompt tạo thế nào? | Một `SYSTEM_PROMPT` text block cố định: nguyên tắc chỉ dùng context, rule #2 từ chối khi rỗng/không liên quan, checklist bảng (9–15), đếm, liệt kê, citation. |
| 2 | User prompt/context? | `buildUserPromptFromRetrievedContexts`: optional locked scope, `[LOẠI CÂU HỎI: …]` + `buildQueryTypeInstruction`, sau đó lặp `[Source i]` với Document/Section/Pages/Type/Content, history, `[CÂU HỎI HIỆN TẠI]`. |
| 3 | TABLE_LOOKUP instruction cũ? | 5 bullet: tên bảng → filter Source; tìm `table_summary`/`table_row_group`; gộp dòng; Markdown; không bỏ sót. **Thiếu**: câu dạng lựa chọn; cấm deny khi đã có ô/hàng/cột trả lời. |
| 4 | Rule nào dễ gây deny sai? | System **#2** (từ chối nếu “không có Source liên quan”) + thiếu hướng dẫn **ưu tiên đọc bảng** và **xử lý “A hay B?”** → model có thể coi câu hỏi “hai số” là không khớp / không dám chọn. |
| 5 | Table chunks trong prompt? | `Type: table_row_group` (v.v.) + `Content` (thường markdown `\|…\|`). |
| 6 | Có thể guard tổng quát? | Có — bổ sung instruction TABLE_LOOKUP + nhắn khi detect chunk type bảng hoặc markdown table trong `RetrievedContext` (không cần DB thêm). |
| 7 | System vs query-type? | Ưu tiên **nhánh TABLE_LOOKUP** (user prompt) để không làm rộng toàn bộ system prompt; nhắn context chỉ khi `TABLE_LOOKUP` + có evidence bảng. |
| 8 | Rủi ro OOS bịa? | Giữ bullet **#7** (không đoán ngoài Source); không ép trả lời khi không có hàng/cột. |
| 9 | Sửa retrieval/cap? | **Không** (theo task). |
| 10 | Minimal fix? | Mở rộng string `TABLE_LOOKUP` + một block nhắn có điều kiện + heuristic markdown nhẹ. |

---

## 4. Code change summary

- File: `PromptBuilderService.java`
  - `TABLE_LOOKUP` instruction: 7 bullet (ưu tiên nguồn bảng; hàng/cột/ô; lựa chọn khớp ô; cấm câu deny cụ thể khi đã có evidence; không bịa).
  - Khi `queryTypeHint` là `TABLE_LOOKUP` (ignore case) **và** `contextsContainTableLikeChunks` → thêm `TABLE_LOOKUP_TABLE_SOURCES_PRESENT_NOTE`.
  - Helper: `contextsContainTableLikeChunks`, `contentLooksLikeMarkdownTable` (dòng có ≥2 ký tự `|`).
- File: `PromptBuilderServiceTest.java` (mới): assert có guard/lựa chọn; không chứa literal golden Basic/99000/199000; COUNT_QUERY không dính banner bảng.

---

## 5. Prompt instruction mới (TABLE_LOOKUP — trích ý)

- Ưu tiên `table_summary`, `table_row_group`, `text_table_like`, và content markdown table.
- Xác định hàng (entity) + cột (thuộc tính) → đọc ô.
- Câu nhiều lựa chọn: chọn khớp ô; không suy diễn ngoài ô.
- **Không** dùng câu “Tôi không tìm thấy thông tin này trong tài liệu.” khi context đã có hàng/cột/ô trả lời trực tiếp (kể cả lựa chọn).
- Nhắn có điều kiện: phải đọc Source bảng trước khi kết luận không có thông tin.

**Không hardcode:** Basic, Pro, Business, 99000, 199000.

---

## 6. Compile / test

| Command | Kết quả |
|---------|---------|
| `Backend\.\mvnw.cmd -DskipTests compile` | **PASS** |
| `Backend\.\mvnw.cmd -Dtest=PromptBuilderServiceTest test` | **PASS** |

---

## 7. Runtime targeted (T01 gốc, T01 v4, regression)

**NOT RUN** trong phiên sửa code này (không có xác nhận Docker + API chat + tenant eval trong cùng phiên).

**Kỳ vọng sau khi deploy:** chạy lại checklist ngắn 21G3 (T01 gốc ×3, T01 v4 ×3, F03, L02, T02, C01, O01, O02) và cập nhật verdict.

---

## 8. T01 v4

| Trạng thái | Ghi chú |
|------------|---------|
| **Chưa verify runtime** | Fix hướng vào prompt; cần LLM thật để xác nhận 3/3 PASS. |

---

## 9. Regression (design-level)

- Chỉ thêm text khi `TABLE_LOOKUP` (+ banner khi có table-like context).
- `COUNT_QUERY` / các hint khác: không đổi behavior ngoài TABLE_LOOKUP path.

---

## 10. Quyết định

| Verdict | Lý do |
|---------|--------|
| **PARTIAL** (đến khi có runtime) | Unit test + compile **PASS**; logic đúng brief 21H; **chưa** có bằng chứng LLM runtime cho T01 v4. |

---

*Không chứa API key / widget key đầy đủ.*
