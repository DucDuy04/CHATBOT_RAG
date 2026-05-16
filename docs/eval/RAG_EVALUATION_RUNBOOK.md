# Runbook — Đánh giá chất lượng RAG golden baseline (nhẹ)

**Phiên bản:** 1.0 (task 21A)  
**Phụ thuộc:** Core E2E đã pass (xem `reports/refactor/CURSOR_REPORT_20G_CORE_RAG_E2E_EXECUTION.md`).  
**Nguyên tắc:** ít câu, ít file, không benchmark nặng, không tự động hóa bắt buộc — có thể làm thủ công qua UI hoặc `POST /api/chat`.

---

## 1. Chuẩn bị

1. Stack backend chạy (Docker hoặc dev), có `GROQ_API_KEY` và `NOMIC_API_KEY` hợp lệ (xem `docs/RAG_CORE_FLOW_E2E_RUNBOOK.md`).  
2. Một **chatbot** (tenant) sạch cho eval **hoặc** chatbot chỉ chứa đúng tài liệu golden — khuyến nghị **tenant riêng** để tránh nhiễu retrieval.  
3. File nguồn: `docs/eval/RAG_GOLDEN_TEST_DOCUMENT.md`.  
4. Danh sách câu hỏi + tiêu chí: `docs/eval/RAG_GOLDEN_QUESTIONS.md`.

### 1.1 Lưu ý bắt buộc — định dạng upload

**SOURCE:** `DocumentService` chỉ chấp nhận tệp kết thúc `.pdf` hoặc `.txt` (không chấp nhận `.md`).

**Cách làm:**

- Sao chép nội dung `RAG_GOLDEN_TEST_DOCUMENT.md` sang `RAG_GOLDEN_TEST_DOCUMENT.txt`, **hoặc**  
- Đổi tên bản sao thành `RAG_GOLDEN_TEST_DOCUMENT.txt` trước khi upload.

Giữ nguyên nội dung (bảng markdown nằm trong plain text `.txt` là hợp lệ).

---

## 2. Upload tài liệu golden

1. `POST /api/documents/upload` — multipart:  
   - `files=@<đường_dẫn>/RAG_GOLDEN_TEST_DOCUMENT.txt`  
   - `chatbotId=<UUID chatbot>`  
2. Đợi `GET /api/documents/{id}/status` → `INDEXED`.  
3. Ghi lại: `DOCUMENT_ID`, tên file hiển thị sau upload (dùng cho cột “expected source”).

**Windows PowerShell:** tránh `curl.exe -d "{json}"` inline; với JSON dùng `Invoke-RestMethod` hoặc `--data-binary @file` (xem report 20G).

---

## 3. Gọi chat (`/api/chat`)

**Endpoint:** `POST http://localhost:8080/api/chat`

**Header:**

- `Content-Type: application/json`  
- `X-Widget-Key: <apiKey>` (UUID từ lúc tạo chatbot — **không** dùng nhầm `chatbotId`)

**Body:**

```json
{
  "sessionId": "<uuid-mới-mỗi-phiên-hoặc-giữ-cố-định-theo-chiến-lược>",
  "message": "<câu hỏi từ RAG_GOLDEN_QUESTIONS.md>"
}
```

**Gợi ý session:**

- Trước delete: có thể dùng một `sessionId` cố định cho cả 11 câu (trừ GQ-D01) để giảm chi phối từ lịch sử ngắn — hoặc **session mới mỗi câu** để test retrieval “lạnh”. Ghi rõ trong bảng kết quả bạn chọn chiến lược nào.  
- GQ-D01: nên **session mới** sau `DELETE` (giống khuyến nghị E2E 20G).

---

## 4. Ghi kết quả mỗi câu

Tạo bảng (CSV hoặc markdown) với các cột tối thiểu:

| Cột | Mô tả |
|-----|--------|
| `case_id` | GQ-F01 … GQ-D01 |
| `question` | Copy nguyên văn |
| `answer_raw` | Full `answer` từ API (có thể truncate trong báo cáo tóm tắt nhưng giữ bản đầy đủ local) |
| `sources_json` | Snapshot `sources` (fileName, chunkType, …) |
| `A_correct` | yes/no/partial |
| `B_source` | yes/no/partial |
| `C_faithful` | yes/no/partial |
| `D_complete` | yes/no/partial |
| `verdict` | PASS / PARTIAL / FAIL |
| `tags` | RETRIEVAL, … (nếu có) |
| `notes` | Quan sát người chạy |

---

## 5. Đánh giá answer (trục A, D)

1. Đối chiếu với **Expected answer** trong `RAG_GOLDEN_QUESTIONS.md`.  
2. Cho phép diễn đạt khác nếu **số và fact** không đổi (ví dụ “99000 đồng” vs “99.000 VNĐ”).  
3. **OUT_OF_SCOPE:** PASS nếu **không** đưa con số/tên bịa; câu từ chối đúng hướng.  
4. **LIST:** PARTIAL nếu thiếu 1 trong các mục bắt buộc; FAIL nếu sai lệch hoặc thiếu quá nửa.

---

## 6. Đánh giá sources (trục B)

1. Với câu **in-scope** (FACT/LIST/TABLE/COUNT): mong đợi ít nhất một source có `fileName` khớp file golden đã upload.  
2. Nếu answer đúng nhưng **không có source**: đánh `B_source=no` → thường **PARTIAL** (trừ khi policy nội bộ coi là FAIL).  
3. **OUT_OF_SCOPE:** không được cite golden để “bịa” tỷ giá/CEO; nếu cite đúng mục “phạm vi không đề cập” thì có thể chấp nhận.  
4. **GQ-D01:** **FAIL** nếu còn bất kỳ source nào trùng file golden đã xóa.

---

## 7. Đánh giá faithfulness (trục C)

1. Soát nhanh answer có chứa entity **không** xuất hiện trong tài liệu (tên người, mã CK, tỷ giá, …).  
2. Kiểm tra mâu thuẫn với mục “dễ nhầm” (24/7 vs 24 giờ làm việc). Nếu model khẳng định 24/7 → **FAIL** + `HALLUCINATION` hoặc `GENERATION`.

---

## 8. Phân loại lỗi (gắn tag)

Dùng bảng tag trong `RAG_GOLDEN_QUESTIONS.md`. Một case có thể có nhiều tag.

| Tag | Khi nào dùng |
|-----|----------------|
| RETRIEVAL | Context không chứa đoạn cần thiết / trả lời “không có” dù doc có. |
| GENERATION | Context đủ nhưng tóm tắt sai. |
| NO_CONTEXT | Đồng nghĩa thực hành với retrieval miss (có thể gộp RETRIEVAL). |
| HALLUCINATION | Thêm fact không có trong doc. |
| OUT_OF_SCOPE_HANDLING | Hỏi ngoài doc nhưng model vẫn trả lời như có dữ liệu. |
| SOURCE_MISSING | Thiếu source khi cần. |
| TABLE_PARSE | Bảng markdown không được trả lời đúng dù mắt người đọc được trong file. |

---

## 9. Tổng hợp pass rate

Sau khi đủ 12 case (GQ-D01 sau bước xóa document):

```
pass_rate = PASS / 12
partial_rate = PARTIAL / 12
fail_rate = FAIL / 12
```

Điền thêm bảng tổng hợp ở cuối `RAG_GOLDEN_QUESTIONS.md` (mục “Bảng tổng hợp”).

**Không** yêu cầu ngưỡng pass cứng trong baseline — mục tiêu là **ghi nhận số liệu** trước khi tối ưu retrieval.

---

## 10. Thứ tự khuyến nghị (tối thiểu thao tác)

1. Tạo chatbot eval → lưu `apiKey`.  
2. Upload `RAG_GOLDEN_TEST_DOCUMENT.txt` → INDEXED.  
3. Chạy **GQ-F01 → GQ-O02** theo thứ tự (11 câu).  
4. `DELETE /api/documents/{DOCUMENT_ID}`.  
5. Chạy **GQ-D01**.  
6. Điền bảng tổng hợp + lưu file kết quả (ví dụ `docs/eval/results/run-YYYYMMDD.md` — tùy chọn, không bắt buộc trong repo).

---

## 11. Liên kết tài liệu

| File | Vai trò |
|------|---------|
| `docs/eval/RAG_GOLDEN_TEST_DOCUMENT.md` | Nội dung ingest (upload `.txt`) |
| `docs/eval/RAG_GOLDEN_QUESTIONS.md` | Golden set + tiêu chí + bảng tổng hợp |
| `docs/RAG_CORE_FLOW_E2E_RUNBOOK.md` | Luồng hạ tầng / env / delete |
| `docs/RAG_TARGET_ARCHITECTURE.md` | Bối cảnh module RAG |

---

*Tài liệu này không thay thế đo latency hay load test; chỉ baseline chất lượng trả lời.*
