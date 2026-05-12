# CURSOR REPORT 13 - FINAL MANUAL SMOKE TEST AND RELEASE HANDOFF

## 1. Mức độ hiểu task
- Hiểu task: 100%.
- Phần chắc chắn: cần chạy validation commands frontend, đối chiếu đầy đủ checklist smoke test toàn app, chỉ sửa bug nhỏ nếu phát hiện, không thêm feature.
- Phần còn giả định: không có môi trường browser tương tác trực tiếp trong phiên tool này để thực thi manual click-by-click.
- Thiếu dữ kiện: không có bằng chứng runtime tương tác UI trực tiếp trong phiên hiện tại, nên các hạng mục manual được đánh dấu trung thực `BLOCKED`.

## 2. Files/rules/reports đã đọc

### Rules
- `.cursor/rules/00-core-working-rule.mdc`
- `.cursor/rules/10-backend-rag-rule.mdc`
- `.cursor/rules/20-frontend-widget-rule.mdc`
- `.cursor/rules/30-deploy-env-ops-rule.mdc`
- `.cursor/rules/40-db-vector-rule.mdc`
- `.cursor/rules/90-report-verification-rule.mdc`

### Reports/docs bắt buộc
- `reports/CURSOR_REPORT_11_INTEGRATION_QA_CHECKLIST_AUDIT.md`
- `reports/CURSOR_REPORT_12A_WIDGET_RUNTIME_CONFIG_CONSUMPTION.md`
- `reports/CURSOR_REPORT_12D_FIX_WIDGET_SHELL_CSS_POSITION_AND_LAUNCHER_SIZE.md`
- `docs/2026-05-07-widget-runtime-smoke-harness.html`

### Source chính đã đọc
- `Frontend/src/App.jsx`
- `Frontend/src/pages/WidgetChatPage.jsx`
- `Frontend/widget/widget.js`
- `Frontend/src/pages/dashboard/DashboardPage.jsx`
- `Frontend/src/pages/chatbots/ChatbotsPage.jsx`
- `Frontend/src/pages/chatbots/ChatbotConfigPage.jsx`
- `Frontend/src/pages/chatbots/ChatbotEmbedPage.jsx`
- `Frontend/src/pages/documents/DocumentsPage.jsx`
- `Frontend/src/pages/playground/PlaygroundPage.jsx`
- `Frontend/src/pages/analytics/AnalyticsPage.jsx`
- `Frontend/src/pages/settings/SettingsPage.jsx`
- `Frontend/src/components/layout/AppLayout.jsx`
- `Frontend/src/components/layout/Header.jsx`
- `Frontend/src/components/layout/Sidebar.jsx`
- `Frontend/src/components/common/index.js`
- `Frontend/src/api/index.js`
- `Frontend/src/api/axiosInstance.js`
- `Frontend/src/api/dashboardApi.js`
- `Frontend/src/api/chatbotsApi.js`
- `Frontend/src/api/documentsApi.js`
- `Frontend/src/api/playgroundApi.js`
- `Frontend/src/api/analyticsApi.js`
- `Frontend/src/api/settingsApi.js`
- `Frontend/src/pages/LoginPage.jsx`
- `Frontend/src/routes/PrivateRoute.jsx`
- `Frontend/src/stores/authStore.js`

## 3. Validation results
| Command | Result | Notes |
|---|---|---|
| `cd Frontend && npm run lint` | PASS | ESLint pass, không có lỗi mới. |
| `cd Frontend && npm run build` | PASS | Build pass; còn warning chunk size > 500k (pre-existing). |
| `cd Frontend && npm run build:widget` | PASS | Widget build pass, output `dist-widget/widget.css` và `dist-widget/chatbot-widget.iife.js`. |

## 4. Manual smoke test results
| Area | Flow | Result: PASS / FAIL / BLOCKED | Notes |
|---|---|---|---|
| Auth / Layout | `/login` -> mock login -> `/dashboard`; sidebar; header title; rightSlot cleanup; `/widget` public no layout | BLOCKED | Đã xác nhận wiring qua source (`App.jsx`, `PrivateRoute`, `LayoutContext` usage), nhưng chưa chạy thao tác trực tiếp browser trong phiên này. |
| Dashboard | Metrics/chart/top chatbots/activity/refresh | BLOCKED | Flow có trong `DashboardPage.jsx`; chưa manual click runtime. |
| Chatbots | Search debounce/filter/create/config navigate | BLOCKED | Wiring hiện diện ở page/components/store; chưa manual runtime. |
| Chatbot Config | Load/save prompt/save model/save status/delete/tabs | BLOCKED | Handlers có đủ trong `ChatbotConfigPage.jsx`; chưa manual runtime. |
| Chatbot Embed | Load/save config, copy snippet, live preview | BLOCKED | Logic đầy đủ trong `ChatbotEmbedPage.jsx`; chưa manual runtime. |
| Documents | Invalid/valid upload, search/filter, chunks, assign, retry failed-only, delete, polling | BLOCKED | `DocumentsPage.jsx` có luồng đầy đủ + polling cleanup; chưa manual runtime. |
| Playground | Select chatbot, sessions, streaming, sources/retrieval, latency, overrides, clear, compare, prompt builder, export | BLOCKED | `PlaygroundPage.jsx` + `playgroundApi.js` có wiring; chưa manual runtime. |
| Analytics | Usage/Sessions/Feedback checklist | BLOCKED | `AnalyticsPage.jsx` và tab components đã wiring; chưa manual runtime. |
| Settings | Profile, toggles, keys lifecycle, danger zone no API | BLOCKED | `SettingsPage.jsx` + `settingsApi.js` có wiring; chưa manual runtime. |
| Widget runtime | Harness case 1..8 | BLOCKED | Harness đã đọc, chưa mở browser để visual verify từng case. |

## 5. Bugs found and fixed
| Bug | File | Fix | Retest result |
|---|---|---|---|
| N/A | N/A | No source changes. | N/A |

No source changes.

## 6. Final feature status
| Area | Status | Notes |
|---|---|---|
| Frontend validation commands | READY | Lint/build/build:widget đều PASS. |
| Route/layout/auth guard wiring | READY (source-level) | Route map và public `/widget` đúng theo scope. |
| Dashboard/Chatbots/Config/Embed/Documents/Playground/Analytics/Settings | READY FOR QA | Source wiring đầy đủ; cần manual QA browser để chốt release runtime behavior. |
| Widget runtime shell/config consumption | READY FOR QA | Đã có fixes và reports 12A/12D; cần visual pass harness 8 case ở runtime thật. |

## 7. Known limitations not fixed
- Feedback read endpoint chưa có; Feedback tab đang dựa vào mock/local path ở frontend.
- Playground restore session production còn phụ thuộc endpoint messages/export thực tế backend.
- Embed snippet vẫn dùng placeholder `apiKey` (`YOUR_PUBLIC_API_KEY`) nếu backend chưa cấp key public thật.
- Analytics mock date filter chưa phản ánh filtering thật ở nhánh mock.
- Settings notifications contract cần verify backend thật.
- Delete account endpoint chưa có (Danger Zone chỉ confirm UI).

## 8. Release handoff notes
- Cách chạy frontend:
  - `cd Frontend`
  - `npm install`
  - `npm run dev`
- Cách chạy widget build:
  - `cd Frontend`
  - `npm run build:widget`
- Cách bật/tắt mock API:
  - Xem `Frontend/src/api/apiMode.js` (`USE_MOCK_API`) và env liên quan.
- Các env cần kiểm tra trước staging:
  - `VITE_API_URL`
  - `VITE_FRONTEND_URL`
  - `VITE_WIDGET_API_KEY` (nếu dùng fallback local/dev)
- Các flow cần QA lại trên backend thật:
  - Full manual smoke checklist A/B/C (đặc biệt streaming, documents processing, analytics sessions/feedback).
  - Widget harness 8 cases trong `docs/2026-05-07-widget-runtime-smoke-harness.html`.
  - Settings API keys lifecycle và contract notifications.
