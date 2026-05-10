# Cursor Report 02B Rerun - BACKEND_CHATBOTS_API_RUNTIME_SMOKE_TEST

## 1. Runtime environment

| Item | Status |
|------|--------|
| **Docker** | **Running** — `docker ps`: `ragchatbot-mysql` (healthy), `ragchatbot-qdrant` (Up); ports `3306`, `6333–6334` mapped |
| **MySQL** | **Up** (container healthy) |
| **Qdrant** | **Up** |
| **Backend server** | **Running** on `http://localhost:8080` — Spring Boot restarted sau khi sửa filter để áp dụng binary mới |
| **Base URL tested** | `http://localhost:8080` |
| **API keys cho server** | Biến môi trường load từ file `.env` ở root repo khi start Maven (không ghi giá trị secret trong report) |

**Lưu ý:** Smoke HTTP dùng `Invoke-WebRequest` / JSON body UTF-8. `curl.exe` với `-d` trong PowerShell dễ gây **400 Bad Request** (JSON sai); đây là hạn chế client Windows, không phải lỗi API.

---

## 2. Smoke test results

| Test | Endpoint | Expected | Actual | Status | Notes |
|------|----------|----------|--------|--------|--------|
| 1 | `POST /api/chatbots` | 200, chatbot shape, có thể có `apiKey` | 200, đủ field + `apiKey` (không log chi tiết) | **PASS** | Body JSON qua `Invoke-WebRequest` |
| 2 | `GET /api/chatbots?page=0&size=10` | `items`, `page`, `size`, `total`, `totalPages` | 200, pagination đúng; item có trong list | **PASS** | Item đầu không có field `apiKey` |
| 3 | `GET ...?search=Support&status=ACTIVE&domain=support...` | Không crash; filter hợp lý | 200; `MATCH_IDS:1` | **PASS** | |
| 4 | `GET /api/chatbots/{id}` | Object chatbot | 200 | **PASS** | |
| 5 | `PUT /api/chatbots/{id}` | `systemPrompt` + `modelConfig.temperature` = 0.3 | 200; `temperature` 0.3; prompt tiếng Việt đúng trong JSON | **PASS** | |
| 6 | `GET .../embed-config` | Embed shape + defaults | 200 | **PASS** | |
| 7 | `PUT .../embed-config` (valid) | Màu, welcome, position, origins, icon | 200; `#dc2626`, `bottom-left`, `spark` | **PASS** | |
| 8 | `GET /api/chatbots/not-a-uuid` | 400 + `message`/`error` | 400; `{"message":"..."}` | **PASS** | |
| 9 | `PUT .../embed-config` (invalid color + position) | 400 + message | 400; message validation `widgetColor` | **PASS** | Kiểm tra fail trước tại màu |
| 10 | `DELETE /api/chatbots/{id}` | `{success:true}` | 200 `{"success":true}` | **PASS** | |
| 11 | `GET /api/chatbots/{id}` sau delete | 404 | 404 + message | **PASS** | |
| 12 | `GET /api/chatbots` sau delete | Bot đã xóa không còn | `total` giảm; id không còn trong `items` | **PASS** | Còn 1 bot khác trong DB từ trước |

---

## 3. Bugs found and fixed

| Bug | File | Fix | Retest |
|-----|------|-----|--------|
| `/api/chatbots` bị nhầm là endpoint chat → filter yêu cầu `X-Widget-Key` → **401 Missing API key** | `WidgetAuthFilter.java` | Chỉ coi là legacy chat khi `path.equals("/api/chat")` hoặc `path.startsWith("/api/chat/")`, **không** dùng `startsWith("/api/chat")` (tránh khớp `/api/chatbots`) | POST/GET chatbots **PASS** sau restart |

---

## 4. Validation results

| Command | Result | Notes |
|---------|--------|-------|
| `cd Backend && .\mvnw.cmd -DskipTests compile` | **PASS** | BUILD SUCCESS |
| `cd Backend && .\mvnw.cmd test` | **FAIL** | `Could not resolve placeholder 'GROQ_API_KEY'` — môi trường test JVM không có biến env (không liên quan Chatbots CRUD). Cần export `GROQ_API_KEY`/`NOMIC_API_KEY` khi chạy test hoặc cấu hình test profile. |

---

## 5. Final decision

**All Chatbots API smoke tests PASS. Proceed to next backend prompt.**

*(Điều kiện: backend đã restart sau fix filter; DB + Qdrant Docker đang chạy.)*
