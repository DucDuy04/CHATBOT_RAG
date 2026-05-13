# Cursor Report 12D - RUNTIME_SMOKE_QDRANT_PURGE_DELETE_AND_FILE_TYPE_VALIDATION

**Ngày:** 2026-05-13  
**Môi trường:** Windows 10, Docker Desktop, Backend `dev` profile trên `localhost:8080`, Qdrant `localhost:6333`.

---

## 1. Mức độ hiểu task

| Mục | Nội dung |
|-----|----------|
| **Task là gì?** | Runtime verify implementation 12B: (1) purge Qdrant khi `DELETE /api/documents/{id}`; (2) chặn upload không PDF/TXT (DOC/DOCX/no ext); (3) TXT/PDF upload vẫn OK khi provider/env đủ; không feature mới; chỉ sửa bug nhỏ nếu verify fail — **không phát sinh thay đổi source**. |
| **Hiểu task** | **98%** |
| **Phạm vi không làm** | Parser DOCX; đổi retrieval/prompt/chunking; widget; schema DB; refactor lớn. |

---

## 2. Files/rules/reports đã đọc

| File | Mục đích đọc | Kết luận chính |
|------|----------------|----------------|
| `.cursor/rules/00-core-working-rule.mdc` | Luật nền | Minimal diff; verify trung thực. |
| `.cursor/rules/10-backend-rag-rule.mdc` | Scope backend | Trỏ Document/Embedding/Qdrant. |
| `.cursor/rules/20-frontend-widget-rule.mdc` | FE | Widget không đổi; Documents UI liên quan accept. |
| `.cursor/rules/30-deploy-env-ops-rule.mdc` | Env/Docker | GROQ/NOMIC, Qdrant ports, compose. |
| `.cursor/rules/40-db-vector-rule.mdc` | Vector | Filter payload, không recreate collection. |
| `.cursor/rules/90-report-verification-rule.mdc` | Report | Mặc định `docs/`; prompt 12D yêu cầu `reports/` — báo cáo này theo **prompt 12D**. |
| `reports/CURSOR_REPORT_12A_CORE_INGESTION_EMBEDDING_FLOW_AUDIT.md` | Luồng ingest | Payload `document_id`, `widgetId`; batch fail theo thứ tự file. |
| `reports/CURSOR_REPORT_12B_QDRANT_PURGE_AND_FILE_TYPE_CONSISTENCY.md` | Kỳ vọng 12B | Purge trước soft-delete; validate trước `saveFile`; FE PDF/TXT. |
| `Backend/.../QdrantPurgeService.java` | Body delete/count | `filter.must`: `document_id` + `widgetId`; `POST .../points/delete` + optional `count`. |
| `Backend/.../DocumentService.java` | Upload + delete | `validateUploadableFile` trước lưu file; `softDeleteDocument` → purge rồi `deleted_at`. |
| `Backend/.../EmbeddingService.java` | Payload | `document_id`, `widgetId` string khớp purge filter. |
| `Backend/.../DocumentController.java` | HTTP | Upload 400 `message`; delete 502 nếu `IllegalStateException` purge. |
| `Backend/.../Document.java` | Entity | Soft delete `deleted_at`; `@SQLRestriction` ẩn row đã xóa khỏi list mặc định. |
| `Backend/.../DocumentChunk.java` | Chunk | FK document; không dùng trong smoke purge trực tiếp. |
| `Backend/.../DocumentRepository.java` | Purge orphan widget | Native `BIN_TO_UUID(widget_config_id)` khi widget soft-deleted. |
| `Backend/.../application-dev.yml` | Runtime | MySQL `localhost:3306`; Qdrant HTTP `6333`; collection `documents`. |
| `Backend/.../application.yml` | Multipart | 50MB; profile default `dev` trong file (đã biết từ 12A). |
| `docker-compose.yml` | Infra | `mysql` + `qdrant` ports 3306, 6333. |
| `Frontend/.../UploadZone.jsx` | UI | `accept=".pdf,.txt,..."`; validate batch. |
| `Frontend/.../DocumentsToolbar.jsx` | Filter | Chỉ PDF/TXT (không DOCX). |
| `Frontend/src/api/documentsApi.js` | Mock | Không mở chi tiết — smoke API dùng curl thật. |
| `DocumentsPage.jsx`, `DocumentsTable.jsx` | Prompt liệt kê | Không đổi trong phiên; không bắt buộc cho kết quả API/Qdrant. |

---

## 3. Runtime environment

| Item | Status | Notes |
|------|--------|------|
| Docker | **PASS** | `docker compose up -d mysql qdrant` OK. |
| MySQL | **PASS** | `ragchatbot-mysql` healthy; port 3306. |
| Qdrant | **PASS** | `ragchatbot-qdrant`; HTTP `6333`. |
| Backend | **PASS** (smoke window) | `mvnw spring-boot:run -Dspring-boot.run.profiles=dev` với biến môi trường **placeholder** cho `GROQ_API_KEY` / `NOMIC_API_KEY` (chỉ để context load; **không** ghi giá trị vào báo cáo). |
| Frontend dev / browser | **NOT RUN** | Không mở trình duyệt; kiểm tra UI qua đọc `UploadZone`/`DocumentsToolbar` + lint/build. |
| Env keys thật (`GROQ_API_KEY`, `NOMIC_API_KEY`) | **Không có trong shell Cursor** | `GROQ_API_KEY set: False`, `NOMIC_API_KEY set: False` tại thời điểm kiểm tra → ingest TXT hoàn tất embed **bị chặn provider** (xem §5). |

---

## 4. Test data

| Field | Giá trị (test đã dọn) |
|-------|------------------------|
| **chatbotId** (widget) | `116b125f-c91c-4bf4-8cbb-f467c2b03e30` — đã `DELETE /api/chatbots/{id}` → 200. |
| **documentId** (TXT thử ingest) | `845feeeb-397d-4812-829b-1dcda9e79c12` — upload TXT lỗi ở bước Nomic; sau đó dùng cho purge test; đã `DELETE /api/documents/{id}` → 200. |
| **fileName** | `purge-test.txt` |
| **chunkCount** | Parse log: **1 chunk** trước khi embed fail; không đạt `COMPLETED` qua API. |
| **qdrant collection** | `documents` |
| **qdrant filter used** | `must`: `document_id` match `845feeeb-397d-4812-829b-1dcda9e79c12`, `widgetId` match `116b125f-c91c-4bf4-8cbb-f467c2b03e30`; `"exact": true` trên `/points/count`. |

**Phương pháp bổ trợ purge (do thiếu Nomic thật):** Sau khi xác nhận ingest TXT không tạo vector (embed lỗi), đã **upsert 1 point** vào Qdrant qua REST (`PUT /collections/documents/points`) với **cùng payload keys** như `EmbeddingService` (`document_id`, `widgetId`) và vector 768 chiều, để đo `count` trước/sau `DELETE` — không đổi code backend.

---

## 5. File type validation results

| Test | Expected | Actual | Status | Notes |
|------|----------|--------|--------|------|
| TXT upload | 200, processed, chunkCount>0 | **500** (JSON Spring default error body) | **BLOCKED (provider)** | Nomic embed lỗi với key placeholder; parse+chunk OK trong log. |
| PDF upload | 200 | — | **NOT RUN** | Không có file PDF nhỏ sẵn (theo prompt). |
| DOCX API upload | 400, message tiếng Việt, không tạo Document | **400** `{"message":"Định dạng file chưa được hỗ trợ. Hiện chỉ hỗ trợ PDF và TXT."}` | **PASS** | Không gọi parse/embed. |
| DOC API upload | 400 | **400** cùng message | **PASS** | |
| No-extension upload | 400 | **400** cùng message | **PASS** | File tên `unsupportedfile`. |
| Batch TXT + DOCX (DOCX **trước**) | Fail cả batch, 400 rõ | **400** cùng message | **PASS** | Không silent skip. |
| Batch TXT + DOCX (TXT **trước**) | 400 hoặc fail rõ toàn batch | **500** (Spring error JSON) | **EXPECTED / GHI NHẬN** | File đầu vào pipeline đầy đủ tới embed → lỗi provider trước khi xét file 2; không chứng minh validate DOCX trong cùng multipart khi thứ tự như vậy. |
| UI DOCX blocked trước API | Không POST upload | — | **NOT RUN (browser)** | Code: `accept` chỉ pdf/txt; `ALLOWED_EXTENSIONS` không docx; toolbar type không có DOCX. |

**GET** `/api/documents?search=unsupported-test&page=0&size=10` → `items: []`, `total: 0` → không có hàng mới từ upload DOC/DOCX/no-ext.

---

## 6. Qdrant purge results

| Step | Expected | Actual | Status | Notes |
|------|----------|--------|--------|------|
| Count trước delete | count > 0 (nếu có vector khớp filter) | **count = 1** (`POST .../points/count`) | **PASS** | Sau upsert test point khớp `document_id` + `widgetId`. |
| `DELETE /api/documents/{id}` | 200, `success: true` | **200** `{"success":true}` | **PASS** | Không 502 → purge HTTP 2xx. |
| Count sau delete | count = 0 | **count = 0** | **PASS** | Filter giữ nguyên; payload keys khớp implementation. |
| Document list sau delete | Không thấy document (soft delete) | List theo search không còn document hoạt động (đã xóa + `@SQLRestriction`) | **PASS** | Không kiểm tra raw DB. |
| Retrieval / Playground sau delete | Không trích `purge-test.txt` | — | **NOT RUN** | Không gọi playground (thiếu Groq hợp lệ trong shell). |

---

## 7. Bugs found and fixed

| Bug | File | Fix | Retest status |
|-----|------|-----|----------------|
| — | — | — | **No source changes.** |

---

## 8. Files changed

| File | Change | Reason | Risk |
|------|--------|--------|------|
| — | — | — | **No source changes.** |

---

## 9. Validation results

| Command | Result | Notes |
|---------|--------|------|
| `docker compose up -d mysql qdrant` | **PASS** | Containers up; MySQL healthy. |
| `docker ps` | **PASS** | mysql + qdrant running. |
| `GET http://localhost:8080/api/chatbots?page=0&size=1` | **PASS** | HTTP 200 sau khi backend start. |
| `cd Backend && mvnw.cmd -DskipTests compile` | **PASS** | exit 0 (chạy trước đó trong phiên). |
| `cd Backend && mvnw.cmd test` | **FAIL** | `ApplicationContext` load lỗi / thiếu cấu hình env cho test (2 errors: `RagChatbotBeApplicationTests`, `QdrantConnectionTest`). **Không** xác định là regression do diff 12B (không đổi code 12D). |
| `cd Frontend && npm run lint` | **PASS** | exit 0. |
| `cd Frontend && npm run build` | **PASS** | Vite build OK (cảnh báo chunk size). |
| `npm run build:widget` | **NOT RUN** | Theo prompt (widget không đổi). |
| `docker compose config` | **PASS** | `-q` OK. |

---

## 10. Cleanup

| Hạng mục | Trạng thái |
|----------|------------|
| Test document | Đã `DELETE` (soft-delete + purge vector test). |
| Test chatbot | Đã `DELETE /api/chatbots/116b125f-c91c-4bf4-8cbb-f467c2b03e30` → 200. |
| Temp files | Đã xóa nội dung trong `tmp-runtime-smoke/` (file test + JSON Qdrant tạm). |
| API keys / secrets | **Không** ghi key thật trong báo cáo. |
| Qdrant vectors test | Point gắn `document_id` test đã bị xóa cùng purge khi delete document. |

---

## 11. Known limitations

- **Ingest TXT/PDF cần `NOMIC_API_KEY` hợp lệ** — không verify được “upload 200 + COMPLETED” trong phiên này.
- **DB `document_chunks`** vẫn có thể tồn tại sau soft-delete document (đã biết từ 12B/12A); smoke không kiểm tra retrieval DB.
- **Vector lịch sử** không có `document_id`/`widgetId` đúng format sẽ không bị purge bởi filter hiện tại.
- **DOCX parser** cố ý không có — chỉ validate extension/MIME.
- **UI browser** chưa chạy tay — chỉ xác nhận qua source + lint/build.

---

## 12. Final decision

**Some issues remain. Follow-up required.**

Lý do ngắn: (1) Không có **NOMIC** (và không set key thật trong môi trường agent) nên không hoàn tất E2E “upload TXT → vector từ embedding → delete”; purge/filter đã **PASS** với điểm Qdrant có payload khớp code. (2) `mvn test` **FAIL** do context test / env, cần profile test hoặc env riêng nếu muốn CI xanh.

Nếu chỉ xét **API chặn file lỗi + purge HTTP sau DELETE** trên stack Docker: **PASS**.

---

## 13. Recommended next prompt

- Chạy lại **12D** hoặc smoke ngắn với **`NOMIC_API_KEY` + `GROQ_API_KEY` thật** trong shell/CI để: TXT/PDF 200, optional Playground retrieval sau delete.
- **`12C_DB_CHUNK_VISIBILITY_FOR_DELETED_DOCUMENTS`** nếu sau này retrieval vẫn trích nội dung từ chunk DB của document đã `deleted_at`.
- Sửa **`mvn test`** (testcontainers / `@DynamicPropertySource` / profile `test`) nếu muốn `mvn test` xanh không phụ thuộc key máy dev — **ngoài scope 12D**.

---

*Bản ghi runtime smoke độc lập; không thay thế rule mặc định lưu report vào `docs/` cho các task khác.*
