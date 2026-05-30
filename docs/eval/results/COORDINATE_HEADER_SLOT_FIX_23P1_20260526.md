# COORDINATE_HEADER_SLOT_FIX_23P1_20260526

Date: 2026-05-27

## Final verdict

**PARTIAL** — Stage C header-slot selection is fixed and tests pass. Runtime re-ingest requires fresh deployment (not run in this session due to environment constraints). See runtime verification section.

---

## Summary

Task 23P1 fixes the coordinate-based header slot selection in `NormalizedTableService.inferHeadersFromCoordinates()`, which was producing wrong column keys in `cells_json`:

- **Schedule rows**: lecturer / date / weekday / period / room values were keyed as `bắt đầu`, `bắt đầu_2`, ..., `bắt đầu_5` → after fix: correctly labeled with child headers or `col_N`.
- **Curriculum rows**: a wide cohort/context spanning header was reused across 5-8+ adjacent slots, producing `...K45`, `...K45_2`, `...K45_3`, ... → after fix: demoted to `col_N`.

---

## Files changed

| File | Layer | Change |
|---|---|---|
| `Backend/src/main/java/.../service/NormalizedTableService.java` | service/ingest | Core fix: zero-overlap rejection, child/leaf preference, broad spanning demotion, new records/constants |
| `Backend/src/main/java/.../service/TableIngestMetrics.java` | service/metrics | Added 3 new metric fields |
| `Backend/src/test/java/.../service/NormalizedTableIngestTest.java` | test | Created: Tests 1-6, 9, and additional |
| `Backend/src/test/java/.../service/NormalizedTableSuppressionTest.java` | test | Created: Test 10 (suppression/metrics) |
| `Backend/src/test/java/.../service/NoHardcodedLexiconInTableNormalizerTest.java` | test | Created: Test 11 (no-hardcode audit) |
| `Backend/src/test/java/.../diagnostic/HeaderMapping23P0DiagnosticTest.java` | test | Replaced: Tests 7-8 (schedule/curriculum fixtures) |

---

## Implementation summary

### Fix 1 — Zero-overlap fragment rejection

In `inferHeadersFromCoordinates`, for each header row cell returned by `bestHeaderCellForSlot`:

```java
// Before (old code — nearest-center fallback allowed zero-overlap fragment to reach final selection):
RawTableCell best = bestHeaderCellForSlot(headerRow, minX, maxX, col);
if (best != null && best.text() != null && !best.text().isBlank()) {
    fragments.add(best.text().trim().replaceAll("\\s+", " "));
}

// After (new code — zero-overlap rejected):
double ov = overlap(best.x(), best.xEnd(), slotX, slotXEnd);
if (ov <= 0.0) {
    zeroOverlapRejected++;  // metric
    continue;               // rejected
}
```

### Fix 2 — Broad spanning parent detection and demotion

New constant: `BROAD_HEADER_CELL_FACTOR = 2.5`. A fragment's source cell is "broad" if `srcWidth > slotWidth × 2.5`.

Broad cells go into `parentFrags`; non-broad cells go into `childFrags`. `childFrags` wins if non-empty.

### Fix 3 — child/leaf preference over broad parent

```java
List<CoordFragment> preferred = childFrags.isEmpty() ? parentFrags : childFrags;
```

### Fix 4 — `applyBroadSpanningFallback` prevents collision-suffix masking

New constant: `BROAD_HEADER_REUSE_MIN = 3`. After building preliminary slots:

```java
// Find runs of >= 3 consecutive slots with the same non-generic header text.
// These came from one broad spanning physical cell → demote entire run to col_N.
if (runLen >= BROAD_HEADER_REUSE_MIN) {
    for (int k = i; k < j; k++) demote[k] = true;
}
```

This prevents `key`, `key_2`, `key_3` collision-suffix masking. Duplicate keys from genuinely distinct narrow cells (run < 3) still receive suffix.

### New records

```java
private record CoordFragment(String text, double srcX, double srcXEnd) {}
private record CoordInferenceResult(List<CoordinateHeaderSlot> slots,
        int zeroOverlapRejected, int broadSpanningDemoted, int collisionSuffixPrevented) {}
private record BroadDemotionResult(List<CoordinateHeaderSlot> slots,
        int demotedCount, int collisionSuffixPrevented) {}
```

### New constants

```java
private static final double BROAD_HEADER_CELL_FACTOR = 2.5;
private static final int BROAD_HEADER_REUSE_MIN = 3;
```

---

## Before/after: schedule cells_json keys

### Before (23P0 diagnostic)

```
bắt đầu: NguyenChiNgan
bắt đầu_2: 08/09/2025
bắt đầu_3: 2           ← weekday (Q2 key value)
bắt đầu_4: 1-3
bắt đầu_5: H310
```

### After (expected with fix)

```
GiangVien: NguyenChiNgan    ← correct aligned child header (or col_N)
NgayBD: 08/09/2025          ← correct child header (or col_N)
Thu: 2                      ← weekday key now identifiable (fixes Q2)
Tiet: 1-3
Phong: H310
```

Root: `bắt đầu` had zero overlap with cols 5-9 → Fix 1 rejects it → proper child headers selected.

---

## Before/after: curriculum header keys

### Before (23P0 diagnostic)

```
hoa, nganh: Kien Truc K45: <course_code>
hoa, nganh: Kien Truc K45_2: <course_title>
hoa, nganh: Kien Truc K45_3: <credits>
...
```

### After (expected with fix — child headers available)

```
MaHP: KTR3185
TenHP: DoAnKienTrucTHDCN
SoTC: 5
HocKy2: x
```

When no child headers, broad demotion applies:

```
col_1: KTR3185
col_2: DoAnKienTrucTHDCN
col_3: 5
col_4: x
```

Both forms are correct. The broad cohort context cell (K45/K46) does not become a per-column key.

---

## Zero-overlap fragment handling

- `bestHeaderCellForSlot` returns the best cell per header row (including nearest-center fallback).
- The fix adds an overlap check BEFORE adding to fragments.
- Cells with `overlap(cell.x, cell.xEnd, slotX, slotXEnd) <= 0` are rejected and counted in `zeroOverlapRejected`.
- These cells no longer participate in the "last fragment wins" selection.

---

## Spanning parent handling

- Fragment source cell classified as broad if `srcWidth > slotWidth * BROAD_HEADER_CELL_FACTOR`.
- Broad fragments → `parentFrags`; narrow fragments → `childFrags`.
- `childFrags` preferred. `parentFrags` used only as fallback when no child exists.
- After slot building, `applyBroadSpanningFallback` finds runs ≥ `BROAD_HEADER_REUSE_MIN` and demotes.

---

## Collision suffix handling

- Current `disambiguateCoordinateSlots` still suffixes remaining duplicates (unchanged).
- Broad spanning reuse is demoted BEFORE disambiguation, so no `key_2`, `key_3` suffix from one source.
- Legitimate duplicate headers from distinct narrow cells (e.g., 2× "Val" from 2 cells) are still suffixed as `Val`, `Val_2` (acceptable).
- Broad runs of ≥ 3 become `col_1`, `col_2`, `col_3` — generic, no misleading suffixes.

---

## New metrics

| Metric | Location | Description |
|---|---|---|
| `zeroOverlapHeaderRejected` | `QualityStats`, `TableIngestMetrics` | Fragments rejected for zero x-overlap |
| `broadSpanningHeaderDemoted` | `QualityStats`, `TableIngestMetrics` | Slots demoted by broad-spanning run detection |
| `collisionSuffixPrevented` | `QualityStats`, `TableIngestMetrics` | Same as broadSpanningDemoted (per-slot count) |

---

## No-hardcode proof

- No Vietnamese course codes, group labels, domain terms, or page numbers in production source.
- `BROAD_HEADER_CELL_FACTOR = 2.5` and `BROAD_HEADER_REUSE_MIN = 3` are generic numeric constants.
- Comments in source are in English only.
- No domain-specific label maps.

Test `NoHardcodedLexiconInTableNormalizerTest.test11_noHardcodedLexicon_inProductionNormalizerSource()` passes.

---

## Tests run

| Command | Result | Note |
|---|---|---|
| `.\mvnw.cmd -DskipTests compile` | PASS | 121 source files, 1 deprecation warning (pre-existing) |
| `.\mvnw.cmd "-Dtest=NormalizedTableIngestTest,NormalizedTableSuppressionTest,NoHardcodedLexiconInTableNormalizerTest,KLTN.RAG_CHATBOT_BE.diagnostic.HeaderMapping23P0DiagnosticTest" test` | **PASS 21/21** | Focused 23P1 tests |
| `.\mvnw.cmd "-Dtest=HybridKeywordSearchTest,...(full targeted suite)" test` | **PASS 91/91** | Full targeted suite, 0 failures, 0 errors |
| `docker compose up --build -d backend` | NOT RUN | Requires live environment |
| Fresh re-ingest SoTayHocVu PDF | NOT RUN | Requires live environment |
| Runtime Q1-Q8 verification | NOT RUN | Requires live environment |

---

## Fresh ingest audit (expected, not verified)

After fresh re-ingest with this fix:

| Metric | Expected |
|---|---|
| `structuredTablesNormalized` | > 0 |
| `pdfTablesUsingMarkdownBridge` | 0 |
| `table_row_group` chunk type | 0 |
| `text_table_like` chunk type | 0 |
| `zeroOverlapHeaderRejected` | > 0 (per schedule table) |
| `broadSpanningHeaderDemoted` | > 0 (per curriculum table, if no child headers) |
| `cells_json` schedule row weekday key | `Thu` or `col_N` (not `bắt_đầu_N`) |
| `cells_json` curriculum row keys | child headers or `col_N` (not cohort_N) |

---

## Q1-Q8 runtime table (expected, NOT verified without fresh ingest)

| Q | Question scope | Expected behavior after fix | Status |
|---|---|---|---|
| Q1 | Kỹ năng mềm Nhóm 2 | GiangVien key correct → Hoàng Ngô Tự Do | Expected PASS |
| Q2 | Kỹ năng mềm Nhóm 4 weekday | Thu key = 6 → thứ 6 identified | Expected PASS (was PARTIAL) |
| Q3 | Nhóm 1 vs Nhóm 2 compare | Both rows have correct GiangVien/Phong keys | Expected PASS |
| Q4 | K45-K48 enrollment dates | Unrelated to header fix | Expected PASS |
| Q5 | Kiến trúc K46 HK2 | Curriculum keys improved; K46 context not embedded under K45 key | Expected PARTIAL→PASS (needs verify) |
| Q6 | Công nghệ sinh học K46 | Similar to Q5 | Expected PARTIAL |
| Q7 | KTR3185 name and credits | No merge regression per test8 | Expected PASS |
| Q8 | USD/VND rate | OOS refusal | Expected PASS |

---

## Q2 result

Q2 failure root cause was: weekday value `6` stored under key `bắt đầu_3` → LLM couldn't identify it as weekday.

After Fix 1: `bắt đầu` (zero-overlap fragment) rejected for the weekday slot → `Thu` child header selected (or `col_N`) → LLM can match query "thứ mấy?" to the value under `Thu` key.

**Expected: Q2 PASS after fresh ingest.** (Runtime verification NOT RUN.)

---

## Q5 status

Q5 (curriculum cohort/semester filtering) is still expected PARTIAL because:
1. The broad cohort header is demoted to `col_N` OR child headers are used.
2. When child headers exist (MaHP, TenHP, SoTC, etc.), curriculum rows should be well-structured.
3. If cohort context (K45/K46) was previously embedded in keys and is now lost, retrieval may need additional scoped-context help.
4. Q5 requires separate scoped-context extraction work (not in scope for 23P1).

---

## KTR3185 regression guard

Test `test8_curriculumFixture_broadCohortHeaderNotReusedAcrossSlots` asserts:
- KTR3185 row does NOT contain KTR3273 or KTR4015 values.
- DoAnKienTrucTHDCN and credits "5" are in same row as KTR3185.

Passes: PASS.

---

## Latency notes

- `inferHeadersFromCoordinates` now does 2 passes (fragment collection + broad demotion) instead of 1.
- Both passes are O(headerRows × maxCols) — same asymptotic complexity.
- No additional PDF parsing, embedding, or Qdrant calls.
- No memory overhead beyond constant-factor increase for `CoordFragment` records.
- Impact on production (1 core, 1.5GB RAM): negligible.

---

## Known limitations

1. `applyBroadSpanningFallback` uses run-length heuristic (≥ 3 consecutive same-header). Rare legitimate tables with 3+ identical column headers (e.g., "Yes/Yes/Yes" for 3 boolean columns) could be incorrectly demoted to `col_N`. This is acceptable given the structural rarity and correctness tradeoff.
2. The "last row wins" selection still applies within the non-broad (child) fragment pool. If multiple child rows exist, the deepest (last header row) child wins. This is generally correct for tables with parent→child header hierarchy.
3. Fresh runtime verification was not run in this session. Results are expected based on structural analysis and synthetic test fixtures.

---

## Next recommended task

1. **Task 23P2**: Fresh re-ingest SoTayHocVu PDF and verify Q1-Q8 runtime table. Document exact `cells_json` keys observed for schedule and curriculum rows. Confirm Q2 PASS and Q5 status.
2. **Task 23P3** (if Q5 still PARTIAL): Extract broad cohort/context cells into `groupContext` for curriculum tables (instead of dropping). This would improve Q5 LLM filtering by making cohort labels available in canonical text without polluting column keys.
