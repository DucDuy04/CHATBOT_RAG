# CURSOR_REPORT_21E — Runtime verify sau parser fix 21D1

## 1. Mức độ hiểu task

- **~95%** — chạy đúng luồng Docker, API, MySQL, Qdrant, 6 câu golden, điền checklist, so baseline.
- **Chắc chắn:** lệnh đã chạy, kết quả HTTP/SQL/Qdrant/chat ghi nhận trung thực; không sửa Java runtime trong phiên.
- **Giả định nhỏ:** so sánh 21B/21C dựa trên bảng tóm tắt trong `RAG_BASELINE_CORE_CHECKLIST.md` và file diag 21C (không replay lại run 21B).

## 2. Tóm tắt yêu cầu

Xác minh runtime sau fix 21D1: rebuild stack, chatbot mới, upload `RAG_GOLDEN_TEST_DOCUMENT.txt`, chờ `INDEXED`, kiểm tra MySQL (sections/chunks/tables), Qdrant (count + payload), chạy GQ-F04/L01/L02/T02/F03/T01, so baseline, kết luận parser vs retrieval vs attribution vs table-aware. **Không** sửa code runtime trừ blocker rõ (không phát sinh).

## 3. Phạm vi đã làm

- `docker compose up --build -d` (rebuild backend image).
- Kiểm tra env Groq/Nomic trong container (boolean, không in secret).
- `POST /api/chatbots` → `POST /api/documents/upload` → poll `GET .../status`.
- SQL trên `document_sections`, `document_chunks`, `document_tables` (UUID `document_id` qua `UNHEX(REPLACE(...))`).
- Qdrant `points/count` + `points/scroll` (không lấy vector).
- 6 lần `POST /api/chat` (session UUID mới mỗi lần, UTF-8 JSON body).
- `.\mvnw.cmd -DskipTests compile` và `.\mvnw.cmd -q test -Dtest=ParserAndOrderingTests`.
- Tạo `docs/eval/results/RAG_RUNTIME_VERIFY_21E_20260514.md`, cập nhật `docs/eval/RAG_BASELINE_CORE_CHECKLIST.md` (mục phụ 21E).

## 4. Phạm vi không làm

- Không sửa `DocumentParserService`, retrieval, prompt, frontend, Docker config, `.gitignore`, schema, migration/backfill.
- Không chạy full `mvnw test` toàn bộ module (chỉ lớp parser test theo ngữ cảnh 21D1).
- Không chạy Frontend lint/build/widget build (ngoài scope 21E).

## 5. File đã đọc (tối thiểu)

| Path | Mục đích | Kết luận ngắn |
|------|----------|----------------|
| `docs/eval/RAG_BASELINE_CORE_CHECKLIST.md` | Endpoint + checklist | Đã bổ sung block 21E |
| `docs/eval/RAG_EVALUATION_RUNBOOK.md` | Chat/upload | `.txt` golden; `X-Widget-Key`; session |
| `docker-compose.yml` | Stack | Port 8080/3306/6333; env `GROQ_*`, `NOMIC_*` |
| `Backend/.../application-docker.yml` | Qdrant collection | `documents`, vector 768 |
| `Backend/.../SecurityConfig.java` | Auth | `/api/chat`, `/api/chatbots` permit; filter widget cho chat |
| `Backend/.../WidgetAuthFilter.java` | Chat header | `X-Widget-Key` UUID |
| `Backend/.../DocumentController.java` | Upload | `files` + `chatbotId` |
| `Backend/.../ChatbotController.java` | Create | `POST` body `name`, … |
| `Backend/.../ChatController.java` | Chat | `sessionId` + `message` bắt buộc |
| `Backend/.../DocumentSection.java` / `DocumentChunk.java` / `DocumentTable.java` | SQL column | `heading_path_text`, `section_title`, `markdown_content` |
| `Backend/.../EmbeddingService.java` | Qdrant payload keys | `document_id`, `widgetId`, `section_title`, … |
| `docs/eval/RAG_GOLDEN_TEST_DOCUMENT.txt` | Kỳ vọn section | Có `## 1`…`## 7` |
| `docs/eval/results/RAG_PARSER_FIX_21D1_20260514.md` | Bối cảnh 21D1 | Unit PASS; runtime NOT_RUN trước 21E |

*(Các file plan/template/21C report dài — đã tham chiếu theo checklist và kết quả runtime; không trích dài trong report này.)*

## 6. Môi trường chạy

- OS: Windows 10 (19045).
- Docker: `chatbot-backend`, `ragchatbot-mysql`, `ragchatbot-qdrant`, `chatbot-frontend`.
- API: `http://localhost:8080`, Qdrant HTTP: `http://localhost:6333`.

## 7. Env check

- `GROQ_API_KEY`, `NOMIC_API_KEY`: **có** trong container (kiểm tra presence-only).
- Không ghi giá trị key vào file kết quả/checklist.

## 8. Build / restart stack

- `docker compose up --build -d` — **PASS** (exit 0; backend Maven build trong image).

## 9. Upload golden

- `POST /api/documents/upload` — **PASS** (200); `DOCUMENT_ID=ad80a626-e083-495e-b70f-c8cb1ac71957`; `chunkCount=9`; `INDEXED`.

## 10. G1 metadata verification

### Sections

- Có mục 1–6 đúng `##`; **không** có false-root từ list `1.` chính sách / bước quy trình.
- **Thiếu** row `document_sections` cho **`## 7. Phạm vi…`** — nội dung mục 7 nằm trong chunk cuối gắn `section_title` mục 6.

### Chunks

- Bước 2 → section Quy trình: **PASS**.
- 24h + 15 phút → Chính sách: **PASS**.
- Table chunks → mục Bảng: **PASS**; không gắn bảng vào “Bước 3” sai: **PASS**.

### Tables

- Một bảng markdown đầy đủ Basic/Pro/Business và giá/Ưu tiên: **PASS**.

### Qdrant

- `count(document_id + widgetId) = 9` = `chunkCount`: **PASS**.
- Payload: table → mục 4; text quy trình → mục 3: **PASS** (scroll + khớp SQL).

## 11. G3 targeted result

| Case | Kết quả |
|------|---------|
| GQ-F04 | **PASS** — phân loại + nhãn + 4 giờ LV; 1 source đúng mục 3. |
| GQ-L01 | **PASS** nội dung — đủ 3 chính sách; **9 sources** (nhiễu) → tag attribution. |
| GQ-L02 | **PASS** — Basic, Pro, Business. |
| GQ-T02 | **PASS** — **Ưu tiên**. |
| GQ-F03 | **PASS** — **500**; footer section mục bảng. |
| GQ-T01 | **FAIL** — câu trả lời “không tìm thấy” mâu thuẫn sources có **99000**. |

## 12. Checklist status summary

- Đã thêm bảng **Phiên 21E** vào `docs/eval/RAG_BASELINE_CORE_CHECKLIST.md` với các ID G0/G1/G3 bắt buộc.
- Chi tiết đầy đủ trong `docs/eval/results/RAG_RUNTIME_VERIFY_21E_20260514.md`.

## 13. So sánh với baseline 21B/21C

- F04, L02, T02: từ **FAIL** (21B/21C) → **PASS** runtime 21E.
- L01: từ **FAIL** → **PASS** nội dung; vẫn nhiễu citation.
- F03: **PARTIAL** → **PASS** (section đúng mục bảng).
- T01: **PARTIAL** (số đúng) → **FAIL** (generation phủ nhận sai).

## 14. Có sửa runtime không?

- **Không** — không thay đổi Java/Spring, retrieval, prompt, Docker, frontend.

## 15. Nếu không sửa — no code diff

```diff
# Không có thay đổi source runtime trong task 21E.
# Chỉ thêm/cập nhật tài liệu kết quả + checklist:
#   docs/eval/results/RAG_RUNTIME_VERIFY_21E_20260514.md
#   docs/eval/RAG_BASELINE_CORE_CHECKLIST.md
#   reports/refactor/CURSOR_REPORT_21E_RUNTIME_VERIFY_AFTER_PARSER_FIX.md
```

## 16. Kết luận

- **Parser fix runtime:** **PARTIAL** — metadata cốt lõi (ATX, Bước 2, bảng, Qdrant) **PASS**; thiếu section DB cho `## 7` + gắn nhãn chunk cuối lệch section.
- **Case cải thiện rõ:** F04, L02, T02, F03 (+ L01 so tuyệt đối no-context trước đây).
- **Case còn fail:** T01 (generation); L01/T02 còn nhiều source (retrieval/presentation).

## 17. Rủi ro còn lại

- Model có thể **phủ nhận** fact dù context đủ (T01) — rủi ro P0 cho UX RAG.
- Cửa sổ source quá rộng (9 chunks) trên tenant một doc — tốn token/latency trên máy yếu.
- Section cuối tài liệu có thể **không tách** `document_sections` nếu logic parse/chunk biên giới vẫn thiếu cho `##` cuối file.

## 18. Đề xuất prompt tiếp theo (task sau, ngoài 21E)

1. **21D2 / parser-chunking:** tách **`## 7`** thành `document_sections` + `section_title` chunk khớp (nếu product yêu cầu inventory đủ 7 mục).
2. **21F retrieval:** giảm “full doc dump” vào context (re-rank / cap / heading-lock fallback có đo lường).
3. **Generation/attribution:** guard khi sources chứa bảng giá — tránh câu “không tìm thấy” mâu thuẫn; có thể cần điều chỉnh prompt **ngoài** phạm vi đã cấm 21E (ghi rõ trong task mới).

---

## Kiểm tra sau task

| Kiểm tra | Kết quả |
|-----------|---------|
| `docs/eval/results/RAG_RUNTIME_VERIFY_21E_20260514.md` | Tồn tại, không rỗng |
| `reports/refactor/CURSOR_REPORT_21E_RUNTIME_VERIFY_AFTER_PARSER_FIX.md` | Tồn tại, không rỗng |

| Command | Kết quả | Ghi chú |
|---------|---------|---------|
| `docker compose up --build -d` | PASS | Exit 0 |
| `docker compose config -q` | PASS | |
| `Backend\.\mvnw.cmd -DskipTests compile` | PASS | |
| `Backend\.\mvnw.cmd -q test -Dtest=ParserAndOrderingTests` | PASS | Exit 0 |
| `cd Frontend && npm run lint` | NOT RUN | Ngoài scope 21E |
| `cd Frontend && npm run build` | NOT RUN | Ngoài scope 21E |
| `cd Frontend && npm run build:widget` | NOT RUN | Ngoài scope 21E |
