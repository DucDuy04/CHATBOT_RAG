# Task 23D — Playground & Model Settings Controls Parity

**Date:** 2026-05-16  
**Status:** PASS (code + unit tests); manual runtime NOT RUN in this session

## Issue summary (user)

1. Playground normal: Top-K slider does not change visible source count (stuck at max 5); Compare Mode Top-K works.
2. Playground normal: temperature effect unclear.
3. Chatbot Model Settings: Top-K seems ineffective; maxTokens PASS.
4. Compare Mode: right sidebar (sources/model override) should hide; only compare area visible.

## Flow analysis

### Playground normal

| Step | Finding |
|------|---------|
| Endpoint | `POST /api/playground/chat` → `PlaygroundController` → `ChatService.chatStream` |
| FE payload | `topK`, `temperature`, `maxTokens` (+ `overrideParams`) via `playgroundApi.js` |
| Retrieval topK | Wired via `resolveRetrievalTopK` — effective topK applied to RAG |
| Response sources | `buildSourceDtosForResponse` hard-capped at **5** (`MAX_RESPONSE_SOURCES`) |
| Compare path | `PlaygroundService.compare` → `buildSourceDtos` **no cap** |

### Model Settings

| Step | Finding |
|------|---------|
| Save | FE sends `modelConfig.topK` in PUT `/api/chatbots/:id` |
| Persist | `WidgetService.updateChatbot` merges into `uiConfig.modelConfig` |
| Runtime | `ChatService.resolveRetrievalTopK` uses `modelConfig.topK` when request topK null |
| User observation | Likely judged by **source count in UI** (still capped at 5 on `/api/chat`) |

### Compare layout

Right column rendered unconditionally in `PlaygroundPage.jsx` (`hidden lg:flex`).

## Root cause

| Issue | Root cause |
|-------|------------|
| Playground Top-K / source count | Production source presentation cap (21J) applied to playground stream path |
| Playground temperature | Already wired FE→BE→LLM (22C); no code bug found |
| Model Settings Top-K | Save/runtime path correct; visible source count on public chat still capped at 5 |
| Compare sidebar | No conditional on `compareMode` |

## Code changes

### Backend

- `ChatRequest.playgroundDebugSources` — playground-only flag
- `PlaygroundController` sets flag on playground chat
- `ChatService.resolveSourcePresentationCap` — playground uses effective topK; production stays 5
- `buildSourceDtosForResponse(contexts, maxSources)` overload

### Frontend

- `PlaygroundPage.jsx` — hide right sidebar when `compareMode`
- `ChatbotConfigPage.jsx` — sync form from API after model save
- `chatbotsApi.js` mock — include `topK` in default modelConfig

## Tests / build

| Command | Result |
|---------|--------|
| `mvnw -DskipTests compile` | PASS |
| `mvnw test -Dtest=ChatServiceSourcePresentationTest,ChatServiceModelConfigTopKTest` | PASS (18 tests) |
| `npm run lint` | PASS |
| `npm run build` | PASS |

## Manual verify (expected)

- Playground topK=3/10: source count follows topK (≤10), can exceed 5 when retrieval has enough
- Playground temperature: logs show `effectiveTemperature` per 22C
- Model Settings: save/reload topK; chat without request topK → log `topK source=MODEL_CONFIG`
- Compare: sidebar hidden; A/B still works
- Regression: `/api/chat` still returns ≤5 sources

## Production source cap

**Unchanged** for `/api/chat`, widget, public routes. Only `/api/playground/chat` sets `playgroundDebugSources=true`.

## Conclusion

**PASS** for scoped fixes. Model Settings topK runtime was already correct for retrieval; user-facing source count on production chat remains capped by design.
