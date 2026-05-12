# CURSOR REPORT 06B BACKEND ANALYTICS SESSIONS CANONICAL API

## 1. Mức độ hiểu task
- Hiểu task: 98%.
- Chắc chắn: cần implement 2 endpoints analytics sessions theo FE contract, có paging/filter, detail messages, JSON error đúng format.
- Còn giả định: chưa có nguồn rating thật trong DB, nên áp dụng safe behavior theo yêu cầu prompt.
- Thiếu dữ kiện: không có feedback/rating entity chính thức trong backend hiện tại.

## 2. Tóm tắt yêu cầu
- Implement:
  - `GET /api/analytics/sessions?from=&to=&chatbotId=&rating=&page=&size=`
  - `GET /api/analytics/sessions/{id}/messages`
- Không làm feedback/settings/schema migration.
- Compile + test + runtime smoke test + report.

## 3. Hiện trạng trước khi sửa
- `AnalyticsController` mới có usage endpoints (`summary/daily/by-chatbot/unanswered`).
- `AnalyticsService` chưa có sessions list/detail methods.
- Chưa có DTO cho sessions page + messages detail.
- `ChatSessionRepository` chưa có query analytics-safe để lọc orphan/deleted widget trong list.

## 4. Nguyên nhân gốc xác nhận từ source
- FE Sessions tab gọi `analyticsApi.getSessions` và `analyticsApi.getSessionMessages`, nhưng backend chưa có endpoint tương ứng.
- Runtime lần đầu xuất hiện 500 với sessions list all-chatbot do lazy load `WidgetConfig` bị orphan/deleted (`EntityNotFoundException`) khi map `chatbotName`.

## 5. Chiến lược sửa đã chọn
- Thêm DTO rõ ràng cho response, không trả entity.
- Bổ sung endpoint và validation ngay trong `AnalyticsController` để trả JSON error đồng nhất.
- Bổ sung query `ChatSessionRepository.findForAnalyticsRange` có `JOIN FETCH` + filter `deletedAt IS NULL` cho session/widget để tránh orphan crash.
- Thêm mapping logic ở `AnalyticsService`:
  - sessions page
  - session messages + parse sources safe
  - rating filter behavior an toàn khi chưa có data source rating.

## 6. Danh sách file đã đọc
- `.cursor/rules/*.mdc`: lấy quy tắc scope/minimal diff/report.
- `reports/CURSOR_REPORT_04B_BACKEND_PLAYGROUND_SESSIONS_COMPARE_EXPORT_CANONICAL_API.md`: xác nhận id session FE dùng `sessionKey`.
- `reports/CURSOR_REPORT_06A_BACKEND_ANALYTICS_USAGE_CANONICAL_API.md`: giữ nguyên usage APIs đã PASS.
- `Frontend/src/api/analyticsApi.js`: xác nhận contract sessions endpoint/query.
- `Frontend/src/mocks/analyticsMock.js`: xác nhận shape sessions item/messages.
- `Frontend/src/pages/analytics/AnalyticsPage.jsx`: hiểu filter được truyền xuống sessions tab.
- `Frontend/src/pages/analytics/components/AnalyticsSessionsTab.jsx`: xác nhận pagination shape expected.
- `Frontend/src/pages/analytics/components/SessionsTable.jsx`: xác nhận field list hiển thị.
- `Frontend/src/pages/analytics/components/SessionsPagination.jsx`: xác nhận page indexing.
- `Frontend/src/pages/analytics/components/SessionDetailDrawer.jsx`: xác nhận field message/source FE consume.
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/api/AnalyticsController.java`: điểm vào API analytics.
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/AnalyticsService.java`: service analytics usage hiện tại.
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/domain/chat/ChatSessionRepository.java`: điểm mở rộng query sessions.
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/domain/chat/ChatMessageRepository.java`: reuse query message/session count.

## 7. Danh sách file đã sửa
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/api/AnalyticsController.java`
  - Mục đích: thêm 2 endpoints sessions + validation page/size/session id.
  - Ảnh hưởng lớp: api.
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/AnalyticsService.java`
  - Mục đích: thêm business logic sessions list/detail, mapping DTO, safe rating filter.
  - Ảnh hưởng lớp: service.
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/domain/chat/ChatSessionRepository.java`
  - Mục đích: query analytics-safe với join/filter soft-deleted.
  - Ảnh hưởng lớp: db.
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/dto/AnalyticsSessionItem.java`
  - Mục đích: DTO row sessions.
  - Ảnh hưởng lớp: api.
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/dto/AnalyticsSessionPageResponse.java`
  - Mục đích: DTO pagination payload.
  - Ảnh hưởng lớp: api.
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/dto/AnalyticsSessionMessageResponse.java`
  - Mục đích: DTO detail message.
  - Ảnh hưởng lớp: api.
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/dto/AnalyticsSessionSourceResponse.java`
  - Mục đích: DTO source trong drawer.
  - Ảnh hưởng lớp: api.
- `reports/CURSOR_REPORT_06B_BACKEND_ANALYTICS_SESSIONS_CANONICAL_API.md`
  - Mục đích: report prompt yêu cầu.
  - Ảnh hưởng lớp: docs.
- `docs/CURSOR_REPORT_06B_BACKEND_ANALYTICS_SESSIONS_CANONICAL_API.md`
  - Mục đích: report verification theo rule 90.
  - Ảnh hưởng lớp: docs.

## 8. Diff thay đổi của từng file

### `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/api/AnalyticsController.java`
- Hiện trạng cũ: chưa có sessions endpoints.
- Đã sửa: thêm `/sessions` và `/sessions/{id}/messages`; thêm parse page/size/session id.
- Vì sao: khớp FE contract + error JSON chuẩn.
- Ảnh hưởng: FE sessions tab gọi trực tiếp được.
```diff
+ @GetMapping("/sessions")
+ public ResponseEntity<?> getSessions(...)
+ @GetMapping("/sessions/{id}/messages")
+ public ResponseEntity<?> getSessionMessages(...)
+ private Integer parsePage(String pageRaw) { ... }
+ private Integer parseSize(String sizeRaw) { ... }
```

### `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/AnalyticsService.java`
- Hiện trạng cũ: chỉ có usage methods.
- Đã sửa: thêm `getSessions`, `getSessionMessages`, mapping sources helpers.
- Vì sao: gom business logic analytics sessions trong service.
- Ảnh hưởng: endpoint trả đúng shape FE; tránh crash khi sources null.
```diff
+ public AnalyticsSessionPageResponse getSessions(...)
+ public List<AnalyticsSessionMessageResponse> getSessionMessages(UUID sessionKey)
+ private List<AnalyticsSessionSourceResponse> mapSources(ChatMessage message)
+ private String asString(Object value)
+ private Double asDouble(Object value)
```

### `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/domain/chat/ChatSessionRepository.java`
- Hiện trạng cũ: chưa có query analytics-safe.
- Đã sửa: thêm `findForAnalyticsRange` với `JOIN FETCH s.widgetConfig w` và filter `deletedAt`.
- Vì sao: fix runtime 500 do orphan/deleted widget.
- Ảnh hưởng: sessions list all-chatbot không còn `EntityNotFoundException`.
```diff
+ @Query(value = "... JOIN FETCH s.widgetConfig w ...", countQuery = "...")
+ Page<ChatSession> findForAnalyticsRange(...);
```

### `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/dto/AnalyticsSessionItem.java`
- Hiện trạng cũ: chưa có DTO row sessions.
- Đã sửa: tạo DTO fields `id/chatbotId/chatbotName/messageCount/rating/createdAt`.
- Vì sao: giữ response contract ổn định.
- Ảnh hưởng: api.
```diff
+ public class AnalyticsSessionItem { ... }
```

### `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/dto/AnalyticsSessionPageResponse.java`
- Hiện trạng cũ: chưa có DTO page.
- Đã sửa: tạo DTO `items/page/size/total/totalPages`.
- Vì sao: khớp FE normalize/pagination.
- Ảnh hưởng: api.
```diff
+ public class AnalyticsSessionPageResponse { ... }
```

### `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/dto/AnalyticsSessionMessageResponse.java`
- Hiện trạng cũ: chưa có DTO message detail.
- Đã sửa: tạo DTO `id/role/content/createdAt/sources`.
- Vì sao: khớp drawer conversation replay.
- Ảnh hưởng: api.
```diff
+ public class AnalyticsSessionMessageResponse { ... }
```

### `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/dto/AnalyticsSessionSourceResponse.java`
- Hiện trạng cũ: chưa có DTO source detail.
- Đã sửa: tạo DTO source với fields FE SourcePill có thể đọc.
- Vì sao: tránh trả raw map khó kiểm soát.
- Ảnh hưởng: api.
```diff
+ public class AnalyticsSessionSourceResponse { ... }
```

### `reports/CURSOR_REPORT_06B_BACKEND_ANALYTICS_SESSIONS_CANONICAL_API.md`
```diff
+ Added full prompt-required report for 06B.
```

### `docs/CURSOR_REPORT_06B_BACKEND_ANALYTICS_SESSIONS_CANONICAL_API.md`
```diff
+ Added verification report (rule 90 format) for 06B.
```

## 9. Ảnh hưởng sau sửa
- Behavior thay đổi:
  - Có thêm 2 analytics sessions endpoints hoạt động.
  - Sessions list trả pageable object chuẩn FE.
  - Session detail trả full conversation theo thứ tự thời gian.
- Behavior giữ nguyên:
  - Analytics usage endpoints cũ không đổi contract.
  - Không thay đổi auth model/schema.
- Điều kiện kích hoạt:
  - Rating filter meaningful chỉ khi có data source rating (hiện chưa có).
- Fallback giữ:
  - `rating` mặc định null; positive/negative trả rỗng an toàn.
- Tài nguyên:
  - CPU/memory tăng nhẹ do count message per session trong page (size mặc định 10).
- Latency/API cost:
  - Không gọi LLM/provider trong 2 endpoint mới.
- Dữ liệu MySQL/Qdrant cũ:
  - Không migration; chỉ read queries.

## 10. Edge cases đã xem xét
- `from`/`to` invalid format.
- `from > to`.
- `chatbotId` invalid UUID.
- `page` âm hoặc không parse được.
- `size` ngoài range.
- `session id` invalid UUID.
- `session id` hợp lệ nhưng không tồn tại.
- Session có sources rỗng/null.
- Session linked widget soft-deleted/orphan gây lazy-load exception (đã fix bằng query join/filter).

## 11. Kết quả kiểm tra
| Command | Kết quả | Ghi chú |
|---|---|---|
| `cd Backend && ./mvnw -DskipTests compile` | PASS | Compile pass sau code change |
| `cd Backend && ./mvnw test` | PASS | 14 tests pass |
| `cd Frontend && npm run lint` | NOT RUN | Ngoài scope prompt backend |
| `cd Frontend && npm run build` | NOT RUN | Ngoài scope prompt backend |
| `cd Frontend && npm run build:widget` | NOT RUN | Ngoài scope prompt backend |
| `docker compose config` | NOT RUN | Không đổi docker compose |

Manual smoke test:
- PASS: sessions list / filter chatbot / unrated / positive / negative.
- PASS: invalid date -> 400 message.
- PASS: session messages -> 200 với sources.
- PASS: invalid session id -> 400 message.
- PASS: not found session id -> 404 message.

## 12. Rủi ro còn lại
- Chưa có rating source thật nên positive/negative luôn rỗng.
- Nếu cần scale lớn, `messageCount` theo từng session đang count riêng từng row (N query theo page size).

## 13. Đề xuất tiếp theo
- Prompt tiếp theo nên làm Analytics Feedback canonical API để có nguồn rating thật, sau đó update rating filter behavior sang data-driven.
