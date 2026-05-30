# CHATBOT_RAG — Tổng quan

## Một đoạn tóm tắt

CHATBOT_RAG là hệ thống chatbot RAG cho tài liệu tiếng Việt (PDF, DOCX, TXT), tập trung vào bảng có cấu trúc và tài liệu học vụ. Backend Spring Boot parse → chuẩn hóa bảng → chunk → embed (Nomic) → lưu MySQL + Qdrant (REST); khi chat, hybrid retrieval + Groq LLM sinh câu trả lời có nguồn. Multi-tenant theo widget/chatbot — mỗi chatbot có documents và vector space riêng.

---

## Milestone hiện tại (2026-05-30)

| Hạng mục | Trạng thái |
|----------|------------|
| Backend package refactor (25B–25E) | ✓ PASS |
| Qdrant REST UTF-8 write (24D4) | ✓ PASS |
| Normalized table ingest | ✓ PASS |
| Hybrid retrieval + cell-aware scoring | ✓ PASS |
| Chatbot cascade delete (25K) | ✓ PASS |
| Residual data cleanup baseline (25J) | ✓ PASS |
| Final architecture docs (26A) | ✓ PASS |
| Thesis architecture docs (26B) | ✓ PASS |
| README + agent docs (26C) | ✓ (task này) |
| Production admin auth | ✗ Chưa |
| Adaptive context-N | ✗ Chưa implement |
| Testcontainers full integration | ✗ Optional future |

---

## Module overview

| Module | Path | Vai trò |
|--------|------|---------|
| Backend API | `Backend/` | RAG pipeline, REST/SSE |
| Frontend UI | `Frontend/` | Admin, chat, widget embed |
| Infra | `docker-compose.yml` | MySQL, Qdrant, backend, frontend |
| Preprocess | `preprocess/` | Standalone Java experiments — **không** gọi từ Backend runtime |
| Architecture docs | `docs/architecture/` | Final + thesis docs |
| Eval | `docs/eval/` | Manual test files, result reports |
| Cursor rules | `.cursor/rules/` | AI working constraints |

---

## Verified baseline snapshot

Từ eval tasks 25I–25K (không re-query mỗi doc session):

| Metric | Value |
|--------|-------|
| Active chatbots | 5 |
| Active completed documents | 3 |
| Active DB chunks | 9888 |
| Qdrant points | 9888 |
| Orphan chunks | 0 |
| Stray Qdrant points | 0 |

**Test baseline:** 71 tests, 0 failures, 0 errors (`mvn clean test`).

---

## Key docs

**Đọc trước khi sửa backend/RAG:**

1. `docs/architecture/FINAL_BACKEND_RAG_ARCHITECTURE_20260529.md`
2. `docs/architecture/FINAL_RAG_PIPELINE_OVERVIEW_20260529.md`
3. `.cursor/rules/10-backend-rag-rule.mdc`
4. `.cursor/rules/40-db-vector-rule.mdc`

**Thesis / handoff:**

- `docs/architecture/THESIS_ARCHITECTURE_SUMMARY_20260529.md`
- `docs/architecture/THESIS_RAG_PIPELINE_DESCRIPTION_20260529.md`
- `docs/architecture/THESIS_ARCHITECTURE_OUTLINE_20260529.md`

**Entry points:**

- `README.md` — human onboarding
- `agent.md` — AI/Cursor entry

---

## Luồng end-to-end

1. Admin tạo chatbot → upload PDF/DOCX/TXT → status `COMPLETED`.
2. User widget gửi câu hỏi + `X-Widget-Key`.
3. Backend: intent analysis → hybrid retrieval → prompt → Groq → answer + sources.

---

## What not to change casually

- Qdrant write path (REST only via `index.embedding.EmbeddingService`)
- `cells_json` payload schema và UTF-8 encoding
- Chunk types emitted on ingest (`normalized_table_row`, not legacy types)
- Package locations của RAG core (không move về `service.*`)
- Vector size (768) / collection name (`documents`) without re-embed plan
- Chatbot delete cascade order (documents first, then chatbot soft-delete)
- Docker service names used in `application-docker.yml` (`mysql`, `qdrant`)
