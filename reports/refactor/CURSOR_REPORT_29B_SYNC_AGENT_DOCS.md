# CURSOR REPORT 29B — Sync Agent Docs with Final README Baseline

**Date:** 2026-05-30  
**Task:** 29B — Sync Agent Docs  
**Mode:** Documentation only (no production code changes)

---

## Final verdict: **PASS**

`agent.md` and all targeted `agent/*.md` files synced with README 29A baseline. Stale 71-test baseline removed. Removed features documented as inactive only. Validation commands PASS.

---

## 1. Mức độ hiểu task

| Mục | Nội dung |
|-----|----------|
| Hiểu task | **99%** |
| Chắc chắn | Docs-only; sync baseline 120 tests; align with 28C E2E; no feedback/mock/satisfaction as active |
| Giả định | `agent/05-testing.md` included in sync (referenced by agent index, stale 71 baseline) |
| Thiếu dữ liện | Không |

---

## 2. Tóm tắt yêu cầu

Sau 29A, cập nhật `agent.md` và `agent/*.md` để AI/Cursor sessions đọc cùng baseline với README: 120 tests, widget E2E PASS, removed feedback/mock, REST Qdrant path, optimizations 27A–27F, async-persist config path đúng.

---

## 3. Hiện trạng trước khi sửa

| File | Stale content |
|------|---------------|
| `agent.md` | 71 tests baseline; thiếu optimization status, widget E2E, removed features section |
| `agent/01-overview.md` | 71 tests; milestone dừng ở 26C |
| `agent/02-architecture.md` | Thiếu optimizations + removed features |
| `agent/03-backend.md` | 71 tests; thiếu async-persist config path; thiếu widget/feedback troubleshooting |
| `agent/04-runbook.md` | 71 tests; docker up command thiếu `--build`; thiếu DB cleanup SQL |
| `agent/05-api.md` | Thiếu explicit feedback 404 + no satisfaction fields |
| `agent/06-operations.md` | Baseline 25J; thiếu optimizations, async persist risk, feedback cleanup |
| `agent/05-testing.md` | 71 tests (not in task list but referenced) |

No active feedback/mock/USE_MOCK_API guidance found in agent docs before fix — only stale 71 baseline and missing 27–29 milestones.

---

## 4. Phase 1 — Stale scan classification

| Match | Classification | Action |
|-------|----------------|--------|
| `71 tests` in agent.md, 01, 03, 04, 05-testing | **UPDATE_STALE_BASELINE** | → 120 tests |
| `gRPC` / `QdrantEmbeddingStore` in 02, 03, 06 | **KEEP_LEGACY_READ_COMPAT_NOTE** | Kept as not-used |
| `table_row_group` / `text_table_like` in 02 | **KEEP_LEGACY_READ_COMPAT_NOTE** | Kept |
| `feedback` / `USE_MOCK_API` after update | **KEEP_HISTORICAL_REMOVED_NOTE** | Documented as removed |
| `POST /api/chat/feedback` in 05-api | **KEEP_HISTORICAL_REMOVED_NOTE** | Documented as 404 |
| `mock/in-memory` in 05-testing | **KEEP_FALSE_POSITIVE** | Unit test mocks, not USE_MOCK_API |

---

## 5. Files updated

| Path | Changes |
|------|---------|
| `agent.md` | Rewritten as concise AI entry: baseline 120, invariants, optimizations, validation |
| `agent/01-overview.md` | Milestones 27–29, removed features, widget E2E, 120 tests |
| `agent/02-architecture.md` | Runtime optimizations section, removed features note |
| `agent/03-backend.md` | 120 tests, `rag.runtime.async-persist` config, troubleshooting (env scope, widget key, feedback DB) |
| `agent/04-runbook.md` | Docker commands match README, frontend/widget commands, DB cleanup SQL, 120 tests |
| `agent/05-api.md` | Removed endpoints section, no satisfaction fields, stale claims table extended |
| `agent/06-operations.md` | Optimizations, async persist risk, 28C baseline, feedback DB cleanup, build:widget |
| `agent/05-testing.md` | 120 tests baseline (referenced by agent index) |
| `reports/refactor/CURSOR_REPORT_29B_SYNC_AGENT_DOCS.md` | This report |

**Production code:** không sửa.

---

## 6. Current baseline documented

| Metric | Value |
|--------|-------|
| Backend tests | 120 PASS, 0 failures |
| Frontend build/lint | PASS |
| Widget build | PASS |
| Docker compose config | PASS |
| Widget E2E | PASS |
| DB/Qdrant parity | PASS (28C) |
| Feedback endpoint | 404 (removed) |

---

## 7. Removed features documented correctly

Documented as **removed/inactive** in agent.md, 01-overview, 02-architecture, 05-api, 06-operations:

- `POST /api/chat/feedback`
- Satisfaction/rating metrics (`avgSatisfaction`, etc.)
- `newFeedback` / `notifyNewFeedback`
- Frontend mock mode / `USE_MOCK_API` / `src/mocks`

No active guidance to use these features.

---

## 8. Key corrections

| Item | Before | After |
|------|--------|-------|
| Test baseline | 71 | **120** |
| Async persist config | Not documented / risk of wrong path | `rag.runtime.async-persist.enabled: true` |
| Query variant dedupe | Not in agent docs | PARTIAL / low impact |
| Widget E2E | Not in agent docs | PASS (28C link) |
| Docker up backend | `up -d mysql qdrant backend` | `up -d mysql qdrant` then `up --build -d backend` |

---

## 9. Validation results

| Command | Result | Notes |
|---------|--------|-------|
| `docker compose config -q` | **PASS** | exit 0 |
| `cd Backend && .\mvnw.cmd clean test` | **PASS** | 120 tests, 0 failures |
| `cd Frontend && npm run build` | **PASS** | |
| `cd Frontend && npm run lint` | **PASS** | |
| `cd Frontend && npm run build:widget` | **PASS** | IIFE + sync |
| Stale scan `71 tests\|USE_MOCK_API\|avgSatisfaction` in agent | **PASS** | No active stale guidance |
| Stale scan `POST /api/chat/feedback` | **PASS** | Only as removed/404 |

---

## 10. Remaining docs risks

| Risk | Severity |
|------|----------|
| `agent/04-frontend.md` not updated (legacy index) | Low — no stale 71/mock found on grep |
| `agent.md` in root vs `agent/` folder naming | None — intentional |
| Physical DB may still have `chat_feedbacks` table | Low — documented as optional cleanup |
| Node.js version not pinned | Low — same as README |

---

## 11. Next recommended task

1. **Optional 29C:** Sync `agent/04-frontend.md` with widget embed details from README §12.
2. **Task 30:** Physical DB migration — drop legacy feedback tables after backup.
3. **Production:** Admin route auth hardening before public deploy.

---

## Report summary (task format)

- **Verdict:** PASS
- **Files updated:** 8 agent docs + this report
- **Stale fixed:** 71-test baseline (5 files), missing optimizations, missing removed-features notes, wrong/missing async-persist path
- **Validation:** All PASS
- **Production code changed:** No
