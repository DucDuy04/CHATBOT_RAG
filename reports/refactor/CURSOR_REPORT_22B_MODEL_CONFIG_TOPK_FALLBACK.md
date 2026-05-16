# CURSOR_REPORT_22B — modelConfig.topK fallback

## 1. Mức độ hiểu task

- **~97%** — wire fallback `uiConfig.modelConfig.topK` khi request không có `topK`; giữ precedence request > config > default 30; không tuning RAG khác.
- **Chắc chắn:** topK persist trong `WidgetConfig.uiConfig`; 22A đã có `ChatRequest.topK` + `normalizeAnchorTopK`.
- **Giả định:** Playground qua `ChatService` được fallback; compare path riêng không bắt buộc trong scope.
- **Thiếu dữ liện:** runtime Docker không chạy trong session này.

## 2. Tóm tắt yêu cầu

Task 22B: `modelConfig.topK` làm fallback cho `/api/chat` (widget/public/playground→ChatService) khi request không truyền `topK`; validation/clamp giữ rule 22A.

## 3. Phạm vi đã làm

- `WidgetService.parseModelConfigTopK`
- `ChatService` precedence + log source
- Sync `chat()` + stream `chatStream()`
- `ChatServiceModelConfigTopKTest` (9 tests)
- Docs eval + fix-loop + report

## 4. Phạm vi không làm

Parser, QueryAnalyzer, PromptBuilder, source cap 21J, Qdrant, migration, FE, PlaygroundService compare fallback, temperature/maxTokens, `.gitignore`.

## 5. Phân tích bắt buộc (12 câu)

| # | Kết luận |
|---|----------|
| 1 | `modelConfig.topK` lưu trong `widget_configs.ui_config` JSON |
| 2 | Field: `topK` trong object `modelConfig` |
| 3 | Kiểu: Number/String → Integer qua `parseTopKOverride` |
| 4 | `ChatService` đã có `widgetConfigRepository`; session create load widget |
| 5 | Có thể parse từ `uiConfig` không cần `WidgetService` bean |
| 6 | Thêm `findById` khi `request.topK == null` — chấp nhận 1 query/chat |
| 7 | Request topK: `ChatController`, `PublicChatController`, `PlaygroundController` → `ChatService` |
| 8 | Stream + non-stream dùng `resolveRetrievalTopK` |
| 9 | Playground compare: không đổi (explicit config map) |
| 10 | Public widget chat: **có** fallback modelConfig (cùng ChatService) |
| 11 | modelConfig invalid → null → DEFAULT 30 |
| 12 | Minimal: static parse + `resolveTopK` trong ChatService |

## 6. modelConfig.topK hiện trạng

- **Persist:** `WidgetConfig.uiConfig["modelConfig"]["topK"]`
- **Ghi:** `PUT /api/chatbots/{id}` với `modelConfig` (FE `ChatbotConfigPage`)
- **Đọc API list:** `mergeModelConfig` thêm default topK=5 cho response only
- **Retrieval:** chỉ giá trị persist; absent → không dùng default 5

## 7. ChatService topK resolution trước sửa

```java
ragRetrievalService.retrieveWithMetadata(question, widgetId, request.getTopK());
// null → normalize → 30; bỏ qua modelConfig
```

## 8. Thiết kế precedence đã chọn

```text
REQUEST → MODEL_CONFIG → DEFAULT (normalize null → 30)
```

DB `findById(widgetId)` chỉ khi `request.getTopK() == null`.

## 9. Default / min / max topK

| Constant | Giá trị |
|----------|--------:|
| `DEFAULT_ANCHOR_TOP_K` | 30 |
| `MIN_ANCHOR_TOP_K` | 1 |
| `MAX_ANCHOR_TOP_K` | 30 |

## 10. Validation/clamp rule

Reuse `RagRetrievalService.normalizeAnchorTopK` — không duplicate.

## 11. File đã đọc

`RAG_FE_TOPK_WIRING_22A_20260515.md`, `FIX_LOOP_22A_FE_TOPK_WIRING.md`, `CURSOR_REPORT_22A_FE_TOPK_WIRING.md`, `ChatService.java`, `WidgetService.java`, `WidgetConfig.java`, `RagRetrievalService.java`, `ChatRequest.java`, `PlaygroundController.java`, `PlaygroundService.java`, `ChatbotConfigPage.jsx`, `ModelSettingsSection.jsx`, `RetrievalTopKTest.java`.

## 12. File đã sửa

| File | Lớp | Mục đích |
|------|-----|----------|
| `WidgetService.java` | service | `parseModelConfigTopK` |
| `ChatService.java` | service | precedence + wire chat/stream |
| `ChatServiceModelConfigTopKTest.java` | test | unit tests |
| docs eval + reports | docs | audit |

## 13. Diff từng file

### `WidgetService.java`

**Hiện trạng cũ:** chỉ merge default topK=5 khi build API response; không helper đọc raw uiConfig cho retrieval.

**Đã sửa:** static `parseModelConfigTopK` delegate `RagRetrievalService.parseTopKOverride`.

```diff
+    public static Integer parseModelConfigTopK(Map<String, Object> uiConfig) {
+        ...
+        return RagRetrievalService.parseTopKOverride(modelMap);
+    }
```

**Ảnh hưởng:** ChatService đọc topK persist mà không apply DEFAULT_MODEL_CONFIG 5 khi key missing.

### `ChatService.java`

**Hiện trạng cũ:** truyền thẳng `request.getTopK()`; null → effective 30.

**Đã sửa:**

```diff
+    enum TopKSource { REQUEST, MODEL_CONFIG, DEFAULT }
+    record TopKResolution(...)
+    static TopKResolution resolveTopK(Integer requestTopK, Integer configuredTopK) { ... }
+    private TopKResolution resolveRetrievalTopK(UUID widgetId, Integer requestTopK) { ... }

- retrieveWithMetadata(..., request.getTopK());
+ TopKResolution topKResolution = resolveRetrievalTopK(widgetId, request.getTopK());
+ retrieveWithMetadata(..., topKResolution.candidate());
```

**Ảnh hưởng:** widget/chat/playground→ChatService dùng modelConfig fallback; playground explicit topK vẫn REQUEST.

### `ChatServiceModelConfigTopKTest.java`

**Mới:** 9 tests theo acceptance matrix.

## 14. API contract

**Không đổi** — `topK` vẫn optional trên `ChatRequest`. Behavior thay đổi khi omit: effective có thể = modelConfig thay vì luôn 30.

## 15. Backward compatibility

- Client gửi `topK` → không đổi (22A).
- Client không gửi, chatbot **không** có modelConfig.topK → vẫn 30.
- Client không gửi, chatbot **có** modelConfig.topK → **mới** dùng config (thay vì 30).

## 16. Source cap 21J

**Không ảnh hưởng** — chỉ anchor Qdrant limit; `MAX_RESPONSE_SOURCES=5` giữ nguyên; tests 21J PASS.

## 17. Kết quả kiểm tra

| Command | Kết quả | Ghi chú |
|---------|---------|---------|
| `Backend\.\mvnw.cmd -DskipTests compile` | **PASS** | |
| `Backend\.\mvnw.cmd -Dtest=ChatServiceModelConfigTopKTest,RetrievalTopKTest,ChatServiceSourcePresentationTest test` | **PASS** | 21 tests |
| Frontend lint | **NOT RUN** | không sửa FE |
| Frontend build | **NOT RUN** | |
| Widget build | **NOT RUN** | |
| `docker compose config` | **NOT RUN** | |
| Runtime Docker | **NOT RUN** | |

## 18. Runtime verification

**NOT RUN** — expected log:

```text
[RAG] retrieval topK source=MODEL_CONFIG requested=null configured=5 effective=5
[RAG] retrieval topK source=REQUEST requested=10 configured=null effective=10
```

## 19. Resource analysis

| Khía cạnh | Ảnh hưởng |
|-----------|-----------|
| DB | +0–1 `findById(widgetId)` / chat khi request.topK null |
| Latency | ~1ms MySQL PK lookup trên máy yếu — chấp nhận |
| Qdrant | limit = effective topK (có thể 5 thay vì 30 khi config=5) |
| RAM/CPU | Giảm vector hits khi config < 30 |
| LLM/token | Gián tiếp qua ít anchor hơn nếu config thấp |
| MySQL/Qdrant cũ | Không migration; dữ liệu ui_config có sẵn được dùng |

## 20. Rủi ro còn lại

- Chatbot chưa lưu model settings → vẫn effective 30 (không auto-insert default 5 vào retrieval).
- Playground compare không fallback modelConfig khi panel thiếu topK.
- Log trùng: ChatService (source) + RagRetrievalService (requested/effective).
- FINAL_LIMIT 10 vẫn cap context sau anchor.

## 21. Đề xuất tiếp theo

1. Runtime verify Docker với chatbot `modelConfig.topK=5`.
2. Optional: compare path fallback modelConfig.
3. Map temperature/maxTokens playground nếu cần parity.

---

**Kết luận:** Task 22B **PASS** (compile + unit tests); runtime **NOT RUN**.
