# RAG runtime verify 21E2 — sau parser fix 21D2 (section 7 ATX)

**Ngày chạy:** 2026-05-14  
**Môi trường:** Windows 10 (19045), Docker Desktop, repo `CHATBOT_RAG`  
**Git HEAD (tham chiếu):** `a014357`  
**Phạm vi task:** VERIFY ONLY — không sửa Java/retrieval/prompt/frontend/Docker/`.gitignore`.

---

## 1. Môi trường & stack

| Mục | Giá trị |
|-----|----------|
| Lệnh stack | `docker compose up --build -d` (full stack: mysql, qdrant, backend, frontend) |
| `docker compose config -q` | Exit **0** |
| `docker compose ps` | `chatbot-backend`, `ragchatbot-mysql`, `ragchatbot-qdrant`, `chatbot-frontend` — **Up** |
| API backend | `http://localhost:8080` |
| Qdrant HTTP | `http://localhost:6333` |
| Collection | `documents` (theo `application-docker.yml`) |

**Env trong container `chatbot-backend` (chỉ boolean, không in secret):**

- `GROQ_API_KEY`: **present**
- `NOMIC_API_KEY`: **present**

**Health:**

- `GET http://localhost:8080/api/chatbots?page=0&size=1` → **HTTP 200**
- `GET http://localhost:6333/collections` → response chứa collection **`documents`**

---

## 2. Build / compile / test (host, không đổi code trong task)

| Command | Kết quả | Ghi chú |
|---------|---------|---------|
| `Backend\.\mvnw.cmd -DskipTests compile -q` | **PASS** (exit 0) | Xác nhận workspace build được |
| `Backend\.\mvnw.cmd -q -Dtest=ParserAndOrderingTests test` | **PASS** (exit 0) | Log golden: `markdownAtx=true`, `Sections created: 8`, `totalChunks=10` |
| `cd Frontend && npm run lint` | **NOT RUN** | Ngoài scope verify |
| `cd Frontend && npm run build` | **NOT RUN** | Ngoài scope |
| `cd Frontend && npm run build:widget` | **NOT RUN** | Ngoài scope |

---

## 3. Identity phiên verify (không lộ secret)

| Field | Value |
|--------|--------|
| Chatbot name | `Golden Section7 Verify` |
| `CHATBOT_ID` / `widgetId` (Qdrant) | `818e5680-718a-426f-8abe-dae586779fc2` |
| `WIDGET_API_KEY` | `5c1b9f9e-…` (chỉ prefix; key đầy đủ chỉ lưu local operator) |
| `DOCUMENT_ID` | `ea5cfde1-b84b-4d22-8636-c0514a51b4c6` |
| Golden file | `docs/eval/RAG_GOLDEN_TEST_DOCUMENT.txt` |

**Lưu ý API tạo chatbot:** `POST /api/chatbots` với JSON thuần qua `curl.exe -d "..."` trên PowerShell 5.1 dễ **400**; dùng `Invoke-RestMethod` + `ConvertTo-Json` từ object → **200** (đã dùng trong phiên).

---

## 4. Upload & status

| Bước | Kết quả |
|------|---------|
| `POST /api/documents/upload` | **HTTP 200** — multipart `files=@RAG_GOLDEN_TEST_DOCUMENT.txt`, `chatbotId=<CHATBOT_ID>` |
| Response (canonical) | Mảng 1 phần tử `DocumentResponse`: `status=INDEXED`, `chunkCount=10`, `progress=100`, `error=null` |
| `GET /api/documents/{id}/status` | Không cần poll thêm — upload response đã **INDEXED** |

---

## 5. G1 — MySQL `document_sections`

**Filter:** `document_id = UNHEX(REPLACE('<DOCUMENT_ID>','-',''))`

| order_index | title (rút gọn) |
|-------------|-----------------|
| 0 | `General` |
| 1 | `1. Thông tin chung` |
| 2 | `2. Chính sách hỗ trợ (…)` |
| 3 | `3. Quy trình xử lý yêu cầu (3 bước cố định)` |
| 4 | `4. Bảng gói dịch vụ (…)` |
| 5 | `5. Danh sách giới hạn (…)` |
| 6 | `6. Thông tin dễ gây nhầm (…)` |
| 7 | **`7. Phạm vi KHÔNG có trong tài liệu (dùng cho câu hỏi out-of-scope)`** |

**False-root patterns** (đếm `document_sections` theo từng mẫu title): **0 row** cho tất cả:

- `1. Phản hồi yêu cầu…`
- `2. Mỗi phiên chat…`
- `3. Không hỗ trợ can thiệp…`
- `1. Bước 1…` / `2. Bước 2…` / `3. Bước 3…`

---

## 6. G1 — MySQL `document_chunks`

**`COUNT(*)` active chunks:** **10** (khớp API).

| Kiểm tra | Kết quả |
|-----------|---------|
| Chunk chứa `Bước 2 — Phân loại` | `chunk_index=3`, `chunk_type=text`, `section_title` = mục **3. Quy trình…**, `heading_path_text` chứa **Quy trình** → **PASS** |
| Chunk chứa `24 giờ làm việc` + `15 phút` | `chunk_index=2`, `section_title` = mục **2. Chính sách…** → **PASS** |
| `table_summary` / `table_row_group` | `chunk_index` 4–5, `section_title` = mục **4. Bảng gói dịch vụ** → **PASS** |
| Chunk chứa `USD/VND` / `CEO` / `Mã chứng khoán` | `chunk_index=9`, `chunk_type=text_table_like`, `section_title` = **`7. Phạm vi KHÔNG có trong tài liệu…`**, `heading_path_text` chứa **Phạm vi** → **PASS** |
| Table chunk gắn nhầm title kiểu chỉ **Bước 3** | Không thấy; bảng gắn mục 4 → **PASS** |

---

## 7. G1 — MySQL `document_tables`

| Mục | Kết quả |
|-----|---------|
| Số bảng | **1** row |
| `title` | Khớp mục **4. Bảng gói dịch vụ…** |
| `markdown_content` | Có **Basic**, **Pro**, **Business**, **99000**, **Ưu tiên** (và cấu trúc markdown) → **PASS** |

---

## 8. G1 — Qdrant

| Kiểm tra | Kết quả |
|-----------|---------|
| `POST …/collections/documents/points/count` filter `widgetId` + `document_id` (UUID string) | **`count = 10`** — khớp `chunkCount` / SQL |
| Count chỉ `widgetId` (tenant mới chỉ 1 doc) | **`count = 10`** |
| Payload chunk quy trình (`chunkIndex=3`) | `section_title` mục **3. Quy trình…**; `text_segment` chứa **Bước 2 — Phân loại** → **PASS** |
| Payload table chunks | `section_title` mục **4. Bảng gói dịch vụ**; `chunk_type` `table_summary` / `table_row_group` → **PASS** |
| Payload chunk mục 7 (`chunkIndex=9`, `section_id=sec_7`) | `section_title` và `heading_path_text` chứa **Phạm vi** / **KHÔNG có trong tài liệu**; `text_segment` chứa **USD/VND**, **CEO**, **Mã chứng khoán** → **PASS** |
| Table chunk `section_title` = **Bước 3** (sai ngữ cảnh) | **Không có** → **PASS** |

**Không** dump vector trong phiên verify.

---

## 9. Optional — GQ-O01 / GQ-O02 (`POST /api/chat`, header `X-Widget-Key`, session UUID mới mỗi câu)

**Lưu ý:** DTO `ChatResponse` **không** có field `sourceCount`; số source lấy từ `len(sources)`.

| Case | Câu hỏi | Tóm tắt answer | Sources | Cite mục 7? | Verdict |
|------|---------|----------------|---------|-------------|---------|
| **GQ-O01** | `Tỷ giá USD/VND hôm nay là bao nhiêu?` | Từ chối: không có thông tin tỷ giá **trong tài liệu** (không đưa số tỷ giá bịa). | **10** sources (full-window), trong đó có entry **mục 7** | Có (mục 7 trong danh sách source) | **PASS** (xử lý OOS đúng hướng; vẫn nhiều source như pattern 21E) |
| **GQ-O02** | `CEO của AlphaDemo tên đầy đủ là gì?` | Từ chối: không có thông tin **trong tài liệu** (không bịa tên CEO). | **10** sources, có mục 7 | Có | **PASS** |

Console PowerShell có thể hiển thị mojibake Unicode cho `answer`; nội dung thật từ API là tiếng Việt UTF-8 (đã đối chiếu cấu trúc câu + danh sách `sectionTitle`).

---

## 10. Checklist status summary (G0 / G1 + mục mới)

| ID | Status | Evidence ngắn |
|----|--------|----------------|
| G0-CMP-001 | **PASS** | `mvnw -DskipTests compile` |
| G0-DCK-001 | **PASS** | `docker compose config -q` |
| G0-ENV-001 | **PASS** | `docker exec` → GROQ present |
| G0-ENV-002 | **PASS** | `docker exec` → NOMIC present |
| G0-SEC-001 | **PASS** | Report không dán key đầy đủ |
| G0-SCP-001 | **PASS** | Task verify-only: không phát sinh diff code trong phiên |
| G1-UPL-001 | **PASS** | Upload 200, doc mới |
| G1-STS-001 | **PASS** | `INDEXED`, `chunkCount=10` |
| G1-MDH-001 | **PASS** | Ranh giới `##` đủ mục 2–4 + mục 7 |
| G1-SEC-001 … G1-SEC-005 | **PASS** | Không false list; có Quy trình + Bảng |
| **NEW** Section `7. Phạm vi KHÔNG có trong tài liệu` | **PASS** | Row `order_index=7` |
| G1-CHK-001 … G1-CHK-003 | **PASS** | Bước 2 → Quy trình; 24h+15phút → Chính sách |
| **NEW** Chunk USD/CEO/Mã CK → section 7 | **PASS** | `chunk_index=9` |
| G1-TBL-001 … G1-TBL-003 | **PASS** | 1 bảng; table chunks → mục 4; không “Bước 3” sai cho bảng |
| G1-QDR-001 … G1-QDR-003 | **PASS** | count=10; payload table + quy trình đúng |
| **NEW** Qdrant mục 7 `section_title` / `heading_path_text` chứa **Phạm vi** | **PASS** | Scroll payload `chunkIndex=9` |
| G1-CNT-001 | **PASS** | 10 chunks — đúng kỳ vọng sau 21D2 |
| GQ-O01 | **PASS** | Không bịa tỷ giá |
| GQ-O02 | **PASS** | Không bịa CEO |

**G1 metadata tổng (runtime sau 21D2):** **PASS** (trước đây 21E **PARTIAL** vì thiếu section 7).

---

## 11. So sánh trước / sau (21E vs 21E2 vs 21D2 unit)

| Tiêu chí | 21E runtime (`RAG_RUNTIME_VERIFY_21E_20260514.md`) | 21E2 runtime (phiên này) | 21D2 unit (`ParserAndOrderingTests`) |
|-----------|-----------------------------------------------------|----------------------------|----------------------------------------|
| `document_sections` | 7 hàng mục 1–6 + General; **thiếu mục 7** | **8** hàng: General + 1…**7** | 8 sections |
| `chunkCount` / Qdrant | **9** | **10** | 10 chunks |
| Chunk `USD/VND` metadata | Gắn mục **6** | Gắn mục **7** | Gắn mục 7 |
| Regression Bước 2 / bảng / false list | PASS | PASS | PASS |

---

## 12. Kết luận gate

1. **G1 metadata sau 21D2 (runtime):** **PASS** — đủ section DB cho mục 7; chunk & Qdrant payload khớp; không regress false section / Bước 2 / bảng.
2. **Cần sửa parser thêm cho mục 7 (golden TXT ATX)?** **Không** — runtime khớp unit 21D2.
3. **Bước tiếp theo đề xuất:** chuyển trọng tâm sang **GQ-T01** (generation phủ nhận sai dù context có bảng) và/hoặc **giảm nhiễu source** (retrieval / presentation) — ngoài scope parser G1.

---

*Tệp phục vụ audit runtime 21E2; không chứa API key đầy đủ.*
