# Vận hành và roadmap

## Chạy local nhanh
- Cách 1: chạy full stack với `docker-compose.yml`.
- Cách 2: chạy riêng backend và frontend để debug nhanh.

## Biến môi trường quan trọng
- Backend:
  - khóa Groq
  - khóa embedding provider (Nomic)
  - MySQL connection
  - Qdrant host/port/collection/vector-size
- Frontend:
  - `VITE_API_URL`
  - cấu hình widget runtime (`window.RagChatbotConfig` hoặc data attributes)

## Rủi ro kỹ thuật cần lưu ý
- Kích thước vector phải đồng bộ giữa embedding model và Qdrant collection.
- Đổi embedding model cần re-embed dữ liệu cũ.
- Streaming SSE cần timeout/retry hợp lý để tránh treo kết nối.

## Việc cần làm để production-ready
- Bổ sung AuthN/AuthZ cho các endpoint API.
- Chuẩn hóa error response và log correlation.
- Bổ sung monitoring (latency, error rate, throughput).
- Tăng test coverage cho upload, chat sync, chat stream.
- Rà soát CORS/rate limiting/secret management.
