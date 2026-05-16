# Fix loop — 21D2 ATX last section (`## 7. Phạm vi…`)

**Phiên bản:** theo [RAG_BASELINE_CORE_FIX_LOOP_TEMPLATE.md](../RAG_BASELINE_CORE_FIX_LOOP_TEMPLATE.md)  
**Ngày:** 2026-05-14

---

## 1. Tên fix

`fix-atx-footer-heuristic-allow-numbered-outline-tai-lieu-sec7`

---

## 2. Mục tiêu fix (một dòng)

Chấp nhận heading ATX mục 7 chứa "trong tài liệu" mà không bị heuristic footer skip nhầm; giữ `#` đầu file trong section General.

---

## 3. Checklist item liên quan

| Checklist ID | Gate | Ghi chú |
|--------------|------|---------|
| G1-MDH-001 | G1 | Đủ ranh giới `##` gồm mục 7 |
| G1-SEC-001…003 | G1 | Không false list |
| G1-CHK-001…003 | G1 | Bước 2 / chính sách |
| G1-TBL-001…003 | G1 | Bảng |

---

## 4. Files đã đọc (trước sửa)

| Path | Mục đích |
|------|----------|
| `docs/eval/results/RAG_RUNTIME_VERIFY_21E_20260514.md` | Symptom 21E |
| `DocumentParserService.java` | `headingSkipReason`, `isLikelySectionHeaderSkipReason` |
| `docs/eval/RAG_GOLDEN_TEST_DOCUMENT.txt` | Tiêu đề mục 7 |

---

## 5. Files được phép sửa

| Path | Lớp |
|------|-----|
| `DocumentParserService.java` | parser |
| `ParserAndOrderingTests.java` | test |

---

## 6. Files không được sửa

Retrieval, prompt, `ChatService`, frontend, Docker schema, `.gitignore` — theo brief 21D2.

---

## 7. Pre-test snapshot

| Mục | Giá trị |
|-----|----------|
| Symptom | 21E: 7 section rows, thiếu mục 7 |
| Nguyên nhân | `footer-header-artifact` + token `tài liệu` |

---

## 8. Diff summary

- **Số file:** 2  
- **Ý chính:** truyền `markdownHeadingMode` vào skip reason; tách footer mạnh vs `tài liệu` có điều kiện outline số trong ATX.

---

## 9. Post-test result

| Gate | Kết quả |
|------|---------|
| G0 compile | PASS |
| G1 (golden unit) | PASS |
| Runtime SQL sau deploy | Operator nên reupload golden và kiểm tra 8 sections + 10 chunks |

---

## 10. Rollback plan

Revert commit thay `isLikelySectionHeaderSkipReason` / chữ ký `headingSkipReason`.

---

## 11. Notes / rủi ro

- Footer một dòng chỉ có "Tài liệu …" không có số mục: trong TXT markdown vẫn có thể bị skip (chấp nhận để giữ General cho `#` đầu file).
- PDF không markdown: hành vi cũ với `tài liệu` (skip) gần như giữ nguyên nếu không phải outline số.

---

*Chi tiết kỹ thuật: `RAG_PARSER_FIX_21D2_20260514.md` và `reports/refactor/CURSOR_REPORT_21D2_ATX_LAST_SECTION_PARSER_FIX.md`.*
