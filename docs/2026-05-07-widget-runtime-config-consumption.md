# Cursor Report 12A - Widget Runtime Config Consumption

## 1. Muc do hieu task

- Hieu task: 100%.
- Phan chac chan:
  - Can dong bo widget runtime voi config tu `window.RagChatbotConfig` cho `position`, `widgetColor`, `welcomeMessage`, `launcherIcon`.
  - Giu nguyen flow `widgetKey/apiKey` va `frontendUrl`.
  - Khong sua backend contract.
- Phan con gia dinh:
  - `launcherIcon` khong nam trong contract backend embed-config that, nhung co the consume runtime tu snippet frontend.
  - `allowedOrigins` la server-side concern.
- Thieu du kien:
  - Khong co yeu cau enforce `allowedOrigins` client-side, va khong co endpoint moi de cap apiKey that cho snippet.

## 2. Tom tat yeu cau

- Audit runtime hien tai (`widget.js`, `WidgetChatPage.jsx`, embed page/snippet/preview).
- Consume runtime fields:
  - `position`: `bottom-right` / `bottom-left`, fallback `bottom-right`.
  - `widgetColor`: validate hex, apply launcher bubble + truyen vao iframe de apply header UI.
  - `welcomeMessage`: truyen vao iframe va dung lam welcome message ban dau.
  - `launcherIcon`: support `chat/help/spark`, fallback `chat`.
- Bao toan flow key:
  - Giu logic `widgetKey`/`apiKey` nhu hien tai.
  - Giu `frontendUrl` handling hien tai.
- Neu chua co `reports/CURSOR_REPORT_11_INTEGRATION_QA_CHECKLIST_AUDIT.md`, tao ban copy tu `docs/2026-05-07-integration-qa-checklist-audit.md`.
- Chay `lint/build/build:widget`.

## 3. Hien trang truoc khi sua

- `Frontend/widget/widget.js` chi doc:
  - `widgetKey` hoac `apiKey`
  - `frontendUrl`
- Iframe URL truoc sua:
  - `/widget?widgetKey=<...>` neu co key
  - Khong truyen `widgetColor`, `welcomeMessage`, `launcherIcon`, `position`.
- `WidgetChatPage.jsx` truoc sua:
  - Doc query `widgetKey` hoac `apiKey`.
  - Welcome message hardcoded.
  - Header mau xanh hardcoded.
- `ChatbotEmbedPage.jsx` snippet da co `widgetColor`, `welcomeMessage`, `position`, `allowedOrigins`, nhung chua co `launcherIcon` trong snippet.
- `allowedOrigins` chi la field admin config, runtime khong consume.

## 4. Nguyen nhan goc xac nhan tu source

- Root cause: runtime widget (`Frontend/widget/widget.js`) khong consume cac field UI config da duoc tao o Embed UI; no chi dung key/frontendUrl de mo iframe.
- Root cause phu: `WidgetChatPage.jsx` khong co logic doc query param cho color/welcome.

## 5. Chien luoc sua da chon

- Minimal diff, tap trung vao 3 file runtime/embed:
  - `Frontend/widget/widget.js`
  - `Frontend/src/pages/WidgetChatPage.jsx`
  - `Frontend/src/pages/chatbots/ChatbotEmbedPage.jsx`
- Bo sung helper nho de validate/fallback:
  - Hex color validator.
  - Launcher icon normalizer.
  - Position styler cho bubble/frame.
- Truyen config qua query param vao `/widget` de page iframe consume.
- Khong sua backend, khong them dependency, khong doi route `/widget`.

## 6. Danh sach file da doc

- `.cursor/rules/00-core-working-rule.mdc`: quy trinh source-first, minimal diff.
- `.cursor/rules/10-backend-rag-rule.mdc`: xac nhan task khong backend.
- `.cursor/rules/20-frontend-widget-rule.mdc`: checklist frontend/widget can verify.
- `.cursor/rules/30-deploy-env-ops-rule.mdc`: quy tac env/deploy.
- `.cursor/rules/40-db-vector-rule.mdc`: xac nhan khong lien quan db/vector.
- `.cursor/rules/90-report-verification-rule.mdc`: format report + final message.
- `agent.md`: context tong quan du an.
- `agent/01-overview.md`: luong multi-tenant/widget tong quan.
- `agent/02-architecture.md`: architecture va role cua widget runtime.
- `reports/CURSOR_REPORT_00_PROJECT_AUDIT.md`: baseline frontend.
- `reports/CURSOR_REPORT_02_API_LAYER.md`: context `publicChatApi`.
- `reports/CURSOR_REPORT_06_CHATBOT_EMBED.md`: baseline embed UI va limitation runtime truoc sua.
- `reports/CURSOR_REPORT_11_INTEGRATION_QA_CHECKLIST_AUDIT.md`: khong ton tai truoc khi sua (file missing).
- `docs/2026-05-07-integration-qa-checklist-audit.md`: source de copy tao report 11.
- `Frontend/widget/widget.js`: xac nhan runtime chi doc key/frontendUrl.
- `Frontend/widget/widget.css`: xac nhan default vi tri/mau bubble/frame.
- `Frontend/src/pages/WidgetChatPage.jsx`: xac nhan param consumption hien tai.
- `Frontend/src/pages/chatbots/ChatbotEmbedPage.jsx`: xac nhan logic snippet generation.
- `Frontend/src/pages/chatbots/components/EmbedCodeBlock.jsx`: xac nhan snippet render/copy.
- `Frontend/src/pages/chatbots/components/WidgetLivePreview.jsx`: xac nhan preview fields.
- `Frontend/src/pages/chatbots/components/EmbedSettingsSection.jsx`: xac nhan icon options.
- `Frontend/src/api/publicChatApi.js`: xac nhan flow public key khong JWT.
- `Frontend/vite.widget.config.js`: xac nhan widget build output.
- `Frontend/package.json`: xac nhan scripts lint/build/build:widget.

## 7. Danh sach file da sua

- `Frontend/widget/widget.js`
  - Muc dich: consume runtime config + truyen param vao iframe.
  - Layer: widget.
- `Frontend/src/pages/WidgetChatPage.jsx`
  - Muc dich: consume query params `widgetColor`, `welcomeMessage`.
  - Layer: ui/widget.
- `Frontend/src/pages/chatbots/ChatbotEmbedPage.jsx`
  - Muc dich: dong bo snippet bo sung `launcherIcon`.
  - Layer: ui.
- `reports/CURSOR_REPORT_11_INTEGRATION_QA_CHECKLIST_AUDIT.md`
  - Muc dich: tao ban copy bat buoc tu docs khi file report 11 chua ton tai.
  - Layer: docs.
- `reports/CURSOR_REPORT_12A_WIDGET_RUNTIME_CONFIG_CONSUMPTION.md`
  - Muc dich: report task 12A.
  - Layer: docs.
- `docs/2026-05-07-widget-runtime-config-consumption.md`
  - Muc dich: report docs bat buoc theo workspace rule.
  - Layer: docs.

## 8. Diff thay doi cua tung file

### File: `Frontend/widget/widget.js`

- Hien trang cu lien quan bug:
  - Chi lay `widgetKey/apiKey` + `frontendUrl`.
  - Bubble icon hardcoded chat.
  - Position hardcoded bottom-right.
  - Khong truyen color/welcome/icon vao iframe.
- Da sua:
  - Them parse + fallback:
    - `position` (`bottom-left` hoac fallback `bottom-right`)
    - `widgetColor` (hex hop le hoac fallback `#2563eb`)
    - `launcherIcon` (`chat/help/spark`, fallback `chat`)
    - `welcomeMessage` string fallback `""`
  - Apply `widgetColor` vao launcher bubble.
  - Apply `position` cho bubble + frame qua inline styles.
  - Truyen query params vao iframe:
    - `widgetKey` (giu nguyen logic key)
    - `widgetColor`
    - `welcomeMessage`
    - `launcherIcon`
  - Tach helper SVG theo icon.
- Vi sao sua:
  - De runtime consume dung config tu embed snippet ma khong doi backend.
- Anh huong sau sua:
  - Widget nhung tren website se doi vi tri/mau/icon theo config runtime.

```diff
+ const position = config.position === "bottom-left" ? "bottom-left" : "bottom-right";
+ const widgetColor = isHexColor(config.widgetColor) ? config.widgetColor : "#2563eb";
+ const launcherIcon = normalizeLauncherIcon(config.launcherIcon);
+ const welcomeMessage = typeof config.welcomeMessage === "string" ? config.welcomeMessage : "";
+ if (widgetKey) queryParams.set("widgetKey", widgetKey);
+ queryParams.set("widgetColor", widgetColor);
+ queryParams.set("welcomeMessage", welcomeMessage);
+ queryParams.set("launcherIcon", launcherIcon);
+ applyPositionStyles(bubble, position, 24);
+ applyPositionStyles(frame, position, 24);
```

### File: `Frontend/src/pages/WidgetChatPage.jsx`

- Hien trang cu lien quan bug:
  - Welcome message hardcoded.
  - Header mau hardcoded.
  - Khong doc query param color/welcome.
- Da sua:
  - Doc `widgetColor`, `welcomeMessage` tu query.
  - Validate hex mau; invalid fallback mau cu.
  - Apply welcome message cho message dau tien.
  - Apply color cho header + user bubble + send button.
- Vi sao sua:
  - De iframe widget reflect config duoc runtime truyen vao.
- Anh huong sau sua:
  - Header trong iframe dong bo mau runtime.
  - Message chao ban dau co the tuy chinh theo config.

```diff
+ const queryWidgetColor = params.get("widgetColor");
+ const queryWelcomeMessage = params.get("welcomeMessage");
+ const widgetColor = isHexColor(queryWidgetColor) ? queryWidgetColor : DEFAULT_WIDGET_COLOR;
+ const welcomeMessage = queryWelcomeMessage?.trim() || DEFAULT_WELCOME_MESSAGE;
- content: "Xin chào! Tôi có thể giúp gì cho bạn?",
+ content: welcomeMessage,
- className="... bg-blue-600 ..."
+ className="... border-b" style={{ background: widgetColor }}
```

### File: `Frontend/src/pages/chatbots/ChatbotEmbedPage.jsx`

- Hien trang cu lien quan bug:
  - Snippet khong co `launcherIcon`.
- Da sua:
  - Them `launcherIcon` vao object `window.RagChatbotConfig` generate trong snippet.
- Vi sao sua:
  - De runtime co du input consume icon option.
- Anh huong sau sua:
  - Snippet admin dong bo voi runtime field moi.

```diff
  position: form.position,
+ launcherIcon: form.launcherIcon,
  allowedOrigins: form.allowedOrigins,
```

### File: `reports/CURSOR_REPORT_11_INTEGRATION_QA_CHECKLIST_AUDIT.md`

- Hien trang cu lien quan bug: file chua ton tai trong `reports/`.
- Da sua: tao moi bang cach copy noi dung audit tu `docs/2026-05-07-integration-qa-checklist-audit.md`.
- Vi sao sua: yeu cau phu bat buoc truoc khi sua code.
- Anh huong sau sua: du bo report trong thu muc `reports/` dung ten yeu cau.

```diff
+ # Integration QA Checklist Audit (11)
+ ...
```

### File: `reports/CURSOR_REPORT_12A_WIDGET_RUNTIME_CONFIG_CONSUMPTION.md`

- Hien trang cu lien quan bug: chua co report 12A.
- Da sua: tao report chi tiet cho task nay.
- Vi sao sua: user request + can audit thay doi.
- Anh huong sau sua: reviewer co tai lieu doi chieu truoc/sau.

```diff
+ # Cursor Report 12A - Widget Runtime Config Consumption
+ ...
```

### File: `docs/2026-05-07-widget-runtime-config-consumption.md`

- Hien trang cu lien quan bug: chua co report docs theo rule 90.
- Da sua: tao report docs cung noi dung 12A.
- Vi sao sua: bat buoc theo workspace rule.
- Anh huong sau sua: dap ung quy tac report workspace.

```diff
+ # Cursor Report 12A - Widget Runtime Config Consumption
+ ...
```

## 9. Anh huong sau sua

- Behavior thay doi:
  - Runtime sau prompt doc cac field: `widgetKey/apiKey`, `frontendUrl`, `position`, `widgetColor`, `welcomeMessage`, `launcherIcon`.
  - Iframe URL sau prompt truyen query params:
    - `widgetKey` (neu co)
    - `widgetColor`
    - `welcomeMessage`
    - `launcherIcon`
  - `WidgetChatPage` consume params:
    - `widgetKey/apiKey` (giu logic cu)
    - `widgetColor` (header + user/send UI)
    - `welcomeMessage` (message khoi tao)
  - `launcherIcon` anh huong launcher bubble icon tren script runtime.
  - `position` anh huong vi tri bubble/frame trai/phai.
- Behavior giu nguyen:
  - Route `/widget` van public, khong doi.
  - Flow auth public chat van dua tren widget key/x-api-key, khong JWT moi.
  - `frontendUrl` handling van fallback `http://localhost:5173` trong widget runtime.
  - `apiKey/widgetKey` fallback va localStorage behavior khong doi.
- Dieu kien bat:
  - `position` chi chap nhan `bottom-left`, con lai fallback `bottom-right`.
  - `widgetColor` chi chap nhan hex 3/6 ky tu, invalid fallback mau hien tai.
  - `launcherIcon` chi chap nhan `chat/help/spark`, invalid fallback `chat`.
- Fallback giu:
  - Welcome fallback ve message cu khi query rong.
- Tai nguyen:
  - Memory/CPU tang khong dang ke (them parse config nho va inline style).
  - Khong doi latency/token/API cost.
  - Khong anh huong MySQL/Qdrant data.

## 10. Edge cases da xem xet

- `position` invalid/null/blank -> fallback `bottom-right`.
- `widgetColor` invalid hex -> fallback `#2563eb`.
- `launcherIcon` invalid -> fallback `chat`.
- Thieu `widgetKey/apiKey` -> giu thong bao loi hien tai trong widget chat page.
- `welcomeMessage` rong -> fallback message mac dinh.
- `allowedOrigins` khong enforce client-side -> giu backend/security validate.
- `frontendUrl` khong co -> giu fallback runtime hien tai.

## 11. Ket qua kiem tra

| Command | Ket qua | Ghi chu |
|---|---|---|
| `cd Backend && ./mvnw -DskipTests compile` | NOT RUN | Task frontend/widget runtime |
| `cd Backend && ./mvnw test` | NOT RUN | Task frontend/widget runtime |
| `cd Frontend && npm run lint` | PASS | Khong co eslint error |
| `cd Frontend && npm run build` | PASS | Build pass; co warning chunk > 500k (pre-existing) |
| `cd Frontend && npm run build:widget` | PASS | Bat buoc cho scope widget runtime |
| `docker compose config` | NOT RUN | Khong sua deploy/compose |

### Manual test checklist (expected runtime verification)

| Case | Trang thai | Ghi chu |
|---|---|---|
| snippet bottom-right | NOT RUN | Chua mo browser trong phien nay |
| snippet bottom-left | NOT RUN | Chua mo browser trong phien nay |
| color custom | NOT RUN | Chua verify visual thu cong |
| welcome message custom | NOT RUN | Chua verify visual thu cong |
| launcher icon custom | NOT RUN | Chua verify visual thu cong |
| missing config fallback | NOT RUN | Chua verify visual thu cong |

## 12. Rui ro con lai

- `allowedOrigins` van khong enforce client-side (chu y: day la chu y dung scope, khong phai bug) va phu thuoc backend validate.
- `apiKey` trong snippet van la placeholder `YOUR_PUBLIC_API_KEY`; chua co source that tu backend embed-config API.
- Manual browser verification chua chay trong task nay; da co verification build/lint/build:widget.

## 13. De xuat tiep theo

- Chay smoke test thu cong trong browser voi 6 testcase manual o tren.
- Neu can hardening them cho style input focus theo mau runtime, co the bo sung class/inline ring color nhe trong widget page.
- Neu backend sau nay expose public key source cho embed, cap nhat snippet de thay placeholder.
