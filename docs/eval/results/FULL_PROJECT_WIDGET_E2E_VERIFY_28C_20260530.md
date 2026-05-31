# FULL PROJECT + WIDGET E2E VERIFICATION — TASK 28C (2026-05-30)

## Final verdict: **PASS** (with DB physical cleanup note)

| Area | Check | Result | Evidence |
|------|-------|--------|----------|
| Backend unit tests | `mvnw clean test` | **PASS** | 120 tests, 0 failures |
| Frontend build | `npm run build` | **PASS** | Vite build OK |
| Frontend lint | `npm run lint` | **PASS** | ESLint clean |
| Widget build | `npm run build:widget` | **PASS** | `dist-widget/chatbot-widget.iife.js` + sync to `public/` |
| Docker config | `docker compose config -q` | **PASS** | exit 0 |
| Backend startup | docker logs | **PASS** | `Started RagChatbotBeApplication`; KeywordIndexPrewarm OK (3 widgets) |
| Stale code scan | rg Backend+Frontend src | **PASS** | No active feedback/mock/satisfaction refs |
| DB cleanup (physical) | MySQL SHOW | **PARTIAL** | `chat_feedbacks` table + `notify_new_feedback` column still exist; backend does not map them |
| DB/Qdrant parity | 3 COMPLETED docs | **PASS** | 3296 chunks = 3296 points each; collection total 9888 |
| Chat API exact | POST `/api/chat` | **PASS** | Answer contains Nguyễn Thị Vân Anh, thứ 2, tiết 1-2, E301 |
| Chat API OOS | POST `/api/chat` | **PASS** | Refusal; no fabricated USD/VND rate |
| Removed feedback | POST `/api/chat/feedback` | **PASS** | HTTP 404 |
| Dashboard/Analytics/Settings API | GET smoke | **PASS** | 200; no stale JSON fields |
| Admin frontend | Playwright headless | **PASS** | `/dashboard`, `/analytics`, `/settings`, `/chatbots`, `/documents` load; no stale strings |
| Widget E2E exact | Playwright `/widget` | **PASS** | Stream 200; answer contains expected facts |
| Widget E2E OOS | Playwright `/widget` | **PASS** | Refusal text; no rate hallucination |
| Public chat API | POST `/api/public/chat` | **PASS** | 200 with answer |

## Production code changed

**No.** Only verification helper: `docs/eval/scripts/widget_e2e_28c.mjs` (allowed).

## Environment

- OS: Windows 10
- Date: 2026-05-30
- Branch: `documents-architecture-final` (dirty working tree from 28A/28B)
- Backend: Docker `chatbot-backend` :8080, profile `docker`
- MySQL/Qdrant: Docker volumes (existing data)
- Frontend dev: Vite :5173 with `/api` proxy → :8080

## Phase 0 — Preflight

**Changed (tracked):** 28A/28B backend+frontend cleanup files (feedback/mock/satisfaction).

**Untracked:** eval reports 28A/28B, refactor reports.

**Docker compose config:** PASS.

## Phase 1 — Stale cleanup

**Active code (`Backend/src/main/java`, `Frontend/src`):** no matches for ChatFeedback, chat_feedbacks, /feedback, avgSatisfaction, notifyNewFeedback, USE_MOCK_API, apiMode, src/mocks.

**False positives (allowed):**

- `generating` in `ApiKeysSection.jsx` (substring `rating`)
- `Mock Login` on `LoginPage.jsx` (dev auth placeholder, not `USE_MOCK_API`)
- `Mock browser chrome` comment in `WidgetLivePreview.jsx`

**MySQL physical schema:**

```text
SHOW TABLES LIKE 'chat_feedbacks'  → 1 row (table still exists)
SHOW COLUMNS ... notify_new_feedback → column still exists
```

**Runtime:** Backend starts without missing-column errors because `SettingsProfile` no longer maps `notify_new_feedback` and no JPA entity uses `chat_feedbacks`.

## Phase 2 — Backend

```
Tests run: 120, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Phase 3 — Frontend

- `npm install` OK
- `npm run build` OK
- `npm run lint` OK
- `npm run build:widget` OK → IIFE 3.27 kB + CSS; synced to `public/dist-widget/`

Widget bundle: `vite.widget.config.js` → `dist-widget/chatbot-widget.iife.js`; embed via `window.RagChatbotConfig` + script tag (see `ChatbotEmbedPage.jsx`).

## Phase 4 — Docker

```
mysql, qdrant, backend Up
Started RagChatbotBeApplication in ~54s
KeywordIndexPrewarm: widgets=3, rank-1 widget 3a26c18c-... status=OK
No ERROR lines matching notify_new_feedback|chat_feedbacks|ChatFeedback in tail scan
```

## Phase 5 — DB / Qdrant

| Metric | Value |
|--------|-------|
| Active chatbots | 4 |
| Active documents | 3 (all COMPLETED) |
| Chunks per doc | 3296 each |
| Qdrant points per doc | 3296 each |
| Collection total | 9888 |

**Rank-1 widget (unchanged):**

- widgetId: `3a26c18c-bfd1-477e-af79-fcd7fba551a9`
- apiKey: `c2e09246-...-7191c8d` (masked)
- documentId: `7ae6d0b9-b7de-4bc8-8be6-10798add73aa`

## Phase 6 — API smoke

- **A exact:** 200, answer includes lecturer/schedule/room.
- **B OOS:** 200, “không tìm thấy thông tin này trong tài liệu”; no numeric USD/VND.
- **C feedback:** 404.
- **D:** `/api/dashboard/summary` 200; `/api/analytics/summary?from&to` 200; `/api/settings/profile` 200 — notifications only `embeddingFailed`, `dailySummary`.

## Phase 7 — Admin frontend

Playwright (mock dev login): pages load, no `avgSatisfaction`, `feedback`, `USE_MOCK_API` in DOM; no console errors.

## Phase 8 — Widget E2E

**Integration:**

- Embed script: `/dist-widget/chatbot-widget.iife.js`
- Iframe: `{frontendUrl}/widget?widgetKey=...`
- Chat: `POST /api/chat/stream` header `X-Widget-Key`
- Alternate: `POST /api/public/chat` header `x-api-key`

**Playwright (`docs/eval/scripts/widget_e2e_28c.mjs`):**

1. Open `http://localhost:5173/widget?widgetKey=c2e09246-...`
2. Send exact question → stream 200 → answer contains Nguyễn Thị Vân Anh, thứ 2, tiết 1, E301
3. Send OOS → refusal, no fabricated rate
4. Console errors: none

## Phase 9 — Mock mode

No `USE_MOCK_API`, no `src/mocks`, no `apiMode.js` in Frontend src. API modules call real backend only.

## Phase 10 — Upload smoke

**Skipped** — existing 3 COMPLETED docs intact; parity confirmed.

## Issues found

1. **DB physical cleanup not applied** on this Docker MySQL volume: `chat_feedbacks` + `notify_new_feedback` remain. Non-blocking for current code.
2. Historical docs/reports still mention removed features (allowed).

## Fixes applied

None to production code.

## Remaining risks

- Orphan DB artifacts until manual `DROP TABLE` / `DROP COLUMN` executed on production DB.
- `LoginPage` mock login remains dev-only; not product mock API mode.

## Next recommended task

- **28D:** Run documented SQL on all environments; confirm Hibernate `ddl-auto` will not recreate dropped objects; optional migration file for ops.
