# CURSOR_REPORT 23D — Playground & Model Settings Controls Parity

## 1. Mức độ hiểu task

- **~95%**
- **Chắc chắn:** Playground normal dùng `/api/playground/chat` + `chatStream`; source cap 5 là nguyên nhân Top-K không đổi số nguồn; Compare không cap; Model Settings save payload đúng key `topK`.
- **Giả định:** User đánh giá Model Settings Top-K chủ yếu qua số sources hiển thị (không qua log retrieval).
- **Thiếu dữ kiện:** Manual verify trên Docker production-like chưa chạy trong session này.

## 2. Tóm tắt yêu cầu

Đồng bộ Top-K/temperature Playground thường, Top-K Model Settings, ẩn sidebar Compare Mode; giữ source cap production ≤5.

## 3. Hiện trạng trước khi sửa

- Playground Top-K: retrieval đúng, API/SSE sources luôn ≤5.
- Compare Top-K: PASS (không qua ChatService cap).
- Temperature Playground: FE/BE đã wire (22C).
- Model Settings: PUT có `topK`; runtime `resolveRetrievalTopK` đọc `modelConfig.topK`.
- Compare UI: sidebar phải luôn hiện.

## 4. Nguyên nhân gốc (source)

| Nhóm | Root cause |
|------|------------|
| Playground Top-K source count | `ChatService.buildSourceDtosForResponse` + `MAX_RESPONSE_SOURCES=5` trên `chatStream` dùng cho playground |
| Playground temperature | Không có bug — đã map `overrideParams.temperature` → `ChatRequest.temperature` → `resolveLlmGenerationOptions` |
| Model Settings Top-K | Persist/runtime OK; hiển thị sources public/playground (không debug) vẫn cap 5 |
| Compare sidebar | `PlaygroundPage` không `!compareMode` wrap cột phải |

## 5. Chiến lược sửa

**Option A2 (minimal):** flag `playgroundDebugSources` chỉ set từ `PlaygroundController`; presentation cap = effective topK. Production `/api/chat` không đổi.

## 6. Danh sách file đã đọc

| Path | Mục đích | Kết luận |
|------|----------|----------|
| `PlaygroundPage.jsx` | Layout, override, compare | Sidebar không conditional; FE slice sources theo topK |
| `playgroundApi.js` | Payload | Gửi topK/temperature/maxTokens |
| `PlaygroundController.java` | Routing | Map sang ChatRequest + chatStream |
| `ChatService.java` | Cap + stream | Cap 5 trên mọi stream trước fix |
| `PlaygroundService.java` | Compare | Sources không cap |
| `WidgetService.java` | modelConfig merge | topK persist/parse OK |
| `ChatbotConfigPage.jsx` | Model save | Payload có topK |

## 7. Danh sách file đã sửa

| Path | Sửa để | Lớp |
|------|--------|-----|
| `ChatRequest.java` | Flag playground debug sources | api/dto |
| `PlaygroundController.java` | Set flag | api |
| `ChatService.java` | Dynamic presentation cap | service |
| `ChatServiceSourcePresentationTest.java` | Tests | test |
| `PlaygroundPage.jsx` | Hide sidebar compare | ui |
| `ChatbotConfigPage.jsx` | Sync form after model save | ui |
| `chatbotsApi.js` | Mock topK | api |

## 8. Diff từng file

### `ChatRequest.java`

- **Cũ:** Không có cách tách presentation cap playground.
- **Sửa:** Thêm `playgroundDebugSources`.
- **Vì sao:** Chỉ playground bypass cap 5, không lộ public API.

```diff
+    private Boolean playgroundDebugSources;
```

### `PlaygroundController.java`

```diff
+        chatRequest.setPlaygroundDebugSources(true);
```

### `ChatService.java`

```diff
+    static int resolveSourcePresentationCap(ChatRequest request, TopKResolution topKResolution) {
+        if (Boolean.TRUE.equals(request.getPlaygroundDebugSources()) && topKResolution != null) {
+            return topKResolution.effective();
+        }
+        return MAX_RESPONSE_SOURCES;
+    }
+    List<ChatResponse.SourceDto> buildSourceDtosForResponse(List<RetrievedContext> contexts, int maxSources)
```

Stream path:

```diff
+                int sourcePresentationCap = resolveSourcePresentationCap(request, streamTopK);
+                List<ChatResponse.SourceDto> sources = buildSourceDtosForResponse(contexts, sourcePresentationCap);
```

### `PlaygroundPage.jsx`

```diff
+        {!compareMode && (
         <motion.div className="hidden lg:flex w-72 ...">
         ...
+        )}
```

### `ChatbotConfigPage.jsx`

```diff
+      if (updated?.modelConfig) {
+        setForm((f) => ({ ...f, topK: updated.modelConfig.topK ?? f.topK, ... }));
+      }
```

## 9. Ảnh hưởng sau sửa

| Behavior | Thay đổi |
|----------|----------|
| Playground normal sources | Có thể >5 khi topK>5 và retrieval đủ |
| `/api/chat` / widget | Giữ cap ≤5 |
| OOS refusal cap | Giữ ≤2 (applyAnswerAwareSourceCap) |
| Compare | Logic API không đổi; sidebar ẩn |
| Temperature | Giữ nguyên wiring 22C |
| LLM context | Không đổi — cap chỉ presentation |

## 10. Edge cases

- topK=1 → cap presentation 1
- topK>30 → clamp bởi `normalizeAnchorTopK` (MAX_ANCHOR_TOP_K)
- `playgroundDebugSources` chỉ playground — widget không set được
- Compare mode: sources trong `ComparePane`, không cần sidebar
- Thiếu env / Qdrant down — không đổi

## 11. Kết quả kiểm tra

| Command | Kết quả | Ghi chú |
|---------|---------|---------|
| `mvnw -DskipTests compile` | PASS | |
| `mvnw test` (subset 18 tests) | PASS | Source + modelConfig topK |
| `npm run lint` | PASS | |
| `npm run build` | PASS | |
| `npm run build:widget` | NOT RUN | Không sửa widget |
| `docker compose config` | PASS | |
| Manual playground topK 3/10 | NOT RUN | Cần stack + PDF |

## 12. Rủi ro còn lại

- Manual UI chưa verify trên Docker.
- Model Settings: user vẫn thấy tối đa 5 sources trên widget chat (đúng thiết kế 21J).
- Playground luôn gửi request topK từ override panel — che `modelConfig.topK` khi test trên playground (expected).

## 13. Đề xuất tiếp theo

1. Manual: playground topK 3 vs 10 + log temperature.
2. Model Settings: verify log `source=MODEL_CONFIG` trên `/api/chat/stream` (không playground).
3. Không mở rộng scope RAG/parser.

## API contract

- Thêm field nội bộ `playgroundDebugSources` trên `ChatRequest` — chỉ playground controller set; **không breaking** public clients.

## RAG core

**Không sửa** RagRetrievalService, PromptBuilder, QueryAnalyzer, parser.

## Kết luận

**PASS** (code + unit tests). Manual runtime **PARTIAL** (not run).
