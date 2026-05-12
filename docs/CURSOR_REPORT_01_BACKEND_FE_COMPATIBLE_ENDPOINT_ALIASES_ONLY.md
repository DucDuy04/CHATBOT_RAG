# Cursor Report 01 - BACKEND_FE_COMPATIBLE_ENDPOINT_ALIASES_ONLY

Bản này mirror nội dung report chính theo rule nội bộ `docs/`.

Vui lòng xem chi tiết đầy đủ tại:

- `reports/CURSOR_REPORT_01_BACKEND_FE_COMPATIBLE_ENDPOINT_ALIASES_ONLY.md`

## Kết luận nhanh
- Đã thêm alias:
  - `POST /api/public/chat`
  - `POST /api/chatbots`
- Đã giữ nguyên endpoint cũ:
  - `/api/chat`, `/api/chat/stream`, `/api/widgets`, `/api/documents/upload/{widgetId}`
- Đã block (không implement trong prompt này):
  - `POST /api/playground/chat`
  - `POST /api/documents/upload`
- Compile backend: PASS
- Backend test: FAIL do môi trường DB connection refused (ngoài scope alias prompt)
