# Tài liệu Frontend RAG Chatbot (Theo flow thực tế dự án)

## 1. Tổng quan kiến trúc FE

Dự án Frontend hiện tại chạy trên React + Vite, đồng thời hỗ trợ 2 kiểu sử dụng:

- Ứng dụng quản trị/chat đầy đủ trong src.
- Widget nhúng website ngoài thông qua script bundle IIFE.

Flow widget đang dùng trong project:

1. Website host load file script chatbot-widget.iife.js.
2. Script khởi tạo bubble chat và iframe.
3. Iframe trỏ vào route /widget của FE app.
4. Trang /widget render giao diện chat tối giản.
5. Người dùng gửi câu hỏi, FE gọi API stream đến Java Backend.
6. FE nhận token theo SSE và append dần vào tin nhắn bot.
7. FE giữ sessionId trong localStorage để duy trì ngữ cảnh hội thoại.

---

## 2. Cấu trúc file quan trọng theo flow

- widget/widget.js
: Entry của widget bundle. Tạo bubble + iframe, đọc config nhúng từ window.RagChatbotConfig.

- vite.widget.config.js
: Cấu hình build widget dạng IIFE, output về dist-widget.

- src/pages/WidgetChatPage.jsx
: Giao diện chat trong iframe, xử lý gửi/nhận stream, quản lý sessionId.

- src/api/axiosInstance.js
: Axios instance cho các API không-stream (baseURL từ env).

- src/api/chatApi.js
: API helper gửi message dạng REST /api/chat.

---

## 3. Component Structure (Theo yêu cầu tích hợp)

Mặc dù code hiện tại dùng một số component nội bộ khác nhau, khi tài liệu hóa để tích hợp đa website có thể chuẩn hóa thành 3 khối chính:

### 3.1 ChatWindow

Trách nhiệm:

- Quản lý khung chat tổng thể (header, message list, input).
- Quản lý trạng thái mở/đóng widget.
- Điều phối trạng thái loading/error.

Ánh xạ với project:

- src/components/ChatWidget/index.jsx (bản popup trong app)
- src/pages/WidgetChatPage.jsx (bản chạy trong iframe)

### 3.2 MessageList

Trách nhiệm:

- Render danh sách tin nhắn user/bot.
- Hiển thị token streaming theo thời gian thực.
- Auto-scroll xuống cuối khi có nội dung mới.
- Hiển thị nguồn tham chiếu (sources) khi backend trả về event done.

Ánh xạ với project:

- Khối danh sách message bên trong src/pages/WidgetChatPage.jsx

### 3.3 InputArea

Trách nhiệm:

- Nhận input từ người dùng.
- Chặn submit khi rỗng hoặc đang loading.
- Trigger gửi bằng Enter hoặc nút Gửi.

Ánh xạ với project:

- Khối input + button trong src/pages/WidgetChatPage.jsx
- (phiên bản app) src/components/ChatWidget/ChatInput.jsx

---

## 4. State Management và hội thoại

### 4.1 State chính

Trong flow widget hiện tại, các state quan trọng:

- messages: Danh sách hội thoại đang hiển thị.
- input: Nội dung người dùng đang nhập.
- loading: Trạng thái đang gửi/nhận phản hồi.
- sessionId: Mã định danh phiên chat.

### 4.2 Duy trì sessionId

Flow hiện có:

- FE đọc localStorage key widget_session_id.
- Nếu chưa có thì tạo UUID mới.
- Mỗi request gửi lên BE đều đính kèm sessionId.

Lợi ích:

- Giữ ngữ cảnh hội thoại cho RAG.
- Có thể lấy lại lịch sử theo session từ backend.

### 4.3 Luồng gửi và nhận tin nhắn (stream)

1. User nhập câu hỏi.
2. FE đẩy message user vào UI ngay.
3. FE tạo một bot placeholder streaming.
4. FE gọi POST /api/chat/stream.
5. FE đọc response.body bằng reader.
6. Với event token: nối text vào bot message.
7. Với event done: kết thúc streaming và cập nhật sources.
8. Nếu lỗi: hiển thị thông báo lỗi trong bot message.

---

## 5. API Integration với Java Backend

### 5.1 Cấu hình Base URL

Có 2 lớp cấu hình:

- Build-time: VITE_API_URL trong môi trường FE.
- Runtime embed: apiUrl truyền từ website host.

Ví dụ env:

VITE_API_URL=http://localhost:8080

### 5.2 API stream (khuyến nghị cho chatbot)

Request:

- Method: POST
- URL: /api/chat/stream
- Body:

```json
{
  "sessionId": "uuid",
  "message": "Nội dung câu hỏi"
}
```

Response dạng SSE:

- event: token
- data: {"token":"..."}

- event: done
- data: [{"fileName":"...","chunkText":"..."}]

Lưu ý kỹ thuật:

- Không trim bừa dữ liệu token để tránh mất khoảng trắng.
- Parse từng block SSE theo dấu phân tách \n\n.

### 5.3 API text thường (fallback)

Khi backend chưa mở stream:

- Method: POST
- URL: /api/chat

Response ví dụ:

```json
{
  "answer": "Nội dung trả lời",
  "sources": []
}
```

### 5.4 API lấy lịch sử chat (MySQL thông qua BE)

Frontend không truy cập MySQL trực tiếp, mà gọi backend:

- Method: GET
- URL: /api/chat/history?sessionId=<id>

Response đề xuất:

```json
{
  "sessionId": "uuid",
  "messages": [
    {
      "id": "m1",
      "role": "user",
      "content": "Học phí ngành CNTT?",
      "createdAt": "2026-03-26T10:00:00Z"
    },
    {
      "id": "m2",
      "role": "assistant",
      "content": "Học phí dự kiến...",
      "sources": [
        { "fileName": "hocphi.pdf", "chunkText": "..." }
      ],
      "createdAt": "2026-03-26T10:00:02Z"
    }
  ]
}
```

---

## 6. CORS giữa FE Widget và Java Backend

Vì widget nhúng vào domain khác, backend phải cấu hình CORS rõ ràng:

- Allowed origins: chỉ whitelist domain tin cậy.
- Allowed methods: GET, POST, OPTIONS.
- Allowed headers: Content-Type, Authorization, X-API-Key.
- Nếu dùng credentials/cookie: bật allowCredentials.

Khuyến nghị:

- DEV: cho phép localhost phục vụ test.
- PROD: chỉ mở đúng domain tích hợp thực tế.

---

## 7. UI/UX Features cần có

### 7.1 Loading state

- Disable input và nút gửi khi request đang chạy.
- Hiển thị typing indicator hoặc caret nhấp nháy.

### 7.2 Auto-scroll

- Mỗi khi messages đổi, scroll về cuối để luôn thấy câu trả lời mới nhất.

### 7.3 Markdown rendering cho câu trả lời bot

Để hiển thị câu trả lời đẹp hơn (heading/list/code), nên render markdown an toàn:

- Có thể dùng react-markdown + remark-gfm.
- Nếu cho phép HTML thì bắt buộc sanitize (ví dụ rehype-sanitize).

### 7.4 Hiển thị nguồn tham chiếu

- Sau khi stream hoàn tất, hiển thị section Nguồn.
- Mỗi nguồn gồm fileName và đoạn chunkText.

---

## 8. Widget Integration vào website khác

### 8.1 Nhúng vào HTML trắng (cách đơn giản nhất)

```html
<!DOCTYPE html>
<html lang="vi">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>Demo Host</title>
</head>
<body>
  <h1>Website host</h1>

  <script>
    window.RagChatbotConfig = {
      apiUrl: "http://localhost:8080",
      frontendUrl: "http://localhost:5173",
      title: "Tư vấn tuyển sinh"
    };
  </script>

  <link rel="stylesheet" href="http://localhost:5173/dist-widget/widget.css" />
  <script src="http://localhost:5173/dist-widget/chatbot-widget.iife.js"></script>
</body>
</html>
```

### 8.2 Nhúng bằng script attributes (linh hoạt cho nhiều tenant/site)

Ví dụ host page:

```html
<link rel="stylesheet" href="https://cdn.example.com/widget.css" />
<script
  src="https://cdn.example.com/chatbot-widget.iife.js"
  data-api-url="https://api.example.com"
  data-frontend-url="https://widget.example.com"
  data-theme-color="#2563eb"
  data-api-key="public-site-key"
  data-title="Trợ lý AI"
></script>
```

Pattern parse config trong widget bootstrap:

```js
const current = document.currentScript;
const attrConfig = {
  apiUrl: current?.dataset.apiUrl,
  frontendUrl: current?.dataset.frontendUrl,
  themeColor: current?.dataset.themeColor,
  apiKey: current?.dataset.apiKey,
  title: current?.dataset.title,
};

window.RagChatbotConfig = {
  ...attrConfig,
  ...(window.RagChatbotConfig || {}),
};
```

Thứ tự ưu tiên cấu hình nên là:

1. data-* trên script
2. window.RagChatbotConfig
3. default trong widget

### 8.3 Có cần thẻ div mount không?

Flow hiện tại không bắt buộc, vì widget tự append bubble/iframe vào body.

Nếu cần kiểm soát vị trí mount theo layout host, có thể mở rộng thêm data-mount-id và cho phép mount vào div chỉ định.

---

## 9. Build và phát hành widget

Lệnh build:

npm run build:widget

Kết quả:

- dist-widget/chatbot-widget.iife.js
- dist-widget/widget.css

Checklist trước khi bàn giao:

- Nhúng chạy trên HTML trắng.
- Test được cả trường hợp stream thành công và lỗi.
- SessionId giữ ổn định sau reload.
- CORS đúng với domain thực tế.
- Responsive tốt trên mobile.

---

## 10. Kết luận

Frontend của RAG Chatbot đã có nền tảng phù hợp cho mô hình nhúng đa website: nhẹ, tách biệt, và dễ cấu hình. Với flow hiện tại (script -> iframe -> /widget -> stream API), team có thể mở rộng thêm theme, auth, telemetry và multi-tenant mà không phải thay đổi cách tích hợp phía website host.