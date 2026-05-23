# CURSOR_REPORT 23F5 — Relevance-Aware Final Budget Fix

## 1. Mức độ hiểu task

**95%**

- Chắc chắn: root cause 23F4, phạm vi sửa `dedupeSortBudget`, không đổi Top-K/sources/UI/parser.
- Giả định nhỏ: PDF/table regression (Runtime D) không chạy vì không có script sẵn trong loop này.
- Không thiếu dữ kiện cho fix chính.

## 2. Tóm tắt yêu cầu

Sau expansion, khi pool > `FINAL_LIMIT_EXPANDED`, chọn chunk theo relevance với query (entity terms), rồi sort lại document order — tránh rơi Eta/Theta ở cuối tài liệu khi topK=20.

## 3. Phạm vi đã làm

- `FinalContextSelector.java` (mới)
- `RagRetrievalService.dedupeSortBudget`
- `FinalContextSelectorTest.java`
- Runtime verify + docs

## 4. Phạm vi không làm

Parser, PromptBuilder, QueryAnalyzer, ChatService source cap, Frontend, DB schema, Top-K semantics, dependency, `.gitignore`, hardcode entity.

## 5. File đã đọc

| Path | Mục đích | Kết luận |
|------|----------|----------|
| `RAG_TARGETED_ETA_THETA_RETRIEVAL_MISS_AUDIT_23F4_20260517.md` | Evidence 23F4 | Cap document-order là root cause |
| `RagRetrievalService.java` | `dedupeSortBudget` | Linear cap sau sort |
| `RetrievedContext.java` | Score field | `score` không được set ở budget step |
| `RerankService.java` | Rerank | Score không persist vào DTO |
| `QueryAnalyzerService.java` | LIST_ALL | Expanded query type |
| `ChatService.java` | Source cap | Không sửa — cap 5/2 giữ nguyên |

## 6. Root cause từ 23F4

`dedupeSortBudget`: 25 unique → sort doc order → lấy 20 đầu → `sec_idx_24`/`sec_idx_28` bị loại.

## 7. Phân tích dedupeSortBudget trước sửa

- Dedup: `chunk.id`
- Sort: `(documentId, sectionOrder, orderIndex)` + summary-first khi expanded
- Cap: sequential first-N + char limit

## 8. Option đã chọn

**Option A/B hybrid** — `FinalContextSelector.selectByRelevanceBudget`: protected term matches + relevance fill + document-order output.

## 9. Thiết kế

- `extractImportantTerms`: normalize NFD, stopwords (`ma`, `chinh`, `sach`, `liet`, `ke`, …).
- `relevanceScore`: +4 title, +3 heading, +2 content per term.
- Trigger: `unique.size() > finalLimit` AND (`isExpanded(queryType)` OR có important terms).
- Không hardcode entity names.

## 10. File đã sửa

| Path | Layer | Ảnh hưởng |
|------|-------|-----------|
| `FinalContextSelector.java` | service | Final budget selection |
| `RagRetrievalService.java` | service | Gọi selector |
| `FinalContextSelectorTest.java` | test | Regression unit tests |

## 11. Diff từng file

### `FinalContextSelector.java` (new)

```diff
+ final class FinalContextSelector {
+   static boolean shouldUseRelevanceAwareSelection(QueryType, String question)
+   static List<DocumentChunk> selectByRelevanceBudget(...)
+   // extractImportantTerms, relevanceScore, normalizeForSearch, BUDGET_STOPWORDS
+ }
```

### `RagRetrievalService.java`

```diff
-        for (DocumentChunk c : sorted) {
-            if (result.size() >= finalLimit) { break; }
-            ...
-        }
+        if (unique.size() > finalLimit && FinalContextSelector.shouldUseRelevanceAwareSelection(...)) {
+            budgeted = FinalContextSelector.selectByRelevanceBudget(sorted, question, finalLimit, maxContextChars);
+        } else {
+            budgeted = FinalContextSelector.capByDocumentOrder(sorted, finalLimit, maxContextChars);
+        }
```

## 12. Ảnh hưởng sau sửa

| Behavior | Thay đổi |
|----------|----------|
| LIST_ALL / expanded pool > 20 | Chọn theo query terms, rồi sort doc order |
| Pool ≤ limit | Giữ logic cũ (không relevance path) |
| NORMAL_FACT generic | `capByDocumentOrder` |
| Vector Top-K | Không đổi |
| Source cap production | Không đổi |
| Latency/token | ~same final count; scoring O(n) trên pool nhỏ |

## 13. Edge cases

- Protected > limit: chọn protected theo score desc, không vượt limit.
- Query chỉ stopwords → fallback document-order cap.
- OOS entity không trong doc → score 0 → không protect.
- Char budget vẫn áp dụng khi add chunk.

## 14. Kết quả kiểm tra

| Command | Kết quả | Ghi chú |
|---------|---------|---------|
| `mvnw -DskipTests compile` | PASS | |
| `mvnw -Dtest=FinalContextSelectorTest test` | PASS | 7 tests |
| `mvnw test` | PARTIAL | 2 lỗi Qdrant integration có sẵn |
| Frontend lint/build | NOT RUN | Ngoài scope |
| Widget build | NOT RUN | Ngoài scope |
| `docker compose config` | PASS | |
| Runtime 23F5 script | PASS | `_run_23f5_results.json` |

## 15. Rủi ro còn lại

- Tài liệu rất lớn với >20 entity trong một câu LIST_ALL vẫn có thể thiếu mã nếu pool expansion không chứa chunk.
- Rerank score chưa dùng cho tie-break.
- Runtime D (PDF table) chưa verify trong task này.

## 16. Bước tiếp theo

- Chạy PDF/table regression nếu cần đóng loop 23B3.
- Cân nhắc ghi `score` từ rerank vào context khi enabled.
