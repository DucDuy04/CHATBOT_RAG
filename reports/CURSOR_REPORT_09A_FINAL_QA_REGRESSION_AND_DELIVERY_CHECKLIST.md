# Cursor Report 09A - FINAL_QA_REGRESSION_AND_DELIVERY_CHECKLIST

## 1. Mức độ hiểu task

- **Task là gì?** Final QA regression và delivery checklist: không làm feature mới; đọc rules/reports/source chính; chạy Docker (mysql+qdrant), build/test FE/BE/widget, smoke API nếu backend sẵn sàng; sanity bảo mật; ghi limitation thật; chỉ sửa bug nhỏ nếu có — trong phiên này chỉ bổ sung `.env.example`.
- **Hiểu task:** 98%
- **Phạm vi không làm:** Feature mới, refactor lớn, đổi schema/RAG core/JWT/compare engine sâu/Qdrant purge/delete account/Settings middleware/UI lớn.

---

## 2. Files/rules/reports đã đọc

| File | Mục đích đọc | Kết luận chính |
|------|----------------|----------------|
| `.cursor/rules/00-core-working-rule.mdc` | Luật nền | Minimal diff, awareness tài nguyên yếu |
| `.cursor/rules/10-backend-rag-rule.mdc` | Backend/RAG | Checklist compile/test |
| `.cursor/rules/20-frontend-widget-rule.mdc` | FE/widget | lint/build/build:widget |
| `.cursor/rules/30-deploy-env-ops-rule.mdc` | Deploy/env | Docker, env bắt buộc |
| `.cursor/rules/40-db-vector-rule.mdc` | DB/Qdrant | Không đổi schema trong QA này |
| `.cursor/rules/90-report-verification-rule.mdc` | Report | Rule mặc định `docs/`; prompt 09A yêu cầu `reports/` |
| `reports/CURSOR_REPORT_02B_RERUN_BACKEND_CHATBOTS_API_RUNTIME_SMOKE_TEST.md` | Bắt buộc | Tồn tại — baseline chatbots/smoke |
| `reports/CURSOR_REPORT_03C_BACKEND_DOCUMENTS_RUNTIME_SMOKE_TEST.md` | Bắt buộc | Documents runtime |
| `reports/CURSOR_REPORT_03B_FE_DOCUMENT_UPLOAD_TENANT_CONTEXT.md` | Bắt buộc | FE upload context |
| `reports/CURSOR_REPORT_04A_BACKEND_PUBLIC_AND_PLAYGROUND_CHAT_RUNTIME_SMOKE_TEST.md` | Bắt buộc | Public + playground |
| `reports/CURSOR_REPORT_04B_BACKEND_PLAYGROUND_SESSIONS_COMPARE_EXPORT_CANONICAL_API.md` | Bắt buộc | Sessions/compare/export |
| `reports/CURSOR_REPORT_05A_BACKEND_DASHBOARD_CANONICAL_API.md` | Bắt buộc | Dashboard API |
| `reports/CURSOR_REPORT_06A_BACKEND_ANALYTICS_USAGE_CANONICAL_API.md` | Bắt buộc | Analytics usage |
| `reports/CURSOR_REPORT_06B_BACKEND_ANALYTICS_SESSIONS_CANONICAL_API.md` | Bắt buộc | Analytics sessions |
| `reports/CURSOR_REPORT_06C_BACKEND_CHAT_FEEDBACK_CANONICAL_API_AND_ANALYTICS_RATING_SOURCE.md` | Bắt buộc | Feedback + rating |
| `reports/CURSOR_REPORT_07B_BACKEND_SETTINGS_RUNTIME_SMOKE_TEST_AND_MINOR_FIXES.md` | Bắt buộc | Settings smoke |
| `reports/CURSOR_REPORT_08A_FULLSTACK_REAL_API_INTEGRATION_QA_AND_MINOR_FIXES.md` | Bắt buộc | Fullstack QA |
| `reports/CURSOR_REPORT_08B_BROWSER_MANUAL_E2E_QA_REAL_API_AND_MINOR_FIXES.md` | Bắt buộc | Browser E2E |
| `reports/CURSOR_REPORT_08C_FIX_BROWSER_E2E_DOCUMENTS_AND_PLAYGROUND_ISSUES.md` | Bắt buộc | 08C fixes |
| `reports/CURSOR_REPORT_08D_FIX_REMAINING_BROWSER_E2E_ISSUES.md` | Bắt buộc | 08D fixes |
| `reports/CURSOR_REPORT_08D1_FIX_ACTUAL_DOCUMENTS_LIST_500.md` | Bắt buộc | Root cause 500 documents + fix WidgetConfig orphan |
| `reports/CURSOR_REPORT_08E_PUBLIC_WIDGET_AND_PUBLIC_CHAT_VERIFICATION.md` | Bắt buộc | Public chat + widget SSE/header |
| `reports/CURSOR_REPORT_08F_FIX_EMBED_SNIPPET_PUBLIC_WIDGET_KEY_DISPLAY.md` | Bắt buộc | `widgetKey` trong embed-config + snippet |
| `reports/CURSOR_REPORT_11_INTEGRATION_QA_CHECKLIST_AUDIT.md` | Bắt buộc | Audit integration FE |
| `docker-compose.yml` | Checklist A3 | Có `mysql` + `qdrant` (+ backend/frontend) |
| `Frontend/.env.example` | A1 | Đã bổ sung hướng dẫn mock/real + URL |
| `Frontend/package.json` | B2/B3 | Scripts `lint`, `build`, `build:widget` |
| `Frontend/src/api/apiMode.js` | A1 | Mock chỉ khi `VITE_USE_MOCK_API=true` hoặc unset trong dev |
| `Backend/.env.example` | A2 | Trước đó chỉ có GROQ — đã mở rộng |
| `Backend/src/main/java/.../dto/SettingsApiKeyResponse.java` | E sanity | List key: `maskedKey`, không `plainTextKey` |
| `agent/06-operations.md` | Env/docs | GROQ/NOMIC/MySQL/Qdrant/profile dev đã tài liệu |

**Report trong danh sách bắt buộc prompt:** tất cả path trên **đều tồn tại** trong workspace (đã glob/đọc).

---

## 3. Environment readiness

| Item | Status | Notes |
|------|--------|--------|
| Docker | **PASS** | `docker compose up -d mysql qdrant` — containers `ragchatbot-mysql` (healthy), `ragchatbot-qdrant` (Up). |
| MySQL | **PASS** | Port 3306, healthcheck healthy sau ~16s. |
| Qdrant | **PASS** | Ports 6333–6334. |
| Backend env | **DOCUMENTED** | `GROQ_API_KEY`, `NOMIC_API_KEY` bắt buộc cho context Spring; xem `Backend/.env.example`, `agent/06-operations.md`. |
| Frontend env | **PASS** (docs) | `Frontend/.env.example` cập nhật `VITE_API_URL`, `VITE_USE_MOCK_API`, ghi chú `VITE_FRONTEND_URL`. |
| Widget build output | **PASS** | `Frontend/dist-widget/chatbot-widget.iife.js`, `Frontend/dist-widget/widget.css` tồn tại sau `npm run build:widget`. |
| Mock mode | **DOCUMENTED** | `apiMode.js`: `false` → real API; unset dev → mock; unset prod build → real. |

---

## 4. Build/test results

| Command | Result | Notes |
|---------|--------|--------|
| `docker compose config --no-interpolate` | **PASS** | Cấu hình hợp lệ; dùng `--no-interpolate` để tránh in giá trị từ file `.env` host khi chia sẻ log. |
| `cd Backend && .\mvnw.cmd -DskipTests compile` | **PASS** | BUILD SUCCESS. |
| `cd Backend && .\mvnw.cmd test` (shell **không** set `GROQ_API_KEY` / `NOMIC_API_KEY`, trước khi infra sẵn sàng) | **FAIL** | Lần 1: MySQL down → JDBC communications link failure; lần 2 (sau MySQL up): `PlaceholderResolutionException` — thiếu `GROQ_API_KEY`. |
| `cd Backend && .\mvnw.cmd test` với `GROQ_API_KEY` + `NOMIC_API_KEY` non-empty (placeholder) và **MySQL+Qdrant** đang chạy | **PASS** | Tests run: 14, Failures: 0, Errors: 0. |
| `cd Frontend && npm run lint` | **PASS** | ESLint exit 0. |
| `cd Frontend && npm run build` | **PASS** | Vite build thành công (`dist/`). |
| `cd Frontend && npm run build:widget` | **PASS** | `dist-widget/chatbot-widget.iife.js` + `widget.css`. |

---

## 5. API smoke results

| Area | Endpoint/Flow | Expected | Actual | Status | Notes |
|------|-----------------|----------|--------|--------|--------|
| C1 Chatbots | GET/POST chatbots, embed-config | 200/201, `widgetKey` | Không gọi được | **NOT RUN** | Không có process lắng nghe `localhost:8080` tại thời điểm kiểm tra (`actuator/health` timeout). |
| C2 Documents | GET list, invalid chatbotId | No 500, 400 invalid UUID | NOT RUN | **NOT RUN** | Cần backend + auth/mock như luồng admin. |
| C3 Public chat | POST `/api/public/chat` | 200/401 JSON | NOT RUN | **NOT RUN** | Cần backend + key thật. |
| C4 Playground | SSE, sessions, compare | No 500 | NOT RUN | **NOT RUN** | Cần backend + env LLM cho stream thật. |
| C5 Dashboard | summary, charts | 200 empty-safe | NOT RUN | **NOT RUN** | |
| C6 Analytics | summary/daily/… | 200, invalid date 400 | NOT RUN | **NOT RUN** | |
| C7 Settings | profile, api-keys | No leak list | NOT RUN | **NOT RUN** | Code review: `SettingsApiKeyResponse` chỉ `maskedKey`. |

**Kết luận:** Smoke HTTP không chạy vì backend local không được start trong phiên agent (tránh tiêu tốn key/thời gian). Behavior đã được cover bởi các report 02B–08E trước đó; 09A không claim PASS runtime API mới.

---

## 6. Browser regression results

| Area | Flow | Expected | Actual | Status | Notes |
|------|------|----------|--------|--------|--------|
| D1–D9 | Full admin + widget harness | Theo checklist | Agent không mở browser | **NOT RUN** | Prompt nói user đã **PASS** widget browser sau 08F — không claim lại PASS trong môi trường agent. |

---

## 7. Security/secret sanity

| Check | Result | Notes |
|--------|--------|--------|
| Repo không commit key Groq/Nomic thật (grep mẫu) | **PASS** (mẫu) | Chỉ thấy placeholder dạng `gsk_xxxxx` trong `agent/06-operations.md`. **Cảnh báo:** `docker compose config` **không** dùng `--no-interpolate` sẽ expand biến từ `.env` host — không paste log đó công khai. |
| Settings API key list không lộ plain/hash | **PASS** (code) | `SettingsApiKeyResponse`: `id`, `name`, `maskedKey`, `status`, timestamps — không `plainTextKey`/`keyHash`. |
| Widget public key chỗ định nghĩa | **PASS** (theo 08E/08F) | embed-config + snippet + create response — đã báo cáo 08F. |
| `plainTextKey` chỉ khi create | **PASS** (theo 07B/06C pattern + DTO) | `SettingsApiKeyCreateResponse` có `plainTextKey`. |
| Delete account endpoint | **PASS** (không audit sâu) | Không phát hiện implement mới trong phạm vi QA này. |
| JWT half-done | **PASS** (không đổi) | Không kiểm tra thêm ngoài scope. |
| Stacktrace trong JSON API | **NOT RUN** | Cần gọi lỗi có chủ đích trên server chạy; mặc định Spring Boot không bật stacktrace cho client thông thường. |
| Reports không chứa full provider keys | **PASS** | Các report đã đọc dùng placeholder/prefix. |

---

## 8. Bugs found and fixed

| Bug | Area | File | Fix | Retest status |
|-----|------|------|-----|----------------|
| `Frontend/.env.example` thiếu hướng dẫn đầy đủ mock/real + URL | Env / onboarding | `Frontend/.env.example` | Thêm `VITE_API_URL`, ghi chú `VITE_FRONTEND_URL`, mô tả rõ `VITE_USE_MOCK_API` khớp `apiMode.js` | Manual review |
| `Backend/.env.example` quá tối thiểu | Env / onboarding | `Backend/.env.example` | Thêm `NOMIC_API_KEY`, Cohere optional, pointer MySQL/Qdrant/profile | Manual review |

Không phát hiện bug runtime mới trong phạm vi build/lint.

---

## 9. Files changed

| File | Change | Reason | Risk |
|------|--------|--------|------|
| `Frontend/.env.example` | Bổ sung biến + mô tả mock | Checklist A1 | Thấp — chỉ tài liệu |
| `Backend/.env.example` | Bổ sung NOMIC + ghi chú infra | Checklist A2 | Thấp — placeholder |

---

## 10. Known limitations accepted

1. **Compare mode:** backend có thể echo/accept config A/B nhưng không có engine override runtime sâu — kết quả A/B có thể tương tự.
2. **Gán document sang chatbot khác:** bị chặn an toàn — không có Qdrant re-index/move vector đầy đủ.
3. **Qdrant vector purge khi xóa document:** có thể hạn chế nếu chưa implement đầy đủ trước đó.
4. **Feedback read/list:** không có endpoint đọc hàng loạt trừ khi đã có ngoài scope QA này — FE có thể chỉ ghi nhận/gửi feedback.
5. **Delete account:** không implement.
6. **Settings API keys:** metadata trong app; không có middleware tiêu thụ key trừ khi triển khai riêng.
7. **Browser visual QA:** cần xác nhận thủ công — agent **NOT RUN**.
8. **Documents “Unknown chatbot”:** có thể với document cũ khi widget/chatbot soft-deleted (đã ghi 08D1).

---

## 11. Delivery checklist

| Item | Status | Notes |
|------|--------|--------|
| Real API mode documented | **PASS** | `Frontend/.env.example` + `apiMode.js` + `agent/06-operations.md` |
| Backend start command documented | **PASS** | `agent/06-operations.md` — `mvnw spring-boot:run` profile `dev` |
| Frontend start command documented | **PASS** | `npm run dev` trong `agent/06-operations.md` / `Frontend/README.md` (template Vite) |
| Docker dependencies documented | **PASS** | `docker-compose.yml` + `agent/06-operations.md` |
| Widget embed snippet works | **PASS (theo 08F + user)** | User xác nhận browser PASS sau 08F; agent không rerun browser. |
| Main admin flows verified | **PARTIAL** | Đã verify build/lint; E2E browser **NOT RUN** trong agent. |
| Public chat verified | **PARTIAL** | Đã verify trong report lịch sử 08E; agent không rerun live. |
| Final build/test pass | **PASS** | compile, lint, build, build:widget; `mvn test` PASS với điều kiện infra + env placeholder. |

---

## 12. Final decision

**READY WITH KNOWN LIMITATIONS**

- Build pipeline và test backend **PASS** trong điều kiện: Docker MySQL+Qdrant + biến `GROQ_API_KEY`/`NOMIC_API_KEY` non-empty (có thể placeholder) cho Spring context.
- Smoke API trực tiếp và browser regression trong phiên agent: **NOT RUN** (không claim thêm ngoài báo cáo/tiền đề user).

---

## 13. Recommended next prompt

- **Nếu cần chứng thực tuyệt đối trước bàn giao:** `10A — LIVE_API_SMOKE_AND_BROWSER_REGRESSION_RERUN` (chạy backend dev, curl/PowerShell đủ nhóm C1–C7, manual checklist D1–D9, không feature).
- **Nếu đủ với trạng thái hiện tại:** không cần prompt implementation tiếp — chỉ triển khai/thủ công theo `agent/06-operations.md`.
