# Cursor Report 08A - FULLSTACK_REAL_API_INTEGRATION_QA_AND_MINOR_FIXES

## 1. Mức độ hiểu task

- **Task là gì?**  
  QA tích hợp FE ↔ BE với **real API** (tắt mock), kiểm tra các vùng checklist chính, chỉ sửa bug nhỏ contract/CORS/baseURL/mock toggle; chạy validate Maven + npm.

- **Hiểu task:** 90%

- **Phần chắc chắn:**  
  - `apiMode.js`: `USE_MOCK_API` = `true` nếu `VITE_USE_MOCK_API === "true"` **hoặc** (không set `VITE_USE_MOCK_API` **và** `import.meta.env.DEV`) → **dev mặc định MOCK** trừ khi set explicit `VITE_USE_MOCK_API=false`.  
  - `axiosInstance` `baseURL` = `import.meta.env.VITE_API_URL`; Vite `server.proxy` `/api` → `localhost:8080` khi dev relative calls.  
  - Path FE `src/api/*.js` khớp mapping controller đã grep.

- **Phần còn giả định:**  
  - Không chạy trình duyệt tự động cho toàn bộ checklist UI; thay bằng **HTTP smoke** + **build/lint** với env real API.

- **Phạm vi không làm:**  
  - Auth/JWT, feature mới, widget build (không sửa widget), đổi schema, RAG core, feedback GET endpoint.

---

## 2. Files/rules/reports đã đọc

| File | Mục đích đọc | Kết luận chính |
|------|----------------|----------------|
| `.cursor/rules/*` | Luật workspace | minimal diff, report trung thực |
| `reports/CURSOR_REPORT_11_INTEGRATION_QA_CHECKLIST_AUDIT.md` | Checklist baseline | AppLayout đã fix trước; scope QA rộng |
| `reports/CURSOR_REPORT_07B_...md` | Settings/runtime | BE smoke OK trên 8080 |
| `Frontend/src/api/apiMode.js` | Mock toggle | **Dev default mock** nếu không set env |
| `Frontend/src/api/axiosInstance.js` | baseURL + errors | Bearer optional; `message`/`error` |
| `Frontend/src/api/dashboardApi.js` | Paths | `/api/dashboard/*` |
| `Frontend/src/api/chatbotsApi.js` | Paths | `/api/chatbots` CRUD + embed-config |
| `Frontend/src/api/documentsApi.js` | Upload/list | FormData `chatbotId` + `files` |
| `Frontend/src/api/playgroundApi.js` | SSE + sessions | `fetch(\`${API_BASE}/api/playground/chat\`)` — `API_BASE` rỗng → URL tương đối `/api/...` qua proxy |
| `Frontend/src/api/analyticsApi.js` | Params | `from`/`to` bắt buộc phía BE khi gọi |
| `Frontend/src/api/settingsApi.js` | Settings | Khớp 07A |
| `Frontend/src/api/publicChatApi.js` | Public | `x-api-key`, `API_BASE` |
| `Backend/.../SecurityConfig.java` | CORS | `localhost:5173`, `3000` |
| `Backend/.../AnalyticsController.java` | Validation | Thiếu `from`/`to` → 400 (đúng thiết kế) |
| `Backend/.../PlaygroundController.java` | Sessions | Thiếu `chatbotId` → 400 (FE luôn truyền) |
| `Frontend/vite.config.js` | Proxy | `/api` → 8080 |

---

## 3. Runtime environment

| Item | Status | Notes |
|------|--------|-------|
| Docker | **RUNNING** | `ragchatbot-mysql` healthy, `ragchatbot-qdrant` up |
| MySQL | **OK** | `localhost:3306` / `ragchatbot` |
| Qdrant | **OK** | Local; client/server version WARN (đã biết) |
| Backend | **STARTED** | `spring-boot:run` profile `dev`, env Groq/Nomic **placeholder** (không ghi secret) |
| Frontend dev server | **NOT RUN** (không bắt buộc cho HTTP smoke) | Build/lint chạy với env CLI |
| Base API URL (HTTP tests) | `http://localhost:8080` | Trực tiếp tới Tomcat |
| Mock mode (FE build/lint) | **Tắt** | `VITE_USE_MOCK_API=false` trong shell khi `npm run lint` / `npm run build` |
| Env keys | **Placeholder** | Chỉ để boot BE + tests |

---

## 4. Real API mode setup

- **FE env khi build/lint:**  
  - `VITE_USE_MOCK_API=false`  
  - `VITE_API_URL=http://localhost:8080`  
  (set trong **PowerShell session** cho `npm run lint` / `npm run build` — không commit secret.)

- **`VITE_API_URL`:** `http://localhost:8080` cho build test.

- **Mock tắt:** explicit `VITE_USE_MOCK_API=false` — vì `apiMode.js` mặc định **mock trong dev** nếu không set.

- **axios `baseURL`:** tại build time = `http://localhost:8080` (đã embed trong bundle build test).

- **CORS:** Không test cross-origin browser trong prompt này; `SecurityConfig` đã allow `http://localhost:5173`. Gọi trực tiếp 8080 bằng PowerShell **không** qua CORS.

- **Bổ sung repo:** Thêm `Frontend/.env.example` hướng dẫn copy → `.env.local` và **bắt buộc** `VITE_USE_MOCK_API=false` để tránh nhầm “đã nối BE” nhưng vẫn mock trong `npm run dev`.

---

## 5. Integration QA results

| Area | Flow | Expected | Actual | Status | Notes |
|------|------|----------|--------|--------|-------|
| C1 Auth/Layout | Login, sidebar, `/widget` | Không crash, đúng layout | **NOT RUN** | **NOT RUN** | Không có browser automation trong session |
| C2 Dashboard | GET summary, message-volume, top, activity | 200 + JSON | Đã gọi `GET /api/dashboard/summary` → **200**; các endpoint khác đã PASS trong lượt smoke trước (07B-style) | **PASS** | HTTP subset |
| C3 Chatbots | GET list | 200, `items` | **200**, pagination keys đúng | **PASS** | |
| C4 Chatbot Config | GET/PUT detail | Contract | **NOT RUN** UI | **NOT RUN** | Không mở UI; path `/api/chatbots/{id}` đã khớp code |
| C5 Embed | GET/PUT embed-config | Contract | **NOT RUN** UI | **NOT RUN** | |
| C6 Documents | GET list theo `chatbotId` | 200 | **200** | **PASS** | Upload/ingest **NOT RUN** (phụ thuộc embedding/provider thật) |
| C7 Playground | GET sessions với `chatbotId` | 200 | **200** | **PASS** | SSE `/api/playground/chat` **NOT RUN** (stream) |
| C8 Analytics Usage | GET summary với `from`/`to` | 200 | **200** | **PASS** | Thiếu `from`/`to` → **400** (đúng BE + FE luôn gửi range) |
| C9 Analytics Sessions | GET sessions + date + page/size | 200 | **200** | **PASS** | |
| C9b Session messages | GET `/sessions/{id}/messages` | 200 | **200** | **PASS** | `id` từ trang sessions |
| C10 Feedback UI | Submit từ UI | POST feedback | **NOT RUN** | **NOT RUN** | Tab Feedback vẫn mock (`AnalyticsFeedbackTab`); BE POST đã có ở 06C |
| C11 Settings | GET profile/keys | 200 | **200** (đã verify trong session BE trước khi stop) | **PASS** | Cùng pattern 07B |
| C12 Public widget/chat | curl/widget | Hoạt động | **NOT RUN** | **NOT RUN** | Ngoài thời gian smoke HTTP |

---

## 6. Bugs found and fixed

| Bug | Area | File | Fix | Retest status |
|-----|------|------|-----|----------------|
| Dev dễ nhầm vẫn đang **mock** khi `npm run dev` | Mock toggle / onboarding | `Frontend/.env.example` | Thêm file mẫu: `VITE_USE_MOCK_API=false` + gợi ý `VITE_API_URL` / proxy | `npm run lint` + `npm run build` với env real: **PASS** |

**Không** phát hiện mismatch path FE/BE cần sửa code runtime trong phạm vi đã kiểm.

---

## 7. Files changed

| File | Change | Reason | Risk |
|------|--------|--------|------|
| `Frontend/.env.example` | New | Document cách tắt mock + base URL | Thấp — không ảnh hưởng build nếu không copy |

---

## 8. Validation results

| Command | Result | Notes |
|---------|--------|-------|
| `cd Backend && ./mvnw -DskipTests compile` | **PASS** | |
| `cd Backend && ./mvnw test` (với `GROQ_API_KEY`/`NOMIC_API_KEY` placeholder trong shell) | **PASS** | 14 tests |
| `cd Frontend && npm run lint` (`VITE_USE_MOCK_API=false`, `VITE_API_URL=http://localhost:8080`) | **PASS** | |
| `cd Frontend && npm run build` (cùng env) | **PASS** | |
| `npm run build:widget` | **NOT RUN** | Không sửa widget |

---

## 9. Known limitations / gaps

- **UI checklist (C1, C4, C5, C7 stream, C10, C12):** chưa chạy browser automation trong task này.  
- **Document upload end-to-end:** cần key/provider thật + thời gian ingest.  
- **Playground SSE:** chưa verify byte-by-byte trong task này (logic đã review 04A).  
- **Feedback tab:** vẫn mock list; không có GET feedback contract.  
- **`apiMode` dev default = mock:** hành vi đúng code; dev **phải** set `VITE_USE_MOCK_API=false` (đã ghi `.env.example`).

---

## 10. Final decision

**Fullstack real API integration QA PASS** — đối với **tầng API contract + build/lint** và smoke HTTP có tham số hợp lệ; **manual UI / SSE / upload / public widget** còn bước xác nhận thủ công hoặc prompt follow-up nếu cần 100% checklist UI.

---

## 11. Recommended next prompt

- **08B — Browser/E2E manual script:** checklist từng màn hình với `npm run dev` + `.env.local` (`VITE_USE_MOCK_API=false`), ghi screenshot/log console.  
- Hoặc **Playground SSE + Document upload** smoke chuyên sâu với provider thật.
