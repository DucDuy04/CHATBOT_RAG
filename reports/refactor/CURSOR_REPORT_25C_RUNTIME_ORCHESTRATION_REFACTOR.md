# CURSOR REPORT — Task 25C Runtime / Orchestration Refactor

**Date:** 2026-05-28  
**Verdict:** **PASS**

---

## Summary

Continued backend package-boundary refactor after 25B. Moved audit, query analysis, rerank/budget, and chat runtime classes out of monolithic `service`. No algorithm or API behavior changes. **50 tests**, 0 failures.

---

## Inventory (focus classes)

| Class | Target | Decision |
|---|---|---|
| `RagTokenAudit`, `RagLatencyTrace` | `audit.metrics` | Moved |
| `QueryAnalyzerService`, `QuerySignalExtractor` | `rag.analysis` | Moved |
| `RerankService` | `rag.rerank` | Moved |
| `PromptBudgetResolver` | `rag.budget` | Moved |
| `ChatService`, `PlaygroundService` | `rag.runtime` | Moved |
| `DocumentService` | `service` | **Kept** (Option A) |
| `LlmFallbackService`, `LlmGenerationOptions` | `service` | **Kept** (deferred) |
| `common.util` | — | **Deferred** (no candidates) |

---

## Package map (before → after)

**Before:** orchestration/audit/analysis in `service` + 25B RAG packages.

**After:**

- `audit.metrics` — token + latency tracing
- `rag.analysis` — query type + signals
- `rag.rerank` — Cohere rerank
- `rag.budget` — prompt char budget
- `rag.runtime` — `ChatService`, `PlaygroundService`
- `service` — `DocumentService`, LLM fallback, widget/admin

---

## Files moved (main)

```
service/RagTokenAudit.java          → audit/metrics/
service/RagLatencyTrace.java        → audit/metrics/
service/QueryAnalyzerService.java   → rag/analysis/
service/QuerySignalExtractor.java   → rag/analysis/
service/RerankService.java          → rag/rerank/
service/PromptBudgetResolver.java   → rag/budget/
service/ChatService.java            → rag/runtime/
service/PlaygroundService.java      → rag/runtime/
```

**Import updates:** `RagRetrievalService`, `KeywordSearch*`, `CellAwareTableRowScorer`, `EmbeddingService`, API controllers, `LlmFallbackService`.

**Tests moved:** `ChatServiceSourcePresentationTest`, `RetrievalTopKTest` → `rag.runtime` package.

**Docs:** `agent/02-architecture.md`, `agent/03-backend.md`, `application-dev.yml` / `application-docker.yml` logging FQCN.

**package-info.java:** 12 packages (25B + 25C).

---

## Tests per phase

| Phase | `mvn clean test` |
|---|---|
| 2 audit | PASS |
| 3 analysis | PASS |
| 4 rerank/budget | PASS |
| 5 runtime | PASS |
| 7–8 package-info/docs | PASS |
| Final | **PASS (50)** |

`docker compose config -q`: **PASS**

---

## Behavior changes

**None** (package/import/logging config path only).

---

## Risks remaining

- `LlmFallbackService` still in `service` (cross-layer dep from `rag.runtime`).
- No Docker DOCX smoke in this session.

---

## Recommended next task

**25D** — `LlmFallbackService` / LLM adapter package + stale `service` duplicate cleanup from 25B.
