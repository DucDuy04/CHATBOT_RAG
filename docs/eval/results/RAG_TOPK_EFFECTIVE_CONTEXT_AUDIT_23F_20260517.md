# Task 23F — Top-K Effective Context Audit

**Date:** 2026-05-17  
**Type:** AUDIT (diagnosis-first; no code changes)  
**Conclusion:** **PARTIAL**

---

## Executive summary

| Question | Answer |
|----------|--------|
| What does `topK` control today? | **Qdrant vector search `limit` per query variant** (`effectiveAnchorTopK`). Not final LLM context count. |
| Does `topK=1` limit prompt contexts to 1? | **No.** Pipeline expands to neighbors/sections/tables; `dedupeSortBudget` caps at **FINAL_LIMIT=10** (normal), **20** (LIST_ALL/table), **60** (locked scope). |
| Why can `topK=1` still answer “fully”? | Expansion + final limits + possibly one large chunk + chat history; **not** because topK is ignored. |
| Playground topK real effect? | **Yes** on vector anchor count; **partial** on sources (debug cap = effectiveTopK); **no** on final context ceiling. |
| Compare topK real effect? | **Yes** on vector anchors when `config.topK` sent; **no** modelConfig fallback; sources = all final contexts (uncapped). |
| Widget/modelConfig topK? | **Yes** when request omits topK (`ChatService.resolveRetrievalTopK` → MODEL_CONFIG). Source UI still ≤5. |
| Code fix needed? | **No** for wiring bugs. Semantics/documentation gap only unless product wants topK = final context limit. |

---

## 1. Top-K semantics inventory

### 1.1 Normalize & bounds

| Item | Location | Value |
|------|----------|-------|
| Normalize | `RagRetrievalService.normalizeAnchorTopK` | clamp `[1, 30]`, null → **30** |
| Precedence (chat paths) | `ChatService.resolveTopK` | request → `uiConfig.modelConfig.topK` → default (null candidate → 30) |
| Precedence (compare) | `PlaygroundService.runCompareOnce` | **only** `RagRetrievalService.parseTopKOverride(config)` — no modelConfig fallback |
| Playground normal | `PlaygroundController` → `ChatRequest.topK` + `playgroundDebugSources=true` | request/overrideParams topK |

### 1.2 Where `effectiveAnchorTopK` is used

```java
// RagRetrievalService.retrieveWithMetadata — STEP 3 only
anchors = embeddingService.search(variant, effectiveAnchorTopK, widgetId);
```

**Not used for:** rerank `topN`, `dedupeSortBudget` `finalLimit`, section expansion max, neighbor window, heading-lock DB fetch.

### 1.3 Constants that override “feeling” of topK

| Constant | Value | Role |
|----------|-------|------|
| `FINAL_LIMIT` | 10 | Max chunks to LLM (normal query) |
| `FINAL_LIMIT_EXPANDED` | 20 | LIST_ALL / TABLE / SECTION_SUMMARY / COUNT |
| `FINAL_LIMIT_LOCKED` | 60 | Heading lock or rerank-guided lock |
| `SECTION_EXPANSION_MAX_CHUNKS` | 12 (30 expanded) | Per anchor section pull from DB |
| `WINDOW_BEFORE` / `WINDOW_AFTER` | 1 / 2 | Neighbor chunks per vector anchor |
| Rerank `topN` | `ceil(FINAL_LIMIT * 1.3)` ≈ 13 | Independent of user topK |
| `MAX_RESPONSE_SOURCES` | 5 | API source display (production) |
| Playground debug cap | `effectiveTopK` | Source display only when `playgroundDebugSources=true` |

### 1.4 Pipeline map (after vector search)

```
topK → Qdrant limit (per variant)
  → collect chunk/section/table IDs from hits
  → [heading lock?] fetch ALL chunks in locked section tree
  → else: lexical anchors + section range expansion (≤12 chunks/section)
  → neighbor window (±1/+2) OR expanded-query section/sibling/table pulls
  → [rerank?] topN ≈ 13 (not topK)
  → [rerank-guided lock?] replace pool with full section tree
  → dedupeSortBudget → finalLimit 10/20/60 + char budget
  → PromptBuilder (all final contexts)
  → buildSourceDtos (cap 5 or effectiveTopK playground)
```

### 1.5 Response sources vs prompt context

`ChatService` comment (line 47–48): **“Max sources returned to client … does not limit LLM retrieval context.”**

Sources are built from **same** `RetrievedContext` list as prompt, then **capped separately** for API.

---

## 2. Flow-by-flow

### 2.1 Playground normal

| Stage | topK effect? | Evidence |
|-------|----------------|----------|
| FE sends topK | Yes | `playgroundApi.js` body `topK` + `overrideParams` |
| Backend effective | Yes | `PlaygroundController` → `ChatService.resolveRetrievalTopK` |
| Vector search | Yes | `embeddingService.search(..., effectiveAnchorTopK, ...)` |
| Expansion | **No** (can multiply chunks) | `expandSectionRanges`, `expandAroundAnchors`, locked scope |
| Rerank input pool | Indirect (smaller anchors → smaller pool often) | Pool not capped by topK |
| Final contexts to LLM | **No hard tie to topK** | `FINAL_LIMIT` etc. |
| Response sources | Capped at `effectiveTopK` | `playgroundDebugSources=true` (23D) |
| FE display | Also slices `lastSources` to topK | `PlaygroundPage` `displaySources` |

23D2 runtime (prior): topK=3 → 3 sources; topK=10 → 10 sources; logs `effective=3/10`, `cap=3/10`.

### 2.2 Compare mode

| Stage | topK effect? | Notes |
|-------|----------------|-------|
| Path | `POST /api/playground/compare` → `PlaygroundService` | Not `ChatService.chatStream` |
| topK | From `configA`/`configB` map only | `parseTopKOverride(config)` |
| modelConfig fallback | **No** | If FE omits topK → default anchor 30 |
| Session history | **No** | `List.of()` in prompt build |
| Sources | **All** final contexts | `buildSourceDtos` no cap |
| Same PromptBuilder | Yes | `buildUserPromptFromRetrievedContexts` |

### 2.3 Widget / public chat

| Path | Request topK | Backend |
|------|----------------|---------|
| Widget `WidgetChatPage` | **Not sent** | `{ sessionId, message }` only |
| Public `publicChatApi` | **Not sent** | `{ message, sessionId }` |
| Effective topK | `MODEL_CONFIG` or DEFAULT | `resolveRetrievalTopK` when `request.topK == null` |
| Sources | Cap **5** (2 if refusal) | Hides retrieval >5 in UI |

23D2: modelConfig topK=5/10 → logs `source=MODEL_CONFIG effective=5/10`; display still ≤5.

### 2.4 `/api/chat` direct

Same as widget: optional `ChatRequest.topK`; else modelConfig; sources cap 5.

### 2.5 Model Settings

Persisted `uiConfig.modelConfig.topK` (WidgetService default 5). **Runtime retrieval uses it** when request has no topK. **Not** “UI-only.”

---

## 3. Why `topK=1` can still answer completely

Ranked by source evidence:

1. **Final context limit is 10+, not 1** — `dedupeSortBudget` uses `FINAL_LIMIT`, not `topK`.
2. **Section expansion** — one vector hit → up to **12** chunks in that section (`SECTION_EXPANSION_MAX_CHUNKS`).
3. **Neighbor window** — each anchor → up to **4** chunks (`WINDOW_BEFORE=1`, `WINDOW_AFTER=2`).
4. **LIST_ALL / aggregate questions** — `isExpandedQuery` → section/sibling/table expansion + `FINAL_LIMIT_EXPANDED=20`.
5. **Heading / rerank-guided lock** — fetches **entire section subtree** (up to 60 chunks).
6. **Multiple query variants** — up to ~4 variants × 1 hit = more anchor IDs (still 1 per variant per search).
7. **Single chunk may contain full answer** — especially PDF sections.
8. **Chat history** — Playground/widget stream includes last 10 messages in prompt (not compare).
9. **Source count ≠ context count** — user may see 1 source while prompt has more chunks (less likely in playground debug after 23D).

**Not root cause:** topK ignored in backend wiring (it is applied to Qdrant `limit`).

---

## 4. Runtime matrix (task 23F)

| Matrix | Status | Notes |
|--------|--------|-------|
| A Playground topK 1/3/10 | **NOT RUN** | `localhost:8080` unreachable (curl exit 7) |
| B Compare A=1 B=10 | **NOT RUN** | Same |
| C Widget modelConfig 1 vs 10 | **NOT RUN** | Same |
| D `/api/chat` explicit topK | **NOT RUN** | Same |
| TOPK_CONTROL_TEST_DOCUMENT | **Created** | `docs/eval/manual/TOPK_CONTROL_TEST_DOCUMENT.txt` — upload + index required before matrix |

**Prior runtime used:** `docs/eval/results/PLAYGROUND_MODEL_CONTROLS_PARITY_RUNTIME_VERIFY_23D2_20260516.md` (Playground source cap + modelConfig effective topK).

**Expected when backend up + doc indexed:**

| topK | Single-fact query | “List all 5 codes” query |
|------|-------------------|---------------------------|
| 1 | Often PASS if best chunk/section has fact | Often **PARTIAL** or full if LIST_ALL expansion / lock |
| 3 | PASS with more anchors | May return >3 facts in answer, >3 in prompt |
| 10 | More anchors | Up to FINAL_LIMIT 10–20 in prompt regardless |

---

## 5. Scoring (task §8)

| Criterion | Result |
|-----------|--------|
| Each flow receives topK (where designed) | **PASS** (compare lacks modelConfig fallback — edge) |
| Logs prove topK affects retrieval candidate | **PASS** (23D2 + `[RAG] retrieval topK requested=… effective=…`) |
| final contexts > topK explained | **PASS** (expansion + FINAL_LIMIT) |
| Widget modelConfig topK | **PASS** |
| Production source cap ≠ topK | **PASS** (documented, not bug) |
| User expectation “topK = context size” | **FAIL** (semantic gap → overall **PARTIAL**) |

---

## 6. Fix recommendation

| Issue | Fix? |
|-------|------|
| topK only limits vector anchors | **No code change** unless product redefines semantics |
| UI label “Top-K” implies source/context count | Note for future UX; out of scope |
| Compare no modelConfig topK fallback | Optional minimal fix **only if** compare configs omit topK; FE always sends default 5 today |
| Add `[RAG][retrieval] afterExpansion=… finalContexts=…` | Optional; existing logs partially sufficient |

---

## 7. Production source cap

**Unchanged.** Still ≤5 in-scope, ≤2 refusal. Does **not** prove topK ineffective — only masks source count on widget/production.

---

## 8. Related files read

Backend: `RagRetrievalService`, `ChatService`, `PlaygroundController`, `PlaygroundService`, `ChatController`, `PublicChatController`, `EmbeddingService`, `RerankService`, `PromptBuilderService`, DTOs, `WidgetService`.

Frontend: `PlaygroundPage`, `playgroundApi.js`, `ModelOverridePanel`, `CompareConfigPanel`, `ComparePane`, `RetrievalPanel`, `ChatbotConfigPage`, `WidgetChatPage`, `publicChatApi.js`.

Docs: 22A, 22B2, 21J, 23D, 23D2 reports.
