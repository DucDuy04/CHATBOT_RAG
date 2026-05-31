# CURSOR_REPORT_26A — Final Architecture Documentation

**Date:** 2026-05-29  
**Verdict:** **PASS**

## Summary

Created final architecture documentation for the current post-25K backend RAG baseline. **No production Java code changed.** Validation: `mvn clean test` **71** tests PASS; `docker compose config -q` PASS.

## Files created

| Path | Purpose |
|------|---------|
| `docs/architecture/FINAL_BACKEND_RAG_ARCHITECTURE_20260529.md` | Full architecture reference (15 sections) |
| `docs/architecture/FINAL_RAG_PIPELINE_OVERVIEW_20260529.md` | Diagram-first pipeline + invariants |
| `reports/refactor/CURSOR_REPORT_26A_FINAL_ARCHITECTURE_DOCS.md` | This report |

## Source reports / docs read

| Source | Used for |
|--------|----------|
| `DEFERRED_REGRESSION_TESTS_25F_20260528.md` | Test suite baseline |
| `RUNTIME_SMOKE_AFTER_REFACTOR_25G_20260528.md` | Runtime verification |
| `KEEP_LATEST_5_CHATBOTS_CLEANUP_25I_20260529.md` | Data cleanup baseline |
| `RESIDUAL_DATA_CLEANUP_25J_20260529.md` | Orphan/stray zero state |
| `CHATBOT_DELETE_CASCADE_25K_20260529.md` | Delete cascade behavior |
| `agent.md`, `agent/01-overview.md`, `agent/02-architecture.md`, `agent/03-backend.md` | Existing architecture truth |
| `.cursor/rules/10-backend-rag-rule.mdc`, `40-db-vector-rule.mdc` | Canonical package map |
| Current `Backend/src/main/java` packages | Class paths, delete flow, entities |

## Architecture summary

- **Multi-tenant RAG** scoped by `WidgetConfig` / `widgetId`
- **Ingest:** PDF/DOCX/TXT → `RawTableModel` → `NormalizedTableService` → `ChunkingService2` → MySQL + Qdrant REST
- **Retrieval:** Hybrid vector + keyword + cell-aware scoring + optional Cohere rerank
- **Runtime:** `ChatService` / `PlaygroundService` → `RagRetrievalService` → `PromptBuilderService` → `LlmFallbackService`
- **Delete:** Document soft-delete with Qdrant purge; chatbot delete cascades documents first (25K)

## Package map (canonical)

Documented packages: `api`, `service`, `ingest.*`, `index.*`, `rag.*`, `audit.metrics`, `llm`, `domain` (+ repositories under `domain.*`), `config`.

## Diagrams added

| Doc | Diagrams |
|-----|----------|
| `FINAL_RAG_PIPELINE_OVERVIEW` | One-page flowchart; ingest branches; retrieval; delete/cascade; package dependency |
| `FINAL_BACKEND_RAG_ARCHITECTURE` | Text flow diagrams for runtime, ingest, delete |

All Mermaid in pipeline overview; backend doc uses structured tables + ASCII flows.

## Production code changed

**None.**

## Validation commands

| Command | Result | Notes |
|---------|--------|-------|
| `cd Backend && .\mvnw.cmd clean test` | **PASS** | 71 tests, 0 failures |
| `docker compose config -q` | **PASS** | |
| Stale doc scan (`rg` on docs/architecture) | **N/A** | `docs/architecture/` did not exist before this task; new docs use canonical paths |
| Stale scan on `agent/` | **OK** | Mentions of `QdrantEmbeddingStore` / `langchain4j-qdrant` are explicit “do not use” guidance |

## Uncertainty

| Item | Note |
|------|------|
| Runtime counts (5 bots, 9888 chunks) | Snapshot from 25I–25K reports; not re-queried live in 26A |
| Legacy duplicate classes under `service/` | Some old filenames may still exist on disk; docs reference **canonical** `ingest.*` / `index.*` / `rag.*` / `llm` paths per 25D refactor |
| `preprocess/` module | Documented as non-runtime; not fully inventoried |

## Recommended next step

1. Link `agent.md` mục lục to `docs/architecture/FINAL_BACKEND_RAG_ARCHITECTURE_20260529.md` (optional path-only update).
2. Optional: add `@Tag("integration")` Qdrant Testcontainers test when infra budget allows.
3. Optional: return `documentsDeleted` count from chatbot delete API response.

## Acceptance mapping

| Criterion | Status |
|-----------|--------|
| Final architecture doc | ✓ |
| Pipeline overview + Mermaid | ✓ |
| Accurate package map | ✓ |
| Qdrant REST-only documented | ✓ |
| DOCX/PDF/TXT ingest documented | ✓ |
| cells_json / group_context | ✓ |
| Delete cascade (25K) | ✓ |
| Test architecture (71 tests) | ✓ |
| No production code change | ✓ |
| mvn test + compose config | ✓ |
| **Verdict** | **PASS** |
