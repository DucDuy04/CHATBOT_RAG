# Cursor Report 00 - Project Audit

**Ngày**: 2026-05-05  
**Người thực hiện**: Senior Frontend Engineer / Tech Lead (AI)  
**Scope**: Audit codebase Frontend, lập kế hoạch triển khai feature checklist.

---

## 1. Mức độ hiểu task

**Hiểu task**: 92%

**Task này là gì**:
- Đọc và audit toàn bộ codebase Frontend hiện tại.
- Xác định tech stack đang dùng.
- So sánh hiện trạng với feature checklist được yêu cầu.
- Đề xuất cấu trúc thư mục và kế hoạch triển khai từng bước.

**Phạm vi KHÔNG làm ở bước này**:
- Không implement bất kỳ feature nào.
- Không tạo mock API.
- Không refactor code hiện tại.
- Không cài thêm package.
- Không sửa logic hiện có.

**Điểm còn thiếu thông tin**:
- Feature checklist được đề cập là "source of truth" nhưng **không có file checklist trong repo**. Report này dựa trên danh sách section từ prompt: Global/Shared → Dashboard → Chatbot Management → Chatbot Config → Chatbot Embed → Documents → Playground → Analytics → Settings.
- Chưa rõ API Backend cho Dashboard (stats), Analytics (logs) đã có chưa — cần xác nhận trước khi implement.

---

## 2. Công nghệ hiện tại của project

| Hạng mục | Công nghệ | Phiên bản | Ghi chú |
|---|---|---|---|
| **Framework** | React | 19.2.0 | Stable |
| **Build tool** | Vite + @vitejs/plugin-react-swc | 7.3.1 | SWC (nhanh) |
| **Routing** | React Router DOM | 7.13.1 | BrowserRouter, Routes/Route |
| **Styling** | TailwindCSS | 4.2.2 | `@tailwind base/components/utilities` in index.css |
| **HTTP client** | Axios | 1.13.6 | axiosInstance với baseURL=VITE_API_URL |
| **HTTP stream** | Native `fetch` | — | Dùng riêng cho SSE (chat stream) |
| **Markdown** | react-markdown + remark-gfm | 10.1.0 / 4.0.1 | Render markdown + tables |
| **UUID** | uuid | 13.0.0 | Tạo sessionId |
| **State management** | Không có | — | Chỉ dùng `useState` / `useEffect` local |
| **Global store** | Không có | — | Dữ liệu lưu qua `localStorage` |
| **Lint** | ESLint v9 | 9.39.1 | Flat config, react-hooks + react-refresh plugins |
| **Prettier** | Không có | — | Chưa setup |
| **Test** | Không có | — | Không có Vitest/Jest/Testing Library |
| **TypeScript** | Không dùng | — | Thuần JS/JSX |
| **PostCSS** | Có | — | Dùng kèm Tailwind |
| **Widget build** | Vite IIFE | — | `vite.widget.config.js` → `dist-widget/chatbot-widget.js` |

---

## 3. Các file/thư mục đã tham khảo

| File/Thư mục | Mục đích đọc | Kết luận |
|---|---|---|
| `Frontend/package.json` | Dependencies, scripts | React 19 + Vite 7, có lint, không có test/Prettier |
| `Frontend/vite.config.js` | Proxy, plugin | `/api` proxy → localhost:8080; serve widget dist dev server |
| `Frontend/vite.widget.config.js` | Widget build config | IIFE output → `dist-widget/chatbot-widget.js` |
| `Frontend/tailwind.config.js` | Tailwind setup | Đơn giản, không có custom theme |
| `Frontend/eslint.config.js` | Lint rules | Flat config v9, no-unused-vars |
| `Frontend/src/main.jsx` | Entry point | StrictMode, mount App vào #root |
| `Frontend/src/App.jsx` | Routing + Layout | Nav inline trong App, 3 routes: `/`, `/documents`, `/widget` |
| `Frontend/src/index.css` | Global styles | TailwindCSS directives + scrollbar custom |
| `Frontend/src/pages/ChatPage.jsx` | Page chat admin | Playground-like, input widget key inline, SSE streaming |
| `Frontend/src/pages/DocumentPage.jsx` | Page documents | Upload file, tạo widget, list docs — logic dày trong page |
| `Frontend/src/pages/WidgetChatPage.jsx` | Widget iframe UI | Chat UI tối giản, nhận widgetKey qua URL param |
| `Frontend/src/api/axiosInstance.js` | HTTP client | Axios, baseURL từ VITE_API_URL, timeout 30s |
| `Frontend/src/api/documentApi.js` | Document API | uploadDocument, getDocuments |
| `Frontend/src/api/widgetApi.js` | Widget API | createWidget |
| `Frontend/widget/widget.js` | Widget embed | IIFE: bubble + iframe, nhận config từ `window.RagChatbotConfig` |
| `README.md` | Project docs | Chỉ là Vite template README mặc định |
| `agent/01-overview.md` đến `06-operations.md` | Architecture context | Multi-tenant widget, backend API endpoints |

---

## 4. Hiện trạng codebase

### 4a. Đã có những phần nào

```
Frontend/
├── src/
│   ├── App.jsx          ✅ Routing cơ bản (3 routes)
│   ├── main.jsx         ✅ Entry point
│   ├── index.css        ✅ TailwindCSS
│   ├── pages/
│   │   ├── ChatPage.jsx          ✅ Playground (prototype)
│   │   ├── DocumentPage.jsx      ✅ Documents + Widget tạo (prototype)
│   │   └── WidgetChatPage.jsx    ✅ Widget iframe UI (hoàn thiện nhất)
│   └── api/
│       ├── axiosInstance.js      ✅
│       ├── documentApi.js        ✅ upload + list
│       └── widgetApi.js          ✅ createWidget
└── widget/
    └── widget.js                 ✅ IIFE embed script
```

### 4b. Thiếu so với feature checklist

| Feature | Trạng thái | Ghi chú |
|---|---|---|
| **Global / Shared** | ❌ Thiếu | Không có component dùng chung (Button, Badge, Modal, Table...) |
| **Layout** | ❌ Thiếu | Nav nhúng thẳng vào `App.jsx`, không có Sidebar, không có AppShell |
| **Dashboard** | ❌ Chưa có | Cần card stats (số widget, số doc, số chat...) |
| **Chatbot Management** | ❌ Chưa có | List/create/delete widget configs (chỉ có "Tạo widget mới" embedded trong DocumentPage) |
| **Chatbot Config** | ❌ Chưa có | Edit widget (name, allowedOrigin, uiConfig, themeColor) |
| **Chatbot Embed** | ❌ Chưa có | Trang hướng dẫn nhúng widget + snippet code |
| **Documents** | 🟡 Prototype | Có upload + list nhưng không có filter theo widget, không có delete, UX thô |
| **Playground** | 🟡 Prototype | `ChatPage.jsx` hoạt động nhưng đang dùng như admin tool, không phải playground trực quan |
| **Analytics** | ❌ Chưa có | Backend chưa có endpoint analytics |
| **Settings** | ❌ Chưa có | Không có trang settings |
| **State management** | ❌ Thiếu | Dữ liệu widget key/config chỉ qua localStorage, không có global store |
| **Mock API** | ❌ Thiếu | Không có mock layer cho dev offline |

### 4c. Rủi ro kỹ thuật

| Rủi ro | Mức độ | Mô tả |
|---|---|---|
| Logic dày trong pages | 🟡 Trung bình | `DocumentPage.jsx` mix API call, state, UI một chỗ → khó test, khó tái sử dụng |
| Không có global state | 🟡 Trung bình | `widgetConfigId`, `widgetApiKey` lưu localStorage, dùng trong nhiều page → cần Context hoặc store |
| Nav nhúng trong App.jsx | 🟡 Trung bình | Mở rộng route mới sẽ phải edit `App.jsx` nhiều → nên tách Layout component |
| Không có TypeScript | 🟡 Trung bình | Không có type safety, dễ nhầm API response shape |
| Không có test | 🔴 Cao | Không có unit test hay integration test, thêm feature dễ regress |
| Không có Prettier | 🟢 Thấp | Format code không nhất quán nhưng không ảnh hưởng runtime |
| Analytics backend chưa có | 🔴 Cao | Feature Analytics sẽ cần mock hoặc chờ backend implement |
| `GET /api/documents` trả tất cả | 🟡 Trung bình | Không filter theo widget → khi có nhiều widget sẽ hiển thị nhầm documents |

---

## 5. Đề xuất cấu trúc triển khai

Nguyên tắc:
- **Giữ nguyên** `widget/` và `WidgetChatPage.jsx` — đã hoàn thiện.
- **Tái sử dụng** logic SSE từ `ChatPage.jsx` và `WidgetChatPage.jsx` khi build Playground.
- **Tách dần** không refactor toàn bộ một lúc.
- **Không dùng TypeScript** ở giai đoạn này (giữ JS/JSX để di chuyển nhanh).
- **State management**: dùng React Context đơn giản cho widget config (không cần Zustand nếu scope nhỏ).

```
Frontend/
├── index.html
├── vite.config.js                ← giữ nguyên
├── vite.widget.config.js         ← giữ nguyên
├── tailwind.config.js            ← giữ nguyên
├── eslint.config.js              ← giữ nguyên
├── package.json                  ← thêm package nếu cần (vitest?)
│
├── widget/                       ← giữ nguyên
│   ├── widget.js
│   └── widget.css
│
└── src/
    ├── main.jsx                  ← giữ nguyên
    ├── index.css                 ← giữ nguyên
    ├── App.jsx                   ← refactor nhẹ: thêm routes mới, dùng AppLayout
    │
    ├── components/
    │   ├── common/               ← [MỚI] UI atoms/molecules dùng chung
    │   │   ├── Button.jsx
    │   │   ├── Badge.jsx
    │   │   ├── Card.jsx
    │   │   ├── Modal.jsx
    │   │   ├── Table.jsx
    │   │   ├── Spinner.jsx
    │   │   ├── EmptyState.jsx
    │   │   └── CopyButton.jsx    ← dùng cho Embed snippet
    │   │
    │   └── layout/               ← [MỚI] Layout shells
    │       ├── AppLayout.jsx     ← sidebar + topbar + outlet
    │       ├── Sidebar.jsx
    │       ├── Topbar.jsx
    │       └── WidgetLayout.jsx  ← layout no-nav cho /widget (đang inline trong App.jsx)
    │
    ├── pages/
    │   ├── ChatPage.jsx          ← giữ nguyên (rename sang Playground sau)
    │   ├── DocumentPage.jsx      ← giữ nguyên tạm, refactor ở prompt Documents
    │   ├── WidgetChatPage.jsx    ← giữ nguyên
    │   │
    │   ├── dashboard/            ← [MỚI]
    │   │   └── DashboardPage.jsx
    │   │
    │   ├── chatbots/             ← [MỚI] Chatbot Management + Config + Embed
    │   │   ├── ChatbotListPage.jsx
    │   │   ├── ChatbotCreatePage.jsx
    │   │   ├── ChatbotDetailPage.jsx
    │   │   ├── ChatbotConfigPage.jsx
    │   │   └── ChatbotEmbedPage.jsx
    │   │
    │   ├── documents/            ← [MỚI] replace DocumentPage.jsx
    │   │   └── DocumentsPage.jsx
    │   │
    │   ├── playground/           ← [MỚI] replace ChatPage.jsx
    │   │   └── PlaygroundPage.jsx
    │   │
    │   ├── analytics/            ← [MỚI]
    │   │   └── AnalyticsPage.jsx
    │   │
    │   └── settings/             ← [MỚI]
    │       └── SettingsPage.jsx
    │
    ├── api/
    │   ├── axiosInstance.js      ← giữ nguyên
    │   ├── documentApi.js        ← giữ nguyên
    │   ├── widgetApi.js          ← mở rộng: getWidgets, getWidgetById, updateWidget, deleteWidget
    │   ├── chatApi.js            ← [MỚI] chat sync (nếu cần)
    │   └── analyticsApi.js       ← [MỚI] (mock trước)
    │
    ├── context/                  ← [MỚI] React Context cho global state
    │   └── WidgetContext.jsx     ← currentWidgetId, currentApiKey, setCurrentWidget
    │
    └── mocks/                    ← [MỚI] Mock data cho dev offline
        ├── widgetMocks.js
        ├── documentMocks.js
        └── analyticsMocks.js
```

---

## 6. Kế hoạch triển khai theo từng prompt nhỏ

Mỗi prompt = 1 đơn vị công việc nhỏ, có thể review độc lập.

| # | Prompt | Nội dung | Phụ thuộc |
|---|---|---|---|
| **00** | ✅ Project Audit | Report này | — |
| **01** | Global/Shared - Common Components | Button, Badge, Card, Spinner, EmptyState, Modal, CopyButton | — |
| **02** | Global/Shared - Layout | AppLayout, Sidebar, Topbar, WidgetLayout; refactor App.jsx routing | Prompt 01 |
| **03** | Global/Shared - Context | WidgetContext (currentWidget global state) + mocks cơ bản | — |
| **04** | Dashboard | DashboardPage: cards thống kê (widgets, docs, sessions), dùng mock nếu backend chưa có | 01, 02, 03 |
| **05** | Chatbot Management | ChatbotListPage, ChatbotCreatePage (gọi POST /api/widgets) | 01, 02, 03 |
| **06** | Chatbot Config | ChatbotDetailPage + ChatbotConfigPage (edit name/origins/uiConfig) | 05 |
| **07** | Chatbot Embed | ChatbotEmbedPage: snippet code generator, copy button, preview | 05, 06 |
| **08** | Documents | DocumentsPage mới: filter theo widget, upload, list, status badge, delete (nếu API có) | 02, 03, 05 |
| **09** | Playground | PlaygroundPage: chat UI full-featured, chọn widget, SSE stream, sources | 02, 03, 05 |
| **10** | Analytics | AnalyticsPage: charts (mock → real khi backend sẵn) | 02, 03 |
| **11** | Settings | SettingsPage: cấu hình hệ thống (API URL, theme...) | 02 |
| **12** | Polish | Responsive mobile, loading states nhất quán, error boundary, empty states | Tất cả |

---

## 7. Lưu ý cho prompt tiếp theo

### Prompt 01 nên làm gì
- Tạo `src/components/common/` với các atoms: `Button.jsx`, `Badge.jsx`, `Card.jsx`, `Spinner.jsx`, `EmptyState.jsx`, `Modal.jsx`, `CopyButton.jsx`.
- Tất cả dùng TailwindCSS, không dùng thư viện UI ngoài (giữ nhẹ bundle).
- Export named export, không dùng default export cho components (dễ barrel import).
- Tạo `src/components/common/index.js` barrel file.

### Prompt 01 cần tránh gì
- Không thêm thư viện UI (shadcn/ui, Ant Design, MUI...) nếu chưa có sự đồng ý — sẽ làm nặng bundle và xung đột với Tailwind v4.
- Không refactor `App.jsx` hoặc pages hiện có ở prompt này.
- Không thêm TypeScript.
- Không cài thêm package ngoài nếu không cần thiết.

### Về state management
- **Đề xuất dùng React Context** (`WidgetContext`) thay vì Zustand/Redux cho scope hiện tại — đủ nhẹ và không cần thêm package.
- Nếu sau này cần nhiều store hơn (auth, user settings...) thì xem xét Zustand.

### Về mock API
- Analytics và Dashboard stats chưa có backend endpoint → cần mock layer ở `src/mocks/`.
- Các endpoint đã có backend (widgets, documents, chat) → không cần mock, gọi thật qua Axios.

### Về `GET /api/documents`
- Hiện trả tất cả documents không filter widget → `DocumentsPage` mới cần filter client-side theo `widgetConfigId`, hoặc đề xuất backend thêm query param `?widgetId=`.

### Về widget flow hiện tại
- `WidgetChatPage.jsx` đã hoàn thiện và đúng → **không động vào**.
- `widget/widget.js` IIFE embed → **không động vào**.
- Chỉ cần build thêm UI admin ở các trang mới.

---

## Tóm tắt nhanh

```
Đã có:  React 19 + Vite 7 + TailwindCSS 4 + React Router 7 + Axios
         3 pages hoạt động được (ChatPage, DocumentPage, WidgetChatPage)
         Widget IIFE embed hoàn thiện

Thiếu:  Layout/Sidebar, Common components, Global state (Context)
         Dashboard, Chatbot Management, Chatbot Config, Chatbot Embed
         Analytics, Settings
         Mock API, Test framework, Prettier

Không nên:  Thêm TypeScript ngay, dùng UI library ngoài, refactor lớn giai đoạn đầu

Bước tiếp:  Prompt 01 — Global/Shared Common Components
```
