# CURSOR REPORT 27D — Query Variant Dedupe (Qdrant Vector Search Optimization)
**Date:** 2026-05-30  
**Task:** 27D — Dedupe Qdrant Query Variants to Reduce Redundant Vector Search

---

## 1. Mức độ hiểu task

- **Hiểu task: 98%**
- **Phần chắc chắn:** Query variant generation flow, EmbeddingService.search() call pattern, dedupe key design, normalization rules, tracing fields, test structure.
- **Phần giả định:** Runtime benchmark expected impact (V1–V5) — ước tính dựa trên code analysis, chưa chạy live.
- **Thiếu dữ kiện:** Live backend (GROQ_API_KEY, Qdrant, MySQL) để benchmark runtime V1–V5 và đo qdrantSearchCalls thực tế.

---

## 2. Tóm tắt yêu cầu

Giảm số lần gọi Qdrant vector search bằng cách dedup query variants trước khi gọi `embeddingService.search()`. Chỉ dùng conservative normalized-text dedupe. Không thay đổi parser, schema, answer semantics, không cache final answer.

---

## 3. Hiện trạng trước khi sửa

- `QueryAnalyzerService.rewriteQuery()` tạo ra tối đa 4 variants, đã gọi `distinct()` (exact string equality).
- `RagRetrievalService` loop qua tất cả variants, gọi `embeddingService.search()` một lần per variant.
- `EmbeddingService.getCachedOrEmbedQuery()` cache embedding call theo normalized key — nhưng Qdrant REST search vẫn được gọi riêng cho mỗi variant.
- Không có tracing field nào về query variant count hay qdrantSearchCalls.
- Không có dedupe ở level normalized text — hai variant khác nhau về case/whitespace vẫn trigger 2 Qdrant calls.

---

## 4. Nguyên nhân gốc xác nhận từ source

File `RagRetrievalService.java` lines 229–264 (trước khi sửa):

```java
for (String variant : queryVariants) {
    List<TextSegment> anchors;
    try {
        anchors = embeddingService.search(variant, fixedVectorAnchorK, widgetId);
    } catch (Exception e) { ... }
    // process anchors...
}
```

`queryVariants` là `List<String>` từ `rewriteQuery()`, đã `distinct()` nhưng chỉ theo exact string. Các variants như:
- `"Kỹ năng mềm Nhóm 4"` (trimmed)
- `"Kỹ năng mềm Nhóm 4"` (stripped, same nếu không có prefix/suffix)
- `"kỹ năng mềm nhóm 4"` (normalized lowercase)

→ Gọi Qdrant 3 lần thay vì 1 lần.

---

## 5. Chiến lược sửa đã chọn

1. Tạo `QueryVariantDedupe` class mới trong `rag.retrieve` package.
2. Normalization: trim + lowercase + collapse whitespace + NFC + strip control chars.
3. Dedupe key: `normalizedText|scopeFilterKey|searchModeKey` — bảo tồn variants khác scope/mode.
4. Áp dụng dedupe trong `RagRetrievalService` trước vòng lặp Qdrant search.
5. Thêm 5 tracing fields vào `RagLatencyTrace`.
6. Thêm config `query-variant-dedupe.*` trong `application.yml`.
7. `enabled=false` → old behavior hoàn toàn preserved.

---

## 6. Danh sách file đã đọc

| File | Đọc để làm gì | Kết luận chính |
|---|---|---|
| `rag/retrieve/RagRetrievalService.java` | Xác nhận loop variant + Qdrant call pattern | Loop qua `queryVariants` gọi `embeddingService.search()` per variant, không có dedupe |
| `rag/analysis/QueryAnalyzerService.java` | Xem `rewriteQuery()` tạo bao nhiêu variants | 4 variants, `distinct()` exact string, không normalize |
| `index/embedding/EmbeddingService.java` | Xem embedding cache và Qdrant REST call | Cache chỉ saves embedding API call, không saves Qdrant call |
| `audit/metrics/RagLatencyTrace.java` | Xem các fields hiện có, nơi thêm fields mới | Có `vectorMs`, `rerankSkipped`, chưa có variant stats |
| `Backend/src/main/resources/application.yml` | Xem namespace config hiện có | Config under `rag.retrieval.*` — thêm `query-variant-dedupe` |
| `rag/rerank/RerankGuardTest.java` | Xem pattern test unit không cần Spring | Pattern: instantiate class trực tiếp + ReflectionTestUtils |
| `rag/retrieve/RagRetrievalServiceE2ETest.java` | Xem pattern integration test | No Spring/MySQL/Qdrant, test pure logic |
| `index/embedding/EmbeddingServiceQueryCacheIntegrationTest.java` | Xem mock pattern | Mockito + ReflectionTestUtils |

---

## 7. Danh sách file đã sửa

| File | Sửa để làm gì | Ảnh hưởng lớp |
|---|---|---|
| `Backend/src/main/resources/application.yml` | Thêm config block `query-variant-dedupe` | config |
| `Backend/.../audit/metrics/RagLatencyTrace.java` | Thêm 5 fields + setter + log format | audit/metrics |
| `Backend/.../rag/retrieve/RagRetrievalService.java` | Apply dedupe trước Qdrant loop, thêm `@Value` fields, đếm `qdrantSearchCalls` | service |
| `Backend/.../rag/retrieve/QueryVariantDedupe.java` | New class | service |
| `Backend/.../rag/retrieve/QueryVariantDedupeTest.java` | New test class (13 tests) | test |
| `Backend/.../rag/retrieve/QueryVariantDedupeIntegrationTest.java` | New test class (9 tests) | test |

---

## 8. Diff thay đổi của từng file

### 8.1 application.yml

```diff
+    query-variant-dedupe:
+      enabled: true
+      mode: normalized_text
+      cosine-enabled: false
+      cosine-threshold: 0.98
+      log-skipped-variants: true
```

Vì sao: cần config-gate để có thể tắt về old behavior.

### 8.2 RagLatencyTrace.java

```diff
+    // Query-variant dedupe fields (task 27D)
+    private int queryVariantTotal;
+    private int queryVariantUnique;
+    private int queryVariantSkipped;
+    private String queryVariantDedupeMode = "NONE";
+    private int qdrantSearchCalls;

+    public void setQueryVariantDedupeStats(int total, int unique, int skipped,
+                                           String dedupeMode, int qdrantCalls) { ... }
```

Log format extended với 5 fields mới ở cuối.

### 8.3 RagRetrievalService.java

```diff
+    @Value("${rag.retrieval.query-variant-dedupe.enabled:true}")
+    private boolean variantDedupeEnabled;

+    @Value("${rag.retrieval.query-variant-dedupe.log-skipped-variants:true}")
+    private boolean variantDedupeLogSkipped;

     // STEP 2: Query rewriting
     List<String> queryVariants = queryAnalyzerService.rewriteQuery(question);
     log.info("[RAG] Query variants: {}", queryVariants);

+    // STEP 2b: Deduplicate query variants before vector search
+    String scopeFilterKey = widgetId != null ? widgetId.toString() : "";
+    QueryVariantDedupe.DedupeResult dedupeResult = QueryVariantDedupe.dedupe(
+            queryVariants, variantDedupeEnabled, scopeFilterKey, "VECTOR");
+    // emit trace + log if skips occurred
+    ...
+    int qdrantSearchCalls = 0;

-    for (String variant : queryVariants) {
-        List<TextSegment> anchors;
-        try {
-            anchors = embeddingService.search(variant, fixedVectorAnchorK, widgetId);
+    for (QueryVariantDedupe.QueryVariant qv : dedupeResult.uniqueVariants()) {
+        String variant = qv.originalText();
+        List<TextSegment> anchors;
+        try {
+            anchors = embeddingService.search(variant, fixedVectorAnchorK, widgetId);
+            qdrantSearchCalls++;
         ...

+    // After loop: update trace with actual qdrantSearchCalls
+    if (trace != null) { trace.setQueryVariantDedupeStats(..., qdrantSearchCalls); }
```

Vì sao: thay loop over `List<String>` bằng loop over `dedupeResult.uniqueVariants()` — giữ semantics giống hệt nhưng bỏ qua variants bị dedup.

### 8.4 QueryVariantDedupe.java (new file)

```java
public final class QueryVariantDedupe {
    public enum SkipReason { NOT_SKIPPED, NORMALIZED_TEXT_DUPLICATE, DEDUPE_DISABLED }
    public record QueryVariant(String originalText, String normalizedKey, boolean skipped,
                               SkipReason skipReason, String representedBy) {}
    public record DedupeResult(List<QueryVariant> uniqueVariants, List<QueryVariant> allVariants,
                               int total, int unique, int skipped) {}

    public static DedupeResult dedupe(List<String> variants, boolean enabled,
                                      String scopeFilterKey, String searchModeKey) { ... }

    public static String normalizeKey(String text, String scopeFilterKey, String searchModeKey) { ... }
    public static String normalizeText(String text) { ... }
}
```

---

## 9. Ảnh hưởng sau sửa

| Dimension | Trước 27D | Sau 27D |
|---|---|---|
| Qdrant calls per request | `variants.size()` (1–4) | `unique_variants.size()` (1–4, reduced khi có dup) |
| vectorMs | Proportional to Qdrant calls | Same hoặc giảm nếu có duplicate variants |
| Embedding API calls | Đã được cache trong EmbeddingService | Không đổi (cache ở layer thấp hơn) |
| Recall | Full | Full — distinct variants không bị dedup |
| Answer correctness | Baseline | Không thay đổi (same candidates) |
| Memory/CPU | Không đổi đáng kể | Thêm LinkedHashMap nhỏ per request |
| Config backward-compat | N/A | `enabled=false` → old behavior hoàn toàn |

**Behavior giữ nguyên:** Scope filter, result merge, rerank, context selection — toàn bộ pipeline sau STEP 3 không thay đổi.

**Behavior thay đổi:** Số lần gọi `embeddingService.search()` có thể giảm khi variants normalize identically.

---

## 10. Edge cases đã xem xét

| Edge case | Xử lý |
|---|---|
| `variants == null` | Return empty DedupeResult |
| `variants.isEmpty()` | Return empty DedupeResult |
| `enabled=false` | Tất cả variants pass through với `DEDUPE_DISABLED` reason |
| Single variant | Never skipped |
| Variants với khác widgetId | Dedupe key bao gồm scopeFilterKey → không bị dedup |
| `widgetId == null` | `scopeFilterKey = ""` → vẫn consistent |
| Invisible control chars (U+200B, U+FEFF) | Strip trong `normalizeText()` |
| NFC/NFD unicode difference | NFC normalize trước khi lowercase |
| K46 vs K45, Nhóm 4 vs Nhóm 2 | Preserved — khác nhau sau normalize |
| First occurrence preserved | LinkedHashMap insertion order |
| Trace null (no active trace) | Guard null check trước `trace.set*` |

---

## 11. Kết quả kiểm tra

| Command | Kết quả | Ghi chú |
|---|---|---|
| `cd Backend; .\mvnw.cmd clean test` | PASS | 115 tests, 0 failures, 0 errors |
| Frontend lint | NOT RUN | Không trong scope task |
| Frontend build | NOT RUN | Không trong scope task |
| Widget build | NOT RUN | Không trong scope task |
| Docker compose config | NOT RUN | Không trong scope task |
| Runtime benchmark V1–V5 | NOT RUN | Cần live backend (Qdrant + MySQL + GROQ_API_KEY) |

---

## 12. Rủi ro còn lại

- **Runtime correctness V1–V5 chưa verify** — cần live backend.
- **Cosine dedupe chưa implement** — chỉ normalized-text dedupe.
- Nếu `rewriteQuery()` tạo ra variants tất cả đều unique sau normalize → không có latency gain (chỉ overhead nhỏ).
- Nếu `rewriteQuery()` thay đổi logic trong tương lai → dedupe tự động cập nhật (không cần sửa dedupe code).

---

## 13. Đề xuất tiếp theo

- **27E:** Runtime benchmark V1–V5 với live backend. Đo `qdrantSearchCalls` thực tế trước/sau.
- **27F (optional):** Cosine-similarity dedupe (`cosine-enabled: true`, threshold ≥ 0.98) — chỉ sau khi recall tests trên live data pass.
- Monitor `[RAG][variant-dedupe]` log trong production để xác nhận skip rate.
