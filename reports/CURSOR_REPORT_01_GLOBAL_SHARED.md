# Cursor Report 01 - Global Shared Foundation

**Ngày**: 2026-05-05  
**Scope**: Global / Shared foundation — layout, components, auth, axios, routing.

---

## 1. Mức độ hiểu task

**Task này là gì**:  
Xây dựng toàn bộ nền móng chia sẻ (Global / Shared) cho admin frontend: layout shell, shared components, Axios foundation với auth interceptor, Zustand auth store, PrivateRoute, LoginPage mock, App.jsx routing đầy đủ với placeholder pages.

**Phạm vi đã làm**:
- ✅ Cài Zustand
- ✅ `authStore.js` (token, user, login, logout, persist localStorage)
- ✅ `axiosInstance.js` cập nhật: Bearer token interceptor + global error handler → Toast
- ✅ `Toast.jsx` + `ToastProvider` + `ToastRegister` + `ToastContext.js` + `toastSingleton.js` + `useToast.js`
- ✅ `AppLayout.jsx` (Sidebar 188px + Header 48px + scrollable Outlet)
- ✅ `Sidebar.jsx` (nav items, active state, collapse tablet, hamburger mobile)
- ✅ `Header.jsx` (pageTitle, rightSlot, hamburger)
- ✅ `Modal.jsx` (portal, Escape, overlay click, focus trap cơ bản)
- ✅ `Drawer.jsx` (slide-in right, overlay, Escape)
- ✅ `ConfirmDeleteModal.jsx` (destructive action, loading state)
- ✅ `SkeletonLoader.jsx` (variants: line, card, table)
- ✅ `EmptyState.jsx` (icon, title, message, CTA)
- ✅ `ErrorBoundary.jsx` (class component, fallback UI, reset + reload)
- ✅ `StatusBadge.jsx` (Active, Inactive, Warn, Info, Synced, Failed, Pending, Processing, Completed)
- ✅ `PrivateRoute.jsx` (redirect /login nếu không có token)
- ✅ `LoginPage.jsx` (Mock Login button dev)
- ✅ 8 placeholder pages với AppLayout
- ✅ `App.jsx` refactored: public + private routes đầy đủ
- ✅ Build PASS, lint clean cho tất cả file mới

**Phạm vi chưa làm** (theo yêu cầu):
- Dashboard thật, Chatbot Management thật, Documents mới, Playground mới, Analytics, Settings
- Auth backend thật (chỉ mock)
- Không sửa `widget/widget.js`, `WidgetChatPage.jsx` (logic chính), `vite.config.js`

---

## 2. Checklist mapping

| Checklist item | Status | File liên quan | Ghi chú |
|---|---|---|---|
| AppLayout | ✅ Done | `components/layout/AppLayout.jsx` | Sidebar + Header + Outlet |
| Sidebar fixed 188px | ✅ Done | `components/layout/Sidebar.jsx` | 188px desktop |
| Sidebar active state | ✅ Done | `Sidebar.jsx` | NavLink `isActive` → blue-600 bg |
| Sidebar collapse 1024px | ✅ Done | `Sidebar.jsx` + `AppLayout.jsx` | w-14 icon-only khi ≤1023px |
| Sidebar hamburger 768px | ✅ Done | `Sidebar.jsx` + `Header.jsx` | Hidden <768px, toggle qua mobileOpen state |
| Header 48px | ✅ Done | `components/layout/Header.jsx` | `h-12` = 48px |
| Header pageTitle/right-slot | ✅ Done | `Header.jsx` | props `pageTitle`, `rightSlot` |
| Toast success/error/warning | ✅ Done | `components/common/Toast.jsx` | + info variant |
| Toast auto-dismiss 3s | ✅ Done | `Toast.jsx` | duration default 3000ms |
| Toast top-right | ✅ Done | `Toast.jsx` | `fixed top-4 right-4` |
| Modal portal/focus/Escape/overlay | ✅ Done | `components/common/Modal.jsx` | portal, Escape, overlay click, focus tabIndex |
| Drawer | ✅ Done | `components/common/Drawer.jsx` | slide-in right, overlay, Escape |
| ConfirmDeleteModal | ✅ Done | `components/common/ConfirmDeleteModal.jsx` | reuses Modal, destructive style |
| SkeletonLoader | ✅ Done | `components/common/SkeletonLoader.jsx` | variants: line/card/table |
| EmptyState | ✅ Done | `components/common/EmptyState.jsx` | icon + title + message + CTA |
| ErrorBoundary | ✅ Done | `components/common/ErrorBoundary.jsx` | class component, Thử lại + Tải lại |
| StatusBadge | ✅ Done | `components/common/StatusBadge.jsx` | 9 variants |
| Axios Bearer interceptor | ✅ Done | `src/api/axiosInstance.js` | lấy `auth_token` từ localStorage |
| Axios global error handler | ✅ Done | `axiosInstance.js` | map status → message → toastSingleton.error |
| PrivateRoute | ✅ Done | `src/routes/PrivateRoute.jsx` | kiểm tra token từ authStore |
| Zustand authStore | ✅ Done | `src/stores/authStore.js` | token, user, login(), logout(), hydrate localStorage |

---

## 3. Các file đã tham khảo

| File | Lý do đọc |
|---|---|
| `Frontend/package.json` | Kiểm tra dependencies trước khi cài Zustand |
| `Frontend/src/App.jsx` (cũ) | Hiểu routing hiện tại trước khi refactor |
| `Frontend/src/api/axiosInstance.js` (cũ) | Cập nhật thay vì tạo mới |
| `Frontend/src/pages/ChatPage.jsx` | Hiểu logic SSE, widgetKey pattern |
| `Frontend/src/pages/WidgetChatPage.jsx` | Kiểm tra KHÔNG phá khi refactor routing |
| `Frontend/src/pages/DocumentPage.jsx` | Kiểm tra import/deps trước khi thay routing |
| `Frontend/eslint.config.js` | Hiểu rule trước khi fix lint |
| `reports/CURSOR_REPORT_00_PROJECT_AUDIT.md` | Source of truth cho kế hoạch |

---

## 4. Các file đã thay đổi/tạo mới

| File | Thay đổi | Lý do |
|---|---|---|
| `Frontend/package.json` | Thêm `zustand` | Checklist yêu cầu Zustand authStore |
| `src/stores/authStore.js` | **Tạo mới** | Zustand store: token, user, login, logout, persist |
| `src/api/axiosInstance.js` | **Cập nhật** | Thêm Bearer interceptor + global error handler |
| `src/components/common/ToastContext.js` | **Tạo mới** | Tách context riêng để tránh react-refresh lint |
| `src/components/common/Toast.jsx` | **Tạo mới** | ToastProvider, ToastRegister, ToastContainer, ToastItem |
| `src/components/common/toastSingleton.js` | **Tạo mới** | Singleton fn cho axiosInstance dùng ngoài React tree |
| `src/components/common/useToast.js` | **Tạo mới** | Hook `useToast()` cho React components |
| `src/components/common/Modal.jsx` | **Tạo mới** | Portal modal, Escape, overlay, focus trap |
| `src/components/common/Drawer.jsx` | **Tạo mới** | Slide-in right drawer |
| `src/components/common/ConfirmDeleteModal.jsx` | **Tạo mới** | Reusable destructive confirm |
| `src/components/common/SkeletonLoader.jsx` | **Tạo mới** | line/card/table variants |
| `src/components/common/EmptyState.jsx` | **Tạo mới** | Empty state với CTA |
| `src/components/common/ErrorBoundary.jsx` | **Tạo mới** | Class component, reset + reload |
| `src/components/common/StatusBadge.jsx` | **Tạo mới** | 9 status variants |
| `src/components/common/index.js` | **Tạo mới** | Barrel export cho tất cả common |
| `src/components/layout/Sidebar.jsx` | **Tạo mới** | Nav với active state, collapse, hamburger |
| `src/components/layout/Header.jsx` | **Tạo mới** | 48px header, pageTitle, rightSlot |
| `src/components/layout/AppLayout.jsx` | **Tạo mới** | Shell layout với Sidebar + Header + Outlet |
| `src/routes/PrivateRoute.jsx` | **Tạo mới** | Token guard → /login |
| `src/pages/LoginPage.jsx` | **Tạo mới** | Login với Mock Login dev button |
| `src/pages/dashboard/DashboardPage.jsx` | **Tạo mới** | Placeholder |
| `src/pages/chatbots/ChatbotsPage.jsx` | **Tạo mới** | Placeholder |
| `src/pages/chatbots/ChatbotConfigPage.jsx` | **Tạo mới** | Placeholder |
| `src/pages/chatbots/ChatbotEmbedPage.jsx` | **Tạo mới** | Placeholder |
| `src/pages/documents/DocumentsPage.jsx` | **Tạo mới** | Placeholder |
| `src/pages/playground/PlaygroundPage.jsx` | **Tạo mới** | Placeholder |
| `src/pages/analytics/AnalyticsPage.jsx` | **Tạo mới** | Placeholder |
| `src/pages/settings/SettingsPage.jsx` | **Tạo mới** | Placeholder |
| `src/App.jsx` | **Refactored** | Public + private routes, ToastProvider wrapper |

---

## 5. Package thay đổi

| Package | Phiên bản | Lý do |
|---|---|---|
| `zustand` | latest (^5.x) | Checklist item 17: Zustand authStore |

Không thêm UI library ngoài (không MUI, Ant Design, shadcn). Không thêm TypeScript.

---

## 6. Routing sau khi sửa

**Public routes** (không cần auth):
| Route | Component | Ghi chú |
|---|---|---|
| `/login` | `LoginPage` | Mock Login button cho dev |
| `/widget` | `WidgetChatPage` | Giữ nguyên, không auth guard |

**Private routes** (cần token, bọc PrivateRoute + AppLayout):
| Route | Component |
|---|---|
| `/` | Redirect → `/dashboard` |
| `/dashboard` | `DashboardPage` (placeholder) |
| `/chatbots` | `ChatbotsPage` (placeholder) |
| `/chatbots/:id/config` | `ChatbotConfigPage` (placeholder) |
| `/chatbots/:id/embed` | `ChatbotEmbedPage` (placeholder) |
| `/documents` | `DocumentsPage` (placeholder) |
| `/playground` | `PlaygroundPage` (placeholder) |
| `/analytics` | `AnalyticsPage` (placeholder) |
| `/settings` | `SettingsPage` (placeholder) |

**Catch-all**: `*` → redirect `/login`

**Route bị loại bỏ**: Route cũ `/documents` trỏ `DocumentPage.jsx` prototype không còn trong App.jsx. File `DocumentPage.jsx` và `ChatPage.jsx` vẫn còn trong `src/pages/` nhưng không được route nữa.

---

## 7. Auth behavior

| Hành vi | Chi tiết |
|---|---|
| Token lưu ở đâu | `localStorage["auth_token"]` |
| User lưu ở đâu | `localStorage["auth_user"]` (JSON) |
| `login(token, user)` | Set localStorage + Zustand state |
| `logout()` | Remove localStorage + clear Zustand state |
| `PrivateRoute` kiểm tra | `useAuthStore(s => s.token)` — nếu null → redirect `/login` |
| `/widget` có bị guard không | **Không** — route public, WidgetChatPage không dùng PrivateRoute |
| Mock Login | Set token `"mock-token-dev-only"` + user `{name: "Dev User"}` → navigate `/dashboard` |
| Hydrate khi reload | `authStore` đọc localStorage trong constructor → token persist qua F5 |

---

## 8. Axios behavior

| Hành vi | Chi tiết |
|---|---|
| Bearer token lấy từ đâu | `localStorage.getItem("auth_token")` trong request interceptor |
| Không có token | Request gửi bình thường, không có Authorization header |
| Status 400 | Toast error: "Dữ liệu gửi lên không hợp lệ." |
| Status 401 | Toast error + xóa localStorage token + redirect `/login` (tránh loop: check `pathname !== /login`) |
| Status 403 | Toast error: "Bạn không có quyền..." |
| Status 404 | Toast error: "Không tìm thấy dữ liệu." |
| Status 409 | Toast error: "Dữ liệu bị xung đột." |
| Status 422 | Toast error: "Dữ liệu không hợp lệ." |
| Status 500 | Toast error: "Lỗi máy chủ. Vui lòng thử lại sau." |
| Backend message override | Nếu `response.data.message` hoặc `response.data.error` tồn tại → dùng thay status map |
| Toast được gọi ở đâu | `toastSingleton.error(message)` trong response interceptor |
| `toastSingleton` kết nối React | `ToastRegister` component mount trong `App.jsx` → gọi `registerToastFn` |

---

## 9. Cách kiểm tra thủ công

```bash
# 1. Start dev server
cd Frontend
npm run dev
```

**Kiểm tra LoginPage**:
- Vào `http://localhost:5173` → redirect về `/login`
- Thấy LoginPage với Mock Login button
- Click "Mock Login" → navigate `/dashboard`
- Refresh trang → vẫn còn ở `/dashboard` (token persist)

**Kiểm tra Sidebar desktop (>1024px)**:
- Sidebar width 188px bên trái
- Nav items: Dashboard, Chatbots, Documents, Playground, Analytics, Settings
- Active item highlight màu xanh theo route hiện tại
- Click từng item → route thay đổi, active state cập nhật

**Kiểm tra responsive tablet (768px–1023px)**:
- Resize browser xuống ~900px
- Sidebar collapse về icon-only (w-14 = 56px)
- Icons hiển thị, labels ẩn
- Tooltip label khi hover icon (`title` attribute)

**Kiểm tra responsive mobile (<768px)**:
- Resize xuống <768px
- Sidebar ẩn hoàn toàn
- Hamburger ☰ hiện trong Header
- Click hamburger → sidebar slide in từ trái
- Click overlay hoặc nav item → đóng sidebar

**Kiểm tra /widget không vỡ**:
- Vào `http://localhost:5173/widget`
- WidgetChatPage render đúng, không có sidebar/header
- Không cần auth (public route)

**Kiểm tra logout**:
- Từ bất kỳ trang nào, xóa `auth_token` từ DevTools localStorage
- Refresh → redirect `/login`

**Kiểm tra Toast**:
- Trong console DevTools: `localStorage.removeItem("auth_token")` → trigger một Axios call → Toast error xuất hiện top-right

---

## 10. Kết quả build/lint

| Command | Kết quả | Chi tiết |
|---|---|---|
| `npm run build` | **PASS** ✅ | 345 modules, 13.42s, dist 405KB |
| `npm run lint` | **FAIL** (11 lỗi, tất cả pre-existing) | 0 lỗi trong file mới tạo |

**Chi tiết 11 lỗi lint pre-existing** (không thuộc scope prompt này):
| File | Lỗi | Ghi chú |
|---|---|---|
| `ChatPage.jsx` (×4) | `node` unused trong ReactMarkdown components | File prototype cũ, không sửa per checklist |
| `WidgetChatPage.jsx` (×4) | `node` unused trong ReactMarkdown components | **Không được sửa** theo yêu cầu |
| `vite.config.js` (×1) | `__dirname` not defined (ESM context) | Pre-existing config issue |
| `widget/widget.js` (×2) | `apiUrl`, `title` unused | **Không được sửa** theo yêu cầu |

---

## 11. Lỗi hoặc giới hạn còn tồn tại

| Vấn đề | Mức độ | Mô tả |
|---|---|---|
| 11 lint lỗi pre-existing | 🟡 Trung bình | Trong 4 file không được sửa. Cần fix ở prompt sau nếu được phép |
| `AppLayout` không nhận `pageTitle` từ page con | 🟢 Thấp | Hiện tại placeholder pages không truyền title. Mỗi page phải truyền qua `<AppLayout pageTitle="...">` hoặc cần context/hook riêng |
| Responsive toggle dùng `window.innerWidth` | 🟢 Thấp | Không reactivity với resize thực sự; dùng ResizeObserver hoặc custom hook sẽ tốt hơn |
| Mock Login token không expire | 🟢 Thấp | Token `"mock-token-dev-only"` không có expiry; phù hợp cho dev nhưng cần thay bằng JWT thật khi implement auth |
| `GET /api/documents` trả tất cả widgets | 🟡 Trung bình | `DocumentsPage` mới sẽ cần filter hoặc backend cần thêm `?widgetId=` param |

---

## 12. Đề xuất prompt tiếp theo

**Prompt 02 nên làm gì**: Triển khai **Dashboard** page thật (Prompt 04 trong kế hoạch):
- Cards thống kê: tổng số chatbots, tổng documents, tổng sessions.
- Backend hiện chưa có `/api/dashboard/stats` → dùng mock data từ `src/mocks/`.
- Dùng `SkeletonLoader` khi loading, `EmptyState` khi không có data.
- Fix pageTitle mechanism: mỗi page truyền `pageTitle` lên Header (có thể dùng `usePageTitle` hook hoặc props qua context).

**Trước Prompt 02 cần làm nếu có thời gian**:
1. Fix 11 lint pre-existing nếu được phép sửa `ChatPage.jsx` và `WidgetChatPage.jsx` (chỉ xóa `node,` trong destructuring — không ảnh hưởng logic).
2. Tạo `src/mocks/` với mock data cơ bản cho Dashboard.
3. Implement `usePageTitle` hook hoặc quy ước truyền `pageTitle` từ page → AppLayout.
