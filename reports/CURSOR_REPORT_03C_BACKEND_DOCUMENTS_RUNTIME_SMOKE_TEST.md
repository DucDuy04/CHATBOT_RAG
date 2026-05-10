# Cursor Report 03C - BACKEND_DOCUMENTS_RUNTIME_SMOKE_TEST

## 1. Mức độ hiểu task

- **Task là gì?** Chạy smoke test runtime thật cho nhóm Documents API (upload có tenant, list/filter, status, chunks, assign an toàn, retry an toàn, delete, invalid id), trên Docker MySQL/Qdrant + Backend; sửa bug nhỏ nếu FAIL; không mở rộng feature.
- **Hiểu task:** 95%
- **Phần chắc chắn:** Contract lấy từ `Frontend/src/api/documentsApi.js` và `DocumentController` trong repo; tenant upload qua `chatbotId`.
- **Phần còn giả định:** Backend trên `localhost:8080` có thể là JVM cũ — nếu không khớp source cần restart (đã xảy ra trong session).
- **Phạm vi không làm:** Purge Qdrant theo document, safe assign/retry lớn, Frontend, schema DB, refactor ingest.

## 2. Files/rules/reports đã đọc

| File | Mục đích đọc | Kết luận chính |
|------|----------------|----------------|
| `.cursor/rules/00-core-working-rule.mdc` | Scope | Minimal fix |
| `.cursor/rules/90-report-verification-rule.mdc` | Report | Chi tiết audit |
| `reports/CURSOR_REPORT_03A_BACKEND_DOCUMENTS_API_CONTRACT_ALIGNMENT_AND_SAFE_CANONICAL_ENDPOINTS.md` | Contract BE | Canonical routes + tenant |
| `reports/CURSOR_REPORT_03B_FE_DOCUMENT_UPLOAD_TENANT_CONTEXT.md` | FE upload | FormData `chatbotId` + `files` |
| `reports/CURSOR_REPORT_02B_RERUN_BACKEND_CHATBOTS_API_RUNTIME_SMOKE_TEST.md` | Chatbots smoke | POST `/api/chatbots` OK |
| `Frontend/src/api/documentsApi.js` | FE contract | Paths + shapes |
| `Backend/.../DocumentController.java` | Runtime mapping | `MSG_TENANT_REQUIRED`, assign blocked |
| `docker-compose.yml` | Services | mysql `ragchatbot-mysql`, qdrant `ragchatbot-qdrant` |

## 3. Runtime environment

| Item | Status | Notes |
|------|--------|------|
| Docker | UP | `docker compose up -d mysql qdrant` — images pulled/lần đầu ~5 phút |
| MySQL | RUNNING / healthy | `ragchatbot-mysql` port 3306 |
| Qdrant | RUNNING | `ragchatbot-qdrant` ports 6333–6334 |
| Backend | RUNNING | Khởi động lại bằng `.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev` sau khi dừng JVM cũ trên 8080 |
| Base URL | `http://localhost:8080` | |
| Env keys | Present (session) | `GROQ_API_KEY`, `NOMIC_API_KEY` nạp từ file `.env` ở root repo (không ghi giá trị trong report). Shell khởi tạo Maven/backend đã có biến môi trường. |

**Lưu ý quan trọng:** Lần đầu gọi `POST /api/documents/upload` nhận **404** — response `GET /api/documents` là **array legacy** → JVM đang chạy **không khớp source** có canonical Documents API. Sau **stop process Java trên 8080** và **start lại** Spring Boot từ workspace đã compile, mọi endpoint canonical hoạt động. Không phải bug code trong repo; là **runtime chưa deploy bản build mới**.

## 4. Documents API smoke test results

| Test | Endpoint | Expected | Actual | Status | Notes |
|------|-----------|----------|--------|--------|------|
| 1 Create chatbot | `POST /api/chatbots` | 2xx + UUID `id` | 200, `id=8b0c8bf9-0b60-4c2b-9fd3-bd94dc55448a` | PASS | Body: Docs Smoke Bot |
| 2 Upload no tenant | `POST /api/documents/upload` multipart chỉ `files` | 400 + message tenant | 400 `{"message":"chatbotId is required for document upload"}` | PASS | Sau restart backend |
| 3 Upload + chatbotId | `POST /api/documents/upload` + `chatbotId` + file TXT | 200, array `DocumentResponse` đủ field | 200, một phần tử `INDEXED`, `chunkCount` 1, đủ field FE | PASS | `documentId=e4f7774a-aee2-4bf4-a01b-0aa3915fdda9` |
| 4 List | `GET /api/documents?page=0&size=10` | `items`, `page`, `size`, `total`, `totalPages` | JSON đúng shape | PASS | |
| 5 Filter | `GET /api/documents?search=tmp-doc-smoke&chatbotId=<id>&status=INDEXED&...` | 200, có doc sau upload | 200, `total>=1`, doc khớp | PASS | |
| 6 Status | `GET /api/documents/{id}/status` | `id`, `status`, `progress`, `chunkCount`, `error` | Đúng; INDEXED, progress 100 | PASS | |
| 7 Chunks | `GET /api/documents/{id}/chunks` | Mảng có `chunkIndex`, `content`, `tokenCount`; sort | Một chunk index 0 | PASS | Không lộ vector |
| 8 Assign | `POST .../assign` + chatbot khác | 400 + message re-index / không silent move | 400 `{"message":"Gán document sang chatbot khác cần re-index vector trong Qdrant; hiện chưa hỗ trợ."}` | PASS | Bot 2: `88a7d7dc-7cfa-48cc-8e0d-eea17b323bc6`; JSON body qua file để tránh lỗi escape PowerShell |
| 9 Retry | `POST .../retry` khi doc không FAILED | 400 message rõ | 400 `{"message":"Chỉ có thể retry document đang FAILED."}` | PASS | |
| 10 Delete | `DELETE .../{id}` + verify | `success:true`; status 404; không còn trong list | 200 `success:true`; GET status 404 `Document not found`; search `tmp-doc-smoke` → `total:0` | PASS | Soft delete; **Qdrant vectors không purge** (giữ đúng limitation 03A) |
| 11 Invalid id | `GET .../not-a-uuid/status` | 400 + message | 400 `{"message":"Invalid document id"}` | PASS | |

## 5. Bugs found and fixed

**No source changes.**

- **Không sửa Java:** Mọi endpoint PASS sau khi backend chạy đúng bản source.
- **Observed operational issue:** JVM cũ trên 8080 không có canonical `/upload` → **404**; xử lý bằng **restart** backend, không đổi code.

## 6. Files changed

**No source changes.**

## 7. Validation results

| Command | Result | Notes |
|---------|--------|------|
| `cd Backend && .\mvnw.cmd -DskipTests compile` | PASS | Cuối session |
| `cd Backend && .\mvnw.cmd test` | PASS | 14 tests; QdrantConnectionTest chạy với MySQL+Qdrant local; cảnh báo client/server Qdrant version mismatch (không fail) |
| Frontend lint/build | NOT RUN | Ngoài scope 03C |

## 8. Known limitations / gaps

- **Qdrant:** Điểm vector của document đã xóa có thể vẫn tồn tại (chưa purge-by-document) — đã biết từ 03A.
- **Client lib vs server:** Log Surefire: Qdrant client 1.13 vs server 1.17 — chỉ warning trong test.
- **Chatbots thử nghiệm:** Hai chatbot smoke (`Docs Smoke Bot`, `Docs Smoke Bot 2`) vẫn trong DB sau session; document smoke đã **DELETE**. Có thể xóa chatbot qua API sau nếu cần dọn dẹp.
- **File tạm:** `tmp-doc-smoke.txt` và `tmp-assign-body.json` đã xóa sau test.

## 9. Final decision

**All Documents API smoke tests PASS. Proceed to next backend prompt.**

(Điều kiện: backend phải là bản build khớp repo; MySQL + Qdrant + API keys embedding như trong session.)

## 10. Recommended next prompt

- Nếu tiếp tục hardening backend: **`03D_BACKEND_DOCUMENTS_DELETE_QDRANT_VECTOR_CLEANUP`** (chỉ khi có thiết kế safe purge — ngoài scope trước đó).
- Hoặc tích hợp FE end-to-end với API thật: **`04_FE_DOCUMENTS_E2E_REAL_API`** / playground smoke tương tự.

---

*Bản sao workspace rule:* có thể đồng bộ sang `docs/CURSOR_REPORT_03C_BACKEND_DOCUMENTS_RUNTIME_SMOKE_TEST.md`.
