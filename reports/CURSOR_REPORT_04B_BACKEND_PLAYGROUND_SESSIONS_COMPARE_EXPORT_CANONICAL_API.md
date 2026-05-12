# Cursor Report 04B - BACKEND_PLAYGROUND_SESSIONS_COMPARE_EXPORT_CANONICAL_API

## 1. Mức độ hiểu task
- Task là gì? Triển khai 4 Playground APIs còn thiếu theo FE contract: sessions list, delete session, compare, export.
- Hiểu task: 98%
- Phần chắc chắn:
  - FE gọi `GET /api/playground/sessions?chatbotId=...` và mong **array trực tiếp**.
  - FE gọi `DELETE /api/playground/sessions/:id` và mong `{ success: true }`.
  - FE ComparePane dùng `configA/configB`, mỗi config cần `answer`, `sources`, `latency`.
  - FE export gọi blob mode, backend JSON vẫn chấp nhận được.
- Phần còn giả định:
  - Compare override config chỉ cần accept + echo nếu chưa có engine apply runtime đầy đủ.
  - Session id FE dùng `sessionKey` (UUID string) thay vì PK DB.
- Phạm vi không làm:
  - Không sửa FE/mock.
  - Không đổi schema DB.
  - Không refactor lớn `ChatService`/RAG core.
  - Không làm dashboard/analytics/settings.

## 2. Files/rules/reports đã đọc
| File | Mục đích đọc | Kết luận chính |
|---|---|---|
| `.cursor/rules/00-core-working-rule.mdc` | Scope/minimal diff | Chỉ sửa endpoint trong phạm vi 04B |
| `.cursor/rules/10-backend-rag-rule.mdc` | Quy tắc backend | Reuse flow hiện có, không đại phẫu |
| `.cursor/rules/20-frontend-widget-rule.mdc` | FE contract check | SSE/parser và shape FE là source of truth |
| `.cursor/rules/30-deploy-env-ops-rule.mdc` | Runtime verify | Cần kiểm tra instance backend đúng binary mới |
| `.cursor/rules/40-db-vector-rule.mdc` | DB safety | Không đổi schema/entity quan hệ |
| `.cursor/rules/90-report-verification-rule.mdc` | Reporting compliance | Report trung thực + đủ verify |
| `reports/CURSOR_REPORT_01B_BACKEND_CANONICAL_FE_ENDPOINTS_NOT_ALIASES.md` | Baseline endpoint canonical | Playground chat đã canonical từ 01B |
| `reports/CURSOR_REPORT_02B_RERUN_BACKEND_CHATBOTS_API_RUNTIME_SMOKE_TEST.md` | Runtime baseline chatbot | Có thể tạo/xóa chatbot runtime |
| `reports/CURSOR_REPORT_03C_BACKEND_DOCUMENTS_RUNTIME_SMOKE_TEST.md` | Runtime baseline document | Upload/index document runtime sẵn |
| `reports/CURSOR_REPORT_04A_BACKEND_PUBLIC_AND_PLAYGROUND_CHAT_RUNTIME_SMOKE_TEST.md` | Baseline playground chat | `/api/playground/chat` đã pass |
| `Frontend/src/api/playgroundApi.js` | Contract chính | `getSessions` trả array; compare expects `configA/configB`; export fetch blob |
| `Frontend/src/mocks/playgroundMock.js` | Shape tham chiếu | Session summary fields + message shape |
| `Frontend/src/pages/playground/PlaygroundPage.jsx` | Cách FE consume API | `setSessions(data || [])`, compare đọc `result?.configA/B`, restore từ export messages |
| `Frontend/src/pages/playground/components/ComparePane.jsx` | Compare field usage | `answer/sources/latency` được dùng trực tiếp |
| `Frontend/src/pages/playground/components/SessionList.jsx` | Session field usage | `id,lastMessage,messageCount,updatedAt` |
| `Frontend/src/pages/playground/components/ExportSessionButton.jsx` | Export behavior | Blob/object đều xử lý được |
| `Backend/src/main/java/.../api/PlaygroundController.java` | Điểm vào API | Trước chỉ có `/chat` |
| `Backend/src/main/java/.../service/ChatService.java` | Reuse chat core | Có persistence + retrieval + source mapping cho chat |
| `Backend/src/main/java/.../domain/chat/*` | Entity/repo model | `sessionKey` unique theo widget; chat message lưu sources JSON |
| `Backend/src/main/resources/application*.yml`, `docker-compose.yml` | Runtime env | MySQL/Qdrant/dev profile có sẵn |

## 3. Current Playground/Chat model analysis
- ChatSession entity:
  - Có `id` (PK UUID), `sessionKey` (UUID dùng bởi client), `widgetConfig`, `title`, `createdAt`, `updatedAt`, `deletedAt`.
  - Unique `(widget_config_id, session_key)`.
- ChatMessage entity:
  - Có `session`, `role` (`USER|ASSISTANT`), `content`, `sources` (JSON), `createdAt`, `deletedAt`.
- Repository methods hiện có:
  - `ChatSessionRepository`: lookup theo `sessionKey` và `sessionKey+widget`.
  - `ChatMessageRepository`: lấy top10 hoặc full messages theo `sessionId`.
- ChatService methods hiện có:
  - `chat` (sync) và `chatStream` (SSE), có persistence session/messages.
- Session/message persistence behavior:
  - `chatStream` tạo/find session theo `sessionKey` và lưu cả user + assistant messages.
- Existing PlaygroundController behavior:
  - Chỉ có `POST /api/playground/chat`, validate cơ bản, map `chatbotId -> widgetId`.

## 4. FE contract mapping after implementation
| FE function | Endpoint | Request/header expected | Response expected | Backend implementation status | Notes |
|---|---|---|---|---|---|
| `playgroundApi.getSessions(chatbotId)` | `GET /api/playground/sessions?chatbotId=` | Query `chatbotId` required UUID | Array sessions | Implemented | 400 nếu missing/invalid chatbotId |
| `playgroundApi.deleteSession(id)` | `DELETE /api/playground/sessions/{id}` | Path `id` (session UUID key) | `{success:true}` | Implemented | Soft delete session + messages |
| `playgroundApi.compare({...})` | `POST /api/playground/compare` | Body `{chatbotId,message,configA,configB}` | `{configA:{...},configB:{...}}` | Implemented | Reuse retrieval/LLM, không persistence compare |
| `playgroundApi.exportSession(sessionId)` | `GET /api/playground/export/{sessionId}` | Path session id UUID | JSON export session/messages | Implemented | FE real mode vẫn nhận blob (do `responseType: blob`) |

## 5. Files changed
| File | Change | Reason | Risk |
|---|---|---|---|
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/api/PlaygroundController.java` | Thêm 4 endpoints canonical + validation/error mapping | Align FE API contract | Low |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/PlaygroundService.java` | Thêm service list/delete/compare/export | Giữ controller mỏng, reuse core logic | Medium |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/domain/chat/ChatSessionRepository.java` | Thêm query list theo chatbot | Phục vụ sessions API | Low |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/domain/chat/ChatMessageRepository.java` | Thêm helper count/last message | Phục vụ mapping session summary | Low |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/dto/PlaygroundMessageResponse.java` | DTO message API | Tránh trả entity trực tiếp | Low |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/dto/PlaygroundSessionResponse.java` | DTO sessions API | Khớp FE list shape | Low |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/dto/PlaygroundCompareRequest.java` | DTO compare request | Parse body rõ ràng | Low |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/dto/PlaygroundCompareResult.java` | DTO compare result | Khớp ComparePane field usage | Low |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/dto/PlaygroundCompareResponse.java` | DTO compare response root | `configA/configB` contract | Low |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/dto/PlaygroundExportResponse.java` | DTO export response | Export JSON session/messages | Low |

## 6. Details per endpoint

### `GET /api/playground/sessions`
- Method/path: `GET /api/playground/sessions?chatbotId=...`
- Request params/body:
  - `chatbotId` required, UUID.
- Response DTO:
  - `List<PlaygroundSessionResponse>`.
- Service/repository used:
  - `PlaygroundService.listSessions` + `ChatSessionRepository.findByWidgetConfigIdOrderByUpdatedAtDesc` + `ChatMessageRepository.findBySessionIdOrderByCreatedAtAsc`.
- Mapping entity ↔ FE response shape:
  - `id` = `session.sessionKey` (string UUID).
  - `chatbotId` = `widgetConfig.id`.
  - `messageCount`, `lastMessage`, timestamps, `messages[]`.
- Error handling:
  - Missing chatbotId → 400 `{message:"chatbotId is required"}`
  - Invalid UUID → 400 `{message:"chatbotId must be a UUID"}`

### `DELETE /api/playground/sessions/{id}`
- Method/path: `DELETE /api/playground/sessions/{id}`
- Request params/body:
  - Path `id` required UUID (`sessionKey`).
- Response DTO:
  - `SimpleSuccessResponse {success:true}`.
- Service/repository used:
  - `PlaygroundService.deleteSession` + `findBySessionKey`.
- Mapping entity ↔ FE response shape:
  - Không trả entity, chỉ success flag.
- Error handling:
  - Invalid UUID → 400 `{message:"Invalid session id"}`
  - Session không tồn tại → 404 `{message:"Session not found"}`

### `POST /api/playground/compare`
- Method/path: `POST /api/playground/compare`
- Request params/body:
  - `{ chatbotId, message, configA, configB }`.
- Response DTO:
  - `PlaygroundCompareResponse { configA, configB }`.
- Service/repository used:
  - `PlaygroundService.compare` (reuse `QueryAnalyzerService`, `RagRetrievalService`, `PromptBuilderService`, `LlmFallbackService`).
- Mapping entity ↔ FE response shape:
  - Mỗi nhánh có `answer`, `sources`, `latency`, `config`.
- Error handling:
  - missing/blank message → 400
  - missing/invalid chatbotId → 400

### `GET /api/playground/export/{sessionId}`
- Method/path: `GET /api/playground/export/{sessionId}`
- Request params/body:
  - Path `sessionId` UUID (`sessionKey`).
- Response DTO:
  - `PlaygroundExportResponse { sessionId, chatbotId, createdAt, updatedAt, messages[] }`.
- Service/repository used:
  - `PlaygroundService.exportSession` + `findBySessionKey`.
- Mapping entity ↔ FE response shape:
  - `messages[]` gồm `id`, `role` (`user/assistant`), `content`, `createdAt`, `sources`.
- Error handling:
  - Invalid UUID → 400 `{message:"Invalid session id"}`
  - Not found → 404 `{message:"Session not found"}`

## 7. Compare behavior decision
- Có dùng overrideParams/configA/configB thật không?
  - **Một phần**: backend nhận và echo `configA/configB` trong response.
- Nếu chưa, vì sao?
  - Chưa áp dụng runtime model override (temperature/topK/maxTokens/systemPrompt) vào `ChatService` để tránh refactor lớn ngoài scope.
- Có reuse ChatService.chat không?
  - **Không** trực tiếp để tránh persistence compare flow.
  - Reuse retrieval + prompt + llm fallback service ở level thấp.
- Có tạo session/message không?
  - **Không**. Compare chạy stateless, không ghi DB.
- Có latency/sources không?
  - Có `latency` (ms tổng, đo wall-clock) và `sources` từ retrieval mapping.

## 8. Export behavior
- Export JSON hay CSV?
  - JSON.
- Content-Type:
  - `application/json` (Spring default JSON response).
- Filename suggestion nếu có:
  - FE tự đặt `playground-session-<sessionId>.json` khi tải blob.
- Response body shape:
  - `{ sessionId, chatbotId, createdAt, updatedAt, messages:[...] }`.

## 9. Validation results
| Command | Result | Notes |
|---|---|---|
| `cd Backend && ./mvnw -DskipTests compile` | PASS | Build success sau thêm endpoints/DTO/service |
| `cd Backend && ./mvnw test` | PASS | 14 tests pass |
| `cd Frontend && npm run lint` | NOT RUN | Ngoài scope prompt backend |
| `cd Frontend && npm run build` | NOT RUN | Ngoài scope prompt backend |
| `cd Frontend && npm run build:widget` | NOT RUN | Ngoài scope prompt backend |
| `docker compose config` | NOT RUN | Không đổi compose |

## 10. Manual/API test plan and results
| Endpoint | Curl / Steps | Result | Notes |
|---|---|---|---|
| Setup chatbot | `POST /api/chatbots` | PASS | Tạo bot test runtime thành công |
| Setup document | `POST /api/documents/upload` với `chatbotId` + `tmp-playground-sessions.txt` | PASS | Document `INDEXED`, `chunkCount=1` |
| Setup session | `POST /api/playground/chat` | PASS | SSE có `token` + `done`, session persisted |
| `GET /api/playground/sessions` | `?chatbotId=<uuid>` | PASS | Trả array, count=1, có messageCount=2 |
| `GET /api/playground/export/{sessionId}` | sessionId từ list | PASS | Trả JSON có `messages=2` |
| `POST /api/playground/compare` | body configA/configB | PASS | Cả 2 nhánh có answer, sources, latency |
| `DELETE /api/playground/sessions/{id}` | sessionId từ list | PASS | `{success:true}`, list sau delete không còn session |
| Invalid chatbotId sessions | `GET /api/playground/sessions?chatbotId=not-a-uuid` | PASS | 400 + message rõ |
| Invalid sessionId export | `GET /api/playground/export/not-a-uuid` | PASS | 400 + message rõ |
| Missing chatbotId sessions | `GET /api/playground/sessions` | PASS | 400 + `chatbotId is required` |

## 11. Known limitations / gaps
- `compare` hiện chưa áp dụng runtime override config vào model/retrieval internals; chỉ echo config và chạy cùng core config hiện tại.
- `sessions` trả full `messages` cho từng session; với dữ liệu rất lớn có thể cần pagination/lazy-load ở prompt sau.
- FE real `exportSession` đang dùng `responseType: blob`, nên restore messages hiện tại vẫn dựa vào strategy FE; backend đã trả JSON đúng shape.

## 12. Recommended next prompt
- `04C_BACKEND_PLAYGROUND_COMPARE_RUNTIME_OVERRIDE_MINIMAL_APPLICATION`  
  (nếu cần apply một phần override `temperature/topK/maxTokens/systemPrompt` mà vẫn tránh refactor lớn).

