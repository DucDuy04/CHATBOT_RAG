# RAG Relevance-Aware Final Budget Fix 23F5

**Date:** 2026-05-18  
**Task:** 23F5 — Relevance-aware Final Context Selection after Expansion  
**Conclusion:** **PASS**

---

## 1. Before (from 23F4)

| topK | final contexts | sec_idx_24 (Eta) | sec_idx_28 (Theta) | answer |
|------|----------------|------------------|--------------------|--------|
| 20 | 20 (ends sec_idx_23) | No | No | refusal |
| 1 | 17 | Yes | Yes | ETA-777 / THETA-888 |

Root cause: `dedupeSortBudget` sorted by document order then took first `FINAL_LIMIT_EXPANDED=20` chunks → tail chunks dropped.

---

## 2. Root cause (confirmed)

`dedupeSortBudget` pipeline **before fix**:

1. Dedup by `chunk.id` (LinkedHashMap, first wins).
2. Sort `(documentId, sectionOrder, orderIndex)`.
3. For expanded queries: `prioritizeSummaryChunks` (summaries first, then rest in doc order).
4. Linear scan: add chunks until `finalLimit` or `maxContextChars` → **document-order cap**.

With `input=25`, `limit=20`: chunks at indices 20–24 (including Eta/Theta) never entered the result.

---

## 3. Pre-fix analysis (mandatory questions)

| # | Answer |
|---|--------|
| 1 | Dedup: chunk UUID. Sort: document order (+ summary boost). Cap: first N in that order + char budget. |
| 2 | sec_idx_24/28 have highest orderIndex; cap stops at 20 earliest positions → tail excluded. |
| 3 | Usable metadata: content, sectionTitle, headingPathText, chunkType, sectionOrder/orderIndex; vector score not on `RetrievedContext`; rerank score not persisted on DTO. |
| 4 | Rerank score **not** stored in `RetrievedContext` (only `Double score` field exists but not set in `dedupeSortBudget`). |
| 5 | Yes — lightweight lexical overlap with Vietnamese diacritic stripping is feasible (already used in `extractSearchTerms` / `lexicalScore`). |
| 6 | Query `Liệt kê mã chính sách của Eta và Theta.` → terms **eta**, **theta** match section titles and body on chunks 24/28. |
| 7 | Yes — `normalizeForSearch` in `RagRetrievalService` (NFD strip + đ→d). Reused in `FinalContextSelector`. |
| 8 | Added dedicated `FinalContextSelector` (same package) to keep `RagRetrievalService` diff small. |
| 9 | Risk: generic terms could keep noise — mitigated by expanded stopword list (`ma`, `chinh`, `sach`, …) and requiring term length ≥ 3. |
| 10 | OOS: only **omega** extracted for Omega query; no chunk matches → no false protection. |
| 11 | Source cap unchanged (`ChatService` not modified). |
| 12 | Minimal fix: relevance-aware selection when `unique.size() > finalLimit` and expanded query / important terms present. |

---

## 4. Option chosen

**Option A + B hybrid** (`FinalContextSelector`):

- Extract important query terms (stopword-filtered, diacritic-normalized).
- Score chunks (title > heading > content weights).
- **Pass 1:** keep all chunks with score > 0 (protected term matches).
- **Pass 2:** fill to `finalLimit` by relevance.
- Re-sort selected set by document order for prompt coherence.

Not Option C (blind `FINAL_LIMIT_EXPANDED` increase).

---

## 5. Code changes

| File | Change |
|------|--------|
| `FinalContextSelector.java` | **New** — term extraction, relevance score, budget selection |
| `RagRetrievalService.java` | `dedupeSortBudget` calls selector when pool > limit |
| `FinalContextSelectorTest.java` | **New** — 7 unit tests (RedPolicy/BluePolicy, LateAlpha/LateBeta, OOS, …) |

No hardcode of Eta/Theta/ETA-777/THETA-888.

---

## 6. Compile / test

| Command | Result | Note |
|---------|--------|------|
| `mvnw -DskipTests compile` | PASS | JDK 21 |
| `mvnw -Dtest=FinalContextSelectorTest test` | PASS | 7/7 |
| `mvnw test` | PARTIAL | 75 run, **2 errors** pre-existing (`QdrantConnectionTest`, `RagChatbotBeApplicationTests` — no Docker test profile) |
| `docker compose config` | PASS | |
| `docker compose up --build -d` | PASS | |

---

## 7. Runtime A — Eta/Theta targeted

Chatbot: `bed4feee-2946-43a9-8238-b1a04f7c6cac` (23F3 corpus, INDEXED).

| topK | relevanceAware | sec_idx_24 | sec_idx_28 | ETA-777 | THETA-888 |
|------|----------------|------------|------------|---------|-----------|
| 20 | true | Yes | Yes | Yes | Yes |
| 10 | true | Yes | Yes | Yes | Yes |
| 1 | false (pool≤20) | Yes | Yes | Yes | Yes |

Log sample (topK=20):

```text
[RAG] relevanceAwareBudget: ... output=20 terms=[eta, theta] queryType=LIST_ALL
[RAG] dedupeSortBudget: ... relevanceAware=true
```

---

## 8. Runtime B — Aggregate 8 policies

Question: list Alpha…Theta codes.

| topK | policy codes in answer | count |
|------|------------------------|-------|
| 20 | all 8 | **8/8** (was 6/8 baseline) |
| 10 | all 8 | **8/8** |
| 1 | all 8 | **8/8** |

terms logged: `[alpha, beta, gamma, delta, epsilon, zeta, eta, theta]`

---

## 9. Runtime C — OOS Omega

- Answer: refusal — no Omega policy in document.
- `fakeOmegaCode`: **false**
- Sources capped: 20 → 1 returned (refusal path)

---

## 10. Source cap / Vector Top-K

- **Vector Top-K:** unchanged (Qdrant anchor limit only).
- **Production source cap:** not modified; playground shows cap behavior consistent with prior tasks.
- **PDF/table regression (D):** NOT RUN (no automated script in this pass).

---

## 11. Conclusion

**PASS** — Target Eta/Theta topK=20 fixed; aggregate 8/8; OOS safe; no entity hardcoding; Vector Top-K semantics preserved.

---

## 12. Artifacts

- `docs/eval/results/_run_23f5_relevance_budget_verify.ps1`
- `docs/eval/results/_run_23f5_results.json`
- `reports/refactor/CURSOR_REPORT_23F5_RELEVANCE_AWARE_FINAL_BUDGET_FIX.md`
