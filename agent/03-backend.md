# Backend (Spring Boot)

## Tech stack
- Java 21, Spring Boot 3.4.4
- Spring Web, Spring Data JPA, Spring Security
- LangChain4j BOM 1.0.0-beta1 (`langchain4j`, `langchain4j-open-ai`, `langchain4j-nomic`, `langchain4j-qdrant`)
- Apache PDFBox 3.0.2 + Tabula 1.0.5 (parse PDF + extract table)
- Lombok, Hibernate (MySQL dialect)

## Domain và dữ liệu

**Tất cả entity dùng UUID PK** (`GenerationType.UUID`), soft delete qua `deleted_at` + `@SQLRestriction`.

### WidgetConfig (`widget_configs`)
- Là **multi-tenant root**.
- Trường chính: `id` (UUID), `name`, `apiKey` (UUID, unique), `allowedOrigin` (JSON list), `uiConfig` (JSON map), `isActive`.
- `apiKey` được dùng làm `X-Widget-Key` để xác thực.

### Document (`documents`)
- FK `widget_config_id` → `WidgetConfig`.
- Trường chính: `id` (UUID), `fileName`, `filePath`, `fileType`, `mimeType`, `fileSize`, `checksum` (unique, dùng dedup), `status` (PENDING/PROCESSING/COMPLETED/FAILED), `chunkCount`.
- Soft delete qua `deletedAt`.

### DocumentSection (`document_sections`)
- Lưu hierarchy section của tài liệu sau parse.
- Trường chính: `sectionKey` (ví dụ: `"sec_6.2"`), `title`, `headingPathText`, `orderIndex`.
- FK `widget_config_id`.

### DocumentChunk (`document_chunks`)
- Nội dung chunk thực tế, được index vào Qdrant.
- Trường chính: `id` (UUID), `content`, `chunkType`, `sectionId`, `sectionTitle`, `headingPathText`, `orderIndex`, `sectionOrder`, `headingLevel`, `sourceFile`, `pageStart`, `pageEnd`, `tableId`, `childSectionIds`.
- FK `widget_config_id`, `document_id`.

### DocumentTable (`document_tables`)
- Metadata bảng trong tài liệu.

### ChatSession (`chat_sessions`)
- Phiên chat per widget per browser.
- Trường chính: `id` (UUID), `sessionKey` (UUID, gửi từ browser), `widgetOrigin`, `title`.
- Unique constraint: `(widget_config_id, session_key)`.
- FK `widget_config_id`.

### ChatMessage (`chat_messages`)
- Tin nhắn trong phiên.
- Trường chính: `id`, `role` (USER/ASSISTANT), `content`, `sources` (JSON).
- FK `chat_session_id`.

### Repositories
- `WidgetConfigRepository`: `findByApiKey(UUID)`
- `DocumentRepository`, `DocumentChunkRepository`, `DocumentSectionRepository`, `DocumentTableRepository`
- `ChatSessionRepository`, `ChatMessageRepository`

## Cấu hình quan trọng

### Profiles
- `application.yml`: profile mặc định `dev`, multipart max `50MB`, app.upload-dir `./uploads`, cleaner config.
- `application-dev.yml`: MySQL localhost:3306, Qdrant localhost:6334 (gRPC), Groq + Nomic + Cohere config.
- `application-docker.yml`: MySQL `ragchatbot-mysql:3306`, Qdrant `ragchatbot-qdrant:6334`.

### GroqConfig
- Chat model chính: `llama-3.3-70b-versatile`
- Base URL: `https://api.groq.com/openai/v1`
- Fallback models: `llama-3.1-8b-instant,meta-llama/llama-4-scout-17b-16e-instruct,qwen/qwen3-32b`
- Tạo `OpenAiChatModel` và `NomicEmbeddingModel` làm Spring beans.

### QdrantConfig
- Collection `documents`, vector size `768`, distance Cosine.
- Port gRPC `6334` (LangChain4j dùng gRPC), HTTP `6333` (REST API quản trị).
- Tạo `QdrantEmbeddingStore` bean.

### SecurityConfig
- CSRF disabled.
- CORS cho `http://localhost:5173` và `http://localhost:3000` trên `/api/**`.
- `WidgetAuthFilter` được đăng ký trước `UsernamePasswordAuthenticationFilter`.
- Tất cả request hiện `permitAll()` trong Security chain — auth thực sự do filter xử lý.

## Bảo mật: WidgetAuthFilter

```
Scope: CHỈ áp dụng cho /api/chat/**
Logic:
  1. Lấy header "X-Widget-Key" (UUID string)
  2. Nếu thiếu → HTTP 401 {"error": "Missing X-Widget-Key header."}
  3. Parse UUID → tra DB: widgetConfigRepository.findByApiKey(apiKey)
  4. Nếu không tìm thấy hoặc isActive=false → HTTP 401 {"error": "Invalid or inactive Widget Key."}
  5. Đính kèm widgetConfig.getId() vào request attribute "Widget-Id"
  6. Controller đọc @RequestAttribute("Widget-Id") UUID widgetId
```

**Lưu ý**: `/api/documents/**` và `/api/widgets/**` hiện không bị filter này chặn — admin tạo widget và upload tài liệu tự do.

## Ingestion pipeline (DocumentService)

1. `POST /api/documents/upload/{widgetId}` — nhận MultipartFile.
2. Validate file type (PDF/TXT), kiểm tra checksum tránh upload trùng.
3. Lưu file vào `./uploads`, tạo bản ghi `Document` trạng thái `PENDING`.
4. `DocumentParserService.parse()`:
   - PDF: PDFBox extract text + Tabula extract tables (chuyển sang Markdown).
   - TXT: đọc thẳng.
   - Áp dụng cleaner (noise removal, line-unbreak, whitespace normalization) theo `app.preprocessing.cleaner.*`.
   - Phát hiện section hierarchy theo heading patterns → trả về `List<Section>`.
5. `ChunkingService2.processSections2()` → `List<DocumentChunk>` (record):
   - parent_section_summary → section_summary → text/table_summary/table_row_group.
   - Pseudo-table detection: 3+ dòng liên tiếp với 3+ cột → chuyển Markdown table.
   - Chunk size: `MAX_CHARS_PER_TEXT_CHUNK=2200`, `OVERLAP_CHARS=250`.
   - Table: `TABLE_ROWS_PER_GROUP=10`.
6. `EmbeddingService.upsert()`:
   - Lưu `DocumentSection` vào MySQL.
   - Lưu `DocumentChunk` entity vào MySQL.
   - Embed từng chunk bằng Nomic, upsert vào Qdrant kèm payload đầy đủ (widgetId, section metadata, chunkType...).
7. Cập nhật `Document.status = COMPLETED` (hoặc `FAILED` nếu có lỗi), ghi `chunkCount`.

## Query/Chat pipeline (ChatService + RagRetrievalService)

1. Nhận `ChatRequest { sessionId, message }` + `widgetId` từ `@RequestAttribute`.
2. Resolve/tạo `ChatSession` trong MySQL (lookup by `(widgetId, sessionKey)`).
3. Lưu user message vào `chat_messages`.
4. `RagRetrievalService.retrieve(question, widgetId)` → 7 bước (xem `02-architecture.md`).
5. `ChatMessageRepository.findTop10BySessionIdOrderByCreatedAtDesc()` → lịch sử gần nhất.
6. `PromptBuilderService.build()` → system prompt + context + history + câu hỏi.
7. Gọi Groq LLM:
   - Sync: `OpenAiChatModel.generate()` → trả `ChatResponse { answer, sources }`.
   - Stream: `SseEmitter`, `LlmFallbackService.buildStreamingModel(modelName)`, emit `event: token`, kết thúc `event: done` kèm sources.
8. Lưu assistant message vào `chat_messages`.

## LlmFallbackService

- Tạo `OpenAiChatModel` hoặc `OpenAiStreamingChatModel` theo model name.
- Khi Groq rate-limit model chính → thử lần lượt các model trong `fallback-models`.
- Fallback order hiện tại: `llama-3.3-70b-versatile` → `llama-3.1-8b-instant` → `meta-llama/llama-4-scout-17b-16e-instruct` → `qwen/qwen3-32b`.

## RerankService (optional)

- Gọi Cohere Rerank API (`rerank-multilingual-v3.0`) để chấm điểm lại từng cặp (query, chunk).
- Chỉ kích hoạt khi `COHERE_RERANK_ENABLED=true` và có `COHERE_API_KEY`.
- `LOW_CONFIDENCE_THRESHOLD`: log warning khi max score < ngưỡng này.
- Khi disabled: trả về danh sách gốc không đổi thứ tự.

## Logging cấu hình
```yaml
logging:
  level:
    KLTN.RAG_CHATBOT_BE.service.RerankService: DEBUG     # dev profile
    KLTN.RAG_CHATBOT_BE.service.DocumentParserService: DEBUG
    KLTN.RAG_CHATBOT_BE.service.ChunkingService2: DEBUG
```
