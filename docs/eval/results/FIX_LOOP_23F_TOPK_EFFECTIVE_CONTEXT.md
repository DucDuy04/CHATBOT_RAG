# FIX_LOOP_23F — Top-K Effective Context Audit

**Date:** 2026-05-17  
**Loop type:** Diagnosis-only (no code diff)

## Diagnosis

- **Symptom:** User sets Top-K = 1; answer still rich; sources may look insufficient.
- **Root cause (confirmed from source):** `topK` = **Qdrant vector candidate limit** (`effectiveAnchorTopK`), not final LLM context limit. Post-retrieval expansion (`SECTION_EXPANSION`, neighbor window, heading/rerank lock) and `FINAL_LIMIT` (10/20/60) dominate prompt size.
- **Not a wiring bug** in Playground / Chat / Widget for request or modelConfig topK.

## Action taken this loop

- None (per task scope: no change until proven bug).

## Optional follow-ups (not implemented)

1. Product decision: should UI topK cap **final** contexts (would touch `dedupeSortBudget` — semantic change).
2. Compare: align topK resolution with `ChatService.resolveRetrievalTopK` if configs can omit topK.
3. Minimal extra logs: `afterExpansion`, `finalContextsToPrompt` counts (if runtime audit needed without reading dedupe logs).

## Re-verify when backend available

1. Upload `docs/eval/manual/TOPK_CONTROL_TEST_DOCUMENT.txt` to test chatbot.
2. Run matrix in `RAG_TOPK_EFFECTIVE_CONTEXT_AUDIT_23F_20260517.md` §4.
3. Compare logs: `effective=1` vs `Final context chunks: N` (expect N often > 1).
