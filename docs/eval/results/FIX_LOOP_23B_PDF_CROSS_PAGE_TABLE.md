# FIX_LOOP — 23B PDF cross-page table continuation

**Ngày:** 2026-05-15  
**Scope:** `DocumentParserService` — merge bảng Tabula continuation trang N → N+1

## Vòng lặp

| Bước | Hành động |
|------|-----------|
| 1 | Chẩn đoán: continuation chỉ merge khi `isDataRow(firstRow)`; header lặp trang 14 không merge |
| 2 | Sửa: merge trước `isUsableTable`; thêm `isRepeatedHeaderContinuationTable` + `convertTableDataRowsOnly` |
| 3 | Test: `ParserAndOrderingTests.merged_cross_page_entity_table_indexes_all_entity_rows` |
| 4 | Runtime PDF: **NOT RUN** — file `HeThongQuanLyYeuCauPhucKhao.pdf` không có trong repo; cần re-upload sau deploy |

## Kết quả vòng này

- **Code:** PASS compile + ParserAndOrderingTests
- **Runtime PDF target:** NOT RUN (thiếu artifact PDF local)

## Bước tiếp theo (ops)

1. Re-upload `HeThongQuanLyYeuCauPhucKhao.pdf` (document mới).
2. Hỏi: `Bảng các thực thể và thuộc tính gồm những thực thể nào?`
3. Kiểm tra log `[Parse] Table MERGED continuation page=14 → page=13`.
4. SQL: chunk text chứa `LichSuPhucKhao`, `BienBan`.
