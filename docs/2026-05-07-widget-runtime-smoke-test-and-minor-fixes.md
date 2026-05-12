# Widget Runtime Smoke Test and Minor Fixes (12B)

## 1. Mức độ hiểu task

- Hiểu task: 100%.
- Phần chắc chắn:
  - Scope là smoke test widget runtime + minor fixes nếu có bug nhỏ.
  - Không backend, không API contract change.
  - Bắt buộc run lint/build/build:widget.
- Phần còn giả định:
  - Không có visual browser verification trực tiếp trong phiên chạy này.
- Thiếu dữ kiện:
  - Không có automation browser runner trong scope prompt hiện tại.

## 2. Tóm tắt yêu cầu

- Đọc rules + reports + source files bắt buộc.
- Tạo test harness nếu cần.
- Thực hiện smoke test 8 case:
  - bottom-right
  - bottom-left
  - custom color
  - custom welcome message
  - launcher icon help
  - launcher icon spark
  - invalid config fallback
  - missing optional fields fallback
- Chỉ fix bug nhỏ nếu phát hiện.
- Chạy:
  - `cd Frontend && npm run lint`
  - `cd Frontend && npm run build`
  - `cd Frontend && npm run build:widget`

## 3. Hiện trạng trước khi sửa

- Prompt 12A đã cập nhật runtime consume các field:
  - `position`
  - `widgetColor`
  - `welcomeMessage`
  - `launcherIcon`
- Tuy nhiên 12A chưa có manual visual verification thực tế cho các case smoke test.

## 4. Nguyên nhân gốc xác nhận từ source

- Không phát hiện bug mới qua static inspection.
- Khoảng trống chính là thiếu visual verification runbook chuẩn cho 8 case.

## 5. Chiến lược sửa đã chọn

- Không sửa source runtime vì chưa có bug thực tế trong scope static inspection.
- Thêm 1 harness docs độc lập để chạy thủ công 8 case có cấu hình rõ ràng.
- Giữ minimal diff, không ảnh hưởng runtime app.

## 6. Danh sách file đã đọc

- `.cursor/rules/00-core-working-rule.mdc`: kiểm tra nguyên tắc source-first/minimal diff.
- `.cursor/rules/10-backend-rag-rule.mdc`: xác nhận không cần backend.
- `.cursor/rules/20-frontend-widget-rule.mdc`: checklist widget/frontend.
- `.cursor/rules/30-deploy-env-ops-rule.mdc`: quy tắc claim verify production.
- `.cursor/rules/40-db-vector-rule.mdc`: xác nhận không liên quan DB/vector.
- `.cursor/rules/90-report-verification-rule.mdc`: yêu cầu report docs bắt buộc.
- `reports/CURSOR_REPORT_06_CHATBOT_EMBED.md`: baseline embed config UI và limitation cũ.
- `reports/CURSOR_REPORT_11_INTEGRATION_QA_CHECKLIST_AUDIT.md`: baseline audit/limitations.
- `reports/CURSOR_REPORT_12A_WIDGET_RUNTIME_CONFIG_CONSUMPTION.md`: baseline thay đổi runtime mới nhất.
- `Frontend/widget/widget.js`: kiểm tra parse config/fallback/query params.
- `Frontend/widget/widget.css`: kiểm tra style fixed position mặc định.
- `Frontend/src/pages/WidgetChatPage.jsx`: kiểm tra consume `widgetColor`/`welcomeMessage`.
- `Frontend/src/pages/chatbots/ChatbotEmbedPage.jsx`: kiểm tra snippet có `launcherIcon`.
- `Frontend/src/pages/chatbots/components/WidgetLivePreview.jsx`: kiểm tra preview sync behavior.
- `Frontend/vite.widget.config.js`: kiểm tra build widget output.
- `Frontend/package.json`: kiểm tra scripts validation.

## 7. Danh sách file đã sửa

- `docs/2026-05-07-widget-runtime-smoke-harness.html`
  - Sửa để làm gì: thêm harness smoke test 8 case runtime config.
  - Ảnh hưởng lớp nào: docs.
- `reports/CURSOR_REPORT_12B_WIDGET_RUNTIME_SMOKE_TEST_AND_MINOR_FIXES.md`
  - Sửa để làm gì: report chính theo template prompt 12B.
  - Ảnh hưởng lớp nào: docs.
- `docs/2026-05-07-widget-runtime-smoke-test-and-minor-fixes.md`
  - Sửa để làm gì: report docs theo workspace rule 90.
  - Ảnh hưởng lớp nào: docs.

## 8. Diff thay đổi của từng file

### File: `docs/2026-05-07-widget-runtime-smoke-harness.html`

- Hiện trạng cũ liên quan bug: chưa có harness smoke test runtime widget theo 8 case.
- Đã sửa gì:
  - Thêm HTML harness có dropdown 8 case.
  - Thêm logic set `window.RagChatbotConfig`.
  - Thêm logic inject/remove widget script để test lặp.
- Vì sao sửa như vậy:
  - Chuẩn hóa smoke test thủ công cho runtime behavior.
- Ảnh hưởng sau sửa:
  - Có thể mở file để visual verify trực tiếp từng case.

```diff
+ const CASES = [ ...8 cấu hình smoke test... ];
+ window.RagChatbotConfig = { ...selected.config };
+ script.src = "http://localhost:5173/chatbot-widget.js";
+ removeWidgetDom();
```

### File: `reports/CURSOR_REPORT_12B_WIDGET_RUNTIME_SMOKE_TEST_AND_MINOR_FIXES.md`

- Hiện trạng cũ liên quan bug: chưa có report 12B.
- Đã sửa gì: thêm report 12B theo cấu trúc prompt.
- Vì sao sửa như vậy: đáp ứng yêu cầu deliverable của task.
- Ảnh hưởng sau sửa: reviewer có tài liệu audit smoke test + validation rõ ràng.

```diff
+ # Cursor Report 12B - Widget Runtime Smoke Test and Minor Fixes
+ ...
```

### File: `docs/2026-05-07-widget-runtime-smoke-test-and-minor-fixes.md`

- Hiện trạng cũ liên quan bug: chưa có report docs theo rule 90 cho task 12B.
- Đã sửa gì: thêm report docs đầy đủ 13 mục bắt buộc.
- Vì sao sửa như vậy: tuân thủ workspace rule.
- Ảnh hưởng sau sửa: quy trình audit/report nhất quán.

```diff
+ # Widget Runtime Smoke Test and Minor Fixes (12B)
+ ...
```

## 9. Ảnh hưởng sau sửa

- Behavior thay đổi:
  - Không thay đổi behavior runtime/widget source.
  - Bổ sung công cụ smoke test thủ công (harness).
- Behavior giữ nguyên:
  - Runtime logic 12A giữ nguyên.
  - API/state/route không đổi.
- Điều gì chỉ bật khi đủ điều kiện:
  - Harness chỉ hoạt động khi mở file trong browser và script URL khả dụng.
- Fallback giữ:
  - Fallback runtime (`position/color/welcome/icon`) không thay đổi.
- Ảnh hưởng memory/cpu/disk:
  - Không ảnh hưởng runtime production.
  - Chỉ thêm 1 file docs tĩnh.
- Ảnh hưởng latency/token/API cost:
  - Không có.
- Ảnh hưởng dữ liệu MySQL/Qdrant:
  - Không có.

## 10. Edge cases đã xem xét

- `position` invalid -> fallback `bottom-right`.
- `widgetColor` invalid -> fallback `#2563eb`.
- `launcherIcon` invalid -> fallback `chat`.
- `welcomeMessage` rỗng -> fallback message mặc định.
- Thiếu optional config fields -> widget vẫn render.
- Thiếu browser visual environment -> đánh dấu `NOT RUN`, không claim PASS.

## 11. Kết quả kiểm tra

| Command | Kết quả | Ghi chú |
|---|---|---|
| `cd Backend && ./mvnw -DskipTests compile` | NOT RUN | Task frontend/widget smoke test |
| `cd Backend && ./mvnw test` | NOT RUN | Task frontend/widget smoke test |
| `cd Frontend && npm run lint` | PASS | Không có lỗi lint |
| `cd Frontend && npm run build` | PASS | Build pass; warning chunk-size >500k là pre-existing |
| `cd Frontend && npm run build:widget` | PASS | Build widget pass |
| `docker compose config` | NOT RUN | Không sửa deploy/infra |

## 12. Rủi ro còn lại

- 8 case visual hiện `NOT RUN` trong phiên này vì chưa có verify trực tiếp trên browser.
- `apiKey` trong snippet vẫn placeholder.
- `allowedOrigins` vẫn là server-side concern, không enforce client-side.

## 13. Đề xuất tiếp theo

- Mở `docs/2026-05-07-widget-runtime-smoke-harness.html` trong browser để chốt PASS/FAIL visual cho 8 case.
- Nếu visual test phát hiện bug nhỏ, tách prompt follow-up fix đúng điểm lỗi.
- Khi backend có source key thật, thay placeholder `apiKey` trong snippet.
