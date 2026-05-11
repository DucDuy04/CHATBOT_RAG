# Cursor Report 08F - FIX_EMBED_SNIPPET_PUBLIC_WIDGET_KEY_DISPLAY

## 1. Mức độ hiểu task

- **Task là gì?** Sửa trang Chatbot Embed để snippet nhúng widget dùng **public widget key thật** (UUID `WidgetConfig.apiKey`), không còn placeholder `YOUR_PUBLIC_API_KEY`, không dùng `chatbotId` thay key; bổ sung API embed-config nếu thiếu field; cảnh báo khi key không tải được; không đụng Settings API keys / RAG core / schema DB.
- **Hiểu task:** 98%
- **Phần chắc chắn:**
  - Key public nằm ở `WidgetConfig.apiKey` (UUID), trùng với header `x-api-key` / `X-Widget-Key` cho chat (report 08E).
  - `POST /api/chatbots` đã trả `apiKey` một lần (`@JsonInclude` trên list/detail).
  - `ChatbotEmbedPage.buildSnippet` trước đây hardcode `YOUR_PUBLIC_API_KEY` và script sai path `chatbot-widget.js`.
- **Phần còn giả định:**
  - Browser manual (Embed tab, harness) không chạy trong agent — ghi NOT RUN.
- **Phạm vi không làm:** JWT, Settings API keys backend, đổi widget runtime (`widget.js`), refactor lớn Embed layout.

## 2. Files/rules/reports đã đọc

| File | Mục đích đọc | Kết luận chính |
|------|----------------|----------------|
| `.cursor/rules/00-core-working-rule.mdc` | Nền | Minimal diff |
| `.cursor/rules/10-backend-rag-rule.mdc` | BE | DTO embed-config |
| `.cursor/rules/20-frontend-widget-rule.mdc` | Widget | Snippet + `dist-widget` |
| `.cursor/rules/30-deploy-env-ops-rule.mdc` | Env | `VITE_FRONTEND_URL` / origin |
| `.cursor/rules/40-db-vector-rule.mdc` | DB | Không đổi schema |
| `.cursor/rules/90-report-verification-rule.mdc` | Report | Audit trung thực |
| `reports/CURSOR_REPORT_08E_PUBLIC_WIDGET_AND_PUBLIC_CHAT_VERIFICATION.md` | Tiền đề | `widgetKey`/`apiKey` UUID; script `dist-widget/...` |
| `reports/CURSOR_REPORT_02B_RERUN_BACKEND_CHATBOTS_API_RUNTIME_SMOKE_TEST.md` | Chatbots API | Create trả `apiKey` |
| `reports/CURSOR_REPORT_12A_WIDGET_RUNTIME_CONFIG_CONSUMPTION.md` | Config | `RagChatbotConfig.widgetKey \|\| apiKey` |
| `reports/CURSOR_REPORT_12B_WIDGET_RUNTIME_SMOKE_TEST_AND_MINOR_FIXES.md` | Build widget | `chatbot-widget.iife.js` |
| `Frontend/src/api/chatbotsApi.js` | FE API | `getEmbedConfig` / mock |
| `Frontend/src/pages/chatbots/ChatbotEmbedPage.jsx` | Snippet | Root cause hardcode |
| `Frontend/src/pages/chatbots/components/EmbedCodeBlock.jsx` | Copy | Cần chặn copy khi thiếu key |
| `Backend/.../EmbedConfigResponse.java` | DTO | Thiếu `widgetKey` |
| `Backend/.../WidgetService.java` | Map embed | `toEmbedConfigResponse` |

## 3. Root cause

- **Vì sao snippet là `YOUR_PUBLIC_API_KEY`:** `ChatbotEmbedPage.jsx` hàm `buildSnippet` **hardcode** chuỗi đó và luôn gắn `chatbotId` vào config, không đọc key từ API sau khi reload trang.
- **Key thật nằm ở đâu:** `WidgetConfig.apiKey` (cột `api_key`, UUID). Được tạo khi tạo chatbot; filter auth dùng đúng UUID này.
- **API thiếu field:** `GET /api/chatbots/{id}/embed-config` trả `EmbedConfigResponse` **không** có field public key — sau reload Embed tab, FE không thể hiển thị key (create response có key nhưng user không ở màn create).
- **FE có bỏ qua field không:** Trước fix không có field để đọc; sau fix FE đọc `widgetKey` hoặc fallback `apiKey` từ embed-config.

## 4. Files changed

| File | Change | Reason | Risk |
|------|--------|--------|------|
| `Backend/.../dto/EmbedConfigResponse.java` | Thêm `String widgetKey` (public embed UUID, cùng giá trị `WidgetConfig.apiKey`) | Embed tab cần key sau reload | Thấp — lộ key public là mục đích embed |
| `Backend/.../service/WidgetService.java` | `toEmbedConfigResponse`: `.widgetKey(w.getApiKey().toString())` | Map từ entity | Thấp |
| `Frontend/.../ChatbotEmbedPage.jsx` | `toFormState` lấy `widgetKey \|\| apiKey`; `buildSnippet` dùng `widgetKey` thật, bỏ `chatbotId`/placeholder; script → `/dist-widget/chatbot-widget.iife.js`; banner thiếu key; `copyDisabled` | Snippet chạy được + đúng contract 08E | Thấp |
| `Frontend/.../components/EmbedCodeBlock.jsx` | Prop `copyDisabled`, chặn copy + toast khi thiếu key | Tránh copy snippet không hợp lệ | Thấp |
| `Frontend/src/mocks/chatbotsMock.js` | Mỗi `embedConfigs[cb-*]` thêm `widgetKey` UUID mock | Mock mode Embed có key | Thấp |
| `Frontend/src/api/chatbotsApi.js` | Mock `createChatbot`: `apiKey` + `embedConfigs.widgetKey` | Mock create + embed đồng bộ | Thấp |

## 5. API contract after fix

| Endpoint | Field |
|----------|--------|
| `GET /api/chatbots/{id}/embed-config` | **`widgetKey`** (string UUID) — chính thức; đồng thời vẫn có `widgetColor`, `welcomeMessage`, `position`, `allowedOrigins`, `launcherIcon`. |
| `PUT /api/chatbots/{id}/embed-config` | Response cùng DTO → **vẫn có `widgetKey`** sau save (map lại từ `WidgetConfig.apiKey`). |

**Ví dụ response (sau khi deploy BE mới):**

```json
{
  "widgetKey": "cf7e4b33-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
  "widgetColor": "#2563eb",
  "welcomeMessage": "Xin chào! ...",
  "position": "bottom-right",
  "allowedOrigins": ["http://localhost:5173"],
  "launcherIcon": "chat"
}
```

**Khác Settings API keys:** `widgetKey` là UUID cột `widget_configs.api_key` dùng embed/chat; **không** lấy từ Settings / không tạo endpoint reveal Settings secrets.

**Ghi chú verify runtime:** Gọi `GET .../embed-config` lên JVM **chưa restart** sau build chỉ thấy các field cũ (không có `widgetKey`) — đã kiểm chứng trước deploy; sau restart BE bản mới, field xuất hiện.

## 6. Snippet behavior after fix

- **Placeholder `YOUR_PUBLIC_API_KEY`:** Không còn trong code path — snippet dùng `widgetKey: "<uuid thật từ API>"` khi đã load.
- **Copy snippet:** Nút Copy **disabled** khi `widgetKey` rỗng; bấm vẫn toast lỗi tiếng Việt nếu bypass UI.
- **Thiếu key:** Banner vàng: *“Thiếu public widget key. Vui lòng refresh trang hoặc tạo lại chatbot…”*.
- **Live preview:** Không đổi — vẫn chỉ visual (không gọi API); không inject key giả vào preview.
- **Script URL:** `.../dist-widget/chatbot-widget.iife.js` (khớp `vite.config.js` serve + build widget).

## 7. Widget verification

| Flow | Expected | Actual | Status |
|------|----------|--------|--------|
| `GET /api/chatbots/{id}/embed-config` có `widgetKey` | JSON có UUID | Cần BE **restart** sau deploy; code + compile OK | **PASS (source)** / runtime cũ NOT YET |
| Snippet hiển thị key thật | Không YOUR_PUBLIC_API_KEY | FE map từ `embedConfig.widgetKey` | PASS (code) |
| Copy snippet | Key thật | Disabled nếu thiếu key | PASS (code) |
| Harness `?widgetKey=` | Stream + header | **NOT RUN** browser trong agent | NOT RUN |
| `POST /api/public/chat` + `x-api-key` | 200 | Không đổi BE auth; regression theo code | NOT RUN (không đổi filter) |

## 8. Validation results

| Command | Result | Notes |
|---------|--------|-------|
| `cd Backend && ./mvnw -DskipTests compile` | **PASS** | |
| `cd Backend && ./mvnw test` | **PASS** | exit 0 |
| `cd Frontend && npm run lint` | **PASS** | |
| `cd Frontend && npm run build` | **PASS** | |
| `npm run build:widget` | **NOT RUN** | Không sửa `widget/` runtime |

## 9. Manual retest

| Flow | Expected | Actual | Status |
|------|----------|--------|--------|
| UI: tạo chatbot → Embed tab | Snippet có UUID | **NOT RUN** trong agent | NOT RUN |
| Copy → harness | Widget chat OK | **NOT RUN** | NOT RUN |

## 10. Known limitations

- **Public widget key hiển thị trong UI/snippet** — bất kỳ ai có quyền admin Embed đều thấy; đây là bản chất “public embed key”, không phải secret kiểu password.
- Cần **restart Spring Boot** để API embed-config trả field mới trên môi trường đang chạy.

## 11. Final decision

**Embed snippet public widget key fixed.** Tiếp tục widget browser verification trên máy dev sau khi restart backend + `npm run dev` + `npm run build:widget` (để file `dist-widget` tồn tại cho script snippet).

## 12. Recommended next prompt

**09A_FINAL_QA_REGRESSION_AND_DELIVERY_CHECKLIST**
