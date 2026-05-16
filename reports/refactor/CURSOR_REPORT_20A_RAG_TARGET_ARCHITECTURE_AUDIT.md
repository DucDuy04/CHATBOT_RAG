# Cursor Report 20A — RAG Target Architecture Audit (Documentation Only)

**Ngày:** 2026-05-13  
**Loại task:** Chỉ đọc source + tài liệu hiện có; **không** sửa logic Java/React; **không** chạy build runtime.  
**Output:** `docs/RAG_TARGET_ARCHITECTURE.md` (mới), report này (mới).

---

## 1. Mức độ hiểu task

| Mục | Nội dung |
|-----|----------|
| **Hiểu task** | **94%** |
| **Chắc chắn** | Phạm vi “chỉ docs”; danh sách file bắt buộc đọc; cấu trúc backend/FE/deploy chính; pipeline RAG offline/online từ các class đã đọc; payload Qdrant + purge từ `EmbeddingService` / `QdrantPurgeService`; hằng số retrieval từ `RagRetrievalService`. |
| **Giả định** | Roadmap phase P3+ là **đề xuất** triển khai — chưa được user phê duyệt ưu tiên nghiệp vụ; một số endpoint/settings có thể tiếp tục đổi ngoài scope audit này. |
| **Thiếu dữ kiện** | Không chạy app thật nên không xác nhận runtime behavior (latency thực, point id LangChain4j). Không đọc **toàn bộ** từng dòng `RagRetrievalService.java` (~900+ dòng) — đã đọc phần đầu + grep STEP/constants + đọc khối lexical anchors. |

---

## 2. Tóm tắt yêu cầu

Tạo kiến trúc đích **modular monolith**, có roadmap phase nhỏ, phân biệt SOURCE vs PROPOSE; mô tả data, offline/online RAG, table-aware, hybrid 3 mức, evaluation, ràng buộc máy yếu; tạo `docs/RAG_TARGET_ARCHITECTURE.md` + report chi tiết dưới `reports/refactor/`.

---

## 3. Phạm vi đã làm

- Đọc các nhóm file theo prompt (rule, agent, reports 11B/12A/12B, backend API/config/service/domain chính, FE package/App/api sample, deploy, env examples).
- Tạo `docs/RAG_TARGET_ARCHITECTURE.md` đủ 20 mục theo yêu cầu (có Mermaid, bảng mapping, bảng phase).
- Tạo report audit này.

---

## 4. Phạm vi không làm

- Không sửa Java/React/YAML runtime.
- Không chạy `mvn compile`, `npm build` (task chỉ tài liệu — theo chỉ dẫn user).
- Không đọc **từng** file trong `Frontend/src/pages/**/*.jsx` (75 file) — đã đọc `App.jsx`, glob cấu trúc, `axiosInstance.js`, `widget/widget.js` một phần; agent `04-frontend.md` bổ sung bối cảnh.

---

## 5. Danh sách file đã đọc

### 5.1 Rule / convention / tài liệu

| Path | Đọc để | Kết luận chính |
|------|---------|----------------|
| `.cursor/rules/00-core-working-rule.mdc` | Luật nền | Stack, minimal diff, report `docs/` mặc định workspace |
| `.cursor/rules/10-backend-rag-rule.mdc` | Gợi ý backend | File trọng tâm RAG |
| `.cursor/rules/20-frontend-widget-rule.mdc` | Gợi ý FE | Widget/SSE |
| `.cursor/rules/30-deploy-env-ops-rule.mdc` | Deploy | Compose, env |
| `.cursor/rules/40-db-vector-rule.mdc` | DB/Qdrant | Không đổi schema tùy tiện |
| `.cursor/rules/90-report-verification-rule.mdc` | Report | Template chi tiết cho task sửa code |
| `README.md` | Root | Chủ yếu hướng dẫn copy `.cursor/rules` |
| `TASK_PROMPT_TEMPLATE.md` | Template task | Cấu trúc giao task |
| `agent.md` | Điều hướng | Snapshot stack + luồng |
| `agent/01-overview.md` | Tổng quan | Multi-tenant widget |
| `agent/02-architecture.md` | Kiến trúc | 7 bước retrieval, payload Qdrant |
| `agent/03-backend.md` | Backend | Ingestion + chat pipeline |
| `agent/04-frontend.md` | FE | Routes legacy + widget |
| `agent/05-api.md` | API | Một phần **stale** so với code (vd delete document đã có) |
| `agent/06-operations.md` | Ops | Env, rủi ro |
| `reports/CURSOR_REPORT_11B_MVP_RAG_QUALITY_EMBED_ORIGINS_DASHBOARD_UX.md` | RAG quality gần đây | Rerank lock, expansion, dashboard |
| `reports/CURSOR_REPORT_12A_CORE_INGESTION_EMBEDDING_FLOW_AUDIT.md` | Ingest | Purge trước/sau 12B, `qdrantPointId` |
| `reports/CURSOR_REPORT_12B_QDRANT_PURGE_AND_FILE_TYPE_CONSISTENCY.md` | Purge + validate | Filter delete, PDF/TXT |

**Không tồn tại / không đọc:** Toàn bộ `docs/*.md` gần nhất ngoài việc tạo mới — workspace rule hay nhắc `docs/` report; user yêu cầu report dưới `reports/refactor/`. Không có file `docs/CURSOR_REPORT_*` bắt buộc cho task này.

### 5.2 Backend

| Path | Đọc để | Kết luận chính |
|------|---------|----------------|
| `Backend/pom.xml` | Stack | Spring Boot 3.4.4, Java 21, LC4j, PDFBox, Tabula |
| `Backend/src/main/resources/application.yml` | Core config | Profile dev, multipart 50MB, cleaner |
| `Backend/src/main/resources/application-dev.yml` | Dev | MySQL, Qdrant, Groq, Nomic, Cohere |
| `Backend/src/main/resources/application-docker.yml` | Docker | Host `mysql`, `qdrant` |
| `Backend/.../api/*Controller.java` (10 file) | API surface | Chat, documents, widgets, chatbots, public, playground, analytics, dashboard, settings |
| `Backend/.../config/SecurityConfig.java` | Security | permitAll rộng, CORS localhost |
| `Backend/.../config/WidgetAuthFilter.java` | Auth chat | Path chính xác `/api/chat`, `/api/chat/stream`, public chat |
| `Backend/.../config/QdrantConfig.java` | Vector | Collection bootstrap |
| `Backend/.../config/GroqConfig.java` | AI beans | Groq chat + Nomic embed |
| `Backend/.../config/AppConfig.java` | Pool | `streamingExecutor` sizing |
| `Backend/.../service/DocumentService.java` (đoạn đầu + soft delete) | Ingest + delete | `executeProcessing`, purge trước soft delete |
| `Backend/.../service/DocumentParserService.java` (đầu) | Parse | PDF+Tabula, chỉ pdf/txt |
| `Backend/.../service/ChunkingService2.java` (đầu) | Chunk | Hằng số chunk/table |
| `Backend/.../service/EmbeddingService.java` | Embed/search | Payload keys, HTTP search |
| `Backend/.../service/RagRetrievalService.java` (đầu + đoạn lexical) | Retrieval | Constants, STEP comments, lexical anchors |
| `Backend/.../service/QueryAnalyzerService.java` (đầu + grep rewrite) | Intent | QueryType enum, rewriteQuery variants |
| `Backend/.../service/RerankService.java` | Rerank | Cohere, MAX 50, threshold |
| `Backend/.../service/PromptBuilderService.java` (đầu) | Prompt | System prompt dài, table/count rules |
| `Backend/.../service/ChatService.java` (đầu) | Chat | retrieveWithMetadata, stream executor |
| `Backend/.../service/QdrantPurgeService.java` | Purge | Filter document_id + widgetId |
| `Backend/.../service/AnalyticsService.java` (đầu) | Analytics | UNANSWERED_HINTS, summary |
| `Backend/.../domain/document/*.java` | Schema chunks/sections/tables | `DocumentTable.json_content` |
| `Backend/.../domain/chat/*.java` + `ChatFeedback.java` | Chat | sources JSON, feedback |
| `Backend/.../domain/widget/WidgetConfig.java` | Tenant | apiKey, JSON fields |
| `Backend/.../domain/settings/SettingsProfile.java` | Settings | Singleton pattern |
| `Backend/.../domain/settings/SettingsApiKey.java` | API keys | Hash at rest |
| `Backend/src/test/java/**/*.java` (3 file, đọc 1) | Test | Parser ordering regression |

### 5.3 Frontend

| Path | Đọc để | Kết luận chính |
|------|---------|----------------|
| `Frontend/package.json` | Deps | React 19, Vite 7, zustand |
| `Frontend/src/App.jsx` | Routes | Private dashboard/chatbots/documents/...; `/widget` public |
| `Frontend/src/main.jsx` | Entry | StrictMode |
| `Frontend/src/api/axiosInstance.js` | HTTP | Bearer token, VITE_API_URL |
| `Frontend/vite.config.js` | Dev | Proxy `/api` |
| `Frontend/vite.widget.config.js` | Widget build | IIFE output `dist-widget` |
| `Frontend/widget/widget.js` (đầu) | Embed | `RagChatbotConfig`, iframe URL |
| **Glob** `Frontend/src/api/*.js` | Kiểm tra tồn tại | 13 file API client |
| **Glob** `Frontend/src/pages/**/*.jsx` | Kiểm tra tồn tại | 75 file — cấu trúc module theo feature |

### 5.4 Deploy / env

| Path | Đọc để | Kết luận chính |
|------|---------|----------------|
| `.env.example` | Root env | Placeholder keys |
| `Backend/.env.example` | BE | GROQ/NOMIC/Cohere note |
| `Frontend/.env.example` | FE | VITE_API_URL, mock flag |
| `docker-compose.yml` | Stack | 4 services + volumes |
| `Backend/Dockerfile` | BE image | Multi-stage jar |
| `Frontend/Dockerfile` | FE image | nginx serve |
| `Frontend/nginx.conf` | Reverse proxy | `/api` → backend; client_max_body_size 20M |

---

## 6. Kiến trúc hiện tại xác nhận từ source

- **Monolith Spring Boot** một JAR, tách FE build Vite.
- **Persistence:** MySQL (metadata, chunks text, sessions, messages, feedback, settings) + Qdrant (vectors, payload filter `widgetId`).
- **Ingestion:** `DocumentService.executeProcessing` = parse → `ChunkingService2` → lưu sections/tables/chunks → `EmbeddingService.embedAndStore` → COMPLETED.
- **Online RAG:** `ChatService` gọi `RagRetrievalService.retrieveWithMetadata` → contexts → `PromptBuilderService` → `LlmFallbackService` / stream; lưu message + sources.
- **Retrieval:** Multi-step với vector + DB expansion + lexical anchors trong doc scope + optional Cohere rerank + char budget theo intent/lock.
- **Widget:** Filter theo path cụ thể; playground/public dùng `chatbotId` hoặc header tương ứng.

---

## 7. Vấn đề / rủi ro kiến trúc hiện tại

1. **Bảo mật bề mặt rộng** (`permitAll`) — document/chatbot/settings không qua cùng cơ chế với widget chat.
2. **CORS** chưa generalize cho production origin thực.
3. **Schema drift** vì `ddl-auto=update`.
4. **Tải máy yếu:** context locked 64k + nhiều variant vector search + expansion có thể gây spike RAM/CPU.
5. **Table/COUNT:** vẫn phụ thuộc LLM đếm trừ khi có structured path (chưa có `document_table_rows`).
6. **Controller assign:** logic trả lỗi sau `assignDocument` — dễ gây 400 sai kỳ vọng (SOURCE `DocumentController.assign`).

---

## 8. Kiến trúc đích đã đề xuất

Tóm tắt: Giữ **một** backend; **package modules** theo bounded context; hybrid search **lớp hóa**; table-aware **tăng dần** tới SQL row store; evaluation **tận dụng** messages/sources/feedback + golden nhẹ.

Chi tiết đầy đủ: `docs/RAG_TARGET_ARCHITECTURE.md`.

---

## 9. Vì sao kiến trúc này phù hợp máy yếu (1.5GB RAM / 1 CPU / 15GB SSD)

- **Không** thêm microservice hay JVM thứ hai mặc định.
- Tắt rerank/bật theo widget; giữ Qdrant + MySQL đã có — không bắt thêm OpenSearch sớm.
- Phase nhỏ: an toàn rollback; tránh big-bang migration.
- Điều chỉnh budget/limit có thể làm sau khi có metric (PROPOSE).

---

## 10. Danh sách file đã tạo / cập nhật

| Path | Hành động | Layer |
|------|-----------|-------|
| `docs/RAG_TARGET_ARCHITECTURE.md` | Tạo mới | docs |
| `reports/refactor/CURSOR_REPORT_20A_RAG_TARGET_ARCHITECTURE_AUDIT.md` | Tạo mới | reports |

---

## 11. Diff thay đổi của từng file tài liệu

### 11.1 `docs/RAG_TARGET_ARCHITECTURE.md`

- **Hiện trạng cũ:** File không tồn tại.
- **Đã tạo:** Toàn bộ nội dung kiến trúc đích + mapping + roadmap + Mermaid.
- **Vì sao:** Deliverable task 20A.
- **Ảnh hưởng:** Chỉ tài liệu; không ảnh hưởng runtime.

```diff
diff --git a/docs/RAG_TARGET_ARCHITECTURE.md b/docs/RAG_TARGET_ARCHITECTURE.md
new file mode 100644
--- /dev/null
+++ b/docs/RAG_TARGET_ARCHITECTURE.md
@@ -0,0 +1,433 @@
+# RAG Target Architecture — CHATBOT_RAG
+
+**Loại tài liệu:** Kiến trúc đích + roadmap triển khai, dựa trên **source thực tế** trong repo ...
+
+**Chú thích nguồn:**
+
+| Ký hiệu | Ý nghĩa |
+|--------|---------|
+| **SOURCE** | Xác nhận từ class/file/config trong repo ...
+| **PROPOSE** | Đề xuất kiến trúc / phase tương lai ...
+
+## 1. Executive summary
+...
+## 20. Backlog prompts đề xuất tiếp theo
+...
```

> **Ghi chú:** File có **433** dòng (`git diff --cached --stat` sau `git add`). Nội dung đầy đủ không paste hết vào report — **source of truth** là file trong repo.

### 11.2 `reports/refactor/CURSOR_REPORT_20A_RAG_TARGET_ARCHITECTURE_AUDIT.md`

- **Hiện trạng cũ:** File không tồn tại; thư mục `reports/refactor/` có thể chưa có — đã tạo bằng cách ghi file path đầy đủ.
- **Đã tạo:** Report audit (mục 1–13 trong yêu cầu user).
- **Vì sao:** Bắt buộc theo prompt 20A.

```diff
diff --git a/reports/refactor/CURSOR_REPORT_20A_RAG_TARGET_ARCHITECTURE_AUDIT.md b/reports/refactor/CURSOR_REPORT_20A_RAG_TARGET_ARCHITECTURE_AUDIT.md
new file mode 100644
--- /dev/null
+++ b/reports/refactor/CURSOR_REPORT_20A_RAG_TARGET_ARCHITECTURE_AUDIT.md
@@ -0,0 +1,276 @@
+# Cursor Report 20A — RAG Target Architecture Audit (Documentation Only)
+...
```

---

## 12. Các điểm còn giả định / chưa chắc

- Độ chính xác **toàn bộ** nhánh `RagRetrievalService` (table expansion, rerank-guided lock đầy đủ) chưa đọc từng dòng — dựa trên grep + đoạn đọc tay.
- Hành vi LangChain4j **point id** và payload `text_segment` — không mở jar.
- **Production traffic** và kích thước corpus trung bình — không có số liệu.

---

## 13. Đề xuất prompt tiếp theo theo thứ tự ưu tiên

1. Sửa `DocumentController.assign` — dead path HTTP sau service (SOURCE).
2. Thêm `proxy_read_timeout` SSE trong `Frontend/nginx.conf` + tài liệu compose host networking.
3. Flyway baseline + tắt `ddl-auto` trên prod profile.
4. Rate limit upload/chat theo widget.
5. Module package move (P2) với `mvn test` full.
6. `retrieval_logs` tối thiểu (JSON column trên `chat_messages` hoặc bảng riêng).
7. PoC `document_table_rows` cho một document mẫu COUNT.

---

## 14. Kết quả kiểm tra sau task (nhẹ)

| Kiểm tra | Kết quả | Ghi chú |
|----------|---------|---------|
| `git add` + `git diff --cached --stat` | **PASS** | `709 insertions`, `433` dòng `docs/RAG_TARGET_ARCHITECTURE.md`, `276` dòng report (sau đó `git reset HEAD` để bỏ stage) |
| `git diff -- docs/... reports/...` (untracked) | **NOT RUN** meaningful | File mới chưa tracked → `git diff` không hiện; đã dùng `git diff --cached` khi staged |
| File tồn tại + không rỗng | **PASS** | PowerShell: byte size ~24KB cho kiến trúc doc |
| Mermaid | **manual check only** | `flowchart` / `sequenceDiagram` |

**Không claim:** runtime verified, build pass.

---

## 15. Lệnh đã chạy (agent)

```text
cd E:\chatbot-rag-workspace\CHATBOT_RAG
git add docs/RAG_TARGET_ARCHITECTURE.md reports/refactor/CURSOR_REPORT_20A_RAG_TARGET_ARCHITECTURE_AUDIT.md
git diff --cached --stat
git diff --cached -- docs/RAG_TARGET_ARCHITECTURE.md | Select-Object -First 50
git reset HEAD docs/RAG_TARGET_ARCHITECTURE.md reports/refactor/CURSOR_REPORT_20A_RAG_TARGET_ARCHITECTURE_AUDIT.md
```

---

*End of report 20A.*
