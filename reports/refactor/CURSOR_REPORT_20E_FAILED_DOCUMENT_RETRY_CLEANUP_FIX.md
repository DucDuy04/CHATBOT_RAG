# Cursor Report 20E — FAILED Document Retry + Partial Ingestion Cleanup

**Ngày:** 2026-05-14  
**Scope:** Retry document `FAILED` sau ingestion lỗi giữa chừng — purge Qdrant + xóa children DB an toàn, minimal; không backfill/migration toàn hệ thống.

---

## 1. Mức độ hiểu task

| Mục | Nội dung |
|-----|----------|
| **Hiểu task** | **95%** |
| **Chắc chắn** | `retryFailedDocument` cũ từ chối khi đã có chunk/section/table; unique `(document_id, chunk_index)`, `(document_id, section_key)`, `(document_id, table_key)` khiến **chỉ soft-delete** không đủ để insert lại cùng key; purge Qdrant dùng `document_id` + `widgetId` giống `QdrantPurgeService` / `EmbeddingService`. |
| **Giả định** | JPQL bulk `DELETE`/`UPDATE` không áp dụng `@SQLRestriction` theo cách loại row khỏi DML (hành vi Hibernate phổ biến cho bulk update/delete) — nếu không, vẫn xóa đủ row theo `document.id`. |
| **Thiếu dữ kiện** | Chưa chạy E2E retry trên MySQL+Qdrant thật trong phiên này. |

---

## 2. Tóm tắt yêu cầu

Cho phép retry document `FAILED` dù pipeline trước đó đã tạo một phần chunk/section/table hoặc vector Qdrant; trước khi chạy lại pipeline: kiểm tra file còn → purge Qdrant (fail thì dừng) → xóa children cũ không trộn với lần mới; không backfill; không đổi dependency/collection/contract.

---

## 3. Phạm vi đã làm

- Phân tích unique constraint + flow retry cũ.
- Thêm bulk hard-delete theo `documentId` trên 3 repository; unlink `prev`/`next` chunk trước khi xóa chunk.
- Sửa `DocumentService.retryFailedDocument`: thứ tự an toàn, purge trước reprocess.
- Compile + `docker compose config -q`.
- Báo cáo này (+ bản sao trong `docs/` theo rule 90).

---

## 4. Phạm vi không làm

- Không migration/Flyway/backfill; không UI/security/CORS; không đổi embedding/Qdrant collection/size; không `assign` endpoint.

---

## 5. Vì sao không làm backfill

Theo yêu cầu: dữ liệu test, user có thể xóa sạch; chỉ sửa luồng retry cho document hiện tại/tương lai.

---

## 6. Trả lời phân tích bắt buộc (trước khi sửa)

1. **`retryFailedDocument` hiện nằm ở đâu?** — `DocumentService.retryFailedDocument` (~dòng 330), gọi từ `DocumentController.retry` `POST /api/documents/{id}/retry`.
2. **Retry hiện kiểm tra status thế nào?** — `doc.getStatus() != DocumentStatus.FAILED` → `IllegalArgumentException`.
3. **Retry hiện kiểm tra file path thế nào?** — `Files.exists(Paths.get(doc.getFilePath()))`.
4. **Retry hiện có chặn nếu đã có chunk/section/table không?** — **Có** — `findByDocumentId` / `findByDocumentIdOrderBy...` non-empty → từ chối (trước fix 20E).
5. **Ingestion lỗi sau khi đã lưu chunk nhưng trước embed?** — DB: có `document_chunks` (+ có thể sections/tables); `documents.status` = `FAILED` (trong `uploadAndProcess` catch); Qdrant: thường chưa hoặc chưa đủ vectors.
6. **Embed lỗi sau khi Qdrant ghi một phần?** — DB: có thể đã có chunks (và point ids); Qdrant: có thể một subset points; status `FAILED`.
7. **Method soft-delete children theo documentId?** — `softDeleteByDocumentId` trên `DocumentChunkRepository`, `DocumentSectionRepository`, `DocumentTableRepository` (từ 20C).
8. **Delete document purge Qdrant theo key nào?** — `must`: `document_id` + `widgetId` (`QdrantPurgeService.purgeDocumentVectors`).
9. **FAILED: hard vs soft vs từ chối?** — Soft-delete **không** đủ vì unique constraint vẫn tồn tại trên row đã `deleted_at`; từ chối retry → **kẹt**. Chọn **hard-delete** children **chỉ** trong nhánh retry FAILED (sau purge), giữ nguyên row `documents`.
10. **Hướng minimal?** — Reuse `resolveWidgetIdForPurge` + `qdrantPurgeService`; thêm JPQL bulk delete/update theo `document.id` — không load list vào memory, không dependency mới.

---

## 7. Retry flow hiện tại từ source (sau 20E)

| Bước | Hành động |
|------|-----------|
| 1 | `findById` — document phải tồn tại (`@SQLRestriction` — không soft-deleted). |
| 2 | Status phải `FAILED` — không retry COMPLETED/PENDING/PROCESSING. |
| 3 | File gốc phải tồn tại — nếu không: `IllegalArgumentException`, **chưa** purge/delete (tránh mất dữ liệu khi không reprocess được). |
| 4 | `resolveWidgetIdForPurge(doc)` — lấy `widgetId` (kể cả widget soft-delete qua native query như delete). |
| 5 | `qdrantPurgeService.purgeDocumentVectors(documentId, widgetId)` — fail → `IllegalStateException`, **không** xóa DB children. |
| 6 | `unlinkNeighborsByDocumentId` → `hardDeleteByDocumentId` chunks → hard-delete tables → hard-delete sections. |
| 7 | `doc` → `PENDING`, `chunkCount=null`, `save`. |
| 8 | `executeProcessing(doc, mf)` — giữ nguyên pipeline parse→chunk→embed; lỗi → `FAILED` + throw. |

---

## 8. Partial failure scenarios

| Scenario | DB trước retry | Qdrant trước retry | Sau 20E |
|----------|----------------|------------------|---------|
| Parse OK, save sections, fail trước chunk | sections (+ maybe tables) | không / ít | purge (no-op/nhẹ) + hard delete all children → reprocess |
| Chunks saved, embed throws | chunks (+ sections/tables) | partial/full vectors | purge clears doc vectors + hard delete DB → reprocess |
| Chỉ Qdrant partial, DB không chunk | không áp dụng retry block cũ | vectors | purge + delete 0 rows DB → OK |

---

## 9. Có bug/rủi ro thật không?

**Có** — retry cũ **kẹt** khi partial children tồn tại (từ chối) hoặc nếu force soft-delete-only thì **unique conflict** khi insert lại.

---

## 10. Root cause

1. Ingestion không transaction end-to-end → trạng thái `FAILED` có thể kèm row con.  
2. Retry yêu cầu “không có chunk/section/table” → không bao phủ partial failure.  
3. Unique trên `(document_id, …)` + row soft-delete vẫn chiếm unique → không thể chỉ soft-delete rồi insert lại cùng index/key.

---

## 11. Chiến lược sửa minimal đã chọn

- **Option B (hard-delete children)** scoped `documentId` trong service retry, **sau** purge Qdrant thành công, **sau** kiểm tra file tồn tại.  
- Không đụng document khác; không cleanup toàn hệ thống.  
- Không chọn Option A (soft-only) vì unique.  
- Không Option C (document mới) — đổi hành vi/UX không cần thiết.

---

## 12. Vì sao không sửa backlog khác

- Transaction dài toàn pipeline + compensating Qdrant: ngoài scope minimal.  
- Pessimistic lock chống double-retry: tùy chọn tương lai.

---

## 13. Danh sách file đã đọc

| Path | Mục đích | Kết luận |
|------|----------|----------|
| `DocumentChunk.java` / `DocumentSection.java` / `DocumentTable.java` | Unique + FK | Cần unlink chunk self-FK; thứ tự xóa chunks → tables → sections. |
| `DocumentService.java` | Retry cũ | Chặn partial; đã thay bằng purge + hard-delete + reprocess. |
| `QdrantPurgeService.java` | Filter purge | Khớp payload ingest. |
| `DocumentChunkRepository.java` (và section/table) | softDelete hiện có | Thêm hard-delete + unlink. |

---

## 14. Danh sách file đã sửa

| Path | Sửa để | Layer |
|------|--------|--------|
| `DocumentService.java` | Retry FAILED an toàn với partial data | service / db / qdrant |
| `DocumentChunkRepository.java` | `unlinkNeighborsByDocumentId`, `hardDeleteByDocumentId` | db |
| `DocumentTableRepository.java` | `hardDeleteByDocumentId` | db |
| `DocumentSectionRepository.java` | `hardDeleteByDocumentId` | db |
| `reports/refactor/CURSOR_REPORT_20E_FAILED_DOCUMENT_RETRY_CLEANUP_FIX.md` | Báo cáo | docs |
| `docs/CURSOR_REPORT_20E_FAILED_DOCUMENT_RETRY_CLEANUP_FIX.md` | Bản sao báo cáo (rule 90) | docs |

---

## 15. Diff thay đổi của từng file

### `DocumentChunkRepository.java`

```diff
+    @Modifying(clearAutomatically = true, flushAutomatically = true)
+    @Query("UPDATE DocumentChunk c SET c.prevChunk = NULL, c.nextChunk = NULL WHERE c.document.id = :documentId")
+    int unlinkNeighborsByDocumentId(@Param("documentId") UUID documentId);
+
+    @Modifying(clearAutomatically = true, flushAutomatically = true)
+    @Query("DELETE FROM DocumentChunk c WHERE c.document.id = :documentId")
+    int hardDeleteByDocumentId(@Param("documentId") UUID documentId);
```

### `DocumentTableRepository.java`

```diff
+    @Modifying(clearAutomatically = true, flushAutomatically = true)
+    @Query("DELETE FROM DocumentTable t WHERE t.document.id = :documentId")
+    int hardDeleteByDocumentId(@Param("documentId") UUID documentId);
```

### `DocumentSectionRepository.java`

```diff
+    @Modifying(clearAutomatically = true, flushAutomatically = true)
+    @Query("DELETE FROM DocumentSection s WHERE s.document.id = :documentId")
+    int hardDeleteByDocumentId(@Param("documentId") UUID documentId);
```

### `DocumentService.java` (`retryFailedDocument`)

```diff
-        if (!documentChunkRepository.findByDocumentId(documentId).isEmpty()) { ... }
-        if (!documentSectionRepository.findByDocumentIdOrderByOrderIndexAsc(documentId).isEmpty()) { ... }
-        if (!documentTableRepository.findByDocumentIdOrderByOrderIndexAsc(documentId).isEmpty()) { ... }
-        Path path = Paths.get(doc.getFilePath());
-        if (!Files.exists(path)) { ... }
+        Path path = Paths.get(doc.getFilePath());
+        if (!Files.exists(path)) { ... }
+        UUID widgetId = resolveWidgetIdForPurge(doc);
+        qdrantPurgeService.purgeDocumentVectors(documentId, widgetId);
+        documentChunkRepository.unlinkNeighborsByDocumentId(documentId);
+        documentChunkRepository.hardDeleteByDocumentId(documentId);
+        documentTableRepository.hardDeleteByDocumentId(documentId);
+        documentSectionRepository.hardDeleteByDocumentId(documentId);
          BytesMultipartFile mf = ...
```

---

## 16. Ảnh hưởng sau sửa

**Thay đổi**

- Document `FAILED` có partial DB/Qdrant: `POST .../retry` purge + xóa hết children của document đó rồi chạy lại pipeline; không còn kẹt vì “đã có chunk”.
- Thứ tự: file tồn tại trước khi purge/delete — tránh xóa hết children rồi phát hiện mất file.

**Giữ nguyên**

- Retry vẫn chỉ `FAILED`; không đổi URL/request body.
- `softDeleteDocument` vẫn soft-delete + purge (không dùng hard-delete đó).
- Chat history / message sources (JSON snapshot) không bị rewrite bởi retry.

**RAM/CPU/DB/disk**

- Bulk JPQL theo `document_id` — không load toàn bộ entity list; index `document_id` thường có (entity indexes trên section/table).
- Thêm **một** lần gọi purge Qdrant mỗi retry thành công (HTTP như delete) — chấp nhận được trên tenant nhỏ.

---

## 17. Edge cases đã xem xét

| Edge | Xử lý |
|------|--------|
| Qdrant down purge fail | Throw `IllegalStateException` — không xóa DB, document vẫn FAILED. |
| File mất | Throw sớm — không purge/delete. |
| Status không FAILED | `IllegalArgumentException` như cũ. |
| Hai request retry đồng thời | Có thể chạy purge/delete/reprocess trùng — rủi ro thấp; có thể thêm lock sau. |
| Cleanup DB xong, embed fail lại | Document FAILED, không còn children — retry được lần nữa (purge idempotent). |
| Audit row children | Hard-delete mất row partial failed — chấp nhận cho ingestion lỗi (test/prod yếu). |

---

## 18. Resource / production (bắt buộc)

**A. Resource**

- Cleanup: JPQL `UPDATE`/`DELETE` WHERE `document.id` — không full-scan widget; không materialize list chunk.
- Thêm purge mỗi lần retry (giống nguyên tắc “sạch vector trước khi ghi lại”).

**B. Stability**

- Như bảng edge cases.

**C. Data / side effects**

- COMPLETED/PENDING/PROCESSING: không đi qua nhánh retry (check status).
- Document khác: không (WHERE theo id cụ thể).
- Chat: không FK tới chunk trong code đã grep — không ảnh hưởng.

---

## 19. Kết quả kiểm tra

| Command | Kết quả |
|---------|---------|
| `cd Backend; .\mvnw.cmd -DskipTests compile` | **PASS** |
| `docker compose config -q` | **PASS** |
| `mvn test` | **NOT RUN** (MySQL local có thể thiếu) |

---

## 20. Runtime verification checklist

1. Stack: MySQL + Qdrant + backend.  
2. Upload file hoặc giả lập lỗi embed để document `FAILED` với chunk trong DB (nếu có cách inject).  
3. `POST /api/documents/{id}/retry`.  
4. Kỳ vọng: purge OK → children cũ biến mất (hard delete) → status PROCESSING → COMPLETED; Qdrant không trộn point cũ (cùng document sau purge).  
5. Chat câu hỏi nội dung file — context khớp bản ingest mới.  
6. Nếu không giả lập partial: **expected verification only** như trên.

---

## 21. Rủi ro còn lại

- Hai retry đồng thời trên cùng document id (hiếm) có thể gây race; chưa thêm pessimistic lock.  
- File mất nhưng vector còn trong Qdrant: không purge (vì return sớm) — document vẫn FAILED; user có thể xóa document hoặc sửa path/file.

---

## 22. Đề xuất prompt tiếp theo

1. E2E test retry với partial mock (Testcontainers MySQL + Qdrant test container nếu muốn).  
2. Optional: `SELECT ... FOR UPDATE` document row khi bắt đầu retry.  
3. Optional: endpoint “purge only” cho FAILED không có file — ngoài scope.

---

**Kết thúc report 20E.**
