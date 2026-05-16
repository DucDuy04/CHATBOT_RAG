# RAG — QueryAnalyzer runtime verify sau 21G2 (21G3)

**Ngày:** 2026-05-14  
**Loại:** VERIFY ONLY — không sửa Java/prompt/retrieval/frontend.  
**Môi trường:** Windows 10, Docker Desktop, `docker compose up --build -d` từ repo `CHATBOT_RAG`.

---

## 1. Environment

| Mục | Giá trị |
|-----|---------|
| `docker compose config -q` | Exit **0** |
| `docker compose ps` | `chatbot-backend`, `ragchatbot-mysql`, `ragchatbot-qdrant`, `chatbot-frontend` — **Up** sau rebuild |
| Backend API | `http://localhost:8080` |
| Qdrant | `http://localhost:6333`, collection `documents` (theo stack hiện tại) |

**Env container `chatbot-backend` (boolean only):**

- `GROQ_API_KEY`: **present**
- `NOMIC_API_KEY`: **present**

**Health:** `GET /api/chatbots?page=0&size=1` → **200** (sau vài lần retry — backend khởi động sau recreate).

**Ghi chú vận hành:** log có **Groq TPM rate limit** (429) trên một số request; `LlmFallbackService` đợi và retry — không đổi code trong task này.

---

## 2. Build / compile / test

| Command | Kết quả |
|---------|---------|
| `docker compose up --build -d` | **PASS** (exit 0; backend image rebuild Maven trong Docker) |
| `Backend\.\mvnw.cmd -DskipTests compile` | **PASS** (đã chạy trong phiên trước verify 21G2; không đổi code trong 21G3) |
| `Backend\.\mvnw.cmd -q -Dtest=QueryAnalyzerServiceTest test` | **PASS** (trước đó trong workspace) |

---

## 3. Tenant / document runtime

**Không** tái dùng widget key đầy đủ từ 21E2 (chỉ có prefix trong tài liệu cũ). Tạo **chatbot eval mới** + upload golden.

| Field | Value |
|--------|--------|
| Chatbot name | `21G3 Runtime Verify B` |
| `CHATBOT_ID` / `widgetId` | `d353bb55-db3d-4a3b-bf03-b032d864bffd` |
| `WIDGET_API_KEY` | **chỉ lưu local** — báo cáo không ghi full key (prefix operator: `8c602421…`) |
| Golden file | `docs/eval/RAG_GOLDEN_TEST_DOCUMENT.txt` |
| Upload | `POST /api/documents/upload` — **HTTP 200**, `status=INDEXED`, `chunkCount=10` |

| `DOCUMENT_ID` | `5ee4d890-b127-4144-81e9-fcf59d310450` (xác nhận qua `GET /api/documents?chatbotId=…`) |

---

## 4. Query type từ log runtime

`docker logs chatbot-backend` có dòng:

`[RAG] Detected intent: question='Giá gói Basic là bao nhiêu VNĐ theo bảng?' queryType=TABLE_LOOKUP …`

và:

`[RAG] Final context chunks: 3 | queryType=TABLE_LOOKUP | lockedScope=sec_4`

→ **Runtime xác nhận** sau 21G2, T01 gốc **không còn** `COUNT_QUERY` cho câu này.

---

## 5. GQ-T01 original ×5 (session mới mỗi lần)

**Câu hỏi:** `Giá gói Basic là bao nhiêu VNĐ theo bảng?`

| Run | sourceCount | chunkText có Basic+99000? | Answer (tóm tắt) | Từ chối “không tìm thấy”? | Verdict |
|-----|-------------|---------------------------|-------------------|---------------------------|---------|
| 1 | 3 | Có | `99000 VNĐ` + nguồn mục 4 | Không | **PASS** |
| 2 | 3 | Có | `99.000 VNĐ` + nguồn mục 4 | Không | **PASS** (số đúng, format khác) |
| 3 | 3 | Có | `99.000 VNĐ` … | Không | **PASS** |
| 4 | 3 | Có | `99.000 VNĐ` … | Không | **PASS** |
| 5 | 3 | Có | `99.000 VNĐ` … | Không | **PASS** |

**So 21F:** 21F ghi T01 **1 PASS / 2 FAIL** với cùng 3 source nhưng LLM **từ chối**; lần verify này **0/5** từ chối trên T01 gốc → **cải thiện rõ** về mặt generation refusal (không claim 100% ổn định vĩnh viễn — chỉ mẫu 5 lần phiên này).

---

## 6. T01 variants (mỗi câu 1 lần)

| # | Câu hỏi | sourceCount | Verdict / ghi chú |
|---|---------|-------------|-------------------|
| 1 | Theo bảng gói dịch vụ, Basic có giá bao nhiêu? | 3 | **PASS** (markdown bảng + 99000) |
| 2 | Trong bảng, dòng Basic có giá là bao nhiêu? | 10 | **PASS** |
| 3 | Basic \| Giá là bao nhiêu? | 10 | **PASS** |
| 4 | Giá của gói Basic là 99000 hay 199000? | 10 | **FAIL** — answer **“Tôi không tìm thấy…”** dù source vẫn trỏ mục 4 bảng (**GENERATION_CONTRADICTION**) |

---

## 7. Regression

| Case | Expected (rút gọn) | sourceCount | Verdict |
|------|----------------------|---------------|---------|
| F03 | 500 lượt | 3 | **PASS** |
| L02 | Basic, Pro, Business | 3 | **PASS** (log: `queryType=TABLE_LOOKUP`; golden doc cho phép TABLE hoặc LIST) |
| T02 | Ưu tiên (Business) | 10 | **PASS** (bảng + hàng Business) |
| C01 | 3 gói / 3 hàng | 3 | **PASS** — **không regress** count |
| O01 | Không bịa tỷ giá | 10 | **PASS** (log final `queryType=COUNT_QUERY`) |
| O02 | Không bịa CEO | 10 | **PASS** (log: `queryType=LIST_ALL` — intent không tối ưu nhưng answer vẫn từ chối đúng hướng) |

---

## 8. Source noise

| Case | sourceCount | Ghi chú |
|------|-------------|---------|
| T01 gốc | 3 | Giống pattern 21F (pool nhỏ) — **OK** |
| T01 v2/v3, T02, O01/O02 | 10 | Full-window như 21E2 — **SOURCE_NOISE** (presentation) nếu chỉ xét số lượng; answer vẫn đúng hướng |

---

## 9. Before / after (tóm tắt)

| Nguồn | T01 hint / symptom |
|--------|---------------------|
| 21F | `COUNT_QUERY` + LLM đôi khi deny dù có evidence |
| 21G/21G2 (unit) | `TABLE_LOOKUP` cho T01-like |
| **21G3 runtime** | Log **TABLE_LOOKUP** + T01 gốc **5/5** không deny; v4 variant vẫn có **1** case deny |

---

## 10. Kết luận

**A. QueryAnalyzer 21G2 có giúp T01 runtime không?**  
**Có** — log `queryType=TABLE_LOOKUP` cho T01 gốc; **5/5** không còn pattern “không tìm thấy” như 21F trên cùng câu.

**B. Count (C01) regress?** **Không** trong phiên này.

**C. OOS (O01/O02) regress?** **Không** — không bịa số tỷ giá / không bịa CEO.

**D. Còn cần PromptBuilder 21H không?**  
**Có thể cần** — biến thể **T01 v4** vẫn **FAIL** (contradiction dù context có bảng). Gợi ý: table guard / instruction khi source chứa `table_*` + entity match.

**Verdict tổng task 21G3:** **PARTIAL–PASS** — T01 gốc ổn định hơn 21F rõ rệt; vẫn còn edge generation.

---

*Không chứa API key đầy đủ.*
