# Cursor Report 10A - LIVE_API_SMOKE_AND_BROWSER_REGRESSION_RERUN

## 1. Mức độ hiểu task

- **Task:** Chạy lại live verification sau 09A: Docker MySQL+Qdrant, start Backend (`dev`), Frontend real API (`.env.local` + `npm run dev`), smoke API (curl/PowerShell), browser checklist nếu có browser; validation build/test; chỉ sửa bug cực nhỏ; không feature mới.
- **Hiểu task:** 99%
- **Giới hạn:** Không mở browser tự động trong agent — mục browser ghi **NOT RUN**, không claim PASS UI.

---

## 2. Files/rules/reports đã đọc

| File | Mục đích | Kết luận |
|------|-----------|----------|
| `.cursor/rules/00-core-working-rule.mdc` | Luật nền | Minimal diff, không mở rộng scope |
| `.cursor/rules/10-backend-rag-rule.mdc` | Backend | Smoke qua REST/SSE |
| `.cursor/rules/20-frontend-widget-rule.mdc` | FE | `.env.local`, Vite |
| `.cursor/rules/30-deploy-env-ops-rule.mdc` | Ops | Docker + env |
| `.cursor/rules/40-db-vector-rule.mdc` | Vector | Không đổi schema |
| `.cursor/rules/90-report-verification-rule.mdc` | Report | Có mirror `docs/` |
| `reports/CURSOR_REPORT_09A_FINAL_QA_REGRESSION_AND_DELIVERY_CHECKLIST.md` | Tiền đề | API smoke trước đó NOT RUN |
| `reports/CURSOR_REPORT_08D1_FIX_ACTUAL_DOCUMENTS_LIST_500.md` | Documents | List + invalid UUID 400 |
| `reports/CURSOR_REPORT_08E_PUBLIC_WIDGET_AND_PUBLIC_CHAT_VERIFICATION.md` | Public/widget | `x-api-key`, JSON body `message`/`sessionId` |
| `reports/CURSOR_REPORT_08F_FIX_EMBED_SNIPPET_PUBLIC_WIDGET_KEY_DISPLAY.md` | Embed | `widgetKey` trên embed-config |
| `Backend/.../SecurityConfig.java` | Smoke | `permitAll` cho `/api/**` hiện tại |
| `Backend/.../PublicChatController.java` | Public | `ChatRequest` + `Widget-Id` từ filter |
| `Backend/.../dto/ChatbotResponse.java` | Smoke | `apiKey` chỉ lúc create |

---

## 3. Runtime environment

| Item | Status | Notes |
|------|--------|--------|
| Docker `mysql` + `qdrant` | **PASS** | `docker compose up -d mysql qdrant` — mysql **healthy**, qdrant **Up**. |
| Backend Spring Boot `dev` | **PASS** | `.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"` (background); log có `Started RagChatbotBeApplication`. |
| Env `GROQ_API_KEY` / `NOMIC_API_KEY` | **PASS** | Nạp từ file `.env` ở root repo vào **process** trước khi chạy Maven (không ghi giá trị key trong report). |
| Frontend `.env.local` | **PASS** | Đã tạo/cập nhật: `VITE_USE_MOCK_API=false`, `VITE_API_URL=http://localhost:8080`. |
| Frontend `npm run dev` | **PASS** | Chạy background; `GET http://localhost:5173` → **200**. |
| Browser manual | **NOT RUN** | Không có môi trường browser tự động trong agent. |

---

## 4. Live API smoke results

Base URL: `http://localhost:8080`. Chatbot test tạo qua `POST /api/chatbots` (name prefix `10A Smoke …`); `apiKey` (UUID) dùng cho public chat — **không** ghi full UUID trong report.

| Area | Endpoint/Flow | Expected | Actual | Status | Notes |
|------|-----------------|----------|--------|--------|--------|
| Chatbots | `GET /api/chatbots?page=0&size=5` | 200, items | 200, items=5 | **PASS** | Pagination OK |
| Chatbots | `POST /api/chatbots` | 200 + id + apiKey | 200, id UUID, apiKey len 36 | **PASS** | Khớp `ChatbotResponse.apiKey` chỉ khi create |
| Chatbots | `GET /api/chatbots/{id}` | 200 | 200 | **PASS** | Detail không trả `apiKey` (expected) |
| Chatbots | `GET /api/chatbots/{id}/embed-config` | 200, `widgetKey` | `widgetKey` present | **PASS** | Khớp 08F |
| Documents | `GET /api/documents?page=0&size=10` | 200 | 200, total=11 | **PASS** | Không 500 (08D1) |
| Documents | `GET ...?status=INDEXED&...` | 200 | 200 | **PASS** | items=9 |
| Documents | `GET ...?chatbotId=not-a-uuid&...` | 400 JSON | HTTP **400** | **PASS** | Khớp 08D1 |
| Public | `POST /api/public/chat` + `x-api-key` + ASCII body | 200, answer/sessionId/sources | 200 | **PASS** | `Invoke-RestMethod` + `'{"message":"hello",...}'` OK |
| Public | Same + body `"message":"Xin chào"` (UTF-8) qua `Invoke-RestMethod` | 200 | **400** | **FAIL (client)** | PowerShell mặc định làm hỏng encoding JSON có dấu → server thấy `message` blank/invalid. `Invoke-WebRequest` + UTF-8 hoặc browser/curl UTF-8: **200** (đã verify nhanh bằng bot debug). **Không** sửa backend trong scope 10A. |
| Public | Missing `x-api-key` | 401/400 | **401** | **PASS** | JSON `error` |
| Public | Invalid UUID key | 401/400 | **401** | **PASS** | |
| Playground | `POST /api/playground/chat` (SSE) | 200, stream | 200, content chứa `data:`/`token`/`done` | **PASS** | Timeout 45s |
| Playground | `GET /api/playground/sessions?chatbotId=` | 200 list | 200, count≥0 | **PASS** | Bot mới: có thể 0 session |
| Playground | `POST /api/playground/compare` | configA/configB | hasA/hasB true | **PASS** | |
| Playground | `GET /api/playground/export/{sessionId}` | 200 hoặc 404 nếu không có session | **NOT RUN** | **SKIP** | Chatbot lấy `size=1` không có session tại thời điểm test; không chứng minh export end-to-end trong script này. |
| Dashboard | `/summary`, `/message-volume?days=7`, `/top-chatbots?limit=5`, `/activity?limit=20` | 200 | 200 | **PASS** | empty-safe |
| Analytics | Invalid `from=bad` | 400 | **400** | **PASS** | |
| Analytics | summary/daily/by-chatbot/unanswered/sessions (valid range) | 200 | 200 | **PASS** | Dates `2026-05-01` … theo prompt |
| Settings | `GET/PUT /api/settings/profile` | 200 | 200 | **PASS** | PUT round-trip same payload |
| Settings | `GET /api/settings/api-keys` | list không `plainTextKey`/`keyHash` | không field nhạy cảm | **PASS** | |
| Settings | `POST /api/settings/api-keys` | 201 + `plainTextKey` once | có `plainTextKey` | **PASS** | |
| Settings | `DELETE /api/settings/api-keys/{id}` | 200 | 200 | **PASS** | Key tạo riêng cho 10A đã xóa |

**Optional (upload TXT / chunks / delete document):** **NOT RUN** trong phiên này (đủ bằng list + filter + 400).

---

## 5. Browser regression results

| Area | Flow | Expected | Actual | Status | Notes |
|------|------|----------|--------|--------|--------|
| Full checklist D1–D12 | Login → … → widget harness | Theo prompt 10A | Không chạy browser trong agent | **NOT RUN** | Cần user/manual: `http://localhost:5173` + harness `public-widget-test.html?widgetKey=…` |

---

## 6. Bugs found and fixed

| Bug | Area | File | Fix | Retest |
|-----|------|------|-----|--------|
| Không có bug **backend/FE source** mới trong phạm vi smoke | — | — | — | — |
| PowerShell `Invoke-RestMethod` + JSON có ký tự Unicode (vd. `chào`) → 400 public chat | Client/tooling | — | Không sửa code repo; workaround: ASCII body, `Invoke-WebRequest` + UTF-8, hoặc curl/browser | ASCII public chat **PASS** |

---

## 7. Files changed

| File | Change | Reason | Risk |
|------|--------|--------|------|
| `Frontend/.env.local` | Tạo/cập nhật `VITE_USE_MOCK_API=false`, `VITE_API_URL=http://localhost:8080` | Setup real API theo prompt 10A | Thấp — file thường gitignore |

---

## 8. Validation results

| Command | Result | Notes |
|---------|--------|--------|
| `cd Backend && .\mvnw.cmd -DskipTests compile` | **PASS** | BUILD SUCCESS |
| `cd Backend && .\mvnw.cmd test` (sau khi nạp `.env` vào process, MySQL+Qdrant đã chạy) | **PASS** | Tests run: 14, Failures: 0, Errors: 0 |
| `cd Frontend && npm run lint` | **PASS** | ESLint exit 0 |
| `cd Frontend && npm run build` | **PASS** | Vite build OK (cảnh báo chunk size như cũ) |
| `cd Frontend && npm run build:widget` | **PASS** | `dist-widget/chatbot-widget.iife.js`, `widget.css` |

---

## 9. Known limitations

- So với prompt: **export session** chưa smoke có session thật; **upload document** optional chưa chạy.
- **Browser / widget UI** chưa xác nhận lại trong agent (NOT RUN).
- **Compare mode** vẫn limitation engine sâu (đã biết từ các report trước).
- **PowerShell + JSON tiếng Việt có dấu** có thể gây 400 khi dùng `Invoke-RestMethod` — không phải regression API server khi client gửi UTF-8 đúng.
- Smoke đã tạo thêm vài chatbot tên `10A Smoke …` / `10A PubDbg*` — có thể xóa qua admin API/UI nếu muốn DB gọn.

---

## 10. Final decision

**READY WITH KNOWN LIMITATIONS**

- Live API smoke (hầu hết nhóm C) **PASS** trên backend đang chạy + Docker infra.
- Browser regression và một phần optional (upload/export) **chưa** chứng minh trong phiên agent.

**Gợi ý tiếp:** Manual/browser một vòng checklist D + thử `public-widget-test.html` với `widgetKey` từ Embed tab; nếu cần script smoke ổn định tiếng Việt trên Windows, dùng `curl.exe` hoặc `[System.Text.Encoding]::UTF8.GetBytes()` khi gọi `Invoke-WebRequest`.
