# Cursor Report 01B - BACKEND_CANONICAL_FE_ENDPOINTS_NOT_ALIASES

## 1. Mức độ hiểu task
- Task là gì? Chuyển các endpoint đã làm ở Prompt 01 từ mô hình alias sang canonical theo FE contract (path/header/body), không đổi core RAG flow.
- Hiểu task: 99%
- Phần chắc chắn:
  - `POST /api/public/chat` là canonical public chat.
  - `POST /api/chatbots` là canonical create chatbot.
  - `POST /api/playground/chat` có thể canonical hóa an toàn nếu `chatbotId` map trực tiếp UUID `WidgetConfig.id`.
  - `POST /api/documents/upload` vẫn blocked vì FE upload không gửi tenant context.
- Phần còn giả định:
  - `chatbotId` thực tế FE runtime sẽ là UUID string (không phải mock id kiểu `cb-001`) khi dùng real API.
  - `allowedOrigin` default vẫn cần để reuse `WidgetService` hiện tại.
- Phạm vi không làm:
  - Không làm CRUD chatbots còn thiếu.
  - Không làm compare/session/export playground.
  - Không làm documents status/chunks/assign/retry/delete.
  - Không làm dashboard/analytics/settings.
  - Không đổi core service ingestion/retrieval/LLM.

## 2. Files/rules/reports đã đọc
| File | Mục đích đọc | Kết luận chính |
|---|---|---|
| `.cursor/rules/00-core-working-rule.mdc` | Rule nền | Giữ minimal diff, không mở rộng scope |
| `.cursor/rules/10-backend-rag-rule.mdc` | Rule backend | Ưu tiên wrap/reuse flow hiện có |
| `.cursor/rules/20-frontend-widget-rule.mdc` | Contract FE | Header/path FE là source of truth |
| `.cursor/rules/30-deploy-env-ops-rule.mdc` | Validation deploy | Không cần đổi Docker/env ở prompt này |
| `.cursor/rules/40-db-vector-rule.mdc` | DB/vector safety | Không đổi schema/vector |
| `.cursor/rules/90-report-verification-rule.mdc` | Reporting format | Ghi trung thực PASS/FAIL/BLOCKED |
| `reports/CURSOR_REPORT_00_BACKEND_FLOW_AND_FE_CONTRACT_AUDIT.md` | Baseline Different/Partial | Public/Playground/Chatbots/Upload là nhóm cần canonical hóa |
| `reports/CURSOR_REPORT_01_BACKEND_FE_COMPATIBLE_ENDPOINT_ALIASES_ONLY.md` | Trạng thái prompt trước | Có alias + class tên `Alias`, cần cleanup canonical |
| `Frontend/src/api/publicChatApi.js` | Public contract | `POST /api/public/chat`, header `x-api-key`, body `{message,sessionId}` |
| `Frontend/src/api/chatbotsApi.js` | Chatbot create contract | `POST /api/chatbots` body `{name,description,domain}`, response object chatbot |
| `Frontend/src/api/playgroundApi.js` | Playground SSE contract | `POST /api/playground/chat`, body có `chatbotId`, `overrideParams`; parser SSE token/done |
| `Frontend/src/api/documentsApi.js` | Upload contract | `POST /api/documents/upload`, multipart field `files`, không có tenant id |
| `Frontend/src/api/axiosInstance.js` | Error contract | FE đọc `message` hoặc `error` từ response lỗi |
| `Frontend/src/mocks/chatbotsMock.js` | Response shape chatbot | Shape chuẩn FE cho create/list chatbot |
| `Frontend/src/mocks/documentsMock.js` | Upload response shape | FE kỳ vọng upload trả danh sách docs |
| `Frontend/src/mocks/playgroundMock.js` | Playground payload shape | `chatbotId` mock là string id, không phải UUID |
| `Backend/src/main/java/.../api/ChatController.java` | Core chat hiện có | `/api/chat` và `/api/chat/stream` vẫn là legacy flow |
| `Backend/src/main/java/.../api/PublicChatController.java` | Canonical public endpoint | Reuse `ChatService.chat` |
| `Backend/src/main/java/.../api/WidgetController.java` | Legacy widget create | `/api/widgets` cũ |
| `Backend/src/main/java/.../api/ChatbotAliasController.java` | Artifact prompt 01 | Được thay bằng canonical controller mới |
| `Backend/src/main/java/.../api/DocumentController.java` | Ingest entrypoint | Cần `widgetId` path param |
| `Backend/src/main/java/.../config/WidgetAuthFilter.java` | API key filter | Canonical `x-api-key` cho public chat + fallback legacy |
| `Backend/src/main/java/.../config/SecurityConfig.java` | Security permit list | Không yêu cầu JWT cho canonical endpoints trong scope |
| `Backend/src/main/resources/application.yml` | Upload + app config | Không có default widget tenant cho upload |
| `Backend/src/main/resources/application-dev.yml` | DB/vector env | Test fail vì MySQL env runtime |

## 3. Alias-to-canonical migration summary
| Current/old BE endpoint | FE canonical endpoint | Action | Notes |
|---|---|---|---|
| `POST /api/chat` + key `X-Widget-Key` | `POST /api/public/chat` + key `x-api-key` | Canonicalized (public endpoint chính) | `PublicChatController` giữ flow `ChatService.chat`; response thêm `sessionId` |
| `POST /api/widgets` | `POST /api/chatbots` | Canonicalized (create endpoint chính cho FE) | Đổi từ class/DTO alias sang controller/DTO canonical |
| `POST /api/chat/stream` | `POST /api/playground/chat` | Canonicalized mới | Dùng `chatbotId` (UUID) làm `widgetId`, reuse `ChatService.chatStream` |
| `POST /api/documents/upload/{widgetId}` | `POST /api/documents/upload` | Blocked | FE không gửi tenant id (`widgetId`/`chatbotId`) nên không thể canonical hóa an toàn |

## 4. FE contract mapping after change
| FE function | FE endpoint | Request/header expected | Response expected | Backend implementation status | Notes |
|---|---|---|---|---|---|
| `publicChatApi.publicChat` | `POST /api/public/chat` | Header `x-api-key`; body `{message,sessionId}` | `{answer,sessionId,sources}` | **Implemented** | `PublicChatResponse` trả đúng 3 field |
| `chatbotsApi.createChatbot` | `POST /api/chatbots` | Body `{name,description,domain}` | chatbot object create | **Implemented** | `ChatbotCreateResponse` khớp các field FE create đang dùng |
| `playgroundApi.chat` | `POST /api/playground/chat` (SSE) | Body `{chatbotId,message,sessionId,overrideParams}` | SSE `event: token`, `event: done` | **Implemented (with assumption)** | `chatbotId` phải là UUID; `overrideParams` nhận nhưng chưa áp dụng model config |
| `documentsApi.uploadDocuments` | `POST /api/documents/upload` multipart `files` | FormData field `files` | Uploaded list | **Blocked** | Thiếu tenant context bắt buộc cho `DocumentService.uploadAndProcess` |

## 5. Files changed
| File | Change | Reason | Risk |
|---|---|---|---|
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/api/ChatbotController.java` | Thêm controller canonical `/api/chatbots` | Thay alias bằng endpoint canonical FE | Medium (default allowed origin) |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/dto/ChatbotCreateRequest.java` | DTO canonical request | Bỏ DTO tên Alias | Low |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/dto/ChatbotCreateResponse.java` | DTO canonical response | Bỏ DTO tên Alias, align FE shape | Low |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/api/PublicChatController.java` | Sửa response trả thêm `sessionId` qua `PublicChatResponse` | Align FE publicChat contract | Low |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/dto/PublicChatResponse.java` | Thêm DTO response public chat | Đảm bảo FE lấy được `sessionId` | Low |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/api/PlaygroundController.java` | Thêm canonical `/api/playground/chat` SSE | Reuse stream flow với FE path/body | Medium (assumption chatbotId UUID) |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/dto/PlaygroundChatRequest.java` | Thêm DTO request playground stream | Nhận đúng body FE | Low |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/config/WidgetAuthFilter.java` | Support canonical `x-api-key` cho public chat, fallback legacy key | Security/header alignment | Low |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/config/SecurityConfig.java` | Add requestMatchers canonical FE paths | Rõ ràng hóa security config trong scope | Low |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/api/ChatbotAliasController.java` | **Deleted** | Cleanup artifact alias prompt 01 | Low |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/dto/ChatbotCreateAliasRequest.java` | **Deleted** | Cleanup artifact alias prompt 01 | Low |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/dto/ChatbotCreateAliasResponse.java` | **Deleted** | Cleanup artifact alias prompt 01 | Low |

## 6. Details per endpoint changed

### 6.1 Public chat
- Old path/header/body:
  - Legacy: `POST /api/chat`, header `X-Widget-Key`, body `ChatRequest`.
  - Prompt 01 alias: `POST /api/public/chat` nhưng response vẫn `ChatResponse` (không có `sessionId` field riêng).
- New canonical FE path/header/body:
  - `POST /api/public/chat`
  - Header canonical: `x-api-key` (fallback support `X-Widget-Key` chỉ backward compatibility)
  - Body canonical: `{ message, sessionId }`
- Controller method: `PublicChatController.publicChat`
- DTO request/response:
  - Request: `ChatRequest`
  - Response: `PublicChatResponse { answer, sessionId, sources }`
- Service flow reused: `ChatService.chat` (không đổi retrieval/LLM/session persistence)
- Endpoint cũ còn expose không?
  - `POST /api/chat` vẫn còn để backward compatibility cho flow widget/prototype cũ; không coi là FE canonical.

### 6.2 Chatbot create
- Old path/header/body:
  - Legacy: `POST /api/widgets`, body `WidgetCreateRequest`.
  - Prompt 01: `POST /api/chatbots` qua `ChatbotAliasController`.
- New canonical FE path/header/body:
  - `POST /api/chatbots`
  - Body canonical FE: `{ name, description, domain }`
- Controller method: `ChatbotController.createChatbot`
- DTO request/response:
  - `ChatbotCreateRequest`
  - `ChatbotCreateResponse`
- Service flow reused: `WidgetService.createWidgetConfig`
- Endpoint cũ còn expose không?
  - `POST /api/widgets` vẫn còn cho backward compatibility và nội bộ legacy upload bootstrap.
- Ghi chú canonical:
  - `allowedOrigin` chưa có trong FE create payload nên dùng constant default `http://localhost:5173` để không phá validation hiện hữu của service.

### 6.3 Playground chat
- Old path/header/body:
  - Legacy: `POST /api/chat/stream`, body `ChatRequest`, widget context qua filter key header.
- New canonical FE path/header/body:
  - `POST /api/playground/chat` (SSE)
  - Body FE: `{ chatbotId, message, sessionId, overrideParams }`
- Controller method: `PlaygroundController.chat`
- DTO request/response:
  - Request: `PlaygroundChatRequest`
  - Response: SSE stream (`SseEmitter`) theo events token/done từ `ChatService.chatStream`
- Service flow reused: `ChatService.chatStream`
- Endpoint cũ còn expose không?
  - `POST /api/chat/stream` vẫn expose cho legacy key-based widget flow.
- Limitation:
  - `chatbotId` phải parse được UUID để map `WidgetConfig.id`.
  - `overrideParams` chưa apply model runtime (ngoài scope prompt này).

### 6.4 Documents upload
- Old path/header/body:
  - `POST /api/documents/upload/{widgetId}`, multipart `file`.
- FE canonical expected:
  - `POST /api/documents/upload`, multipart `files`.
- Kết quả:
  - Không đổi endpoint upload trong prompt này vì thiếu tenant context từ FE contract hiện tại.
  - Endpoint cũ giữ nguyên làm core ingest entrypoint.

## 7. Flow test results
| Flow | Steps | Result: PASS / FAIL / BLOCKED / NOT RUN | Notes |
|---|---|---|---|
| Canonical chatbot create | `curl.exe POST /api/chatbots` | **BLOCKED** | Không có backend runtime trên `localhost:8080` trong phiên này |
| Canonical public chat | `curl.exe POST /api/public/chat` | **BLOCKED** | Không có runtime backend để test live flow |
| Canonical playground chat SSE | `curl.exe -N POST /api/playground/chat` | **BLOCKED** | Không có runtime backend để mở SSE |
| Documents upload canonical | N/A | **BLOCKED** | Chưa implement endpoint canonical do thiếu tenant id |
| Legacy widgets endpoint check | `curl.exe POST /api/widgets` | **BLOCKED** | Không có runtime backend để xác nhận HTTP behavior |

## 8. Validation results
| Command | Result | Notes |
|---|---|---|
| `cd Backend && ./mvnw -DskipTests compile` | **PASS** | BUILD SUCCESS, compile 52 source files |
| `cd Backend && ./mvnw test` | **FAIL** | Lỗi MySQL connection (`Communications link failure`, `Connection refused`) khi load Spring context; không phải lỗi compile endpoint |

## 9. Blocked / not changed
- `POST /api/documents/upload` canonical: **chưa thể canonical hóa** vì FE hiện không truyền `widgetId/chatbotId`; core ingest bắt buộc tenant context.
- Không remove legacy endpoints:
  - `/api/chat`, `/api/chat/stream`, `/api/widgets`, `/api/documents/upload/{widgetId}`
  - Lý do: backward compatibility với luồng widget/prototype cũ trong repo; canonical FE endpoint đã được bổ sung/ưu tiên.

## 10. Known limitations
- `POST /api/playground/chat` giả định `chatbotId` là UUID `WidgetConfig.id`; nếu FE gửi id kiểu khác (vd `cb-001`) sẽ trả 400.
- `overrideParams` trong request playground chưa áp dụng vào config model stream.
- `POST /api/chatbots` vẫn phải set `allowedOrigin` default để đi qua validation service hiện tại.
- Runtime smoke API chưa chạy được vì backend server/dependencies chưa được bring up trong phiên kiểm tra.

## 11. Recommended next prompt
1. `01C_BACKEND_PLAYGROUND_CHATBOT_ID_NORMALIZATION`  
   - Chốt contract `chatbotId` (UUID hay external id) và mapping bền vững.
2. `01D_BACKEND_DOCUMENT_UPLOAD_CANONICAL_TENANT_CONTEXT`  
   - Chốt FE truyền `chatbotId/widgetId` trong form/query cho upload canonical.
3. `02_BACKEND_CHATBOTS_READ_UPDATE_DELETE_CANONICAL`  
   - Hoàn thiện phần còn thiếu của `/api/chatbots` để đồng bộ với FE modules hiện hữu.
