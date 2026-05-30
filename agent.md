# AGENT DOCS — CHATBOT_RAG

Entry point cho AI/Cursor sessions và developer onboarding. Đọc file này trước khi sửa code hoặc docs.

---

## Current project state

- **Backend:** Spring Boot 3.4.4 / Java 21 — ổn định sau refactor 25B–25K.
- **Test baseline:** `mvn clean test` → **71 tests, 0 failures, 0 errors** (default tests không cần live MySQL/Qdrant/API keys).
- **Data baseline (eval snapshot 25I–25K):** 5 active chatbots, 3 completed documents, 9888 DB chunks = 9888 Qdrant points, 0 orphans.
- **Qdrant write path:** REST HTTP 6333 qua `index.embedding.EmbeddingService` — **không** gRPC, **không** `QdrantEmbeddingStore`.
- **Chatbot delete:** cascade active documents qua `DocumentService.softDeleteDocument` trước khi soft-delete chatbot.

---

## Read first

1. [`docs/architecture/FINAL_BACKEND_RAG_ARCHITECTURE_20260529.md`](docs/architecture/FINAL_BACKEND_RAG_ARCHITECTURE_20260529.md)
2. [`docs/architecture/FINAL_RAG_PIPELINE_OVERVIEW_20260529.md`](docs/architecture/FINAL_RAG_PIPELINE_OVERVIEW_20260529.md)
3. [`.cursor/rules/10-backend-rag-rule.mdc`](.cursor/rules/10-backend-rag-rule.mdc)
4. [`.cursor/rules/40-db-vector-rule.mdc`](.cursor/rules/40-db-vector-rule.mdc)

Thesis handoff (optional):

- [`docs/architecture/THESIS_ARCHITECTURE_SUMMARY_20260529.md`](docs/architecture/THESIS_ARCHITECTURE_SUMMARY_20260529.md)

API docs:

- [`docs/api/API_REFERENCE_20260530.md`](docs/api/API_REFERENCE_20260530.md)
- [`docs/api/API_QUICKSTART_20260530.md`](docs/api/API_QUICKSTART_20260530.md)
- [`agent/05-api.md`](agent/05-api.md)

---

## Agent doc index

| File | Nội dung |
|------|----------|
| [`agent/01-overview.md`](agent/01-overview.md) | Tổng quan, milestone, baseline |
| [`agent/02-architecture.md`](agent/02-architecture.md) | Kiến trúc truth — package map, pipelines |
| [`agent/03-backend.md`](agent/03-backend.md) | Backend chi tiết, troubleshooting |
| [`agent/04-runbook.md`](agent/04-runbook.md) | Lệnh vận hành |
| [`agent/05-testing.md`](agent/05-testing.md) | Test suite, baseline |
| [`agent/06-operations.md`](agent/06-operations.md) | Data lifecycle, delete, Qdrant parity |
| [`agent/04-frontend.md`](agent/04-frontend.md) | Frontend + widget (legacy index) |
| [`agent/05-api.md`](agent/05-api.md) | API reference nhanh |

Human entry: [`README.md`](README.md)

---

## Canonical package map

```text
api
service                    ← upload lifecycle, widget/admin only
ingest.parser              ← DocumentParserService, RawTableModel
ingest.normalize           ← NormalizedTableService
ingest.chunking            ← ChunkingService2
index.embedding            ← EmbeddingService (Nomic + Qdrant REST)
index.qdrant               ← QdrantConfig, QdrantPurgeService
rag.retrieve               ← RagRetrievalService, KeywordSearchService
rag.prompt                 ← PromptBuilderService
rag.analysis               ← QueryAnalyzerService, QuerySignalExtractor
rag.rerank                 ← RerankService
rag.budget                 ← PromptBudgetResolver
rag.runtime                ← ChatService, PlaygroundService
audit.metrics              ← RagTokenAudit, RagLatencyTrace
llm                        ← LlmFallbackService, LlmGenerationOptions
domain                     ← entities + repositories
```

**Không** tìm RAG core trong `service.ChatService`, `service.EmbeddingService`, `service.RagRetrievalService`, `service.DocumentParserService`, `service.LlmFallbackService` — đã chuyển package (25B–25D).

---

## Non-negotiable invariants

| Invariant | Chi tiết |
|-----------|----------|
| No Qdrant gRPC write path | Upsert/search chỉ REST `:6333` qua `EmbeddingService` |
| No LangChain4j QdrantEmbeddingStore | Không dùng cho write/search |
| No DOCX Markdown bridge as primary | Structured tables → `RawTableModel`; DOCX `physicalColIndex`, PDF x-overlap |
| No `table_row_group` / `text_table_like` on new ingest | Legacy read-compatible only |
| `cells_json` UTF-8 | Vietnamese Unicode phải readable sau Qdrant upsert |
| DB chunks = Qdrant points | Cho documents `COMPLETED` |
| Chatbot delete cascades documents | `WidgetService.softDeleteChatbot` → `DocumentService.softDeleteDocument` |
| No adaptive context-N | Budget cố định theo query type — chưa implement dynamic topK |

---

## How to run tests

```powershell
cd Backend
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
.\mvnw.cmd clean test
```

Expected: **71 tests, 0 failures, 0 errors**.

```powershell
docker compose config -q
```

---

## Important warnings

- **Không** drop Qdrant collection / `docker compose down -v` cho cleanup thường.
- **Không** log full API keys, prompts, hoặc document content dài.
- **Không** claim production auth nếu endpoint vẫn `permitAll` (trừ widget chat qua `X-Widget-Key`).
- **Không** sửa Qdrant payload shape / DB schema / ingest logic ngoài scope task.
- Admin endpoints (`/api/documents/**`, `/api/chatbots/**`) chưa hardening — ghi rõ trong report nếu deploy production.
- Sau mỗi task sửa code: tạo report trong `docs/<TEN_REPORT>.md` theo rule 90.

---

## Cursor rules

`.cursor/rules/` — `00-core-working-rule.mdc` và `90-report-verification-rule.mdc` có `alwaysApply: true`.
