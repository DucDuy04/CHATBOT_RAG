# RAG — FE LLM params wiring 22C (official result)

**File:** `docs/eval/results/RAG_FE_LLM_PARAMS_WIRING_22C_20260515.md`  
**Report:** `reports/refactor/CURSOR_REPORT_22C_FE_LLM_PARAMS_WIRING.md`

**Ngày:** 2026-05-15

---

## 1. FE inventory

| Control | File | Gửi payload? (trước) | Sau 22C |
|---------|------|---------------------|---------|
| Temperature slider 0–1 | `ModelOverridePanel.jsx` | Chỉ trong `overrideParams` | Root + `overrideParams` |
| Max tokens 64–4096 | `ModelOverridePanel.jsx` | Chỉ trong `overrideParams` | Root + `overrideParams` |
| Compare A/B | `CompareConfigPanel.jsx` | Trong `configA`/`configB` | BE compare dùng |
| Chatbot persistent | `ModelSettingsSection.jsx` | `PUT modelConfig` | Fallback qua ChatService |

`playgroundApi.js` gửi `temperature`, `maxTokens` (clamp FE) cùng `topK`.

---

## 2. BE inventory

| Layer | Trước | Sau |
|-------|-------|-----|
| `ChatRequest` | chỉ `topK` | + `temperature`, `maxTokens` |
| `PlaygroundChatRequest` | chỉ `topK` | + fields + `overrideParams` |
| `ChatService` | LLM hardcode 0.1 / fallback 1500 | `resolveLlmGenerationOptions` |
| `LlmFallbackService` | `.temperature(0.1).maxTokens(1500)` cố định | overload + `LlmGenerationOptions` |
| Stream | `temperature(0.1)` inline builder | `buildStreamingModel(..., options)` |
| `PlaygroundService.compare` | default LLM | resolve từ config + modelConfig |

---

## 3. LLM service inventory

- **Sync chat:** `LlmFallbackService.generateWithFallback(messages, options)`
- **Stream:** `LlmFallbackService.buildStreamingModel` + fallback `generateFallbackAnswer(..., options)`
- **Bean `GroqConfig.chatModel`:** không đổi (0.1 / 1000) — path chat chính không dùng bean này
- **Defaults thực tế chat:** `LlmGenerationOptions.DEFAULT_TEMPERATURE=0.1`, `DEFAULT_MAX_TOKENS=1500` (giữ hành vi LlmFallbackService cũ)

---

## 4. Design chosen

`LlmGenerationOptions` record + resolver trong `ChatService` (mirror topK 22B):

```text
temperature: request → uiConfig.modelConfig → 0.1
maxTokens:   request → uiConfig.modelConfig → 1500
```

Clamp tại `LlmGenerationOptions.normalize*`.

Log: `[LLM] generation options tempSource=... maxTokensSource=... effectiveTemperature=... effectiveMaxTokens=...`

---

## 5. Precedence rule

| Param | 1 | 2 | 3 |
|-------|---|---|---|
| temperature | `request.temperature` | `modelConfig.temperature` | 0.1 |
| maxTokens | `request.maxTokens` | `modelConfig.maxTokens` | 1500 |

---

## 6. Validation / clamp

| Param | min | max | default |
|-------|-----|-----|---------|
| temperature | 0.0 | 1.0 | 0.1 |
| maxTokens | 64 | 4096 | 1500 |

Invalid string trong map → null → fallback tier tiếp theo.

---

## 7. Code change summary

**Backend:** `LlmGenerationOptions.java`, DTOs, `ChatService`, `LlmFallbackService`, `WidgetService`, `PlaygroundController`, `PlaygroundService`, tests.

**Frontend:** `playgroundApi.js`, help text `ModelOverridePanel`, `ComparePane`.

**Không sửa:** RagRetrievalService, QueryAnalyzer, PromptBuilder, source cap 21J, topK precedence.

---

## 8. Tests

| Class | Tests |
|-------|-------|
| `LlmGenerationOptionsTest` | 8 |
| `ChatServiceLlmParamsTest` | 5 |
| Regression topK + source cap | 15 |

**Total new+regression:** 34 tests — **PASS**

---

## 9. Frontend lint / build

| Command | Result |
|---------|--------|
| `npm run lint` | **PASS** |
| `npm run build` | **PASS** |
| `npm run build:widget` | **NOT RUN** |

---

## 10. Runtime verify

**NOT RUN** trong session implement — expected khi Docker up:

- Chatbot `modelConfig.temperature=0.2`, `maxTokens=256`, chat không field → log `tempSource=MODEL_CONFIG`
- Request `temperature=0.7`, `maxTokens=512` → `tempSource=REQUEST`
- Invalid clamp → effective trong range

---

## 11. Limitations

- `GroqConfig` bean vẫn 0.1/1000 nếu code path khác dùng bean.
- Widget `/api/chat` nhận field optional nhưng widget FE chưa gửi temperature/maxTokens.
- Runtime Docker chưa verify trong task implement.

---

## 12. Top-K / source cap

- **Top-K 22A/22B:** không đổi logic — regression tests **PASS**
- **Source cap 21J:** không đổi — `ChatServiceSourcePresentationTest` **PASS**
