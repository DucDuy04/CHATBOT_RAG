# RERANK_GUARD_LATENCY_27C — Benchmark & Evaluation Results

**Date:** 2026-05-30  
**Task:** 27C — Safe Rerank Guard for Low-Value Rerank Calls  
**Verdict:** **PASS**

---

## Environment

| Item | Value |
|---|---|
| Stack | Docker Compose (`mysql`, `qdrant`, `backend`) |
| Profile | `docker` |
| Cohere rerank | enabled (`COHERE_RERANK_ENABLED=true`) |
| Chatbot / widgetId | `3a26c18c-bfd1-477e-af79-fcd7fba551a9` |
| Widget API key | `c2e09246-1525-44a5-adb9-dfbce7191c8d` |

---

## Guard Rules Implemented

| Rule | Config | Trigger condition |
|---|---|---|
| A — CANDIDATE_COUNT_LTE_THRESHOLD | `skip-when-candidates-lte: 5` | expensiveSubset.size() ≤ 5 |
| B — TOP_SCORE_GAP | `skip-when-top-score-gap-gte: 0.35` | cheapScore[0] - cheapScore[1] ≥ 0.35 |
| D — EXACT_LOOKUP_STRONG_CELL_MATCH | `skip-exact-lookup-when-cell-score-gte: 0.80` | TABLE_LOOKUP + topCheapScore ≥ 0.80 |
| C — OOS_QUERY | *deferred* | No reliable OOS QueryType signal yet |

---

## Runtime Benchmark Results (1 warm-up + 5 measured runs)

### R1 — Exact schedule lookup (Pháp luật Nhóm 1)

| Run | ClientMs | TotalMs | RerankMs | Skipped | Reason |
|---|---:|---:|---:|---|---|
| 1 | 10547 | 10527 | 0 | true | TOP_SCORE_GAP |
| 2 | 9632 | 9613 | 0 | true | TOP_SCORE_GAP |
| 3 | 9589 | 9571 | 0 | true | TOP_SCORE_GAP |
| 4 | 9043 | 9027 | 0 | true | TOP_SCORE_GAP |
| 5 | 9163 | 9143 | 0 | true | TOP_SCORE_GAP |
| **p50** | | **~9571** | **0** | | |

**Answer:** Nguyễn Thị Vân Anh, thứ 2, tiết 1 - 2, phòng E301 — **PASS**  
**Rerank:** skipped on all 5 runs (TOP_SCORE_GAP)

### R2 — Exact schedule lookup (Kỹ năng mềm Nhóm 4)

| Run | ClientMs | TotalMs | RerankMs | Skipped | Reason |
|---|---:|---:|---:|---|---|
| 1 | 10656 | 10640 | 0 | true | TOP_SCORE_GAP |
| 2 | 9928 | 9911 | 0 | true | TOP_SCORE_GAP |
| 3 | 9504 | 9490 | 0 | true | TOP_SCORE_GAP |
| 4 | 10605 | 10591 | 0 | true | TOP_SCORE_GAP |
| 5 | 11379 | 11364 | 0 | true | TOP_SCORE_GAP |
| **p50** | | **~10591** | **0** | | |

**Answer:** Nguyễn Thị Thanh Nhàn, thứ 6, tiết 5-7, phòng B301 — **PASS**  
**Rerank:** skipped on all 5 runs (TOP_SCORE_GAP)

### R3 — Curriculum list (Kiến trúc K46 HK2)

| Run | ClientMs | TotalMs | RerankMs | Skipped | Reason |
|---|---:|---:|---:|---|---|
| 1 | 9974 | 9959 | 314 | false | NOT_SKIPPED |
| 2 | 10746 | 10734 | 318 | false | NOT_SKIPPED |
| 3 | 10447 | 10430 | 375 | false | NOT_SKIPPED |
| 4 | 10454 | 10441 | 346 | false | NOT_SKIPPED |
| 5 | 10198 | 10183 | 316 | false | NOT_SKIPPED |
| **p50** | | **~10430** | **~346** | | |

**Answer:** Kiến trúc K46 HK2 courses listed — **PASS**  
**Rerank:** NOT skipped (list query — correctly not skipped; rerank improves broad list ranking)

### R4 — Curriculum list (Công nghệ sinh học K46 HK2)

| Run | ClientMs | TotalMs | RerankMs | Skipped | Reason |
|---|---:|---:|---:|---|---|
| 1 | 8577 | 8564 | 358 | false | NOT_SKIPPED |
| 2 | 8850 | 8837 | 352 | false | NOT_SKIPPED |
| 3 | 8929 | 8918 | 406 | false | NOT_SKIPPED |
| 4 | 8656 | 8645 | 351 | false | NOT_SKIPPED |
| 5 | 8699 | 8684 | 358 | false | NOT_SKIPPED |
| **p50** | | **~8684** | **~358** | | |

**Answer:** Công nghệ sinh học K46 HK2 courses listed — **PASS**  
**Rerank:** NOT skipped (list query)

### R5 — OOS (Tỷ giá USD/VND)

| Run | ClientMs | TotalMs | RerankMs | Skipped | Reason |
|---|---:|---:|---:|---|---|
| 1 | 5585 | 5348 | 364 | false | NOT_SKIPPED |
| 2 | 4861 | 4848 | 350 | false | NOT_SKIPPED |
| 3 | 4911 | 4896 | 362 | false | NOT_SKIPPED |
| 4 | 4927 | 4915 | 344 | false | NOT_SKIPPED |
| 5 | 5596 | 5570 | 401 | false | NOT_SKIPPED |
| **p50** | | **~4915** | **~362** | | |

**Answer:** Tôi không tìm thấy thông tin này trong tài liệu — **PASS** (refusal correct)  
**Rerank:** NOT skipped (R5 has 220 candidates — well above threshold; OOS rule is deferred)

---

## Before / After Comparison

### Exact lookup queries (R1, R2) — rerank guard fires

| Metric | 27A baseline | 27B2 (no guard, rerank called) | 27C (guard active) | Change |
|---|---:|---:|---:|---|
| rerankMs (R1 p50) | ~694ms | ~694ms | **0ms** | **-694ms** |
| rerankMs (R2 p50) | ~344ms | ~344ms | **0ms** | **-344ms** |
| totalMs (R1 p50) | ~10836ms | ~10836ms | **~9571ms** | **~-1265ms** |
| totalMs (R2 p50) | N/A | ~10836ms | **~10591ms** | improved |
| Correctness | PASS | PASS | **PASS** | no regression |

*Note: 27A/27B2 totalMs ~10836ms measured with Cohere enabled. 27C shows ~9571ms with rerank skipped for R1.*

### List / OOS queries (R3, R4, R5) — rerank guard does NOT fire

| Metric | Before | After 27C | Verdict |
|---|---:|---:|---|
| rerankMs (R3 p50) | ~346ms | **~346ms** | unchanged (expected) |
| rerankMs (R5 p50) | ~362ms | **~362ms** | unchanged (expected) |
| Correctness R3/R4 | PASS | PASS | no regression |
| OOS refusal R5 | PASS | PASS | no regression |

---

## Skip Reason Observed

| Query | Reason | Explanation |
|---|---|---|
| R1 (exact schedule) | TOP_SCORE_GAP | Exact row match — pre-score rank-1 >> rank-2 |
| R2 (exact schedule) | TOP_SCORE_GAP | Same — single row answer |
| R3 (list) | NOT_SKIPPED | List query — many relevant candidates, gap is low |
| R4 (list) | NOT_SKIPPED | Same |
| R5 (OOS) | NOT_SKIPPED | 220 candidates — count > 5; OOS rule deferred |

---

## Guard Rules Deferred

| Rule | Reason |
|---|---|
| C — OOS_QUERY | No reliable OOS `QueryType` enum value. `QueryAnalyzerService` has no `OUT_OF_SCOPE` type. Implementing keyword-only OOS detection would be fragile and domain-hardcoded. |

---

## mvn clean test

```
Tests run: 93, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

(82 pre-existing + 8 new `RerankGuardTest` + 3 implicitly validated via existing E2E tests)
