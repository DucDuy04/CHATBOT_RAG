# FIX_LOOP — 23B3 PDF sparse continuation table merge

**Ngày:** 2026-05-16  
**Scope:** `DocumentParserService` — merge bảng Tabula sparse continuation trang N+1 → N

## Vòng lặp

| Bước | Hành động |
|------|-----------|
| 1 | Chẩn đoán 23B2: page 14 REJECTED `too-many-empty-cells`, không merge 14→13 |
| 2 | Thiết kế nhánh `SPARSE_CONTINUATION` trước reject (Option A) |
| 3 | Sửa extract hàng 3 cột + heading guard (chỉ hàng substantive đầu) |
| 4 | Unit test `DocumentParserCrossPageMergeTest` + regression `ParserAndOrderingTests` |
| 5 | Runtime: rebuild backend, upload PDF, verify log + chat |

## Kết quả vòng này

- **Code:** PASS compile + 19 parser tests
- **Runtime:** PASS — log `SPARSE_CONTINUATION page=14 → page=13`, target **PASS** 12/12 entity

## Bước tiếp theo (ops)

1. Merge code 23B3 vào branch baseline.
2. Re-upload PDF production nếu document cũ index trước fix.
3. Không đổi PromptBuilder trừ khi verify mới cho thấy retrieval gap.
