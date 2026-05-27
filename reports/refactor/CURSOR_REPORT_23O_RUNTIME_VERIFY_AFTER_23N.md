# CURSOR_REPORT_23O — Runtime Verify After 23N

**Task ID**: 23O  
**Date**: 2026-05-26  
**Verdict**: **PARTIAL** (live Q1-Q8 run; no code changes)

---

## 1. Mức độ hiểu task

- Hiểu: **100%**
- Chắc chắn: Verify-only scope, document/chatbot IDs, runtime config, acceptance criteria
- Giả địm: Không có
- Thiếu: Không

---

## 2. Tóm tắt yêu cầu

Sau 23N (value-level coverage fix, tests PASS), chạy live Q1-Q8 trên document 23M đã ingest. Chỉ sửa code nếu runtime chứng minh failure ở pipeline cụ thể.

---

## 3. Hiện trạng trước khi verify

- 23N: PARTIAL — compile + 157 tests PASS, runtime chưa verify
- 23M baseline: Q1-Q8 = 4/8 PASS (Q1/Q2/Q3/Q5 FAIL)

---

## 4. Nguyên nhân gốc (runtime evidence)

| Query | Root cause (live) |
|---|---|
| Q1 (fixed) | 23N valueCoverageMatch → col_N rows ranked correctly |
| Q2 (partial) | Retrieval OK; LLM không map `bắt đầu_3: 6` → thứ 6 |
| Q3 (fixed) | Compare coverage + value coverage → both groups in context |
| Q5 (partial) | Ingest: rows under "Kiến trúc K45" header; K46 chỉ suffix trong cell; LLM admits no K46 HK2 list |

Không có evidence cho thêm retrieval fix ngay trong 23O.

---

## 5. Chiến lược đã chọn

1. Rebuild + restart backend với 23N code
2. Pre-check API + Qdrant chunk counts
3. Chạy Q1-Q8 qua `/api/playground/chat` SSE
4. Phân tích logs + sources cho Q2/Q5
5. Không sửa code (failure không nằm ở retrieval pipeline đã fix)

---

## 6. Danh sách file đã đọc

| File | Mục đích |
|---|---|
| `docs/eval/results/STRUCTURED_TABLE_RUNTIME_RESTORE_23N_20260526.md` | Baseline 23N |
| `docs/eval/results/STRUCTURED_TABLE_INGEST_23M_20260526.md` | Document IDs, 23M Q1-Q8 baseline |
| `Backend/.../PlaygroundController.java` | API contract |
| `docker-compose.yml` | Deploy config |

---

## 7. Danh sách file đã sửa/tạo

| File | Layer | Mục đích |
|---|---|---|
| `docs/eval/results/_run_23o_questions.json` | test/docs | UTF-8 question fixtures |
| `docs/eval/results/_run_23o_runtime_verify.ps1` | test/docs | Live Q1-Q8 runner |
| `docs/eval/results/_run_23o_results.json` | test/docs | Raw runtime artifact |
| `docs/eval/results/RUNTIME_VERIFY_23O_AFTER_23N_20260526.md` | docs | Eval report |
| `reports/refactor/CURSOR_REPORT_23O_RUNTIME_VERIFY_AFTER_23N.md` | docs | Cursor report |

**Production code: không sửa.**

---

## 8. Pre-check kết quả

```
documentId=33f4f99a-70ab-46b9-9a81-ec72c686fd3a
status=INDEXED, chunkCount=3417
Qdrant: normalized_table_row=3160, table_summary=122, text=131
table_row_group=0, text_table_like=0
```

---

## 9. Q1-Q8 runtime (live)

| Q | Verdict | ms | Note |
|---|---|---:|---|
| Q1 | PASS | 27270 | Full schedule for Nhóm 2 |
| Q2 | PARTIAL | 12581 | Missing weekday; source has `6` |
| Q3 | PASS | 12712 | Both groups compared |
| Q4 | PASS | 9808 | Date range correct |
| Q5 | PARTIAL | 15246 | No K46 HK2 list; K45 rows |
| Q6 | PASS | 9726 | Biotech K46 HK2 courses |
| Q7 | PASS | 13348 | KTR3185 title + 5 TC; no neighbor merge |
| Q8 | PASS | 4260 | OOS refusal |

**Overall: PARTIAL (6–7/8 strict)**

---

## 10. KTR3185 regression guard

- PASS: Full title + 5 credits
- KTR3273 appears in separate source row, not merged into KTR3185 cells
- Q7 same as 23M PASS

---

## 11. Kết quả kiểm tra

| Command | Kết quả | Ghi chú |
|---|---|---|
| `docker compose build backend` | PASS | 23N code deployed |
| `docker compose up -d backend` | PASS | No `-v` |
| Pre-check API + Qdrant | PASS | INDEXED, metrics OK |
| Live Q1-Q8 playground | PASS/PARTIAL | 7/8 automated; Q2/Q5 gaps |
| Backend compile (no new changes) | PASS | Unchanged |
| Backend test | NOT RUN | No code change |
| Frontend lint/build | NOT RUN | N/A |
| Docker compose config | NOT RUN | N/A |

---

## 12. Rủi ro còn lại

1. **Q2 weekday:** Prompt không hướng dẫn LLM interpret numeric weekday trong generic headers.
2. **Q5 cohort list:** Ingest không tách cohort K46 thành context riêng trong curriculum mega-table.
3. **Claim PASS blocked:** Cannot claim 8/8 until Q2 weekday + Q5 K46 HK2 list resolved.

---

## 13. Đề xuất tiếp theo

**Task 23P:**
- Generic cohort suffix extraction into `group_context` at ingest
- Generic prompt hint for numeric weekday fields in schedule rows
- Re-verify Q2 + Q5 live

Cleanup task (after 8/8): DocumentParserService comments, DB audit route, merge rejection counters.
