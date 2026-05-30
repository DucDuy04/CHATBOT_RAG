# LLM Adapter Package Refactor 25D — Full Report

**Date:** 2026-05-28  
**Verdict:** **PASS**

---

## 1. Mức độ hiểu task

- **Hiểu task:** 95%
- **Chắc chắn:** package-only move `LlmFallbackService` + `LlmGenerationOptions` → `llm`; cập nhật imports; không đổi fallback/streaming/temperature semantics; test sau mỗi phase
- **Giả định:** dùng `KLTN.RAG_CHATBOT_BE.llm` (không tách `llm.provider` vì chỉ 2 class liên kết chặt)
- **Thiếu dữ liện:** Docker smoke upload DOCX không chạy trong session

---

## 2. Tóm tắt yêu cầu

Tiếp 25C: tách LLM provider/adaptor khỏi `service`, xóa stale duplicate nếu còn, cập nhật docs/package-info, `mvn clean test` sau mỗi bước.

---

## 3. Hiện trạng trước khi sửa

Sau 25C, `service` còn:

- `LlmFallbackService`, `LlmGenerationOptions` (Groq sync/stream + fallback)
- `DocumentService`, `WidgetService`, `AnalyticsService`, `DashboardService`, `SettingsService`, `ChatFeedbackService`

Stale duplicate từ 25B/25C **không còn** trong `src/main/java/.../service` (đã xóa ở 25C).

---

## 4. Nguyên nhân gốc (từ source)

`LlmFallbackService` nằm trong `service` cùng admin/widget/upload — `rag.runtime` phụ thuộc ngược layer generic `service` cho LLM, khó audit boundary LLM provider.

---

## 5. Chiến lược sửa đã chọn

1. Inventory (rg + list `service/`)
2. Move `LlmFallbackService`, `LlmGenerationOptions` → `llm` (package declaration + imports only)
3. Cập nhật imports runtime/controllers/tests/widget
4. Xác nhận không còn stale duplicate trong `service/`
5. `package-info.java` + `agent/02-architecture.md`, `agent/03-backend.md`
6. Final scan + `mvn clean test` + `docker compose config -q`

---

## 6. Inventory (Phase 1)

| Class/File | Current package/path | Responsibility | Dependencies | Target package | Move / Keep / Delete / Defer | Reason |
|---|---|---|---|---|---|---|
| `LlmFallbackService` | `service` | Groq sync/stream, TPM/TPD fallback, model builders | `RagTokenAudit`, LangChain4j OpenAI | `llm` | **Move** | LLM provider adapter |
| `LlmGenerationOptions` | `service` | temperature/maxTokens record + parse overrides | None | `llm` | **Move** | Generation params tied to LLM |
| `DocumentService` | `service` | Upload → parse → chunk → embed → status | ingest/index/qdrant/DB | `service` | **Keep** | Application lifecycle |
| `WidgetService` | `service` | Widget CRUD, uiConfig modelConfig | DB, `RagRetrievalService`, `LlmGenerationOptions` parsers | `service` | **Keep** | Admin; import `llm` after move |
| `AnalyticsService` | `service` | Analytics queries | DB | `service` | **Keep** | Admin |
| `DashboardService` | `service` | Dashboard stats | DB | `service` | **Keep** | Admin |
| `SettingsService` | `service` | Settings/API keys | DB | `service` | **Keep** | Admin |
| `ChatFeedbackService` | `service` | Chat feedback | DB | `service` | **Keep** | Application |
| Stale `service/ChatService` etc. | — | — | — | — | **N/A** | Already removed in 25C |

**Stale import scan (pre-move):** imports còn `service.LlmFallbackService` / `service.LlmGenerationOptions` tại `ChatService`, `PlaygroundService`, `PlaygroundController`, `ChatServiceSourcePresentationTest`. Không còn import `service.ChatService` / `RagTokenAudit` / … trong `Backend/src`.

---

## 7. Danh sách file đã đọc

| Path | Đọc để làm gì | Kết luận chính |
|---|---|---|
| `service/LlmFallbackService.java` | Move source | Logic giữ nguyên; chỉ đổi package |
| `service/LlmGenerationOptions.java` | Move source | Record + parsers giữ nguyên |
| `rag/runtime/ChatService.java` | Import update | Dùng LLM qua fallback service |
| `rag/runtime/PlaygroundService.java` | Import update | Compare/playground LLM options |
| `api/PlaygroundController.java` | Import update | Parse override params |
| `service/WidgetService.java` | Import update | Method ref `LlmGenerationOptions::*` |
| `agent/02-architecture.md`, `agent/03-backend.md` | Docs update | Còn ghi LLM trong `service` |
| `service/` directory listing | Stale audit | Chỉ 6 application services + không duplicate |

---

## 8. Danh sách file đã sửa

| Path | Sửa để làm gì | Layer |
|---|---|---|
| `llm/LlmFallbackService.java` | **New** (moved from service) | service/llm |
| `llm/LlmGenerationOptions.java` | **New** (moved from service) | service/llm |
| `llm/package-info.java` | **New** package doc | docs |
| `service/LlmFallbackService.java` | **Deleted** | service |
| `service/LlmGenerationOptions.java` | **Deleted** | service |
| `rag/runtime/ChatService.java` | Import `llm.*` | service |
| `rag/runtime/PlaygroundService.java` | Import `llm.*` | service |
| `api/PlaygroundController.java` | Import `llm.*` | api |
| `service/WidgetService.java` | Import `llm.LlmGenerationOptions` | service |
| `rag/runtime/ChatServiceSourcePresentationTest.java` | Import `llm.*` | test |
| `agent/02-architecture.md` | Package map + LLM row | docs |
| `agent/03-backend.md` | Package map 25D, LLM path | docs |

---

## 9. Diff thay đổi của từng file

### `llm/LlmFallbackService.java` (new)

- **Cũ:** `package KLTN.RAG_CHATBOT_BE.service;`
- **Sửa:** `package KLTN.RAG_CHATBOT_BE.llm;` — nội dung class giữ nguyên
- **Vì sao:** tách LLM adapter khỏi application `service`
- **Ảnh hưởng:** Spring `@Service` scan vẫn trong `KLTN.RAG_CHATBOT_BE.*`

```diff
-package KLTN.RAG_CHATBOT_BE.service;
+package KLTN.RAG_CHATBOT_BE.llm;
```

### `llm/LlmGenerationOptions.java` (new)

```diff
-package KLTN.RAG_CHATBOT_BE.service;
+package KLTN.RAG_CHATBOT_BE.llm;
```

### Runtime / API imports (representative)

```diff
-import KLTN.RAG_CHATBOT_BE.service.LlmFallbackService;
-import KLTN.RAG_CHATBOT_BE.service.LlmGenerationOptions;
+import KLTN.RAG_CHATBOT_BE.llm.LlmFallbackService;
+import KLTN.RAG_CHATBOT_BE.llm.LlmGenerationOptions;
```

### `service/LlmFallbackService.java`, `service/LlmGenerationOptions.java`

- **Deleted** sau khi copy sang `llm/`

---

## 10. Ảnh hưởng sau sửa

**Thay đổi:**

- FQCN: `KLTN.RAG_CHATBOT_BE.llm.LlmFallbackService`, `...llm.LlmGenerationOptions`
- Docs architecture/backend phản ánh package `llm`

**Giữ nguyên:**

- Fallback order, TPM/TPD handling, streaming callbacks, temperature/maxTokens defaults
- API/DTO contracts, chat runtime behavior, Qdrant REST, cells_json payload
- `RagTokenAudit` vẫn từ `audit.metrics`

**Điều kiện / fallback:** không đổi

**Memory/CPU/latency/token:** không đổi (chỉ package)

**MySQL/Qdrant cũ:** không đổi

---

## 11. Edge cases đã xem xét

- Spring component scan: base package `KLTN.RAG_CHATBOT_BE` bao gồm `llm`
- `WidgetService` method reference cần explicit import sau khi rời cùng package
- Không còn duplicate class cùng tên trong `service/` và `llm/`
- Test mock `LlmFallbackService` cập nhật import

---

## 12. Kết quả kiểm tra

| Command | Kết quả | Ghi chú |
|---|---|---|
| `cd Backend; .\mvnw.cmd clean test` (post-move) | **PASS** | 50 tests, 0 failures, 0 errors |
| `cd Backend; .\mvnw.cmd test` (final confirm) | **PASS** | BUILD SUCCESS |
| `docker compose config -q` | **PASS** | Exit 0 |
| Docker smoke `up` + logs | **NOT RUN** | Optional; không yêu cầu bắt buộc PASS |

### Tests per phase

| Phase | `mvn clean test` |
|---|---|
| 2 Move LLM → `llm` | PASS |
| 3 Runtime imports | (included in phase 2) PASS |
| 4 Stale duplicate cleanup | N/A — none found |
| 5 Docs + package-info | PASS (final 50) |
| 7 Final scan | No stale `service.Llm*` imports in `Backend/src` |

---

## 13. Rủi ro còn lại

- Historical docs/reports vẫn mention `service/LlmFallbackService` (read-only legacy)
- `EmbeddingServiceCacheTest` vẫn ở package test `service` (không thuộc scope 25D)
- Docker runtime smoke chưa verify startup logs

---

## 14. Đề xuất tiếp theo

- **25E:** `common.util` extraction nếu có helper thuần; hoặc relocate test fixtures từ `service` test package
- Cập nhật `.cursor/rules/10-backend-rag-rule.mdc` paths `ChatService` → `rag.runtime` (docs hygiene)
- Optional: Docker smoke backend startup

---

## Package map after 25D

```
ingest.parser | ingest.normalize | ingest.chunking
index.embedding | index.qdrant
rag.retrieve | rag.prompt | rag.runtime | rag.analysis | rag.rerank | rag.budget
audit.metrics
llm                    ← LlmFallbackService, LlmGenerationOptions
service                ← DocumentService, WidgetService, Analytics*, Dashboard*, Settings*, ChatFeedback*
```

---

## Phase 7 — Final stale import scan

| Pattern | Backend/src | Notes |
|---|---|---|
| `service.(ChatService\|…\|Llm*)` | **0 hits** | Removed / updated |
| `QdrantEmbeddingStore\|langchain4j-qdrant\|qdrant.port` in src | **0 hits** in main/test src | Legitimate: config yml may still mention ports |
| `table_row_group\|text_table_like` | Hits in `rag.runtime`, `rag.prompt`, `rag.retrieve` | **Legitimate** chunk-type behavior |

---

## Behavior changes

**None** (package/import/docs only).

---

## Final verdict

**PASS**
