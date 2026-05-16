# Template — Fix loop cho từng lần sửa baseline RAG

**Phiên bản:** 1.0 (task 21D0)  
**Dùng chung với:** [RAG_BASELINE_CORE_REBUILD_PLAN.md](RAG_BASELINE_CORE_REBUILD_PLAN.md), [RAG_BASELINE_CORE_CHECKLIST.md](RAG_BASELINE_CORE_CHECKLIST.md)

---

## 1. Tên fix

_(VD: `fix-txt-parser-markdown-h2-section-boundary`)_

---

## 2. Mục tiêu fix (một dòng)

_(Chỉ một mục tiêu chính — VD: “TXT parser nhận `##` làm section; không tạo section từ list `1.` trong mục chính sách.”)_

---

## 3. Checklist item liên quan (ID từ RAG_BASELINE_CORE_CHECKLIST.md)

| Checklist ID | Gate | Ghi chú ngắn |
|--------------|------|---------------|
| _(điền)_ | _(G0–G6)_ | _(điền)_ |

---

## 4. Files dự kiến đọc (trước khi sửa)

| Path | Mục đích đọc |
|------|----------------|
| _(điền)_ | _(điền)_ |

---

## 5. Files được phép sửa (scope PR này)

| Path | Lớp ảnh hưởng |
|------|----------------|
| _(điền)_ | _(parser / service / …)_ |

---

## 6. Files không được sửa (để tránh lan scope)

| Path hoặc nhóm | Lý do không đụng |
|----------------|------------------|
| _(vd Frontend, unrelated controller)_ | _(điền)_ |

---

## 7. Pre-test snapshot

| Mục | Giá trị |
|-----|---------|
| Git branch | _(điền)_ |
| Commit hash (trước fix) | _(điền)_ |
| CHATBOT_ID / tenant | _(điền hoặc “sẽ tạo mới”)_ |
| DOCUMENT_ID (golden hoặc E2E) trước fix | _(điền)_ |
| Tóm tắt verdict G3/G4 trước fix | _(điền)_ |

---

## 8. Diff summary (sau khi implement)

- **Số file thay đổi:** _(n)_  
- **Ý chính thay đổi:** _(3–5 bullet)_  
- **Link PR hoặc `git show`:** _(điền)_

---

## 9. Post-test result (điền sau khi chạy checklist)

| Gate | Các ID đã chạy | Kết quả (PASS / PARTIAL / FAIL / NOT_RUN) |
|------|----------------|-------------------------------------------|
| G0 | _(IDs)_ | _(điền)_ |
| G1 | _(IDs)_ | _(điền)_ |
| G2 | _(IDs)_ | _(điền)_ |
| G3 | _(IDs)_ | _(điền)_ |
| G4 | _(IDs)_ | _(điền)_ |
| G5 | _(IDs)_ | _(điền)_ |
| G6 | _(IDs)_ | _(điền)_ |

**Evidence đính kèm (path hoặc mô tả):**

- API response snapshot: _(điền)_  
- SQL output (rút gọn): _(điền)_  
- Qdrant count / payload rút gọn: _(điền)_  
- `docker logs` trích (không secret): _(điền)_  

---

## 10. Case improved (so với baseline 21B / run trước)

| case_id | Trước | Sau | Ghi chú |
|---------|-------|-----|---------|
| _(điền)_ | _(FAIL/PARTIAL/PASS)_ | _(điền)_ | _(điền)_ |

---

## 11. Case regressed

| case_id | Trước | Sau | Hành động |
|---------|-------|-----|-----------|
| _(điền hoặc “none”)_ | _(điền)_ | _(điền)_ | _(rollback / fix follow-up)_ |

---

## 12. Quyết định (chọn một)

- [ ] **PASS** — đạt ngưỡng gate; chuyển task tiếp theo (ghi tên task).  
- [ ] **PARTIAL** — merge được nhưng cần **fix nhỏ** ngay sau; không mở G4 full cho đến khi… _(điền)_.  
- [ ] **FAIL** — **rollback** commit / revert PR **hoặc** mở **diagnosis** lại (ghi lý do: compile, G2 fail, GQ-D01 fail, v.v.).

**Ghi chú quyết định:** _(điền)_

---

## 13. Prompt / task tiếp theo cần tạo

_(Mô tả ngắn cho Cursor/PR tiếp — VD: “Sửa `RagRetrievalService` refill anchor sau khi clear lock khi mọi hit bị exclude.”)_

1. _(điền)_  
2. _(điền)_  

---

*Xóa các dòng placeholder `_()` khi điền thật; lưu bản copy mỗi lần fix (vd `fix-loop-20260514-parser.md`).*
