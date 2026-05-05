# Kiến trúc hệ thống

## Mô hình tổng thể
- Kiến trúc tách lớp FE/BE, chưa phải microservices.
- Backend thực thi pipeline RAG trên dữ liệu nội bộ theo mô hình **multi-tenant theo widget**.
- `WidgetConfig` là entity trung tâm: tài liệu, phiên chat, chunks, sections đều được scoped theo `widgetConfigId`.
- Dữ liệu persistence tách đôi:
  - MySQL cho dữ liệu giao dịch (documents, chat_sessions, chat_messages, document_chunks, document_sections, document_tables, widget_configs).
  - Qdrant cho vector search, filter theo `widgetId`.

## Sơ đồ luồng nghiệp vụ

```mermaid
flowchart LR
  Admin[Admin] -->|POST /api/widgets| API[Spring Boot API]
  Admin -->|POST /api/documents/upload/{widgetId}| API
  User[User / Widget] -->|POST /api/chat/** + X-Widget-Key| WAF[WidgetAuthFilter]
  WAF -->|Widget-Id attr| API
  API --> DB[(MySQL)]
  API --> VDB[(Qdrant)]
  API --> LLM[Groq LLM]
  API --> EMB[Nomic Embedding]
  API -.->|optional| RERANK[Cohere Rerank]
```

## Các module backend chính

| Module | Class | Vai trò |
|---|---|---|
| Document ingestion | `DocumentService` | Orchestrate upload → parse → chunk → embed → lưu trạng thái |
| Document parsing | `DocumentParserService` | Parse PDF (PDFBox + Tabula) / TXT, phát hiện bảng |
| Chunking nâng cao | `ChunkingService2` | Chia chunk theo section hierarchy, tạo summary chunks, phát hiện pseudo-table |
| Embedding | `EmbeddingService` | Tạo embedding (Nomic), upsert/search Qdrant theo `widgetId` |
| Chat orchestration | `ChatService` | Điều phối chat sync/stream, lưu hội thoại |
| RAG retrieval | `RagRetrievalService` | 7-bước: intent → heading lock → vector search → expansion → rerank → dedup/sort/budget |
| Intent detection | `QueryAnalyzerService` | Phân loại query type, tìm heading match, rewrite query |
| Prompt building | `PromptBuilderService` | Dựng prompt từ context + history + câu hỏi |
| LLM fallback | `LlmFallbackService` | Xây model sync/stream theo tên, hỗ trợ fallback models |
| Rerank | `RerankService` | Chấm điểm lại candidates bằng Cohere Cross-Encoder (optional) |
| Widget auth | `WidgetAuthFilter` | Xác thực `X-Widget-Key` header cho tất cả `/api/chat/**` |
| Widget management | `WidgetService` | Tạo widget config, sinh API key |

## Các module frontend chính
- `ChatPage.jsx`: giao diện chat chính, gọi `/api/chat/stream`.
- `DocumentPage.jsx`: quản lý danh sách tài liệu.
- `WidgetChatPage.jsx`: giao diện chat tối giản cho route `/widget` trong iframe.
- `widget/widget.js`: bootstrap widget (bubble + iframe) để nhúng website ngoài.
- `src/api/axiosInstance.js`: Axios instance với `VITE_API_URL`.
- `src/api/documentApi.js`: gọi document endpoints.
- `src/api/widgetApi.js`: gọi widget endpoints.

## Cơ sở dữ liệu MySQL — bảng thực tế

Tất cả entities dùng **UUID** làm PK (`@GeneratedValue(strategy = GenerationType.UUID)`).
Soft delete qua cột `deleted_at` với `@SQLRestriction("deleted_at IS NULL")`.

| Bảng | Entity class | Mô tả |
|---|---|---|
| `widget_configs` | `WidgetConfig` | Multi-tenant root: name, apiKey (UUID unique), allowedOrigin (JSON), uiConfig (JSON), isActive |
| `documents` | `Document` | Metadata file upload, FK `widget_config_id`, checksum để dedup |
| `document_sections` | `DocumentSection` | Section hierarchy (sectionKey, title, headingPathText, orderIndex), FK `widget_config_id` |
| `document_chunks` | `DocumentChunk` | Nội dung chunk (content, chunkType, sectionId, sectionTitle, headingPathText, orderIndex, sectionOrder), FK `widget_config_id` và `document_id` |
| `document_tables` | `DocumentTable` | Metadata bảng trong tài liệu |
| `chat_sessions` | `ChatSession` | Phiên chat per widget (sessionKey UUID, widgetOrigin, title), unique constraint `(widget_config_id, session_key)` |
| `chat_messages` | `ChatMessage` | Tin nhắn (role USER/ASSISTANT, content, sources JSON), FK `chat_session_id` |

## Qdrant — cấu trúc thực tế

- Collection: `documents`
- Vector size: `768` (Nomic `nomic-embed-text-v1.5`)
- Distance: `Cosine`
- Port: gRPC `6334`, HTTP `6333`
- Payload metadata mỗi chunk:
  - `widgetId`, `documentId`, `fileName`
  - `chunk_id`, `section_id`, `section_title`, `heading_path`
  - `chunk_type` (text / section_summary / table_summary / table_row_group / parent_section_summary / text_table_like)
  - `child_section_ids` (nếu chunk_type = parent_section_summary)
  - `table_id` (nếu chunk là table)
  - `page_start`, `page_end`, `order_index`, `section_order`
- Tất cả truy vấn đều filter theo `widgetId` để đảm bảo isolation.

## Retrieval pipeline (7 bước)

```
STEP 0: Intent detection (QueryAnalyzerService.analyze → QueryType)
         NORMAL_FACT / LIST_ALL / TABLE_LOOKUP / SECTION_SUMMARY / COUNT_QUERY / CROSS_PAGE_SECTION

STEP 1: Fetch all sections (1 lần dùng chung cho STEP 2)

STEP 2: Heading-first match
         → findMatchedSections() scoring: titleHits×3 + pathHits×1 - missedTerms×2 + bonus
         → nếu titleHits ≥ 2 → lock scope về section + descendants
         → ANCHOR_TOP_K = 30

STEP 3: Query rewriting (rewriteQuery → max 4 variants)
        + Vector search Qdrant (mỗi variant, filter out-of-scope nếu locked)

STEP 4: Build context pool
         [Locked mode]: fetch all chunks trong locked section tree từ DB
         [Semantic mode]: lexical anchors + window expansion + section expansion + table expansion
         [Expanded query (LIST_ALL/TABLE/SECTION_SUMMARY/COUNT)]: section + sibling + parent summary expansion

STEP 5: Rerank (Cohere Cross-Encoder, nếu enabled và không locked)
         topN = ceil(baseLimit × 1.3)
         → nếu maxScore ≥ 0.5 và lock chưa kích hoạt → Rerank-Guided Scope Lock

STEP 6: Dedup → sort (sectionOrder ASC, orderIndex ASC) → budget
         NORMAL:  FINAL_LIMIT=10,  MAX_CONTEXT_CHARS=18_000
         EXPANDED: FINAL_LIMIT=20, MAX_CONTEXT_CHARS=32_000
         LOCKED:  FINAL_LIMIT=60,  MAX_CONTEXT_CHARS=64_000

STEP 7: Log kết quả cuối
```

## Chunking types (ChunkingService2)

| chunkType | Mô tả |
|---|---|
| `text` | Đoạn văn bản thông thường (max 2200 ký tự, overlap 250) |
| `section_summary` | Tóm tắt section dài (> 4400 ký tự text) — đặt đầu section |
| `parent_section_summary` | Tóm tắt section cha + danh sách section con — dùng cho heading match |
| `table_summary` | Mô tả bảng (cột + preview 5 dòng đầu) |
| `table_row_group` | Nhóm 10 hàng bảng liên tiếp |
| `text_table_like` | Text có cấu trúc dạng bảng nhưng không convert được sang Markdown |

## Module preprocess/ (standalone)

- Nằm tại `preprocess/`: thư mục Java độc lập, **không** là dependency trong `Backend/pom.xml`.
- Dùng để thử nghiệm cục bộ pipeline parse/chunk/clean với các class: `chunk/`, `cleaner/`, `parser/`, `model/`, `service/`, `config/`.
- Không được invoke ở runtime của Backend.
- Backend sử dụng config `app.preprocessing.cleaner.*` trong `application.yml` cho logic cleaner riêng của mình.
