# Final Manual Smoke Test and Release Handoff (13)

## 1. Mức độ hiểu task
- Hiểu task: 100%.
- Phần chắc chắn: chạy validation commands frontend, rà soát toàn checklist smoke test và chỉ fix bug nhỏ/blocker nếu phát hiện.
- Phần còn giả định: phiên tool hiện tại không có thao tác browser tương tác trực tiếp để chạy manual click-by-click.
- Thiếu dữ kiện: không có bằng chứng runtime UI thao tác tay trong phiên này.

## 2. Tóm tắt yêu cầu
- Đọc toàn bộ `.cursor/rules`.
- Đọc report nền `11`, `12A`, `12D` và harness widget.
- Đọc source frontend chính theo scope.
- Chạy `npm run lint`, `npm run build`, `npm run build:widget`.
- Thực hiện smoke test cuối toàn app; chỉ sửa bug nhỏ nếu phát hiện.
- Tạo report bàn giao final.

## 3. Hiện trạng trước khi sửa
- Working tree có nhiều file build artifact và docs/report đã tồn tại từ các task trước.
- Frontend đã có đầy đủ route/page/API wiring cho checklist.
- Không có yêu cầu feature mới; scope là final smoke + release handoff.

## 4. Nguyên nhân gốc xác nhận từ source
- Không phát hiện bug blocker mới từ source audit trong phạm vi task.
- Trạng thái `manual smoke` bị giới hạn bởi thiếu môi trường browser tương tác trực tiếp trong phiên này, nên không thể claim PASS thủ công một cách trung thực.

## 5. Chiến lược sửa đã chọn
- Không sửa source khi chưa có bằng chứng bug runtime cụ thể.
- Chạy đủ validation commands để xác nhận baseline build health.
- Rà source theo checklist để xác nhận wiring và điểm cần QA thực địa.
- Ghi trung thực các hạng mục manual là `BLOCKED`.

## 6. Danh sách file đã đọc
- `.cursor/rules/00-core-working-rule.mdc`: xác nhận nguyên tắc source-first, minimal diff.
- `.cursor/rules/10-backend-rag-rule.mdc`: xác nhận task không sửa backend.
- `.cursor/rules/20-frontend-widget-rule.mdc`: checklist frontend/widget cần đối chiếu.
- `.cursor/rules/30-deploy-env-ops-rule.mdc`: nguyên tắc claim verification trung thực.
- `.cursor/rules/40-db-vector-rule.mdc`: xác nhận không scope DB/vector.
- `.cursor/rules/90-report-verification-rule.mdc`: format report/final message bắt buộc.
- `agent.md`: điều hướng tài liệu kiến trúc.
- `agent/01-overview.md`: tổng quan luồng hệ thống.
- `agent/02-architecture.md`: kiến trúc FE/BE và flow runtime.
- `reports/CURSOR_REPORT_11_INTEGRATION_QA_CHECKLIST_AUDIT.md`: baseline checklist audit.
- `reports/CURSOR_REPORT_12A_WIDGET_RUNTIME_CONFIG_CONSUMPTION.md`: baseline widget config runtime.
- `reports/CURSOR_REPORT_12D_FIX_WIDGET_SHELL_CSS_POSITION_AND_LAUNCHER_SIZE.md`: baseline widget shell/css fixes.
- `docs/2026-05-07-widget-runtime-smoke-harness.html`: harness 8 case widget runtime.
- `Frontend/src/App.jsx`: route map public/private + layout wrapper.
- `Frontend/src/pages/WidgetChatPage.jsx`: widget iframe runtime behavior.
- `Frontend/widget/widget.js`: widget embed runtime.
- `Frontend/src/pages/dashboard/DashboardPage.jsx`: dashboard flows.
- `Frontend/src/pages/chatbots/ChatbotsPage.jsx`: chatbot list/search/filter/create.
- `Frontend/src/pages/chatbots/ChatbotConfigPage.jsx`: config save/delete/tab flows.
- `Frontend/src/pages/chatbots/ChatbotEmbedPage.jsx`: embed config/snippet/preview.
- `Frontend/src/pages/documents/DocumentsPage.jsx`: upload/filter/chunks/assign/retry/delete/polling.
- `Frontend/src/pages/playground/PlaygroundPage.jsx`: streaming/sources/latency/compare/export.
- `Frontend/src/pages/analytics/AnalyticsPage.jsx`: usage/sessions/feedback tabs.
- `Frontend/src/pages/settings/SettingsPage.jsx`: settings sections.
- `Frontend/src/components/layout/AppLayout.jsx`: layout/sidebar/header shell.
- `Frontend/src/components/layout/Header.jsx`: title/rightSlot rendering.
- `Frontend/src/components/layout/Sidebar.jsx`: navigation.
- `Frontend/src/components/common/index.js`: common component exports.
- `Frontend/src/api/index.js`: api barrel.
- `Frontend/src/api/axiosInstance.js`: auth header + error mapping.
- `Frontend/src/api/dashboardApi.js`: dashboard contracts/mock.
- `Frontend/src/api/chatbotsApi.js`: chatbot/config/embed contracts/mock.
- `Frontend/src/api/documentsApi.js`: documents contracts/mock.
- `Frontend/src/api/playgroundApi.js`: streaming/compare/session contracts/mock.
- `Frontend/src/api/analyticsApi.js`: analytics usage/sessions/feedback contracts/mock.
- `Frontend/src/api/settingsApi.js`: settings contracts/mock.
- `Frontend/src/pages/LoginPage.jsx`: mock login flow.
- `Frontend/src/routes/PrivateRoute.jsx`: private route guard.
- `Frontend/src/stores/authStore.js`: auth token state.

## 7. Danh sách file đã sửa
- `reports/CURSOR_REPORT_13_FINAL_MANUAL_SMOKE_TEST_AND_RELEASE_HANDOFF.md`
  - Sửa để tạo report bàn giao theo yêu cầu user.
  - Ảnh hưởng lớp: docs.
- `docs/2026-05-07-final-manual-smoke-test-and-release-handoff.md`
  - Sửa để đáp ứng rule bắt buộc tạo report trong `docs/`.
  - Ảnh hưởng lớp: docs.

## 8. Diff thay đổi của từng file
### File: `reports/CURSOR_REPORT_13_FINAL_MANUAL_SMOKE_TEST_AND_RELEASE_HANDOFF.md`
- Hiện trạng cũ liên quan bug: chưa có report #13.
- Đã sửa gì: tạo mới report final smoke test + handoff.
- Vì sao sửa như vậy: theo yêu cầu task.
- Ảnh hưởng sau sửa: có tài liệu bàn giao final cho reviewer/QA.

```diff
+ # CURSOR REPORT 13 - FINAL MANUAL SMOKE TEST AND RELEASE HANDOFF
+ ...
+ ## 8. Release handoff notes
+ ...
```

### File: `docs/2026-05-07-final-manual-smoke-test-and-release-handoff.md`
- Hiện trạng cũ liên quan bug: chưa có report docs tương ứng task #13.
- Đã sửa gì: tạo mới report chuẩn rule 90 với 13 mục.
- Vì sao sửa như vậy: bắt buộc theo workspace rule.
- Ảnh hưởng sau sửa: đảm bảo compliance report verification.

```diff
+ # Final Manual Smoke Test and Release Handoff (13)
+ ...
+ ## 11. Kết quả kiểm tra
+ ...
```

## 9. Ảnh hưởng sau sửa
- Behavior thay đổi:
  - Không thay đổi behavior runtime/source frontend.
- Behavior giữ nguyên:
  - Toàn bộ route/API contract/widget runtime code giữ nguyên.
- Điều kiện chỉ bật khi đủ điều kiện:
  - Không có điều kiện behavior mới do không chỉnh source.
- Fallback giữ:
  - Fallback hiện có trong từng page/api vẫn giữ nguyên.
- Ảnh hưởng memory/cpu/disk:
  - Không đáng kể; chỉ thêm tài liệu markdown.
- Ảnh hưởng latency/token/API cost:
  - Không thay đổi.
- Ảnh hưởng MySQL/Qdrant:
  - Không thay đổi dữ liệu.

## 10. Edge cases đã xem xét
- Route public `/widget` không bị private/layout wrapper.
- Route private không có token sẽ redirect `/login`.
- Analytics invalid date range thì pause fetch.
- Documents polling cleanup interval khi hết `PROCESSING`.
- Playground streaming có abort cleanup khi clear/unmount.
- Widget runtime fallback khi config invalid/missing optional fields.

## 11. Kết quả kiểm tra
| Command | Kết quả | Ghi chú |
|---|---|---|
| `cd Backend && ./mvnw -DskipTests compile` | NOT RUN | Task frontend smoke/handoff, không sửa backend. |
| `cd Backend && ./mvnw test` | NOT RUN | Task frontend smoke/handoff, không sửa backend. |
| `cd Frontend && npm run lint` | PASS | ESLint pass. |
| `cd Frontend && npm run build` | PASS | Build pass, còn warning chunk-size >500k (pre-existing). |
| `cd Frontend && npm run build:widget` | PASS | Widget build pass. |
| `docker compose config` | NOT RUN | Không thay đổi deploy/compose trong scope task. |

### Manual smoke checklist tổng hợp
- Tất cả mục manual click-by-click trong A/B checklist: `BLOCKED` (không có browser interaction runtime trong phiên tool này).

## 12. Rủi ro còn lại
- Chưa có manual runtime PASS evidence trong phiên này cho toàn bộ checklist.
- Feedback read endpoint analytics vẫn phụ thuộc mock/local path.
- Playground restore session production vẫn phụ thuộc endpoint thực.
- Embed snippet vẫn dùng placeholder `apiKey` nếu backend chưa cấp key public thật.
- Settings notification contract cần QA backend thật.

## 13. Đề xuất tiếp theo
- Chạy manual QA trực tiếp browser theo đúng checklist Area 1..10.
- Ưu tiên verify trên backend thật (không mock) cho Analytics/Playground/Documents/Settings.
- Chạy lại harness widget 8 case và chụp bằng chứng PASS trước release staging.
- Nếu phát hiện blocker runtime, mở prompt fix nhỏ riêng và rerun 3 validation commands.
