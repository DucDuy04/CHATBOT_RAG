# RAG — PDF sparse continuation table merge fix 23B3

**File:** `docs/eval/results/RAG_PDF_SPARSE_CONTINUATION_FIX_23B3_20260516.md`  
**Report:** `reports/refactor/CURSOR_REPORT_23B3_PDF_SPARSE_CONTINUATION_FIX.md`  
**Ngày:** 2026-05-16  
**PDF:** `docs/eval/manual/HeThongQuanLyYeuCauPhucKhao.pdf`

---

## Before (23B2)

| Item | 23B2 |
|------|------|
| Page 13 table | ACCEPTED (9 rows) |
| Page 14 table | REJECTED `too-many-empty-cells(65/99>66%)` |
| Merge 14→13 | **Không** |
| Target question | PARTIAL (9/12 entity, thiếu TuiBaiThi, BienBan) |
| Merge khác | page 18→17 REPEATED_HEADER |

---

## Root cause

1. Fix 23B chỉ merge khi `isContinuationTable` / `isRepeatedHeaderContinuationTable` pass **trước** `isUsableTable`.
2. Bảng page 14 fail `isUsableTable` vì >66% cell rỗng → `continue` reject, không vào merge.
3. Header lặp page 14 sparse (1 non-empty header cell) không khớp header row đầy đủ.
4. Hàng continuation PDF 3 cột: cột 0 rỗng, text rải cột 1–2; compact “non-empty only” không đủ ≥2 cột/hàng.
5. `hasIndependentSectionHeadingInTable` quét toàn bảng → heading `5. Quan hệ…` ở cuối bbox Tabula chặn nhầm merge.

---

## Parser design (23B3)

- Gom logic merge vào `attemptCrossPageMerge` (DATA_ROWS / REPEATED_HEADER / **SPARSE_CONTINUATION**).
- `isSparseContinuationCandidate`: chỉ khi reject `too-many-empty-cells` hoặc `header-row-too-sparse`, trang liền kề, có ≥2 data rows sau `convertSparseContinuationRows`.
- `extractSparseLogicalRowCells`: map 3 cột PDF; `convertSparseContinuationRows` ghép fragment thuộc tính vào hàng trước.
- Heading guard: chỉ kiểm tra heading độc lập ở **hàng substantive đầu tiên**.
- Không nới global 60% empty threshold; không hardcode tên entity.

---

## Code change summary

| File | Thay đổi |
|------|----------|
| `DocumentParserService.java` | SPARSE_CONTINUATION merge path, sparse row extraction, heading guard, `extractMarkdownHeaderRow` stripLeading |
| `DocumentParserCrossPageMergeTest.java` | 5 unit tests (repeated header, sparse, negative, late heading) |
| `TabulaTableTestHelper.java` | Build Tabula tables in tests |

---

## Tests

| Test | Kết quả |
|------|---------|
| `ParserAndOrderingTests` | PASS |
| `DocumentParserCrossPageMergeTest` (5 tests) | PASS |

---

## Runtime upload / index

| Field | Value |
|-------|-------|
| status | INDEXED |
| chunkCount | **84** (23B2: 82) |
| documentId | `586364e7-2e79-4dd6-b204-bdb7465b08fb` |
| Artifact | `_run_23b3_results.json` |

---

## Parser log evidence

```text
[Parse] Table ACCEPTED page=13: rows=9 cols=3 ...
[Parse] Table MERGED continuation page=14 → page=13: mode=SPARSE_CONTINUATION rows=33
```

(Page 14 không còn log REJECTED cho bảng 33 rows — đã merge trước reject.)

---

## SQL / Qdrant evidence

| Check | Result |
|-------|--------|
| Qdrant 12/12 entity | **12/12** |
| Qdrant page-14 quartet | **4/4** |
| SQL chunk preview (80 rows) | 10/12 entity names in sample (KetQuaHocTap, YeuCauPhucKhao có thể nằm chunk khác / encoding) |

---

## Target chat

**Câu hỏi:** Bảng các thực thể và thuộc tính gồm những thực thể nào?

| Metric | Value |
|--------|-------|
| Verdict | **PASS** |
| Entities in answer | **12/12** |
| Page-14 in answer | **4/4** |
| sourceCount | 5 |

---

## Regression smoke

| Case | Pass |
|------|------|
| Mã SV LÊ ĐỨC DUY | Yes |
| Cột NguoiDung | Yes (9/9) |
| OOS USD/VND | Yes |

---

## Conclusion

**23B3 PASS** — sparse continuation page 14 merge vào page 13; ingest và target question đạt 12 entity; không sửa prompt/retrieval. Regression smoke PASS.
