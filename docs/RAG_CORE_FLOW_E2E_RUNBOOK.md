# Runbook — Kiểm thử E2E / manual luồng RAG chính (DB sạch)

**Phiên bản tài liệu:** 1.0 (task 20F)  
**Đối tượng:** Người chạy thủ công trên môi trường test (có thể xóa sạch MySQL / Qdrant / `uploads/`).  
**Lưu ý:** Tài liệu này **không** thay thế chạy CI; **chưa** xác minh runtime trong phiên soạn thảo trừ khi bạn tự chạy các bước dưới đây.  
**Git:** Trong repo này, các file dưới `docs/` mặc định bị ignore trừ whitelist trong `.gitignore` — sau khi thêm runbook/sample, dùng `git add docs/RAG_CORE_FLOW_E2E_RUNBOOK.md docs/samples/ ...` nếu bạn muốn commit.

---

## 1. Mục tiêu kiểm thử

Xác nhận end-to-end (hoặc gần E2E) các bước:

1. Stack chạy (MySQL, Qdrant, backend; frontend tùy chọn).  
2. Có chatbot/widget (tenant) hợp lệ và **API key** để gọi chat.  
3. Upload TXT nhỏ → document trạng thái xử lý xong.  
4. DB có bản ghi document + chunk (và section/table nếu parser tạo).  
5. Qdrant có collection và (sau upload thành công) có point gắn `widgetId` / `document_id`.  
6. Chat trả lời đúng nội dung file và `sources` có file vừa upload.  
7. Xóa document → soft-delete document + children.  
8. Chat lại **không** còn dùng context/doc đã xóa.  
9. (Tùy chọn) Retry document `FAILED` theo logic sau 20E.

---

## 2. Điều kiện trước khi chạy

- Repo `CHATBOT_RAG` đã clone; có quyền chạy Docker hoặc MySQL + Qdrant + backend local.  
- Biết rõ đang dùng **docker** (`SPRING_PROFILES_ACTIVE=docker`) hay **dev** (`dev`) để chọn host MySQL/Qdrant (xem mục 5).  
- Có khóa API **Groq** và **Nomic** hợp lệ (upload + chat + embed sẽ fail nếu thiếu/sai).  
- **Cảnh báo:** Các lệnh SQL xóa dữ liệu ở mục 4 chỉ dùng khi bạn chấp nhận **mất toàn bộ dữ liệu test** trong DB đó.

---

## 3. Env cần có

| Thành phần | Bắt buộc | Ghi chú (theo `docker-compose.yml` + `application-docker.yml` / `application-dev.yml`) |
|-------------|-----------|----------------------------------------------------------------------------------------|
| MySQL 8 | Có | Docker: host `localhost`, port `3306`, DB `ragchatbot`, user `root`, password `root`. |
| Qdrant | Có | HTTP `http://localhost:6333`, collection mặc định `documents`, vector size `768`. |
| Backend Spring Boot | Có | Port `8080`. Profile `docker` trong compose; local dev thường `dev`. |
| Frontend | Không bắt buộc | Docker map `5173:80`; có thể gọi API trực tiếp bằng `curl`. |
| `GROQ_API_KEY` | Có (chat) | `${GROQ_API_KEY}` trong compose. |
| `NOMIC_API_KEY` | Có (embed) | `${NOMIC_API_KEY}` trong compose. |
| `COHERE_API_KEY` / `COHERE_RERANK_ENABLED` | Không bắt buộc | Rerank optional (`application-docker.yml` mặc định `false`). |

File gợi ý biến môi trường: `.env.example` ở root repo; backend đọc biến qua Spring (xem `application-docker.yml`).

---

## 4. Cách clean dữ liệu test thủ công

**CHỈ DÙNG KHI LÀ DỮ LIỆU TEST — KHÔNG CHẠY TRÊN PRODUCTION.**

### 4.1 MySQL (xóa nội dung các bảng liên quan RAG/chat — thứ tự gợi ý tránh FK)

Kết nối: `mysql -h 127.0.0.1 -P 3306 -u root -p` (password `root` nếu dùng compose mặc định).

```sql
USE ragchatbot;

-- Tùy schema thực tế: có thể cần xóa chat trước document.
SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE chat_feedbacks;
TRUNCATE TABLE chat_messages;
TRUNCATE TABLE chat_sessions;
TRUNCATE TABLE document_chunks;
TRUNCATE TABLE document_tables;
TRUNCATE TABLE document_sections;
TRUNCATE TABLE documents;
SET FOREIGN_KEY_CHECKS = 1;
```

Nếu `TRUNCATE` báo lỗi FK, hãy xóa theo thứ tự `chat_feedbacks` → `chat_messages` → `chat_sessions` → `document_chunks` → `document_tables` → `document_sections` → `documents` (hoặc dùng `DELETE` có điều kiện). **Không** chạy trên DB production.

### 4.2 Qdrant (xóa collection hoặc toàn bộ points)

Nhẹ nhất để test sạch: xóa collection `documents` rồi để backend tạo lại khi khởi động (`QdrantConfig` có `ApplicationRunner` tạo collection nếu thiếu).

**Git Bash / Linux / macOS:**

```bash
curl.exe -s -X DELETE "http://localhost:6333/collections/documents"
```

Nếu backend đang chạy, khởi động lại backend hoặc đợi runner tạo lại collection.

### 4.3 Thư mục upload trên máy host

Theo `app.upload-dir: ./uploads` (tương đối thư mục làm việc của **process** Java — với Docker thường là trong container; với dev local là thư mục `Backend/` khi chạy từ đó).

- Dev local: xóa hoặc dọn `Backend/uploads/` (nếu tồn tại).  
- Docker: `docker exec -it chatbot-backend sh` rồi kiểm tra `/app` hoặc working dir trong `Backend/Dockerfile` — chỉ xóa nếu bạn chắc đường dẫn.

---

## 5. Cách start stack

### 5.1 Docker Compose (khuyến nghị cho E2E gần production)

Từ **root** repo (nơi có `docker-compose.yml`):

**Git Bash:**

```bash
cd /e/chatbot-rag-workspace/CHATBOT_RAG
export GROQ_API_KEY="your-groq-key"
export NOMIC_API_KEY="your-nomic-key"
export COHERE_API_KEY=""
export COHERE_RERANK_ENABLED="false"
docker compose up --build -d
```

**Windows PowerShell:**

```powershell
Set-Location "E:\chatbot-rag-workspace\CHATBOT_RAG"
$env:GROQ_API_KEY = "your-groq-key"
$env:NOMIC_API_KEY = "your-nomic-key"
$env:COHERE_API_KEY = ""
$env:COHERE_RERANK_ENABLED = "false"
docker compose up --build -d
```

Compose map: backend `8080`, mysql `3306`, qdrant `6333`, frontend `5173` (xem `docker-compose.yml`).

### 5.2 Dev local (backend + MySQL + Qdrant đã chạy sẵn trên localhost)

```powershell
Set-Location "E:\chatbot-rag-workspace\CHATBOT_RAG\Backend"
$env:SPRING_PROFILES_ACTIVE = "dev"
.\mvnw.cmd spring-boot:run
```

Cần MySQL/Qdrant đúng `application-dev.yml` (`localhost:3306`, `localhost:6333`).

---

## 6. Health checks

**Backend (không dùng Actuator trong `pom.xml` hiện tại — ưu tiên endpoint có sẵn):**

```bash
curl.exe -s -i "$BASE/api/chatbots?page=0&size=1"
```

Thay `$BASE` bằng `http://localhost:8080` hoặc set biến như các mục trên. Kỳ vọng: HTTP **200**.

**Qdrant:**

```bash
curl.exe -s http://localhost:6333/collections
```

**MySQL:**

```bash
docker exec ragchatbot-mysql mysqladmin ping -h 127.0.0.1 -uroot -proot
```

---

## 7. Chuẩn bị sample TXT

File mẫu trong repo: `docs/samples/RAG_E2E_SAMPLE.txt`

Nội dung có câu kiểm thử cố định: **Mã xác nhận RAG E2E là RAG-E2E-31415.**  
Dùng câu hỏi chat: `Mã xác nhận RAG E2E là gì?` — kỳ vọng answer chứa `RAG-E2E-31415` (sau bước upload + index).

---

## 8. Tạo / xác nhận widget (chatbot)

Theo source:

- `POST /api/chatbots` — tạo chatbot (permitAll trong `SecurityConfig`).  
- Response `ChatbotResponse`: field `id` là **UUID chatbot/widget** (tenant); field `apiKey` chỉ có khi **tạo mới** (dùng cho header chat).

**Git Bash** (từ root repo, `curl` là `curl.exe` trên Windows hoặc `curl` trên Linux):

```bash
BASE="http://localhost:8080"
curl.exe -s -X POST "$BASE/api/chatbots" \
  -H "Content-Type: application/json" \
  -d "{\"name\":\"E2E Chatbot\",\"description\":\"runbook\",\"domain\":\"general\"}"
```

Lưu JSON trả về:

- `CHATBOT_ID` = giá trị `id` (UUID).  
- `WIDGET_API_KEY` = giá trị `apiKey` (UUID string). **Không commit key lên git.**

**PowerShell** (tạo biến sau khi copy từ response):

```powershell
$BASE = "http://localhost:8080"
$CHATBOT_ID = "<paste-uuid-from-id>"
$WIDGET_API_KEY = "<paste-apiKey>"
```

**Chat / stream:** `WidgetAuthFilter` đọc header **`X-Widget-Key`** với giá trị **apiKey** (không phải `id`).  
**Public chat:** `POST /api/public/chat` dùng header **`x-api-key`** (cùng giá trị apiKey).

---

## 9. Upload document

### 9.1 Canonical upload (multipart)

Theo `DocumentController`: `POST /api/documents/upload`, `multipart/form-data`, field `files` (có thể nhiều file), tenant qua **`chatbotId`** hoặc **`widgetId`** (cùng UUID với `CHATBOT_ID`).

**Git Bash** (đường dẫn file tương đối từ root repo):

```bash
BASE="http://localhost:8080"
CHATBOT_ID="<your-chatbot-uuid>"
curl.exe -s -X POST "$BASE/api/documents/upload" \
  -F "files=@docs/samples/RAG_E2E_SAMPLE.txt" \
  -F "chatbotId=$CHATBOT_ID"
```

**PowerShell:**

```powershell
$BASE = "http://localhost:8080"
$CHATBOT_ID = "<your-chatbot-uuid>"
curl.exe -s -X POST "$BASE/api/documents/upload" `
  -F "files=@docs/samples/RAG_E2E_SAMPLE.txt" `
  -F "chatbotId=$CHATBOT_ID"
```

Lưu `DOCUMENT_ID` từ phần tử đầu mảng JSON (field `id`) hoặc từ log backend.

### 9.2 Legacy upload (một file, widget trong path)

`POST /api/documents/upload/{widgetId}` với param `file`:

```bash
curl.exe -s -X POST "$BASE/api/documents/upload/$CHATBOT_ID" -F "file=@docs/samples/RAG_E2E_SAMPLE.txt"
```

---

## 10. Poll document status

`GET /api/documents/{id}/status` — theo `DocumentService.getDocumentStatusDto`, trường `status` trả về dạng FE: **`INDEXED`** khi backend `COMPLETED`; **`PROCESSING`** khi `PENDING`/`PROCESSING`; **`FAILED`** khi lỗi.

**Git Bash:**

```bash
DOC_ID="<document-uuid>"
curl.exe -s "$BASE/api/documents/$DOC_ID/status"
```

Poll thủ công vài lần (không cần vòng lặp stress); kỳ vọng cuối: `"status":"INDEXED"`, `chunkCount` > 0.

---

## 11. SQL checks sau upload

**CHỈ DÙNG KHI LÀ DỮ LIỆU TEST.**

```sql
USE ragchatbot;

-- Document vừa upload (UUID có thể hiển thị binary — dùng BIN_TO_UUID nếu cần, MySQL 8+)
SELECT BIN_TO_UUID(id) AS id, file_name, status, chunk_count, deleted_at, file_path
FROM documents
ORDER BY created_at DESC
LIMIT 5;

SELECT COUNT(*) AS chunk_rows FROM document_chunks WHERE deleted_at IS NULL;

SELECT COUNT(*) AS section_rows FROM document_sections WHERE deleted_at IS NULL;

SELECT COUNT(*) AS table_rows FROM document_tables WHERE deleted_at IS NULL;
```

Kỳ vọng: một document `COMPLETED` (cột enum), `chunk_count` khớp số chunk; chunk > 0; section/table có thể = 0 với TXT đơn giản.

---

## 12. Qdrant checks sau upload

Collection name: **`documents`** (`application-docker.yml` / `application-dev.yml`).

**Kiểm tra collection tồn tại:**

```bash
curl.exe -s "http://localhost:6333/collections/documents"
```

**Đếm point theo tenant (widgetId = UUID chatbot, dạng string):**

```bash
WIDGET_UUID="<same-as-CHATBOT_ID>"
curl.exe -s -X POST "http://localhost:6333/collections/documents/points/count" \
  -H "Content-Type: application/json" \
  -d "{\"filter\":{\"must\":[{\"key\":\"widgetId\",\"match\":{\"value\":\"$WIDGET_UUID\"}}]},\"exact\":true}"
```

**Đếm point theo document:**

```bash
DOC_ID="<document-uuid>"
curl.exe -s -X POST "http://localhost:6333/collections/documents/points/count" \
  -H "Content-Type: application/json" \
  -d "{\"filter\":{\"must\":[{\"key\":\"document_id\",\"match\":{\"value\":\"$DOC_ID\"}},{\"key\":\"widgetId\",\"match\":{\"value\":\"$WIDGET_UUID\"}}]},\"exact\":true}"
```

Payload keys upsert theo `EmbeddingService`: `document_id`, `documentId`, `widgetId`, `chunk_id`, … (tham khảo `docs/RAG_TARGET_ARCHITECTURE.md`).

---

## 13. Chat query test

### 13.1 Private chat (bắt buộc `sessionId` non-blank)

Theo `ChatController`: `POST /api/chat`, header **`X-Widget-Key`**: giá trị **`apiKey`** (UUID string). Body JSON: `ChatRequest` — `sessionId`, `message`.

**Git Bash:**

```bash
BASE="http://localhost:8080"
WIDGET_API_KEY="<apiKey-from-create-chatbot>"
SESSION_ID="<new-random-uuid>"   # ví dụ tạo bằng PowerShell: [guid]::NewGuid().ToString()
curl.exe -s -X POST "$BASE/api/chat" \
  -H "Content-Type: application/json" \
  -H "X-Widget-Key: $WIDGET_API_KEY" \
  -d "{\"sessionId\":\"$SESSION_ID\",\"message\":\"Mã xác nhận RAG E2E là gì?\"}"
```

**PowerShell** (tạo session UUID một lần):

```powershell
$BASE = "http://localhost:8080"
$WIDGET_API_KEY = "<apiKey>"
$SESSION_ID = [guid]::NewGuid().ToString()
curl.exe -s -X POST "$BASE/api/chat" `
  -H "Content-Type: application/json" `
  -H "X-Widget-Key: $WIDGET_API_KEY" `
  -d "{`"sessionId`":`"$SESSION_ID`",`"message`":`"Mã xác nhận RAG E2E là gì?`"}"
```

**Kỳ vọng:**

- `answer` chứa chuỗi **`RAG-E2E-31415`** (hoặc tương đương theo retrieval + LLM).  
- `sources` là mảng; phần tử có `fileName` khớp tên file upload (ví dụ `RAG_E2E_SAMPLE.txt`), `chunkText` có nội dung liên quan.

### 13.2 Public chat (sessionId có thể để trống — server gán)

`PublicChatController` `POST /api/public/chat`, header **`x-api-key`**: cùng `apiKey`.

```bash
curl.exe -s -X POST "$BASE/api/public/chat" \
  -H "Content-Type: application/json" \
  -H "x-api-key: $WIDGET_API_KEY" \
  -d "{\"message\":\"Mã xác nhận RAG E2E là gì?\"}"
```

---

## 14. Delete document

`DELETE /api/documents/{id}` — theo `DocumentService.softDeleteDocument`: purge Qdrant trước; nếu purge fail → `IllegalStateException` (controller trả **502**); nếu OK → soft-delete tables, sections, chunks, document.

```bash
curl.exe -s -i -X DELETE "$BASE/api/documents/$DOC_ID"
```

Kỳ vọng: HTTP **200** và body `success: true` (theo `SimpleSuccessResponse`).

---

## 15. SQL checks sau delete

```sql
SELECT BIN_TO_UUID(id) AS id, file_name, status, deleted_at FROM documents WHERE BIN_TO_UUID(id) = '<DOC_ID>';
-- hoặc: ORDER BY updated_at DESC LIMIT 3;

SELECT COUNT(*) FROM document_chunks WHERE document_id = UUID_TO_BIN('<DOC_ID>') AND deleted_at IS NOT NULL;
SELECT COUNT(*) FROM document_sections WHERE document_id = UUID_TO_BIN('<DOC_ID>') AND deleted_at IS NOT NULL;
SELECT COUNT(*) FROM document_tables WHERE document_id = UUID_TO_BIN('<DOC_ID>') AND deleted_at IS NOT NULL;
```

Điều chỉnh `UUID_TO_BIN` / `BIN_TO_UUID` nếu schema JPA lưu UUID dạng CHAR(36) thay vì binary — so khớp với cột thực tế trong DB.

Kỳ vọng: `documents.deleted_at` NOT NULL; children `deleted_at` NOT NULL (fix 20C).

---

## 16. Chat lại sau delete

Dùng **cùng** `SESSION_ID` hoặc session mới đều được; câu hỏi giống bước 13.

Kỳ vọng:

- `answer` kiểu *"Tôi không tìm thấy thông tin này trong tài liệu."* (theo `ChatService` khi retrieval rỗng), **hoặc** không còn `sources` trỏ tới file đã xóa.  
- Không còn chunk của document đã xóa trong retrieval (MySQL `@SQLRestriction` + soft-delete children).

---

## 17. Retry document FAILED

Theo `DocumentService.retryFailedDocument` (sau 20E):

- Chỉ khi `status == FAILED`.  
- File gốc (`file_path`) phải còn tồn tại.  
- Purge Qdrant `document_id` + `widgetId`; nếu purge lỗi → không xóa DB children.  
- Hard-delete toàn bộ chunk/section/table của document đó; chạy lại `executeProcessing`.

**Gọi API:**

```bash
curl.exe -s -i -X POST "$BASE/api/documents/$DOC_ID/retry"
```

**Giả lập FAILED (chỉ khi chấp nhận rủi ro / DB test):**

- Tạm thời sai `NOMIC_API_KEY`, upload file nhỏ → document `FAILED` có thể kèm partial DB/Qdrant.  
- Sửa lại key đúng, gọi `retry`.  

Nếu không muốn đụng env: ghi **expected verification only** — sau `retry`, status → `INDEXED`, chunk mới, Qdrant count theo document > 0, chat trả đúng nội dung file.

---

## 18. Troubleshooting

| Hiện tượng | Hướng xử lý |
|-------------|-------------|
| Document `FAILED` | Xem log backend (`DocumentParserService`, `EmbeddingService`, Groq/Nomic). Kiểm tra `GET .../status` field `error`. Thử `POST .../retry` sau 20E nếu file còn. |
| Chat: no context / luôn "không tìm thấy" | Sai `X-Widget-Key` (phải là **apiKey**, không phải chatbot `id`). Chatbot khác widget của document. Document chưa `INDEXED`. Qdrant không có point. |
| Qdrant purge fail khi delete | HTTP **502** từ delete; document chưa soft-delete. Kiểm tra Qdrant up, collection name, env host/port. |
| MySQL connection refused | Sai host (`mysql` trong container vs `localhost` trên host), port, password. |
| Missing env key | Backend fail khi embed/chat; set `GROQ_API_KEY`, `NOMIC_API_KEY` trong compose hoặc shell. |
| Upload path / file empty | Canonical cần field `files`; legacy cần `file`. `chatbotId` phải UUID tồn tại (`WidgetConfigRepository.existsById`). |
| Widget key sai | `401` + JSON error từ `WidgetAuthFilter` ("Invalid or inactive Widget Key", …). |

---

## 19. Kết quả cần gửi lại cho reviewer / debug nếu fail

- **Thời điểm + profile** (`docker` / `dev`) + branch/commit.  
- **Request** (method, URL, headers đã che bớt key), **response status + body** (hoặc curl `-v` rút gọn).  
- **Log backend** đoạn liên quan upload / embed / chat / delete (không paste secret).  
- **Kết quả** `GET /api/documents/{id}/status`.  
- **SQL** (vài dòng kết quả, không dump cả bảng): `documents`, đếm chunk/section/table.  
- **Qdrant** response `collections/documents` và `points/count` với filter (nếu có).  
- **Ảnh màn hình** (UI) nếu test qua frontend.

---

## 20. Checklist (tick khi xong)

- [ ] Đã clean DB/Qdrant/uploads **test** (nếu cần) và hiểu rủi ro  
- [ ] `docker compose up` hoặc dev stack chạy; health cơ bản OK  
- [ ] `GROQ_API_KEY` + `NOMIC_API_KEY` đã set  
- [ ] `POST /api/chatbots` → có `id` + `apiKey`  
- [ ] `POST /api/documents/upload` với `docs/samples/RAG_E2E_SAMPLE.txt` thành công  
- [ ] `GET /api/documents/{id}/status` → `INDEXED`  
- [ ] SQL: document + chunk hợp lệ  
- [ ] Qdrant: collection + count theo `widgetId` / `document_id`  
- [ ] `POST /api/chat` + `X-Widget-Key` → answer + sources đúng  
- [ ] `DELETE /api/documents/{id}` → 200  
- [ ] SQL: `deleted_at` trên document + children  
- [ ] Chat lại → không còn context doc đã xóa  
- [ ] (Tuỳ chọn) `POST .../retry` sau FAILED — đã ghi nhận kết quả hoặc "expected only"

---

## 21. Kiểm thử chất lượng RAG (golden baseline — tùy chọn)

Sau khi luồng core E2E ổn định, có thể chạy bộ câu hỏi golden nhẹ (không benchmark nặng) theo:

- `docs/eval/RAG_EVALUATION_RUNBOOK.md` — thứ tự upload, chat, delete, chấm điểm PASS/PARTIAL/FAIL.  
- `docs/eval/RAG_GOLDEN_TEST_DOCUMENT.md` — tài liệu nguồn (upload bản **`.txt`** vì backend chỉ chấp nhận `.pdf`/`.txt`).  
- `docs/eval/RAG_GOLDEN_QUESTIONS.md` — 12 case + tiêu chí 4 trục.

Báo cáo baseline task: `reports/refactor/CURSOR_REPORT_21A_RAG_GOLDEN_EVALUATION_BASELINE.md`.

---

## Phụ lục: Endpoint đã đối chiếu source

| Method | Path | Ghi chú nhanh |
|--------|------|----------------|
| POST | `/api/chatbots` | Tạo chatbot; response có `apiKey` khi tạo mới |
| POST | `/api/documents/upload` | multipart `files`, `chatbotId` hoặc `widgetId` |
| POST | `/api/documents/upload/{widgetId}` | multipart `file` |
| GET | `/api/documents/{id}/status` | `status`: `INDEXED` / `PROCESSING` / `FAILED` |
| DELETE | `/api/documents/{id}` | Purge Qdrant rồi soft-delete |
| POST | `/api/documents/{id}/retry` | Chỉ `FAILED`; logic 20E |
| POST | `/api/chat` | Header `X-Widget-Key`; body `sessionId`, `message` |
| POST | `/api/chat/stream` | SSE; cùng header/body rule |
| POST | `/api/public/chat` | Header `x-api-key`; `sessionId` optional |

---

*Tài liệu này được soạn theo source tại thời điểm task 20F; nếu controller đổi, cập nhật runbook theo class tương ứng.*
