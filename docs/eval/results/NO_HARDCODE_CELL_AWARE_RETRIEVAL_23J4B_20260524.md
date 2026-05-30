# 23J4B No-hardcode Cell-aware Retrieval

**Date:** 2026-05-24
**Verdict:** PARTIAL

## Scope

Removed fixed lexicon/domain matching from cell-aware row retrieval. The scorer now uses:

- runtime query prefix + structured value candidates
- runtime table headers and `cells_json`
- exact/boundary value matching
- query ngrams and content tokens
- generic identifier/number/date/code patterns
- same-row multi-signal boost

## Removed

- `STRUCTURED_LABEL` fixed regex from `CellAwareTableRowScorer`
- `CANONICAL_META_PREFIXES`
- `COLUMN_INTENT_SYNONYMS`
- `labelTypeAliases`
- compare intent hardcoded words
- `QuerySignalExtractor` fixed structured-label regex/list
- `KeywordSearchService` fixed table-like word regex and fixed structured prefix array

## Structured Cell Source

`CellAwareTableRowScorer.parseCells()` now reads structured `cells_json` only. It no longer parses canonical prose and no longer filters prose metadata prefixes.

`DocumentChunk` now persists row metadata with JPA columns:

- `table_name`
- `row_index`
- `cells_json`
- `group_context`

No explicit SQL migration was added because this project uses Hibernate `ddl-auto:update`; during local full test startup Hibernate added these columns to the dev MySQL schema.

## Tests

Command:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'; .\mvnw.cmd '-Dtest=HybridKeywordSearchTest,CellAwareNormalizedRowRetrievalTest,NoHardcodedLexiconInCellAwareScorerTest' test
```

Result: PASS, 24 tests.

Covered:

- no fixed lexicon structures in scorer source
- runtime header prefix matching
- Vietnamese runtime headers from test data
- arbitrary runtime domain (`Policy/Tier/Limit`)
- exact identifier match
- numeric boundary no substring false match
- multi-signal same-row boost
- no canonical meta-prefix dependency

## Full Suite Note

`mvnw test` was also attempted. It is not fully green because of existing environment/test issues outside this change:

- Spring context tests fail when `GROQ_API_KEY` is absent.
- `TabulaTableTestHelperTest` has an existing helper fixture failure (`expected "Entity" but was ""`).

The cell-aware, hybrid keyword, and no-hardcode regression suites pass.

## Runtime Q1-Q8

Not run with the new code. Docker stack is up, but the running backend container predates this patch. Rebuilding through compose in this shell showed blank API-key environment variables, so restarting the backend would risk replacing the working runtime with an unusable one.

Conclusion: code/tests PASS, runtime verification pending.
