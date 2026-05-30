# Backend Architecture Refactor 25B — Full Report

**Date:** 2026-05-28  
**Verdict:** **PASS**

---

## 1. Mức độ hiểu task

- **Hiểu task:** 95%
- **Chắc chắn:** package-only refactor; test sau mỗi phase; giữ DOCX/PDF/TXT, cells_json, Qdrant REST Unicode
- **Giả định:** Spring `@SpringBootApplication` scan vẫn cover sub-packages dưới `KLTN.RAG_CHATBOT_BE` (đã xác nhận qua test context)
- **Thiếu dữ liện:** Docker smoke upload DOCX không chạy trong session này

---

## 2. Tóm tắt yêu cầu

Tách boundary package backend RAG theo `ingest.parser`, `ingest.normalize`, `ingest.chunking`, `index.embedding`, `index.qdrant`, `rag.retrieve`, `rag.prompt` — không đổi hành vi; `mvn clean test` sau mỗi bước; cập nhật docs Qdrant REST-only.

---

## 3. Hiện trạng trước khi sửa

- Hầu hết logic RAG nằm trong `KLTN.RAG_CHATBOT_BE.service.*`
- `QdrantConfig` ở `config` nhưng đã REST bootstrap (không còn `QdrantEmbeddingStore` bean)
- Docs `agent/03-backend.md` vẫn ghi LangChain4j gRPC write

---

## 4. Nguyên nhân gốc (từ source)

Monolithic `service` package gây coupling khó bảo trì — task 25B yêu cầu tách boundary rõ, không phải sửa bug runtime.

---

## 5. Chiến lược sửa đã chọn

1. Viết inventory trước khi move  
2. Move từng boundary: parser → normalize → chunking → index → rag  
3. Bulk-update FQCN imports + fix compile  
4. Relocate tests cần package-private access  
5. Widen `public` tối thiểu cho cross-package static helpers  
6. Cập nhật `agent/02-architecture.md`, `agent/03-backend.md`

---

## 6. Danh sách file đã đọc

| Path | Mục đích | Kết luận |
|---|---|---|
| `DocumentParserService.java` | Parser boundary | Phụ thuộc `NormalizedTableService`, POI/Tabula |
| `NormalizedTableService.java` | Normalize boundary | Dùng `RawTableModel`, emit `cells_json` |
| `ChunkingService2.java` | Chunking | Nối parser + normalize → `record.DocumentChunk` |
| `EmbeddingService.java` | Index | REST upsert, không gRPC store |
| `QdrantConfig.java` | Qdrant init | HTTP collection create only |
| `RagRetrievalService.java` | Retrieval | `selectFinalContexts` in-class, không class riêng |
| `agent/03-backend.md` | Docs | Stale gRPC EmbeddingStore mention |

---

## 7. Danh sách file đã sửa

| Path | Mục đích | Layer |
|---|---|---|
| `ingest/parser/*.java` | Move parser + raw table models | ingest |
| `ingest/normalize/*.java` | Move normalizer | ingest |
| `ingest/chunking/ChunkingService2.java` | Move chunking | ingest |
| `index/embedding/EmbeddingService.java` | Move embedding | index |
| `index/qdrant/*.java` | Move Qdrant config/purge | index |
| `rag/retrieve/*.java` | Move retrieval | rag |
| `rag/prompt/PromptBuilderService.java` | Move prompt | rag |
| `service/DocumentService.java`, `ChatService.java`, … | Import updates | api/service |
| `record/Section.java` | Import `RawTableBlock` | record |
| `application.yml`, `application-docker.yml` | Logger FQCN | config |
| Test files (relocated + imports) | test | test |
| `NoHardcodedLexiconInTableNormalizerTest.java` | Path literals | test |
| `agent/02-architecture.md`, `agent/03-backend.md` | REST-only docs | docs |

---

## 8. Diff thay đổi (representative)

### Package declaration (pattern mọi file moved)

```diff
-package KLTN.RAG_CHATBOT_BE.service;
+package KLTN.RAG_CHATBOT_BE.ingest.parser;
```

### DocumentService imports

```diff
+import KLTN.RAG_CHATBOT_BE.ingest.parser.DocumentParserService;
+import KLTN.RAG_CHATBOT_BE.ingest.chunking.ChunkingService2;
+import KLTN.RAG_CHATBOT_BE.ingest.normalize.TableIngestMetrics;
+import KLTN.RAG_CHATBOT_BE.index.embedding.EmbeddingService;
+import KLTN.RAG_CHATBOT_BE.index.qdrant.QdrantPurgeService;
+import KLTN.RAG_CHATBOT_BE.rag.retrieve.KeywordIndexCache;
```

### agent/03-backend.md — Qdrant write path

```diff
-### QdrantConfig
-- Port gRPC `6334` (LangChain4j dùng gRPC), HTTP `6333` (REST API quản trị).
-- Tạo `QdrantEmbeddingStore` bean.
+### QdrantConfig (`index.qdrant`)
+- HTTP `6333`: bootstrap collection qua REST
+- **Qdrant write path**: REST upsert/search qua `EmbeddingService` — không dùng LangChain4j QdrantEmbeddingStore / gRPC write
```

### Visibility (package boundary only)

```diff
-    static boolean hasHighTableLikeDensity(String text) {
+    public static boolean hasHighTableLikeDensity(String text) {
```

---

## 9. Ảnh hưởng sau sửa

| Khía cạnh | Thay đổi |
|---|---|
| Runtime RAG pipeline | **Giữ nguyên** |
| DOCX logical grid / cells_json | **Giữ nguyên** |
| Qdrant REST Unicode payload | **Giữ nguyên** |
| Classpath / Spring beans | Beans vẫn register (cùng root package) |
| API contracts | **Không đổi** |
| Memory/CPU | Không đổi |
| Latency/tokens | Không đổi |
| MySQL/Qdrant data cũ | Không migrate |

---

## 10. Edge cases đã xem xét

- Package-private methods sau move → relocate tests hoặc `public` tối thiểu  
- `NoHardcodedLexicon` path literals → cập nhật path mới  
- Logger YAML FQCN → cập nhật theo package mới  
- Cross-package `QuerySignalExtractor` / `RagLatencyTrace` → explicit imports  

---

## 11. Kết quả kiểm tra

| Command | Kết quả | Ghi chú |
|---|---|---|
| `mvnw.cmd clean test` (P2–P7) | PASS | 50 tests, 0 failures mỗi lần cuối phase |
| `docker compose config -q` | PASS | Exit 0 |
| Frontend lint/build | NOT RUN | Ngoài scope |
| Docker smoke upload | NOT RUN | Không có stack running trong session |

---

## 12. Rủi ro còn lại

- Orchestration vẫn trong `service` — boundary chưa hoàn chỉnh  
- Một số helper `public` rộng hơn trước (chỉ visibility, không logic)  
- Smoke production DOCX/Qdrant parity chưa re-run manual Q1–Q8  

---

## 13. Đề xuất tiếp theo

**Task 25C:** `rag.runtime` + `common.util` + `audit.metrics`; `package-info.java`; optional mirror test package tree.

---

## Phase test log

| Phase | Result |
|---|---|
| P1 Inventory | Done |
| P2 parser | PASS |
| P3 normalize | PASS |
| P4 chunking | PASS |
| P5 index | PASS |
| P6 rag | PASS |
| P7 docs + final test | PASS (50 tests) |
