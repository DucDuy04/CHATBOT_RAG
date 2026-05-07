# CURSOR REPORT 09B - ANALYTICS SESSIONS TAB

## 1. Muc do hieu task

- Hieu task: 100%
- Chac chan: chi implement chi tiet tab `Sessions` trong `/analytics`, giu nguyen `Usage`, giu `Feedback` placeholder.
- Phan gia dinh: real API co the khong nhan `rating=positive|negative|unrated`; mock da duoc adapter de support.
- Thieu du kien: format sources cua API real trong `getSessionMessages` co the da dang hon mock.

## 2. Tom tat yeu cau

- Implement Sessions tab voi table + pagination + rating filter + drawer detail conversation.
- Goi 2 endpoint dung checklist:
  - `GET /api/analytics/sessions`
  - `GET /api/analytics/sessions/:id/messages`
- Reuse date range va chatbot filter tu AnalyticsPage neu phu hop.
- Khong lam regress Usage tab.
- Khong implement Feedback chi tiet.

## 3. Hien trang truoc khi sua

- `AnalyticsPage` da co Usage day du (09A), Sessions chi la `EmptyState` placeholder.
- Date range va chatbot filter dang dat trong Usage block.
- Chua co sessions table/pagination/drawer.
- `analyticsApi.getSessions` mock chi loc rating theo so (Number), chua support semantic filter `positive/negative/unrated`.

## 4. Nguyen nhan goc xac nhan tu source

- Trang analytics thieu toan bo UI/state cho sessions flow: khong co fetch list, khong co view detail, khong co pagination/rating filter.

## 5. Chien luoc sua da chon

- Tao bo component rieng cho tab Sessions:
  - `AnalyticsSessionsTab`
  - `SessionsTable`
  - `SessionsPagination`
  - `SessionDetailDrawer`
- Reuse state `from/to/chatbotId` bang cach dua DateRangePicker + chatbot filter len shared area cho `usage|sessions`.
- Giu usage fetch effect chi chay khi `activeTab==="usage"` de tranh regress.
- Adapter nhe trong `analyticsApi.getSessions` (mock mode) cho rating semantic.

## 6. Danh sach file da doc

- `.cursor/rules/00-core-working-rule.mdc`: quy trinh source-first.
- `.cursor/rules/10-backend-rag-rule.mdc`: xac nhan ngoai scope backend.
- `.cursor/rules/20-frontend-widget-rule.mdc`: khong them package, verify lint/build.
- `.cursor/rules/30-deploy-env-ops-rule.mdc`: khong doi deploy/env.
- `.cursor/rules/40-db-vector-rule.mdc`: khong doi db/vector.
- `.cursor/rules/90-report-verification-rule.mdc`: format report + final output.
- `reports/CURSOR_REPORT_00_PROJECT_AUDIT.md`: context roadmap.
- `reports/CURSOR_REPORT_01C_LAYOUT_CONTEXT.md`: pattern useLayout.
- `reports/CURSOR_REPORT_02_API_LAYER.md`: API contracts va mock assumptions.
- `reports/CURSOR_REPORT_09A_ANALYTICS_USAGE_TAB.md`: baseline analytics 09A.
- `Frontend/src/pages/analytics/AnalyticsPage.jsx`: current analytics wiring.
- `Frontend/src/pages/analytics/components/AnalyticsTabs.jsx`: tabs UI.
- `Frontend/src/pages/analytics/components/DateRangePicker.jsx`: date filter UI.
- `Frontend/src/pages/analytics/components/AnalyticsMetricCard.jsx`: usage metric component.
- `Frontend/src/pages/analytics/components/DailyBarChart.jsx`: usage chart component.
- `Frontend/src/pages/analytics/components/ChatbotShareBars.jsx`: usage share component.
- `Frontend/src/pages/analytics/components/UnansweredTable.jsx`: usage unanswered table.
- `Frontend/src/api/analyticsApi.js`: sessions endpoint implementation.
- `Frontend/src/mocks/analyticsMock.js`: sessions + messages mock shapes.
- `Frontend/src/api/chatbotsApi.js`: chatbot options source.
- `Frontend/src/components/common/Drawer.jsx`: reused drawer.
- `Frontend/src/components/common/SkeletonLoader.jsx`: loading UI.
- `Frontend/src/components/common/EmptyState.jsx`: empty UI.
- `Frontend/src/components/common/StatusBadge.jsx`: reviewed for rating display option.
- `Frontend/src/components/common/useToast.js`: toast behavior.

## 7. Danh sach file da sua

- `Frontend/src/api/analyticsApi.js` (api): adapter rating filter cho mock sessions.
- `Frontend/src/pages/analytics/AnalyticsPage.jsx` (ui): integrate sessions tab + shared filters.
- `Frontend/src/pages/analytics/components/AnalyticsSessionsTab.jsx` (ui): sessions logic.
- `Frontend/src/pages/analytics/components/SessionsTable.jsx` (ui): sessions list table.
- `Frontend/src/pages/analytics/components/SessionsPagination.jsx` (ui): controlled pagination.
- `Frontend/src/pages/analytics/components/SessionDetailDrawer.jsx` (ui): conversation replay drawer.

## 8. Diff thay doi cua tung file

### `Frontend/src/api/analyticsApi.js`

- Cu: mock sessions chi support numeric `rating`.
- Moi: support `positive`/`negative`/`unrated` trong mock mode, van giu numeric fallback.

```diff
- if (rating) filtered = filtered.filter((s) => s.rating === Number(rating));
+ if (rating === "positive") filtered = filtered.filter((s) => Number(s.rating) >= 4);
+ else if (rating === "negative") filtered = filtered.filter((s) => Number(s.rating) <= 2);
+ else if (rating === "unrated") filtered = filtered.filter((s) => s.rating == null);
+ else ... numeric fallback
```

### `Frontend/src/pages/analytics/AnalyticsPage.jsx`

- Cu: DateRangePicker + chatbot filter chi nam trong Usage block; Sessions placeholder.
- Moi:
  - Dua filter bar len shared area cho `usage` va `sessions`.
  - Replace sessions placeholder bang `AnalyticsSessionsTab`.
  - Giu usage render/fetch logic cu.

```diff
+ import AnalyticsSessionsTab ...
+ const showSharedFilters = activeTab === "usage" || activeTab === "sessions";
+ {showSharedFilters && <DateRangePicker ... /> + chatbot select}
- {activeTab==="sessions" && <EmptyState ... />}
+ {activeTab==="sessions" && <AnalyticsSessionsTab ... />}
```

### `Frontend/src/pages/analytics/components/AnalyticsSessionsTab.jsx`

- Cu: chua co.
- Moi:
  - Rating filter (`All/Positive/Negative/Unrated`) -> map vao `analyticsApi.getSessions(...rating...)`.
  - Fetch list sessions khi active va khi from/to/chatbotId/rating/page thay doi.
  - Reset page ve 0 khi doi from/to/chatbot/rating.
  - loading/error/empty state.
  - open drawer khi click View.

```diff
+ const [rating, setRating] = useState("");
+ const [page, setPage] = useState(0);
+ const payload = await analyticsApi.getSessions({ from, to, chatbotId, rating, page });
+ <SessionsTable items={state.items} onView={handleView} />
+ <SessionsPagination ... onPageChange={setPage} />
+ <SessionDetailDrawer ... fetchMessages={fetchMessages} />
```

### `Frontend/src/pages/analytics/components/SessionsTable.jsx`

- Cu: chua co.
- Moi: table dung cot checklist:
  - Session ID | Chatbot | Messages | Rating | Date | View
  - mobile horizontal scroll support.

```diff
+ <th>Session ID</th><th>Chatbot</th><th>Messages</th><th>Rating</th><th>Date</th><th>View</th>
+ <button onClick={() => onView(session)}>View</button>
```

### `Frontend/src/pages/analytics/components/SessionsPagination.jsx`

- Cu: chua co.
- Moi: controlled pagination Prev/Next + page 1-based + disabled state dung.

```diff
+ disabled={page <= 0}
+ Page {page + 1} / {totalPages}
+ disabled={page >= totalPages - 1}
```

### `Frontend/src/pages/analytics/components/SessionDetailDrawer.jsx`

- Cu: chua co.
- Moi:
  - Reuse shared `Drawer`.
  - fetch messages khi mo drawer.
  - loading skeleton / error + retry / empty state.
  - replay full conversation (user vs assistant bubble).
  - sources per assistant turn: file name + score + snippet neu co.

```diff
+ <Drawer isOpen ... title={`Session ${selectedSession.id}`}>
+ const messages = await fetchMessages(selectedSession.id)
+ {role==="assistant" && message.sources?.length > 0 && <SourcePill ... />}
```

## 9. Anh huong sau sua

- Behavior thay doi:
  - Tab Sessions da hoat dong day du: list, rating filter, pagination, detail drawer.
  - Date range + chatbot filter duoc reuse chung giua Usage va Sessions.
- Behavior giu nguyen:
  - Usage tab van fetch theo logic cu (`activeTab==="usage"`).
  - Feedback tab van placeholder.
  - Khong sua Playground/Dashboard/Chatbots/Documents.
- Dieu kien bat:
  - Sessions API chi goi khi `activeTab==="sessions"` va range hop le.
  - Drawer chi fetch messages khi click View + drawer open.
- Fallback:
  - sessions list fail: inline error + 1 toast.
  - drawer fail: inline error + Retry.
  - khong co data: EmptyState.
- Tai nguyen:
  - tang nhe render/frontend bundle.
  - khong anh huong MySQL/Qdrant schema/data.

## 10. Edge cases da xem xet

- Invalid custom range: pause sessions fetch.
- Doi filter/range/chatbot: reset page ve 0.
- Tab switch khoi sessions: khong tiep tuc fetch sessions.
- API response shape khac (`array` vs paginated object): normalize fallback.
- Rating null: hien `-`.
- Session khong co message: drawer EmptyState.
- Sources thieu `score/snippet/fileName`: render fallback, khong crash.

## 11. Ket qua kiem tra

| Command | Ket qua | Ghi chu |
|---|---|---|
| `cd Backend && ./mvnw -DskipTests compile` | NOT RUN | Khong sua backend |
| `cd Backend && ./mvnw test` | NOT RUN | Khong sua backend |
| `cd Frontend && npm run lint` | PASS | lint pass sau adjust hook rule comments |
| `cd Frontend && npm run build` | PASS | build pass, warning chunk >500k van ton tai |
| `cd Frontend && npm run build:widget` | NOT RUN | Ngoai scope |
| `docker compose config` | NOT RUN | Khong doi infra |

## 12. Rui ro con lai

- Mock sessions khong loc theo `from/to` that (pre-existing), nen filter date effect tren sessions se phu thuoc API real.
- Mapping `positive/negative/unrated` la frontend convention; can align exact semantic voi backend real neu khac.
- Drawer sources dang render theo cac key pho bien; neu backend real tra schema rat khac can adapter bo sung.

## 13. De xuat tiep theo

- Prompt 09C: implement Feedback tab chi tiet.
- Verify backend analytics sessions endpoint tren env that de xac nhan rating mapping contract.
- Neu can, trich utility chung cho pagination + rating format de tai su dung.
