# CURSOR_REPORT_23F4 — Targeted Eta/Theta Retrieval Miss Audit

## Task understanding

- **~97%** — verify-only audit: xác định stage pipeline làm mất Eta/Theta cho targeted + aggregate queries trên corpus 23F3.
- Chắc chắn: source/DB/Qdrant có ETA-777 & THETA-888; miss xảy ra sau expansion khi `dedupeSortBudget` cắt `FINAL_LIMIT_EXPANDED=20`.
- Giả định nhỏ: vector stage có anchor tới sec 24/28 trước cap (suy từ pool 25 unique).

## Files read

- `TOPK_LOW_HIGH_DIFFERENCE_TEST_DOCUMENT.txt`, 23F3 report/script/results
- `RagRetrievalService.java`, `QueryAnalyzerService.java`, `ChatService.java`, `PlaygroundController.java`, `EmbeddingService.java`, `PromptBuilderService.java`

## Files created/modified

| File | Action |
|------|--------|
| `docs/eval/results/_run_23f4_eta_theta_audit.ps1` | Created |
| `docs/eval/results/_run_23f4_results.json` | Created |
| `docs/eval/results/RAG_TARGETED_ETA_THETA_RETRIEVAL_MISS_AUDIT_23F4_20260517.md` | Created |
| `reports/refactor/CURSOR_REPORT_23F4_TARGETED_ETA_THETA_RETRIEVAL_MISS_AUDIT.md` | Created |

No temporary debug logs. No production code changes.

## Commands run

- `docker compose up -d`
- API chunks + Qdrant scroll + `_run_23f4_eta_theta_audit.ps1`
- `docker logs chatbot-backend` (RAG lines)

## Evidence summary

| Stage | Eta/Theta |
|-------|-----------|
| Source doc | Present (Section Eta/Theta Policy) |
| DB chunks | chunkIndex 24 / 28 |
| Qdrant | pointIds with `text_segment` containing codes |
| Vector topK=20 | 20 anchors; pool 25 before cap |
| Final contexts topK=20 | **sec_idx_0…23 only** — **24 & 28 dropped** |
| Prompt | Same as final — **no Eta/Theta** |
| Answer Q1 topK=20 | Refusal — no ETA-777/THETA-888 |
| Answer Q1 topK=1 | **Both codes correct** — final includes sec_idx_24 & 28 |

Log smoking gun:

```text
[RAG] Budget: finalLimit=20 reached; 5 chunks excluded by count limit
[RAG] Context chunk list: [..., sec_idx_23[text]]  // no sec_idx_24, sec_idx_28
```

## Root cause case

**CASE E** (primary): `dedupeSortBudget` + `FINAL_LIMIT_EXPANDED=20` + document-order sort loại chunk cuối (Eta 24, Theta 28) khi expanded pool > 20.

**CASE F** (secondary): LLM “không tìm thấy” khi context thiếu.

**CASE H** (minor): Q2 misclassified `TABLE_LOOKUP`; Q5 single-section lock.

Not A/B/C. Not pure D or G.

## Recommended next task

1. **Retrieval final budget task:** relevance-aware cap or higher expanded limit for `LIST_ALL`; optional boost chunks matching query entity terms — **do not** conflate with vector topK semantics.
2. **QueryAnalyzer:** tighten `TABLE_LOOKUP` ` ma ` pattern.
3. **Optional:** multi-section heading lock for Q5-style queries.

## Risks remaining

- Fix chỉ tăng limit có thể tăng token/latency trên doc lớn hơn 29 chunks.
- topK cao vẫn có thể làm pool rộng hơn → cần test regression trên PDF lớn.
- User nhầm document chunk UI với chat sources — cần UX/debug clarity.

## Verification (this task)

| Check | Result |
|-------|--------|
| Backend compile | NOT RUN (no code change) |
| Backend test | NOT RUN |
| Frontend lint/build/widget | NOT RUN |
| Docker compose config | NOT RUN |
| Runtime audit script | PASS |
