# CURSOR REPORT — 23I Hybrid Search v1

**Date:** 2026-05-23  
**Verdict:** PARTIAL (unit tests PASS; runtime benchmark pending)

## What changed

- Added generic keyword/BM25-like branch parallel to existing Qdrant vector search.
- Merge vector + keyword candidates, dedupe by `chunkId`, hybrid-weighted rerank scoring.
- Prompt budget caps context chars (fact 10k, table 14k, list 16k, hard max 18k).
- No hardcode domain/file/testcase in production logic.

## Files created

| File | Purpose |
|------|---------|
| `QuerySignalExtractor.java` | Generic query signals (identifiers, dates, labels, ngrams) |
| `KeywordSearchService.java` | Bounded in-memory keyword scoring + merge helper |
| `PromptBudgetResolver.java` | Query-type char budget |
| `HybridKeywordSearchTest.java` | 5 generic scenarios + merge test |

## Files modified

| File | Change |
|------|--------|
| `RagRetrievalService.java` | Hybrid pipeline, scoring weights, budget logging |
| `application.yml` | `rag.retrieval.hybrid.*` + `prompt-budget.*` |

## Key design points

- **Generic keyword:** IDF from scanned corpus, runtime ngrams, identifier/date/label patterns — no SoTay/policy strings in code.
- **Vector preserved:** `fixedVectorAnchorK=30` unchanged; keyword only supplements candidates.
- **Rerank after merge:** `scoreCandidatesForSelection` runs on deduped merged pool.
- **Prompt budget:** `min(legacy cap, prompt-budget)` prevents 23H3-style 28k–64k context when budget enabled.

## Test results

| Command | Result |
|---------|--------|
| `mvnw compile` | PASS |
| `HybridKeywordSearchTest` | PASS 7/7 |
| `FinalContextSelectionTest` | PASS 7/7 |
| `mvnw test` (full) | PARTIAL — 97/99 pass; 2 infra tests fail (GROQ_API_KEY / Qdrant context) |

## Runtime improvements

Not measured in this session. Design targets Q1–Q5 retrieval gaps (dates, groups, HK, codes).

## Remaining failures

- Live benchmark table empty.
- Keyword scan limit 3000 chunks per widget.

## Toggle

Set `rag.retrieval.hybrid.enabled=false` to revert to vector-only scoring path (legacy weights).
