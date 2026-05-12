# CURSOR REPORT 04A - BACKEND PUBLIC + PLAYGROUND CHAT RUNTIME SMOKE TEST

## 1. Mức độ hiểu task
- Hiểu task: 98%
- Phần chắc chắn:
  - Phải smoke test runtime thật cho `POST /api/public/chat` và `POST /api/playground/chat`.
  - Public chat cần header `x-api-key`, không JWT.
  - Playground chat trả SSE, FE parser đọc `event: token` và `event: done`.
- Phần còn giả định:
  - Runtime backend trên `localhost:8080` phải là binary mới nhất của source workspace.
  - `chatbotId` playground là UUID của chatbot/widget backend.
- Thiếu dữ kiện:
  - Không có yêu cầu bắt buộc enrich `done` event bằng latency/sessionId trong prompt này.

## 2. Tóm tắt yêu cầu
- Dùng FE contract làm source of truth.
- Tạo test data (chatbot + document), chạy smoke tests public + playground.
- Nếu có bug nhỏ endpoint mapping/response/SSE format thì sửa đúng bug và retest.
- Chạy compile và test backend để xác nhận.
- Tạo report kết quả trung thực.

## 3. Hiện trạng trước khi sửa
- Public chat runtime hoạt động với key-based filter.
- Playground chat SSE success path hoạt động, nhưng case validate lỗi (`chatbotId` invalid/missing) trả JSON generic của framework (`timestamp/status/error`) thay vì message rõ để FE hiển thị trực tiếp.

## 4. Nguyên nhân gốc xác nhận từ source
- `PlaygroundController` trước sửa dùng `ResponseStatusException` cho validate fail.
- Với cấu hình hiện tại, lỗi 400 từ `ResponseStatusException` được serialize theo default error body, không chứa trường `message` như FE ưu tiên parse trong `playgroundApi`.

## 5. Chiến lược sửa đã chọn
- Giữ nguyên core stream flow `ChatService.chatStream`.
- Chỉ chỉnh `PlaygroundController`:
  - Validation fail trả `ResponseEntity.badRequest().body(Map.of("message", ...))`.
  - Success path trả trực tiếp `SseEmitter`.
- Không đụng service/retrieval/LLM/DB schema.

## 6. Danh sách file đã đọc
- `.cursor/rules/00-core-working-rule.mdc`: xác nhận nguyên tắc minimal diff, không mở rộng scope.
- `.cursor/rules/90-report-verification-rule.mdc`: xác nhận format report bắt buộc.
- `agent.md`, `agent/01-overview.md`, `agent/02-architecture.md`: nắm kiến trúc runtime và multi-tenant flow.
- `reports/CURSOR_REPORT_01B_BACKEND_CANONICAL_FE_ENDPOINTS_NOT_ALIASES.md`: đối chiếu endpoint canonical đã có.
- `reports/CURSOR_REPORT_02B_RERUN_BACKEND_CHATBOTS_API_RUNTIME_SMOKE_TEST.md`: xác nhận chatbots API runtime đã PASS.
- `reports/CURSOR_REPORT_03A_BACKEND_DOCUMENTS_API_CONTRACT_ALIGNMENT_AND_SAFE_CANONICAL_ENDPOINTS.md`: nắm documents contract canonical.
- `reports/CURSOR_REPORT_03B_FE_DOCUMENT_UPLOAD_TENANT_CONTEXT.md`: nắm FE upload tenant context.
- `reports/CURSOR_REPORT_03C_BACKEND_DOCUMENTS_RUNTIME_SMOKE_TEST.md`: baseline runtime môi trường.
- `Frontend/src/api/publicChatApi.js`: xác nhận request/response public chat.
- `Frontend/src/api/playgroundApi.js`: xác nhận parser SSE token/done + parse lỗi.
- `Frontend/src/mocks/playgroundMock.js`: tham chiếu done shape mock.
- `Frontend/src/pages/playground/PlaygroundPage.jsx` và `Frontend/src/pages/playground/components/*`: xác nhận FE consume `sources/latency/sessionId` kiểu defensive.
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/api/PublicChatController.java`: xác nhận response public chat.
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/api/PlaygroundController.java`: xác nhận validate + stream mapping.
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/ChatService.java`: xác nhận SSE event shape thực tế.
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/config/WidgetAuthFilter.java`: xác nhận behavior missing/invalid API key.
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/config/SecurityConfig.java`: xác nhận security permit.
- `Backend/src/main/resources/application.yml`, `application-dev.yml`, `docker-compose.yml`: xác nhận runtime env.

## 7. Danh sách file đã sửa
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/api/PlaygroundController.java`
  - Sửa để trả lỗi validation có trường `message` rõ ràng.
  - Ảnh hưởng lớp: `api`.
- `reports/CURSOR_REPORT_04A_BACKEND_PUBLIC_AND_PLAYGROUND_CHAT_RUNTIME_SMOKE_TEST.md`
  - Thêm report theo format prompt 04A.
  - Ảnh hưởng lớp: `docs`.
- `docs/CURSOR_REPORT_04A_BACKEND_PUBLIC_AND_PLAYGROUND_CHAT_RUNTIME_SMOKE_TEST.md`
  - Thêm report bắt buộc theo workspace rule 90.
  - Ảnh hưởng lớp: `docs`.

## 8. Diff thay đổi của từng file

### File: `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/api/PlaygroundController.java`
- Hiện trạng cũ liên quan bug:
  - Validate fail ném `ResponseStatusException`, body lỗi generic.
- Đã sửa gì:
  - Chuyển validate fail sang `ResponseEntity.badRequest().body(Map.of("message", ...))`.
  - Success path trả trực tiếp `SseEmitter`.
- Vì sao sửa:
  - Để FE nhận được trường `message` nhất quán cho invalid/missing chatbotId.
- Ảnh hưởng sau sửa:
  - Invalid/missing chatbotId trả 400 + message rõ.
  - SSE success path giữ nguyên token/done behavior.

```diff
@@
-    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
-    public SseEmitter chat(@RequestBody PlaygroundChatRequest request) {
+    @PostMapping("/chat")
+    public Object chat(@RequestBody PlaygroundChatRequest request) {
@@
-        if (request.getMessage() == null || request.getMessage().isBlank()) {
-            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message must not be blank");
-        }
+        if (request.getMessage() == null || request.getMessage().isBlank()) {
+            return ResponseEntity.badRequest().body(Map.of("message", "message must not be blank"));
+        }
@@
-        if (request.getChatbotId() == null || request.getChatbotId().isBlank()) {
-            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "chatbotId must not be blank");
-        }
+        if (request.getChatbotId() == null || request.getChatbotId().isBlank()) {
+            return ResponseEntity.badRequest().body(Map.of("message", "chatbotId must not be blank"));
+        }
@@
-            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "chatbotId must be a UUID");
+            return ResponseEntity.badRequest().body(Map.of("message", "chatbotId must be a UUID"));
@@
-        return ResponseEntity.ok().contentType(MediaType.TEXT_EVENT_STREAM).body(chatService.chatStream(chatRequest, widgetId));
+        return chatService.chatStream(chatRequest, widgetId);
```

### File: `reports/CURSOR_REPORT_04A_BACKEND_PUBLIC_AND_PLAYGROUND_CHAT_RUNTIME_SMOKE_TEST.md`
- Hiện trạng cũ liên quan bug: chưa có report 04A.
- Đã sửa gì: tạo report runtime smoke test theo format prompt user yêu cầu.
- Vì sao sửa: đáp ứng đầu ra bắt buộc của prompt.
- Ảnh hưởng sau sửa: reviewer có đầy đủ evidence runtime test.

```diff
+ Added full report file with sections 1..12 for prompt 04A.
```

### File: `docs/CURSOR_REPORT_04A_BACKEND_PUBLIC_AND_PLAYGROUND_CHAT_RUNTIME_SMOKE_TEST.md`
- Hiện trạng cũ liên quan bug: chưa có report theo workspace rule 90.
- Đã sửa gì: tạo report đầy đủ 13 mục bắt buộc.
- Vì sao sửa: tuân thủ rule workspace always-apply.
- Ảnh hưởng sau sửa: bảo đảm audit nội bộ theo chuẩn docs/.

```diff
+ Added workspace-compliant report with sections 1..13.
```

## 9. Ảnh hưởng sau sửa
- Behavior thay đổi:
  - `POST /api/playground/chat` khi `chatbotId` invalid/missing trả 400 body có `message` rõ ràng.
- Behavior giữ nguyên:
  - Public chat flow không đổi.
  - Playground success vẫn là SSE `token` + `done`.
  - Core retrieval/LLM/chunking/embedding không đổi.
- Điều kiện kích hoạt:
  - Chỉ ảnh hưởng nhánh validation fail của playground.
- Fallback giữ nguyên:
  - `ChatService` fallback model stream/non-stream không đổi.
- Tài nguyên:
  - Không tăng memory/cpu đáng kể.
- Latency/token/API cost:
  - Không đổi ở success path.
- Dữ liệu MySQL/Qdrant cũ:
  - Không migration, không đổi schema, không xóa dữ liệu production.

## 10. Edge cases đã xem xét
- Missing `x-api-key` ở public chat.
- Invalid `x-api-key` format.
- `sessionId` null ở public chat.
- Invalid UUID `chatbotId` ở playground.
- Missing `chatbotId` ở playground.
- SSE stream có `token` và `done` để FE parser không crash.
- Runtime mismatch do JVM cũ trên cổng 8080 (đã xử lý bằng restart đúng source).

## 11. Kết quả kiểm tra
| Command | Kết quả | Ghi chú |
|---|---|---|
| `docker ps` | PASS | mysql + qdrant running |
| `POST /api/chatbots` (runtime) | PASS | tạo chatbot smoke + lấy apiKey |
| `POST /api/documents/upload` (runtime) | PASS | upload txt, status INDEXED, chunkCount=1 |
| Public tests 1..4 | PASS | 2xx cho valid, 401 cho missing/invalid key, không 500 |
| Playground tests 5..7 | PASS | SSE token/done hoạt động; invalid/missing chatbotId trả 400 + message |
| `cd Backend && ./mvnw -DskipTests compile` | PASS | build success sau sửa |
| `cd Backend && ./mvnw test` | PASS | 14 tests pass |
| `cd Frontend && npm run lint` | NOT RUN | ngoài scope |
| `cd Frontend && npm run build` | NOT RUN | ngoài scope |
| `cd Frontend && npm run build:widget` | NOT RUN | ngoài scope |
| `docker compose config` | NOT RUN | không sửa compose/config |

## 12. Rủi ro còn lại
- SSE `done` hiện trả array `sources` (chưa enrich `latency/sessionId`), FE hiện tại xử lý được nhưng panel latency/session có thể không đầy đủ dữ liệu từ done event.
- Terminal PowerShell có thể hiển thị ký tự tiếng Việt lỗi mã hóa dù payload contract không sai.

## 13. Đề xuất tiếp theo
- Nếu cần parity cao hơn với mock playground, tạo prompt follow-up nhỏ để enrich `done` event với object có `sources`, `latency`, `sessionId` (không bắt buộc để pass smoke test hiện tại).

