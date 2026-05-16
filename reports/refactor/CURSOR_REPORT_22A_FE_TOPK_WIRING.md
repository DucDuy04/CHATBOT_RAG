# CURSOR_REPORT_22A — FE Top-K control wiring

## 1. Mức độ hiểu task

- **~96%** — wiring per-request topK FE playground → BE Qdrant anchor limit; không tuning RAG; giữ 21J source cap.
- **Chắc chắn:** gap là overrideParams không map; anchor search dùng constant 30.
- **Giả định:** chỉ playground/compare cần wire (widget/chat page không có control).

## 2. Tóm tắt yêu cầu

Cho phép user chỉnh Top-K retrieval từ UI playground; BE validate/clamp; backward compatible khi không gửi topK.

## 3. Phạm vi đã làm

- `ChatRequest.topK`, `PlaygroundChatRequest.topK`
- `RagRetrievalService.retrieveWithMetadata(..., Integer topKOverride)`
- `normalizeAnchorTopK` / `parseTopKOverride`
- `ChatService` sync + stream truyền topK
- `PlaygroundController` + `PlaygroundService.compare`
- FE: `playgroundApi`, `ModelOverridePanel`, `CompareConfigPanel`, `ComparePane` text
- `RetrievalTopKTest`
- Runtime log verify trên Docker

## 4. Phạm vi không làm

Parser, PromptBuilder, QueryAnalyzer, FINAL_LIMIT/rerank tuning, chatbot persistent modelConfig → chat auto, widget public topK UI, temperature/maxTokens LLM, migration, dependency.

## 5. Phân tích trước sửa (17 câu)

| # | Kết luận |
|---|----------|
| 1 | FE control: `ModelOverridePanel.jsx` |
| 2 | State: `overrideParams.topK` |
| 3 | FE gửi trong `overrideParams` nhưng BE ignore |
| 4 | Payload: `{ chatbotId, message, sessionId, overrideParams }` |
| 5 | `ChatRequest` không có topK |
| 6 | Controllers pass `ChatRequest` to `ChatService` |
| 7 | `retrieveWithMetadata(question, widgetId)` |
| 8 | Hardcoded `ANCHOR_TOP_K=30` |
| 9 | `embeddingService.search(..., ANCHOR_TOP_K, ...)` |
| 10 | Qdrant limit trong `EmbeddingService.search` |
| 11 | Có sync (`/api/chat`) và stream (`/api/playground/chat`) |
| 12 | Playground + `/api/chat` hỗ trợ field; public widget không bắt buộc |
| 13 | Chỉ màn có control (playground) |
| 14 | null topK → effective 30 (giữ cũ) |
| 15 | FE thêm `topK` trong JSON playground |
| 16 | Có unit test normalize/parse |
| 17 | Minimal: overload retrieval + map playground override |

## 6. Thiết kế wiring

Per-request `Integer topK` → `normalizeAnchorTopK` → chỉ thay đổi Qdrant anchor search limit; FINAL_LIMIT/expansion không đổi.

## 7. Default / min / max

- default: **30**
- min: **1**
- max: **30**

## 8. Validation rule

```java
Math.max(1, Math.min(30, requested)) // null → 30
```

Invalid string trong map → null → default.

## 9. File đã đọc

`ChatService.java`, `RagRetrievalService.java`, `ChatRequest.java`, `PlaygroundController.java`, `PlaygroundService.java`, `PlaygroundPage.jsx`, `ModelOverridePanel.jsx`, `playgroundApi.js`, `RAG_SOURCE_PRESENTATION_CLEANUP_21J_20260515.md`, eval runbooks.

## 10. File đã sửa

| File | Lớp |
|------|-----|
| `ChatRequest.java` | dto |
| `PlaygroundChatRequest.java` | dto |
| `RagRetrievalService.java` | service / retrieval |
| `ChatService.java` | service |
| `PlaygroundController.java` | api |
| `PlaygroundService.java` | service |
| `RetrievalTopKTest.java` | test |
| `playgroundApi.js` | ui/api |
| `ModelOverridePanel.jsx` | ui |
| `CompareConfigPanel.jsx` | ui |
| `ComparePane.jsx` | ui |
| docs eval + report | docs |

## 11. Diff từng file (tóm tắt)

### `ChatRequest.java`

```diff
+    private Integer topK;
```

### `RagRetrievalService.java`

```diff
-    private static final int ANCHOR_TOP_K = 30;
+    public static final int DEFAULT_ANCHOR_TOP_K = 30;
+    public static final int MIN_ANCHOR_TOP_K = 1;
+    public static final int MAX_ANCHOR_TOP_K = 30;
+    public RetrievalResult retrieveWithMetadata(String question, UUID widgetId, Integer topKOverride) {
+        int effectiveAnchorTopK = normalizeAnchorTopK(topKOverride);
+        log.info("[RAG] retrieval topK requested={}, effective={}", topKOverride, effectiveAnchorTopK);
-        anchors = embeddingService.search(variant, ANCHOR_TOP_K, widgetId);
+        anchors = embeddingService.search(variant, effectiveAnchorTopK, widgetId);
```

### `PlaygroundController.java`

```diff
+        chatRequest.setTopK(request.getTopK() != null ? request.getTopK()
+                : RagRetrievalService.parseTopKOverride(request.getOverrideParams()));
```

### `playgroundApi.js`

```diff
+            topK: clamped from overrideParams.topK,
             overrideParams,
```

## 12. API contract

- **Thêm** optional `topK` (integer) trên `ChatRequest` và playground body.
- Backward compatible: omitted/null → effective **30**.

## 13–14. FE / BE compatibility

- FE playground gửi topK; BE clamp.
- `/api/public/chat` có thể nhận topK nếu client gửi (không bắt buộc UI).

## 15. Source cap 21J

**Không đổi** — retrieval context có thể thay đổi theo topK; response sources vẫn cap 5.

## 16. Compile / test / build

| Command | Kết quả |
|---------|---------|
| Backend compile | **PASS** |
| RetrievalTopKTest + ChatServiceSourcePresentationTest | **PASS** |
| Frontend lint | **PASS** |
| Frontend build | **PASS** |
| Widget build | **NOT RUN** |

## 17. Runtime verification

Docker backend log:

- `requested=10, effective=10` **PASS**
- `requested=999, effective=30` **PASS**
- `requested=null, effective=30` **PASS**

## 18. Resource analysis

| Khía cạnh | Ảnh hưởng |
|-----------|-----------|
| Qdrant | `limit` = effective topK mỗi variant query |
| Context size | Gián tiếp qua số anchor; FINAL_LIMIT vẫn cap |
| RAM/CPU | topK=30 max bảo vệ máy yếu |
| DB/LLM calls | Không thêm |
| Reindex/migration | Không |
| 5 user concurrent @ max | 5×30 vector hits — chấp nhận với clamp |

## 19. Rủi ro còn lại

- FINAL_LIMIT 10 vẫn có thể giới hạn context dù anchor topK=30.
- Playground default UI topK=5 vs BE default 30 khi không gửi (playground luôn gửi 5).
- `modelConfig.topK` trên chatbot config chưa auto-wire widget chat.

## 20. Đề xuất tiếp theo

1. Wire `modelConfig.topK` làm fallback khi request không có topK.
2. Map temperature/maxTokens nếu cần playground parity.
3. Log metric effective topK trong analytics (optional).

---

**Kết luận:** Task 22A **PASS** — Top-K playground wired end-to-end tới Qdrant vector search.
