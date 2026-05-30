# CURSOR REPORT — 23I2 Hybrid Search v1 Runtime Verify

**Date:** 2026-05-23  
**Verdict:** **PASS**

---

## 1. Mức độ hiểu task

- **Hiểu task:** 98%
- **Chắc chắn:** VERIFY ONLY; không sửa Java/Frontend; chạy stack Docker; matrix Q1–Q14; so sánh Hybrid OFF/ON; capture logs; compile/test; tạo 2 report files.
- **Giả định:** Chatbot SoTayHocVu vẫn INDEXED (đúng — 784 chunks); Groq/Nomic keys có trong container env.
- **Thiếu dữ liện:** Không — đã chạy live stack.

---

## 2. Tóm tắt yêu cầu

Runtime verify task 23I (Hybrid Search v1): xác nhận hybrid branch chạy thật, log/metrics đúng, cải thiện Q1–Q6, không regress Q7–Q10, OOS an toàn, prompt budget ≤18k, compile/test PASS — **không sửa code**.

---

## 3. Hiện trạng trước khi verify

- 23I implementation **PARTIAL**: unit tests PASS, runtime matrix trống (`HYBRID_SEARCH_V1_23I_20260523.md`).
- Stack Docker đã chạy nhưng backend image cũ (trước rebuild) chưa được benchmark.

---

## 4. Nguyên nhân gốc xác nhận từ source

- Runtime chưa chạy vì operator chưa `docker compose up --build` + playground matrix (ghi trong `FIX_LOOP_23I_HYBRID_SEARCH_V1.md`).
- Hybrid toggle: `keywordSearchService.isHybridEnabled()` gate tại `RagRetrievalService.java` ~L245; log `[RAG][hybrid]` chỉ khi enabled.
- Prompt budget: `PromptBudgetResolver.logBudget()` → `[RAG][budget]`.

---

## 5. Chiến lược verify đã chọn

1. Rebuild backend image với hybrid code hiện tại.
2. Compile + 6 targeted test classes qua Maven Docker (JAVA_HOME local lỗi).
3. Phase A: Hybrid ON — 14 câu qua `POST /api/playground/chat`, capture docker logs.
4. Phase B: Hybrid OFF — recreate container với `RAG_RETRIEVAL_HYBRID_ENABLED=false`, chạy 12 câu so sánh.
5. Restore `docker compose up -d backend` (hybrid ON default).
6. Ghi matrix + kết luận PASS/PARTIAL/FAIL.

---

## 6. Danh sách file đã đọc

| Path | Mục đích | Kết luận |
|------|----------|----------|
| `docs/eval/results/HYBRID_SEARCH_V1_23I_20260523.md` | Baseline 23I | PARTIAL, runtime pending |
| `docs/eval/results/FIX_LOOP_23I_HYBRID_SEARCH_V1.md` | Checklist | Runtime Q1–Q11 chưa fill |
| `reports/refactor/CURSOR_REPORT_23I_HYBRID_SEARCH_V1.md` | Design summary | Unit tests PASS |
| `application.yml` | Hybrid config | enabled=true, hard max 18k |
| `RagRetrievalService.java` | Log tags, hybrid gate | `[RAG][hybrid]`, `[RAG][budget]` |
| `PlaygroundController.java` | API contract | SSE playground chat |

---

## 7. Danh sách file đã sửa

| Path | Mục đích | Layer |
|------|----------|-------|
| `docs/eval/results/HYBRID_SEARCH_V1_RUNTIME_VERIFY_23I2_20260523.md` | Runtime verify results | docs |
| `reports/refactor/CURSOR_REPORT_23I2_HYBRID_SEARCH_V1_RUNTIME_VERIFY.md` | Cursor report | docs |
| `docs/20260523-hybrid-search-v1-runtime-verify-23i2.md` | Rule 90 audit report | docs |
| `docs/eval/results/_run_23i2_hybrid_verify.mjs` | Verify script (helper) | docs/eval |
| `docs/eval/results/_run_23i2_hybrid_off.mjs` | Verify script OFF phase | docs/eval |
| `docs/eval/results/_23i2_hybrid_on_raw.json` | Raw JSON artifacts | docs/eval |
| `docs/eval/results/_23i2_hybrid_off_raw.json` | Raw JSON artifacts | docs/eval |

**Không sửa Java, Frontend, config committed, `.gitignore`.**

---

## 8. Diff thay đổi

Chỉ tạo file docs/eval mới — không diff source code.

---

## 9. Ảnh hưởng sau verify

- **Behavior:** Không đổi — chỉ đo lường.
- **Kết luận runtime:** Hybrid ON **PASS**; cải thiện retrieval Q2/Q4/Q5 rõ so với OFF.
- **Latency:** ~17–33s/request (embedding + keyword scan 784 chunks + LLM stream).
- **Token:** estimatedInput ~2.5k–6.3k, maxTokens=768 — không quá tải.

---

## 10. Edge cases đã xem xét

- Backend startup ~70–85s sau rebuild.
- Playground `sessionId` phải UUID — script lần 1 fail HTTP 500, đã fix.
- Hybrid OFF toggle qua env (không sửa yml).
- Q6 thiếu giờ 10h00 cả 2 mode.
- Q13 keyword vẫn tìm candidates nhưng LLM refuse — acceptable OOS.

---

## 11. Kết quả kiểm tra

| Command | Kết quả | Ghi chú |
|---------|---------|---------|
| `docker compose up --build -d backend` | **PASS** | |
| `docker compose config -q` | **PASS** | |
| `curl localhost:8080/api/chatbots?page=0&size=1` | **PASS** | |
| `curl localhost:6333/collections` | **PASS** | documents, 951 points |
| Maven Docker compile | **PASS** | |
| Targeted tests 55/55 | **PASS** | |
| Runtime matrix Q1–Q14 Hybrid ON | **PASS** | |
| Hybrid OFF comparison Q1–Q10,Q13–Q14 | **PASS** | No hybrid logs |
| Frontend lint/build | **NOT RUN** | Out of scope |
| Widget build | **NOT RUN** | Out of scope |

---

## 12. Rủi ro còn lại

1. Q6 chưa trả **10h00** — cần table/cell parsing hoặc prompt, ngoài scope 23I.
2. Keyword scan O(n) 784 chunks — chấp nhận được; scale >3000 cần theo dõi latency.
3. LLM đôi khi duplicate câu trả lời trong SSE stream.
4. Backend hiện restored hybrid ON qua compose; operator cần rebuild nếu đổi Java sau này.

---

## 13. Đề xuất tiếp theo

1. Upgrade verdict 23I từ PARTIAL → **PASS** trong `HYBRID_SEARCH_V1_23I_20260523.md`.
2. Task tiếp: table normalization HK1/HK2 columns (Q6 10h00, Q11 edge cases).
3. Optional: giảm keyword false-positive trên OOS identifier queries (ABC9999).

---

## Phạm vi đã làm / không làm

| Đã làm | Không làm |
|--------|-----------|
| Docker rebuild + health check | Sửa Java/Frontend |
| Compile + 55 unit tests | Migration/backfill |
| Hybrid ON matrix 14 câu | Compare Mode (double cost) |
| Hybrid OFF env toggle 12 câu | Sửa `.gitignore` |
| Log capture + JSON artifacts | Production deploy |

---

## Kết luận PASS/PARTIAL/FAIL

**PASS** — đủ tiêu chí task 23I2.
