# Analytics Usage Tab - Verification Report

## 1. Muc do hieu task
- Hieu task: 100%
- Chac chan: implement Usage tab day du theo checklist, Sessions/Feedback chi placeholder.
- Gia dinh: real API field names khop hoac tuong thich voi mock hien co.
- Thieu du kien: khong co export endpoint server-side, nen xuat CSV phia client.

## 2. Tom tat yeu cau
- Implement route `/analytics` voi tab Usage mac dinh.
- Add date range presets + custom, chatbot filter, export CSV, 4 metric cards, daily bar chart, chatbot share bars, unanswered table.
- Fetch 4 endpoint Usage song song voi `Promise.allSettled`.
- Co loading/error/empty states; khong implement chi tiet Sessions/Feedback.

## 3. Hien trang truoc khi sua
- `AnalyticsPage.jsx` chi placeholder text.
- Chua co analytics components local.
- API layer `analyticsApi` va `chatbotsApi` da san sang.

## 4. Nguyen nhan goc xac nhan tu source
- Trang `/analytics` chua co implementation UI/logic data loading, nen khong dap ung checklist Usage.

## 5. Chien luoc sua da chon
- Sửa minimal diff trong scope `Frontend/src/pages/analytics`.
- Tao components local cho tab Usage.
- Rewrite `AnalyticsPage` de setup header Export CSV, range/filter states, API orchestration, CSV generation.
- Khong sua endpoint contracts, khong them package.

## 6. Danh sach file da doc
- `.cursor/rules/00-core-working-rule.mdc`: quy trinh source-first va minimal diff.
- `.cursor/rules/20-frontend-widget-rule.mdc`: khong them package, check lint/build.
- `.cursor/rules/90-report-verification-rule.mdc`: format report va verify trung thuc.
- `agent.md`, `agent/01-overview.md`, `agent/02-architecture.md`: boi canh project.
- `reports/CURSOR_REPORT_00_PROJECT_AUDIT.md`: roadmap va stack.
- `reports/CURSOR_REPORT_01C_LAYOUT_CONTEXT.md`: pattern `setPageTitle`/`setRightSlot`.
- `reports/CURSOR_REPORT_02_API_LAYER.md`: API contracts va mock notes.
- `reports/CURSOR_REPORT_03_DASHBOARD.md`: pattern loading/error per widget.
- `reports/CURSOR_REPORT_04_CHATBOT_MANAGEMENT.md`: pattern table/filter.
- `reports/CURSOR_REPORT_08B_PLAYGROUND_ADVANCED.md`: tranh overlap scope.
- `Frontend/src/pages/analytics/AnalyticsPage.jsx`: placeholder goc.
- `Frontend/src/api/analyticsApi.js`: summary/daily/by-chatbot/unanswered endpoints.
- `Frontend/src/mocks/analyticsMock.js`: data shape summary/daily/byChatbot/unanswered.
- `Frontend/src/api/chatbotsApi.js`: `getChatbots`.
- `Frontend/src/components/common/SkeletonLoader.jsx`: loading skeleton.
- `Frontend/src/components/common/EmptyState.jsx`: empty UI.
- `Frontend/src/components/common/useToast.js`: toast methods.
- `Frontend/src/contexts/LayoutContext.jsx`: page title + header slot lifecycle.
- `Frontend/src/pages/dashboard/components/MessageVolumeChart.jsx`: chart style reference.
- `Frontend/src/pages/dashboard/components/MetricCard.jsx`: metric style reference.

## 7. Danh sach file da sua
- `Frontend/src/pages/analytics/AnalyticsPage.jsx` (ui): main page implementation, API orchestration, CSV export.
- `Frontend/src/pages/analytics/components/AnalyticsTabs.jsx` (ui): tab bar.
- `Frontend/src/pages/analytics/components/DateRangePicker.jsx` (ui): presets + custom range inputs.
- `Frontend/src/pages/analytics/components/AnalyticsMetricCard.jsx` (ui): metrics + delta.
- `Frontend/src/pages/analytics/components/DailyBarChart.jsx` (ui): CSS/Tailwind bars.
- `Frontend/src/pages/analytics/components/ChatbotShareBars.jsx` (ui): horizontal share bars.
- `Frontend/src/pages/analytics/components/UnansweredTable.jsx` (ui): unanswered table + action.
- `reports/CURSOR_REPORT_09A_ANALYTICS_USAGE_TAB.md` (docs): task report.

## 8. Diff thay doi cua tung file

### `Frontend/src/pages/analytics/AnalyticsPage.jsx`
```diff
- placeholder text only
+ add tabs + usage section components
+ add range/filter states
+ load chatbots via chatbotsApi.getChatbots({ page:0, size:100 })
+ fetch 4 usage APIs with Promise.allSettled
+ per-widget loading/error/empty rendering
+ add header rightSlot Export CSV button via useLayout
+ client-side CSV export with escaping + download filename analytics-usage-<from>-to-<to>.csv
+ sessions/feedback placeholders "Coming in next implementation"
```

### `Frontend/src/pages/analytics/components/AnalyticsTabs.jsx`
```diff
+ new component with Usage/Sessions/Feedback buttons and active style
```

### `Frontend/src/pages/analytics/components/DateRangePicker.jsx`
```diff
+ new component with presets: 7d/30d/this month/custom
+ custom date inputs with invalid inline warning
```

### `Frontend/src/pages/analytics/components/AnalyticsMetricCard.jsx`
```diff
+ new local metric card with loading/error/fallback value and delta styles
```

### `Frontend/src/pages/analytics/components/DailyBarChart.jsx`
```diff
+ new CSS bar chart, x-axis labels by day, min/max label, loading/error/empty states
```

### `Frontend/src/pages/analytics/components/ChatbotShareBars.jsx`
```diff
+ new horizontal progress bars by chatbot
+ clamp share percentage 0-100
```

### `Frontend/src/pages/analytics/components/UnansweredTable.jsx`
```diff
+ new table columns: Question | Chatbot | Count | Add docs
+ Add docs action callback for navigation
+ loading/error/empty states
```

### `reports/CURSOR_REPORT_09A_ANALYTICS_USAGE_TAB.md`
```diff
+ add detailed report for task output requirement
```

## 9. Anh huong sau sua
- Thay doi:
  - `/analytics` da co Usage tab day du theo checklist.
  - Header co nut `Export CSV`, tao file CSV tu data da load.
  - Date range va chatbot filter trigger reload.
- Giu nguyen:
  - Khong goi API Sessions/Feedback.
  - Khong sua Playground va cac page ngoai scope.
  - Khong thay doi DB/Qdrant/API contracts.
- Chi bat khi du dieu kien:
  - Export disabled khi chua co data.
  - Fetch tam dung neu custom range invalid.
- Fallback:
  - Widget API fail khong lam hong toan page.
- Tai nguyen:
  - Tang nhe frontend render logic; khong anh huong MySQL/Qdrant data.

## 10. Edge cases da xem xet
- Custom range invalid.
- Missing fields tu API.
- 1 hoac nhieu widget fail cung luc.
- Share percentage bat thuong.
- Unanswered empty list.
- Export khi chua co data.
- CSV escaping voi comma, quote, newline.

## 11. Ket qua kiem tra
| Command | Ket qua | Ghi chu |
|---|---|---|
| `cd Backend && ./mvnw -DskipTests compile` | NOT RUN | Khong sua Backend |
| `cd Backend && ./mvnw test` | NOT RUN | Khong sua Backend |
| `cd Frontend && npm run lint` | PASS | lint pass sau fix no-unused-vars |
| `cd Frontend && npm run build` | PASS | build pass, chunk-size warning thong thuong |
| `cd Frontend && npm run build:widget` | NOT RUN | Ngoai scope |
| `docker compose config` | NOT RUN | Khong doi deploy |

## 12. Rui ro con lai
- Mock analytics khong filter that theo date range.
- Field naming real API co the khac 100% voi mock.
- `by-chatbot` endpoint khong nhan `chatbotId`, nen chart share dang level toan bo range.

## 13. De xuat tiep theo
- Implement tab `Sessions` chi tiet.
- Implement tab `Feedback` chi tiet.
- Verify field mappings voi backend staging/production-like environment.
