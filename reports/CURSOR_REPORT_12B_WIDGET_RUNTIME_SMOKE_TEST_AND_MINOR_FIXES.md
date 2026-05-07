# Cursor Report 12B - Widget Runtime Smoke Test and Minor Fixes

## 1. Mức độ hiểu task
- Task là gì?
  - Thực hiện smoke test widget runtime sau Prompt 12A cho 8 case config (`position`, `widgetColor`, `welcomeMessage`, `launcherIcon`, fallback), và chỉ sửa bug nhỏ nếu phát hiện.
- Hiểu task: 100%
- Phần chắc chắn:
  - Scope là frontend/widget runtime, không backend.
  - Bắt buộc chạy `lint`, `build`, `build:widget`.
  - Không được claim PASS visual nếu chưa verify thật trong browser.
- Phần còn giả định:
  - Môi trường hiện tại không có khả năng xác nhận visual trực tiếp qua browser automation trong phiên làm việc này.
- Phạm vi không làm:
  - Không sửa backend/API contract.
  - Không thêm package, không refactor lớn.
  - Không mở rộng tính năng ngoài smoke test + minor fixes.

## 2. Files/rules/reports đã đọc

| File | Mục đích đọc | Kết luận chính |
|---|---|---|
| `.cursor/rules/00-core-working-rule.mdc` | Rule nền source-first/minimal diff | Cần sửa tối thiểu, không ngoài scope |
| `.cursor/rules/10-backend-rag-rule.mdc` | Xác nhận phạm vi backend | Task không cần sửa backend |
| `.cursor/rules/20-frontend-widget-rule.mdc` | Rule frontend/widget | Bám đúng file widget/page/build liên quan |
| `.cursor/rules/30-deploy-env-ops-rule.mdc` | Rule env/deploy | Không claim production verify |
| `.cursor/rules/40-db-vector-rule.mdc` | Rule DB/vector | Không liên quan trong task này |
| `.cursor/rules/90-report-verification-rule.mdc` | Rule report/verification | Cần tạo report docs và ghi trung thực PASS/FAIL/NOT RUN |
| `reports/CURSOR_REPORT_06_CHATBOT_EMBED.md` | Baseline embed UI + limitation cũ | Xác nhận embed config fields có trên UI |
| `reports/CURSOR_REPORT_11_INTEGRATION_QA_CHECKLIST_AUDIT.md` | Baseline QA audit | Xác nhận limitation runtime trước 12A |
| `reports/CURSOR_REPORT_12A_WIDGET_RUNTIME_CONFIG_CONSUMPTION.md` | Baseline thay đổi gần nhất | Xác nhận runtime đã consume position/color/welcome/icon về mặt code |
| `Frontend/widget/widget.js` | Kiểm tra runtime consume field/query params | Có parse fallback + truyền param vào iframe |
| `Frontend/widget/widget.css` | Kiểm tra style vị trí/màu mặc định | Inline style từ JS có thể override left/right khi cần |
| `Frontend/src/pages/WidgetChatPage.jsx` | Kiểm tra consume query params trong iframe | Có đọc `widgetColor` và `welcomeMessage`, fallback đúng |
| `Frontend/src/pages/chatbots/ChatbotEmbedPage.jsx` | Kiểm tra snippet field đồng bộ runtime | Snippet đã include `launcherIcon` |
| `Frontend/src/pages/chatbots/components/WidgetLivePreview.jsx` | So khớp behavior preview | Preview vẫn phản ánh icon/color/position/welcome |
| `Frontend/vite.widget.config.js` | Kiểm tra cấu hình build widget | Build IIFE ra `dist-widget/chatbot-widget.iife.js` |
| `Frontend/package.json` | Kiểm tra scripts validation | Có đủ `lint`, `build`, `build:widget` |

## 3. Test harness đã dùng
- Path: `docs/2026-05-07-widget-runtime-smoke-harness.html`
- Cách tạo:
  - Tạo một HTML harness có dropdown cho 8 case config.
  - Gán `window.RagChatbotConfig` theo case.
  - Inject script runtime qua URL configurable (mặc định `http://localhost:5173/chatbot-widget.js`).
- Cách chạy:
  - Mở file harness trong browser.
  - Chọn case -> `Apply Case` -> quan sát bubble/frame + iframe.
- Có giữ lại không?
  - Có giữ lại để tái sử dụng smoke test cho các prompt kế tiếp.
- Nếu xóa sau test, ghi rõ:
  - Không xóa.

## 4. Manual smoke test results

| Case | Config tested | Expected | Actual | Status: PASS / FAIL / NOT RUN | Notes |
|---|---|---|---|---|---|
| Case 1 | bottom-right + blue + welcome + chat | Bubble/frame right, màu đúng, welcome đúng, icon chat | Chưa verify visual trực tiếp | NOT RUN | Đã có harness; chưa có browser visual verification trong phiên |
| Case 2 | bottom-left | Bubble/frame left, không conflict right | Chưa verify visual trực tiếp | NOT RUN | Static inspection cho thấy JS set `left/right` inline đúng |
| Case 3 | custom color `#dc2626` | Bubble/header/user/send dùng màu đỏ | Chưa verify visual trực tiếp | NOT RUN | Static inspection cho thấy màu bind theo param |
| Case 4 | welcome tiếng Việt dài | Welcome hiển thị đúng dấu, không encoded thô | Chưa verify visual trực tiếp | NOT RUN | `URLSearchParams` decode tự động; chưa có visual confirm |
| Case 5 | launcher icon `help` | Bubble icon đổi help | Chưa verify visual trực tiếp | NOT RUN | Code path SVG `help` tồn tại |
| Case 6 | launcher icon `spark` | Bubble icon đổi spark | Chưa verify visual trực tiếp | NOT RUN | Code path SVG `spark` tồn tại |
| Case 7 | invalid config values | Fallback position/color/welcome/icon, không throw | Chưa verify visual trực tiếp | NOT RUN | Static inspection: fallback có trong code |
| Case 8 | missing optional fields | Widget vẫn render + fallback mặc định | Chưa verify visual trực tiếp | NOT RUN | Static inspection: config default + optional params an toàn |

## 5. Bugs found

No bugs found trong static inspection scope 12B.

| Bug | File | Severity | Fix | Status |
|---|---|---|---|---|
| None | - | - | No code fix needed | Closed |

## 6. Files changed

| File | Nội dung sửa | Lý do | Risk |
|---|---|---|---|
| `docs/2026-05-07-widget-runtime-smoke-harness.html` | Thêm harness chạy 8 case smoke test runtime | Hỗ trợ manual visual verification có cấu trúc | Low - file docs/harness độc lập, không ảnh hưởng runtime app |

## 7. Validation results

| Command | Result | Notes |
|---|---|---|
| `cd Frontend && npm run lint` | PASS | Không có lỗi lint |
| `cd Frontend && npm run build` | PASS | Build pass; còn warning chunk-size > 500k (pre-existing) |
| `cd Frontend && npm run build:widget` | PASS | Build widget pass |

## 8. Known limitations remaining
- `apiKey` trong embed snippet vẫn là placeholder (`YOUR_PUBLIC_API_KEY`) vì chưa có source thật từ backend embed-config API.
- `allowedOrigins` là server-side/security concern; runtime client không enforce.
- Manual browser verification cho 8 case hiện `NOT RUN` trong phiên này vì không có visual browser verification trực tiếp.

## 9. Đề xuất tiếp theo
- Chạy harness trong browser thật để chốt PASS/FAIL cho 8 case visual.
- Nếu có case FAIL khi visual test thật, tạo prompt 12C chỉ cho bugfix nhỏ tương ứng (position/color/icon/welcome/fallback).
- Khi backend có endpoint cấp public key thật, cập nhật snippet để bỏ placeholder `apiKey`.
