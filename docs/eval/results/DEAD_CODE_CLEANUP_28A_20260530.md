# Dead Code Cleanup — Task 28A
**Date:** 2026-05-30  
**Verdict:** PASS

---

## Summary

Removed unused backend chat feedback stack (`POST /api/chat/feedback`), orphaned frontend feedback UI/components, and `feedbackMockData`. Kept analytics/dashboard satisfaction **fields** (always `null` from backend) and mock API dev fallback infrastructure. No DB migration — `chat_feedbacks` table left orphaned in schema.

---

## Inventory (before deletion)

| Item | Path | Type | Used by | Proposed action | Risk |
|---|---|---|---|---|---|
| `POST /api/chat/feedback` | `ChatController.java` | API endpoint | None (UI removed) | DELETE_FEEDBACK_ONLY | Low |
| `ChatFeedbackService` | `service/ChatFeedbackService.java` | Service | Endpoint only | DELETE_FEEDBACK_ONLY | Low |
| `ChatFeedback` entity | `domain/chat/ChatFeedback.java` | JPA entity | Service + Analytics (read) | DELETE_FEEDBACK_ONLY | Med — DB table remains |
| `ChatFeedbackRepository` | `domain/chat/ChatFeedbackRepository.java` | Repository | Service, Analytics, Dashboard | DELETE_FEEDBACK_ONLY | Med — remove query usage |
| `ChatFeedbackRequest/Response` | `dto/ChatFeedback*.java` | DTO | Endpoint | DELETE_FEEDBACK_ONLY | Low |
| Analytics rating filter | `AnalyticsController` `rating` param | API param | Removed MVP UI | DELETE_FEEDBACK_ONLY | Low — breaking unused param |
| `AnalyticsFeedbackTab` etc. | `Frontend/.../AnalyticsFeedbackTab.jsx` (+3) | UI components | Not in routes | DELETE_FRONTEND_MOCK_ONLY | Low |
| `submitFeedback()` | `analyticsApi.js` | API client | Dead after UI removal | DELETE_FEEDBACK_ONLY | Low |
| `feedbackMockData` | `analyticsMock.js` | Mock export | Removed tab | DELETE_FRONTEND_MOCK_ONLY | Low |
| `USE_MOCK_API` + `*Mock.js` | `Frontend/src/mocks/*`, `apiMode.js` | Dev fallback | All admin API modules | KEEP_DEV_FALLBACK | High if removed |
| `avgSatisfaction` / `rating` DTO fields | Analytics/Dashboard DTOs | Response fields | Frontend MetricCard still renders | KEEP_ACTIVE_API | Med |
| `SettingsNotificationsDto.newFeedback` | Settings DTO + DB column | Settings | Backend persists; UI toggle removed | REVIEW_MANUALLY | Low — no migration |
| `findAllForAnalyticsRange` | `ChatSessionRepository.java` | Repo method | Unused after rating filter removal | REVIEW_MANUALLY | Low — dead code |
| Historical reports mentioning feedback | `reports/refactor/*`, old eval docs | Audit artifacts | Reference only | KEEP (historical) | None |

---

## Backend feedback files removed

| File | Action |
|---|---|
| `Backend/.../service/ChatFeedbackService.java` | Deleted |
| `Backend/.../dto/ChatFeedbackRequest.java` | Deleted |
| `Backend/.../dto/ChatFeedbackResponse.java` | Deleted |
| `Backend/.../domain/chat/ChatFeedback.java` | Deleted |
| `Backend/.../domain/chat/ChatFeedbackRepository.java` | Deleted |

## Backend files updated

| File | Change |
|---|---|
| `api/ChatController.java` | Removed `/feedback` endpoint and `ChatFeedbackService` dependency |
| `api/AnalyticsController.java` | Removed `rating` query param from `GET /sessions` |
| `service/AnalyticsService.java` | Removed feedback repo, rating filter, satisfaction aggregation; session `rating` always `null` |
| `service/DashboardService.java` | Removed `ChatFeedbackRepository`; `avgSatisfaction` always `null` |

---

## DTO fields removed

| DTO | Fields removed |
|---|---|
| `ChatFeedbackRequest` | Entire class deleted |
| `ChatFeedbackResponse` | Entire class deleted |

## DTO fields kept (and why)

| Field | DTO | Reason kept |
|---|---|---|
| `rating` | `AnalyticsSessionItem` | Frontend analytics sessions table/CSV still references field; backend returns `null` |
| `avgSatisfaction`, `avgSatisfactionDelta` | `AnalyticsSummaryResponse`, `DashboardSummaryResponse` | Frontend dashboard/analytics MetricCards still display; backend returns `null` |
| `newFeedback` | `SettingsNotificationsDto` | DB column `notify_new_feedback` exists; backend read/write intact; UI toggle removed only |
| All chat/document/source fields | Various | Active API contract — not in scope |

No other DTO fields removed after backend + frontend + docs cross-check.

---

## Frontend mock files removed

| File | Action |
|---|---|
| `pages/analytics/components/AnalyticsFeedbackTab.jsx` | Deleted |
| `pages/analytics/components/FeedbackCommentsList.jsx` | Deleted |
| `pages/analytics/components/FeedbackRatingFilter.jsx` | Deleted |
| `pages/analytics/components/FeedbackSummaryCards.jsx` | Deleted |
| `analyticsMock.js` — `feedbackMockData` export | Removed |

## Frontend mock files kept (dev fallback)

All remaining `Frontend/src/mocks/*.js` files — still imported when `USE_MOCK_API=true` via `apiMode.js`.

## Frontend files updated

| File | Change |
|---|---|
| `api/analyticsApi.js` | Removed `submitFeedback()` |
| `mocks/analyticsMock.js` | Removed `feedbackMockData` |
| `pages/dashboard/DashboardPage.jsx` | Caption when `avgSatisfaction == null` |
| `pages/settings/components/ProfileSettingsSection.jsx` | Removed "New feedback" notification toggle |

---

## Docs updated

| File | Change |
|---|---|
| `agent/05-api.md` | Noted feedback endpoint removed |
| `docs/RAG_TARGET_ARCHITECTURE.md` | Marked feedback module removed; analytics satisfaction deprecated |

**Not updated (missing files):** `docs/api/API_REFERENCE_20260530.md`, `API_QUICKSTART_20260530.md`, `API_SMOKE_TESTS_20260530.md` — referenced in `agent/05-api.md` but do not exist in repo.

**Historical reports** under `reports/refactor/` still mention `ChatFeedbackService` — intentional audit history.

---

## Validation results

| Command | Result | Notes |
|---|---|---|
| `cd Backend && .\mvnw.cmd clean test` | **PASS** | 120 tests, 0 failures |
| `cd Frontend && npm install && npm run build` | **PASS** | Vite build OK |
| `cd Frontend && npm run lint` | **PASS** | ESLint clean |
| `docker compose config -q` | **PASS** | No errors |
| Runtime smoke (Docker chat) | **NOT RUN** | Optional per task |

## Stale feedback scan (active code)

```
Backend/src/main/java — no ChatFeedback, no /feedback endpoint
Frontend/src — only redirect guard for ?activeTab=feedback in AnalyticsPage.jsx
agent/05-api.md — documents removal
docs/RAG_TARGET_ARCHITECTURE.md — updated
```

---

## REVIEW_MANUALLY (deferred)

1. **`ChatSessionRepository.findAllForAnalyticsRange`** — dead method after rating filter removal; safe to delete in future cleanup.
2. **`SettingsProfile.notifyNewFeedback` / DB column** — orphaned setting; needs migration strategy to drop.
3. **`chat_feedbacks` MySQL table** — orphaned; no JPA entity; optional future migration.
4. **`AnalyticsSessionItem.rating` / satisfaction metrics** — consider removing from API + UI in dedicated UX task when replacement metric defined.
5. **Mock `rating` values in `analyticsMock.js`** — harmless dev display data while mock mode active.

---

## Risks remaining

- Orphaned `chat_feedbacks` table consumes disk only; no runtime access.
- Dashboard/analytics satisfaction cards show empty/null — expected until new metric.
- Old bookmarks to `?activeTab=feedback` redirect to overview (harmless).

---

## Next recommended task

- **28B (optional):** Drop dead repo method `findAllForAnalyticsRange`, remove satisfaction UI fields or replace with actionable metric, plan DB migration for `chat_feedbacks` + `notify_new_feedback`.
