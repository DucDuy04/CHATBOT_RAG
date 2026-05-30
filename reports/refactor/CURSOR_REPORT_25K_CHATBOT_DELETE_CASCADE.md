# CURSOR_REPORT_25K — Chatbot Delete Cascade

**Date:** 2026-05-29  
**Verdict:** **PASS**

## Summary

`WidgetService.softDeleteChatbot()` now soft-deletes all **active** documents via `DocumentService.softDeleteDocument()` (Qdrant purge + DB cascade) **before** soft-deleting the chatbot. Six new unit tests; **71** tests pass. Runtime smoke on temp bot **PASS**; kept 5 chatbots / 3 docs **unchanged**.

## Code changed

| File | Change |
|------|--------|
| `WidgetService.java` | Inject `DocumentService`; cascade loop + `[ChatbotDelete]` log |
| `WidgetServiceSoftDeleteChatbotTest.java` | **NEW** — 6 tests |

No new service class (no circular dependency).

## Delete flow

**Before:** chatbot row only.  
**After:** active docs → `softDeleteDocument` each → chatbot row.

API: `DELETE /api/chatbots/{id}` (unchanged contract).

## Dependency decision

`WidgetService` → `DocumentService` only. `DocumentService` does not depend on `WidgetService`. **No** `ChatbotDeletionService`.

## Tests

- Added: `WidgetServiceSoftDeleteChatbotTest` (6 cases per task spec)
- `mvnw.cmd clean test`: **71** tests, **0** failures

## Runtime smoke

Temp chatbot `25K-cascade-delete-smoke` + TXT upload (1 chunk) + delete:

- chatbot active: 0
- document/chunks active: 0
- Qdrant `document_id` count: 0

## Kept data safety

| Check | Result |
|-------|--------|
| Active chatbots | 5 |
| Active COMPLETED docs | 3 |
| DB chunks | 9888 |
| Rank-1 smoke S2 | PASS |

## Risks

- Multi-document chatbot delete slower (sequential Qdrant purges)
- No live Qdrant integration test in default `mvn test`

## Next

Optional API response field `documentsDeleted`.

## Detail

`docs/eval/results/CHATBOT_DELETE_CASCADE_25K_20260529.md`
