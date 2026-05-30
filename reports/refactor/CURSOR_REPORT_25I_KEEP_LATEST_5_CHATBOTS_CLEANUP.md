# CURSOR_REPORT_25I — Keep Latest 5 Chatbots Cleanup

**Date:** 2026-05-29  
**Verdict:** **PASS**

## Summary

Data cleanup giữ **5 chatbot mới nhất** (`COALESCE(updated_at, created_at) DESC`), soft-delete **69** chatbot cũ + documents qua API hiện có. Dry-run: `docs/eval/results/_25i_cleanup_plan.json`. Execution: `docs/eval/results/_25i_cleanup_execution.jsonl` (69/69 PASS). **Không sửa production Java.**

## Code changed

| Path | Layer |
|------|-------|
| `docs/eval/scripts/execute_25i_cleanup.ps1` | eval script (NEW) |
| Backend `src/main/java` | **none** |

## Kept 5 chatbot IDs

1. `3a26c18c-bfd1-477e-af79-fcd7fba551a9`
2. `1c04ed8b-bbb5-4aba-a257-59fe1dd80b38`
3. `a53cea76-8f8b-4ade-9558-ffb2cceafa87`
4. `650fc61c-e8c8-4e25-b5e3-284f4493bfac`
5. `88ff1cba-3340-4db4-9101-5e56795b58a8`

## Deleted scope

- **69** chatbot IDs (rank 6–74) — see full list in `_25i_cleanup_plan.json` → `deleteChatbots`
- **18** documents via main cleanup + **3** orphan docs manual (`aa2cb0d5`, `c02ec656`, `18af5a7b`)

## Counts before / after

| Metric | Before | After |
|--------|-------:|------:|
| Active chatbots | 74 | 5 |
| Active documents | ~22 | 4 |
| Active DB chunks | ~24k+ | 13184 |
| Qdrant collection (exact) | ~10138 | 9890 |

**Kept COMPLETED docs:** DB chunks = Qdrant = **3296** each (3 documents).

## Orphan checks

| Check | After |
|-------|-------|
| Active docs on deleted chatbot | 0 |
| Active chunk without active parent doc | 0 |
| Qdrant on sampled deleted `document_id` | 0 |
| Chunks on soft-deleted docs (inactive) | 106 ⚠ |

## Smoke (rank-1)

| ID | Verdict |
|----|---------|
| S2 (LUA1012 Nhóm 1 schedule) | PASS |
| S5 (USD/VND OOS) | PASS |

## Risks remaining

- `softDeleteChatbot` no document cascade — manual doc delete required
- FAILED doc `834262a0`: 3296 DB chunks, 0 Qdrant
- 106 legacy chunks on soft-deleted documents

## Next task

Purge 106 stale chunks; optional cascade on chatbot delete; re-index or remove `834262a0`.

## Detail

`docs/eval/results/KEEP_LATEST_5_CHATBOTS_CLEANUP_25I_20260529.md`
