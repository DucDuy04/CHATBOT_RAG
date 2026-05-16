# RAG — Full golden regression 21I (official result)

**File:** `docs/eval/results/RAG_FULL_GOLDEN_REGRESSION_21I_20260515.md`  
**Task report:** `reports/refactor/CURSOR_REPORT_21I_FULL_GOLDEN_REGRESSION.md`

**Ngày chạy:** 2026-05-15  
**Môi trường:** Windows 10, Docker Desktop, repo `CHATBOT_RAG`  
**Phạm vi:** VERIFY ONLY — không sửa Java/prompt/retrieval/frontend/Compose/schema.

---

## 1. Environment

| Bước | Kết quả |
|------|---------|
| `docker compose config -q` | **PASS** (exit 0) |
| `docker compose up --build -d` | **PASS** — backend image rebuild Maven trong Docker |
| `docker compose ps` | `chatbot-backend`, `ragchatbot-mysql`, `ragchatbot-qdrant`, `chatbot-frontend` — **Up** |
| Backend API | `http://localhost:8080` |
| Qdrant | `http://localhost:6333`, collection `documents` |
| Health `GET /api/chatbots?page=0&size=1` | **200** |

**Env container `chatbot-backend` (boolean only):**

- `GROQ_API_KEY`: **present**
- `NOMIC_API_KEY`: **present**

Không ghi full key trong báo cáo.

---

## 2. Build / compile / test (local workspace)

| Command | Kết quả |
|---------|---------|
| `Backend\.\mvnw.cmd -DskipTests compile` | **PASS** |
| `Backend\.\mvnw.cmd -Dtest=QueryAnalyzerServiceTest test` | **PASS** |
| `Backend\.\mvnw.cmd -Dtest=PromptBuilderServiceTest test` | **PASS** |

---

## 3. Tenant / document (eval run)

| Field | Value |
|--------|--------|
| Chatbot name | `21I Full Golden Regression` |
| `CHATBOT_ID` | `6dfabf0f-8aab-467c-9597-5889ee0ba963` |
| `WIDGET_API_KEY` | Masked: `48c7be23...cb8b` (full key chỉ trên máy chạy eval) |
| Golden file | `docs/eval/RAG_GOLDEN_TEST_DOCUMENT.txt` |
| `DOCUMENT_ID` | `8e0e17bc-148d-4cbe-bd22-a3363d0c02d8` |
| Upload response | `INDEXED`, `chunkCount=10` |
| `GET …/status` | `INDEXED`, `progress=100` |

**Chiến lược session:** UUID **mới cho mỗi** câu (11 câu trước delete); **session mới** cho GQ-D01 sau delete (theo runbook).

---

## 4. Qdrant (filter `document_id` + `widgetId`)

| Thời điểm | `points/count` (exact) |
|------------|-------------------------|
| Sau index | **10** |
| Sau `DELETE /api/documents/{id}` | **0** |

---

## 5. Metadata sanity (MySQL)

**Sections** (`document_sections`, `UUID_TO_BIN(document_id)`), thứ tự `order_index`:

- General  
- 1. Thông tin chung  
- 2. Chính sách hỗ trợ (áp dụng cho khách hàng dùng thử)  
- 3. Quy trình xử lý yêu cầu (3 bước cố định)  
- 4. Bảng gói dịch vụ (giá VNĐ, lượt hỏi AI/tháng)  
- 5. Danh sách giới hạn (để tránh nhầm với thông tin khác)  
- 6. Thông tin dễ gây nhầm (chỉ đọc đúng câu chữ)  
- 7. Phạm vi KHÔNG có trong tài liệu (dùng cho câu hỏi out-of-scope)  

→ Khớp checklist G1 (tên có thể hiển thị lệch encoding trong log raw, nội dung DB đúng UTF-8).

**Chunk type counts:**

| chunk_type | cnt |
|------------|-----|
| text | 6 |
| text_table_like | 2 |
| table_summary | 1 |
| table_row_group | 1 |

**Table chunks → section 4:**

- `table_summary` → mục 4  
- `table_row_group` → mục 4  
- `text_table_like` → General và mục 7 (đúng golden: phần đầu + OOS block)

**Ghi chú `MYSQL_STEP2_SAMPLE`:** truy vấn `LIKE '%Bước 2%'` trong script automation trả **0 dòng** (khả năng literal SQL / collation khi chạy từ Windows); **không** dùng làm FAIL G1 vì **GQ-F04 PASS** runtime chứng minh nội dung bước 2 có trong retrieval.

**Sau delete:** `documents.deleted_at IS NOT NULL` cho đúng `document_id` → **1** (có dòng đã xóa).

---

## 6. Bảng 12 case (tóm tắt verdict)

| case_id | sourceCount | Verdict | Tags / ghi chú |
|---------|-------------|---------|----------------|
| GQ-F01 | 10 | **PASS** | SOURCE_NOISE (full-window 10 chunk) |
| GQ-F02 | 1 | **PASS** | — |
| GQ-F03 | 10 | **PASS** | So 21B: attribution đúng mục 4 bảng |
| GQ-F04 | 10 | **PASS** | So 21B: **improved** (trước RETRIEVAL miss) |
| GQ-L01 | 10 | **PASS** | So 21B: **improved** (đủ 3 chính sách) |
| GQ-L02 | 10 | **PASS** | So 21B: **improved** (Basic, Pro, Business) |
| GQ-T01 | 10 | **PASS** | So 21B: **improved**; bảng có Basic 99000 |
| GQ-T02 | 10 | **PARTIAL** | GENERATION — có “ưu tiên” nhưng thêm câu sai lệch “không có thông tin kênh cụ thể” dù ô Hỗ trợ = Ưu tiên |
| GQ-C01 | 10 | **PASS** | Đếm 3 hàng gói |
| GQ-O01 | 10 | **PASS** | Không bịa tỷ giá; SOURCE_NOISE |
| GQ-O02 | 10 | **PASS** | Không bịa CEO; SOURCE_NOISE (sources không lý tưởng cho OOS) |
| GQ-D01 | 0 | **PASS** | Sau delete: refuse + không source |

**Answer summary (rút gọn):**

- **F01:** Công ty TNHH AlphaDemo.  
- **F02:** `GOLDEN-VN-2026-714`.  
- **F03:** 500 lượt/tháng + bảng markdown.  
- **F04:** Bước 2: gán nhãn `billing` / `technical` / `other` trong 4 giờ làm việc.  
- **L01:** Đủ 3 bullet chính sách (24h email, 15 phút chat, không bên thứ ba không ủy quyền).  
- **L02:** Basic, Pro, Business.  
- **T01:** Bảng markdown; Basic **99000**.  
- **T02:** Kết luận lẫn lộn; vẫn nhắc Business **ưu tiên**.  
- **C01:** **3** hàng gói.  
- **O01:** Không có tỷ giá trong tài liệu.  
- **O02:** Không tìm thấy CEO.  
- **D01:** Không tìm thấy; `sourceCount=0`.

---

## 7. Delete verification

| Tiêu chí | Quan sát |
|-----------|----------|
| `DELETE /api/documents/{id}` | **200**, `success: true` |
| Qdrant count (document+widget) | **0** |
| GQ-D01 | Không leak mã golden; không source |

---

## 8. Tổng hợp số liệu (recompute từ bảng case)

| Chỉ số | Giá trị |
|--------|--------:|
| total | 12 |
| **PASS** | **11** |
| **PARTIAL** | **1** |
| **FAIL** | **0** |
| Cases có tag SOURCE_NOISE (sourceCount=10, không sai fact) | 8 (F01,F03,F04,L01,L02,T01,T02,C01,O01,O02 — đếm case: F01,F03,F04,L01,L02,T01,T02,C01,O01,O02 = 10 có 10 sources; F02=1; D01=0) |
| GENERATION (T02 hedge sai) | 1 |
| RETRIEVAL fail (như 21B F04/L01/L02/T02) | **0** trong run này |
| HALLUCINATION / OOS bịa | **0** |
| DELETE issue | **0** |

---

## 9. So sánh 21B → 21I

**21B (recomputed):** PASS **7** / PARTIAL **2** / FAIL **3**

**21I (run này):** PASS **11** / PARTIAL **1** / FAIL **0**

| Case | 21B | 21I |
|------|-----|-----|
| GQ-F04 | FAIL | **PASS** |
| GQ-L01 | FAIL | **PASS** |
| GQ-L02 | FAIL | **PASS** |
| GQ-T02 | FAIL | **PARTIAL** |
| GQ-F03 | PARTIAL | **PASS** |
| GQ-T01 | PARTIAL | **PASS** |
| Khác | PASS | PASS (không regress) |

**Không có regression** so với 21B trên các case 21B đã PASS.

---

## 10. Source noise

- Hầu hết câu in-scope: `sourceCount=10` (full-window một doc 10 chunk) — đúng pattern đã thấy ở 21G3; trả lời vẫn đúng hướng.
- GQ-F02: `sourceCount=1` (hẹp).

---

## 11. Kết luận baseline & bước tiếp theo

**A. Baseline quality:** **PASS chức năng** theo tiêu chí brief (≥10 PASS, không FAIL P0 delete/OOS). **11/12 PASS**, **1 PARTIAL** (T02 diễn giải cột “Hỗ trợ” / từ “kênh”).

**B. Bước tiếp theo (ưu tiên):**

1. **Source cap / source presentation** — giảm 10 source mặc định khi 1 doc nhỏ; cải thiện trải nghiệm và giảm nhiễu cho OOS.  
2. **T02 nhẹ (optional):** có thể prompt nhỏ hoặc hướng dẫn “cột Hỗ trợ = kênh/ưu tiên” — **ngoài scope task 21I** (verify only).

---

## 12. Artifact / reproducibility

- Log máy: `docs/eval/results/21i_raw_eval_output.txt` (dòng `CHAT_JSON=` + meta).

---

*Không chứa API key đầy đủ.*
