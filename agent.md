# AGENT DOCS - CHATBOT_RAG

Tài liệu agent được tách nhỏ để dễ bảo trì. Đây là trang điều hướng chính.

## Mục lục
- `agent/01-overview.md`: mục tiêu, phạm vi, trạng thái dự án.
- `agent/02-architecture.md`: kiến trúc tổng thể, schema DB thực tế, Qdrant payload, retrieval pipeline 7 bước.
- `agent/03-backend.md`: chi tiết backend Spring Boot, WidgetAuthFilter, RAG pipeline, chunking types.
- `agent/04-frontend.md`: luồng frontend, widget key, streaming SSE, sources.
- `agent/05-api.md`: tài liệu API tham chiếu nhanh (đã cập nhật đúng paths).
- `agent/06-operations.md`: vận hành, env vars đầy đủ, rủi ro kỹ thuật, roadmap.

## Snapshot nhanh
- Dự án gồm `Backend` (Spring Boot 3.4.4 / Java 21) và `Frontend` (React 19 + Vite 7).
- RAG pipeline end-to-end: upload → parse/chunk/embed → multi-stage retrieve → chat.
- **Multi-tenant theo widget**: `WidgetConfig` là root entity; documents, chunks, sessions scoped theo `widgetConfigId`.
- Vector store: Qdrant (collection `documents`, vector 768, Cosine). Transactional store: MySQL (`ragchatbot`).
- LLM: Groq (`llama-3.3-70b-versatile`) với fallback models. Embedding: Nomic (`nomic-embed-text-v1.5`).
- Cohere Rerank: optional (`COHERE_RERANK_ENABLED=false` mặc định).
- Widget nhúng bằng bundle IIFE, xác thực qua `X-Widget-Key` header.

## Cách sử dụng tài liệu này
- Nếu cần hiểu tổng thể: đọc từ `01-overview` → `02-architecture`.
- Nếu triển khai API/logic: đọc `03-backend` + `05-api`.
- Nếu tích hợp UI/widget: đọc `04-frontend`.
- Nếu chuẩn bị production: đọc `06-operations`.

---

# 🧠 PROJECT OVERVIEW
- Tên dự án: `CHATBOT_RAG` (Backend artifact: `RAG_CHATBOT_BE`, Frontend package: `rag-chatbot-fe`).
- Mục tiêu: Chatbot RAG hỏi đáp dựa trên tài liệu nội bộ (PDF/TXT), giao diện web quản trị + widget nhúng.
- Mô hình: multi-tenant theo `WidgetConfig` — mỗi widget có documents, sessions và Qdrant search context riêng.

# 🏗️ SYSTEM ARCHITECTURE
- Kiến trúc: Client-Server + RAG pipeline, tách FE/BE.
- `WidgetConfig` là **multi-tenant root**: tất cả entities chính đều có FK `widget_config_id`.
- FE gọi BE qua REST / SSE (`/api/documents`, `/api/chat`, `/api/chat/stream`, `/api/widgets`).
- BE ghi metadata/history vào MySQL, vector vào Qdrant, gọi Groq/Nomic qua API.
- Chat endpoint `/api/chat/**` bảo vệ bởi `WidgetAuthFilter` (header `X-Widget-Key`).

Luồng xử lý chính:
1. Admin tạo widget: `POST /api/widgets` → nhận `widgetConfigId` + `apiKey`.
2. Admin upload file: `POST /api/documents/upload/{widgetId}`.
3. BE parse PDF/TXT, chunk theo section hierarchy, embed, lưu vào Qdrant + MySQL.
4. User widget gửi câu hỏi kèm `X-Widget-Key` header.
5. BE xác thực key, phân tích intent, truy hồi context multi-stage (heading lock + vector + expansion + rerank).
6. BE dựng prompt + gọi Groq → stream token SSE hoặc trả JSON sync.
7. FE render câu trả lời + sources.

# ⚙️ TECH STACK
- Backend: Java 21, Spring Boot 3.4.4, Spring Web/JPA/Security, LangChain4j 1.0.0-beta1, PDFBox 3.0.2, Tabula 1.0.5.
- Frontend: React 19, Vite 7, React Router 7, Axios, TailwindCSS 4, react-markdown, uuid.
- Database: MySQL 8.0, Qdrant (latest).
- AI providers: Groq (LLM), Nomic (embedding), Cohere Rerank (optional).
- Infra: Docker Compose (frontend, backend, mysql, qdrant).

# 🗄️ DATABASE DESIGN
Polyglot persistence: MySQL (transactional metadata) + Qdrant (semantic vectors).

**Tất cả entities dùng UUID PK, soft delete qua `deleted_at`.**

MySQL (`ragchatbot`):
- `widget_configs`: multi-tenant root (id UUID, name, apiKey UUID unique, allowedOrigin JSON, uiConfig JSON, isActive).
- `documents`: metadata tài liệu (id UUID, widget_config_id FK, fileName, fileType, mimeType, fileSize, checksum, status, chunkCount, deleted_at).
- `document_sections`: section hierarchy (sectionKey, title, headingPathText, orderIndex, widget_config_id FK).
- `document_chunks`: nội dung chunk thực (id UUID, content, chunkType, sectionId, sectionTitle, headingPathText, orderIndex, sectionOrder, widget_config_id FK, document_id FK).
- `document_tables`: metadata bảng.
- `chat_sessions`: phiên chat (id UUID, widget_config_id FK, sessionKey UUID, widgetOrigin, title; unique: widget_config_id + session_key).
- `chat_messages`: tin nhắn (role USER/ASSISTANT, content TEXT, sources JSON, chat_session_id FK).
- Schema tạo tự động qua `ddl-auto=update` (chưa có Flyway/Liquibase).

Qdrant:
- Collection: `documents`, vector size `768`, distance Cosine.
- Port: gRPC `6334` (LangChain4j), HTTP `6333` (REST admin).
- Payload: `widgetId`, `documentId`, `fileName`, `chunk_id`, `section_id`, `section_title`, `heading_path`, `chunk_type`, `child_section_ids`, `table_id`, `page_start`, `page_end`, `order_index`, `section_order`.
- Tất cả search đều filter theo `widgetId`.

# 📂 PROJECT STRUCTURE
- `Backend/`: Spring Boot API.
  - `api/`: ChatController, DocumentController, WidgetController.
  - `service/`: ChatService, DocumentService, DocumentParserService, ChunkingService2, EmbeddingService, RagRetrievalService, QueryAnalyzerService, PromptBuilderService, LlmFallbackService, RerankService, WidgetService.
  - `domain/`: chat/ (ChatSession, ChatMessage), document/ (Document, DocumentChunk, DocumentSection, DocumentTable + repos), widget/ (WidgetConfig + repo), enums/.
  - `config/`: GroqConfig, QdrantConfig, SecurityConfig, WidgetAuthFilter, AppConfig.
  - `resources/`: application.yml, application-dev.yml, application-docker.yml.
- `Frontend/`: React app + widget.
  - `src/pages/`: ChatPage, DocumentPage, WidgetChatPage.
  - `src/api/`: axiosInstance, documentApi, widgetApi.
  - `widget/widget.js`: IIFE embed script.
  - `vite.widget.config.js`: build widget bundle.
- `docker-compose.yml`: full stack local.
- `preprocess/`: standalone Java module (không phụ thuộc Backend runtime).

# 🔌 CORE MODULES
- Document Ingestion: `DocumentService` → `DocumentParserService` → `ChunkingService2` → `EmbeddingService`.
  - ChunkingService2 tạo: `text`, `section_summary`, `parent_section_summary`, `table_summary`, `table_row_group`, `text_table_like`.
  - Chunk size: max 2200 chars, overlap 250 chars. Table rows: 10 rows/group.
- Chat/RAG: `ChatService` → `RagRetrievalService` (7 bước) → `PromptBuilderService` → Groq LLM.
  - Retrieval: ANCHOR_TOP_K=30; final limit 10/20/60 tùy intent + locked scope.
  - Rerank: Cohere Cross-Encoder, optional.
  - LLM fallback: llama-3.3-70b → llama-3.1-8b-instant → llama-4-scout → qwen3-32b.
- Widget auth: `WidgetAuthFilter` xác thực `X-Widget-Key` cho `/api/chat/**`.

# 🔄 DATA FLOW

Ingestion flow:
1. `POST /api/documents/upload/{widgetId}` — multipart file.
2. Validate type, checksum dedup, lưu file vào `./uploads`.
3. Tạo Document record `PENDING` → `PROCESSING`.
4. Parse (PDFBox/TXT) + cleaner → `List<Section>`.
5. ChunkingService2 → chunk theo section hierarchy, pseudo-table detection.
6. Embed (Nomic) → upsert Qdrant + lưu DocumentChunk/DocumentSection vào MySQL.
7. Document `COMPLETED` hoặc `FAILED`.

Query flow:
1. `POST /api/chat/stream` + header `X-Widget-Key`.
2. WidgetAuthFilter → xác thực → đính kèm `Widget-Id` attribute.
3. ChatService: resolve ChatSession (lookup by widgetId + sessionKey).
4. Lưu user message.
5. RagRetrievalService.retrieve(question, widgetId) — 7 bước multi-stage.
6. Lấy top-10 history từ MySQL.
7. PromptBuilderService → gọi Groq (sync hoặc stream SSE).
8. Lưu assistant message + sources.

# 🌐 API DESIGN
**Đúng theo code hiện tại:**
- `POST /api/widgets` — tạo WidgetConfig, nhận apiKey.
- `POST /api/documents/upload/{widgetId}` — upload tài liệu (widgetId là path param bắt buộc).
- `GET /api/documents` — danh sách tất cả tài liệu (không filter widget).
- `POST /api/chat` — chat đồng bộ (cần `X-Widget-Key` header).
- `POST /api/chat/stream` — chat streaming SSE (cần `X-Widget-Key` header).

**Chưa implement:**
- `GET /api/chat/history`
- `DELETE /api/documents/{id}`
- `GET /api/widgets/{id}`

# 🧪 CURRENT STATUS
Đã hoàn thành:
- Pipeline RAG multi-tenant end-to-end.
- WidgetAuthFilter bảo vệ chat endpoints.
- Chunking theo section hierarchy với nhiều chunk types.
- Retrieval nâng cao: intent detection, heading lock, multi-stage expansion, optional Cohere rerank.
- LLM fallback mechanism.
- Widget embed IIFE + SSE streaming.
- Docker Compose full stack.

Còn thiếu:
- Auth cho `/api/documents/**` và `/api/widgets/**`.
- CORS cho production origins.
- API lịch sử chat + pagination.
- Monitoring (metrics/tracing/log correlation).
- Test tự động đầy đủ.
- DB migration tool (Flyway/Liquibase).

# 📊 PROGRESS SNAPSHOT (cập nhật 2026-05-05)
| Hạng mục | Trạng thái | Ghi chú |
|---|---|---|
| Ingestion tài liệu (upload/parse/chunk/embed) | Hoàn thành | Multi-tenant theo widgetId, section hierarchy chunking |
| Chat sync (`POST /api/chat`) | Hoàn thành | Widget auth, widgetId-scoped retrieval |
| Chat streaming (`POST /api/chat/stream`) | Hoàn thành | SSE token/done events, sources |
| Widget nhúng (bubble + iframe + IIFE) | Hoàn thành | X-Widget-Key qua URL param hoặc localStorage |
| Retrieval nâng cao | Hoàn thành | Intent + heading lock + expansion + optional rerank |
| Bảo mật chat | Hoàn thành | WidgetAuthFilter, X-Widget-Key header |
| Bảo mật document/widget endpoints | Chưa làm | Hiện permitAll |
| Quan sát hệ thống/monitoring | Chưa làm | Chưa có metrics/tracing |
| Test tự động | Chưa đủ | Chưa cover luồng chính |

# 🗺️ VISUAL DELIVERY MAP
```mermaid
flowchart LR
  Admin[Admin] -->|POST /api/widgets| BE[Spring Boot API]
  Admin -->|POST /api/documents/upload/{widgetId}| BE
  BE --> MySQL[(MySQL)]
  BE --> Qdrant[(Qdrant)]
  BE --> Nomic[Nomic Embed]
  User[User / Widget] -->|POST /api/chat/stream + X-Widget-Key| WAF[WidgetAuthFilter]
  WAF --> BE
  BE --> Groq[Groq LLM]
  Groq --> SSE[SSE token/done]
  SSE --> FE[WidgetChatPage]
```
