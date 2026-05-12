# Cursor Report 06 - Chatbot Embed

## 1. Mức độ hiểu task

- **Task là gì?** Biến placeholder `ChatbotEmbedPage` thành trang cấu hình embed widget thật, tại route `/chatbots/:id/embed`, bao gồm: form cấu hình widget, live preview, embed code snippet, tab bar Config | Embed, save config.
- **Hiểu task:** 98%
- **Phần chắc chắn:** Toàn bộ UI fields, API integration (mock), embed snippet format, component structure, tab bar, loading/error state.
- **Phần còn giả định:**
  - `launcherIcon` chắc chắn không có trong backend contract thật (không thấy trong endpoint spec). Đã gửi trong payload mock nhưng ghi rõ gap.
  - `apiKey` trong embed snippet là placeholder vì backend không expose public api key qua embed-config endpoint hiện tại.
  - `VITE_FRONTEND_URL` env var có thể chưa được set, fallback sang `window.location.origin`.
- **Phạm vi không làm:** Không sửa widget.js, WidgetChatPage, Dashboard, Chatbot Management, Chatbot Config, không implement public chat runtime.

---

## 2. Checklist mapping

| Chatbot Embed checklist item | Status | File liên quan | Ghi chú |
|---|---|---|---|
| Route `/chatbots/:id/embed` | ✅ Done | `src/App.jsx` (đã có sẵn) | Route đã đăng ký từ Prompt 05 |
| Color picker | ✅ Done | `EmbedSettingsSection.jsx` | `input[type=color]` + text hex input |
| Welcome message textarea | ✅ Done | `EmbedSettingsSection.jsx` | Có char count, max 200 |
| Position dropdown bottom-right/bottom-left | ✅ Done | `EmbedSettingsSection.jsx` | Select với 2 options |
| Launcher icon selector | ✅ Done | `EmbedSettingsSection.jsx` | 3 options: chat/help/spark (UI only) |
| CORS allowed origins tag input | ✅ Done | `AllowedOriginsInput.jsx` | |
| Add origin on Enter | ✅ Done | `AllowedOriginsInput.jsx` | `onKeyDown Enter` |
| Remove origin with × | ✅ Done | `AllowedOriginsInput.jsx` | Button `×` trên mỗi tag |
| Read-only embed script code block | ✅ Done | `EmbedCodeBlock.jsx` | `<pre>` read-only |
| Copy to clipboard button | ✅ Done | `EmbedCodeBlock.jsx` | `navigator.clipboard` + fallback |
| Live widget preview realtime | ✅ Done | `WidgetLivePreview.jsx` | Reflect widgetColor, welcomeMessage, position, launcherIcon |
| Save config button | ✅ Done | `EmbedSettingsSection.jsx` + Header rightSlot | Cả trong card lẫn header |
| `chatbotsApi.getEmbedConfig(id)` | ✅ Done | `ChatbotEmbedPage.jsx` | Load khi mount |
| `chatbotsApi.updateEmbedConfig(id, payload)` | ✅ Done | `ChatbotEmbedPage.jsx` | Gọi khi save |
| Tab bar Config \| Embed | ✅ Done | `ChatbotConfigTabs.jsx` (reused) | |
| Config tab link `/chatbots/:id/config` | ✅ Done | `ChatbotConfigTabs.jsx` | NavLink |
| Embed active tab | ✅ Done | `ChatbotConfigTabs.jsx` | NavLink isActive |
| Loading skeleton | ✅ Done | `ChatbotEmbedSkeleton.jsx` | Two-column skeleton |
| Error state | ✅ Done | `ChatbotEmbedPage.jsx` | Hard error card + partial warning banner |
| Public endpoint note: POST `/api/public/chat` validates x-api-key no JWT | ✅ Noted | Xem mục 6 & 7 | Endpoint đã có ở API layer, snippet có placeholder `apiKey` |

---

## 3. Các file đã tham khảo

| File | Mục đích đọc | Kết luận chính |
|---|---|---|
| `src/api/chatbotsApi.js` | Xác nhận `getEmbedConfig`, `updateEmbedConfig` tồn tại | Đã có đầy đủ, mock dùng `embedConfigs` in-memory |
| `src/mocks/chatbotsMock.js` | Xem shape của `embedConfigs` | Fields: `widgetColor`, `welcomeMessage`, `position`, `allowedOrigins` — không có `launcherIcon` |
| `src/pages/chatbots/ChatbotEmbedPage.jsx` | Xem placeholder hiện tại | Chỉ có stub 7 dòng |
| `src/pages/chatbots/components/ChatbotConfigTabs.jsx` | Reuse tab bar | Component đầy đủ, export default, nhận prop `id` |
| `src/pages/chatbots/ChatbotConfigPage.jsx` | Tham khảo pattern (useLayout, form state, save handler) | Pattern chuẩn: useLayout + useParams + useCallback + toast |
| `widget/widget.js` | Xác định field nào widget runtime đang consume | Chỉ đọc `widgetKey`/`apiKey` và `frontendUrl`. Không đọc `widgetColor`, `welcomeMessage`, `position` |
| `src/contexts/LayoutContext.jsx` | API useLayout | `setPageTitle`, `setRightSlot`, `clearRightSlot`, `resetLayout` |
| `src/components/common/SkeletonLoader.jsx` | Xem variant có sẵn | variant: card / table / line. Không dùng trực tiếp, tạo custom skeleton |
| `src/components/common/useToast.js` | API toast | `toast.success()`, `toast.error()` |
| `src/App.jsx` | Kiểm tra route | Route `/chatbots/:id/embed` đã đăng ký sẵn |

---

## 4. Các file đã thay đổi

| File | Nội dung thay đổi | Lý do | Ảnh hưởng behavior cũ |
|---|---|---|---|
| `src/pages/chatbots/ChatbotEmbedPage.jsx` | Thay toàn bộ placeholder bằng implementation đầy đủ | Implement tính năng mới | Không — placeholder không có logic |
| `src/pages/chatbots/components/ChatbotEmbedSkeleton.jsx` | File mới | Loading state | Không |
| `src/pages/chatbots/components/AllowedOriginsInput.jsx` | File mới | CORS origins tag input | Không |
| `src/pages/chatbots/components/EmbedCodeBlock.jsx` | File mới | Read-only code + copy | Không |
| `src/pages/chatbots/components/WidgetLivePreview.jsx` | File mới | Live preview | Không |
| `src/pages/chatbots/components/EmbedSettingsSection.jsx` | File mới | Widget settings form card | Không |

**Không sửa:** `chatbotsApi.js`, `chatbotsMock.js`, `ChatbotConfigTabs.jsx`, `App.jsx`, `widget.js`, `WidgetChatPage.jsx`.

---

## 5. Component design

### ChatbotEmbedPage
- Lấy `id` từ `useParams()`.
- `Promise.allSettled([getEmbedConfig, getChatbot])` — nếu embed config fail thì show warning + default form; nếu getChatbot fail thì không có tên bot nhưng trang vẫn hoạt động.
- `setPageTitle("${bot.name} — Embed")` khi bot load.
- `setRightSlot(<Save button>)` — disabled khi `saving || loading`.
- Cleanup `clearRightSlot` khi unmount.
- Renders: skeleton → error → main (`EmbedSettingsSection` + `WidgetLivePreview` + `EmbedCodeBlock`).
- `buildSnippet()` — tạo snippet động từ form state, dùng `FRONTEND_URL` từ env hoặc `window.location.origin`.

### EmbedSettingsSection
- Card "Widget Settings" với 5 fields: color picker (native input[type=color] + hex text), welcome message textarea (max 200 + char count), position select, launcher icon button group, AllowedOriginsInput.
- Save button disabled khi hex invalid hoặc đang saving.

### AllowedOriginsInput
- Tag input: nhập origin → Enter → validate regex `^https?://...` → add tag.
- Remove bằng `×`.
- Không cho duplicate (exact match).
- Trim whitespace.
- Inline error message khi invalid.
- Helper text: "Press Enter to add an origin."

### EmbedCodeBlock
- `<pre>` read-only với snippet được truyền qua prop.
- Copy dùng `navigator.clipboard.writeText` với fallback `document.execCommand("copy")`.
- Hiển thị "Copying…" khi đang copy.
- Toast success/error.

### WidgetLivePreview
- Mock browser chrome (3 colored circles + URL bar).
- Simulated page content (mờ, pointer-events-none).
- Widget overlay: bubble launcher + mini chat window.
- Chat window có header (màu theo `widgetColor`), welcome bubble, input bar.
- `position` control `right-3` vs `left-3`.
- `LauncherSvg` inline SVG — không dùng icon library.

### ChatbotEmbedSkeleton
- Two-column layout (settings card left, preview+code right) với animate-pulse.

### ChatbotConfigTabs (reuse)
- Được reuse nguyên vẹn — không sửa.

---

## 6. API integration

| Câu hỏi | Trả lời |
|---|---|
| `getEmbedConfig(id)` gọi ở đâu | `ChatbotEmbedPage.jsx` trong `loadData()` khi mount |
| `updateEmbedConfig(id, payload)` gọi ở đâu | `ChatbotEmbedPage.jsx` trong `handleSave()` |
| Payload save gồm field nào | `{ widgetColor, welcomeMessage, position, allowedOrigins, launcherIcon }` |
| `launcherIcon` có được gửi không | Có, trong mock. Xem ghi chú dưới |
| Sau save local state cập nhật thế nào | Response từ `updateEmbedConfig` được merge qua `toFormState({ ...form, ...updated })` |
| `publicChatApi` có được gọi không | Không — trang này chỉ là admin config UI, không cần chat runtime |

**launcherIcon trong payload:** Đã gửi trong mock/dev. Mock dùng `Object.assign(embedConfigs[id], payload)` nên field này được lưu an toàn vào in-memory store. Tuy nhiên, **backend contract thật** (`PUT /api/chatbots/:id/embed-config`) không rõ có nhận `launcherIcon` không (spec chỉ nêu 4 fields chuẩn). Xem mục 13 về giới hạn này.

**Public endpoint note:** `POST /api/public/chat` đã implement ở API layer (Backend `WidgetController`, `WidgetAuthFilter`) — validates `x-api-key` header, không cần JWT. Embed snippet đã có placeholder `apiKey: "YOUR_PUBLIC_API_KEY"`. Admin cần thay bằng api key thật khi lấy được từ backend.

---

## 7. Embed snippet design

```html
<script>
  window.RagChatbotConfig = {
    "chatbotId": "cb-001",
    "apiKey": "YOUR_PUBLIC_API_KEY",
    "frontendUrl": "https://your-frontend-domain.com",
    "widgetColor": "#3b82f6",
    "welcomeMessage": "Xin chào! Tôi có thể giúp gì cho bạn?",
    "position": "bottom-right",
    "allowedOrigins": ["https://example.com"]
  };
</script>
<script async src="https://your-frontend-domain.com/chatbot-widget.js"></script>
```

| Câu hỏi | Trả lời |
|---|---|
| Global config tên gì | `window.RagChatbotConfig` — giữ nguyên theo project |
| Script src lấy từ đâu | `${FRONTEND_URL}/chatbot-widget.js` (env `VITE_FRONTEND_URL` hoặc `window.location.origin`) |
| `apiKey` là placeholder hay thật | **Placeholder** `"YOUR_PUBLIC_API_KEY"` — backend không expose key qua embed-config API hiện tại |
| `frontendUrl` dùng thế nào | Widget dùng `config.frontendUrl` để build iframe src: `${frontendUrl}/widget?widgetKey=...` |
| Widget.js hiện consume những field nào | Chỉ `widgetKey`/`apiKey` (để build iframe src) và `frontendUrl` (base URL của iframe). Bubble SVG là hardcoded. |
| Field admin config mà widget runtime chưa consume | `widgetColor`, `welcomeMessage`, `position`, `allowedOrigins` — admin UI đã có nhưng widget runtime hiện **không đọc** những field này. Xem mục 13. |

---

## 8. Data shape assumptions

| Field | Assumption | Fallback |
|---|---|---|
| `widgetColor` | hex string `#rrggbb` hoặc `#rgb` | `config.widgetColor \|\| config.color \|\| "#2563eb"` |
| `welcomeMessage` | string | `""` |
| `position` | `"bottom-right"` hoặc `"bottom-left"` | fallback `"bottom-right"` nếu không phải `"bottom-left"` |
| `allowedOrigins` | `string[]` | `Array.isArray(config.allowedOrigins) ? config.allowedOrigins : []` |
| `launcherIcon` | `"chat"` \| `"help"` \| `"spark"` | `config.launcherIcon \|\| "chat"` |
| Mock `embedConfigs` shape | `{ widgetColor, welcomeMessage, position, allowedOrigins }` | Không có `launcherIcon` trong mock ban đầu — default to `"chat"` |

---

## 9. Validation

| Field | Validation | Xử lý lỗi |
|---|---|---|
| Hex color | Regex `/^#([0-9A-Fa-f]{3}\|[0-9A-Fa-f]{6})$/` | Inline error text dưới input; Save button disabled |
| Origin | Regex `/^https?:\/\/[a-zA-Z0-9.-]+(:\d+)?(\/.*)?$/` | Inline error trong `AllowedOriginsInput` |
| Duplicate origin | `origins.includes(value)` | Inline error "This origin is already in the list." |
| Empty origins | Cho phép — không có minimum | Helper text giải thích ý nghĩa |
| Welcome message | Max 200 chars (`.slice(0, 200)`) | Char counter hiển thị `n/200` |

---

## 10. Responsive behavior

| Tình huống | Xử lý |
|---|---|
| Desktop (≥lg) | 2-column grid: settings left, preview+code right |
| Tablet/Mobile (<lg) | Single column: settings → preview → code block |
| Preview overflow | Preview box có `min-height: 280px`, widget overlay absolute-positioned, không overflow container |
| Code block horizontal scroll | `overflow-x: auto` trong `EmbedCodeBlock` — `<pre>` với `whitespace: pre` cuộn ngang |
| Header rightSlot trên mobile | AppLayout xử lý rightSlot — button "Save" compact (`px-3 py-1.5`) |

---

## 11. Cách test thủ công

```bash
# 1. Chạy dev server
cd Frontend && npm run dev

# 2. Truy cập
http://localhost:5173

# 3. Login (mock: bất kỳ email/password)
# 4. Vào /chatbots → click Config của "Customer Support Bot" → tab Embed
# Hoặc trực tiếp: http://localhost:5173/chatbots/cb-001/embed
```

**Test cases:**

1. **Load config:** Trang load → skeleton hiện → form điền từ mock (`#3b82f6`, welcome message, "bottom-right", allowedOrigins đã có).
2. **Đổi color:** Chọn color picker hoặc nhập hex → preview bubble + header cập nhật ngay.
3. **Welcome message realtime:** Gõ welcome message → preview chat bubble cập nhật ngay.
4. **Position:** Đổi sang "Bottom Left" → preview widget chuyển sang góc trái.
5. **Launcher icon:** Click "Help" → preview bubble icon đổi.
6. **Add origin Enter:** Focus input → gõ `https://test.com` → Enter → tag xuất hiện.
7. **Remove origin ×:** Click `×` trên tag → tag biến mất.
8. **Invalid origin:** Gõ `not-a-url` → Enter → inline error hiện.
9. **Duplicate origin:** Thêm origin đã tồn tại → error "already in the list".
10. **Copy snippet:** Click "📋 Copy" → toast "Embed snippet copied to clipboard!".
11. **Save config:** Click "💾 Save Config" hoặc "💾 Save" ở header → toast "Embed config saved!".
12. **Tab Config navigate:** Click "Config" tab → navigate tới `/chatbots/cb-001/config` không reload.
13. **Loading/error:** Tạm thời test với id không tồn tại (`/chatbots/cb-999/embed`) → partial warning banner + default values (vì code dùng `allSettled`).

---

## 12. Kết quả command

| Command | Kết quả | Ghi chú |
|---|---|---|
| `cd Frontend && npm run lint` | ✅ PASS | Fix 1 lỗi: `no-useless-escape` trong `AllowedOriginsInput.jsx` (`\-` → `-`) |
| `cd Frontend && npm run build` | ✅ PASS | 431 modules, build 30s. Warning chunk >500kB (pre-existing, không phải từ task này) |
| `cd Frontend && npm run build:widget` | NOT RUN | Không sửa widget.js, không cần |
| Backend compile | NOT RUN | Task thuần frontend |
| Docker compose config | NOT RUN | Không sửa infra |

---

## 13. Lỗi hoặc giới hạn còn tồn tại

### Widget runtime chưa consume đủ config fields

Widget.js hiện chỉ đọc 2 fields từ `window.RagChatbotConfig`:
- `widgetKey` / `apiKey` → build iframe URL
- `frontendUrl` → base URL của iframe và script src

**Không đọc:** `widgetColor`, `welcomeMessage`, `position`, `allowedOrigins`, `launcherIcon`.

Hệ quả: Admin embed config UI đã hoạt động và lưu đúng, nhưng bubble color, welcome message, position, launcher icon hiện tại **không được apply ở widget runtime**. Widget.js dùng hardcoded SVG, hardcoded class `rag-chatbot-bubble`/`rag-chatbot-frame`. Đây là tech debt cần fix ở Prompt 08+ (Widget Runtime Enhancement) nếu cần.

### launcherIcon không trong backend contract thật

`launcherIcon` là UI field chỉ dùng cho admin preview. Backend contract thật (`PUT /api/chatbots/:id/embed-config`) theo spec chỉ có 4 fields: `widgetColor`, `welcomeMessage`, `position`, `allowedOrigins`. Mock `Object.assign` cho phép merge field ngoài contract nên không crash, nhưng khi deploy real backend thì `launcherIcon` sẽ bị bỏ qua (hoặc gây 400 nếu backend strict). Cần confirm với backend.

### apiKey là placeholder

Embed snippet hiển thị `"YOUR_PUBLIC_API_KEY"`. Backend (`WidgetController`) đọc `x-api-key` header từ widget iframe request, nhưng admin không có cách lấy api key từ embed-config API hiện tại. Nếu backend có endpoint trả public api key (widgetKey), cần cập nhật `buildSnippet` để điền vào.

### VITE_FRONTEND_URL có thể chưa được set

`buildSnippet` fallback sang `window.location.origin`. Ở dev sẽ là `http://localhost:5173` — đúng cho dev. Production cần đặt `VITE_FRONTEND_URL` trong `.env.production`.

---

## 14. Đề xuất prompt tiếp theo

Build và lint đã pass. Đề xuất chạy **Prompt 07 — Documents Page** (`/documents`).

Ngoài ra, nếu muốn widget runtime thực sự consume `widgetColor`, `welcomeMessage`, `position` từ config, cần **Prompt 08 — Widget Runtime Enhancement** để sửa `widget.js` và `WidgetChatPage.jsx`.
