# REGRESSION_TEST_RESTORE_25A2 — 2026-05-28

## Final verdict: **PASS**

| Criterion | Result |
|-----------|--------|
| Core test sources restored under `Backend/src/test` | PASS |
| `mvn clean test` without live MySQL | PASS (50 run, 1 skipped integration) |
| DOCX logical-grid mapping tests | PASS (`NormalizedTableIngestTest`, `DocxParserServiceTest`) |
| Qdrant Unicode payload tests | PASS (`QdrantPayloadUnicodeTest`) |
| No-hardcode guard tests | PASS (`NoHardcodedLexiconInTableNormalizerTest`) |
| Retrieval/prompt guards restored | PASS (see table below) |
| Backend compile | PASS |
| `docker compose config -q` | PASS |
| No production behavior change (except test seam) | PASS |

---

## Phase 1 — Test source state (before restore)

| Item | Finding |
|------|---------|
| Files on disk | Only `RagChatbotBeApplicationTests.java` |
| `mvn clean test` | FAIL — `contextLoads` needs MySQL |
| Stale `target/test-classes` | Prior focused runs could PASS without sources (25A finding) |

---

## Phase 2 — Restored test classes

| Class | Tests | Role |
|-------|-------|------|
| `DocxParserServiceTest` | 5 (1 optional fixture) | DOCX grid, gridSpan, blanks, 11 columns |
| `NormalizedTableIngestTest` | 5 | DOCX physicalColIndex, group header, curriculum, PDF overlap |
| `QdrantPayloadUnicodeTest` | 2 | UTF-8 payload via Jackson (24D4) |
| `EmbeddingServiceCacheTest` | 3 | Query embedding cache key |
| `NoHardcodedLexiconInTableNormalizerTest` | 4 | Banned domain literals in production sources |
| `PromptBuilderServiceTest` | 5 | TABLE_LOOKUP guards / banners |
| `ChatServiceSourcePresentationTest` | 9 | OOS source cap, leading refusal |
| `FinalContextSelectionTest` | 9 | top-N normalize/adaptive |
| `RetrievalTopKTest` | 4 | top-K resolution helpers |
| `CellAwareNormalizedRowRetrievalTest` | 4 | Cell-aware scorer |
| `RagChatbotBeApplicationTests` | 0 run (disabled) | Full stack integration |

**Helper:** `TestRawTableFixtures` — synthetic `RawTableModel` builders.

---

## Phase 3 — SpringBootTest / MySQL

**Option B + A:** `@Tag("integration")` + `@Disabled` on `RagChatbotBeApplicationTests`.

**Surefire** (`pom.xml`): `<excludedGroups>integration</excludedGroups>` — default `mvn test` does not load Spring context against MySQL.

Manual full stack: `mvn test -Dgroups=integration` when Docker MySQL/Qdrant are up.

---

## Production changes (testability only)

| File | Change |
|------|--------|
| `EmbeddingService.java` | Extract `buildQdrantRestPayload(TextSegment)` used by REST upsert |
| `pom.xml` | Surefire exclude `integration` group |
| `RagChatbotBeApplicationTests.java` | Tag + disable |

No ingest/retrieval/Qdrant behavior change.

---

## Phase 4 — Clean test results

| Command | Result |
|---------|--------|
| `mvnw clean test` | **PASS** — Tests run: 50, Failures: 0, Errors: 0, Skipped: 1 |
| Focused `-Dtest=DocxParserServiceTest,NormalizedTableIngestTest,NoHardcodedLexiconInTableNormalizerTest,EmbeddingServiceCacheTest,QdrantPayloadUnicodeTest` | **PASS** — 19 tests |
| Focused retrieval/prompt batch | Included in full suite — **PASS** |
| `docker compose config -q` | **PASS** |

---

## Missing / deferred

| Item | Status |
|------|--------|
| `NormalizedTableSuppressionTest` | **REQUIRED_NEXT** — not recreated (scope); suppression still covered indirectly via ingest tests |
| `HybridKeywordSearchTest` | **REQUIRED_NEXT** — needs heavier Spring/mocks |
| Full `RagRetrievalService.selectFinalContexts` E2E | **REQUIRED_NEXT** — package-private; static top-N tests restored |

---

## Risks remaining

- Optional DOCX fixture test skips if file path wrong when Maven cwd differs (path fixed to `../docs/eval/manual/...` from `Backend/`).
- Integration test disabled — production wiring still needs periodic `docker compose up` smoke.
- `NormalizedTableSuppressionTest` not yet restored.

---

## Recommendation for 25B architecture refactor

Proceed with package split only after keeping this test suite green on each step. Suggested order: `ingest.parser` → `ingest.normalize` → `index.qdrant` → `rag.retrieve` — run `mvn clean test` after each move.
