# CURSOR REPORT — Task 25B Backend Architecture Package Refactor

**Date:** 2026-05-28  
**Verdict:** **PASS**

## Summary

Refactored backend RAG classes from monolithic `KLTN.RAG_CHATBOT_BE.service` into layered packages (`ingest.*`, `index.*`, `rag.*`) with **no algorithm changes**. Each phase gated by `mvnw.cmd clean test`.

## Final test result

| Check | Result |
|---|---|
| `mvn clean test` | **PASS** — 50 tests, 0 failures, 0 errors |
| `docker compose config -q` | **PASS** |

## Package map (after)

```text
KLTN.RAG_CHATBOT_BE.ingest.parser     DocumentParserService, RawTable*
KLTN.RAG_CHATBOT_BE.ingest.normalize  NormalizedTableService, NormalizedTableRow, TableIngestMetrics, LogicalTableState
KLTN.RAG_CHATBOT_BE.ingest.chunking   ChunkingService2
KLTN.RAG_CHATBOT_BE.index.embedding   EmbeddingService
KLTN.RAG_CHATBOT_BE.index.qdrant      QdrantConfig, QdrantPurgeService
KLTN.RAG_CHATBOT_BE.rag.retrieve      RagRetrievalService, KeywordSearchService, KeywordIndexCache, CellAwareTableRowScorer
KLTN.RAG_CHATBOT_BE.rag.prompt        PromptBuilderService
KLTN.RAG_CHATBOT_BE.service           DocumentService, ChatService, QueryAnalyzerService, … (orchestration)
```

## Classes moved

| Phase | Files |
|---|---|
| P2 parser | `DocumentParserService`, `RawTableModel`, `RawTableRow`, `RawTableCell`, `RawTableBlock` |
| P3 normalize | `NormalizedTableService`, `NormalizedTableRow`, `TableIngestMetrics`, `LogicalTableState` |
| P4 chunking | `ChunkingService2` |
| P5 index | `EmbeddingService`, `QdrantConfig`, `QdrantPurgeService` |
| P6 rag | `RagRetrievalService`, `KeywordSearchService`, `KeywordIndexCache`, `CellAwareTableRowScorer`, `PromptBuilderService` |

## Tests relocated (package access only)

| Test | New package |
|---|---|
| `DocxParserServiceTest` | `ingest.parser` |
| `QdrantPayloadUnicodeTest` | `index.embedding` |
| `FinalContextSelectionTest` | `rag.retrieve` |
| `CellAwareNormalizedRowRetrievalTest` | `rag.retrieve` |

## Behavior changes

**None** in RAG algorithms. Minor visibility widenings required by package boundaries:

- `NormalizedTableService.hasHighTableLikeDensity` → `public`
- `QuerySignalExtractor.normalize`, `isStructuredValueToken`, `buildNgrams` → `public`
- `CellAwareTableRowScorer.isCompareQuery` → `public`

## Tests per phase

| Phase | `mvn clean test` |
|---|---|
| P2 parser | PASS |
| P3 normalize | PASS |
| P4 chunking | PASS |
| P5 index | PASS |
| P6 rag | PASS |
| P7 docs | PASS (50 tests) |

## Deferred

- `DocumentService`, `ChatService`, `PlaygroundService` → `rag.runtime` / ingest orchestration
- `QueryAnalyzerService`, `QuerySignalExtractor`, `RerankService`, `PromptBudgetResolver`
- `RagTokenAudit`, `RagLatencyTrace` → `audit.metrics`
- `common.util` extraction

## Risks remaining

- IDE/refactor tools may still reference old `service.*` paths in local notes.
- Cross-package tests depend on package-private methods in `rag.retrieve` / `ingest.parser`.
- Full Docker smoke (upload DOCX → INDEXED) not run in this session.

## Recommended next task

**25C:** Move orchestration (`DocumentService`, `ChatService`) to `rag.runtime` + extract `QuerySignalExtractor` to `common.util`; optional `package-info.java` per layer.
