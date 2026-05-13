# Cursor Report 12B - QDRANT_PURGE_AND_FILE_TYPE_CONSISTENCY

**Ngày:** 2026-05-13  
**Scope:** Purge vector Qdrant khi `DELETE /api/documents/{id}`; chặn upload không phải PDF/TXT (BE+FE); không đổi chunking/RAG/prompt/schema.

---

## 1. Mức độ hiểu task

| Mục | Nội dung |
|-----|----------|
| **Task là gì?** | (A) Xóa vector Qdrant theo `document_id` **và** `widgetId` trước khi soft-delete document; lỗi purge → không xóa DB, trả lỗi rõ. (B) Chỉ cho phép upload PDF/TXT ở backend (trước khi lưu file) + đồng bộ FE (accept, validate, copy). |
| **Hiểu task** | **96%** |
| **Phạm vi không làm** | Parser DOCX; purge theo `widgetId`/`documentId` đơn lẻ; đổi retrieval/prompt; soft-delete `document_chunks`; migration DB; dependency mới; widget build. |

---

## 2. Files/rules/reports đã đọc

| File | Mục đích đọc | Kết luận chính |
|------|----------------|----------------|
| `.cursor/rules/00–90*.mdc` | Luật workspace | Minimal diff; report + verify; không đổi schema không cần thiết. |
| `reports/CURSOR_REPORT_12A_CORE_INGESTION_EMBEDDING_FLOW_AUDIT.md` | Tiền đề ingest | Payload `document_id` + `widgetId`; delete chưa purge; DOCX không parse. |
| `reports/CURSOR_REPORT_03A_*.md` | Contract delete | Soft delete DB, chưa Qdrant. |
| `reports/CURSOR_REPORT_03C_*.md` | Smoke | Delete không purge vector (trước fix). |
| `reports/CURSOR_REPORT_08D1_*.md` | Widget orphan | `EntityNotFoundException` khi map widget. |
| `reports/CURSOR_REPORT_10A_*.md`, `11B_*.md` | Regression/RAG | Không đổi retrieval trong task này. |
| `EmbeddingService.java` | Payload keys | `document_id`, `documentId`, `widgetId`, `chunk_id`, … |
| `DocumentService.java`, `DocumentController.java`, `DocumentRepository.java` | Điểm sửa | `softDeleteDocument`, `uploadAndProcess`. |
| `UploadZone.jsx`, `DocumentsToolbar.jsx`, `DocumentsTable.jsx`, `documentsApi.js` | FE consistency | Trước đó cho phép DOCX ở UI. |

---

## 3. Current root causes

| Area | Current behavior (trước fix) | Root cause | Risk |
|------|-------------------------------|------------|------|
| Delete document | Chỉ `deleted_at` trên `documents` | Không gọi Qdrant API | Vector còn → retrieval có thể vẫn match (theo 12A/03C). |
| Upload DOCX | `getFileType` = DOCX nhưng `DocumentParserService` chỉ PDF/TXT | Validate chỉ ở parser sau khi đã lưu file + tạo row | File rác trên disk; trạng thái FAILED; UX tệ. |
| FE UploadZone | `accept` gồm `.docx` | Copy “PDF, TXT, DOCX” | User tưởng DOCX được hỗ trợ. |

---

## 4. Qdrant purge implementation

| Hạng mục | Chi tiết |
|----------|----------|
| **Class** | `KLTN.RAG_CHATBOT_BE.service.QdrantPurgeService` (mới). |
| **Endpoint** | `POST http://{qdrant.host}:{qdrant.http-port}/collections/{qdrant.collection-name}/points/delete` — giống pattern HTTP của `EmbeddingService#search`. |
| **Count (optional)** | `POST .../points/count` với cùng `filter` + `"exact": true` — nếu lỗi thì bỏ qua count, vẫn delete. |
| **Filter** | `must`: `document_id` match `documentId.toString()` **và** `widgetId` match `widgetId.toString()` — khớp metadata khi `embedAndStore`. |
| **Thứ tự** | Load document → resolve `widgetId` → **purge Qdrant** → nếu HTTP 2xx → `deleted_at` + `save`. Purge fail → **không** soft-delete. |
| **Failure** | `IllegalStateException` với message chứa `Failed to purge document vectors from Qdrant` (hoặc HTTP không 2xx). |
| **Logging** | `documentId`, `widgetId`, `points_matched_by_count` (nếu count OK); không log secret/body dài (truncate cảnh báo). |
| **Tránh xóa nhầm** | Bắt buộc **cả hai** điều kiện; không filter chỉ `widgetId`; không xóa collection. |
| **Widget soft-deleted** | `resolveWidgetIdForPurge`: thử `getWidgetConfig().getId()`; nếu `EntityNotFoundException` → native `BIN_TO_UUID(widget_config_id)` từ bảng `documents`. |

---

## 5. File type validation implementation

| Hạng mục | Chi tiết |
|----------|----------|
| **Backend** | `DocumentService.validateUploadableFile(MultipartFile)` (static, gọi đầu `uploadAndProcess` trước `saveFile`). |
| **Cho phép** | Tên file kết thúc `.pdf` / `.txt` (không phân biệt hoa thường). MIME: rỗng, `application/pdf`, `text/plain*`, `application/octet-stream`, `binary/octet-stream`. |
| **Chặn** | Không extension / không `.pdf|.txt` / MIME khác (vd `image/*`, `application/vnd...docx`). |
| **Message** | `DocumentService.UNSUPPORTED_UPLOAD_FILE_MSG` = tiếng Việt theo yêu cầu prompt. |
| **Controller** | `IllegalArgumentException` từ upload → **400** `{ "message": ... }` (đã có sẵn canonical upload). |
| **FE** | `UploadZone.jsx`: chỉ `pdf`/`txt`; batch có file lỗi → **không upload cả batch** + toast; `accept` bỏ docx. |
| **Toolbar** | Bỏ filter type DOCX. |
| **Table** | Badge DOCX/DOC → màu xám (legacy), không gợi ý “ưu tiên hỗ trợ”. |
| **Mock API** | `documentsApi.js` mock path reject không phải pdf/txt với `status: 400`. |

---

## 6. Files changed

| File | Change | Reason | Risk |
|------|--------|--------|------|
| `service/QdrantPurgeService.java` | **New** — delete-by-filter + optional count | Purge an toàn theo payload | Phụ thuộc Qdrant REST schema; cần payload keys cố định |
| `service/DocumentService.java` | Purge trước soft-delete; `validateUploadableFile` | A+B | Purge fail → user không xóa được doc (cần Qdrant up) |
| `domain/document/DocumentRepository.java` | Native `BIN_TO_UUID(widget_config_id)` | Widget đã soft-delete vẫn purge đúng tenant | Native SQL gắn MySQL 8 |
| `api/DocumentController.java` | `catch IllegalStateException` → **502** | Lỗi upstream Qdrant | Client cần xử lý 502 |
| `UploadZone.jsx` | accept/validate/batch | Khớp BE | — |
| `DocumentsToolbar.jsx` | Bỏ DOCX filter | Tránh gợi ý hỗ trợ | Row cũ type DOCX không filter nhanh bằng dropdown |
| `DocumentsTable.jsx` | Style DOCX/DOC | Legacy neutral | — |
| `api/documentsApi.js` | Mock validate | Mock khớp BE | — |

---

## 7. API behavior after fix

| Endpoint/Flow | Trước | Sau |
|---------------|-------|-----|
| `DELETE /api/documents/{id}` | 200 + soft-delete; vector còn | Gọi Qdrant delete-by-filter; 200 + soft-delete nếu Qdrant 2xx; **502** nếu purge fail (document không bị soft-delete). |
| `POST /api/documents/upload` + DOCX | Tạo row + file disk → parse fail | **400** + message; không lưu file; không tạo `Document`. |
| `POST /api/documents/upload` + PDF/TXT | Như cũ | Không đổi pipeline ingest. |

---

## 8. Validation results

| Command | Result | Notes |
|---------|--------|------|
| `cd Backend && mvnw.cmd -DskipTests compile` | **PASS** | exit 0 |
| `cd Backend && mvnw.cmd test` | **FAIL (env)** | MySQL `Connection refused` — không liên quan diff code |
| `cd Frontend && npm run lint` | **PASS** | exit 0 |
| `cd Frontend && npm run build` | **PASS** | Vite build OK |
| `npm run build:widget` | **NOT RUN** | Không sửa widget |
| `docker compose config` | **PASS** | `-q` |

---

## 9. Runtime/manual retest results

| Test | Expected | Actual | Status | Notes |
|------|----------|--------|--------|------|
| Qdrant purge sau DELETE | Vector theo document+widget hết; DB soft-delete | **NOT RUN** | Agent không chạy MySQL+Qdrant+upload E2E trong phiên |
| TXT upload | 200 | **NOT RUN** | Cần stack đầy đủ |
| PDF upload | 200 | **NOT RUN** | — |
| DOCX upload API | 400, không file disk | **NOT RUN** | Logic đã implement + compile PASS |
| Playground source sau delete | Không trích document đã xóa | **NOT RUN** | Cần user verify |

---

## 10. Known limitations

- **Payload consistency:** Purge dựa trên key `document_id` và `widgetId` giống lúc embed; vector cũ ingest trước khi có field này có thể không bị xóa (dữ liệu lịch sử hiếm).
- **DB `document_chunks`:** Vẫn tồn tại sau soft-delete document; retrieval chủ yếu dùng DB chunk sau vector anchor — nếu query DB không loại document đã `deleted_at`, vẫn có thể lộ context từ chunk DB (ngoài scope purge Qdrant; không sửa retrieval trong prompt này).
- **Hàng DOCX cũ** trong list (nếu có từ trước): vẫn hiển thị type DOCX với badge xám; không tự xóa.
- **Filter type DOCX** đã bỏ khỏi toolbar — lọc theo DOCX qua UI không còn; vẫn có thể dùng search/`All types`.

---

## 11. Final decision

**Qdrant purge and file type consistency — implemented; compile/lint/build PASS; full runtime purge + ingest E2E NOT RUN** (thiếu MySQL trong `mvn test`; không chạy Docker upload trong phiên).

---

## 12. Recommended next prompt

- **`12C_DB_CHUNK_VISIBILITY_FOR_DELETED_DOCUMENTS`** — khi document `deleted_at` set, loại chunk khỏi retrieval DB hoặc cascade soft-delete chunks (cần thiết kế transaction).
- **`12D_RUNTIME_SMOKE_QDRANT_PURGE_DELETE`** — script curl/PowerShell: upload TXT → delete → count Qdrant / gọi search.

---

## 13. Diff thay đổi chính (tóm tắt)

### `QdrantPurgeService.java` (new)

- Service `purgeDocumentVectors(documentId, widgetId)` gọi `POST .../points/delete` với `filter.must` hai điều kiện; optional `POST .../points/count`.

### `DocumentService.java`

```diff
+    private final QdrantPurgeService qdrantPurgeService;
+    public static final String UNSUPPORTED_UPLOAD_FILE_MSG =
+            "Định dạng file chưa được hỗ trợ. Hiện chỉ hỗ trợ PDF và TXT.";
     public Document uploadAndProcess(...) {
         WidgetConfig widgetConfig = ...
+        validateUploadableFile(file);
         String savedPath = saveFile(file);
```

```diff
-    @Transactional
     public void softDeleteDocument(UUID id) {
         Document d = documentRepository.findById(id)
                 .orElseThrow(() -> new IllegalArgumentException("Document not found"));
-        d.setDeletedAt(LocalDateTime.now());
-        documentRepository.save(d);
+        UUID widgetId = resolveWidgetIdForPurge(d);
+        qdrantPurgeService.purgeDocumentVectors(d.getId(), widgetId);
+        d.setDeletedAt(LocalDateTime.now());
+        documentRepository.save(d);
     }
```

### `DocumentController.java` (delete)

```diff
         } catch (IllegalArgumentException e) {
             return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Document not found"));
+        } catch (IllegalStateException e) {
+            String msg = e.getMessage() != null ? e.getMessage() : "Failed to purge document vectors from Qdrant";
+            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("message", msg));
         }
```

### `DocumentRepository.java`

- Thêm `findWidgetConfigIdUuidStringForPurge` (native `BIN_TO_UUID(widget_config_id)`).

### `UploadZone.jsx`

- `ALLOWED_EXTENSIONS` bỏ `docx`; validate MIME; batch có lỗi → không gọi `onUpload`.

---

*Bản sao theo rule workspace: `docs/CURSOR_REPORT_12B_QDRANT_PURGE_AND_FILE_TYPE_CONSISTENCY.md`.*
