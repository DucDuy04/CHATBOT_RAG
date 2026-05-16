# RAG Golden Eval — Run 21B (thực tế)

**Ngày chạy:** 2026-05-14  
**Môi trường:** Windows, Docker Compose (backend `localhost:8080`, Qdrant `6333`, MySQL `ragchatbot`).  
**Env:** `GROQ_API_KEY` / `NOMIC_API_KEY` không set trên shell; có trong `.env` (Compose nạp cho container). Không ghi secret.

---

## 1. Chiến lược session

- **Một `sessionId` (UUID) mới cho mỗi câu** trong 11 câu trước delete — retrieval “lạnh”, không phụ thuộc lịch sử.  
- **GQ-D01:** `sessionId` mới sau `DELETE`.

---

## 2. Artifact

| Trường | Giá trị |
|--------|---------|
| **Chatbot name** | `Golden Eval 21B <HHmmss>` (tên động lúc tạo) |
| **CHATBOT_ID** | `a265b956-7864-408b-9b84-d75db80ac92f` |
| **WIDGET_API_KEY** | Masked: `a2955c72...28a6` |
| **DOCUMENT_ID** | `fcf9b169-4be8-413e-9feb-71c48e357539` |
| **File upload** | `docs/eval/RAG_GOLDEN_TEST_DOCUMENT.txt` |
| **Status sau upload** | `INDEXED` (`chunkCount`: 6) |
| **DELETE** | HTTP **200** |
| **Qdrant** (`document_id` + `widgetId`, sau delete) | **0** point |
| **MySQL** `documents.deleted_at` | **NOT NULL** (cột `del` = 1 trong query kiểm tra) |

---

## 3. Bảng kết quả từng case (trước delete: GQ-F01 … GQ-O02)

| case_id | A | B | C | D | verdict | tags | Ghi chú ngắn |
|---------|---|---|---|---|---------|------|---------------|
| GQ-F01 | y | y | y | y | **PASS** | — | Đúng “Công ty TNHH AlphaDemo”; có source file golden. |
| GQ-F02 | y | y | y | y | **PASS** | — | Đúng `GOLDEN-VN-2026-714`. |
| GQ-F03 | y | y | y | y | **PARTIAL** | GENERATION | Số **500** đúng; dòng “Nguồn/ Section” trong answer trỏ nhầm sang mục Bước 3 thay vì bảng giá. |
| GQ-F04 | n | n | y | n | **FAIL** | RETRIEVAL, NO_CONTEXT | Trả “không tìm thấy” dù tài liệu có Bước 2 rõ ràng; không có source. |
| GQ-L01 | n | y | n | n | **FAIL** | RETRIEVAL, GENERATION | Khẳng định không có “ba chính sách” trong khi doc có 3 mục đánh số; có source nhưng trả lời mâu thuẫn tài liệu. |
| GQ-L02 | n | n | y | n | **FAIL** | RETRIEVAL, TABLE_PARSE | “Không tìm thấy” cho tên gói; bảng trong TXT không được dùng đúng. |
| GQ-T01 | y | y | y | y | **PARTIAL** | GENERATION | Giá **99000** đúng; attribution section trong answer lệch (Bước 3). |
| GQ-T02 | n | n | y | n | **FAIL** | RETRIEVAL, TABLE_PARSE | “Không tìm thấy” thay vì “Ưu tiên”. |
| GQ-C01 | y | y | y | y | **PASS** | — | Đếm **3** gói đúng; attribution section vẫn lệch nhẹ nhưng fact chính đạt. |
| GQ-O01 | y | y | y | y | **PASS** | — | Không đưa tỷ giá; từ chối đúng hướng; có cite mục 7 (phạm vi OOS). |
| GQ-O02 | y | y | y | y | **PASS** | — | Không bịa CEO; từ chối đúng. |

---

## 4. GQ-D01 (sau delete)

| Tiêu chí | Quan sát |
|-----------|----------|
| Answer | “Tôi không tìm thấy thông tin này trong tài liệu.” |
| Sources | `sourceCount` = **0** |
| Verdict | **PASS** (không còn `GOLDEN-VN-2026-714` như fact phục vụ; không cite file golden) |

---

## 5. Tổng hợp (12 case)

| Chỉ số | Số |
|--------|---:|
| total | 12 |
| PASS | **7** |
| PARTIAL | **2** |
| FAIL | **3** |
| Cases có tag **RETRIEVAL** (miss context / “không tìm thấy” sai) | **4** (F04, L01, L02, T02) |
| generation issues (GENERATION / mâu thuẫn) | **3** (F03, T01, L01) |
| out-of-scope issues | **0** |
| table parse issues (TABLE_PARSE) | **2** (L02, T02) |

*Ghi chú:* một case có thể có nhiều tag; `TABLE_PARSE` gắn thêm khi nghi ngờ bảng TXT không được retrieve đúng.

---

## 6. Nhận xét & đề xuất sau baseline (không thực hiện trong 21B)

1. **Bảng markdown trong TXT:** GQ-L02, GQ-T02 fail — ưu tiên sau: table-aware / chunk bả ổn định hơn.  
2. **Fact quy trình (GQ-F04):** miss hoàn toàn — cần xem retrieval heading/section vs nội dung “Bước 2” (có thể chunk boundary hoặc query rewrite).  
3. **Attribution sai section dù số đúng (F03, T01):** nhẹ hơn — có thể cải thiện prompt yêu cầu trích đúng mục hoặc post-check (task khác).  
4. **Out-of-scope:** baseline ổn (O01, O02 PASS).

---

## 7. Answer summary (trích lưu ý — không log full 6 lần source)

- Nhiều câu trả `sourceCount` = 6 trùng tên file — có thể rút gọn khi báo cáo UI sau này; eval chỉ cần biết **có/không** file golden.

---

*File này là kết quả thực thi task 21B; không chỉnh sửa backend trong phiên chạy.*
