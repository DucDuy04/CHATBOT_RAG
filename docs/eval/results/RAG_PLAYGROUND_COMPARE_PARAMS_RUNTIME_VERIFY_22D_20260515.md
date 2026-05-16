# RAG — Playground Compare params runtime verify 22D (official result)

**File:** `docs/eval/results/RAG_PLAYGROUND_COMPARE_PARAMS_RUNTIME_VERIFY_22D_20260515.md`  
**Report:** `reports/refactor/CURSOR_REPORT_22D_PLAYGROUND_COMPARE_PARAMS_RUNTIME_VERIFY.md`  
**Ngày:** 2026-05-15  
**Loại task:** VERIFY ONLY — không sửa code

---

## 1. Environment

| Item | Giá trị |
|------|---------|
| OS | Windows 10 |
| Stack | Docker Compose (backend đã rebuild từ 22C2, không rebuild lại trong 22D) |
| Backend | `http://localhost:8080` |
| Qdrant | 1 collection |
| Env keys | `GROQ_API_KEY` ***VTiv, `NOMIC_API_KEY` ***tz84 |

---

## 2. Compile / test

| Command | Kết quả |
|---------|---------|
| `mvnw -DskipTests compile` | **PASS** |
| Regression 34 tests (LLM + topK + source cap) | **PASS** |
| `docker compose config -q` | **PASS** |
| PlaygroundService unit test | **Không có** trong repo |

---

## 3. Playground compare endpoint / payload

| Field | Giá trị |
|-------|---------|
| Endpoint | `POST /api/playground/compare` |
| Controller | `PlaygroundController.compare` |
| Request DTO | `PlaygroundCompareRequest` |
| Body | `{ "chatbotId": "<uuid>", "message": "<text>", "configA": { ... }, "configB": { ... } }` |
| Config keys | `topK`, `temperature`, `maxTokens` (Number; parse trong `RagRetrievalService` / `LlmGenerationOptions`) |
| Response | `{ "configA": { answer, sources, latency, config }, "configB": { ... } }` |
| FE client | `Frontend/src/api/playgroundApi.js` → `compare({ chatbotId, message, configA, configB })` |

**Lưu ý log:** Compare path **không** gọi `ChatService` → **không** có log `[LLM] generation options`. Top-K chỉ log qua `RagRetrievalService`: `[RAG] retrieval topK requested={}, effective={}`.

---

## 4. Chatbot / document setup

| Field | Giá trị |
|-------|---------|
| Name | `22D Playground Compare Verify 231108` |
| ID | `69fdd389-aebb-4363-b2b5-4c630088e2f4` |
| Widget key | `6008...c921` (masked) |
| modelConfig | `temperature=0.2`, `maxTokens=256`, `topK=5` |
| Document | `e40f4ffd-538d-4488-be3f-16a036c2ef69` — **INDEXED** |

---

## 5. Compare explicit A/B

Message: `Mã xác nhận golden là gì?`

| Side | Config | Expected topK | Observed log | Pass |
|------|--------|---------------|--------------|------|
| A | topK=5, temp=0.2, maxTokens=256 | 5 | `requested=5, effective=5` | **PASS** |
| B | topK=10, temp=0.7, maxTokens=512 | 10 | `requested=10, effective=10` | **PASS** |

| Side | Expected LLM | Runtime log | Pass |
|------|--------------|-------------|------|
| A | temp=0.2, maxTokens=256 | Không có log compare | **PASS (code)** — `resolveCompareLlmOptions` → `ChatService.resolveLlmGeneration` |
| B | temp=0.7, maxTokens=512 | Không có log compare | **PASS (code)** |

HTTP **200**. `configA.sources`=9, `configB.sources`=10. Latency A≈7.8s, B≈8.1s. Không crash.

---

## 6. Compare clamp

Config A: `{ topK: 999, temperature: 999, maxTokens: 999999 }`  
Config B: `{ topK: 5, temperature: 0.2, maxTokens: 256 }` (hợp lệ)

| Param | Expected effective (A) | topK log (A) | Pass |
|-------|------------------------|--------------|------|
| topK | 30 | `requested=999, effective=30` | **PASS** |
| temperature | 1.0 | no log | **PASS (code)** `normalizeTemperature` |
| maxTokens | 4096 | no log | **PASS (code)** `normalizeMaxTokens` |

B: `requested=5, effective=5` — **PASS**.

---

## 7. Compare missing partial params

Config A: `{ "topK": 5 }`  
Config B: `{ "temperature": 0.7 }`

| Side | Field | Observed topK log | Expected LLM (source) | Pass |
|------|-------|-------------------|----------------------|------|
| A | topK only | `requested=5, effective=5` | temp/max → **modelConfig** 0.2 / 256 | topK **PASS**; LLM **PASS (code)** |
| B | temp only | `requested=null, effective=30` | temp=0.7; maxTokens → **modelConfig** 256; topK → **DEFAULT 30** (không dùng modelConfig.topK=5) | topK **PASS (observed)**; LLM **PASS (code)** |

**Limitation:** Compare `runCompareOnce` truyền `parseTopKOverride(config)` trực tiếp — **không** có tier modelConfig cho topK như `/api/chat`. Thiếu `topK` trong config → effective **30**, không phải chatbot `modelConfig.topK=5`.

---

## 8. `/api/chat` regression smoke

Cùng chatbot, header `X-Widget-Key`:

| Case | Observed `[LLM] generation options` | Pass |
|------|--------------------------------------|------|
| No fields | `MODEL_CONFIG` → 0.2 / 256 | **PASS** |
| temp=0.7, maxTokens=512 | `REQUEST` → 0.7 / 512 | **PASS** |

Compare verify **không** làm regress path chat.

---

## 9. Log evidence (timeline)

```text
16:11:19 requested=5, effective=5      # case1 configA
16:11:26 requested=10, effective=10  # case1 configB
16:11:39 requested=999, effective=30 # case2 clamp A
16:11:50 requested=5, effective=5      # case2 configB
16:12:32 requested=5, effective=5    # case3 configA
16:13:02 requested=null, effective=30 # case3 configB
16:13:49 [LLM] ... MODEL_CONFIG 0.2/256  # /api/chat regression
16:14:13 [LLM] ... REQUEST 0.7/512
```

Artifact: `_run_22d_results.json`, `_run_22d_runtime_verify.ps1`.

---

## 10. Conclusion

| Criterion | Result |
|-----------|--------|
| Compare explicit topK A/B | **PASS** |
| Compare explicit LLM A/B | **PASS (code path)** — không có runtime log trên compare |
| Compare clamp topK | **PASS** |
| Compare clamp LLM | **PASS (code)** |
| Missing params behavior documented | **Yes** — topK → default 30; temp/max → modelConfig |
| Compare API stable | **PASS** (HTTP 200, có answers) |
| `/api/chat` regression | **PASS** |

**Task 22D: PASS** với limitation: LLM params trên compare chưa có log runtime; topK thiếu trong compare config không fallback modelConfig.

**Có cần fix PlaygroundService?** Tùy product: (1) thêm log LLM cho compare; (2) optional align compare topK với modelConfig như ChatService.

---

## 11. Đề xuất tiếp theo

1. Prompt fix nhỏ: log `[LLM] generation options` trong `PlaygroundService.runCompareOnce` (mirror ChatService).
2. Optional: compare topK fallback `modelConfig.topK` khi config thiếu field.
3. Widget FE parity — chỉ khi product yêu cầu.
