# FINAL UI POLISH — Playground Collapse & Direct Answer (32B)

**Date:** 2026-06-01  
**Verdict:** **PASS** (build/lint/tests); **PARTIAL** runtime (manual smoke not run in agent)

## Summary

| Requirement | Status |
|---|---|
| Playground source collapse/expand | Implemented |
| Collapsed = one line `Nguồn (N)` | Implemented |
| Token UI removed from Playground | Implemented |
| Total latency only (details collapsed) | Implemented |
| No "Theo Source..." in answers | Prompt + `stripLeadingSourcePreamble` + playground `done.answer` |
| Source cards/metadata preserved | Unchanged |
| Retrieval semantics unchanged | Confirmed |

## Root cause

- LLM labels context as `[Source N]` → answers often start with "Theo Source N, …".
- 32A added token panel; 32B removes presentation only.

## Files changed

- `Frontend/src/pages/playground/components/RetrievalPanel.jsx`
- `Frontend/src/pages/playground/components/LatencyPanel.jsx`
- `Frontend/src/pages/playground/PlaygroundPage.jsx`
- `Frontend/src/pages/playground/components/ComparePane.jsx`
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/rag/prompt/PromptBuilderService.java`
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/rag/runtime/ChatService.java`
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/rag/runtime/PlaygroundService.java`
- `Backend/src/test/java/KLTN/RAG_CHATBOT_BE/rag/runtime/ChatServiceAnswerStyleTest.java`

## Implementation

### Source collapse

- Toggle `▶/▼` + `Nguồn (count)`; `aria-expanded` / `aria-label`.
- Default expanded; collapsed hides card list.

### Token removal

- Removed `TokenUsagePanel` from `PlaygroundPage`.
- Removed token block from `ComparePane`.
- Backend `tokenUsage` in API unchanged.

### Latency

- Display: `Latency: X.XXs` only.
- Retrieval/LLM ms behind optional "Chi tiết" (default hidden).

### Direct answer

- System prompt section **PHONG CÁCH TRẢ LỜI** (rules 4a–4e).
- `ChatService.stripLeadingSourcePreamble()` for sync, stream complete, compare.
- Playground SSE `done` includes `"answer"`; UI replaces bubble content on done.

### Post-processing

- **Yes** — safe leading-only regex; 6 unit tests.

## Build / test

| Check | Result |
|---|---|
| Frontend lint | PASS |
| Frontend build | PASS |
| Widget build | PASS |
| Backend compile | PASS |
| ChatServiceAnswerStyleTest (6) | PASS |
| docker compose config | PASS |

## Runtime verification

NOT RUN — recommended manual checks in task Phase 8.

## Remaining risks

- Brief streamed preamble on widget before stream ends.
- Rare preamble variants not matched by regex.

## Next task

Manual QA on Playground with Top-N 20; verify military schedule / Tết holiday questions.
