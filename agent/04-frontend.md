# Frontend (React + Vite)

## Tech stack
- React 19, Vite 7, React Router 7
- Axios, TailwindCSS 4, `react-markdown` + `remark-gfm`, `uuid`
- Build: `npm run build` (app) và `npm run build:widget` (IIFE bundle)

## Thành phần chính

| File | Vai trò |
|---|---|
| `src/pages/ChatPage.jsx` | Trang chat chính (admin/dashboard) |
| `src/pages/DocumentPage.jsx` | Trang quản lý tài liệu |
| `src/pages/WidgetChatPage.jsx` | UI chat tối giản trong iframe widget |
| `src/api/axiosInstance.js` | Axios instance với `baseURL=VITE_API_URL`, timeout 30s |
| `src/api/documentApi.js` | Gọi document endpoints |
| `src/api/widgetApi.js` | `createWidget({ name, allowedOrigin, uiConfig })` → `POST /api/widgets` |
| `widget/widget.js` | Bootstrap widget: tạo bubble + iframe embed |
| `vite.widget.config.js` | Build config riêng cho IIFE bundle |

## Routes
- `/`: `ChatPage` (có nav)
- `/documents`: `DocumentPage` (có nav)
- `/widget`: `WidgetChatPage` (không có nav — hiển thị trong iframe)

## Widget nhúng

**Cơ chế**: `widget/widget.js` được build thành IIFE bundle, website ngoài nhúng script này.
Script tạo bubble chat + iframe trỏ về route `/widget`.

**Truyền API key vào widget**:
- URL param: `/widget?widgetKey=<uuid>` hoặc `/widget?apiKey=<uuid>`
- Fallback: `localStorage.getItem("widget_api_key")`
- Fallback cuối: biến môi trường `VITE_WIDGET_API_KEY` (build time)

`WidgetChatPage` lưu `queryWidgetKey` vào localStorage khi nhận được từ URL để tái sử dụng.

## Session và X-Widget-Key

- `sessionId` được tạo từ `uuid v4`, lưu trong `localStorage.getItem("widget_session_id")`.
- Tất cả request chat đều gửi header `X-Widget-Key: <widgetKey>`.
- Nếu thiếu `widgetKey`, FE hiển thị thông báo lỗi trực tiếp (không gọi API).
- Nếu backend trả `401` và key lấy từ localStorage (không phải URL) → tự động xóa key cũ.

## Chat streaming (WidgetChatPage)

Dùng `fetch` trực tiếp (không qua Axios) để handle SSE stream:

```
POST /api/chat/stream
Headers: { "Content-Type": "application/json", "X-Widget-Key": widgetKey }
Body: { sessionId, message }
```

**SSE event format**:
```
event: token
data: {"token": " text content with spaces preserved"}

event: done
data: [{"fileName":"...", "sectionTitle":"...", "chunkType":"...", "pages":"...", "chunkText":"..."}]
```

**Xử lý token**: parse JSON để giữ nguyên whitespace (khoảng trắng đầu/cuối token).
**Delay**: 30ms giữa các token để render mượt hơn.
**Cursor**: hiển thị pulse cursor khi `streaming=true`.

## Sources hiển thị
Sau khi nhận `event: done`, widget hiển thị `details` collapsible với:
- `fileName`: tên file nguồn
- `sectionTitle`: tiêu đề section
- `chunkType`: loại chunk (text / table_summary / ...)
- `pages`: số trang (nếu có)
- `chunkText`: preview nội dung chunk

## Vận hành FE
```bash
# Dev
cd Frontend && npm run dev

# Build app
npm run build

# Build widget IIFE
npm run build:widget

# Lint
npm run lint
```

## Env vars Frontend
| Biến | Mô tả | Giá trị mặc định |
|---|---|---|
| `VITE_API_URL` | Base URL của backend | `http://localhost:8080` |
| `VITE_WIDGET_API_KEY` | Widget API key dùng khi build (build-time fallback) | (rỗng) |
