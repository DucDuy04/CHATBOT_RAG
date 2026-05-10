# CURSOR REPORT 06C BACKEND CHAT FEEDBACK CANONICAL API AND ANALYTICS RATING SOURCE

## 1. Mức độ hiểu task
- Hiểu task: 99%.
- Phần chắc chắn:
  - Cần implement `POST /api/chat/feedback` theo FE contract.
  - Cần tích hợp feedback vào Analytics Sessions rating filter và Analytics Summary `avgSatisfaction`.
  - Không được tạo GET feedback endpoint nếu FE không gọi.
- Phần còn giả định:
  - Session aggregate rating tie (`positive == negative`) nên trả `null` để an toàn với FE hiện tại.
- Thiếu dữ kiện:
  - FE chưa có contract đọc feedback real-time (Feedback tab đang mock local).

## 2. Tóm tắt yêu cầu
- Add canonical feedback endpoint:
  - body `{ messageId, rating: 1|-1, comment }`
  - validation + JSON error message chuẩn.
- Thêm data source feedback tối thiểu để lưu feedback (upsert theo `messageId`).
- Dùng feedback source cập nhật:
  - sessions rating + filter (`positive/negative/unrated`)
  - summary `avgSatisfaction` và delta.

## 3. Hiện trạng trước khi sửa
- Chưa có feedback entity/repository.
- `POST /api/chat/feedback` chưa tồn tại.
- `WidgetAuthFilter` chặn toàn bộ `/api/chat/**`, gây không gọi được feedback endpoint nếu không gửi key.
- Analytics sessions đang trả `rating = null`; filter positive/negative trả rỗng.
- Analytics summary đang trả `avgSatisfaction = null` cố định.

## 4. Nguyên nhân gốc xác nhận từ source
- FE `analyticsApi.submitFeedback` đã gọi `POST /api/chat/feedback` nhưng backend thiếu endpoint.
- `AnalyticsFeedbackTab` đang dùng mock local, chưa có GET backend contract.
- `AnalyticsService` chưa có nguồn dữ liệu rating nên không thể tính `avgSatisfaction` hoặc filter sessions theo feedback.

## 5. Chiến lược sửa đã chọn
- Thêm mô hình feedback tối thiểu (`ChatFeedback`) gắn `ChatMessage` one-to-one, unique theo message.
- Thêm service riêng `ChatFeedbackService` để xử lý validation business và upsert.
- Thêm endpoint trong `ChatController` để giữ path canonical `/api/chat/feedback`.
- Chỉnh `WidgetAuthFilter` chỉ bắt auth cho `/api/chat` và `/api/chat/stream`, không bắt `/api/chat/feedback`.
- Nối feedback vào `AnalyticsService`:
  - rating per session (aggregate + filter)
  - satisfaction summary theo date range.
- Giữ diff nhỏ, không đụng core RAG.

## 6. Danh sách file đã đọc
- `.cursor/rules/00-core-working-rule.mdc`: quy tắc nền, minimal diff, report bắt buộc.
- `.cursor/rules/10-backend-rag-rule.mdc`: giới hạn backend scope.
- `.cursor/rules/20-frontend-widget-rule.mdc`: FE contract là source of truth.
- `.cursor/rules/30-deploy-env-ops-rule.mdc`: runtime verify trung thực.
- `.cursor/rules/40-db-vector-rule.mdc`: schema change tối thiểu có căn cứ.
- `.cursor/rules/90-report-verification-rule.mdc`: format báo cáo chi tiết bắt buộc.
- `reports/CURSOR_REPORT_04B_BACKEND_PLAYGROUND_SESSIONS_COMPARE_EXPORT_CANONICAL_API.md`: session key behavior.
- `reports/CURSOR_REPORT_06A_BACKEND_ANALYTICS_USAGE_CANONICAL_API.md`: analytics usage baseline.
- `reports/CURSOR_REPORT_06B_BACKEND_ANALYTICS_SESSIONS_CANONICAL_API.md`: sessions rating limitation trước 06C.
- `Frontend/src/api/analyticsApi.js`: xác nhận contract submitFeedback + analytics endpoints.
- `Frontend/src/mocks/analyticsMock.js`: rating shape + feedback mock context.
- `Frontend/src/pages/analytics/components/AnalyticsFeedbackTab.jsx`: xác nhận không có GET feedback backend.
- `Frontend/src/pages/analytics/components/AnalyticsSessionsTab.jsx`: rating filter keys.
- `Frontend/src/pages/analytics/components/SessionsTable.jsx`: FE render rating numeric.
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/api/ChatController.java`: điểm add feedback endpoint.
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/config/WidgetAuthFilter.java`: nguyên nhân 401 feedback khi chưa chỉnh filter.
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/AnalyticsService.java`: điểm tích hợp rating source.
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/domain/chat/ChatMessage.java`: map `messageId`.
- `Backend/src/main/resources/application*.yml`: xác nhận `ddl-auto=update` cho schema bổ sung tối thiểu.

## 7. Danh sách file đã sửa
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/domain/chat/ChatFeedback.java`
  - Sửa để làm gì: thêm entity feedback.
  - Ảnh hưởng lớp: db.
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/domain/chat/ChatFeedbackRepository.java`
  - Sửa để làm gì: query upsert lookup + aggregate analytics.
  - Ảnh hưởng lớp: db.
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/dto/ChatFeedbackRequest.java`
  - Sửa để làm gì: parse request body feedback.
  - Ảnh hưởng lớp: api.
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/dto/ChatFeedbackResponse.java`
  - Sửa để làm gì: trả response feedback nhất quán.
  - Ảnh hưởng lớp: api.
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/ChatFeedbackService.java`
  - Sửa để làm gì: feedback upsert + assistant-only guard.
  - Ảnh hưởng lớp: service.
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/api/ChatController.java`
  - Sửa để làm gì: thêm endpoint `POST /api/chat/feedback` và validation.
  - Ảnh hưởng lớp: api.
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/config/WidgetAuthFilter.java`
  - Sửa để làm gì: bỏ yêu cầu API key cho feedback endpoint.
  - Ảnh hưởng lớp: config.
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/AnalyticsService.java`
  - Sửa để làm gì: tích hợp feedback source vào sessions rating + summary satisfaction.
  - Ảnh hưởng lớp: service.
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/domain/chat/ChatSessionRepository.java`
  - Sửa để làm gì: thêm query list toàn range cho in-memory rating filter paging chính xác.
  - Ảnh hưởng lớp: db.
- `reports/CURSOR_REPORT_06C_BACKEND_CHAT_FEEDBACK_CANONICAL_API_AND_ANALYTICS_RATING_SOURCE.md`
  - Sửa để làm gì: report theo prompt user.
  - Ảnh hưởng lớp: docs.
- `docs/CURSOR_REPORT_06C_BACKEND_CHAT_FEEDBACK_CANONICAL_API_AND_ANALYTICS_RATING_SOURCE.md`
  - Sửa để làm gì: report verification theo rule 90.
  - Ảnh hưởng lớp: docs.

## 8. Diff thay đổi của từng file

### `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/domain/chat/ChatFeedback.java`
- Hiện trạng cũ liên quan bug: chưa có feedback data source thật.
- Đã sửa gì: thêm entity feedback one-to-one với message.
- Vì sao sửa như vậy: cần persistence tối thiểu cho endpoint và analytics rating source.
- Ảnh hưởng sau sửa: có table `chat_feedbacks` khi app khởi động.
```diff
+ @Entity
+ @Table(name = "chat_feedbacks", uniqueConstraints = @UniqueConstraint(columnNames = {"message_id"}))
+ public class ChatFeedback { ... message, rating, comment, createdAt, updatedAt, deletedAt ... }
```

### `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/domain/chat/ChatFeedbackRepository.java`
- Hiện trạng cũ liên quan bug: không có query feedback.
- Đã sửa gì: thêm lookup by message và aggregate queries theo range/session.
- Vì sao sửa như vậy: phục vụ upsert và analytics calculations.
- Ảnh hưởng sau sửa: tính được satisfaction và session rating.
```diff
+ Optional<ChatFeedback> findByMessageId(UUID messageId);
+ long countInRange(...)
+ long countByRatingInRange(..., int rating)
+ List<Object[]> aggregateBySessionIds(...)
```

### `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/dto/ChatFeedbackRequest.java`
- Hiện trạng cũ liên quan bug: chưa có request DTO feedback.
- Đã sửa gì: thêm `messageId`, `rating`, `comment`.
- Vì sao sửa như vậy: parse body chuẩn FE.
- Ảnh hưởng sau sửa: controller validate field rõ ràng.
```diff
+ public class ChatFeedbackRequest { private String messageId; private Integer rating; private String comment; }
```

### `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/dto/ChatFeedbackResponse.java`
- Hiện trạng cũ liên quan bug: chưa có response DTO feedback.
- Đã sửa gì: thêm response `success/messageId/rating/comment`.
- Vì sao sửa như vậy: align FE `submitFeedback` return shape an toàn.
- Ảnh hưởng sau sửa: FE có thể dùng trực tiếp kết quả submit.
```diff
+ public class ChatFeedbackResponse { boolean success; String messageId; Integer rating; String comment; }
```

### `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/ChatFeedbackService.java`
- Hiện trạng cũ liên quan bug: không có business logic feedback.
- Đã sửa gì: thêm `submitFeedback` với:
  - check message tồn tại
  - check message role assistant
  - upsert theo `messageId`.
- Vì sao sửa như vậy: đảm bảo idempotent và không duplicate.
- Ảnh hưởng sau sửa: feedback resubmit sẽ update record cũ.
```diff
+ ChatMessage message = chatMessageRepository.findById(messageId).orElseThrow(...)
+ if (message.getRole() != MessageRole.ASSISTANT) throw new IllegalStateException(...)
+ ChatFeedback feedback = chatFeedbackRepository.findByMessageId(messageId).orElseGet(...)
+ feedback.setRating(rating); feedback.setComment(...)
+ chatFeedbackRepository.save(feedback)
```

### `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/api/ChatController.java`
- Hiện trạng cũ liên quan bug: chưa có `/api/chat/feedback`.
- Đã sửa gì: thêm endpoint mới + JSON validation errors theo checklist.
- Vì sao sửa như vậy: implement canonical endpoint FE đang gọi.
- Ảnh hưởng sau sửa: submit feedback hoạt động không cần đổi FE.
```diff
+ @PostMapping("/feedback")
+ public ResponseEntity<?> submitFeedback(@RequestBody ChatFeedbackRequest request) { ... }
+ // 400: messageId is required / messageId must be a UUID / rating must be 1 or -1
+ // 404: Message not found
+ // 400: Feedback can only be submitted for assistant messages
```

### `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/config/WidgetAuthFilter.java`
- Hiện trạng cũ liên quan bug: `/api/chat/feedback` bị match `/api/chat/**` và yêu cầu key.
- Đã sửa gì: `isChatPath` chỉ còn `/api/chat` và `/api/chat/stream`.
- Vì sao sửa như vậy: FE submitFeedback không gửi widget key.
- Ảnh hưởng sau sửa: feedback endpoint không còn bị 401 do missing header.
```diff
- boolean isChatPath = path.equals("/api/chat") || path.startsWith("/api/chat/");
+ boolean isChatPath = path.equals("/api/chat") || path.equals("/api/chat/stream");
```

### `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/AnalyticsService.java`
- Hiện trạng cũ liên quan bug:
  - sessions rating luôn null.
  - summary avgSatisfaction luôn null.
- Đã sửa gì:
  - inject `ChatFeedbackRepository`.
  - tính satisfaction stats theo range/chatbot.
  - tính session rating aggregate (+1/-1/null) từ feedback per session.
  - filter sessions theo `positive/negative/unrated`.
- Vì sao sửa như vậy: đưa rating source thật vào analytics mà không thêm endpoint ngoài scope.
- Ảnh hưởng sau sửa:
  - sessions filter phản ánh feedback.
  - summary satisfaction phản ánh feedback trong period.
```diff
+ private final ChatFeedbackRepository chatFeedbackRepository;
+ SatisfactionStats currentSatisfaction = getSatisfactionStats(...)
+ .avgSatisfaction(currentSatisfaction.avgSatisfaction)
+ .avgSatisfactionDelta(calculatePointDelta(...))
+ buildSessionRatingMap(...)
+ matchesRatingFilter(...)
+ getSatisfactionStats(...)
```

### `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/domain/chat/ChatSessionRepository.java`
- Hiện trạng cũ liên quan bug: chỉ có query pageable; rating-filter cần total/page chính xác theo aggregate rating.
- Đã sửa gì: thêm `findAllForAnalyticsRange(...)`.
- Vì sao sửa như vậy: cho phép filter aggregate rating in-memory trước khi paginate.
- Ảnh hưởng sau sửa: total/page của `rating=positive|negative|unrated` chính xác theo dataset.
```diff
+ List<ChatSession> findAllForAnalyticsRange(@Param("from")..., @Param("to")..., @Param("widgetId")...)
```

### `reports/CURSOR_REPORT_06C_BACKEND_CHAT_FEEDBACK_CANONICAL_API_AND_ANALYTICS_RATING_SOURCE.md`
```diff
+ Added prompt-required technical report for 06C.
```

### `docs/CURSOR_REPORT_06C_BACKEND_CHAT_FEEDBACK_CANONICAL_API_AND_ANALYTICS_RATING_SOURCE.md`
```diff
+ Added verification report for 06C following rule 90.
```

## 9. Ảnh hưởng sau sửa
- Behavior thay đổi:
  - `POST /api/chat/feedback` hoạt động theo contract.
  - Feedback cùng message được update thay vì duplicate.
  - Sessions analytics có rating thật và filter theo rating.
  - Summary analytics có `avgSatisfaction` tính theo feedback range.
- Behavior giữ nguyên:
  - Core chat sync/stream, RAG retrieval, dashboard, settings không đổi.
  - Fallback rate logic analytics giữ nguyên.
- Điều kiện bật:
  - Satisfaction/rating chỉ meaningful khi có feedback data.
- Fallback giữ:
  - Không có feedback thì `avgSatisfaction=null`, session rating null.
- Ảnh hưởng memory/cpu/disk:
  - Tăng nhẹ CPU/DB read cho aggregate rating/satisfaction.
  - Tăng disk nhỏ do lưu feedback records.
- Ảnh hưởng latency/token/API cost:
  - Không tăng token/API cost LLM (không gọi provider mới).
- Ảnh hưởng dữ liệu MySQL/Qdrant cũ:
  - MySQL thêm table `chat_feedbacks`; không ảnh hưởng dữ liệu cũ.
  - Qdrant không thay đổi.

## 10. Edge cases đã xem xét
- `messageId` null/blank.
- `messageId` invalid UUID.
- `messageId` không tồn tại.
- feedback target là user message (non-assistant).
- `rating` ngoài `1|-1`.
- submit lại cùng `messageId` nhiều lần (upsert).
- analytics range có/không có feedback.
- rating filter `positive/negative/unrated/all`.
- runtime cũ chưa reload gây false 401 (đã restart và retest).

## 11. Kết quả kiểm tra
| Command | Kết quả | Ghi chú |
|---|---|---|
| `cd Backend && ./mvnw -DskipTests compile` | PASS | Compile thành công sau thay đổi |
| `cd Backend && ./mvnw test` | PASS | 14 tests pass; Hibernate tạo `chat_feedbacks` |
| `cd Frontend && npm run lint` | NOT RUN | ngoài scope backend prompt |
| `cd Frontend && npm run build` | NOT RUN | ngoài scope backend prompt |
| `cd Frontend && npm run build:widget` | NOT RUN | ngoài scope backend prompt |
| `docker compose config` | NOT RUN | không đổi deploy config |

Manual API verification:
- PASS `POST /api/chat/feedback` positive submit.
- PASS upsert update cùng message sang negative.
- PASS validate errors (missing/invalid/not found/non-assistant/rating invalid).
- PASS sessions rating filters phản ánh feedback.
- PASS summary satisfaction phản ánh feedback.

## 12. Rủi ro còn lại
- FE Feedback tab chưa gọi backend GET nên chưa hiển thị feedback real từ DB.
- Sessions rating aggregate tie hiện map `null` (coi như unrated), chưa có trạng thái `mixed`.
- Với dữ liệu rất lớn, rating filter in-memory có thể cần tối ưu query-level ở prompt sau.

## 13. Đề xuất tiếp theo
- Nếu FE cần bỏ mock Feedback tab: implement read-only analytics feedback endpoints theo contract mới (chỉ khi FE đã define rõ API).
