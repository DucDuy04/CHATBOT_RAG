# CURSOR_REPORT_25F — Deferred Regression Tests Restoration

**Date:** 2026-05-28  
**Verdict:** PASS

## Summary

Restored three deferred test classes from 25E REQUIRED_NEXT using lightweight unit/slice setup (no Spring context, no live MySQL/Qdrant/API). Production code unchanged.

## Tests restored

| Class | Package | Count |
|-------|---------|------:|
| `NormalizedTableSuppressionTest` | `ingest.normalize` | 5 |
| `HybridKeywordSearchTest` | `rag.retrieve` | 6 |
| `RagRetrievalServiceE2ETest` | `rag.retrieve` | 4 |

## Test count

- Before: 50  
- After: 65  
- Delta: +15  

## Production changes

None.

## Commands run

```powershell
cd Backend
.\mvnw.cmd clean test                                    # PASS 65/65
.\mvnw.cmd "-Dtest=NormalizedTableSuppressionTest,NormalizedTableIngestTest" test
.\mvnw.cmd "-Dtest=HybridKeywordSearchTest,CellAwareNormalizedRowRetrievalTest,RetrievalTopKTest" test
.\mvnw.cmd "-Dtest=RagRetrievalServiceE2ETest,FinalContextSelectionTest,CellAwareNormalizedRowRetrievalTest" test
cd ..
docker compose config -q                                 # PASS
```

## Stale scan (classify)

| Pattern | Hits | Classification |
|---------|------|----------------|
| `QdrantEmbeddingStore` / `langchain4j-qdrant` in `Backend/src` | 0 | — |
| `table_row_group` in main | KeywordSearchService boost, PromptBuilder backward-compat | valid |
| `text_table_like` in main | ChatService + PromptBuilder read path for legacy chunks | valid |
| `table_row_group` in test | Suppression assertion deny-list | valid |

## Risks

- Full retrieval E2E (Qdrant + rerank + DB) not in default suite  
- Runtime eval Q1–Q8 still operator responsibility  

## Recommended next

Optional Testcontainers integration test; runtime SoTay verification.

## Detail report

`docs/eval/results/DEFERRED_REGRESSION_TESTS_25F_20260528.md`
