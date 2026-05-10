# Cursor Report 03A - BACKEND_DOCUMENTS_API_CONTRACT_ALIGNMENT_AND_SAFE_CANONICAL_ENDPOINTS

## 1. Mức độ hiểu task

- **Task là gì?** Align Backend Documents API với `Frontend/src/api/documentsApi.js`: upload có tenant (`chatbotId`/`widgetId`), list có pagination/filter, status, chunks, assign (an toàn), retry (an toàn), soft delete — không hack widget mặc định, không đổi core ingest logic.
- **Hiểu task:** 92%
- **Phần chắc chắn:**
  - FE upload chỉ gửi `files` trong FormData — **không** có tenant trong client hiện tại.
  - `DocumentService.uploadAndProcess` là pipeline chuẩn; map status DB → FE (`INDEXED`/`PROCESSING`/`FAILED`).
  - `assign` đổi widget làm lệch vector Qdrant → không làm silent move.
- **Phần còn giả định:**
  - Retry chỉ an toàn khi FAILED và **chưa** có row chunk/section/table (fail sớm).
- **Phạm vi không làm:** Dashboard/analytics/settings/playground/public/chat/chatbots CRUD; không purge Qdrant khi delete; không sửa FE/mock; không đổi schema DB.

## 2. Files/rules/reports đã đọc

| File | Mục đích đọc | Kết luận chính |
|------|----------------|----------------|
| `.cursor/rules/00-core-working-rule.mdc` | Minimal scope | Giữ diff nhỏ |
| `Frontend/src/api/documentsApi.js` | Contract | Path/query/body |
| `Frontend/src/mocks/documentsMock.js` | Shape | Field names FE |
| `reports/CURSOR_REPORT_01B_*` | Canonical baseline | Upload path mismatch đã biết |
| `DocumentController.java` (trước) | Legacy | Chỉ `/upload/{widgetId}` + list đơn giản |
| `DocumentService.java` | Ingest | `uploadAndProcess` đồng bộ, đủ pipeline |

## 3. Current Documents/RAG ingest analysis

- **Controller (trước):** `POST /api/documents/upload/{widgetId}` + `GET /api/documents` trả list `DocumentListItemResponse` (không pagination FE).
- **Document entity:** `id`, `widgetConfig`, `fileName`, `filePath`, `fileType`, `fileSize`, `mimeType`, `status` (`PENDING|PROCESSING|COMPLETED|FAILED`), `chunkCount`, `createdAt`, `updatedAt`, `deletedAt` — **không** có cột `error` text.
- **DocumentChunk:** `chunkIndex`, `content`, `tokenCount`, `document`, `widgetConfig`, …
- **`uploadAndProcess`:** Lưu file → parse → chunk → sections/tables → embed Qdrant → `COMPLETED`; exception → `FAILED` và rethrow.
- **Tenant:** Bắt buộc qua `widgetId` UUID (`WidgetConfig`).
- **Qdrant:** `EmbeddingService.embedAndStore`; không có API xóa point theo document trong scope.
- **Delete/purge:** Chưa có safe delete vector — chỉ soft delete DB trong prompt này.

## 4. FE contract mapping after implementation

| FE function | Endpoint | Request/header expected | Response expected | Backend implementation status | Notes |
|-------------|----------|-------------------------|-------------------|-------------------------------|--------|
| `uploadDocuments` | `POST /api/documents/upload` | FormData `files` | Array document objects | **Implemented** | Thêm `chatbotId` hoặc `widgetId` (multipart/query); thiếu → **400** đúng message audit |
| `getDocuments` | `GET /api/documents` | Query filters + page/size | `DocumentPageResponse` | **Implemented** | 0-based pagination |
| `getDocumentStatus` | `GET .../status` | — | Status DTO | **Implemented** | |
| `getDocumentChunks` | `GET .../chunks` | — | Array chunks | **Implemented** | Sort `chunkIndex` |
| `assignDocument` | `POST .../assign` | `{ chatbotId }` | doc object (mock) | **Blocked intentionally** | **400** message Qdrant re-index |
| `retryDocument` | `POST .../retry` | — | mock `{ success }` | **Partial** | Trả **document object** sau retry (FE có thể bỏ qua body nếu chỉ check lỗi) |
| `deleteDocument` | `DELETE .../{id}` | — | `{ success: true }` | **Implemented** | `SimpleSuccessResponse` |

## 5. Files changed

| File | Change | Reason | Risk |
|------|--------|--------|------|
| `DocumentController.java` | Canonical routes + legacy `/upload/{widgetId}` | FE path + tenant safety | Medium |
| `DocumentService.java` | `executeProcessing`, list/spec, chunks, upload batch, retry guard, soft delete, mapping FE status | Reuse pipeline | Medium |
| `DocumentRepository.java` | `JpaSpecificationExecutor` | Filter/page | Low |
| `DocumentChunkRepository.java` | `findByDocumentIdOrderByChunkIndexAsc` | Chunks API | Low |
| `dto/DocumentResponse.java` | New | FE row shape | Low |
| `dto/DocumentPageResponse.java` | New | Pagination | Low |
| `dto/DocumentStatusResponse.java` | New | Status endpoint | Low |
| `dto/DocumentChunkResponse.java` | New | Chunks endpoint | Low |
| `dto/DocumentAssignRequest.java` | New | Assign body | Low |
| `support/BytesMultipartFile.java` | New | Retry từ file disk không dùng spring-test | Low |

## 6. Details per endpoint

### `POST /api/documents/upload` (canonical)

- **Multipart:** `files[]`, optional `chatbotId`, `widgetId` (hoặc query cùng tên).
- **Validation:** Thiếu tenant → `400` `{ "message": "chatbotId is required for document upload" }`. UUID sai → `Invalid chatbotId or widgetId`. Widget không tồn tại → message tiếng Việt.
- **Response:** `List<DocumentResponse>` — một entry mỗi file; một file lỗi → fail cả batch (exception).
- **Service:** `uploadDocumentsCanonical` → `uploadAndProcess` từng file.

### `POST /api/documents/upload/{widgetId}` (legacy)

- Giữ nguyên `DocumentUploadResponse` cho backward compatibility.

### `GET /api/documents`

- **Query:** `search`, `type`, `chatbotId`, `status` (FE: INDEXED/PROCESSING/FAILED), `page`, `size`.
- **Response:** `DocumentPageResponse`.
- **Mapping:** `COMPLETED` → `INDEXED`; `PENDING`+`PROCESSING` → `PROCESSING`; progress heuristic (100/50/0).

### `GET /api/documents/{id}/status`

- **404** `{ "message": "Document not found" }`; invalid UUID → `Invalid document id`.

### `GET /api/documents/{id}/chunks`

- **404** nếu không có document.

### `POST /api/documents/{id}/assign`

- Luôn **400** với message giải thích cần re-index Qdrant (không đổi `widgetConfig`).

### `POST /api/documents/{id}/retry`

- Chỉ `FAILED` và không có chunk/section/table; nếu không đủ điều kiện → **400** rõ ràng.
- Thành công → **200** + `DocumentResponse` (khác mock `{success:true}` — ghi nhận limitation).

### `DELETE /api/documents/{id}`

- Soft delete `deletedAt`; **không** xóa vector Qdrant.

## 7. Upload tenant-context decision

| Question | Answer |
|----------|--------|
| FE hiện có gửi `chatbotId`/`widgetId` không? | **Không** — `documentsApi.uploadDocuments` chỉ append `files`. |
| Backend canonical nhận tenant ở đâu? | Form field hoặc query: `chatbotId` (ưu tiên) hoặc `widgetId`. |
| Thiếu tenant? | **400** `chatbotId is required for document upload` — không default widget. |
| Sửa FE trong prompt này? | **Không** — upload qua UI real API sẽ cần prompt FE sau để append `chatbotId`. |

## 8. Validation results

| Command | Result | Notes |
|---------|--------|-------|
| `cd Backend && .\mvnw.cmd -DskipTests compile` | **PASS** | |
| `cd Backend && .\mvnw.cmd test` | **NOT RUN** | Giống các report trước: phụ thuộc env DB/API keys |

## 9. Manual/API test plan and results

| Endpoint | Steps | Result | Notes |
|----------|-------|--------|-------|
| Upload không tenant | curl multipart chỉ `files` | **NOT RUN** | Kỳ vọng 400 |
| Upload có `chatbotId` | | **NOT RUN** | Cần backend + file + embedding |
| List/filter/status/chunks/assign/retry/delete | | **NOT RUN** | Môi trường runtime không chạy trong phiên |

## 10. Known limitations / gaps

- **Upload từ FE hiện tại:** Sẽ nhận **400** cho đến khi FE gửi tenant — cố ý an toàn.
- **`error` field:** Không có cột DB — FAILED trả message generic trong DTO.
- **DELETE:** Vector Qdrant còn — retrieval có thể vẫn thấy chunk cũ nếu filter không đủ (ngoài scope purge).
- **Retry:** Hẹp — chỉ FAIL “sạch” (không residue sections/chunks).
- **Assign:** Không implement move.

## 11. Recommended next prompt

- **FE:** Append `chatbotId` (UUID chatbot đang chọn trên Documents page) vào FormData upload.
- **Ops:** Qdrant delete-by-document-id trước khi cho phép assign/retry phức tạp.
