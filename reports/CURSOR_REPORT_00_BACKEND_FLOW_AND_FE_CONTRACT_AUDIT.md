# Cursor Report 00 — Backend Flow & FE Contract Audit

**Ngày**: 2026-05-09  
**Scope**: Chỉ đọc và audit Backend hiện tại + map với contract Frontend (`Frontend/src/api/*`, mocks). **Không sửa source code.**  
**Mục đích**: Source of truth cho các prompt backend tiếp theo.

---

## 1. Nguồn đã đọc (bắt buộc theo prompt)

| Nhóm | Path / pattern | Ghi chú ngắn |
|------|----------------|--------------|
| Cursor rules | `.cursor/rules/*.mdc` (6 file) | Core stack, backend RAG file hints, FE/widget, deploy/env, DB/vector, report format |
| Reports | `reports/CURSOR_REPORT_00_PROJECT_AUDIT.md`, `02_API_LAYER.md`, `03`–`12A`, `11` | Baseline FE; 02 checklist 40 endpoint mock/real; các report page mô tả UI/contract mong đợi |
| FE API | `Frontend/src/api/apiMode.js`, `axiosInstance.js`, `dashboardApi.js`, `chatbotsApi.js`, `documentsApi.js`, `playgroundApi.js`, `analyticsApi.js`, `settingsApi.js`, `publicChatApi.js`, `index.js` | Contract path, query, body, SSE/public fetch |
| FE mocks | `Frontend/src/mocks/*.js` (6 file) | Hình dạng response/pagination FE đang kỳ vọng |
| Backend | `Backend/pom.xml`, `application.yml`, `application-*.yml`, `**/*Controller*.java`, `**/*Service*.java`, `**/*Repository*.java`, `domain/**/*.java`, `dto/**/*.java`, `config/**/*.java`, `**/*Security*.java`, `**/*Filter*.java` | Stack + toàn bộ surface API và pipeline RAG |

**Ghi chú**: Không có thư mục migration Flyway/Liquibase trong `Backend/`. JPA `ddl-auto: update` (dev/docker profile). Không có `Backend/README*`.

---

## 2. Backend current architecture map

### 2.1 Stack (mục A — tóm tắt)

| Hạng mục | Giá trị (từ `pom.xml` + `application*.yml`) |
|----------|-----------------------------------------------|
| Framework | **Spring Boot 3.4.4** (`spring-boot-starter-parent`) |
| Java | **21** |
| Build | **Maven** (`mvnw` / `mvnw.cmd`) |
| Web / JSON | `spring-boot-starter-web` |
| DB chính | **MySQL** (`mysql-connector-j`, JPA Hibernate) |
| Vector DB | **Qdrant** (`langchain4j-qdrant`, collection `documents`, vector size **768** trong config đang dùng) |
| Auth / security | **Spring Security** bật nhưng `SecurityConfig`: `anyRequest().permitAll()`; filter tùy chỉnh **`WidgetAuthFilter`** chỉ enforce **UUID** header **`X-Widget-Key`** cho prefix **`/api/chat`** |
| AI / embedding | **Groq** (OpenAI-compatible) LLM qua `OpenAiChatModel` / streaming; **Nomic** embedding (`langchain4j-nomic`); **Cohere rerank** optional (`RerankService`, `COHERE_*`) |
| Swagger / OpenAPI | **Không** (không dependency springdoc; không annotation OpenAPI trong code đã quét) |
| Validation library | **Không** `spring-boot-starter-validation` / `@Valid` toàn cục; validation thủ công (`IllegalArgumentException`, null checks controller) |
| Global exception handler | **Không** `@ControllerAdvice` / `@ExceptionHandler` (grep không có) |

### 2.2 Cây package / tầng logic

```
KLTN.RAG_CHATBOT_BE
├── RagChatbotBeApplication.java          # entry
├── api/                                   # REST controllers (3)
│   ├── ChatController          (/api/chat)
│   ├── DocumentController      (/api/documents)
│   └── WidgetController        (/api/widgets)
├── config/
│   ├── SecurityConfig, WidgetAuthFilter
│   ├── GroqConfig                # OpenAiChatModel + Nomic EmbeddingModel beans
│   ├── QdrantConfig              # QdrantEmbeddingStore + collection init runner
│   └── AppConfig                 # streamingExecutor (SSE)
├── dto/                                   # request/response POJO (không có suffix Request/Response cho mọi API)
├── domain/                                # JPA entities + enums
│   ├── widget/   WidgetConfig, WidgetConfigRepository
│   ├── document/ Document, DocumentChunk, DocumentSection, DocumentTable + repositories
│   ├── chat/     ChatSession, ChatMessage + repositories
│   └── enums/    DocumentStatus, MessageRole
├── service/                               # business + RAG
│   ├── DocumentService, DocumentParserService, ChunkingService2, EmbeddingService
│   ├── RagRetrievalService, QueryAnalyzerService, RerankService
│   ├── ChatService, PromptBuilderService, LlmFallbackService
│   └── WidgetService
└── record/                                # immutable records (Section, DocumentChunk pipeline)
```

---

## 3. Current RAG flow (ingest → chunk → embed → store → retrieve → answer)

### 3.1 Ingest

| Câu hỏi | Chi tiết từ source |
|---------|-------------------|
| Entry point | **`POST /api/documents/upload/{widgetId}`** → `DocumentController.upload` → `DocumentService.uploadAndProcess` |
| Input | `MultipartFile file` (một file), path **`widgetId`** (UUID `WidgetConfig`) |
| File lưu đâu | **`${app.upload-dir}`** (mặc định `./uploads`), tên file `timestamp_sanitizedOriginalName`; `Document.filePath` lưu đường dẫn đĩa |
| Metadata document | **MySQL** bảng `documents` (`Document`: `widget_config_id`, `file_name`, `file_type`, `file_size`, `status`, `chunk_count`, timestamps, soft delete `deleted_at`) |
| Ghi chú vs FE | FE contract `POST /api/documents/upload` multipart field **`files`** (multi); BE nhận **`file`** đơn và **bắt buộc** `widgetId` trên URL — **Different** |

### 3.2 Chunking

| Câu hỏi | Chi tiết |
|---------|----------|
| Service | **`ChunkingService2.processSections2`** |
| Input | `List<Section>` từ `DocumentParserService.parse` |
| Chunk “fields” (record `KLTN.RAG_CHATBOT_BE.record.DocumentChunk`) | `content`, `header`, `startPage`/`endPage`, `chunkType`, `sectionId`, `parentId`, `tableId`, `headingPathText`, `orderIndex`, **`tokenCount`** (ước lượng `len/4`), `sectionOrder`, `headingLevel`, `childSectionIds` (parent summary) |
| Lưu entity | `DocumentService.saveChunks` → **`DocumentChunk`** (JPA): `chunkIndex`, `content`, `chunkType`, section/table FK, `pageStart`/`pageEnd`, `orderIndex`, `sectionOrder`, `tokenCount`, `headingLevel`, `childSectionIds`, `prevChunk`/`nextChunk` links |
| Loại chunk | `text`, `text_table_like`, `section_summary`, `parent_section_summary`, `table_summary`, `table_row_group`, … |

### 3.3 Embedding

| Câu hỏi | Chi tiết |
|---------|----------|
| Service | **`EmbeddingService.embedAndStore`** |
| Model / provider | **Nomic** `NomicEmbeddingModel` (`nomic.embedding-model` trong yaml) |
| Vector dimension | **`qdrant.vector-size: 768`** (phải khớp model; comment cũ trong `application-dev.yml` đề cập Gemini 3072 — chỉ là comment, không phải config active) |
| Retry / error | `embedAll` không bọc retry riêng; lỗi bubble lên `DocumentService` → document `FAILED`. **`EmbeddingService.search`** (Qdrant REST) catch ở `RagRetrievalService` (log + continue variant) |

### 3.4 Vector storage

| Câu hỏi | Chi tiết |
|---------|----------|
| Store | **Qdrant** qua `QdrantEmbeddingStore.addAll` |
| Collection | Config **`qdrant.collection-name`** → `documents` |
| Metadata / filter | Payload: `widgetId`, `document_id`, `chunk_id`, `section_id`, `chunk_type`, pages, `child_section_ids`, prev/next chunk ids, … — retrieval filter **`widgetId`** trong `EmbeddingService.search` |
| Mapping document / “chatbot” | **`widgetId`** (UUID `WidgetConfig`) là tenant cho mọi chunk + vector; **không** có entity `Chatbot` riêng |
| Delete / purge | **Không** có service/controller xóa point Qdrant theo `documentId`/`widgetId` trong code đã đọc; entity có `deleted_at` (soft delete) nhưng không thấy cascade xóa vector |

### 3.5 Retrieval

| Câu hỏi | Chi tiết |
|---------|----------|
| Service | **`RagRetrievalService.retrieveWithMetadata`** |
| Vector search | `EmbeddingService.search(queryVariant, topK=30, widgetId)` → Qdrant; nhiều query variants từ `QueryAnalyzerService.rewriteQuery` |
| DB mở rộng | `DocumentChunkRepository` / `DocumentSectionRepository`: heading lock, section/table expansion, lexical anchors, rerank-guided lock, v.v. |
| Output | `List<RetrievedContext>`: `chunkId`, `documentId`, `fileName`, `content`, `chunkType`, `sectionId`, `sectionTitle`, `headingPathText`, `pageStart`/`pageEnd` (không có score riêng trong DTO; score nội bộ rerank nếu bật) |

### 3.6 AI response

| Câu hỏi | Chi tiết |
|---------|----------|
| Service | **`ChatService`** gọi **`LlmFallbackService.generateWithFallback`** (sync) hoặc **`OpenAiStreamingChatModel`** (SSE) |
| System prompt | **`PromptBuilderService.getSystemPrompt()`** — prompt dài cố định (RAG, bảng, đếm, citation…) |
| Model config | `GroqConfig`: `temperature(0.1)`, `maxTokens(1000)`; streaming build thêm `temperature(0.1)`; fallback models từ `groq.fallback-models` |
| Streaming / SSE | **`POST /api/chat/stream`** — events `token` (JSON `{\"token\":\"...\"}`), `done` (plain text JSON array sources) |
| Citations | Response **`ChatResponse.sources`** (`SourceDto`: fileName, sectionTitle, pages, chunkType, chunkText); lưu JSON trong `ChatMessage.sources` |

---

## 4. Existing backend endpoints

| Method | Path | Controller | Auth required? | Request | Response | Notes |
|--------|------|--------------|----------------|---------|----------|-------|
| POST | `/api/widgets` | `WidgetController` | **Không** (permitAll) | JSON `WidgetCreateRequest` | `WidgetCreateResponse` (widgetConfigId, apiKey UUID, uploadEndpoint, …) | Tạo tenant/widget; apiKey dùng làm **X-Widget-Key** cho chat |
| POST | `/api/chat` | `ChatController` | **Có** — `WidgetAuthFilter`: header **`X-Widget-Key`** (UUID), chỉ áp dụng `/api/chat` | JSON `ChatRequest` { sessionId, message } | `ChatResponse` { answer, sources } | Không phải JWT |
| POST | `/api/chat/stream` | `ChatController` | **Có** — cùng filter | JSON `ChatRequest` | `SseEmitter` — events `token`, `done` | Timeout 180s; executor `streamingExecutor` |
| POST | `/api/documents/upload/{widgetId}` | `DocumentController` | **Không** ở filter (upload không qua `/api/chat`) | `multipart/form-data` param **`file`** | `DocumentUploadResponse` | widgetId phải tồn tại |
| GET | `/api/documents` | `DocumentController` | **Không** | — | `List<DocumentListItemResponse>` | Không pagination/filter |

**Tổng**: **5** route patterns thực tế (6 nếu tách `/api/chat` và `/api/chat/stream`).

---

## 5. FE contract mapping (checklist prompt + `settingsApi` mở rộng)

**Chú thích cột Backend status**: `Existing` = cùng method + path như FE; `Partial` = có API/backend liên quan nhưng thiếu shape/path/auth; `Missing` = không có endpoint tương ứng; `Different` = có luồng tương tự nhưng path/header/body khác contract FE.

### 5.1 Dashboard

| FE Area | Method | Path | FE request shape | FE response shape (mock / axios) | Backend status | Existing backend if different | Gap / action |
|---------|--------|------|------------------|----------------------------------|----------------|--------------------------------|--------------|
| Dashboard | GET | `/api/dashboard/summary` | — | Object metrics (mock: activeChatbots, messages7d, …) | **Missing** | — | Aggregate từ DB hiện có (sessions/messages/docs) hoặc stub |
| Dashboard | GET | `/api/dashboard/message-volume` | `?days=` | Array `{ date, count }[]` | **Missing** | — | Time-series query |
| Dashboard | GET | `/api/dashboard/top-chatbots` | `?limit=` | Array top bots | **Missing** | — | Cần map `WidgetConfig` / future `Chatbot` → metric |
| Dashboard | GET | `/api/dashboard/activity` | `?limit=` | Array activity events | **Missing** | — | Cần event log hoặc stub |

### 5.2 Chatbots

| FE Area | Method | Path | FE request | FE response | Backend status | Existing backend if different | Gap / action |
|---------|--------|------|------------|-------------|----------------|--------------------------------|--------------|
| Chatbots | GET | `/api/chatbots` | query search, status, domain, page, size | Paginated `{ items, page, size, total, totalPages }` | **Missing** | — | CRUD + pagination; id FE mock dạng `cb-001` vs BE UUID |
| Chatbots | POST | `/api/chatbots` | `{ name, description, domain }` | Chatbot object | **Partial** | `POST /api/widgets` tạo `WidgetConfig` + apiKey + origins + uiConfig | Đổi path/DTO hoặc adapter; thiếu domain/description model |
| Chatbots | GET | `/api/chatbots/:id` | — | Chatbot detail | **Missing** | — | Entity chatbot riêng hoặc map Widget |
| Chatbots | PUT | `/api/chatbots/:id` | payload name, description, status, systemPrompt, modelConfig | Updated bot | **Missing** | — | `WidgetConfig` chưa có systemPrompt/model fields active (comment trong entity) |
| Chatbots | DELETE | `/api/chatbots/:id` | — | `{ success }` soft delete | **Missing** | — | Soft delete pattern đã có `deleted_at` trên widget |
| Chatbots | GET | `/api/chatbots/:id/embed-config` | — | embed UI fields | **Missing** | — | Dùng `uiConfig` JSON / cột mới |
| Chatbots | PUT | `/api/chatbots/:id/embed-config` | `{ widgetColor, welcomeMessage, position, allowedOrigins }` | embed config | **Missing** | — | Map vào `allowedOrigin` + `uiConfig` |

### 5.3 Documents

| FE Area | Method | Path | FE request | FE response | Backend status | Existing backend if different | Gap / action |
|---------|--------|------|------------|-------------|----------------|--------------------------------|--------------|
| Documents | POST | `/api/documents/upload` | FormData **`files`** (multi) | Array uploaded doc metadata | **Different** | `POST /api/documents/upload/{widgetId}` + **`file`** đơn | Thêm route FE-compatible; resolve `chatbotId` → widgetId; multi-file |
| Documents | GET | `/api/documents` | query filters + page/size | Paginated | **Partial** | `GET /api/documents` list phẳng | Thêm query + pagination; field names `filename` vs `fileName` |
| Documents | GET | `/api/documents/:id/status` | — | `{ id, status, progress, error }` | **Missing** | — | Progress: BE không có % realtime (chỉ status enum) |
| Documents | GET | `/api/documents/:id/chunks` | — | Array chunk preview | **Missing** | — | Đọc `document_chunks` |
| Documents | POST | `/api/documents/:id/assign` | `{ chatbotId }` | Updated doc | **Missing** | — | Gán widget/document quan hệ |
| Documents | POST | `/api/documents/:id/retry` | — | `{ success }` | **Missing** | — | Re-run pipeline + vector |
| Documents | DELETE | `/api/documents/:id` | — | `{ success }` | **Missing** | — | + xóa Qdrant points |

### 5.4 Playground

| FE Area | Method | Path | FE request | FE response | Backend status | Existing backend if different | Gap / action |
|---------|--------|------|------------|-------------|----------------|--------------------------------|--------------|
| Playground | POST | `/api/playground/chat` | SSE JSON `{ chatbotId, message, sessionId, overrideParams }` + **JWT optional** | Events `token`, `done` với JSON result | **Different** | `POST /api/chat/stream` + **X-Widget-Key**; body không có chatbotId (widget từ key) | Auth model khác; map chatbotId↔widget; overrideParams |
| Playground | POST | `/api/playground/compare` | `{ chatbotId, message, configA, configB }` | `{ configA, configB }` | **Missing** | — | 2x LLM hoặc reuse service |
| Playground | GET | `/api/playground/sessions` | `?chatbotId=` | Array sessions | **Missing** | — | Phân biệt playground vs `chat_sessions` (hiện gắn widget) |
| Playground | DELETE | `/api/playground/sessions/:id` | — | `{ success }` | **Missing** | — | — |
| Playground | GET | `/api/playground/export/:sessionId` | — | blob / object | **Missing** | — | Export messages |

### 5.5 Analytics

| FE Area | Method | Path | FE request | FE response | Backend status | Existing backend if different | Gap / action |
|---------|--------|------|------------|-------------|----------------|--------------------------------|--------------|
| Analytics | GET | `/api/analytics/summary` | `from, to, chatbotId` | summary object | **Missing** | — | Aggregate |
| Analytics | GET | `/api/analytics/daily` | same | daily array | **Missing** | — | — |
| Analytics | GET | `/api/analytics/by-chatbot` | `from, to` | by-chatbot array | **Missing** | — | — |
| Analytics | GET | `/api/analytics/unanswered` | `limit` | list | **Missing** | — | Cần rule “unanswered” + lưu flag |
| Analytics | GET | `/api/analytics/sessions` | filters + page/size | paginated | **Missing** | — | vs `chat_sessions` cần rating, filters |
| Analytics | GET | `/api/analytics/sessions/:id/messages` | — | messages[] | **Missing** | — | `chat_messages` có data |
| Analytics | POST | `/api/chat/feedback` | `{ messageId, rating, comment }` | success | **Missing** | — | Bảng feedback mới hoặc mở rộng `ChatMessage` |

### 5.6 Settings (checklist prompt)

| FE Area | Method | Path | FE request | FE response | Backend status | Existing backend if different | Gap / action |
|---------|--------|------|------------|-------------|----------------|--------------------------------|--------------|
| Settings | GET | `/api/settings/profile` | — | profile object | **Missing** | — | User/auth subsystem chưa có |
| Settings | PUT | `/api/settings/profile` | payload | profile | **Missing** | — | — |
| Settings | GET | `/api/settings/api-keys` | — | list keys (masked) | **Missing** | — | Khác `WidgetConfig.apiKey` (một key/widget) |
| Settings | POST | `/api/settings/api-keys` | `{ name }` | `{ key, plainTextKey }` | **Missing** | — | — |
| Settings | DELETE | `/api/settings/api-keys/:id` | — | `{ success }` | **Missing** | — | — |

### 5.7 Settings — mở rộng trong `settingsApi.js` (ngoài bullet prompt nhưng là contract FE)

| FE Area | Method | Path | Backend status | Ghi chú |
|---------|--------|------|----------------|--------|
| Settings | GET | `/api/settings/team` | **Missing** | — |
| Settings | POST | `/api/settings/team/invite` | **Missing** | — |
| Settings | PUT | `/api/settings/team/:userId/role` | **Missing** | — |
| Settings | DELETE | `/api/settings/team/:userId` | **Missing** | — |

### 5.8 Public chat

| FE Area | Method | Path | FE request | FE response | Backend status | Existing backend if different | Gap / action |
|---------|--------|------|------------|-------------|----------------|--------------------------------|--------------|
| Public | POST | `/api/public/chat` | Header **`x-api-key`**, body `{ message, sessionId }`, **no JWT** | JSON answer + sessionId + sources | **Different** | `POST /api/chat` + **`X-Widget-Key`** (UUID), cùng body shape gần giống | Đổi header name + path hoặc gateway; public key có thể = widget apiKey string |

### 5.9 Prototype FE cũ (không nằm trong bảng checklist 36 nhưng còn trong repo)

| File | Endpoint thực tế | Ghi chú |
|------|------------------|---------|
| `Frontend/src/api/documentApi.js` | Upload/list khớp **legacy** BE (`/api/documents/upload/:widgetId`, …) | `reports/CURSOR_REPORT_02_API_LAYER.md` đã ghi không xóa; app có thể dùng song song |

---

## 6. Đếm trạng thái (checklist **36** endpoint trong mục E của prompt)

| Trạng thái | Số lượng | Ghi chú |
|-------------|----------|---------|
| **Existing** | **0** | Không có path trùng khớp FE |
| **Partial** | **2** | `GET /api/documents` vs list có filter/page; `POST /api/widgets` vs tạo chatbot (khái niệm gần) |
| **Different** | **3** | Upload path/multipart; Playground SSE vs `/api/chat/stream` + header; Public chat vs widget-key chat |
| **Missing** | **31** | Còn lại |

**Thêm** 4 endpoint Settings team → toàn bộ `settingsApi`: **41** call FE, trong đó **35 Missing** + **2 Partial** + **3 Different** + **0 Existing** (cùng công thức trên + 4 missing team).

---

## 7. Risk / gap analysis (mục G)

| Nhóm endpoint | Nhanh với DB hiện có | Cần entity/table mới | Reuse core RAG | Auth / security | Stub hợp lý khi thiếu data |
|---------------|---------------------|----------------------|----------------|-----------------|---------------------------|
| Dashboard / Analytics | Một phần (đếm từ `chat_messages`, `documents`, `widget_configs`) | Rating, feedback, activity feed, “unanswered” | Không | JWT/tenant chưa có | Có — stub aggregate |
| Chatbots CRUD | Giới hạn: chỉ có `WidgetConfig` | `Chatbot` riêng nếu FE bắt buộc id string + domain fields | Không | Phân quyền admin | Partial map widget |
| Documents FE API | `Document` + chunks đã có | `progress`, multi-assign | **DocumentService** + **EmbeddingService** | JWT upload hiện open | Status progress stub |
| Playground | `ChatSession`/`ChatMessage` có sẵn | Phân loại playground vs widget; `overrideParams` | **ChatService** + retrieval | Bearer optional FE vs BE none | Sessions stub |
| Public chat | Cùng **ChatService** | — | **Full reuse** | **`x-api-key` vs X-Widget-Key`** quyết định rõ | — |
| Settings | Không | User, ApiKey entity, Team | Không | JWT bắt buộc | Stub profile/keys |
| Vector delete | — | — | Cần Qdrant delete API | — | Block nếu không implement purge |

**Block contract**: Playground SSE và Public chat đều cần chốt **một** mô hình auth (JWT + chatbotId vs widget key vs x-api-key) để không phá FE.

---

## 8. Recommended backend implementation prompts (thứ tự đề xuất — chỉnh theo code thật)

1. **01_BACKEND_API_ENVELOPE_AND_ERRORS** — (optional nhưng nên sớm) exception handler, thống nhất lỗi JSON cho axios interceptor (`message` / `error`); validation nếu cần.  
2. **02_BACKEND_CHATBOTS_AND_EMBED_CONFIG_API** — map `WidgetConfig` ↔ `/api/chatbots` + `/embed-config` (JSON `uiConfig` + origins).  
3. **03_BACKEND_DOCUMENTS_API_WRAP_INGEST** — `POST /api/documents/upload` (multi-file), pagination/filter, status/chunks/assign/retry/delete + Qdrant purge.  
4. **04_BACKEND_PUBLIC_CHAT_AND_PLAYGROUND_CHAT_API** — `POST /api/public/chat` (`x-api-key`); `POST /api/playground/chat` SSE (JWT + chatbotId) nội bộ gọi **ChatService** / retrieval.  
5. **05_BACKEND_PLAYGROUND_SESSIONS_COMPARE_EXPORT** — sessions CRUD, compare, export.  
6. **06_BACKEND_DASHBOARD_ANALYTICS_AGGREGATES** — dashboard 4 route + analytics 6 GET + feedback POST.  
7. **07_BACKEND_SETTINGS_PROFILE_API_KEYS_TEAM** — profile, keys, team (nếu giữ FE).  
8. **08_BACKEND_INTEGRATION_QA_REAL_API_SWITCH** — đối chiếu `reports/CURSOR_REPORT_11_INTEGRATION_QA_CHECKLIST_AUDIT.md`, `VITE_USE_MOCK_API=false`, CORS (`SecurityConfig` chỉ localhost:5173/3000).

---

## 9. Validation

| Command | Kết quả | Ghi chú |
|---------|---------|---------|
| `cd Backend; .\mvnw.cmd -DskipTests compile` | **PASS** | `BUILD SUCCESS` (~34s), Java 21, 45 source files |

---

## 10. Tóm tắt điều hành

- **Stack**: Spring Boot 3.4, Java 21, Maven, MySQL + JPA, Qdrant, LangChain4j, Groq LLM, Nomic embeddings, optional Cohere rerank; Security permitAll + **X-Widget-Key** chỉ cho `/api/chat`.  
- **Core RAG**: **Đã có đầy đủ** luồng ingest → chunk → embed → Qdrant → retrieval (hybrid) → LLM (+ SSE).  
- **FE checklist 36**: **Existing 0 / Partial 2 / Different 3 / Missing 31**; thêm Settings team +4 → **Missing 35** trong tổng **41** call `settingsApi`+checklist.  
- **Report path**: `reports/CURSOR_REPORT_00_BACKEND_FLOW_AND_FE_CONTRACT_AUDIT.md`
