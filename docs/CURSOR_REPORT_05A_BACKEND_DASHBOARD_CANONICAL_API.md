# CURSOR REPORT 05A BACKEND DASHBOARD CANONICAL API

## 1. Mức độ hiểu task
- Hiểu task: 98%.
- Chắc chắn:
  - Cần implement đúng 4 endpoint dashboard theo FE contract.
  - Không sửa FE/mock, không đổi schema DB, không làm analytics/settings.
- Còn giả định:
  - `avgSatisfaction` chưa có data source thật trong DB hiện tại.
  - Activity phải derive từ dữ liệu có sẵn.
- Thiếu dữ kiện:
  - Không có bảng feedback/rating và không có event log table chuẩn audit.

## 2. Tóm tắt yêu cầu
- Triển khai:
  - `GET /api/dashboard/summary`
  - `GET /api/dashboard/message-volume?days=7`
  - `GET /api/dashboard/top-chatbots?limit=5`
  - `GET /api/dashboard/activity?limit=20`
- Match FE contract trong:
  - `Frontend/src/api/dashboardApi.js`
  - `Frontend/src/mocks/dashboardMock.js`
  - `Frontend/src/pages/dashboard/*`
- Bắt buộc:
  - Query invalid -> HTTP 400 JSON `{ "message": "..." }`
  - Compile pass, test pass nếu môi trường cho phép, có runtime smoke test.

## 3. Hiện trạng trước khi sửa
- Backend chưa có controller/service dashboard.
- FE dashboard đang gọi 4 endpoint canonical nhưng backend trả 404.
- Không có event log system riêng; chỉ có dữ liệu widget/document/chat.

## 4. Nguyên nhân gốc xác nhận từ source
- Root cause: thiếu implementation cho namespace `/api/dashboard/*`.
- Xác nhận qua:
  - `Frontend/src/api/dashboardApi.js`: FE gọi thật 4 endpoint.
  - Backend `api/` không có `DashboardController`.
  - Runtime call trước sửa trả 404 toàn bộ dashboard routes.

## 5. Chiến lược sửa đã chọn
- Tạo mới:
  - `DashboardController` để expose API và validate query params.
  - `DashboardService` để aggregate read-only từ repository hiện có.
  - DTO dashboard để trả đúng shape FE.
- Mở rộng repository tối thiểu:
  - Method count/range/top/latest cho widget/document/message.
- Activity strategy:
  - Derive từ chat messages + documents + widgets (không tạo event logging system lớn).
- Bugfix runtime:
  - Tránh lazy-load crash với dữ liệu orphan bằng query `findLatestForDashboard(Pageable)` join fetch và lọc deleted.

## 6. Danh sách file đã đọc
| Path | Đọc để làm gì | Kết luận chính |
|---|---|---|
| `.cursor/rules/00-core-working-rule.mdc` | Nắm nguyên tắc core | Sửa minimal diff, không out-of-scope |
| `.cursor/rules/90-report-verification-rule.mdc` | Nắm yêu cầu report | Bắt buộc tạo report chi tiết |
| `reports/CURSOR_REPORT_00_BACKEND_FLOW_AND_FE_CONTRACT_AUDIT.md` | Baseline contract | Dashboard còn missing |
| `reports/CURSOR_REPORT_02B_RERUN_BACKEND_CHATBOTS_API_RUNTIME_SMOKE_TEST.md` | Baseline runtime | Backend stack/runtime đã dùng ổn |
| `reports/CURSOR_REPORT_03C_BACKEND_DOCUMENTS_RUNTIME_SMOKE_TEST.md` | Baseline documents | Có data source document |
| `reports/CURSOR_REPORT_04A_BACKEND_PUBLIC_AND_PLAYGROUND_CHAT_RUNTIME_SMOKE_TEST.md` | Baseline chat | Có data source messages |
| `reports/CURSOR_REPORT_04B_BACKEND_PLAYGROUND_SESSIONS_COMPARE_EXPORT_CANONICAL_API.md` | Baseline recent backend | Chat/session model đang active |
| `Frontend/src/api/dashboardApi.js` | API contract source of truth | Path/query/return chính xác cần match |
| `Frontend/src/mocks/dashboardMock.js` | Response shape | Tên field summary + arrays |
| `Frontend/src/pages/dashboard/DashboardPage.jsx` | FE consume logic | Summary dùng delta; các khối khác nhận array |
| `Frontend/src/pages/dashboard/components/MetricCard.jsx` | Delta format | Delta expected là số phần trăm |
| `Frontend/src/pages/dashboard/components/MessageVolumeChart.jsx` | Chart data shape | `{date,count}`, hiển thị đủ ngày |
| `Frontend/src/pages/dashboard/components/TopChatbotsList.jsx` | Top list shape | `id,name,messageCount,domain?,satisfaction?` |
| `Frontend/src/pages/dashboard/components/ActivityTable.jsx` | Activity shape | `id,type,actor,target,createdAt` |
| `Backend/src/main/java/.../domain/widget/WidgetConfig.java` | Data model chatbot | Có `isActive`, soft delete |
| `Backend/src/main/java/.../domain/widget/WidgetConfigRepository.java` | Repo chatbot | Có thể thêm count/latest methods |
| `Backend/src/main/java/.../domain/document/Document.java` | Data model doc | Có status + timestamp |
| `Backend/src/main/java/.../domain/document/DocumentRepository.java` | Repo doc | Có thể thêm latest method |
| `Backend/src/main/java/.../domain/chat/ChatSession.java` | Data model session | session gắn widget |
| `Backend/src/main/java/.../domain/chat/ChatMessage.java` | Data model message | createdAt/sources/role đủ cho aggregate |
| `Backend/src/main/java/.../domain/chat/ChatSessionRepository.java` | Cross-check scope | Không cần thay đổi cho 05A |
| `Backend/src/main/java/.../domain/chat/ChatMessageRepository.java` | Repo message | Cần thêm count/group/latest dashboard |
| `Backend/src/main/java/.../domain/enums/DocumentStatus.java` | Mapping activity doc | map status -> activity type |

## 7. Danh sách file đã sửa
| Path | Sửa để làm gì | Ảnh hưởng lớp |
|---|---|---|
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/api/DashboardController.java` | Expose 4 endpoint dashboard + validate query/error JSON | api |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/DashboardService.java` | Aggregation summary/volume/top/activity | service |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/domain/chat/ChatMessageRepository.java` | Query count/range/top/latest-activity | db |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/domain/document/DocumentRepository.java` | Query latest documents for activity | db |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/domain/widget/WidgetConfigRepository.java` | Query active count/latest widgets | db |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/dto/DashboardSummaryResponse.java` | DTO summary | api |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/dto/DashboardMessageVolumeItem.java` | DTO volume item | api |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/dto/DashboardTopChatbotItem.java` | DTO top chatbot item | api |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/dto/DashboardActivityItem.java` | DTO activity item | api |

## 8. Diff thay đổi của từng file

### File: `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/api/DashboardController.java`
- Hiện trạng cũ liên quan bug:
  - Không có controller dashboard -> FE gọi 404.
- Đã sửa gì:
  - Thêm controller và 4 route dashboard.
  - Parse query params thủ công để trả 400 có `message`.
- Vì sao sửa như vậy:
  - Match FE contract và error handling requirement.
- Ảnh hưởng sau sửa:
  - Dashboard endpoints available + predictable 400 JSON errors.
```diff
+@RestController
+@RequestMapping("/api/dashboard")
+public class DashboardController {
+  @GetMapping("/summary")
+  @GetMapping("/message-volume")
+  @GetMapping("/top-chatbots")
+  @GetMapping("/activity")
+}
```

### File: `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/DashboardService.java`
- Hiện trạng cũ liên quan bug:
  - Không có business aggregation cho dashboard.
- Đã sửa gì:
  - Implement `getSummary`, `getMessageVolume`, `getTopChatbots`, `getActivity`.
  - Add delta calculator và map domain/chatbot name helper.
- Vì sao sửa như vậy:
  - Tách controller mỏng, tập trung logic aggregate tại service.
- Ảnh hưởng sau sửa:
  - FE dashboard nhận data từ DB thật (không cần mock ở backend).
```diff
+public DashboardSummaryResponse getSummary() { ... }
+public List<DashboardMessageVolumeItem> getMessageVolume(int days) { ... }
+public List<DashboardTopChatbotItem> getTopChatbots(int limit) { ... }
+public List<DashboardActivityItem> getActivity(int limit) { ... }
```

### File: `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/domain/chat/ChatMessageRepository.java`
- Hiện trạng cũ liên quan bug:
  - Thiếu method aggregate theo khoảng ngày/top chatbot/latest dashboard.
  - Activity từng bị 500 do lazy-load session orphan.
- Đã sửa gì:
  - Thêm count/range methods.
  - Thêm query `findTopChatbotMessageCounts(Pageable)`.
  - Thêm `findLatestForDashboard(Pageable)` join fetch + lọc deleted để tránh lazy-load lỗi.
- Vì sao sửa như vậy:
  - Hỗ trợ đầy đủ metrics dashboard và fix bug runtime activity.
- Ảnh hưởng sau sửa:
  - Activity endpoint ổn định hơn với dữ liệu soft-delete không đồng bộ.
```diff
+long countByCreatedAtBetween(LocalDateTime from, LocalDateTime to);
+List<ChatMessage> findByCreatedAtBetweenOrderByCreatedAtAsc(...);
+List<ChatMessage> findLatestForDashboard(Pageable pageable);
+List<Object[]> findTopChatbotMessageCounts(Pageable pageable);
```

### File: `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/domain/document/DocumentRepository.java`
- Hiện trạng cũ liên quan bug:
  - Chưa có method lấy latest documents cho activity.
- Đã sửa gì:
  - Thêm `findTop50ByOrderByUpdatedAtDesc()`.
- Vì sao sửa như vậy:
  - Derive activity documents nhanh, minimal diff.
- Ảnh hưởng sau sửa:
  - Activity có thêm các event document gần nhất.
```diff
+List<Document> findTop50ByOrderByUpdatedAtDesc();
```

### File: `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/domain/widget/WidgetConfigRepository.java`
- Hiện trạng cũ liên quan bug:
  - Thiếu active count/latest widgets cho summary/activity.
- Đã sửa gì:
  - Thêm `countByIsActiveTrue()`.
  - Thêm `findTop50ByOrderByUpdatedAtDesc()`.
- Vì sao sửa như vậy:
  - Cần số liệu active chatbots + chatbot activity.
- Ảnh hưởng sau sửa:
  - Summary và activity có nguồn widget trực tiếp.
```diff
+long countByIsActiveTrue();
+List<WidgetConfig> findTop50ByOrderByUpdatedAtDesc();
```

### File: `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/dto/DashboardSummaryResponse.java`
- Hiện trạng cũ liên quan bug:
  - Chưa có DTO summary.
- Đã sửa gì:
  - Tạo DTO fields đúng FE summary shape.
- Vì sao sửa như vậy:
  - Không trả entity trực tiếp, giữ contract ổn định.
- Ảnh hưởng sau sửa:
  - JSON summary đúng field FE.
```diff
+public class DashboardSummaryResponse {
+  Long activeChatbots;
+  Double activeChatbotsDelta;
+  Long messages7d;
+  Double messages7dDelta;
+  Double avgSatisfaction;
+  Double avgSatisfactionDelta;
+  Long documentCount;
+  Double documentCountDelta;
+}
```

### File: `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/dto/DashboardMessageVolumeItem.java`
- Hiện trạng cũ liên quan bug:
  - Chưa có DTO volume item.
- Đã sửa gì:
  - Tạo DTO `{date,count}`.
- Vì sao sửa như vậy:
  - Match chart data FE.
- Ảnh hưởng sau sửa:
  - Chart render được với dữ liệu API thật.
```diff
+public class DashboardMessageVolumeItem {
+  String date;
+  Long count;
+}
```

### File: `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/dto/DashboardTopChatbotItem.java`
- Hiện trạng cũ liên quan bug:
  - Chưa có DTO top chatbot.
- Đã sửa gì:
  - Tạo DTO gồm `id,name,messageCount,satisfaction,domain,status`.
- Vì sao sửa như vậy:
  - Match list FE + dư field optional an toàn.
- Ảnh hưởng sau sửa:
  - Top chatbots hiển thị được theo message count.
```diff
+public class DashboardTopChatbotItem {
+  String id;
+  String name;
+  Long messageCount;
+  Double satisfaction;
+  String domain;
+  String status;
+}
```

### File: `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/dto/DashboardActivityItem.java`
- Hiện trạng cũ liên quan bug:
  - Chưa có DTO activity item.
- Đã sửa gì:
  - Tạo DTO `id,type,actor,target,createdAt`.
- Vì sao sửa như vậy:
  - Khớp `ActivityTable` FE.
- Ảnh hưởng sau sửa:
  - Activity table có đủ field để render.
```diff
+public class DashboardActivityItem {
+  String id;
+  String type;
+  String actor;
+  String target;
+  String createdAt;
+}
```

## 9. Ảnh hưởng sau sửa
- Behavior thay đổi:
  - Backend có đủ 4 dashboard endpoint canonical.
  - Query invalid (`days/limit`) trả 400 JSON có `message`.
  - Activity trả dữ liệu derive từ message/document/widget, sorted newest first.
- Behavior giữ nguyên:
  - Core ingest/chunking/embedding/retrieval/LLM flow không đổi.
  - Endpoint ngoài scope không bị chỉnh sửa.
- Điều kiện bật:
  - Dashboard data phụ thuộc dữ liệu có sẵn trong MySQL.
- Fallback giữ:
  - `avgSatisfaction` giữ `null` khi chưa có nguồn.
- Ảnh hưởng memory/cpu/disk:
  - Tăng nhẹ query read cho dashboard; giới hạn latest 50 items/source để tránh tải quá lớn.
- Ảnh hưởng latency/token/API cost:
  - Không gọi thêm LLM/API provider; chỉ query DB nội bộ.
- Ảnh hưởng dữ liệu MySQL/Qdrant cũ:
  - Không thay schema, không migrate, không chỉnh dữ liệu cũ.

## 10. Edge cases đã xem xét
- `days` null/blank -> default 7.
- `days` không phải số (`abc`) -> 400 JSON message.
- `days` ngoài range (`<1` hoặc `>90`) -> 400.
- `limit` null/blank -> default (5,20).
- `limit` không phải số / out-of-range -> 400.
- DB rỗng -> summary zero/null-safe, các list trả `[]` hoặc 0-count.
- Dữ liệu soft-delete không đồng bộ (message trỏ session đã soft-delete) -> xử lý bằng query join fetch lọc deleted.

## 11. Kết quả kiểm tra
| Command | Kết quả | Ghi chú |
|---|---|---|
| `cd Backend && ./mvnw -DskipTests compile` | PASS | Build success sau implement + bugfix activity |
| `cd Backend && ./mvnw test` | PASS | 14 tests pass |
| `cd Frontend && npm run lint` | NOT RUN | Ngoài scope backend prompt |
| `cd Frontend && npm run build` | NOT RUN | Ngoài scope backend prompt |
| `cd Frontend && npm run build:widget` | NOT RUN | Ngoài scope backend prompt |
| `docker compose config` | NOT RUN | Không thay đổi compose |

Manual runtime smoke:
- `GET /api/dashboard/summary`: PASS (200)
- `GET /api/dashboard/message-volume?days=7`: PASS (200)
- `GET /api/dashboard/message-volume?days=abc`: PASS (400 + message JSON)
- `GET /api/dashboard/top-chatbots?limit=5`: PASS (200 array)
- `GET /api/dashboard/activity?limit=20`: PASS (200 array)

## 12. Rủi ro còn lại
- `avgSatisfaction` vẫn là `null` do chưa có feedback data source.
- Delta cho `activeChatbots` và `documentCount` tạm `0.0`, chưa có logic compare period riêng.
- Activity chỉ là derived feed, không thay thế audit log chuẩn.

## 13. Đề xuất tiếp theo
- Prompt tiếp theo nên tập trung:
  - Chuẩn hóa timezone output cho `createdAt` activity (ISO with offset/UTC).
  - Nếu có feedback table ở prompt sau, map vào `avgSatisfaction` thật.
  - Cân nhắc pagination/filter cho activity khi data lớn.
