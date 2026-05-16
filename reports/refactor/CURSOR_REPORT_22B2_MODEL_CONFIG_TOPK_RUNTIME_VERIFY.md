# CURSOR_REPORT_22B2 — modelConfig.topK runtime verify

## 1. Mức độ hiểu task

- **~99%** — verify-only runtime cho precedence 22B; không sửa code.
- **Chắc chắn:** acceptance cases, API paths, log format `source=REQUEST|MODEL_CONFIG|DEFAULT`.
- **Giả định:** Docker local = proxy production yếu đủ cho smoke.

## 2. Tóm tắt yêu cầu

Chạy Docker, tạo chatbot có/không có `modelConfig.topK` persist, gọi `/api/chat` với các payload topK, đọc log backend, xác nhận source cap 21J.

## 3. Phạm vi đã làm

- `docker compose up --build -d`
- Health: `/api/chatbots`, Qdrant collections
- Env check (masked)
- Backend compile + 21 unit tests
- Tạo chatbot A (topK=5), B (no persist)
- Upload golden document, chờ INDEXED
- 4 topK cases + OOS source cap smoke
- MySQL inspect `ui_config.modelConfig.topK`
- Docs result + report

## 4. Phạm vi không làm

Không sửa Java/FE/config/schema/retrieval/source cap; không migration; không claim nếu không chạy được (đã chạy được).

## 5. File đã đọc

`RAG_MODEL_CONFIG_TOPK_FALLBACK_22B_20260515.md`, `FIX_LOOP_22B_MODEL_CONFIG_TOPK_FALLBACK.md`, `CURSOR_REPORT_22B_MODEL_CONFIG_TOPK_FALLBACK.md`, `RAG_FE_TOPK_WIRING_22A_20260515.md`, `CURSOR_REPORT_22A_FE_TOPK_WIRING.md`, `ChatService.java`, `WidgetService.java`, `docker-compose.yml`, `ChatbotController.java`, `ChatController.java`.

## 6. Môi trường chạy

Windows, Docker Compose 4 services, backend port 8080, mysql healthy, qdrant up.

## 7. Env check

| Key | Present |
|-----|---------|
| GROQ_API_KEY | Yes (masked) |
| NOMIC_API_KEY | Yes (masked) |

## 8. Build / restart result

| Step | Result |
|------|--------|
| `docker compose up --build -d` | **PASS** — backend recreated with 22B code |
| `docker compose ps` | All running |
| `docker compose config -q` | **PASS** |

## 9. Compile / test result

| Command | Result |
|---------|--------|
| `mvnw -DskipTests compile` | **PASS** |
| `mvnw -Dtest=ChatServiceModelConfigTopKTest,RetrievalTopKTest,ChatServiceSourcePresentationTest test` | **PASS** |

## 10. Setup chatbot modelConfig.topK=5

- `POST /api/chatbots` name `22B2-Verify-A-223030`
- `PUT /api/chatbots/8c1ed488-c716-42b9-a83d-9365f9688c94` body `{ "modelConfig": { "topK": 5 } }`
- MySQL confirm: `topK_persisted = 5`

## 11. Runtime verification table

| request topK | configured | expected source | expected effective | observed (ChatService) | pass |
|--------------|------------|-----------------|-------------------|------------------------|------|
| null | 5 | MODEL_CONFIG | 5 | `source=MODEL_CONFIG ... effective=5` | **PASS** |
| 10 | null | REQUEST | 10 | `source=REQUEST requested=10 ... effective=10` | **PASS** |
| 999 | null | REQUEST | 30 | `source=REQUEST requested=999 ... effective=30` | **PASS** |
| null (bot B) | null | DEFAULT | 30 | `source=DEFAULT ... effective=30` | **PASS** |

## 12. Source cap smoke

| Case | sources | pass |
|------|---------|------|
| Fact golden | 5 | **PASS** (≤5) |
| OOS Bitcoin | 2 | **PASS** (≤2 refusal cap) |

## 13. Có sửa code không?

**Không.** Verify only.

## 14. No code diff

Không có thay đổi Java, Frontend, config, schema trong task này.  
Helper script verify: `docs/eval/results/_run_22b2_runtime_verify.ps1` (chỉ automation, không ảnh hưởng runtime).

## 15. Kết luận

| Item | Runtime pass? |
|------|---------------|
| modelConfig fallback | **Yes** |
| request override | **Yes** |
| clamp 999 → 30 | **Yes** |
| default 30 (no persist) | **Yes** |
| chat stable | **Yes** |

**Task 22B2: PASS**

## 16. Rủi ro còn lại

- GET chatbot detail luôn hiển thị `modelConfig.topK=5` merged — dễ nhầm với persist.
- Playground compare chưa runtime-verify fallback modelConfig.
- Log trùng ChatService + RagRetrievalService.

## 17. Đề xuất prompt tiếp theo

1. **22C (optional):** Runtime verify playground compare khi compare config thiếu topK.
2. **22D (optional):** FE widget chat gửi explicit topK nếu cần parity playground.
3. Hoặc chuyển sang task RAG quality/eval golden full nếu topK wiring đủ.

---

**Evidence files:**  
`docs/eval/results/RAG_MODEL_CONFIG_TOPK_RUNTIME_VERIFY_22B2_20260515.md`  
`docs/eval/results/_run_22b2_results.json`
