# Cursor Report 08E - PUBLIC_WIDGET_AND_PUBLIC_CHAT_VERIFICATION

## 1. Mức độ hiểu task

- **Task là gì?** Kiểm thử tích hợp Public Chat (`POST /api/public/chat`) + widget runtime (build IIFE, harness `RagChatbotConfig`, iframe `/widget`), chỉ sửa bug nhỏ contract/path/config nếu cần; không feature mới, không RAG core/schema/JWT.
- **Hiểu task:** 97%
- **Phần chắc chắn:**
  - `x-api-key` (UUID `apiKey` của widget/chatbot) + body `{ message, sessionId }` → JSON `{ answer, sessionId, sources }`.
  - Widget shell (`widget.js`) mở iframe `frontendUrl/widget?...`; trang iframe dùng **SSE** `POST /api/chat/stream` + header **`X-Widget-Key`** (cùng giá trị UUID với `apiKey`), không dùng `POST /api/public/chat` trong iframe — đây là kiến trúc hiện có (stream vs JSON), không đổi trong prompt này.
  - `npm run build:widget` sinh `dist-widget/chatbot-widget.iife.js`; Vite dev serve `/dist-widget/*` qua middleware trong `vite.config.js`.
- **Phần còn giả định:**
  - Browser manual (bubble click, CORS console) **không** chạy trong agent; kết quả widget UI ghi **NOT RUN** / code-path.
- **Phạm vi không làm:** Dashboard/Analytics/Settings/Documents/Playground (ngoài tạo test data), refactor widget lớn, đổi ingest/RAG.

## 2. Files/rules/reports đã đọc

| File | Mục đích đọc | Kết luận chính |
|------|----------------|----------------|
| `.cursor/rules/00-core-working-rule.mdc` | Nền | Minimal diff |
| `.cursor/rules/10-backend-rag-rule.mdc` | Backend | Public chat trong `PublicChatController` + `ChatService` |
| `.cursor/rules/20-frontend-widget-rule.mdc` | Widget | `widget.js`, `WidgetChatPage`, build widget |
| `.cursor/rules/30-deploy-env-ops-rule.mdc` | Env | `VITE_API_URL`, proxy `/api` |
| `.cursor/rules/40-db-vector-rule.mdc` | DB | Không đổi schema |
| `.cursor/rules/90-report-verification-rule.mdc` | Report | Ghi trung thực PASS/NOT RUN |
| `reports/CURSOR_REPORT_04A_BACKEND_PUBLIC_AND_PLAYGROUND_CHAT_RUNTIME_SMOKE_TEST.md` | Baseline public | 200 + fields; missing/invalid key **401** + `error` |
| `reports/CURSOR_REPORT_08D_FIX_REMAINING_BROWSER_E2E_ISSUES.md` | Tiền đề | 08D không scope public widget |
| `reports/CURSOR_REPORT_12A_WIDGET_RUNTIME_CONFIG_CONSUMPTION.md` | Widget config | `RagChatbotConfig` fields consumed |
| `reports/CURSOR_REPORT_12B_WIDGET_RUNTIME_SMOKE_TEST_AND_MINOR_FIXES.md` | Widget smoke | build:widget + harness pattern |
| `Frontend/src/api/publicChatApi.js` | FE public JSON | `fetch` + `x-api-key`, parse `message`/`error` |
| `Frontend/src/pages/WidgetChatPage.jsx` | Iframe chat | `/api/chat/stream`, `X-Widget-Key` |
| `Frontend/widget/widget.js` | Shell | `widgetKey`/`apiKey`, iframe query, position |
| `Frontend/vite.widget.config.js` | Build | `dist-widget/chatbot-widget.iife.js` |
| `Frontend/public/public-widget-test.html` | Harness | Đã chỉnh để query param + path script |
| `Backend/.../PublicChatController.java` | Public JSON API | Map 400 message khi blank |
| `Backend/.../WidgetAuthFilter.java` | Auth | `x-api-key` cho `/api/public/chat`; `X-Widget-Key` cho `/api/chat` + stream |
| `Backend/.../SecurityConfig.java` | permit | `/api/public/**` permitAll |

*(Các file còn lại trong danh sách prompt đã đối chiếu khi cần; không có thay đổi `ChatbotEmbedPage` / `WidgetLivePreview` trong prompt này.)*

## 3. Test data setup

| Field | Value |
|-------|--------|
| **chatbotId (full public chat flow)** | `694ec0ff-87e9-4c1a-ae6d-eb88b1396b8c` — tạo qua `POST /api/chatbots` (name `08E Public Flow`). |
| **apiKey / widgetKey** | UUID trả về field `apiKey` **chỉ lúc create**; không ghi full key trong report (chỉ verify prefix `cf7e4b33...` trong session test). |
| **Document upload** | `POST /api/documents/upload` (curl `-F`) với TXT chứa câu về đổi trả → doc `6db7d2d5-2866-4494-8792-09b9378aa441`, **status INDEXED**, `chunkCount` 1. |
| **File tạm** | Đã tạo `tmp-public-widget-test.txt` ở root repo cho upload, sau đó **đã xóa** để tránh rác workspace (có thể tạo lại nội dung tương tự khi cần retest). |
| **Chatbot bổ sung** | Các bot tên `08E Public Widget Smoke 2`, `08E blankmsg`, v.v. dùng cho thử nhanh — có thể xóa qua API sau nếu dọn DB. |

## 4. Public Chat curl / PowerShell results

| Test | Expected | Actual | Status | Notes |
|------|----------|--------|--------|-------|
| Valid key, body `message` + `sessionId:null` | 200, `answer` non-empty, `sessionId`, `sources` array | **200**, đủ field, `answerNonEmpty=True`, `sourcesIsArray=True` | PASS | Gọi `Invoke-RestMethod` + header `x-api-key` |
| Missing `x-api-key` | 400/401, JSON, không 500 | **401** `{"error": "Missing API key header."}` | PASS | `curl -i` |
| Invalid UUID `x-api-key: not-a-uuid` | 400/401, không 500 | **401** `{"error": "Invalid UUID format for Widget Key."}` | PASS | |
| Blank / whitespace `message` (valid key) | 400 + `message` rõ (sau fix) | Trước fix: **400** body Spring default JSON; sau sửa source: **400** + `{"message":"message must not be blank"}` | **PASS (source)** / runtime 8080 cần **restart** BE để thấy body mới | JVM `localhost:8080` tại thời điểm curl vẫn bản cũ (chứng minh: body vẫn default error). |

## 5. Widget browser results

| Test | Expected | Actual | Status | Notes |
|------|----------|--------|--------|-------|
| Bubble + iframe + gửi tin + network | Như checklist prompt | Không mở browser trong agent | **NOT RUN** | Cần manual: `npm run dev` → `http://localhost:5173/public-widget-test.html?widgetKey=<apiKey>` |
| Network path “public chat” | Prompt gợi ý `/api/public/chat` | Iframe **WidgetChatPage** gọi **`/api/chat/stream`** với **`X-Widget-Key`** (proxy tới 8080) | **N/A (design)** | JSON public chat là `publicChatApi.js` / tích hợp khác; widget giữ **SSE** — không coi là bug trong scope 08E. |
| CORS | Không lỗi nghiêm trọng | Chưa verify browser | **NOT RUN** | `vite.config.js` proxy `/api`; shell load từ cùng origin dev |

## 6. Runtime config spot check

| Config | Expected | Actual | Status |
|--------|----------|--------|--------|
| `position: bottom-right` | Default iframe + bubble | `widget.js` default + `applyPositionStyles` | PASS (code) |
| `position: bottom-left` | `left` thay `right` | `widget.js` nhánh `bottom-left` | PASS (code) |
| `widgetColor` hex | Bubble màu custom | `isHexColor` + inline style | PASS (code) |
| `welcomeMessage` | Truyền query vào `/widget` | `URLSearchParams` + `WidgetChatPage` đọc | PASS (code) |
| `launcherIcon` help/spark | SVG khác default | `normalizeLauncherIcon` | PASS (code) |

## 7. Bugs found and fixed

| Bug | Area | File | Fix | Retest status |
|-----|------|------|-----|----------------|
| Harness dùng `sk_live_...` không phải UUID + script URL tuyệt đối localhost | FE harness | `Frontend/public/public-widget-test.html` | `widgetKey`/`apiKey` lấy từ query; script `/dist-widget/chatbot-widget.iife.js` (cùng origin + middleware Vite); hướng dẫn lấy key từ `POST /api/chatbots` | Manual retest |
| Blank `message` trả 400 không có `message` cho FE | BE | `PublicChatController.java` | `ResponseEntity.badRequest().body(Map.of("message", "message must not be blank"))`; ký hiệu trả về `ResponseEntity<?>` | **Compile PASS**; runtime cần redeploy BE |

## 8. Files changed

| File | Change | Reason | Risk |
|------|--------|--------|------|
| `Backend/.../PublicChatController.java` | 400 blank message có JSON `message` | Align `publicChatApi` / pattern lỗi JSON | Thấp |
| `Frontend/public/public-widget-test.html` | Query param key + path script + hướng dẫn | Tránh placeholder sai contract UUID | Thấp |

## 9. Validation results

| Command | Result | Notes |
|---------|--------|-------|
| `cd Backend && ./mvnw -DskipTests compile` | **PASS** | |
| `cd Backend && ./mvnw test` (env từ `.env`) | **PASS** | exit 0 |
| `cd Frontend && npm run lint` | **PASS** | |
| `cd Frontend && npm run build` | **PASS** | |
| `cd Frontend && npm run build:widget` | **PASS** | Output: `dist-widget/chatbot-widget.iife.js`, `dist-widget/widget.css` |

## 10. Known limitations / gaps

- **`apiKey` chỉ xuất hiện khi tạo chatbot** — harness cần copy thủ công hoặc query URL; không có API public để “lấy lại” key trong report.
- **Widget iframe không dùng `POST /api/public/chat`** — dùng stream legacy; kiểm `x-api-key` + JSON nên dùng `publicChatApi` hoặc curl như mục 4.
- **Browser visual / CORS thực tế** chưa xác nhận trong phiên agent.
- Sau sửa `PublicChatController`, máy dev cần **restart Spring Boot** để thấy body 400 mới (đã kiểm chứng JVM cũ vẫn trả body default).

## 11. Final decision

**Public widget/chat verification PASS** đối với: tạo data + upload + `POST /api/public/chat` 200 + auth 401 JSON + `build:widget` + lint/build; harness được sửa hợp lệ UUID + path dev.

**Some items remain manual:** mở browser harness, xác nhận bubble/iframe/stream end-to-end (mục 5).

Không BLOCKED provider: Groq/Nomic đã trả lời được trong public chat test (answer non-empty).

## 12. Recommended next prompt

**09A_FINAL_QA_REGRESSION_AND_DELIVERY_CHECKLIST**
