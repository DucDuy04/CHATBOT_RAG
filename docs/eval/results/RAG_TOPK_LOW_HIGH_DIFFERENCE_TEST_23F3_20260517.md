# Task 23F3 — Top-K Low vs High Difference Test

**Date:** 2026-05-17  
**Type:** VERIFY ONLY (no code changes)  
**Conclusion:** **PARTIAL**

---

## Environment

| Item | Value |
|------|-------|
| Docker stack | UP (`docker compose up --build -d`) |
| Backend health | HTTP 200 |
| Qdrant | reachable |
| Script | `docs/eval/results/_run_23f3_low_high_diff_test.ps1` |
| Raw JSON | `docs/eval/results/_run_23f3_results.json` |

## Test document

| Field | Value |
|-------|-------|
| File | `docs/eval/manual/TOPK_LOW_HIGH_DIFFERENCE_TEST_DOCUMENT.txt` |
| CHATBOT_ID | `bed4feee-2946-43a9-8238-b1a04f7c6cac` |
| DOCUMENT_ID | `ee587096-4fe9-48c3-b197-ff98c6045cbb` |
| WIDGET_API_KEY | `254c...e426` (masked) |
| chunkCount | **29** |
| Index | INDEXED |

8 policy sections (Alpha→Theta) + 21 noise sections.

---

## Executive comparison (question A2 — aggregate codes)

Expected 8 codes: `ALPHA-111` … `THETA-888`.

| topK | effectiveTopK | vectorAnchors | finalContexts | responseSources | foundCodes | Missing |
|------|---------------|---------------|---------------|-----------------|------------|---------|
| 1 | 1 | **1** | **20** | 1 | **6/8** | ETA-777, THETA-888 |
| 3 | 3 | **3** | **20** | 2 | **6/8** | same |
| 10 | 10 | **10** | **20** | 2 | **6/8** | same |
| 20 | 20 | **20** | **20** | 2 | **6/8** | same |

**Answer text:** All topK levels explicitly say *không tìm thấy Eta và Theta* and list the same 6 codes.

### Interpretation

| Metric | Changes with topK? |
|--------|-------------------|
| **effectiveTopK** | **Yes** — logs match request |
| **vectorAnchors** | **Yes** — 1 → 3 → 10 → 20 |
| **finalContexts** | **No** for A2 — stuck at **20** (`FINAL_LIMIT_EXPANDED` / `LIST_ALL`) |
| **foundCodes in answer** | **No** — 6/8 at every topK |
| **responseSources (Playground)** | Slight increase (1→2), capped by UI |

**Root cause when answer does not differ:** `queryType=LIST_ALL` (compare/widget logs) + section expansion + **final context cap 20** — not vector topK alone.

---

## Matrix A — Playground (`POST /api/playground/chat`)

| topK | Q | eff | vec | final | src | codes | verdict | notes |
|------|---|-----|-----|-------|-----|-------|---------|-------|
| 1 | A1 | 1 | 1 | 10 | 1 | 1/1 | PASS | ALPHA-111 |
| 1 | A2 | 1 | 1 | 20 | 1 | 6/8 | PARTIAL | expansion → 20 contexts |
| 1 | A3 | 1 | 1 | 20 | 1 | 0* | FAIL† | *0 policy codes; 8 condition keywords in text |
| 1 | A4 | 1 | 1 | 10 | 1 | 0 | FAIL† | partial conditions in prose |
| 1 | A5 | 1 | 2 | 12 | 1 | 0 | PARTIAL | OOS OK, no OMEGA |
| 3 | A2 | 3 | 3 | 20 | 2 | 6/8 | PARTIAL | |
| 10 | A2 | 10 | 10 | 20 | 2 | 6/8 | PARTIAL | |
| 10 | A3 | 10 | 11 | 20 | 2 | 6/8 | PARTIAL | codes appear when topK=10 |
| 20 | A2 | 20 | 20 | 20 | 2 | 6/8 | PARTIAL | vec=20, answer still 6 codes |

†A3/A4 scoring by policy-code regex; LLM may answer conditions without literal `ALPHA-111` strings.

### A1 single-fact

topK=1 **PASS** (ALPHA-111). topK=20: vec=23, final=20 — over-retrieval for one fact.

### A5 OOS

**PASS** — no `OMEGA-*` hallucination; refusal phrasing at all topK.

---

## Matrix B — Compare

Config A `topK=1` vs B `topK=10`, `temperature=0.2`, `maxTokens=1024`.

| side | topK | Q | sourceCount | finalContexts | foundCodes | verdict |
|------|------|---|-------------|---------------|------------|---------|
| A | 1 | A2 | 20 | 20 | 6/8 | PARTIAL |
| B | 10 | A2 | 20 | 20 | 6/8 | PARTIAL |
| A | 1 | A3 | 20 | 20 | 0 codes | FAIL |
| B | 10 | A3 | 20 | 20 | 0 codes | FAIL |

Logs (A2):

```text
# Side A
[RAG] retrieval topK requested=1, effective=1
[RAG] Vector anchors: chunks=1
[RAG] Final context chunks: 20 | queryType=LIST_ALL

# Side B
[RAG] retrieval topK requested=10, effective=10
[RAG] Vector anchors: chunks=10
[RAG] Final context chunks: 20 | queryType=LIST_ALL
```

**Compare:** Wiring PASS; **answers identical** on A2 (6 codes). Side B does **not** list more policies than Side A.

---

## Matrix C — Widget / modelConfig (`POST /api/chat`)

| test | modelConfig topK | effective | source | vec | final | src | foundCodes |
|------|------------------|-----------|--------|-----|-------|-----|------------|
| C1 | 1 (saved/reload OK) | 1 | MODEL_CONFIG | 1 | 20 | 2 | 6/8 |
| C2 | 10 (saved/reload OK) | 10 | MODEL_CONFIG | 10 | 20 | 2 | 6/8 |

Same answer pattern: missing Eta/Theta; 6 codes listed. **sourceCount≤5** not applicable to conclusion (2 sources returned). Logs prove topK works.

---

## Evidence logs (sample A2 aggregate, Playground topK=1)

```text
[RAG] retrieval topK source=REQUEST … effective=1
[RAG] retrieval topK requested=1, effective=1
[RAG] Vector anchors: chunks=1 sections=1 …
[RAG] Section range expansion: 8 chunks
[RAG] dedupeSortBudget: … output=20 …
[RAG] Final context chunks: 20 | queryType=NORMAL_FACT (playground) or LIST_ALL (compare)
```

topK=1 → **1 vector anchor** but **20 final contexts** → explains full partial answer without high topK.

---

## Answers to task goals

| # | Question | Result |
|---|----------|--------|
| 1 | topK=1 how much info? | **6/8 policy codes** on aggregate; **1/1** on single-fact |
| 2 | topK=3? | **Same 6/8** on A2 |
| 3 | topK=10/20? | **Same 6/8** on A2; A3 improves codes at 10 (6/8) vs 1 (0 codes in text) |
| 4 | vectorAnchors change? | **Yes** — scales with topK |
| 5 | finalContexts change? | **Weakly** — often **20** for list-style queries regardless of topK |
| 6 | responseSources change? | **Yes** in Playground (cap=topK); not a proxy for prompt size |
| 7 | Higher topK → fuller answer? | **Mostly no** on A2; **some** on A3 at topK=10 |
| 8 | If no difference, why? | **LIST_ALL / expanded final limit 20**, section expansion, Eta/Theta chunks may be outside expanded window or LLM omission |

---

## Scoring (task §9)

| Criterion | Result |
|-----------|--------|
| effectiveTopK correct | **PASS** |
| vectorAnchors higher when topK higher | **PASS** |
| A2/A3 topK high → more codes than low | **PARTIAL** — A3 yes at 10 vs 1; **A2 no** |
| Widget modelConfig | **PASS** |
| OOS no hallucination | **PASS** |
| Clear user-visible low vs high difference | **PARTIAL** |

**Overall: PARTIAL** — infrastructure behaves correctly; **answer completeness often independent of topK** because expansion + `FINAL_LIMIT_EXPANDED=20` dominate.

---

## Không sửa code

No Java/Frontend changes. Only test doc + verify script + reports.

## Bước tiếp theo (đúng luồng)

1. Inspect whether Eta/Theta chunks are in Qdrant payload for this doc (index coverage).
2. If product wants topK to limit **answers**, need semantic/product change (cap final contexts or narrow expansion) — out of 23F3 scope.
3. Optional: rerun on PDF lớn hơn nhiều chunk để xem topK=1 có **miss** rõ hơn topK=20 trên aggregate.
