# CURSOR_REPORT_25G — Runtime Smoke After Architecture Refactor

**Date:** 2026-05-29  
**Verdict:** PARTIAL

## Summary

Post–25F runtime smoke on stable DOCX confirms **architecture refactor did not break** ingest, REST Qdrant upsert, DB parity, or Vietnamese `cells_json`. Representative chat: **3/5 PASS**, **2/5 FAIL** (S1 schedule Nhóm 4, S3 curriculum K46 HK2). **No production code changed.**

## Code changed

**No** production changes. Verify-only: `docs/eval/scripts/run_25g_smoke_chat.ps1`, `docs/eval/results/_25g_*`.

## Test before runtime

`mvn clean test` — **PASS** (65 tests, 0 failures).

## Docker / Nomic

| Item | Result |
|------|--------|
| `docker compose config -q` | PASS |
| Stack | mysql + qdrant + backend UP |
| Backend startup | PASS (no missing class/bean) |
| NOMIC prefix (host + container) | `nk-RpR4S` (no quota error on ingest) |

## IDs

| | UUID |
|---|------|
| chatbotId / widgetId | `3a26c18c-bfd1-477e-af79-fcd7fba551a9` |
| documentId (25G smoke) | `7ae6d0b9-b7de-4bc8-8be6-10798add73aa` |

## Ingest audit

| Metric | Value |
|--------|------:|
| Status | INDEXED |
| chunkCount | 3296 |
| Upload ~ms | 65632 |
| docxTablesUsingMarkdownBridge | 0 |
| valuesDroppedCount | 0 |

## DB / Qdrant

| | Count |
|---|------:|
| DB | 3296 |
| Qdrant (document filter) | 3296 |
| normalized_table_row | 3078 |
| table_row_group / text_table_like | 0 |

## cells_json Unicode

LUA1012 Nhóm 1: DB ↔ Qdrant **MATCH**, correct Vietnamese, required keys (STT, Mã học phần, Phòng E301, …).

## Chat smoke

| ID | Verdict |
|----|---------|
| S1 | FAIL (refusal) |
| S2 | PASS |
| S3 | FAIL (refusal) |
| S4 | PASS |
| S5 | PASS (OOS refuse) |

Failure class: **E/F** (retrieval/LLM), not ingest/wiring.

## Stale documents

Warn: older INDEXED docs in MySQL/Qdrant may predate 24D2/24D4 fixes — do not delete without approval.

## Next

Retrieval-focused fix for S1/S3; optional stale Qdrant cleanup after approval.

## Detail

`docs/eval/results/RUNTIME_SMOKE_AFTER_REFACTOR_25G_20260528.md`
