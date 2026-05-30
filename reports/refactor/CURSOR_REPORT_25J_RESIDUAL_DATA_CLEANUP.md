# CURSOR_REPORT_25J — Residual Data Cleanup

**Date:** 2026-05-29  
**Verdict:** **PASS**

## Summary

Safe residual cleanup after 25I: soft-deleted **106** orphan chunks on already-deleted documents, removed FAILED doc **`834262a0`** via API (Option A), deleted **2** stray Qdrant points. Post-state: **5** chatbots, **3** COMPLETED documents, **9888** DB chunks = Qdrant total. **No production Java changes.**

## Code changed

| Path | Layer |
|------|-------|
| `docs/eval/scripts/execute_25j_cleanup.ps1` | eval script (NEW) |
| `docs/eval/results/_25j_*.json` | audit artifacts |
| Backend `src/main/java` | **none** |

## Dry-run (Phase 1)

| Table | Finding |
|-------|---------|
| Orphan chunks on soft-deleted docs | **106** |
| FAILED active doc with chunks | `834262a0` — 3296 DB, 0 Qdrant |
| Active COMPLETED parity | 3/3 OK |
| Qdrant stray | **2** point IDs |
| Collection total | **9890** |

Artifact: `docs/eval/results/_25j_residual_audit.json`

## Execution

| Phase | Action | Result |
|-------|--------|--------|
| 2 | SQL soft-delete orphan chunks | 106 → **0** PASS |
| 3 | `DELETE /api/documents/834262a0…` (Option A) | doc + chunks soft-deleted PASS |
| 4 | Qdrant delete 2 stray point IDs | 2 → **0** PASS |

Script: `docs/eval/scripts/execute_25j_cleanup.ps1`  
Results: `docs/eval/results/_25j_cleanup_results.json`

## Before / after

| Metric | Before | After |
|--------|-------:|------:|
| Active documents | 4 | 3 |
| Active DB chunks | 13184 | 9888 |
| Orphan chunks | 106 | 0 |
| Qdrant total | 9890 | 9888 |
| Stray Qdrant | 2 | 0 |

## Kept scope (verified)

- **5** chatbot IDs unchanged (25I list)
- **3** COMPLETED SoTay DOCX: DB chunks = Qdrant = **3296** each
- Rank-1 smoke S2: **PASS** (Nguyễn Thị Vân Anh, thứ 2, tiết 1-2, E301)

## FAILED document action

- **Option A** — soft-delete via `DocumentService.softDeleteDocument()` API
- `834262a0-c2c5-4778-9d1d-849478f4e6b6`: 3296 active chunks → 0; Qdrant remained 0

## Qdrant stray points

| | Before | After |
|---|-------:|------:|
| Stray count | 2 | 0 |
| IDs removed | `c266fe20-…`, `ff468a47-…` | — |

Collection total now **9888** = 3 × 3296 (exact parity).

## Checks

| Command | Result |
|---------|--------|
| `execute_25j_cleanup.ps1 -DryRunOnly` | PASS |
| `execute_25j_cleanup.ps1` | PASS |
| `docker compose config -q` | PASS |
| Smoke playground rank-1 | PASS |
| `mvnw compile/test` | NOT RUN (no Java change) |

## Risks remaining

- Soft-deleted historical rows still on disk (MySQL)
- `softDeleteChatbot` still no document cascade
- Chatbot `88ff1cba…` has zero active documents after FAILED doc removal

## Next task

Optional hard-purge archived rows; optional chatbot-delete cascade in Java.

## Detail

`docs/eval/results/RESIDUAL_DATA_CLEANUP_25J_20260529.md`
