# CURSOR_REPORT_23H4 — Per-Request Token Usage Visibility

## Verdict: **PASS**

## Behavior changed?

**No** retrieval/chunking/Top-N/source cap/PromptBuilder content. **Yes** observability: logs + Playground debug response + UI panel.

## Token fields

| Field | Meaning |
|-------|---------|
| `estimatedInputTokens` | ceil((systemChars+promptChars)/4) |
| `reservedOutputTokens` | maxTokens |
| `estimatedTotalRequestTokens` | sum of above |
| `actualPromptTokens` | LangChain4j inputTokenCount when available |
| `actualCompletionTokens` | outputTokenCount |
| `actualTotalTokens` | totalTokenCount |
| `providerRequestedTokens` | parsed from Groq TPM error |

## Log example

```text
[RAG][token-usage] ... estimatedInputTokens=4776 reservedOutputTokens=1024
  estimatedTotalRequestTokens=5800 actualPromptTokens=null ... success=true
```

## API example (Playground done)

```json
{ "sources": [...], "tokenUsage": { "estimatedInputTokens": 3303, "reservedOutputTokens": 512, ... } }
```

Compare: `configA.tokenUsage`, `configB.tokenUsage`.

## Tests

`RagTokenAuditTest` — 6 cases PASS (estimate, total, parser, DTO, failure parse, no secrets).

## Artifacts

- `docs/eval/results/PER_REQUEST_TOKEN_USAGE_23H4_20260522.md`
- `docs/2026-05-22-per-request-token-usage-23h4.md`
