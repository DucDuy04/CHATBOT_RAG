# RAG — PDF cross-page table continuation 23B (official result)

**File:** `docs/eval/results/RAG_PDF_CROSS_PAGE_TABLE_23B_20260515.md`  
**Report:** `reports/refactor/CURSOR_REPORT_23B_PDF_CROSS_PAGE_TABLE.md`  
**Ngày:** 2026-05-15

---

## 1. PDF test summary

| Item | Giá trị |
|------|---------|
| File | `HeThongQuanLyYeuCauPhucKhao.pdf` |
| Trong repo | **Không có** — chỉ có mô tả manual từ user |
| Manual baseline | Upload/index PASS; hầu hết câu PASS; **một** bảng trang 13–14 thiếu phần continuation |

---

## 2. Target testcase

**Câu hỏi:** `Bảng các thực thể và thuộc tính gồm những thực thể nào?`

**Expected (12 thực thể):** NguoiDung, ThongBao, SinhVien, GiangVien, MonHoc, KetQuaHocTap, PhongKhaoThi, YeuCauPhucKhao, **LichSuPhucKhao, Khoa, TuiBaiThi, BienBan** (trang 14).

---

## 3. Phân tích bắt buộc (pre-fix)

| # | Câu hỏi | Kết luận |
|---|---------|----------|
| 1 | Parser extract theo page hay document? | **Theo page** → `Map<Integer, String> pageContents` → `parseSections` |
| 2 | Trang 13 vs 14 là 1 hay 2 table? | **2 table Tabula** (typical) — trang 14 thường **lặp header** → không pass `isContinuationTable` (chỉ data row) |
| 3 | `document_tables` có record bảng? | **Không inspect runtime** (thiếu PDF/DB local) — pipeline lưu qua chunks + embed |
| 4 | Chunks có `LichSuPhucKhao`…? | **Giả định thiếu** trước fix (khớp symptom user) |
| 5 | Qdrant có payload trang 14? | **Không verify** — cùng lý do |
| 6 | Retrieval có chunk trang 14? | **Không verify** — nếu không có chunk thì retrieval không thể lấy |
| 7 | Context đủ nhưng LLM bỏ? | **Không phải root cause chính** — chưa có evidence context đủ |
| 8 | Sources không có trang 14? | **Root cause phía ingest:** parser/chunking tách 2 khối `[TABLE_*]` |
| 9 | Heuristic continuation? | **Đã có** nhưng **hẹp** — chỉ `isDataRow(firstRow)` |
| 10 | Sửa minimal? | **Có** — mở rộng merge repeated-header + merge trước `isUsableTable` |
| 11 | Rủi ro merge nhầm? | **Có** nếu fuzzy quá rộng — chỉ match **header row normalized exact** |
| 12 | Re-upload sau sửa? | **Có** — parser thay đổi |

---

## 4. Root cause

**Parser/chunking (Option B)** — không phải PromptBuilder / QueryAnalyzer / retrieval (chưa chứng minh thiếu context khi đã có chunk).

Cụ thể trong `DocumentParserService.parsePdf`:

1. **Continuation merge** chỉ kích hoạt khi `isContinuationTable` → `isDataRow(firstRow)`.
2. PDF cross-page thường **lặp header** ở trang N+1 → `convertTableToMarkdown` tạo header mới → `isContinuation = false`.
3. Bảng trang 14 thành **`[TABLE_START]` riêng** trên page 14 (hoặc bị `isUsableTable` reject nếu &lt;3 rows) → chunk/table_summary tách → retrieval/topK dễ chỉ lấy nhóm chunk trang 13.
4. User thấy đủ 8 entity đầu, thiếu 4 entity trang 14 — khớp **split table**, không khớp LLM-only.

---

## 5. Code change

**File:** `Backend/.../DocumentParserService.java`

| Thay đổi | Mục đích |
|----------|----------|
| Merge **trước** `isUsableTable` | Cho phép continuation nhỏ (&lt;3 rows) vẫn ghép vào trang trước |
| `isRepeatedHeaderContinuationTable` | Nhận header lặp trang N+1 |
| `convertTableDataRowsOnly(table, 1)` | Ghép chỉ data rows, bỏ header trùng |
| Bỏ điều kiện `lastTableHeader.equals(currentHeader[0])` sau convert | Tránh false-negative khi header bị ghi đè |

**Không hardcode** tên entity (`LichSuPhucKhao`, …).

**Test:** `ParserAndOrderingTests.merged_cross_page_entity_table_indexes_all_entity_rows`

---

## 6. Runtime result (PDF)

| Check | Kết quả |
|-------|---------|
| Re-upload `HeThongQuanLyYeuCauPhucKhao.pdf` | **NOT RUN** |
| Target question 12 entities | **NOT RUN** |
| Log `Table MERGED continuation page=14` | **NOT RUN** |

**Expected sau re-upload:** PASS hoặc PARTIAL tốt hơn nếu Tabula extract đúng 2 phần bảng.

---

## 7. Regression smoke

| Check | Kết quả |
|-------|---------|
| `mvnw -DskipTests compile` | **PASS** |
| `mvnw -Dtest=ParserAndOrderingTests test` | **PASS** (gồm test 23B mới) |
| Golden F02/T01/O01 | **NOT RUN** |
| Fact/OOS/table khác trên PDF | **NOT RUN** (manual user đã PASS trước fix) |

---

## 8. Conclusion

| Item | Result |
|------|--------|
| Root cause | **Parser** — cross-page table không merge khi header lặp |
| Fix applied | **Yes** — minimal parser merge |
| Unit test | **PASS** |
| Runtime PDF verify | **NOT RUN** — cần user re-upload |
| Task 23B | **PARTIAL** — code+test PASS; runtime PDF pending |

---

## 9. Rủi ro còn lại

- Tabula không detect table trang 14 → vẫn chỉ có text thường (cần EarlyDetect / pseudo-table path riêng).
- Header OCR lệch ký tự → không match exact normalized header.
- Hai bảng độc lập cùng số cột liền trang — **không** merge (đã bỏ fuzzy chỉ-theo col count).
