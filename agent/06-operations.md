# Vận hành và roadmap

## Chạy local nhanh

### Cách 1: Docker Compose (full stack)
```bash
# Tạo file .env tại root với các biến bắt buộc (xem mục Biến môi trường)
docker compose up --build
```
Dịch vụ khởi động:
- Frontend: `http://localhost:5173`
- Backend: `http://localhost:8080`
- MySQL: `localhost:3306` (db: `ragchatbot`, pass: `root`)
- Qdrant REST: `http://localhost:6333`, gRPC: `localhost:6334`

### Cách 2: Chạy riêng Backend + Frontend
```bash
# Terminal 1: Backend (cần MySQL + Qdrant đang chạy)
cd Backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Terminal 2: Frontend
cd Frontend
npm install
npm run dev
```

### Khởi động MySQL + Qdrant với Docker (chỉ infra)
```bash
docker run -d --name ragchatbot-mysql \
  -e MYSQL_DATABASE=ragchatbot -e MYSQL_ROOT_PASSWORD=root \
  -p 3306:3306 mysql:8.0

docker run -d --name ragchatbot-qdrant \
  -p 6333:6333 -p 6334:6334 qdrant/qdrant:latest
```

---

## Biến môi trường Backend

### Bắt buộc
| Biến | Mô tả | Ví dụ |
|---|---|---|
| `GROQ_API_KEY` | API key Groq (LLM) | `gsk_...` |
| `NOMIC_API_KEY` | API key Nomic (embedding) | `nk-...` |

### Tùy chọn (Cohere Rerank)
| Biến | Mô tả | Mặc định |
|---|---|---|
| `COHERE_API_KEY` | API key Cohere | (rỗng — disabled) |
| `COHERE_RERANK_ENABLED` | Bật/tắt rerank | `false` |

Khi `COHERE_RERANK_ENABLED=false` hoặc không có key: retrieval vẫn hoạt động bình thường, không rerank.
Cohere free tier: 1000 requests/tháng (dùng `rerank-multilingual-v3.0`).

### Cấu hình ẩn trong code (không cần override thường)
| Biến config | Giá trị dev | Mô tả |
|---|---|---|
| `groq.chat-model` | `llama-3.3-70b-versatile` | Model LLM chính |
| `groq.base-url` | `https://api.groq.com/openai/v1` | Groq OpenAI-compatible endpoint |
| `groq.fallback-models` | `llama-3.1-8b-instant,meta-llama/llama-4-scout-17b-16e-instruct,qwen/qwen3-32b` | Fallback khi model chính rate-limit |
| `nomic.embedding-model` | `nomic-embed-text-v1.5` | Embedding model (vector size 768) |
| `qdrant.collection-name` | `documents` | Tên collection Qdrant |
| `qdrant.vector-size` | `768` | Phải khớp với embedding model |
| `app.upload-dir` | `./uploads` | Thư mục lưu file upload |
| `spring.servlet.multipart.max-file-size` | `50MB` | Giới hạn upload |

### Biến môi trường Frontend
| Biến | Mô tả | Giá trị mặc định |
|---|---|---|
| `VITE_API_URL` | Base URL của Backend API | `http://localhost:8080` |
| `VITE_WIDGET_API_KEY` | Widget API key mặc định khi build | (rỗng) |

---

## Template .env (đặt tại root, dùng cho Docker Compose)
```env
GROQ_API_KEY=gsk_xxxxx
NOMIC_API_KEY=nk-xxxxx
# Optional Cohere:
# COHERE_API_KEY=xxxxx
# COHERE_RERANK_ENABLED=true
```

---

## Schema DB
- Backend dùng `spring.jpa.hibernate.ddl-auto=update` (dev/docker).
- Không có migration tool (Flyway/Liquibase).
- Nếu cần chạy SQL migration thủ công: xem file `schema_update.sql` ở root.
- Backup trước khi đổi schema: `ragchatbot_backup_before_schema_update.sql`.

---

## Rủi ro kỹ thuật cần lưu ý

| Rủi ro | Mô tả | Phòng tránh |
|---|---|---|
| Vector size mismatch | Qdrant collection đã tạo với size khác embedding model hiện tại (768) | Không đổi embedding model tùy tiện; cần re-index nếu đổi |
| Re-embed khi đổi model | Đổi Nomic model → toàn bộ vector cũ vô nghĩa | Luôn kiểm tra vector-size config trước khi deploy |
| Rate limit Groq | Model chính hết quota TPD → fallback models | Fallback đã có; giám sát log `LlmFallbackService` |
| SSE timeout | Stream giữ connection lâu → Nginx/load balancer timeout | Cấu hình `proxy_read_timeout` trong Nginx |
| Upload lớn | PDF nhiều trang → parse/chunk ăn CPU/RAM | Max 50MB đã giới hạn; production yếu nên test với file lớn |
| N+1 query | Expansion retrieval có thể gây nhiều query DB | RagRetrievalService đã dùng batch fetch; theo dõi `show-sql: true` |
| Disk đầy | Upload file + logs | `./uploads` dùng volume Docker; rotate log |
| Cohere free tier | 1000 req/tháng | Dùng `COHERE_RERANK_ENABLED=false` trừ khi thực sự cần |
| CORS thiếu origin | Widget nhúng website ngoài → CORS block | Cấu hình CORS đúng cho origin production (hiện chỉ localhost) |

---

## Việc cần làm để production-ready

1. **Auth cho document/widget endpoints**: `/api/documents/**` và `/api/widgets/**` hiện không có auth — bất kỳ ai cũng có thể upload hoặc tạo widget.
2. **CORS production**: thêm origin thực vào `SecurityConfig.corsConfigurationSource()`.
3. **Input validation**: thêm `@Valid` + DTO validation cho các request bodies.
4. **API lịch sử chat**: `GET /api/chat/history?sessionId=...` với phân trang.
5. **Monitoring**: error rate, latency, request ID (MDC correlation).
6. **Test tự động**: upload, chat sync, chat stream, widget auth.
7. **Rate limiting**: chặn spam request per widget key.
8. **Secret management**: không để key trong repo; dùng secrets manager hoặc env inject.
9. **DB migration**: chuyển sang Flyway/Liquibase thay vì `ddl-auto=update` trên production.
10. **Pagination**: `GET /api/documents` trả tất cả không phân trang.

---

## Build checks nhanh
```bash
# Backend compile (không chạy test)
cd Backend && ./mvnw -DskipTests compile

# Backend test
cd Backend && ./mvnw test

# Frontend lint
cd Frontend && npm run lint

# Frontend build
cd Frontend && npm run build

# Widget build
cd Frontend && npm run build:widget

# Docker compose config validate
docker compose config
```
