# CURSOR REPORT 27B — Startup Keyword Index Prewarm

**Date:** 2026-05-30  
**Task:** 27B — Startup Keyword Index Prewarm After Backend Restart  
**Verdict:** PARTIAL PASS

---

## 1. Mức độ hiểu task

- **Hiểu task:** 100%
- **Phần chắc chắn:** flow prewarm, repository method, config, tests, thread safety
- **Phần còn giả định:** runtime latency improvement (chưa verify live env)
- **Thiếu dữ kiện:** không có live Docker/MySQL/Qdrant để verify startup log thật

---

## 2. Tóm tắt yêu cầu

Sau backend restart, `KeywordIndexCache` rỗng. Chat đầu tiên cho mỗi widget phải build lại keyword index từ DB — đo được ≈12s (27A baseline). Task yêu cầu prewarm tự động khi backend khởi động để user không phải chịu cold latency đó.

---

## 3. Hiện trạng trước khi sửa

- `KeywordIndexCache.warm(widgetId, corpusLimit)` đã có từ 27A — được gọi sau ingest.
- Sau restart: cache trống, không có startup warm, first-chat vẫn trả buildMs ≈ 12s.
- Không có `ApplicationReadyEvent` listener.
- Không có `existsByWidgetConfigId` trong `DocumentChunkRepository`.
- `application.yml` không có `rag.retrieval.keyword-index.*` entries (chỉ có defaults trong code).

---

## 4. Nguyên nhân gốc xác nhận từ source

`KeywordIndexCache` dùng `ConcurrentHashMap<UUID, CacheEntry>` in-memory. Map này trống mỗi khi JVM restart. `warm()` đã có nhưng không ai gọi sau startup. First `lookup()` call từ `RagRetrievalService` trigger build index — với corpus 3000 chunks, build mất ≈12s.

---

## 5. Chiến lược sửa đã chọn

- Thêm `@EventListener(ApplicationReadyEvent.class)` component nhỏ (`KeywordIndexStartupPrewarmer`).
- Async by default (daemon thread) — không block startup.
- Query `findAll()` widgets (filtered by `@SQLRestriction`) + `existsByWidgetConfigId` để skip widget rỗng.
- Cap bằng `prewarm-max-widgets` (default 20).
- One-widget-failure không dừng toàn bộ warm.
- Config flags để disable khi cần.

---

## 6. Danh sách file đã đọc

| File | Đọc để làm gì | Kết luận chính |
|---|---|---|
| `rag/retrieve/KeywordIndexCache.java` | Hiểu warm() implementation | Dùng ConcurrentHashMap.compute(), thread-safe, buildIndex() là blocking |
| `service/DocumentService.java` | Hiểu corpus limit config key | `${rag.retrieval.hybrid.max-keyword-scan-chunks:3000}` là corpus limit |
| `domain/widget/WidgetConfigRepository.java` | Tìm method list active widgets | findAll() + @SQLRestriction auto-filter deleted |
| `domain/document/DocumentChunkRepository.java` | Tìm existence check method | Không có — cần thêm existsByWidgetConfigId |
| `domain/widget/WidgetConfig.java` | Hiểu entity, @SQLRestriction | @SQLRestriction("deleted_at IS NULL") filter tự động |
| `domain/document/DocumentChunk.java` | Confirm @SQLRestriction | @SQLRestriction("deleted_at IS NULL") filter tự động |
| `resources/application.yml` | Xem config namespace hiện tại | rag.retrieval.keyword-index.* chưa có entry |
| `resources/application-dev.yml` | Xem dev overrides | Không override keyword-index namespace |
| `resources/application-docker.yml` | Xem docker overrides | Không override keyword-index namespace |
| `test/rag/retrieve/KeywordIndexCacheTest.java` | Pattern test hiện tại | @ExtendWith(MockitoExtension.class), no Spring context |

---

## 7. Danh sách file đã sửa

| File | Sửa để làm gì | Ảnh hưởng lớp |
|---|---|---|
| `Backend/src/main/resources/application.yml` | Thêm prewarm config block | config |
| `Backend/src/main/java/.../domain/document/DocumentChunkRepository.java` | Thêm existsByWidgetConfigId | db |
| `Backend/src/main/java/.../rag/retrieve/KeywordIndexStartupPrewarmer.java` (NEW) | Startup prewarm component | service |
| `Backend/src/test/java/.../rag/retrieve/KeywordIndexStartupPrewarmerTest.java` (NEW) | Unit tests 5 scenarios | test |

---

## 8. Diff thay đổi của từng file

### `application.yml`

```diff
 rag:
   retrieval:
     vector-anchor-k: 30
+    keyword-index:
+      # TTL and eviction (defaults in KeywordIndexCache: ttl=3600000ms, max-widgets=25)
+      ttl-ms: 3600000
+      max-widgets: 25
+      # Startup prewarm
+      prewarm-on-startup: true
+      prewarm-async: true
+      prewarm-max-widgets: 20
+      # Should match rag.retrieval.hybrid.max-keyword-scan-chunks (3000)
+      prewarm-corpus-limit: 3000
+      prewarm-delay-ms: 2000
     hybrid:
```

**Vì sao:** Tạo explicit config block cho prewarm, đồng thời document ttl-ms và max-widgets (trước đây chỉ có defaults trong code).

### `DocumentChunkRepository.java`

```diff
+    /**
+     * Lightweight active-chunk check for startup prewarm — respects
+     * {@code @SQLRestriction("deleted_at IS NULL")} on {@link DocumentChunk}.
+     * Returns {@code true} only when at least one non-deleted chunk exists for the widget.
+     */
+    boolean existsByWidgetConfigId(UUID widgetConfigId);
+
     /**
      * Soft-delete all chunks for a document...
```

**Vì sao:** Cần check nhanh widget có chunk hay không mà không load toàn bộ chunk. Spring Data JPA + Hibernate `@SQLRestriction` tự thêm `deleted_at IS NULL` vào query.

### `KeywordIndexStartupPrewarmer.java` (NEW)

Full file — xem source. Key behavior:
- `@EventListener(ApplicationReadyEvent.class)` → trigger sau khi app fully started
- `prewarmAsync=true` → Executors.newSingleThreadExecutor với daemon thread
- `prewarmDelayMs=2000` → sleep trước khi warm (DB connection pool settle)
- Per-widget try-catch: fail một widget, log ERROR, tiếp tục warm widget tiếp theo
- `findEligibleWidgetIds()`: findAll() → filter existsByWidgetConfigId → limit(cap)

### `KeywordIndexStartupPrewarmerTest.java` (NEW)

5 tests, no Spring context, no DB/Qdrant required.  
Dùng `ReflectionTestUtils.setField` để inject `@Value` fields.  
`prewarmAsync=false`, `prewarmDelayMs=0` để test đồng bộ, nhanh.

---

## 9. Ảnh hưởng sau sửa

| Behavior | Trước 27B | Sau 27B |
|---|---|---|
| First chat after restart | keywordIndexBuildMs ≈ 12s | ≈ 0ms (index prewarmed) |
| Subsequent chats | unchanged | unchanged |
| Backend startup time | unaffected | +2s delay (prewarm chạy async, không block startup) |
| OOS refusal | correct | correct (retrieval semantics unchanged) |
| Deleted widget warmed | N/A | NO — @SQLRestriction filters out |
| Widget without chunks warmed | N/A | NO — existsByWidgetConfigId returns false |
| DB/JVM memory | unchanged | keyword index loaded earlier (same memory footprint) |
| Prewarm disabled | N/A | prewarm-on-startup=false → log và return |

---

## 10. Edge cases đã xem xét

| Edge case | Xử lý |
|---|---|
| DB không ready khi prewarm | try-catch trong findEligibleWidgetIds() — log ERROR, return gracefully |
| Widget bị xóa | @SQLRestriction trên WidgetConfig tự filter |
| Widget có chunks bị soft-delete | @SQLRestriction trên DocumentChunk tự filter |
| Một widget warm fail | per-widget try-catch, log ERROR, tiếp tục widget khác |
| Quá nhiều widget | prewarm-max-widgets cap (default 20) |
| prewarm-delay-ms bị interrupt | Thread.currentThread().interrupt(), log WARN, return |
| prewarm-on-startup=false | log và return ngay, không query DB |
| prewarm-async=true + shutdown nhanh | daemon thread → JVM có thể kill nó khi shutdown |
| Corpus limit mismatch | prewarm-corpus-limit có thể set độc lập vs max-keyword-scan-chunks |
| Empty widget list | findAll() trả rỗng → loop không chạy, log finished ok=0 failed=0 |

---

## 11. Kết quả kiểm tra

| Command | Kết quả | Ghi chú |
|---|---|---|
| `cd Backend && .\mvnw.cmd clean test` | **PASS** | 82 tests, 0 failures, 0 errors |
| Frontend lint | NOT RUN | Không sửa Frontend |
| Frontend build | NOT RUN | Không sửa Frontend |
| Widget build | NOT RUN | Không sửa Frontend |
| `docker compose config` | NOT RUN | application.yml thêm config, không sửa docker-compose |
| Runtime startup log | NOT RUN | Không có live env với valid API keys |

---

## 12. Rủi ro còn lại

1. **Runtime verification pending** — chưa verify startup log thật với live MySQL/Qdrant.
2. **prewarm-delay-ms=2000** có thể cần tăng nếu DB connection pool chậm hơn 2s (hiếm nhưng có thể trên prod yếu).
3. `widgetConfigRepository.findAll()` load tất cả widget vào memory (acceptable cho ≤30 widgets, cần monitor nếu scale lên).
4. Nếu warm một widget lớn (nhiều chunks) bị OOM trong background thread, JVM không crash (daemon thread) nhưng index đó sẽ không được prewarm.

---

## 13. Đề xuất tiếp theo

- **27C**: Runtime verify: chạy `docker compose up --build -d backend`, check log `[KeywordIndexPrewarm]`, đo `keywordIndexBuildMs` first chat sau restart.
- **27D (optional)**: Thêm `prewarm-order: recent-first` config — sort widget theo `updatedAt DESC` trước khi warm, ưu tiên widget đang hoạt động nhiều nhất.
- **Monitoring**: Thêm metric counter cho prewarm ok/failed (nếu có Micrometer/Actuator).
