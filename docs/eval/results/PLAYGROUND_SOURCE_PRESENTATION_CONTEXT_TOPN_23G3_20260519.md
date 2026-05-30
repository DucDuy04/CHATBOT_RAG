# Playground Source Presentation after Context Top-N — 23G3 (2026-05-19)

## Root cause

Playground normal uses `ChatService.chatStream()` with `playgroundDebugSources=true`.

Flow before fix:

1. `buildSourceDtosForResponse(contexts, effectiveTopN)` → correct cap (e.g. 6 sources).
2. `applyAnswerAwareSourceCap(answer, sources)` → if answer contains **any** refusal marker (`không tìm thấy thông tin`, …), cap to **2**.

Partial list answers (e.g. 6 policy codes + “Không tìm thấy … Epsilon và Eta”) matched refusal markers → sidebar showed **SOURCES (2)** despite `finalContexts=6`.

Compare Mode uses `PlaygroundService.buildSourceDtos()` — **no** `applyAnswerAwareSourceCap` → showed all contexts.

## Fix (Option A + B)

**File:** `ChatService.java`

1. **Playground debug bypass:** if `playgroundDebugSources=true`, skip refusal cap entirely.
2. **Pure refusal only:** production/widget use `isPureRefusalLikeAnswer()` — refusal phrase **and** no substantive facts (policy codes `WORD-123`, numbered lists, multiple `:` lines).

## Path comparison

| Path | Endpoint | Sources built | Refusal cap |
|------|----------|---------------|-------------|
| Playground normal | `POST /api/playground/chat` → `chatStream` | `buildSourceDtosForResponse(..., topN)` | Was: always if marker; Now: skip if debug |
| Compare | `POST /api/playground/compare` → `PlaygroundService` | All contexts → DTOs | Never applied |
| Widget/production | `POST /api/chat` | `buildSourceDtosForResponse` max 5 | Pure refusal only → ≤2 |

## Tests

| Test | Result |
|------|--------|
| `ChatServiceSourcePresentationTest` (12 tests) | PASS |
| Runtime Playground topK=6, list-all question | `sourcesInDoneEvent=6` PASS |

## Production regression

| Case | Expected | Status |
|------|----------|--------|
| Pure OOS “không tìm thấy…” | sources ≤2 | Unchanged (`isPureRefusalLikeAnswer`) |
| In-scope factual | sources ≤5 | Unchanged |
| Partial answer with codes | not forced to 2 | Improved (B) |

## Retrieval

**Not modified** — `RagRetrievalService`, `fixedVectorAnchorK`, Context Top-N semantics unchanged.

## Conclusion

**PASS**
