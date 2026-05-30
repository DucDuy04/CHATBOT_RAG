# CURSOR REPORT 23G2 — Runtime Verify Top-K as Final Context Top-N

## 1. Mức độ hiểu task

- **95%** — VERIFY ONLY, no product code changes.

## 2. Phạm vi đã làm

- Docker: `mysql`, `qdrant`, `backend` (rebuilt 23G image).
- Health checks, targeted compile/unit tests, frontend lint/build.
- Full runtime matrix A–F via `_run_23g2_runtime_verify.ps1`.
- Manual log re-check for Compare A/B.
- Reports + raw `_run_23g2_results.json`.

## 3. Phạm vi không làm

- No Java / Frontend / Backend logic edits.
- No PDF/table regression (artifact missing).
- No full `docker compose --build` (frontend npm ETIMEDOUT).

## 4. File đã đọc

- `docs/eval/results/RAG_TOPK_AS_FINAL_CONTEXT_TOPN_23G_20260519.md`
- `docs/eval/results/FIX_LOOP_23G_TOPK_AS_FINAL_CONTEXT_TOPN.md`
- `reports/refactor/CURSOR_REPORT_23G_TOPK_AS_FINAL_CONTEXT_TOPN.md`
- `docs/eval/results/_run_23f3_low_high_diff_test.ps1` (pattern reference)

## 5. Có sửa code không?

**No product code diff.** Only eval artifacts:

- `docs/eval/results/_run_23g2_runtime_verify.ps1` (new)
- `docs/eval/results/_run_23g2_results.json` (generated)
- This report + eval result markdown

## 6. Kết quả chính

| Area | Result |
|------|--------|
| fixedVectorAnchorK | Stable **30** |
| finalContexts vs Top-N | **1 / 5 / 20** PASS |
| Compare 1 vs 20 | PASS (logs + different answers) |
| Widget modelConfig 1 vs 20 | PASS (`MODEL_CONFIG`) |
| OOS Omega | PASS |
| Source cap | PASS |
| PDF/table | NOT_RUN |

## 7. Kết luận

**PASS** (with minor answer-quality PARTIAL on Eta/Theta topN=5).

## 8. Rủi ro còn lại

- Rerank/LLM may miss entities at low Top-N even when `finalContexts` is correct.
- Cohere/network dependency for best scoring.

## 9. Bước tiếp theo

- Optional: add PDF artifact and rerun Runtime G.
- Retry `docker compose up --build` when npm registry stable.
