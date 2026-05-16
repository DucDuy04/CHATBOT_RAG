# Golden questions — RAG quality baseline (CHATBOT_RAG)

**Phiên bản:** 1.0 (task 21A)  
**Tài liệu nguồn:** `RAG_GOLDEN_TEST_DOCUMENT.md` (upload dưới tên `.txt` — xem runbook)  
**Số case tối thiểu:** 12 (đúng phân bổ yêu cầu)

---

## Tiêu chí đánh giá (4 trục → PASS / PARTIAL / FAIL)

Với **mỗi** câu hỏi, đánh giá:

| # | Tiêu chí | Ý nghĩa |
|---|----------|---------|
| A | **Answer correctness** | Nội dung trả lời có khớp *expected answer* (cho phép diễn đạt khác nếu không đổi nghĩa số/fact). |
| B | **Source correctness** | `sources` có chứa file tài liệu golden (tên file sau khi upload); với OUT_OF_SCOPE/DEL sau xóa: **không** được trỏ nhầm sang golden khi đã ngoài phạm vi. |
| C | **Faithfulness** | Không bịa số liệu/tên/cơ chế không có trong tài liệu (hoặc không bịa khi đáp án đúng là “không có”). |
| D | **Completeness** | Đủ ý chính (với LIST: đủ mục; với COUNT: đúng số; OUT_OF_SCOPE: từ chối rõ). |

**Kết quả gộp một case:**

| Kết quả | Điều kiện gợi ý |
|---------|------------------|
| **PASS** | A+B+C đạt; D đạt hoặc chỉ thiếu chi tiết không ảnh hưởng fact chính. |
| **PARTIAL** | A gần đúng nhưng thiếu/sai nhẹ **hoặc** source đúng file nhưng chunk không lý tưởng **hoặc** LIST thiếu 1 mục. |
| **FAIL** | A sai **hoặc** B sai (sai file / thiếu source khi bắt buộc có / còn source sau delete) **hoặc** C hallucinate **hoặc** D thiếu hụt nghiêm trọng. |

**Gắn nhãn lỗi khi fail/partial (chọn một hoặc nhiều):**

- `RETRIEVAL` — không lôi đúng chunk / thiếu context.  
- `GENERATION` — LLM diễn giải sai dù context đủ.  
- `NO_CONTEXT` — đúng ra có trong doc nhưng hệ thống báo không có.  
- `HALLUCINATION` — thêm fact không có trong doc.  
- `OUT_OF_SCOPE_HANDLING` — không từ chối đúng khi hỏi ngoài tài liệu.  
- `SOURCE_MISSING` — đúng câu nhưng không có source khi cần có.  
- `TABLE_PARSE` — bảng markdown không được parse/chunk đủ để trả lời (ghi nhận cho tương lai table-aware).

---

## Bảng tổng hợp (điền sau khi chạy eval)

| Chỉ số | Giá trị |
|--------|---------|
| total cases | 12 |
| PASS | _(điền)_ |
| PARTIAL | _(điền)_ |
| FAIL | _(điền)_ |
| retrieval issues (số case có nhãn RETRIEVAL/TABLE_PARSE) | _(điền)_ |
| generation issues (GENERATION/HALLUCINATION) | _(điền)_ |
| out-of-scope issues (OUT_OF_SCOPE_HANDLING) | _(điền)_ |

---

## Danh sách test case

### GQ-F01 — FACT

| Trường | Nội dung |
|--------|----------|
| **ID** | GQ-F01 |
| **Câu hỏi** | Tên pháp lý đầy đủ của công ty trong tài liệu là gì? |
| **Loại** | FACT |
| **Query type dự kiến (code)** | `NORMAL_FACT` |
| **Expected answer** | Công ty TNHH AlphaDemo (cho phép thêm cụm “theo tài liệu” nhưng **không** đổi tên). |
| **Expected source** | File golden đã upload (ví dụ `RAG_GOLDEN_TEST_DOCUMENT.txt`). |
| **Pass criteria** | A đúng tên; B có source đúng file; C không thêm địa chỉ/MSDN giả; D đủ tên pháp lý. |
| **Lỗi cần đánh dấu nếu fail** | RETRIEVAL, SOURCE_MISSING, HALLUCINATION |

---

### GQ-F02 — FACT

| Trường | Nội dung |
|--------|----------|
| **ID** | GQ-F02 |
| **Câu hỏi** | Mã xác nhận baseline (golden) trong tài liệu là gì? |
| **Loại** | FACT |
| **Query type dự kiến** | `NORMAL_FACT` |
| **Expected answer** | `GOLDEN-VN-2026-714` (đúng chuỗi). |
| **Expected source** | File golden. |
| **Pass criteria** | Chuỗi khớp tuyệt đối trong answer; có source file golden. |
| **Lỗi nếu fail** | RETRIEVAL, GENERATION, HALLUCINATION |

---

### GQ-F03 — FACT

| Trường | Nội dung |
|--------|----------|
| **ID** | GQ-F03 |
| **Câu hỏi** | Gói Pro có bao nhiêu lượt hỏi AI mỗi tháng theo bảng giá? |
| **Loại** | FACT |
| **Query type dự kiến** | `TABLE_LOOKUP` hoặc `NORMAL_FACT` |
| **Expected answer** | 500 lượt/tháng (hoặc tương đương “500”). |
| **Expected source** | File golden (phần bảng). |
| **Pass criteria** | Số **500** đúng; không đổi sang 100/2000; có source golden. |
| **Lỗi nếu fail** | RETRIEVAL, TABLE_PARSE, HALLUCINATION |

---

### GQ-F04 — FACT

| Trường | Nội dung |
|--------|----------|
| **ID** | GQ-F04 |
| **Câu hỏi** | Ở quy trình xử lý yêu cầu, bước 2 làm gì? |
| **Loại** | FACT |
| **Query type dự kiến** | `NORMAL_FACT` / `SECTION_SUMMARY` |
| **Expected answer** | Phân loại: gán nhãn `billing`, `technical` hoặc `other` trong vòng 4 giờ làm việc (đủ 3 nhãn + thời hạn). |
| **Expected source** | File golden. |
| **Pass criteria** | Đúng ý Bước 2; không nhầm với Bước 1 hoặc 3. |
| **Lỗi nếu fail** | RETRIEVAL, GENERATION |

---

### GQ-L01 — LIST

| Trường | Nội dung |
|--------|----------|
| **ID** | GQ-L01 |
| **Câu hỏi** | Liệt kê đầy đủ ba chính sách hỗ trợ được nêu trong mục chính sách. |
| **Loại** | LIST |
| **Query type dự kiến** | `LIST_ALL` |
| **Expected answer** | Đủ 3 mục: (1) 24 giờ làm việc phản hồi email; (2) tối đa 15 phút/phiên chat; (3) không hỗ trợ bên thứ ba không có ủy quyền. |
| **Expected source** | File golden. |
| **Pass criteria** | Đủ 3 ý không đổi nghĩa; có source. |
| **Lỗi nếu fail** | RETRIEVAL, GENERATION (thiếu mục → PARTIAL/FAIL) |

---

### GQ-L02 — LIST

| Trường | Nội dung |
|--------|----------|
| **ID** | GQ-L02 |
| **Câu hỏi** | Trong bảng gói dịch vụ, có những tên gói nào? |
| **Loại** | LIST |
| **Query type dự kiến** | `LIST_ALL` / `TABLE_LOOKUP` |
| **Expected answer** | Basic, Pro, Business (đủ 3 tên). |
| **Expected source** | File golden. |
| **Pass criteria** | Đủ 3 tên gói; không thêm gói khác. |
| **Lỗi nếu fail** | TABLE_PARSE, RETRIEVAL |

---

### GQ-T01 — TABLE_LOOKUP

| Trường | Nội dung |
|--------|----------|
| **ID** | GQ-T01 |
| **Câu hỏi** | Giá gói Basic là bao nhiêu VNĐ theo bảng? |
| **Loại** | TABLE_LOOKUP |
| **Query type dự kiến** | `TABLE_LOOKUP` |
| **Expected answer** | 99000 (VNĐ). |
| **Expected source** | File golden. |
| **Pass criteria** | Đúng 99000; không nhầm với Pro/Business. |
| **Lỗi nếu fail** | TABLE_PARSE, RETRIEVAL, HALLUCINATION |

---

### GQ-T02 — TABLE_LOOKUP

| Trường | Nội dung |
|--------|----------|
| **ID** | GQ-T02 |
| **Câu hỏi** | Gói Business được hỗ trợ theo kênh nào trong bảng? |
| **Loại** | TABLE_LOOKUP |
| **Query type dự kiến** | `TABLE_LOOKUP` |
| **Expected answer** | Ưu tiên (đúng từ cột Hỗ trợ). |
| **Expected source** | File golden. |
| **Pass criteria** | Khớp từ “Ưu tiên”; không đổi thành “Email” thuần. |
| **Lỗi nếu fail** | TABLE_PARSE, RETRIEVAL |

---

### GQ-C01 — COUNT

| Trường | Nội dung |
|--------|----------|
| **ID** | GQ-C01 |
| **Câu hỏi** | Bảng gói dịch vụ có tất cả bao nhiêu hàng gói (số gói)? |
| **Loại** | COUNT |
| **Query type dự kiến** | `COUNT_QUERY` |
| **Expected answer** | 3 gói. |
| **Expected source** | File golden. |
| **Pass criteria** | Trả lời đếm đúng **3**; không đếm dòng header. |
| **Lỗi nếu fail** | RETRIEVAL, GENERATION, TABLE_PARSE |

---

### GQ-O01 — OUT_OF_SCOPE

| Trường | Nội dung |
|--------|----------|
| **ID** | GQ-O01 |
| **Câu hỏi** | Tỷ giá USD/VND hôm nay là bao nhiêu? |
| **Loại** | OUT_OF_SCOPE |
| **Query type dự kiến** | (ngoài enum — không áp dụng) |
| **Expected answer** | Không có trong tài liệu / không tìm thấy (theo `PromptBuilderService`: một câu từ chối chuẩn hoặc tương đương). **Không** đưa con số tỷ giá. |
| **Expected source** | Không bắt buộc có source golden; **không** được cite bảng giá làm tỷ giá. |
| **Pass criteria** | C không hallucinate số; A từ chối đúng hướng. |
| **Lỗi nếu fail** | HALLUCINATION, OUT_OF_SCOPE_HANDLING |

---

### GQ-O02 — OUT_OF_SCOPE

| Trường | Nội dung |
|--------|----------|
| **ID** | GQ-O02 |
| **Câu hỏi** | CEO của AlphaDemo tên đầy đủ là gì? |
| **Loại** | OUT_OF_SCOPE |
| **Query type dự kiến** | — |
| **Expected answer** | Tài liệu không nêu CEO / không có thông tin (không bịa tên). |
| **Expected source** | Không cite fact giả. |
| **Pass criteria** | Không đưa tên người cụ thể; có thể kèm “không có trong tài liệu”. |
| **Lỗi nếu fail** | HALLUCINATION, OUT_OF_SCOPE_HANDLING |

---

### GQ-D01 — DELETE_VERIFICATION

| Trường | Nội dung |
|--------|----------|
| **ID** | GQ-D01 |
| **Câu hỏi** | *(Sau khi đã `DELETE` document golden khỏi hệ thống — dùng session mới hoặc session cũ theo runbook E2E)* Mã xác nhận baseline (golden) trong tài liệu là gì? |
| **Loại** | DELETE_VERIFICATION |
| **Query type dự kiến** | — |
| **Expected answer** | Không trả `GOLDEN-VN-2026-714` như một fact từ tài liệu đang được phục vụ **hoặc** trả lời kiểu không tìm thấy trong tài liệu. |
| **Expected source** | **Không** có `sources[].fileName` trùng file golden đã xóa. |
| **Pass criteria** | B: không source từ doc đã xóa; C: không “nhớ” mã như doc hiện hành (trừ khi trùng tình cờ từ doc khác — trong eval baseline chỉ có một doc golden). |
| **Lỗi nếu fail** | RETRIEVAL (vector chưa purge), SOURCE_MISSING (nếu vẫn cite golden), HALLUCINATION |

---

## Ghi chú phân loại tương lai

- Các câu **TABLE_LOOKUP** / **COUNT** trên bảng markdown trong `.txt`: nếu fail nhiều, ghi nhận ưu tiên sau này là **table-aware** (ngoài scope 21A).  
- Câu **LIST** dài: nếu partial, xem `RagRetrievalService` giới hạn context (`MAX_CONTEXT_*`) — không chỉnh trong task này.
