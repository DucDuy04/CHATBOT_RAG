# Agent Entry Point — CHATBOT_RAG

Concise entry point cho AI/Cursor sessions. Human onboarding: [`README.md`](README.md).

---

## Current verified baseline (28C / 29A, 2026-05-30)

| Check | Result |
|-------|--------|
| Backend tests (`mvn clean test`) | **120 PASS**, 0 failures |
| Frontend build | **PASS** |
| Frontend lint | **PASS** |
| Widget build (`npm run build:widget`) | **PASS** |
| Docker compose config | **PASS** |
| Widget E2E | **PASS** |
| DB/Qdrant parity (active COMPLETED docs) | **PASS** — chunk count = point count per document |

Default unit tests **không** cần live MySQL/Qdrant/API keys.

**Removed (không còn active):** feedback endpoint, satisfaction/rating metrics, `newFeedback` notification, frontend mock mode, `USE_MOCK_API`. Frontend clients gọi backend thật only.

---

## Read first

1. [`README.md`](README.md) — clone-and-run handoff
2. [`docs/architecture/FINAL_BACKEND_RAG_ARCHITECTURE_20260529.md`](docs/architecture/FINAL_BACKEND_RAG_ARCHITECTURE_20260529.md)
3. [`docs/architecture/FINAL_RAG_PIPELINE_OVERVIEW_20260529.md`](docs/architecture/FINAL_RAG_PIPELINE_OVERVIEW_20260529.md)
4. [`docs/api/API_REFERENCE_20260530.md`](docs/api/API_REFERENCE_20260530.md)
5. [`agent/04-runbook.md`](agent/04-runbook.md)
6. [`agent/05-api.md`](agent/05-api.md)
7. [`agent/06-operations.md`](agent/06-operations.md)

Cursor rules: `.cursor/rules/00-core-working-rule.mdc`, `10-backend-rag-rule.mdc`, `40-db-vector-rule.mdc`.

---

## Agent doc index

| File | Nội dung |
|------|----------|
| [`agent/01-overview.md`](agent/01-overview.md) | Tổng quan, milestone, baseline |
| [`agent/02-architecture.md`](agent/02-architecture.md) | Kiến trúc truth — package map, pipelines |
| [`agent/03-backend.md`](agent/03-backend.md) | Backend chi tiết, troubleshooting |
| [`agent/04-runbook.md`](agent/04-runbook.md) | Lệnh vận hành |
| [`agent/05-api.md`](agent/05-api.md) | API reference nhanh |
| [`agent/05-testing.md`](agent/05-testing.md) | Test suite, baseline |
| [`agent/06-operations.md`](agent/06-operations.md) | Data lifecycle, delete, Qdrant parity |
| [`agent/04-frontend.md`](agent/04-frontend.md) | Frontend + widget (legacy index) |

---

## Non-negotiable invariants

| Invariant | Chi tiết |
|-----------|----------|
| No Qdrant gRPC write/search path | Upsert/search chỉ REST `:6333` qua `EmbeddingService` |
| No LangChain4j QdrantEmbeddingStore | Không dùng cho write/search |
| No frontend mock mode / USE_MOCK_API | Development bắt buộc backend thật |
| No feedback/satisfaction/rating/newFeedback | Endpoint và metrics đã gỡ (28A/28B) |
| No DOCX Markdown bridge as primary | Structured tables → `RawTableModel`; DOCX `physicalColIndex`, PDF x-overlap |
| No `table_row_group` / `text_table_like` on new ingest | Legacy read-compatible only |
| `cells_json` UTF-8 | Vietnamese Unicode phải readable sau Qdrant upsert |
| DB chunks = Qdrant points | Cho documents `COMPLETED` |
| Chatbot delete cascades documents | Purge Qdrant by `document_id`, soft-delete DB rows |
| No adaptive context-N | Budget cố định theo query type — chưa implement dynamic topK |
| Do not cache final answers | Mỗi request retrieval + LLM fresh (trừ embedding/query cache nội bộ) |

---

## Current architecture summary

```text
Frontend / Admin / Widget
        ↓ REST / SSE
   Spring Boot API (api, service)
   ├── ingest.parser / normalize / chunking
   ├── index.embedding (Qdrant REST) / index.qdrant (bootstrap/purge)
   ├── rag.retrieve / prompt / analysis / rerank / budget / runtime
   ├── llm (Groq adapter)
   └── audit.metrics
        ↓                    ↓
     MySQL              Qdrant (:6333 REST)
        ↓
   Nomic + Groq (+ Cohere optional)
```

RAG core **không** nằm trong `service.*` (moved 25B–25D). Current chunk types: `text`, `section_summary`, `parent_section_summary`, `table_summary`, `normalized_table_row`.

---

## Current optimization status

| Optimization | Status | Config path |
|--------------|--------|-------------|
| Startup keyword index prewarm | **PASS** (27B verified) | `rag.retrieval.keyword-index.prewarm-*` |
| Rerank guard | **PASS** (27C verified) | `rag.retrieval.rerank-guard.*` |
| Async assistant message persistence | **PASS** (27F verified) | `rag.runtime.async-persist.*` |
| Query variant dedupe | **PARTIAL** — safe, low impact on current V1–V5 workload | `rag.retrieval.query-variant-dedupe.*` |

---

## Validation commands

```powershell
cd Backend
.\mvnw.cmd clean test          # Expected: 120 tests, 0 failures

cd ..\Frontend
npm run build
npm run lint
npm run build:widget

cd ..
docker compose config -q
```

Widget E2E evidence: [`docs/eval/results/FULL_PROJECT_WIDGET_E2E_VERIFY_28C_20260530.md`](docs/eval/results/FULL_PROJECT_WIDGET_E2E_VERIFY_28C_20260530.md).

---

## Do not touch casually

- Parser / normalizer logic (`ingest.parser`, `ingest.normalize`)
- Qdrant payload shape và `cells_json` encoding
- Retrieval semantics (`rag.retrieve`) without eval baseline
- Vector size (768) / collection name (`documents`) without re-embed plan
- Chatbot delete cascade order
- Docker service names in `application-docker.yml` (`mysql`, `qdrant`)

---

## Important warnings

- **Không** `docker compose down -v` cho cleanup thường.
- **Không** log full API keys, prompts, hoặc document content dài.
- **Không** claim production auth — admin endpoints vẫn `permitAll` (trừ widget chat qua `X-Widget-Key` / `x-api-key`).
- Sau mỗi task sửa code: tạo report theo `.cursor/rules/90-report-verification-rule.mdc`.
