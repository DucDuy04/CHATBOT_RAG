# Cursor Report 01 - BACKEND_FE_COMPATIBLE_ENDPOINT_ALIASES_ONLY

## 1. Mức độ hiểu task
- Task là gì? Thêm alias endpoint Backend để khớp FE ở các nhóm đã có flow tương đương, không triển khai business/API mới ngoài alias scope.
- Hiểu task: 98%
- Phần chắc chắn:
  - Public chat alias có thể reuse `ChatService`.
  - Chatbot create alias có thể reuse `WidgetService`.
  - Playground chat và document upload có contract mismatch chưa đủ dữ kiện để alias thuần tên/path.
- Phần còn giả định:
  - Alias `/api/chatbots` dùng default `allowedOrigin` vì payload FE không có field này.
  - FE có thể chấp nhận response create chatbot theo shape adapter tối thiểu.
- Phạm vi không làm:
  - Không làm dashboard/analytics/settings/full chatbot CRUD/full documents/playground compare-session-export.
  - Không đổi core RAG ingest/chunk/retrieval/LLM.
  - Không đổi schema/entity/table.

## 2. Files/rules/reports đã đọc
| File | Mục đích đọc | Kết luận chính |
|---|---|---|
| `.cursor/rules/00-core-working-rule.mdc` | Rule nền | Sửa tối thiểu, không lan scope |
| `.cursor/rules/10-backend-rag-rule.mdc` | Rule backend | Ưu tiên reuse flow hiện có |
| `.cursor/rules/20-frontend-widget-rule.mdc` | Check FE contract/header | FE public chat dùng `x-api-key`, playground dùng body `chatbotId` |
| `.cursor/rules/30-deploy-env-ops-rule.mdc` | Verify command scope | Không đổi deploy/env |
| `.cursor/rules/40-db-vector-rule.mdc` | DB/vector safety | Không đổi schema/vector flow |
| `.cursor/rules/90-report-verification-rule.mdc` | Chuẩn report + verify honesty | Ghi rõ PASS/FAIL/NOT RUN |
| `reports/CURSOR_REPORT_00_BACKEND_FLOW_AND_FE_CONTRACT_AUDIT.md` | Baseline mapping | Nhóm Different/Partial cần xử lý: public chat, playground chat, chatbot create, upload |
| `Frontend/src/api/publicChatApi.js` | Contract public chat | `POST /api/public/chat`, header `x-api-key`, no JWT |
| `Frontend/src/api/playgroundApi.js` | Contract playground SSE | `POST /api/playground/chat` body có `chatbotId`, `overrideParams`, không gửi widget key |
| `Frontend/src/api/chatbotsApi.js` | Contract chatbot create | `POST /api/chatbots` body `{name,description,domain}` |
| `Frontend/src/api/documentsApi.js` | Contract upload | `POST /api/documents/upload`, form field `files`, không gửi `widgetId`/`chatbotId` |
| `Frontend/src/api/axiosInstance.js` | Error contract | FE ưu tiên backend trả `message`/`error` |
| `Backend/src/main/java/.../api/ChatController.java` | Endpoint chat hiện có | `/api/chat`, `/api/chat/stream` cần `Widget-Id` từ filter |
| `Backend/src/main/java/.../api/WidgetController.java` | Endpoint widget hiện có | `/api/widgets` gọi `WidgetService` |
| `Backend/src/main/java/.../api/DocumentController.java` | Upload hiện có | `/api/documents/upload/{widgetId}`, field `file` |
| `Backend/src/main/java/.../service/ChatService.java` | Reuse flow chat | Có thể reuse trực tiếp cho alias |
| `Backend/src/main/java/.../service/WidgetService.java` | Reuse flow widget create | Bắt buộc `allowedOrigin` không rỗng |
| `Backend/src/main/java/.../service/DocumentService.java` | Ingest dependency | Cần `widgetId` để chạy upload pipeline |
| `Backend/src/main/java/.../config/WidgetAuthFilter.java` | API key validation hiện có | Chỉ check `/api/chat` + header `X-Widget-Key` trước khi sửa |
| `Backend/src/main/java/.../config/SecurityConfig.java` | Security scope | permitAll, filter gắn global |
| `Backend/src/main/java/.../dto/WidgetCreateRequest.java` | DTO đầu vào widget | Có `name`, `allowedOrigin`, `uiConfig` |
| `Backend/src/main/java/.../dto/WidgetCreateResponse.java` | DTO output widget | Có `widgetConfigId`, `apiKey`, `uploadEndpoint`, ... |

## 3. Endpoint alias mapping
| FE endpoint | Existing BE endpoint | Action: Added alias / Blocked / Skipped | Reason |
|---|---|---|---|
| `POST /api/public/chat` | `POST /api/chat` | **Added alias** | Reuse `ChatService.chat`, chỉ khác path/header |
| `POST /api/playground/chat` (SSE) | `POST /api/chat/stream` | **Blocked** | FE gửi `chatbotId` + Bearer optional, BE stream hiện xác định widget bằng API key header; thiếu mapping chatbotId→widgetId trong scope alias-only |
| `POST /api/chatbots` | `POST /api/widgets` | **Added alias** | Reuse `WidgetService.createWidgetConfig` + adapter request/response tối thiểu |
| `POST /api/documents/upload` | `POST /api/documents/upload/{widgetId}` | **Blocked** | FE upload hiện không gửi `widgetId`/`chatbotId`; BE ingest bắt buộc `widgetId` |

## 4. Files changed
| File | Change | Reason | Risk |
|---|---|---|---|
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/api/PublicChatController.java` | Thêm controller alias `/api/public/chat` | Khớp FE publicChat path | Thấp |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/config/WidgetAuthFilter.java` | Mở rộng check path `/api/public/chat`, hỗ trợ header `x-api-key` (fallback `X-Widget-Key`) | Validate key cho alias public chat | Thấp |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/api/ChatbotAliasController.java` | Thêm controller alias `/api/chatbots` | Reuse flow tạo widget dưới tên chatbot | Trung bình (adapter fields mặc định) |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/dto/ChatbotCreateAliasRequest.java` | DTO request alias chatbot create | Tách contract FE `{name,description,domain}` | Thấp |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/dto/ChatbotCreateAliasResponse.java` | DTO response alias chatbot create | Trả shape FE kỳ vọng cho create | Thấp |

## 5. Details per alias

### 5.1 Public chat alias
- Path mới: `POST /api/public/chat`
- Header accept:
  - `x-api-key` (ưu tiên cho public chat FE)
  - `X-Widget-Key` (fallback tương thích cũ)
- Request DTO: `ChatRequest` (`message`, `sessionId`)
- Response DTO: `ChatResponse`
- Service flow reuse: `ChatService.chat` (giữ nguyên core retrieval/LLM flow)
- Endpoint cũ giữ nguyên: `POST /api/chat`, `POST /api/chat/stream`
- Ghi chú: alias này chấp nhận `sessionId` null/blank và tự generate UUID trước khi gọi service để tương thích FE public chat.

### 5.2 Chatbot create alias
- Path mới: `POST /api/chatbots`
- Header: không yêu cầu JWT (như endpoint cũ `/api/widgets`, đang permitAll)
- Request DTO alias: `ChatbotCreateAliasRequest { name, description, domain }`
- Mapping request:
  - `name` -> `WidgetCreateRequest.name`
  - `allowedOrigin` -> default `["http://localhost:5173"]` (vì FE payload không có)
  - `description/domain` -> lưu vào `uiConfig`
- Response DTO alias: `ChatbotCreateAliasResponse` (id/name/description/domain/documentCount/messageCount/status/updatedAt/initials/systemPrompt/modelConfig)
- Service flow reuse: `WidgetService.createWidgetConfig`
- Endpoint cũ giữ nguyên: `POST /api/widgets`

### 5.3 Không thêm alias trong prompt này
- `POST /api/playground/chat`: **Blocked**
- `POST /api/documents/upload`: **Blocked**

## 6. Blocked endpoints
- `POST /api/playground/chat`
  - Thiếu mapping `chatbotId -> widgetId` trong backend hiện tại.
  - FE gọi stream bằng body `{chatbotId,...}` + Bearer; BE stream hiện dựa vào key header để lấy widget context.
  - Nếu ép alias thuần path sẽ không xác định được widget đúng theo contract FE.
- `POST /api/documents/upload`
  - FE `documentsApi` chỉ gửi `files`.
  - BE ingest bắt buộc `widgetId` trong `DocumentService.uploadAndProcess`.
  - Không thể chọn widget mặc định trong scope này (bị cấm “hack default widget”).

## 7. Validation results
| Command | Result | Notes |
|---|---|---|
| `cd Backend && ./mvnw -DskipTests compile` | **PASS** | BUILD SUCCESS, compile 49 source files |
| `cd Backend && ./mvnw test` | **FAIL** | 14 tests run, 2 errors; lỗi môi trường DB (`Communications link failure`, `Connection refused` MySQL) khi load Spring context |
| `docker compose config` | **NOT RUN** | Prompt này không sửa docker/deploy wiring |

## 8. Curl/API test plan

### Public chat alias
```bash
curl -X POST http://localhost:8080/api/public/chat \
  -H "Content-Type: application/json" \
  -H "x-api-key: <widget-api-key-uuid>" \
  -d '{"message":"Xin chào","sessionId":null}'
```

### Legacy chat endpoint vẫn hoạt động
```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -H "X-Widget-Key: <widget-api-key-uuid>" \
  -d '{"message":"Xin chào","sessionId":"6f6c6797-f6d6-4fd1-bf1d-8b6f69b22f7a"}'
```

### Chatbot create alias
```bash
curl -X POST http://localhost:8080/api/chatbots \
  -H "Content-Type: application/json" \
  -d '{"name":"Test Bot","description":"Demo","domain":"support"}'
```

### Legacy widget create vẫn hoạt động
```bash
curl -X POST http://localhost:8080/api/widgets \
  -H "Content-Type: application/json" \
  -d '{"name":"Legacy Widget","allowedOrigin":["http://localhost:5173"],"uiConfig":{"theme":"blue"}}'
```

### Document upload alias (blocked expected)
```bash
curl -X POST http://localhost:8080/api/documents/upload \
  -F "files=@sample.pdf"
```
Expected: hiện chưa có alias do thiếu `widgetId`.

## 9. Known limitations
- Alias `/api/chatbots` hiện chỉ cover create path; chưa có list/get/update/delete/embed-config.
- `POST /api/chatbots` dùng `allowedOrigin` mặc định `http://localhost:5173` để đáp ứng validation hiện tại của `WidgetService`.
- Chưa xử lý `POST /api/playground/chat` vì thiếu mapping chatbot/widget và auth contract khác.
- Chưa xử lý `POST /api/documents/upload` vì FE contract chưa truyền tenant ID bắt buộc cho ingest.

## 10. Recommended next prompt
1. `02_BACKEND_PLAYGROUND_CHAT_WIDGET_MAPPING_ALIAS`  
   - Chốt mapping `chatbotId <-> widgetId` cho `/api/playground/chat` và auth model thống nhất.
2. `03_BACKEND_DOCUMENT_UPLOAD_ALIAS_WITH_WIDGET_CONTEXT`  
   - Chốt FE truyền `widgetId/chatbotId` trong form/query và hỗ trợ multi-file alias an toàn.
3. `04_BACKEND_CHATBOTS_CRUD_AND_EMBED_CONFIG`  
   - Hoàn thiện các endpoint còn thiếu cho area Chatbots/Embed.
