# CURSOR REPORT - 23J4B No-hardcode Cell-aware Retrieval

**Date:** 2026-05-24
**Conclusion:** PARTIAL

## Hardcode Removed

Removed from production scoring/extraction:

- fixed structured-label regex
- fixed canonical metadata prefixes
- fixed column intent synonym map
- fixed label alias switch/map
- fixed compare words
- fixed table-like query word regex
- fixed structured prefix array in keyword scoring

No SoTay-specific rule was added.

## New Data-driven Scoring

`QuerySignalExtractor` now derives structured candidates from the query by generic morphology:

- one to three prefix tokens before a structured value
- numeric values
- short alphanumeric/code-like values

`CellAwareTableRowScorer` validates those candidates only against runtime row data:

- prefix must match a runtime column key by exact/token/ngram similarity
- value must match the runtime cell with token boundary
- same row gets boosted when multiple independent query signals match
- column intent boost is header/query ngram overlap only
- compare coverage activates when multiple structured candidates exist

## Structured Cells

`parseCells()` uses `cells_json` only. Canonical prose fallback returns empty, so there is no metadata-prefix parsing. `DocumentChunk` now persists `table_name`, `row_index`, `cells_json`, and `group_context` via JPA columns.

Migration: no manual migration file. Hibernate `ddl-auto:update` created the columns in local dev MySQL during test context startup.

## Tests

PASS:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'; .\mvnw.cmd '-Dtest=HybridKeywordSearchTest,CellAwareNormalizedRowRetrievalTest,NoHardcodedLexiconInCellAwareScorerTest' test
```

Result: 24 tests, 0 failures.

`NoHardcodedLexiconInCellAwareScorerTest` verifies:

- source no longer has the removed lexicon structures
- runtime English headers
- runtime Vietnamese headers
- arbitrary non-education domain
- identifier exact match
- numeric boundary
- same-row multi-signal
- no canonical meta-prefix dependency

Full `mvnw test` was attempted but blocked by existing unrelated environment/fixture failures: missing `GROQ_API_KEY` for Spring context and `TabulaTableTestHelperTest` fixture output.

## Runtime Q1-Q8

Not run with patched code. Containers are up, but backend is older than the patch. Compose rebuild in this shell reported blank API-key environment variables, so restarting would risk breaking runtime chat/embedding.

## Acceptance

- Cell-aware scorer hardcoded lexicon removed: PASS
- `STRUCTURED_LABEL` removed from scorer: PASS
- `COLUMN_INTENT_SYNONYMS` removed: PASS
- `labelTypeAliases` removed: PASS
- `CANONICAL_META_PREFIXES` removed: PASS
- Runtime-header label/cell matching: PASS
- Boundary numeric matching: PASS
- No-hardcode test: PASS
- Generic tests: PASS
- Runtime Q1-Q8: NOT RUN
- Migration: no manual migration; JPA columns added with `ddl-auto:update`
- Remaining hardcode in scorer: none found

Final verdict: PARTIAL until runtime Q1-Q8 is rerun after safe backend rebuild and re-ingest.
