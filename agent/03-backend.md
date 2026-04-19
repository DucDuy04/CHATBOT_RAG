# Backend (Spring Boot)

## Tech stack
- Java 21, Spring Boot 3.4.x
- Spring Web, Spring Data JPA, Spring Security
- LangChain4j (OpenAI-compatible client cho Groq)

## Domain và dữ liệu
- `Document`: metadata file upload và trạng thái xử lý.
- `DocumentChunk`: thông tin chunk (nếu dùng trong persistence nội bộ).
- `ChatMessage`: lịch sử hội thoại (`USER` / `ASSISTANT`) theo `sessionId`.
- Repositories:
  - `DocumentRepository`
  - `ChatMessageRepository`
  - `DocumentChunkRepository` (nếu bật luồng lưu chunk quan hệ)

## Cấu hình quan trọng
- `application.yml`, `application-dev.yml`, `application-docker.yml`
- Nhóm config:
  - `GroqConfig`: chat model.
  - `QdrantConfig`: collection/vector settings.
  - `SecurityConfig`: CORS + security rules.

## Ingestion pipeline
1. Nhận multipart file tại `POST /api/documents/upload`.
2. Lưu file vật lý vào thư mục upload.
3. Parse nội dung (PDF/TXT).
4. Chunk text.
5. Embed từng chunk và upsert vào Qdrant kèm payload (`documentId`, `fileName`, `chunkIndex`, `text_segment`).
6. Cập nhật trạng thái document (`PROCESSING`, `COMPLETED`, `FAILED`).

## Query pipeline
1. Nhận `sessionId` + `message`.
2. Lưu user message.
3. Embed query và search top-k từ Qdrant.
4. Lấy history chat gần nhất từ MySQL.
5. Build prompt + gọi Groq.
6. Lưu assistant message và trả `answer + sources` hoặc stream token SSE.
