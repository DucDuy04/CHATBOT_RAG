# FIX LOOP - 23J4B No-hardcode Cell-aware Retrieval

**Date:** 2026-05-24
**Verdict:** PARTIAL

## Loop

1. Found remaining fixed lexicon in `CellAwareTableRowScorer`, plus related fixed extraction in `QuerySignalExtractor` and table-like helpers in `KeywordSearchService`.
2. Replaced fixed label types/synonyms with query-derived prefix/value candidates.
3. Required label boost to validate prefix against runtime table headers before matching value.
4. Removed canonical prose cell parsing from scorer and persisted structured row metadata on `DocumentChunk`.
5. Added source scanner and generic behavior tests.
6. Ran targeted regression suites: PASS.
7. Runtime Q1-Q8 not run because backend container is not refreshed with this patch and compose rebuild currently lacks API-key env values.

## Changed Files

- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/CellAwareTableRowScorer.java`
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/QuerySignalExtractor.java`
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/KeywordSearchService.java`
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/domain/document/DocumentChunk.java`
- `Backend/src/test/java/KLTN/RAG_CHATBOT_BE/service/CellAwareNormalizedRowRetrievalTest.java`
- `Backend/src/test/java/KLTN/RAG_CHATBOT_BE/service/NoHardcodedLexiconInCellAwareScorerTest.java`

## Next Runtime Step

Run after safe backend rebuild with API keys available:

1. `docker compose up -d --build backend`
2. Re-ingest `docs/eval/manual/SoTayHocVu-HocKy1-NamHoc20252026.pdf`
3. Run Q1-Q8 playground QA
4. Confirm Q1/Q2 correct row, Q7 KTR3185 no regression, OOS still refuses, no raw table text returns
