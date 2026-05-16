# Fix loop — 21H PromptBuilder table guard (TABLE_LOOKUP)

**Phiên bản:** theo [RAG_BASELINE_CORE_FIX_LOOP_TEMPLATE.md](../RAG_BASELINE_CORE_FIX_LOOP_TEMPLATE.md)  
**Ngày:** 2026-05-15

---

## 1. Tên fix

`promptbuilder-table-lookup-choice-guard-no-entity-literals`

---

## 2. Mục tiêu fix (một dòng)

Giảm LLM deny (“không tìm thấy”) khi `TABLE_LOOKUP` và context có chunk bảng / markdown table, đặc biệt câu dạng lựa chọn giá trị — **không** hardcode entity/giá golden.

---

## 3. Checklist item liên quan

| Checklist ID | Gate | Ghi chú ngắn |
|--------------|------|--------------|
| G3-T01 v4 | G3 | Choice/compare giá — generation contradiction (21G3) |
| G0-CMP-001 | G0 | compile |

---

## 4. Files dự kiến đọc (trước khi sửa)

| Path | Mục đích |
|------|----------|
| `docs/eval/results/RAG_QUERY_ANALYZER_RUNTIME_VERIFY_21G3_20260514.md` | Symptom T01 v4 |
| `PromptBuilderService.java` | Instruction TABLE_LOOKUP |
| `RetrievedContext.java` | Field `chunkType`, `content` |

---

## 5. Files được phép sửa (scope)

| Path | Lớp |
|------|-----|
| `Backend/.../PromptBuilderService.java` | prompt / service |
| `Backend/.../PromptBuilderServiceTest.java` | test |

---

## 6. Files không được sửa

`QueryAnalyzerService.java`, `RagRetrievalService.java`, `ChatService.java`, frontend, Docker, schema, dependency, `.gitignore`, retrieval cap.

---

## 7. Pre-test snapshot

| Mục | Giá trị |
|-----|---------|
| Trước fix | 21G3: T01 v4 **FAIL** (deny có bảng); T01 gốc **PASS** |

---

## 8. Diff summary

- **Số file thay đổi:** 2 (1 sửa + 1 test mới).
- **Ý chính:** mở rộng `TABLE_LOOKUP` instruction; banner khi context có `table_*` / `text_table_like` / markdown `|` heuristic; unit test assert không literal golden.

---

## 9. Post-test result

| Gate | Kết quả |
|------|---------|
| G0 compile | **PASS** |
| Unit `PromptBuilderServiceTest` | **PASS** |
| Runtime T01 v4 ×3 | **NOT_RUN** (phiên này) |

---

## 10. Verdict

**PARTIAL** — sẵn sàng verify runtime theo checklist 21H; không claim T01 v4 PASS cho đến khi chạy LLM thật.

---

## 11. Next verify (khi có Docker + key)

1. `docker compose up --build -d`
2. Chatbot + golden `RAG_GOLDEN_TEST_DOCUMENT.txt` đã index.
3. T01 gốc ×3, T01 v4 ×3, F03, L02, T02, C01, O01, O02 — ghi answer + sourceCount + verdict.
