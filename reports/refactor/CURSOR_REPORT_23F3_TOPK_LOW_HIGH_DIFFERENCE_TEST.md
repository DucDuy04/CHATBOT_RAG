# CURSOR_REPORT_23F3 — Top-K Low vs High Difference Test

## 1. Mức độ hiểu task

- **~98%**
- Tạo doc lớn nhiều section + noise, chạy matrix topK 1/3/10/20, so sánh vector/final/sources/answer.

## 2. Phạm vi đã làm

- Tạo `docs/eval/manual/TOPK_LOW_HIGH_DIFFERENCE_TEST_DOCUMENT.txt`
- Docker stack + chatbot mới + upload + INDEXED (29 chunks)
- Script `_run_23f3_low_high_diff_test.ps1` — Matrix A/B/C
- Báo cáo `RAG_TOPK_LOW_HIGH_DIFFERENCE_TEST_23F3_20260517.md`

## 3. Phạm vi không làm

- Không sửa Java, Frontend, retrieval, parser, PromptBuilder, QueryAnalyzer, source cap
- Không thêm dependency

## 4. File đã đọc / tạo

| Path | Ghi chú |
|------|---------|
| `TOPK_LOW_HIGH_DIFFERENCE_TEST_DOCUMENT.txt` | Tạo mới — 29 chunks indexed |
| `_run_23f3_low_high_diff_test.ps1` | Runner verify-only |
| `_run_23f3_results.json` | Raw matrix |
| 23F / 23F2 audit reports | Baseline semantics |

## 5. Kết quả từng matrix

### A — Playground

- **Wiring PASS** — effective 1/3/10/20 đúng.
- **A2 aggregate:** vectorAnchors 1→20; **foundCodes luôn 6/8**; finalContexts luôn **20**.
- **A1:** PASS mọi topK.
- **A5 OOS:** PASS (không bịa Omega).

### B — Compare

- topK 1 vs 10: vector 1 vs 10; final **20** cả hai; **cùng 6 codes** trên A2.

### C — Widget

- modelConfig topK 1 vs 10: `MODEL_CONFIG effective=1/10`; vector 1 vs 10; **cùng 6 codes**; final **20**.

## 6. Bảng so sánh topK=1 vs topK=10/20 (A2)

| | topK=1 | topK=10 | topK=20 |
|---|--------|---------|---------|
| vectorAnchors | 1 | 10 | 20 |
| finalContexts | 20 | 20 | 20 |
| foundCodes | 6 | 6 | 6 |
| responseSources (PG) | 1 | 2 | 2 |

## 7. Có sửa code không

**Không.**

## 8. Kết luận PASS/PARTIAL/FAIL

**PARTIAL**

- **PASS:** topK wiring, vector anchor scaling, OOS, modelConfig.
- **PARTIAL:** Câu trả lời aggregate **không đầy hơn** khi tăng topK (6/8 cố định); expansion + final limit 20 lấn át.
- **Không FAIL wiring.**

## 9. Bước tiếp theo

- Kiểm tra chunk index cho Eta/Theta trong DB/Qdrant.
- Quyết định product: có cần topK giới hạn **final** context không.

## 10. Kết quả kiểm tra

| Command | Kết quả |
|---------|---------|
| `docker compose up --build -d` | PASS |
| Runtime script 23F3 | PASS (executed) |
| Backend compile/test | NOT RUN |
| Frontend lint/build | NOT RUN |
