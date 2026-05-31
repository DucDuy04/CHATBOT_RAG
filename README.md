# CHATBOT_RAG — Vietnamese Document RAG Chatbot

Hệ thống chatbot RAG (Retrieval-Augmented Generation) hỏi đáp trên tài liệu tiếng Việt — PDF, DOCX, TXT — với hỗ trợ bảng có cấu trúc, multi-chatbot theo widget, trả lời có nguồn tham chiếu, và widget nhúng được.

**Baseline đã verify (task 28C, 2026-05-30):** Backend 120 tests PASS · Frontend build/lint PASS · Widget build PASS · Docker compose config PASS · Widget E2E PASS.

---

## 1. Project overview

Đây là chatbot RAG dựa trên tài liệu tiếng Việt, không dựa vào kiến thức tổng quát của LLM.

- Hỗ trợ **nhiều chatbot/widget**, mỗi widget có tập tài liệu và không gian vector riêng (lọc theo `widgetId`).
- Upload **PDF, DOCX, TXT**; xử lý bảng học thuật bằng hàng chuẩn hóa (`normalized_table_row`) và payload `cells_json`.
- **MySQL** lưu metadata, section, chunk, lịch sử chat.
- **Qdrant** lưu vector; application write/search dùng **REST HTTP port 6333** (không ghi qua gRPC).
- **Nomic** (`nomic-embed-text-v1.5`, 768-dim) cho embedding; **Groq** cho LLM; **Cohere Rerank** tùy chọn.
- Gồm **admin frontend** (React) và **embeddable widget** (IIFE bundle).

---

## 2. Main features

- Tạo và quản lý chatbot/widget.
- Upload và index PDF / DOCX / TXT.
- Chuẩn hóa bảng có cấu trúc → `normalized_table_row` với `cells_json` (UTF-8 tiếng Việt).
- **Hybrid retrieval:** vector search + keyword index + cell-aware scoring cho table rows.
- Trả lời có nguồn (source-grounded); từ chối câu hỏi ngoài phạm vi tài liệu (OOS refusal).
- Luồng chat công khai qua widget và `/api/public/chat`.
- **Chatbot delete cascade:**
  - soft-delete chatbot;
  - soft-delete mọi document active của widget;
  - purge Qdrant points theo `document_id`;
  - không xóa toàn bộ collection.
- **Runtime optimizations:**
  - startup keyword index prewarm;
  - rerank guard (bỏ qua rerank khi không cần);
  - async assistant message persistence.
- Admin UI: dashboard, documents, chatbots, analytics, settings, playground.
- Widget nhúng qua `window.RagChatbotConfig` + script IIFE.

**Đã gỡ (không còn active):** feedback endpoint, satisfaction/rating metrics, `newFeedback` notification, frontend mock mode, `USE_MOCK_API`.

---

## 3. Tech stack

### Backend

| Thành phần | Chi tiết |
|------------|----------|
| Language | Java 21 |
| Framework | Spring Boot 3.4.4 |
| Build | Maven (`Backend/mvnw.cmd` có sẵn) |
| Web / ORM / Security | Spring Web, Data JPA, Security |
| Database | MySQL 8.0 |
| Vector store | Qdrant (REST `:6333`) |
| DOCX | Apache POI 5.3 |
| PDF | Apache PDFBox 3.0.2 + Tabula 1.0.5 |
| LLM / embedding abstractions | LangChain4j 1.0.0-beta1 (`langchain4j-open-ai`, `langchain4j-nomic`) |
| Embedding provider | Nomic API (`nomic-embed-text-v1.5`) |
| LLM provider | Groq API |
| Rerank (optional) | Cohere Rerank (`COHERE_RERANK_ENABLED`) |

> LangChain4j **không** dùng `QdrantEmbeddingStore` / gRPC write path. Upsert/search Qdrant qua `index.embedding.EmbeddingService` (REST).

### Frontend

| Thành phần | Chi tiết |
|------------|----------|
| Framework | React 19 |
| Build | Vite 7 |
| Routing | React Router 7 |
| HTTP | Axios (+ fetch cho public chat / SSE stream) |
| UI | TailwindCSS 4, react-markdown, Zustand |
| Scripts | `npm run dev`, `build`, `lint`, `build:widget`, `preview` |

### Infrastructure

Docker Compose (`docker-compose.yml`):

| Service | Container | Port |
|---------|-----------|--------|
| `mysql` | `ragchatbot-mysql` | 3306 |
| `qdrant` | `ragchatbot-qdrant` | 6333 (REST), 6334 (gRPC — tooling only) |
| `backend` | `chatbot-backend` | 8080 |
| `frontend` | `chatbot-frontend` | 5173 → nginx :80 |

---

## 4. Repository structure

```text
CHATBOT_RAG/
├── Backend/                 # Spring Boot API
├── Frontend/                # React admin UI + widget bundle
├── docs/                    # Architecture, API, eval reports
├── agent/                   # Agent/runbook docs for AI/Cursor
├── reports/                 # Refactor task reports
├── docker-compose.yml
└── README.md
```

**Thư mục quan trọng:**

```text
Backend/src/main/java/.../rag/          # Retrieval, prompt, runtime, rerank, budget
Backend/src/main/java/.../ingest/       # Parser, normalize, chunking
Backend/src/main/java/.../index/        # Embedding + Qdrant REST I/O
Backend/src/main/java/.../api/          # REST controllers
Frontend/src/                           # Admin pages, API clients, widget page
Frontend/widget/                        # Widget IIFE source
Frontend/dist-widget/                   # Widget build output (generated)
docs/architecture/                      # Final architecture docs
docs/api/                               # API reference, quickstart, smoke tests
docs/eval/                              # E2E verification results
```

---

## 5. Prerequisites

Cài đặt trước khi clone-and-run:

| Tool | Yêu cầu |
|------|---------|
| Git | Clone repo |
| Docker Desktop | MySQL, Qdrant, backend (và optional frontend container) |
| Java | **21** (`Backend/pom.xml` → `java.version=21`) |
| Maven wrapper | Có sẵn trong `Backend/mvnw.cmd` — không bắt buộc cài Maven global |
| Node.js | LTS phù hợp Vite 7 (khuyến nghị 20+) |
| npm | Cài dependencies frontend |

**API keys (bắt buộc cho chat thật):**

- `GROQ_API_KEY` — LLM
- `NOMIC_API_KEY` — embedding
- `COHERE_API_KEY` + `COHERE_RERANK_ENABLED=true` — optional rerank

---

## 6. Environment variables

Tạo file `.env` tại **repo root** (Docker Compose đọc biến từ shell/env file). **Không commit key thật.**

Tham khảo: [`Backend/.env.example`](Backend/.env.example), [`Frontend/.env.example`](Frontend/.env.example).

### Root `.env` (Docker / shared)

```env
# Required — backend container và local backend
GROQ_API_KEY=your_groq_api_key_here
NOMIC_API_KEY=your_nomic_api_key_here

# Optional — Cohere rerank
COHERE_API_KEY=your_cohere_api_key_here
COHERE_RERANK_ENABLED=false

# Docker backend profile (compose set sẵn SPRING_PROFILES_ACTIVE=docker)
# MySQL/Qdrant host trong container: mysql / qdrant (application-docker.yml)
```

MySQL trong compose (`docker-compose.yml`):

```env
MYSQL_DATABASE=ragchatbot
MYSQL_ROOT_PASSWORD=root
```

Qdrant (local dev, `application-dev.yml`):

```env
QDRANT_HOST=localhost
QDRANT_HTTP_PORT=6333
```

### Frontend (local dev)

Copy `Frontend/.env.example` → `Frontend/.env.local`:

```env
VITE_API_URL=http://localhost:8080
# VITE_FRONTEND_URL=http://localhost:5173
```

Khi `VITE_API_URL` rỗng, Vite dev server proxy `/api` → `http://localhost:8080` (xem `Frontend/vite.config.js`).

### PowerShell — set tạm cho session hiện tại

```powershell
$env:GROQ_API_KEY="your_groq_api_key_here"
$env:NOMIC_API_KEY="your_nomic_api_key_here"
$env:COHERE_API_KEY="your_cohere_api_key_here"
$env:COHERE_RERANK_ENABLED="false"
```

**Lưu ý:**

- Không commit API key thật.
- Docker Compose inject env vào backend container — sau khi sửa `.env`, recreate/restart backend: `docker compose up --build -d backend`.
- PowerShell process env chỉ có hiệu lực terminal hiện tại. Kiểm tra scope:

```powershell
[Environment]::GetEnvironmentVariable("NOMIC_API_KEY", "Process")
[Environment]::GetEnvironmentVariable("NOMIC_API_KEY", "User")
[Environment]::GetEnvironmentVariable("NOMIC_API_KEY", "Machine")
```

---

## 7. Run with Docker Compose

Từ repo root:

```powershell
cd E:\chatbot-rag-workspace\CHATBOT_RAG

docker compose config -q
docker compose up -d mysql qdrant
docker compose up --build -d backend
docker compose ps
docker compose logs backend --tail=200
```

Frontend container (nginx, port 5173):

```powershell
docker compose up --build -d frontend
```

**URLs:**

| Service | URL |
|---------|-----|
| Backend API | http://localhost:8080 |
| Frontend (compose) | http://localhost:5173 |
| Qdrant REST | http://localhost:6333 |
| MySQL | localhost:3306 (db: `ragchatbot`, user/pass: `root`) |

**Dev frontend thường chạy riêng** qua `npm run dev` (mục 9) thay vì container — widget hot-reload và proxy `/api` tiện hơn.

---

## 8. Run backend locally

Cần MySQL + Qdrant đang chạy (Docker hoặc local).

```powershell
cd Backend

# Set keys (hoặc dùng .env đã export)
$env:GROQ_API_KEY="your_groq_api_key_here"
$env:NOMIC_API_KEY="your_nomic_api_key_here"

.\mvnw.cmd clean test
.\mvnw.cmd spring-boot:run
```

Profile mặc định trong `application.yml` là `dev`. Override nếu cần:

```powershell
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
```

Local dev kết nối:

- MySQL: `localhost:3306/ragchatbot` (user/pass `root`)
- Qdrant REST: `localhost:6333`

---

## 9. Run frontend locally

```powershell
cd Frontend
npm install
npm run dev
```

Build / lint / widget:

```powershell
npm run build
npm run lint
npm run build:widget
```

| Script | Output |
|--------|--------|
| `build:widget` | `dist-widget/chatbot-widget.iife.js`, `dist-widget/widget.css` |
| sync (auto) | copy → `public/dist-widget/` (phục vụ dev server và embed) |

Admin UI: http://localhost:5173

---

## 10. How to use the system

1. **Start stack:** MySQL + Qdrant + backend (+ frontend dev hoặc compose).
2. **Mở admin UI:** http://localhost:5173
3. **Tạo chatbot:** menu Chatbots → Create → lưu `widgetKey` / API key từ embed config.
4. **Upload tài liệu:** Documents → upload PDF/DOCX/TXT gán chatbot.
5. **Chờ index:** document status `COMPLETED` (hoặc `FAILED` — xem logs backend).
6. **Hỏi thử:** Playground hoặc Chat — kiểm tra answer + sources.
7. **Nhúng widget:** build widget (`npm run build:widget`) → copy embed snippet từ Chatbot Embed page hoặc dùng HTML mẫu mục 12.

**Tài liệu eval có sẵn trong repo:**

```text
docs/eval/manual/SoTayHocVu_HocKy1_2025-2026_RAG_CONTEXT_HOC_KY.docx
```

Các file eval khác: `docs/eval/manual/`.

---

## 11. API quickstart

Tài liệu đầy đủ:

- [`docs/api/API_REFERENCE_20260530.md`](docs/api/API_REFERENCE_20260530.md)
- [`docs/api/API_QUICKSTART_20260530.md`](docs/api/API_QUICKSTART_20260530.md)
- [`docs/api/API_SMOKE_TESTS_20260530.md`](docs/api/API_SMOKE_TESTS_20260530.md)

### Chat API (sync) — header `X-Widget-Key`

Body DTO: `ChatRequest` — fields `sessionId` (UUID string, optional), `message` (required).

```powershell
$headers = @{
  "X-Widget-Key" = "your-widget-api-key-uuid"
  "Content-Type" = "application/json"
}

$body = @{
  sessionId = [guid]::NewGuid().ToString()
  message   = "Pháp luật Việt Nam đại cương Nhóm 1 học với giảng viên nào, thứ mấy, tiết nào, phòng nào?"
} | ConvertTo-Json

Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/chat" -Headers $headers -Body $body
```

Stream: `POST /api/chat/stream` — SSE, cùng header và body.

### Public widget chat — `/api/public/chat`

Header: `x-api-key` (hoặc `X-Widget-Key`). Body: `{ "message": "...", "sessionId": "..." }`.

```powershell
$headers = @{
  "x-api-key"      = "your-widget-api-key-uuid"
  "Content-Type"   = "application/json"
}

$body = @{
  message = "Pháp luật Việt Nam đại cương Nhóm 1 học với giảng viên nào, thứ mấy, tiết nào, phòng nào?"
} | ConvertTo-Json

Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/public/chat" -Headers $headers -Body $body
```

Response: `{ "answer", "sessionId", "sources" }`.

### Upload document

```text
POST /api/documents/upload          multipart: files + chatbotId
POST /api/documents/upload/{widgetId}   legacy single file
GET  /api/documents/{id}/status
GET  /api/documents/{id}/chunks
```

---

## 12. Widget usage

### Build

```powershell
cd Frontend
npm run build:widget
```

Output:

```text
dist-widget/chatbot-widget.iife.js
dist-widget/widget.css
public/dist-widget/          ← synced for dev server
```

### Embed snippet

```html
<script>
  window.RagChatbotConfig = {
    widgetKey: "your-widget-api-key-uuid",
    frontendUrl: "http://localhost:5173",
    widgetColor: "#2563eb",
    welcomeMessage: "Xin chào! Tôi có thể giúp gì?",
    position: "bottom-right",
    launcherIcon: "chat"
  };
</script>
<script async src="http://localhost:5173/dist-widget/chatbot-widget.iife.js"></script>
```

- `widgetKey` (hoặc legacy `apiKey`): UUID từ embed config chatbot.
- `frontendUrl`: URL frontend host widget iframe `/widget`.
- Widget tạo bubble + iframe → route `/widget?widgetKey=...`.

### Local test

| Cách | URL |
|------|-----|
| Widget page trực tiếp | http://localhost:5173/widget?widgetKey=YOUR_UUID |
| Public test HTML | http://localhost:5173/public-widget-test.html?widgetKey=YOUR_UUID |
| Demo | `Frontend/public/demo.html` |

### Verified widget flow (28C)

1. Bubble xuất hiện góc màn hình.
2. User mở widget → iframe load `/widget`.
3. User gửi câu hỏi → `POST /api/chat/stream` + `X-Widget-Key`.
4. Answer stream về (SSE).
5. Câu OOS → refusal, không bịa số liệu.

Widget iframe gọi backend qua `VITE_API_URL` hoặc same-origin proxy — cần backend reachable từ browser.

---

## 13. Testing and verification

```powershell
cd Backend
.\mvnw.cmd clean test

cd ..\Frontend
npm run build
npm run lint
npm run build:widget

cd ..
docker compose config -q
```

**Baseline verified (28C):**

| Check | Result |
|-------|--------|
| Backend tests | 120 PASS, 0 failures |
| Frontend build | PASS |
| Frontend lint | PASS |
| Widget build | PASS |
| Docker compose config | PASS |
| Widget E2E | PASS |

Default unit tests **không** cần MySQL/Qdrant/API keys live (mock/in-memory).

---

## 14. Runtime smoke tests

Chạy sau khi stack + ít nhất một document `COMPLETED` đã index.

### Backend smoke — exact answer

**Hỏi:**

```text
Pháp luật Việt Nam đại cương Nhóm 1 học với giảng viên nào, thứ mấy, tiết nào, phòng nào?
```

**Kỳ vọng:** answer chứa **Nguyễn Thị Vân Anh**, **thứ 2**, **tiết 1 - 2**, **E301** (với tài liệu SoTayHocVu đã index).

### OOS smoke

**Hỏi:**

```text
Tỷ giá USD/VND hôm nay là bao nhiêu?
```

**Kỳ vọng:** refusal / không tìm thấy trong tài liệu — **không** bịa tỷ giá.

### Removed feedback endpoint

```powershell
Invoke-WebRequest -Method Post -Uri "http://localhost:8080/api/chat/feedback" -SkipHttpErrorCheck
```

**Kỳ vọng:** HTTP **404** (endpoint đã gỡ).

---

## 15. Data cleanup and operations

### Delete chatbot

`DELETE /api/chatbots/{id}`:

- Cascade: soft-delete mọi document active của widget.
- Mỗi document: purge Qdrant theo `document_id`, soft-delete rows MySQL.
- Soft-delete chatbot.

### DB / Qdrant consistency

Với document `COMPLETED`, **chunk count trong MySQL** nên khớp **point count trong Qdrant** (filter `document_id`). Xem [`agent/06-operations.md`](agent/06-operations.md).

### Không chạy bừa bãi

```powershell
docker compose down -v
```

**Xóa volumes** (`mysql_data`, `qdrant_data`) — mất toàn bộ DB và vector. Chỉ dùng khi reset destructive có chủ đích.

### Historical cleanup (feedback — đã gỡ)

Feedback/satisfaction/mock mode đã remove khỏi codebase. DB cũ có thể còn bảng `chat_feedbacks` hoặc cột `notify_new_feedback` — có thể drop sau backup nếu không cần lịch sử. Backend hiện tại không map các artifact này.

---

## 16. Troubleshooting

### Backend — port 8080 đã dùng

```powershell
Get-NetTCPConnection -LocalPort 8080 -ErrorAction SilentlyContinue |
  Select-Object OwningProcess, State
Stop-Process -Id <PID> -Force   # thay <PID> bằng process thực tế
```

### API key cũ sau khi mở PowerShell mới

Process env mất khi đóng terminal. Set lại hoặc dùng `.env` + restart container. Kiểm tra prefix trong container:

```powershell
docker compose exec backend sh -lc 'echo ${NOMIC_API_KEY:0:8}'
```

### Nomic / Groq quota hoặc invalid key

```powershell
docker compose logs backend --tail=200
```

Tìm `401`, `429`, `rate limit`, `embedding`, `Groq`. Verify key prefix — không log full key.

### MySQL / Qdrant không chạy

```powershell
docker compose ps
docker compose logs mysql --tail=100
docker compose logs qdrant --tail=100
docker compose up -d mysql qdrant
```

### Qdrant mismatch (chunk count ≠ points)

- Kiểm tra document status `COMPLETED`.
- Retry: `POST /api/documents/{id}/retry`.
- Purge/reindex theo document — **không** drop collection toàn cục trừ khi reset có chủ đích.
- Xem [`agent/06-operations.md`](agent/06-operations.md).

### Widget không chat được

Kiểm tra:

- `widgetKey` đúng UUID từ embed config.
- Header `X-Widget-Key` / backend reachable.
- `frontendUrl` trong `RagChatbotConfig` trỏ đúng host.
- `npm run build:widget` đã chạy; file `dist-widget/chatbot-widget.iife.js` tồn tại.
- Browser Network tab: `/api/chat/stream` status, CORS, SSE events.
- Backend logs khi gửi message.

### Lỗi schema cũ (feedback table/column)

Nếu branch cũ còn map `chat_feedbacks` / `notify_new_feedback`: cập nhật branch mới (28A+) hoặc không chạy code cũ trên DB đã cleanup.

---

## 17. Documentation links

### Architecture

- [`docs/architecture/FINAL_BACKEND_RAG_ARCHITECTURE_20260529.md`](docs/architecture/FINAL_BACKEND_RAG_ARCHITECTURE_20260529.md)
- [`docs/architecture/FINAL_RAG_PIPELINE_OVERVIEW_20260529.md`](docs/architecture/FINAL_RAG_PIPELINE_OVERVIEW_20260529.md)
- [`docs/architecture/THESIS_ARCHITECTURE_SUMMARY_20260529.md`](docs/architecture/THESIS_ARCHITECTURE_SUMMARY_20260529.md)
- [`docs/architecture/THESIS_RAG_PIPELINE_DESCRIPTION_20260529.md`](docs/architecture/THESIS_RAG_PIPELINE_DESCRIPTION_20260529.md)

### API

- [`docs/api/API_REFERENCE_20260530.md`](docs/api/API_REFERENCE_20260530.md)
- [`docs/api/API_QUICKSTART_20260530.md`](docs/api/API_QUICKSTART_20260530.md)
- [`docs/api/API_SMOKE_TESTS_20260530.md`](docs/api/API_SMOKE_TESTS_20260530.md)

### Agent / runbook

- [`agent.md`](agent.md)
- [`agent/04-runbook.md`](agent/04-runbook.md)
- [`agent/05-api.md`](agent/05-api.md)
- [`agent/06-operations.md`](agent/06-operations.md)

### Verification

- [`docs/eval/results/FULL_PROJECT_WIDGET_E2E_VERIFY_28C_20260530.md`](docs/eval/results/FULL_PROJECT_WIDGET_E2E_VERIFY_28C_20260530.md)

---

## 18. Known limitations

- **Adaptive context-N / dynamic topK theo query:** chưa implement; budget cố định theo query type (`PromptBudgetResolver`).
- **Admin API security:** `/api/documents/**`, `/api/chatbots/**`, analytics, dashboard — dev-oriented (`permitAll`); cần hardening trước deploy công khai.
- **Frontend mock mode:** đã gỡ; development bắt buộc chạy backend thật.
- **DB migration framework:** chưa có Flyway/Liquibase; JPA `ddl-auto=update` — không phù hợp production migration nghiêm ngặt.
- **Chatbot delete nhiều document lớn:** purge Qdrant tuần tự — có thể chậm.
- **Legacy chunk types:** `table_row_group`, `text_table_like` chỉ read-compatible trên dữ liệu cũ; ingest mới không emit.
- **Retrieval edge cases:** một số câu hỏi phức tạp có thể cần tuning thêm (xem eval reports).

---

## Quick start (fresh clone)

```powershell
git clone <repo-url> CHATBOT_RAG
cd CHATBOT_RAG

# .env với GROQ_API_KEY, NOMIC_API_KEY
docker compose config -q
docker compose up -d mysql qdrant backend

cd Frontend
npm install
npm run build:widget
npm run dev

cd ..\Backend
.\mvnw.cmd clean test
```

Mở http://localhost:5173 → tạo chatbot → upload DOCX/PDF → chờ COMPLETED → hỏi thử Playground.

Cursor AI: đọc [`agent.md`](agent.md) và `.cursor/rules/`.
