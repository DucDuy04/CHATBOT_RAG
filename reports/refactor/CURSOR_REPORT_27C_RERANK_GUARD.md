# CURSOR REPORT 27C — Safe Rerank Guard for Low-Value Rerank Calls

**Date:** 2026-05-30  
**Task:** 27C — Safe Rerank Guard  
**Verdict:** **PASS**

---

## 1. Mức độ hiểu task

| Item | Value |
|---|---|
| Hiểu task | **100%** |
| Chắc chắn | Guard logic, integration point, test approach, config design |
| Giả định | `TOP_SCORE_GAP` sẽ fire cho exact lookup (confirmed runtime) |
| Thiếu dữ kiện | Không |

---

## 2. Tóm tắt yêu cầu

Thêm config-driven guard để skip external Cohere rerank call cho các trường hợp low-value, tiết kiệm ~300–700ms/request cho những query có winner rõ ràng.

---

## 3. Hiện trạng trước khi sửa

`RagRetrievalService.scoreCandidatesForSelection()` gọi `rerankService.scoreCandidates()` vô điều kiện khi `rerankService.isEnabled()` là true. Không có guard hay skip logic nào. Mỗi query khi rerank enabled → 1 Cohere API call (~344–694ms).

---

## 4. Nguyên nhân gốc xác nhận từ source

Trong `scoreCandidatesForSelection()` line ~1240:
```java
if (rerankService.isEnabled()) {
    List<RerankService.ScoredChunk> reranked = rerankService.scoreCandidates(question, expensiveSubset);
```
Không có điều kiện guard nào. `cheapScored` (pre-scored candidates, sorted desc) có sẵn trước rerank call và cho phép estimate top-score gap mà không cần external API.

---

## 5. Chiến lược sửa đã chọn

- Tạo `RerankGuard` component nhỏ trong `rag.rerank`, nhận primitives (không phụ thuộc package-private `CheapScoredCandidate`).
- Inject `RerankGuard` vào `RagRetrievalService` qua `@RequiredArgsConstructor`.
- Guard chạy TRƯỚC external API call, chỉ dùng: `expensiveSubset.size()`, `cheapScored` top 2 scores, `queryType`.
- Thêm trace fields `rerankSkipped`, `rerankSkipReason`, `rerankGuardCandidates` vào `RagLatencyTrace`.
- Config namespace: `rag.retrieval.rerank-guard.*`.

---

## 6. Danh sách file đã đọc

| File | Đọc để làm gì | Kết luận chính |
|---|---|---|
| `rag/rerank/RerankService.java` | Hiểu rerank API, fallback, flags | `scoreCandidates()` gọi Cohere; không có guard |
| `rag/retrieve/RagRetrievalService.java` | Tìm call site, hiểu cheapScored availability | Call site line ~1240; `cheapScored` available before rerank |
| `rag/analysis/QueryAnalyzerService.java` | Có OOS QueryType không? | Không — chỉ có NORMAL_FACT, TABLE_LOOKUP, LIST_ALL, COUNT_QUERY, SECTION_SUMMARY, CROSS_PAGE_SECTION |
| `audit/metrics/RagLatencyTrace.java` | Hiểu cấu trúc fields và finish() | Add fields at end of log without breaking format |
| `resources/application.yml` | Tìm config namespace | Dùng `rag.retrieval.rerank-guard.*` mới |

---

## 7. Danh sách file đã sửa

| File | Sửa để làm gì | Ảnh hưởng lớp |
|---|---|---|
| `rag/rerank/RerankGuard.java` (NEW) | Guard component với 3 rules | service |
| `rag/retrieve/RagRetrievalService.java` | Inject guard, wrap rerank call | service |
| `audit/metrics/RagLatencyTrace.java` | Add rerankSkipped/rerankSkipReason/rerankGuardCandidates fields | audit |
| `resources/application.yml` | Add rerank-guard config block | config |
| `test/rag/rerank/RerankGuardTest.java` (NEW) | 8 unit tests | test |

---

## 8. Diff thay đổi của từng file

### `RerankGuard.java` (NEW)

Full file — 3 rules evaluated in order:
- Rule A: `candidateCount <= skipWhenCandidatesLte` (default 5)
- Rule B: `topScore - secondScore >= skipWhenTopScoreGapGte` (default 0.35)
- Rule D: `queryType == TABLE_LOOKUP && topCheapScore >= skipExactLookupWhenCellScoreGte` (default 0.80)

### `RagRetrievalService.java`

```diff
+import KLTN.RAG_CHATBOT_BE.rag.rerank.RerankGuard;
 import KLTN.RAG_CHATBOT_BE.rag.rerank.RerankService;

+    private final RerankGuard rerankGuard;

-        if (rerankService.isEnabled()) {
-            List<RerankService.ScoredChunk> reranked = rerankService.scoreCandidates(question, expensiveSubset);
-            for (RerankService.ScoredChunk rc : reranked) {
-                ...
-            }
-            if (!rerankScores.isEmpty()) { scorer = "RERANK_SERVICE"; }
-        }
+        if (rerankService.isEnabled()) {
+            double topCheapScore = cheapScored.isEmpty() ? 0.0 : cheapScored.get(0).score();
+            double secondCheapScore = cheapScored.size() < 2 ? 0.0 : cheapScored.get(1).score();
+            RerankGuard.Decision guardDecision = rerankGuard.decide(
+                    queryType, expensiveSubset.size(), topCheapScore, secondCheapScore);
+            RagLatencyTrace guardTrace = RagLatencyTrace.current();
+            if (guardTrace != null) {
+                guardTrace.setRerankGuardDecision(
+                        guardDecision.shouldSkip(), guardDecision.reason().name(), expensiveSubset.size());
+            }
+            if (!guardDecision.shouldSkip()) {
+                // existing rerank call unchanged
+            }
+        }
```

### `RagLatencyTrace.java`

```diff
+    private boolean rerankSkipped;
+    private String rerankSkipReason = "NOT_SKIPPED";
+    private int rerankGuardCandidateCount;

+    public void setRerankGuardDecision(boolean skipped, String reason, int candidateCount) { ... }

// finish() log appended:
-  "requestedMaxTokens={} effectiveMaxTokens={} outputTokens={}"
+  "requestedMaxTokens={} effectiveMaxTokens={} outputTokens={} rerankSkipped={} rerankSkipReason={} rerankGuardCandidates={}"
```

### `application.yml`

```diff
+    rerank-guard:
+      enabled: true
+      skip-when-candidates-lte: 5
+      skip-when-top-score-gap-gte: 0.35
+      skip-exact-lookup-when-cell-score-gte: 0.80
```

---

## 9. Ảnh hưởng sau sửa

| Behavior | Trước 27C | Sau 27C |
|---|---|---|
| Exact lookup (R1, R2) rerank | called (~344–694ms) | **skipped (0ms)** |
| List query (R3, R4) rerank | called | **still called** (guard didn't fire) |
| OOS (R5) rerank | called | **still called** (220 candidates, count > 5) |
| Answer correctness R1–R4 | PASS | **PASS** |
| OOS refusal R5 | PASS | **PASS** |
| guard disabled | N/A | behavior identical to pre-27C |
| rerank globally disabled | unchanged | unchanged |
| rerank API fail | fallback | fallback unchanged |
| `rerankMs` in trace | actual latency | **0 when skipped** |
| New trace fields | absent | `rerankSkipped`, `rerankSkipReason`, `rerankGuardCandidates` |

---

## 10. Edge cases đã xem xét

| Case | Xử lý |
|---|---|
| `cheapScored` empty (0 candidates) | `topCheapScore=0, secondCheapScore=0` → gap=0 < threshold → no skip |
| 1 candidate | `secondCheapScore=0` → gap = topScore → nếu ≥ 0.35 skip (correct) |
| `queryType=null` | Rule D không fire (null check) |
| `guardEnabled=false` | return `NOT_SKIPPED` immediately |
| rerank globally disabled | guard never called (outer `isEnabled()` check blocks) |
| OOS query with high gap | Rule B fires → `TOP_SCORE_GAP` → rerank skipped (acceptable) |

---

## 11. Kết quả kiểm tra

| Command | Kết quả | Ghi chú |
|---|---|---|
| `cd Backend && .\mvnw.cmd clean test` | **PASS** | 93 tests, 0 failures, 0 errors |
| Runtime R1 correctness | **PASS** | Vân Anh, E301 |
| Runtime R2 correctness | **PASS** | Thanh Nhàn, B301 |
| Runtime R3 correctness | **PASS** | Kiến trúc K46 HK2 courses |
| Runtime R4 correctness | **PASS** | Công nghệ sinh học K46 HK2 courses |
| Runtime R5 OOS | **PASS** | refusal |
| rerankMs R1/R2 | **0** (skipped) | confirmed guard fires |
| rerankMs R3/R4/R5 | ~344–401ms | rerank called (expected) |

---

## 12. Rủi ro còn lại

1. **Rule B uses cheap pre-score as proxy** — if cheapScored top-2 gap is high but rerank would significantly change the order of the remaining candidates (beyond rank 1), those candidates' ordering changes slightly. Acceptable: only final selection (top-N) matters, and rank-1 is already the correct answer.
2. **R5 OOS still calls rerank** — OOS guard deferred. 220 candidate OOS query pays ~362ms rerank cost. Accept: Rule C needs reliable OOS signal.
3. **TABLE_LOOKUP Rule D not confirmed to fire** — threshold 0.80 may be conservative; runtime R1/R2 fired via Rule B (gap) before Rule D could be evaluated.

---

## 13. Đề xuất tiếp theo

- **27D**: Add OOS QueryType to `QueryAnalyzerService` if reliable signal can be identified — enable Rule C.
- **Monitoring**: Track `rerankSkipped` rate in logs; if > 80% of queries skip rerank, consider whether cost of always-on Cohere subscription is justified.
- **Tuning**: Adjust `skip-when-top-score-gap-gte` threshold based on observed gap distribution in production logs.
