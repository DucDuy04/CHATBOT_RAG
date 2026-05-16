# CURSOR_REPORT_21I — Full golden regression (baseline closure)

## 1. Mức độ hiểu task

- **~98%** — verify-only: full 12 golden case, upload mới, metadata sanity, delete + D01, so sánh 21B, không sửa code.

## 2. Tóm tắt yêu cầu

Chạy lại **đủ 12 case** từ `docs/eval/RAG_GOLDEN_QUESTIONS.md` trên `RAG_GOLDEN_TEST_DOCUMENT.txt` sau các fix 21D1/21D2/21G2/21H; ghi verdict; tính lại PASS/PARTIAL/FAIL; so với 21B (6/2/4); kết luận baseline và next step; **không** sửa Java/prompt/retrieval/frontend.

## 3. Phạm vi đã làm

- `docker compose up --build -d`, `docker compose config -q`, `docker compose ps`.
- Health API, Qdrant `collections`.
- `mvnw -DskipTests compile`, `QueryAnalyzerServiceTest`, `PromptBuilderServiceTest`.
- `POST /api/chatbots` → upload golden → poll `INDEXED` (chunkCount=10).
- MySQL: sections, chunk_type histogram, table→section 4, `deleted_at` sau delete.
- Qdrant `points/count` filter `document_id` + `widgetId` (10 → 0 sau delete).
- `POST /api/chat` ×11 (session mới/câu) + `DELETE` document + GQ-D01.
- Ghi kết quả: `docs/eval/results/RAG_FULL_GOLDEN_REGRESSION_21I_20260515.md`, `docs/eval/results/21i_raw_eval_output.txt`.

## 4. Phạm vi không làm

- Không sửa source Java, prompt, QueryAnalyzer, RagRetrieval, ChatService, frontend, Docker compose, schema, dependency, `.gitignore`, migration.

## 5. File đã đọc (tối thiểu)

| Path | Mục đích |
|------|----------|
| `docs/eval/RAG_GOLDEN_QUESTIONS.md` | 12 case + tiêu chí PASS/PARTIAL/FAIL |
| `docs/eval/RAG_EVALUATION_RUNBOOK.md` | API upload/chat/delete |
| `docs/eval/results/RAG_EVAL_RUN_21B_20260514.md` | Baseline so sánh |
| `docker-compose.yml` | Service names / ports |
| `ChatbotController` / `ChatController` / `DocumentController` / `WidgetAuthFilter` | Xác nhận endpoint & header (đọc grep/trước đó trong project) |
| `QdrantPurgeService` / `EmbeddingService` (grep) | Payload keys `document_id`, `widgetId` cho count |

Các file eval 21G3/21H/21E2 trong brief: tham chiếu ngữ cảnh; không cần đọc lại toàn bộ cho thực thi 21I.

## 6. Môi trường chạy

- Windows 10, Docker Desktop, ngày **2026-05-15**.

## 7. Env check

- Container `chatbot-backend`: `GROQ_API_KEY` **present**, `NOMIC_API_KEY` **present** (boolean only).

## 8. Build/restart result

| Bước | Kết quả |
|------|---------|
| `docker compose up --build -d` | **PASS** |

## 9. Compile/test result

| Command | Kết quả |
|---------|---------|
| `mvnw -DskipTests compile` | **PASS** |
| `mvnw -Dtest=QueryAnalyzerServiceTest test` | **PASS** |
| `mvnw -Dtest=PromptBuilderServiceTest test` | **PASS** |

## 10. Upload/index result

- **CHATBOT_ID:** `6dfabf0f-8aab-467c-9597-5889ee0ba963`
- **DOCUMENT_ID:** `8e0e17bc-148d-4cbe-bd22-a3363d0c02d8`
- **Status:** `INDEXED`, **chunkCount:** 10  
- **Widget key:** masked `48c7be23...cb8b`

## 11. Metadata sanity check

- **Sections:** 8 dòng (General + 7 mục) khớp checklist G1 (tên đầy đủ xem file kết quả chính thức).
- **Chunk types:** `text`×6, `text_table_like`×2, `table_summary`×1, `table_row_group`×1 — bảng gắn mục **4**.
- **Ghi chú:** truy vấn SQL mẫu `LIKE '%Bước 2%'` trong script trả rỗng (encoding/literal); **GQ-F04 PASS** chứng minh chunk quy trình có trong RAG.

## 12. Full golden cases (12)

| ID | sourceCount | Verdict | Ghi chú ngắn |
|----|-------------|---------|----------------|
| GQ-F01 | 10 | PASS | AlphaDemo |
| GQ-F02 | 1 | PASS | GOLDEN-VN-2026-714 |
| GQ-F03 | 10 | PASS | 500; cite mục 4 |
| GQ-F04 | 10 | PASS | billing/technical/other + 4h |
| GQ-L01 | 10 | PASS | đủ 3 chính sách |
| GQ-L02 | 10 | PASS | Basic, Pro, Business |
| GQ-T01 | 10 | PASS | Basic 99000 trong bảng |
| GQ-T02 | 10 | PARTIAL | có “ưu tiên” + hedge sai về “kênh” |
| GQ-C01 | 10 | PASS | 3 hàng |
| GQ-O01 | 10 | PASS | không tỷ giá |
| GQ-O02 | 10 | PASS | không CEO |
| GQ-D01 | 0 | PASS | refuse, không source |

Chi tiết answer/source: `docs/eval/results/RAG_FULL_GOLDEN_REGRESSION_21I_20260515.md` và `docs/eval/results/21i_raw_eval_output.txt`.

## 13. Delete verification

- `DELETE` → **200**; MySQL `deleted_at` set; Qdrant count **0**; GQ-D01 **PASS**.

## 14. Summary (recompute)

| Chỉ số | Số |
|--------|---:|
| total | 12 |
| PASS | **11** |
| PARTIAL | **1** |
| FAIL | **0** |

Issue categories: **SOURCE_NOISE** (nhiều case `sourceCount=10`); **GENERATION** (1 case T02 hedge); **DELETE/OOS/HALLUCINATION:** 0.

## 15. So sánh 21B

- **21B:** 7 PASS / 2 PARTIAL / 3 FAIL  
- **21I:** 11 PASS / 1 PARTIAL / 0 FAIL  
- **Improved:** F04, L01, L02, T01, F03; **T02:** FAIL→PARTIAL  
- **Regress:** không thấy trên các case 21B đã PASS.

## 16. Có sửa code không?

**Không.**

## 17. No code diff

```diff
# Không có thay đổi source trong task 21I (verify-only).
# Thêm/ghi nhận artifact:
#   docs/eval/results/RAG_FULL_GOLDEN_REGRESSION_21I_20260515.md
#   docs/eval/results/21i_raw_eval_output.txt
```

## 18. Kết luận baseline quality

- **PASS chức năng** (≥10 PASS, không FAIL P0 delete/OOS).  
- Còn **1 PARTIAL** (T02): diễn giải cột Hỗ trợ / từ “kênh”.

## 19. Rủi ro còn lại

- Full-window **10 sources** mọi câu → chi phí token/latency và nhiễu trích dẫn.  
- Automation PowerShell: nên `-Encoding UTF8` khi gửi `message` nếu cần verbatim log câu hỏi tiếng Việt.

## 20. Đề xuất bước tiếp theo

1. **Source cap / source presentation** (ưu tiên) — giảm noise khi chỉ 1 doc nhỏ.  
2. Tùy chọn: tinh chỉnh prompt/table semantics cho câu kiểu T02 (“kênh” = cột Hỗ trợ) — task riêng, không thuộc 21I.

---

*Báo cáo task 21I — baseline closure.*
