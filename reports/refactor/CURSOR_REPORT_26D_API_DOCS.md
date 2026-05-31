# CURSOR_REPORT_26D — API Documentation Standardization

**Date:** 2026-05-30  
**Verdict:** **PASS**

## Summary

Created/updated API documentation from actual REST controllers, DTOs, and security config. **No production Java code changed.** Validation: `mvn clean test` PASS; `docker compose config -q` PASS.

## Controllers inspected (10 classes, 9 `@RestController`)

| Controller | Base path | Endpoints |
|------------|-----------|-----------|
| `ChatbotController` | `/api/chatbots` | 7 |
| `WidgetController` | `/api/widgets` | 1 |
| `DocumentController` | `/api/documents` | 8 |
| `ChatController` | `/api/chat` | 3 |
| `PublicChatController` | `/api/public` | 1 |
| `PlaygroundController` | `/api/playground` | 5 |
| `AnalyticsController` | `/api/analytics` | 6 |
| `DashboardController` | `/api/dashboard` | 4 |
| `SettingsController` | `/api/settings` | 5 |

**Total documented:** 40 endpoint mappings (including legacy + canonical upload paths).

**Not found:** `ChatFeedbackController` (feedback is in `ChatController`); no Spring Actuator health endpoints.

## Files created/updated

| Path | Action |
|------|--------|
| `docs/api/API_REFERENCE_20260530.md` | Created — full reference |
| `docs/api/API_QUICKSTART_20260530.md` | Created — quickstart |
| `docs/api/API_SMOKE_TESTS_20260530.md` | Created — S1–S6 smoke tests |
| `agent/05-api.md` | Rewritten — AI/Cursor API guide |
| `README.md` | Updated — API doc links |
| `agent.md` | Updated — API doc links |
| `agent/04-runbook.md` | Updated — smoke test link |
| `agent/06-operations.md` | Updated — API doc links |
| `docs/26D_API_DOCS_STANDARDIZATION.md` | Created — rule 90 report |
| `reports/refactor/CURSOR_REPORT_26D_API_DOCS.md` | Created — this report |

## Auth / security notes

| Path | Auth |
|------|------|
| `/api/chat`, `/api/chat/stream` | `WidgetAuthFilter` → `X-Widget-Key` required |
| `/api/public/chat` | `WidgetAuthFilter` → `x-api-key` (preferred) or `X-Widget-Key` |
| `/api/chat/feedback` | **No** widget filter (open) |
| Admin: chatbots, documents, playground, analytics, dashboard, settings | `SecurityConfig` **permitAll** |

Documented honestly: no JWT/OAuth; admin APIs need hardening before production.

## Delete / cascade notes

- `DELETE /api/documents/{id}` → Qdrant purge by `document_id` + soft-delete DB rows; `502` if purge fails.
- `DELETE /api/chatbots/{id}` → cascade `DocumentService.softDeleteDocument` for each active doc → soft-delete chatbot.
- Qdrant application path: REST `:6333` only (not gRPC).

## Smoke tests documented

S1 create chatbot → S2 upload eval DOCX → S3 DB/Qdrant parity → S4 schedule question (Vân Anh, T2, tiết 1-2, E301) → S5 OOS USD/VND → S6 cascade delete with Qdrant=0.

## Stale guidance fixed

| Old (`agent/05-api.md`) | Fixed |
|-------------------------|-------|
| `DELETE /api/documents/{id}` "Chưa implement" | Documented as implemented |
| `GET /api/chatbots/{id}` "Chưa implement" | Documented |
| Sources with `pageStart`/`pageEnd` | Corrected to `pages` string in `SourceDto` |
| PDF/TXT only upload | PDF, DOCX, TXT |
| Missing public/playground/analytics/dashboard/settings | All added |

## Validation results

| Command | Result |
|---------|--------|
| `cd Backend; .\mvnw.cmd clean test` | **PASS** (exit 0, 71 tests baseline) |
| `docker compose config -q` | **PASS** |
| Stale scan (Grep README, agent, docs/api) | **PASS** — no stale active API guidance |
| Endpoint consistency (grep controllers vs docs) | **PASS** — 40 mappings aligned |

Frontend lint/build: **NOT RUN** (docs-only).

## Production code changed

**None.**

## Remaining risks

- `POST /api/documents/{id}/assign` documented as always 400 — may confuse FE if UI exposes it.
- `POST /api/chat/feedback` unauthenticated — noted in hardening section.
- Smoke test S4/S5 expected answers depend on eval DOCX content and LLM behavior — semantic match, not exact string.
- Settings API keys vs widget `apiKey` distinction must stay clear for integrators.

## Next recommended task

1. Optional: OpenAPI/Swagger generation from controllers (if desired for thesis appendix).
2. Optional: refresh `agent/04-frontend.md` with canonical `/api/public/chat` + `x-api-key` header.
3. Optional: protect admin endpoints or add dev-only API key middleware.

## Acceptance mapping

| Criterion | Status |
|-----------|--------|
| API reference created | ✓ |
| Quickstart created | ✓ |
| Smoke tests doc created | ✓ |
| agent/05-api.md updated | ✓ |
| README/agent links updated | ✓ |
| Endpoints from actual controllers | ✓ |
| Auth truth documented | ✓ |
| Delete cascade documented | ✓ |
| No production code change | ✓ |
| mvn test + compose config | ✓ |
| **Verdict** | **PASS** |
