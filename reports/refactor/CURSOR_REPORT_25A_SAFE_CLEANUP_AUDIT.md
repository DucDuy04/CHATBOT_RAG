# CURSOR_REPORT_25A — Safe Dead Code / Unused File Cleanup

**Date:** 2026-05-28  
**Final verdict:** **PARTIAL**

---

## Executive summary

Evidence-based cleanup before architecture refactor. Removed **proven-unused** Qdrant gRPC dependency and dead config keys; removed **comment-only** legacy blocks in PDF Tabula loop. Did **not** remove active markdown bridge methods, ingest pipelines, metrics guards, eval fixtures, or final 24D2/24D4 reports.

---

## Baseline (Phase 1)

| Step | Result |
|------|--------|
| `mvnw -DskipTests compile` | PASS |
| Focused `-Dtest=DocxParserServiceTest,...` | PASS (57 tests) — **stale** `target/test-classes` from prior build; sources not in `Backend/src/test` |
| `mvnw clean test` | FAIL — MySQL connection refused |
| `docker compose config -q` | PASS |

---

## Deleted / changed (with evidence)

| Item | Why unused | Evidence |
|------|------------|----------|
| Maven `langchain4j-qdrant` | No Java references after 24D4 REST upsert | `rg` → 0 in `Backend/src` |
| `qdrant.port` (dev/docker YAML) | No `@Value("${qdrant.port}")` | `rg qdrant.port Backend/src` → 0 |
| `qdrant.url` (docker YAML) | Never injected | `rg qdrant.url Backend/src` → 0 |
| Commented `attemptCrossPageMerge` block in Tabula loop | Disabled comment; live path uses `convertTabulaTableToRawTableModel` | `DocumentParserService.java` ~L172 |
| Commented `legacyPreview` markdown snippet | Dead comment only | Same file |

**Not deleted:** `convertTableToMarkdown`, `parseMarkdownTable`, `[TABLE_START]` chunking, `TableIngestMetrics` markdown bridge counters, 80+ refactor reports, stable DOCX fixture.

---

## Intentionally kept

- **Qdrant REST:** `EmbeddingService` + `QdrantConfig` (`http-port` 6333).
- **Structured tables:** `RawTableModel` pipeline, DOCX logical-grid fix in `NormalizedTableService`.
- **Deprecated chunk types:** No re-enable; ingest still emits `normalized_table_row` / `table_summary`; `table_row_group`/`text_table_like` only in prompt/keyword **read** paths for legacy index data.
- **docker-compose** port `6334` — not removed (Qdrant image / possible external gRPC clients).
- **Reports:** 24D2, 24D3, 24D4, runtime verify docs — all kept.

---

## Deferred (uncertain / out of scope)

| Item | Classification |
|------|----------------|
| Restore `EmbeddingServiceCacheTest`, `NormalizedTableIngestTest`, … | Tests absent on disk — **do not delete references in task**; restore separately |
| Delete FAIL-era reports (24B2 Nomic quota, 24D0) | **DELETE_AFTER_CONFIRMATION** |
| `SoTayHocVu_24D2_upload_copy.docx` | Duplicate candidate — keep until confirmed |
| Remove `agent/03-backend.md` gRPC narrative | **REFACTOR_LATER** |
| Remove unused `inc*MarkdownBridge()` methods | Keep metric fields at 0 for regression logs |

---

## Verification (Phase 7–8)

| Command | Post-cleanup |
|---------|----------------|
| `mvnw -DskipTests compile` | **PASS** (121 files) |
| `mvnw test` | **FAIL** — `RagChatbotBeApplicationTests.contextLoads` (MySQL not running; unchanged by cleanup) |
| `docker compose config -q` | **PASS** |
| Runtime DOCX/PDF ingest smoke | **NOT RUN** (no stack) |

**gRPC write path:** None in application code after this task (already true post-24D4; pom now aligned).

---

## Risks remaining

1. **Test gap:** Task-listed unit tests not present under `src/test` — architecture refactor should restore them first.
2. **Docs drift:** Agent docs still mention `QdrantEmbeddingStore` / port 6334 for LangChain4j.
3. **Integration test:** Sole `@SpringBootTest` requires MySQL — local `mvn test` fails without Docker MySQL.

---

## Recommended next step (architecture refactor)

1. Re-add focused unit tests (normalized table, embedding cache, no-hardcode lexicon) **without** full Spring context.
2. Package-boundary split: `ingest.parser` / `ingest.normalize` / `index.qdrant` / `rag.retrieve` / `rag.prompt`.
3. Sync `agent/03-backend.md` to REST-only Qdrant.
4. Then structural refactor — not in 25A scope.

---

## Files touched in 25A

- `Backend/pom.xml`
- `Backend/src/main/resources/application-dev.yml`
- `Backend/src/main/resources/application-docker.yml`
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/DocumentParserService.java`
- `docs/eval/results/SAFE_CLEANUP_AUDIT_25A_20260528.md` (this audit)
- `reports/refactor/CURSOR_REPORT_25A_SAFE_CLEANUP_AUDIT.md`
