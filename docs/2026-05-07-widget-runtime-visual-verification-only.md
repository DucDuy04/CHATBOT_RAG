# Widget Runtime Visual Verification Only (12C)

## 1. Mức độ hiểu task

- Hiểu task: 100%.
- Phần chắc chắn:
  - Chỉ verify visual/manual 8 case runtime widget.
  - Không thêm feature, không refactor, không backend change.
  - Chỉ fix bug nhỏ nếu thấy lỗi thật.
- Phần còn giả định:
  - Không có browser automation trực tiếp trong môi trường tool.
- Thiếu dữ kiện:
  - Không có kênh capture screenshot/DOM visual tự động từ browser thật trong phiên này.

## 2. Tóm tắt yêu cầu

- Đọc rules + report 12A, 12B + source bắt buộc.
- Setup môi trường:
  - `build:widget`
  - `dev` ở `http://localhost:5173`
- Verify harness/script path đúng.
- Chạy 8 case visual:
  1) bottom-right
  2) bottom-left
  3) custom color
  4) Vietnamese welcome
  5) icon help
  6) icon spark
  7) invalid fallback
  8) missing optional fields
- Nếu không verify visual được, đánh dấu `BLOCKED` trung thực.

## 3. Hiện trạng trước khi sửa

- Report 12B ghi 8 case đều `NOT RUN`.
- Harness có default URL `http://localhost:5173/chatbot-widget.js`.
- Kiểm tra runtime cho thấy output bundle chuẩn là `/dist-widget/chatbot-widget.iife.js`.

## 4. Nguyên nhân gốc xác nhận từ source

- Root cause nhỏ trong harness:
  - Default script URL có thể trả HTML fallback thay vì runtime JS bundle.
- Không phát hiện bug runtime source mới từ static inspection.

## 5. Chiến lược sửa đã chọn

- Sửa tối thiểu và đúng phạm vi:
  - Chỉ chỉnh default script URL trong harness.
  - Không chạm runtime source (`widget.js`, `WidgetChatPage.jsx`) vì chưa có bug visual đã xác nhận.
- Chuẩn bị đầy đủ để user/manual tester chạy visual thật.

## 6. Danh sách file đã đọc

- `.cursor/rules/00-core-working-rule.mdc`: xác nhận quy trình source-first/minimal diff.
- `.cursor/rules/10-backend-rag-rule.mdc`: xác nhận không sửa backend.
- `.cursor/rules/20-frontend-widget-rule.mdc`: checklist widget/frontend scope.
- `.cursor/rules/30-deploy-env-ops-rule.mdc`: nguyên tắc verify môi trường.
- `.cursor/rules/40-db-vector-rule.mdc`: xác nhận không liên quan DB/vector.
- `.cursor/rules/90-report-verification-rule.mdc`: yêu cầu report docs + trung thực trạng thái test.
- `reports/CURSOR_REPORT_12A_WIDGET_RUNTIME_CONFIG_CONSUMPTION.md`: baseline runtime behavior sau 12A.
- `reports/CURSOR_REPORT_12B_WIDGET_RUNTIME_SMOKE_TEST_AND_MINOR_FIXES.md`: baseline smoke status và harness.
- `docs/2026-05-07-widget-runtime-smoke-harness.html`: file harness cần chạy visual test.
- `Frontend/widget/widget.js`: xác nhận parse config, fallback, query param truyền iframe.
- `Frontend/src/pages/WidgetChatPage.jsx`: xác nhận consume color/welcome fallback.
- `Frontend/vite.config.js`: xác nhận serve `/dist-widget/*` khi dev.
- `Frontend/vite.widget.config.js`: xác nhận output widget bundle.
- `Frontend/package.json`: xác nhận scripts `build:widget`, `dev`.

## 7. Danh sách file đã sửa

- `docs/2026-05-07-widget-runtime-smoke-harness.html`
  - Sửa để làm gì: chỉnh default script URL sang đường dẫn JS runtime đúng.
  - Ảnh hưởng lớp nào: docs.
- `reports/CURSOR_REPORT_12C_WIDGET_RUNTIME_VISUAL_VERIFICATION_ONLY.md`
  - Sửa để làm gì: report chính theo yêu cầu 12C.
  - Ảnh hưởng lớp nào: docs.
- `docs/2026-05-07-widget-runtime-visual-verification-only.md`
  - Sửa để làm gì: report docs bắt buộc theo rule 90.
  - Ảnh hưởng lớp nào: docs.

## 8. Diff thay đổi của từng file

### File: `docs/2026-05-07-widget-runtime-smoke-harness.html`

- Hiện trạng cũ liên quan bug:
  - Input URL mặc định là `http://localhost:5173/chatbot-widget.js`.
- Đã sửa gì:
  - Đổi default thành `http://localhost:5173/dist-widget/chatbot-widget.iife.js`.
- Vì sao sửa như vậy:
  - Đảm bảo harness nạp đúng JS bundle runtime.
- Ảnh hưởng sau sửa:
  - Manual tester có thể chạy smoke test chính xác hơn mà không phải chỉnh URL thủ công.

```diff
- value="http://localhost:5173/chatbot-widget.js"
+ value="http://localhost:5173/dist-widget/chatbot-widget.iife.js"
```

### File: `reports/CURSOR_REPORT_12C_WIDGET_RUNTIME_VISUAL_VERIFICATION_ONLY.md`

- Hiện trạng cũ liên quan bug: chưa có report 12C.
- Đã sửa gì: thêm report theo template prompt 12C.
- Vì sao sửa như vậy: đáp ứng output bắt buộc.
- Ảnh hưởng sau sửa: reviewer có kết quả setup + trạng thái visual verification.

```diff
+ # Cursor Report 12C - Widget Runtime Visual Verification Only
+ ...
```

### File: `docs/2026-05-07-widget-runtime-visual-verification-only.md`

- Hiện trạng cũ liên quan bug: chưa có report docs cho task 12C.
- Đã sửa gì: thêm report docs đầy đủ 13 mục bắt buộc.
- Vì sao sửa như vậy: tuân thủ workspace rule 90.
- Ảnh hưởng sau sửa: quy trình audit/report nhất quán.

```diff
+ # Widget Runtime Visual Verification Only (12C)
+ ...
```

## 9. Ảnh hưởng sau sửa

- Behavior thay đổi:
  - Harness default script URL được chỉnh đúng runtime bundle path.
- Behavior giữ nguyên:
  - Runtime source (`widget.js`, `WidgetChatPage.jsx`) không đổi.
  - API/state/route không đổi.
- Điều kiện bật:
  - Chỉ có tác dụng khi mở harness để manual visual test.
- Fallback giữ:
  - Fallback runtime cũ (position/color/welcome/icon) giữ nguyên.
- Ảnh hưởng memory/cpu/disk:
  - Không ảnh hưởng runtime app; chỉ chỉnh file docs tĩnh.
- Ảnh hưởng latency/token/API cost:
  - Không có.
- Ảnh hưởng dữ liệu MySQL/Qdrant:
  - Không có.

## 10. Edge cases đã xem xét

- Script URL trả HTML thay vì JS bundle.
- Visual case không thể xác nhận nếu không có browser/preview control.
- Case invalid config cần confirm thêm qua browser console để đảm bảo không throw error.

## 11. Kết quả kiểm tra

| Command | Kết quả | Ghi chú |
|---|---|---|
| `cd Backend && ./mvnw -DskipTests compile` | NOT RUN | Task frontend visual verification |
| `cd Backend && ./mvnw test` | NOT RUN | Task frontend visual verification |
| `cd Frontend && npm run build:widget` | PASS | Build widget thành công |
| `cd Frontend && npm run dev -- --host 0.0.0.0 --port 5173` | PASS | Dev server start; có `http://localhost:5173` |
| `HEAD http://localhost:5173/chatbot-widget.js` | PASS | 200 nhưng trả HTML |
| `HEAD http://localhost:5173/dist-widget/chatbot-widget.iife.js` | PASS | 200, đúng JS runtime |
| `cd Frontend && npm run lint` | NOT RUN | Không sửa runtime source |
| `cd Frontend && npm run build` | NOT RUN | Không sửa runtime source |
| `docker compose config` | NOT RUN | Không thay đổi deploy/infra |

## 12. Rủi ro còn lại

- 8 case visual hiện `BLOCKED` vì không có browser visual verification trực tiếp trong phiên tool.
- Cần manual browser run để kết luận PASS/FAIL thực tế.
- Limitation cũ vẫn giữ:
  - `apiKey` snippet placeholder.
  - `allowedOrigins` không enforce client-side (server-side concern).

## 13. Đề xuất tiếp theo

- Mở `docs/2026-05-07-widget-runtime-smoke-harness.html` trong browser thật và chạy 8 case.
- Nếu case nào FAIL, tạo prompt bugfix nhỏ follow-up đúng điểm lỗi.
- Sau khi manual visual pass đầy đủ, tạo report closure cập nhật trạng thái 8 case sang PASS.
