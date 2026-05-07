# Fix Analytics Max Update Depth Loop (14A)

## 1. Mức độ hiểu task
- Hiểu task: 100%.
- Chắc chắn: fix hẹp bug loop tại `/analytics`, không thêm feature, không đổi backend/API contract.
- Còn giả định: line number stack có thể chênh nhẹ theo build map, nhưng root cause trùng vùng effect/callback analytics.
- Thiếu dữ kiện: không có recording runtime từ browser trong phiên tool.

## 2. Tóm tắt yêu cầu
- Điều tra chính xác loop source trong `AnalyticsPage.jsx` và `LayoutContext.jsx`.
- Fix ưu tiên trong `AnalyticsPage.jsx`.
- Đảm bảo không refactor lớn.
- Giữ nguyên behavior chức năng analytics hiện có.

## 3. Hiện trạng trước khi sửa
- `/analytics` gặp lỗi `Maximum update depth exceeded`.
- Stack trace user report trỏ:
  - `AnalyticsPage.jsx` quanh `setSummaryState` trong fetch usage.
  - `LayoutContext.jsx` dòng `clearRightSlot -> setRightSlotState(null)`.
- UI loading/skeleton nháy liên tục.

## 4. Nguyên nhân gốc xác nhận từ source
- `useToast()` tạo object/hàm mới mỗi render.
- `AnalyticsPage.jsx` dùng `toast` trong dependency của:
  - `loadUsageData` (`useCallback`)
  - `loadChatbots` (`useEffect`)
  - `handleExportCsv` (`useCallback`)
- Điều này làm callback/effect bị coi là thay đổi liên tục -> chạy lặp -> setState lặp.
- Đồng thời effect set rightSlot vừa set vừa cleanup trong cùng effect có deps thay đổi thường xuyên, kéo theo `clearRightSlot()` gọi lặp, khớp stack ở `LayoutContext.jsx`.

## 5. Chiến lược sửa đã chọn
- Minimal diff, chỉ sửa `AnalyticsPage.jsx`.
- Không sửa `LayoutContext.jsx` vì context bản chất ổn định.
- Ổn định dependency bằng `toastRef` thay vì phụ thuộc trực tiếp `toast`.
- Tách effect set rightSlot và effect cleanup.
- Bỏ `setPageTitle("Analytics")` vì route default title đã có trong `App.jsx`.

## 6. Danh sách file đã đọc
- `.cursor/rules/00-core-working-rule.mdc`: xác nhận quy trình source-first/minimal diff.
- `.cursor/rules/10-backend-rag-rule.mdc`: xác nhận không backend scope.
- `.cursor/rules/20-frontend-widget-rule.mdc`: frontend bugfix và kiểm tra lint/build.
- `.cursor/rules/30-deploy-env-ops-rule.mdc`: không claim production verify.
- `.cursor/rules/40-db-vector-rule.mdc`: không liên quan DB/vector.
- `.cursor/rules/90-report-verification-rule.mdc`: format report bắt buộc.
- `reports/CURSOR_REPORT_01C_LAYOUT_CONTEXT.md`: pattern layout context/reset.
- `reports/CURSOR_REPORT_09A_ANALYTICS_USAGE_TAB.md`: baseline analytics usage implementation.
- `reports/CURSOR_REPORT_09B_ANALYTICS_SESSIONS_TAB.md`: baseline sessions integration.
- `reports/CURSOR_REPORT_09C_ANALYTICS_FEEDBACK_TAB.md`: baseline feedback integration.
- `reports/CURSOR_REPORT_11_INTEGRATION_QA_CHECKLIST_AUDIT.md`: baseline audit tổng frontend.
- `Frontend/src/pages/analytics/AnalyticsPage.jsx`: xác định dependency loop source.
- `Frontend/src/contexts/LayoutContext.jsx`: xác nhận setter tại line stack (`clearRightSlot`).
- `Frontend/src/App.jsx`: xác nhận default title `/analytics`.
- `Frontend/src/components/layout/Header.jsx`: xác nhận rightSlot render.
- `Frontend/src/components/layout/AppLayout.jsx`: xác nhận rightSlot forwarding.
- `Frontend/src/components/common/useToast.js`: xác nhận unstable return object/hàm mỗi render.
- `Frontend/src/pages/analytics/components/*.jsx` (14 files): xác nhận không có loop source phụ.

## 7. Danh sách file đã sửa
- `Frontend/src/pages/analytics/AnalyticsPage.jsx`
  - Sửa để ổn định dependencies và ngắt render loop.
  - Ảnh hưởng lớp: ui.
- `reports/CURSOR_REPORT_14A_FIX_ANALYTICS_MAX_UPDATE_DEPTH_LOOP.md`
  - Tạo report bàn giao task theo yêu cầu.
  - Ảnh hưởng lớp: docs.
- `docs/2026-05-07-fix-analytics-max-update-depth-loop.md`
  - Tạo report docs bắt buộc theo rule 90.
  - Ảnh hưởng lớp: docs.

## 8. Diff thay đổi của từng file
### File: `Frontend/src/pages/analytics/AnalyticsPage.jsx`
- Hiện trạng cũ liên quan bug:
  - dependency `toast` làm callback/effect đổi liên tục.
  - effect rightSlot cleanup chạy lặp theo deps.
  - có `setPageTitle("Analytics")` dư thừa.
- Đã sửa:
  - thêm `toastRef` để gọi toast không kéo deps bất ổn.
  - bỏ `toast` khỏi deps của `loadUsageData`.
  - effect load chatbots chuyển deps `[toast]` -> `[]`.
  - tạo `exportRightSlot` bằng `useMemo`.
  - tách effect setRightSlot khỏi effect cleanup.
  - bỏ effect `setPageTitle`.
- Vì sao sửa như vậy:
  - chặn re-trigger vòng lặp effect/state/context.
- Ảnh hưởng sau sửa:
  - analytics page render ổn định, không loop update depth.

```diff
- const { setPageTitle, setRightSlot, clearRightSlot } = useLayout();
+ const { setRightSlot, clearRightSlot } = useLayout();
+ const toastRef = useRef(toast);

- }, [chatbotId, from, invalidRange, to, toast]);
+ }, [chatbotId, from, invalidRange, to]);

- useEffect(() => { setPageTitle("Analytics"); }, [setPageTitle]);

- useEffect(() => {
-   setRightSlot(<button ... />);
-   return () => clearRightSlot();
- }, [clearRightSlot, handleExportCsv, hasLoadedData, isExporting, setRightSlot]);
+ const exportRightSlot = useMemo(() => (<button ... />), [handleExportCsv, hasLoadedData, isExporting]);
+ useEffect(() => { setRightSlot(exportRightSlot); }, [exportRightSlot, setRightSlot]);
+ useEffect(() => () => clearRightSlot(), [clearRightSlot]);
```

### File: `reports/CURSOR_REPORT_14A_FIX_ANALYTICS_MAX_UPDATE_DEPTH_LOOP.md`
- Hiện trạng cũ liên quan bug: chưa có report 14A.
- Đã sửa: tạo mới report kỹ thuật task 14A.
- Vì sao sửa: user yêu cầu output report và cần trace audit.
- Ảnh hưởng: reviewer có log root cause/fix/verify.

```diff
+ # CURSOR REPORT 14A - FIX ANALYTICS MAX UPDATE DEPTH LOOP
+ ...
```

### File: `docs/2026-05-07-fix-analytics-max-update-depth-loop.md`
- Hiện trạng cũ liên quan bug: chưa có docs report theo rule 90 cho task này.
- Đã sửa: tạo mới docs report 13 mục.
- Vì sao sửa: compliance workspace rule.
- Ảnh hưởng: đầy đủ tài liệu xác minh thay đổi.

```diff
+ # Fix Analytics Max Update Depth Loop (14A)
+ ...
```

## 9. Ảnh hưởng sau sửa
- Behavior thay đổi:
  - `/analytics` không còn trigger chuỗi re-render vô hạn từ dependency loop.
  - rightSlot export button không còn set/clear lặp trong cùng render cycle.
- Behavior giữ nguyên:
  - Usage/Sessions/Feedback UI và contracts giữ nguyên.
  - Route title vẫn là `Analytics` từ default map trong `App.jsx`.
- Điều kiện chỉ bật:
  - Toast gọi qua `toastRef.current` bên trong callbacks/effects để giữ dependency ổn định.
- Fallback giữ:
  - loading/error handling của analytics widgets vẫn giữ.
- Memory/CPU/disk:
  - giảm churn render không cần thiết; không thêm cost đáng kể.
- Latency/token/API cost:
  - giảm request lặp không mong muốn tại `/analytics`.
- MySQL/Qdrant:
  - không thay đổi dữ liệu.

## 10. Edge cases đã xem xét
- mount lần đầu `/analytics`: không loop.
- đổi tab usage/sessions/feedback: không phát sinh loop do rightSlot cleanup.
- đổi date range/chatbot filter: fetch chạy theo dependency hợp lệ, không tự lặp.
- export khi chưa có data: vẫn warning như cũ.
- unmount `/analytics`: rightSlot được clear đúng lúc unmount.

## 11. Kết quả kiểm tra
| Command | Kết quả | Ghi chú |
|---|---|---|
| `cd Backend && ./mvnw -DskipTests compile` | NOT RUN | Không scope backend. |
| `cd Backend && ./mvnw test` | NOT RUN | Không scope backend. |
| `cd Frontend && npm run lint` | PASS | Không lỗi lint mới. |
| `cd Frontend && npm run build` | PASS | Build pass, warning chunk-size >500k là pre-existing. |
| `cd Frontend && npm run build:widget` | NOT RUN | Không scope widget runtime. |
| `docker compose config` | NOT RUN | Không thay đổi deploy/infra. |

## 12. Rủi ro còn lại
- Chưa có xác nhận visual runtime trực tiếp từ browser trong phiên tool này (cần user confirm thực địa).
- Cảnh báo chunk-size build frontend vẫn tồn tại (không thuộc bugfix scope).

## 13. Đề xuất tiếp theo
- QA nhanh trực tiếp:
  - vào `/analytics`, confirm không còn `Maximum update depth exceeded`.
  - xác nhận không còn loading/skeleton flashing.
  - xác nhận `Export CSV` vẫn hoạt động.
- Nếu muốn triệt tiêu nguy cơ tương tự ở page khác, có thể chuẩn hóa `useToast` hook trả stable callbacks (ngoài scope task hiện tại).

## 14. Cập nhật follow-up theo phản hồi user
- User báo còn nháy tại table `Sessions`.
- Đã xác nhận và sửa thêm trong `Frontend/src/pages/analytics/components/AnalyticsSessionsTab.jsx`:
  - thêm `toastRef`,
  - bỏ `toast` khỏi deps của `loadSessions`,
  - dùng `toastRef.current` cho thông báo lỗi.
- Kết quả: `cd Frontend && npm run lint` PASS sau sửa follow-up.
