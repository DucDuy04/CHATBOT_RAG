# RAG runtime verify 21E — sau parser fix 21D1 (TXT markdown ATX)

**Ngày chạy:** 2026-05-14  
**Môi trường:** Windows 10, Docker Desktop, repo `CHATBOT_RAG`  
**Stack:** `docker compose up --build -d` (backend image rebuild từ source hiện tại; MySQL + Qdrant + backend + frontend)

---

## 1. Identity phiên verify (không lộ secret)

| Field | Value |
|--------|--------|
| Chatbot name | `Golden Parser Verify 21E` |
| `CHATBOT_ID` | `186970d5-8aca-4607-9d51-c126631bbbc2` |
| `WIDGET_API_KEY` | `c381c5ac-…` (chỉ prefix; key đầy đủ lưu local operator) |
| `DOCUMENT_ID` | `ad80a626-e083-495e-b70f-c8cb1ac71957` |
| Golden file upload | `docs/eval/RAG_GOLDEN_TEST_DOCUMENT.txt` |

---

## 2. Build / health

| Bước | Kết quả | Ghi chú |
|------|---------|---------|
| `docker compose up --build -d` | OK | Backend rebuild ~2m; container recreate |
| `docker compose config -q` | OK | Đã chạy riêng sau verify |
| `GET http://localhost:8080/api/chatbots?page=0&size=1` | OK | Sau khi backend lên (lần đầu có thể cần chờ vài giây) |
| `GET http://localhost:6333/collections` | OK | Collection `documents` tồn tại |
| `Backend\.\mvnw.cmd -DskipTests compile` | **PASS** | Exit 0 |
| `.\mvnw.cmd -q test -Dtest=ParserAndOrderingTests` | **PASS** | Exit 0 (phiên 21E) |
| Env trong container | **GROQ_PRESENT / NOMIC_PRESENT** | `docker exec chatbot-backend sh -c 'test -n "$GROQ_API_KEY" && echo GROQ_PRESENT'` (không in giá trị) |

---

## 3. Upload & status

- `POST /api/documents/upload` — `files=@RAG_GOLDEN_TEST_DOCUMENT.txt`, `chatbotId=<CHATBOT_ID>` → **HTTP 200**
- Response: `chunkCount=9`, `status=INDEXED` ngay trong body upload.
- `GET /api/documents/{id}/status` → `INDEXED`, `chunkCount=9`, `progress=100`, `error=null`.

---

## 4. G1 — MySQL `document_sections`

**Truy vấn:** `document_id = UNHEX(REPLACE('<DOCUMENT_ID>','-',''))`, `--default-character-set=utf8mb4`.

**Inventory (order_index, title):**

| order_index | title (rút gọn ý) |
|-------------|-------------------|
| 0 | `General` (khối intro sau `#`) |
| 1 | `1. Thông tin chung` |
| 2 | `2. Chính sách hỗ trợ (…)` |
| 3 | `3. Quy trình xử lý yêu cầu (3 bước cố định)` |
| 4 | `4. Bảng gói dịch vụ (…)` |
| 5 | `5. Danh sách giới hạn (…)` |
| 6 | `6. Thông tin dễ gây nhầm (…)` |

**False-root patterns** (theo yêu cầu 21E): truy vấn `title LIKE` các mẫu `1. Phản hồi…`, `2. Mỗi phiên chat…`, `3. Không hỗ trợ can thiệp…`, `1. Bước 1…`, `2. Bước 2…`, `3. Bước 3…` → **0 row**.

**Lệch so checklist “đủ 7 mục ##”:** Không có row section riêng cho **`## 7. Phạm vi KHÔNG có trong tài liệu`** — nội dung mục 7 nằm trong **chunk** cuối nhưng `document_sections` chỉ tới mục 6. Đây là **PARTIAL** so kỳ vọn inventory đầy đủ 7 mục có tiêu đề riêng.

---

## 5. G1 — MySQL `document_chunks` (tiêu điểm)

| Kiểm tra | Kết quả |
|-----------|---------|
| Chunk chứa `Bước 2 — Phân loại` | `chunk_index=3`, `section_title` = mục **3. Quy trình…**, `heading_path_text` chứa quy trình → **đúng** |
| Chunk chứa `24 giờ làm việc` + `15 phút` | `chunk_index=2`, `section_title` = mục **2. Chính sách…** → **đúng** |
| `table_summary` / `table_row_group` | `chunk_index` 4–5, `section_title` = mục **4. Bảng gói dịch vụ** → **đúng**; **không** gắn nhầm sang tiêu đề kiểu “Bước 3” |
| `chunkCount` | 9 (API khớp SQL count chunk active) |

---

## 6. G1 — MySQL `document_tables`

- **1 row** cho document; `title` = mục 4 bảng; `markdown_content` chứa đủ: Basic, Pro, Business, 99000, 199000, 499000, **Ưu tiên** (và cấu trúc markdown bảng).

---

## 7. G1 — Qdrant

- **Collection:** `documents` (theo `application-docker.yml`).
- **Count** `POST /collections/documents/points/count` filter `must`: `widgetId` + `document_id` (string UUID) → **`count = 9`** — khớp `chunkCount`.
- **Count** theo chỉ `widgetId` tenant mới (chỉ doc golden) → **9** (trùng; tenant sạch một doc).
- **Scroll** (payload, không vector): mọi point có `chunk_type` + `section_title`; table chunks gắn mục 4; chunk quy trình gắn mục 3 — khớp MySQL. (Console PowerShell có thể garble Unicode; payload thật trong Qdrant UTF-8.)

---

## 8. G3 — Targeted chat (`POST /api/chat`, header `X-Widget-Key`, **session mới mỗi câu**)

Encoding JSON body: UTF-8 bytes + `[ordered]` để tránh lỗi 400 của `Invoke-RestMethod` mặc định.

| Case | sourceCount | Verdict | Tóm tắt answer | Tags |
|------|-------------|---------|----------------|------|
| **GQ-F04** | 1 | **PASS** | Bước 2 = phân loại; nhãn billing/technical/other; trong vòng **4 giờ làm việc**. Source: chunk mục 3. | — |
| **GQ-L01** | 9 | **PASS** (nội dung) / **PARTIAL** (gọi ý) | Liệt kê đủ 3 chính sách đúng văn bản. **9 sources** gồm bảng, General, mục 1… — nhiễu citation. | `SOURCE_ATTRIBUTION` |
| **GQ-L02** | 3 | **PASS** | Basic, Pro, Business; sources đều mục 4 + table chunks. | — |
| **GQ-T02** | 9 | **PASS** | Trả lời **Ưu tiên** cho Business. Nhiều source không cần thiết. | `SOURCE_ATTRIBUTION` (nhẹ) |
| **GQ-F03** | 3 | **PASS** | **500** lượt/tháng; footer section mục 4 — không còn attribution “Bước 3” sai như 21B/21C. | — |
| **GQ-T01** | 3 | **FAIL** | Câu trả lời: *“Tôi không tìm thấy thông tin này trong tài liệu”* trong khi **sources** chứa bảng với **99000** cho Basic — mâu thuẫn generation vs context. | `GENERATION`, `HALLUCINATION` (phủ nhận sai) |

**Chi tiết câu trả lời đầy đủ:** đã lưu tạm trong phiên chạy verify để đối chiếu; bảng mục 8 phản ánh đúng nội dung và `sourceCount` từ API.

---

## 9. Gate tổng hợp

| Gate | Verdict | Lý do ngắn |
|------|---------|------------|
| **G1 metadata** | **PARTIAL** | Core parser 21D1: false sections hết; Bước 2 / chính sách / bảng / Qdrant đúng. Thiếu **section row** cho `## 7`; chunk 8 gắn `section_title` mục 6 dù body có `## 7`. |
| **G3 targeted (6 case)** | **PARTIAL** | 5/6 PASS; **GQ-T01 FAIL** (generation). |

---

## 10. So sánh nhanh 21B / 21C / 21D1 → 21E

| Case | 21B/21C (tóm tắt checklist) | 21E runtime |
|------|---------------------------|-------------|
| GQ-F04 | FAIL / no context / lock sai | **PASS** |
| GQ-L01 | FAIL / nhiễu | **PASS** nội dung; citation vẫn rất rộng |
| GQ-L02 | FAIL | **PASS** |
| GQ-T02 | FAIL | **PASS** |
| GQ-F03 | PARTIAL (số đúng, section sai) | **PASS** (section mục bảng) |
| GQ-T01 | PARTIAL | **FAIL** (model phủ nhận dù context có 99000) |

**21D1:** chứng minh unit/parser in-memory; **21E** xác nhận ingest + DB + Qdrant + phần lớn golden chat runtime.

---

## 11. Kết luận gate (theo brief)

1. **Parser fix 21D1 có đủ chưa (runtime metadata)?** — **Gần đủ / PARTIAL:** ranh giới ATX và gắn chunk Bước 2 / bảng / chính sách **đúng**; thiếu section DB riêng cho `## 7` (edge doc cuối).
2. **Cần retrieval fix (21F)?** — **Có thể có lợi:** một số câu trả về **9 sources** (full-window) — gợi ý heading-lock / trim context theo relevance (ngoài scope 21E).
3. **Cần attribution / source formatting?** — **Có:** GQ-T01 mâu thuẫn “không tìm thấy” vs sources; GQ-L01/T02 nhiều source dư.
4. **Cần table-aware thêm?** — **Thấp hơn retrieval/generation cho T01:** bảng đã vào chunk + Qdrant; F03/L02/T02 đã trả lời đúng — lỗi T01 không giống lỗi parse bảng.

---

## 12. Checklist 21E (bản rút gọn ID bắt buộc)

Chi tiết đầy đủ được đồng bộ vào `docs/eval/RAG_BASELINE_CORE_CHECKLIST.md` (mục phụ **21E**).

| ID | Status |
|----|--------|
| G0-CMP-001 | PASS |
| G0-DCK-001 | PASS |
| G0-ENV-001 | PASS |
| G0-ENV-002 | PASS |
| G0-SEC-001 | PASS |
| G0-SCP-001 | PASS (tham chiếu 21D1; phiên 21E verify-only) |
| G1-UPL-001 … G1-QDR-003, G1-CNT-001 | Chủ yếu PASS; G1 metadata tổng **PARTIAL** vì mục `## 7` |
| G3-F04-001/002 | PASS |
| G3-L01-001/002 | PASS (002 file golden có; noise = attribution) |
| G3-L02-001/002 | PASS |
| G3-T02-001/002 | PASS |
| G3-F03-001/002 | PASS |
| G3-T01-001 | **FAIL** |
| G3-T01-002 | **FAIL / PARTIAL** (câu trả lời sai; source vẫn đúng file/section) |

---

*Tệp này phục vụ audit runtime 21E; không chứa API key đầy đủ.*
