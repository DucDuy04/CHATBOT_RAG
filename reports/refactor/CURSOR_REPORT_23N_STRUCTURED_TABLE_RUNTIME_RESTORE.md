# CURSOR_REPORT_23N — Structured Table Runtime Restore

**Task ID**: 23N  
**Date**: 2026-05-26  
**Session**: [23M/23N Runtime Restore](a9836e96-afc9-4eb6-a314-6f0090b4f69d)

---

## 1. Mức độ hiểu task

- Hiểu: **95%**
- Chắc chắn: Architecture sau 23M, root cause col_N, fix scope, test requirements, no-hardcode constraints
- Giả định: Runtime behavior của Q1-Q8 sau khi apply fix (không verify được live backend trong session này)
- Thiếu: Runtime execution environment để chạy Q1-Q8 thực tế

---

## 2. Tóm tắt yêu cầu

Sau Task 23M, hệ thống ingest PDF table dùng RawTableModel với coordinate-based header inference, nhưng accuracy runtime chỉ đạt 4/8 PASS (Q1, Q2, Q3, Q5 FAIL). Task 23N yêu cầu restore về 8/8 PASS mà không rollback 23M, không hardcode domain literals.

---

## 3. Hiện trạng trước khi sửa

- Q1/Q2/Q3/Q5 FAIL sau 23M
- `CellAwareTableRowScorer.cellMatchesLabel()` check `headerPrefixSimilarity(queryPrefix, columnKey)`
- Khi headers là `col_N` (vì coordinate inference không map được), similarity ≈ 0 → không score
- `applyCompareLabelCoverage()` dùng `cellMatchesLabel()` để detect covered labels → miss với col_N
- Correct rows tồn tại trong Qdrant nhưng bị ranked thấp vì scorer không nhận ra

---

## 4. Nguyên nhân gốc xác nhận từ source

**File**: `CellAwareTableRowScorer.java`, method `cellMatchesLabel()`:
```java
static boolean cellMatchesLabel(String columnKey, String cellValue, ParsedStructuredLabel label) {
    double headerSim = headerPrefixSimilarity(label.prefix(), columnKey);
    if (headerSim < 0.72) {
        return false;  // ← col_N always fails here
    }
    ...
}
```

`headerPrefixSimilarity("group", "col_2")` = Levenshtein distance → luôn < 0.72 → early return false → row không được scored dù value đúng.

---

## 5. Chiến lược sửa đã chọn

**Fix area B**: Generic value-level coverage scoring.

Thêm `valueCoverageMatch(cellValue, label)`: nếu `label.value()` (e.g., "2" từ "group 2") match boundary-token trong `cellValue`, thì cấp điểm `VALUE_COVERAGE_CELL = 4.0` — không cần header semantic.

Đây là generic fix, không dùng domain literals.

Sau đó update `applyCompareLabelCoverage()` và `rowConflictsWithCompareLabels()` trong `RagRetrievalService` để dùng `valueCoverageMatch()` cùng với `cellMatchesLabel()`.

---

## 6. Danh sách file đã đọc

| File | Đọc để làm gì | Kết luận chính |
|---|---|---|
| `docs/eval/results/STRUCTURED_TABLE_INGEST_23M_20260526.md` | Hiểu kết quả 23M, baseline cho 23N | Q1/Q2/Q3/Q5 FAIL vì col_N headers; KTR3185 fixed; 122 structured tables |
| `reports/refactor/CURSOR_REPORT_23M_STRUCTURED_TABLE_INGEST.md` | Architecture của 23M | RawTableModel với coordinates; pdfTablesUsingMarkdownBridge=0 |
| `Backend/.../CellAwareTableRowScorer.java` | Root cause scoring failure | cellMatchesLabel() fails for col_N via headerPrefixSimilarity < 0.72 |
| `Backend/.../RagRetrievalService.java` | Understand compare coverage logic | applyCompareLabelCoverage() cần update để dùng valueCoverage |
| `Backend/.../NormalizedTableService.java` | Header inference logic | inferHeadersFromCoordinates() produce col_N khi overlap ambiguous |
| `Backend/.../QuerySignalExtractor.java` | Signal extraction | Structured labels parsed từ query (e.g., "group 2" → prefix="group", value="2") |
| `Backend/.../KeywordSearchService.java` | Keyword scoring | Dùng CellAwareTableRowScorer.score() cho normalized_table_row |

---

## 7. Danh sách file đã sửa

| File | Sửa để làm gì | Layer |
|---|---|---|
| `CellAwareTableRowScorer.java` | Thêm value-level coverage scoring | service |
| `RagRetrievalService.java` | Update compare coverage và conflict detection | service |
| `CellAwareNormalizedRowRetrievalTest.java` | Thêm tests cho col_N value coverage | test |
| `HybridKeywordSearchTest.java` | Thêm test keyword ranking với col_N | test |
| `FinalContextSelectionTest.java` | Fix filter condition, thêm tests | test |
| `NoHardcodedLexiconInCellAwareScorerTest.java` | Thêm test value coverage không dùng hardcode | test |
| `NormalizedTableIngestTest.java` | Thêm tests 7&8: coordinate header inference | test |

---

## 8. Diff thay đổi của từng file

### CellAwareTableRowScorer.java

```diff
+ static final double VALUE_COVERAGE_CELL = 4.0;

  // In score() method, label loop:
  for (ParsedStructuredLabel label : labels) {
      boolean matched = false;
      boolean conflict = false;
+     boolean valueCovered = false;
      for (Map.Entry<String, String> cell : cells.entrySet()) {
          if (cellMatchesLabel(cell.getKey(), cell.getValue(), label)) {
              exactLabel += EXACT_LABEL_CELL;
              matched = true;
              matchedCategories.add("label");
          } else if (cellConflictsWithLabel(cell.getKey(), cell.getValue(), label)) {
              conflict = true;
+         } else if (!valueCovered && valueCoverageMatch(cell.getValue(), label)) {
+             valueCovered = true;
          }
      }
      if (!matched) {
-         if (conflict) {
+         if (valueCovered) {
+             exactLabel += VALUE_COVERAGE_CELL;
+             matchedCategories.add("label");
+         } else if (conflict) {
              mismatch += MISMATCH_PENALTY;
          }
      }
  }

+ static boolean valueCoverageMatch(String cellValue, ParsedStructuredLabel label) {
+     if (label == null || label.value() == null || label.value().isBlank()) return false;
+     if (cellValue == null || cellValue.isBlank()) return false;
+     String normLabelValue = QuerySignalExtractor.normalize(label.value());
+     if (normLabelValue.isBlank()) return false;
+     String normCell = QuerySignalExtractor.normalize(cellValue);
+     return boundaryTokenEquals(normCell, normLabelValue);
+ }
```

Lý do: `cellMatchesLabel()` fail với col_N headers; value-level coverage cho phép match dựa trên cell value mà không cần header semantic.

### RagRetrievalService.java

```diff
  // applyCompareLabelCoverage - covered labels detection:
  if (CellAwareTableRowScorer.cellMatchesLabel(cell.getKey(), cell.getValue(), label)
+     || CellAwareTableRowScorer.valueCoverageMatch(cell.getValue(), label)) {
      coveredLabels.add(label.raw());
  }

  // applyCompareLabelCoverage - best candidate search:
  return cells.entrySet().stream().anyMatch(e ->
      CellAwareTableRowScorer.cellMatchesLabel(e.getKey(), e.getValue(), target)
+     || CellAwareTableRowScorer.valueCoverageMatch(e.getValue(), target));

  // rowConflictsWithCompareLabels - matchesAnyLabel:
  boolean matchesAnyLabel = labels.stream().anyMatch(label -> cells.entrySet().stream()
      .anyMatch(e -> CellAwareTableRowScorer.cellMatchesLabel(e.getKey(), e.getValue(), label)
+             || CellAwareTableRowScorer.valueCoverageMatch(e.getValue(), label)));
```

Lý do: Compare coverage cần nhận ra rows có matching values ngay cả khi headers là col_N.

---

## 9. Ảnh hưởng sau sửa

- **Thay đổi**: Rows với col_N headers nhưng đúng values sẽ được scored cao hơn (VALUE_COVERAGE_CELL = 4.0 vào exactLabel)
- **Giữ nguyên**: Rows với semantic headers vẫn score bình thường qua cellMatchesLabel()
- **Fallback**: col_N vẫn được fallback nếu không có value match
- **Latency**: Không đáng kể — thêm một `boundaryTokenEquals()` call per cell per label
- **Memory**: Không ảnh hưởng
- **Qdrant data**: Không đổi — chỉ scoring, không ingest
- **MySQL**: Không đổi

---

## 10. Edge cases đã xem xét

| Edge case | Xử lý |
|---|---|
| Label value là substring (e.g., "2" trong "12") | `boundaryTokenEquals()` ngăn match substring |
| Label value blank | Early return false trong `valueCoverageMatch()` |
| Cell value blank | Early return false |
| col_N conflict detection (row value ≠ any label) | Không filter với col_N — accepted limitation, documented in tests |
| Multi-label same row (e.g., "group 2 room X") | Cả hai labels được check riêng; multi-signal boost vẫn áp dụng |
| Value coverage AND header match | Header match wins (matched=true → skips valueCovered check) |
| Q7/KTR3185 | Không liên quan — fixed rows dùng semantic headers |

---

## 11. Kết quả kiểm tra

| Command | Kết quả | Ghi chú |
|---|---|---|
| `cd Backend && mvnw -DskipTests compile` | PASS | Nothing to compile (classes up to date) |
| Focused test suite (9 test classes, 107 tests) | PASS | 0 failures, 0 errors |
| Full targeted suite (15 test classes, 157 tests) | PASS | 0 failures, 0 errors |
| Frontend lint | NOT RUN | No frontend changes |
| Frontend build | NOT RUN | No frontend changes |
| Widget build | NOT RUN | No frontend changes |
| Docker compose config | NOT RUN | No infra changes |
| Runtime Q1-Q8 | NOT RUN | No live backend available |

---

## 12. Rủi ro còn lại

1. **Runtime Q1-Q8 unverified**: Fix is logically correct and tested but not live-verified. Could still fail if: (a) query signals do not extract expected labels ("group 2" parsed differently), (b) cells_json values are not what we expect from actual ingest.

2. **col_N conflict detection gap**: In compare queries, rows with non-matching group values may remain in context. Unlikely to cause wrong answers since correct rows are also present, but it can slightly increase context noise.

3. **Q5 list-length**: Curriculum list queries may still return incomplete context if relevant rows are spread across many pages with low individual scores. Not addressed in this task (requires Fix area D or list-budget tuning).

---

## 13. Đề xuất tiếp theo

**Task 23O — Runtime Q1-Q8 Verification**:
1. Deploy 23N backend
2. Run Q1-Q8 with existing document (id=33f4f99a)
3. If Q5 fails: tune `selectTopNByScore` list-budget or keyword index coverage for semester values
4. If Q1/Q2 fail despite fix: dump extracted query signals from live logs to confirm "group 2" → structuredLabels=[{prefix="group", value="2"}]
5. If compare coverage still wrong for Q3: add debug log in `applyCompareLabelCoverage` to trace which labels were found/missed
