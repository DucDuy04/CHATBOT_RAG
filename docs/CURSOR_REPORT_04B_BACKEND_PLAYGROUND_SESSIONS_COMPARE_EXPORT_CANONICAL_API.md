# CURSOR REPORT 04B - BACKEND PLAYGROUND SESSIONS / COMPARE / EXPORT

## 1. Mức độ hiểu task
- Hiểu task: 98%
- Phần chắc chắn:
  - Cần implement 4 API: sessions list, delete session, compare, export.
  - FE contract là nguồn sự thật (`playgroundApi.js`, `ComparePane`, `SessionList`).
  - Không đổi core RAG flow, không sửa FE, không đổi DB schema.
- Phần còn giả định:
  - `session id` FE dùng là `sessionKey` UUID.
  - Compare có thể stateless và không persist.
- Thiếu dữ kiện:
  - Không có yêu cầu bắt buộc apply override runtime đầy đủ vào model/retrieval ở prompt này.

## 2. Tóm tắt yêu cầu
- Bổ sung endpoint canonical còn thiếu cho Playground backend để FE gọi được thực tế.
- Chuẩn hóa validation/error JSON có `message`.
- Reuse service hiện có ở mức hợp lý, tránh refactor lớn.
- Verify bằng compile + test + runtime smoke tests.

## 3. Hiện trạng trước khi sửa
- `PlaygroundController` chỉ có `POST /api/playground/chat`.
- Chưa có API sessions/compare/export.
- FE đã gọi các API đó nên khi runtime sẽ 404.

## 4. Nguyên nhân gốc xác nhận từ source
- Từ `Frontend/src/api/playgroundApi.js`: FE gọi `/api/playground/sessions`, `/api/playground/compare`, `/api/playground/export/:id`, `/api/playground/sessions/:id`.
- Từ backend source: chưa có mapping tương ứng ngoài `/chat`.

## 5. Chiến lược sửa đã chọn
- Thêm `PlaygroundService` để gom logic nghiệp vụ endpoint mới.
- Bổ sung DTO dedicated thay vì trả entity JPA trực tiếp.
- Giữ `ChatService` nguyên core; compare dùng retrieval+prompt+llm fallback ở service mới, không persist.
- Delete session dùng soft delete (`deletedAt`) cho session/messages.

## 6. Danh sách file đã đọc
- `.cursor/rules/00-core-working-rule.mdc`: xác định phạm vi sửa tối thiểu.
- `.cursor/rules/10-backend-rag-rule.mdc`: xác nhận hướng reuse backend core.
- `.cursor/rules/20-frontend-widget-rule.mdc`: kiểm tra parser/shape FE.
- `.cursor/rules/30-deploy-env-ops-rule.mdc`: lưu ý runtime phải chạy đúng binary.
- `.cursor/rules/40-db-vector-rule.mdc`: tránh đổi schema.
- `.cursor/rules/90-report-verification-rule.mdc`: format report bắt buộc.
- `reports/CURSOR_REPORT_01B...md`: baseline canonical endpoints.
- `reports/CURSOR_REPORT_02B...md`: baseline runtime chatbots.
- `reports/CURSOR_REPORT_03C...md`: baseline runtime documents.
- `reports/CURSOR_REPORT_04A...md`: baseline playground chat runtime.
- `Frontend/src/api/playgroundApi.js`: contract request/response.
- `Frontend/src/mocks/playgroundMock.js`: shape mock tham chiếu.
- `Frontend/src/pages/playground/PlaygroundPage.jsx`: cách FE consume data.
- `Frontend/src/pages/playground/components/ComparePane.jsx`: fields compare FE dùng.
- `Frontend/src/pages/playground/components/SessionList.jsx`: fields session list FE dùng.
- `Frontend/src/pages/playground/components/ExportSessionButton.jsx`: behavior download blob/object.
- `Backend/src/main/java/.../api/PlaygroundController.java`: điểm vào API hiện tại.
- `Backend/src/main/java/.../service/ChatService.java`: core chat/retrieval/sources.
- `Backend/src/main/java/.../domain/chat/ChatSession.java`: model session + sessionKey.
- `Backend/src/main/java/.../domain/chat/ChatMessage.java`: model messages + sources JSON.
- `Backend/src/main/java/.../domain/chat/ChatSessionRepository.java`: repository sessions.
- `Backend/src/main/java/.../domain/chat/ChatMessageRepository.java`: repository messages.
- `Backend/src/main/resources/application.yml`, `application-dev.yml`: config runtime.
- `docker-compose.yml`: trạng thái service runtime.

## 7. Danh sách file đã sửa
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/api/PlaygroundController.java` (api)
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/PlaygroundService.java` (service)
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/domain/chat/ChatSessionRepository.java` (db)
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/domain/chat/ChatMessageRepository.java` (db)
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/dto/PlaygroundMessageResponse.java` (api)
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/dto/PlaygroundSessionResponse.java` (api)
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/dto/PlaygroundCompareRequest.java` (api)
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/dto/PlaygroundCompareResult.java` (api)
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/dto/PlaygroundCompareResponse.java` (api)
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/dto/PlaygroundExportResponse.java` (api)
- `reports/CURSOR_REPORT_04B_BACKEND_PLAYGROUND_SESSIONS_COMPARE_EXPORT_CANONICAL_API.md` (docs)
- `docs/CURSOR_REPORT_04B_BACKEND_PLAYGROUND_SESSIONS_COMPARE_EXPORT_CANONICAL_API.md` (docs)

## 8. Diff thay đổi của từng file

### `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/api/PlaygroundController.java`
- Cũ: chỉ có `POST /chat`.
- Mới: thêm `GET /sessions`, `DELETE /sessions/{id}`, `POST /compare`, `GET /export/{sessionId}` + validation JSON message.
```diff
+ @GetMapping("/sessions")
+ @DeleteMapping("/sessions/{id}")
+ @PostMapping("/compare")
+ @GetMapping("/export/{sessionId}")
```

### `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/PlaygroundService.java`
- Cũ: chưa tồn tại.
- Mới: list/delete/export/compare logic, mapping DTO.
```diff
+ public List<PlaygroundSessionResponse> listSessions(UUID chatbotId)
+ public void deleteSession(UUID sessionKey)
+ public PlaygroundExportResponse exportSession(UUID sessionKey)
+ public PlaygroundCompareResponse compare(UUID chatbotId, String message, Map<String,Object> configA, Map<String,Object> configB)
```

### `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/domain/chat/ChatSessionRepository.java`
```diff
+ List<ChatSession> findByWidgetConfigIdOrderByUpdatedAtDesc(UUID widgetConfigId);
```

### `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/domain/chat/ChatMessageRepository.java`
```diff
+ long countBySessionId(UUID sessionId);
+ ChatMessage findTopBySessionIdOrderByCreatedAtDesc(UUID sessionId);
```

### DTO files mới
```diff
+ PlaygroundMessageResponse
+ PlaygroundSessionResponse
+ PlaygroundCompareRequest
+ PlaygroundCompareResult
+ PlaygroundCompareResponse
+ PlaygroundExportResponse
```

### Docs/report files mới
```diff
+ reports/CURSOR_REPORT_04B_BACKEND_PLAYGROUND_SESSIONS_COMPARE_EXPORT_CANONICAL_API.md
+ docs/CURSOR_REPORT_04B_BACKEND_PLAYGROUND_SESSIONS_COMPARE_EXPORT_CANONICAL_API.md
```

## 9. Ảnh hưởng sau sửa
- Behavior thay đổi:
  - FE có thể gọi đủ 4 API playground mới thay vì 404.
  - Sessions trả array theo chatbot, có `messageCount/lastMessage/messages`.
  - Compare trả `configA/configB` với answer/sources/latency/config.
  - Export trả JSON session + messages.
  - Delete session trả `{success:true}` và không còn xuất hiện trong list.
- Behavior giữ nguyên:
  - `/api/playground/chat` flow SSE không đổi core.
  - Ingest/retrieval/embedding không đổi.
- Điều kiện bật:
  - API mới hoạt động khi backend chạy binary mới.
- Fallback giữ:
  - LLM fallback service vẫn giữ nguyên.
- Tài nguyên:
  - Sessions API hiện tải full messages theo session (chi phí tăng khi lịch sử lớn).
- MySQL/Qdrant:
  - Không migration schema, không tác động dữ liệu vector.

## 10. Edge cases đã xem xét
- Missing `chatbotId` ở sessions.
- Invalid UUID `chatbotId`.
- Invalid UUID `sessionId` ở export/delete.
- Session không tồn tại.
- Compare với message blank.
- Compare khi retrieval rỗng.
- Runtime mismatch do instance cũ (404 dù code đã có) -> restart đúng binary.

## 11. Kết quả kiểm tra
| Command | Kết quả | Ghi chú |
|---|---|---|
| `cd Backend && ./mvnw -DskipTests compile` | PASS | Build success |
| `cd Backend && ./mvnw test` | PASS | 14 tests pass |
| Runtime setup chatbot + upload + chat | PASS | Tạo dữ liệu test thành công |
| `GET /api/playground/sessions?chatbotId=<uuid>` | PASS | Trả array, count=1 |
| `GET /api/playground/export/{sessionId}` | PASS | JSON có 2 messages |
| `POST /api/playground/compare` | PASS | A/B đều có answer/sources/latency |
| `DELETE /api/playground/sessions/{id}` | PASS | success=true, list sau delete không còn session |
| Invalid sessions/export ids | PASS | 400 + message rõ |
| `cd Frontend && npm run lint` | NOT RUN | ngoài scope |
| `cd Frontend && npm run build` | NOT RUN | ngoài scope |
| `cd Frontend && npm run build:widget` | NOT RUN | ngoài scope |
| `docker compose config` | NOT RUN | ngoài scope prompt |

## 12. Rủi ro còn lại
- Compare chưa apply runtime configA/configB thật vào model/retrieval internals, mới echo config + chạy cùng core behavior.
- Sessions trả full messages có thể nặng nếu dataset lớn (cần pagination/lazy-load nếu scale).

## 13. Đề xuất tiếp theo
- Prompt follow-up nhỏ để apply một phần override compare (temperature/systemPrompt) mà vẫn không refactor lớn.

