# CURSOR_REPORT_22C2 — LLM params (temperature / maxTokens) runtime verify

## 1. Mức độ hiểu task

- **~99%** — verify-only runtime cho precedence/clamp 22C; không sửa code.
- **Chắc chắn:** 7 acceptance cases, log format `[LLM] generation options tempSource=...`, API `/api/chat` + `X-Widget-Key`.
- **Giả định:** Docker local đủ proxy production yếu cho smoke LLM + RAG.

## 2. Tóm tắt yêu cầu

Chạy Docker, compile/test nhanh, tạo chatbot A (persist `modelConfig.temperature/maxTokens/topK`) và B (không persist), upload golden doc, gọi `/api/chat` với các payload, đọc log backend, smoke topK 22B và source cap 21J.

## 3. Phạm vi đã làm

- Đọc docs 22C, 22B2, 21J và source liên quan (read-only)
- `docker compose up --build -d`, `docker compose ps`, `docker compose config -q`
- Health: `/api/chatbots`, Qdrant collections
- Env check GROQ/NOMIC (masked suffix)
- Backend compile + 34 unit tests
- Runtime 4 LLM cases + 2 topK + OOS source cap
- MySQL inspect `ui_config` (binary UUID display; JSON snippet confirm A có modelConfig)
- Tạo result doc + report + artifact JSON/script

## 4. Phạm vi không làm

Không sửa Java/FE/config/schema/retrieval/prompt/source cap; không migration; không thêm dependency; không sửa `.gitignore`.

## 5. File đã đọc

| Path | Mục đích | Kết luận chính |
|------|----------|----------------|
| `docs/eval/results/RAG_FE_LLM_PARAMS_WIRING_22C_20260515.md` | Baseline 22C | Precedence + clamp; runtime NOT RUN trước đó |
| `docs/eval/results/FIX_LOOP_22C_FE_LLM_PARAMS_WIRING.md` | Scope fix | Verify Docker chưa chạy |
| `reports/refactor/CURSOR_REPORT_22C_FE_LLM_PARAMS_WIRING.md` | Implement report | Log format, files touched |
| `docs/eval/results/RAG_MODEL_CONFIG_TOPK_RUNTIME_VERIFY_22B2_20260515.md` | Pattern 22B2 verify | Script + log grep pattern |
| `reports/refactor/CURSOR_REPORT_22B2_MODEL_CONFIG_TOPK_RUNTIME_VERIFY.md` | Report template | VERIFY ONLY structure |
| `docs/eval/results/RAG_SOURCE_PRESENTATION_CLEANUP_21J_20260515.md` | Source cap baseline | cap 5 fact / 2 refusal |
| `reports/refactor/CURSOR_REPORT_21J_SOURCE_PRESENTATION_CLEANUP_FIX.md` | 21J scope | không đổi trong 22C2 |
| `ChatService.java` | Log/resolver | `resolveLlmGenerationOptions` + log line |
| `LlmGenerationOptions.java` | Clamp defaults | 0.1/1500, max 1.0/4096 |
| `WidgetService.java` | modelConfig parse | merge vs persist |
| `docker-compose.yml` | Stack | port 8080, env keys |

## 6. Môi trường chạy

Windows 10, Docker Compose 4 services, backend `localhost:8080`, mysql healthy, qdrant up. Backend container recreated với image build mới (22C code).

## 7. Env check

| Key | Present | Masked |
|-----|---------|--------|
| GROQ_API_KEY | Yes (len 56) | ***VTiv |
| NOMIC_API_KEY | Yes (len 46) | ***tz84 |

## 8. Build / restart result

| Step | Result |
|------|--------|
| `docker compose up --build -d` | **PASS** — backend + frontend recreated |
| `docker compose ps` | All running |
| `docker compose config -q` | **PASS** |
| Backend ready poll | ~20s after start |

## 9. Compile / test result

| Command | Result |
|---------|--------|
| `mvnw -DskipTests compile` | **PASS** |
| `mvnw -Dtest=LlmGenerationOptionsTest,ChatServiceLlmParamsTest,RetrievalTopKTest,ChatServiceModelConfigTopKTest,ChatServiceSourcePresentationTest test` | **PASS** (34 tests) |

## 10. Setup chatbot modelConfig

**Chatbot A**

- ID: `a780a357-3fd3-4b91-8606-d049260a5075`
- Widget key: `1915...5d62`
- PUT: `{ "modelConfig": { "temperature": 0.2, "maxTokens": 256, "topK": 5 } }`
- DB/UI: `temperature=0.2`, `maxTokens=256`, `topK=5`

**Chatbot B**

- ID: `2fef74a1-59f5-4bea-90d6-77a55a1c0b1b`
- Widget key: `b9da...0653`
- No PUT modelConfig
- GET merged: `0.7/1024/topK=5`; runtime LLM: `DEFAULT 0.1/1500`

**Document:** `88955d11-02e1-4d29-a1ab-8f6937241953` INDEXED on A; B indexed trong cùng script.

## 11. Runtime verification table

| req temp | req maxTokens | cfg temp | cfg max | expected temp src | expected max src | expected effective | observed log | pass |
|----------|---------------|----------|---------|-------------------|------------------|---------------------|--------------|------|
| null | null | 0.2 | 256 | MODEL_CONFIG | MODEL_CONFIG | 0.2 / 256 | `tempSource=MODEL_CONFIG ... effectiveTemperature=0.2 effectiveMaxTokens=256` | **PASS** |
| 0.7 | 512 | null | null | REQUEST | REQUEST | 0.7 / 512 | `tempSource=REQUEST ... effectiveTemperature=0.7 effectiveMaxTokens=512` | **PASS** |
| 999 | 999999 | null | null | REQUEST | REQUEST | 1.0 / 4096 | `effectiveTemperature=1.0 effectiveMaxTokens=4096` | **PASS** |
| null (B) | null | null | null | DEFAULT | DEFAULT | 0.1 / 1500 | `tempSource=DEFAULT ... effectiveTemperature=0.1 effectiveMaxTokens=1500` | **PASS** |

## 12. Top-K regression smoke

| request topK | expected | observed | pass |
|--------------|----------|----------|------|
| null (A) | MODEL_CONFIG / 5 | `source=MODEL_CONFIG ... effective=5` | **PASS** |
| 10 (A) | REQUEST / 10 | `source=REQUEST requested=10 ... effective=10` | **PASS** |
| null (B) | DEFAULT / 30 | `source=DEFAULT ... effective=30` | **PASS** |

## 13. Source cap smoke

| Case | sources | pass |
|------|---------|------|
| Fact golden | 5 | **PASS** (≤5) |
| OOS Bitcoin | 2 | **PASS** (≤2, refusal cap) |

## 14. Có sửa code không?

**Không** — verify only (production Java/FE không đổi).

## 15. No code diff

Không có thay đổi `Backend/src`, `Frontend/`, `docker-compose.yml`, schema.  
Helper automation (không ảnh hưởng runtime app):

- `docs/eval/results/_run_22c2_runtime_verify.ps1`
- `docs/eval/results/_run_22c2_results.json`

## 16. Kết luận

| Item | Runtime pass? |
|------|---------------|
| modelConfig temperature/maxTokens fallback | **Yes** |
| request override | **Yes** |
| clamp | **Yes** |
| default 0.1 / 1500 (no persist) | **Yes** |
| topK 22B regression | **Yes** |
| source cap 21J | **Yes** |
| chat stable | **Yes** |

**Task 22C2: PASS**

## 17. Rủi ro còn lại

- GET chatbot detail merge default `modelConfig` — dễ nhầm với persist (đặc biệt B).
- Playground compare / stream path chưa runtime-verify temperature/maxTokens.
- Widget FE chưa gửi root `temperature`/`maxTokens` qua `/api/chat`.
- Không đo token/latency thực tế Groq theo từng maxTokens.

## 18. Đề xuất prompt tiếp theo

1. **22D (optional):** Runtime verify playground compare khi config A/B thiếu temperature/maxTokens (mirror topK 22B compare note).
2. **Widget parity (optional):** FE widget gửi optional LLM params nếu product cần.
3. Hoặc chuyển **RAG golden eval full** / quality tuning — wiring 22C+22C2 đủ cho merge.

---

**Evidence files:**

- `docs/eval/results/RAG_LLM_PARAMS_RUNTIME_VERIFY_22C2_20260515.md`
- `docs/eval/results/_run_22c2_results.json`
- `docs/eval/results/_run_22c2_runtime_verify.ps1`
