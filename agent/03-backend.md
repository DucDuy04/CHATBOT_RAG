# Backend (Spring Boot)

## Tech stack
- Java 21, Spring Boot 3.4.4
- Spring Web, Spring Data JPA, Spring Security
- LangChain4j BOM 1.0.0-beta1 (`langchain4j`, `langchain4j-open-ai`, `langchain4j-nomic` — không còn `langchain4j-qdrant`; Qdrant I/O qua REST trong `EmbeddingService`)
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
- `application-dev.yml`: MySQL localhost:3306, Qdrant REST `http-port: 6333`, Groq + Nomic + Cohere config.
- `application-docker.yml`: MySQL `ragchatbot-mysql:3306`, Qdrant host `ragchatbot-qdrant`, REST port `6333`.

### GroqConfig
- Chat model chính: `llama-3.3-70b-versatile`
- Base URL: `https://api.groq.com/openai/v1`
- Fallback models: `llama-3.1-8b-instant,meta-llama/llama-4-scout-17b-16e-instruct,qwen/qwen3-32b`
- Tạo `OpenAiChatModel` và `NomicEmbeddingModel` làm Spring beans.

### QdrantConfig (`index.qdrant`)
- Collection `documents`, vector size `768`, distance Cosine.
- HTTP `6333`: bootstrap collection qua REST (`ApplicationRunner` trong `QdrantConfig`).
- **Qdrant write path**: REST upsert/search qua `EmbeddingService` (`index.embedding`) — không dùng LangChain4j `QdrantEmbeddingStore` / gRPC write.
- Port gRPC `6334` có thể còn trong compose cho tooling; backend không ghi vector qua gRPC.

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
4. `ingest.parser.DocumentParserService.parse()`:
   - PDF: PDFBox + Tabula → `RawTableModel` (structured grid, không Markdown bridge làm primary).
   - DOCX: Apache POI logical grid → `RawTableModel` (`physicalColIndex` cho slot mapping).
   - TXT: đọc thẳng.
   - Cleaner theo `app.preprocessing.cleaner.*`; section hierarchy → `List<Section>`.
5. `ingest.normalize.NormalizedTableService` → `normalized_table_row` (+ `cells_json`, `group_context`).
6. `ingest.chunking.ChunkingService2.processSections2()` → `List<DocumentChunk>`:
   - `parent_section_summary` → `section_summary` → `text` / `table_summary` / `normalized_table_row`.
   - Chunk size: `MAX_CHARS_PER_TEXT_CHUNK=2200`, `OVERLAP_CHARS=250`.
7. `index.embedding.EmbeddingService.upsert()` (Qdrant REST, UTF-8 safe):
   - Lưu `DocumentSection` vào MySQL.
   - Lưu `DocumentChunk` entity vào MySQL.
   - Embed từng chunk bằng Nomic, upsert vào Qdrant kèm payload đầy đủ (widgetId, section metadata, chunkType...).
8. Cập nhật `Document.status = COMPLETED` (hoặc `FAILED` nếu có lỗi), ghi `chunkCount`.

## Query/Chat pipeline (`rag.runtime.ChatService` + `rag.retrieve.RagRetrievalService`)

1. Nhận `ChatRequest { sessionId, message }` + `widgetId` từ `@RequestAttribute`.
2. Resolve/tạo `ChatSession` trong MySQL (lookup by `(widgetId, sessionKey)`).
3. Lưu user message vào `chat_messages`.
4. `RagRetrievalService.retrieve(question, widgetId)` → 7 bước (xem `02-architecture.md`); `QueryAnalyzerService` + `PromptBudgetResolver` trong retrieval path.
5. `ChatMessageRepository.findTop10BySessionIdOrderByCreatedAtDesc()` → lịch sử gần nhất.
6. `PromptBuilderService.build()` → system prompt + context + history + câu hỏi.
7. Gọi Groq LLM qua `LlmFallbackService` (`llm`):
   - Sync: `OpenAiChatModel.generate()` → trả `ChatResponse { answer, sources }`.
   - Stream: `SseEmitter`, `LlmFallbackService.buildStreamingModel(modelName)`, emit `event: token`, kết thúc `event: done` kèm sources.
8. Lưu assistant message vào `chat_messages`.
9. `RagTokenAudit` / `RagLatencyTrace` (`audit.metrics`) ghi structured logs, không log full prompt.

## LlmFallbackService (`llm`)

- Tạo `OpenAiChatModel` hoặc `OpenAiStreamingChatModel` theo model name.
- Khi Groq rate-limit model chính → thử lần lượt các model trong `fallback-models`.
- Fallback order hiện tại: `llama-3.3-70b-versatile` → `llama-3.1-8b-instant` → `meta-llama/llama-4-scout-17b-16e-instruct` → `qwen/qwen3-32b`.

## RerankService (`rag.rerank`, optional)

- Gọi Cohere Rerank API (`rerank-multilingual-v3.0`) để chấm điểm lại từng cặp (query, chunk).
- Chỉ kích hoạt khi `COHERE_RERANK_ENABLED=true` và có `COHERE_API_KEY`.
- `LOW_CONFIDENCE_THRESHOLD`: log warning khi max score < ngưỡng này.
- Khi disabled: trả về danh sách gốc không đổi thứ tự.

## Backend package map (25D)

| Concern | Package |
|---|---|
| Parse / raw tables | `ingest.parser` |
| Table normalization | `ingest.normalize` |
| Chunking | `ingest.chunking` |
| Embed + Qdrant REST I/O | `index.embedding` |
| Qdrant bootstrap / purge | `index.qdrant` |
| Retrieval | `rag.retrieve` |
| Prompt | `rag.prompt` |
| Query analysis | `rag.analysis` |
| Rerank | `rag.rerank` |
| Context budget | `rag.budget` |
| Chat runtime | `rag.runtime` |
| Audit metrics | `audit.metrics` |
| LLM provider + fallback | `llm` |
| Upload lifecycle + admin services | `service` |

`DocumentService` giữ trong `service` vì phối hợp API upload, DB, parse, chunk, embed, Qdrant purge và document status.

## Logging cấu hình
```yaml
logging:
  level:
    KLTN.RAG_CHATBOT_BE.rag.rerank.RerankService: DEBUG     # dev profile
    KLTN.RAG_CHATBOT_BE.ingest.parser.DocumentParserService: DEBUG
    KLTN.RAG_CHATBOT_BE.ingest.chunking.ChunkingService2: DEBUG
```
