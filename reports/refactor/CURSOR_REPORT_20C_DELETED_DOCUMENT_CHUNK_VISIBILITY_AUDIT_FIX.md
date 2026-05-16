# Cursor Report 20C — Deleted Document Chunk Visibility (RAG MySQL Retrieval)

**Ngày:** 2026-05-14  
**Scope:** Audit + fix tối thiểu để chunk/section/table của document đã soft-delete **không** còn được RAG load từ MySQL qua các repository query hiện dùng.

---

## 1. Mức độ hiểu task

| Mục | Nội dung |
|-----|----------|
| **Hiểu task** | **96%** |
| **Chắc chắn** | `Document`, `DocumentChunk`, `DocumentSection`, `DocumentTable` đều có `@SQLRestriction("deleted_at IS NULL")` trên **chính entity đó**; `softDeleteDocument` trước fix chỉ set `documents.deleted_at`; mọi query `DocumentChunkRepository` trong `RagRetrievalService` là derived query trên bảng chunk **không** join filter `document.deleted_at`. |
| **Giả định** | Dữ liệu cũ đã xóa document **trước** bản fix có thể vẫn để chunk `deleted_at` null (orphan) — cần one-off SQL hoặc re-delete nếu muốn sạch 100%. |
| **Thiếu dữ kiện** | Không chạy E2E runtime upload/delete/chat trên DB thật trong phiên này. |

---

## 2. Tóm tắt yêu cầu

Xác minh retrieval DB có lọt chunk/section/table thuộc document đã `deleted_at`; nếu có bug thì sửa minimal; không đổi API; không refactor RAG lớn; compile pass; report chi tiết.

---

## 3. Hiện trạng trước khi sửa

- `DocumentService.softDeleteDocument`: purge Qdrant → `document.setDeletedAt` → `save(document)`.
- **Không** cập nhật `deleted_at` trên `document_chunks`, `document_sections`, `document_tables`.
- Hibernate `@SQLRestriction` trên `DocumentChunk` chỉ áp dụng cột `document_chunks.deleted_at`, **không** suy ra từ parent `documents.deleted_at`.

---

## 4. Document delete flow hiện tại (SOURCE — sau fix)

| Bước | Hành động | Ghi chú |
|------|-----------|---------|
| 1 | `documentRepository.findById(id)` | Document vẫn visible (chưa set `deleted_at`) |
| 2 | `resolveWidgetIdForPurge(d)` | Widget hoặc native `findWidgetConfigIdUuidStringForPurge` |
| 3 | `qdrantPurgeService.purgeDocumentVectors` | Fail → `IllegalStateException`, **không** soft-delete DB |
| 4 | `documentTableRepository.softDeleteByDocumentId(id, ts)` | JPQL `UPDATE` set `deleted_at` |
| 5 | `documentSectionRepository.softDeleteByDocumentId(id, ts)` | JPQL `UPDATE` |
| 6 | `documentChunkRepository.softDeleteByDocumentId(id, ts)` | JPQL `UPDATE` |
| 7 | `d.setDeletedAt(ts)`; `documentRepository.save(d)` | Document ẩn khỏi query có `@SQLRestriction` |

**Purge Qdrant fail:** document và children **không** bị set `deleted_at` (hành vi giữ nguyên).

---

## 5. Retrieval DB flow hiện tại (SOURCE)

- **Sections (heading / intent):** `documentSectionRepository.findByWidgetConfigIdOrderByOrderIndexAsc` (và `QueryAnalyzerService` dùng `findTop200...` / `findByWidgetConfigIdOrderByOrderIndexAsc`).
- **Chunks:** toàn bộ đường trong `RagRetrievalService` qua `documentChunkRepository` — locked scope, table/section/sibling expansion, `findByWidgetConfigIdAndIdIn`, window `findByWidgetConfigIdAndDocumentIdAndOrderIndexBetween...`, rerank-guided re-fetch, `findParentSectionSummaries` → `findByWidgetConfigIdAndSectionIdIn...`, `findLexicalAnchors` → `findByWidgetConfigIdAndDocumentIdIn...`, `expandSectionRanges` → cùng method, `expandAroundAnchors` → `findByWidgetConfigIdAndDocumentIdAndOrderIndexBetween...`.

Không có `DocumentTableRepository` trong `RagRetrievalService` — table data vào context qua **chunk** types `table_*`.

---

## 6. Danh sách repository method / call site đã kiểm tra

### `RagRetrievalService` → `DocumentChunkRepository`

| Repository method | Call site (ước lượng dòng) |
|--------------------|----------------------------|
| `findByWidgetConfigIdAndSectionIdInOrderByDocumentIdAscOrderIndexAsc` | ~197–199, ~262–264, ~362–364, ~493–495 |
| `findByWidgetConfigIdAndTableIdInOrderByDocumentIdAscOrderIndexAsc` | ~219–221, ~290–292 |
| `findByWidgetConfigIdAndParentIdInOrderByDocumentIdAscOrderIndexAsc` | ~272–274 |
| `findByWidgetConfigIdAndIdIn` | ~300 |
| `findByWidgetConfigIdAndDocumentIdAndOrderIndexBetweenOrderByOrderIndexAsc` | ~305–307, ~707–709 |
| `findByWidgetConfigIdAndDocumentIdInOrderByDocumentIdAscOrderIndexAsc` | ~515–517, ~547–549 |

### `RagRetrievalService` / `QueryAnalyzerService` → `DocumentSectionRepository`

| Method | Call site |
|--------|-----------|
| `findByWidgetConfigIdOrderByOrderIndexAsc` | `RagRetrievalService` ~101; `QueryAnalyzerService` ~134 |
| `findTop200ByWidgetConfigIdOrderByOrderIndexAsc` | `QueryAnalyzerService` ~313 |

### Qdrant

- `EmbeddingService.search` filter `widgetId` — purge theo `document_id` + `widgetId` đã có (report 12B); không thuộc scope MySQL bug nhưng vector đã được xóa khi purge OK.

---

## 7. Nguyên nhân gốc xác nhận từ source

1. **`DocumentChunk` / `DocumentSection` / `DocumentTable`:** `@SQLRestriction("deleted_at IS NULL")` trên entity con — **chỉ** lọc theo `deleted_at` của **row đó**.
2. **`softDeleteDocument`:** chỉ ghi `documents.deleted_at` → các row con vẫn `deleted_at IS NULL` → **vẫn match** mọi derived query trên `widget_config_id`, `section_id`, `document_id`, v.v.
3. **Kết luận:** Có **bug thật** — sau delete, RAG vẫn có thể load chunk/section từ MySQL (Qdrant đã purge nên hybrid “chỉ vector” có thể giảm nhưng lexical/locked DB path vẫn trả chunk).

---

## 8. Có bug thật không?

**Có** — điều kiện (2)+(1) chứng minh leak qua MySQL retrieval cho document đã soft-delete (trừ khi chunk row đã bị set `deleted_at` bằng tay khác, không có trong code cũ).

---

## 9. Chiến lược sửa minimal đã chọn

**Không chọn** sửa từng derived query trong repository (nhiều method, dễ sót).

**Chọn** bổ sung **JPQL bulk `UPDATE`** trên 3 repository + gọi trong `softDeleteDocument` **sau** purge Qdrant, **trước** khi set `document.deleted_at` (cùng timestamp `ts`).

- Tương đương **cascade soft-delete có giới hạn** chỉ cho **một** `documentId` — không migration schema, không dependency mới.
- `@Transactional` trên `softDeleteDocument` để bulk update + save document trong một transaction.

---

## 10. Vì sao không sửa các vấn đề backlog khác

- `DocumentController.assign`, security, CORS, Flyway, nginx, Qdrant point id: không liên quan trực tiếp leak MySQL này.
- Không đổi `RagRetrievalService` để tránh refactor lớn và giữ một điểm sửa (`DocumentService` + repos).

---

## 11. Danh sách file đã đọc

| Path | Đọc để | Kết luận |
|------|--------|----------|
| `.cursor/rules/00-core-working-rule.mdc` | Luật nền | Minimal diff |
| `docs/RAG_TARGET_ARCHITECTURE.md` | Context | — |
| `reports/refactor/CURSOR_REPORT_20A_*.md`, `20B_*.md` | Context | 20B compile baseline |
| `reports/CURSOR_REPORT_12A_*.md`, `12B_*.md` | Purge/delete | Qdrant filter; soft delete document |
| `DocumentService.java` | Delete flow | Chỉ document `deleted_at` trước fix |
| `Document.java`, `DocumentChunk.java`, `DocumentSection.java`, `DocumentTable.java` | `@SQLRestriction` | Filter trên entity row |
| `DocumentChunkRepository.java`, `DocumentSectionRepository.java`, `DocumentTableRepository.java` | Query shape | Derived, không join document |
| `RagRetrievalService.java` (đoạn STEP 4, lexical, expand, rerank lock) | Call sites | Danh sách mục 6 |
| `QueryAnalyzerService.java` (grep + đoạn section fetch) | Heading match | `findTop200` / `findByWidget` |

---

## 12. Danh sách file đã sửa

| Path | Sửa để | Layer |
|------|--------|--------|
| `DocumentChunkRepository.java` | `softDeleteByDocumentId` JPQL | db / repository |
| `DocumentSectionRepository.java` | `softDeleteByDocumentId` JPQL | db / repository |
| `DocumentTableRepository.java` | `softDeleteByDocumentId` JPQL | db / repository |
| `DocumentService.java` | Gọi 3 bulk update + `@Transactional` + dùng chung `ts` | runtime / business |

---

## 13. Diff thay đổi của từng file

### 13.1 `DocumentChunkRepository.java`

- **Cũ:** Không có bulk soft-delete theo document.
- **Mới:** `@Modifying` + `UPDATE DocumentChunk c SET c.deletedAt = :ts WHERE c.document.id = :documentId AND c.deletedAt IS NULL`.
- **Vì sao:** Để `@SQLRestriction` trên chunk ẩn row sau delete document.
- **Ảnh hưởng:** Thêm 3 UPDATE đơn theo `document_id` khi delete — index `document_id` đã có trên các bảng liên quan; không scan toàn widget.

```diff
diff --git a/Backend/src/main/java/KLTN/RAG_CHATBOT_BE/domain/document/DocumentChunkRepository.java b/Backend/src/main/java/KLTN/RAG_CHATBOT_BE/domain/document/DocumentChunkRepository.java
index 0391ebd..8b5fc60 100644
--- a/Backend/src/main/java/KLTN/RAG_CHATBOT_BE/domain/document/DocumentChunkRepository.java
+++ b/Backend/src/main/java/KLTN/RAG_CHATBOT_BE/domain/document/DocumentChunkRepository.java
@@ -1,8 +1,12 @@
 package KLTN.RAG_CHATBOT_BE.domain.document;
 
 import org.springframework.data.jpa.repository.JpaRepository;
+import org.springframework.data.jpa.repository.Modifying;
+import org.springframework.data.jpa.repository.Query;
+import org.springframework.data.repository.query.Param;
 import org.springframework.stereotype.Repository;
 
+import java.time.LocalDateTime;
 import java.util.Collection;
 import java.util.List;
 import java.util.UUID;
@@ -58,4 +62,12 @@ public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, U
             UUID widgetConfigId,
             Collection<UUID> documentIds
     );
+
+    /**
+     * Soft-delete all chunks for a document (same {@code deleted_at} as parent document).
+     * Keeps rows for audit; {@code @SQLRestriction} on {@link DocumentChunk} then hides them from retrieval.
+     */
+    @Modifying(clearAutomatically = true, flushAutomatically = true)
+    @Query("UPDATE DocumentChunk c SET c.deletedAt = :ts WHERE c.document.id = :documentId AND c.deletedAt IS NULL")
+    int softDeleteByDocumentId(@Param("documentId") UUID documentId, @Param("ts") LocalDateTime ts);
 }
```

### 13.2 `DocumentSectionRepository.java`

```diff
+import org.springframework.data.jpa.repository.Modifying;
+import org.springframework.data.jpa.repository.Query;
+import org.springframework.data.repository.query.Param;
+import java.time.LocalDateTime;
@@
+    @Modifying(clearAutomatically = true, flushAutomatically = true)
+    @Query("UPDATE DocumentSection s SET s.deletedAt = :ts WHERE s.document.id = :documentId AND s.deletedAt IS NULL")
+    int softDeleteByDocumentId(@Param("documentId") UUID documentId, @Param("ts") LocalDateTime ts);
```

### 13.3 `DocumentTableRepository.java`

```diff
+    @Modifying(clearAutomatically = true, flushAutomatically = true)
+    @Query("UPDATE DocumentTable t SET t.deletedAt = :ts WHERE t.document.id = :documentId AND t.deletedAt IS NULL")
+    int softDeleteByDocumentId(@Param("documentId") UUID documentId, @Param("ts") LocalDateTime ts);
```

### 13.4 `DocumentService.java`

- **Cũ:** Chỉ `d.setDeletedAt(LocalDateTime.now())` sau purge.
- **Mới:** `@Transactional`; `ts` dùng chung; gọi `softDeleteByDocumentId` cho table → section → chunk; rồi `d.setDeletedAt(ts)`.

```diff
@@
+    @Transactional
     public void softDeleteDocument(UUID id) {
@@
-        d.setDeletedAt(LocalDateTime.now());
+        LocalDateTime ts = LocalDateTime.now();
+        documentTableRepository.softDeleteByDocumentId(id, ts);
+        documentSectionRepository.softDeleteByDocumentId(id, ts);
+        documentChunkRepository.softDeleteByDocumentId(id, ts);
+        d.setDeletedAt(ts);
         documentRepository.save(d);
     }
```

---

## 14. Ảnh hưởng sau sửa

| Hạng mục | Thay đổi / Giữ nguyên |
|----------|------------------------|
| **RAG retrieval mới** | Không load chunk/section/table của document vừa xóa qua Hibernate list queries. |
| **Document chưa xóa** | Không đổi. |
| **API contract** | Không đổi. |
| **Chat history đã lưu** | Tin nhắn cũ + `sources` JSON **không** bị rewrite — ngoài scope. |
| **CPU/RAM** | Thêm tối đa 3 UPDATE có điều kiện `document_id` mỗi lần delete; không load full chunk list vào heap. |
| **Disk / DB size** | Không xóa vật lý; chỉ set `deleted_at`. |
| **Latency delete** | Tăng nhẹ (3 UPDATE); chấp nhận được so với purge Qdrant đã có. |

---

## 15. Edge cases đã xem xét

| Case | Ghi chú |
|------|---------|
| Purge Qdrant fail | Exception trước bulk child delete — không đổi. |
| Document `findById` sau khi đã xóa | `@SQLRestriction` document — consumer khác có thể không thấy; `findWidgetConfigIdUuidStringForPurge` dùng native `deleted_at IS NULL` (purge path). |
| Dữ liệu orphan lịch sử | Document đã xóa **trước** release fix: chunk vẫn có thể `deleted_at` null — cần migration SQL một lần nếu cần sạch tuyệt đối. |
| Retry / ingest | `executeProcessing` tạo chunk mới cho document **chưa** deleted — không ảnh hưởng. |

---

## 16. Kết quả kiểm tra

| Command | Kết quả | Ghi chú |
|---------|---------|---------|
| `cd Backend; .\mvnw.cmd -DskipTests compile` | **PASS** | BUILD SUCCESS sau diff |
| `cd Backend; .\mvnw.cmd test` | **FAIL** | Giống report 20B: `ApplicationContext` — MySQL `Connection refused` (env). **Không** do diff 20C. |
| `docker compose config -q` | **PASS** | `compose_exit=0` |

---

## 17. Expected runtime verification (chưa chạy trong phiên)

1. Upload PDF vào widget A, đợi COMPLETED.  
2. Chat câu hỏi trùng nội dung file — có answer + sources.  
3. `DELETE /api/documents/{id}` (soft delete + purge).  
4. Chat lại cùng câu — không còn chunk từ document đó trong context/sources **mới**; Qdrant không trả point (đã purge).  
5. SQL kiểm tra: `document_chunks.deleted_at` / `document_sections.deleted_at` / `document_tables.deleted_at` **NOT NULL** cho `document_id` đã xóa.

---

## 18. Rủi ro còn lại

- **Dữ liệu cũ** trước bản fix có thể vẫn leak cho đến khi chạy script UPDATE backfill.  
- **Native SQL** hoặc raw JDBC ngoài Hibernate (nếu có sau này) có thể bypass `@SQLRestriction` — hiện không thấy trong retrieval path đã đọc.

---

## 19. Đề xuất tiếp theo

1. One-off SQL backfill: set `deleted_at` cho chunk/section/table có `document_id` thuộc `documents.deleted_at IS NOT NULL`.  
2. Optional test `@DataJpaTest` với H2 chỉ verify `softDeleteDocument` sets child `deleted_at` (nếu muốn CI không cần MySQL).  
3. `DocumentController.assign` backlog — tách task.

---

*End of report 20C.*
