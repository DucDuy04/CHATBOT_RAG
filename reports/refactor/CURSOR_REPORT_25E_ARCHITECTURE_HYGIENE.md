# CURSOR_REPORT_25E — Architecture Hygiene (Test Packages + Docs/Rules Sync)

**Verdict:** PASS  
**Date:** 2026-05-28

## Summary

Aligned 5 test classes to production packages, updated Cursor rules (`10-backend-rag`, `40-db-vector`) and agent docs (`agent.md`, `01`–`03`) to the post-25D package map. No production Java changes. `mvn clean test`: 50/50 PASS.

## Stale reference inventory

| Classification | Count in active guidance (after) |
|---|---|
| STALE_RULE_REFERENCE | 0 (fixed) |
| STALE_DOC_REFERENCE (agent) | 0 (fixed) |
| STALE_TEST_PACKAGE | 0 (fixed) |
| VALID_LEGACY_REPORT | Many in `docs/`, `reports/` — kept |
| Production `service.*` RAG imports | 0 |

## Test package moves

| From | To |
|---|---|
| `KLTN.RAG_CHATBOT_BE.PromptBuilderServiceTest` | `rag.prompt` |
| `KLTN.RAG_CHATBOT_BE.service.EmbeddingServiceCacheTest` | `index.embedding` |
| `KLTN.RAG_CHATBOT_BE.service.NormalizedTableIngestTest` | `ingest.normalize` |
| `KLTN.RAG_CHATBOT_BE.service.NoHardcodedLexiconInTableNormalizerTest` | `ingest.normalize` |
| `KLTN.RAG_CHATBOT_BE.service.TestRawTableFixtures` | `ingest.normalize` |

## Cursor rules updated

- `10-backend-rag-rule.mdc` — full package map, Qdrant REST, DOCX `RawTableModel`/`physicalColIndex`, UTF-8 payload
- `40-db-vector-rule.mdc` — globs → `index.qdrant`, `index.embedding`, `rag.retrieve`; REST write checklist

## Agent/docs updated

- `agent.md`, `agent/01-overview.md`, `agent/02-architecture.md`, `agent/03-backend.md`

## common.util

Deferred — no safe pure shared utility candidate.

## Deferred tests

- `NormalizedTableSuppressionTest` — REQUIRED_NEXT  
- `HybridKeywordSearchTest` — REQUIRED_NEXT  
- Full `RagRetrievalService` E2E — REQUIRED_NEXT  

## Validation

| Check | Result |
|---|---|
| `mvn clean test` | PASS (50 tests) |
| `docker compose config -q` | PASS |
| Final stale scan `Backend/src` | 0 hits |
| Behavior change | None |

## Risks remaining

Historical reports unchanged; optional ops doc (`06-operations.md`) still mentions gRPC port for Qdrant container.

## Recommended next task

Restore REQUIRED_NEXT regression tests with lightweight Spring test configuration; optional `agent/06-operations.md` REST vs gRPC clarification.
