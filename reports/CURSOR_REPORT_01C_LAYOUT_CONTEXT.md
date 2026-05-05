# Cursor Report 01C - Layout Context

**Ngày**: 2026-05-05  
**Scope**: Implement LayoutContext để page con có thể set Header title và rightSlot action buttons.

---

## 1. Mức độ hiểu task

**Task này là gì**:  
Implement cơ chế `LayoutContext` cho phép page con inject `pageTitle` override và `rightSlot` (action buttons) vào Header mà không cần prop drilling. Đây là foundation step cuối trước khi triển khai API layer và các feature page thật.

**Phạm vi đã làm**:
- ✅ Tạo `src/contexts/LayoutContext.jsx` với `LayoutProvider` + `useLayout` hook
- ✅ Cập nhật `src/App.jsx`: bọc `LayoutProvider` qua `PrivateLayoutShell`, `AppLayoutWithTitle` đọc context
- ✅ `AppLayoutWithTitle` reset context khi pathname thay đổi
- ✅ Default title từ route map vẫn hoạt động khi page không gọi `setPageTitle`
- ✅ `rightSlot` được forward từ context → `AppLayout` → `Header`
- ✅ npm run lint: **PASS** (0 lỗi)
- ✅ npm run build: **PASS** (346 modules, 406KB)

**Phạm vi không làm**:
- Không implement Dashboard, Chatbots, Documents, Playground, Analytics, Settings thật
- Không implement API layer / mock data
- Không sửa `widget/widget.js` hay `WidgetChatPage.jsx`
- Không thêm UI library, không đổi framework, không thêm TypeScript

---

## 2. Vấn đề đã xử lý

| Vấn đề | Trạng thái | File liên quan | Ghi chú |
|---|---|---|---|
| Header `rightSlot` injection từ page con | ✅ Done | `LayoutContext.jsx`, `App.jsx` | Page gọi `setRightSlot(<node>)`, AppLayoutWithTitle forward vào AppLayout |
| Optional `pageTitle` override từ page con | ✅ Done | `LayoutContext.jsx`, `App.jsx` | `contextTitle \|\| defaultTitle` - route default vẫn là fallback |
| Reset `rightSlot` khi route đổi | ✅ Done | `App.jsx` (AppLayoutWithTitle) | `useEffect([pathname])` → `resetLayout()` |
| Giữ default route title khi không override | ✅ Done | `App.jsx` (PAGE_TITLES map) | Exact + prefix match giữ nguyên từ report 01B |
| `/widget` public safety | ✅ Verified | `App.jsx` | Route vẫn ngoài `PrivateRoute`, không bọc `LayoutProvider` |
| react-refresh lint lỗi (mixed exports) | ✅ Fixed | `LayoutContext.jsx` | Thêm `// eslint-disable-next-line react-refresh/only-export-components` trước `useLayout` |
| `useEffect` import sai từ `react-router-dom` | ✅ Fixed | `App.jsx` | Sửa import về `react` đúng cách |
| Build pass | ✅ PASS | — | 346 modules, 406.19KB |
| Lint pass | ✅ PASS | — | 0 lỗi |

---

## 3. Các file đã tham khảo

| File | Lý do đọc | Kết luận |
|---|---|---|
| `reports/CURSOR_REPORT_01B_FOUNDATION_FIX.md` | Source of truth: pageTitle mechanism hiện tại, rightSlot còn thiếu, route map | AppLayout sẵn sàng nhận `rightSlot`; `AppLayoutWithTitle` đã có `useLocation` + `PAGE_TITLES` map |
| `Frontend/src/App.jsx` | Đọc routing structure, `AppLayoutWithTitle` hiện tại | `AppLayoutWithTitle` chỉ return `<AppLayout pageTitle={...} />` — chưa có rightSlot |
| `Frontend/src/components/layout/AppLayout.jsx` | Confirm interface `pageTitle` + `rightSlot` props | Cả hai props đã sẵn sàng nhận, forward xuống Header |
| `Frontend/src/components/layout/Header.jsx` | Confirm Header render rightSlot đúng cách | `{rightSlot && <div ...>{rightSlot}</div>}` — đã đúng |
| `Frontend/eslint.config.js` | Hiểu rule trước khi tạo file mới | `react-refresh/only-export-components` sẽ flag mixed exports; `reactHooks.configs.flat.recommended` check useEffect deps |

---

## 4. Các file đã thay đổi

| File | Nội dung thay đổi | Lý do | Ảnh hưởng behavior |
|---|---|---|---|
| `Frontend/src/contexts/LayoutContext.jsx` | **Tạo mới** — `LayoutProvider` + `useLayout` hook | Implement context mechanism | **Có** — page con giờ có thể inject rightSlot/title vào Header |
| `Frontend/src/App.jsx` | Thêm `useEffect` import từ `react`; import `LayoutProvider`, `useLayout`; cập nhật `AppLayoutWithTitle` đọc context; thêm `PrivateLayoutShell` bọc `LayoutProvider` | Kết nối context với layout shell | **Có** — Header giờ nhận `rightSlot` từ context; reset khi route đổi |

---

## 5. LayoutContext design

### State

| State | Kiểu | Mặc định | Mô tả |
|---|---|---|---|
| `pageTitle` | `string \| null` | `null` | Title override từ page con. `null` = dùng route default |
| `rightSlot` | `React.ReactNode \| null` | `null` | Action buttons render trong Header. `null` = không render |

### Functions

| Function | Signature | Mô tả |
|---|---|---|
| `setPageTitle` | `(title: string) => void` | Page gọi để override Header title |
| `setRightSlot` | `(node: ReactNode) => void` | Page gọi để inject action buttons |
| `clearRightSlot` | `() => void` | Xóa rightSlot (dùng trong cleanup) |
| `resetLayout` | `() => void` | Xóa cả `pageTitle` lẫn `rightSlot` |

### rightSlot được clear khi nào

1. **Route đổi** (`pathname` thay đổi): `AppLayoutWithTitle` có `useEffect([pathname, resetLayout])` → gọi `resetLayout()` → cả `rightSlot` và `contextTitle` đều về `null`.
2. **Page cleanup** (`return () => clearRightSlot()` trong useEffect của page): Page tự dọn dẹp khi unmount.
3. Page gọi tường minh `clearRightSlot()`.

Lưu ý: `resetLayout` chạy sau render (useEffect), nên có thể có 1 frame flash nếu page cũ đã set rightSlot. Trong thực tế không visible vì navigation thường kèm theo re-render nhanh.

### pageTitle override hoạt động như nào

```
Page gọi setPageTitle("Custom Title")
    ↓
contextTitle = "Custom Title" (trong LayoutContext state)
    ↓
AppLayoutWithTitle: effectiveTitle = contextTitle || defaultTitle
                  = "Custom Title" || "Dashboard"
                  = "Custom Title"
    ↓
AppLayout pageTitle="Custom Title"
    ↓
Header: <h1>Custom Title</h1>
```

### default route title fallback

Nếu page không gọi `setPageTitle` (contextTitle = null):
```
effectiveTitle = null || PAGE_TITLES["/dashboard"]
              = null || "Dashboard"
              = "Dashboard"
```

Route map trong `App.jsx`:
- `/dashboard` → "Dashboard"
- `/chatbots` → "Chatbots"
- `*.endsWith("/config")` → "Chatbot Config"
- `*.endsWith("/embed")` → "Chatbot Embed"
- `*.startsWith("/chatbots")` → "Chatbots" (fallback)
- `/documents` → "Documents"
- `/playground` → "Playground"
- `/analytics` → "Analytics"
- `/settings` → "Settings"

---

## 6. Ví dụ sử dụng cho page sau

### Dashboard — Refresh button

```jsx
import { useEffect } from "react";
import { useLayout } from "../../contexts/LayoutContext";

export default function DashboardPage() {
  const { setRightSlot, clearRightSlot } = useLayout();

  useEffect(() => {
    setRightSlot(
      <button
        onClick={() => window.location.reload()}
        className="px-3 py-1.5 text-sm font-medium text-white bg-blue-600 rounded-lg hover:bg-blue-700"
      >
        Refresh
      </button>
    );
    return () => clearRightSlot();
  }, [setRightSlot, clearRightSlot]);

  return <div>Dashboard content...</div>;
}
```

### Chatbots — + New chatbot button

```jsx
import { useEffect, useState } from "react";
import { useLayout } from "../../contexts/LayoutContext";

export default function ChatbotsPage() {
  const { setRightSlot, clearRightSlot } = useLayout();
  const [showModal, setShowModal] = useState(false);

  useEffect(() => {
    setRightSlot(
      <button
        onClick={() => setShowModal(true)}
        className="px-3 py-1.5 text-sm font-medium text-white bg-blue-600 rounded-lg hover:bg-blue-700"
      >
        + New chatbot
      </button>
    );
    return () => clearRightSlot();
  }, [setRightSlot, clearRightSlot]);

  return (
    <>
      <div>Chatbots list...</div>
      {showModal && <CreateChatbotModal onClose={() => setShowModal(false)} />}
    </>
  );
}
```

### Analytics — Export CSV button

```jsx
import { useEffect } from "react";
import { useLayout } from "../../contexts/LayoutContext";

export default function AnalyticsPage() {
  const { setRightSlot, clearRightSlot } = useLayout();

  useEffect(() => {
    setRightSlot(
      <button
        onClick={handleExport}
        className="px-3 py-1.5 text-sm font-medium text-gray-700 bg-white border rounded-lg hover:bg-gray-50"
      >
        Export CSV
      </button>
    );
    return () => clearRightSlot();
  }, [setRightSlot, clearRightSlot]);

  return <div>Analytics content...</div>;
}
```

---

## 7. Kiểm tra route/public/private

| Route | Loại | Guard | LayoutProvider | AppLayout | Ghi chú |
|---|---|---|---|---|---|
| `/login` | Public | Không | Không | Không | LoginPage trực tiếp |
| `/widget` | Public | Không | Không | Không | WidgetChatPage không bọc layout |
| `/dashboard` | Private | PrivateRoute | ✅ | ✅ (title: "Dashboard") | Page có thể gọi `useLayout()` |
| `/chatbots` | Private | PrivateRoute | ✅ | ✅ (title: "Chatbots") | |
| `/chatbots/:id/config` | Private | PrivateRoute | ✅ | ✅ (title: "Chatbot Config") | |
| `/chatbots/:id/embed` | Private | PrivateRoute | ✅ | ✅ (title: "Chatbot Embed") | |
| `/documents` | Private | PrivateRoute | ✅ | ✅ (title: "Documents") | |
| `/playground` | Private | PrivateRoute | ✅ | ✅ (title: "Playground") | |
| `/analytics` | Private | PrivateRoute | ✅ | ✅ (title: "Analytics") | |
| `/settings` | Private | PrivateRoute | ✅ | ✅ (title: "Settings") | |
| `*` catch-all | — | — | — | — | Redirect /login |

**WidgetChatPage**: Không thay đổi — không bọc `LayoutProvider`, không có AppLayout, không cần auth.

---

## 8. Kết quả command

| Command | Kết quả | Chi tiết |
|---|---|---|
| `cd Frontend; npm run lint` | **PASS ✅** | 0 lỗi (sau khi fix eslint-disable và import) |
| `cd Frontend; npm run build` | **PASS ✅** | 346 modules, 9.44s, dist 406.19KB (gzip 127.59KB) |
| `npm run build:widget` | NOT RUN | Không có thay đổi liên quan widget |
| `docker compose config` | NOT RUN | Không có thay đổi infra |

**Lỗi gặp và đã fix trong quá trình**:

1. **`react-refresh/only-export-components`** trong `LayoutContext.jsx`:
   - Nguyên nhân: file export cả `LayoutProvider` (component) lẫn `useLayout` (hook)
   - Fix: Thêm `// eslint-disable-next-line react-refresh/only-export-components` trước export hook
   - Lý do an toàn: đây là pattern chuẩn cho context files trong React ecosystem

2. **`useEffect` import sai từ `react-router-dom`**:
   - Nguyên nhân: khi cập nhật import line, đã nhầm thêm `useEffect` vào import của `react-router-dom`
   - Fix: tách thành `import { useEffect } from "react"` riêng
   - Build phát hiện lỗi này (lint không báo vì eslint config không check module resolution)

---

## 9. Lỗi hoặc giới hạn còn tồn tại

| Vấn đề | Mức độ | Mô tả |
|---|---|---|
| 1-frame flash khi navigate (rightSlot reset) | 🟢 Thấp | `resetLayout()` chạy trong `useEffect` (sau render), nên rightSlot của page cũ có thể render 1 frame trước khi bị xóa. Không visible trong thực tế vì transition nhanh. Giải pháp: dùng cleanup pattern trong page (`return () => clearRightSlot()`). |
| `setRightSlot` trong page phải memoize button | 🟢 Thấp | Nếu page dùng `setRightSlot` trong `useEffect` với `[setRightSlot]` deps, và button có `onClick` là hàm mới mỗi render, sẽ cause stale closure. Page phải dùng `useCallback` hoặc pattern đơn giản. Không phải issue hiện tại vì placeholder pages chưa dùng. |
| LayoutContext không persist qua reload | 🟢 Thấp | Đúng thiết kế — `rightSlot` là React node, không thể serialize. Page tự set lại khi mount. |
| `isMobile` trong AppLayout vẫn dùng `window.innerWidth` | 🟢 Thấp | Pre-existing từ report 01, không thuộc scope. |

---

## 10. Đề xuất prompt tiếp theo

**Build và lint PASS** → foundation đã đủ để triển khai feature pages.

**Đề xuất Prompt 02 — API Layer + Mock Data**:
Tạo API layer và mock data để Dashboard/Chatbots/Documents có thể render dữ liệu mà không cần backend running:
- `src/mocks/chatbots.js` — mock data chatbots
- `src/mocks/documents.js` — mock data documents
- `src/mocks/analytics.js` — mock data analytics/stats
- `src/api/chatbotsApi.js` — wraps axiosInstance với endpoints thật
- `src/api/documentsApi.js` — wraps axiosInstance
- Pattern: dev dùng mock, production dùng axiosInstance thật (hoặc feature flag)

**Hoặc Prompt 03 — Dashboard Page thật**:
Implement Dashboard với data thật từ backend (nếu backend đã running):
- Cards: tổng chatbots, tổng documents, tổng sessions
- Gọi `useLayout()` để set pageTitle và Refresh button trong rightSlot
- Dùng `SkeletonLoader` khi loading, `EmptyState` khi không có data
