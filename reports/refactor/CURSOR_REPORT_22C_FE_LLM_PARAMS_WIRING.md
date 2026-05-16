# CURSOR_REPORT_22C — FE LLM params (temperature / maxTokens) wiring

## 1. Mức độ hiểu task

- **~97%** — wire temperature/maxTokens FE→BE→Groq; precedence giống topK; không tuning RAG.
- **Chắc chắn:** LLM path dùng `LlmFallbackService` hardcode 0.1/1500; FE đã có controls.
- **Giả định:** default 0.1/1500 giữ hành vi cũ khi không config.

## 2. Tóm tắt yêu cầu

Optional `temperature` / `maxTokens` trên request; fallback `modelConfig`; truyền vào Groq builders; sync + stream + compare.

## 3. Phạm vi đã làm

- `LlmGenerationOptions` + normalize/parse
- DTO + PlaygroundController mapping
- `ChatService.resolveLlmGenerationOptions` + log
- `LlmFallbackService` overloads `generateWithFallback`, `generateFallbackAnswer`, `buildChatModel`, `buildStreamingModel`
- `WidgetService.parseModelConfigTemperature/MaxTokens`
- `PlaygroundService.compare` LLM options
- FE `playgroundApi.js` + help text
- Tests + docs

## 4. Phạm vi không làm

Retrieval, parser, QueryAnalyzer, PromptBuilder, source cap 21J, topK logic, migration, runtime Docker verify, widget FE gửi params.

## 5. Phân tích bắt buộc (tóm tắt)

| # | Kết luận |
|---|----------|
| FE controls | `ModelOverridePanel`, `CompareConfigPanel`, `ModelSettingsSection` |
| FE gửi trước 22C | topK yes; temp/max chỉ nested overrideParams — BE ignore |
| BE DTO | thiếu temperature/maxTokens |
| modelConfig | `ui_config.modelConfig.{temperature,maxTokens}` |
| LLM default | LlmFallbackService 0.1 / 1500; GroqConfig bean 0.1 / 1000 (unused main path) |
| ChatService LLM | `llmFallbackService.generateWithFallback` |
| Stream | inline builder 0.1 → dùng `buildStreamingModel` |
| Compare | cần map — đã thêm `resolveCompareLlmOptions` |
| Widget chat | fallback modelConfig qua ChatService — yes |
| Validation | `LlmGenerationOptions` static normalize |
| Minimal | record + resolver, không refactor lớn |

## 6. Thiết kế wiring

Option B: `LlmGenerationOptions` record → truyền per-call vào `LlmFallbackService`.

## 7–8. Precedence & defaults

| Param | request | modelConfig | default | min | max |
|-------|---------|-------------|---------|-----|-----|
| temperature | ✓ | ✓ | 0.1 | 0.0 | 1.0 |
| maxTokens | ✓ | ✓ | 1500 | 64 | 4096 |

## 9. Validation/clamp

`LlmGenerationOptions.normalizeTemperature` / `normalizeMaxTokens` — single place.

## 10. File đã đọc

22A/22B reports, `ChatService`, `LlmFallbackService`, `GroqConfig`, `WidgetService`, `PlaygroundController`, `playgroundApi.js`, `ModelOverridePanel.jsx`, tests topK/source cap.

## 11. File đã sửa

| File | Lớp |
|------|-----|
| `LlmGenerationOptions.java` | service (new) |
| `ChatRequest.java`, `PlaygroundChatRequest.java` | dto |
| `ChatService.java` | service |
| `LlmFallbackService.java` | service |
| `WidgetService.java` | service |
| `PlaygroundController.java` | api |
| `PlaygroundService.java` | service |
| `playgroundApi.js` | ui |
| `ModelOverridePanel.jsx`, `ComparePane.jsx` | ui |
| `LlmGenerationOptionsTest.java`, `ChatServiceLlmParamsTest.java` | test |
| docs eval + report | docs |

## 12. Diff từng file (tóm tắt)

### `LlmGenerationOptions.java` (new)

```diff
+ public record LlmGenerationOptions(double temperature, int maxTokens)
+ DEFAULT_TEMPERATURE=0.1, DEFAULT_MAX_TOKENS=1500
+ normalizeTemperature / normalizeMaxTokens / parse*Override
```

### `ChatRequest.java`

```diff
+    private Double temperature;
+    private Integer maxTokens;
```

### `LlmFallbackService.java`

```diff
+ generateWithFallback(messages, LlmGenerationOptions options)
+ buildChatModel(modelName, options) // uses effective temperature/maxTokens
+ buildStreamingModel(modelName, options)
```

### `ChatService.java`

```diff
+ resolveLlmGenerationOptions(widgetId, request)
+ llmFallbackService.generateWithFallback(messages, llmOptions.effective())
+ stream: llmFallbackService.buildStreamingModel(..., options)
```

### `playgroundApi.js`

```diff
+ temperature: clamp 0-1
+ maxTokens: clamp 64-4096
```

## 13. API contract

**Thêm** optional `temperature` (number), `maxTokens` (integer) trên `ChatRequest` / playground body. Omitted → modelConfig → default. **Backward compatible.**

## 14–15. FE / BE compatibility

Playground gửi root fields; BE đọc root hoặc `overrideParams`. Chatbot config persist → fallback widget chat.

## 16. Top-K 22A/22B

**Không đổi** — `resolveRetrievalTopK` giữ nguyên; tests **PASS**.

## 17. Source cap 21J

**Không đổi** — `ChatServiceSourcePresentationTest` **PASS**.

## 18. Compile / test / build

| Command | Kết quả |
|---------|---------|
| Backend compile | **PASS** |
| 34 unit tests (new + regression) | **PASS** |
| Frontend lint | **PASS** |
| Frontend build | **PASS** |
| Widget build | **NOT RUN** |
| Runtime Docker | **NOT RUN** |

## 19. Runtime verification

**NOT RUN** — kỳ vọng log `[LLM] generation options tempSource=MODEL_CONFIG ...`.

## 20. Resource analysis

| Khía cạnh | Ảnh hưởng |
|-----------|-----------|
| maxTokens cao | +output tokens → latency/cost ↑; clamp 4096 |
| temperature cao | +hallucination risk; clamp 1.0 |
| DB | +0–1 `findById` khi thiếu request params (shared `loadUiConfig` với topK có thể 2 calls nếu cả topK và LLM null) |
| LLM calls | Không thêm call |
| Reindex/migration | Không |
| 5 user concurrent @ maxTokens=4096 | Rủi ro TPM/cost — clamp giảm |

## 21. Rủi ro còn lại

- Double DB load khi request thiếu cả topK và LLM params (2 `findById` paths).
- Widget FE chưa gửi temperature/maxTokens.
- GroqConfig bean không sync với per-request options.
- Runtime chưa verify Docker.

## 22. Đề xuất tiếp theo

1. **22C2:** Runtime verify LLM log (mirror 22B2).
2. Widget chat FE gửi modelConfig overrides nếu cần.
3. Gộp `loadUiConfig` một lần cho topK + LLM khi cả hai cần fallback.

---

**Kết luận:** Task 22C **PASS** (compile + 34 tests + FE lint/build); runtime **NOT RUN**.
