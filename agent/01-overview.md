# CHATBOT_RAG - Tổng quan

## Mục tiêu
- Xây dựng chatbot RAG hỏi đáp theo tài liệu nội bộ.
- Hỗ trợ cả giao diện web quản trị và widget nhúng website ngoài.
- Trả lời theo 2 chế độ: đồng bộ (`POST /api/chat`) và streaming SSE (`POST /api/chat/stream`).
- Hỗ trợ multi-tenant theo `WidgetConfig`: mỗi widget có tài liệu, phiên chat và kết quả truy vấn riêng biệt.

## Thành phần hệ thống
- `Frontend/`: React + Vite cho UI chat, quản lý tài liệu, route widget nhúng.
- `Backend/`: Spring Boot xử lý upload, parse/chunk/embed, retrieval nâng cao, gọi LLM.
- `MySQL`: lưu metadata tài liệu, lịch sử chat (chat_sessions + chat_messages), widget configs, chunk/section metadata.
- `Qdrant`: lưu vectors embedding phục vụ semantic search, filter theo `widgetId`.
- `preprocess/`: module Java độc lập (không phụ thuộc Backend) dùng để thử nghiệm pipeline parse/chunk cục bộ. Không được gọi từ Backend runtime.

## Luồng chính end-to-end
1. Admin tạo `WidgetConfig` qua `POST /api/widgets` → nhận `widgetConfigId` và `apiKey`.
2. Admin upload PDF/TXT vào `POST /api/documents/upload/{widgetId}`.
3. Backend parse, chunk (phân loại theo `chunkType`), embed và lưu vectors vào Qdrant kèm `widgetId`.
4. Người dùng (widget bên ngoài) gửi câu hỏi kèm header `X-Widget-Key: <apiKey>`.
5. Backend xác thực widget key, embed câu hỏi, phân tích intent và truy hồi context từ Qdrant + MySQL.
6. Backend ghép prompt với history và context, gọi LLM Groq, trả về câu trả lời + nguồn tham chiếu.

## Trạng thái hiện tại
- Pipeline RAG chạy end-to-end với multi-tenant theo widget.
- Widget nhúng qua bundle IIFE đã hoạt động.
- Bảo mật chat đã được triển khai qua `WidgetAuthFilter` (header `X-Widget-Key`).
- Retrieval nâng cao: intent detection, heading lock, multi-stage expansion, Cohere rerank (tùy chọn).
- Chunking nâng cao: section hierarchy + bảng structured (`normalized_table_row`, `table_summary`, `text`, `section_summary`, `parent_section_summary`). Legacy `table_row_group` / `text_table_like` chỉ trên dữ liệu cũ.
- Chưa hoàn thiện: monitoring/tracing, test coverage đầy đủ, pagination API lịch sử chat.
