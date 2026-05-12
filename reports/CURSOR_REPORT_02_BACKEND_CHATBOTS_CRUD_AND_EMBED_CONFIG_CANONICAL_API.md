# Cursor Report 02 - BACKEND_CHATBOTS_CRUD_AND_EMBED_CONFIG_CANONICAL_API

## 1. Mức độ hiểu task

- **Task là gì?** Triển khai đủ nhóm API CRUD chatbot + GET/PUT embed config theo contract `Frontend/src/api/chatbotsApi.js`, map `WidgetConfig` → object chatbot, dùng `uiConfig` JSON cho các field không có cột riêng, soft delete qua `deleted_at`, không đổi core RAG, không sửa FE/mock.
- **Hiểu task:** 95%
- **Phần chắc chắn:**
  - Contract path/method/query giống `chatbotsApi.js`.
  - `WidgetConfig` có `id` (UUID), `name`, `allowedOrigin` (JSON list), `uiConfig` (JSON map), `isActive`, `deletedAt`, `apiKey`.
  - Đếm document qua `Document.widgetConfig`; đếm message qua `ChatMessage` → `ChatSession` → `widgetConfig`.
- **Phần còn giả định:**
  - Native SQL JSON (`JSON_EXTRACT`/`JSON_UNQUOTE`) đúng với MySQL 8 + kiểu JSON của cột `ui_config`.
  - FE khi bật real API dùng UUID string làm `id` (không dùng mock `cb-001`).
- **Phạm vi không làm:** Dashboard, documents ngoài scope, playground compare/session/export, analytics, settings, public chat, upload tenant fix, JWT, đổi schema DB, sửa Frontend/mock.

## 2. Files/rules/reports đã đọc

| File | Mục đích đọc | Kết luận chính |
|------|----------------|----------------|
| `.cursor/rules/00-core-working-rule.mdc` | Luật nền | Minimal diff, không mở scope |
| `.cursor/rules/10-backend-rag-rule.mdc` | Backend | Không đụng core RAG |
| `.cursor/rules/40-db-vector-rule.mdc` | DB | Không đổi schema khi không cần |
| `.cursor/rules/90-report-verification-rule.mdc` | Report | User prompt chọn output `reports/` cho report 02 |
| `reports/CURSOR_REPORT_01B_BACKEND_CANONICAL_FE_ENDPOINTS_NOT_ALIASES.md` | Baseline | `POST /api/chatbots` đã có; CRUD còn thiếu |
| `Frontend/src/api/chatbotsApi.js` | Contract | Endpoints + query + body chuẩn |
| `Frontend/src/mocks/chatbotsMock.js` | Shape | Field chatbot + embed |
| `Backend/.../WidgetConfig.java` | Entity | Có `deletedAt`, `uiConfig`, `isActive` |
| `Backend/.../WidgetService.java` | Service | Mở rộng CRUD/embed |
| `Backend/.../ChatbotController.java` | API | Full CRUD + embed |

## 3. Current Chatbot/Widget model analysis

- **Entity đang dùng:** `WidgetConfig` (`widget_configs`), đại diện “chatbot” phía admin.
- **WidgetConfig fields:** `id` (UUID), `name`, `allowedOrigin` (`List<String>` JSON), `apiKey` (UUID), `uiConfig` (`Map<String,Object>` JSON), `isActive`, `createdAt`, `updatedAt`, `deletedAt`.
- **Soft delete:** Có cột `deleted_at`; entity có `@SQLRestriction("deleted_at IS NULL")`.
- **`uiConfig` đang lưu / được dùng cho:** `description`, `domain`, `systemPrompt`, `modelConfig`, `status` (mirror), embed: `widgetColor`, `welcomeMessage`, `position`, `launcherIcon`.
- **`allowedOrigin`:** List domain CORS/widget; create chatbot set default `[http://localhost:5173]`; PUT embed-config có thể ghi đè từ `allowedOrigins` request.
- **Field FE cần mà BE không có cột riêng:** `description`, `domain`, `systemPrompt`, `modelConfig`, embed UI → lưu trong `uiConfig`; `documentCount`/`messageCount` tính từ DB.

## 4. FE contract mapping after implementation

| FE function | Endpoint | Request/header expected | Response expected | Backend implementation status | Notes |
|-------------|----------|-------------------------|-------------------|-------------------------------|--------|
| `getChatbots` | `GET /api/chatbots` | Query: `search`, `status`, `domain`, `page`, `size` | `{ items, page, size, total, totalPages }` | **Implemented** | Pagination 0-based; default page=0, size=10 |
| `createChatbot` | `POST /api/chatbots` | Body `{ name, description, domain }` | Chatbot object | **Implemented** | Thêm `apiKey` (public key) chỉ lúc create; default `allowedOrigin` build |
| `getChatbot` | `GET /api/chatbots/{id}` | Path UUID | Chatbot object | **Implemented** | 400 invalid UUID; 404 not found |
| `updateChatbot` | `PUT /api/chatbots/{id}` | Body partial config | Updated chatbot | **Implemented** | Merge `uiConfig`; từ chối `DELETED` |
| `deleteChatbot` | `DELETE /api/chatbots/{id}` | — | `{ success: true }` | **Implemented** | Soft delete |
| `getEmbedConfig` | `GET .../embed-config` | — | Embed shape | **Implemented** | Defaults theo spec |
| `updateEmbedConfig` | `PUT .../embed-config` | Body embed | Updated embed | **Implemented** | Validate position + hex color |

## 5. Files changed

| File | Change | Reason | Risk |
|------|--------|--------|------|
| `WidgetConfigRepository.java` | Native query `searchChatbots` + `Pageable` | List + filter + pagination | Medium — phụ thuộc MySQL JSON |
| `DocumentRepository.java` | `countByWidgetConfig_Id` | `documentCount` | Low |
| `ChatMessageRepository.java` | `countByWidgetConfigId` JPQL | `messageCount` | Low |
| `WidgetService.java` | CRUD + embed + mapping | Business logic tập trung | Medium |
| `ChatbotController.java` | GET/POST/PUT/DELETE + embed routes | Canonical API | Low |
| `dto/ChatbotResponse.java` | **New** (thay `ChatbotCreateResponse`) | Một DTO thống nhất + `apiKey` nullable | Low |
| `dto/ChatbotPageResponse.java` | New | Paginated list | Low |
| `dto/ChatbotUpdateRequest.java` | New | PUT chatbot | Low |
| `dto/EmbedConfigResponse.java` | New | GET embed | Low |
| `dto/EmbedConfigUpdateRequest.java` | New | PUT embed | Low |
| `dto/SimpleSuccessResponse.java` | New | DELETE response | Low |
| `dto/ChatbotCreateResponse.java` | **Deleted** | Trùng `ChatbotResponse` | Low |

## 6. Details per endpoint

### GET `/api/chatbots`

- **Query:** `search`, `status`, `domain`, `page` (default 0), `size` (default 10).
- **DTO response:** `ChatbotPageResponse` (`items`: `ChatbotResponse`, `page`, `size`, `total`, `totalPages`).
- **Service:** `WidgetService.listChatbots` → `WidgetConfigRepository.searchChatbots`.
- **Mapping:** `toChatbotResponse` — không trả `apiKey`.
- **Errors:** Không throw riêng; filter rỗng = không lọc theo field đó.

### POST `/api/chatbots`

- **Request:** `ChatbotCreateRequest` (`name`, `description`, `domain`).
- **Response:** `ChatbotResponse` **có** `apiKey` (string của UUID widget) để test public chat.
- **Service:** `createChatbot` → `createWidgetConfig` với `allowedOrigin = [http://localhost:5173]`, `uiConfig.description` / `domain`.
- **Errors:** 400 `{ "message": ... }` từ validation widget.

### GET `/api/chatbots/{id}`

- **Response:** `ChatbotResponse`.
- **Errors:** 400 invalid UUID; 404 `{ "message": ... }`.

### PUT `/api/chatbots/{id}`

- **Request:** `ChatbotUpdateRequest` — partial; field null/absent giữ cũ (merge).
- **Response:** `ChatbotResponse`.
- **`status`:** chỉ `ACTIVE` / `INACTIVE`; `DELETED` → 400.
- **Errors:** 400 validation; 404.

### DELETE `/api/chatbots/{id}`

- **Response:** `SimpleSuccessResponse` `{ "success": true }`.
- **Service:** `deletedAt = now()`, `isActive = false`, `uiConfig.status = DELETED`.
- **Errors:** 404.

### GET `/api/chatbots/{id}/embed-config`

- **Response:** `EmbedConfigResponse` — defaults: `widgetColor` `#2563eb`, `welcomeMessage` tiếng Việt theo spec, `position` `bottom-right`, `allowedOrigins` từ entity hoặc `[]`, `launcherIcon` `chat`.

### PUT `/api/chatbots/{id}/embed-config`

- **Request:** `EmbedConfigUpdateRequest`.
- **Validation:** `position` ∈ {`bottom-right`,`bottom-left`}; `widgetColor` hex `#RGB` / `#RRGGBB` nếu có; `allowedOrigins` array → ghi `WidgetConfig.allowedOrigin`.
- **Response:** `EmbedConfigResponse`.

## 7. Validation results

| Command | Result | Notes |
|---------|--------|-------|
| `cd Backend && ./mvnw -DskipTests compile` | **PASS** | BUILD SUCCESS |
| `cd Backend && ./mvnw test` | **FAIL** | MySQL `Communications link failure` / Connection refused — không có DB runtime |

## 8. Manual/API test plan and results

| Endpoint | Curl / Steps | Result | Notes |
|----------|----------------|--------|-------|
| POST `/api/chatbots` | Prompt § TEST 1 | **NOT RUN** | Backend không chạy trong session |
| GET `/api/chatbots` | § TEST 2 | **NOT RUN** | |
| Search/filter | § TEST 3 | **NOT RUN** | |
| GET detail | § TEST 4 | **NOT RUN** | |
| PUT chatbot | § TEST 5 | **NOT RUN** | |
| GET embed | § TEST 6 | **NOT RUN** | |
| PUT embed | § TEST 7 | **NOT RUN** | |
| DELETE | § TEST 8 | **NOT RUN** | |

## 9. Known limitations / gaps

- **`allowedOrigin` khi create:** Luôn `[http://localhost:5173]` nếu FE không gửi — cần chỉnh qua PUT embed-config để khớp production (ghi nhận trong spec).
- **`modelConfig` / `systemPrompt`:** Không có cột DB riêng; toàn bộ trong `uiConfig`. Default `modelConfig` khi thiếu: GPT-4o, temperature 0.7, topK 5, maxTokens 1024 (theo prompt).
- **Native query:** Gắn MySQL 8; đổi DB dialect có thể cần sửa SQL.
- **Performance:** List N chatbot có thể gọi `count` document/message mỗi row (chấp nhận trong phạm vi hiện tại).

## 10. Recommended next prompt

- **Upload canonical + tenant:** Hoàn thiện `POST /api/documents/upload` khi FE gửi `chatbotId`/`widgetId` (report 01B đã block).
- **Auth admin:** Khi có JWT/session, khóa `/api/chatbots/**` thay vì `permitAll`.

---

## Appendix — Diff summary (high level)

### `ChatbotController.java`

```diff
- Chỉ POST /api/chatbots
+ GET list (query search/status/domain/page/size)
+ GET /{id}, PUT /{id}, DELETE /{id}
+ GET/PUT /{id}/embed-config
+ Xử lý UUID invalid → 400 JSON message
+ Optional empty → 404 JSON message
```

### `WidgetService.java`

```diff
+ createChatbot, listChatbots, getChatbot, updateChatbot, softDeleteChatbot
+ getEmbedConfig, updateEmbedConfig
+ DEFAULT_MODEL_CONFIG, merge uiConfig, validation embed (hex, position)
```

### `WidgetConfigRepository.java`

```diff
+ Page<WidgetConfig> searchChatbots(... nativeQuery ...)
```
