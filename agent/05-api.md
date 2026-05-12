# API tham chiếu nhanh

## Xác thực

### Chat endpoints (`/api/chat/**`)
**Bắt buộc** gửi header:
```
X-Widget-Key: <widgetApiKey>  (UUID string)
```
Backend xác thực qua `WidgetAuthFilter`. Thiếu hoặc sai key → `HTTP 401`.

### Document và Widget endpoints
Hiện không yêu cầu auth (permitAll). Dùng cho admin tạo widget và upload tài liệu.

---

## Widget

### `POST /api/widgets`
- Mục đích: tạo `WidgetConfig` mới.
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
- Response (`WidgetCreateResponse`):
```json
{
  "id": "uuid-widget-config-id",
  "name": "Widget tuyển sinh",
  "apiKey": "uuid-api-key",
  "allowedOrigin": ["https://abc.edu.vn"],
  "uiConfig": { "themeColor": "#2563eb" },
  "isActive": true
}
```
- **Lưu ý**: `apiKey` là UUID dùng làm `X-Widget-Key`. Cần lưu lại ngay khi tạo.
- **Không có** endpoint `GET /api/widgets/{apiKey}` hay `GET /api/widgets/{id}` trong codebase hiện tại.

---

## Documents

### `POST /api/documents/upload/{widgetId}`
- Mục đích: upload và xử lý file tài liệu gắn với widget.
- Path param: `widgetId` (UUID của `WidgetConfig.id`).
- Input: `multipart/form-data`, field `file` (PDF hoặc TXT, max 50MB).
- Response (`DocumentUploadResponse`):
```json
{
  "id": "uuid-document-id",
  "fileName": "quy_che_tuyen_sinh.pdf",
  "status": "COMPLETED",
  "message": "Upload và xử lý thành công! Đã tạo 42 chunks."
}
```
- Response khi lỗi:
```json
{
  "status": "FAILED",
  "message": "Lỗi hệ thống: ..."
}
```

### `GET /api/documents`
- Mục đích: lấy danh sách tất cả tài liệu (tất cả widgets).
- Response: array `DocumentListItemResponse`:
```json
[
  {
    "id": "uuid",
    "fileName": "...",
    "fileSize": 102400,
    "fileType": "PDF",
    "status": "COMPLETED",
    "chunkCount": 42,
    "widgetConfigId": "uuid-widget-config-id"
  }
]
```
- **Lưu ý**: endpoint này trả tất cả documents, không filter theo widget. Cần filter bằng `widgetConfigId` phía FE nếu cần.

---

## Chat

### `POST /api/chat`
- Mục đích: chat đồng bộ.
- Header: `X-Widget-Key: <apiKey>` (bắt buộc).
- Request:
```json
{
  "sessionId": "uuid-string",
  "message": "Điều kiện tuyển sinh ngành CNTT là gì?"
}
```
- Response:
```json
{
  "answer": "Điều kiện tuyển sinh...",
  "sources": [
    {
      "fileName": "quy_che_tuyen_sinh.pdf",
      "sectionTitle": "3.2 Điều kiện xét tuyển",
      "chunkType": "text",
      "pageStart": 5,
      "pageEnd": 6
    }
  ]
}
```
- Validation: `sessionId` và `message` không được blank → `400 Bad Request`.

### `POST /api/chat/stream`
- Mục đích: chat dạng streaming SSE.
- Header: `X-Widget-Key: <apiKey>` (bắt buộc).
- Request: tương tự endpoint sync.
- Response (`text/event-stream`):

```
event: token
data: {"token": " nội"}

event: token
data: {"token": " dung"}

event: done
data: [{"fileName":"...","sectionTitle":"...","chunkType":"...","pageStart":5,"pageEnd":6}]
```

**Lưu ý quan trọng**:
- `event: token` trả JSON `{"token": "..."}` để giữ nguyên whitespace.
- `event: done` trả JSON array của sources.
- Client phải parse JSON `data` payload trước khi dùng.
- Validation: tương tự sync endpoint, throw `400` nếu blank.

---

## Các endpoint chưa implement

| Endpoint | Trạng thái | Ghi chú |
|---|---|---|
| `GET /api/chat/history` | Chưa implement | Lấy lịch sử chat theo sessionId |
| `DELETE /api/documents/{id}` | Chưa implement | Xóa tài liệu (soft delete) |
| `GET /api/widgets/{id}` | Chưa implement | Lấy thông tin widget theo ID |
