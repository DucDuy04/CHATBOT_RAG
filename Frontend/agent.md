# NGỮ CẢNH HỆ THỐNG & KIẾN THỨC DỰ ÁN (AI SYSTEM CONTEXT)
Tên dự án: Hệ thống RAG Chatbot (Kiến trúc Decoupled FE/BE)
Mô tả: Một chatbot RAG (Retrieval-Augmented Generation) cho phép upload file PDF/TXT, cắt nhỏ văn bản (chunking), nhúng vector (embedding), tìm kiếm ngữ nghĩa, và triển khai giao diện React dưới dạng Widget nhúng qua file script IIFE.

## 1. CÔNG NGHỆ & PHIÊN BẢN (TECH STACK)
### Backend (BE)
- Framework: Java Spring Boot
- AI Framework: LangChain4j
- LLM Provider: Groq (gọi qua API tương thích chuẩn OpenAI)
- Embedding Model: Nomic (Kích thước vector: 768 dimensions)
- Vector DB: Qdrant
- Relational DB: MySQL
- Trích xuất tài liệu: Apache PDFBox

### Frontend (FE)
- Framework: React + Vite
- Output build: IIFE Bundle (`chatbot-widget.iife.js`) + Kiến trúc Iframe
- Giao thức mạng: REST & Server-Sent Events (SSE)

## 2. QUY TẮC KIẾN TRÚC & TIÊU CHUẨN (ARCHITECTURAL RULES)
- **Tách biệt hoàn toàn (Strict Decoupling):** FE và BE hoạt động hoàn toàn độc lập. FE widget chỉ giao tiếp với BE thông qua REST API và SSE.
- **Triển khai RAG:** Tạm thời không dùng `AiServices` cấp cao của LangChain4j để quản lý state. Trạng thái và lịch sử hội thoại được quản lý thủ công bên trong `ChatService` và lưu xuống MySQL (bảng `chat_messages`).
- **Ưu tiên Streaming:** Luồng chat chính bắt buộc phải dùng Server-Sent Events (`/api/chat/stream`) để tối ưu UX.
- **Nhúng Widget:** FE được thiết kế để nhúng vào các website bên ngoài bằng thẻ `<script>`. Bắt buộc phải xử lý cấu hình CORS trên BE và sử dụng URL API tuyệt đối trên FE.

## 3. LUỒNG XỬ LÝ LÕI (CORE WORKFLOWS)
### Luồng A: Nạp tài liệu (Document Ingestion - `POST /api/documents/upload`)
1. Nhận file Multipart (PDF/TXT) -> Lưu file vào local.
2. MySQL: Insert dòng mới vào bảng `documents` (Trạng thái: PENDING).
3. Đọc text (dùng PDFBox) -> Clean formatting (xóa khoảng trắng/xuống dòng thừa).
4. Chunking: Cắt với kích thước 500 ký tự, Overlap 50 ký tự. Ưu tiên ngắt tại dấu chấm câu.
5. Embedding: Nhúng các chunks bằng model Nomic (768 dimensions).
6. Qdrant: Upsert các points. Payload bắt buộc chứa: `documentId`, `fileName`, `chunkIndex`, `text_segment`.
7. MySQL: Cập nhật trạng thái trong `documents` thành COMPLETED.

### Luồng B: Chat RAG & Sinh text (`POST /api/chat/stream`)
1. Nhận `sessionId` và `message` từ FE.
2. MySQL: Lưu tin nhắn của USER vào `chat_messages`.
3. Nhúng (Embed) câu hỏi của user -> Tìm kiếm độ tương đồng (Similarity Search) trong Qdrant (Cosine, Top-K = 5).
4. MySQL: Lấy 10 tin nhắn gần nhất theo `sessionId` để làm lịch sử hội thoại (history).
5. Lắp ráp Prompt: System Instruction + Top-K Chunks + History + Câu hỏi hiện tại.
6. Gọi LLM (Groq) -> Stream các token trả về thông qua SSE.
7. SSE Event `token`: Gửi các đoạn text. SSE Event `done`: Gửi kèm mảng JSON chứa nguồn tham chiếu (sources).
8. MySQL: Lưu tin nhắn của ASSISTANT và sources vào `chat_messages`.

## 4. CẤU TRÚC CƠ SỞ DỮ LIỆU (DATABASE SCHEMAS)
### MySQL (Relational Data)
- Bảng `chat_messages`: `id`, `session_id`, `role` (USER/ASSISTANT), `content`, `sources` (Lưu chuỗi JSON), `created_at`.
- Bảng `documents`: `id`, `file_name`, `file_path`, `file_type`, `file_size`, `status` (PENDING/PROCESSING/COMPLETED/FAILED), `chunk_count`, `created_at`, `processed_at`.

### Qdrant (Vector Data)
- Tên Collection: `documents`
- Cấu hình Vector: `size = 768`, `distance = Cosine`

## 5. GIAO THỨC API (API CONTRACTS)
- `POST /api/documents/upload`: Content-Type là multipart/form-data.
- `GET /api/documents`: Trả về danh sách tài liệu đã ingest.
- `POST /api/chat`: Chat đồng bộ (Fallback). Body: `{sessionId, message}`.
- `POST /api/chat/stream`: Chat Streaming (Chính). Body: `{sessionId, message}`.
- `GET /api/chat/history?sessionId={id}`: Trả về lịch sử chat theo session.

## 6. KIẾN TRÚC FRONTEND WIDGET
- **Entry Point:** File `widget.js` sẽ đọc cấu hình từ biến global `window.RagChatbotConfig` hoặc qua các attributes `<script data-*>`.
- **Render:** Khởi tạo một nút Chat Bubble và một Iframe ẩn/hiện trỏ tới route `/widget` của FE app.
- **State Management:** Giá trị `sessionId` bắt buộc phải được lưu và đọc từ `localStorage` để giữ liên kết với lịch sử chat trong MySQL.
- **SSE Parsing:** FE lắng nghe sự kiện `event: token` để nối chuỗi text hiển thị, và sự kiện `event: done` để render danh sách tài liệu tham khảo (sources).

## 7. CHỈ THỊ DÀNH CHO AI ASSISTANT (KHI VIẾT CODE)
1. Luôn bảo toàn luồng truyền và nhận `sessionId` trong mọi tương tác chat/history.
2. Nếu có yêu cầu sửa đổi schema database, hãy tự động sinh ra script SQL DDL tương ứng.
3. Nếu thay đổi model embedding trong LangChain4j, phải kiểm tra và đảm bảo kích thước vector trong Qdrant (hiện tại là 768) khớp với model mới.
4. Khi chỉnh sửa FE widget, phải đảm bảo tính tương thích ngược với phương thức nhúng script ban đầu (IIFE).