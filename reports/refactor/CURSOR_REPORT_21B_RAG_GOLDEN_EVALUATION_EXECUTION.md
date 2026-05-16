# Cursor Report 21B — RAG Golden Evaluation Execution (runtime)

**Ngày:** 2026-05-14  
**Scope:** Chạy thực tế 12 golden case (21A) trên Docker; ghi kết quả; **không** sửa Java/React/prompt/retrieval; **không** chỉnh `.gitignore` (theo yêu cầu 21B).

---

## 1. Mức độ hiểu task

| Mục | Nội dung |
|-----|----------|
| **Hiểu task** | **98%** |
| **Chắc chắn** | Đủ bước: `.txt` từ golden doc → chatbot mới → upload → INDEXED → 11 chat (session mới/câu) → chấm A–D → DELETE → Qdrant/MySQL spot-check → GQ-D01. |
| **Giả định** | Đánh giá A–D dựa trên answer + `sources` lưu trong run log (PowerShell `Invoke-RestMethod`). |
| **Thiếu dữ kiện** | Không ghi âm FE; chỉ API. |

---

## 2. Tóm tắt yêu cầu

Verify chất lượng RAG bằng golden set nhẹ; tổng hợp PASS/PARTIAL/FAIL và nhóm lỗi; không tối ưu retrieval trong task.

---

## 3. Môi trường chạy

| Hạng mục | Giá trị |
|----------|---------|
| OS | Windows (agent Cursor) |
| Stack | `docker compose up -d` từ root repo |
| Backend | `http://localhost:8080` |
| Qdrant | `http://localhost:6333` |
| MySQL | `ragchatbot`, container `ragchatbot-mysql` |

---

## 4. Env check

- **Shell:** `GROQ_API_KEY` / `NOMIC_API_KEY` **không** set.  
- **`.env`:** pattern dòng GROQ/NOMIC có giá trị non-empty → **có** (Compose inject).  
- **Không** paste key vào report/run file.

---

## 5. Phạm vi đã chạy

1. Copy `RAG_GOLDEN_TEST_DOCUMENT.md` → `RAG_GOLDEN_TEST_DOCUMENT.txt` (nội dung giống).  
2. Health backend + Qdrant (sau khi container `Started`).  
3. `POST /api/chatbots` (tên có suffix thời gian).  
4. `POST /api/documents/upload` + poll `INDEXED`.  
5. 11× `POST /api/chat` (UTF-8, câu hỏi đúng golden).  
6. Chấm điểm thủ công theo 4 trục.  
7. `DELETE` document + Qdrant count + MySQL `deleted_at`.  
8. `POST /api/chat` GQ-D01.  
9. Ghi `docs/eval/results/RAG_EVAL_RUN_21B_20260514.md` + report này.

---

## 6. Phạm vi chưa chạy và lý do

| Hạng mục | Lý do |
|----------|--------|
| `mvnw compile` | Không sửa code — không bắt buộc. |
| Re-run với cùng session | Đã chọn session mới/câu theo khuyến nghị 21B. |

---

## 7. File đã tạo / cập nhật

| Path | Mô tả |
|------|--------|
| `docs/eval/RAG_GOLDEN_TEST_DOCUMENT.txt` | Bản copy từ `.md` để upload |
| `docs/eval/results/RAG_EVAL_RUN_21B_20260514.md` | Kết quả chi tiết run |
| `docs/eval/results/` | Thư mục (đã có từ trước hoặc tạo trong 21B) |
| `reports/refactor/CURSOR_REPORT_21B_RAG_GOLDEN_EVALUATION_EXECUTION.md` | Báo cáo task (file này) |

**Đã xóa file phụ** (không nằm trong spec 21B): `golden_questions_payload_21B.json`, `RAG_EVAL_RUN_21B_raw_payload.json` sau khi tổng hợp vào markdown.

---

## 8. Upload golden document

| Trường | Giá trị |
|--------|---------|
| DOCUMENT_ID | `fcf9b169-4be8-413e-9feb-71c48e357539` |
| CHATBOT_ID | `a265b956-7864-408b-9b84-d75db80ac92f` |
| Status | `INDEXED`, `chunkCount` = 6 |
| apiKey | Masked `a2955c72...28a6` |

---

## 9. Kết quả từng golden case (tóm tắt)

Chi tiết cột A/B/C/D, answer summary, tag: **`docs/eval/results/RAG_EVAL_RUN_21B_20260514.md`**.

Tóm tắt verdict:

| ID | Verdict |
|----|---------|
| GQ-F01 | PASS |
| GQ-F02 | PASS |
| GQ-F03 | PARTIAL |
| GQ-F04 | FAIL |
| GQ-L01 | FAIL |
| GQ-L02 | FAIL |
| GQ-T01 | PARTIAL |
| GQ-T02 | FAIL |
| GQ-C01 | PASS |
| GQ-O01 | PASS |
| GQ-O02 | PASS |
| GQ-D01 | PASS |

---

## 10. Bảng tổng hợp pass/partial/fail

| Chỉ số | Số |
|--------|---:|
| total | 12 |
| PASS | 7 |
| PARTIAL | 2 |
| FAIL | 3 |

---

## 11. Phân tích lỗi theo nhóm

| Nhóm | Quan sát |
|------|----------|
| **Retrieval** | F04 (Bước 2), L01/L02 (chính sách + tên gói), T02 (Business) — miss hoặc “không tìm thấy” sai. |
| **Generation** | F03, T01: số đúng nhưng dòng attribution “Section” lệch; L01: mâu thuẫn nội dung tài liệu. |
| **Source** | F04, L02, T02: `sourceCount` = 0 khi cần có evidence in-scope (FAIL tiêu chí B). |
| **Table parse** | L02, T02 — liên quan bảng markdown trong TXT. |
| **Out-of-scope** | O01, O02: **không** có lỗi xử lý OOS. |

---

## 12. Delete verification

- HTTP DELETE: **200**.  
- Qdrant filter `document_id` + `widgetId`: **0** point.  
- MySQL: `documents.deleted_at` set (`del=1` trong query kiểm tra).  
- GQ-D01: không source; không trả mã như doc đang phục vụ — **PASS**.

---

## 13. Có sửa runtime không?

**Không** — không thay đổi Java/React/YAML.

---

## 14. Không có runtime diff

Không có thay đổi source backend/frontend.

---

## 15. Kết luận

- **Baseline hiện tại:** **7/12 PASS**, **2 PARTIAL**, **3 FAIL** — out-of-scope ổn; điểm yếu tập trung **bảng trong TXT** và **một số fact/section** (F04, L01, L02, T02).  
- **Nhóm ưu tiên tối ưu sau:** (1) table-aware / chunk bảng; (2) retrieval cho mục quy trình & list; (3) (nhẹ) giảm attribution sai section khi số liệu đúng.

---

## 16. Đề xuất prompt tiếp theo

1. Task nhỏ: **phân tích chunk** của `RAG_GOLDEN_TEST_DOCUMENT.txt` trong DB/Qdrant cho F04/L02/T02 (read-only).  
2. Task table-aware / markdown table pipeline (sau khi có căn cứ).  
3. Task prompt: yêu cầu “Nguồn” khớp section thực tế (sau khi retrieval ổn).

---

## Phụ lục — File đã đọc (bắt buộc)

Tất cả tồn tại: `RAG_GOLDEN_TEST_DOCUMENT.md`, `RAG_GOLDEN_QUESTIONS.md`, `RAG_EVALUATION_RUNBOOK.md`, `CURSOR_REPORT_21A`, `CURSOR_REPORT_20G`, `RAG_CORE_FLOW_E2E_RUNBOOK.md`, `DocumentController`, `ChatController`, `ChatbotController`, `WidgetAuthFilter` — đối chiếu flow upload/chat/delete/header.

---

## Kiểm tra sau task

| Kiểm tra | Kết quả |
|----------|---------|
| `docs/eval/RAG_GOLDEN_TEST_DOCUMENT.txt` tồn tại, không rỗng | **OK** |
| `docs/eval/results/RAG_EVAL_RUN_21B_20260514.md` tồn tại, không rỗng | **OK** |
| Report 21B tồn tại, không rỗng | **OK** |
