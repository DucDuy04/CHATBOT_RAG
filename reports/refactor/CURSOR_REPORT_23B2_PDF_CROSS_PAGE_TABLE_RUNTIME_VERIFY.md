# CURSOR_REPORT_23B2 — PDF cross-page table runtime verify

## 1. Mức độ hiểu task

- **~98%** — verify-only runtime sau fix 23B với PDF thật.
- **Chắc chắn:** stack rebuild, upload, chat smoke, log đọc được.
- **Giả định:** số trang PDF Tabula = số trang user mô tả (13–14) — xác nhận log page 13 ACCEPT / page 14 REJECT.

## 2. Tóm tắt yêu cầu

Rebuild backend 23B, upload `HeThongQuanLyYeuCauPhucKhao.pdf`, kiểm tra merge log, DB, Qdrant, câu target 12 entity, regression smoke — không sửa code.

## 3. Phạm vi đã làm

- `docker compose up --build -d`, health, env check
- Compile + ParserAndOrderingTests
- Upload PDF từ `docs/eval/manual/`
- Parser logs, MySQL read-only, Qdrant scroll
- `/api/chat` target + 3 smoke
- `_run_23b2_results.json` + result/report docs

## 4. Phạm vi không làm

Không sửa Java/FE/parser/retrieval/prompt; không delete smoke; không golden full 12.

## 5. File đã đọc

23B result/fix loop/report; closure 22G; `DocumentParserService` (log behavior); script artifacts.

## 6. PDF artifact/path used

`e:\chatbot-rag-workspace\CHATBOT_RAG\docs\eval\manual\HeThongQuanLyYeuCauPhucKhao.pdf` — **có**.

## 7. Build/restart result

Docker rebuild backend+frontend — **PASS**, 4 services up.

## 8. Compile/test result

| Command | Result |
|---------|--------|
| compile | **PASS** |
| ParserAndOrderingTests | **PASS** |

## 9. Upload/index result

| Field | Value |
|-------|-------|
| status | INDEXED |
| chunkCount | 82 |
| documentId | `0bcde0c5-eb27-490b-a39e-fd15b62cff7f` |

## 10. Parser log evidence

- **page=13:** Table ACCEPTED (9 rows, entity table)
- **page=14:** Table REJECTED `too-many-empty-cells` — preview có `LichSuPhucKhao`
- **Không** `MERGED continuation page=14 → page=13`
- **Có** merge `page=18 → page=17` REPEATED_HEADER (bảng khác)

## 11. SQL chunks/tables evidence

- COUNT chunks with `LichSuPhucKhao` or `BienBan`: **10**
- Scripted entity scan on chunk preview: partial (console encoding)
- Qdrant payload: **12/12** entities, **4/4** page-14 names

## 12. Qdrant evidence

82 points; payload text chứa đủ `LichSuPhucKhao`, `Khoa`, `TuiBaiThi`, `BienBan`.

## 13. Target question result

| Item | Value |
|------|-------|
| Verdict | **PARTIAL** |
| Entities (ASCII token match) | 9/12 |
| Page-14 in answer | 2/4 |
| sourceCount | 5 |

Cải thiện vs pre-23B (~8 entity); chưa đủ 12.

## 14. Regression smoke result

| Case | Pass |
|------|------|
| Fact 22T1020585 | **Yes** |
| NguoiDung columns | **Yes** (9/9) |
| OOS USD/VND | **Yes** |

## 15. Có sửa code không?

**Không.**

## 16. No code diff

Chỉ docs + `_run_23b2_pdf_cross_page_verify.ps1` + `_run_23b2_results.json`.

## 17. Kết luận

| Item | Result |
|------|--------|
| Cross-page table (target 13–14) | **PARTIAL** |
| Fix 23B proven on runtime | **Partial** — merge works on other pages; target pair blocked by page-14 reject |
| Cần fix tiếp | **Yes** — merge path for rejected sparse continuation tables |

## 18. Rủi ro còn lại

- Tabula sparse tables on continuation pages still REJECTED.
- LLM may not list all entities even when Qdrant has them (topK=5 sources).
- Automated entity match misses Vietnamese diacritics (`YeuCauPhucKhao` vs `YêuCầuPhúcKhao`).

## 19. Đề xuất bước tiếp theo

1. **23B3 (small parser):** Merge continuation when page N+1 table fails `isUsableTable` but `lastTableHeaderPage == N-1` and rows share header / entity-table context.
2. Re-upload PDF + re-run 23B2 script.
3. Không đổi PromptBuilder until ingest merge 13–14 confirmed in logs.

---

**Evidence:** `docs/eval/results/RAG_PDF_CROSS_PAGE_TABLE_RUNTIME_VERIFY_23B2_20260515.md`
