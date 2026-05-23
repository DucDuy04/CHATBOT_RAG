# Hybrid Search v1 — Runtime Eval Results (23I)

**Date:** 2026-05-23  
**Task:** 23I — Hybrid Search v1: Generic Keyword/BM25-like + Vector Search  
**Benchmark corpus:** `docs/eval/manual/SoTayHocVu-HocKy1-NamHoc20252026.pdf` (784 chunks)

## Summary

| Item | Status |
|------|--------|
| Hybrid branch added | YES |
| Vector search unchanged (`fixedVectorAnchorK=30`) | YES |
| Generic keyword (no file hardcode) | YES |
| Merge/dedupe/rerank after merge | YES |
| Prompt budget | YES |
| Generic unit tests | PASS (7/7) |
| Runtime benchmark (live stack) | NOT RUN — stack not exercised in this session |
| Verdict | **PARTIAL** (code + unit tests PASS; runtime eval pending) |

## Architecture Implemented

```
User Query
  → QuerySignalExtractor (identifiers, dates, labels, ngrams, IDF terms)
  → Vector Search Qdrant (fixedVectorAnchorK=30)     [unchanged]
  → KeywordSearchService (keywordTopM=30, max scan 3000)
  → Merge + dedupe (chunkId, source=VECTOR|KEYWORD|BOTH)
  → Rerank/scoring (0.35 vector + 0.35 keyword + 0.30 rerank)
  → Context Top-N selection
  → Prompt budget cap
  → Document order sort
  → PromptBuilder → LLM
```

## Config (application.yml)

```yaml
rag.retrieval.hybrid.enabled: true
rag.retrieval.hybrid.keyword-top-m: 30
rag.retrieval.prompt-budget.hard-max-context-char-budget: 18000
```

## Runtime Test Matrix (template — fill when stack running)

| id | question | topN | hybridOff | hybridOn | keywordCandidates | finalContexts | contextChars | verdict |
|----|----------|------|-----------|----------|-------------------|---------------|--------------|---------|
| Q1 | Thời gian nghỉ Tết Nguyên Đán... | 5/10/15 | TBD | TBD | TBD | TBD | TBD | PENDING |
| Q2 | Khóa 48 học quân sự... | 5/10/15 | TBD | TBD | TBD | TBD | TBD | PENDING |
| Q3 | Kỹ năng mềm Nhóm 2... | 10 | TBD | TBD | TBD | TBD | TBD | PENDING |
| Q4 | So sánh Nhóm 1 và Nhóm 2... | 10 | TBD | TBD | TBD | TBD | TBD | PENDING |
| Q5 | K45-K48 đăng ký học phần... | 10 | TBD | TBD | TBD | TBD | TBD | PENDING |
| Q6–Q9 | Regression | 10 | TBD | TBD | — | TBD | TBD | PENDING |
| Q10–Q11 | OOS | 5 | TBD | TBD | — | TBD | TBD | PENDING |

### Expected improvements (design intent)

- **Q1/Q2/Q5:** keyword date-range + identifier boosts should surface exact date cells missed by pure vector wording drift.
- **Q3/Q4:** structured label matching (`Nhóm 2`, `HK2`) reduces wrong-group retrieval.
- **Q6–Q9:** vector anchor preserved; hybrid should not regress when keyword adds no signal.
- **Q10–Q11:** no matching identifier → low keyword score; LLM should still refuse if context empty/low confidence.

## Log tags to verify at runtime

```
[RAG][hybrid] strategy=VECTOR+KEYWORD vectorCandidates=... keywordCandidates=...
[RAG][hybrid-top] rank=1 source=BOTH chunkType=... vScore=... kScore=... rScore=...
[RAG][budget] requestedTopN=... selectedContexts=... contextChars=... budgetLimited=...
```

## Unit test evidence

```
HybridKeywordSearchTest: 7/7 PASS
FinalContextSelectionTest: 7/7 PASS
RetrievalTopKTest: 7/7 PASS
```

## Remaining failures / risks

1. Runtime benchmark not executed — requires `docker compose up` + ingested SoTay PDF.
2. Keyword scan capped at 3000 chunks — widgets with more chunks may miss tail documents.
3. In-memory keyword scan adds latency on weak CPU (~784 chunks acceptable).
4. Locked-scope mode still uses legacy higher char caps before prompt-budget min().

## PASS / PARTIAL / FAIL

**PARTIAL** — implementation complete, generic tests PASS, live retrieval benchmark pending operator run.
