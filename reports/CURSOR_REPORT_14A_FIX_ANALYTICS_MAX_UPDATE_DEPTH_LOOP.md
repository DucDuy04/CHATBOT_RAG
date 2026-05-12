# CURSOR REPORT 14A - FIX ANALYTICS MAX UPDATE DEPTH LOOP

## 1. Mức độ hiểu task
- Hiểu task: 100%.
- Phần chắc chắn: bug runtime tại `/analytics` gây `Maximum update depth exceeded`, stack trỏ về `AnalyticsPage.jsx` và `LayoutContext.jsx`, cần fix hẹp không mở rộng feature.
- Phần còn giả định: line number có thể lệch nhẹ theo editor, nhưng vùng lỗi thực tế nằm ở khối `setSummaryState` trong `loadUsageData` và `clearRightSlot` trong context.
- Thiếu dữ kiện: không có browser trace/video, nên root cause xác minh bằng source dependency graph + stack message user cung cấp.

## 2. Files/rules/reports đã đọc

### Rules
- `.cursor/rules/00-core-working-rule.mdc`
- `.cursor/rules/10-backend-rag-rule.mdc`
- `.cursor/rules/20-frontend-widget-rule.mdc`
- `.cursor/rules/30-deploy-env-ops-rule.mdc`
- `.cursor/rules/40-db-vector-rule.mdc`
- `.cursor/rules/90-report-verification-rule.mdc`

### Reports
- `reports/CURSOR_REPORT_01C_LAYOUT_CONTEXT.md`
- `reports/CURSOR_REPORT_09A_ANALYTICS_USAGE_TAB.md`
- `reports/CURSOR_REPORT_09B_ANALYTICS_SESSIONS_TAB.md`
- `reports/CURSOR_REPORT_09C_ANALYTICS_FEEDBACK_TAB.md`
- `reports/CURSOR_REPORT_11_INTEGRATION_QA_CHECKLIST_AUDIT.md`

### Source files
- `Frontend/src/pages/analytics/AnalyticsPage.jsx`
- `Frontend/src/contexts/LayoutContext.jsx`
- `Frontend/src/App.jsx`
- `Frontend/src/components/layout/Header.jsx`
- `Frontend/src/components/layout/AppLayout.jsx`
- `Frontend/src/pages/analytics/components/AnalyticsTabs.jsx`
- `Frontend/src/pages/analytics/components/DateRangePicker.jsx`
- `Frontend/src/pages/analytics/components/AnalyticsMetricCard.jsx`
- `Frontend/src/pages/analytics/components/DailyBarChart.jsx`
- `Frontend/src/pages/analytics/components/ChatbotShareBars.jsx`
- `Frontend/src/pages/analytics/components/UnansweredTable.jsx`
- `Frontend/src/pages/analytics/components/AnalyticsSessionsTab.jsx`
- `Frontend/src/pages/analytics/components/SessionsTable.jsx`
- `Frontend/src/pages/analytics/components/SessionsPagination.jsx`
- `Frontend/src/pages/analytics/components/SessionDetailDrawer.jsx`
- `Frontend/src/pages/analytics/components/AnalyticsFeedbackTab.jsx`
- `Frontend/src/pages/analytics/components/FeedbackSummaryCards.jsx`
- `Frontend/src/pages/analytics/components/FeedbackRatingFilter.jsx`
- `Frontend/src/pages/analytics/components/FeedbackCommentsList.jsx`
- `Frontend/src/components/common/useToast.js`

## 3. Root cause
- **Root cause chính**: `useToast()` trả về object/hàm mới mỗi render, nhưng trong `AnalyticsPage.jsx` các callback/effect (`loadUsageData`, `loadChatbots`, `handleExportCsv`) lại phụ thuộc trực tiếp vào `toast`.
- Hệ quả:
  - `loadUsageData` bị tạo lại mỗi render -> effect fetch usage chạy lặp -> `setSummaryState(...)` (vùng user báo quanh line 96) tiếp tục kích hoạt render.
  - Effect set rightSlot phụ thuộc callback không ổn định -> cleanup `clearRightSlot()` bị gọi lặp (trùng với stack ở `LayoutContext.jsx` line 26: `setRightSlotState(null)`).
  - UI analytics ở trạng thái loading/skeleton nháy liên tục.

## 4. Files changed
| File | Change | Reason |
|---|---|---|
| `Frontend/src/pages/analytics/AnalyticsPage.jsx` | Ổn định dependencies, tách cleanup rightSlot, bỏ setPageTitle dư thừa, dùng `toastRef` | Chặn render loop giữa analytics state updates và layout context updates |

## 5. Diff summary
### `Frontend/src/pages/analytics/AnalyticsPage.jsx`
- Cũ:
  - `useLayout()` lấy cả `setPageTitle`.
  - `loadUsageData` và `loadChatbots` phụ thuộc `toast`.
  - Effect setRightSlot vừa set vừa cleanup trong cùng effect có dependencies thường xuyên thay đổi.
  - Có effect `setPageTitle("Analytics")` dù route default title đã có trong `App.jsx`.
- Mới:
  - Thêm `toastRef` (`useRef`) để gọi toast mà không kéo `toast` vào deps.
  - `loadUsageData` bỏ dependency `toast`.
  - `loadChatbots` effect chuyển dependency từ `[toast]` sang `[]`, dùng `toastRef.current`.
  - Tạo `exportRightSlot` bằng `useMemo`.
  - Tách effect setRightSlot khỏi effect cleanup:
    - effect 1: setRightSlot khi node đổi.
    - effect 2: chỉ cleanup `clearRightSlot` lúc unmount.
  - Bỏ `setPageTitle("Analytics")` (title default đã có từ `App.jsx`).

```diff
- const { setPageTitle, setRightSlot, clearRightSlot } = useLayout();
+ const { setRightSlot, clearRightSlot } = useLayout();
+ const toastRef = useRef(toast);

- }, [chatbotId, from, invalidRange, to, toast]);
+ }, [chatbotId, from, invalidRange, to]);

- useEffect(() => { setPageTitle("Analytics"); }, [setPageTitle]);

- useEffect(() => {
-   setRightSlot(<button ... onClick={handleExportCsv}>...</button>);
-   return () => clearRightSlot();
- }, [clearRightSlot, handleExportCsv, hasLoadedData, isExporting, setRightSlot]);
+ const exportRightSlot = useMemo(() => (<button ... onClick={handleExportCsv}>...</button>), [handleExportCsv, hasLoadedData, isExporting]);
+ useEffect(() => { setRightSlot(exportRightSlot); }, [exportRightSlot, setRightSlot]);
+ useEffect(() => () => clearRightSlot(), [clearRightSlot]);

- }, [toast]);
+ }, []);
```

## 6. Validation results
| Command | Result | Notes |
|---|---|---|
| `cd Frontend && npm run lint` | PASS | Không có lỗi eslint mới. |
| `cd Frontend && npm run build` | PASS | Build pass; warning chunk size >500k là pre-existing. |

## 7. Runtime impact after fix
- `Maximum update depth exceeded` tại `/analytics`: expected resolved.
- Analytics loading/skeleton flashing: expected resolved.
- Header title vẫn hiển thị `Analytics` qua route default trong `App.jsx`.
- Export CSV button vẫn hoạt động qua `rightSlot` context nhưng không còn gây loop.

## 8. Known limitations not fixed
- Analytics mock vẫn không filter date thật trong một số dataset mock (đã biết từ report trước).
- Chunk-size warning build frontend vẫn tồn tại (ngoài scope bugfix).

## 9. Release handoff note for this fix
- Verify nhanh runtime:
  - Vào `/analytics` không còn lỗi console max update depth.
  - Tab Usage render ổn định, không nháy loading liên tục.
  - Export CSV button vẫn render ở header right slot.

## 10. Follow-up hotfix (Sessions table flicker)
- Triệu chứng còn lại user xác nhận: `Sessions` table skeleton vẫn nháy liên tục.
- Root cause phụ:
  - `AnalyticsSessionsTab.jsx` cũng dùng `useToast()` trong dependency của `loadSessions` callback.
  - Vì `useToast` không stable-reference, callback bị recreate mỗi render -> effect fetch chạy lặp -> `loading` reset liên tục.
- Đã fix:
  - Thêm `toastRef` trong `AnalyticsSessionsTab`.
  - Bỏ `toast` khỏi dependency của `loadSessions`.
  - `fetchMessages` callback dùng `toastRef.current` và dependency rỗng.
- Kết quả kiểm tra:
  - `cd Frontend && npm run lint`: PASS.
