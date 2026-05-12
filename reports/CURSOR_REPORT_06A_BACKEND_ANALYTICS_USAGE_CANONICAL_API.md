# Cursor Report 06A - BACKEND_ANALYTICS_USAGE_CANONICAL_API

## 1. Mức độ hiểu task
- Task là gì? Triển khai Analytics Usage canonical APIs cho FE contract: summary, daily, by-chatbot, unanswered.
- Hiểu task: 98%
- Phần chắc chắn:
  - FE Usage tab consume `analyticsApi.getSummary/getDaily/getByChatbot/getUnanswered`.
  - Response shapes lấy trực tiếp từ `analyticsMock.js` + component usage.
  - Không làm sessions/feedback endpoints trong prompt này.
- Phần còn giả định:
  - `avgSatisfaction` và `fallbackRate` chưa có data source DB thật.
  - `unanswered` phải dùng heuristic từ nội dung assistant message.
- Phạm vi không làm:
  - Không làm `/api/analytics/sessions`, `/api/analytics/sessions/{id}/messages`, `/api/chat/feedback`.
  - Không sửa FE/mock, không đổi schema DB.

## 2. Files/rules/reports đã đọc
| File | Mục đích đọc | Kết luận chính |
|---|---|---|
| `.cursor/rules/*.mdc` (00,10,20,30,40,90) | Tuân thủ quy trình/scope/report | Giữ minimal diff, report trung thực, không out-of-scope |
| `agent.md`, `agent/01-overview.md`, `agent/02-architecture.md` | Nắm kiến trúc/data flow | Analytics phải dựa `WidgetConfig/ChatSession/ChatMessage` |
| `reports/CURSOR_REPORT_00_BACKEND_FLOW_AND_FE_CONTRACT_AUDIT.md` | Baseline mapping | Analytics endpoints trước đó Missing |
| `reports/CURSOR_REPORT_04B_BACKEND_PLAYGROUND_SESSIONS_COMPARE_EXPORT_CANONICAL_API.md` | Baseline chat session/message | Session/message model đã ổn định |
| `reports/CURSOR_REPORT_05A_BACKEND_DASHBOARD_CANONICAL_API.md` | Reuse pattern aggregate | Có query read-only + error JSON style |
| `Frontend/src/api/analyticsApi.js` | Source of truth endpoint/query | 4 Usage endpoints cần triển khai |
| `Frontend/src/mocks/analyticsMock.js` | Source of truth response shape | Summary/daily/byChatbot/unanswered field names rõ ràng |
| `Frontend/src/pages/analytics/AnalyticsPage.jsx` | FE consumption thực tế | Usage tab load song song 4 API, CSV export cần fields cụ thể |
| `Frontend/src/pages/analytics/components/AnalyticsMetricCard.jsx` | Summary fields | Cần `totalMessagesDelta`, `uniqueSessionsDelta`, `avgSatisfactionDelta`, `fallbackRateDelta` |
| `Frontend/src/pages/analytics/components/DailyBarChart.jsx` | Daily fields | Cần `date`, `messages`, `sessions` |
| `Frontend/src/pages/analytics/components/ChatbotShareBars.jsx` | By-chatbot fields | Cần `chatbotId`, `chatbotName`, `messageCount`, `share` |
| `Frontend/src/pages/analytics/components/UnansweredTable.jsx` | Unanswered fields | Cần `id`, `question`, `chatbotName`, `count` |
| `Backend/src/main/java/.../domain/chat/*` | Data source chính | Có `role`, `createdAt`, session->widget link |
| `Backend/src/main/java/.../domain/widget/*` | Metadata chatbot | chatbotId map từ `WidgetConfig.id` |
| `Backend/src/main/resources/application*.yml` | Runtime profile/env | dev profile local chạy được để smoke test |

## 3. Current analytics data source analysis
- WidgetConfig / Chatbot:
  - Có source thật cho chatbot name/id qua `ChatMessage -> ChatSession -> WidgetConfig`.
- Document:
  - Không dùng trực tiếp cho Usage endpoints 06A.
- ChatSession:
  - Có source thật để tính `uniqueSessions` (distinct session id theo range).
- ChatMessage:
  - Có source thật cho `totalMessages`, `daily messages/sessions`, `by-chatbot share`.
- Feedback/rating:
  - Không có bảng feedback chính thức trong backend hiện tại.
- Fallback/unanswered:
  - Không có fallback flag/unanswered flag trong schema.
  - `fallbackRate`: safe default `0.0`.
  - `unanswered`: heuristic từ cặp USER->ASSISTANT liên tiếp trong cùng session, match cụm từ fallback.
- Field tính thật vs safe default:
  - Tính thật: `totalMessages`, `uniqueSessions`, `daily.messages`, `daily.sessions`, `byChatbot.messageCount`, `byChatbot.share`.
  - Safe default: `avgSatisfaction=null`, `avgSatisfactionDelta=0.0`, `fallbackRate=0.0`, `fallbackRateDelta=0.0`.

## 4. FE contract mapping after implementation
| FE function | Endpoint | Query expected | Response expected | Backend implementation status | Notes |
|---|---|---|---|---|---|
| `getSummary({from,to,chatbotId})` | `GET /api/analytics/summary` | `from`, `to`, `chatbotId?` | `totalMessages,totalMessagesDelta,uniqueSessions,uniqueSessionsDelta,avgSatisfaction,avgSatisfactionDelta,fallbackRate,fallbackRateDelta` | DONE | invalid date/range/chatbotId -> 400 JSON message |
| `getDaily({from,to,chatbotId})` | `GET /api/analytics/daily` | `from`, `to`, `chatbotId?` | `[{date,messages,sessions}]` | DONE | fill đủ ngày zero-count |
| `getByChatbot({from,to})` | `GET /api/analytics/by-chatbot` | `from`, `to` | `[{chatbotId,chatbotName,messageCount,share}]` | DONE | sort desc by messageCount |
| `getUnanswered({limit})` | `GET /api/analytics/unanswered` | `limit?` default 10 | `[{id,question,chatbotName,count}]` | DONE | heuristic; không có hit thì `[]` |

## 5. Files changed
| File | Change | Reason | Risk |
|---|---|---|---|
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/api/AnalyticsController.java` | Add 4 analytics usage endpoints + validation | Match FE contract and 400 JSON requirement | Low |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/AnalyticsService.java` | Add aggregation/heuristic logic | Keep controller thin, reuse existing data model | Medium |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/domain/chat/ChatMessageRepository.java` | Add analytics range query with join fetch | Efficient read-only analytics data access | Low |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/dto/AnalyticsSummaryResponse.java` | New summary DTO | Stable API shape | Low |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/dto/AnalyticsDailyItem.java` | New daily DTO | Match chart data shape | Low |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/dto/AnalyticsByChatbotItem.java` | New by-chatbot DTO | Match share bars shape | Low |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/dto/AnalyticsUnansweredItem.java` | New unanswered DTO | Match unanswered table shape | Low |

## 6. Details per endpoint

### `GET /api/analytics/summary`
- Query params: `from`, `to`, `chatbotId?`.
- Response DTO: `AnalyticsSummaryResponse`.
- Service/repository used:
  - `AnalyticsService.getSummary`
  - `ChatMessageRepository.findForAnalyticsRange(...)`.
- Aggregation logic:
  - `totalMessages`: count messages in date window.
  - `uniqueSessions`: distinct session IDs from messages in window.
  - Delta: compare với period trước có cùng số ngày.
  - `avgSatisfaction`: `null` (no feedback source).
  - `fallbackRate`: `0.0` (no explicit fallback flag source).
- Empty DB behavior: zero/null-safe metrics, không crash.
- Error handling:
  - invalid date -> `400 {"message":"Invalid date format. Expected YYYY-MM-DD"}`
  - from>to -> `400 {"message":"from must be before or equal to to"}`
  - invalid chatbotId -> `400 {"message":"chatbotId must be a UUID"}`

### `GET /api/analytics/daily`
- Query params: `from`, `to`, `chatbotId?`.
- Response DTO: `List<AnalyticsDailyItem>` (`date,messages,sessions`).
- Service/repository used:
  - `AnalyticsService.getDaily`
  - `ChatMessageRepository.findForAnalyticsRange(...)`.
- Aggregation logic:
  - Count theo từng ngày, fill đầy đủ ngày trong range kể cả `0`.
  - `sessions` là distinct session count theo ngày.
- Empty DB behavior: trả đủ ngày với 0.
- Error handling: cùng validate date/chatbotId như summary.

### `GET /api/analytics/by-chatbot`
- Query params: `from`, `to`.
- Response DTO: `List<AnalyticsByChatbotItem>`.
- Service/repository used:
  - `AnalyticsService.getByChatbot`
  - `ChatMessageRepository.findForAnalyticsRange(..., widgetId=null)`.
- Aggregation logic:
  - Group theo chatbot id/name, count messages.
  - `share = messageCount / total * 100`, làm tròn 1 số lẻ.
  - sort desc by message count.
- Empty DB behavior: `[]`.
- Error handling: validate `from/to`.

### `GET /api/analytics/unanswered`
- Query params: `limit?` default 10, valid range `1..100`.
- Response DTO: `List<AnalyticsUnansweredItem>`.
- Service/repository used:
  - `AnalyticsService.getUnanswered`
  - `ChatMessageRepository.findLatestForDashboard(PageRequest.of(0,500))`.
- Aggregation logic:
  - Duyệt timeline, bắt cặp USER message với ASSISTANT kế tiếp cùng session.
  - Nếu assistant chứa cụm heuristic fallback (`khong biet`, `khong tim thay`, `khong co thong tin`, `i don't know`, `not found`, `no relevant`) thì tăng count câu hỏi user.
  - Group theo exact question string, sort count desc, limit.
- Empty DB/no hit behavior: `[]`.
- Error handling:
  - invalid limit -> `400 {"message":"limit must be between 1 and 100"}`

## 7. Validation results
| Command | Result | Notes |
|---|---|---|
| `cd Backend && ./mvnw -DskipTests compile` | PASS | Build success after adding analytics endpoints |
| `cd Backend && ./mvnw test` | PASS | 14 tests pass |

## 8. Manual/API test plan and results
| Endpoint | Curl / Steps | Result | Notes |
|---|---|---|---|
| Summary | `GET /api/analytics/summary?from=2026-05-01&to=2026-05-31` | PASS | 200 + all expected fields |
| Summary with chatbotId | `...&chatbotId=cd14c04d-dc66-4169-8341-648d4397ea6a` | PASS | 200 + filtered metrics |
| Invalid date | `from=bad` | PASS | 400 + exact message |
| Daily | `GET /api/analytics/daily?from=2026-05-01&to=2026-05-07` | PASS | 200 + 7 items + zero days |
| Daily with chatbotId | `...&chatbotId=cd14...` | PASS | 200 + filtered daily |
| By chatbot | `GET /api/analytics/by-chatbot?from=2026-05-01&to=2026-05-31` | PASS | 200 + sorted by count + share 0..100 |
| Unanswered | `GET /api/analytics/unanswered?limit=10` | PASS | 200 + `[]` (no heuristic hit in current dataset) |
| Invalid limit | `GET /api/analytics/unanswered?limit=abc` | PASS | 400 + message |
| FE compatibility spot check | Read `AnalyticsPage` + usage components | PASS | Field names and shapes match FE consume/export |

## 9. Known limitations / gaps
- `avgSatisfaction` chưa có backend feedback data source thật => trả `null`.
- `fallbackRate` chưa có explicit fallback tracking => trả `0.0`.
- `unanswered` là heuristic text-based, có thể false negative/positive; hiện dataset test chưa hit nên trả `[]`.
- Timezone theo server local date (`LocalDate`) khi bucket daily; FE render UTC label từ `YYYY-MM-DD`.

## 10. Recommended next prompt
- `06B_BACKEND_ANALYTICS_SESSIONS_TAB_CANONICAL_API` để triển khai phần Sessions tab (`/api/analytics/sessions`, `/api/analytics/sessions/{id}/messages`) theo FE contract hiện có.
