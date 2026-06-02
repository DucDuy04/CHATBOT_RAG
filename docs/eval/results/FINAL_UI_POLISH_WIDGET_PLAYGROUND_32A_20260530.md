# FINAL UI POLISH — Widget & Playground (32A)

**Date:** 2026-05-30 (eval doc id) / implemented 2026-06-01  
**Verdict:** **PASS** (build/lint/widget build); **PARTIAL** runtime (not executed in agent session)

## Summary

| Requirement | Status |
|---|---|
| Widget close button | Implemented |
| Playground total tokens + total latency | Implemented |
| Playground source scroll (~3 visible) | Implemented |
| Widget sources not hard-capped at 5 | Backend presentation cap → effective topK + scroll UI |
| No retrieval semantic change | Confirmed |

## Root cause

- Widget **display cap at 5** originated in **`ChatService.resolveSourcePresentationCap`** (`MAX_RESPONSE_SOURCES`), not frontend `slice(0,5)`.
- Playground source panel capped via **`PlaygroundPage` `displaySources.slice(0, topK)`**.
- Playground **latency** missing from SSE `done` JSON (only `sources` + `tokenUsage`).

## Files changed

- `Frontend/src/pages/WidgetChatPage.jsx`
- `Frontend/widget/widget.js`
- `Frontend/src/pages/playground/PlaygroundPage.jsx`
- `Frontend/src/pages/playground/components/RetrievalPanel.jsx`
- `Frontend/src/pages/playground/components/LatencyPanel.jsx`
- `Frontend/src/pages/playground/components/TokenUsagePanel.jsx`
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/rag/runtime/ChatService.java`
- `Frontend/public/dist-widget/` (rebuilt)

## Implementation notes

### Close button

- Header `×`, `aria-label="Đóng chat"`.
- `window.parent.postMessage({ type: "RAG_CHATBOT_CLOSE" }, "*")`.
- `widget.js` hides iframe, shows bubble.

### Playground metrics

- **Tokens:** `actualTotalTokens` → `estimatedTotalRequestTokens` → `N/A`.
- **Latency:** total seconds from `result.latency` (ms); breakdown under **Chi tiết**.

### Playground sources

- `max-h-[360px] overflow-y-auto`; all `lastSources` passed through.

### Widget sources

- Backend returns up to **configured effective topK** sources.
- Widget `<details>` list scrolls (`max-h-48`).

## Backend change

**Yes — minimal, presentation/metadata only:**

1. `resolveSourcePresentationCap` uses `topKResolution.effective()` for all clients (widget + playground).
2. Playground SSE `done` includes `"latency": <ms>`.

**Not changed:** retrieval, Qdrant, classifier, Top-N retrieval logic.

## Build results

| Check | Result |
|---|---|
| `npm run lint` | PASS |
| `npm run build` | PASS |
| `npm run build:widget` | PASS |
| `mvnw compile` | PASS |
| `mvnw test` | PASS (0 tests) |

## Runtime verification

| Check | Result |
|---|---|
| Widget open/close | NOT RUN |
| Playground metrics | NOT RUN |
| Playground source scroll | NOT RUN |
| Widget source count > 5 | NOT RUN (expected after backend cap fix + admin topK > 5) |

**Expected manual steps:** `docker compose up`, `npm run dev`, test `/widget?widgetKey=...` and `/playground` with Context Top-N 15–20.

## Screenshots

Not captured in this session.

## Remaining risks

- Compare mode columns still slice sources by config topK.
- Refusal answers may still cap sources to 2 (by design).

## Next task

Runtime verify Phase 8 acceptance; optional ComparePane source scroll parity.
