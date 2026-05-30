# DEFERRED_REGRESSION_TESTS_25F — Restore Deferred Regression Tests

**Date:** 2026-05-28  
**Task:** 25F — Restore deferred regression tests with lightweight setup  
**Verdict:** **PASS**

---

## 1. Mức độ hiểu task

| Item | Value |
|------|-------|
| Hiểu task | **95%** |
| Chắc chắn | Restore 3 deferred test classes; no production refactor; `mvn clean test` without live services |
| Giả định | Full `RagRetrievalService.retrieveWithMetadata` Spring E2E not required when static pipeline tests cover ranking/selection |
| Thiếu dữ kiện | Old test source files not in git history at checked commits — recreated from 25E spec + eval docs |

---

## 2. Tóm tắt yêu cầu

Restore:

1. `NormalizedTableSuppressionTest` — ingest suppression / deprecated chunk paths  
2. `HybridKeywordSearchTest` — hybrid keyword merge, cells_json, Unicode, topK  
3. Lightweight `RagRetrievalServiceE2ETest` — scoped curriculum + schedule ranking without MySQL/Qdrant/API

---

## 3. Hiện trạng trước khi sửa

- **50 tests**, 0 failures (25E baseline)  
- 13 test classes; missing `NormalizedTableSuppressionTest`, `HybridKeywordSearchTest`, `RagRetrievalServiceE2ETest`  
- Surefire excludes `@Tag("integration")` by default (`pom.xml`)

---

## 4. Nguyên nhân gốc (từ source / 25E)

Tests deferred in 25A2/25E during package refactor; production ingest/retrieval logic intact but regression guardrails removed.

---

## 5. Chiến lược sửa

- **Suppression:** `NormalizedTableService` + `ChunkingService2` no-arg ctor + `TestRawTableFixtures` synthetic `RawTableModel`  
- **Hybrid:** Mockito `DocumentChunkRepository` + `ReflectionTestUtils` for hybrid flags; static `mergeCandidates`; package `scoreChunk`  
- **E2E:** Static `RagRetrievalService` helpers (`applyCellAwareScoreBoost`, `selectTopNByScoreWithBudget`) + `CellAwareTableRowScorer` — no Spring context

**Production changes:** none

---

## 6. Danh sách file đã đọc

| Path | Mục đích | Kết luận |
|------|----------|----------|
| `NormalizedTableIngestTest.java` | Pattern for normalize fixtures | Reuse `TestRawTableFixtures`, `parseCells` |
| `TestRawTableFixtures.java` | Synthetic tables | Extended ABC + broad-parent fixtures |
| `ChunkingService2.java` | Chunk types / metrics | `processSections2` + `RAW_TABLE_REF` path |
| `KeywordSearchService.java` | Hybrid merge/score | Static merge + package `scoreChunk` |
| `RagRetrievalService.java` | Ranking/selection static API | E2E via static scoring pipeline |
| `CellAwareNormalizedRowRetrievalTest.java` | Scorer patterns | Reused in E2E tests |
| `pom.xml` | Surefire integration exclude | No change needed |
| `docs/eval/results/ARCHITECTURE_HYGIENE_25E_20260528.md` | REQUIRED_NEXT list | Confirmed scope |

---

## 7. Danh sách file đã sửa

| Path | Sửa để làm gì | Layer |
|------|---------------|-------|
| `Backend/src/test/.../NormalizedTableSuppressionTest.java` | **Created** — 5 suppression tests | test |
| `Backend/src/test/.../HybridKeywordSearchTest.java` | **Created** — 6 hybrid tests | test |
| `Backend/src/test/.../RagRetrievalServiceE2ETest.java` | **Created** — 4 lightweight E2E tests | test |
| `Backend/src/test/.../TestRawTableFixtures.java` | Added `docxSimpleAbcTable`, `docxBroadParentHeaderTable` | test |

---

## 8. Diff thay đổi (tóm tắt)

### `NormalizedTableSuppressionTest.java` (new)

- Asserts `normalized_table_row` + `table_summary` only; bans `table_row_group` / `text_table_like`  
- Broad parent header → keys `A/B/C`, not `Parent_*`  
- 24D2 group-header row slot integrity  
- `valuesDroppedCount == 0`  
- Metrics: `structuredTablesNormalized > 0`, markdown bridge counts = 0  

### `HybridKeywordSearchTest.java` (new)

- Mock repo corpus; keyword hit when vector empty  
- `mergeCandidates` dedupe → `BOTH`  
- Vietnamese curriculum `cells_json` scoring  
- `groupContext` via `CellAwareTableRowScorer`  
- `keywordTopM` cap  

### `RagRetrievalServiceE2ETest.java` (new)

- K46 + HK2 row ranks above HK1 / K45 rows  
- Nhóm 4 schedule row above Nhóm 2  
- Signal extraction + cell/keyword scoring smoke  
- `selectTopNByScoreWithBudget` picks exact scope under topN=1  

### `TestRawTableFixtures.java`

```diff
+ docxSimpleAbcTable()
+ docxBroadParentHeaderTable()
```

---

## 9. Ảnh hưởng sau sửa

| Behavior | Change |
|----------|--------|
| Production ingest/retrieval/chat | **Unchanged** |
| Default `mvn clean test` | +15 tests; still no MySQL/Qdrant/API |
| CI Surefire | Still excludes `integration` tag |
| Memory/CPU | Negligible (unit tests only) |

---

## 10. Edge cases đã xem xét

- Empty corpus → keyword search returns 0 (not exercised; merge/score paths covered)  
- Broad spanning header demotion  
- Duplicate chunk UUID merge  
- Wrong-scope rows with tight topN  
- Mockito + non-Spring `@Value` fields → `ReflectionTestUtils`  

---

## 11. Kết quả kiểm tra

| Command | Kết quả | Ghi chú |
|---------|---------|---------|
| `cd Backend; .\mvnw.cmd clean test` | **PASS** | **65 tests**, 0 failures, 0 errors |
| `.\mvnw.cmd "-Dtest=NormalizedTableSuppressionTest,NormalizedTableIngestTest" test` | **PASS** | 10 tests |
| `.\mvnw.cmd "-Dtest=HybridKeywordSearchTest,CellAwareNormalizedRowRetrievalTest,RetrievalTopKTest" test` | **PASS** | 14 tests |
| `.\mvnw.cmd "-Dtest=RagRetrievalServiceE2ETest,FinalContextSelectionTest,CellAwareNormalizedRowRetrievalTest" test` | **PASS** | 17 tests |
| `docker compose config -q` | **PASS** | repo root |
| Frontend lint/build/widget | **NOT RUN** | out of scope |
| Stale scan `QdrantEmbeddingStore` in `Backend/src` | **0 hits** | valid |
| `table_row_group` in main | backward-compat read (KeywordSearchService, PromptBuilder, ChatService) | valid |
| `text_table_like` in test | assertion set only | valid |

**Test count:** before **50** → after **65** (+15)

---

## 12. Rủi ro còn lại

- Lightweight E2E does not exercise full `retrieveWithMetadata` (Qdrant embed, rerank API, DB sections)  
- Keyword index cache behavior in production may differ slightly from fallback scan in edge widgets  
- Runtime Q1–Q8 eval still manual / docker stack  

---

## 13. Đề xuất tiếp theo

1. Optional `@Tag("integration")` test for full `RagRetrievalService` with Testcontainers MySQL + Qdrant (excluded by default)  
2. Restore `KeywordIndexCacheTest` / `KeywordSearchIndexTest` if needed for index regression  
3. Runtime verify SoTay corpus Q1–Q8 after deploy  

---

## Tests restored

| Class | Tests | Status |
|-------|------:|--------|
| `NormalizedTableSuppressionTest` | 5 | Restored |
| `HybridKeywordSearchTest` | 6 | Restored |
| `RagRetrievalServiceE2ETest` | 4 | Restored (lightweight) |

## Integration tests tagged/disabled

None added. Existing Surefire `excludedGroups=integration` unchanged.

## Remaining deferred

- Full Spring-wire `RagRetrievalService.retrieveWithMetadata` integration test (optional)  
- Live runtime benchmark Q1–Q8  
