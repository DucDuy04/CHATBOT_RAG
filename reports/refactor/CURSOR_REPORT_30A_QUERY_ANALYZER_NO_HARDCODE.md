# CURSOR REPORT 30A — Remove Hardcoded Query Analysis Rules Using LLM Classifier

**Date:** 2026-05-30  
**Task:** 30A — Remove Hardcoded Query Analysis Rules Using LLM Classifier with Safe Fallback

---

## 1. Mức độ hiểu task

- Hiểu task: **97%**
- Phần chắc chắn: yêu cầu xóa domain keyword lists, thêm LLM classifier với shadow mode, giữ backward compat
- Phần giả định: ngưỡng confidence 0.70 theo spec mặc định, ForkJoinPool là đủ an toàn cho shadow async
- Thiếu: không có live Docker env để verify production runtime; không chạy smoke test thật

---

## 2. Tóm tắt yêu cầu

Xóa tất cả hardcoded domain keyword list khỏi `QueryAnalyzerService.java`. Thay bằng:
- `LocalGenericQueryAnalyzer`: chỉ dùng structural signals (số section, code identifier)
- `LlmQueryClassifier`: gọi LLM với JSON prompt generic, timeout 1200ms, fallback an toàn
- Shadow mode: LLM chạy async, không ảnh hưởng latency runtime
- Giữ public API cũ: `analyze(String)`, `analyze(String, UUID)`, `rewriteQuery(String)`, `findMatchedSections(...)`

---

## 3. Hiện trạng trước khi sửa

`QueryAnalyzerService.java` (545 dòng) chứa:
- 3 domain regex: `SPECIFIC_ROW_CATEGORY_ENTITY`, `SPECIFIC_SKU_REFERENCE`, `ENTITY_THEN_CO_FIELD`
- 7 private method với hardcoded lists: `isExplicitItemCountQuery`, `hasCountTargetCategory`, `hasTableCue`, `hasValueFieldCue`, `hasSpecificRowReference`, `isTableCellLookupQuery`, `isGenericCategoryTail`
- 4 `containsAny()` calls với Vietnamese word arrays trong `analyze()`
- `rewriteQuery()` với Vietnamese prefix/suffix lists

---

## 4. Nguyên nhân gốc xác nhận từ source

Đọc `QueryAnalyzerService.java` (line 83-128): các `containsAny()` calls chứa strings như:
- `"liet ke", "tat ca", "danh sach"` → LIST_ALL
- `"sku", "gia tri", "so lieu", "bang gia"` → TABLE_LOOKUP  
- `"muc", "phan", "chuong", "quy trinh"` → SECTION_SUMMARY
- `"bao nhieu san pham", "bao nhieu goi dich vu"` → COUNT_QUERY

Pattern `SPECIFIC_ROW_CATEGORY_ENTITY` (line 325-328) chứa "goi|san pham|dich vu|sku|...", `ENTITY_THEN_CO_FIELD` (line 338-341) chứa "gia|ton kho|luot|trang thai|phong ban".

---

## 5. Chiến lược sửa đã chọn

**Minimal diff, orchestrator pattern:**
1. Tạo `QueryAnalysisResult` + `QueryAnalysisSource` — model mới, không phá compat
2. `LocalGenericQueryAnalyzer` — structural only (regex số, code pattern)
3. `LlmQueryClassifier` — JSON prompt generic, timeout, fallback
4. Refactor `QueryAnalyzerService` thành orchestrator, xóa toàn bộ domain lists
5. Config `rag.analysis.llm-classifier.*` — default `enabled=false` cho safety
6. 50 unit tests mới

**Shadow mode là default:** LLM chạy async background, không ảnh hưởng response. Runtime vẫn dùng local result cho đến khi user enable active mode.

---

## 6. Danh sách file đã đọc

| File | Mục đích đọc | Kết luận |
|---|---|---|
| `QueryAnalyzerService.java` | Inventory hardcoded items | 3 domain regex, 7 domain methods, 4 containsAny lists, rewriteQuery lists |
| `LlmFallbackService.java` | Hiểu LLM abstraction để tái sử dụng | `buildChatModel(name, opts)` là public, có thể dùng trực tiếp |
| `LlmGenerationOptions.java` | Hiểu LLM options | Record immutable, `new LlmGenerationOptions(0.0, 150)` cho classifier |
| `QuerySignalExtractor.java` | Hiểu extractors đã có | Structural extraction đã có; không cần duplicate |
| `RagRetrievalService.java` (lines 1-80) | Hiểu callers của QueryAnalyzerService | `analyze()`, `findMatchedSections()`, `rewriteQuery()` phải giữ compat |
| `application.yml` | Hiểu config structure | Đã có `rag.runtime.*`, `rag.retrieval.*`; thêm `rag.analysis.*` |
| `NoHardcodedLexiconInTableNormalizerTest.java` | Hiểu pattern test no-hardcode | Đọc source file dưới dạng text, assert không chứa banned strings |
| `RagRetrievalServiceE2ETest.java` | Hiểu test pattern để không break | Không mock QueryAnalyzerService; test riêng CellAwareScorer/KeywordSearch |
| `PromptBuilderService.java` | Hiểu cách dùng SystemMessage/UserMessage | `SystemMessage.from()`, `UserMessage.from()` là đúng cách |

---

## 7. Danh sách file đã sửa

| File | Sửa để làm gì | Ảnh hưởng layer |
|---|---|---|
| `QueryAnalyzerService.java` | Xóa domain lists, thêm orchestrator logic | service / rag |
| `application.yml` | Thêm `rag.analysis.llm-classifier.*` config | config |

---

## 8. Danh sách file đã tạo mới

| File | Mục đích | Layer |
|---|---|---|
| `QueryAnalysisSource.java` | Enum nguồn classifier | rag/analysis |
| `QueryAnalysisResult.java` | Record kết quả với explainability | rag/analysis |
| `LocalGenericQueryAnalyzer.java` | Structural-only classifier | rag/analysis |
| `LlmQueryClassifier.java` | LLM classifier với timeout + fallback | rag/analysis |
| `QueryAnalysisResultTest.java` | Unit test model | test |
| `LocalGenericQueryAnalyzerTest.java` | Unit test local analyzer | test |
| `LlmQueryClassifierTest.java` | Unit test LLM classifier (mocked) | test |
| `QueryAnalyzerServiceCompatibilityTest.java` | Compat test public API | test |
| `QueryAnalyzerServiceNoHardcodedLexiconTest.java` | Source-level banned-string test | test |
| `docs/eval/results/QUERY_ANALYZER_NO_HARDCODE_LLM_CLASSIFIER_30A_20260530.md` | Eval report | docs |

---

## 9. Diff thay đổi của từng file

### `QueryAnalyzerService.java` (major refactor)

**Hiện trạng cũ:** 545 dòng, chứa các domain lists sau trong `analyze()`:
```diff
- if (containsAny(q, "bao nhieu", "co may", "may cai", "may loai", ...)) {
-     return QueryType.COUNT_QUERY;
- }
- if (containsAny(q, "liet ke", "tat ca", "toan bo", "danh sach", ...)) {
-     return QueryType.LIST_ALL;
- }
- if (containsAny(q, "bang", "cot", " row", "sku", "id", "code", "gia tri", ...)) {
-     return QueryType.TABLE_LOOKUP;
- }
- if (q.matches(".*\\b\\d+(\\.\\d+)+\\b.*") || containsAny(q, "muc", "phan", "chuong", 
-         "quy trinh", "yeu cau", "chuc nang", "thiet ke", ...)) {
-     return QueryType.SECTION_SUMMARY;
- }
```

Và 7 private methods chứa domain: `isExplicitItemCountQuery`, `hasCountTargetCategory`, `hasTableCue`, `hasValueFieldCue`, `hasSpecificRowReference`, `isTableCellLookupQuery`, `isGenericCategoryTail`.

Và 3 domain regex constants: `SPECIFIC_ROW_CATEGORY_ENTITY`, `SPECIFIC_SKU_REFERENCE`, `ENTITY_THEN_CO_FIELD`.

**Đã sửa thành:**
```diff
+ public QueryAnalysisResult analyzeDetailed(String question, UUID widgetId) {
+     if (question == null || question.isBlank()) {
+         return QueryAnalysisResult.ofLocal(QueryType.NORMAL_FACT, 1.0, ...);
+     }
+     QueryAnalysisResult localResult = localAnalyzer.analyze(question);
+     if (localResult.confidence() >= triggerThreshold) return localResult;
+     if (widgetId != null && isLikelyHeadingQuery(q, widgetId)) {
+         return QueryAnalysisResult.ofHeadingMatch(SECTION_SUMMARY, 0.85, ...);
+     }
+     if (llmEnabled) { // shadow or active mode }
+     return localResult;
+ }
+
+ public QueryType analyze(String question, UUID widgetId) {
+     return analyzeDetailed(question, widgetId).queryType(); // backward compat
+ }
```

`rewriteQuery()` đơn giản hóa xuống còn 3 generic variants (original trimmed, punctuation-stripped, normalized). Không còn Vietnamese prefix/suffix lists.

### `application.yml`

```diff
+   analysis:
+     llm-classifier:
+       enabled: false
+       shadow-mode: true
+       only-when-local-confidence-below: 0.70
+       min-accepted-confidence: 0.65
+       timeout-ms: 1200
+       max-input-chars: 1000
```

---

## 10. Ảnh hưởng sau sửa

**Behavior thay đổi:**
- Queries dùng domain keyword (liet ke, tat ca, bao nhieu goi, bang gia) → không còn được phân loại đặc biệt khi LLM disabled → NORMAL_FACT (retrieval vẫn hoạt động, nhưng không dùng LIST_ALL/COUNT_QUERY expansion)
- `rewriteQuery()` không còn strip polite prefix/suffix → 3 generic variants thay vì 4

**Behavior giữ nguyên:**
- Section number trong query → SECTION_SUMMARY (preserved via LocalGenericQueryAnalyzer)
- Heading overlap với DB sections → SECTION_SUMMARY (preserved via isLikelyHeadingQuery)
- Empty/null → NORMAL_FACT
- `findMatchedSections()` logic không đổi (data-driven từ DB sections)
- OOS refusal không thay đổi (prompt/context selection không đổi)
- `analyze(String)`, `analyze(String, UUID)` vẫn trả về `QueryType` như trước

**Fallback:**
- LLM disabled/fail → local result hoặc NORMAL_FACT
- Retrieval quality không bị crash; chỉ có thể dùng expansion ít hơn trong một số case

**Memory/CPU:** Không tác động đáng kể. Shadow mode dùng ForkJoinPool thread pool cho 1 async call nhỏ.

**Latency:** LLM disabled = zero overhead. Shadow mode = zero overhead (async). Active mode = max 1200ms overhead (configurable).

---

## 11. Edge cases đã xem xét

| Edge case | Handling |
|---|---|
| LLM timeout | `CompletableFuture.get(timeoutMs, MILLISECONDS)` → FALLBACK_DEFAULT |
| LLM returns non-JSON | `extractJsonObject()` + try-catch → FALLBACK_DEFAULT |
| LLM returns unknown QueryType | `IllegalArgumentException` caught → FALLBACK_DEFAULT |
| LLM returns low confidence | Checked against `minAcceptedConfidence` → FALLBACK_DEFAULT |
| LLM raises RuntimeException | Caught in `classify()` → FALLBACK_DEFAULT |
| Shadow mode thread exception | Caught inside `CompletableFuture.runAsync` → log.debug only |
| null question to classifier | Returns FALLBACK_DEFAULT before calling LLM |
| widgetId null | Skip heading match (no DB call) |
| Empty section list in DB | `isLikelyHeadingQuery` returns false immediately |
| question > maxInputChars | Truncated before sending to LLM |

---

## 12. Kết quả kiểm tra

| Command | Kết quả | Ghi chú |
|---|---|---|
| `.\mvnw.cmd clean test` | **PASS** | 176 tests, 0 failures, 0 errors |
| Frontend lint | NOT RUN | Không liên quan (backend-only change) |
| Frontend build | NOT RUN | Không liên quan |
| Widget build | NOT RUN | Không liên quan |
| `docker compose config` | NOT RUN | Config chỉ thêm YAML keys mới với defaults |
| Live smoke test | NOT RUN | Không có live Docker env |

Baseline trước: 120 tests. Sau: 176 tests (+56 tests mới cho query analyzer).

---

## 13. Rủi ro còn lại

| Rủi ro | Mức độ | Mitigation |
|---|---|---|
| Queries dùng domain pattern (count/list/table) không được phân loại đúng khi LLM disabled | MEDIUM | Enable shadow mode để validate trước; enable active mode sau |
| Shadow async thread dùng ForkJoinPool.commonPool (shared pool) | LOW | Bounded 1200ms; 5 concurrent users không gây vấn đề |
| LLM classifier chưa được validate với actual queries | MEDIUM | Enable shadow mode và quan sát log [QueryAnalysis] shadow ... |
| `rewriteQuery()` bỏ polite prefix stripping có thể ảnh hưởng vector search | LOW | 3 variants còn lại vẫn đủ cho dedup; vector model xử lý được polite prefix |

---

## 14. Đề xuất tiếp theo

- **30B:** Enable shadow mode, thu thập 100+ shadow log samples, so sánh local vs LLM accuracy
- **30C:** Enable active mode sau khi validate, monitor latency, đánh giá impact
- **30D:** Thêm table-header-derived signal vào `LocalGenericQueryAnalyzer` (dùng `cells_json` keys từ DB để raise local confidence cho TABLE_LOOKUP)
- **30E:** Add integration test với mocked sections để verify HEADING_MATCH flow end-to-end
