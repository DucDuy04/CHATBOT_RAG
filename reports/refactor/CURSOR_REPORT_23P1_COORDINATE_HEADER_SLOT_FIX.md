# CURSOR_REPORT_23P1 — Coordinate Header Slot Fix

## 1. Mức độ hiểu task

- Hiểu: **95%**
- Chắc chắn:
  - Root cause từ 23P0: `bestHeaderCellForSlot` trả về zero-overlap fragment qua nearest-center fallback; fragment đó vào `fragments` list và được chọn làm header cuối cùng ("last row wins").
  - Pipeline: `inferHeadersFromCoordinates` → `CoordinateHeaderSlot` → `mapCellsFromCoordinates` → `cells_json`.
  - Fix 1 (zero-overlap rejection) giải quyết trực tiếp `bắt đầu_*` schedule problem.
  - Fix 2 (broad spanning demotion) giải quyết curriculum cohort key problem.
  - Fix 3 (child/leaf preference) bổ sung cho Fix 2 khi child headers tồn tại.
  - Fix 4 (collision-suffix prevention) được handle bởi Fix 2 trước disambiguation.
- Giả định:
  - Thực tế runtime (fresh re-ingest) chưa được verify vì không có live environment trong session này.
  - Q2 PASS là expected nhưng chưa được xác nhận bằng runtime.
- Thiếu:
  - Live runtime verification sau re-ingest.
  - Qdrant payload audit post-ingest.

## 2. Tóm tắt yêu cầu

Sửa `inferHeadersFromCoordinates()` trong `NormalizedTableService` để:
1. Reject zero-overlap header fragments (Fix 1).
2. Prefer child/leaf headers over broad spanning parent (Fix 3).
3. Detect and demote broad spanning parent headers reused across >= 3 adjacent slots (Fix 2).
4. Prevent collision-suffix masking from hiding wrong parent reuse (Fix 4).
5. Add metrics: `zeroOverlapHeaderRejected`, `broadSpanningHeaderDemoted`, `collisionSuffixPrevented`.
6. Tests 1-11 pass.
7. No hardcoded domain literals in production.

## 3. Hiện trạng trước khi sửa

- `bestHeaderCellForSlot` có thể trả về cell với zero overlap (via nearest-center fallback).
- Fragment đó được add vào `fragments` list bất kể overlap.
- "Last row wins" selection → zero-overlap fragment từ row cuối được chọn làm header.
- `disambiguateCoordinateSlots` suffix collision → `bắt_đầu_2`, `bắt_đầu_3`, ...
- Curriculum: wide K45 cell overlap nhiều slots → chọn làm header cho tất cả → `K45_2`, `K45_3`, ...

## 4. Nguyên nhân gốc xác nhận từ source

### 4.1 Schedule (bắt đầu_* keys)

Trong `inferHeadersFromCoordinates` cũ (lines 1384-1437):

```java
for (int r = headerStart; r < dataStart; r++) {
    RawTableCell best = bestHeaderCellForSlot(headerRow, minX, maxX, col);
    if (best != null && best.text() != null && !best.text().isBlank()) {
        fragments.add(best.text().trim().replaceAll("\\s+", " "));
        // BUG: no overlap check → zero-overlap cell added
    }
}
```

`bestHeaderCellForSlot` trả về `bắt đầu` cho slots 5-9 (lecturer, date, weekday, period, room) vì:
- `bắt đầu` là cell duy nhất trong header row cuối có overlap=0 với các slots đó.
- Score = `1/(1+|center_dist|)` > 0 → cell được chọn bởi nearest-center fallback.
- Fragment được add → trở thành "last fragment" → selected.

### 4.2 Curriculum (K45_* keys)

Wide cohort cell có positive overlap với nhiều slots (từng slot đều có overlap > 0 với cell spanning toàn table). Cell được add cho MỌI slot. Không có child headers → mọi slot dùng cohort cell → `K45`, `K45_2`, `K45_3`, ...

## 5. Chiến lược sửa đã chọn

**Fix 1 (Zero-overlap rejection)**: Kiểm tra `overlap > 0.0` trước khi add fragment. Đơn giản, direct, không có side effect.

**Fix 2 (Broad demotion)**: Sau khi build preliminary slots, detect run ≥ BROAD_HEADER_REUSE_MIN=3 với same header → demote to `col_N`. Generic heuristic không dùng domain knowledge.

**Fix 3 (Child preference)**: Phân loại fragments thành child (srcWidth/slotWidth ≤ BROAD_HEADER_CELL_FACTOR=2.5) và parent (broad). Prefer child.

**Fix 4 (Collision prevention)**: Handled by Fix 2 (broad runs demoted before disambiguation).

**Minimal scope**: Không thay đổi `bestHeaderCellForSlot`, `mapCellsFromCoordinates`, `mergeWrappedRowsWithCoordinateGuard`, hay bất kỳ phần nào khác.

## 6. Danh sách file đã đọc

| File | Mục đích đọc | Kết luận chính |
|---|---|---|
| `docs/eval/results/CELLS_JSON_HEADER_MAPPING_DIAGNOSTIC_23P0_20260526.md` | Root cause từ 23P0 | Zero-overlap fragment trong "last row wins" là root cause |
| `reports/refactor/CURSOR_REPORT_23P0_CELLS_JSON_HEADER_MAPPING_DIAGNOSTIC.md` | Confirm diagnosis | Stage C là bug layer |
| `docs/eval/results/RUNTIME_VERIFY_23O_AFTER_23N_20260526.md` | Runtime evidence Q2/Q5 | Q2 fail vì weekday key sai; Q5 fail vì cohort mix |
| `Backend/src/main/java/.../NormalizedTableService.java` | Locate `inferHeadersFromCoordinates` | Main target method identified |
| `Backend/src/main/java/.../TableIngestMetrics.java` | Biết existing metrics | Biết cần add 3 new fields |
| `Backend/src/main/java/.../RawTableModel.java` | Confirm record structure | `RawTableModel` record fields |
| `Backend/src/main/java/.../RawTableCell.java` | Confirm coordinate fields | `x`, `xEnd`, `width`, `centerX()`, `hasCoordinates()` |
| `Backend/src/main/java/.../RawTableRow.java` | Confirm row structure | Simple `List<RawTableCell>` |
| `Backend/src/main/java/.../NormalizedTableRow.java` | Confirm output record | `cells_json` via `NormalizedTableService.cellsToJson()` |
| `Backend/src/main/java/.../LogicalTableState.java` | Confirm continuation state | Not affected by this fix |

## 7. Danh sách file đã sửa

| File | Sửa để làm gì | Layer |
|---|---|---|
| `NormalizedTableService.java` | Core fix: zero-overlap rejection, child preference, broad demotion, new records/constants, updated QualityStats | service |
| `TableIngestMetrics.java` | Add 3 new metric fields, update reset(), addQualityStats() | service/metrics |

## 8. Diff thay đổi

### `NormalizedTableService.java`

#### 8.1 New constants (added after MAX_ROW_PAGE_SPAN)

```diff
+    private static final double BROAD_HEADER_CELL_FACTOR = 2.5;
+    private static final int BROAD_HEADER_REUSE_MIN = 3;
```

#### 8.2 New internal records (added before CoordinateHeaderSlot)

```diff
+    private record CoordFragment(String text, double srcX, double srcXEnd) {}
+    private record CoordInferenceResult(List<CoordinateHeaderSlot> slots,
+            int zeroOverlapRejected, int broadSpanningDemoted, int collisionSuffixPrevented) {}
```

#### 8.3 QualityStats — 3 new fields

```diff
     public record QualityStats(
         ...
         int valuesDroppedCount,
+        int zeroOverlapHeaderRejected,
+        int broadSpanningHeaderDemoted,
+        int collisionSuffixPrevented
     ) {
         static QualityStats empty() {
-            return new QualityStats(0,0,...,0);  // 25 fields
+            return new QualityStats(0,0,...,0,0,0,0);  // 28 fields
         }
     }
```

#### 8.4 inferHeadersFromCoordinates — rewritten

**Before:**
- Returns `List<CoordinateHeaderSlot>`
- Collects fragments without overlap check
- No broad parent detection

**After:**
- Returns `CoordInferenceResult` (slots + metrics)
- Fix 1: `if (ov <= 0.0) { zeroOverlapRejected++; continue; }`
- Fix 3: classify broad vs child, `preferred = childFrags.isEmpty() ? parentFrags : childFrags`
- Fix 2+4: calls `applyBroadSpanningFallback` before `disambiguateCoordinateSlots`

#### 8.5 New method `applyBroadSpanningFallback`

```java
private static BroadDemotionResult applyBroadSpanningFallback(List<CoordinateHeaderSlot> slots) {
    // Find runs of >= BROAD_HEADER_REUSE_MIN slots with same non-generic header
    // Demote entire run to col_N
}
```

#### 8.6 normalizeRawTable — update call site

```diff
-    List<CoordinateHeaderSlot> slots = inferHeadersFromCoordinates(table, scanFrom, dataStart, maxCols);
+    CoordInferenceResult inferResult = inferHeadersFromCoordinates(table, scanFrom, dataStart, maxCols);
+    List<CoordinateHeaderSlot> slots = inferResult.slots();
```

```diff
-    QualityStats stats = buildStats(outRows, ..., attribution);
+    QualityStats stats = buildStats(outRows, ..., attribution,
+            inferResult.zeroOverlapRejected(),
+            inferResult.broadSpanningDemoted(),
+            inferResult.collisionSuffixPrevented());
```

#### 8.7 buildStats — add 3 params

```diff
-    private static QualityStats buildStats(..., HeaderAttribution attribution) {
+    private static QualityStats buildStats(..., HeaderAttribution attribution,
+            int zeroOverlapRejected, int broadSpanningDemoted, int collisionSuffixPrevented) {
```

### `TableIngestMetrics.java`

```diff
+    private int zeroOverlapHeaderRejected;
+    private int broadSpanningHeaderDemoted;
+    private int collisionSuffixPrevented;

     public void reset() {
         ...
+        zeroOverlapHeaderRejected = 0;
+        broadSpanningHeaderDemoted = 0;
+        collisionSuffixPrevented = 0;
     }

     public void addQualityStats(NormalizedTableService.QualityStats stats) {
         ...
+        zeroOverlapHeaderRejected += stats.zeroOverlapHeaderRejected();
+        broadSpanningHeaderDemoted += stats.broadSpanningHeaderDemoted();
+        collisionSuffixPrevented += stats.collisionSuffixPrevented();
     }
```

## 9. Ảnh hưởng sau sửa

**Behavior thay đổi:**
- `cells_json` keys cho schedule rows: `bắt đầu_N` family eliminated → proper child headers or `col_N`.
- `cells_json` keys cho curriculum rows: wide cohort header demoted → child headers or `col_N`.
- `canonicalText` (used in Qdrant payload and LLM prompt) now has structurally correct keys.

**Behavior giữ nguyên:**
- Values never dropped (value extraction unchanged).
- Row structure and row merge logic unchanged.
- Markdown path (`normalize()`) unchanged.
- `buildCanonicalText()` unchanged (improved by better keys upstream).
- `RawTableModel`, `RawTableCell`, `RawTableRow` unchanged.
- All existing test scenarios pass (A/B/C invariant, suppression, etc.).

**Điều kiện:**
- Improvement visible only after fresh re-ingest (old Qdrant data has old keys).
- `col_N` fallback is used when:
  - All header fragments for a slot have zero overlap.
  - A broad spanning run is detected (≥ 3 consecutive same header).

**Memory/CPU:** Minimal O(maxCols × headerRows) extra work per table. CoordFragment records are small and GC'd after ingest. No change to embedding/Qdrant calls.

## 10. Edge cases đã xem xét

- **Tất cả fragments zero overlap**: Slot falls back to `col_N`. ✓
- **Chỉ broad parent, không có child**: Broad run detected (if ≥ 3 adjacent) → all `col_N`. ✓
- **Child narrow header tồn tại**: Child wins over broad parent. ✓
- **2 distinct narrow cells cùng text**: Suffix `Val`, `Val_2` kept (run length=2 < BROAD_MIN=3). ✓
- **A/B/C invariant**: No broad run, correct headers selected. ✓
- **PDF parse fail**: Not affected (this layer only runs after successful parse). ✓
- **Qdrant unavailable**: Not affected (coordinate path runs before embedding). ✓
- **Table with fewer than BROAD_HEADER_REUSE_MIN columns**: No demotion occurs. ✓
- **All header rows have same broad cell**: All demoted to `col_N`. Values preserved. ✓
- **K45 và K46 cohort headers on different rows**: Each row handled independently by `applyBroadSpanningFallback`. ✓

## 11. Kết quả kiểm tra

| Command | Kết quả | Ghi chú |
|---|---|---|
| `.\mvnw.cmd -DskipTests compile` | PASS | 121 files, 1 pre-existing deprecation warning |
| `.\mvnw.cmd "-Dtest=NormalizedTableIngestTest,...,HeaderMapping23P0DiagnosticTest" test` | **PASS 21/21** | Focused 23P1 tests |
| `.\mvnw.cmd "-Dtest=...(full targeted suite)" test` | **PASS 91/91** | Full suite, 0 failures, 0 errors |
| `docker compose up --build -d backend` | NOT RUN | Live environment required |
| Fresh re-ingest | NOT RUN | Live environment required |
| Q1-Q8 runtime | NOT RUN | Live environment required |

## 12. Rủi ro còn lại

- **Runtime unverified**: Fresh re-ingest and Q1-Q8 not run. Structural fix confirmed via synthetic tests.
- **Q5 still potentially PARTIAL**: Curriculum cohort context may be lost from keys → K46 queries still challenging. Needs Task 23P2/23P3.
- **Broad demotion false positive**: Tables with ≥ 3 identical narrow column names would be incorrectly demoted. Acceptable tradeoff (rare in practice).
- **"Last row wins" within child pool**: If multiple non-broad header rows exist, the deepest wins. Could still select a wrong child if table has confusing multi-row header structure.

## 13. Đề xuất tiếp theo

1. **Task 23P2**: Fresh re-ingest + runtime verification Q1-Q8. Confirm Q2 PASS (weekday "6" under "Thu" key). Confirm Q5 status.
2. **Task 23P3**: Extract broad cohort/context headers into `groupContext` (instead of dropping). Improves Q5 without polluting column keys.
3. **Optional**: Consider promoting broad spanning cell text to table's `groupContext` when demoting slots, so cohort info is still available in canonical text.
