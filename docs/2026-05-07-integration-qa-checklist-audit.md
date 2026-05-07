# Integration QA Checklist Audit (11)

## 1. Muc do hieu task

- Hieu task: 100%.
- Phan chac chan: can audit full frontend tu Global/Shared den Settings, chi fix bug nho/blocker, khong mo rong feature.
- Phan con gia dinh: cac limitation lon da duoc note tu cac prompt truoc tiep tuc duoc giu nguyen theo scope.
- Thieu du kien: khong co moi contract backend moi cho feedback read, playground session messages, widget runtime config consume.

## 2. Tom tat yeu cau

- Audit route map trong `App.jsx` (public/private guard, layout wrapper, fallback).
- Audit Global/Shared components + auth/axios foundation.
- Audit API layer + mock layer + reconfirm limitation da biet.
- Audit tung page theo checklist: Dashboard, Chatbots, Chatbot Config, Embed, Documents, Playground, Analytics, Settings.
- Chay full validation: `npm run lint`, `npm run build`, `npm run build:widget`.

## 3. Hien trang truoc khi sua

- Frontend da co day du implementation cho tat ca area den Settings.
- Build/lint/build:widget deu pass truoc khi fix.
- Ton tai 1 diem risk nho o layout: class responsive margin trong `AppLayout` dang o dang `md:${...}`, co nguy co Tailwind khong generate dung `md:ml-*` utility o build static.

## 4. Nguyen nhan goc xac nhan tu source

- Root cause duoc xac nhan trong `Frontend/src/components/layout/AppLayout.jsx`: chuoi class dynamic `md:${collapsed ? "ml-14" : "ml-[188px]"}` co the lam mat utility responsive trong qua trinh scan class cua Tailwind, dan den nguy co main content de len sidebar o viewport md+.

## 5. Chien luoc sua da chon

- Giu minimal diff, chi sua 1 dong class trong `AppLayout`.
- Khong doi API contract, khong them package, khong doi logic lon.
- Re-run full validation commands sau fix de dam bao khong regress.
- Cac limitation lon ngoai scope duoc giu nguyen va dua vao follow-up.

## 6. Danh sach file da doc

- `.cursor/rules/00-core-working-rule.mdc`: xac nhan quy trinh source-first, minimal diff.
- `.cursor/rules/90-report-verification-rule.mdc`: xac nhan bat buoc tao report docs + format final.
- `reports/CURSOR_REPORT_00_PROJECT_AUDIT.md`: baseline architecture + initial checklist map.
- `reports/CURSOR_REPORT_01_GLOBAL_SHARED.md`: baseline Global/Shared implementation.
- `reports/CURSOR_REPORT_01B_FOUNDATION_FIX.md`: baseline lint/layout fix history.
- `reports/CURSOR_REPORT_01C_LAYOUT_CONTEXT.md`: baseline title/rightSlot context behavior.
- `reports/CURSOR_REPORT_02_API_LAYER.md`: baseline API/mock modules + toggle.
- `reports/CURSOR_REPORT_03_DASHBOARD.md`: baseline dashboard checklist completion.
- `reports/CURSOR_REPORT_04_CHATBOT_MANAGEMENT.md`: baseline chatbot management checklist completion.
- `reports/CURSOR_REPORT_05_CHATBOT_CONFIG.md`: baseline chatbot config checklist completion.
- `reports/CURSOR_REPORT_06_CHATBOT_EMBED.md`: baseline embed checklist completion + known widget limitation.
- `reports/CURSOR_REPORT_07_DOCUMENTS.md`: baseline documents checklist completion + known mock limitations.
- `reports/CURSOR_REPORT_08A_PLAYGROUND_CORE.md`: baseline playground core completion.
- `reports/CURSOR_REPORT_08B_PLAYGROUND_ADVANCED.md`: baseline compare/prompt/export completion.
- `reports/CURSOR_REPORT_09A_ANALYTICS_USAGE_TAB.md`: baseline analytics usage completion.
- `reports/CURSOR_REPORT_09B_ANALYTICS_SESSIONS_TAB.md`: baseline analytics sessions completion.
- `reports/CURSOR_REPORT_09C_ANALYTICS_FEEDBACK_TAB.md`: baseline analytics feedback completion + feedback-read limitation.
- `reports/CURSOR_REPORT_10_SETTINGS_PAGE.md`: baseline settings completion.
- `Frontend/src/App.jsx`: verify route map, public/private guard, fallback route.
- `Frontend/src/api/index.js`: verify barrel exports con dung checklist.
- `Frontend/src/api/analyticsApi.js`: verify sessions/feedback behavior va mock adapter.
- `Frontend/src/api/playgroundApi.js`: verify chat/compare/sessions/export contracts.
- `Frontend/src/api/settingsApi.js`: verify profile/api-keys/team contract wiring.
- `Frontend/src/api/axiosInstance.js`: verify bearer interceptor + global error handler.
- `Frontend/src/stores/authStore.js`: verify zustand auth state/persist logic.
- `Frontend/src/routes/PrivateRoute.jsx`: verify guard behavior.
- `Frontend/src/components/layout/AppLayout.jsx`: verify responsive shell behavior (tim thay root cause).
- `Frontend/src/components/layout/Sidebar.jsx`: verify responsive sidebar + nav.
- `Frontend/src/components/layout/Header.jsx`: verify pageTitle/rightSlot render.
- `Frontend/src/contexts/LayoutContext.jsx`: verify title/rightSlot state isolation.
- `Frontend/src/components/common/Toast.jsx`: verify variants + auto-dismiss.
- `Frontend/src/components/common/Modal.jsx`: verify Escape/overlay/focus behavior.
- `Frontend/src/components/common/Drawer.jsx`: verify slide-in + Escape/overlay.
- `Frontend/src/components/common/ConfirmDeleteModal.jsx`: verify destructive modal reuse.
- `Frontend/src/components/common/SkeletonLoader.jsx`: verify variants.
- `Frontend/src/components/common/EmptyState.jsx`: verify shared empty state.
- `Frontend/src/components/common/ErrorBoundary.jsx`: verify app-level fallback.
- `Frontend/src/components/common/StatusBadge.jsx`: verify status variants map.
- `Frontend/src/pages/dashboard/DashboardPage.jsx`: verify dashboard checklist wiring.
- `Frontend/src/pages/chatbots/ChatbotsPage.jsx`: verify chatbot management wiring.
- `Frontend/src/pages/chatbots/ChatbotConfigPage.jsx`: verify config checklist wiring.
- `Frontend/src/pages/chatbots/ChatbotEmbedPage.jsx`: verify embed checklist wiring.
- `Frontend/src/pages/documents/DocumentsPage.jsx`: verify documents checklist wiring.
- `Frontend/src/pages/playground/PlaygroundPage.jsx`: verify playground checklist wiring.
- `Frontend/src/pages/analytics/AnalyticsPage.jsx`: verify usage/sessions/feedback tab integration.
- `Frontend/src/pages/settings/SettingsPage.jsx`: verify settings sections integration.
- `Frontend/package.json`: verify scripts/dependencies.
- `Frontend/vite.config.js`: verify frontend build/proxy setup.
- `Frontend/eslint.config.js`: verify lint rule baseline.
- `Frontend/widget/widget.js`: verify public widget runtime status (khong sua theo scope).
- `Frontend/src/pages/WidgetChatPage.jsx`: verify public widget route behavior.

## 7. Danh sach file da sua

- `Frontend/src/components/layout/AppLayout.jsx`
  - Sua de on dinh class responsive margin cho main content o md+.
  - Layer: ui.
- `docs/2026-05-07-integration-qa-checklist-audit.md`
  - Tao report audit/verification theo workspace rule.
  - Layer: docs.

## 8. Diff thay doi cua tung file

### File: `Frontend/src/components/layout/AppLayout.jsx`

- Hien trang cu lien quan bug: class responsive margin dang `md:${...}`.
- Da sua: doi sang class full string co prefix day du.
- Vi sao sua: dam bao Tailwind scan/generate utility responsive on dinh.
- Anh huong sau sua: main content margin-left md+ on dinh theo trang thai collapse/full, giam nguy co overlap voi sidebar.

```diff
-          md:${collapsed ? "ml-14" : "ml-[188px]"}
+          ${collapsed ? "md:ml-14" : "md:ml-[188px]"}
```

### File: `docs/2026-05-07-integration-qa-checklist-audit.md`

- Hien trang cu lien quan bug: chua co report cho task audit 11.
- Da sua: tao moi report tong hop audit + ket qua verify + limitation/follow-up.
- Vi sao sua: bat buoc theo workspace rule 90.
- Anh huong sau sua: reviewer co tai lieu audit day du de trace thay doi va ket qua test.

```diff
+ # Integration QA Checklist Audit (11)
+ ...
+ ## 11. Ket qua kiem tra
+ ...
```

## 9. Anh huong sau sua

- Behavior thay doi:
  - Responsive margin cua `AppLayout` md+ duoc xac dinh ro rang theo utility class (`md:ml-14` hoac `md:ml-[188px]`).
- Behavior giu nguyen:
  - Route map, private guard, API contracts, widget runtime behavior, page features khong bi thay doi.
- Dieu kien bat:
  - Margin behavior thay doi theo `collapsed` state nhu truoc, chi sua cach khai bao class.
- Fallback giu:
  - Cac fallback/loading/error states cua pages va widgets van giu nguyen.
- Tai nguyen:
  - Khong thay doi memory/cpu/disk dang ke.
  - Khong thay doi token/API cost.
  - Khong thay doi du lieu MySQL/Qdrant.

## 10. Edge cases da xem xet

- Route public `/widget` khong bi AppLayout/PrivateRoute boc.
- Route `/login` public, private routes redirect dung khi khong token.
- title/rightSlot khong leak giua pages (reset theo route + cleanup).
- Invalid date range analytics pause fetch.
- Documents polling cleanup interval khi khong con PROCESSING.
- Playground stream abort cleanup khi unmount/clear.
- API fail tung widget/page co fallback error state thay vi crash.

## 11. Ket qua kiem tra

| Command | Ket qua | Ghi chu |
|---|---|---|
| `cd Backend && ./mvnw -DskipTests compile` | NOT RUN | Task thuoc frontend audit |
| `cd Backend && ./mvnw test` | NOT RUN | Task thuoc frontend audit |
| `cd Frontend && npm run lint` | PASS | Chay lai sau fix |
| `cd Frontend && npm run build` | PASS | Chay lai sau fix; van co chunk-size warning >500k |
| `cd Frontend && npm run build:widget` | PASS | Chay lai sau fix |
| `docker compose config` | NOT RUN | Khong co thay doi deploy/infra |

## 12. Rui ro con lai

- Khong co rui ro lon moi trong scope vua sua.
- Cac limitation lon da biet va duoc giu nguyen theo scope audit:
  - Feedback tab chua co read endpoint backend, dang dung mock local.
  - Analytics mock chua filter date that.
  - Playground restore session real van thieu endpoint messages.
  - Embed snippet apiKey van la placeholder.
  - Widget runtime chua consume day du embed config fields.
  - Documents upload progress van la simulate.
  - DOCX mock upload co the map ve `OTHER`.

## 13. De xuat tiep theo

- Follow-up prompt 1: bo sung endpoint read feedback analytics (aggregate + comments) va migrate Feedback tab tu local mock sang API real.
- Follow-up prompt 2: bo sung endpoint restore session messages cho playground production.
- Follow-up prompt 3: dong bo widget runtime (`widget.js` + widget page) de consume embed config fields (`widgetColor`, `position`, `welcomeMessage`, ...).
- Follow-up prompt 4: neu can production hardening, bo sung max-timeout/max-retry cho documents polling.

## 14. Audit table theo area

| Area | Ket qua audit | Trang thai |
|---|---|---|
| Route map (`App.jsx`) | `/login` va `/widget` public; private routes guard dung; fallback `* -> /login`; layout wrapper dung | PASS |
| Global/Shared | `AppLayout`, `Sidebar`, `Header`, `Toast`, `Modal`, `Drawer`, `ConfirmDeleteModal`, `SkeletonLoader`, `EmptyState`, `ErrorBoundary`, `StatusBadge`, `PrivateRoute`, `authStore`, axios interceptors day du | PASS (co 1 fix nho AppLayout class) |
| API/mock layer | `api/index.js` exports dung; dashboard/chatbots/documents/playground/analytics/settings/publicChat modules ton tai; mock toggle khong vo | PASS |
| Dashboard | 4 metric cards, chart, top list, activity, refresh, loading/error/empty co day du | PASS |
| Chatbots | debounce search, filters, modal create, table columns, pagination, config nav, zustand store | PASS |
| Chatbot Config | load/save sections, prompt/model/status, tabs, back, delete confirm | PASS |
| Chatbot Embed | color/welcome/position/icon/origins/embed code/copy/preview/save | PASS (giu limitation apiKey placeholder + widget runtime consume) |
| Documents | upload/validate/polling/search/filter/table/chunks/assign/retry failed/delete/pagination | PASS (giu limitation progress simulate + DOCX mock map) |
| Playground | selector/sessions/stream/sources/retrieval/latency/override/clear/compare/prompt/export | PASS (giu limitation restore session real) |
| Analytics | usage/sessions/feedback tabs + filters + csv + drawer/table/chart/loading/error/empty | PASS (giu limitation feedback read + mock date filter) |
| Settings | profile/api keys/danger zone day du theo checklist | PASS |
