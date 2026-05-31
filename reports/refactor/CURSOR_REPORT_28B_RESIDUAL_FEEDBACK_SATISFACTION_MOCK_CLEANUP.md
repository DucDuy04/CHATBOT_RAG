# CURSOR REPORT 28B — Residual Feedback / Satisfaction / Mock Cleanup

## Final verdict

**PASS** for source cleanup and validation.

DB cleanup status: **manual SQL documented** because repo has no Flyway/Liquibase migration system.

## Inventory before deletion

| Item | Path | Type | Used by | Proposed action | Risk |
|---|---|---|---|---|---|
| `avgSatisfaction`, `avgSatisfactionDelta` | Backend analytics/dashboard DTOs/services | API field | Frontend cards/CSV | DELETE_SATISFACTION_METRIC | Medium contract change requested by user |
| `rating` | `AnalyticsSessionItem`, old analytics logic | API field | Removed sessions rating UI | DELETE_SATISFACTION_METRIC | Low |
| `satisfaction` | `DashboardTopChatbotItem`, top chatbot UI | API/UI field | Dashboard list | DELETE_SATISFACTION_METRIC | Low |
| `newFeedback` | `SettingsNotificationsDto`, `SettingsService` | API setting | Settings profile contract | DELETE_SETTINGS_FEEDBACK_NOTIFICATION | Low |
| `notifyNewFeedback` / `notify_new_feedback` | `SettingsProfile` | DB/entity column | Settings entity only | DELETE_SETTINGS_FEEDBACK_NOTIFICATION | Medium DB cleanup deferred |
| `chat_feedbacks` | Active schema only / old docs | DB table | No active entity/repo/service | DELETE_FEEDBACK_RESIDUAL | Medium DB cleanup deferred |
| `findAllForAnalyticsRange` | `ChatSessionRepository` | Repository method | Removed rating filter only | DELETE_FEEDBACK_RESIDUAL | Low |
| `USE_MOCK_API`, `apiMode.js`, `src/mocks/*` | Frontend API clients | Mock infra | Active API modules | DELETE_FRONTEND_MOCK_INFRA | Medium; fixed by real API only |
| `Mock Login` | `LoginPage.jsx` | Dev auth placeholder | Login page | KEEP_ACTIVE_REAL_API | Out of scope; not mock API data |
| Historical reports | `docs/eval/results/*`, `reports/refactor/*` | Audit docs | Reviewer history | KEEP_TEST_FIXTURE | None |

## Backend updates

- Removed satisfaction fields from `AnalyticsSummaryResponse` and `DashboardSummaryResponse`.
- Removed `rating` from `AnalyticsSessionItem`.
- Removed `satisfaction` from `DashboardTopChatbotItem`.
- Removed `newFeedback` from `SettingsNotificationsDto`.
- Removed service setters/aggregators for satisfaction/rating.
- Removed `SettingsProfile.notifyNewFeedback` entity mapping.
- Removed dead `ChatSessionRepository.findAllForAnalyticsRange`.

## Settings field cleanup

Active code no longer reads/writes:

- `SettingsNotificationsDto.newFeedback`
- `SettingsService` `newFeedback` mapping
- `SettingsProfile.notifyNewFeedback`

Manual DB cleanup needed:

```sql
ALTER TABLE settings_profiles DROP COLUMN notify_new_feedback;
```

## DB table cleanup

No active `ChatFeedback` entity/repository/service/API remains. No migration framework exists under `Backend/src/main/resources/db/migration`, so physical cleanup is documented as manual SQL:

```sql
DROP TABLE IF EXISTS chat_feedbacks;
```

## Frontend updates

- Removed analytics satisfaction metric card and CSV export row.
- Removed dashboard avg satisfaction card.
- Removed top-chatbot satisfaction rendering.
- Removed old feedback tab guard from analytics page.
- API clients now always call real backend.
- Deleted `Frontend/src/api/apiMode.js`.
- Deleted `Frontend/src/mocks/*.js`.
- Removed `VITE_USE_MOCK_API` docs from `Frontend/.env.example`.

## Docs updated

- `agent/05-api.md`
- `docs/api/API_REFERENCE_20260530.md`
- `docs/api/API_SMOKE_TESTS_20260530.md`
- `docs/RAG_TARGET_ARCHITECTURE.md`
- `docs/RAG_CORE_FLOW_E2E_RUNBOOK.md`
- `docs/eval/results/RESIDUAL_FEEDBACK_SATISFACTION_MOCK_CLEANUP_28B_20260530.md`
- `reports/refactor/CURSOR_REPORT_28B_RESIDUAL_FEEDBACK_SATISFACTION_MOCK_CLEANUP.md`

## Stale scan result

Active scan over backend source, frontend source, API docs, architecture docs, agent docs, README:

```text
No active feedback/satisfaction/rating/newFeedback/chat_feedbacks/USE_MOCK_API/apiMode/mocks references remain.
Only false positive: `generating` in ApiKeysSection.jsx contains substring `rating`.
```

Frontend mock scan:

```text
No USE_MOCK_API/apiMode/mocks/../mocks/mockData references remain.
```

## Validation

| Command | Result | Notes |
|---|---|---|
| `cd Backend && .\mvnw.cmd clean test` | PASS | First compile attempt failed from missing `List` import after repo edit; fixed and reran PASS. |
| `cd Frontend && npm run build` | PASS | Vite build passed with existing chunk-size warning. |
| `cd Frontend && npm run lint` | PASS | ESLint passed. |
| `docker compose config -q` | PASS | Compose config valid. |
| `cd Frontend && npm run build:widget` | NOT RUN | Widget source not changed in scope. |

## Remaining risks

- Old DB table/column remain until manual SQL or future migration framework.
- Historical reports still mention old feedback/mock artifacts by design.
- Removing mock API mode means local frontend development needs a running backend.

## Next recommended task

Add a proper schema migration baseline (Flyway/Liquibase) and then apply the documented SQL in a controlled DB migration.
