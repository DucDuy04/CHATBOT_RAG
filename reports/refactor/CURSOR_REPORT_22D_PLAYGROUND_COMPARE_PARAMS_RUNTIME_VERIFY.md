# CURSOR_REPORT_22D — Playground Compare params runtime verify

## 1. Mức độ hiểu task

- **~98%** — verify-only compare A/B sau 22A/22B/22C; không sửa code.
- **Chắc chắn:** endpoint `POST /api/playground/compare`, config map keys, compare dùng `PlaygroundService` riêng khỏi `ChatService`.
- **Giả định:** LLM đúng nếu cùng resolver static với chat — đã đọc `resolveCompareLlmOptions`.

## 2. Tóm tắt yêu cầu

Runtime verify compare gửi topK/temperature/maxTokens cho config A/B, clamp, missing fields, regression `/api/chat`.

## 3. Phạm vi đã làm

- Đọc docs 22A–22C2 + source Playground/Compare
- Docker stack up (đã chạy), health, env masked
- Compile + 34 unit tests
- Tạo chatbot + golden doc INDEXED
- 3 compare cases + 2 chat smoke
- Log grep + artifact JSON/script
- 2 file result/report

## 4. Phạm vi không làm

Không sửa Java/FE/config/schema; không migration; không widget parity.

## 5. File đã đọc

| Path | Kết luận |
|------|----------|
| `RAG_LLM_PARAMS_RUNTIME_VERIFY_22C2_20260515.md` | Chat LLM verify PASS |
| `CURSOR_REPORT_22C2_*` | Compare chưa verify |
| `RAG_FE_LLM_PARAMS_WIRING_22C_*` | Wiring design |
| `RAG_MODEL_CONFIG_TOPK_RUNTIME_VERIFY_22B2_*` | Script pattern |
| `RAG_FE_TOPK_WIRING_22A_*` | topK path |
| `PlaygroundController.java` | `/compare` endpoint |
| `PlaygroundService.java` | `runCompareOnce`, `resolveCompareLlmOptions` |
| `PlaygroundCompareRequest.java` | DTO shape |
| `playgroundApi.js` | FE payload |
| `CompareConfigPanel.jsx`, `ComparePane.jsx` | UI controls |
| `LlmGenerationOptions.java` | parse + clamp |
| `RagRetrievalService.java` | topK log line |

## 6. Môi trường chạy

Windows, Docker 4 services, backend port 8080, mysql healthy.

## 7. Env check

GROQ ***VTiv, NOMIC ***tz84 — present.

## 8. Build / restart result

Stack đã up từ 22C2; `docker compose ps` all running; `config -q` PASS. Không rebuild bắt buộc trong 22D.

## 9. Compile / test result

| Command | Result |
|---------|--------|
| compile | **PASS** |
| 34 regression tests | **PASS** |

## 10. Playground compare endpoint/payload

`POST /api/playground/compare`  
Body: `{ chatbotId, message, configA: Map, configB: Map }`  
Keys: `topK`, `temperature`, `maxTokens`.

## 11. Chatbot/document setup

- ID: `69fdd389-aebb-4363-b2b5-4c630088e2f4`
- Key: `6008...c921`
- modelConfig: 0.2 / 256 / topK 5
- Doc: `e40f4ffd-538d-4488-be3f-16a036c2ef69` INDEXED

## 12. Runtime verification table

| Case | Expected | Observed | Pass |
|------|----------|----------|------|
| A explicit topK=5 | effective=5 | `requested=5, effective=5` | **PASS** |
| B explicit topK=10 | effective=10 | `requested=10, effective=10` | **PASS** |
| A explicit LLM 0.2/256 | passed to Groq | no compare log; code `resolveCompareLlmOptions` | **PASS (code)** |
| B explicit LLM 0.7/512 | passed to Groq | no compare log | **PASS (code)** |
| Clamp A topK 999 | 30 | `effective=30` | **PASS** |
| Clamp A temp/max | 1.0 / 4096 | normalize in `LlmGenerationOptions` | **PASS (code)** |
| Missing A topK=5 only | topK=5; LLM modelConfig | log 5/5; LLM modelConfig in code | **PASS** |
| Missing B temp=0.7 only | topK default 30; temp 0.7 | `requested=null, effective=30` | **PASS** (limitation: no modelConfig topK) |

## 13. Backend log evidence

See `RAG_PLAYGROUND_COMPARE_PARAMS_RUNTIME_VERIFY_22D_20260515.md` §9.

## 14. `/api/chat` regression smoke

MODEL_CONFIG 0.2/256 và REQUEST 0.7/512 — **PASS**, không regress.

## 15. Có sửa code không?

**Không.**

## 16. No code diff

Chỉ thêm docs + helper script `_run_22d_runtime_verify.ps1`, `_run_22d_results.json`.

## 17. Kết luận

| Item | Result |
|------|--------|
| Compare explicit params (topK) | **PASS** |
| Compare explicit params (LLM) | **PASS (code)** — thiếu log runtime |
| Clamp | **PASS** (topK logged; LLM code) |
| Missing fallback | **Rõ** — topK→30; temp/max→modelConfig |
| `/api/chat` regress | **No** |
| Compare crash | **No** |

**Task 22D: PASS** với limitation logging/fallback topK trên compare.

## 18. Rủi ro còn lại

- Compare không log LLM effective values — khó audit production.
- Compare topK không fallback `modelConfig` khi config thiếu field (khác `/api/chat`).
- `configA`/`configB` `sources` count có thể >5 (compare không qua source cap 21J của ChatService).

## 19. Đề xuất prompt tiếp theo

1. **22D-fix (optional):** Thêm `log.info` LLM options trong `PlaygroundService.runCompareOnce` + unit test `resolveCompareLlmOptions`.
2. **22D-topK (optional):** Align compare topK với `ChatService.resolveRetrievalTopK` / modelConfig fallback.
3. **RAG golden eval full** hoặc widget parity nếu product cần.

---

**Evidence:** `docs/eval/results/RAG_PLAYGROUND_COMPARE_PARAMS_RUNTIME_VERIFY_22D_20260515.md`
