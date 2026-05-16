# Cursor Report 20G — Core RAG E2E Runtime Execution (Verify)

**Ngày thực hiện:** 2026-05-14 (môi trường agent; log container ghi nhãn UTC 2026-05-13)  
**Scope:** Chạy kiểm thử E2E/manual theo `docs/RAG_CORE_FLOW_E2E_RUNBOOK.md`; **không** sửa runtime backend/frontend; không backfill/migration/cleanup toàn hệ thống.

---

## 1. Mức độ hiểu task

| Mục | Nội dung |
|-----|----------|
| **Hiểu task** | **98%** |
| **Chắc chắn** | Luồng đã chạy: env → compose → health → tạo chatbot → upload sample → status → MySQL → Qdrant count → chat → delete → MySQL/Qdrant sau xóa → chat sau xóa. Kết quả: **pass** trên các bước đã thực thi. |
| **Giả định** | `docker compose` nạp biến từ file `.env` ở root repo (chuẩn Compose) nên backend nhận được Groq/Nomic dù shell PowerShell không export. |
| **Thiếu dữ kiện** | Không giả lập document `FAILED` + `POST .../retry` (mục 11 — chỉ **expected**). |

---

## 2. Tóm tắt yêu cầu

Xác minh runtime luồng RAG cốt lõi: stack, ingest TXT mẫu, DB + vector, chat có `RAG-E2E-31415` và source đúng file, xóa document, kiểm tra soft-delete + Qdrant sạch theo filter, chat lại không còn source từ doc đã xóa; báo cáo evidence; không sửa code trừ blocker rõ (không phát sinh).

---

## 3. Môi trường chạy

| Hạng mục | Giá trị |
|----------|---------|
| **OS / shell** | Windows 10 (10.0.19045); PowerShell (Cursor agent). |
| **Stack** | Docker Compose từ `E:\chatbot-rag-workspace\CHATBOT_RAG\docker-compose.yml` — `docker compose up --build -d`. |
| **Backend URL** | `http://localhost:8080` |
| **Qdrant HTTP** | `http://localhost:6333` |
| **MySQL** | Host `localhost:3306`, DB `ragchatbot`, user `root` (password mặc định compose: `root` — **không** lưu secret trong repo; chỉ dùng để exec local). |
| **Container names** | `chatbot-backend`, `ragchatbot-mysql`, `ragchatbot-qdrant`, `chatbot-frontend` (khớp `docker compose ps`). |

---

## 4. Env check

### 4.1 PowerShell (theo prompt 20G)

- `GROQ_API_KEY` trong **shell**: **không** set (`Length`/null → false).  
- `NOMIC_API_KEY` trong **shell**: **không** set.  
- `COHERE_RERANK_ENABLED`: rỗng trong shell; khi `docker compose up` đã set tạm `$env:COHERE_RERANK_ENABLED = "false"` trong phiên shell agent (optional).

### 4.2 File `.env` (Compose interpolation)

- File `.env` **tồn tại** tại root repo.  
- Kiểm tra **không in secret**: pattern dòng `GROQ_API_KEY=...` và `NOMIC_API_KEY=...` có giá trị non-empty → **có** (boolean).  
- **Không** ghi full key; **không** mask partial key trong báo cáo (tránh lộ dài/prefix).

### 4.3 Kết luận env cho E2E

- Theo đúng lệnh kiểm tra **shell** thì key “thiếu trên shell”.  
- Runtime Docker vẫn nhận key qua `.env` + compose — upload + chat + embed **đã chạy thành công**, chứng minh container có credential hợp lệ cho provider.

---

## 5. Phạm vi đã chạy

1. `docker compose config -q` (trước đó khi `ps` rỗng) — implicit validate khi `up`.  
2. `docker compose up --build -d`  
3. `docker compose ps`  
4. Health: `GET /api/chatbots?page=0&size=1` → HTTP 200 sau khi backend `Started RagChatbotBeApplication`.  
5. `GET http://localhost:6333/collections` → collection `documents` tồn tại.  
6. `POST /api/chatbots` (tạo chatbot E2E).  
7. `POST /api/documents/upload` với `docs/samples/RAG_E2E_SAMPLE.txt`.  
8. `GET /api/documents/{id}/status`.  
9. MySQL: document + chunk count (trước và sau delete).  
10. Qdrant: `points/count` filter `widgetId` và `document_id` + `widgetId` (JSON gửi qua file tạm `%TEMP%` để tránh lỗi escape PowerShell).  
11. `POST /api/chat` (trước delete).  
12. `DELETE /api/documents/{id}`.  
13. `POST /api/chat` (sau delete, session mới — xem mục 7).  

---

## 6. Phạm vi chưa chạy và lý do

| Hạng mục | Lý do |
|----------|--------|
| **Retry document `FAILED`** | Không giả lập lỗi ingest an toàn; không chủ động phá env/provider. **Expected** theo 20E: purge Qdrant + hard-delete children rồi reprocess. |
| **Frontend / widget UI** | Runbook cho phép chỉ gọi API bằng curl/IRM. |
| **Cùng `sessionId` trước/sau delete** | `sessionId` lần chat đầu không được lưu trong log agent; lần sau dùng **session mới** để kiểm tra retrieval không còn doc đã xóa (tránh phụ thuộc lịch sử hội thoại cũ). |

---

## 7. Kết quả từng bước E2E

| Bước | Kết quả | Ghi chú ngắn |
|------|---------|--------------|
| Stack / compose | **PASS** | Build + start OK; MySQL healthy. |
| Backend sẵn sàng | **PASS** (sau ~74s boot) | Log: `Started RagChatbotBeApplication`. |
| Qdrant | **PASS** | Collection `documents` có. |
| Tạo chatbot | **PASS** | `POST /api/chatbots` — xem mục 8. |
| Upload sample | **PASS** | HTTP 200; `status` trong body: `INDEXED`. |
| Poll status | **PASS** | `INDEXED`, `chunkCount`: 1. |
| DB (trước delete) | **PASS** | 1 document, `chunk_count=1`, 1 row `document_chunks`. Cột `status` trong MySQL: `COMPLETED` (khác nhãn API `INDEXED` — mapping layer). |
| Qdrant (trước delete) | **PASS** | `count`: 1 (widget only và widget+document). |
| Chat (trước delete) | **PASS** | Answer chứa `RAG-E2E-31415`; `sources[0].fileName` = `RAG_E2E_SAMPLE.txt`. |
| Delete | **PASS** | HTTP 200; `{"success":true}`. |
| DB (sau delete) | **PASS** | `documents.deleted_at` không null; mọi chunk của doc: `deleted_at` đã set (0 row còn `deleted_at IS NULL`). |
| Qdrant (sau delete) | **PASS** | `count`: 0 với filter `document_id` + `widgetId`. |
| Chat (sau delete) | **PASS** | Answer: không tìm thấy trong tài liệu (VN); `sources`: mảng rỗng. |
| Retry FAILED | **NOT RUN** | Expected only (mục 6). |

---

## 8. Request/response chính (đã che secret)

### 8.1 Chatbot tạo mới

- **Request:** `POST /api/chatbots`  
  Body JSON: `name` = `E2E Chatbot 20G`, `description` = `runbook`, `domain` = `general`.  
- **Lưu ý kỹ thuật:** `curl.exe ... -d "{...}"` từ PowerShell trả **400** generic Spring (`Malformed JSON` / không parse được) — dùng `Invoke-RestMethod` + `ConvertTo-Json` → **200**.  
- **Response (trích):**  
  - `id`: `48913edd-aaeb-4b03-90c2-120eef9b2c0d` (dùng làm `chatbotId` / tenant / `widgetId` trong Qdrant).  
  - `apiKey` (Widget key cho header): **masked** `f23b...6d1` (UUID đầy đủ chỉ dùng trong lệnh local khi chạy E2E, **không** ghi full vào báo cáo).

### 8.2 Upload

- **Request:** `POST /api/documents/upload` multipart: `files=@...RAG_E2E_SAMPLE.txt`, `chatbotId=<id trên>`.  
- **Response:** HTTP 200; một phần tử JSON: `id` = `b85c1fa5-eee9-4fc2-ac12-76161fecbd89`, `filename` = `RAG_E2E_SAMPLE.txt`, `status` = `INDEXED`, `chunkCount` = 1.

### 8.3 Status

- `GET /api/documents/b85c1fa5-eee9-4fc2-ac12-76161fecbd89/status` → `INDEXED`, `chunkCount`: 1.

### 8.4 Chat (trước delete)

- **Header:** `X-Widget-Key: <apiKey UUID>`.  
- **Body:** `sessionId` (UUID mới), `message` (ASCII test: `Ma xac nhan RAG E2E la gi?`).  
- **Response:** answer chứa chuỗi `RAG-E2E-31415`; sources có `fileName`: `RAG_E2E_SAMPLE.txt`.

### 8.5 Delete

- `DELETE /api/documents/b85c1fa5-eee9-4fc2-ac12-76161fecbd89` → HTTP 200, `success`: true.

### 8.6 Chat (sau delete)

- Session mới; cùng nội dung câu hỏi dạng ASCII.  
- **Response:** answer kiểu “không tìm thấy trong tài liệu”; `sources`: `[]`.

---

## 9. Log lỗi chính (nếu có)

- **Không** có lỗi ứng dụng chặn luồng E2E trong các bước trên.  
- Giai đoạn đầu: `curl` tới backend khi JVM chưa bind → empty reply / HTTP 000 — **đã** resolve bằng đợi startup xong.  
- Cảnh báo log: SLF4J multiple bindings, Hibernate dialect deprecation — **không** chặn luồng.

---

## 10. Có sửa runtime không?

**Không.**

---

## 11–12. Sửa runtime / diff

- **Không có** thay đổi source Java/React/Docker trong task 20G.  
- **Không có** runtime diff.

---

## 13. Kết quả compile (nếu có chạy)

| Command | Kết quả |
|---------|---------|
| `cd Backend && ./mvnw -DskipTests compile` | **PASS** (exit 0, không sửa code trong task). |

Các mục không chạy trong scope verify: `mvnw test`, Frontend lint/build/widget build — **NOT RUN** (task verify runtime E2E, không bắt buộc frontend).

---

## 14. Kết luận

- **Luồng core RAG (upload → index → retrieve → chat → delete → không còn retrieve/doc trong source)**: **PASS** trên stack Docker local với DB/Qdrant hiện có.  
- **Blocker:** không có blocker cho luồng đã chạy.  
- **Lưu ý:** Trạng thái document trong MySQL hiển thị `COMPLETED` trong khi API status trả `INDEXED` — hành vi nhất quán nội bộ API; không đổi code trong task này.

---

## 15. Đề xuất prompt tiếp theo

1. **Nếu ưu tiên chất lượng:** prompt tối ưu retrieval (top-k, rerank Cohere optional, table-aware) — **sau** khi baseline E2E đã pass.  
2. **Retry FAILED:** prompt nhỏ — tạo một file TXT invalid hoặc mock lỗi embed **trong môi trường throwaway** rồi gọi `POST /api/documents/{id}/retry` và assert cleanup — chỉ khi chấp nhận dữ liệu test.  
3. **DevEx Windows:** bổ sung một dòng trong runbook: `curl.exe` + JSON inline trên PowerShell dễ 400 → nên dùng file `--data-binary @path` hoặc `Invoke-RestMethod` (tài liệu, không đổi API).

---

## Phụ lục — File đã đọc (theo prompt 20G)

| Path | Mục đích |
|------|----------|
| `docs/RAG_CORE_FLOW_E2E_RUNBOOK.md` | Quy trình E2E. |
| `reports/refactor/CURSOR_REPORT_20F_CORE_RAG_E2E_RUNBOOK.md` | Bối cảnh runbook 20F. |
| `reports/refactor/CURSOR_REPORT_20E_FAILED_DOCUMENT_RETRY_CLEANUP_FIX.md` | Retry FAILED + cleanup. |
| `reports/refactor/CURSOR_REPORT_20C_DELETED_DOCUMENT_CHUNK_VISIBILITY_AUDIT_FIX.md` | Soft-delete children + retrieval. |
| `docker-compose.yml` | Service names, ports, env. |
| `.env.example` | Danh sách biến. |
| `Backend/.../application-docker.yml` | Profile docker, Qdrant, models. |
| `Backend/.../application-dev.yml` | So sánh dev. |
| `DocumentController.java` | Upload/status/delete paths. |
| `ChatController.java` | Chat body + widget attribute. |
| `PublicChatController.java` | Public chat header `x-api-key`. |
| `ChatbotController.java` | `POST /api/chatbots`. |
| `WidgetAuthFilter.java` | `X-Widget-Key` vs public path. |

**Không thiếu file** trong danh sách bắt buộc trên.

---

## Phụ lục — ID artifact test (để audit local)

| Artifact | UUID |
|----------|------|
| Chatbot (tenant) | `48913edd-aaeb-4b03-90c2-120eef9b2c0d` |
| Document đã xóa | `b85c1fa5-eee9-4fc2-ac12-76161fecbd89` |

File báo cáo này **không rỗng**; đường dẫn: `reports/refactor/CURSOR_REPORT_20G_CORE_RAG_E2E_EXECUTION.md`.
