# Cursor Report 05A - BACKEND_DASHBOARD_CANONICAL_API

## 1. Mức độ hiểu task
- Task là gì? Triển khai 4 Dashboard endpoint canonical theo FE contract hiện tại: summary, message-volume, top-chatbots, activity.
- Hiểu task: 98%
- Phần chắc chắn:
  - FE gọi đúng 4 endpoint tại `Frontend/src/api/dashboardApi.js`.
  - FE kỳ vọng summary object + 3 endpoint dạng array.
  - Query params chính: `days`, `limit`.
- Phần còn giả định:
  - `avgSatisfaction` chưa có data source thật (feedback/rating table chưa tồn tại).
  - Activity là dữ liệu suy diễn từ entity hiện có, không phải audit log chuẩn.
- Phạm vi không làm:
  - Không làm `/api/analytics/*`, `/api/settings/*`.
  - Không đổi schema DB, không sửa FE/mock, không refactor core RAG.

## 2. Files/rules/reports đã đọc
| File | Mục đích đọc | Kết luận chính |
|---|---|---|
| `.cursor/rules/00-core-working-rule.mdc` | Chốt nguyên tắc sửa | Giữ minimal diff, không mở rộng ngoài scope |
| `.cursor/rules/90-report-verification-rule.mdc` | Chốt format verify/report | Cần report đầy đủ và trung thực |
| `reports/CURSOR_REPORT_00_BACKEND_FLOW_AND_FE_CONTRACT_AUDIT.md` | Baseline contract backend/FE | Dashboard endpoint đang Missing |
| `reports/CURSOR_REPORT_02B_RERUN_BACKEND_CHATBOTS_API_RUNTIME_SMOKE_TEST.md` | Baseline runtime | Runtime backend/mysql/qdrant khả dụng |
| `reports/CURSOR_REPORT_03C_BACKEND_DOCUMENTS_RUNTIME_SMOKE_TEST.md` | Baseline documents | Dùng document data cho dashboard aggregation |
| `reports/CURSOR_REPORT_04A_BACKEND_PUBLIC_AND_PLAYGROUND_CHAT_RUNTIME_SMOKE_TEST.md` | Baseline chat | Dùng chat messages cho metrics |
| `reports/CURSOR_REPORT_04B_BACKEND_PLAYGROUND_SESSIONS_COMPARE_EXPORT_CANONICAL_API.md` | Baseline playground | Reuse chat/session model hiện có |
| `Frontend/src/api/dashboardApi.js` | Source-of-truth API contract | 4 endpoint và params mặc định |
| `Frontend/src/mocks/dashboardMock.js` | Shape dữ liệu FE dùng | Summary fields + activity item fields |
| `Frontend/src/pages/dashboard/DashboardPage.jsx` | Cách FE consume | Metric cards dùng field delta; các phần còn lại consume array |
| `Frontend/src/pages/dashboard/components/*` | Validate field-level contract | Activity table đọc `id/type/actor/target/createdAt` |
| `Backend/src/main/java/.../WidgetConfig*.java` | Data source chatbot | Có `isActive`, `uiConfig`, soft delete |
| `Backend/src/main/java/.../Document*.java` | Data source documents | Có `status`, `createdAt/updatedAt`, soft delete |
| `Backend/src/main/java/.../ChatSession*.java` | Data source session | session theo widget |
| `Backend/src/main/java/.../ChatMessage*.java` | Data source message | message count + timeline |

## 3. Current data source analysis
- WidgetConfig / Chatbot:
  - `WidgetConfig` + `WidgetConfigRepository` là nguồn cho `activeChatbots`, top chatbots metadata, activity chatbot events.
- Document:
  - `DocumentRepository` cung cấp `documentCount` và document activity (`document_indexed`, `document_uploaded`, `document_failed`).
- ChatSession:
  - Dùng join qua `ChatMessage -> ChatSession -> WidgetConfig` để aggregate top chatbots và activity message.
- ChatMessage:
  - Nguồn chính cho `messages7d`, `message-volume`, top chatbots by message count.
- DocumentStatus:
  - Mapping thành event type activity cho documents.
- Event log thật:
  - Không có event log table chuyên biệt trong backend hiện tại.
- ActivityTable derive:
  - Derive từ 3 nguồn: latest chat messages + latest documents + latest widgets, merge/sort theo thời gian giảm dần.

## 4. FE contract mapping after implementation
| FE function | Endpoint | Query expected | Response expected | Backend implementation status | Notes |
|---|---|---|---|---|---|
| `getSummary` | `GET /api/dashboard/summary` | none | object gồm `activeChatbots/messages7d/avgSatisfaction/documentCount` + delta fields | DONE | `avgSatisfaction` trả `null` (chưa có feedback data source) |
| `getMessageVolume(days)` | `GET /api/dashboard/message-volume?days=7` | `days` default 7 | array `{date, count}` | DONE | Fill đủ ngày 0-count, date format `YYYY-MM-DD` |
| `getTopChatbots(limit)` | `GET /api/dashboard/top-chatbots?limit=5` | `limit` default 5 | array items top chatbot | DONE | Trả `id,name,messageCount,satisfaction,domain,status` |
| `getActivity(limit)` | `GET /api/dashboard/activity?limit=20` | `limit` default 20 | array events | DONE | Trả `id,type,actor,target,createdAt` đúng ActivityTable |

## 5. Files changed
| File | Change | Reason | Risk |
|---|---|---|---|
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/api/DashboardController.java` | Thêm 4 dashboard endpoints + validate query params | Match FE path/params/error JSON | Low |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/DashboardService.java` | Implement aggregation summary/volume/top/activity | Tách business logic khỏi controller | Medium |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/domain/chat/ChatMessageRepository.java` | Thêm query method cho count/range/top/activity-safe join | Phục vụ dashboard read-only aggregate | Low |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/domain/document/DocumentRepository.java` | Thêm latest documents query | Derive activity | Low |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/domain/widget/WidgetConfigRepository.java` | Thêm active count + latest widgets query | Summary + activity | Low |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/dto/DashboardSummaryResponse.java` | DTO summary | Không trả entity trực tiếp | Low |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/dto/DashboardMessageVolumeItem.java` | DTO volume item | Khớp FE chart shape | Low |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/dto/DashboardTopChatbotItem.java` | DTO top chatbot item | Khớp FE list shape | Low |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/dto/DashboardActivityItem.java` | DTO activity item | Khớp FE activity table shape | Low |

## 6. Details per endpoint

### A) `GET /api/dashboard/summary`
- Query params: none.
- Response DTO: `DashboardSummaryResponse`.
- Service/repository used:
  - `WidgetConfigRepository.countByIsActiveTrue()`
  - `ChatMessageRepository.countByCreatedAtBetween(...)`
  - `DocumentRepository.count()`
- Aggregation logic:
  - `messages7d` = số message trong [today-6, today].
  - `messages7dDelta` so với 7 ngày trước đó.
  - `activeChatbotsDelta`, `documentCountDelta` = 0.0 (chưa có baseline period entity-specific).
  - `avgSatisfaction` = `null`, `avgSatisfactionDelta` = `0.0`.
- Empty DB behavior:
  - Trả object hợp lệ với số 0/null-safe, không crash.
- Error handling:
  - Endpoint không có query input nên không phát sinh 400 param parse.

### B) `GET /api/dashboard/message-volume?days=7`
- Query params:
  - `days` default 7.
  - Validate range `1..90`.
- Response DTO:
  - `List<DashboardMessageVolumeItem>` với `{date, count}`.
- Service/repository used:
  - `ChatMessageRepository.findByCreatedAtBetweenOrderByCreatedAtAsc(...)`.
- Aggregation logic:
  - Build đầy đủ N ngày liên tục và fill 0 cho ngày không có message.
- Empty DB behavior:
  - Trả đủ N phần tử với count = 0.
- Error handling:
  - `days` non-numeric -> `400 {"message":"days must be a number"}`
  - out-of-range -> `400 {"message":"days must be between 1 and 90"}`

### C) `GET /api/dashboard/top-chatbots?limit=5`
- Query params:
  - `limit` default 5.
  - Validate range `1..20`.
- Response DTO:
  - `List<DashboardTopChatbotItem>`.
- Service/repository used:
  - `ChatMessageRepository.findTopChatbotMessageCounts(Pageable)`
  - `WidgetConfigRepository.findById(...)` để lấy domain/status.
- Aggregation logic:
  - Group theo chatbot, sort desc by message count.
  - Exclude deleted chatbot qua SQL restriction + explicit condition.
- Empty DB behavior:
  - Trả `[]`.
- Error handling:
  - invalid/out-of-range `limit` -> HTTP 400 JSON `message`.

### D) `GET /api/dashboard/activity?limit=20`
- Query params:
  - `limit` default 20.
  - Validate range `1..100`.
- Response DTO:
  - `List<DashboardActivityItem>` với fields `id,type,actor,target,createdAt`.
- Service/repository used:
  - `ChatMessageRepository.findLatestForDashboard(Pageable)` (join fetch session/widget để tránh lỗi orphan lazy load).
  - `DocumentRepository.findTop50ByOrderByUpdatedAtDesc()`
  - `WidgetConfigRepository.findTop50ByOrderByUpdatedAtDesc()`
- Aggregation logic:
  - Derive event list từ messages + documents + widgets, merge, sort newest first, cắt theo limit.
- Empty DB behavior:
  - Trả `[]`.
- Error handling:
  - invalid/out-of-range `limit` -> HTTP 400 JSON `message`.

## 7. Validation results
| Command | Result | Notes |
|---|---|---|
| `cd Backend && ./mvnw -DskipTests compile` | PASS | Compile pass sau implement dashboard và bugfix activity |
| `cd Backend && ./mvnw test` | PASS | 14 tests pass trong môi trường hiện tại |

## 8. Manual/API test plan and results
| Endpoint | Curl / Steps | Result | Notes |
|---|---|---|---|
| `GET /api/dashboard/summary` | `Invoke-WebRequest http://localhost:8080/api/dashboard/summary` | PASS | HTTP 200; có đủ field summary + delta |
| `GET /api/dashboard/message-volume?days=7` | `Invoke-WebRequest .../message-volume?days=7` | PASS | HTTP 200; trả 7 items; có ngày count=0 |
| `GET /api/dashboard/message-volume?days=abc` | `Invoke-WebRequest .../message-volume?days=abc` | PASS | HTTP 400; body JSON `message` |
| `GET /api/dashboard/top-chatbots?limit=5` | `Invoke-WebRequest .../top-chatbots?limit=5` | PASS | HTTP 200; array sorted desc theo messageCount |
| `GET /api/dashboard/activity?limit=20` | `Invoke-WebRequest .../activity?limit=20` | PASS | HTTP 200; array item có `event(type)/chatbot(target)/user(actor)/time(createdAt)` |

## 9. Known limitations / gaps
- `avgSatisfaction` chưa có feedback table -> trả `null` theo safe shape FE.
- `activeChatbotsDelta` và `documentCountDelta` hiện trả `0.0` do chưa có baseline period riêng cho 2 metric này.
- Activity là dữ liệu suy diễn từ entities hiện có, không phải event log audit chuẩn.
- `createdAt` activity hiện là `LocalDateTime.toString()` (không timezone suffix `Z`).

## 10. Recommended next prompt
- `05B_BACKEND_DASHBOARD_ACTIVITY_TIMEZONE_AND_SATISFACTION_SOURCE`:
  - Chuẩn hóa timezone output (ISO with offset/UTC),
  - Bổ sung source thật cho satisfaction nếu có bảng feedback trong tương lai,
  - Tinh chỉnh delta logic cho `activeChatbots`/`documentCount` nếu cần period comparison thực sự.
