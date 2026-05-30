# CURSOR_REPORT_26C — README and Agent Docs Standardization

**Date:** 2026-05-30  
**Verdict:** **PASS**

## Summary

Standardized project README and agent docs for thesis handoff, developer onboarding, and future AI/Cursor sessions. **No production code changed.** Validation: `mvn clean test` PASS; `docker compose config -q` PASS; stale guidance scan PASS (legacy paths only in "do not use" context).

## Files updated

| Path | Action | Purpose |
|------|--------|---------|
| `README.md` | Rewritten | Main human entry point — 12 sections |
| `agent.md` | Rewritten | AI/Cursor entry — invariants, read-first, package map |
| `agent/01-overview.md` | Updated | Milestone, baseline snapshot, key docs |
| `agent/02-architecture.md` | Updated | Canonical architecture truth, pipelines, delete cascade |
| `agent/03-backend.md` | Updated | Backend guide + troubleshooting |
| `agent/04-runbook.md` | **Created** | Operational commands |
| `agent/05-testing.md` | **Created** | Test baseline + important test groups |
| `agent/06-operations.md` | Updated | Data lifecycle, Qdrant purge, parity checks |
| `docs/26C_README_AGENT_DOCS_STANDARDIZATION.md` | Created | Full verification report (rule 90) |
| `reports/refactor/CURSOR_REPORT_26C_README_AGENT_DOCS.md` | Created | This report |

**Not modified:** `agent/04-frontend.md`, `agent/05-api.md`, existing `docs/architecture/*` files.

## Stale guidance fixed

| Before | After |
|--------|-------|
| Root `README.md` was Cursor rules copy-paste, not project docs | Full project README with architecture, run, test, delete |
| `agent.md` had outdated "Chưa implement DELETE" and "Test tự động Chưa đủ" | DELETE cascade documented; 71-test baseline documented |
| `agent.md` progress snapshot dated 2026-05-05 | Updated to 25B–25K / 26A–26C milestone |
| No `agent/04-runbook.md` or `agent/05-testing.md` | Created per task spec |
| `agent/06-operations.md` lacked delete cascade / Qdrant REST clarity | Expanded data lifecycle + parity + port 6333 vs 6334 |

## Package map summary

Canonical packages documented in README §3, `agent.md`, `agent/02-architecture.md`:

`api`, `service`, `ingest.parser`, `ingest.normalize`, `ingest.chunking`, `index.embedding`, `index.qdrant`, `rag.retrieve`, `rag.prompt`, `rag.runtime`, `rag.analysis`, `rag.rerank`, `rag.budget`, `audit.metrics`, `llm`, `domain`

Stale `service.ChatService` / `service.EmbeddingService` etc. listed only as **do not use**.

## README sections added

1. Project overview  
2. Main features  
3. Architecture summary + package map  
4. Technology stack  
5. Environment variables / secrets  
6. How to run with Docker  
7. How to run backend locally  
8. How to ingest a document  
9. How to test (71 tests)  
10. Data cleanup / delete behavior  
11. Known limitations  
12. Documentation links  

## Agent docs added/updated

- **01-overview:** milestone table, baseline snapshot, what-not-to-change  
- **02-architecture:** ingest/retrieval/delete diagrams, chunk types, REST Qdrant truth  
- **03-backend:** profiles, flows, troubleshooting (NOMIC key, mojibake, stale docs)  
- **04-runbook:** start stack, logs, tests, destructive-op warnings  
- **05-testing:** 71-test baseline, test class table, integration status  
- **06-operations:** cascade delete, Qdrant purge rules, parity checks  

## Validation commands and result

| Command | Result | Notes |
|---------|--------|-------|
| `cd Backend; .\mvnw.cmd clean test` | **PASS** | Exit 0; baseline 71 tests |
| `docker compose config -q` | **PASS** | Exit 0 |
| Stale scan (Grep on README, agent, .cursor, docs/architecture) | **PASS** | Legacy refs only in "do not use" sections |

Frontend lint/build: **NOT RUN** (docs-only task, no frontend changes).

## Production code changed

**None.**

## Remaining documentation risks

- `agent/04-frontend.md` and `agent/05-api.md` not refreshed in 26C — may lag backend API additions (chatbots, analytics, playground).
- Runtime data counts (5 bots, 9888 chunks) cited from 25I–25K snapshot, not re-queried.
- `application-dev.yml` Groq primary model differs from some older agent prose (`llama-4-scout` vs `llama-3.3-70b`) — docs describe fallback chain generically.
- Old eval reports in `docs/eval/results/` still mention historical gRPC path — acceptable as historical audit trail.

## Next recommended task

1. Optional: refresh `agent/05-api.md` against current controllers (`ChatbotController`, `AnalyticsController`, `PlaygroundController`).
2. Optional: add `agent/04-frontend.md` cross-link note pointing to README frontend stack section.
3. Thesis: use `docs/architecture/THESIS_*` + updated `README.md` for submission appendix.

## Acceptance mapping

| Criterion | Status |
|-----------|--------|
| README updated and usable | ✓ |
| agent.md AI entry point | ✓ |
| agent/01–06 current | ✓ |
| Canonical package paths | ✓ |
| Qdrant REST-only documented | ✓ |
| DOCX/PDF/TXT ingest documented | ✓ |
| Test baseline documented | ✓ |
| Delete cascade documented | ✓ |
| No stale active guidance | ✓ |
| No production code change | ✓ |
| mvn clean test | ✓ |
| docker compose config -q | ✓ |
| **Verdict** | **PASS** |
