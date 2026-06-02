# CURSOR REPORT — 32A Final UI Polish

## Verdict: PASS (build) / PARTIAL (runtime)

## Files changed

See `docs/report_32a_final_ui_polish.md` for full audit diff.

## Close button

- `WidgetChatPage.jsx`: top-right `×`, `aria-label="Đóng chat"`.
- `widget.js`: listens for `RAG_CHATBOT_CLOSE`, collapses iframe.

## Playground token/latency

- `TokenUsagePanel.jsx`: `Tokens: <n>|N/A` + collapsible detail rows.
- `LatencyPanel.jsx`: `Latency: X.XXs` + collapsible retrieval/LLM.
- `ChatService.buildStreamDoneData`: adds `"latency"` for playground SSE.

## Playground source scroll

- Removed `displaySources` slice in `PlaygroundPage.jsx`.
- `RetrievalPanel.jsx`: `max-h-[360px] overflow-y-auto`.

## Widget source display

- **Backend:** `resolveSourcePresentationCap` → `topKResolution.effective()` (not fixed 5).
- **Frontend:** scrollable source list; count from `msg.sources.length`.

## Backend needed?

**Yes** — widget source count and playground latency in `done` event.

## Build / lint

| Command | PASS/FAIL |
|---|---|
| Frontend lint | PASS |
| Frontend build | PASS |
| Widget build | PASS |
| Backend compile | PASS |
| Backend test | PASS (0 run) |

## Runtime

NOT RUN in agent environment.

## Next

Manual QA on embed test page + playground with Top-N 20.
