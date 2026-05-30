# CURSOR REPORT - 23J4C-RERUN Runtime Verify No-hardcode Cell-aware Retrieval

**Date:** 2026-05-24
**Mode:** VERIFY ONLY
**Conclusion:** FAIL

## Files Read

- `docs/eval/results/NO_HARDCODE_CELL_AWARE_RETRIEVAL_23J4B_20260524.md`
- `docs/eval/results/FIX_LOOP_23J4B_NO_HARDCODE_CELL_AWARE.md`
- `reports/refactor/CURSOR_REPORT_23J4B_NO_HARDCODE_CELL_AWARE_RETRIEVAL.md`
- `docs/eval/results/NO_HARDCODE_CELL_AWARE_RUNTIME_VERIFY_23J4C_20260524.md`
- `reports/refactor/CURSOR_REPORT_23J4C_NO_HARDCODE_CELL_AWARE_RUNTIME_VERIFY.md`
- `CellAwareTableRowScorer.java`
- `QuerySignalExtractor.java`
- `KeywordSearchService.java`
- `RagRetrievalService.java`
- `NormalizedTableService.java`
- `ChunkingService2.java`
- `DocumentChunk.java`
- `EmbeddingService.java`
- `PromptBuilderService.java`
- `QueryAnalyzerService.java`

## Code Edit Status

No source code was changed.

No Java, Frontend, Backend logic, parser/chunking, retrieval, PromptBuilder, QueryAnalyzer, source cap, dependency, migration/backfill, or `.gitignore` changes were made.

Verification report files updated:

- `docs/eval/results/NO_HARDCODE_CELL_AWARE_RUNTIME_VERIFY_23J4C_RERUN_20260524.md`
- `reports/refactor/CURSOR_REPORT_23J4C_RERUN_NO_HARDCODE_CELL_AWARE_RUNTIME_VERIFY.md`

## Env / API Key Status

API keys are available in the verification shell/User env:

- `GROQ_API_KEY`: SET
- `NOMIC_API_KEY`: SET
- `COHERE_API_KEY`: SET

`docker compose config -q`: PASS.

## Build / Test Result

Backend rebuild:

- `docker compose up --build -d backend`: PASS
- `chatbot-backend` recreated and started
- Backend health: PASS after startup
- Qdrant health: PASS

Targeted tests:

- Result: PASS
- Tests run: 81
- Failures: 0
- Errors: 0
- `NoHardcodedLexiconInCellAwareScorerTest`: PASS, 8 tests

## No-hardcode Audit Result

Result: PASS.

Removed hardcoded structures were not found in production scorer/retrieval files:

- `STRUCTURED_LABEL`
- `COLUMN_INTENT_SYNONYMS`
- `labelTypeAliases`
- `CANONICAL_META_PREFIXES`
- fixed compare words
- fixed structured prefix arrays
- old table-like query word regex markers

Assessment:

- Cell-aware scoring reads `cells_json`.
- No fixed cell-aware domain dictionary was found.
- Runtime logs confirm `[RAG][cell-aware]` uses extracted runtime signals and logs row `cells`.

## Re-ingest Result

- `NEW_CHATBOT_ID`: `72d9bb71-8c3e-4b22-b460-53ce0779393f`
- `NEW_DOCUMENT_ID`: `c0938e8b-d161-4a56-81b1-cd02b7499bc7`
- File: `docs/eval/manual/SoTayHocVu-HocKy1-NamHoc20252026.pdf`
- Status: `INDEXED`
- Ingest duration: 128.70s
- Chunk count: 2734
- Qdrant point count: 2734
- Parse: sections=59, tables=106, chunks=2734
- Embed finish: saved 2734 vectors

## Chunk / Qdrant / Leakage Audits

| check | result |
|---|---|
| `normalized_table_row` count | 2474 |
| `table_summary` count | 106 |
| `table_row_group` count | 0 |
| `text_table_like` count | 0 |
| DB `cells_json` persistence | PASS |
| Qdrant payload `cells_json` | PASS |
| DB chunks = Qdrant points | PASS, 2734 = 2734 |
| raw table text leakage | PASS, no raw table mega-text found |

Qdrant payload sample includes `chunk_type`, `table_name`, `row_index`, `cells_json`, `document_id`, `page_start`, and `page_end`.

## Q1-Q8 Result

| query | result |
|---|---|
| Q1 Nhóm 2 | FAIL |
| Q2 Nhóm 4 | FAIL |
| Q3 Nhóm 1 vs Nhóm 2 | FAIL |
| Q4 K45-K48 registration dates | PARTIAL |
| Q5 Kiến trúc K46 HK2 | FAIL |
| Q6 Công nghệ sinh học K46 HK2 | PARTIAL |
| Q7 KTR3185 | FAIL |
| Q8 USD/VND OOS | PASS |

Tally:

- PASS: 1/8
- PARTIAL: 2/8
- FAIL: 5/8

## Source / Cells JSON Evidence

Q1/Q2:

- Runtime signals were extracted correctly enough to include `nhom 2` / `nhom 4`.
- Top rows were generic `KNM1013 Kỹ năng mềm` normalized rows, not actual Nhóm 2/Nhóm 4 timetable rows.
- Example cells: `{"TT":"KNM1013 Kỹ năng mềm","Mã":""}`
- Answer refused.

Q4:

- Correct registration row surfaced:
  - `{"STT":"2","NỘI DUNG CÔNG VIỆC":"Sinh viên đăng ký học phần qua mạng Internet","KHÓA":"","THỜI GIAN":""}`
- Missing `KHÓA` and `THỜI GIAN` cell values caused answer failure.

Q6:

- Top row relevant to Công nghệ sinh học K46:
  - `{"TT":"","Mã":"Mã T học phần hóa, ngành: Công nghệ sinh học K46"}`
- Answer returned only one item, not a complete HK2 list.

Q7:

- DB contains KTR3185 cells, but sparse:
  - `{"STT":"461","Mã":"KTR3185"}`
  - `{"STT":"463","Mã":"KTR3185 5"}`
- Expected title `Đồ án kiến trúc công trình tổ hợp đa chức năng` was not found in DB content/cells search.
- Runtime top rows did not include KTR3185.

Q8:

- Correctly refused out-of-scope USD/VND current exchange-rate question.

## Remaining Issues

1. Runtime row targeting still fails for Q1/Q2.
2. KTR3185 regression remains.
3. Some persisted normalized rows are too sparse/malformed for target QA:
   - KNM1013 rows often lack group/timetable columns.
   - KTR3185 rows lack title/name cell and sometimes only preserve code plus credit.
   - Q4 registration row lacks time-range cells.
4. Structural ingest succeeded, but parser/table normalization quality is insufficient for the target runtime questions.

## Next Step

Because this task was VERIFY ONLY, no fix was made.

Recommended next task should focus on parser/table normalization fidelity for the SoTay timetable/program tables, especially preserving:

- group number
- lecturer
- weekday
- period
- room
- course code
- course title
- credits
- date/time range cells

Final conclusion: **FAIL**.
