# Cursor Report 06B - BACKEND_ANALYTICS_SESSIONS_CANONICAL_API

## 1. Mức độ hiểu task
- Task là gì? Triển khai Analytics Sessions tab APIs theo FE contract:
  - `GET /api/analytics/sessions?...`
  - `GET /api/analytics/sessions/{id}/messages`
- Hiểu task: 98%
- Phần chắc chắn:
  - FE cần pagination shape `{items,page,size,total,totalPages}`.
  - Session detail drawer cần array messages có `id/role/content/sources`.
  - Không làm feedback endpoint.
- Phần còn giả định:
  - Rating chưa có nguồn thật => cần safe behavior cho rating filter.
- Phạm vi không làm:
  - Không làm `/api/chat/feedback`.
  - Không sửa FE/mock, không đổi schema DB.

## 2. Files/rules/reports đã đọc
| File | Mục đích đọc | Kết luận chính |
|---|---|---|
| `.cursor/rules/*.mdc` | Scope và verification | Giữ minimal diff, report trung thực |
| `reports/CURSOR_REPORT_04B_BACKEND_PLAYGROUND_SESSIONS_COMPARE_EXPORT_CANONICAL_API.md` | Reuse session-key behavior | Session id FE dùng `sessionKey` UUID string |
| `reports/CURSOR_REPORT_06A_BACKEND_ANALYTICS_USAGE_CANONICAL_API.md` | Không phá Usage API | Chỉ mở rộng analytics scope |
| `Frontend/src/api/analyticsApi.js` | Source of truth endpoints | Sessions API nhận `from,to,chatbotId,rating,page,size` |
| `Frontend/src/mocks/analyticsMock.js` | Shape table/drawer | Sessions row fields + message fields |
| `Frontend/src/pages/analytics/components/AnalyticsSessionsTab.jsx` | FE pagination/filter consume | FE normalize payload về items/page/size/total/totalPages |
| `Frontend/src/pages/analytics/components/SessionsTable.jsx` | Fields table | Cần `id,chatbotName,messageCount,rating,createdAt` |
| `Frontend/src/pages/analytics/components/SessionsPagination.jsx` | Pagination behavior | FE dùng `page,totalPages,total,size` |
| `Frontend/src/pages/analytics/components/SessionDetailDrawer.jsx` | Fields message/source | Cần `role/content/sources[]`; SourcePill đọc `fileName/documentName/title/score/snippet/chunk/content` |
| `Backend/src/main/java/.../api/AnalyticsController.java` | Current analytics endpoints | Mới có usage endpoints |
| `Backend/src/main/java/.../service/AnalyticsService.java` | Current analytics logic | Chưa có sessions list/detail |
| `Backend/src/main/java/.../domain/chat/ChatSessionRepository.java` | Session query points | Cần query theo date range/page |
| `Backend/src/main/java/.../domain/chat/ChatMessageRepository.java` | Message query points | Đã có findBySessionIdOrderByCreatedAtAsc |

## 3. Current sessions data source analysis
- ChatSession:
  - Source chính cho row sessions list (session id, createdAt, chatbot relation).
- ChatMessage:
  - Source cho `messageCount` và session detail conversation.
- WidgetConfig / Chatbot:
  - Dùng để map `chatbotId`, `chatbotName`.
- sources JSON:
  - `ChatMessage.sources` là list JSON object; parse trực tiếp sang source DTO.
- rating data:
  - Không có rating source chính thức trong DB model hiện tại.
- field tính thật vs safe default:
  - Tính thật: session list, messageCount, conversation messages + sources.
  - Safe default: `rating = null`; rating filter `positive/negative` trả empty.

## 4. FE contract mapping after implementation
| FE function | Endpoint | Query expected | Response expected | Backend implementation status | Notes |
|---|---|---|---|---|---|
| `analyticsApi.getSessions` | `GET /api/analytics/sessions` | `from,to,chatbotId?,rating?,page?,size?` | `{items,page,size,total,totalPages}` | DONE | `id` dùng `sessionKey` để đồng nhất endpoint detail |
| `analyticsApi.getSessionMessages(id)` | `GET /api/analytics/sessions/{id}/messages` | path session UUID | `[{id,role,content,createdAt,sources}]` | DONE | invalid id 400, not found 404 |

## 5. Files changed
| File | Change | Reason | Risk |
|---|---|---|---|
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/api/AnalyticsController.java` | Add sessions endpoints + validation page/size/session id | Match FE sessions tab contract | Low |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/AnalyticsService.java` | Add getSessions/getSessionMessages mapping logic | Keep controller thin | Medium |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/domain/chat/ChatSessionRepository.java` | Add pageable JPQL query `findForAnalyticsRange` join widget filter deleted | Fix orphan widget runtime crash and support paging | Medium |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/dto/AnalyticsSessionItem.java` | New sessions row DTO | Stable API shape | Low |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/dto/AnalyticsSessionPageResponse.java` | New page response DTO | Match FE pagination shape | Low |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/dto/AnalyticsSessionMessageResponse.java` | New detail message DTO | Match drawer shape | Low |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/dto/AnalyticsSessionSourceResponse.java` | New source DTO for drawer source pill fields | Match FE source rendering | Low |

## 6. Details per endpoint

### `GET /api/analytics/sessions`
- Method/path: `GET /api/analytics/sessions`
- Query params:
  - `from`, `to` required (`YYYY-MM-DD`)
  - `chatbotId` optional UUID
  - `rating` optional
  - `page` default `0`, must be `>=0`
  - `size` default `10`, range `1..100`
- Response DTO:
  - `AnalyticsSessionPageResponse`.
- Service/repository used:
  - `AnalyticsService.getSessions`
  - `ChatSessionRepository.findForAnalyticsRange(...)`
  - `ChatMessageRepository.countBySessionId(...)`
- Mapping logic:
  - `id` = `session.sessionKey` (string UUID)
  - `chatbotId` = `session.widgetConfig.id`
  - `chatbotName` = `session.widgetConfig.name`
  - `messageCount` = count messages by `session.id`
  - `rating` = `null` (safe default)
  - `createdAt` = `session.createdAt`
  - sort newest first.
- Empty DB behavior:
  - `{items:[], page, size, total:0, totalPages:0}`.
- Error handling:
  - invalid date -> 400 JSON message
  - from>to -> 400 JSON message
  - invalid chatbotId -> 400 JSON message
  - invalid page/size -> 400 JSON message

### `GET /api/analytics/sessions/{id}/messages`
- Method/path: `GET /api/analytics/sessions/{id}/messages`
- Path params:
  - `id` = session key UUID (same id from sessions list)
- Response DTO:
  - `List<AnalyticsSessionMessageResponse>`.
- Service/repository used:
  - `AnalyticsService.getSessionMessages`
  - `ChatSessionRepository.findBySessionKey(...)`
  - `ChatMessageRepository.findBySessionIdOrderByCreatedAtAsc(...)`
- Mapping logic:
  - `role` lowercase `user/assistant`
  - `sources` mapped to `AnalyticsSessionSourceResponse` with FE-readable fields.
- Empty DB behavior:
  - session exists but no messages => `[]`.
- Error handling:
  - invalid UUID -> 400 `{"message":"Invalid session id"}`
  - not found -> 404 `{"message":"Session not found"}`

## 7. Rating filter behavior
- Map hiện tại:
  - `rating=unrated` hoặc `rating` rỗng/all -> trả sessions bình thường (rating null).
  - `rating=positive` -> trả empty page.
  - `rating=negative` -> trả empty page.
  - các giá trị rating khác (vì chưa có source thật) -> trả empty page.
- Vì sao:
  - Chưa có rating/feedback table trong backend hiện tại.
- Limitation:
  - Không phân loại được positive/negative thực tế cho sessions.

## 8. Validation results
| Command | Result | Notes |
|---|---|---|
| `cd Backend && ./mvnw -DskipTests compile` | PASS | Compile pass sau implementation và bugfix |
| `cd Backend && ./mvnw test` | PASS | 14 tests pass |

## 9. Manual/API test plan and results
| Endpoint | Curl / Steps | Result | Notes |
|---|---|---|---|
| Sessions list | `GET /api/analytics/sessions?from=2026-05-01&to=2026-05-31&page=0` | PASS | 200 + page shape đúng |
| Sessions list with chatbotId | `...&chatbotId=cd14c04d-dc66-4169-8341-648d4397ea6a&page=0` | PASS | 200 + filtered sessions |
| Rating unrated | `...&rating=unrated&page=0` | PASS | 200 + sessions unrated |
| Rating positive | `...&rating=positive&page=0` | PASS | 200 + empty page |
| Rating negative | `...&rating=negative&page=0` | PASS | 200 + empty page |
| Invalid date | `...from=bad&to=2026-05-31&page=0` | PASS | 400 + message |
| Session messages | `GET /api/analytics/sessions/{id}/messages` | PASS | 200 + chronological messages + sources |
| Invalid session id | `.../not-a-uuid/messages` | PASS | 400 + message |
| Not found session id | `.../{random-uuid}/messages` | PASS | 404 + message |

## 10. Known limitations / gaps
- `rating` chưa có source thật nên chỉ hỗ trợ safe behavior (`unrated/all`); `positive/negative` trả rỗng.
- Session list currently filtered theo `session.createdAt` range (không theo last message time).
- `sources` payload phụ thuộc dữ liệu lưu trong `ChatMessage.sources`; field score/snippet có thể null.

## 11. Recommended next prompt
- `06C_BACKEND_ANALYTICS_FEEDBACK_CANONICAL_API` để triển khai nguồn rating/feedback thực, từ đó bật filter positive/negative có ý nghĩa.
