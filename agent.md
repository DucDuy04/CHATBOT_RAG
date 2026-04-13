# 🧠 PROJECT OVERVIEW
- Tên dự án: `CHATBOT_RAG` (Backend artifact: `RAG_CHATBOT_BE`, Frontend package: `rag-chatbot-fe`).
- Mục tiêu hệ thống: Xây dựng chatbot RAG hỗ trợ hỏi đáp dựa trên tài liệu nội bộ (PDF/TXT), có giao diện web quản trị và widget nhúng.
- Mô tả ngắn: Hệ thống cho phép upload tài liệu, parse/chunk/embed vào Qdrant, sau đó truy hồi ngữ cảnh và gọi LLM (Groq) để trả lời theo chế độ đồng bộ hoặc streaming SSE.

# 🏗️ SYSTEM ARCHITECTURE
- Kiến trúc tổng thể: Client-Server + RAG pipeline, triển khai theo mô hình tách FE/BE (decoupled), chưa phải microservices.
- Thành phần chính:
  - Frontend React (dashboard + playground + trang tài liệu + widget script IIFE).
  - Backend Spring Boot (REST API, xử lý ingestion, chat, truy hồi ngữ nghĩa).
  - MySQL (metadata tài liệu + lịch sử chat).
  - Qdrant (vector store).
  - External AI providers: Groq (chat model), Nomic (embedding model).
- Tương tác:
  - FE gọi BE qua REST/SSE (`/api/documents`, `/api/chat`, `/api/chat/stream`).
  - BE ghi metadata/history vào MySQL, vector vào Qdrant.
  - BE gọi Groq/Nomic qua API.

Luồng xử lý chính:
1. User upload file tại FE.
2. BE parse nội dung, chunk, embed, lưu vectors vào Qdrant, cập nhật trạng thái vào MySQL.
3. User đặt câu hỏi.
4. BE embed query, search top-k chunks từ Qdrant.
5. BE dựng prompt từ system prompt + context + history.
6. BE gọi Groq để sinh câu trả lời (sync hoặc stream token SSE).
7. FE render câu trả lời và nguồn tham chiếu.

# ⚙️ TECH STACK
- Backend:
  - Java 21
  - Spring Boot 3.4.4
  - Spring Web, Spring Data JPA, Spring Security
  - LangChain4j (OpenAI-compatible client cho Groq)
- Frontend:
  - React 19 + Vite
  - React Router, Axios, TailwindCSS
  - Widget build dạng IIFE (`vite.widget.config.js`)
- Database:
  - MySQL 8.0 (docker)
  - Qdrant (vector DB)
- RAG components:
  - LLM: Groq (`llama-3.3-70b-versatile` trong config dev/docker)
  - Embedding: Nomic (`nomic-embed-text-v1.5`)
  - Retriever: search Qdrant theo cosine, top-k hiện tại trong code `TOP_K = 8`
- Công cụ khác:
  - Docker Compose (frontend, backend, mysql, qdrant)
  - Apache PDFBox + Tabula (parse PDF + extract table)

# 🗄️ DATABASE DESIGN
- Mô hình dữ liệu: Polyglot persistence (MySQL cho transactional metadata + Qdrant cho semantic vectors).

MySQL (`ragchatbot`):
- Bảng `documents`:
  - Vai trò: lưu metadata tài liệu upload và trạng thái xử lý ingestion.
  - Cột chính (từ entity `Document`):
    - `id` (PK, auto increment)
    - `file_name`, `file_path`, `file_type`, `file_size`
    - `status` (`PENDING` | `PROCESSING` | `COMPLETED` | `FAILED`)
    - `chunk_count`, `created_at`, `processed_at`
- Bảng `chat_messages`:
  - Vai trò: lưu hội thoại theo phiên để build context nhiều lượt.
  - Cột chính (từ entity `ChatMessage`):
    - `id` (PK, auto increment)
    - `session_id`
    - `role` (`USER` | `ASSISTANT`)
    - `content` (TEXT)
    - `sources` (TEXT, JSON string)
    - `created_at`
- Cơ chế tạo schema: đang dùng `spring.jpa.hibernate.ddl-auto=update` (chưa thấy migration tool như Flyway/Liquibase trong code).

Qdrant (vector store):
- Collection: `documents`.
- Vector config: `size=768`, `distance=Cosine` (khởi tạo trong `QdrantConfig`).
- Payload cho mỗi chunk (từ `EmbeddingService`):
  - `documentId`
  - `fileName`
  - `chunkIndex`
  - `text_segment`
- Truy vấn: BE embed query rồi gọi search top-k (`TOP_K=8`) để lấy context chunks.

Data lifecycle giữa 2 DB:
1. Upload file -> ghi `documents` (MySQL).
2. Parse/chunk/embed -> ghi vectors + payload vào Qdrant.
3. Chat theo `session_id` -> ghi lịch sử vào `chat_messages`.
4. Query RAG -> đọc Qdrant + đọc history MySQL để dựng prompt.

# 📂 PROJECT STRUCTURE
- `Backend/`: Spring Boot API.
  - `src/main/java/.../api`: REST controllers (`ChatController`, `DocumentController`).
  - `src/main/java/.../service`: business logic (chat, ingestion, embedding, prompt, chunking, parsing).
  - `src/main/java/.../domain`: entities + repositories (MySQL).
  - `src/main/java/.../config`: security, Groq/Nomic config, Qdrant init.
  - `src/main/resources`: profile config (`application.yml`, `application-dev.yml`, `application-docker.yml`).
- `Frontend/`: React app + widget embed.
  - `src/pages/`: các trang đang hoạt động gồm `ChatPage`, `DocumentPage`, `WidgetChatPage`.
  - `src/api/`: API client cho chat/documents.
  - `widget/widget.js`: script nhúng tạo bubble + iframe.
  - `vite.widget.config.js`: build widget IIFE.
- `docker-compose.yml`: môi trường chạy full stack local bằng container.

# 🔌 CORE MODULES
- Document Ingestion Module (`DocumentService`, `DocumentParserService`, `ChunkingService`, `EmbeddingService`):
  - Nhận file upload.
  - Parse PDF/TXT (có xử lý bảng trong PDF).
  - Chunk text và lưu embedding vào Qdrant.
  - Cập nhật trạng thái tài liệu trong MySQL.
- Chat/RAG Module (`ChatService`, `PromptBuilderService`):
  - Nhận câu hỏi + sessionId.
  - Lấy context từ Qdrant.
  - Lấy chat history từ MySQL.
  - Sinh prompt và gọi Groq trả lời.
  - Hỗ trợ sync và streaming SSE.
- Persistence Module (`Document`, `ChatMessage` + repository):
  - Lưu tài liệu, trạng thái xử lý, lịch sử hội thoại.
- Frontend Chat Module (`ChatPage.jsx`, `WidgetChatPage.jsx`):
  - Gửi câu hỏi stream.
  - Parse SSE `token`/`done`.
  - Lưu `sessionId` ở localStorage.
- Widget Embed Module (`widget/widget.js`):
  - Cắm vào website khác bằng script.
  - Tạo bubble và iframe đến route widget.

# 🔄 DATA FLOW (QUAN TRỌNG)
Ingestion flow (upload tài liệu):
1. FE gửi multipart file đến `POST /api/documents/upload`.
2. BE lưu file vào thư mục upload local.
3. Tạo bản ghi `documents` trạng thái `PENDING` -> `PROCESSING`.
4. Parse nội dung (PDF/TXT), làm sạch text.
5. Chunk text (code hiện tại: `CHUNK_SIZE=1200`, `OVERLAP=200`; table block giữ nguyên).
6. Embed từng chunk bằng Nomic, lưu vào Qdrant kèm payload (`documentId`, `fileName`, `chunkIndex`, `text_segment`).
7. Cập nhật document `COMPLETED` hoặc `FAILED`.

Query flow (user -> retrieve -> LLM -> response):
1. FE gửi `sessionId` + `message` vào `POST /api/chat/stream` (hoặc `/api/chat`).
2. BE lưu user message vào `chat_messages`.
3. BE embed câu hỏi và search top-k trong Qdrant.
4. BE lấy top 10 history của session trong MySQL.
5. BE build prompt: system instruction + context chunks + history + current question.
6. BE gọi Groq:
  - Sync endpoint: trả về JSON `answer + sources`.
  - Stream endpoint: trả dần `event: token`, kết thúc bằng `event: done` chứa sources.
7. FE ghép token để render realtime, sau đó hiển thị sources.

# 🌐 API DESIGN
API hiện có trong code backend:
- `POST /api/documents/upload`
  - Chức năng: upload + xử lý tài liệu.
  - Input: multipart `file`.
  - Output: `DocumentUploadResponse` (id, fileName, status, message).
- `GET /api/documents`
  - Chức năng: lấy danh sách tài liệu đã lưu.
  - Output: list `Document`.
- `POST /api/chat`
  - Chức năng: chat đồng bộ.
  - Input: `{ sessionId, message }`.
  - Output: `{ answer, sources[] }`.
- `POST /api/chat/stream` (SSE)
  - Chức năng: chat streaming.
  - Input: `{ sessionId, message }`.
  - Output: SSE events `token`, `done`.

Lưu ý: Endpoint lịch sử chat (`GET /api/chat/history`) chưa thấy được implement trong controller hiện tại.

# 🧪 CURRENT STATUS
Đã hoàn thành:
- Pipeline RAG cơ bản chạy end-to-end (upload -> embed -> retrieve -> chat).
- Tích hợp Groq (LLM) + Nomic (embedding) + Qdrant.
- Hỗ trợ chat streaming SSE trên FE và BE.
- Có trang quản lý tài liệu trên FE.
- Widget route `/widget` đã có trong router FE, dùng cho iframe embed.
- Có Docker Compose cho môi trường local đầy đủ.

Đang làm dở / chuyển tiếp (suy ra từ code):
- Đã dọn một phần lớn mã Frontend legacy không còn đi từ entrypoint; hiện FE tập trung vào 3 màn hình chính (chat, documents, widget chat).
- Cần chốt tiêu chí giữ/xóa tiếp cho các artifact build và tài liệu kỹ thuật cũ để tránh nhiễu repository.

Còn thiếu:
- AuthN/AuthZ thực tế (security đang `permitAll`).
- API lịch sử chat chính thức.
- Quan sát hệ thống (metrics/tracing/log correlation) và test tự động đầy đủ.
- Cấu hình production-ready cho CORS, rate limiting, secret management.

# 📊 PROGRESS SNAPSHOT (2026-04-13)
## Mức hoàn thiện theo mảng chức năng
| Hạng mục | Trạng thái | Mô tả ngắn |
|---|---|---|
| Ingestion tài liệu (upload/parse/chunk/embed) | Da hoan thanh | Luồng hoạt động đủ để nạp PDF/TXT lên Qdrant và lưu metadata MySQL |
| Chat sync (`POST /api/chat`) | Da hoan thanh | Có validate cơ bản `sessionId/message`, trả `answer + sources` |
| Chat streaming (`POST /api/chat/stream`) | Da hoan thanh co dieu kien | Stream token SSE hoạt động, còn thiếu validation input và tối ưu threading |
| Widget nhúng (bubble + iframe) | Da hoan thanh co dieu kien | Route `/widget` đã map đúng, script IIFE và demo embed có sẵn |
| Bảo mật ứng dụng | Chua hoan thanh | Security vẫn `permitAll`, chưa có cơ chế auth thực chiến |
| Quan sát hệ thống/monitoring | Chua hoan thanh | Chưa có metrics/tracing/log correlation bài bản |
| Test tự động | Chua hoan thanh | Mới có test cơ bản, chưa đủ phủ luồng nghiệp vụ chính |

## Trạng thái kỹ thuật đã kiểm tra nhanh
- Frontend build: PASS (`npm run build` chạy thành công).
- Backend compile: PASS (`./mvnw -DskipTests compile` không báo lỗi).
- Docker compose full stack: lần chạy gần nhất trong phiên có thất bại (`docker compose --build`, mã thoát 1), cần tách log để xử lý riêng.

## Scope Frontend hiện đang active
- Routes chính: `/`, `/documents`, `/widget`.
- API client đang dùng: `src/api/axiosInstance.js`, `src/api/documentApi.js`, gọi trực tiếp `fetch` cho chat stream trong page.
- Legacy UI kit/hooks/store/types không còn trong đường chạy hiện tại (đã dọn khỏi `src`).

# 🗺️ VISUAL DELIVERY MAP
```mermaid
flowchart LR
  A[Upload PDF/TXT] --> B[DocumentService]
  B --> C[(MySQL: documents)]
  B --> D[Chunking + Embedding]
  D --> E[(Qdrant: vectors)]
  U[User asks question] --> F[ChatPage/WidgetPage]
  F --> G[/api/chat or /api/chat/stream]
  G --> H[ChatService]
  H --> E
  H --> C2[(MySQL: chat_messages)]
  H --> I[Groq LLM]
  I --> J[Answer + Sources]
```

## Definition of Done cho mốc "production-ready v1"
- Co auth token cho API chat/documents.
- Co input validation cho endpoint stream tuong duong endpoint sync.
- Co endpoint history chat va co phan trang.
- Co monitoring toi thieu (error rate, latency, request id).
- Co bo test tu dong cho 3 luong: upload, chat sync, chat stream.
