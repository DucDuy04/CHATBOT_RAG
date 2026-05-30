# CHATBOT_DELETE_CASCADE — Task 25K

**Date:** 2026-05-29  
**Verdict:** **PASS**

## 1. Mức độ hiểu task

| | |
|---|---|
| Hiểu task | **98%** |
| Chắc chắn | Cascade qua `DocumentService.softDeleteDocument`; fail-fast; unit tests mock-only; không đổi Qdrant payload/parser |
| Giả định | `DocumentRepository.findByWidgetConfigId` chỉ trả active docs nhờ `@SQLRestriction` trên `Document` |
| Thiếu dữ kiện | Không |

## 2. Tóm tắt yêu cầu

Khi xóa chatbot, xóa mọi document active của chatbot trước (DB + Qdrant qua path hiện có), rồi soft-delete chatbot. Thêm unit tests; `mvn clean test` không cần MySQL/Qdrant live.

## 3. Phase 1 — Inspect (trước khi code)

| # | Câu hỏi | Trả lời |
|---|---------|---------|
| 1 | Endpoint xóa chatbot? | `DELETE /api/chatbots/{id}` → `ChatbotController.deleteChatbot` |
| 2 | Service method? | `WidgetService.softDeleteChatbot(UUID)` |
| 3 | Có fetch documents? | **Không** (trước 25K) |
| 4 | `softDeleteDocument` purge Qdrant? | **Có** — `qdrantPurgeService.purgeDocumentVectors` trước soft-delete rows |
| 5 | Circular dependency? | **Không** — `DocumentService` không inject `WidgetService`; inject trực tiếp `DocumentService` vào `WidgetService` |

**Quyết định:** Không cần `ChatbotDeletionService` / `DocumentCascadeDeletionService` riêng.

## 4. Hiện trạng trước khi sửa

```text
DELETE /api/chatbots/{id}
  → WidgetService.softDeleteChatbot(id)
       → set widget deleted_at, status DELETED
       → (documents/chunks/Qdrant untouched)
```

Hậu quả 25I/25J: orphan chunks khi chatbot xóa trước document.

## 5. Delete flow sau khi sửa

```text
DELETE /api/chatbots/{id}
  → WidgetService.softDeleteChatbot(id)
       1. findById(chatbot) — empty if already soft-deleted → return false
       2. findByWidgetConfigId(chatbot) — active documents only
       3. for each doc: DocumentService.softDeleteDocument(docId)
            → Qdrant purge by document_id + widgetId
            → soft-delete tables, sections, chunks, document
       4. log [ChatbotDelete] chatbot=<id> documentsDeleted=<n>
       5. soft-delete widget row
```

Fail-fast: exception từ bước 3 → chatbot không soft-delete (transaction rollback).

## 6. Danh sách file đã đọc

| Path | Mục đích | Kết luận |
|------|----------|----------|
| `WidgetService.java` | Cascade point | Chỉ soft-delete widget |
| `ChatbotController.java` | API | `DELETE /api/chatbots/{id}` |
| `DocumentService.java` | Safe delete | Qdrant + cascade children |
| `DocumentRepository.java` | List docs | `findByWidgetConfigId` |
| `QdrantPurgeService.java` | Vector purge | Filter `document_id` + `widgetId` |
| `WidgetConfig.java` / `Document.java` | SQLRestriction | Active-only queries |

## 7. Danh sách file đã sửa

| Path | Sửa để làm gì | Layer |
|------|---------------|-------|
| `Backend/.../service/WidgetService.java` | Cascade + log | service |
| `Backend/.../service/WidgetServiceSoftDeleteChatbotTest.java` | 6 unit tests | test |

## 8. Diff thay đổi

### `WidgetService.java`

```diff
+ import Document;
+ import lombok.extern.slf4j.Slf4j;
+ @Slf4j
+ private final DocumentService documentService;

  public boolean softDeleteChatbot(UUID id) {
      ...
+     List<Document> activeDocuments = documentRepository.findByWidgetConfigId(id);
+     for (Document doc : activeDocuments) {
+         documentService.softDeleteDocument(doc.getId());
+     }
+     log.info("[ChatbotDelete] chatbot={} documentsDeleted={}", id, activeDocuments.size());
      w.setDeletedAt(LocalDateTime.now());
      ...
  }
```

### `WidgetServiceSoftDeleteChatbotTest.java` (NEW)

6 tests: 2 docs cascade, 0 docs, skip soft-deleted (repo returns active only), fail-fast on doc error, not-found, inOrder verify.

## 9. Tests added/updated

| Test | Mô tả |
|------|--------|
| `softDeleteChatbot_callsSoftDeleteDocumentForEachActiveDocument` | 2 docs → 2 calls + chatbot deleted |
| `softDeleteChatbot_withNoDocuments_stillSoftDeletesChatbot` | 0 doc calls |
| `softDeleteChatbot_skipsDocumentsNotReturnedByRepository` | 1 active only |
| `softDeleteChatbot_whenDocumentDeleteFails_doesNotSoftDeleteChatbot` | exception, no save |
| `softDeleteChatbot_whenChatbotNotFound_returnsFalse` | idempotent 404 path |
| `softDeleteChatbot_savesChatbotAfterAllDocumentDeletes` | order verify |

Existing document-delete tests: không có dedicated test trước đó; **65 → 71** tests, 0 failures.

## 10. Kết quả kiểm tra

| Command | Kết quả | Ghi chú |
|---------|---------|---------|
| `cd Backend && .\mvnw.cmd clean test` | **PASS** | **71** tests, 0 failures, 0 errors |
| Runtime smoke 25K temp bot | **PASS** | TXT upload COMPLETED 1 chunk; delete → bot/doc/chunks inactive; Qdrant count=0 |
| Kept data safety audit | **PASS** | 5 chatbots, 3 COMPLETED docs, 9888 chunks |
| Optional rank-1 smoke S2 | **PASS** | Nguyễn Thị Vân Anh, E301 |
| `docker compose config -q` | NOT RUN | |
| Frontend lint/build | NOT RUN | out of scope |

### Runtime smoke detail

| Step | Result |
|------|--------|
| POST `/api/chatbots` name `25K-cascade-delete-smoke` | `809f2793-e466-421d-88bf-0b900cfea1bb` |
| POST upload TXT | doc `71c15e07-42b3-4313-b46b-4beb7ce70ae6`, COMPLETED, 1 chunk |
| DELETE chatbot | `success: true` |
| MySQL bot active | 0 |
| MySQL doc/chunks active | 0 |
| Qdrant points for doc | 0 |

## 11. DB/Qdrant verification (kept data)

| Metric | Expected | Actual |
|--------|----------|--------|
| Active chatbots | 5 | 5 |
| Active documents | 3 COMPLETED | 3 |
| Active DB chunks | 9888 | 9888 |
| Qdrant collection total | 9888 | 9888 (post-smoke; temp bot purged) |
| Orphan chunks on deleted docs | 0 | 0 |

## 12. Ảnh hưởng sau sửa

| Behavior | Change |
|----------|--------|
| `DELETE /api/chatbots/{id}` | Cascade active documents first |
| `DELETE /api/documents/{id}` | **Unchanged** |
| Chatbot already deleted | `findById` empty → 404 (unchanged) |
| Chatbot with 0 docs | Still deletes chatbot |
| Latency on delete | +N × document delete (Qdrant per doc) |

## 13. Edge cases

- Document delete Qdrant fail → chatbot not deleted
- Partial doc list from repo (SQLRestriction) → soft-deleted docs never in list
- Re-delete chatbot → false (not found)
- Other chatbots' documents never touched

## 14. Rủi ro còn lại

1. Delete chatbot với nhiều document lớn có thể chậm (N Qdrant purges).
2. Không có integration test live Qdrant (by design).
3. Historical orphan data đã dọn ở 25J — cascade ngăn tái phát.

## 15. Đề xuất tiếp theo

- Optional: return `documentsDeleted` count in delete API response body.
- Optional: batch Qdrant purge if many docs per chatbot (only if perf issue observed).

## 16. Acceptance

| Criterion | Status |
|-----------|--------|
| Cascade active documents | ✓ |
| Uses `softDeleteDocument` | ✓ |
| Qdrant purged per doc | ✓ (smoke + existing path) |
| No new orphans | ✓ |
| 71 tests pass | ✓ |
| Kept data intact | ✓ |
| **Verdict** | **PASS** |
