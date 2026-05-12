# CURSOR REPORT 09A - ANALYTICS USAGE TAB

## 1. Muc do hieu task

- Hieu task: 100%
- Phan chac chan: chi implement route `/analytics` cho tab `Usage`, giu `Sessions`/`Feedback` la placeholder, dung dung endpoint trong checklist, export CSV client-side.
- Phan con gia dinh: shape real API co the khac mock o mot vai field optional; da render fallback `-`.
- Thieu du kien: khong co endpoint export rieng (theo checklist), nen da chon export client-side tu data da load.

## 2. Tom tat yeu cau

- Replace placeholder `AnalyticsPage.jsx` bang implementation that.
- Usage tab co: date range picker (7d/30d/this month/custom), chatbot filter, export CSV, 4 metric cards + delta, daily bar chart, chatbot share bars, unanswered table + action Add docs.
- Fetch song song 4 endpoint Usage bang `Promise.allSettled`.
- Loading/error/empty state theo tung widget.
- Khong implement chi tiet Sessions/Feedback (chi placeholder ro rang).
- Khong them package/chart library moi.

## 3. Hien trang truoc khi sua

- `Frontend/src/pages/analytics/AnalyticsPage.jsx` chi la placeholder text.
- Chua co analytics components cho tabs/range/chart/share/table.
- `analyticsApi` da co san 4 endpoint Usage can dung.
- `chatbotsApi.getChatbots` da co san de lam dropdown filter.

## 4. Nguyen nhan goc xac nhan tu source

- Root cause truc tiep: page `/analytics` chua co UI + state orchestration de goi API va render Usage widgets; chi return 1 doan text placeholder.

## 5. Chien luoc sua da chon

- Giu scope toi thieu trong `Frontend/src/pages/analytics`.
- Tao cac component local trong `src/pages/analytics/components/` dung checklist.
- Rewrite `AnalyticsPage` de:
  - set page title + rightSlot Export CSV qua `useLayout`.
  - load chatbot options.
  - fetch 4 Usage datasets song song bang `Promise.allSettled`.
  - xu ly loading/error rieng cho tung widget.
  - export CSV client-side tu state da load.
- Khong dong vao Playground/Dashboard/Chatbots/Documents ngoai viec navigate `/documents?search=...` khi bam Add docs.

## 6. Danh sach file da doc

- `.cursor/rules/00-core-working-rule.mdc`: xac nhan quy trinh source-first, minimal diff.
- `.cursor/rules/10-backend-rag-rule.mdc`: xac nhan khong lien quan scope frontend nay.
- `.cursor/rules/20-frontend-widget-rule.mdc`: xac nhan khong them package, kiem tra lint/build.
- `.cursor/rules/30-deploy-env-ops-rule.mdc`: xac nhan khong doi deploy/env.
- `.cursor/rules/40-db-vector-rule.mdc`: xac nhan khong doi db/qdrant.
- `.cursor/rules/90-report-verification-rule.mdc`: xac nhan format report + ket qua verify.
- `agent.md`, `agent/01-overview.md`, `agent/02-architecture.md`: xac nhan boi canh project va rule docs.
- `reports/CURSOR_REPORT_00_PROJECT_AUDIT.md`: xac nhan roadmap va stack.
- `reports/CURSOR_REPORT_01C_LAYOUT_CONTEXT.md`: pattern `setPageTitle`, `setRightSlot`, `clearRightSlot`.
- `reports/CURSOR_REPORT_02_API_LAYER.md`: contract analytics/chatbots API + mock assumptions.
- `reports/CURSOR_REPORT_03_DASHBOARD.md`: pattern metric card/chart states.
- `reports/CURSOR_REPORT_04_CHATBOT_MANAGEMENT.md`: pattern table/filter UX.
- `reports/CURSOR_REPORT_08B_PLAYGROUND_ADVANCED.md`: tranh overlap scope Playground.
- `Frontend/src/pages/analytics/AnalyticsPage.jsx`: xac nhan placeholder.
- `Frontend/src/api/analyticsApi.js`: xac nhan 4 endpoint Usage + params.
- `Frontend/src/mocks/analyticsMock.js`: xac nhan shape summary/daily/byChatbot/unanswered.
- `Frontend/src/api/chatbotsApi.js`: xac nhan `getChatbots({ page, size })`.
- `Frontend/src/components/common/SkeletonLoader.jsx`: reuse loading skeleton.
- `Frontend/src/components/common/EmptyState.jsx`: reuse empty state.
- `Frontend/src/components/common/useToast.js`: toast success/error/warning.
- `Frontend/src/components/common/index.js`: export map.
- `Frontend/src/contexts/LayoutContext.jsx`: set/clear rightSlot va set title.
- `Frontend/src/pages/dashboard/components/MessageVolumeChart.jsx`: chart style tham khao.
- `Frontend/src/pages/dashboard/components/MetricCard.jsx`: metric style tham khao.
- `Frontend/src/App.jsx`: xac nhan route `/analytics` va title map.

## 7. Danh sach file da sua

- `Frontend/src/pages/analytics/AnalyticsPage.jsx`
  - Muc dich: implement full Usage tab va placeholder tabs con lai.
  - Layer: ui
- `Frontend/src/pages/analytics/components/AnalyticsTabs.jsx`
  - Muc dich: tab switch Usage/Sessions/Feedback.
  - Layer: ui
- `Frontend/src/pages/analytics/components/DateRangePicker.jsx`
  - Muc dich: preset + custom range + invalid warning.
  - Layer: ui
- `Frontend/src/pages/analytics/components/AnalyticsMetricCard.jsx`
  - Muc dich: 4 metric cards with delta/loading/error.
  - Layer: ui
- `Frontend/src/pages/analytics/components/DailyBarChart.jsx`
  - Muc dich: daily bars bang CSS/Tailwind (khong chart lib).
  - Layer: ui
- `Frontend/src/pages/analytics/components/ChatbotShareBars.jsx`
  - Muc dich: horizontal share bars + clamp percentage.
  - Layer: ui
- `Frontend/src/pages/analytics/components/UnansweredTable.jsx`
  - Muc dich: table 4 cot + Add docs action + skeleton/empty/error.
  - Layer: ui

## 8. Diff thay doi cua tung file

### File: `Frontend/src/pages/analytics/AnalyticsPage.jsx`

- Hien trang cu: chi placeholder text.
- Da sua:
  - them state tab/range/filter/widget data.
  - them `Promise.allSettled` fetch 4 APIs.
  - them `setPageTitle("Analytics")`, header rightSlot Export CSV + cleanup.
  - them CSV export client-side (Summary/Daily/By Chatbot/Unanswered) + escape CSV.
  - giu `Sessions`/`Feedback` o dang placeholder.
- Vi sao: dap ung full checklist Usage tab voi scope minimal.
- Anh huong sau sua: route `/analytics` hoat dong that, co loading/error/empty state day du.

```diff
-export default function AnalyticsPage() {
-  return (
-    <div>
-      <p className="text-sm text-gray-500">Analytics — placeholder. Sẽ implement ở Prompt 10.</p>
-    </div>
-  );
-}
+import { analyticsApi } from "../../api/analyticsApi";
+import { chatbotsApi } from "../../api/chatbotsApi";
+import { EmptyState, useToast } from "../../components/common";
+import { useLayout } from "../../contexts/LayoutContext";
+...
+const results = await Promise.allSettled([
+  analyticsApi.getSummary({ from, to, chatbotId: chatbotId || undefined }),
+  analyticsApi.getDaily({ from, to, chatbotId: chatbotId || undefined }),
+  analyticsApi.getByChatbot({ from, to }),
+  analyticsApi.getUnanswered({ limit: 10 }),
+]);
+...
+setRightSlot(<button ...>Export CSV</button>);
+...
+<AnalyticsTabs ... />
+<DateRangePicker ... />
+<AnalyticsMetricCard ... />
+<DailyBarChart ... />
+<ChatbotShareBars ... />
+<UnansweredTable ... />
+...
+<EmptyState title="Sessions tab" message="Coming in next implementation." />
```

### File: `Frontend/src/pages/analytics/components/AnalyticsTabs.jsx`

- Hien trang cu: chua co file.
- Da sua: tao tab bar Usage/Sessions/Feedback voi active style.
- Vi sao: can UX tabs va default Usage.
- Anh huong: switch tab an toan, khong crash.

```diff
+const TABS = [
+  { key: "usage", label: "Usage" },
+  { key: "sessions", label: "Sessions" },
+  { key: "feedback", label: "Feedback" },
+];
+...
+<button onClick={() => onChange(tab.key)} ...>{tab.label}</button>
```

### File: `Frontend/src/pages/analytics/components/DateRangePicker.jsx`

- Hien trang cu: chua co file.
- Da sua: tao picker voi 4 presets + 2 input date cho custom + invalid warning.
- Vi sao: dap ung checklist date range va validate `from <= to`.
- Anh huong: doi range se trigger reload Usage data qua page state.

```diff
+const PRESETS = [ "7d", "30d", "month", "custom" ... ];
+...
+<select value={preset} onChange={(e) => onPresetChange(e.target.value)} />
+<input type="date" value={from} ... disabled={preset !== "custom"} />
+<input type="date" value={to} ... disabled={preset !== "custom"} />
+{invalidRange && <p ...>Invalid date range...</p>}
```

### File: `Frontend/src/pages/analytics/components/AnalyticsMetricCard.jsx`

- Hien trang cu: chua co file.
- Da sua: local metric card co loading/error/value/delta va fallback `-`.
- Vi sao: dashboard card khong bao ham roi rieng analytics error-per-widget.
- Anh huong: 4 metrics render on dinh, khong crash khi field thieu.

```diff
+if (loading) return <SkeletonLoader variant="card" />;
+if (error) return <div className="...">{error}</div>;
+const deltaText = deltaValue == null ? "-" : `${deltaValue > 0 ? "+" : ""}${deltaValue}%`;
+<p className="text-3xl ...">{formatValue(value, valueSuffix)}</p>
```

### File: `Frontend/src/pages/analytics/components/DailyBarChart.jsx`

- Hien trang cu: chua co file.
- Da sua: chart bar CSS/Tailwind, co min/max labels, empty/error/skeleton.
- Vi sao: checklist cam them chart library.
- Anh huong: daily messages hien thi truc quan, khong can dependency moi.

```diff
+const max = Math.max(...messageValues, 1);
+const min = Math.min(...messageValues, 0);
+...
+<p className="text-xs text-gray-500">Min: ... Max: ...</p>
+<div className="flex h-52 items-end ...">
+  <div className="w-full rounded-t bg-blue-500" style={{ height: `${heightPct}%` }} />
+</div>
```

### File: `Frontend/src/pages/analytics/components/ChatbotShareBars.jsx`

- Hien trang cu: chua co file.
- Da sua: horizontal progress bars, clamp 0-100, loading/error/empty.
- Vi sao: checklist share % theo chatbot va tranh vo UI.
- Anh huong: hien share an toan ngay ca data bat thuong.

```diff
+const clampPercent = (value) => Math.min(100, Math.max(0, Number(value) || 0));
+...
+<div className="h-2.5 w-full rounded-full bg-gray-100">
+  <div className="h-2.5 rounded-full bg-blue-500" style={{ width: `${pct}%` }} />
+</div>
```

### File: `Frontend/src/pages/analytics/components/UnansweredTable.jsx`

- Hien trang cu: chua co file.
- Da sua: table dung 4 cot + Add docs action, co skeleton/error/empty.
- Vi sao: dap ung checklist unanswered va action navigate `/documents`.
- Anh huong: user co the chuyen nhanh sang Documents de bo sung knowledge.

```diff
+<th>Question</th>
+<th>Chatbot</th>
+<th>Count</th>
+<th className="text-right">Add docs</th>
+...
+<button onClick={() => onAddDocs?.(item.question || "")}>Add docs</button>
```

## 9. Anh huong sau sua

- Behavior thay doi:
  - `/analytics` da co tab bar va Usage tab functional.
  - Date range + chatbot filter se reload Usage data.
  - Export CSV tai ve file `analytics-usage-<from>-to-<to>.csv` tu data da load.
  - Daily chart/share bars/unanswered table co state loading/error/empty rieng.
- Behavior giu nguyen:
  - Khong co API moi ngoai checklist.
  - Khong doi Playground/Dashboard/Chatbots/Documents logic co san.
  - Sessions/Feedback van chua implement chi tiet.
- Dieu kien bat:
  - Export CSV chi bat khi da co data da load.
  - Fetch Usage tam dung neu custom range invalid.
- Fallback:
  - API fail tung widget van render widget khac (`Promise.allSettled`).
  - Missing field hien `-`, khong crash.
- Tai nguyen:
  - Them render UI frontend; khong tac dong MySQL/Qdrant schema.
  - API cost khong tang ngoai 4 endpoint Usage khi vao tab Usage va doi filter/range.

## 10. Edge cases da xem xet

- Custom date invalid (`from > to`): hien inline warning + pause fetch.
- API fail tung endpoint: inline error tung widget, khong sap trang.
- Nhieu endpoint fail cung luc: gom 1 toast tong, tranh spam 4 toast.
- Summary thieu field: metric hien `-`.
- Share % am hoac >100: clamp 0-100.
- Unanswered empty: hien EmptyState.
- Export khi chua co data: warning toast va khong tai file.
- CSV field co comma/quote/newline: escape theo standard CSV.

## 11. Ket qua kiem tra

| Command | Ket qua | Ghi chu |
|---|---|---|
| `cd Backend && ./mvnw -DskipTests compile` | NOT RUN | Khong sua Backend |
| `cd Backend && ./mvnw test` | NOT RUN | Khong sua Backend |
| `cd Frontend && npm run lint` | PASS | Sau khi fix 2 bien catch khong dung |
| `cd Frontend && npm run build` | PASS | Build thanh cong, co warning chunk size >500k (pre-existing type warning) |
| `cd Frontend && npm run build:widget` | NOT RUN | Scope khong sua widget |
| `docker compose config` | NOT RUN | Khong sua infra/deploy |

## 12. Rui ro con lai

- `analyticsApi` mock hien tai khong filter that theo `from`/`to`; khi ket noi real API can verify response theo range.
- `getByChatbot` trong checklist khong co `chatbotId`, nen share bars dang theo toan bo range, co the khac ky vong neu user dang chon chatbot cu the.
- Chunk size warning cua build van ton tai (khong phai regression scope nay).

## 13. De xuat tiep theo

- Prompt 09B: implement chi tiet tab `Sessions` (session list, filters, detail messages).
- Prompt 09C: implement chi tiet tab `Feedback` (rating distribution, comments table, filters).
- Khi co real backend staging, verify mapping field names summary/daily/byChatbot/unanswered va dieu chinh adapter neu can.
