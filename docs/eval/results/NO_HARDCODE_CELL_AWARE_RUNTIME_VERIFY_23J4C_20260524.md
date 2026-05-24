# 23J4C Runtime Verify - No-hardcode Cell-aware Retrieval

**Date:** 2026-05-24
**Mode:** VERIFY ONLY
**Conclusion:** BLOCKED_MISSING_API_KEYS / PARTIAL

## Environment

- Workspace: `E:\chatbot-rag-workspace\CHATBOT_RAG`
- Shell: Windows PowerShell
- Docker stack status: running, but backend container was created before this verify turn and is not proven rebuilt with the 23J4B patch.
- Backend container: `chatbot-backend`, service `backend`, status `Up 11 hours`.

## Files Read

- `docs/eval/results/NO_HARDCODE_CELL_AWARE_RETRIEVAL_23J4B_20260524.md`
- `docs/eval/results/FIX_LOOP_23J4B_NO_HARDCODE_CELL_AWARE.md`
- `reports/refactor/CURSOR_REPORT_23J4B_NO_HARDCODE_CELL_AWARE_RETRIEVAL.md`
- `CellAwareTableRowScorer.java`
- `QuerySignalExtractor.java`
- `KeywordSearchService.java`
- `RagRetrievalService.java`
- `NormalizedTableService.java`
- `ChunkingService2.java`
- `DocumentChunk.java`
- `EmbeddingService.java`
- `PromptBuilderService.java`

## API Key / Env Check

PowerShell env check:

| key | status |
|---|---|
| `GROQ_API_KEY` | MISSING |
| `NOMIC_API_KEY` | MISSING |
| `COHERE_API_KEY` | MISSING |

`docker compose config -q` also emitted warnings that `GROQ_API_KEY`, `NOMIC_API_KEY`, `COHERE_API_KEY`, and `COHERE_RERANK_ENABLED` defaulted to blank strings.

Per task instruction, backend rebuild/restart was not performed.

## Build / Test Result

Command:

```powershell
cd Backend
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
.\mvnw.cmd "-Dtest=HybridKeywordSearchTest,CellAwareNormalizedRowRetrievalTest,NoHardcodedLexiconInCellAwareScorerTest,NormalizedTableSuppressionTest,NormalizedTableIngestTest,FinalContextSelectionTest,RetrievalTopKTest,ChatServiceSourcePresentationTest,RagTokenAuditTest" test
```

Result: PASS.

Summary: 81 tests, 0 failures, 0 errors.

`NoHardcodedLexiconInCellAwareScorerTest`: PASS, 8 tests.

## No-hardcode Audit

Structure audit command searched:

- `STRUCTURED_LABEL`
- `COLUMN_INTENT_SYNONYMS`
- `labelTypeAliases`
- `CANONICAL_META_PREFIXES`
- fixed compare words
- fixed prefix array markers

Production files checked:

- `CellAwareTableRowScorer.java`
- `QuerySignalExtractor.java`
- `KeywordSearchService.java`
- `RagRetrievalService.java`

Result: no matches for removed structures.

Literal audit notes:

- `CellAwareTableRowScorer.java` has no fixed domain dictionary and reads `cells_json` only.
- `QuerySignalExtractor.java` uses generic identifier/date/number/structured-value morphology.
- `KeywordSearchService.java` no longer has fixed table-like query word regex or fixed structured prefix array.
- Broad `rg` finds generic words like `date`, `name`, and `section` as variable names/comments, and a pre-existing `RagRetrievalService` heading filter containing `ngay/page/trang`; this is not the cell-aware scorer or a production cell-aware lexicon.

No-hardcode scorer audit: PASS.

## Rebuild / Re-ingest

Not run.

Reason: API key env check failed before rebuild. Rebuilding with blank keys would risk replacing a currently running backend with a broken runtime.

| field | value |
|---|---|
| backend rebuilt with 23J4B patch | NO |
| new chatbot created | NO |
| new document uploaded | NO |
| `NEW_CHATBOT_ID` | N/A |
| `NEW_DOCUMENT_ID` | N/A |
| ingest duration | N/A |
| chunk count | N/A |
| Qdrant point count | N/A |

## Chunk / Qdrant Audit

Not run because no new document was ingested.

| check | result |
|---|---|
| `normalized_table_row > 0` | NOT RUN |
| `table_summary > 0` | NOT RUN |
| `table_row_group = 0` | NOT RUN |
| `text_table_like = 0` | NOT RUN |
| `cells_json` persisted | NOT RUN |
| Qdrant payload has `cells_json` | NOT RUN |

## Raw Table Text Leakage Audit

Not run because no new document was ingested.

Result: BLOCKED.

## Runtime Q1-Q8

Not run because backend rebuild and re-ingest were blocked by missing API keys.

| id | verdict | notes |
|---|---|---|
| Q1 | NOT RUN | requires rebuilt backend + new ingest |
| Q2 | NOT RUN | requires rebuilt backend + new ingest |
| Q3 | NOT RUN | requires rebuilt backend + new ingest |
| Q4 | NOT RUN | requires rebuilt backend + new ingest |
| Q5 | NOT RUN | requires rebuilt backend + new ingest |
| Q6 | NOT RUN | requires rebuilt backend + new ingest |
| Q7 | NOT RUN | requires rebuilt backend + new ingest |
| Q8 | NOT RUN | requires rebuilt backend + new ingest |

## Debug Evidence Q1/Q2/Q7

Not available. Runtime was not started because missing API keys blocked safe rebuild.

## Code Change Status

No Java, Backend, Frontend, parser, retrieval, prompt, analyzer, source cap, dependency, migration/backfill, or `.gitignore` changes were made in this verify task.

Only verification report files were added.

## Conclusion

**PARTIAL / BLOCKED_MISSING_API_KEYS**

Static source audit and targeted tests pass. Runtime verification could not proceed safely because required API keys are absent from the shell/compose environment.

Next step: export `GROQ_API_KEY`, `NOMIC_API_KEY`, and optionally `COHERE_API_KEY`, then rerun 23J4C from the rebuild step.
