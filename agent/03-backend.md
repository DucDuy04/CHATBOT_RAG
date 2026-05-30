# Backend (Spring Boot)

Backend-specific guide. Package truth: [`02-architecture.md`](02-architecture.md). Full reference: [`docs/architecture/FINAL_BACKEND_RAG_ARCHITECTURE_20260529.md`](../docs/architecture/FINAL_BACKEND_RAG_ARCHITECTURE_20260529.md).

---

## Tech stack

- Java 21, Spring Boot 3.4.4, Maven
- Spring Web, Data JPA, Security
- LangChain4j 1.0.0-beta1: `langchain4j`, `langchain4j-open-ai`, `langchain4j-nomic` — **LLM/embedding adapters only**
- **No** `langchain4j-qdrant` / `QdrantEmbeddingStore` in application code
- Apache POI 5.3 (DOCX), PDFBox 3.0.2 + Tabula 1.0.5 (PDF)
- MySQL 8.0, Qdrant REST

---

## Profiles

| Profile | File | Usage |
|---------|------|-------|
| `dev` (default) | `application-dev.yml` | Local: MySQL localhost:3306, Qdrant localhost:6333 |
| `docker` | `application-docker.yml` | Container: host `mysql`, `qdrant` |

Common settings in `application.yml`: multipart 50MB, upload dir `./uploads`, hybrid retrieval config, cleaner config.

---

## Important packages and classes

| Concern | Location |
|---------|----------|
| Upload orchestration | `service.DocumentService` |
| Chatbot cascade delete | `service.WidgetService.softDeleteChatbot` |
| Parse | `ingest.parser.DocumentParserService` |
| Table normalize | `ingest.normalize.NormalizedTableService` |
| Chunk | `ingest.chunking.ChunkingService2` |
| Embed + Qdrant I/O | `index.embedding.EmbeddingService` |
| Qdrant bootstrap/purge | `index.qdrant.QdrantConfig`, `QdrantPurgeService` |
| Retrieval | `rag.retrieve.RagRetrievalService` |
| Chat runtime | `rag.runtime.ChatService` |
| LLM | `llm.LlmFallbackService` |
| Widget auth | `config.WidgetAuthFilter` |

---

## Document upload flow

1. `POST /api/documents/upload/{widgetId}` → `DocumentController` → `DocumentService`
2. Validate type (PDF/DOCX/TXT), checksum dedup, save to `./uploads`
3. Status: `PENDING` → `PROCESSING`
4. `DocumentParserService.parse()`:
   - **DOCX:** POI logical grid → `RawTableModel` with `physicalColIndex`
   - **PDF:** PDFBox + Tabula → `RawTableModel` with coordinate overlap
   - **TXT:** direct read
5. `NormalizedTableService` → rows with `cells_json` (UTF-8 Vietnamese)
6. `ChunkingService2.processSections2()` → chunk list
7. `EmbeddingService.upsert()` — REST to Qdrant :6333, save MySQL sections/chunks
8. Status: `COMPLETED` or `FAILED`

Delete: `DELETE /api/documents/{id}` → `softDeleteDocument` → Qdrant purge by `document_id`.

---

## Retrieval flow

1. `POST /api/chat` or `/api/chat/stream` + `X-Widget-Key`
2. `WidgetAuthFilter` → `Widget-Id` attribute
3. `ChatService` → save user message
4. `RagRetrievalService.retrieve(question, widgetId)` — 7 steps (see `02-architecture.md`)
5. `PromptBuilderService.build()` → Groq via `LlmFallbackService`
6. Response + sources; optional SSE stream

Hybrid: vector search + `KeywordSearchService` + cell-aware scoring for `normalized_table_row`.

---

## Qdrant integration

- **Write/search:** `index.embedding.EmbeddingService` — Spring `RestClient` + Jackson UTF-8 JSON
- **Bootstrap:** `index.qdrant.QdrantConfig` — creates `documents` collection on startup (768, Cosine)
- **Purge:** `index.qdrant.QdrantPurgeService` — delete points filter `document_id`
- **Port:** HTTP 6333 only for application I/O; gRPC 6334 not used for writes

Invariant: for `COMPLETED` documents, DB chunk count = Qdrant point count (per document).

---

## Chatbot delete flow

```text
DELETE /api/chatbots/{id}
  → ChatbotController
  → WidgetService.softDeleteChatbot(id)
  → list active documents for widget
  → for each: DocumentService.softDeleteDocument(docId)
  → soft-delete WidgetConfig
```

Test: `WidgetServiceSoftDeleteChatbotTest`.

---

## Security notes

- `WidgetAuthFilter`: `/api/chat/**` requires valid `X-Widget-Key`
- Admin routes (`/api/documents/**`, `/api/chatbots/**`): currently `permitAll` — **not production-grade auth**
- CORS: localhost origins in `SecurityConfig`

---

## Run backend tests

```powershell
cd Backend
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
.\mvnw.cmd clean test
```

Expected: 71 tests, 0 failures. See [`05-testing.md`](05-testing.md).

---

## Common troubleshooting

### NOMIC key mismatch (Process / User / .env / Docker)

**Symptom:** Embedding fails, 401 from Nomic, or wrong key used.

**Check:**

```powershell
# Local
$env:NOMIC_API_KEY.Substring(0,8)

# Docker container
docker compose exec backend sh -lc 'echo ${NOMIC_API_KEY:0:8}'
```

Ensure same key in `.env` (compose), PowerShell session, and not overridden by stale User env var.

### Qdrant points mismatch

**Symptom:** `chunkCount` on document ≠ Qdrant points for `document_id`.

**Check:**

- Document status must be `COMPLETED`
- Query Qdrant: `POST /collections/documents/points/scroll` with filter `document_id`
- Re-upload if document was partially failed
- Never recreate collection without re-embed plan

### cells_json mojibake

**Symptom:** Vietnamese characters corrupted in retrieval / Qdrant payload.

**Cause (historical):** gRPC `QdrantEmbeddingStore` path — **removed**.

**Verify:** run `QdrantPayloadUnicodeTest`; confirm writes go through `EmbeddingService` REST only.

### Stale documents / orphan data

**Symptom:** Soft-deleted docs still have Qdrant points, or chunks without parent document.

**Fix:**

- Use `DELETE /api/documents/{id}` (not manual DB delete)
- Chatbot delete cascades via `WidgetService`
- Audit scripts in `docs/eval/results/RESIDUAL_DATA_CLEANUP_25J_20260529.md`

### Upload stuck in PROCESSING

- Check backend logs: parse/chunk/embed exceptions
- File size ≤ 50MB
- Groq/Nomic API availability

---

## Logging (dev)

```yaml
logging.level:
  KLTN.RAG_CHATBOT_BE.ingest.parser.DocumentParserService: DEBUG
  KLTN.RAG_CHATBOT_BE.ingest.chunking.ChunkingService2: DEBUG
  KLTN.RAG_CHATBOT_BE.rag.rerank.RerankService: DEBUG
```

Do not log full prompts, API keys, or long document content.
