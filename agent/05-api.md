# API tham chiếu nhanh

## Documents

### `POST /api/documents/upload`
- Mục đích: upload và xử lý file tài liệu.
- Input: `multipart/form-data`, field `file`.
- Output: thông tin trạng thái xử lý (`id`, `fileName`, `status`, `message`).

### `GET /api/documents`
- Mục đích: lấy danh sách tài liệu đã upload.

## Chat

### `POST /api/chat`
- Mục đích: chat đồng bộ.
- Request:
```json
{
  "sessionId": "string",
  "message": "string"
}
```
- Response:
```json
{
  "answer": "string",
  "sources": []
}
```

### `POST /api/chat/stream`
- Mục đích: chat dạng streaming SSE.
- Request tương tự endpoint sync.
- Events trả về:
  - `token`: chunk nội dung câu trả lời.
  - `done`: đánh dấu kết thúc, kèm danh sách nguồn.

## Widget (backend support)

### `POST /api/widgets`
- Mục đích: tạo `widgetConfig` để dùng cho ingest tài liệu.
- Request:
```json
{
  "name": "Widget tuyển sinh",
  "allowedOrigin": ["https://abc.edu.vn"],
  "uiConfig": {
    "themeColor": "#2563eb"
  }
}
```
- Response có các trường chính:
  - `widgetConfigId`
  - `apiKey`
  - `uploadEndpoint` (ví dụ: `/api/documents/upload/{widgetConfigId}`)

### `GET /api/widgets/{apiKey}`
- Mục đích: lấy cấu hình widget theo API key.

## Ingest theo widget

### `POST /api/documents/upload/{widgetId}`
- Mục đích: upload tài liệu gắn với đúng `widgetConfigId`.
- Input: `multipart/form-data`, field `file`.
