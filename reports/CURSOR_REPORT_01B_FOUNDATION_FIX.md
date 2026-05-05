# Cursor Report 01B - Foundation Fix

**Ngày**: 2026-05-05  
**Scope**: Fix lint pre-existing + pageTitle/rightSlot mechanism sau Prompt 01.

---

## 1. Mức độ hiểu task

**Task này là gì**:  
Fix các vấn đề nền tảng còn tồn tại sau Prompt 01 (ghi trong `CURSOR_REPORT_01_GLOBAL_SHARED.md`):
- 11 lỗi lint pre-existing trong 4 file
- AppLayout không tự lấy `pageTitle` từ route hiện tại
- Kiểm tra lại route safety (`/widget`, `/login`, private routes)

**Phạm vi đã làm**:
- ✅ Fix 11 lint lỗi pre-existing trong `ChatPage.jsx`, `WidgetChatPage.jsx`, `vite.config.js`, `widget/widget.js`
- ✅ Implement `pageTitle` qua `useLocation()` trong `AppLayoutWithTitle` (Option A)
- ✅ npm run lint: **PASS** (0 lỗi)
- ✅ npm run build: **PASS** (345 modules, 405KB)

**Phạm vi không làm**:
- Không implement Dashboard, Chatbots, Documents, Playground, Analytics, Settings thật
- Không implement API layer / mock data
- Không thêm UI library, không đổi framework, không thêm TypeScript
- Không refactor lớn toàn project

---

## 2. Vấn đề từ report 01 đã xử lý

| Vấn đề | Trạng thái | File liên quan | Ghi chú |
|---|---|---|---|
| `ChatPage.jsx` lint: `node` unused (×4) | ✅ Fixed | `src/pages/ChatPage.jsx` | Xóa `node,` khỏi destructuring |
| `WidgetChatPage.jsx` lint: `node` unused (×4) | ✅ Fixed | `src/pages/WidgetChatPage.jsx` | Xóa `node,` khỏi destructuring |
| `vite.config.js` lint: `__dirname` not defined ESM (×1) | ✅ Fixed | `vite.config.js` | Dùng `fileURLToPath` + `dirname` |
| `widget/widget.js` lint: `apiUrl`, `title` unused (×2) | ✅ Fixed | `widget/widget.js` | Xóa 2 dòng unused variable |
| AppLayout không nhận `pageTitle` từ page con | ✅ Fixed | `src/App.jsx` | Option A: `useLocation()` map → title |
| `/widget` public safety | ✅ Verified | `src/App.jsx` | Route vẫn ngoài `PrivateRoute` |
| Build pass | ✅ PASS | — | 345 modules, 405KB |
| Lint pass | ✅ PASS | — | 0 lỗi sau fix |

---

## 3. Các file đã tham khảo

| File | Lý do đọc | Kết luận |
|---|---|---|
| `reports/CURSOR_REPORT_01_GLOBAL_SHARED.md` | Source of truth cho 11 lỗi lint và vấn đề pageTitle | Xác nhận đầy đủ 4 file cần fix, lỗi cụ thể từng dòng |
| `Frontend/src/pages/ChatPage.jsx` | Đọc để tìm chính xác các dòng `{ node, ...props }` | 4 chỗ: `table`, `th`, `td`, `p` components của ReactMarkdown |
| `Frontend/src/pages/WidgetChatPage.jsx` | Đọc để tìm chính xác các dòng `{ node, ...props }` | 4 chỗ tương tự ChatPage.jsx |
| `Frontend/vite.config.js` | Đọc để hiểu cách `__dirname` được dùng | Dùng 1 lần trong `resolve(__dirname, req.url.slice(1))` — middleware serve dist-widget |
| `Frontend/widget/widget.js` | Đọc để confirm `apiUrl`, `title` thật sự không dùng | `apiUrl` và `title` được assign nhưng không tham chiếu ở bất kỳ đâu trong file |
| `Frontend/src/App.jsx` | Đọc routing structure, `AppLayoutWithTitle`, `PAGE_TITLES` | Đã có map nhưng `AppLayoutWithTitle` chỉ return `<AppLayout />` không truyền title |
| `Frontend/src/components/layout/AppLayout.jsx` | Confirm interface `pageTitle`, `rightSlot` props | Props đã sẵn sàng nhận, chỉ cần truyền từ wrapper |
| `Frontend/src/components/layout/Header.jsx` | Confirm Header nhận `pageTitle` đúng cách | Header render `{pageTitle}` trong `<h1>` — đã đúng |

---

## 4. Các file đã thay đổi

| File | Nội dung thay đổi | Lý do | Ảnh hưởng behavior |
|---|---|---|---|
| `Frontend/src/pages/ChatPage.jsx` | Xóa `node,` khỏi 4 destructuring prop | Fix lint `no-unused-vars` | **Không** — `node` không được dùng trong body |
| `Frontend/src/pages/WidgetChatPage.jsx` | Xóa `node,` khỏi 4 destructuring prop | Fix lint `no-unused-vars` | **Không** — logic render markdown giữ nguyên hoàn toàn |
| `Frontend/vite.config.js` | Thêm `import { fileURLToPath } from 'node:url'`, thêm `dirname` vào path import, define `__filename` và `__dirname` | Fix lint `__dirname` not defined trong ESM | **Không** — behavior serve dist-widget giữ nguyên, chỉ cách lấy `__dirname` đổi |
| `Frontend/widget/widget.js` | Xóa `const apiUrl = ...` và `const title = ...` | Fix lint `no-unused-vars` | **Không** — `apiUrl` và `title` không được dùng ở bất kỳ đâu trong file |
| `Frontend/src/App.jsx` | Thêm `useLocation` import, cập nhật `AppLayoutWithTitle` dùng `useLocation()` để map pathname → title, truyền `pageTitle` vào `<AppLayout>` | Fix pageTitle mechanism | **Có** — Header giờ hiển thị đúng title theo route |

---

## 5. Chi tiết fix lint

### 5.1 ChatPage.jsx (4 lỗi)

| Lỗi | File + Dòng | Fix | Vì sao an toàn |
|---|---|---|---|
| `'node' is defined but never used` | ChatPage.jsx, `table` component | `({ node, ...props })` → `({ ...props })` | `node` là internal react-markdown prop, không bao giờ được render hay dùng trong body |
| `'node' is defined but never used` | ChatPage.jsx, `th` component | Tương tự | Tương tự |
| `'node' is defined but never used` | ChatPage.jsx, `td` component | Tương tự | Tương tự |
| `'node' is defined but never used` | ChatPage.jsx, `p` component | Tương tự | Tương tự |

### 5.2 WidgetChatPage.jsx (4 lỗi)

Tương tự ChatPage.jsx — cùng pattern, cùng cách fix.

### 5.3 vite.config.js (1 lỗi)

| Lỗi | Fix | Vì sao an toàn |
|---|---|---|
| `'__dirname' is not defined` (ESM context) | Thêm `import { fileURLToPath } from 'node:url'`; thêm `dirname` vào path import; định nghĩa `const __filename = fileURLToPath(import.meta.url)` và `const __dirname = dirname(__filename)` | Đây là pattern chuẩn Node.js ESM để có `__dirname` tương đương. Không thay đổi logic serve dist-widget. |

### 5.4 widget/widget.js (2 lỗi)

| Lỗi | Fix | Vì sao an toàn |
|---|---|---|
| `'apiUrl' is assigned a value but never used` | Xóa dòng `const apiUrl = config.apiUrl \|\| "http://localhost:8080";` | `apiUrl` không được tham chiếu ở bất kỳ đâu trong file. Widget frame.src dùng `config.frontendUrl`, không dùng `apiUrl`. |
| `'title' is assigned a value but never used` | Xóa dòng `const title = config.title \|\| "Trợ lý AI";` | `title` không được dùng trong bubble hay iframe. Header của WidgetChatPage tự render "Trợ lý AI" bên trong iframe. |

**Lưu ý**: `window.RagChatbotConfig` vẫn là public API. `config.apiUrl` và `config.title` vẫn có thể được set bởi website nhúng — chỉ là hiện tại widget.js không đọc/dùng chúng. Nếu sau này cần truyền title vào iframe qua URL param, cần thêm logic riêng.

---

## 6. Chi tiết pageTitle/rightSlot design

### Phương án chọn: Option A (useLocation + map)

**Lý do chọn Option A thay Option B**:
- App.jsx đã có sẵn `PAGE_TITLES` map nhưng chưa dùng → chỉ cần hoàn thiện
- Option B (LayoutContext) tốn thêm 1 file context, 1 hook, reset khi unmount — phức tạp hơn cần thiết ở giai đoạn này
- Option A đủ cho tất cả routes hiện tại, ít code, dễ review

### Cách AppLayout/Header nhận title

```
route change
    ↓
useLocation() trong AppLayoutWithTitle
    ↓
pathname → PAGE_TITLES map (exact match)
         → endsWith("/config") → "Chatbot Config"
         → endsWith("/embed")  → "Chatbot Embed"
         → startsWith("/chatbots") → "Chatbots" (fallback)
    ↓
<AppLayout pageTitle={title} />
    ↓
<Header pageTitle={pageTitle} />
    ↓
<h1>{pageTitle}</h1>
```

### Route → Title mapping

| Route | Title hiển thị |
|---|---|
| `/dashboard` | Dashboard |
| `/chatbots` | Chatbots |
| `/chatbots/:id/config` | Chatbot Config |
| `/chatbots/:id/embed` | Chatbot Embed |
| `/documents` | Documents |
| `/playground` | Playground |
| `/analytics` | Analytics |
| `/settings` | Settings |

### Cách page sau truyền action buttons (rightSlot)

`AppLayout` vẫn nhận `rightSlot` prop. Hiện tại `AppLayoutWithTitle` chưa truyền `rightSlot` (undefined → không render). Khi sau này cần action buttons:
- **Option đơn giản**: Tạo `LayoutContext` với `setRightSlot` → page con gọi trong `useEffect`
- **Không cần sửa AppLayout hay Header** — interface `rightSlot` đã sẵn sàng

---

## 7. Kiểm tra route/public/private

| Route | Loại | Guard | AppLayout | Ghi chú |
|---|---|---|---|---|
| `/login` | Public | Không | Không | Vào thẳng LoginPage |
| `/widget` | Public | Không | Không | WidgetChatPage render đầy đủ trong iframe |
| `/dashboard` | Private | PrivateRoute → redirect /login | Có (title: "Dashboard") | |
| `/chatbots` | Private | PrivateRoute | Có (title: "Chatbots") | |
| `/chatbots/:id/config` | Private | PrivateRoute | Có (title: "Chatbot Config") | |
| `/chatbots/:id/embed` | Private | PrivateRoute | Có (title: "Chatbot Embed") | |
| `/documents` | Private | PrivateRoute | Có (title: "Documents") | |
| `/playground` | Private | PrivateRoute | Có (title: "Playground") | |
| `/analytics` | Private | PrivateRoute | Có (title: "Analytics") | |
| `/settings` | Private | PrivateRoute | Có (title: "Settings") | |
| `*` (catch-all) | — | — | Không | Redirect /login |

**WidgetChatPage behavior**: Không bị AppLayout bọc, không cần token, logic SSE/stream giữ nguyên hoàn toàn. widget.js behavior embed giữ nguyên (chỉ xóa 2 biến không dùng).

---

## 8. Kết quả command

| Command | Kết quả | Chi tiết |
|---|---|---|
| `cd Frontend; npm run lint` | **PASS ✅** | 0 lỗi — tất cả 11 lỗi pre-existing đã được fix |
| `cd Frontend; npm run build` | **PASS ✅** | 345 modules, 10.03s, dist 405.53KB (gzip 127KB) |
| `npm run build:widget` | NOT RUN | Widget build không thuộc scope fix này; behavior widget không đổi |
| `docker compose config` | NOT RUN | Không có thay đổi infra/deploy |

**Lint output trước khi fix** (từ report 01):
```
11 lỗi trong 4 file: ChatPage.jsx (×4), WidgetChatPage.jsx (×4), vite.config.js (×1), widget/widget.js (×2)
```

**Lint output sau khi fix**:
```
> rag-chatbot-fe@0.0.0 lint
> eslint .
(exit code 0, no output = no errors)
```

---

## 9. Lỗi hoặc giới hạn còn tồn tại

| Vấn đề | Mức độ | Mô tả |
|---|---|---|
| `rightSlot` chưa có mechanism để page con inject | 🟢 Thấp | AppLayout/Header đã sẵn sàng nhận `rightSlot`, nhưng `AppLayoutWithTitle` chưa forward. Cần LayoutContext khi page cần action buttons (không thuộc scope prompt này). |
| `window.RagChatbotConfig.apiUrl` và `.title` bị đọc nhưng không dùng | 🟢 Thấp | Public API vẫn hoạt động (ai set cũng không crash), nhưng widget.js chưa forward title vào iframe. Nếu sau này muốn dynamic title → cần truyền qua URL param của iframe src. |
| `isMobile` trong AppLayout dùng `window.innerWidth` tại render time | 🟢 Thấp | Không reactive với resize. Pre-existing từ report 01, không thuộc scope prompt này. |
| Mock Login token không expire | 🟢 Thấp | Pre-existing, không thuộc scope. |

---

## 10. Đề xuất prompt tiếp theo

**Build và lint đã PASS** → sẵn sàng tiếp tục.

**Đề xuất Prompt 02**: Implement API layer / mock data foundation:
- Tạo `src/mocks/` với mock data cơ bản (chatbots, documents, sessions) để Dashboard/Chatbots/Documents dùng được mà không cần backend
- Tạo `src/api/` modules: `chatbotsApi.js`, `documentsApi.js`, `analyticsApi.js`
- Pattern: axiosInstance đã có Bearer interceptor sẵn → chỉ cần wrap endpoint

**Hoặc Prompt 02A**: Implement rightSlot mechanism via LayoutContext:
- Tạo `src/contexts/LayoutContext.jsx` với `setRightSlot` + `clearRightSlot`
- Update `AppLayoutWithTitle` để forward `rightSlot` từ context
- Cần trước khi implement các page có action buttons (Chatbots → "Tạo chatbot" button, Documents → "Upload" button)
