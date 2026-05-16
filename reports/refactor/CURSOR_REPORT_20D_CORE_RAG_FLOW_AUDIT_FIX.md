# Cursor Report 20D — Core RAG Flow Audit (Upload → Chat → Delete → Retry)

**Ngày:** 2026-05-14  
**Scope:** Audit luồng RAG chính từ source + compile baseline; **không** backfill/migration; **không** sửa runtime nếu không có blocker rõ ràng.

---

## 1. Mức độ hiểu task

| Mục | Nội dung |
|-----|----------|
| **Hiểu task** | **92%** |
| **Chắc chắn** | Chuỗi controller → service cho upload/parse/chunk/embed/delete/retry/chat; payload Qdrant (`document_id`, `widgetId`, …) khớp purge/search; `ChatService` gọi `ragRetrievalService.retrieveWithMetadata` rồi fallback câu cố định khi context rỗng; `softDeleteDocument` purge Qdrant trước rồi soft-delete bảng con + document (đã có từ 20C). |
| **Giả định** | Chưa chạy E2E runtime (Groq/Nomic/Qdrant/MySQL) trong phiên này; hành vi LangChain4j `QdrantEmbeddingStore.addAll` map payload → Qdrant giống filter HTTP hiện dùng (theo tài liệu kiến trúc + code `EmbeddingService`). |
| **Thiếu dữ kiện** | Không có bằng chứng runtime (log Qdrant, response search) trong task này. |

---

## 2. Tóm tắt yêu cầu

Kiểm tra và ổn định luồng: upload → parse → chunk → embed/index → chat/retrieval → nguồn → delete → chat không dùng doc đã xóa → retry khi FAILED; minimal diff; không backfill; không đổi contract/dependency/collection; báo cáo + checklist verify DB sạch.

---

## 3. Phạm vi đã làm

- Đọc rule (đã biết từ context), README, `docs/RAG_TARGET_ARCHITECTURE.md`, report 20A/20B/20C (20C đọc phần đầu + flow delete).
- Trace source: `DocumentController`, `DocumentService`, `DocumentParserService` (entry + PDF path), `ChunkingService2` (entry `processSections2`), `EmbeddingService`, `QdrantPurgeService`, `QdrantConfig`, `ChatService`, `ChatController`, `PublicChatController`, `RagRetrievalService` (`retrieveWithMetadata`, `extractIds`), `PromptBuilderService` (system prompt đoạn no-context), repository chunk/section/table, entity `Document`/`DocumentChunk`.
- Chạy: `mvnw -DskipTests compile` (PASS), `docker compose config -q` (PASS).
- **Không** chỉnh sửa file Java/YAML runtime trong task 20D.

---

## 4. Phạm vi không làm

- Không hybrid search FULLTEXT, không `document_table_rows`, không Flyway/async ingestion/rate limit.
- Không refactor `RagRetrievalService`, không đổi prompt lớn, không sửa UI/security/CORS/`DocumentController.assign`.
- Không chạy `mvn test` (môi trường có thể thiếu MySQL như 20B/20C).

---

## 5. Vì sao không làm backfill

Theo yêu cầu task: dữ liệu test, user có thể xóa sạch DB/Qdrant/uploads; không cần migration/backfill cho soft-delete cũ.

---

## 6. Current core RAG flow từ source

### 6.1 Upload

| Bước | Source |
|------|--------|
| Legacy | `POST /api/documents/upload/{widgetId}` → `DocumentController.uploadLegacy` → `documentService.uploadAndProcess(file, widgetId)`. |
| Canonical | `POST /api/documents/upload` (multipart `files`, `chatbotId` hoặc `widgetId`) → validate UUID + `widgetConfigRepository.existsById` → `documentService.uploadDocumentsCanonical(files, tenantId)`. |
| Validate | `DocumentService.validateUploadableFile`: `.pdf`/`.txt` + MIME cho phép (pdf, plain text, octet-stream). |
| Lưu file | `saveFile` → `${app.upload-dir}` (mặc định `./uploads` trong `application*.yml`) + tên `timestamp_sanitizedOriginal`. |
| Tạo row | `Document` PENDING, `filePath`/`fileName`/widget FK; `documentRepository.save`. |
| Xử lý | `executeProcessing` (private): PROCESSING → parse → chunk → lưu section/table/chunk DB → `linkPrevNextChunks` → `embeddingService.embedAndStore` → COMPLETED + `chunkCount`. |
| Lỗi | `uploadAndProcess` catch: FAILED + rethrow IOException/Runtime; checked → `IllegalStateException`. |

### 6.2 Parse

- `DocumentParserService.parse(MultipartFile)`: PDF → PDFBox + Tabula; TXT → UTF-8 string + `cleanText`; output `List<Section>`.

### 6.3 Chunk

- `ChunkingService2.processSections2(sections)` → `List<record.DocumentChunk>` (sectionId/parentId/tableId/order/content/chunkType/…).

### 6.4 Embed + Qdrant

- `EmbeddingService.embedAndStore`: metadata gồm `chunk_id`, `document_id`, `documentId`, `fileName`, `source_file`, `widgetId`, `chunkIndex`, `chunk_type`, `section_id`, `parent_id`, …, optional `table_id`, `prev_chunk_id`, `next_chunk_id`; text embed từ `buildEmbeddingText`.
- `qdrantEmbeddingStore.addAll` (LangChain4j) — collection/host từ `QdrantConfig`.
- `QdrantConfig.initQdrantCollection`: ApplicationRunner tạo collection nếu GET collection → 404, body `vectors.size` + Cosine.

### 6.5 Chat / retrieve

- `ChatController` / `PublicChatController`: `Widget-Id` từ filter → `chatService.chat(request, widgetId)`.
- `ChatService.chat`: lưu USER message → `queryAnalyzerService.analyze` → `ragRetrievalService.retrieveWithMetadata(question, widgetId)` → `buildSourceDtos`; nếu `contexts.isEmpty()` → câu trả lời cố định *"Tôi không tìm thấy thông tin này trong tài liệu."* (không gọi LLM); else → `promptBuilderService.buildUserPromptFromRetrievedContexts` + `getSystemPrompt` → `llmFallbackService.generateWithFallback` → lưu ASSISTANT + sources.
- Stream: cùng retrieval; empty context → SSE token câu trên + `done` với `[]`, lưu message không sources.

### 6.6 Prompt / LLM

- `PromptBuilderService`: system prompt yêu cầu chỉ dùng context; rule empty context đồng bộ với nhánh không gọi LLM trong `ChatService` khi pool rỗng.

### 6.7 Delete

- `DocumentController.delete` → `documentService.softDeleteDocument(id)`.
- `softDeleteDocument`: `resolveWidgetIdForPurge` → `qdrantPurgeService.purgeDocumentVectors(documentId, widgetId)` (filter `document_id` + `widgetId`) — fail → `IllegalStateException`, không soft-delete DB → `DocumentController` trả 502.
- Sau purge OK: `documentTableRepository` / `documentSectionRepository` / `documentChunkRepository` `softDeleteByDocumentId` → `document.deletedAt` + save.

### 6.8 Retry

- `POST /api/documents/{id}/retry` → `retryFailedDocument`.
- Điều kiện: status FAILED; **không** có chunk/section/table trong DB (`findByDocumentId` / order queries — entity có `@SQLRestriction`); file `filePath` còn tồn tại.
- `BytesMultipartFile.fromPath` → `executeProcessing`; lỗi → FAILED + throw.

---

## 7. Blocker / rủi ro trực tiếp của luồng chính

| Mục | Đánh giá từ source |
|-----|-------------------|
| Delete → chat không dùng doc đã xóa (MySQL path) | **Đã xử lý ở 20C** (bulk `deleted_at` child + `@SQLRestriction` trên chunk/section/table). |
| Delete → Qdrant | Purge theo đúng key upsert (`document_id` + `widgetId`). |
| widgetId filter search | `EmbeddingService.search` filter `must` `widgetId`. |
| Context rỗng | Sync + stream đều trả câu “không tìm thấy trong tài liệu”, không gọi LLM (sync) / không stream model (empty branch). |
| **Rủi ro còn lại (không sửa 20D)** | Pipeline `uploadAndProcess` / `executeProcessing` **không** bọc `@Transactional` toàn phần: nếu lỗi sau khi đã `saveChunks` nhưng trước/khi embed, có thể DB có chunk, status FAILED, **retry bị từ chối** (có chunk) — user phải xóa document; có thể có vector đã ghi một phần tùy thời điểm lỗi (đề xuất verify runtime / cân nhắc transaction boundary **ngoài** scope minimal 20D). |
| **Edge nhỏ** | `embedAndStore` với 0 chunk: return sớm; `executeProcessing` vẫn set COMPLETED + `chunkCount=0` nếu parse ra chunk rỗng (hiếm). |

**Kết luận:** Không phát hiện **blocker mới** so với trạng thái đã fix 20C; không có diff runtime bắt buộc trong 20D.

---

## 8. Có sửa runtime không?

**Không.**

---

## 9. Nếu có sửa — (không áp dụng)

Không có sửa runtime trong phiên 20D → không có root cause mới / chiến lược sửa minimal trong scope commit này.

---

## 10. Vì sao không sửa backlog khác

- Transaction toàn pipeline + đồng bộ partial Qdrant: thay đổi hành vi lớn, có thể cần compensating delete vectors — trái “minimal diff” và chưa được user yêu cầu trong 20D.
- `DocumentController.assign`: không phải blocker luồng chính đã mô tả.

---

## 11. Danh sách file đã đọc

| Path | Đọc để | Kết luận chính |
|------|--------|----------------|
| `docs/RAG_TARGET_ARCHITECTURE.md` | Khung kiến trúc / map class | Khớp với code đang trace. |
| `reports/refactor/CURSOR_REPORT_20C_*.md` | Delete + child soft-delete | Root cause leak MySQL đã được fix; flow purge trước DB giữ nguyên. |
| `README.md` | Bắt buộc đọc | Hướng dẫn rules + report (gợi ý `docs/`). |
| `TASK_PROMPT_TEMPLATE.md` | Tồn tại | Chỉ xác nhận file có trong repo (không parse sâu). |
| `DocumentController.java` | Upload/list/status/chunks/retry/delete | Hai endpoint upload; assign luôn unsupported; delete map `IllegalStateException` → 502. |
| `DocumentService.java` | Pipeline đầy đủ | Flow status; soft-delete + child; retry guard. |
| `DocumentParserService.java` | Entry parse | PDF/TXT; output `List<Section>`. |
| `ChunkingService2.java` | Entry chunking | `processSections2` từ sections → record chunks. |
| `EmbeddingService.java` | Payload + search | Keys khớp purge/filter. |
| `QdrantPurgeService.java` | Delete vectors | Filter `document_id` + `widgetId`. |
| `QdrantConfig.java` | Collection bootstrap | Tạo collection nếu thiếu. |
| `ChatController.java` / `PublicChatController.java` | Widget → chat | Cùng `ChatService.chat` + `Widget-Id`. |
| `ChatService.java` | Retrieval + empty + sources | `retrieveWithMetadata`; empty → không LLM; sources map. |
| `RagRetrievalService.java` | `retrieveWithMetadata` + `extractIds` | Vector → anchor IDs → DB expand; early empty return khi không có anchor. |
| `PromptBuilderService.java` | System prompt | Khớp policy “không bịa” / empty context. |
| `Document.java` / `DocumentChunk.java` | Soft delete | `@SQLRestriction("deleted_at IS NULL")`. |
| `DocumentChunkRepository.java` / `DocumentSectionRepository.java` / `DocumentTableRepository.java` | softDelete JPQL | Được gọi từ `softDeleteDocument`. |

**Không đọc toàn bộ:** `QueryAnalyzerService`, `RerankService`, `LlmFallbackService` toàn file (chỉ xác nhận qua 20A map + ChatService wiring); `DocumentRepository` native purge query (chỉ referenced từ `resolveWidgetIdForPurge` trong 20C); toàn bộ `ChunkingService2` / `DocumentParserService` body (chỉ entry và constants).

---

## 12. Danh sách file đã sửa

| Path | Sửa để | Layer |
|------|--------|--------|
| `reports/refactor/CURSOR_REPORT_20D_CORE_RAG_FLOW_AUDIT_FIX.md` | Báo cáo audit task 20D | docs/report |

---

## 13. Diff thay đổi của từng file

### 13.1 Runtime (Java/YAML)

**Không có thay đổi runtime** trong task 20D.

### 13.2 Report mới

```diff
+ (new file) reports/refactor/CURSOR_REPORT_20D_CORE_RAG_FLOW_AUDIT_FIX.md
+ Nội dung: báo cáo audit luồng RAG chính, flow 10 bước, rủi ro transaction/partial,
+   kết quả compile + docker compose config, checklist verify DB sạch.
```

---

## 14. Ảnh hưởng sau sửa

- **Thay đổi:** Chỉ thêm file report; không đổi behavior runtime.
- **Giữ nguyên:** Toàn bộ API, DB schema, Qdrant, ingestion, chat.
- **RAM/CPU/DB/disk:** Không đổi.

---

## 15. Edge cases đã xem xét

- Purge Qdrant fail → không soft-delete document (đã có).
- Context retrieval rỗng → không gọi LLM (sync) / message cố định (stream).
- Retry khi đã có chunk/section/table → từ chối an toàn.
- File mất khi retry → `IllegalArgumentException`.
- Partial pipeline không transactional → rủi ro retry/doc “kẹt” (mục 7).
- Qdrant search exception từng variant → `continue` trong RAG (có thể giảm context, không crash).

---

## 16. Kết quả kiểm tra

| Command | Kết quả | Ghi chú |
|---------|---------|---------|
| `cd Backend; .\mvnw.cmd -DskipTests compile` | **PASS** | BUILD SUCCESS. |
| `docker compose config -q` | **PASS** | Exit 0, không output. |
| `mvnw test` | **NOT RUN** | Có thể fail thiếu MySQL như 20B/20C; không bắt buộc task 20D. |
| Frontend lint/build/widget | **NOT RUN** | Không đổi FE. |

---

## 17. Runtime verification checklist (DB sạch)

1. Xóa dữ liệu test (MySQL tables liên quan document/chat nếu muốn), points/collection Qdrant tùy chọn, thư mục `uploads/`.
2. `docker compose up` (hoặc stack dev đầy đủ).
3. Tạo widget/chatbot nếu chưa có.
4. Upload 1 file TXT nhỏ có câu từ khóa rõ qua `POST /api/documents/upload` hoặc legacy path với đúng `widgetId`.
5. Poll `GET /api/documents/{id}/status` → FE status INDEXED / backend COMPLETED.
6. DB: `documents` 1 row; `document_chunks` > 0; `document_sections` tùy parser.
7. `POST /api/chat` hoặc public chat với cùng widget key + `sessionId` → câu hỏi nằm trong file.
8. Kiểm tra answer + `sources` có `fileName` khớp.
9. `DELETE /api/documents/{id}` → 200 (hoặc 502 nếu Qdrant down — kỳ vọng không xóa DB).
10. DB: `documents.deleted_at` NOT NULL; `document_chunks` / `document_sections` / `document_tables` (nếu có) `deleted_at` NOT NULL.
11. Hỏi lại cùng câu → không còn context từ doc đã xóa (câu “không tìm thấy…” hoặc sources không còn file đó).
12. (Tuỳ chọn) Document FAILED không chunk: `POST .../retry` → COMPLETED nếu file còn.

---

## 18. Rủi ro còn lại

- Ingestion không transaction end-to-end: lỗi giữa chừng có thể để lại chunk DB hoặc vector không nhất quán với kỳ vọng “retry một nút” (retry bị chặn nếu đã có chunk).
- Phụ thuộc Qdrant/Groq/Nomic tại runtime — không verify trong task này.

---

## 19. Đề xuất prompt tiếp theo

1. E2E tự động hoặc manual script: upload TXT → chat → delete → chat (một lần chạy trên docker compose).
2. Cân nhắc **một** chiến lược nhất quán ingest: transactional DB + embed chỉ sau commit, hoặc cleanup chunk khi embed fail (phải thiết kế kỹ Qdrant rollback — **ngoài** minimal 20D).
3. Đánh giá `DocumentController.assign` / dead response line nếu sau này hỗ trợ re-assign widget.

---

**Kết thúc report 20D.**
