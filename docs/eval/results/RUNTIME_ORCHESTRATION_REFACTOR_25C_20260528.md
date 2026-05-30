# Runtime / Orchestration Package Refactor 25C — Full Report

**Date:** 2026-05-28  
**Verdict:** **PASS**

---

## 1. Mức độ hiểu task

- **Hiểu task:** 95%
- **Chắc chắn:** package-only refactor sau 25B; test sau mỗi phase; không đổi algorithm/API/DTO/schema
- **Giả định:** `DocumentService` giữ trong `service` (Option A); `LlmFallbackService` / `LlmGenerationOptions` giữ `service` vì dùng chung widget + runtime
- **Thiếu dữ liện:** Docker smoke upload DOCX không chạy trong session

---

## 2. Tóm tắt yêu cầu

Tiếp tục tách orchestration/runtime/audit khỏi monolithic `service` sang `rag.runtime`, `rag.analysis`, `rag.rerank`, `rag.budget`, `audit.metrics`; thêm `package-info.java`; cập nhật architecture docs; `mvn clean test` sau mỗi boundary.

---

## 3. Hiện trạng trước khi sửa

Sau 25B, RAG core đã ở `ingest.*`, `index.*`, `rag.retrieve`, `rag.prompt`. Còn lại trong `service`:

- `ChatService`, `PlaygroundService`, `QueryAnalyzerService`, `QuerySignalExtractor`
- `RerankService`, `PromptBudgetResolver`
- `RagTokenAudit`, `RagLatencyTrace`
- `DocumentService` (upload lifecycle)

---

## 4. Nguyên nhân gốc (từ source)

`service` vẫn gom orchestration + audit + query analysis + rerank/budget + chat runtime — khó audit boundary và onboarding sau khi 25B đã tách ingest/index/retrieve/prompt.

---

## 5. Chiến lược sửa đã chọn

1. Inventory trước khi move  
2. Phase 2 → audit.metrics  
3. Phase 3 → rag.analysis  
4. Phase 4 → rag.rerank + rag.budget  
5. Phase 5 → rag.runtime (+ relocate tests cùng package)  
6. Phase 6 → DocumentService **Keep** trong `service` (Option A)  
7. package-info + docs + logging FQCN dev/docker  

---

## 6. Inventory (Phase 1)

| Class | Current package (before) | Responsibility | Key dependencies | Target package | Move now / later / keep | Reason |
|---|---|---|---|---|---|---|
| `RagTokenAudit` | `service` | ThreadLocal token estimate/actual, `[RAG][token-usage]` log | DTO, LangChain4j `TokenUsage` | `audit.metrics` | **Move now** | Pure audit, no Spring |
| `RagLatencyTrace` | `service` | ThreadLocal latency breakdown, `[RAG][latency]` log | None | `audit.metrics` | **Move now** | Pure audit |
| `QuerySignalExtractor` | `service` | Generic identifiers/dates/ngrams for keyword | None | `rag.analysis` | **Move now** | RAG query semantics |
| `QueryAnalyzerService` | `service` | QueryType, heading match, section repo | `DocumentSectionRepository` | `rag.analysis` | **Move now** | Retrieval/prompt caller |
| `RerankService` | `service` | Cohere rerank optional | REST, `DocumentChunk`, `RagTokenAudit` | `rag.rerank` | **Move now** | Isolated optional step |
| `PromptBudgetResolver` | `service` | Char budget by QueryType | `QueryAnalyzerService.QueryType`, `@Value` | `rag.budget` | **Move now** | Budget only |
| `ChatService` | `service` | Chat sync/stream, sources, audit hooks | retrieval, prompt, analysis, LLM, DB | `rag.runtime` | **Move now** | Chat orchestration |
| `PlaygroundService` | `service` | Playground/compare sessions | `ChatService` helpers, retrieval, LLM | `rag.runtime` | **Move now** | Playground orchestration |
| `DocumentService` | `service` | Upload → parse → chunk → embed → status | parser, chunking, embedding, qdrant, DB | `service` | **Keep** | Broad application lifecycle (Option A) |
| `LlmFallbackService` | `service` | Groq sync/stream + fallback | `RagTokenAudit` | `service` | **Keep** | Shared by runtime + widget paths |
| `LlmGenerationOptions` | `service` | Temperature/maxTokens record | None | `service` | **Keep** | API/playground DTO parsing |
| `WidgetService` | `service` | Widget CRUD, uiConfig | DB | `service` | **Keep** | Admin, not RAG runtime |
| `common.util` | — | — | — | `common.util` | **Deferred** | No pure shared helpers identified in scope |

---

## 7. Package map

### Before (post-25B, pre-25C orchestration)

```
service/  → ChatService, PlaygroundService, QueryAnalyzer*, Rerank*, PromptBudget*, RagToken*, RagLatency*, DocumentService, ...
ingest.parser|normalize|chunking, index.embedding|qdrant, rag.retrieve|prompt
```

### After (25C)

```
audit.metrics/     RagTokenAudit, RagLatencyTrace
rag.analysis/      QueryAnalyzerService, QuerySignalExtractor
rag.rerank/        RerankService
rag.budget/        PromptBudgetResolver
rag.runtime/       ChatService, PlaygroundService
service/           DocumentService, LlmFallbackService, LlmGenerationOptions, WidgetService, ...
(+ 25B packages unchanged)
```

---

## 8. Files / classes moved

| Class | From | To |
|---|---|---|
| `RagTokenAudit` | `service` | `audit.metrics` |
| `RagLatencyTrace` | `service` | `audit.metrics` |
| `QueryAnalyzerService` | `service` | `rag.analysis` |
| `QuerySignalExtractor` | `service` | `rag.analysis` |
| `RerankService` | `service` | `rag.rerank` |
| `PromptBudgetResolver` | `service` | `rag.budget` |
| `ChatService` | `service` | `rag.runtime` |
| `PlaygroundService` | `service` | `rag.runtime` |

**Tests relocated (package-private access):**

- `ChatServiceSourcePresentationTest` → `rag.runtime`
- `RetrievalTopKTest` → `rag.runtime`

**package-info.java added:** `ingest.parser`, `ingest.normalize`, `ingest.chunking`, `index.embedding`, `index.qdrant`, `rag.retrieve`, `rag.prompt`, `rag.runtime`, `rag.analysis`, `rag.rerank`, `rag.budget`, `audit.metrics`

---

## 9. Classes intentionally kept in `service`

- `DocumentService` — upload, DB, parse, chunk, embed, Qdrant purge, status
- `LlmFallbackService`, `LlmGenerationOptions` — LLM provider wiring
- `WidgetService`, `AnalyticsService`, `DashboardService`, `SettingsService`, `ChatFeedbackService` — admin/app

---

## 10. Visibility changes

- **None widened** on production classes.
- Tests moved to `rag.runtime` to retain package-private access to `ChatService` helpers (`isLeadingRefusalAnswer`, `applyAnswerAwareSourceCap`, `TopKResolution`, etc.).

---

## 11. Tests after each phase

| Phase | Command | Result | Notes |
|---|---|---|---|
| 2 audit.metrics | `mvnw.cmd clean test` | **PASS** | 50 tests |
| 3 rag.analysis | `mvnw.cmd clean test` | **PASS** | Fixed `QuerySignalExtractor` import on `ChatService` |
| 4 rag.rerank/budget | `mvnw.cmd clean test` | **PASS** | Imports already on `RagRetrievalService` |
| 5 rag.runtime | `mvnw.cmd clean test` | **PASS** | Removed duplicate `PlaygroundService`; relocated 2 test classes |
| 7 package-info | `mvnw.cmd clean test` | **PASS** | 50 tests |
| 8 docs + logging | `mvnw.cmd clean test` | **PASS** | 50 tests |
| Final | `mvnw.cmd clean test` | **PASS** | 50 tests, 0 failures, 0 errors |
| Final | `docker compose config -q` | **PASS** | repo root |

---

## 12. Ảnh hưởng sau sửa

**Thay đổi:** Java package/FQCN imports; dev/docker logging key `RerankService` → `rag.rerank.RerankService`; architecture docs.

**Giữ nguyên:** API contracts, DTOs, DB schema, Qdrant REST payload, cells_json, retrieval/rerank/budget algorithms, prompt text, chat/SSE behavior, log message formats (`[RAG][token-usage]`, `[RAG][latency]`).

**DocumentService:** vẫn `KLTN.RAG_CHATBOT_BE.service.DocumentService`.

---

## 13. Edge cases đã xem xét

- Spring component scan sub-packages — OK via tests  
- Duplicate `PlaygroundService` during partial move — removed  
- Cross-package test access — resolved by test package move, not `public` widening  
- `PromptBudgetResolver` → `QueryAnalyzerService` import after analysis move  

---

## 14. Kết quả kiểm tra (bảng tổng hợp)

| Command | Kết quả | Ghi chú |
|---|---|---|
| `cd Backend && mvnw.cmd clean test` | **PASS** | 50 tests, 0 failures |
| `docker compose config -q` | **PASS** | |
| Frontend lint/build/widget | **NOT RUN** | Out of scope |
| Docker smoke upload DOCX | **NOT RUN** | Optional phase 9 |

---

## 15. Rủi ro còn lại

- `LlmFallbackService` vẫn trong `service` — runtime phụ thuộc ngược application layer (chấp nhận tạm, có thể 25D).  
- Stale duplicate classes under `service/` từ 25B (nếu còn trên disk) — nên dọn trong safe cleanup.  
- Production smoke DOCX/Qdrant chưa verify trong session.

---

## 16. Đề xuất tiếp theo

- Task **25D**: move `LlmFallbackService` + `LlmGenerationOptions` → `rag.runtime` hoặc `llm` package nếu boundary rõ.  
- Dọn stale `service/*` copies từ 25B nếu còn.  
- Optional: `ingest.runtime` cho `DocumentService` khi upload flow ổn định.
