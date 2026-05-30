# API Guide for AI/Cursor

Short entry point for API documentation. **Do not invent endpoints** — always verify against controllers first.

---

## Read first

1. [`docs/api/API_REFERENCE_20260530.md`](../docs/api/API_REFERENCE_20260530.md) — full endpoint reference
2. [`docs/api/API_QUICKSTART_20260530.md`](../docs/api/API_QUICKSTART_20260530.md) — practical quickstart
3. [`docs/api/API_SMOKE_TESTS_20260530.md`](../docs/api/API_SMOKE_TESTS_20260530.md) — operator smoke tests
4. [`03-backend.md`](03-backend.md) — backend flows and troubleshooting

**Source code (before changing docs):**

```text
Backend/src/main/java/KLTN/RAG_CHATBOT_BE/api/
Backend/src/main/java/KLTN/RAG_CHATBOT_BE/config/SecurityConfig.java
Backend/src/main/java/KLTN/RAG_CHATBOT_BE/config/WidgetAuthFilter.java
Backend/src/main/java/KLTN/RAG_CHATBOT_BE/dto/
```

---

## Rules for AI/Cursor

- **Do not invent endpoints** — grep controllers first.
- **Do not guess DTO fields** — read DTO classes.
- **Keep widget keys secret** — never log `X-Widget-Key`, `x-api-key`, or `apiKey` in reports/commits.
- **Document delete truth:** document delete purges Qdrant by `document_id`; chatbot delete cascades documents first.
- **Admin endpoints are dev-oriented** — `permitAll` unless auth is implemented; do not claim production security.
- **Qdrant:** REST `:6333` only in application path — do not document gRPC as write path.
- **No `INDEXED` status** — use `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED`.

---

## Controller map

| Controller | Base path | Auth |
|------------|-----------|------|
| `ChatbotController` | `/api/chatbots` | None (admin — open) |
| `WidgetController` | `/api/widgets` | None (legacy create) |
| `DocumentController` | `/api/documents` | None (admin — open) |
| `ChatController` | `/api/chat` | `X-Widget-Key` on `/api/chat` and `/api/chat/stream` only |
| `PublicChatController` | `/api/public` | `x-api-key` or `X-Widget-Key` on `/api/public/chat` |
| `PlaygroundController` | `/api/playground` | None (dev/eval) |
| `AnalyticsController` | `/api/analytics` | None (admin — open) |
| `DashboardController` | `/api/dashboard` | None (admin — open) |
| `SettingsController` | `/api/settings` | None (admin — open) |

Retired chat-quality endpoint from task 28A is no longer part of the API contract.

---

## Endpoint groups

### Chatbot / Widget

```text
GET    /api/chatbots
POST   /api/chatbots
GET    /api/chatbots/{id}
PUT    /api/chatbots/{id}
DELETE /api/chatbots/{id}              ← cascade delete documents + Qdrant purge
GET    /api/chatbots/{id}/embed-config
PUT    /api/chatbots/{id}/embed-config
POST   /api/widgets                    ← legacy
```

### Documents

```text
POST   /api/documents/upload/{widgetId}   ← legacy single file
POST   /api/documents/upload              ← canonical (files + chatbotId)
GET    /api/documents
GET    /api/documents/{id}/status
GET    /api/documents/{id}/chunks
POST   /api/documents/{id}/retry
POST   /api/documents/{id}/assign         ← NOT SUPPORTED (always 400)
DELETE /api/documents/{id}                ← Qdrant purge + soft-delete
```

### Chat

```text
POST   /api/chat                          ← X-Widget-Key required
POST   /api/chat/stream                   ← X-Widget-Key required (SSE)
POST   /api/public/chat                   ← x-api-key or X-Widget-Key
```

### Playground

```text
POST   /api/playground/chat               ← SSE, chatbotId in body
GET    /api/playground/sessions?chatbotId=
DELETE /api/playground/sessions/{id}
POST   /api/playground/compare
GET    /api/playground/export/{sessionId}
```

### Analytics / Dashboard / Settings

```text
GET    /api/analytics/summary|daily|by-chatbot|unanswered|sessions
GET    /api/analytics/sessions/{id}/messages
GET    /api/dashboard/summary|message-volume|top-chatbots|activity
GET    /api/settings/profile
PUT    /api/settings/profile
GET    /api/settings/api-keys
POST   /api/settings/api-keys
DELETE /api/settings/api-keys/{id}
```

---

## Auth quick reference

| Path pattern | Filter | Header |
|--------------|--------|--------|
| `/api/chat`, `/api/chat/stream` | `WidgetAuthFilter` | `X-Widget-Key` |
| `/api/public/chat` | `WidgetAuthFilter` | `x-api-key` (preferred) or `X-Widget-Key` |
| Everything else | None in filter | — (SecurityConfig `permitAll`) |

401 response shape: `{"error": "Missing API key header."}` or `"Invalid or inactive Widget Key."`

---

## Key DTOs

| DTO | Used by |
|-----|---------|
| `ChatbotCreateRequest` | POST /api/chatbots |
| `ChatbotResponse` | chatbot CRUD (`apiKey` only on create) |
| `ChatRequest` | chat endpoints (`sessionId`, `message`, optional `topK`, `temperature`, `maxTokens`) |
| `ChatResponse` | sync chat (`answer`, `sources[]`) |
| `PublicChatResponse` | public chat (+ `sessionId`) |
| `DocumentUploadResponse` | legacy upload |
| `DocumentResponse` | canonical upload, list, retry |
| `DocumentStatusResponse` | status polling |
| `PlaygroundChatRequest` | playground chat |
| `SimpleSuccessResponse` | delete success `{ "success": true }` |

### SourceDto fields (in ChatResponse)

`fileName`, `sectionTitle`, `pages` (string, e.g. `"5"` or `"5-6"`), `chunkType`, `chunkText`

---

## Delete behavior (must document correctly)

**Document:**

```text
DELETE /api/documents/{id}
  → QdrantPurgeService (REST, filter document_id)
  → soft-delete DB children + document
  → 502 if Qdrant purge fails
```

**Chatbot:**

```text
DELETE /api/chatbots/{id}
  → WidgetService.softDeleteChatbot
  → foreach active doc: DocumentService.softDeleteDocument
  → soft-delete WidgetConfig
```

---

## Common mistakes in old docs (do not repeat)

| Stale claim | Current truth |
|-------------|---------------|
| `DELETE /api/documents/{id}` not implemented | **Implemented** |
| `GET /api/chatbots/{id}` not implemented | **Implemented** |
| Sources have `pageStart`/`pageEnd` | Use `pages` string in `SourceDto` |
| All `/api/chat/**` need widget key | **Only** `/api/chat` and `/api/chat/stream` |
| Settings API keys = widget keys | **Different** — settings keys vs `WidgetConfig.apiKey` |

---

## Eval file for manual tests

```text
docs/eval/manual/SoTayHocVu_HocKy1_2025-2026_RAG_CONTEXT_HOC_KY.docx
```

Smoke question (S4): Pháp luật Việt Nam đại cương Nhóm 1 — expect Nguyễn Thị Vân Anh, thứ 2, tiết 1-2, E301.

---

## Related

- [`../README.md`](../README.md) — project entry
- [`../agent.md`](../agent.md) — AI session entry
- [`04-runbook.md`](04-runbook.md) — operational commands
- [`06-operations.md`](06-operations.md) — data lifecycle
