# RAG Top-K as Final Context Top-N — Task 23G (2026-05-19)

## Old semantics

```text
UI topK → Qdrant vector search limit → expansion → dedupeSortBudget (document order cap)
```

## New semantics

```text
fixedVectorAnchorK=30 (config) → Qdrant → expansion → dedupe → score all → Top-N by score → document order → LLM
UI topK = finalContextTopN
```

## Design

| Parameter | Value |
|-----------|-------|
| `fixedVectorAnchorK` | 30 (`rag.retrieval.vector-anchor-k`) |
| `finalContextTopN` default | 10 (no request/config) |
| `finalContextTopN` clamp | 1–30 |
| Query-type default (no override) | LIST_ALL/COUNT/SECTION_SUMMARY: 20; TABLE_LOOKUP: 10; else 10 |

## Scoring

- **Rerank enabled:** Cohere `scoreCandidates` + blend `0.6 rerank + 0.3 IDF + 0.1 vector anchor`
- **Rerank disabled:** `0.7 IDF + 0.3 vector anchor`
- **No** `BUDGET_STOPWORDS`, **no** `FinalContextSelector`

## Code changes

See `docs/REPORT_23G_TOPK_AS_FINAL_CONTEXT_TOPN.md`.

## Test results

| Suite | Result |
|-------|--------|
| `FinalContextSelectionTest` | 7/7 PASS |
| `RetrievalTopKTest` | 7/7 PASS |
| `ChatServiceModelConfigTopKTest` | 9/9 PASS |
| Integration (MySQL/Qdrant) | FAIL (env) |

## Runtime results

| Case | Status |
|------|--------|
| Playground Top-N 1/5/20 | NOT RUN (rebuild required) |
| Compare A=1 B=20 | NOT RUN |
| Widget modelConfig 1/20 | NOT RUN |
| OOS Omega | NOT RUN |
| PDF/table regression | NOT RUN |

## UI changes

- Playground / Compare / Model Settings: **Context Top-N** (field `topK` unchanged).

## Regression

- Source cap production: unchanged (code review).
- Parser / PromptBuilder / QueryAnalyzer: not modified.

## Conclusion

**PARTIAL** — Backend semantics + unit tests PASS; runtime eval pending Docker rebuild.
