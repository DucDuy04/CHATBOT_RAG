# CURSOR_REPORT_23F — Top-K Effective Context Audit

## 1. Mức độ hiểu task

- **~95%**
- **Chắc chắn:** Luồng code topK từ FE → controller → `ChatService` / `PlaygroundService` → `RagRetrievalService`; topK chỉ gắn Qdrant `limit`; final context bị `FINAL_LIMIT*` và expansion; source cap tách khỏi prompt.
- **Giả định:** Runtime matrix 23F chưa chạy vì backend local không listen (curl exit 7); kết luận hành vi dựa trên source + log 23D2.
- **Thiếu dữ kiện:** Log `Final context chunks: N` với topK=1 trên `TOPK_CONTROL_TEST_DOCUMENT` sau index.

## 2. Tóm tắt yêu cầu

Audit toàn pipeline Top-K: Playground normal, Compare, Widget/public, Model Settings — xác định topK ảnh hưởng retrieval / rerank / final prompt / sources; giải thích topK=1 vẫn trả lời đủ; chỉ sửa code nếu có bug wiring.

## 3. Phạm vi đã làm

- Đọc docs 22A, 22B2, 21J, 23D, 23D2 và toàn bộ file Backend/Frontend liệt kê trong task.
- Lập semantics inventory + pipeline map.
- Tạo `docs/eval/manual/TOPK_CONTROL_TEST_DOCUMENT.txt`.
- Tạo audit artifacts: `docs/eval/results/RAG_TOPK_EFFECTIVE_CONTEXT_AUDIT_23F_20260517.md`, `FIX_LOOP_23F_TOPK_EFFECTIVE_CONTEXT.md`.

## 4. Phạm vi không làm

- Không sửa Java/JS production code.
- Không sửa parser, PromptBuilder, QueryAnalyzer, source cap production.
- Không migration/backfill, không thêm dependency.
- Không chạy runtime matrix (backend down).

## 5. File đã đọc

| Path | Mục đích | Kết luận chính |
|------|----------|----------------|
| `RagRetrievalService.java` | Pipeline retrieval | topK → `embeddingService.search` only; expansion + FINAL_LIMIT sau đó |
| `ChatService.java` | Chat/playground stream | resolveTopK; sources cap ≠ LLM context |
| `PlaygroundController.java` | Playground entry | Sets topK + `playgroundDebugSources=true` |
| `PlaygroundService.java` | Compare | parseTopKOverride only; sources uncapped |
| `ChatController.java` / `PublicChatController.java` | Production APIs | No topK in widget body; modelConfig fallback in service |
| `EmbeddingService.java` | Qdrant | `limit` = effectiveAnchorTopK |
| `RerankService.java` | Rerank | topN from FINAL_LIMIT, not topK |
| `PromptBuilderService.java` | Prompt | Uses all contexts passed in |
| `PlaygroundPage.jsx` / `playgroundApi.js` | FE wiring | Sends topK; FE also slices display |
| `WidgetChatPage.jsx` / `publicChatApi.js` | Widget | No topK in request |
| 23D / 23D2 eval docs | Prior runtime | effective topK + playground source cap verified |

## 6. Top-K semantics hiện tại

**Định nghĩa thực tế:** `topK` = **số segment tối đa mỗi lần vector search (anchor top-K)**, normalize 1–30, default 30.

**Không phải:** số chunk cuối trong prompt, rerank topN, hay hard cap expansion.

## 7. Luồng Playground normal

| Field | Value |
|-------|-------|
| Request | `topK` + `overrideParams.topK` → `PlaygroundController` |
| effectiveTopK | `ChatService.resolveRetrievalTopK` → log `[RAG] retrieval topK source=… effective=…` |
| vectorHits | ≤ effectiveTopK **per query variant** (variants ≤ ~4) |
| afterExpansion | Often **>> topK** (section 12, window 4, lock full section) |
| finalContexts | `dedupeSortBudget` → max **10** normal / **20** expanded / **60** locked |
| responseSources | min(deduped, **effectiveTopK**) when `playgroundDebugSources=true` |

## 8. Luồng Compare

| Field | Value |
|-------|-------|
| Request | `configA`/`configB` with `topK` |
| effectiveTopK | `parseTopKOverride(config)` only |
| finalContexts | Same pipeline as above |
| responseSources | **All** contexts (no 5-cap) |

## 9. Luồng Widget/public

| Field | Value |
|-------|-------|
| Request | No topK |
| effectiveTopK | MODEL_CONFIG or DEFAULT |
| finalContexts | Same pipeline |
| responseSources | Cap **5** (may hide topK effect in UI) |

## 10. Vì sao topK=1 vẫn trả lời đủ

1. Final limit 10+ chunks vào prompt.  
2. Expansion từ 1 anchor (section/neighbor/lock).  
3. LIST_ALL intent mở rộng thêm.  
4. Chunk lớn / history session.  
5. Source panel có thể hiển thị ít hơn context thật (production cap 5; playground debug sau 23D align hơn).

## 11. Có sửa code không

**Không.**

## 12. Nếu sửa

N/A — no runtime diff.

## 13. Nếu không sửa

**No runtime diff.** Chỉ thêm docs eval + test doc manual.

## 14. Production source cap

**Không bị ảnh hưởng** bởi audit này. Vẫn MAX_RESPONSE_SOURCES=5; không phản ánh đủ final context khi topK > 5 hoặc expansion > 5.

## 15. Kết luận PASS/PARTIAL/FAIL

**PARTIAL**

- Wiring: PASS  
- Semantics vs user expectation: PARTIAL  
- Bug “topK không hoạt động”: **không xác nhận**

## 16. Rủi ro còn lại

- User/UI hiểu nhầm Top-K = số nguồn/context LLM.  
- Compare không fallback modelConfig topK nếu config thiếu field.  
- Runtime matrix 23F chưa verify trên test doc mới.

## 17. Bước tiếp theo

1. Start backend; upload + index `TOPK_CONTROL_TEST_DOCUMENT.txt`.  
2. Chạy matrix; so sánh log `effective=1` vs `Final context chunks: N`.  
3. Nếu product muốn topK = final context cap → thiết kế riêng (không hotfix retrieval).

---

## 11. Kết quả kiểm tra

| Command | Kết quả | Ghi chú |
|---------|---------|---------|
| `cd Backend && ./mvnw -DskipTests compile` | NOT RUN | Audit-only, no code change |
| `cd Backend && ./mvnw test` | NOT RUN | Same |
| `cd Frontend && npm run lint` | NOT RUN | Same |
| `cd Frontend && npm run build` | NOT RUN | Same |
| `cd Frontend && npm run build:widget` | NOT RUN | Same |
| `docker compose config` | NOT RUN | Same |
| Runtime matrix 23F | NOT RUN | `localhost:8080` unreachable |
