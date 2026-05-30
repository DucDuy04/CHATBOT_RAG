# CURSOR_REPORT_23H3 — AI Token Usage / Prompt Size Audit

## Task

23H3 — VERIFY + INSTRUMENT ONLY: identify highest token/model-call cost causing Groq overload message after ~10 consecutive Playground questions on SoTayHocVu.

## Files read

See `docs/2026-05-22-ai-token-usage-audit-23h3.md` §6.

## Files modified

- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/RagTokenAudit.java` (**new**)
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/ChatService.java`
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/PlaygroundService.java`
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/EmbeddingService.java`
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/RerankService.java`
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/LlmFallbackService.java`
- `Backend/src/test/java/KLTN/RAG_CHATBOT_BE/service/RagTokenAuditTest.java` (**new**)
- `docs/eval/results/_run_23h3_token_audit.ps1` (**new**)

## Behavior changed?

**No** — retrieval, parser, chunking, Context Top-N semantics, source cap unchanged.

## Log format

```text
[RAG][token-audit] requestId=... mode=CHAT|PLAYGROUND|COMPARE_A|COMPARE_B|WIDGET
  chatbotId=... sessionId=... model=... temperature=... maxTokens=... contextTopN=...
  finalContexts=... systemChars=... historyMessages=... historyChars=... contextChars=...
  questionChars=... userPromptChars=... estimatedPromptTokens=... estimatedMaxOutputTokens=...
  estimatedTotalTokens=... llmCallIndex=... embeddingCalls=... rerankCalls=... compareMode=...
```

## Test results (runtime 2026-05-22)

| Test | Result |
|------|--------|
| A Top-N 5/10/20/30 | PASS — prompt scales with Top-N until char cap (~28k) |
| B same session | PARTIAL 3/10 — historyChars 46→522; overload on long UI sessions (historical logs) |
| C new session | PASS — history flat ~68 chars |
| D compare | PASS — 2× embed+rerank+LLM |
| E maxTokens 512/1024/2048 | PASS — overload on LIST_ALL; prompt identical |

## Root cause

1. **RAG context chars** (dominant; Groq TPM 22k–31k on fallback).
2. **LLM fallback + retries** after primary TPM failure.
3. **Fixed system prompt** (~5.1k chars).

## Recommendations

- Top-N **5–10** for SoTay; cap locked-scope char budget; trim latest history; lower default maxTokens; skip oversized retry in fallback; warn Compare double cost.

## Verdict

**PASS** — instrumentation + matrix + top-3 causes documented.

## Artifacts

- `docs/eval/results/AI_TOKEN_USAGE_AUDIT_23H3_20260522.md`
- `docs/2026-05-22-ai-token-usage-audit-23h3.md`
