# Cursor Report 06C - BACKEND_CHAT_FEEDBACK_CANONICAL_API_AND_ANALYTICS_RATING_SOURCE

## 1. Mức độ hiểu task
- Task là gì?
  - Triển khai `POST /api/chat/feedback` theo FE contract.
  - Dùng feedback làm rating source cho:
    - `GET /api/analytics/sessions` filter + rating field.
    - `GET /api/analytics/summary` (`avgSatisfaction`, `avgSatisfactionDelta`).
- Hiểu task: 99%
- Phần chắc chắn:
  - FE có `analyticsApi.submitFeedback({ messageId, rating, comment })`.
  - FE không có GET feedback contract backend cho feedback tab (tab hiện dùng mock local).
  - `messageId` map vào `ChatMessage.id`.
- Phần còn giả định:
  - Mapping session rating hiển thị dạng số (`1`, `-1`, `null`) để không phá `SessionsTable`.
- Phạm vi không làm:
  - Không làm GET feedback list endpoint.
  - Không sửa FE/mock.
  - Không refactor core RAG/chat flow.
  - Không làm Settings/Dashboard scope mới.

## 2. Files/rules/reports đã đọc
| File | Mục đích đọc | Kết luận chính |
|---|---|---|
| `.cursor/rules/00-core-working-rule.mdc` | scope/minimal diff | sửa đúng điểm, có report docs bắt buộc |
| `.cursor/rules/10-backend-rag-rule.mdc` | backend constraints | không refactor core chat |
| `.cursor/rules/20-frontend-widget-rule.mdc` | FE contract usage | source of truth là analyticsApi.js/component usage |
| `.cursor/rules/30-deploy-env-ops-rule.mdc` | runtime check rule | verify runtime trung thực |
| `.cursor/rules/40-db-vector-rule.mdc` | DB change constraints | cho phép schema tối thiểu nếu cần |
| `.cursor/rules/90-report-verification-rule.mdc` | report format bắt buộc | cần report chi tiết + test trung thực |
| `reports/CURSOR_REPORT_04B_BACKEND_PLAYGROUND_SESSIONS_COMPARE_EXPORT_CANONICAL_API.md` | session model tham chiếu | id client-side dùng `sessionKey` |
| `reports/CURSOR_REPORT_06A_BACKEND_ANALYTICS_USAGE_CANONICAL_API.md` | usage baseline | `avgSatisfaction` trước đây null |
| `reports/CURSOR_REPORT_06B_BACKEND_ANALYTICS_SESSIONS_CANONICAL_API.md` | sessions baseline | rating filter vẫn placeholder, chưa có source |
| `Frontend/src/api/analyticsApi.js` | FE API contract chính | có `submitFeedback` POST `/api/chat/feedback`; không có GET feedback |
| `Frontend/src/mocks/analyticsMock.js` | shape rating mock | rating numeric; feedback mock chỉ local |
| `Frontend/src/pages/analytics/components/AnalyticsFeedbackTab.jsx` | xác nhận feedback tab runtime | tab hiển thị từ mock, không gọi backend GET |
| `Frontend/src/pages/analytics/components/AnalyticsSessionsTab.jsx` | sessions filter contract | filter values: `positive`, `negative`, `unrated` |
| `Frontend/src/pages/analytics/components/SessionsTable.jsx` | rating render behavior | rating hiển thị numeric (`Number(rating)`) |
| `Backend/src/main/java/.../api/ChatController.java` | vị trí add feedback endpoint | `POST /api/chat/*` hiện ở đây |
| `Backend/src/main/java/.../config/WidgetAuthFilter.java` | auth impact | filter `/api/chat/**` nên chặn feedback nếu không chỉnh |
| `Backend/src/main/java/.../service/AnalyticsService.java` | analytics integration point | cần nối feedback source cho sessions/summary |
| `Backend/src/main/java/.../domain/chat/ChatMessage.java` | map messageId | feedback phải gắn với `ChatMessage` |

## 3. Current feedback/rating contract analysis
- FE hiện có gọi `POST /api/chat/feedback` không?
  - Có. Trong `analyticsApi.submitFeedback(...)`.
- FE có GET/read feedback endpoint không?
  - Không có contract backend GET feedback trong `analyticsApi.js`.
  - `AnalyticsFeedbackTab` đọc mock local (`feedbackMockData`), có cảnh báo explicit.
- `messageId` map tới entity nào?
  - `ChatMessage.id` (UUID).
- rating source hiện có chưa?
  - Trước prompt 06C: chưa có table/entity feedback backend.
- cần thêm entity/table không?
  - Có. Thêm `ChatFeedback` tối thiểu là cần thiết để POST feedback + analytics rating source hoạt động thật.

## 4. FE contract mapping after implementation
| FE function | Endpoint | Request expected | Response expected | Backend implementation status | Notes |
|---|---|---|---|---|---|
| `analyticsApi.submitFeedback` | `POST /api/chat/feedback` | `{messageId, rating, comment}` | object có `success` (và data kèm theo an toàn) | DONE | trả `{success,messageId,rating,comment}` |
| `analyticsApi.getSessions` | `GET /api/analytics/sessions` | `rating=positive/negative/unrated` | page payload, item có `rating` | DONE | rating tính từ feedback aggregate theo session |
| `analyticsApi.getSummary` | `GET /api/analytics/summary` | `from,to,chatbotId?` | `avgSatisfaction`, `avgSatisfactionDelta` | DONE | tính từ feedback trong range |

## 5. Files changed
| File | Change | Reason | Risk |
|---|---|---|---|
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/domain/chat/ChatFeedback.java` | New entity/table | lưu feedback source thật | Medium |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/domain/chat/ChatFeedbackRepository.java` | New repository + aggregate queries | dùng cho summary + sessions rating | Medium |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/dto/ChatFeedbackRequest.java` | New request DTO | parse body chuẩn FE | Low |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/dto/ChatFeedbackResponse.java` | New response DTO | response success rõ ràng | Low |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/ChatFeedbackService.java` | New feedback upsert service | idempotent submit/update feedback | Medium |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/api/ChatController.java` | Add `POST /feedback` + validations | canonical endpoint theo FE | Low |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/config/WidgetAuthFilter.java` | Exclude `/api/chat/feedback` from widget-key check | FE submitFeedback không gửi API key | Medium |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/AnalyticsService.java` | Integrate feedback into sessions/summary | bật rating source thật | Medium |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/domain/chat/ChatSessionRepository.java` | Add full-list analytics query for rating filter pagination | rating filter total/page chính xác | Medium |

## 6. Data model / DB impact
- Entity/table mới:
  - `ChatFeedback` -> table `chat_feedbacks`.
- Fields:
  - `id` (UUID PK), `message_id` (FK unique), `rating` (int), `comment` (TEXT nullable), `created_at`, `updated_at`, `deleted_at`.
- Unique constraints:
  - Unique `message_id` (mỗi assistant message tối đa 1 feedback hiện hành).
- Relationship với `ChatMessage`:
  - `@OneToOne` từ `ChatFeedback` sang `ChatMessage`.
- Migration:
  - Không thêm script migration riêng.
  - Dựa trên `spring.jpa.hibernate.ddl-auto=update` (dev setup hiện tại).
- JPA ddl-auto/update impact:
  - Khi khởi động app/test, Hibernate tạo table + unique/foreign key cho `chat_feedbacks`.

## 7. Details per endpoint

### `POST /api/chat/feedback`
- Method/path:
  - `POST /api/chat/feedback`
- Request body:
  - `{ messageId, rating, comment }`
- Response DTO:
  - `ChatFeedbackResponse { success, messageId, rating, comment }`
- Service/repository used:
  - `ChatFeedbackService.submitFeedback`
  - `ChatMessageRepository.findById`
  - `ChatFeedbackRepository.findByMessageId` + `save`
- Validation:
  - missing `messageId` -> 400 `{ "message": "messageId is required" }`
  - invalid UUID -> 400 `{ "message": "messageId must be a UUID" }`
  - invalid rating -> 400 `{ "message": "rating must be 1 or -1" }`
  - non-assistant message -> 400 `{ "message": "Feedback can only be submitted for assistant messages" }`
  - message not found -> 404 `{ "message": "Message not found" }`
- Upsert behavior:
  - Nếu feedback đã tồn tại theo `messageId` thì update rating/comment, không tạo duplicate.

## 8. Analytics integration
- Sessions rating filter:
  - `positive`: session aggregate rating = `1`.
  - `negative`: session aggregate rating = `-1`.
  - `unrated`: session aggregate rating = `null`.
  - all/empty: không filter rating.
- Session item rating:
  - aggregate từ feedback assistant messages trong session:
    - positiveCount > negativeCount => `1`
    - negativeCount > positiveCount => `-1`
    - tie hoặc không feedback => `null` (safe fallback)
- `avgSatisfaction`:
  - tính theo feedback trong range:
    - `positiveFeedback / totalFeedback * 100`
  - nếu `totalFeedback=0` => `avgSatisfaction = null`.
- `avgSatisfactionDelta`:
  - tính theo previous period cùng độ dài.
  - dùng point delta (current - previous), null-safe.
- `fallbackRate`:
  - giữ nguyên logic cũ (`0.0`) vì chưa có fallback source thật.
- Limitation còn lại:
  - Sessions rating filter đang dùng in-memory filter khi lọc theo rating để giữ total/page chính xác, phù hợp dataset admin nhỏ.

## 9. Validation results
| Command | Result | Notes |
|---|---|---|
| `cd Backend && ./mvnw -DskipTests compile` | PASS | compile thành công sau thêm feedback source |
| `cd Backend && ./mvnw test` | PASS | 14 tests pass; Hibernate tạo table `chat_feedbacks` bởi ddl-auto update |

## 10. Manual/API test plan and results
| Endpoint | Curl / Steps | Result | Notes |
|---|---|---|---|
| Submit positive feedback | `POST /api/chat/feedback` với assistant message id + `rating=1` | PASS | 200 + success true |
| Submit negative feedback update | cùng message id, `rating=-1` | PASS | 200 + upsert update |
| Invalid rating | `rating=0` | PASS | 400 message |
| Invalid messageId | `messageId=not-a-uuid` | PASS | 400 message |
| Missing messageId | body thiếu `messageId` | PASS | 400 message |
| Message not found | random UUID | PASS | 404 message |
| Non-assistant message feedback | user message id | PASS | 400 message đúng contract |
| Sessions negative filter | `GET /api/analytics/sessions?...&rating=negative` | PASS | session có feedback negative xuất hiện |
| Sessions positive filter | `...&rating=positive` | PASS | sau khi update negative thì không còn session |
| Summary satisfaction | `GET /api/analytics/summary?...` | PASS | `avgSatisfaction` từ null -> 0.0 theo feedback hiện tại |

## 11. Known limitations / gaps
- FE Feedback tab hiện không gọi backend GET feedback nên chưa có read analytics feedback endpoint trong scope 06C.
- Session rating aggregate tie (`positive == negative`) đang map `null` (treated as unrated).
- Sessions rating filter dùng in-memory aggregation/filter để giữ đúng semantics; với dữ liệu rất lớn có thể cần query tối ưu hơn.

## 12. Recommended next prompt
- `06D_BACKEND_ANALYTICS_FEEDBACK_READ_API_CANONICAL` (chỉ khi FE cần bỏ mock và đọc feedback thật từ backend cho Feedback tab).
