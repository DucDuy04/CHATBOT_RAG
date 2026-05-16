# Fix loop — 21J Source presentation cleanup (response cap)

**Phiên bản:** theo [RAG_BASELINE_CORE_FIX_LOOP_TEMPLATE.md](../RAG_BASELINE_CORE_FIX_LOOP_TEMPLATE.md)  
**Ngày:** 2026-05-15

---

## 1. Tên fix

`chat-service-response-source-cap-dedup-no-llm-context-change`

---

## 2. Mục tiêu fix (một dòng)

Giảm `sources` noise trong API/SSE (cap 5, refusal 2) **không** thay đổi retrieval context đưa vào LLM.

---

## 3. Checklist item liên quan

| Checklist ID | Gate | Ghi chú ngắn |
|--------------|------|--------------|
| G3 source UX | G3 | SOURCE_NOISE từ 21I |
| G0-CMP-001 | G0 | compile + unit test |

---

## 4. Files dự kiến đọc (trước khi sửa)

| Path | Mục đích |
|------|----------|
| `docs/eval/results/RAG_FULL_GOLDEN_REGRESSION_21I_20260515.md` | Before sourceCount |
| `ChatService.java` | Pipeline sources |
| `ChatResponse.java` | SourceDto contract |
| `RetrievedContext.java` | chunkId cho dedup |
| `Frontend/.../ChatPage.jsx` | chunkText usage |

---

## 5. Files được phép sửa (scope)

| Path | Lớp |
|------|-----|
| `Backend/.../ChatService.java` | service / API response |
| `Backend/.../ChatServiceSourcePresentationTest.java` | test |
| `docs/eval/results/*21J*` | docs |
| `reports/refactor/CURSOR_REPORT_21J_*` | report |

---

## 6. Files không được sửa

`RagRetrievalService.java`, `PromptBuilderService.java`, `QueryAnalyzerService.java`, embedding/Qdrant, DB schema, frontend, Docker, `.gitignore`, `PlaygroundService.java` (ngoài scope).

---

## 7. Thay đổi đã làm

- `buildSourceDtosForResponse` + dedupe + cap 5
- `applyAnswerAwareSourceCap` refusal → 2
- Unit test 6 case
- Runtime targeted 11 case trên Docker

---

## 8. Verify

| Bước | Kết quả |
|------|---------|
| Unit test | PASS |
| Docker targeted eval | PASS — không case >5 sources |
| GQ-D01 | `sourceCount=0` |
| Answer regress | Không trên scope chạy |

---

## 9. Rollback

Revert commit chỉ `ChatService.java` + test — không cần reindex/migration.

---

## 10. Follow-up (ngoài scope)

- Cân nhắc cùng logic cho `PlaygroundService.buildSourceDtos` nếu muốn đồng bộ playground panel.
- Section-level representative (Option B) nếu vẫn thấy 5 source quá rộng cho fact đơn.
