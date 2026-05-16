# RAG — modelConfig.topK fallback 22B (official result)

**File:** `docs/eval/results/RAG_MODEL_CONFIG_TOPK_FALLBACK_22B_20260515.md`  
**Report:** `reports/refactor/CURSOR_REPORT_22B_MODEL_CONFIG_TOPK_FALLBACK.md`

**Ngày:** 2026-05-15

---

## 1. modelConfig inventory

| Item | Giá trị |
|------|---------|
| Lưu trữ | `widget_configs.ui_config` (JSON column `ui_config` trên entity `WidgetConfig`) |
| Path JSON | `uiConfig.modelConfig.topK` |
| Field name | `topK` (camelCase) |
| Kiểu | `Number` hoặc `String` parseable int (qua `RagRetrievalService.parseTopKOverride`) |
| API đọc/ghi | `ChatbotUpdateRequest.modelConfig` → `WidgetService.updateChatbot` merge vào `uiConfig` |
| Default khi **trả API** | `WidgetService.DEFAULT_MODEL_CONFIG` → topK **5** (`mergeModelConfig`) |
| Default khi **retrieval fallback** | Chỉ dùng giá trị **đã persist**; không merge default 5 nếu key absent |

FE lưu qua `ChatbotConfigPage.jsx` → `modelConfig: { model, temperature, topK, maxTokens }`.

---

## 2. Design chosen

Precedence trong `ChatService` trước `retrieveWithMetadata`:

```text
1. ChatRequest.topK (REQUEST)
2. uiConfig.modelConfig.topK (MODEL_CONFIG) — DB findById chỉ khi request topK null
3. RagRetrievalService.normalizeAnchorTopK(null) → 30 (DEFAULT)
```

```java
Integer candidate = requestTopK != null ? requestTopK : configuredTopK;
int effective = normalizeAnchorTopK(candidate);
```

Log: `[RAG] retrieval topK source=REQUEST|MODEL_CONFIG|DEFAULT requested=... configured=... effective=...`

---

## 3. Precedence rule

| request.topK | modelConfig.topK | source | effective |
|--------------|------------------|--------|-----------|
| 10 | 5 | REQUEST | 10 |
| null | 5 | MODEL_CONFIG | 5 |
| null | null/absent | DEFAULT | 30 |
| 999 | 5 | REQUEST | 30 (clamp) |
| null | 999 | MODEL_CONFIG | 30 (clamp) |
| null | 0 | MODEL_CONFIG | 1 (clamp) |

---

## 4. Code change summary

| File | Thay đổi |
|------|----------|
| `WidgetService.java` | `parseModelConfigTopK(uiConfig)` static |
| `ChatService.java` | `TopKSource`, `TopKResolution`, `resolveTopK`, `resolveRetrievalTopK`; chat + stream |
| `ChatServiceModelConfigTopKTest.java` | 9 unit tests |

**Không sửa:** RagRetrievalService clamp, PromptBuilder, QueryAnalyzer, source cap 21J, PlaygroundService compare (vẫn explicit config topK).

---

## 5. Tests

| Test class | Cases |
|------------|-------|
| `ChatServiceModelConfigTopKTest` | 9 — precedence + parse modelConfig |
| `RetrievalTopKTest` | 6 — normalize/parse (regression 22A) |
| `ChatServiceSourcePresentationTest` | 6 — source cap 21J |

---

## 6. Compile / test

| Command | Kết quả |
|---------|---------|
| `Backend\.\mvnw.cmd -DskipTests compile` | **PASS** |
| `Backend\.\mvnw.cmd -Dtest=ChatServiceModelConfigTopKTest,RetrievalTopKTest,ChatServiceSourcePresentationTest test` | **PASS** (21 tests) |
| Runtime Docker | **NOT RUN** (env không verify trong session) |

---

## 7. Runtime verify

**NOT RUN** — expected khi có Docker:

- Chatbot `modelConfig.topK=5`, POST `/api/chat` không `topK` → log `source=MODEL_CONFIG effective=5`
- Cùng chatbot, `topK=10` → `source=REQUEST effective=10`
- Không modelConfig → `source=DEFAULT effective=30`

---

## 8. Limitations

- `mergeModelConfig` default topK=5 **chỉ** cho API response, không inject vào DB khi user chưa lưu model settings.
- Playground compare (`PlaygroundService`) không fallback modelConfig khi config map thiếu topK (chỉ explicit compare panel).
- Một `findById` thêm mỗi chat khi request không gửi topK.
- FINAL_LIMIT / expansion budget không đổi.

---

## 9. Source cap 21J

**Không ảnh hưởng** — `buildSourceDtosForResponse` / `MAX_RESPONSE_SOURCES=5` không đổi.
