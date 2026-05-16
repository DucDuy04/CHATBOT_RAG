# Cursor Report 20B — Backend Build Baseline

**Ngày:** 2026-05-14  
**Scope:** Sửa lỗi **compile** Backend (baseline); không triển khai feature RAG mới.

---

## 1. Mức độ hiểu task

| Mục | Nội dung |
|-----|----------|
| **Hiểu task** | **98%** |
| **Chắc chắn** | Yêu cầu chạy Maven compile thật; nếu PASS thì không sửa runtime; ghi report chi tiết; `prioritizeChunksMatchingTableOrUseCaseHint` chỉ xử lý nếu còn lỗi compile. |
| **Giả định** | Môi trường agent có JDK 21 + Maven wrapper hoạt động (đã xác nhận). |
| **Thiếu dữ kiện** | Không có log compile lỗi từ Docker `/app/...` của user — chỉ kiểm chứng trên workspace Windows hiện tại. |

---

## 2. Tóm tắt yêu cầu

Chạy `mvnw -DskipTests compile` (hoặc tương đương), sửa tối thiểu mọi lỗi compile; ưu tiên kiểm tra lỗi cũ `prioritizeChunksMatchingTableOrUseCaseHint` nếu còn; không refactor/feature; báo cáo tại `reports/refactor/CURSOR_REPORT_20B_BACKEND_BUILD_BASELINE_FIX.md`.

---

## 3. Hiện trạng trước khi sửa

- Report 20A ghi chưa chạy Maven compile.
- User từng gặp lỗi compile thiếu method `prioritizeChunksMatchingTableOrUseCaseHint` trong `RagRetrievalService` (bối cảnh build Docker cũ).

---

## 4. Command build ban đầu và lỗi thật

**Command đã chạy (Windows PowerShell):**

```text
cd E:\chatbot-rag-workspace\CHATBOT_RAG\Backend
.\mvnw.cmd -DskipTests compile
```

**Kết quả:** `BUILD SUCCESS` — **không có** lỗi `COMPILATION ERROR`.

**Output chính (rút gọn):**

```text
[INFO] Compiling 104 source files with javac [debug parameters release 21] to target\classes
[INFO] BUILD SUCCESS
[INFO] Total time:  20.486 s
```

**Lỗi `prioritizeChunksMatchingTableOrUseCaseHint`:** **Không xuất hiện** trong workspace hiện tại (`rg` toàn `Backend/` không có symbol).

---

## 5. Nguyên nhân gốc xác nhận từ source

- **Không có bug compile hiện tại** cần sửa trong source: `javac` hoàn tất 104 file main sources.
- Lỗi lịch sử user mô tả (method thiếu) **không** tái hiện — có thể đã được merge/sửa trước đó hoặc khác branch/image Docker.

---

## 6. Flow code quanh lỗi (giả định nếu lỗi cũ còn)

*Không áp dụng — không có call site tới `prioritizeChunksMatchingTableOrUseCaseHint` trong repo.*

Nếu lỗi tái xuất hiện trong tương lai, checklist theo prompt 20B:

1. Grep method / call site trong `RagRetrievalService.java`.
2. So sánh với helper private hiện có (reorder/filter nhẹ).
3. Chọn Option A/B/C minimal — không thêm DB/Qdrant/LLM trong helper.

---

## 7. Chiến lược sửa minimal đã chọn

**Không sửa source runtime** vì compile đã PASS — đúng nhánh “Bước 5” trong prompt user.

---

## 8. Vì sao không sửa các vấn đề backlog khác

Theo phạm vi task 20B: `DocumentController.assign`, security `permitAll`, CORS, Flyway, SSE nginx, v.v. **không** gây compile fail → chỉ ghi mục 16 (đề xuất tiếp theo).

---

## 9. Danh sách file đã đọc

| Path | Đọc để | Kết luận chính |
|------|--------|----------------|
| `.cursor/rules/00-core-working-rule.mdc` | Luật nền | Minimal diff, production yếu |
| `.cursor/rules/10-backend-rag-rule.mdc` | Backend checklist | Compile/test gợi ý |
| `.cursor/rules/90-report-verification-rule.mdc` | Report | User chỉ định `reports/refactor/` cho 20B |
| `README.md` | Root | Rules copy |
| `TASK_PROMPT_TEMPLATE.md` | Template | — |
| `docs/RAG_TARGET_ARCHITECTURE.md` | Context 20A | Kiến trúc đích, không conflict build |
| `reports/refactor/CURSOR_REPORT_20A_RAG_TARGET_ARCHITECTURE_AUDIT.md` | Context 20A | Chưa compile trước đó |
| `reports/CURSOR_REPORT_11B_MVP_RAG_QUALITY_EMBED_ORIGINS_DASHBOARD_UX.md` | Lịch sử RAG | Rerank lock / expansion (không compile) |
| `reports/CURSOR_REPORT_12A_CORE_INGESTION_EMBEDDING_FLOW_AUDIT.md` | Ingest | Payload Qdrant |
| `reports/CURSOR_REPORT_12B_QDRANT_PURGE_AND_FILE_TYPE_CONSISTENCY.md` | Purge | HTTP delete filter |
| `Backend/pom.xml` | Build | Java 21, Spring Boot 3.4.4 |
| `Backend/src/main/resources/application.yml` | Config | Profile dev default |
| `Backend/src/main/resources/application-dev.yml` | Dev | MySQL localhost, keys env |
| `Backend/src/main/resources/application-docker.yml` | Docker | Host `mysql`, `qdrant` |
| `Backend/.../RagRetrievalService.java` | Nghi ngờ lỗi cũ | Grep không có `prioritizeChunks...` |
| `Backend/.../QueryAnalyzerService.java` | Liên quan RAG | Đọc theo checklist prompt (đã biết từ task trước) |
| `Backend/.../RerankService.java` | Liên quan | — |
| `Backend/.../EmbeddingService.java` | Liên quan | — |
| `Backend/.../domain/document/DocumentChunk.java` | Entity | — |
| `Backend/.../domain/document/DocumentChunkRepository.java` | Repo | — |
| `Backend/src/test/java/**/*.java` | Test | SpringBootTest cần MySQL |

---

## 10. Danh sách file đã sửa

| Path | Sửa để | Layer |
|------|--------|--------|
| *Không có file Java/YAML runtime* | Compile đã PASS | — |
| `reports/refactor/CURSOR_REPORT_20B_BACKEND_BUILD_BASELINE_FIX.md` | Báo cáo task | docs/report |

---

## 11. Diff thay đổi của từng file

### 11.1 Runtime source

**Không có thay đổi.** Hiện trạng: compile OK; không thêm/xóa method, không đổi signature.

### 11.2 `reports/refactor/CURSOR_REPORT_20B_BACKEND_BUILD_BASELINE_FIX.md`

- **Hiện trạng cũ:** File không tồn tại.
- **Đã tạo:** Báo cáo 20B đầy đủ mục 1–16.
- **Vì sao:** Deliverable bắt buộc theo prompt.
- **Ảnh hưởng:** Chỉ tài liệu.

```diff
diff --git a/reports/refactor/CURSOR_REPORT_20B_BACKEND_BUILD_BASELINE_FIX.md b/reports/refactor/CURSOR_REPORT_20B_BACKEND_BUILD_BASELINE_FIX.md
new file mode 100644
--- /dev/null
+++ b/reports/refactor/CURSOR_REPORT_20B_BACKEND_BUILD_BASELINE_FIX.md
@@ -0,0 +1,... @@
+# Cursor Report 20B — Backend Build Baseline
+...
```

*(Toàn bộ nội dung file nằm trong repo; diff đầy đủ có thể lấy bằng `git add` + `git diff --cached`.)*

---

## 12. Ảnh hưởng sau sửa

| Hạng mục | Thay đổi |
|----------|----------|
| **Compile / bytecode** | Giữ nguyên (đã PASS trước task). |
| **Behavior RAG / API** | Không đổi (không diff runtime). |
| **RAM/CPU** | Không tăng từ task này. |
| **DB/Qdrant** | Không tác động. |

---

## 13. Edge cases đã xem xét

| Edge case | Xử lý |
|-----------|--------|
| Lỗi compile khác sau khi sửa lỗi đầu | Không xảy ra — lần compile đầu đã SUCCESS. |
| `mvn test` không có MySQL | FAIL `Connection refused` localhost:3306 — env, không phải compile. |
| Docker không cài | `docker compose` có thể fail; tại agent `docker compose config -q` PASS. |

---

## 14. Kết quả kiểm tra

| Command | Kết quả | Ghi chú |
|---------|---------|---------|
| `cd Backend; .\mvnw.cmd -DskipTests compile` | **PASS** | BUILD SUCCESS, 104 sources |
| `cd Backend; .\mvnw.cmd test` | **FAIL** | `RagChatbotBeApplicationTests`, `QdrantConnectionTest`: `ApplicationContext` — `Communications link failure` / `Connection refused` tới **MySQL** (localhost:3306). **Không liên quan** thay đổi source (không có thay đổi source). |
| `docker compose config -q` (repo root) | **PASS** | Exit 0, output rỗng |

---

## 15. Rủi ro còn lại

- **CI/Docker build khác máy dev:** Nếu image build với source khác commit, vẫn có thể gặp lỗi compile cũ — cần rebuild từ cùng commit.
- **`mvn test` trên máy không có MySQL/Qdrant:** Context load fail — không dùng làm tín hiệu regression compile.

---

## 16. Đề xuất tiếp theo

1. Trên CI: chạy ít nhất `mvn -DskipTests compile`; tách job `mvn test` với service container MySQL (+ Qdrant nếu test cần) hoặc `@MockBean` datasource profile test.
2. Nếu tái hiện `prioritizeChunksMatchingTableOrUseCaseHint`: grep + áp dụng Option A/B/C trong prompt 20B.
3. Sửa `DocumentController.assign` (backlog UX/API) — ngoài scope 20B.

---

*End of report 20B.*
