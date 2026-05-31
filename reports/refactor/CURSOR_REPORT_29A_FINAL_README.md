# CURSOR REPORT 29A — Final README for Clone-and-Run Handoff

**Date:** 2026-05-30  
**Task:** 29A — Write Final README  
**Mode:** Documentation only (no production code changes)

---

## Final verdict: **PASS**

README rewritten as clone-and-run entry point. All validation commands PASS. Stale active guidance removed; removed features documented only as historical/removed.

---

## 1. Mức độ hiểu task

| Mục | Nội dung |
|-----|----------|
| Hiểu task | **98%** |
| Chắc chắn | Cấu trúc README 18 mục; không sửa production code; env/docker/API/widget từ source thật; baseline 28C (120 tests); feedback/mock/satisfaction removed |
| Giả định | Node.js LTS 20+ (không pin trong repo); `<repo-url>` placeholder cho git clone |
| Thiếu dữ liệu | Không |

---

## 2. Tóm tắt yêu cầu

Viết lại `README.md` làm entry point chính cho developer clone repo, cấu hình env, chạy Docker/backend/frontend, ingest tài liệu, test chat, verify widget E2E. Tạo report `reports/refactor/CURSOR_REPORT_29A_FINAL_README.md`. Không sửa production code.

---

## 3. Hiện trạng trước khi sửa

- `README.md` tồn tại (~267 dòng) với nội dung từ task 26C.
- Baseline test ghi **71 tests** — đã lỗi thời (28C: **120 tests**).
- Thiếu mục: prerequisites chi tiết, PowerShell env scope, frontend local dev, widget embed đầy đủ, runtime smoke tests, troubleshooting, public chat API, removed feedback verification.
- Cấu trúc không khớp template 18 mục task 29A.

---

## 4. Nguyên nhân gốc xác nhận từ source

- README cũ chưa cập nhật sau cleanup 28A/28B (feedback/satisfaction/mock removed) và baseline test 28C.
- Task 29A yêu cầu cấu trúc handoff đầy đủ hơn, inspect thực tế `docker-compose.yml`, `pom.xml`, `package.json`, controllers, widget config.

---

## 5. Chiến lược sửa đã chọn

1. Đọc toàn bộ file bắt buộc (compose, Spring profiles, frontend scripts, agent docs, 28C E2E report).
2. Rewrite `README.md` theo 18 mục + Quick start, dùng giá trị thật từ source.
3. Không đoán env/API — verify `ChatRequest`, `WidgetAuthFilter`, `PublicChatController`, `ChatbotEmbedPage`.
4. Chạy validation commands (compose config, backend test, frontend build/lint/widget).
5. Stale scan — chỉ giữ feedback/mock/gRPC/legacy chunk khi mô tả removed hoặc not-used.
6. Tạo report task 29A.

---

## 6. Danh sách file đã đọc

| Path | Đọc để | Kết luận chính |
|------|--------|----------------|
| `docker-compose.yml` | Services, ports, env inject | mysql, qdrant, backend, frontend; keys via `${GROQ_API_KEY}` etc.; profile docker |
| `Backend/pom.xml` | Java/Spring/deps | Java 21, Boot 3.4.4, POI, PDFBox, Tabula, LangChain4j |
| `application.yml` / `dev` / `docker` | DB, Qdrant, providers | dev=localhost; docker=mysql/qdrant hostnames; Nomic/Groq/Cohere config |
| `Frontend/package.json` | Scripts, stack | React 19, Vite 7, build:widget exists |
| `Frontend/vite.config.js` | Dev proxy, widget serve | `/api` → :8080; serves `/dist-widget/` |
| `Frontend/vite.widget.config.js` | Widget output | IIFE → `dist-widget/chatbot-widget.iife.js` |
| `Frontend/scripts/sync-widget-to-public.mjs` | Widget sync | Copies to `public/dist-widget/` |
| `Frontend/.env.example` | Frontend env | `VITE_API_URL` |
| `Backend/.env.example` | Backend env | GROQ, NOMIC, COHERE optional |
| `ChatRequest.java` | API body fields | sessionId, message (+ optional topK, temperature) |
| `PublicChatController.java` | Public chat | POST `/api/public/chat`, x-api-key via filter |
| `WidgetAuthFilter.java` | Headers | x-api-key for public; X-Widget-Key for /api/chat |
| `ChatbotEmbedPage.jsx` | Embed snippet | `RagChatbotConfig` + script src pattern |
| `agent.md`, `agent/04-runbook.md`, `agent/05-api.md`, `agent/06-operations.md` | Runbook/API/ops | Commands, delete cascade, auth truth |
| `docs/eval/results/FULL_PROJECT_WIDGET_E2E_VERIFY_28C_20260530.md` | Baseline | 120 tests, widget E2E PASS, feedback 404 |
| `docs/architecture/*.md`, `docs/api/*.md` | Link validation | All referenced files exist |
| `README.md` (cũ) | Stale content | 71 tests baseline, thiếu sections |

---

## 7. Danh sách file đã sửa

| Path | Sửa để | Ảnh hưởng |
|------|--------|-----------|
| `README.md` | Final clone-and-run handoff doc | docs |
| `reports/refactor/CURSOR_REPORT_29A_FINAL_README.md` | Task report | docs |

**Production code:** không sửa.

---

## 8. Diff thay đổi của từng file

### `README.md`

**Hiện trạng cũ:** ~12 sections, baseline 71 tests, thiếu widget/troubleshooting/smoke/public chat chi tiết.

**Đã sửa:** Rewrite ~620 dòng, 18 sections + Quick start theo task 29A.

**Vì sao:** Handoff cho developer/reviewer/AI sessions; phản ánh baseline 28C và removed features.

```diff
- **Trạng thái:** Backend ổn định sau refactor 25B–25K. Baseline test: **71 tests, 0 failures**.
+ **Baseline đã verify (task 28C, 2026-05-30):** Backend 120 tests PASS · Frontend build/lint PASS · Widget build PASS · Docker compose config PASS · Widget E2E PASS.

+ ## 5. Prerequisites
+ ## 6. Environment variables (PowerShell scope notes)
+ ## 9. Run frontend locally
+ ## 12. Widget usage (build, embed, local test, verified flow)
+ ## 14. Runtime smoke tests (exact + OOS + feedback 404)
+ ## 16. Troubleshooting
+ **Đã gỡ (không còn active):** feedback endpoint, satisfaction/rating metrics, newFeedback, mock mode, USE_MOCK_API
```

### `reports/refactor/CURSOR_REPORT_29A_FINAL_README.md`

**File mới** — report task 29A theo yêu cầu.

---

## 9. Ảnh hưởng sau sửa

| Behavior | Thay đổi |
|----------|----------|
| README content | Thay toàn bộ — developer onboarding path rõ ràng hơn |
| Runtime/API/DB | Không đổi |
| Clone-and-run flow | Documented: Docker → backend → frontend dev → widget build → smoke |
| Removed features | Chỉ mention as removed/historical — không hướng dẫn active |
| Memory/CPU/latency/cost | Không đổi |

---

## 10. Edge cases đã xem xét

- Thiếu env keys → backend fail khi gọi Nomic/Groq (documented in troubleshooting).
- PowerShell Process vs User vs Machine env (documented).
- Widget without `build:widget` → missing IIFE (documented).
- Old DB `chat_feedbacks` / `notify_new_feedback` — historical cleanup note only.
- Docker vs local frontend — both paths documented.
- gRPC port 6334 exposed but not app write path — explicit in README.
- Legacy chunk types — read-only note, not current ingest.

---

## 11. Kết quả kiểm tra

| Command | Kết quả | Ghi chú |
|---------|---------|---------|
| `docker compose config -q` | **PASS** | exit 0 |
| `cd Backend && .\mvnw.cmd clean test` | **PASS** | 120 tests, 0 failures, BUILD SUCCESS |
| `cd Frontend && npm run build` | **PASS** | Vite build OK |
| `cd Frontend && npm run lint` | **PASS** | ESLint clean |
| `cd Frontend && npm run build:widget` | **PASS** | IIFE 3.27 kB + CSS; synced to public/dist-widget |
| Stale scan `rg feedback\|USE_MOCK_API\|avgSatisfaction` on README | **PASS** | Only removed/historical contexts |
| Stale scan `POST /api/chat/feedback` as active guidance | **PASS** | Only in "Removed feedback endpoint" smoke (expects 404) |

---

## 12. Rủi ro còn lại

- `agent.md` vẫn ghi baseline 71 tests — ngoài scope 29A; có thể sync trong task docs riêng.
- Node.js version không pin trong repo — README khuyến nghị LTS 20+.
- Physical DB artifacts (`chat_feedbacks`, `notify_new_feedback`) có thể còn trên DB cũ — documented as optional cleanup.
- Admin API vẫn dev-oriented — documented in Known limitations.

---

## 13. Đề xuất tiếp theo

1. **Task 29B (optional):** Sync `agent.md` baseline 120 tests + removed features note.
2. **Task 30:** Physical DB migration / drop legacy feedback tables after backup.
3. **Production hardening:** Auth for admin routes before public deploy.

---

## Report summary (task format)

### Files updated

- `README.md`
- `reports/refactor/CURSOR_REPORT_29A_FINAL_README.md`

### Stack documented

- Backend: Java 21, Spring Boot 3.4.4, Maven, MySQL 8, Qdrant REST, POI/PDFBox/Tabula, Nomic, Groq, optional Cohere
- Frontend: React 19, Vite 7, npm scripts including `build:widget`
- Infra: Docker Compose 4 services

### Env variables documented

- `GROQ_API_KEY`, `NOMIC_API_KEY`, `COHERE_API_KEY`, `COHERE_RERANK_ENABLED`
- MySQL: `MYSQL_DATABASE`, `MYSQL_ROOT_PASSWORD`
- Frontend: `VITE_API_URL`
- Qdrant local: `QDRANT_HOST`, `QDRANT_HTTP_PORT`

### Docker commands documented

- `docker compose config -q`
- `docker compose up -d mysql qdrant`
- `docker compose up --build -d backend`
- `docker compose up --build -d frontend`
- logs/ps troubleshooting

### Frontend/widget commands documented

- `npm install`, `npm run dev`, `build`, `lint`, `build:widget`
- Output: `dist-widget/chatbot-widget.iife.js`, sync to `public/dist-widget/`

### API/widget quickstart documented

- `POST /api/chat` with `X-Widget-Key`, body `{ sessionId, message }`
- `POST /api/public/chat` with `x-api-key`
- Widget embed via `window.RagChatbotConfig`
- Local routes: `/widget?widgetKey=...`, `public-widget-test.html`

### Stale guidance removed

- No active feedback/satisfaction/mock/USE_MOCK_API guidance
- 71-test baseline replaced with 120
- gRPC/QdrantEmbeddingStore only as not-used
- Legacy chunk types only as read-compatible

### Validation results

All PASS (see section 11).

### Remaining docs risks

- `agent.md` baseline drift (71 vs 120)
- No pinned Node version file

### Next recommended task

Sync agent docs baseline (29B) or production auth hardening.
