# CELLS_JSON_HEADER_MAPPING_DIAGNOSTIC_23P0_20260526

Date: 2026-05-26

## Final diagnostic verdict

**Schedule rows (page ~67):**

- **VALUE_ALIGNMENT**: **CORRECT** — values are mapped to the correct physical columns / x-slots.
- **HEADER_MAPPING**: **WRONG_PARENT + COLLISION_SUFFIX** — multiple distinct physical columns (lecturer / date / weekday / period / room / note) are incorrectly labeled under a repeated parent fragment (e.g. `bắt đầu`, `bắt đầu_2`, `bắt đầu_3`, ...). The suffixing is a consequence of header collisions, not a value mutation.
- **SOURCE_DISPLAY**: **CORRECT but MISLEADING** — source hover prints stored `cells_json` keys verbatim, so wrong/ambiguous header keys mislead both users and LLM.

**Curriculum rows (page ~22):**

- **VALUE_ALIGNMENT**: **CORRECT** for visible non-empty cells (course codes/titles/credits remain in expected x-slots).
- **HEADER_MAPPING**: **WRONG_PARENT + COLLISION_SUFFIX** at extreme scale — a wide spanning header fragment (cohort/context-like) is selected as the header for many slots, producing `...K45`, `...K45_2`, `...K45_3`, ... and pushing other headers out of the final key selection.
- **Resulting Q5 behavior**: cohort/context for K46 is **not isolated into its own column/key**, and can remain embedded inside a course cell value that is primarily keyed under another cohort header → retrieval/LLM cannot reliably filter cohort + semester constraints.

## Scope & constraints (confirmed)

- Diagnostic-only: **no production logic changes** to parser/normalizer/retrieval/prompt/scorer/front-end/Qdrant.
- Allowed: **diagnostic JUnit tests/scripts** + reports.
- Used existing fixture PDF to reproduce mapping: `docs/eval/manual/SoTayHocVu-HocKy1-NamHoc20252026.pdf`.

## Evidence sources read first (required)

- `docs/eval/results/STRUCTURED_TABLE_INGEST_23M_20260526.md`
- `reports/refactor/CURSOR_REPORT_23M_STRUCTURED_TABLE_INGEST.md`
- `docs/eval/results/STRUCTURED_TABLE_RUNTIME_RESTORE_23N_20260526.md`
- `reports/refactor/CURSOR_REPORT_23N_STRUCTURED_TABLE_RUNTIME_RESTORE.md`
- `docs/eval/results/RUNTIME_VERIFY_23O_AFTER_23N_20260526.md`
- `reports/refactor/CURSOR_REPORT_23O_RUNTIME_VERIFY_AFTER_23N.md`

## Diagnostic reproduction method

Added a diagnostic JUnit test that:

1. Parses the PDF fixture using current production `DocumentParserService` Tabula→`RawTableModel` path.
2. Locates the target raw table(s) by page number + diagnostic needles.
3. Re-runs the coordinate pipeline used by structured ingest:
   - headerStart/dataStart inference
   - `inferHeadersFromCoordinates(...)`
   - `mergeWrappedRowsWithCoordinateGuard(...)`
   - `mapCellsFromCoordinates(...)`
4. Prints stage A–F trace.

Test file:

- `Backend/src/test/java/KLTN/RAG_CHATBOT_BE/diagnostic/HeaderMapping23P0DiagnosticTest.java`

Command:

```powershell
cd Backend
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
.\mvnw.cmd "-Dtest=KLTN.RAG_CHATBOT_BE.diagnostic.HeaderMapping23P0DiagnosticTest" test
```

## Stage-by-stage findings (schedule rows)

### Stage A — RawTableModel cells

For schedule rows containing the diagnostic needles (groups 1/2/4), each value cell has coordinates and stable x-ranges per physical column.

### Stage B — Header candidate rows

The table contains multiple header-like rows (4 physical rows before data start).

### Stage C — Header slots (most important)

Observed slot construction uses **data-driven x ranges**, then picks a “best header cell” per header row:

- when x-overlap is 0, it still can choose by **nearest center distance** (fallback) and still contributes a fragment
- final header selection uses **the last fragment (lowest header row)** if it is not “ambiguous”

This causes a lower header-row fragment like `bắt đầu` to be selected as the header for multiple distinct slots, even when that fragment has **0 overlap** for some slots.

Concrete evidence (from the diagnostic output):

- For slot 5 (the lecturer column x-range), fragments were `[Giảng viên, ..., bắt đầu]` and the final selected header became `bắt đầu`.
- For slots 6–10 (date/weekday/period/room/note x-ranges), the last fragment `bắt đầu` becomes `bắt đầu_2..bắt đầu_6` after collision suffixing.

### Stage D — Logical row reconstruction

No wrapped-row merge occurred for the schedule table:

- merge attempts happened, but were rejected as `NEW_ROW_NUMBER` (each row begins with a row number-like value).

Therefore, schedule errors are **not** a row-merge problem.

### Stage E — mapCellsFromCoordinates

For schedule rows, each cell maps to its slot with full overlap:

- `overlap == slotWidth` and `centerDist ~= 0` for all non-empty data cells

So this is **not** a wrong-slot assignment problem. Values remain aligned with physical columns.

### Stage F — Canonical text / source hover

Source hover prints the `cells_json` keys verbatim via `NormalizedTableService.buildCanonicalText(...)`.
Therefore, ambiguous/wrong header keys directly surface in UI and in the LLM prompt.

This explains Q2: the weekday column’s value (e.g. `6`) becomes `bắt đầu_3: 6` because the weekday column’s slot header was mis-selected to a repeated `bắt đầu` fragment and then suffixed.

## Stage-by-stage findings (curriculum rows relevant to Q5)

### Key observation

A very wide header fragment (cohort/context-like) appears as a single cell spanning a large x-range in a header row, and due to the “select last fragment” rule it becomes the selected header for many slots.

Evidence excerpt: Stage C shows slot 0 header becomes `hóa, ngành: Kiến trúc K45` with fragments ending in that spanning cell, and subsequent slots become `...K45_2`, `...K45_3`, ... through collision suffixing.

### Implication for Q5

Even when K46 appears on the same logical row, it can appear embedded in a data value under a `...K45_*` key rather than being isolated into a separate cohort key/column. This makes K46/HK2 filtering unreliable at retrieval + LLM interpretation time.

## Correctness classification (required)

### Schedule rows (page 67)

- **VALUE_ALIGNMENT**: CORRECT
- **HEADER_MAPPING**: WRONG_PARENT + COLLISION_SUFFIX
- **SOURCE_DISPLAY**: MISLEADING (keys ambiguous), not mutated

### Curriculum rows (page 22)

- **VALUE_ALIGNMENT**: CORRECT (for extracted non-empty cells)
- **HEADER_MAPPING**: WRONG_PARENT + COLLISION_SUFFIX (wide spanning header dominates many slots)
- **SOURCE_DISPLAY**: MISLEADING (keys dominated by cohort fragment), not mutated

## Root cause table (required)

| Issue | Example row | Stage where introduced | Root cause | Evidence | Recommended fix layer |
|---|---|---|---|---|---|
| Repeated `bắt đầu_*` keys for distinct schedule columns (weekday/period/room) | schedule row with weekday value `6` shown as `bắt đầu_3: 6` | **Stage C (inferHeadersFromCoordinates)** | **Header cell selection accepts 0-overlap fragments via nearest-center fallback; final header chooses last fragment even if its alignment is weak; collisions are suffixed** | Stage C shows `bắt đầu` chosen for multiple slots; Stage E shows value-to-slot overlap is perfect (values aligned) | **Normalizer header-slot selection** (don’t change values; improve header fragment acceptance/scoring) |
| Cohort header dominates many curriculum columns (K45 keys across columns) and K46 appears embedded in value | curriculum row where a `...K45_2` cell contains trailing `...K46` text | **Stage C (inferHeadersFromCoordinates)** | **Wide spanning header fragment overlaps many slots and becomes the selected header for multiple columns; collisions create `_2.._N` keys** | Stage C shows spanning `...K45` chosen with huge overlap across slots | **Normalizer header-slot selection + group_context extraction** (diagnose further before fixes) |
| UI/source hover confusing even when values are correct | schedule rows displayed with ambiguous keys | **Stage F (canonical text)** | Canonical text prints keys verbatim; no semantic aliasing for columns when headers are ambiguous | `buildCanonicalText()` prints `key: value` in order; no label normalization | **Source presentation layer or prompt layer** (only after mapping fixed / classified) |

## Recommended fixes (ranked by safety; NOT implemented)

1. **NormalizedTableService coordinate header inference (Stage C)**: make header fragment selection alignment-aware (e.g., require overlap > 0 for “child” fragments; treat 0-overlap nearest-center picks as low-confidence and avoid using them as the final selected header).
2. **Collision handling**: detect “parent reused across many sibling columns” and either fall back to `col_N` or derive a stable composite header (parent+child) only when child is confident.
3. **Curriculum cohort isolation** (if still needed after Stage C fix): extract cohort/context-like spanning headers into `group_context` instead of using them as per-column keys.
4. **Prompt/source presentation**: add generic guidance to interpret numeric weekday values when query asks for weekday — only after header mapping is corrected (to avoid masking root problems).

## Tests / commands run (required)

| Command | Result | Note |
|---|---|---|
| `cd Backend && .\mvnw.cmd "-Dtest=KLTN.RAG_CHATBOT_BE.diagnostic.HeaderMapping23P0DiagnosticTest" test` | PASS | Runs fixture schedule+curriculum traces |

## Known limitations

- This diagnostic run used **fixture parsing**, not DB/Qdrant inspection. Chunk IDs / Qdrant payload parity are not reported here unless DB audit is enabled separately.
- The diagnostic uses stdout logs; for reviewer convenience, the report extracts only the most relevant evidence. Full output remains available from the test run logs.

