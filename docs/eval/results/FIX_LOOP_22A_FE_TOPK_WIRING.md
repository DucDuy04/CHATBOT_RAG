# Fix loop — 22A FE Top-K wiring

**Phiên bản:** theo [RAG_BASELINE_CORE_FIX_LOOP_TEMPLATE.md](../RAG_BASELINE_CORE_FIX_LOOP_TEMPLATE.md)  
**Ngày:** 2026-05-15

---

## 1. Tên fix

`fe-playground-topk-per-request-retrieval-wiring`

---

## 2. Mục tiêu fix (một dòng)

Nối Top-K control playground → `ChatRequest.topK` → Qdrant vector `limit` theo từng request, clamp 1–30, default 30 khi null.

---

## 3. Checklist item liên quan

| Checklist ID | Gate | Ghi chú |
|--------------|------|---------|
| Playground override UX | G3 | Top-K trước đây chỉ cap UI |
| G0-CMP-001 | G0 | compile + test |

---

## 4. Files được phép sửa

`ChatRequest.java`, `PlaygroundChatRequest.java`, `ChatService.java`, `RagRetrievalService.java`, `PlaygroundController.java`, `PlaygroundService.java`, FE playground API/panels, tests, docs.

---

## 5. Files không được sửa

Parser, QueryAnalyzer, PromptBuilder, source cap 21J logic, embedding model, Qdrant schema, DB migration, `.gitignore`.

---

## 6. Verify

| Bước | Kết quả |
|------|---------|
| Unit `RetrievalTopKTest` | PASS |
| Docker log effective topK | PASS 3/10/clamp/default |
| Source cap 21J tests | PASS (không regress) |
| FE build | PASS |

---

## 7. Rollback

Revert các file service/DTO/controller + FE playground; không cần reindex.
