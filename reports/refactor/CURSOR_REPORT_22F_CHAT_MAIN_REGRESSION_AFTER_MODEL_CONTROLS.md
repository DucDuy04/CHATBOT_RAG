# CURSOR_REPORT_22F — `/api/chat` main regression after model controls

## 1. Mức độ hiểu task

- **~98%** — verify-only full golden + model-params smoke trên `/api/chat`; không đụng compare/playground.
- **Chắc chắn:** acceptance 12 case, smoke logs, so sánh 21I baseline.
- **Giả định:** Docker image hiện tại đã có 22A–22C + 21J.

## 2. Tóm tắt yêu cầu

Chốt regression luồng chat chính sau wire topK/temperature/maxTokens: golden 12 case, smoke model controls, source cap, không sửa code.

## 3. Phạm vi đã làm

- Đọc docs 21I–22D + golden/runbook
- Docker health, env masked
- Compile + 34 unit tests
- Chatbot eval + modelConfig + golden upload INDEXED
- Model params smoke (fallback / override / clamp)
- Golden 11 + delete + D01
- UTF-8 recheck 4 case khi script mojibake
- Result + report

## 4. Phạm vi không làm

Không sửa Java/FE/compare/config/schema; không Playground 22E; không feature mới.

## 5. File đã đọc

21I/21J/22A/22B2/22C2/22D results & reports; `RAG_GOLDEN_QUESTIONS.md`, `RAG_EVALUATION_RUNBOOK.md`, `RAG_BASELINE_CORE_CHECKLIST.md`; `ChatService.java` (read-only); `22f_raw_eval_output.txt`.

## 6. Môi trường chạy

Windows, Docker 4 services up, backend :8080.

## 7. Env check

GROQ ***VTiv, NOMIC ***tz84 — present.

## 8. Build / restart result

`docker compose ps` — all running. Không `up --build` trong 22F (stack từ 22C2).

## 9. Compile / test result

| Command | Result |
|---------|--------|
| compile | **PASS** |
| 34 regression tests | **PASS** |

## 10. Chatbot/document setup

- ID: `4bedd295-48a7-43bf-8f69-c5902d3dd3cc`
- modelConfig: topK 5, temp 0.2, maxTokens 256
- Doc: `829199ff-5784-4ca8-96a9-3a643df9e313`, 10 chunks, INDEXED

## 11. Full golden 12 case result

| case | 21I | 22F adj | sources |
|------|-----|---------|---------|
| GQ-F01..F04 | PASS | PASS | 5 |
| GQ-L01,L02 | PASS | PASS | 5 |
| GQ-T01 | PASS | PASS | 5 |
| GQ-T02 | PARTIAL | **PASS** | 5 |
| GQ-C01 | PASS | PASS | 5 |
| GQ-O01,O02 | PASS | PASS | 2 |
| GQ-D01 | PASS | PASS | 0 |

**12 PASS / 0 PARTIAL / 0 FAIL** (adjudicated, có UTF-8 recheck).

## 12. Delete verification

D01: refuse, sourceCount=0, không leak golden — **PASS**.

## 13. Model params smoke result

| Smoke | Pass |
|-------|------|
| modelConfig fallback 5 / 0.2 / 256 | **PASS** |
| request 10 / 0.7 / 512 | **PASS** |
| clamp 30 / 1.0 / 4096 | **PASS** |

## 14. Source cap smoke result

In-scope ≤5, OOS ≤2, D01=0 — **PASS**.

## 15. So sánh với 21I/21J

- 21I: 11/1/0, sources 10
- 22F: 12/0/0, sources 5/2/0 — cap 21J hoạt động; T02 cải thiện trên recheck
- Model controls: không regress chat quality/safety

## 16. Có sửa code không?

**Không.**

## 17. No code diff

Chỉ docs + `_run_22f_main_regression.ps1`, `22f_raw_eval_output.txt`.

## 18. Kết luận

| Item | Result |
|------|--------|
| Main chat regression | **PASS** |
| Safety OOS/delete | **PASS** |
| Model controls regress | **No** |
| Source cap 21J | **OK** |

**Task 22F: PASS**

## 19. Rủi ro còn lại

- Eval automation cần UTF-8 BOM / `charset=utf-8` cho câu hỏi tiếng Việt.
- Một số answer vẫn verbose (chain-of-thought style) — không đổi trong verify.
- Playground compare topK fallback vẫn khác `/api/chat` (22D) — ngoài scope.

## 20. Đề xuất prompt tiếp theo

1. **Merge / tag baseline** `22A–22F` chat main PASS — không thêm feature.
2. **Optional:** Script eval UTF-8 cố định (`22f` script fix encoding) — không bắt buộc product.
3. **Optional:** Playground compare log/fallback (22E đã skip) chỉ khi product cần parity.
4. **RAG golden eval định kỳ** hoặc widget FE params — khi product yêu cầu.

---

**Evidence:** `docs/eval/results/RAG_CHAT_MAIN_REGRESSION_AFTER_MODEL_CONTROLS_22F_20260515.md`
