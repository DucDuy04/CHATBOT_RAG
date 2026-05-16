# Fix loop — 21G2 QueryAnalyzer generalized (no entity hardcode)

**Phiên bản:** theo [RAG_BASELINE_CORE_FIX_LOOP_TEMPLATE.md](../RAG_BASELINE_CORE_FIX_LOOP_TEMPLATE.md)  
**Ngày:** 2026-05-14

---

## 1. Tên fix

`refactor-query-analyzer-structural-table-vs-count-no-entity-literals`

---

## 2. Mục tiêu fix (một dòng)

Thay `containsTierPackageMarker` (Basic/Pro/Business) bằng heuristic cấu trúc (`gói X`, `mã X`, `dòng X`, `SKU`, `… có giá`) + explicit COUNT mở rộng.

---

## 3. Checklist item liên quan

| Checklist ID | Gate | Ghi chú |
|--------------|------|---------|
| G0-CMP-001 | G0 | compile |
| G3-T01 (hint) | G3 | TABLE_LOOKUP ổn định hơn, không phụ thuộc tên golden |

---

## 4. Files đã đọc

| Path | Mục đích |
|------|----------|
| `docs/eval/results/RAG_QUERY_ANALYZER_FIX_21G_20260514.md` | Baseline 21G |
| `reports/refactor/CURSOR_REPORT_21G_QUERY_ANALYZER_TABLE_LOOKUP_FIX.md` | Diff 21G |
| `docs/eval/results/RAG_T01_GENERATION_SOURCE_DIAG_21F_20260514.md` | Root cause COUNT trước TABLE |
| `QueryAnalyzerService.java` | Refactor |

---

## 5. Files được phép sửa

`QueryAnalyzerService.java`, `QueryAnalyzerServiceTest.java`, `docs/eval/results/*21G2*`, `reports/refactor/CURSOR_REPORT_21G2_*`, `docs/CURSOR_REPORT_21G2_*`.

---

## 6. Files không được sửa

PromptBuilder, RagRetrievalService, ChatService, frontend, Docker, schema, dependency, `.gitignore`.

---

## 7. Pre-test snapshot

| Mục | Giá trị |
|-----|---------|
| Trước fix | 21G có `containsTierPackageMarker` hardcode Basic/Pro/Business |

---

## 8. Diff summary

- Xóa helper hardcode entity; thêm regex + cue helpers; mở rộng explicit COUNT; chỉnh keyword `hang`/`ma` ở nhánh TABLE cuối.

---

## 9. Post-test result

| Gate | Kết quả |
|------|---------|
| compile | PASS |
| QueryAnalyzerServiceTest | PASS |

---

## 10. Case improved

| case_id | Ghi chú |
|---------|---------|
| Bất kỳ tài liệu có `gói <tên>` + field | TABLE_LOOKUP không cần trùng golden |

---

## 11. Case regress

Không phát hiện trong unit (golden + generalization + edge).

---

## 12. Evidence

- `docs/eval/results/RAG_QUERY_ANALYZER_GENERALIZED_21G2_20260514.md`
- `reports/refactor/CURSOR_REPORT_21G2_QUERY_ANALYZER_GENERALIZED_FIX.md`
