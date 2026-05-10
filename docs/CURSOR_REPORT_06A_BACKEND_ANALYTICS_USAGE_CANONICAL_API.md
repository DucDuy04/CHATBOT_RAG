# CURSOR REPORT 06A BACKEND ANALYTICS USAGE CANONICAL API

## 1. Mức độ hiểu task
- Hiểu task: 98%.
- Phần chắc chắn:
  - Cần triển khai đúng 4 endpoint Usage:
    - `GET /api/analytics/summary`
    - `GET /api/analytics/daily`
    - `GET /api/analytics/by-chatbot`
    - `GET /api/analytics/unanswered`
  - FE contract lấy từ `Frontend/src/api/analyticsApi.js`, `Frontend/src/mocks/analyticsMock.js`, và Usage components.
  - Không làm sessions tab / feedback tab / settings / dashboard.
- Phần còn giả định:
  - `avgSatisfaction` và `fallbackRate` chưa có data source schema thật.
  - `unanswered` chỉ có thể derive heuristic từ content.
- Thiếu dữ kiện:
  - Không có bảng feedback/rating chính thức.
  - Không có cờ fallback/unanswered trong `chat_messages`.

## 2. Tóm tắt yêu cầu
- Triển khai Analytics Usage APIs theo FE canonical contract, gồm query validation và error JSON `message`.
- Match response shape FE đang consume:
  - Summary metrics + delta.
  - Daily chart data with messages/sessions.
  - Share by chatbot.
  - Unanswered list.
- Không sửa FE, không đổi schema, không thêm feature ngoài scope.

## 3. Hiện trạng trước khi sửa
- Backend chưa có `AnalyticsController` / `AnalyticsService`.
- FE usage gọi `/api/analytics/*` sẽ trả 404.
- Data model có thể support aggregate:
  - `ChatMessage.createdAt`, `ChatMessage.role`
  - `ChatMessage -> ChatSession -> WidgetConfig`
- Nhưng chưa có:
  - feedback table (satisfaction source)
  - fallback/unanswered flag chính thức.

## 4. Nguyên nhân gốc xác nhận từ source
- Root cause: thiếu implementation backend cho nhóm endpoint Analytics Usage.
- Xác nhận từ source:
  - `Frontend/src/api/analyticsApi.js` gọi 4 endpoint usage.
  - `Backend/src/main/java/.../api` chưa có analytics controller.
  - Runtime trước sửa trả 404 cho `/api/analytics/*`.

## 5. Chiến lược sửa đã chọn
- Tạo mới:
  - `AnalyticsController`: validate query/date/UUID/limit và trả lỗi 400 JSON message.
  - `AnalyticsService`: aggregate read-only từ `ChatMessage`.
  - DTO riêng cho từng response shape để không lộ JPA entity.
- Mở rộng repository tối thiểu:
  - Thêm query range + optional widget filter bằng JPQL `join fetch`.
- Không dùng native SQL phức tạp:
  - Daily/by-chatbot/unanswered aggregate bằng Java vì scope admin analytics nhỏ và đảm bảo dễ kiểm soát zero-day/heuristic.
- Safe defaults:
  - `avgSatisfaction` = `null`
  - `fallbackRate` = `0.0`.

## 6. Danh sách file đã đọc
| Path | Đọc để làm gì | Kết luận chính |
|---|---|---|
| `.cursor/rules/00-core-working-rule.mdc` | Core constraints | Sửa minimal diff, không out-of-scope |
| `.cursor/rules/10-backend-rag-rule.mdc` | Backend principles | Ưu tiên query/service nhỏ, compile/test bắt buộc |
| `.cursor/rules/20-frontend-widget-rule.mdc` | FE contract consume | FE field names là source of truth |
| `.cursor/rules/30-deploy-env-ops-rule.mdc` | Runtime/ops | Cần verify runtime thật, không claim sai |
| `.cursor/rules/40-db-vector-rule.mdc` | DB/vector safety | Không đổi schema, read-only query |
| `.cursor/rules/90-report-verification-rule.mdc` | Report format bắt buộc | Cần report chi tiết + trung thực |
| `agent.md` | Điều hướng tài liệu kiến trúc | Xác nhận module liên quan |
| `agent/01-overview.md` | Tổng quan hệ thống | Multi-tenant root là `WidgetConfig` |
| `agent/02-architecture.md` | Data flow/schema thực tế | Analytics có thể dựa vào `chat_messages` |
| `reports/CURSOR_REPORT_00_BACKEND_FLOW_AND_FE_CONTRACT_AUDIT.md` | Baseline endpoint status | Analytics từng missing |
| `reports/CURSOR_REPORT_04B_BACKEND_PLAYGROUND_SESSIONS_COMPARE_EXPORT_CANONICAL_API.md` | Baseline chat/session | Message/session link sẵn sàng |
| `reports/CURSOR_REPORT_05A_BACKEND_DASHBOARD_CANONICAL_API.md` | Pattern aggregate gần nhất | Reuse style validate + DTO |
| `Frontend/src/api/analyticsApi.js` | Contract endpoint/query | 4 usage APIs cần triển khai |
| `Frontend/src/mocks/analyticsMock.js` | Shape dữ liệu | field names chính xác cần match |
| `Frontend/src/pages/analytics/AnalyticsPage.jsx` | FE consumption/export | CSV export dùng nhiều field usage |
| `Frontend/src/pages/analytics/components/AnalyticsMetricCard.jsx` | Summary field usage | cần 4 metric + delta |
| `Frontend/src/pages/analytics/components/DailyBarChart.jsx` | Daily field usage | cần `date/messages/sessions` |
| `Frontend/src/pages/analytics/components/ChatbotShareBars.jsx` | By-chatbot field usage | cần `chatbotId/chatbotName/messageCount/share` |
| `Frontend/src/pages/analytics/components/UnansweredTable.jsx` | Unanswered field usage | cần `id/question/chatbotName/count` |
| `Frontend/src/pages/analytics/components/DateRangePicker.jsx` | Date behavior FE | from/to dạng `YYYY-MM-DD` |
| `Backend/src/main/java/.../domain/chat/ChatMessage.java` | Source analytics | có role/content/createdAt/session |
| `Backend/src/main/java/.../domain/chat/ChatSession.java` | Session scope | session thuộc widget |
| `Backend/src/main/java/.../domain/widget/WidgetConfig.java` | Chatbot metadata | chatbot id/name lấy từ widget |
| `Backend/src/main/java/.../domain/chat/ChatMessageRepository.java` | Query points | mở rộng query analytics |
| `Backend/src/main/resources/application.yml` | profile default | active dev |
| `Backend/src/main/resources/application-dev.yml` | runtime env local | MySQL/Qdrant local |
| `Backend/src/main/resources/application-docker.yml` | docker env | tương thích runtime docker |

## 7. Danh sách file đã sửa
| Path | Sửa để làm gì | Ảnh hưởng lớp |
|---|---|---|
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/api/AnalyticsController.java` | Add 4 analytics usage endpoints + param validation + 400 JSON message | api |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/AnalyticsService.java` | Add summary/daily/by-chatbot/unanswered aggregation logic | service |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/domain/chat/ChatMessageRepository.java` | Add JPQL range query for analytics with optional widget filter | db |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/dto/AnalyticsSummaryResponse.java` | Define summary response contract | api |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/dto/AnalyticsDailyItem.java` | Define daily response item | api |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/dto/AnalyticsByChatbotItem.java` | Define by-chatbot response item | api |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/dto/AnalyticsUnansweredItem.java` | Define unanswered response item | api |
| `reports/CURSOR_REPORT_06A_BACKEND_ANALYTICS_USAGE_CANONICAL_API.md` | Prompt-required execution report | docs |
| `docs/CURSOR_REPORT_06A_BACKEND_ANALYTICS_USAGE_CANONICAL_API.md` | Workspace-required verification report | docs |

## 8. Diff thay đổi của từng file

### File: `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/api/AnalyticsController.java`
- Hiện trạng cũ liên quan bug:
  - Không có analytics usage endpoints.
- Đã sửa gì:
  - Thêm controller với 4 endpoints usage.
  - Validate `from/to/chatbotId/limit` và trả message JSON thống nhất.
- Vì sao sửa như vậy:
  - Match FE contract + requirement error handling.
- Ảnh hưởng sau sửa:
  - FE gọi usage APIs không còn 404; invalid params không văng 500.
```diff
+@RestController
+@RequestMapping("/api/analytics")
+public class AnalyticsController {
+  @GetMapping("/summary")
+  @GetMapping("/daily")
+  @GetMapping("/by-chatbot")
+  @GetMapping("/unanswered")
+}
```

### File: `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/AnalyticsService.java`
- Hiện trạng cũ liên quan bug:
  - Không có service aggregate analytics usage.
- Đã sửa gì:
  - Thêm logic:
    - summary + delta theo previous period
    - daily with zero-fill
    - by-chatbot share %
    - unanswered heuristic từ cặp USER->ASSISTANT.
- Vì sao sửa như vậy:
  - Reuse schema hiện tại, không cần migration.
- Ảnh hưởng sau sửa:
  - Backend trả analytics usage data thật từ chat history.
```diff
+public AnalyticsSummaryResponse getSummary(...)
+public List<AnalyticsDailyItem> getDaily(...)
+public List<AnalyticsByChatbotItem> getByChatbot(...)
+public List<AnalyticsUnansweredItem> getUnanswered(int limit)
```

### File: `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/domain/chat/ChatMessageRepository.java`
- Hiện trạng cũ liên quan bug:
  - Chưa có query theo date range + widget cho analytics usage.
- Đã sửa gì:
  - Thêm `findForAnalyticsRange(from,to,widgetId)` với join fetch.
- Vì sao sửa như vậy:
  - Tránh lazy load/N+1 khi aggregate theo session/widget.
- Ảnh hưởng sau sửa:
  - Service analytics query read-only đầy đủ.
```diff
+@Query("SELECT m FROM ChatMessage m JOIN FETCH m.session s JOIN FETCH s.widgetConfig w ...")
+List<ChatMessage> findForAnalyticsRange(LocalDateTime from, LocalDateTime to, UUID widgetId);
```

### File: `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/dto/AnalyticsSummaryResponse.java`
- Hiện trạng cũ liên quan bug:
  - Chưa có DTO summary.
- Đã sửa gì:
  - Tạo DTO đúng shape FE summary usage.
- Vì sao sửa như vậy:
  - Tránh trả map/entity tự do.
- Ảnh hưởng sau sửa:
  - JSON summary ổn định và FE-friendly.
```diff
+public class AnalyticsSummaryResponse {
+  Long totalMessages;
+  Double totalMessagesDelta;
+  Long uniqueSessions;
+  Double uniqueSessionsDelta;
+  Double avgSatisfaction;
+  Double avgSatisfactionDelta;
+  Double fallbackRate;
+  Double fallbackRateDelta;
+}
```

### File: `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/dto/AnalyticsDailyItem.java`
- Hiện trạng cũ liên quan bug:
  - Chưa có DTO daily.
- Đã sửa gì:
  - Tạo DTO `date/messages/sessions`.
- Vì sao sửa như vậy:
  - Match `DailyBarChart` + CSV export FE.
- Ảnh hưởng sau sửa:
  - FE render chart theo expected fields.
```diff
+public class AnalyticsDailyItem {
+  String date;
+  Long messages;
+  Long sessions;
+}
```

### File: `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/dto/AnalyticsByChatbotItem.java`
- Hiện trạng cũ liên quan bug:
  - Chưa có DTO by-chatbot.
- Đã sửa gì:
  - Tạo DTO `chatbotId/chatbotName/messageCount/share`.
- Vì sao sửa như vậy:
  - Match `ChatbotShareBars` FE.
- Ảnh hưởng sau sửa:
  - FE render share bars đúng.
```diff
+public class AnalyticsByChatbotItem {
+  String chatbotId;
+  String chatbotName;
+  Long messageCount;
+  Double share;
+}
```

### File: `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/dto/AnalyticsUnansweredItem.java`
- Hiện trạng cũ liên quan bug:
  - Chưa có DTO unanswered.
- Đã sửa gì:
  - Tạo DTO `id/question/chatbotName/count`.
- Vì sao sửa như vậy:
  - Match `UnansweredTable`.
- Ảnh hưởng sau sửa:
  - FE table consume trực tiếp.
```diff
+public class AnalyticsUnansweredItem {
+  String id;
+  String question;
+  String chatbotName;
+  Long count;
+}
```

### File: `reports/CURSOR_REPORT_06A_BACKEND_ANALYTICS_USAGE_CANONICAL_API.md`
- Hiện trạng cũ liên quan bug:
  - Chưa có report prompt 06A.
- Đã sửa gì:
  - Thêm report execution theo format prompt.
- Vì sao sửa như vậy:
  - Đáp ứng output bắt buộc user prompt.
- Ảnh hưởng sau sửa:
  - Có tài liệu audit kết quả 06A.
```diff
+# Cursor Report 06A - BACKEND_ANALYTICS_USAGE_CANONICAL_API
+## 1. Mức độ hiểu task
+...
```

### File: `docs/CURSOR_REPORT_06A_BACKEND_ANALYTICS_USAGE_CANONICAL_API.md`
- Hiện trạng cũ liên quan bug:
  - Chưa có docs report theo workspace rule.
- Đã sửa gì:
  - Thêm report verification đầy đủ 13 mục.
- Vì sao sửa như vậy:
  - Tuân thủ rule 90 always-apply.
- Ảnh hưởng sau sửa:
  - Reviewer có tài liệu kiểm chứng chi tiết trong `docs/`.
```diff
+# CURSOR REPORT 06A BACKEND ANALYTICS USAGE CANONICAL API
+## 1. Mức độ hiểu task
+...
```

## 9. Ảnh hưởng sau sửa
- Behavior thay đổi:
  - Có đủ 4 endpoint Analytics Usage canonical.
  - FE usage tab có thể lấy dữ liệu thật thay vì mock.
  - Invalid query/date/limit trả JSON message 400 thay vì lỗi chung.
- Behavior giữ nguyên:
  - Core RAG flow không đổi.
  - Sessions/feedback analytics endpoints chưa triển khai (đúng scope).
- Chỉ bật khi đủ điều kiện:
  - Analytics values phụ thuộc dữ liệu chat hiện có trong DB.
- Fallback giữ:
  - Satisfaction và fallback rate dùng safe default khi chưa có source thật.
- Ảnh hưởng memory/cpu/disk:
  - Tăng nhẹ read query/aggregation trên chat_messages.
  - `unanswered` giới hạn 500 messages gần nhất để tránh tốn tài nguyên.
- Ảnh hưởng latency/token/API cost:
  - Không gọi thêm LLM/external provider, chỉ đọc MySQL.
- Ảnh hưởng dữ liệu MySQL/Qdrant cũ:
  - Không schema migration, không mutate dữ liệu cũ, không đụng Qdrant data.

## 10. Edge cases đã xem xét
- `from`/`to` thiếu hoặc sai format -> 400 message.
- `from > to` -> 400 message.
- `chatbotId` invalid UUID -> 400 message.
- `limit` không phải số hoặc ngoài `1..100` -> 400 message.
- Range có ngày không có dữ liệu -> daily vẫn trả đủ ngày count=0.
- DB rỗng -> summary zero/null-safe, by-chatbot/unanswered trả `[]`.
- Assistant text không chứa heuristic fallback -> unanswered `[]` (không crash).

## 11. Kết quả kiểm tra
| Command | Kết quả | Ghi chú |
|---|---|---|
| `cd Backend && ./mvnw -DskipTests compile` | PASS | Build success sau implement analytics |
| `cd Backend && ./mvnw test` | PASS | 14 tests pass |
| `GET /api/analytics/summary?from=2026-05-01&to=2026-05-31` | PASS | 200 + đủ fields summary |
| `GET /api/analytics/summary?...&chatbotId=cd14c04d-dc66-4169-8341-648d4397ea6a` | PASS | 200 + filtered |
| `GET /api/analytics/summary?from=bad&to=2026-05-31` | PASS | 400 + exact invalid date message |
| `GET /api/analytics/daily?from=2026-05-01&to=2026-05-07` | PASS | 200 + 7 ngày |
| `GET /api/analytics/daily?...&chatbotId=cd14...` | PASS | 200 + filtered |
| `GET /api/analytics/by-chatbot?from=2026-05-01&to=2026-05-31` | PASS | 200 + sorted + share |
| `GET /api/analytics/unanswered?limit=10` | PASS | 200 + `[]` với data hiện tại |
| `GET /api/analytics/unanswered?limit=abc` | PASS | 400 + limit message |
| `cd Frontend && npm run lint` | NOT RUN | Ngoài scope backend 06A |
| `cd Frontend && npm run build` | NOT RUN | Ngoài scope backend 06A |
| `cd Frontend && npm run build:widget` | NOT RUN | Ngoài scope backend 06A |
| `docker compose config` | NOT RUN | Không thay đổi deploy/config |

## 12. Rủi ro còn lại
- `avgSatisfaction` vẫn `null` vì chưa có feedback source.
- `fallbackRate` vẫn `0.0` vì chưa có fallback tracking.
- `unanswered` dựa heuristic text, chưa phải signal chuẩn từ model/runtime.

## 13. Đề xuất tiếp theo
- Prompt kế tiếp phù hợp: `06B_BACKEND_ANALYTICS_SESSIONS_TAB_CANONICAL_API` để hoàn thiện tab Sessions.
- Sau đó có thể làm prompt feedback endpoint/source để thay safe defaults bằng dữ liệu thật.
