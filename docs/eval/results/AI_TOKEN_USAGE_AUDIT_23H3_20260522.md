# AI Token Usage / Prompt Size Audit — 23H3 (2026-05-22)

## Kết luận: **PASS** (instrumentation + runtime matrix + root-cause ranking)

## Environment

| Item | Value |
|------|--------|
| Date | 2026-05-22 |
| Backend | Docker `chatbot-backend`, profile `docker`, port 8080 |
| Chatbot ID | `7fc5a049-a1ab-49e2-a106-0bd322c3aab7` |
| Document | SoTayHocVu (23H2: 784 chunks, avg 801 chars) |
| LLM primary | `llama-3.3-70b-versatile` (Groq) |
| LLM fallbacks | `llama-3.1-8b-instant`, `llama-4-scout-17b-16e-instruct`, `qwen/qwen3-32b` |
| Embedding | Nomic `nomic-embed-text-v1.5` |
| Rerank | Cohere `rerank-multilingual-v3.0` (enabled in runtime — `rerankCalls=2`) |
| Default maxTokens | 1500 (`LlmGenerationOptions.DEFAULT_MAX_TOKENS`) |
| UI test maxTokens | 1024 |
| fixedVectorAnchorK | 30 |
| Token estimate | `ceil(chars / 4)` — conservative vs Groq billed tokens |

## Instrumentation added

Log tag: `[RAG][token-audit]` (class `RagTokenAudit.java`)

- Wired in: `ChatService` (sync + stream/playground), `PlaygroundService` (compare A/B), `EmbeddingService.search`, `RerankService`, `LlmFallbackService`
- Does **not** log full prompt, API keys, or context bodies
- Counters per request (ThreadLocal): `embeddingCalls`, `rerankCalls`, `llmCallIndex`

Example (COMPARE_A, Top-N=5):

```text
[RAG][token-audit] requestId=8957c140 mode=COMPARE_A ... contextTopN=5 finalContexts=5
  systemChars=5104 historyMessages=0 historyChars=0 contextChars=6922 questionChars=70
  userPromptChars=8105 estimatedPromptTokens=3303 estimatedMaxOutputTokens=1024
  estimatedTotalTokens=4327 embeddingCalls=3 rerankCalls=2 compareMode=true
```

Script: `docs/eval/results/_run_23h3_token_audit.ps1`

## Per-question external AI calls (from source + logs)

| Stage | Calls / question | Provider |
|--------|------------------|----------|
| Query embedding | **2–3** (query variants in `rewriteQuery`) | Nomic |
| Rerank scoring | **2** when Cohere enabled (pre-lock + final `scoreCandidates`) | Cohere |
| LLM answer | **1** stream primary; on TPM/rate-limit → **up to 3 fallbacks** × LangChain4j **3 retries** each | Groq |
| Query analyzer | 0 external LLM | local rules |

**Compare mode:** runs `runCompareOnce` twice → **~2×** embedding + rerank + LLM per user click.

## Test A — Single question, Context Top-N matrix

Question: *"Thời gian thi kết thúc học phần học kỳ 1 năm học 2025-2026 là khi nào?"*  
Method: `POST /api/playground/compare` (configA metrics, temperature=0.2, maxTokens=1024)

| Context Top-N | finalContexts | contextChars | userPromptChars | est. prompt tokens | est. total tokens | Overload |
|---------------|---------------|--------------|-----------------|-------------------|-------------------|----------|
| 5 | 5 | 6,922 | 8,105 | 3,303 | 4,327 | No |
| 10 | 10 | 12,235 | 14,000 | 4,776 | 5,800 | No |
| 20 | 19 | 27,998 | 30,773 | 8,970 | 9,994 | No |
| 30 | 19 | 27,998 | 30,773 | 8,970 | 9,994 | No |

**Observation:** Top-N 20 and 30 converge — `maxContextChars` budget (~28k chars in this query) caps chunks before count reaches 30. Groq may still bill **~2–3×** higher than `chars/4` (see § Provider errors).

## Test B — Same session (partial 3/10 + historical 10-turn UI)

Session: `ef2932bb-d565-49ab-83d1-570b7028d172`, Top-N=10, maxTokens=1024

| Q# | historyMessages | historyChars | contextChars | est. prompt tokens | Overload |
|----|-----------------|--------------|--------------|-------------------|----------|
| 1 | 1 | 46 | 16,720 | 5,904 | No |
| 2 | 3 | 286 | 17,171 | 6,179 | No |
| 3 | 5 | 522 | 8,622 | 4,117 | No |

Historical UI session (pre-instrumentation docker logs, 2026-05-22): after several turns, Groq returned  
`rate_limit_exceeded` / **TPM** — `Requested 22307` (8b), `Requested 31101` (qwen3-32b) with `Limit 6000` → user sees *"Dịch vụ AI hiện đang quá tải..."*.

**History semantics:** `findTop10BySessionIdOrderByCreatedAtAsc` = **10 oldest** messages, not 10 latest. Growth is bounded but may drop recent turns after ~5 Q&A pairs.

## Test C — New session each question

Same 3 questions as B with fresh `sessionId`: `historyMessages=1`, `historyChars≈68` each — **historyChars flat** vs B Q2/Q3.

→ For controlled tests, **new session per question** avoids history inflation; overload in long UI sessions is driven mainly by **context + fallback**, not history alone in short-answer runs.

## Test D — Compare mode

Question: *"KTR3185 tên là gì và mấy tín chỉ?"*  
Config A: topK=5, Config B: topK=20, maxTokens=1024

| Side | embeddingCalls | rerankCalls | est. prompt tokens | LLM calls (logged at audit) |
|------|----------------|-------------|-------------------|----------------------------|
| COMPARE_A | 3 | 2 | 3,303 (topK=5 smoke) | 1+ per side at audit time |
| COMPARE_B | 3 | 2 | 8,970 (topK=30 smoke) | 1+ per side |

**Compare nhân đôi:** 2× retrieval (embed+rerank) + 2× LLM sequential — confirmed.

## Test E — maxTokens impact

Question: *"Liệt kê các học phần của ngành Kiến trúc K45..."* — `LIST_ALL`, Top-N=20

| maxTokens | finalContexts | contextChars | est. prompt tokens | est. total tokens | Answer | Overload |
|-----------|---------------|--------------|-------------------|-------------------|--------|----------|
| 512 | 20 | 23,839 | 8,270 | 8,782 | 59 chars | **Yes** |
| 1024 | 20 | 23,839 | 8,270 | 9,294 | 59 chars | **Yes** |
| 2048 | 20 | 23,839 | 8,270 | 10,318 | 59 chars | **Yes** |

Prompt size identical; overload persists across maxTokens → **prompt/input TPM**, not output length. Default **1500** maxTokens is high for weak tier; test used 512–2048 — all failed once context ~24k chars + system ~5k.

## Root cause ranking (top 3)

1. **Final RAG contexts + char budget** (`contextChars` 7k–28k+; rerank-lock can set `maxContextChars=64000` per `[RAG][select]` logs) — **largest prompt component**; Groq TPM sees **22k–31k tokens/request** vs ~8–9k `chars/4` estimate.
2. **LLM fallback + retry loop** on primary TPM failure — streams fail → non-stream fallback tries 3 models; LangChain4j **3 retries** each → **multiplies Groq calls** without shrinking prompt (`LlmFallbackService`, docker stack traces).
3. **System prompt** (~**5,104 chars** fixed) + query-type instructions — material baseline on every call.

**Secondary:** Compare 2× cost; 2–3 Nomic embeds + 2 Cohere reranks per question; maxTokens adds reserved output to TPM; history smaller than context in SoTay runs but long assistant answers in UI can add up.

## Provider errors

| Signal | Detail |
|--------|--------|
| HTTP / code | Groq `rate_limit_exceeded`, `type: tokens` (TPM) |
| Message | `Request too large for model ... tokens per minute (TPM): Limit 6000, Requested 22307` (and 31101 on qwen3-32b) |
| User message | `Dịch vụ AI hiện đang quá tải. Vui lòng thử lại sau ít phút.` from `LlmFallbackService` after all models fail |
| Not primary | 503/timeout not observed in sampled logs; overload path is **TPM / request too large**, not empty queue |

## Recommendations (no behavior change in 23H3)

1. **Context Top-N 5–10** for SoTay fact/table lookups; 20+ hits char ceiling without more unique facts.
2. **Cap prompt char budget** below 18k default / audit rerank-lock → 64k path when lock scopes huge sections (`sec_idx_1` TOC).
3. **Trim or window chat history** (use latest N, not oldest 10).
4. **Lower default maxTokens** (e.g. 512–768) for playground/production weak tier.
5. **Compare mode:** warn cost; optional single-side preview when provider stressed.
6. **Fallback:** skip retry when `Request too large` (TPM size); do not retry same oversized prompt on smaller TPM models.
7. **Manual test:** new session per question batch to isolate retrieval settings.

## SoTayHocVu Context Top-N guidance

| Query type | Suggested Top-N |
|------------|-----------------|
| Fact / date / policy | 5–10 |
| Course code table (KTR*) | 5–10 |
| Schedule / LIST_ALL | 10–15 (expect overload risk >15 without char cap fix) |

## Verification commands

| Command | Result |
|---------|--------|
| `mvnw -DskipTests compile` (JAVA_HOME=jdk-21) | **PASS** |
| `mvnw test -Dtest=RagTokenAuditTest,ChatServiceLlmParamsTest,LlmGenerationOptionsTest` | **PASS** |
| `docker compose build backend && up -d backend` | **PASS** |
| Runtime matrix (compare + playground SSE) | **PASS** |
| Frontend lint/build | **NOT RUN** (scope) |
| `docker compose config` | **NOT RUN** |

## Files changed (instrumentation only)

- `Backend/src/main/java/.../RagTokenAudit.java` (new)
- `ChatService.java`, `PlaygroundService.java`, `EmbeddingService.java`, `RerankService.java`, `LlmFallbackService.java`
- `Backend/src/test/java/.../RagTokenAuditTest.java` (new)
- `docs/eval/results/_run_23h3_token_audit.ps1` (new)

**Behavior:** unchanged retrieval, chunking, Context Top-N semantics, source cap.
