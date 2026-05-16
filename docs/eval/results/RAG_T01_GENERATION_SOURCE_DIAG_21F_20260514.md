# RAG — GQ-T01 generation + source noise diagnosis (21F)

**Ngày:** 2026-05-14  
**Loại task:** READ-ONLY DIAGNOSIS — không sửa code/runtime config.  
**Môi trường:** Windows 10, Docker stack local, backend `http://localhost:8080`.

---

## 1. Document / tenant dùng để chẩn đoán

| Field | Value |
|--------|--------|
| Nguồn ID | `docs/eval/results/RAG_RUNTIME_VERIFY_21E2_SECTION7_20260514.md` |
| `CHATBOT_ID` / `widgetId` | `818e5680-718a-426f-8abe-dae586779fc2` |
| `DOCUMENT_ID` | `ea5cfde1-b84b-4d22-8636-c0514a51b4c6` |
| File golden | `RAG_GOLDEN_TEST_DOCUMENT.txt` |
| Widget key | Dùng key tạo trong 21E2 (operator local); **không** ghi full key trong file này. |

Document vẫn tồn tại (SQL + API chat thành công).

---

## 2. DB — inventory chunk liên quan bảng

**Query:** `document_id = UNHEX(REPLACE('ea5cfde1-b84b-4d22-8636-c0514a51b4c6','-',''))`  
**Filter:** `content LIKE '%Basic%'` OR `'%99000%'` OR `chunk_type LIKE 'table%'`

| chunk_index | order_index | chunk_type | section_title | table_id | len(content) | Preview |
|-------------|-------------|------------|---------------|----------|--------------|---------|
| 4 | 4 | `table_summary` | 4. Bảng gói dịch vụ… | `tbl_4_4` | 362 | Bảng trong section… + header markdown + `\| Basic \| 99000 \|` |
| 5 | 5 | `table_row_group` | 4. Bảng gói dịch vụ… | `tbl_4_4` | 180 | Full markdown table (Basic/Pro/Business + 99000…) |

**Footnote chunk:** `chunk_index=6` là `text` sau bảng (“**Lưu ý:** Giá là số nguyên…”), cùng section 4.

**Kết luận DB:** Evidence **Basic / 99000** có đầy đủ trong `table_summary` + `table_row_group`; không có dấu hiệu parse/chunk thiếu cho T01.

---

## 3. Qdrant — payload table (read-only)

**Filter:** `widgetId` + `document_id` (UUID string), `with_payload=true`, `with_vector=false`.

- Points `table_summary` / `table_row_group` có `section_title` = mục **4. Bảng gói dịch vụ…**, `table_id` = `tbl_4_4`, `text_segment` chứa `Basic` và `99000` (khớp MySQL).

**Kết luận Qdrant:** Vector payload không mất evidence bảng.

---

## 4. GQ-T01 gốc — 3 lần, session mới mỗi lần

**Câu hỏi:** `Giá gói Basic là bao nhiêu VNĐ theo bảng?`

| Run | `sources` count | ChunkText có `99000`? | Answer (semantic) | Verdict |
|-----|-----------------|------------------------|-------------------|---------|
| 1 | **3** | Có | Trả **99000** VNĐ + citation | **PASS** |
| 2 | **3** | Có | **“Tôi không tìm thấy thông tin này trong tài liệu.”** + citation | **FAIL** |
| 3 | **3** | Có | Giống run 2 | **FAIL** |

**Quan trọng:** Cùng một câu hỏi, **cùng số source (3)** và detector script xác nhận **ít nhất một** `chunkText` chứa `99000`/`Basic`, nhưng LLM **2/3** lần vẫn phủ nhận. → Không thể quy kết đơn thuần “retrieval miss” hay “context không có bảng”.

---

## 5. Biến thể T01 (cùng tenant)

| ID | Câu hỏi (rút gọn) | sources | Có 99000 trong chunkText? | Kết quả answer (semantic) |
|----|-------------------|---------|---------------------------|---------------------------|
| v1 | Theo bảng gói dịch vụ, Basic có giá bao nhiêu? | 3 | Có | Phủ nhận (FAIL) |
| v2 | Trong bảng, dòng Basic có giá là bao nhiêu? | **10** | Có | Trả bảng Markdown + **99000** (PASS) |
| v3 | Basic \| Giá là bao nhiêu? | **10** | Có | Trả **99000** (PASS) |
| v4 | Giá của gói Basic là 99000 hay 199000? | 3 | Có | Trả **99000** (PASS) |

**Đọc ý:**

- Câu **không** chứa “bao nhiêu” (v4) hoặc wording kích hoạt pool rộng (v2/v3 → 10 sources) dễ **PASS**.
- Câu gốc T01 + v1 (vẫn có “bao nhiêu”) thường **FAIL** trong mẫu chạy này; đồng thời pool nhỏ hơn (3 sources) — gợi ý **khác pipeline retrieval** (ít chunk trong context) chồng lên **lỗi generation**.

---

## 6. Backend logs (T01)

- `docker logs chatbot-backend --tail 400` trong phiên chủ yếu thấy **stack trace** / noise, **không** thấy rõ chuỗi `[RAG] Detected intent` / `[Chat]` cho từng request (encoding/filter).
- **Không** trích log dài; phần intent suy ra chủ yếu từ **source** `QueryAnalyzerService.java` (thứ tự rule) + so sánh behavior variants.

---

## 7. Phân tích PromptBuilder / context (từ source)

### 7.1 Cách dựng context gửi LLM

- `PromptBuilderService.buildUserPromptFromRetrievedContexts`: mỗi context → `[Source i]` + `Document` / `Section` / `Pages` / `Type` + **`Content:`** nguyên văn `ctx.getContent()`.
- Với chunk `table_row_group` / `table_summary`, content trong DB đã là markdown table (đã xác nhận SQL preview).
- **Không** có bước trong builder làm “flatten” bảng thành non-table trừ khi chunk type là `text` khác.

### 7.2 System prompt / guardrail

- Rule **#2** (system): nếu tài liệu tham khảo “hoàn toàn rỗng” → câu cố định *không tìm thấy*. Điều kiện này **không** áp dụng khi `contexts` non-empty (ChatService chỉ gọi LLM khi có context).
- Rule **#2b**: khi có source nhưng chỉ trả lời một phần — hướng dẫn trả lời theo phạm vi đã truy xuất (có thể tăng ngại trả giá cụ thể nếu model “thận trọng” sai).
- Khối **TABLE_LOOKUP** trong `buildQueryTypeInstruction` mạnh về đọc `table_summary` / `table_row_group` — **chỉ áp dụng khi `queryTypeHint` là `TABLE_LOOKUP`** (hoặc override `TABLE_LIKE` khi có chunk `text_table_like`).

### 7.3 Xung đột intent: COUNT_QUERY vs TABLE_LOOKUP

Trong `QueryAnalyzerService.analyze`, nhánh **`COUNT_QUERY`** được kiểm tra **trước** nhánh **`TABLE_LOOKUP`**, và keyword **`bao nhieu`** khớp câu T01 gốc.

→ Câu T01 gốc được classify **`COUNT_QUERY`**, không phải **`TABLE_LOOKUP`** như golden doc ghi.

Hệ quả:

- `queryTypeHint` gửi kèm user prompt là **`COUNT_QUERY`** (đếm / dedupe / liệt kê đối tượng) — **lệch** với nhu cầu “đọc ô giá trong bảng”.
- Checklist system vẫn có mục bảng, nhưng **instruction đầu prompt** đẩy LLM sang workflow đếm — dễ gây **mơ hồ** với câu “giá bao nhiêu”.

**Kết luận format bảng:** Không có bằng chứng “markdown hỏng trong prompt”; vấn đề chính là **hint loại câu hỏi sai** + **hành vi LLM**.

---

## 8. Source array — cơ chế & source noise

### 8.1 Nguồn `sources` trong API

- `ChatService.chat`: `sources = buildSourceDtos(contexts)` — **1:1** với list `RetrievedContext` sau retrieval (không cap riêng cho response).
- Mỗi `SourceDto` gồm `fileName`, `sectionTitle`, `pages`, `chunkType`, **`chunkText` = full content** của chunk.

### 8.2 Vì sao O01/O02 có 10 sources?

- Tenant eval chỉ có **10 chunk** / document; với query expanded / pool đủ lớn, `dedupeSortBudget` có thể trả **tối đa** `FINAL_LIMIT_EXPANDED` (=20) nhưng thực tế chỉ còn 10 chunk → API trả **10** source (full-window).
- **Không** phải “rerank chọn 10 citation riêng”; đúng hơn là **toàn bộ (hoặc gần toàn bộ) context** đang được mirror sang `sources`.

### 8.3 T01 với 3 sources

- Cùng một pipeline nhưng **pool context sau rerank/sort/budget** cho T01 gốc chỉ còn **3** chunk trong lần đo — khác với O01 (10). Giải thích hợp lý: **vector anchor + expansion + rerank** cho wording “bao nhiêu… theo bảng” chọn subset nhỏ hơn; không chứng minh thiếu bảng vì script vẫn thấy 99000 trong `chunkText` của source.

---

## 9. Root cause matrix (tóm tắt)

| Case | Evidence DB/Qdrant | Sources chứa 99000/Basic? | Context likely có bảng? | Answer đúng? | Noise | Primary tag |
|------|--------------------|---------------------------|---------------------------|--------------|-------|----------------|
| T01 orig (run 1) | Có | Có (3 src) | Có | PASS | Thấp | — |
| T01 orig (run 2–3) | Có | Có | Có | FAIL | Thấp | **GENERATION_CONTRADICTION** + **QUERY_ANALYZER** (COUNT hint) |
| T01 v1 | Có | Có | Có | FAIL | Thấp | **GENERATION** + **QUERY_ANALYZER** |
| T01 v2 | Có | Có | Có | PASS | Cao (10) | **SOURCE_PRESENTATION_NOISE** (UI) nhẹ; answer OK |
| T01 v3 | Có | Có | Có | PASS | Cao (10) | Giống v2 |
| T01 v4 | Có | Có | Có | PASS | Thấp | — |
| O01/O02 | Có | mixed | mixed | PASS OOS | **10** = full doc | **SOURCE_PRESENTATION_NOISE** + pool expanded |

**RETRIEVAL_MISS:** không phải nguyên nhân chính cho T01 fail (source vẫn chứa 99000).  
**CONTEXT_FORMAT_TABLE:** không thấy bảng bị strip ở PromptBuilder.  
**PROMPT_GUARDRAIL:** rule #2 “rỗng” không kích hoạt; rule #2b + **COUNT_QUERY** block có thể **làm mờ** hướng xử lý (nhẹ → trung).  
**RERANK:** có thể ảnh hưởng **kích thước pool** (3 vs 10); không xóa evidence trong các lần đo FAIL.

---

## 10. Đề xuất fix (chưa implement — cho task sau)

**Thứ tự đề xuất:**

1. **QueryAnalyzer (hoặc override hint trong ChatService)** — ưu tiên **TABLE_LOOKUP** khi có **cả** “bảng/bang” **và** token giá/gói/Basic/99000 (hoặc đảo thứ tự COUNT vs TABLE cho collision). *Task sau được phép sửa Java.*
2. **PromptBuilder** — nếu vẫn dùng COUNT_QUERY: thêm một dòng “nếu câu hỏi hỏi **giá/ô bảng**, áp dụng luật TABLE_LOOKUP” hoặc map sub-intent. *Task sau.*
3. **Generation** — giảm mâu thuẫn “không tìm thấy” khi `chunk_type` là table_* và content match entity (có thể prompt nhỏ hoặc JSON mode) sau khi (1)(2) ổn.
4. **Source cap / presentation** — tách “context cho LLM” vs “sources cho UI” (hoặc cap + dedup section) để O01/L01 không luôn 10 dòng. *Tách service layer sau.*
5. **Retrieval trim** — chỉ nếu sau (1)–(4) vẫn thấy pool quá rộng gây sai.

---

*File chẩn đoán read-only; không chứa secret đầy đủ.*
