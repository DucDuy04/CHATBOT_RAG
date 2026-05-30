# CURSOR_REPORT_25A2 — Regression Test Suite Restore

**Date:** 2026-05-28  
**Final verdict:** **PASS**

---

## Summary

Restored **11 test classes** (50 unit tests) from source under `Backend/src/test/java`. Default `mvn clean test` passes **without MySQL**. Continues 25A cleanup; does not refactor architecture.

---

## Baseline (pre-restore)

- Only `RagChatbotBeApplicationTests` on disk.
- `mvn clean test` → FAIL (MySQL / ApplicationContext).
- Stale `target/test-classes` could mask missing sources.

---

## What was restored

| Test | Protects |
|------|----------|
| `DocxParserServiceTest` | DOCX logical grid, gridSpan, column alignment |
| `NormalizedTableIngestTest` | 24D2 cells_json mapping, PDF overlap path |
| `QdrantPayloadUnicodeTest` | 24D4 REST payload UTF-8 |
| `EmbeddingServiceCacheTest` | Query embedding cache keys |
| `NoHardcodedLexiconInTableNormalizerTest` | No domain hardcode in parser/normalizer/scorer |
| `PromptBuilderServiceTest` | TABLE_LOOKUP prompt guards |
| `ChatServiceSourcePresentationTest` | OOS leading-refusal source cap |
| `FinalContextSelectionTest` | Adaptive final context top-N |
| `RetrievalTopKTest` | top-K resolution |
| `CellAwareNormalizedRowRetrievalTest` | Cell-aware row scoring |

---

## Integration test handling

`RagChatbotBeApplicationTests`: `@Tag("integration")` + `@Disabled` + surefire `excludedGroups=integration`.

---

## Production diff (minimal)

```diff
+ static Map<String, Object> buildQdrantRestPayload(TextSegment segment)
+ payload = buildQdrantRestPayload(seg);  // in upsertToQdrant
```

Surefire excludes integration group.

---

## Verification

| Command | Result |
|---------|--------|
| `mvnw clean test` | PASS (50 tests, 1 skipped) |
| Focused core 5-class suite | PASS (19 tests) |
| `mvnw -DskipTests compile` | PASS (via test compile) |
| `docker compose config -q` | PASS |

---

## Deferred (REQUIRED_NEXT)

- `NormalizedTableSuppressionTest`
- `HybridKeywordSearchTest`
- Full retrieval pipeline integration tests

---

## Next step (25B)

Architecture package refactor with **mandatory** `mvn clean test` after each boundary move.
