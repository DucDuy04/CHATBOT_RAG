# Cursor Report 20F — Core RAG E2E / Manual Runbook

**Ngày:** 2026-05-14  
**Scope:** Tài liệu runbook E2E/manual + sample TXT; điều chỉnh `.gitignore` tối thiểu để file runbook/report được track; **không** sửa runtime backend/frontend.

---

## 1. Mức độ hiểu task

| Mục | Nội dung |
|-----|----------|
| **Hiểu task** | **96%** |
| **Chắc chắn** | Endpoint upload/chat/delete/retry/chatbot từ controller/filter đã đối chiếu; `docker-compose.yml` port/env; `WidgetAuthFilter` header `X-Widget-Key` vs `x-api-key` public; `pom.xml` không có actuator — health qua `/api/chatbots`. |
| **Giả định** | MySQL lưu UUID dạng BINARY(16) — SQL mẫu dùng `BIN_TO_UUID`/`UUID_TO_BIN` có thể cần chỉnh nếu schema CHAR(36). |
| **Thiếu dữ kiện** | Không chạy E2E runtime trong phiên này. |

---

## 2. Tóm tắt yêu cầu

Runbook cho luồng RAG trên DB sạch: clean, start stack, widget, upload TXT, poll status, SQL/Qdrant, chat, delete, chat lại, retry FAILED optional; sample file + curl/SQL mẫu; troubleshooting + checklist; report task.

---

## 3. Phạm vi đã làm

- `docs/RAG_CORE_FLOW_E2E_RUNBOOK.md` — đủ 20 mục theo prompt 20F.  
- `docs/samples/RAG_E2E_SAMPLE.txt` — nội dung có chuỗi kiểm tra cố định.  
- `.gitignore` — thay một dòng `docs/` bằng `docs/*` + ngoại lệ tối thiểu để runbook, sample, `RAG_TARGET_ARCHITECTURE.md`, và **báo cáo 20F trong `docs/`** được git track (thư mục `reports/` **giữ nguyên** ignore toàn phần — tránh vô tình track cả `reports/refactor/`).  
- Báo cáo task (file git-tracked): `docs/CURSOR_REPORT_20F_CORE_RAG_E2E_RUNBOOK.md`. Bản đồng nội dung theo convention prompt: `reports/refactor/CURSOR_REPORT_20F_CORE_RAG_E2E_RUNBOOK.md` (cùng nội dung, có thể tồn tại trên đĩa nhưng **không** vào git vì `reports/` bị ignore).

---

## 4. Phạm vi không làm

- Không backfill/migration; không sửa Java/React; không thêm dependency/test framework; không chạy benchmark.

---

## 5. Vì sao không sửa runtime

Không phát hiện blocker mới từ task runbook; chỉ bổ sung tài liệu + gitignore cho tracking.

---

## 6. Danh sách file đã đọc

| Path | Mục đích | Kết luận |
|------|----------|----------|
| `WidgetAuthFilter.java` | Header chat | `X-Widget-Key` (chat), `x-api-key` (public chat). |
| `DocumentController.java` | Upload/status/delete/retry | Paths multipart + legacy. |
| `ChatController.java` / `PublicChatController.java` | Chat body/header | `ChatRequest` fields; public optional session. |
| `ChatbotController.java` | Tạo chatbot | `POST /api/chatbots`, `ChatbotCreateRequest` fields. |
| `WidgetController.java` | Widget legacy | `POST /api/widgets` (runbook ưu tiên chatbots). |
| `ChatRequest.java` / `ChatbotResponse.java` | DTO | `apiKey` chỉ khi create; `id` = tenant UUID. |
| `SecurityConfig.java` | permitAll | Chatbots/widgets/chat public. |
| `application.yml` / `application-dev.yml` / `application-docker.yml` | Port, Qdrant, collection | `documents`, 768, upload-dir. |
| `docker-compose.yml` | Stack | Ports 8080/3306/6333/5173; env GROQ/NOMIC. |
| `.env.example` | Biến | GROQ, NOMIC, COHERE, MYSQL. |
| `Backend/pom.xml` | Actuator | Không có — runbook không dựa actuator. |

**Không đọc đầy đủ:** `Frontend/.env.example`, `Backend/.env.example` (không tồn tại hoặc không đổi nội dung runbook); toàn bộ `DocumentService` (chỉ xác nhận retry/delete từ báo cáo 20E trước).

---

## 7. File đã tạo / cập nhật

| Path | Mô tả |
|------|--------|
| `docs/RAG_CORE_FLOW_E2E_RUNBOOK.md` | Runbook chính |
| `docs/samples/RAG_E2E_SAMPLE.txt` | Sample TXT |
| `.gitignore` | Whitelist tối thiểu dưới `docs/*` |
| `docs/CURSOR_REPORT_20F_CORE_RAG_E2E_RUNBOOK.md` | Báo cáo task (tracked) |
| `reports/refactor/CURSOR_REPORT_20F_CORE_RAG_E2E_RUNBOOK.md` | Bản mirror theo đường dẫn prompt (không tracked) |

---

## 8. Diff tài liệu / config (tóm tắt)

- Runbook: file mới dài ~400+ dòng (mục 1–20 + phụ lục endpoint).  
- Sample TXT: vài dòng nội dung cố định `RAG-E2E-31415`.  
- `.gitignore`:

```diff
-docs/
+docs/*
+!docs/RAG_TARGET_ARCHITECTURE.md
+!docs/RAG_CORE_FLOW_E2E_RUNBOOK.md
+!docs/CURSOR_REPORT_20F_CORE_RAG_E2E_RUNBOOK.md
+!docs/samples/
+!docs/samples/**
 reports/
```

---

## 9. Endpoint/API đã xác nhận từ source

| Method | Path |
|--------|------|
| POST | `/api/chatbots` |
| POST | `/api/documents/upload` |
| POST | `/api/documents/upload/{widgetId}` |
| GET | `/api/documents/{id}/status` |
| DELETE | `/api/documents/{id}` |
| POST | `/api/documents/{id}/retry` |
| POST | `/api/chat` |
| POST | `/api/chat/stream` |
| POST | `/api/public/chat` |
| GET | `/api/chatbots` (health nhẹ) |

---

## 10. Giả định còn lại

- Người chạy chỉnh SQL UUID theo kiểu cột thực tế trong DB họ.  
- PDF bước mở rộng: runbook ưu tiên TXT trước (theo yêu cầu); PDF có thể lặp upload với file PDF nhỏ sau.

---

## 11. Kết quả kiểm tra

| Kiểm tra | Kết quả |
|-----------|---------|
| File `docs/RAG_CORE_FLOW_E2E_RUNBOOK.md` tồn tại, không rỗng | **PASS** (đã ghi nội dung) |
| File `docs/samples/RAG_E2E_SAMPLE.txt` tồn tại, không rỗng | **PASS** |
| `git diff --stat` cho runbook + sample + `.gitignore` | Sau `git add` các file `docs/...` mới: thấy thống kê diff; `reports/refactor/...` vẫn ngoài index nếu không bỏ ignore `reports/` |
| `mvnw -DskipTests compile` | **PASS** (không đổi Java trong task 20F; compile baseline sau phiên) |
| Runtime E2E thật | **NOT RUN** |

---

## 12. Rủi ro còn lại

- `.gitignore` mới: mọi file `docs/*` khác ngoài whitelist vẫn bị ignore — nếu team cần thêm file `docs/` vào git, phải thêm dòng `!docs/...`.  
- SQL `TRUNCATE` / `UUID_TO_BIN` có thể khác môi trường — runner phải tự điều chỉnh.

---

## 13. Đề xuất prompt tiếp theo

1. Chạy một lượt E2E theo runbook và chụp log + điền checklist.  
2. (Tuỳ chọn) Thêm `docs/samples/*.pdf` nhỏ cho bước PDF.  
3. (Tuỳ chọn) Actuator health nếu muốn probe chuẩn — cần thêm dependency (ngoài scope 20F).

---

**Kết thúc report 20F.**
