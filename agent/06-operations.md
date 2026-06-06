# Operations — Data lifecycle và Qdrant

Vận hành dữ liệu, delete behavior, và parity checks. Commands: [`04-runbook.md`](04-runbook.md).

---

## Data lifecycle

```text
Upload → PENDING → PROCESSING → COMPLETED | FAILED
                ↓
         MySQL: document, sections, chunks, tables
         Qdrant: vectors + payload (per chunk)
                ↓
Delete → soft-delete DB rows (deleted_at)
         Qdrant purge by document_id
```

- Soft-deleted rows remain in MySQL until optional hard purge.
- Qdrant points removed on document delete — not on chatbot soft-delete alone (documents purged first in cascade).

**Removed (28A/28B):** feedback collection, satisfaction metrics, frontend mock mode — not part of active operations.

---

## Document deletion

**Endpoint:** `DELETE /api/documents/{id}`

**Flow (`DocumentService.softDeleteDocument`):**

1. Load active document by id
2. `QdrantPurgeService.purgeByDocumentId(documentId)` — REST delete with filter
3. Soft-delete: `document_tables`, `document_sections`, `document_chunks`, `document`

**Rules:**

- Purge always filters by `document_id` — never wipe collection
- If Qdrant purge fails → `502 Bad Gateway`, document not soft-deleted
- Idempotent for already-deleted documents → 404

---

## Chatbot cascade deletion

**Endpoint:** `DELETE /api/chatbots/{id}`

**Flow (`WidgetService.softDeleteChatbot`):**

1. Find active `WidgetConfig` by id
2. List all active documents for widget
3. For each document: `DocumentService.softDeleteDocument(doc.getId())`
4. Soft-delete `WidgetConfig`

**Implications:**

- Sequential purge — many large documents may take time
- Ensures no orphan Qdrant points when chatbot removed
- Test: `WidgetServiceSoftDeleteChatbotTest`

---

## Qdrant purge rules

| Rule | Detail |
|------|--------|
| Write path | REST HTTP **6333** via `index.embedding.EmbeddingService` |
| Bootstrap | `index.qdrant.QdrantConfig` on startup |
| Purge scope | Filter `document_id` only |
| Collection | `documents` — do **not** drop for routine cleanup |
| gRPC port 6334 | May exist in Docker — **not** used by backend write/search |
| UTF-8 | `cells_json`, text fields must preserve Vietnamese Unicode |

**Do not use:**

- LangChain4j `QdrantEmbeddingStore`
- gRPC upsert in application code
- Collection recreate without full re-embed

---

## Runtime optimizations

| Feature | Config | Notes |
|---------|--------|-------|
| Startup keyword index prewarm | `rag.retrieval.keyword-index.prewarm-*` | Warms keyword cache after restart; reduces first-chat latency |
| Rerank guard | `rag.retrieval.rerank-guard.*` | Skips Cohere rerank when cheap pre-score already decisive |
| Async assistant persist | `rag.runtime.async-persist.*` | Assistant message saved async — **eventual consistency** if JVM dies immediately after response |
| Query variant dedupe | `rag.retrieval.query-variant-dedupe.*` | PARTIAL impact on current workload; safe to keep enabled |

---

## DB / Qdrant parity checks

For each `COMPLETED` document:

```text
document.chunkCount == COUNT(qdrant points where document_id = doc.id)
```

Baseline snapshot (28C verify):

| Metric | Value |
|--------|-------|
| Backend unit tests | 120 PASS |
| Widget E2E | PASS |
| Active completed documents (eval env) | 3 |
| DB chunks per doc | 3296 each |
| Qdrant points per doc | 3296 each (parity PASS) |
| Removed feedback endpoint | 404 PASS |

Manual verification (Qdrant REST):

```powershell
curl -X POST "http://localhost:6333/collections/documents/points/count" `
  -H "Content-Type: application/json" `
  -d '{"filter":{"must":[{"key":"document_id","match":{"value":"<UUID>"}}]}}'
```

Compare with `GET /api/documents/{id}` → `chunkCount`.

---

## Stale document cleanup guidance

**Symptoms:**

- Document soft-deleted in DB but Qdrant points remain
- `chunkCount` mismatch after failed ingest
- Orphan chunks referencing deleted document

**Safe cleanup:**

1. Identify document UUID
2. `DELETE /api/documents/{id}` if still active
3. If already soft-deleted: use Qdrant purge by `document_id` via `QdrantPurgeService` pattern (admin/script) — do not drop collection
4. Re-upload document if re-index needed

**Audit references:**

- `docs/eval/results/RESIDUAL_DATA_CLEANUP_25J_20260529.md`
- `docs/eval/results/CHATBOT_DELETE_CASCADE_25K_20260529.md`

---

## Environment variables

See [`04-runbook.md`](04-runbook.md) and README §5.

Required: `GROQ_API_KEY`, `NOMIC_API_KEY`

Optional: `COHERE_API_KEY`, `COHERE_RERANK_ENABLED=false`

---

## Historical DB cleanup (feedback — removed)

Backend 28A+ does not map `chat_feedbacks` or `settings_profiles.notify_new_feedback`. If old DB still has them:

```sql
SHOW TABLES LIKE 'chat_feedbacks';
SHOW COLUMNS FROM settings_profiles LIKE 'notify_new_feedback';
```

Optional drop after backup — not required for current runtime.

---

## Destructive operations — warning

**Never for routine maintenance:**

```text
docker compose down -v
DROP COLLECTION documents
TRUNCATE document_chunks
DELETE points without document_id filter
```

Only for intentional full environment reset with backup.

---

## Production readiness gaps

1. Admin auth on `/api/documents/**`, `/api/chatbots/**`
2. CORS for production widget origins
3. Flyway/Liquibase instead of `ddl-auto=update`
4. Rate limiting per widget key
5. Monitoring / request correlation
6. Optional Testcontainers CI for live DB+Qdrant

---

## Build verification

```powershell
cd Backend && .\mvnw.cmd clean test    # Expected: 120 tests, 0 failures
docker compose config -q
cd Frontend && npm run lint && npm run build && npm run build:widget
```

Frontend env: `VITE_API_URL=http://localhost:8080` (or empty for Vite dev proxy).

Widget E2E baseline: `docs/eval/results/FULL_PROJECT_WIDGET_E2E_VERIFY_28C_20260530.md`.

---

## Roadmap (non-blocking)

- Adaptive context-N / dynamic topK
- Parallel Qdrant purge on chatbot delete
- Hard purge job for old soft-deleted rows
- Full integration test suite with Testcontainers

Chi tiết API:

- [`05-api.md`](05-api.md) — AI/Cursor API guide
- [`../docs/api/API_REFERENCE_20260530.md`](../docs/api/API_REFERENCE_20260530.md) — full REST reference
- [`../docs/api/API_QUICKSTART_20260530.md`](../docs/api/API_QUICKSTART_20260530.md) — quickstart
- [`../docs/api/API_SMOKE_TESTS_20260530.md`](../docs/api/API_SMOKE_TESTS_20260530.md) — smoke tests

Frontend ops: [`04-frontend.md`](04-frontend.md).
