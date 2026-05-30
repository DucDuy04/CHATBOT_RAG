# CHATBOT_RAG — Vietnamese Document RAG Chatbot

Hệ thống chatbot RAG (Retrieval-Augmented Generation) hỏi đáp trên tài liệu tiếng Việt — PDF, DOCX, TXT — với hỗ trợ bảng có cấu trúc, multi-chatbot theo widget, và trả lời có nguồn tham chiếu.

**Trạng thái:** Backend ổn định sau refactor 25B–25K. Baseline test: **71 tests, 0 failures**.

---

## 1. Project overview

- **Mục tiêu:** Hỏi đáp dựa trên tài liệu nội bộ (học vụ, quy định, sổ tay…) thay vì kiến thức tổng quát của LLM.
- **Định dạng:** PDF, DOCX, TXT — ưu tiên tài liệu học thuật có bảng và section hierarchy.
- **Lưu trữ:** MySQL (metadata, chunk, chat history) + Qdrant (vector semantic search, REST port 6333).
- **AI providers:** Nomic (`nomic-embed-text-v1.5`, 768-dim) cho embedding; Groq cho LLM; Cohere Rerank tùy chọn.
- **Multi-tenant:** Mỗi chatbot (widget) có tập tài liệu, chunk và không gian vector riêng, lọc theo `widgetId`.

---

## 2. Main features

- Upload và index PDF / DOCX / TXT
- Parse bảng có cấu trúc (`RawTableModel`) — DOCX dùng `physicalColIndex`, PDF dùng coordinate overlap (Tabula)
- Chuẩn hóa hàng bảng → `normalized_table_row` với `cells_json` (UTF-8 tiếng Việt)
- Hybrid retrieval: vector search + keyword + cell-aware scoring cho table rows
- Trả lời có nguồn (source-grounded); từ chối câu hỏi ngoài phạm vi tài liệu
- Quản lý chatbot / widget; nhúng widget qua IIFE + `X-Widget-Key`
- **Cascade delete:** xóa chatbot → soft-delete tất cả documents active → purge Qdrant theo `document_id`
- Playground / debug API cho thử nghiệm retrieval và so sánh prompt

---

## 3. Architecture summary

```text
Frontend / Admin / Widget
        ↓ REST / SSE
   Backend Spring Boot
   ├── ingest.parser / normalize / chunking
   ├── index.embedding (Qdrant REST) / index.qdrant (bootstrap/purge)
   └── rag.retrieve / prompt / runtime / analysis / rerank / budget
        ↓                    ↓
     MySQL              Qdrant (:6333 REST)
        ↓
   Nomic + Groq (+ Cohere optional)
```

**Tài liệu kiến trúc chi tiết:**

- [`docs/architecture/FINAL_BACKEND_RAG_ARCHITECTURE_20260529.md`](docs/architecture/FINAL_BACKEND_RAG_ARCHITECTURE_20260529.md)
- [`docs/architecture/FINAL_RAG_PIPELINE_OVERVIEW_20260529.md`](docs/architecture/FINAL_RAG_PIPELINE_OVERVIEW_20260529.md)

**Package map (canonical):**

| Package | Vai trò |
|---------|---------|
| `api` | REST controllers |
| `service` | Document/widget lifecycle, upload orchestration |
| `ingest.parser` | Parse PDF/DOCX/TXT → sections + `RawTableModel` |
| `ingest.normalize` | Bảng → `normalized_table_row`, `cells_json` |
| `ingest.chunking` | `ChunkingService2` → chunk types |
| `index.embedding` | Nomic embed + **Qdrant REST** upsert/search |
| `index.qdrant` | Collection bootstrap + purge |
| `rag.retrieve` | Hybrid retrieval (7 bước) |
| `rag.prompt` | Prompt building |
| `rag.runtime` | `ChatService`, `PlaygroundService` |
| `rag.analysis` | Query type, keyword signals |
| `rag.rerank` | Optional Cohere rerank |
| `rag.budget` | Context char budget |
| `audit.metrics` | Token/latency audit logs |
| `llm` | Groq adapter + fallback models |
| `domain` | JPA entities + repositories |

RAG core **không** nằm trong `service.*` (đã chuyển sang package theo concern, tasks 25B–25D).

---

## 4. Technology stack

### Backend

- Java 21, Spring Boot 3.4.4, Maven
- Spring Web, Data JPA, Security
- MySQL 8.0, Qdrant (REST HTTP 6333)
- Apache POI 5.3 (DOCX), PDFBox 3.0.2 + Tabula 1.0.5 (PDF)
- LangChain4j 1.0.0-beta1 — **chỉ** cho LLM/embedding abstractions (`langchain4j-open-ai`, `langchain4j-nomic`); **không** dùng `QdrantEmbeddingStore` / gRPC write
- Nomic Embedding API, Groq LLM API, Cohere Rerank (optional)

### Frontend

- React 19, Vite 7, React Router 7
- Axios, TailwindCSS 4, react-markdown, Zustand
- Widget IIFE bundle (`npm run build:widget`)

### Infrastructure

- Docker Compose: `mysql`, `qdrant`, `backend`, `frontend`
- MySQL container: `ragchatbot-mysql`
- Qdrant container: `ragchatbot-qdrant` (6333 REST, 6334 gRPC exposed cho tooling — backend **không** ghi qua gRPC)

---

## 5. Environment variables / secrets

Tạo file `.env` tại root (hoặc set PowerShell env vars). **Không commit key thật.**

```env
GROQ_API_KEY=
NOMIC_API_KEY=
COHERE_API_KEY=          # optional
COHERE_RERANK_ENABLED=false
MYSQL_DATABASE=ragchatbot
MYSQL_USER=root
MYSQL_PASSWORD=root
QDRANT_HOST=localhost
QDRANT_HTTP_PORT=6333
```

- Verify key bằng prefix (8 ký tự đầu), **không** log full key.
- Docker Compose inject `GROQ_API_KEY`, `NOMIC_API_KEY`, `COHERE_*` vào backend container.

---

## 6. How to run with Docker

```powershell
cd E:\chatbot-rag-workspace\CHATBOT_RAG
docker compose config -q
docker compose up -d mysql qdrant backend
docker compose ps
docker compose logs backend --tail=100
```

Frontend (compose có service `frontend`):

```powershell
docker compose up -d frontend
```

- Backend: `http://localhost:8080`
- Frontend: `http://localhost:5173`
- Qdrant REST: `http://localhost:6333`

---

## 7. How to run backend locally

Cần MySQL + Qdrant đang chạy (Docker hoặc local).

```powershell
cd Backend
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
$env:GROQ_API_KEY='your-key'
$env:NOMIC_API_KEY='your-key'
.\mvnw.cmd clean test
.\mvnw.cmd spring-boot:run
```

Profile mặc định trong `application.yml` là `dev`. Override nếu cần:

```powershell
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
```

---

## 8. How to ingest a document

1. **Tạo chatbot/widget:** `POST /api/chatbots` hoặc `POST /api/widgets` → nhận `widgetConfigId` và `apiKey`.
2. **Upload:** `POST /api/documents/upload/{widgetId}` — multipart PDF/DOCX/TXT.
3. **Theo dõi status:** `GET /api/documents/{id}/status` — chờ `COMPLETED` (hoặc `FAILED`).
4. **Verify chunks:** `GET /api/documents/{id}/chunks` — kiểm tra `normalized_table_row`, `cells_json`.
5. **Verify Qdrant:** số points khớp `chunkCount` trên document COMPLETED (filter `document_id`).

**File eval ổn định** (có trong repo):

```text
docs/eval/manual/SoTayHocVu_HocKy1_2025-2026_RAG_CONTEXT_HOC_KY.docx
```

---

## 9. How to test

```powershell
cd Backend
.\mvnw.cmd clean test
```

**Expected:** 71 tests, 0 failures, 0 errors.

Default unit tests **không** cần MySQL/Qdrant/API keys live (mock/in-memory).

Nhóm test quan trọng:

| Test class | Bảo vệ |
|------------|--------|
| `DocxParserServiceTest` | DOCX parse + `physicalColIndex` |
| `NormalizedTableIngestTest` | Table normalization pipeline |
| `NormalizedTableSuppressionTest` | Không emit legacy chunk types |
| `QdrantPayloadUnicodeTest` | UTF-8 `cells_json` qua REST |
| `HybridKeywordSearchTest` | Hybrid vector + keyword |
| `RagRetrievalServiceE2ETest` | End-to-end retrieval |
| `WidgetServiceSoftDeleteChatbotTest` | Chatbot cascade delete |
| `NoHardcodedLexiconInTableNormalizerTest` | Normalizer không hardcode lexicon |
| `PromptBuilderServiceTest` | Prompt + refusal behavior |
| `ChatServiceSourcePresentationTest` | Source presentation |

Chi tiết: [`agent/05-testing.md`](agent/05-testing.md)

---

## 10. Data cleanup / delete behavior

| Action | Endpoint | Behavior |
|--------|----------|----------|
| Xóa document | `DELETE /api/documents/{id}` | Soft-delete DB rows + purge Qdrant points filter `document_id` |
| Xóa chatbot | `DELETE /api/chatbots/{id}` | Cascade: gọi `DocumentService.softDeleteDocument` cho mọi document active → soft-delete chatbot |

**Quy tắc an toàn:**

- **Không** drop Qdrant collection cho cleanup thường ngày.
- **Không** `docker compose down -v` trừ khi reset destructive có chủ đích.
- Purge Qdrant luôn filter theo `document_id`, không xóa toàn collection.

---

## 11. Known limitations

- **Adaptive context-N / dynamic topK:** chưa implement; budget cố định theo query type.
- **Admin route security:** `/api/documents/**`, `/api/chatbots/**` chưa có auth production-grade — cần hardening trước deploy công khai.
- **Chatbot delete nhiều document lớn:** purge Qdrant tuần tự — có thể chậm.
- **Soft-deleted rows:** vẫn tồn tại trong DB cho đến hard purge tùy chọn.
- **Testcontainers integration:** optional future work; hiện default tests offline.
- **Legacy chunk types:** `table_row_group`, `text_table_like` chỉ read-compatible trên dữ liệu cũ; ingest mới không emit.

---

## 12. Documentation links

| Document | Mục đích |
|----------|----------|
| [`docs/architecture/FINAL_BACKEND_RAG_ARCHITECTURE_20260529.md`](docs/architecture/FINAL_BACKEND_RAG_ARCHITECTURE_20260529.md) | Kiến trúc backend chi tiết |
| [`docs/architecture/FINAL_RAG_PIPELINE_OVERVIEW_20260529.md`](docs/architecture/FINAL_RAG_PIPELINE_OVERVIEW_20260529.md) | Pipeline ingest + retrieval |
| [`docs/architecture/THESIS_ARCHITECTURE_SUMMARY_20260529.md`](docs/architecture/THESIS_ARCHITECTURE_SUMMARY_20260529.md) | Tóm tắt luận văn (tiếng Việt) |
| [`docs/architecture/THESIS_RAG_PIPELINE_DESCRIPTION_20260529.md`](docs/architecture/THESIS_RAG_PIPELINE_DESCRIPTION_20260529.md) | Mô tả pipeline cho thesis |
| [`docs/architecture/THESIS_ARCHITECTURE_OUTLINE_20260529.md`](docs/architecture/THESIS_ARCHITECTURE_OUTLINE_20260529.md) | Outline chương kiến trúc |
| [`agent.md`](agent.md) | Entry point cho AI/Cursor sessions |
| [`agent/01-overview.md`](agent/01-overview.md) – [`agent/06-operations.md`](agent/06-operations.md) | Agent docs chi tiết |
| [`docs/api/API_REFERENCE_20260530.md`](docs/api/API_REFERENCE_20260530.md) | REST API reference (full) |
| [`docs/api/API_QUICKSTART_20260530.md`](docs/api/API_QUICKSTART_20260530.md) | API quickstart |
| [`docs/api/API_SMOKE_TESTS_20260530.md`](docs/api/API_SMOKE_TESTS_20260530.md) | Manual API smoke tests |
| [`agent/05-api.md`](agent/05-api.md) | API guide for AI/Cursor |

---

## Quick start (fresh clone)

```powershell
git clone <repo-url> CHATBOT_RAG
cd CHATBOT_RAG
# Tạo .env với GROQ_API_KEY, NOMIC_API_KEY
docker compose up -d mysql qdrant backend
cd Backend && .\mvnw.cmd clean test
```

Cursor rules: copy `.cursor/rules/` vào project root nếu dùng Cursor AI — xem `.cursor/rules/00-core-working-rule.mdc`.
