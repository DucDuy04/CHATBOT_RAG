# CURSOR_REPORT_21E2 — Runtime verify section 7 sau parser 21D2

## 1. Mức độ hiểu task

- **~98%** — chạy đúng luồng Docker, API, MySQL, Qdrant, checklist G0/G1 + kiểm tra mới cho mục 7, optional OOS chat.
- **Chắc chắn:** lệnh đã chạy, số liệu SQL/Qdrant/upload lấy trực tiếp từ runtime; không sửa source Java trong phiên.
- **Giả định nhỏ:** đánh giá semantic câu trả lời O01/O02 dựa trên cấu trúc từ chối + không fact bịa; log terminal PowerShell có thể mojibake Unicode nhưng API trả UTF-8.

## 2. Tóm tắt yêu cầu

Xác minh runtime sau fix **21D2** (ATX mục 7): rebuild stack, chatbot eval mới, upload `RAG_GOLDEN_TEST_DOCUMENT.txt`, `INDEXED`, kiểm tra MySQL (`document_sections` / `document_chunks` / `document_tables`), Qdrant (count + payload), regression so 21E, optional **GQ-O01/O02**, kết luận **G1 metadata** (đặc biệt section 7). **Không** sửa Java/retrieval/prompt/ChatService/frontend/dependency/migration/`.gitignore`.

## 3. Phạm vi đã làm

- Đọc tài liệu eval/checklist/runbook và báo cáo 21D1/21E/21D2 theo brief.
- `docker compose up --build -d` + `docker compose config -q` + `docker compose ps`.
- Kiểm tra env Groq/Nomic trong container (presence-only).
- `POST /api/chatbots` → `POST /api/documents/upload` → xác nhận status trong response.
- SQL MySQL theo `document_id` = `UNHEX(REPLACE(UUID,'-',''))`.
- Qdrant `points/count` + `points/scroll` (không vector).
- `POST /api/chat` hai lần (O01, O02) với `X-Widget-Key`, session mới.
- `.\mvnw.cmd -DskipTests compile` và `.\mvnw.cmd -q -Dtest=ParserAndOrderingTests test` trên host.
- Tạo `docs/eval/results/RAG_RUNTIME_VERIFY_21E2_SECTION7_20260514.md` và báo cáo này.

## 4. Phạm vi không làm

- Không sửa `DocumentParserService`, retrieval, prompt, `ChatService`, frontend, Docker compose/schema, `.gitignore`.
- Không chạy full `mvnw test` toàn module.
- Không chạy Frontend lint/build/widget build.
- Không cập nhật `docs/eval/RAG_BASELINE_CORE_CHECKLIST.md` (kết quả checklist chi tiết nằm trong file result 21E2).

## 5. File đã đọc

| Path | Mục đích | Kết luận ngắn |
|------|----------|----------------|
| `docs/eval/results/RAG_PARSER_FIX_21D2_20260514.md` | Bối cảnh fix 21D2 | Unit 10 chunks; runtime SQL cuối cần operator |
| `docs/eval/results/FIX_LOOP_21D2_ATX_LAST_SECTION.md` | Fix loop | Heuristic `tài liệu` + outline số |
| `reports/refactor/CURSOR_REPORT_21D2_ATX_LAST_SECTION_PARSER_FIX.md` | Chi tiết 21D2 | Root cause + diff tóm tắt |
| `docs/eval/results/RAG_RUNTIME_VERIFY_21E_20260514.md` | Baseline 21E | G1 PARTIAL thiếu mục 7 |
| `reports/refactor/CURSOR_REPORT_21E_RUNTIME_VERIFY_AFTER_PARSER_FIX.md` | Report 21E | Đồng bộ symptom |
| `docs/eval/RAG_BASELINE_CORE_CHECKLIST.md` | ID checklist | G1 items + block 21E |
| `docs/eval/RAG_BASELINE_CORE_REBUILD_PLAN.md` | Gate workflow | G1 trước retrieval |
| `docs/eval/RAG_EVALUATION_RUNBOOK.md` | API chat/upload | `.txt` golden; `X-Widget-Key` |
| `docs/eval/RAG_GOLDEN_TEST_DOCUMENT.txt` | Nội dung mục 7 | `## 7. Phạm vi…` + USD/CEO |
| `docs/eval/RAG_GOLDEN_TEST_DOCUMENT.md` | Tồn tại | Bản `.md` song song `.txt` (runbook) |
| `docs/eval/RAG_GOLDEN_QUESTIONS.md` | GQ-O01/O02 | OUT_OF_SCOPE criteria |
| `docker-compose.yml` | Stack | Port 8080/3306/6333 |
| `Backend/.../application-docker.yml` | Qdrant | `documents`, 768 |
| `Backend/.../SecurityConfig.java` | Auth | `/api/chatbots`, `/api/chat` permit |
| `Backend/.../DocumentController.java` | Upload | `files` + `chatbotId` → `List<DocumentResponse>` |
| `Backend/.../ChatbotController.java` | Create | `POST` body `name`, … |
| `Backend/.../ChatbotCreateRequest.java` / `ChatbotResponse.java` | DTO | `apiKey` chỉ khi create |
| `Backend/.../ChatResponse.java` | Chat DTO | Không có `sourceCount` — dùng `sources.size` |
| `Backend/.../DocumentSection.java` / `DocumentChunk.java` | SQL columns | `title`, `section_title`, `heading_path_text` |

## 6. Môi trường chạy

- OS: Windows 10 build 19041.
- Docker: services như `docker compose ps` trong file result 21E2.
- Git HEAD: `a014357`.

## 7. Env check

- `GROQ_API_KEY`, `NOMIC_API_KEY`: **có** trong container backend (kiểm tra `test -n "$VAR"`).
- Không ghi giá trị key vào báo cáo/result.

## 8. Build / restart result

- `docker compose up --build -d` — **PASS** (exit 0; backend image rebuild Maven trong Docker ~3–6 phút tổng phiên).
- Backend container **Up**, port 8080 mở.

## 9. Upload golden result

- `POST /api/documents/upload` — **PASS** (200).
- `DOCUMENT_ID=ea5cfde1-b84b-4d22-8636-c0514a51b4c6`, `chunkCount=10`, `status=INDEXED`, `progress=100`.

## 10. G1 metadata verification

### Sections

- **8** rows: `General` + mục `1.` … `7.` — có đủ **`7. Phạm vi KHÔNG có trong tài liệu…`**.
- False-root patterns (list chính sách / bước quy trình): **0 row**.

### Chunks

- Bước 2 → **Quy trình** (mục 3): **PASS**.
- 24h + 15 phút → **Chính sách** (mục 2): **PASS**.
- Table chunks → **Bảng gói dịch vụ** (mục 4): **PASS**.
- Chunk chứa **USD/VND** / **CEO** / **Mã chứng khoán** → **mục 7** (`text_table_like`, `chunk_index=9`): **PASS**.

### Tables

- **1** row `document_tables`; markdown chứa Basic/Pro/Business/99000/Ưu tiên; title khớp mục 4.

### Qdrant

- `count(widgetId + document_id) = 10` = `chunkCount`.
- Payload: quy trình → mục 3; table → mục 4; mục 7 → `section_title` / `heading_path_text` chứa **Phạm vi**; không có table chunk gắn title sai **Bước 3** độc lập.

## 11. Optional OOS result

- **GQ-O01:** answer từ chối cung cấp tỷ giá (không bịa số); **10** sources (kể cả mục 7) — **PASS** xử lý OOS; attribution vẫn “full window” giống pattern 21E.
- **GQ-O02:** answer từ chối (không bịa tên CEO); **10** sources — **PASS**.

## 12. Checklist status summary

- Bảng đầy đủ G0/G1 + mục mới + O01/O02: xem **mục 10** trong `docs/eval/results/RAG_RUNTIME_VERIFY_21E2_SECTION7_20260514.md`.

## 13. So sánh với 21E / 21D2

- **21E:** runtime chứng minh thiếu **section 7** + chunk OOS dính mục 6; 9 points Qdrant.
- **21D2:** unit/parser **10** chunks + **8** sections — **chưa** có bằng chứng SQL/Qdrant đầy đủ cho image cuối trong báo cáo 21D2.
- **21E2:** runtime khớp unit 21D2 — **8** sections, **10** chunks/points, metadata mục 7 **đúng**.

## 14. Có sửa code không?

**Không** — không thay đổi Java/Spring, retrieval, prompt, Docker compose, frontend, `.gitignore`.

## 15. No code diff

```diff
# Không có thay đổi source trong task 21E2 (verify-only).
# Chỉ thêm tài liệu kết quả:
#   docs/eval/results/RAG_RUNTIME_VERIFY_21E2_SECTION7_20260514.md
#   reports/refactor/CURSOR_REPORT_21E2_RUNTIME_VERIFY_SECTION7_AFTER_21D2.md
```

## 16. Kết luận

- **Section 7 runtime:** **PASS** — có row `document_sections`; chunk & Qdrant gắn đúng **Phạm vi** / mục 7.
- **G1 metadata tổng:** **PASS** (hết **PARTIAL** của 21E do thiếu mục 7).
- **Case cải thiện:** inventory section/chunk/Qdrant cho **`## 7. Phạm vi…`**; `chunkCount` 9 → 10.
- **Regression so 21E:** không phát hiện — Bước 2, chính sách, bảng, false list vẫn **PASS**.

## 17. Rủi ro còn lại

- Chat O01/O02 vẫn trả **~10 sources** (full-window) — nhiễu citation giống 21E; không phải lỗi parser G1.
- **GQ-T01** (generation vs bảng 99000) **chưa** rerun trong task này — vẫn là rủi ro P0 theo 21E nếu product cần.

## 18. Đề xuất prompt tiếp theo

1. Task **T01 / generation**: guard hoặc prompt khi context chứa bảng giá — tránh phủ nhận sai (ngoài scope verify 21E2).
2. Task **retrieval / source cap**: giảm số source trả về UI/context khi tenant một doc nhỏ (L01/T02/O01 pattern).
3. **Không** mở thêm parser task cho golden TXT ATX mục 7 — G1 đã **PASS** runtime.

---

## Kiểm tra sau task

| File | Trạng thái |
|------|------------|
| `docs/eval/results/RAG_RUNTIME_VERIFY_21E2_SECTION7_20260514.md` | Tồn tại, có nội dung audit |
| `reports/refactor/CURSOR_REPORT_21E2_RUNTIME_VERIFY_SECTION7_AFTER_21D2.md` | Tồn tại |

| Command | Kết quả | Ghi chú |
|---------|---------|---------|
| `docker compose config -q` | **PASS** | Exit 0 |
| `docker compose up --build -d` | **PASS** | Exit 0 |
| `Backend\.\mvnw.cmd -DskipTests compile` | **PASS** | |
| `Backend\.\mvnw.cmd -q -Dtest=ParserAndOrderingTests test` | **PASS** | |
| `cd Frontend && npm run lint` | **NOT RUN** | Ngoài scope |
| `cd Frontend && npm run build` | **NOT RUN** | Ngoài scope |
| `cd Frontend && npm run build:widget` | **NOT RUN** | Ngoài scope |
