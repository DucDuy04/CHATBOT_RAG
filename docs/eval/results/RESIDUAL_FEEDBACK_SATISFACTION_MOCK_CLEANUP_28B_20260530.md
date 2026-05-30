# RESIDUAL FEEDBACK / SATISFACTION / MOCK CLEANUP 28B — 2026-05-30

## 1. Mức độ hiểu task

- Hiểu task: **96%**.
- Chắc chắn: task yêu cầu xóa residual feedback/satisfaction/rating khỏi active backend/frontend/API docs, bỏ `newFeedback`/`notify_new_feedback` khỏi active contract/entity, bỏ mock API infrastructure nếu không còn cần, và validate build/test.
- Giả định: các file report/eval lịch sử được giữ làm audit history, không xem là active contract.
- Thiếu dữ kiện: chưa có migration framework chính thức trong repo; chỉ thấy SQL thủ công (`schema_update.sql`, backup dump), nên DB cleanup được ghi manual SQL.

## 2. Tóm tắt yêu cầu

Remove all remaining feedback/satisfaction/rating artifacts, remove unused frontend mock infrastructure, keep real API integration, avoid retrieval/chat/document ingest changes, update docs/contracts, run backend/frontend/docker validation.

Final verdict: **PASS** for code/docs cleanup and validation. DB physical cleanup is **manual SQL documented** because no Flyway/Liquibase migration system exists.

## 3. Hiện trạng trước khi sửa

- Backend still exposed `avgSatisfaction`, `avgSatisfactionDelta`, session `rating`, dashboard top-chatbot `satisfaction`.
- Backend settings still exposed/persisted `SettingsNotificationsDto.newFeedback` and `SettingsProfile.notifyNewFeedback`.
- `ChatSessionRepository.findAllForAnalyticsRange` remained after rating filter removal.
- Frontend dashboard/analytics still rendered satisfaction placeholders and CSV export column.
- Frontend API modules still imported `apiMode.js` and `Frontend/src/mocks/*`.
- API/architecture/runbook docs still mentioned removed feedback endpoint/table/fields.
- No `Backend/src/main/resources/db/migration` or Flyway/Liquibase migration folder exists.

## 4. Nguyên nhân gốc xác nhận từ source

28A removed the main feedback endpoint/entity/service/UI, but left compatible response placeholders and dev mock branches. Those placeholders kept old API fields alive as `null` or static mock data. Settings still had an entity column mapping for a feedback notification toggle even though the UI toggle was removed.

## 5. Chiến lược sửa đã chọn

- Remove fields from DTO contracts instead of returning `null`.
- Remove service builder setters and dead aggregation/filter logic.
- Remove settings notification field from DTO/entity/service mapping.
- Remove frontend satisfaction cards/CSV/top-chatbot satisfaction display.
- Convert frontend API modules to real API only, then delete `apiMode.js` and `src/mocks/*`.
- Update active API/architecture/runbook docs.
- Do not add a fake migration framework; document manual SQL cleanup.

## 6. Danh sách file đã đọc

| Path | Đọc để làm gì | Kết luận chính |
|---|---|---|
| `agent.md` | Project entry and constraints | Must not touch RAG/Qdrant behavior; tests baseline expected. |
| `agent/01-overview.md` | Scope overview | Backend/Frontend/Docker layout confirmed. |
| `agent/02-architecture.md` | Architecture invariants | Retrieval/Qdrant payload must remain unchanged. |
| `agent/05-api.md` | API contract docs | Feedback endpoint note was stale for 28B cleanup. |
| `.cursor/rules/10-backend-rag-rule.mdc` | Backend constraints | Avoid unrelated API/provider/schema changes. |
| `.cursor/rules/20-frontend-widget-rule.mdc` | Frontend constraints | Keep real API wiring and run build/lint. |
| `.cursor/rules/30-deploy-env-ops-rule.mdc` | Deploy validation | Run compose config; do not claim production verification. |
| `.cursor/rules/40-db-vector-rule.mdc` | DB/vector constraints | Do not manually drop data; inspect migration strategy. |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/AnalyticsService.java` | Find satisfaction/rating logic | Still had placeholder/removed feedback aggregation remnants. |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/DashboardService.java` | Find dashboard satisfaction | Still set satisfaction fields. |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/SettingsService.java` | Find settings mapping | Still read/write `newFeedback`. |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/domain/settings/SettingsProfile.java` | Confirm table/column | Table is `settings_profiles`, column was `notify_new_feedback`. |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/domain/chat/ChatSessionRepository.java` | Check dead method | `findAllForAnalyticsRange` was dead after rating filter removal. |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/dto/*.java` | API response/request fields | DTOs still exposed satisfaction/rating/newFeedback. |
| `schema_update.sql` | Migration strategy | SQL is manual update file; no migration framework. |
| `ragchatbot_backup_before_schema_update.sql` | Existing schema reference | Backup dump did not provide an active migration system. |
| `Frontend/src/api/*.js` | Mock mode imports | Multiple API modules used `USE_MOCK_API` and `../mocks`. |
| `Frontend/src/pages/analytics/AnalyticsPage.jsx` | Analytics UI/CSV | Satisfaction card and CSV row remained. |
| `Frontend/src/pages/dashboard/DashboardPage.jsx` | Dashboard UI | Avg satisfaction placeholder remained. |
| `Frontend/src/pages/dashboard/components/TopChatbotsList.jsx` | Top chatbot UI | Displayed `satisfaction` when present. |
| `Frontend/.env.example` | Mock env docs | `VITE_USE_MOCK_API` still documented. |
| `docs/api/API_REFERENCE_20260530.md` | API docs | Still documented `/api/chat/feedback` and rating param. |
| `docs/api/API_SMOKE_TESTS_20260530.md` | Smoke docs | Optional feedback check was stale. |
| `docs/RAG_TARGET_ARCHITECTURE.md` | Architecture docs | Mentioned feedback table/module/satisfaction. |
| `docs/RAG_CORE_FLOW_E2E_RUNBOOK.md` | Cleanup runbook | Mentioned `chat_feedbacks` truncate. |

## 7. Danh sách file đã sửa

| Path | Sửa để làm gì | Lớp ảnh hưởng |
|---|---|---|
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/dto/AnalyticsSummaryResponse.java` | Remove satisfaction fields | api |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/dto/DashboardSummaryResponse.java` | Remove satisfaction fields | api |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/dto/AnalyticsSessionItem.java` | Remove session rating | api |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/dto/DashboardTopChatbotItem.java` | Remove top-chatbot satisfaction | api |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/dto/SettingsNotificationsDto.java` | Remove `newFeedback` | api |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/AnalyticsService.java` | Stop setting satisfaction/rating | service |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/DashboardService.java` | Stop setting satisfaction | service |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/SettingsService.java` | Stop mapping feedback notification | service |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/domain/settings/SettingsProfile.java` | Remove `notify_new_feedback` mapping | db |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/domain/chat/ChatSessionRepository.java` | Remove dead analytics list method | db |
| `Frontend/src/api/*.js` | Remove mock mode branches/imports | ui |
| `Frontend/src/api/apiMode.js` | Delete mock mode switch | ui |
| `Frontend/src/mocks/*.js` | Delete unused mock data folder contents | ui |
| `Frontend/src/pages/analytics/AnalyticsPage.jsx` | Remove satisfaction card/CSV/feedback guard | ui |
| `Frontend/src/pages/dashboard/DashboardPage.jsx` | Remove avg satisfaction card | ui |
| `Frontend/src/pages/dashboard/components/TopChatbotsList.jsx` | Remove satisfaction display | ui |
| `Frontend/.env.example` | Remove mock env docs | config |
| `agent/05-api.md` | Remove stale feedback contract | docs |
| `docs/api/API_REFERENCE_20260530.md` | Remove feedback endpoint/rating param | docs |
| `docs/api/API_SMOKE_TESTS_20260530.md` | Remove feedback smoke check | docs |
| `docs/RAG_TARGET_ARCHITECTURE.md` | Remove feedback/satisfaction architecture claims | docs |
| `docs/RAG_CORE_FLOW_E2E_RUNBOOK.md` | Remove `chat_feedbacks` active cleanup step | docs |
| `docs/eval/results/RESIDUAL_FEEDBACK_SATISFACTION_MOCK_CLEANUP_28B_20260530.md` | Required verification report | docs |
| `reports/refactor/CURSOR_REPORT_28B_RESIDUAL_FEEDBACK_SATISFACTION_MOCK_CLEANUP.md` | Required refactor report | docs |

## 8. Diff thay đổi của từng file

### Backend DTO/API contract

Removed fields from DTOs so JSON responses no longer expose placeholders.

```diff
-    Double avgSatisfaction;
-    Double avgSatisfactionDelta;
-    Integer rating;
-    Double satisfaction;
-    private Boolean newFeedback;
```

Affected files:

- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/dto/AnalyticsSummaryResponse.java`
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/dto/DashboardSummaryResponse.java`
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/dto/AnalyticsSessionItem.java`
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/dto/DashboardTopChatbotItem.java`
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/dto/SettingsNotificationsDto.java`

### `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/AnalyticsService.java`

Hiện trạng cũ: still set satisfaction/rating fields and included dead rating-filter helpers after 28A.  
Đã sửa: removed satisfaction setters, rating setters, rating-filter path, and feedback aggregation helpers.  
Vì sao: contract must remove fields entirely and no backend service should compute/set satisfaction.

```diff
-                .avgSatisfaction(currentSatisfaction.avgSatisfaction)
-                .avgSatisfactionDelta(calculatePointDelta(...))
                 .fallbackRate(0.0d)
                 .fallbackRateDelta(0.0d)

-                        .rating(sessionRatingMap.get(session.getId()))
                         .createdAt(session.getCreatedAt() == null ? null : session.getCreatedAt().toString())
```

### `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/DashboardService.java`

Hiện trạng cũ: dashboard summary/top-chatbots still set satisfaction.  
Đã sửa: removed feedback repo dependency and satisfaction setters.  
Vì sao: dashboard API should not expose satisfaction.

```diff
-    private final ChatFeedbackRepository chatFeedbackRepository;
-
-                .avgSatisfaction(avgSatisfaction)
-                .avgSatisfactionDelta(0.0d)
                 .documentCount(documentCount)

-                    .satisfaction(null)
                     .domain(readDomain(widget))
```

### `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/SettingsService.java`

Hiện trạng cũ: profile update/response still mapped `newFeedback`.  
Đã sửa: removed mapping in update/default/response.  
Vì sao: feedback notification is not an active setting anymore.

```diff
-            if (n.getNewFeedback() != null) {
-                p.setNotifyNewFeedback(n.getNewFeedback());
-            }
-                .notifyNewFeedback(false)
-                        .newFeedback(p.isNotifyNewFeedback())
```

### `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/domain/settings/SettingsProfile.java`

Hiện trạng cũ: JPA mapped `settings_profiles.notify_new_feedback`.  
Đã sửa: removed field mapping.  
Vì sao: active entity should not depend on the removed column.

```diff
-    @Column(name = "notify_new_feedback", nullable = false)
-    @Builder.Default
-    private boolean notifyNewFeedback = false;
```

### `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/domain/chat/ChatSessionRepository.java`

Hiện trạng cũ: `findAllForAnalyticsRange` supported removed in-memory rating filter.  
Đã sửa: removed dead method.  
Vì sao: paginated `findForAnalyticsRange` is the active sessions query.

```diff
-    List<ChatSession> findAllForAnalyticsRange(...)
```

### Frontend API mock infrastructure

Hiện trạng cũ: API clients imported `USE_MOCK_API`, `mockDelay`, `createPaginatedResponse`, and `../mocks/*`.  
Đã sửa: API clients always call real backend; `apiMode.js` and `src/mocks/*.js` deleted.  
Vì sao: user requested no mock infrastructure unless active runtime requires it; active runtime now uses real API only.

```diff
-import { USE_MOCK_API, mockDelay } from "./apiMode";
-import { ... } from "../mocks/...";
-if (USE_MOCK_API) { ... }
 const res = await axiosInstance.get(...);
 return res.data;
```

Affected files:

- `Frontend/src/api/analyticsApi.js`
- `Frontend/src/api/dashboardApi.js`
- `Frontend/src/api/chatbotsApi.js`
- `Frontend/src/api/documentsApi.js`
- `Frontend/src/api/settingsApi.js`
- `Frontend/src/api/playgroundApi.js`
- `Frontend/src/api/publicChatApi.js`
- `Frontend/src/api/index.js`
- `Frontend/src/api/apiMode.js` deleted
- `Frontend/src/mocks/analyticsMock.js` deleted
- `Frontend/src/mocks/chatbotsMock.js` deleted
- `Frontend/src/mocks/dashboardMock.js` deleted
- `Frontend/src/mocks/documentsMock.js` deleted
- `Frontend/src/mocks/playgroundMock.js` deleted
- `Frontend/src/mocks/settingsMock.js` deleted

### Frontend satisfaction UI

Hiện trạng cũ: dashboard/analytics displayed satisfaction placeholder and CSV export column.  
Đã sửa: removed cards, CSV row, and top-chatbot satisfaction display.  
Vì sao: no placeholders returning null should remain.

```diff
-      lines.push(`Satisfaction (%),${summaryState.data?.avgSatisfaction ?? ""},...`);
-            <AnalyticsMetricCard title="Satisfaction" ... />
-        <MetricCard title="Avg satisfaction" ... />
-                  {bot.satisfaction != null && <p>{bot.satisfaction}%</p>}
```

Affected files:

- `Frontend/src/pages/analytics/AnalyticsPage.jsx`
- `Frontend/src/pages/dashboard/DashboardPage.jsx`
- `Frontend/src/pages/dashboard/components/TopChatbotsList.jsx`
- `Frontend/src/pages/dashboard/components/MetricCard.jsx`

### Frontend stale mock comments

Hiện trạng cũ: comments referenced mock API data shapes.  
Đã sửa: comments now describe real/fallback shapes without mock mode.  
Vì sao: avoid stale mock-mode documentation.

```diff
- * Handles Blob (real) and plain object (mock).
+ * Handles Blob downloads and JSON object fallbacks.
```

Affected files:

- `Frontend/src/pages/playground/components/ExportSessionButton.jsx`
- `Frontend/src/pages/playground/components/LatencyPanel.jsx`
- `Frontend/src/pages/chatbots/components/ModelSettingsSection.jsx`
- `Frontend/src/pages/chatbots/ChatbotEmbedPage.jsx`
- `Frontend/.env.example`

### Docs/API

Hiện trạng cũ: active docs still listed removed feedback endpoint/table/fields.  
Đã sửa: removed active contract references and runbook cleanup step.  
Vì sao: docs must match source contract.

```diff
-| POST | `/api/chat/feedback` | None | Submit message feedback |
-| `GET /api/analytics/sessions` | ..., `rating`, ... |
-TRUNCATE TABLE chat_feedbacks;
+| `GET /api/analytics/sessions` | `from`, `to`; optional `chatbotId`, `page`, `size` |
```

Affected files:

- `agent/05-api.md`
- `docs/api/API_REFERENCE_20260530.md`
- `docs/api/API_SMOKE_TESTS_20260530.md`
- `docs/RAG_TARGET_ARCHITECTURE.md`
- `docs/RAG_CORE_FLOW_E2E_RUNBOOK.md`

### Reports

New files:

```diff
+ docs/eval/results/RESIDUAL_FEEDBACK_SATISFACTION_MOCK_CLEANUP_28B_20260530.md
+ reports/refactor/CURSOR_REPORT_28B_RESIDUAL_FEEDBACK_SATISFACTION_MOCK_CLEANUP.md
```

## 9. Ảnh hưởng sau sửa

- Behavior thay đổi: analytics/dashboard/settings JSON no longer include satisfaction/rating/newFeedback fields.
- Behavior thay đổi: frontend no longer has `USE_MOCK_API` mock fallback; API clients always call real backend.
- Behavior giữ nguyên: chat sync/SSE/public chat, retrieval, Qdrant payload, document upload/ingest/delete, playground real endpoints.
- Điều chỉ bật khi đủ điều kiện: no new conditional behavior.
- Fallback giữ lại: existing frontend error/loading states and backend validation remain.
- Memory/CPU/disk: reduced frontend bundle/source mock data; no backend runtime increase. Old physical DB column/table remain until manual SQL.
- Latency/token/API cost: no retrieval/chat prompt/token changes; frontend no longer waits artificial mock delays.
- MySQL/Qdrant old data: no data deleted by code. Manual SQL needed for orphan MySQL artifacts. Qdrant unchanged.

## 10. Edge cases đã xem xét

- Missing migration framework: documented manual SQL instead of adding fake migration.
- Existing `settings_profiles.notify_new_feedback`: removing JPA mapping does not drop column automatically; old DB column is ignored.
- Existing `chat_feedbacks`: no entity/repository/service references remain; table can be dropped manually.
- Old frontend env `VITE_USE_MOCK_API`: no active code reads it after `apiMode.js` deletion.
- Deleted mocks: scans confirm no active `../mocks`, `apiMode`, `USE_MOCK_API` references.
- False-positive scan: `generating` in `ApiKeysSection.jsx` matches regex substring `rating`; not rating/feedback logic.
- Historical reports: left untouched as audit history.

## 11. Kết quả kiểm tra

| Command | Kết quả | Ghi chú |
|---|---|---|
| `cd Backend && .\mvnw.cmd clean test` | PASS | First run failed due missing `java.util.List` import after repository edit; fixed import; rerun passed. |
| `cd Frontend && npm run build` | PASS | Vite build passed; chunk-size warning only. |
| `cd Frontend && npm run lint` | PASS | ESLint passed. |
| `docker compose config -q` | PASS | Compose config valid. |
| `cd Backend && .\mvnw.cmd -DskipTests compile` | NOT RUN | Covered by `clean test`. |
| `cd Frontend && npm run build:widget` | NOT RUN | Widget source not modified; task validation requested frontend build/lint. |

Stale active scan:

```text
rg feedback/satisfaction/rating/newFeedback/chat_feedbacks/USE_MOCK_API/apiMode/mocks over active Backend/src, Frontend/src, agent, docs/api, docs/architecture:
Only false positive: `generating` in ApiKeysSection.jsx.
```

## 12. Rủi ro còn lại

- Physical MySQL cleanup is not automatic because there is no migration framework.
- Historical docs/reports still mention old feedback/mock behavior as audit history.
- Any developer relying on local mock mode must now run real backend or add a separate test fixture strategy.

Manual DB cleanup needed:

```sql
ALTER TABLE settings_profiles DROP COLUMN notify_new_feedback;
DROP TABLE IF EXISTS chat_feedbacks;
```

Run only after backup/review on the intended database. Do not run against production blindly.

## 13. Đề xuất tiếp theo

- Add a real migration framework baseline (Flyway/Liquibase) before future schema cleanup.
- Add contract tests for analytics/dashboard/settings DTO fields to catch stale placeholders.
- Decide whether `LoginPage` dev-only mock login should be replaced by real auth in a separate auth hardening task.
