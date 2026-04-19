# CHATBOT_RAG - Tổng quan

## Mục tiêu
- Xây dựng chatbot RAG hỏi đáp theo tài liệu nội bộ.
- Hỗ trợ cả giao diện web quản trị và widget nhúng website ngoài.
- Trả lời theo 2 chế độ: đồng bộ (`/api/chat`) và streaming SSE (`/api/chat/stream`).

## Thành phần hệ thống
- `Frontend/`: React + Vite cho UI chat, quản lý tài liệu, route widget.
- `Backend/`: Spring Boot xử lý upload, parse/chunk/embed, retrieval, gọi LLM.
- `MySQL`: lưu metadata tài liệu + lịch sử chat theo `sessionId`.
- `Qdrant`: lưu vectors embedding phục vụ semantic search.

## Luồng chính end-to-end
1. Người dùng upload PDF/TXT.
2. Backend parse, chunk, embed và lưu vectors vào Qdrant.
3. Người dùng đặt câu hỏi.
4. Backend embed câu hỏi, truy hồi context từ Qdrant, ghép prompt với history.
5. Backend gọi LLM Groq và trả về câu trả lời + nguồn tham chiếu.

## Trạng thái hiện tại
- Đã có pipeline RAG chạy được end-to-end.
- Đã có widget nhúng qua bundle IIFE.
- Chưa hoàn thiện các phần production như auth, monitoring và test coverage sâu.
