# Per-Request Token Usage Visibility — 23H4 (2026-05-22)

## Kết luận: **PASS**

## Mục tiêu

Sau mỗi request chat/playground/compare, backend log `[RAG][token-usage]` và Playground trả `tokenUsage` trong response debug (SSE `done` + Compare JSON).

## Log format

```text
[RAG][token-usage] requestId=... mode=CHAT|PLAYGROUND|COMPARE_A|COMPARE_B|WIDGET
  chatbotId=... sessionId=... model=... provider=GROQ temperature=...
  contextTopN=... finalContexts=... maxTokens=...
  systemChars=... historyChars=... contextChars=... questionChars=... promptChars=...
  estimatedInputTokens=... reservedOutputTokens=... estimatedTotalRequestTokens=...
  actualPromptTokens=null|N actualCompletionTokens=... actualTotalTokens=...
  providerRequestedTokens=null|N
  embeddingCalls=... rerankCalls=... llmCallIndex=... fallbackAttempt=...
  success=true|false errorType=... errorCode=... compareMode=...
```

Legacy `[RAG][token-audit]` removed from finish path — chỉ còn `[RAG][token-usage]`.

## Response `tokenUsage` schema

```json
{
  "estimatedInputTokens": 3303,
  "reservedOutputTokens": 1024,
  "estimatedTotalRequestTokens": 4327,
  "actualPromptTokens": null,
  "actualCompletionTokens": null,
  "actualTotalTokens": null,
  "providerRequestedTokens": null,
  "systemChars": 5104,
  "historyChars": 0,
  "contextChars": 6922,
  "questionChars": 70,
  "promptChars": 8105,
  "finalContexts": 5,
  "contextTopN": 5,
  "embeddingCalls": 3,
  "rerankCalls": 2,
  "llmCallIndex": 1,
  "model": "llama-3.3-70b-versatile",
  "provider": "GROQ"
}
```

Playground SSE `done` event (khi `playgroundDebugSources=true`):

```json
{ "sources": [...], "tokenUsage": { ... } }
```

Widget/Chat stream vẫn nhận `done` dạng mảng sources (không đổi contract).

## Actual vs estimated

| Field | Nguồn |
|--------|--------|
| `estimatedInputTokens` | `ceil((systemChars + promptChars) / 4)` |
| `reservedOutputTokens` | `maxTokens` request/config |
| `estimatedTotalRequestTokens` | input estimate + reserved |
| `actual*` | `Response.tokenUsage()` LangChain4j sau LLM success |
| `providerRequestedTokens` | Parse regex `Requested (\d+) tokens` từ Groq error chain |

**Lưu ý:** Groq TPM thường cao hơn estimate (vd. Requested 22307 vs estimate ~9000). UI hiển thị cảnh báo khi không có actual.

## Runtime (post-deploy)

| Test | Expected | Ghi chú |
|------|----------|---------|
| Playground Top-N=5, maxTokens=512 | tokenUsage in done + log | `estimatedTotal = input + 512` |
| Top-N=10 | input/context tăng | So sánh `contextChars` |
| Same session 3 câu | `historyChars` tăng | |
| Compare A topK=5, B topK=10 | `configA/B.tokenUsage` | `compareMode=true` in log |
| Overload | `providerRequestedTokens` populated | `success=false` |

## Tests

| Command | Result |
|---------|--------|
| `mvnw -DskipTests compile` | PASS |
| `mvnw test -Dtest=RagTokenAuditTest,...` | PASS (6 tests) |

## Files changed

- `RagTokenAudit.java` — snapshot + `[RAG][token-usage]`
- `TokenUsageDto.java` — new
- `ChatService.java`, `PlaygroundService.java`, `LlmFallbackService.java`
- `PlaygroundCompareResult.java`
- `TokenUsagePanel.jsx`, `PlaygroundPage.jsx`, `ComparePane.jsx`
- `RagTokenAuditTest.java`

**Behavior:** retrieval/chunking/Top-N/source cap unchanged.
