# Analytics Sessions Tab - Verification Report

## 1. Muc do hieu task
- Hieu task: 100%
- Chac chan: implement sessions tab chi tiet, khong regress usage, feedback van placeholder.
- Gia dinh: real API co the khac mock o rating semantic va sources shape.
- Thieu du kien: contract backend cho rating semantic (`positive/negative/unrated`) chua duoc xac nhan.

## 2. Tom tat yeu cau
- Them sessions table + pagination + view drawer.
- Gọi `GET /api/analytics/sessions` va `GET /api/analytics/sessions/:id/messages`.
- Reuse date/chatbot filters neu co the.
- Add rating filter cho sessions tab.

## 3. Hien trang truoc khi sua
- Sessions tab chi la placeholder.
- Date/chatbot filter chi nam trong Usage block.
- Chua co UI xem session details.

## 4. Nguyen nhan goc xac nhan tu source
- `AnalyticsPage` thieu implementation sessions-specific states/components/fetch flow.

## 5. Chien luoc sua da chon
- Tao component sessions rieng de tach logic khoi Usage.
- Keep Usage effect chi trigger khi active `usage`.
- Dua DateRangePicker + chatbot select len shared area cho `usage|sessions`.
- Adapter mock ratings trong `analyticsApi.getSessions`.

## 6. Danh sach file da doc
- `.cursor/rules/00-core-working-rule.mdc`: source-first + minimal diff.
- `.cursor/rules/20-frontend-widget-rule.mdc`: frontend constraints va verification.
- `.cursor/rules/90-report-verification-rule.mdc`: format report.
- `reports/CURSOR_REPORT_00_PROJECT_AUDIT.md`: context.
- `reports/CURSOR_REPORT_01C_LAYOUT_CONTEXT.md`: useLayout behavior.
- `reports/CURSOR_REPORT_02_API_LAYER.md`: analytics API contracts.
- `reports/CURSOR_REPORT_09A_ANALYTICS_USAGE_TAB.md`: baseline 09A.
- `Frontend/src/pages/analytics/AnalyticsPage.jsx`: target integration point.
- `Frontend/src/pages/analytics/components/*`: existing analytics components.
- `Frontend/src/api/analyticsApi.js`: sessions and session messages API.
- `Frontend/src/mocks/analyticsMock.js`: mock sessions/messages shape.
- `Frontend/src/api/chatbotsApi.js`: chatbot filter options.
- `Frontend/src/components/common/Drawer.jsx`: shared drawer.
- `Frontend/src/components/common/SkeletonLoader.jsx`: loading states.
- `Frontend/src/components/common/EmptyState.jsx`: empty states.
- `Frontend/src/components/common/StatusBadge.jsx`: evaluated for rating render.
- `Frontend/src/components/common/useToast.js`: toast hooks.

## 7. Danh sach file da sua
- `Frontend/src/api/analyticsApi.js` (api): rating adapter in mock mode.
- `Frontend/src/pages/analytics/AnalyticsPage.jsx` (ui): sessions integration + shared filters.
- `Frontend/src/pages/analytics/components/AnalyticsSessionsTab.jsx` (ui): sessions state/fetch/render.
- `Frontend/src/pages/analytics/components/SessionsTable.jsx` (ui): sessions table.
- `Frontend/src/pages/analytics/components/SessionsPagination.jsx` (ui): pagination controls.
- `Frontend/src/pages/analytics/components/SessionDetailDrawer.jsx` (ui): session replay drawer.
- `reports/CURSOR_REPORT_09B_ANALYTICS_SESSIONS_TAB.md` (docs): task report.

## 8. Diff thay doi cua tung file

### `Frontend/src/api/analyticsApi.js`
```diff
- if (rating) filtered = filtered.filter((s) => s.rating === Number(rating));
+ if (rating === "positive") filtered = filtered.filter((s) => Number(s.rating) >= 4);
+ else if (rating === "negative") filtered = filtered.filter((s) => Number(s.rating) <= 2);
+ else if (rating === "unrated") filtered = filtered.filter((s) => s.rating == null);
+ else ... numeric fallback
```

### `Frontend/src/pages/analytics/AnalyticsPage.jsx`
```diff
+ import AnalyticsSessionsTab
+ const showSharedFilters = activeTab === "usage" || activeTab === "sessions"
+ render DateRangePicker + chatbot select o shared area
- sessions placeholder
+ <AnalyticsSessionsTab active={...} from={from} to={to} chatbotId={chatbotId} invalidRange={invalidRange} />
```

### `Frontend/src/pages/analytics/components/AnalyticsSessionsTab.jsx`
```diff
+ rating filter options: all/positive/negative/unrated
+ reset page when from/to/chatbotId/rating changes
+ fetch sessions list by active tab and filters
+ loading/error/empty states
+ open SessionDetailDrawer via View action
```

### `Frontend/src/pages/analytics/components/SessionsTable.jsx`
```diff
+ columns: Session ID | Chatbot | Messages | Rating | Date | View
+ rating render as number + stars, fallback "-"
```

### `Frontend/src/pages/analytics/components/SessionsPagination.jsx`
```diff
+ controlled prev/next with disabled states
+ page display 1-based
+ showing range text
```

### `Frontend/src/pages/analytics/components/SessionDetailDrawer.jsx`
```diff
+ Drawer open triggers message fetch
+ loading skeleton, error+retry, empty state
+ conversation replay with user/assistant visual separation
+ assistant sources render fileName/score/snippet if available
```

### `reports/CURSOR_REPORT_09B_ANALYTICS_SESSIONS_TAB.md`
```diff
+ add detailed implementation and verification report for task output
```

## 9. Anh huong sau sua
- Thay doi:
  - Sessions tab da co full checklist behavior.
  - Shared date/chatbot filters dung cho ca Usage va Sessions.
- Giu nguyen:
  - Usage tab logic fetch/CSV khong doi contract.
  - Feedback tab van "Coming in next implementation".
- Dieu kien bat:
  - Sessions list fetch chi chay khi tab sessions active va range valid.
  - Drawer fetch chi chay khi user click View.
- Fallback:
  - list fail -> inline error + toast.
  - drawer fail -> inline error + retry.
- Tai nguyen:
  - tang nhe JS bundle; khong doi DB/Qdrant.

## 10. Edge cases da xem xet
- invalid range custom.
- filter changes reset page.
- response shape fallback normalize.
- rating null/undefined display.
- empty session messages.
- missing sources fields.

## 11. Ket qua kiem tra
| Command | Ket qua | Ghi chu |
|---|---|---|
| `cd Backend && ./mvnw -DskipTests compile` | NOT RUN | Khong sua backend |
| `cd Backend && ./mvnw test` | NOT RUN | Khong sua backend |
| `cd Frontend && npm run lint` | PASS | lint pass |
| `cd Frontend && npm run build` | PASS | build pass, chunk-size warning thong thuong |
| `cd Frontend && npm run build:widget` | NOT RUN | Ngoai scope |
| `docker compose config` | NOT RUN | Khong doi deploy |

## 12. Rui ro con lai
- Mock sessions chua dung from/to de filter.
- Rating semantic mapping can align lai khi backend chot contract.
- Sources real payload co the can adapter them.

## 13. De xuat tiep theo
- Implement Feedback tab chi tiet (09C).
- Verify sessions/rating behavior voi real backend staging.
