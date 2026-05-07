# Fix Widget Shell CSS Position and Launcher Size (12D)

## 1. Mức độ hiểu task

- Hiểu task: 100%.
- Phần chắc chắn:
  - Bug nằm ở widget shell CSS/runtime.
  - Cần fix hiện tượng launcher/icon phóng lớn và position right/left sai.
  - Không thêm feature mới.
- Phần còn giả định:
  - Không có visual/browser verification trực tiếp trong phiên tool hiện tại.
- Thiếu dữ kiện:
  - Không có kết quả quan sát trực quan sau fix trong cùng phiên.

## 2. Tóm tắt yêu cầu

- Audit root cause giữa `widget.js`, `widget.css`, `vite.widget.config.js`.
- Fix CSS delivery sao cho runtime JS tự đủ style (không phụ thuộc host page phải add CSS link).
- Bảo đảm:
  - Bubble/frame fixed sizing + position đúng.
  - Hidden state chắc chắn hoạt động.
  - `bottom-right`/`bottom-left` hoạt động đúng.
  - Preserve flow `widgetKey/apiKey/frontendUrl` + color/welcome/icon.
- Chạy:
  - `npm run lint`
  - `npm run build`
  - `npm run build:widget`

## 3. Hiện trạng trước khi sửa

- Widget shell phụ thuộc style từ `widget.css`.
- Build widget tạo JS + CSS tách riêng (`dist-widget/chatbot-widget.iife.js` + `dist-widget/widget.css`).
- Nếu website chỉ load JS bundle, CSS shell có thể không áp dụng:
  - launcher/icon phóng lớn
  - fixed positioning không ổn định
  - hidden state phụ thuộc class cũ có thể không hoạt động đúng

## 4. Nguyên nhân gốc xác nhận từ source

- `vite.widget.config.js` cho output CSS tách file riêng.
- Runtime `widget.js` trước fix không tự inject style shell bắt buộc.
- Hidden state trước đó dùng class `hidden` theo style cũ, chưa self-contained.
- Do đó khi CSS tách không được load ở host page, shell layout/sizing/position vỡ.

## 5. Chiến lược sửa đã chọn

- Sửa tối thiểu trong `Frontend/widget/widget.js`:
  - Inject style runtime 1 lần qua `style#rag-chatbot-widget-styles`.
  - Chuẩn hóa class hidden riêng `.rag-chatbot-hidden`.
  - Ràng buộc bubble/frame size/fixed/z-index/sizing responsive.
- Không sửa backend, không đổi API contract, không refactor module khác.

## 6. Danh sách file đã đọc

- `.cursor/rules/00-core-working-rule.mdc`: xác nhận minimal diff/scope control.
- `.cursor/rules/10-backend-rag-rule.mdc`: xác nhận không backend.
- `.cursor/rules/20-frontend-widget-rule.mdc`: xác nhận checklist widget/frontend.
- `.cursor/rules/30-deploy-env-ops-rule.mdc`: nguyên tắc env/verify.
- `.cursor/rules/40-db-vector-rule.mdc`: xác nhận không liên quan DB/vector.
- `.cursor/rules/90-report-verification-rule.mdc`: yêu cầu report bắt buộc.
- `reports/CURSOR_REPORT_12A_WIDGET_RUNTIME_CONFIG_CONSUMPTION.md`: baseline runtime behavior.
- `reports/CURSOR_REPORT_12B_WIDGET_RUNTIME_SMOKE_TEST_AND_MINOR_FIXES.md`: baseline smoke scope.
- `reports/CURSOR_REPORT_12C_WIDGET_RUNTIME_VISUAL_VERIFICATION_ONLY.md`: baseline visual blocker + bug symptom.
- `Frontend/widget/widget.js`: file chính cần fix shell runtime.
- `Frontend/widget/widget.css`: xác nhận style shell hiện có nhưng phụ thuộc file CSS riêng.
- `Frontend/src/pages/WidgetChatPage.jsx`: xác nhận preserve color/welcome consumption.
- `Frontend/vite.widget.config.js`: xác nhận output CSS tách file.
- `Frontend/package.json`: xác nhận scripts lint/build/build:widget.
- `docs/2026-05-07-widget-runtime-smoke-harness.html`: xác nhận harness path/scripts.

## 7. Danh sách file đã sửa

- `Frontend/widget/widget.js`
  - Sửa để làm gì: self-contained CSS delivery + fix shell sizing/position/hidden state.
  - Ảnh hưởng lớp nào: widget.
- `reports/CURSOR_REPORT_12D_FIX_WIDGET_SHELL_CSS_POSITION_AND_LAUNCHER_SIZE.md`
  - Sửa để làm gì: report chính theo yêu cầu prompt.
  - Ảnh hưởng lớp nào: docs.
- `docs/2026-05-07-fix-widget-shell-css-position-and-launcher-size.md`
  - Sửa để làm gì: report docs bắt buộc theo rule 90.
  - Ảnh hưởng lớp nào: docs.

## 8. Diff thay đổi của từng file

### File: `Frontend/widget/widget.js`

- Hiện trạng cũ liên quan bug:
  - Phụ thuộc CSS ngoài, hidden class cũ, shell có thể mất fixed sizing khi CSS không nạp.
- Đã sửa gì:
  - Inject CSS runtime qua `injectWidgetStyles()`.
  - Đổi hidden state sang `.rag-chatbot-hidden`.
  - Ràng buộc bubble `56x56`, SVG `24x24`, frame `360x520` + responsive bounds.
  - Giữ nguyên logic config consumption.
- Vì sao sửa như vậy:
  - Đảm bảo JS bundle tự đủ shell style, không cần host page thêm CSS.
- Ảnh hưởng sau sửa:
  - Giảm nguy cơ launcher phóng lớn và position sai do thiếu CSS.

```diff
+ injectWidgetStyles();
- frame.classList.add("hidden");
+ frame.classList.add("rag-chatbot-hidden");
+ .rag-chatbot-hidden { display: none !important; }
+ #rag-chatbot-bubble { position: fixed; width:56px; height:56px; ... }
+ #rag-chatbot-frame { position: fixed; width:min(360px,...); height:min(520px,...); ... }
```

### File: `reports/CURSOR_REPORT_12D_FIX_WIDGET_SHELL_CSS_POSITION_AND_LAUNCHER_SIZE.md`

- Hiện trạng cũ liên quan bug: chưa có report 12D.
- Đã sửa gì: tạo report theo format yêu cầu prompt.
- Vì sao sửa như vậy: đáp ứng output bắt buộc.
- Ảnh hưởng sau sửa: reviewer có audit rõ root cause/fix.

```diff
+ # Cursor Report 12D - Fix Widget Shell CSS Position and Launcher Size
+ ...
```

### File: `docs/2026-05-07-fix-widget-shell-css-position-and-launcher-size.md`

- Hiện trạng cũ liên quan bug: chưa có report docs theo rule workspace.
- Đã sửa gì: tạo report docs đầy đủ 13 mục bắt buộc.
- Vì sao sửa như vậy: tuân thủ rule 90.
- Ảnh hưởng sau sửa: quy trình kiểm chứng/report nhất quán.

```diff
+ # Fix Widget Shell CSS Position and Launcher Size (12D)
+ ...
```

## 9. Ảnh hưởng sau sửa

- Behavior thay đổi:
  - Widget shell có CSS tự inject khi load JS.
  - Hidden state dùng class riêng chắc chắn (`rag-chatbot-hidden`).
  - Bubble/frame có fixed sizing/position/z-index ổn định hơn.
- Behavior giữ nguyên:
  - `widgetColor`, `welcomeMessage`, `launcherIcon`, `widgetKey/apiKey`, `frontendUrl` flow giữ nguyên.
- Điều kiện bật:
  - Style inject chạy 1 lần nếu chưa có style id.
- Fallback giữ:
  - Fallback position/color/icon/welcome như 12A.
- Ảnh hưởng memory/cpu/disk:
  - Overhead rất nhỏ (1 style tag + CSS text ngắn).
- Ảnh hưởng latency/token/API cost:
  - Không có.
- Ảnh hưởng dữ liệu MySQL/Qdrant:
  - Không có.

## 10. Edge cases đã xem xét

- Host page không load CSS file ngoài -> shell vẫn có style do runtime inject.
- Position `bottom-left`/`bottom-right` áp dụng cho cả bubble/frame.
- Invalid config fallback vẫn giữ (`position`, `widgetColor`, `launcherIcon`, `welcomeMessage`).
- Không ảnh hưởng flow thiếu `widgetKey/apiKey` (vẫn cảnh báo như cũ trong iframe page).

## 11. Kết quả kiểm tra

| Command | Kết quả | Ghi chú |
|---|---|---|
| `cd Backend && ./mvnw -DskipTests compile` | NOT RUN | Task frontend/widget bugfix |
| `cd Backend && ./mvnw test` | NOT RUN | Task frontend/widget bugfix |
| `cd Frontend && npm run lint` | PASS | Không lỗi lint mới |
| `cd Frontend && npm run build` | PASS | Build pass; warning chunk-size >500k là pre-existing |
| `cd Frontend && npm run build:widget` | PASS | Widget build pass sau fix |
| `docker compose config` | NOT RUN | Không thay đổi deploy/infra |

## 12. Rủi ro còn lại

- Chưa có visual verification trực tiếp trong phiên tool để chốt PASS/FAIL các case retest bắt buộc.
- Limitation cũ vẫn còn:
  - `apiKey` snippet là placeholder.
  - `allowedOrigins` không enforce client-side (server-side concern).

## 13. Đề xuất tiếp theo

- Chạy lại harness manual cho các case 1,2,5,6,7,8 để chốt visual PASS.
- Nếu còn FAIL thật trong visual retest, tạo prompt bugfix nhỏ follow-up đúng case còn lỗi.
