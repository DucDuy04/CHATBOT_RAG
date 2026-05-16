# Fix loop — 22C FE LLM params wiring

**Phiên bản:** theo [RAG_BASELINE_CORE_FIX_LOOP_TEMPLATE.md](../RAG_BASELINE_CORE_FIX_LOOP_TEMPLATE.md)  
**Ngày:** 2026-05-15

---

## 1. Tên fix

`fe-playground-llm-temperature-maxtokens-wiring`

---

## 2. Mục tiêu fix (một dòng)

Wire `temperature` và `maxTokens` từ playground/modelConfig xuống Groq LLM với precedence request → modelConfig → default, clamp an toàn.

---

## 3. Checklist item liên quan

| ID | Gate |
|----|------|
| Playground override UX | G3 |
| 22A/22B topK không regress | G0 |

---

## 4. Files được phép sửa

DTO, ChatService, LlmFallbackService, WidgetService, Playground*, FE playground API/panels, tests, docs.

---

## 5. Files không được sửa

RagRetrievalService logic, QueryAnalyzer, PromptBuilder, source cap 21J, parser, migration, `.gitignore`.

---

## 6. Verify

| Bước | Kết quả |
|------|---------|
| `LlmGenerationOptionsTest` + `ChatServiceLlmParamsTest` | **PASS** |
| TopK + source cap regression | **PASS** |
| FE lint/build | **PASS** |
| Docker runtime LLM log | **NOT RUN** |

---

## 7. Rollback

Revert `LlmGenerationOptions`, ChatService resolver, LlmFallbackService overloads, FE playgroundApi; không reindex.
