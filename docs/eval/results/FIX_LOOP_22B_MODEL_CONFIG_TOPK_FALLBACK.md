# Fix loop — 22B modelConfig.topK fallback

**Phiên bản:** theo [RAG_BASELINE_CORE_FIX_LOOP_TEMPLATE.md](../RAG_BASELINE_CORE_FIX_LOOP_TEMPLATE.md)  
**Ngày:** 2026-05-15

---

## 1. Tên fix

`model-config-topk-fallback-chat-retrieval`

---

## 2. Mục tiêu fix (một dòng)

Khi `/api/chat` không gửi `topK`, dùng `uiConfig.modelConfig.topK` của chatbot/widget; không có thì backend default 30; request topK vẫn ưu tiên cao nhất.

---

## 3. Checklist item liên quan

| Checklist ID | Gate | Ghi chú |
|--------------|------|---------|
| 22A limitation | G3 | modelConfig chưa auto-apply |
| G0-CMP-001 | G0 | compile + unit test |

---

## 4. Files được phép sửa

`ChatService.java`, `WidgetService.java`, tests, docs eval/report.

---

## 5. Files không được sửa

Parser, QueryAnalyzer, PromptBuilder, source cap 21J, RagRetrievalService FINAL_LIMIT, Qdrant schema, DB migration, `.gitignore`, FE redesign.

---

## 6. Verify

| Bước | Kết quả |
|------|---------|
| `ChatServiceModelConfigTopKTest` | **PASS** |
| `RetrievalTopKTest` regression | **PASS** |
| `ChatServiceSourcePresentationTest` | **PASS** |
| Docker runtime log | **NOT RUN** |

---

## 7. Rollback

Revert `ChatService` resolution + `WidgetService.parseModelConfigTopK` + test file; không cần reindex.
