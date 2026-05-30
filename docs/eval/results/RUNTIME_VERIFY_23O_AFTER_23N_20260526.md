# RUNTIME_VERIFY_23O_AFTER_23N_20260526

**Task**: 23O — Runtime Verification After 23N Value-Coverage Fix  
**Date**: 2026-05-26  
**Final Verdict**: **PARTIAL** (7/8 automated; strict human review: 6/8 PASS)

---

## 1. Summary

| Metric | Result |
|---|---|
| Code changed in 23O | **No** |
| Backend rebuilt with 23N | Yes |
| Document used | `33f4f99a-70ab-46b9-9a81-ec72c686fd3a` |
| Chatbot used | `51e21150-e884-43c6-aa74-77e06d68cdee` |
| Pre-check | PASS |
| Q1-Q8 live run | **7/8 automated PASS** (Q2 PARTIAL) |
| Strict review | Q5 should be **PARTIAL/FAIL** (lists K45, admits no K46 HK2) |
| KTR3185 regression | PASS |
| Q8 OOS | PASS |
| 23N value-coverage impact | Q1/Q3 restored from FAIL → PASS |

---

## 2. Pre-Check

| Check | Result |
|---|---|
| Backend running | PASS (`chatbot-backend` Up, API 200) |
| Document status | INDEXED, progress=100 |
| chunkCount (API) | 3417 |
| normalized_table_row (Qdrant) | 3160 |
| table_summary (Qdrant) | 122 |
| text (Qdrant) | 131 |
| table_row_group | **0** |
| text_table_like | **0** |
| Fresh re-ingest required | No (retrieval-only 23N change) |
| Structured PDF path | Active (23M ingest unchanged; no markdown bridge in production path) |

---

## 3. Runtime Config

```
temperature = 0.2
maxTokens = 768
topK = 15
Hybrid Search = ON (default rag.retrieval.hybrid.enabled=true)
playgroundDebugSources = true
Endpoint = POST /api/playground/chat (SSE)
```

---

## 4. Q1-Q8 Runtime Table

| Q | Verdict | totalMs | sources | Answer summary | Failure layer (if not PASS) |
|---|---|---:|---:|---|---|
| Q1 | **PASS** | 27270 | 15 | Hoàng Ngô Tự Do, thứ 2, tiết 1-3, H307 | — |
| Q2 | **PARTIAL** | 12581 | 15 | Nguyễn Thị Thanh Nhàn, tiết 5-7, B301; **thứ missing** | LLM ignored weekday `6` in source |
| Q3 | **PASS** | 12712 | 10 | G1: Nguyễn Chí Ngàn/H310; G2: Hoàng Ngô Tự Do/H307 | — |
| Q4 | **PASS** | 9808 | 7 | 30/06/2025 – 06/07/2025 | — |
| Q5 | **PARTIAL*** | 15246 | 15 | Lists K45 courses; admits no K46 HK2 list | Ingest cohort tagging + LLM |
| Q6 | **PASS** | 9726 | 15 | CNS4113, CNS4042 (+ partial list) | — |
| Q7 | **PASS** | 13348 | 15 | Đồ án kiến trúc công trình tổ hợp đa chức năng, 5 TC | — |
| Q8 | **PASS** | 4260 | 15 | Refusal: không tìm thấy | — |

\*Automated scorer marked Q5 PASS (keyword match) but answer explicitly states K46 HK2 not found and lists K45-context rows → **strict verdict PARTIAL/FAIL**.

**Score: 6–7 / 8 PASS depending on Q5 strictness.**

---

## 5. 23N Fix Impact (vs 23M baseline 4/8)

| Query | 23M | 23O | Change |
|---|---|---|---|
| Q1 | FAIL | **PASS** | Fixed by value-level coverage |
| Q2 | FAIL (wrong weekday) | PARTIAL (3/4 fields) | Retrieval fixed; LLM weekday gap |
| Q3 | FAIL | **PASS** | Fixed (compare + value coverage) |
| Q4 | PASS | PASS | Stable |
| Q5 | FAIL | PARTIAL | Improved context but K46 HK2 still missing |
| Q6 | PASS | PASS | Stable |
| Q7 | PASS | PASS | KTR3185 guard intact |
| Q8 | PASS | PASS | Stable |

---

## 6. Per-Question Evidence

### Q1 — PASS

**Top source (rank 3, page 67):**
```
TC: Kỹ năng mềm - Nhóm 2
bắt đầu: Hoàng Ngô Tự Do
bắt đầu_3: 2  (weekday)
bắt đầu_4: 1-3
bắt đầu_5: H307
```
**valueCoverageMatch**: labels `nhom 2` matched via cell value `2` despite generic headers.  
**23M**: could not find group 2. **23O**: full correct answer.

### Q2 — PARTIAL (LLM issue)

**Correct row in Source 3 (page 67, rank 3):**
```
TC: Kỹ năng mềm - Nhóm 4
bắt đầu: Nguyễn Thị Thanh Nhàn
bắt đầu_3: 6
bắt đầu_4: 5-7
bắt đầu_5: B301
```

**QuerySignalExtractor labels:** `nhom 4, mem nhom 4, nang mem nhom 4`  
**Retrieval stage:** row present in final 15 sources at rank 3.  
**LLM answer:** "Thứ: (không có thông tin)" — **LLM did not map numeric weekday 6 → thứ 6**.  
**Conclusion:** Not a retrieval/scoring regression; prompt/LLM interpretation gap.

### Q3 — PASS

Compare coverage retrieved both group rows (page 67, rows 8 and 9).  
Answer includes both lecturers and rooms correctly.

### Q5 — PARTIAL/FAIL

**QuerySignalExtractor labels:** `truc k46, kien truc k46, nganh kien truc k46, ky 2, hoc ky 2, o hoc ky 2`

**Top cell-aware row (rank 1):**
```
hóa, ngành: Kiến trúc K45: 1
hóa, ngành: Kiến trúc K45_2: KTR3319 Đồ án tốt nghiệp 10 x hóa, ngành: Kiến trúc K46
```

**Issue:** Curriculum table uses **Kiến trúc K45** as primary column header; K46 appears only as suffix inside course cell text. No dedicated "Kiến trúc K46 HK2" row block was retrieved as a cohort-scoped list.

**Answer behavior:** LLM lists 15 K45-context courses then states "không có thông tin cụ thể về các học phần của ngành Kiến trúc K46 trong học kỳ 2."

**Failure layer:** Primarily **ingest/header attribution** (cohort not isolated per row) + **LLM** unable to filter K46 from mixed K45 table. Not fixed by 23N value-coverage alone.

### Q7 — PASS (KTR3185 regression guard)

**Answer:** "Đồ án kiến trúc công trình tổ hợp đa chức năng", 5 tín chỉ.

**Sources:** KTR3185 on page 22; KTR3273 on **separate row** (rank 4 source) — no merge of KTR3273/KTR4015/KTR5022 into KTR3185 row.

### Q8 — PASS (OOS)

Answer: "Tôi không tìm thấy thông tin này trong tài liệu." No hallucinated exchange rate.

---

## 7. Latency Notes

| Q | totalMs | vs 23M |
|---|---:|---|
| Q1 | 27270 | ↓ from 45858 |
| Q2 | 12581 | ≈ 14690 |
| Q3 | 12712 | ↓ from 23128 |
| Q4 | 9808 | ≈ 9310 |
| Q5 | 15246 | ≈ 17501 |
| Q6 | 9726 | ≈ 13260 |
| Q7 | 13348 | ≈ 16662 |
| Q8 | 4260 | ≈ 5508 |

Hybrid search + cell-aware scoring active; no latency regression observed.

---

## 8. Code Changes in 23O

**None.** Verification-only task. Failures traced to LLM (Q2) and ingest cohort structure (Q5), not proven retrieval pipeline gaps requiring immediate code fix.

---

## 9. Known Limitations

1. **Q2 weekday:** Canonical row text uses header `bắt đầu_3: 6` without semantic "thứ" label; LLM fails to infer thứ 6.
2. **Q5 cohort isolation:** Architecture curriculum rows tagged under K45 column; K46 only appears inline in cell values — list queries cannot reliably filter K46 HK2 without ingest or generic cohort-context extraction.
3. **Automated scorer leniency:** Q5 marked PASS by keyword presence but answer fails task expectation.

---

## 10. Next Recommended Task

**23P — Curriculum Cohort Context + Weekday Prompt Guard**

1. **Ingest (generic):** Improve cohort/semester context propagation in `NormalizedTableService` when cohort appears in cell value but header names another cohort (e.g., extract trailing `ngành: X K46` into `group_context` without hardcoding domain labels).
2. **Prompt (generic):** Add instruction that numeric schedule fields in table rows may represent weekday (1–7 → thứ 2–CN) when query asks "thứ mấy" — no domain literals.
3. Re-run Q2/Q5 after above; target 8/8 PASS.

**Optional cleanup (after 8/8):** DocumentParserService legacy comments, DB audit route, coordinate merge counters.

---

## 11. Artifacts

- Script: `docs/eval/results/_run_23o_runtime_verify.ps1`
- Questions: `docs/eval/results/_run_23o_questions.json`
- Raw results: `docs/eval/results/_run_23o_results.json`
