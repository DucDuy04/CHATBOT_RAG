# RAG — LLM params (temperature / maxTokens) runtime verify 22C2 (official result)

**File:** `docs/eval/results/RAG_LLM_PARAMS_RUNTIME_VERIFY_22C2_20260515.md`  
**Report:** `reports/refactor/CURSOR_REPORT_22C2_LLM_PARAMS_RUNTIME_VERIFY.md`  
**Ngày:** 2026-05-15  
**Loại task:** VERIFY ONLY — không sửa code

---

## 1. Environment

| Item | Giá trị |
|------|---------|
| OS | Windows 10 |
| Stack | `docker compose up --build -d` (backend + frontend recreated) |
| Backend | `http://localhost:8080` |
| Qdrant | `http://localhost:6333` (1 collection) |
| Profile | `SPRING_PROFILES_ACTIVE=docker` |
| Env keys | `GROQ_API_KEY` present (***VTiv), `NOMIC_API_KEY` present (***tz84) |

Services: backend, frontend, mysql (healthy), qdrant — all **running**.

---

## 2. Compile / test (pre-runtime)

| Command | Kết quả |
|---------|---------|
| `Backend\.\mvnw.cmd -DskipTests compile` | **PASS** |
| `Backend\.\mvnw.cmd -Dtest=LlmGenerationOptionsTest,ChatServiceLlmParamsTest,RetrievalTopKTest,ChatServiceModelConfigTopKTest,ChatServiceSourcePresentationTest test` | **PASS** (34 tests) |
| `docker compose config -q` | **PASS** |

---

## 3. Chatbot / document setup

### Chatbot A (modelConfig LLM params persisted)

| Field | Giá trị |
|-------|---------|
| Name | `22C2-Verify-A-225925` (suffix từ script) |
| ID | `a780a357-3fd3-4b91-8606-d049260a5075` |
| Widget key | `1915...5d62` (masked) |
| Setup | `POST /api/chatbots` → `PUT` `{ "modelConfig": { "temperature": 0.2, "maxTokens": 256, "topK": 5 } }` |
| GET API `modelConfig` | `temperature=0.2`, `maxTokens=256`, `topK=5` |
| DB `ui_config` (recent row) | `modelConfig.temperature=0.2`, `maxTokens=256`, `topK=5` |
| Document | `RAG_GOLDEN_TEST_DOCUMENT.txt` → `88955d11-02e1-4d29-a1ab-8f6937241953` — **INDEXED** |

### Chatbot B (no persisted modelConfig LLM params)

| Field | Giá trị |
|-------|---------|
| Name | `22C2-Verify-B-226044` |
| ID | `2fef74a1-59f5-4bea-90d6-77a55a1c0b1b` |
| Widget key | `b9da...0653` (masked) |
| Setup | `POST /api/chatbots` only (no PUT modelConfig) |
| GET API `modelConfig` | `temperature=0.7`, `maxTokens=1024`, `topK=5` (merged default từ `WidgetService.mergeModelConfig`) |
| DB `ui_config` (200 chars) | chỉ `domain` + `description` — **không** có `modelConfig` block |
| Document | golden txt — **INDEXED** |

**Ghi chú:** Chatbot B chứng minh runtime không dùng API merged default cho LLM; resolver dùng `DEFAULT` → `0.1` / `1500`.

---

## 4. Runtime verification table — LLM params

Endpoint: `POST /api/chat` + header `X-Widget-Key`  
Message: `Mã xác nhận golden là gì?` (trừ OOS)

| # | Chatbot | req temp | req maxTokens | configured (log) | Expected temp source | Expected max source | Expected effective | Observed log (ChatService) | Pass |
|---|---------|----------|---------------|------------------|----------------------|---------------------|-------------------|----------------------------|------|
| 1 | A | omitted | omitted | temp=0.2, max=256 | MODEL_CONFIG | MODEL_CONFIG | 0.2 / 256 | `tempSource=MODEL_CONFIG maxTokensSource=MODEL_CONFIG ... effectiveTemperature=0.2 effectiveMaxTokens=256` | **PASS** |
| 2 | A | 0.7 | 512 | null* | REQUEST | REQUEST | 0.7 / 512 | `tempSource=REQUEST maxTokensSource=REQUEST ... effectiveTemperature=0.7 effectiveMaxTokens=512` | **PASS** |
| 3 | A | 999 | 999999 | null* | REQUEST | REQUEST | 1.0 / 4096 | `tempSource=REQUEST ... effectiveTemperature=1.0 effectiveMaxTokens=4096` | **PASS** |
| 4 | B | omitted | omitted | null | DEFAULT | DEFAULT | 0.1 / 1500 | `tempSource=DEFAULT maxTokensSource=DEFAULT ... effectiveTemperature=0.1 effectiveMaxTokens=1500` | **PASS** |

\* Khi request có giá trị, `resolveLlmGenerationOptions` không load configured tier cho field đó → `configuredTemp=null` / `configuredMaxTokens=null` trong log (đúng thiết kế 22C).

---

## 5. Top-K regression smoke (22B)

| # | Chatbot | request.topK | Expected source | Expected effective | Observed log | Pass |
|---|---------|--------------|-----------------|-------------------|--------------|------|
| 1 | A | omitted | MODEL_CONFIG | 5 | `[RAG] retrieval topK source=MODEL_CONFIG ... effective=5` | **PASS** |
| 2 | A | 10 | REQUEST | 10 | `[RAG] retrieval topK source=REQUEST requested=10 ... effective=10` | **PASS** |

Chatbot B (case 4): `source=DEFAULT ... effective=30` — **PASS** (không regress 22B).

---

## 6. Source cap 21J smoke

| Scenario | sourceCount | Expected | Pass |
|----------|-------------|----------|------|
| Fact retrieval (chatbot A) | 5 | ≤ 5 | **PASS** (`Response sources capped: ... → 5 returned`) |
| OOS Bitcoin | 2 | ≤ 2 | **PASS** (`Refusal-like answer → response sources 5 → 2`) |

OOS không bịa giá Bitcoin; answer refusal-like (HTTP 200).

---

## 7. Chat smoke

| Case | HTTP | sources | Crash |
|------|------|---------|-------|
| All LLM + topK cases | 200 | 5 (fact) | No |
| OOS | 200 | 2 | No |

---

## 8. Log evidence (excerpt)

```text
2026-05-15T15:59:30.804Z ... ChatService : [LLM] generation options tempSource=MODEL_CONFIG maxTokensSource=MODEL_CONFIG requestedTemp=null configuredTemp=0.2 requestedMaxTokens=null configuredMaxTokens=256 effectiveTemperature=0.2 effectiveMaxTokens=256
2026-05-15T15:59:40.730Z ... ChatService : [LLM] generation options tempSource=REQUEST maxTokensSource=REQUEST requestedTemp=0.7 configuredTemp=null requestedMaxTokens=512 configuredMaxTokens=null effectiveTemperature=0.7 effectiveMaxTokens=512
2026-05-15T15:59:49.510Z ... ChatService : [LLM] generation options tempSource=REQUEST maxTokensSource=REQUEST requestedTemp=999.0 configuredTemp=null requestedMaxTokens=999999 configuredMaxTokens=null effectiveTemperature=1.0 effectiveMaxTokens=4096
2026-05-15T16:00:47.481Z ... ChatService : [LLM] generation options tempSource=DEFAULT maxTokensSource=DEFAULT requestedTemp=null configuredTemp=null requestedMaxTokens=null configuredMaxTokens=null effectiveTemperature=0.1 effectiveMaxTokens=1500
2026-05-15T16:00:11.177Z ... ChatService : [RAG] retrieval topK source=REQUEST requested=10 configured=null effective=10
2026-05-15T16:01:02.608Z ... ChatService : [Chat] Refusal-like answer → response sources 5 → 2
```

Artifact: `docs/eval/results/_run_22c2_results.json`, script `_run_22c2_runtime_verify.ps1`.

---

## 9. Code changes

**Không có** — verify only.

---

## 10. Conclusion

| Criterion | Result |
|-----------|--------|
| modelConfig temperature/maxTokens fallback | **PASS** |
| request override | **PASS** |
| clamp 999 / 999999 → 1.0 / 4096 | **PASS** |
| No persisted config → DEFAULT 0.1 / 1500 | **PASS** |
| Top-K 22B regression | **PASS** |
| Source cap 21J | **PASS** |
| Chat không crash | **PASS** |

**Task 22C2 runtime verify: PASS**

---

## 11. Limitations / notes

- Chatbot B GET trả merged `modelConfig` (0.7 / 1024 / topK 5) — cần DB inspect khi test DEFAULT LLM.
- Playground compare path và widget FE gửi temperature/maxTokens chưa verify runtime.
- Script dừng ở bước MySQL do PowerShell coi mysql warning là error; evidence log đã đủ trước đó.
