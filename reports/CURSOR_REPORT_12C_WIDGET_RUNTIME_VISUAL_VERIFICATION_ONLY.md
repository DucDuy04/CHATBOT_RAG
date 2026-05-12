# Cursor Report 12C - Widget Runtime Visual Verification Only

## 1. Mức độ hiểu task
- Task là gì?
  - Chạy visual/manual verification cho 8 case widget runtime đã chuẩn bị ở 12B; chỉ sửa bug nhỏ nếu phát hiện lỗi thật.
- Hiểu task: 100%
- Phần chắc chắn:
  - Không thêm feature mới, không refactor, không backend.
  - Mục tiêu là chuyển trạng thái case sang PASS/FAIL/BLOCKED/NOT RUN dựa trên verify thực tế.
  - Nếu không có browser verification trực tiếp thì không được claim PASS.
- Phần còn giả định:
  - Không có cơ chế điều khiển browser trực tiếp trong môi trường tool hiện tại.
- Phạm vi không làm:
  - Không sửa runtime source nếu chưa có bug visual rõ ràng.
  - Không đổi API contract.

## 2. Files/rules/reports đã đọc

| File | Mục đích đọc | Kết luận chính |
|---|---|---|
| `.cursor/rules/00-core-working-rule.mdc` | Rule nền source-first/minimal diff | Bám đúng scope verification-only |
| `.cursor/rules/10-backend-rag-rule.mdc` | Rule backend | Không cần sửa backend |
| `.cursor/rules/20-frontend-widget-rule.mdc` | Rule frontend/widget | Tập trung widget runtime/harness/build |
| `.cursor/rules/30-deploy-env-ops-rule.mdc` | Rule deploy/env | Không claim production verified |
| `.cursor/rules/40-db-vector-rule.mdc` | Rule DB/vector | Không liên quan task này |
| `.cursor/rules/90-report-verification-rule.mdc` | Rule báo cáo | Ghi trung thực PASS/FAIL/BLOCKED và tạo report docs |
| `reports/CURSOR_REPORT_12A_WIDGET_RUNTIME_CONFIG_CONSUMPTION.md` | Baseline runtime changes | Runtime đã có parse fallback cho position/color/welcome/icon |
| `reports/CURSOR_REPORT_12B_WIDGET_RUNTIME_SMOKE_TEST_AND_MINOR_FIXES.md` | Baseline smoke setup | 8 case trước đó đều NOT RUN |
| `docs/2026-05-07-widget-runtime-smoke-harness.html` | File harness để chạy visual test | Có 8 case đầy đủ, cần chỉnh default script URL |
| `Frontend/widget/widget.js` | Kiểm tra runtime consume config | Query params và fallback logic có đủ |
| `Frontend/src/pages/WidgetChatPage.jsx` | Kiểm tra iframe consume params | Có consume color/welcome + fallback |
| `Frontend/vite.config.js` | Kiểm tra đường dẫn script ở dev | `/dist-widget/*` được serve, `/chatbot-widget.js` có thể map HTML fallback |
| `Frontend/vite.widget.config.js` | Kiểm tra output file widget build | Output chính là `dist-widget/chatbot-widget.iife.js` |
| `Frontend/package.json` | Kiểm tra scripts setup | Có `build:widget`, `dev`, `lint`, `build` |

## 3. Environment setup
- Dev server status:
  - Đã chạy `cd Frontend && npm run build:widget` thành công.
  - Đã chạy `cd Frontend && npm run dev -- --host 0.0.0.0 --port 5173`; log hiển thị `Local: http://localhost:5173/`.
- Widget script path tested:
  - `http://localhost:5173/chatbot-widget.js` -> HTTP 200 nhưng trả `text/html` (không đúng JS bundle).
  - `http://localhost:5173/dist-widget/chatbot-widget.iife.js` -> HTTP 200, `application/javascript` (đúng).
- Harness path used:
  - `docs/2026-05-07-widget-runtime-smoke-harness.html` (mở bằng browser thủ công).
  - Đã chỉnh default URL trong harness sang `http://localhost:5173/dist-widget/chatbot-widget.iife.js`.

## 4. Visual verification results

| Case | Expected | Actual observed | Status: PASS / FAIL / BLOCKED / NOT RUN | Evidence / Notes |
|---|---|---|---|---|
| Case 1: bottom-right + blue + chat | Bubble/frame right, màu xanh, welcome đúng | Chưa observe visual trực tiếp | BLOCKED | Môi trường tool không có browser automation trực tiếp |
| Case 2: bottom-left | Bubble/frame left, không conflict right | Chưa observe visual trực tiếp | BLOCKED | Cần verify thủ công bằng harness |
| Case 3: color `#dc2626` | Bubble/header/user/send đỏ | Chưa observe visual trực tiếp | BLOCKED | Cần verify thủ công bằng harness |
| Case 4: welcome tiếng Việt | Hiển thị đúng dấu, không encoded thô | Chưa observe visual trực tiếp | BLOCKED | Cần verify thủ công bằng harness |
| Case 5: icon `help` | Launcher icon đổi help | Chưa observe visual trực tiếp | BLOCKED | Cần verify thủ công bằng harness |
| Case 6: icon `spark` | Launcher icon đổi spark | Chưa observe visual trực tiếp | BLOCKED | Cần verify thủ công bằng harness |
| Case 7: invalid fallback | Fallback đúng + không throw | Chưa observe visual trực tiếp | BLOCKED | Cần mở console browser để xác nhận không JS error |
| Case 8: missing optional fields | Widget render + fallback mặc định | Chưa observe visual trực tiếp | BLOCKED | Cần verify thủ công bằng harness |

## 5. Bugs found and fixed

| Bug | File | Fix | Retest status |
|---|---|---|---|
| Harness default script URL trỏ tới endpoint trả HTML (`/chatbot-widget.js`) nên có thể khiến smoke test visual không tải đúng runtime JS | `docs/2026-05-07-widget-runtime-smoke-harness.html` | Đổi default sang `http://localhost:5173/dist-widget/chatbot-widget.iife.js` | BLOCKED (cần browser visual retest 8 case) |

## 6. Files changed
- `No runtime source changes`.
- Files changed:
  - `docs/2026-05-07-widget-runtime-smoke-harness.html` (harness-only fix).
  - `reports/CURSOR_REPORT_12C_WIDGET_RUNTIME_VISUAL_VERIFICATION_ONLY.md` (report task 12C).
  - `docs/2026-05-07-widget-runtime-visual-verification-only.md` (report theo workspace rule).

## 7. Validation results

| Command | Result | Notes |
|---|---|---|
| `cd Frontend && npm run build:widget` | PASS | Build widget thành công |
| `cd Frontend && npm run dev -- --host 0.0.0.0 --port 5173` | PASS | Dev server start thành công, có `http://localhost:5173/` |
| `HEAD http://localhost:5173/chatbot-widget.js` | PASS | HTTP 200 nhưng trả HTML |
| `HEAD http://localhost:5173/dist-widget/chatbot-widget.iife.js` | PASS | HTTP 200, đúng JS bundle |
| `cd Frontend && npm run lint` | NOT RUN | Không sửa runtime source |
| `cd Frontend && npm run build` | NOT RUN | Không sửa runtime source |

## 8. Final decision
`Visual verification BLOCKED by environment. Manual browser test required.`
