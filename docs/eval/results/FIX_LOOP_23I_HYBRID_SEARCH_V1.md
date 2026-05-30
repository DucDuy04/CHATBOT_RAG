# FIX LOOP 23I — Hybrid Search v1

## Loop status

| Iteration | Focus | Result |
|-----------|-------|--------|
| 1 | QuerySignalExtractor + KeywordSearchService | DONE |
| 2 | RagRetrievalService merge/scoring integration | DONE |
| 3 | PromptBudgetResolver + config | DONE |
| 4 | Generic unit tests (5 scenarios + merge) | PASS |
| 5 | Runtime benchmark SoTayHocVu | NOT RUN (env) |

## Acceptance checklist

- [x] Hybrid parallel to vector
- [x] No Elasticsearch / new dependency
- [x] No parser/chunking changes
- [x] No domain/file hardcode in production logic
- [x] Merge/dedupe by chunkId, source BOTH
- [x] Rerank after merge
- [x] Context Top-N semantics preserved
- [x] Prompt budget enforced (hard max 18k)
- [x] Unit tests generic
- [ ] Runtime Q1–Q11 table filled
- [ ] OOS verified live

## Next loop actions

1. `docker compose up --build -d` with hybrid enabled.
2. Run Playground compare hybrid OFF vs ON for Q1–Q11.
3. Capture `[RAG][hybrid]` / `[RAG][budget]` logs per query.
4. Update `HYBRID_SEARCH_V1_23I_20260523.md` table → upgrade verdict to PASS if Q1–Q5 improve without Q6–Q9 regression.

## Verdict

**PARTIAL** — ready for runtime verification.
