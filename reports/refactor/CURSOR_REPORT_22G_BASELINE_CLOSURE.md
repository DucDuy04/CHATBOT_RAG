# CURSOR_REPORT_22G — Baseline core RAG closure (docs only)

## 1. Mức độ hiểu task

- **~99%** — task chỉ tài liệu hóa baseline sau 21D–22F; không sửa runtime.
- **Chắc chắn:** phạm vi file phải tạo, nguồn evidence 21I–22F, constants từ source Java đã đọc.
- **Giả định:** nhánh merge chứa toàn bộ code đã verify trong 22F (Docker image từ 22C2+).
- **Thiếu dữ liện:** không chạy lại runtime eval trong 22G (theo yêu cầu).

## 2. Tóm tắt yêu cầu

Tạo bộ tài liệu closure/merge-readiness: baseline status, luồng `/api/chat`, ingest/delete, model controls + precedence, source cap, golden eval, limitations, merge/deploy/rollback checklist, optional backlog — **không** sửa Java/FE/runtime.

## 3. Phạm vi đã làm

- Đọc result docs + refactor reports 21I, 21J, 22A, 22B2, 22C2, 22D, 22F
- Đọc `RAG_BASELINE_CORE_REBUILD_PLAN.md`, `RAG_BASELINE_CORE_CHECKLIST.md`, `RAG_EVALUATION_RUNBOOK.md`, `RAG_GOLDEN_QUESTIONS.md` (tham chiếu)
- Đọc source read-only: `ChatService.java`, `LlmGenerationOptions.java`, `RagRetrievalService.java`, `WidgetService.java`, `ChatController.java`, `DocumentController.java`, `DocumentService` (upload/delete/retry)
- Tạo `docs/eval/RAG_BASELINE_CORE_CLOSURE_22G.md`
- Tạo `docs/eval/RAG_BASELINE_MERGE_CHECKLIST_22G.md`
- Tạo report này
- `git diff --stat` — thống kê nhánh (code changes từ tasks trước, không phải 22G)

## 4. Phạm vi không làm

- Không sửa Java, Frontend, Backend runtime, retrieval, prompt, parser, QueryAnalyzer, Playground Compare code
- Không thêm dependency, migration, backfill, `.gitignore`
- Không merge git, không tag
- Không chạy compile/test/full golden eval lại

## 5. File đã đọc

| Path | Đọc để | Kết luận chính |
|------|--------|----------------|
| `docs/eval/results/RAG_CHAT_MAIN_REGRESSION_AFTER_MODEL_CONTROLS_22F_20260515.md` | Baseline mới nhất | 12/0/0; model smoke PASS; source 5/2/0 |
| `reports/refactor/CURSOR_REPORT_22F_CHAT_MAIN_REGRESSION_AFTER_MODEL_CONTROLS.md` | Task 22F | Verify only; no code |
| `docs/eval/results/RAG_LLM_PARAMS_RUNTIME_VERIFY_22C2_20260515.md` | LLM params | Precedence + clamp PASS |
| `reports/refactor/CURSOR_REPORT_22C2_LLM_PARAMS_RUNTIME_VERIFY.md` | 22C2 scope | DEFAULT 0.1/1500 |
| `docs/eval/results/RAG_MODEL_CONFIG_TOPK_RUNTIME_VERIFY_22B2_20260515.md` | topK | MODEL_CONFIG vs DEFAULT 30 |
| `reports/refactor/CURSOR_REPORT_22B2_MODEL_CONFIG_TOPK_RUNTIME_VERIFY.md` | 22B2 | GET merge ≠ runtime |
| `docs/eval/results/RAG_FE_TOPK_WIRING_22A_20260515.md` | FE topK | Playground wired |
| `reports/refactor/CURSOR_REPORT_22A_FE_TOPK_WIRING.md` | 22A | Per-request override design |
| `docs/eval/results/RAG_SOURCE_PRESENTATION_CLEANUP_21J_20260515.md` | Source cap | 5 / 2; LLM context intact |
| `reports/refactor/CURSOR_REPORT_21J_SOURCE_PRESENTATION_CLEANUP_FIX.md` | 21J | ChatService only |
| `docs/eval/results/RAG_FULL_GOLDEN_REGRESSION_21I_20260515.md` | Pre-controls golden | 11/1/0 |
| `reports/refactor/CURSOR_REPORT_21I_FULL_GOLDEN_REGRESSION.md` | 21I | Functionally PASS |
| `docs/eval/results/RAG_PLAYGROUND_COMPARE_PARAMS_RUNTIME_VERIFY_22D_20260515.md` | Compare limits | Missing topK → 30 |
| `docs/eval/RAG_BASELINE_CORE_REBUILD_PLAN.md` | Gate workflow | G0–G6 framework |
| `docs/eval/RAG_BASELINE_CORE_CHECKLIST.md` | Checklist G0–G6 | 69 items template |
| `ChatService.java` | Flow + constants | MAX 5/2; resolveTopK/LLM |
| `LlmGenerationOptions.java` | Defaults/clamp | 0.1, 1500, 0–1, 64–4096 |
| `RagRetrievalService.java` | topK bounds | DEFAULT 30, max 30 |
| `WidgetService.java` | UI merge defaults | 0.7/1024/topK 5 display only |
| `ChatController.java` | API entry | `/api/chat`, stream |
| `DocumentController.java` | Upload/delete API | canonical upload |
| `DocumentService.java` | Pipeline | parse→chunk→embed; soft delete |

**Files user listed but not fully re-read line-by-line:** `LlmFallbackService.java`, `PromptBuilderService.java`, `QueryAnalyzerService.java`, `DocumentParserService.java`, `playgroundApi.js`, `ModelOverridePanel.jsx` — thông tin lấy từ reports 22A/22C và grep; behavior đã có trong eval results.

## 6. Baseline status đã tổng hợp

| Metric | Value |
|--------|-------|
| Baseline core overall | **PASS** |
| Latest full golden (22F) | **12 / 0 / 0** |
| Prior golden (21I) | **11 / 1 / 0** |
| Source cap (21J) | max **5**, refusal **2**, D01 **0** |
| Model controls `/api/chat` | topK + temperature + maxTokens **PASS** |
| Playground compare | **PASS** with accepted limitations |
| Task 22E | **Skipped** |

## 7. Các tài liệu đã tạo

| File | Mục đích |
|------|----------|
| `docs/eval/RAG_BASELINE_CORE_CLOSURE_22G.md` | Closure chính thức — flows, constants, limitations, deploy/rollback |
| `docs/eval/RAG_BASELINE_MERGE_CHECKLIST_22G.md` | Bảng G0–G6 + deploy smoke |
| `reports/refactor/CURSOR_REPORT_22G_BASELINE_CLOSURE.md` | Report task 22G |

## 8. Merge readiness conclusion

**READY** cho merge nhánh baseline core RAG (21D–22F) khi:

- Reviewer xác nhận G0–G4 trong merge checklist (evidence đã có từ 22F/22C2/22B2)
- G5 limitations **ACCEPTED** (compare parity, widget LLM params, UTF-8 eval, no load test)

22G **không** thay đổi runtime — chỉ đóng gói bằng chứng đã có.

## 9. Known limitations (tóm tắt)

1. Playground compare: missing `topK` không fallback `modelConfig` (effective 30).
2. Compare path không log `[LLM] generation options`.
3. Widget FE không gửi temperature/maxTokens.
4. GET `modelConfig` merge UI khác runtime DEFAULT tier.
5. Eval PowerShell UTF-8 / mojibake.
6. Không production load benchmark.

## 10. Không sửa code

**Đúng** — task 22G chỉ docs.

## 11. No runtime diff

Không có thay đổi `Backend/src`, `Frontend/`, `docker-compose.yml`, test, config runtime trong task 22G.

`git diff --stat HEAD` trên nhánh hiện tại (tasks trước 22G, tham khảo): ~21 files, +979/-97 lines — Java services + FE playground; **không** bao gồm file 22G mới cho đến khi commit.

## 12. Rủi ro còn lại

- Merge/deploy mà **không** rebuild backend image có thể thiếu code 22A–22C (22F dùng image 22C2).
- Operator nhầm GET `modelConfig` với runtime effective values.
- Compare A/B trong playground có thể khác `/api/chat` khi thiếu partial config.
- Periodic regression phụ thuộc chạy golden thủ công / script UTF-8.

## 13. Đề xuất bước tiếp theo

1. **Merge PR** nhánh 21D–22F — dùng `RAG_BASELINE_MERGE_CHECKLIST_22G.md` khi review.
2. **Deploy** — checklist §Deploy trong closure doc; smoke D-001–D-005.
3. **Tag/release note** (optional, user request) — baseline `22G-core-rag-pass`.
4. **Không** tự làm OPT-01–07 trừ khi product yêu cầu.

---

## Kết quả kiểm tra (task 22G)

| Command | Kết quả | Ghi chú |
|---------|---------|---------|
| `docs/eval/RAG_BASELINE_CORE_CLOSURE_22G.md` exists | **PASS** | Non-empty |
| `docs/eval/RAG_BASELINE_MERGE_CHECKLIST_22G.md` exists | **PASS** | Non-empty |
| `reports/refactor/CURSOR_REPORT_22G_BASELINE_CLOSURE.md` exists | **PASS** | Non-empty |
| Backend compile | **NOT RUN** | Docs only |
| Backend test | **NOT RUN** | Docs only |
| Frontend lint/build | **NOT RUN** | Docs only |
| Docker compose config | **NOT RUN** | Docs only |
| `git diff --stat HEAD` | **PASS** | ~21 files changed (prior tasks) |

---

**Evidence closure:** `docs/eval/RAG_BASELINE_CORE_CLOSURE_22G.md`
