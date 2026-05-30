# ARCHITECTURE_HYGIENE_25E — Test Package Hygiene + Cursor Rules / Docs Path Sync

**Date:** 2026-05-28  
**Verdict:** **PASS**  
**Scope:** Architecture hygiene only — no runtime behavior changes.

---

## 1. Mức độ hiểu task

| Mục | Nội dung |
|---|---|
| Hiểu task | **95%** |
| Chắc chắn | Test package alignment, Cursor rules + agent docs sync, stale path inventory, `mvn clean test`, `docker compose config` |
| Giả định | Docker smoke (Phase 8) không chạy — stack không yêu cầu cho PASS |
| Thiếu dữ liện | Không |

---

## 2. Tóm tắt yêu cầu

Sau 25B/25C/25D: căn test packages với production boundaries, cập nhật `.cursor/rules` và `agent/*.md` / `agent.md` theo package map mới, quét stale references, không đổi runtime behavior.

---

## 3. Hiện trạng trước khi sửa

- Một số test còn ở `KLTN.RAG_CHATBOT_BE` root hoặc `service` dù production đã ở `ingest.*`, `index.embedding`, `rag.prompt`.
- `.cursor/rules/10-backend-rag-rule.mdc` và `40-db-vector-rule.mdc` vẫn trỏ `service/ChatService`, `service/EmbeddingService`, `config/QdrantConfig`.
- `agent.md` snapshot vẫn mô tả monolithic `service/` và gRPC LangChain4j Qdrant.
- `agent/03-backend.md` còn `langchain4j-qdrant`, ingest pipeline cũ (`table_row_group`).
- Production `Backend/src`: đã đúng package map 25D.

---

## 4. Nguyên nhân gốc xác nhận từ source

Docs/rules không được sync sau refactor 25B–25D; test files được restore (25A2) trước khi package moves hoàn tất — package declaration lệch production.

---

## 5. Chiến lược sửa đã chọn

1. Inventory stale refs (classify, không xóa report history).
2. Move test files + đổi `package` only (không widen visibility).
3. Rewrite active Cursor rules với canonical paths + Qdrant/DOCX/Unicode notes.
4. Patch `agent.md`, `agent/01-overview.md`, `agent/02-architecture.md`, `agent/03-backend.md`.
5. Defer `common.util` và heavy deferred tests.
6. `mvn clean test` + final stale scan.

---

## 6. Danh sách file đã đọc

| Path | Đọc để | Kết luận |
|---|---|---|
| `.cursor/rules/10-backend-rag-rule.mdc` | Stale paths | Toàn bộ upload/chat globs trỏ `service/*` RAG |
| `.cursor/rules/40-db-vector-rule.mdc` | Stale globs | `config/QdrantConfig`, `service/EmbeddingService` |
| `agent.md` | Snapshot | gRPC Qdrant, monolithic service list |
| `agent/01-overview.md` | Chunk types | Legacy chunk names active guidance |
| `agent/02-architecture.md` | Package map | Đã có 25C table; chunk types cần legacy note |
| `agent/03-backend.md` | Backend detail | `langchain4j-qdrant`, ingest steps cũ |
| `Backend/src/test/**` | Test layout | 5 files cần move |
| `docs/eval/results/REGRESSION_TEST_RESTORE_25A2_20260528.md` | Deferred tests | REQUIRED_NEXT list |
| `Backend/pom.xml` | langchain4j-qdrant | Đã gỡ dependency |

---

## 7. Danh sách file đã sửa

| Path | Sửa để | Layer |
|---|---|---|
| `Backend/src/test/.../rag/prompt/PromptBuilderServiceTest.java` | Move từ root | test |
| `Backend/src/test/.../index/embedding/EmbeddingServiceCacheTest.java` | Move từ `service` | test |
| `Backend/src/test/.../ingest/normalize/NormalizedTableIngestTest.java` | Move từ `service` | test |
| `Backend/src/test/.../ingest/normalize/NoHardcodedLexiconInTableNormalizerTest.java` | Move từ `service` | test |
| `Backend/src/test/.../ingest/normalize/TestRawTableFixtures.java` | Move fixture cùng package | test |
| `.cursor/rules/10-backend-rag-rule.mdc` | Canonical package map + Qdrant/DOCX rules | docs |
| `.cursor/rules/40-db-vector-rule.mdc` | REST path globs + checklist | docs |
| `agent.md` | Structure, Qdrant, core modules | docs |
| `agent/01-overview.md` | Chunk types summary | docs |
| `agent/02-architecture.md` | Chunk types legacy note | docs |
| `agent/03-backend.md` | Deps, profiles, ingestion steps | docs |

**Deleted (moved):** old paths under `service/` test package và root `PromptBuilderServiceTest.java`, `EmbeddingServiceCacheTest.java`.

---

## 8. Diff thay đổi của từng file

### Test moves (pattern chung)

**Hiện trạng cũ:** `package KLTN.RAG_CHATBOT_BE` hoặc `KLTN.RAG_CHATBOT_BE.service`  
**Đã sửa:** package khớp production (`rag.prompt`, `index.embedding`, `ingest.normalize`)  
**Vì sao:** Test hygiene — AI và developer tìm test cùng package với code under test  
**Ảnh hưởng:** Zero runtime; Surefire discovery unchanged (50 tests)

```diff
-package KLTN.RAG_CHATBOT_BE.service;
+package KLTN.RAG_CHATBOT_BE.index.embedding;

-import KLTN.RAG_CHATBOT_BE.index.embedding.EmbeddingService;
 class EmbeddingServiceCacheTest {
```

```diff
-package KLTN.RAG_CHATBOT_BE;
+package KLTN.RAG_CHATBOT_BE.rag.prompt;

-import KLTN.RAG_CHATBOT_BE.rag.prompt.PromptBuilderService;
 class PromptBuilderServiceTest {
```

```diff
-package KLTN.RAG_CHATBOT_BE.service;
+package KLTN.RAG_CHATBOT_BE.ingest.normalize;
```

### `.cursor/rules/10-backend-rag-rule.mdc`

```diff
+- Package map table (ingest/index/rag/llm/audit/service)
+- Qdrant REST / no QdrantEmbeddingStore / DOCX physicalColIndex / UTF-8
-- `service/DocumentParserService.java`
+- `ingest/parser/DocumentParserService.java`
-- `service/ChatService.java`
+- `rag/runtime/ChatService.java`
```

### `.cursor/rules/40-db-vector-rule.mdc`

```diff
-globs: .../config/QdrantConfig.java, .../service/EmbeddingService.java
+globs: .../index/qdrant/**/*, .../index/embedding/**/*, .../rag/retrieve/**/*
+- Qdrant write path (canonical) section
```

### `agent.md`

```diff
-- Port: gRPC 6334 (LangChain4j), HTTP 6333
+- Write/search: REST 6333 via EmbeddingService; 6334 tooling only
-- service/: ChatService, DocumentParserService, ...
+- ingest.*, index.*, rag.*, llm/, service/ (admin only)
```

### `agent/03-backend.md`

```diff
-- langchain4j-qdrant
+- không còn langchain4j-qdrant
-- Qdrant localhost:6334 (gRPC)
+- Qdrant REST http-port: 6333
-- table_row_group ingest steps
+- NormalizedTableService → normalized_table_row
```

---

## 9. Ảnh hưởng sau sửa

| Behavior | Thay đổi |
|---|---|
| Runtime RAG/ingest/chat | **Không** |
| Test assertions | **Không** |
| AI session guidance | **Có** — rules/agent trỏ đúng path |
| Test file locations | **Có** — mirror production packages |
| Qdrant REST / Unicode | **Giữ nguyên** |
| Memory/CPU/latency | Không đổi |

---

## 10. Edge cases đã xem xét

- Empty `service/` test dir sau move — OK, không ảnh hưởng build.
- `NoHardcodedLexiconInTableNormalizerTest` đọc file bằng relative path từ CWD Maven (`Backend/`) — vẫn PASS.
- Historical reports giữ stale paths — **VALID_LEGACY_REPORT**, không xóa.
- `PromptBuilderService` vẫn mention `table_row_group` trong prompt text — backward compat cho chunk cũ, không đổi trong 25E.

---

## 11. Kết quả kiểm tra

| Command | Kết quả | Ghi chú |
|---|---|---|
| `cd Backend; .\mvnw.cmd clean test` | **PASS** | 50 tests, 0 failures, 0 errors |
| `docker compose config -q` | **PASS** | Exit 0 |
| Final stale scan `Backend/src` | **PASS** | 0 production stale `service.*` RAG imports |
| Final stale scan `.cursor` + `agent` | **PASS** | Chỉ “do not use QdrantEmbeddingStore” guidance |
| Frontend lint/build/widget | **NOT RUN** | Out of scope |
| Docker smoke up backend | **NOT RUN** | Optional Phase 8 |

---

## 12. Rủi ro còn lại

- Historical `docs/` và `reports/` vẫn chứa path cũ (acceptable).
- `NormalizedTableSuppressionTest`, `HybridKeywordSearchTest`, full `RagRetrievalService` E2E — **REQUIRED_NEXT** (25A2).
- `agent/06-operations.md` vẫn mention gRPC 6334 cho ops — không sửa trong 25E (tooling port, not write path).

---

## 13. Đề xuất tiếp theo

1. **25F or REQUIRED_NEXT:** Restore `NormalizedTableSuppressionTest` + `HybridKeywordSearchTest` với Spring test slice nhẹ.
2. Sync `agent/06-operations.md` nếu cần tách “tooling gRPC” vs “backend write REST”.
3. Optional: add `.cursor/rules` test-package note mirroring `src/test` layout table.

---

## Stale reference inventory (Phase 1 / 7)

| Pattern | Backend/src | .cursor/agent (active) | docs/reports |
|---|---|---|---|
| `service.ChatService` etc. | 0 | 0 (after fix) | VALID_LEGACY_REPORT |
| `service/ChatService` path | 0 | 0 | VALID_LEGACY_REPORT |
| `QdrantEmbeddingStore` | 0 (comment only in EmbeddingService) | Do-not-use guidance | VALID_LEGACY_REPORT in old reports |
| `langchain4j-qdrant` | 0 in pom | Do-not-use in rules | VALID_LEGACY_REPORT |
| `table_row_group` in production ingest | Not created new | N/A | Runtime/prompt backward compat reads OK |

---

## Test package map (after 25E)

| Test class | Package |
|---|---|
| `DocxParserServiceTest` | `ingest.parser` |
| `NormalizedTableIngestTest` | `ingest.normalize` |
| `NoHardcodedLexiconInTableNormalizerTest` | `ingest.normalize` |
| `TestRawTableFixtures` | `ingest.normalize` |
| `EmbeddingServiceCacheTest` | `index.embedding` |
| `QdrantPayloadUnicodeTest` | `index.embedding` |
| `CellAwareNormalizedRowRetrievalTest` | `rag.retrieve` |
| `FinalContextSelectionTest` | `rag.retrieve` |
| `RetrievalTopKTest` | `rag.runtime` |
| `ChatServiceSourcePresentationTest` | `rag.runtime` |
| `PromptBuilderServiceTest` | `rag.prompt` |
| `RagChatbotBeApplicationTests` | root (context load) |

---

## common.util decision

**Deferred** — không có pure static helper dùng chéo package mà không gắn domain/RAG; `QuerySignalExtractor.normalize` và các normalize trong ingest/retrieve là domain-specific.

---

## Deferred tests (Phase 6)

| Test | Status |
|---|---|
| `NormalizedTableSuppressionTest` | **REQUIRED_NEXT** |
| `HybridKeywordSearchTest` | **REQUIRED_NEXT** |
| Full `RagRetrievalService` E2E | **REQUIRED_NEXT** |

---

## Behavior changes

**None.**
