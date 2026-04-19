# Frontend (React + Vite)

## Thành phần chính
- `src/pages/ChatPage.jsx`: trang chat chính.
- `src/pages/DocumentPage.jsx`: trang tài liệu.
- `src/pages/WidgetChatPage.jsx`: UI chat cho route `/widget`.
- `src/api/axiosInstance.js`, `src/api/documentApi.js`: lớp gọi API.

## Widget nhúng
- Bundle widget được build dạng IIFE từ `vite.widget.config.js`.
- Script `widget/widget.js` tạo bubble chat + iframe.
- Iframe trỏ về route `/widget` để render `WidgetChatPage`.

## Session và streaming
- FE lưu `sessionId` trong `localStorage` để giữ ngữ cảnh hội thoại.
- Chat streaming dùng `POST /api/chat/stream` và xử lý SSE:
  - `event: token` để append text realtime.
  - `event: done` để kết thúc stream và cập nhật sources.

## Vận hành FE
- Local dev: chạy Vite.
- Build app: `npm run build`.
- Build widget: `npm run build:widget` (theo cấu hình của dự án).
