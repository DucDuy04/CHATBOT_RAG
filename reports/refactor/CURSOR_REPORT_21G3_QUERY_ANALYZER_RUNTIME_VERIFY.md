# CURSOR_REPORT_21G3 — QueryAnalyzer runtime verify sau 21G2

## 1. Mức độ hiểu task

- **~98%** — verify-only: rebuild Docker, tenant mới, chat targeted, ghi evidence, không sửa code.

## 2. Tóm tắt yêu cầu

Xác nhận runtime sau refactor **21G2** (QueryAnalyzer tổng quát, không hardcode entity): T01 ×5, variants, regression F03/L02/T02/C01/O01/O02; so sánh với 21F; kết luận có cần **PromptBuilder 21H** không.

## 3. Phạm vi đã làm

- `docker compose up --build -d`, `docker compose config -q`, `docker compose ps`.
- Health `GET /api/chatbots`, env presence trong container (`GROQ_*`, `NOMIC_*` boolean).
- Tạo chatbot mới + upload `RAG_GOLDEN_TEST_DOCUMENT.txt` → `INDEXED`, `chunkCount=10`.
- `POST /api/chat` các case theo brief; đọc `docker logs` cho `[RAG] Detected intent` / `queryType`.
- Viết `docs/eval/results/RAG_QUERY_ANALYZER_RUNTIME_VERIFY_21G3_20260514.md` và báo cáo này + `docs/CURSOR_REPORT_21G3_QUERY_ANALYZER_RUNTIME_VERIFY.md`.

## 4. Phạm vi không làm

- Không sửa Java, PromptBuilder, RagRetrievalService, ChatService, frontend, Docker compose/schema, dependency, `.gitignore`.

## 5. File đã đọc

| Path | Mục đích |
|------|----------|
| `docs/eval/results/RAG_QUERY_ANALYZER_GENERALIZED_21G2_20260514.md` | Bối cảnh 21G2 |
| `docs/eval/results/FIX_LOOP_21G2_QUERY_ANALYZER_GENERALIZED.md` | Fix loop |
| `reports/refactor/CURSOR_REPORT_21G2_QUERY_ANALYZER_GENERALIZED_FIX.md` | Report 21G2 |
| `docs/eval/results/RAG_QUERY_ANALYZER_FIX_21G_20260514.md` | 21G |
| `reports/refactor/CURSOR_REPORT_21G_QUERY_ANALYZER_TABLE_LOOKUP_FIX.md` | 21G report |
| `docs/eval/results/RAG_T01_GENERATION_SOURCE_DIAG_21F_20260514.md` | 21F symptom |
| `reports/refactor/CURSOR_REPORT_21F_T01_GENERATION_SOURCE_DIAGNOSIS.md` | 21F tóm tắt |
| `docs/eval/results/RAG_RUNTIME_VERIFY_21E2_SECTION7_20260514.md` | Tenant cũ (chỉ tham chiếu ID/prefix) |
| `docs/eval/RAG_GOLDEN_QUESTIONS.md` | Text case |
| `docker-compose.yml` | Stack |

## 6. Môi trường chạy

- Windows 10, Docker Desktop, repo `CHATBOT_RAG`, ngày **2026-05-14**.

## 7. Env check

- `GROQ_API_KEY`: **present** (container).
- `NOMIC_API_KEY`: **present** (container).
- Không ghi full key trong báo cáo.

## 8. Build/restart result

| Bước | Kết quả |
|------|---------|
| `docker compose up --build -d` | **PASS** (exit 0) |

## 9. Compile/test result

| Command | Kết quả | Ghi chú |
|---------|---------|---------|
| `mvnw -DskipTests compile` | **PASS** | Không đổi code trong 21G3; có thể chạy lại để xác nhận workspace |
| `mvnw -Dtest=QueryAnalyzerServiceTest test` | **PASS** | Đã PASS trong workspace trước verify |

## 10. Runtime target document/chatbot

| Field | Value |
|--------|--------|
| `CHATBOT_ID` | `d353bb55-db3d-4a3b-bf03-b032d864bffd` |
| `DOCUMENT_ID` | `5ee4d890-b127-4144-81e9-fcf59d310450` |
| Widget key | Chỉ prefix `8c602421…` (full key tạo khi `POST /api/chatbots`, không đưa vào report) |

## 11. T01 original ×5

- **5/5** answer chứa giá đúng (`99000` hoặc `99.000` VNĐ), **0/5** “không tìm thấy”.
- **sourceCount:** 3 mỗi lần; ít nhất một `chunkText` có Basic + 99000 (theo response API).

## 12. T01 variants

- v1–v3: **PASS**.
- v4 (`99000 hay 199000`): **FAIL** — deny string dù có source mục 4 (**GENERATION_CONTRADICTION**).

## 13. Regression cases

| Case | Kết quả | sourceCount (mẫu) |
|------|---------|-------------------|
| F03 | **PASS** | 3 |
| L02 | **PASS** | 3 |
| T02 | **PASS** | 10 |
| C01 | **PASS** | 3 |
| O01 | **PASS** | 10 |
| O02 | **PASS** | 10 |

## 14. Source noise summary

- T01 gốc: **3** sources (giống 21F pattern pool nhỏ).
- O01/O02, một số variant: **10** sources (full-window tenant 1 doc / 10 chunk) — **SOURCE_NOISE** theo định nghĩa brief nếu chỉ nhìn số lượng; answer O01/O02 vẫn đúng hướng.

## 15. Có sửa code không?

**Không.**

## 16. No code diff

```diff
# Không có thay đổi source trong task 21G3 (verify-only).
# Thêm tài liệu kết quả:
#   docs/eval/results/RAG_QUERY_ANALYZER_RUNTIME_VERIFY_21G3_20260514.md
#   reports/refactor/CURSOR_REPORT_21G3_QUERY_ANALYZER_RUNTIME_VERIFY.md
#   docs/CURSOR_REPORT_21G3_QUERY_ANALYZER_RUNTIME_VERIFY.md
```

## 17. Kết luận

- **T01 ổn định (mẫu 5 lần):** **Cải thiện rõ** so 21F về từ chối sai; giá vẫn đúng.
- **Count C01:** **Không regress.**
- **O01/O02:** **Không regress** (không bịa).
- **PromptBuilder 21H:** **Nên có** nếu muốn triệt edge **T01 v4** (deny dù có bảng) — ngoài scope QueryAnalyzer.

## 18. Rủi ro còn lại

- Groq **TPM rate limit** trong log — có thể làm chậm / đổi timing LLM.
- O02 bị classify `LIST_ALL` trong log — không phá answer nhưng không “sạch” intent.

## 19. Đề xuất prompt tiếp theo

- **21H:** guard khi `chunk_type` là `table_*` và entity/số khớp câu hỏi → không dùng câu từ chối #2; hoặc instruction bổ sung cho câu dạng so sánh hai số trong bảng.

---

*Bản rule 90: `docs/CURSOR_REPORT_21G3_QUERY_ANALYZER_RUNTIME_VERIFY.md` (nội dung tương đương file này).*
