# Task 23F2 — Top-K Effective Context Runtime Matrix

**Date:** 2026-05-17  
**Type:** VERIFY ONLY (no code changes)  
**Baseline audit:** 23F  
**Conclusion:** **PARTIAL** (wiring PASS; semantic gap confirmed by logs)

---

## Environment

| Component | Status | Notes |
|-----------|--------|-------|
| `docker compose up --build -d` | **PASS** | backend, mysql, qdrant, frontend |
| `GET /api/chatbots?page=0&size=1` | **PASS** | HTTP 200 |
| `GET http://localhost:6333/collections` | **PASS** | Qdrant reachable |
| Script | `docs/eval/results/_run_23f2_runtime_matrix.ps1` | Raw JSON: `_run_23f2_results.json` |

## Test document

| Field | Value |
|-------|-------|
| File | `docs/eval/manual/TOPK_CONTROL_TEST_DOCUMENT.txt` |
| CHATBOT_ID | `22eb7cf4-f5d4-4729-868e-eca6d0de921b` |
| DOCUMENT_ID | `aa2cb0d5-50df-4c7c-8476-328b4434bce9` |
| Index | **INDEXED** |
| chunkCount | **5** (one chunk per section Alpha–Epsilon) |
| WIDGET_API_KEY | `9c0f...f297` (masked) |

---

## Log fields available / missing

| Field | Available? | Log pattern |
|-------|------------|-------------|
| effectiveTopK | **Yes** | `[RAG] retrieval topK source=… effective=N` / `requested=N, effective=N` |
| vectorHits | **Yes** | `[RAG] Vector anchors: chunks=N` |
| afterExpansion | **Partial** | `Section range expansion: N chunks` (no single “afterExpansion” counter) |
| finalContexts | **Yes** | `[RAG] Final context chunks: N` |
| dedupe budget | **Yes** | `[RAG] dedupeSortBudget: input=… output=…` |
| prompt context count | **No dedicated log** | Infer from `Final context chunks` (= contexts passed to PromptBuilder) |
| responseSources | **Yes** | `Response sources capped: X retrieved → … → Y returned (cap=Z)` |

---

## Matrix A — Playground normal (`POST /api/playground/chat`)

| topK | question | answer summary | effectiveTopK | vectorAnchors | finalContexts | responseSources | verdict | notes |
|------|----------|----------------|---------------|---------------|---------------|-----------------|---------|-------|
| 1 | Alpha | ALPHA-ONLY-111 | 1 | 1 | **1** | 1 | PASS | `dedupeSortBudget output=1`; rerank-lock path (`locked=true` in dedupe log) |
| 1 | Epsilon | EPSILON-ONLY-555 | 1 | 1 | **1** | 1 | PASS | Same pattern |
| 1 | Aggregate | All 5 codes listed | 1 | 1 | **5** | **1** | PASS* | **Expansion:** `Section range expansion: 5 chunks` → prompt has 5 contexts; UI shows 1 source (`cap=1`) |
| 1 | Oos | Refusal (no Zeta) | 1 | 1 | **5** | 1 | PARTIAL | Correct answer; still 5 contexts in prompt / irrelevant source shown |
| 3 | Alpha | ALPHA-ONLY-111 | 3 | 3 | 1 | 1 | PASS | |
| 3 | Epsilon | EPSILON-ONLY-555 | 3 | 3 | 1 | 1 | PASS | |
| 3 | Aggregate | All 5 codes | 3 | 3 | 5 | 3 | PASS | `cap=3` matches topK |
| 3 | Oos | Refusal | 3 | 3 | 5 | 2 | PARTIAL | |
| 10 | Alpha | ALPHA-ONLY-111 | 10 | 5* | 1 | 1 | PASS | *Doc has only 5 chunks → anchors max 5 |
| 10 | Epsilon | EPSILON-ONLY-555 | 10 | 5 | 1 | 1 | PASS | |
| 10 | Aggregate | All 5 codes | 10 | 5 | 5 | 5 | PASS | |
| 10 | Oos | Refusal | 10 | 5 | 5 | 2 | PARTIAL | |

**Playground topK wiring:** **PASS** — every row `effectiveTopK` matches request.

**Key runtime proof (aggregate, topK=1):**

```text
[RAG] retrieval topK source=REQUEST … effective=1
[RAG] retrieval topK requested=1, effective=1
[RAG] Vector anchors: chunks=1 sections=1 …
[RAG] Section range expansion: 5 chunks
[RAG] dedupeSortBudget: input=5 unique=5 output=5 …
[RAG] Final context chunks: 5 | queryType=NORMAL_FACT
[Chat] Response sources capped: 5 retrieved → 5 deduped → 1 returned (cap=1)
```

---

## Matrix B — Compare (`POST /api/playground/compare`)

Config A: `topK=1`, Config B: `topK=10`, `temperature=0.2`, `maxTokens=512`

| side | topK | question | answer | sourceCount | finalContexts (log) | verdict |
|------|------|----------|--------|-------------|---------------------|---------|
| A | 1 | Alpha | ALPHA-ONLY-111 | 1 | 1 | PASS |
| B | 10 | Alpha | ALPHA-ONLY-111 | 1 | 1 | PASS |
| A | 1 | Aggregate | All 5 codes | **5** | **5** | PASS |
| B | 10 | Aggregate | All 5 codes | **5** | **5** | PASS |

**Compare logs (same request, two retrievals):**

```text
# Config A
[RAG] retrieval topK requested=1, effective=1
[RAG] Vector anchors: chunks=1
[RAG] Section range expansion: 5 chunks
[RAG] Final context chunks: 5   # aggregate

# Config B (immediate after)
[RAG] retrieval topK requested=10, effective=10
[RAG] Vector anchors: chunks=5
[RAG] Section range expansion: 5 chunks
[RAG] Final context chunks: 5   # aggregate — same final count
```

**Compare topK wiring:** **PASS** (effective 1 vs 10 in logs).  
**Compare behavioral difference on this doc:** **minimal for aggregate** — section expansion dominates; both sides get 5 prompt contexts. Compare sources **not capped** at 5 (both show 5 sources).

---

## Matrix C — Widget / modelConfig (`POST /api/chat`, no request topK)

### Test 1 — `modelConfig.topK=1`

| question | effectiveTopK | source | finalContexts | responseSources | codes in answer | verdict |
|----------|---------------|--------|---------------|-----------------|-----------------|---------|
| Alpha | 1 | MODEL_CONFIG | 1 | 1 | 1 | PASS |
| Aggregate | 1 | MODEL_CONFIG | **5** | **5** | 5 | PASS |

Log sample:

```text
[RAG] retrieval topK source=MODEL_CONFIG requested=null configured=1 effective=1
[RAG] Vector anchors: chunks=1
[RAG] Section range expansion: 5 chunks
[RAG] Final context chunks: 5
```

### Test 2 — `modelConfig.topK=10`

| question | effectiveTopK | source | finalContexts | responseSources |
|----------|---------------|--------|---------------|-----------------|
| Alpha | 10 | MODEL_CONFIG | **1** | 1 |

Log: `source=MODEL_CONFIG configured=10 effective=10`; `Vector anchors: chunks=5`; `Final context chunks: 1` (rerank-lock on single-fact).

**Widget/modelConfig topK:** **PASS** — effective follows saved config.

**Source cap note:** Aggregate with topK=1 returned **5** response sources (all chunks in doc ≤ production cap 5). Cannot use sourceCount alone to judge topK; must use logs.

---

## Matrix D — Direct `/api/chat` explicit topK

| topK | question | effectiveTopK | finalContexts | responseSources | codes | verdict |
|------|----------|---------------|---------------|-----------------|-------|---------|
| 1 | Aggregate | 1 | 5 | 5 | 5 | PASS |
| 10 | Aggregate | 10 | 5 | 5 | 5 | PASS |

Logs: `source=REQUEST effective=1` vs `effective=10`; vector anchors **1 vs 5**; both `Final context chunks: 5` after section expansion.

---

## Answers to task questions (runtime-based)

### 1. topK=1 — vectorHits?

**1** unique anchor chunk IDs per variant aggregate (`Vector anchors: chunks=1`). With multiple query variants, still deduped to 1 section hit on this 5-chunk doc.

### 2. topK=1 — finalContexts to prompt?

- **Single-fact (Alpha/Epsilon):** **1**
- **Aggregate list:** **5** (all sections in test doc)
- **OOS:** **5** (same expansion; answer still refusal)

### 3. topK=1 — responseSources?

- **Playground:** **1** on aggregate (cap=1) despite 5 prompt contexts
- **Compare:** **5** (uncapped)
- **`/api/chat`:** **5** (≤ production cap; doc has only 5 chunks)

### 4. Why topK=1 can still answer “fully”?

| Factor | Runtime evidence |
|--------|------------------|
| **Section expansion** | **Primary** — `Section range expansion: 5 chunks` after 1 vector hit on 5-chunk doc |
| Chunk large enough | Secondary — each section is one small chunk; expansion still needed for aggregate |
| LIST_ALL intent | **Not triggered** — `queryType=NORMAL_FACT` even for “Liệt kê…” |
| History | Not in compare; Playground stream uses session history but aggregate test still 5 contexts from expansion |
| Source display misleading | **Yes** — Playground `cap=1` while prompt has 5 contexts |

### 5–7. Flow pass?

| Flow | Wiring | Effect on vector | Effect on final prompt (this doc) |
|------|--------|------------------|-----------------------------------|
| Playground | **PASS** | Yes | Aggregate: 5 contexts even at topK=1 |
| Compare | **PASS** | Yes | A vs B same final=5 on aggregate |
| Widget modelConfig | **PASS** | Yes | Same expansion behavior |

### 8. Bug vs semantic gap?

**Semantic gap only** — no wiring bug. Top-K works as **anchor limit**; **section range expansion** on small multi-section doc collapses difference for list-style questions.

---

## Scoring

| Criterion | Result |
|-----------|--------|
| Playground receives topK | PASS |
| Compare receives topK | PASS |
| Widget modelConfig topK | PASS |
| Logs prove effective topK changes | PASS |
| finalContexts > topK explained | PASS (section expansion + 5-chunk doc) |
| Wiring bug | **None found** |
| User expectation “topK = context size” | **Not met** → **PARTIAL** overall |

---

## Conclusion

**PARTIAL**

- **PASS:** All flows wire topK correctly; runtime logs show `effective=1/3/10` and `Vector anchors` scaling.
- **PARTIAL:** On `TOPK_CONTROL_TEST_DOCUMENT` (5 chunks), **section range expansion** routinely produces **5 final contexts** even when `topK=1`; Playground **responseSources** can be **1** while prompt still has **5** contexts.
- **Not FAIL:** No case of stuck default topK or missing modelConfig.

## Re-run

```powershell
powershell -File docs/eval/results/_run_23f2_runtime_matrix.ps1
```
