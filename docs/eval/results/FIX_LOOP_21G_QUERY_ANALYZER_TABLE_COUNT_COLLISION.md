# Fix loop — 21G QueryAnalyzer: COUNT vs TABLE_LOOKUP (`bao nhiêu`)

**Phiên bản:** theo [RAG_BASELINE_CORE_FIX_LOOP_TEMPLATE.md](../RAG_BASELINE_CORE_FIX_LOOP_TEMPLATE.md)  
**Ngày:** 2026-05-14

---

## 1. Tên fix

`fix-query-analyzer-table-cell-before-count-bao-nhieu`

---

## 2. Mục tiêu fix (một dòng)

Tách **đếm item** (`COUNT_QUERY`) khỏi **hỏi giá/lượt/kênh theo ô bảng** (`TABLE_LOOKUP`) khi câu có **“bao nhiêu”**.

---

## 3. Checklist item liên quan

| Checklist ID | Gate | Ghi chú |
|--------------|------|---------|
| G3-T01-001 | G3 | GQ-T01 hint đúng TABLE → giảm lệch prompt |
| G0-CMP-001 | G0 | compile sau sửa Java |
| G0-SCP-001 | G0 | chỉ đụng QueryAnalyzer + test |

---

## 4. Files đã đọc (trước sửa)

| Path | Mục đích |
|------|----------|
| `docs/eval/results/RAG_T01_GENERATION_SOURCE_DIAG_21F_20260514.md` | Root cause COUNT trước TABLE |
| `reports/refactor/CURSOR_REPORT_21F_T01_GENERATION_SOURCE_DIAGNOSIS.md` | Tóm tắt 21F |
| `docs/eval/results/RAG_RUNTIME_VERIFY_21E2_SECTION7_20260514.md` | Tenant G1 PASS |
| `docs/eval/RAG_GOLDEN_QUESTIONS.md` | GQ-T01 / C01 / F03 |
| `QueryAnalyzerService.java` | Thứ tự rule analyze() |

---

## 5. Files được phép sửa

| Path | Lớp |
|------|-----|
| `QueryAnalyzerService.java` | service |
| `QueryAnalyzerServiceTest.java` | test |
| `docs/eval/results/*21G*` | eval evidence |
| `reports/refactor/CURSOR_REPORT_21G_*` | report |
| `docs/CURSOR_REPORT_21G_*` | report (rule 90) |

---

## 6. Files không được sửa

`PromptBuilderService`, `RagRetrievalService`, `ChatService`, frontend, Docker schema, `.gitignore`, dependency — theo brief 21G.

---

## 7. Pre-test snapshot

| Mục | Giá trị |
|-----|---------|
| Symptom | T01 có `bao nhieu` → COUNT_QUERY trước TABLE_LOOKUP |
| Commit (ghi nhận) | `a01435769aad71df7cb8fd35435f93496e449fe2` (trước commit fix local) |

---

## 8. Diff summary

- **Số file:** 2 Java + docs  
- **Ý chính:** `isExplicitItemCountQuery` → `isTableCellLookupQuery` → COUNT generic; tier marker Basic/Pro/Business an toàn hơn cho `pro`.

---

## 9. Post-test result

| Gate | Kết quả |
|------|---------|
| G0 compile | **PASS** |
| Unit QueryAnalyzer | **PASS** |
| Runtime G3 chat (T01×3, …) | **NOT RUN** (thiếu widget key trong phiên agent) |

---

## 10. Case improved (dự kiến)

| case_id | Trước (hint) | Sau (hint) |
|---------|----------------|--------------|
| GQ-T01 | COUNT_QUERY | TABLE_LOOKUP |

---

## 11. Case regress (đã kiểm unit)

| case_id | Ghi chú |
|---------|---------|
| GQ-C01 | Vẫn COUNT_QUERY (`bao nhieu hang goi`) |

---

## 12. Rollback

`git checkout -- Backend/src/main/java/.../QueryAnalyzerService.java` + xóa test nếu cần.

---

## 13. Link evidence

- `docs/eval/results/RAG_QUERY_ANALYZER_FIX_21G_20260514.md`  
- `reports/refactor/CURSOR_REPORT_21G_QUERY_ANALYZER_TABLE_LOOKUP_FIX.md`
