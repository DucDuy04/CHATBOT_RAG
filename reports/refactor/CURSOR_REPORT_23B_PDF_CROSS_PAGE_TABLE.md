# CURSOR_REPORT_23B — PDF cross-page table continuation

## 1. Mức độ hiểu task

- **~95%** — symptom và pipeline rõ; thiếu file PDF + DB/Qdrant runtime local.
- **Chắc chắn:** logic continuation cũ trong `DocumentParserService`; hướng sửa parser.
- **Giả định:** Tabula trích được 2 table trang 13–14 (user manual: phần đầu có trong answer).

## 2. Tóm tắt yêu cầu

Chẩn đoán vì sao bảng “Các thực thể và thuộc tính” thiếu 4 dòng trang 14; sửa minimal parser/chunking; không đụng prompt/QA/model controls/source cap.

## 3. Phạm vi đã làm

- Đọc `DocumentParserService`, `ChunkingService2`, closure 22F/22G
- Phân tích 12 câu diagnosis
- Sửa merge continuation (repeated header + merge trước usable check)
- Test `merged_cross_page_entity_table_indexes_all_entity_rows`
- Tạo result + fix loop + report

## 4. Phạm vi không làm

- PromptBuilder, QueryAnalyzer, ChatService cap, model controls, FE, Playground, schema, migration, `.gitignore`
- Runtime upload PDF (file không có trong workspace)
- Golden full 12 case

## 5. File đã đọc

| Path | Kết luận |
|------|----------|
| `DocumentParserService.java` | Page loop + continuation; gap ở repeated header |
| `ChunkingService2.java` | 1 `[TABLE_*]` → 1 table_summary + row_groups |
| `RAG_BASELINE_CORE_CLOSURE_22G.md` | Baseline PASS |
| `RAG_CHAT_MAIN_REGRESSION_…_22F_20260515.md` | 12/0/0 |
| `HeThongQuanLyYeuCauPhucKhao.pdf` | **Không tồn tại trong repo** |

## 6. Manual issue từ user

Bảng trang 13–14; chatbot liệt kê 8 entity đầu, thiếu LichSuPhucKhao, Khoa, TuiBaiThi, BienBan.

## 7. Parser/table/chunk inventory

- PDF → per-page Tabula + text → `[TABLE_START/END]` → sections → `table_summary` / `table_row_group`
- Trước fix: 2 khối table trên 2 trang → retrieval có thể chỉ hit chunk đầu

## 8. Qdrant inventory

**NOT RUN** — không có document indexed local sau fix.

## 9. Retrieval result target question

**NOT RUN**

## 10. Root cause

**Parser/chunking** — `isContinuationTable` chỉ merge khi first row là data row; PDF cross-page hay lặp header trang sau → không merge; chunk/index tách.

## 11. Có sửa code không?

**Có.**

## 12. File đã sửa + diff

### `DocumentParserService.java`

- Merge continuation **trước** `isUsableTable`
- `isRepeatedHeaderContinuationTable`, `convertTableDataRowsOnly`, helpers normalize header row
- Log `[Parse] Table MERGED continuation page=X → page=Y`

```diff
+ boolean continuationByRepeatedHeader = isRepeatedHeaderContinuationTable(table, headerBeforeConvert);
+ if (canMergeToPrev) { ... convertTableDataRowsOnly(table, 1) ... continue; }
- merge only when isDataRow(firstRow) && lastTableHeader.equals(...)
```

### `ParserAndOrderingTests.java`

- `merged_cross_page_entity_table_indexes_all_entity_rows` — 12 entity names in table chunks

**Không hardcode** entity trong production code.

## 13. Compile/test result

| Command | Result |
|---------|--------|
| `mvnw -DskipTests compile` | **PASS** |
| `mvnw -Dtest=ParserAndOrderingTests test` | **PASS** |

## 14. Runtime PDF verify

**NOT RUN** — PDF không có trong workspace. **Bắt buộc re-upload** sau deploy.

## 15. Regression smoke

**NOT RUN** (runtime). Unit regression **PASS**.

## 16. Rủi ro còn lại

- Tabula không trích table trang 14
- Header OCR khác normalized string
- Cần verify manual sau re-upload

## 17. Đề xuất tiếp theo

1. Re-upload PDF → hỏi câu target → grep log `Table MERGED continuation`
2. Nếu vẫn PARTIAL: inspect Tabula output page 14; cân nhắc pseudo-table merge (không thêm dependency)
3. Optional: retrieval sibling expansion cùng `tableId` — chỉ nếu parser merge vẫn không đủ

---

**Evidence:** `docs/eval/results/RAG_PDF_CROSS_PAGE_TABLE_23B_20260515.md`
