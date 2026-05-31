# CHATBOT_RAG — Tổng quan

## Một đoạn tóm tắt

CHATBOT_RAG là hệ thống chatbot RAG cho tài liệu tiếng Việt (PDF, DOCX, TXT), tập trung vào bảng có cấu trúc và tài liệu học vụ. Backend Spring Boot parse → chuẩn hóa bảng → chunk → embed (Nomic) → lưu MySQL + Qdrant (REST); khi chat, hybrid retrieval + Groq LLM sinh câu trả lời có nguồn. Multi-tenant theo widget/chatbot — mỗi chatbot có documents và vector space riêng. Admin frontend + embeddable widget; frontend gọi backend thật only (mock mode đã gỡ).

---

## Milestone hiện tại (2026-05-30)

| Hạng mục | Trạng thái |
|----------|------------|
| Backend package refactor (25B–25E) | ✓ PASS |
| Qdrant REST UTF-8 write (24D4) | ✓ PASS |
| Normalized table ingest | ✓ PASS |
| Hybrid retrieval + cell-aware scoring | ✓ PASS |
| Chatbot cascade delete (25K) | ✓ PASS |
| Latency optimizations (27A–27F): prewarm, rerank guard, async persist | ✓ PASS |
| Dead code cleanup — feedback/mock/satisfaction (28A/28B) | ✓ PASS |
| Full project + widget E2E verify (28C) | ✓ PASS |
| Final README handoff (29A) | ✓ PASS |
| Agent docs sync (29B) | ✓ (task này) |
| Production admin auth | ✗ Chưa |
| Adaptive context-N | ✗ Chưa implement |
| Testcontainers full integration | ✗ Optional future |

---

## Removed features (28A/28B)

Không còn active trong codebase hoặc API contract:

- `POST /api/chat/feedback` → **404**
- Satisfaction/rating metrics (`avgSatisfaction`, etc.)
- `newFeedback` / `notifyNewFeedback` settings
- Frontend mock mode / `USE_MOCK_API` / `src/mocks`

DB cũ có thể còn bảng `chat_feedbacks` hoặc cột `notify_new_feedback` — optional cleanup sau backup; backend hiện tại không map.

---

## Module overview

| Module | Path | Vai trò |
|--------|------|---------|
| Backend API | `Backend/` | RAG pipeline, REST/SSE |
| Frontend UI | `Frontend/` | Admin, chat, widget embed |
| Infra | `docker-compose.yml` | MySQL, Qdrant, backend, frontend |
| Preprocess | `preprocess/` | Standalone Java experiments — **không** gọi từ Backend runtime |
| Architecture docs | `docs/architecture/` | Final + thesis docs |
| API docs | `docs/api/` | Reference, quickstart, smoke tests |
| Eval | `docs/eval/` | Manual test files, result reports |
| Cursor rules | `.cursor/rules/` | AI working constraints |

---

## Verified baseline snapshot

Từ task 28C full project verify (2026-05-30):

| Metric | Value |
|--------|-------|
| Backend unit tests | **120 PASS**, 0 failures |
| Frontend build/lint | PASS |
| Widget build | PASS |
| Widget E2E | PASS |
| Docker compose config | PASS |
| Active completed documents (eval env) | 3 |
| DB chunks = Qdrant points (per doc) | 3296 each, parity PASS |
| Chat API exact + OOS | PASS |
| Public chat API | PASS |
| Removed feedback endpoint | 404 PASS |

**Test command:** `cd Backend && .\mvnw.cmd clean test` → **120 tests, 0 failures**.

---

## Key docs

**Đọc trước khi sửa backend/RAG:**

1. [`README.md`](../README.md)
2. `docs/architecture/FINAL_BACKEND_RAG_ARCHITECTURE_20260529.md`
3. `docs/architecture/FINAL_RAG_PIPELINE_OVERVIEW_20260529.md`
4. `.cursor/rules/10-backend-rag-rule.mdc`
5. `.cursor/rules/40-db-vector-rule.mdc`

**API:**

- `docs/api/API_REFERENCE_20260530.md`
- `docs/api/API_QUICKSTART_20260530.md`
- `agent/05-api.md`

**Entry points:**

- `README.md` — human clone-and-run
- `agent.md` — AI/Cursor entry

---

## Luồng end-to-end

1. Admin tạo chatbot → upload PDF/DOCX/TXT → status `COMPLETED`.
2. User widget gửi câu hỏi + `X-Widget-Key` (hoặc public chat + `x-api-key`).
3. Backend: intent analysis → hybrid retrieval → prompt → Groq → answer + sources (SSE stream trong widget).
4. OOS question → refusal, không bịa ngoài tài liệu.

---

## What not to change casually

- Qdrant write path (REST only via `index.embedding.EmbeddingService`)
- `cells_json` payload schema và UTF-8 encoding
- Chunk types emitted on ingest (`normalized_table_row`, `table_summary` — not legacy types)
- Package locations của RAG core (không move về `service.*`)
- Vector size (768) / collection name (`documents`) without re-embed plan
- Chatbot delete cascade order (documents first, then chatbot soft-delete)
- Docker service names used in `application-docker.yml` (`mysql`, `qdrant`)
