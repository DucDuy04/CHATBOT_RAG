# CURSOR REPORT - 23J4C Runtime Verify No-hardcode Cell-aware Retrieval

**Date:** 2026-05-24
**Mode:** VERIFY ONLY
**Conclusion:** BLOCKED_MISSING_API_KEYS / PARTIAL

## Files Read

Reports:

- `docs/eval/results/NO_HARDCODE_CELL_AWARE_RETRIEVAL_23J4B_20260524.md`
- `docs/eval/results/FIX_LOOP_23J4B_NO_HARDCODE_CELL_AWARE.md`
- `reports/refactor/CURSOR_REPORT_23J4B_NO_HARDCODE_CELL_AWARE_RETRIEVAL.md`

Production source:

- `CellAwareTableRowScorer.java`
- `QuerySignalExtractor.java`
- `KeywordSearchService.java`
- `RagRetrievalService.java`
- `NormalizedTableService.java`
- `ChunkingService2.java`
- `DocumentChunk.java`
- `EmbeddingService.java`
- `PromptBuilderService.java`

## Code Edit Status

No code was changed in this verify task.

No Java, Frontend, Backend logic, parser/chunking, retrieval, PromptBuilder, QueryAnalyzer, source cap, dependencies, migration/backfill, or `.gitignore` changes were made.

## API Key Status

PowerShell env:

- `GROQ_API_KEY`: missing
- `NOMIC_API_KEY`: missing
- `COHERE_API_KEY`: missing

Compose config also warned that those variables defaulted to blank strings.

Per instruction, backend was not restarted and not rebuilt.

## Docker Status

`docker compose ps`:

- `chatbot-backend`: Up 11 hours, `0.0.0.0:8080->8080/tcp`
- `chatbot-frontend`: Up 13 hours
- `ragchatbot-mysql`: Up 13 hours, healthy
- `ragchatbot-qdrant`: Up 13 hours

The backend running status does not prove it contains the 23J4B patch because no rebuild was allowed after the missing-key check.

## Test Result

Targeted Maven command completed successfully.

Result: PASS, 81 tests, 0 failures.

Included suites:

- `HybridKeywordSearchTest`
- `CellAwareNormalizedRowRetrievalTest`
- `NoHardcodedLexiconInCellAwareScorerTest`
- `NormalizedTableSuppressionTest`
- `NormalizedTableIngestTest`
- `FinalContextSelectionTest`
- `RetrievalTopKTest`
- `ChatServiceSourcePresentationTest`
- `RagTokenAuditTest`

`NoHardcodedLexiconInCellAwareScorerTest`: PASS.

## No-hardcode Audit Result

Removed structures were searched in production retrieval/scorer files:

- `STRUCTURED_LABEL`
- `COLUMN_INTENT_SYNONYMS`
- `labelTypeAliases`
- `CANONICAL_META_PREFIXES`
- fixed compare terms
- fixed structured prefix array markers

Result: PASS, no removed structures found.

Assessment:

- Cell-aware scorer uses `cells_json` and runtime header/value matching.
- No fixed cell-aware synonym map or label alias map remains.
- Generic identifier/date/number regex remains, which is allowed.
- Broader literal search found non-cell-aware comments/identifiers and a pre-existing `RagRetrievalService` heading filter; this was not introduced or changed in this verify task and is not the cell-aware scorer lexicon.

## Re-ingest Result

Not run.

Reason: missing API keys blocked safe backend rebuild.

- `NEW_CHATBOT_ID`: N/A
- `NEW_DOCUMENT_ID`: N/A
- upload time: N/A
- parse finish time: N/A
- embed finish time: N/A
- ingest duration: N/A
- chunk count: N/A
- Qdrant point count: N/A

## Chunk / Qdrant / Leakage Audits

Not run because there is no new 23J4C document.

- `normalized_table_row` count: N/A
- `table_summary` count: N/A
- `table_row_group`: N/A
- `text_table_like`: N/A
- `cells_json` DB persistence on new doc: N/A
- Qdrant payload `cells_json`: N/A
- raw table text leakage: N/A

## Q1-Q8 Runtime Result

Not run.

Reason: backend rebuild + re-ingest were blocked by missing API-key env.

| query | result |
|---|---|
| Q1 | NOT RUN |
| Q2 | NOT RUN |
| Q3 | NOT RUN |
| Q4 | NOT RUN |
| Q5 | NOT RUN |
| Q6 | NOT RUN |
| Q7 | NOT RUN |
| Q8 | NOT RUN |

## Remaining Issues

1. Runtime verification is blocked until API keys are available in the shell/compose environment.
2. Backend must be rebuilt after keys are present to prove it runs the 23J4B patch.
3. A fresh SoTayHocVu ingest is still required for chunk/Qdrant/runtime evidence.

## Next Step

Set env vars before rebuilding:

```powershell
$env:GROQ_API_KEY='...'
$env:NOMIC_API_KEY='...'
$env:COHERE_API_KEY='...'
docker compose up --build -d backend
```

Then re-run ingest and Q1-Q8 runtime QA.

Final conclusion: **PARTIAL / BLOCKED_MISSING_API_KEYS**.
