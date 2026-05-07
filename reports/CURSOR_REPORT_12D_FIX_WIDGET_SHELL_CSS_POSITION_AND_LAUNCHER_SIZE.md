# Cursor Report 12D - Fix Widget Shell CSS Position and Launcher Size

## 1. Mức độ hiểu task
- Task là gì?
  - Sửa bug shell/runtime widget gây icon/bubble phóng lớn và position sai (đặc biệt bottom-right), không thêm feature mới.
- Hiểu task: 100%
- Phần chắc chắn:
  - Bug thuộc lớp shell CSS delivery + hidden/position behavior.
  - Scope fix nằm ở `Frontend/widget/widget.js` (và giữ nguyên flow key/color/welcome/icon đã có).
- Phần còn giả định:
  - Visual retest trực tiếp trong browser không thực hiện được hoàn toàn trong môi trường tool hiện tại.
- Phạm vi không làm:
  - Không backend, không API contract change, không refactor module lớn.

## 2. Files/rules/reports đã đọc

| File | Mục đích đọc | Kết luận chính |
|---|---|---|
| `.cursor/rules/00-core-working-rule.mdc` | Xác nhận minimal diff/scope | Chỉ fix đúng shell bug |
| `.cursor/rules/10-backend-rag-rule.mdc` | Xác nhận backend scope | Không sửa backend |
| `.cursor/rules/20-frontend-widget-rule.mdc` | Rule frontend/widget | Ưu tiên fix widget runtime/build |
| `.cursor/rules/30-deploy-env-ops-rule.mdc` | Rule env/deploy | Không claim production verify |
| `.cursor/rules/40-db-vector-rule.mdc` | Rule db/vector | Không liên quan |
| `.cursor/rules/90-report-verification-rule.mdc` | Rule report trung thực | Ghi rõ PASS/FAIL/NOT RUN |
| `reports/CURSOR_REPORT_12A_WIDGET_RUNTIME_CONFIG_CONSUMPTION.md` | Baseline runtime config consumption | Đã truyền color/welcome/icon/position qua runtime |
| `reports/CURSOR_REPORT_12B_WIDGET_RUNTIME_SMOKE_TEST_AND_MINOR_FIXES.md` | Baseline smoke setup | Có harness và các case test |
| `reports/CURSOR_REPORT_12C_WIDGET_RUNTIME_VISUAL_VERIFICATION_ONLY.md` | Baseline visual blocked + bug symptoms | Xác nhận vấn đề shell xuất hiện trong visual test |
| `Frontend/widget/widget.js` | Audit logic shell runtime | Có dependency vào CSS class/layout |
| `Frontend/widget/widget.css` | Audit style shell | CSS tồn tại nhưng bundle tách file riêng |
| `Frontend/src/pages/WidgetChatPage.jsx` | Kiểm tra preserve behavior | Đang consume `widgetColor`/`welcomeMessage` |
| `Frontend/vite.widget.config.js` | Audit widget build output | CSS output riêng `dist-widget/widget.css` |
| `Frontend/package.json` | Kiểm tra lệnh validate | Có lint/build/build:widget |
| `docs/2026-05-07-widget-runtime-smoke-harness.html` | Kiểm tra harness path/cases | Script path đang đúng tới `dist-widget/chatbot-widget.iife.js` |

## 3. Root cause
- CSS có được load/bundle không?
  - Có bundle, nhưng ở dạng file CSS tách riêng (`dist-widget/widget.css`) thay vì auto-inject vào JS runtime. Khi host chỉ load JS widget, shell CSS có thể không áp dụng.
- Vì sao launcher phóng lớn?
  - Khi CSS shell không được áp dụng, bubble/SVG không còn constraint kích thước (`56px`/`24px`) và mất fixed layout context, dẫn tới hiện tượng launcher/icon render bất thường (phóng lớn theo host context).
- Vì sao bottom-right không hoạt động?
  - `applyPositionStyles` có set `left/right`, nhưng khi CSS shell không hiện diện thì các thuộc tính fixed/size/hidden không ổn định; cộng thêm hidden state cũ dùng class `hidden` phụ thuộc style cũ không self-contained.

## 4. Files changed

| File | Change | Reason | Risk |
|---|---|---|---|
| `Frontend/widget/widget.js` | Inject style self-contained vào `<style id="rag-chatbot-widget-styles">`, đổi hidden class sang `.rag-chatbot-hidden`, chuẩn hóa bubble/frame sizing + fixed + z-index + responsive bounds | Đảm bảo chỉ cần load JS widget vẫn có shell CSS đúng; fix phóng lớn + position + hidden state | Low-Medium (thay đổi shell runtime behavior, nhưng phạm vi hẹp) |

## 5. Runtime behavior after fix
- Bubble size:
  - Cố định `56x56`, SVG `24x24`, không còn phụ thuộc CSS host page.
- Frame size:
  - `360x520` với giới hạn responsive bằng `min(..., calc(...))` để không vỡ màn nhỏ.
- Hidden state:
  - Dùng `.rag-chatbot-hidden { display: none !important; }` self-contained.
- Bottom-right:
  - `right: 24px`, `left: auto` áp dụng cho cả bubble và frame.
- Bottom-left:
  - `left: 24px`, `right: auto` áp dụng cho cả bubble và frame.
- Color/welcome/icon preservation:
  - Giữ nguyên logic `widgetColor`, `welcomeMessage`, `launcherIcon`, `widgetKey/apiKey`, `frontendUrl`.

## 6. Validation results

| Command | Result | Notes |
|---|---|---|
| `cd Frontend && npm run lint` | PASS | Không có lint error mới |
| `cd Frontend && npm run build` | PASS | Build pass; warning chunk-size > 500k là pre-existing |
| `cd Frontend && npm run build:widget` | PASS | Widget build pass sau fix shell |

## 7. Manual retest results

| Case | Expected | Actual | Status |
|---|---|---|---|
| Case 1 bottom-right | Bubble/frame ở phải dưới, launcher không phóng lớn | Chưa quan sát visual trực tiếp trong browser ở phiên tool | NOT RUN |
| Case 2 bottom-left | Bubble/frame ở trái dưới, launcher size đúng | Chưa quan sát visual trực tiếp trong browser ở phiên tool | NOT RUN |
| Case 5 help | Icon help đúng + launcher size đúng | Chưa quan sát visual trực tiếp trong browser ở phiên tool | NOT RUN |
| Case 6 spark | Icon spark đúng + launcher size đúng | Chưa quan sát visual trực tiếp trong browser ở phiên tool | NOT RUN |
| Case 7 invalid fallback | Fallback đúng + không throw console error | Chưa mở console browser thực tế trong phiên tool | NOT RUN |
| Case 8 missing optional | Widget render + fallback + không throw error | Chưa mở browser thực tế trong phiên tool | NOT RUN |

## 8. Known limitations remaining
- `apiKey` trong snippet vẫn là placeholder (`YOUR_PUBLIC_API_KEY`) như các prompt trước.
- `allowedOrigins` vẫn là server-side/security concern, không enforce client-side.
- Manual visual retest cho 6 case trọng tâm ở trên chưa verify trực tiếp trong browser ở phiên này (đánh dấu `NOT RUN` trung thực).
