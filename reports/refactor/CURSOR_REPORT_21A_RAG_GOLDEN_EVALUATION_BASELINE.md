# Cursor Report 21A — RAG Golden Evaluation Baseline (docs only)

**Ngày:** 2026-05-14  
**Scope:** Tạo tài liệu + golden questions + runbook đánh giá chất lượng RAG **nhẹ** sau khi core E2E pass (20G); **không** sửa runtime, không thêm dependency/service/API/DB.

---

## 1. Mức độ hiểu task

| Mục | Nội dung |
|-----|----------|
| **Hiểu task** | **96%** |
| **Chắc chắn** | Đủ 12 case theo phân bổ; 4 tiêu chí/case; runbook manual/API; file đặt đúng path `docs/eval/` + report `reports/refactor/`. |
| **Giả định** | Upload `.txt` chứa markdown bảng — parser TXT giữ ký tự `|`; có thể cần table-aware sau nếu TABLE_LOOKUP fail nhiều (ghi trong golden questions). |
| **Thiếu dữ kiện** | Chưa chạy eval runtime trong task này (theo yêu cầu). |

---

## 2. Tóm tắt yêu cầu

Tạo golden document (VN, ngắn), 12+ câu hỏi có loại FACT/LIST/TABLE_LOOKUP/COUNT/OUT_OF_SCOPE/DELETE_VERIFICATION, runbook chấm điểm PASS/PARTIAL/FAIL trên 4 trục + tag lỗi + bảng tổng hợp; report task; không đụng code.

---

## 3. Vì sao làm evaluation trước khi tối ưu retrieval

- Cần **đường cơ sở có thể lặp lại** để biết câu nào fail do retrieval vs generation vs out-of-scope handling.  
- Tránh tối ưu “mù”: `RagRetrievalService` có nhiều nhánh và ngưỡng `FINAL_LIMIT` / `MAX_CONTEXT_*` (**SOURCE**) — đo bằng golden nhỏ trước khi chỉnh.  
- Khuyến khích trong `docs/RAG_TARGET_ARCHITECTURE.md`: evaluation nhẹ trên máy yếu.

---

## 4. Phạm vi đã làm

- Thư mục `docs/eval/` với 3 file tài liệu.  
- Cập nhật `docs/RAG_CORE_FLOW_E2E_RUNBOOK.md` — mục **21** link sang eval (không đổi endpoint/flow E2E).  
- `.gitignore`: thêm negation `!docs/eval/**` (folder trước đó bị `docs/*` loại khỏi git); đổi `reports/` → `reports/*` + negation cho **chỉ** `reports/refactor/CURSOR_REPORT_21A_RAG_GOLDEN_EVALUATION_BASELINE.md` để report 21A có thể `git add` (các file `reports/` khác vẫn ignore).  
- Report này.

---

## 5. Phạm vi không làm

- Không sửa Java/React/YAML prompt runtime.  
- Không thêm dependency, service, API, bảng DB, hybrid search, rerank tuning, framework benchmark.  
- Không chạy backend/E2E trong task 21A.  
- Không mở whitelist hàng loạt toàn bộ `reports/` (chỉ một file report 21A).

---

## 6. Danh sách file đã đọc

| Path | Mục đích | Kết luận ngắn |
|------|----------|----------------|
| `reports/refactor/CURSOR_REPORT_20G_CORE_RAG_E2E_EXECUTION.md` | Bối cảnh E2E pass | Luồng upload/index/chat/delete đã verify. |
| `docs/RAG_CORE_FLOW_E2E_RUNBOOK.md` | Endpoint / env | Chèn mục 21 link eval. |
| `docs/RAG_TARGET_ARCHITECTURE.md` | Kiến trúc | RAG modular; roadmap eval/hybrid/table-aware. |
| `RagRetrievalService.java` | Retrieval | Giới hạn chunk/context theo loại query. |
| `ChatService.java` | Chat | Orchestration LLM + persistence. |
| `PromptBuilderService.java` | Prompt | Chỉ trả lời từ tài liệu; câu từ chối chuẩn khi không có context. |
| `QueryAnalyzerService.java` | QueryType | Enum: NORMAL_FACT, LIST_ALL, TABLE_LOOKUP, COUNT_QUERY, … — map vào cột “query type dự kiến” trong golden questions. |
| `ChatMessage.java` | Lưu message | `sources` JSON trên entity. |
| `ChatFeedback.java` | Feedback | Rating cho tin nhắn — có thể dùng sau cho analytics eval. |
| `AnalyticsService.java` | Analytics | Tổng hợp session/source — ngoài scope baseline thủ công. |
| `docs/samples/RAG_E2E_SAMPLE.txt` | Style sample | TXT ngắn cho E2E. |

**Tất cả file trên tồn tại** tại thời điểm task.

---

## 7. Danh sách file đã tạo / cập nhật

| Path | Hành động |
|------|-----------|
| `docs/eval/RAG_GOLDEN_TEST_DOCUMENT.md` | Tạo mới |
| `docs/eval/RAG_GOLDEN_QUESTIONS.md` | Tạo mới |
| `docs/eval/RAG_EVALUATION_RUNBOOK.md` | Tạo mới |
| `docs/RAG_CORE_FLOW_E2E_RUNBOOK.md` | Thêm mục 21 |
| `.gitignore` | Whitelist `docs/eval/**` + report 21A dưới `reports/refactor/` |
| `reports/refactor/CURSOR_REPORT_21A_RAG_GOLDEN_EVALUATION_BASELINE.md` | Tạo mới (file này) |

---

## 8. Nội dung chính của golden document

- Công ty giả **AlphaDemo**, mã nội bộ `ADM-DEMO-009`, mã golden `GOLDEN-VN-2026-714`.  
- **3** chính sách hỗ trợ; **quy trình 3 bước** (Bước 2 = phân loại nhãn).  
- **Bảng markdown** 3 gói Basic/Pro/Business (giá, lượt hỏi, kênh hỗ trợ).  
- **Giới hạn** (3 file đính kèm, …).  
- Mục **dễ nhầm** (24/7 vs 24 giờ làm việc).  
- Mục **phạm vi không đề cập** (USD/VND, CEO, CK) phục vụ OUT_OF_SCOPE.

---

## 9. Danh sách test case đã tạo (12)

| ID | Loại |
|----|------|
| GQ-F01 … GQ-F04 | FACT (4) |
| GQ-L01, GQ-L02 | LIST (2) |
| GQ-T01, GQ-T02 | TABLE_LOOKUP (2) |
| GQ-C01 | COUNT (1) |
| GQ-O01, GQ-O02 | OUT_OF_SCOPE (2) |
| GQ-D01 | DELETE_VERIFICATION (1) |

Chi tiết câu hỏi / expected / pass criteria: `docs/eval/RAG_GOLDEN_QUESTIONS.md`.

---

## 10. Cách đánh giá PASS / PARTIAL / FAIL

Đồng bộ với `RAG_GOLDEN_QUESTIONS.md`: bốn trục A/B/C/D → verdict gộp; tag lỗi khi PARTIAL/FAIL. Runbook `RAG_EVALUATION_RUNBOOK.md` mô tả cách ghi bảng kết quả và pass rate.

---

## 11. Diff từng file

### 11.1 `docs/eval/RAG_GOLDEN_TEST_DOCUMENT.md` (file mới)

```diff
+--- (mới) Tài liệu giả lập AlphaDemo: thông tin chung, chính sách, quy trình 3 bước,
+    bảng gói dịch vụ markdown, giới hạn, mục dễ nhầm, phạm vi không đề cập (CEO/tỷ giá/CK).
+    Mã golden: GOLDEN-VN-2026-714
```

*(Toàn bộ nội dung nằm trong file — không trích dài trong report.)*

### 11.2 `docs/eval/RAG_GOLDEN_QUESTIONS.md` (file mới)

```diff
+--- (mới) 12 test case + bảng tiêu chí 4 trục + bảng tổng hợp pass/partial/fail + tag lỗi
+    + map QueryType dự kiến từ QueryAnalyzerService
```

### 11.3 `docs/eval/RAG_EVALUATION_RUNBOOK.md` (file mới)

```diff
+--- (mới) Hướng dẫn upload (.txt), POST /api/chat, ghi kết quả, chấm A-D, tag lỗi,
+    pass rate, thứ tự chạy kèm DELETE trước GQ-D01; cảnh báo DocumentService chỉ .pdf/.txt
```

### 11.4 `docs/RAG_CORE_FLOW_E2E_RUNBOOK.md`

```diff
@@ ... giữa checklist và Phụ lục endpoint ...
+
+## 21. Kiểm thử chất lượng RAG (golden baseline — tùy chọn)
+...
+Báo cáo baseline task: `reports/refactor/CURSOR_REPORT_21A_RAG_GOLDEN_EVALUATION_BASELINE.md`.
```

### 11.5 `reports/refactor/CURSOR_REPORT_21A_RAG_GOLDEN_EVALUATION_BASELINE.md`

```diff
+--- (mới) Báo cáo task 21A (file này)
```

### 11.6 `.gitignore`

```diff
 !docs/samples/
 !docs/samples/**
+!docs/eval/
+!docs/eval/**
 
-reports/
+reports/*
+!reports/refactor/
+!reports/refactor/CURSOR_REPORT_21A_RAG_GOLDEN_EVALUATION_BASELINE.md
```

---

## 12. Kết quả kiểm tra file tồn tại / không rỗng

| Kiểm tra | Kết quả |
|----------|---------|
| `docs/eval/RAG_GOLDEN_TEST_DOCUMENT.md` tồn tại, size > 0 | **OK** (~3009 bytes) |
| `docs/eval/RAG_GOLDEN_QUESTIONS.md` tồn tại, size > 0 | **OK** (~10537 bytes) |
| `docs/eval/RAG_EVALUATION_RUNBOOK.md` tồn tại, size > 0 | **OK** (~6992 bytes) |
| `git check-ignore -q docs/eval/...` | **not ignored** (có thể `git add`) |
| `git diff --stat --` (các path trên) | Trên working tree hiện tại: **chỉ** `.gitignore` có diff đã track; `docs/eval/*`, report 21A, chỉnh sửa runbook là **untracked**/`??` cho đến khi `git add` — dùng `git status` để xem đầy đủ. |

---

## 13. Có sửa runtime không?

**Không** sửa backend/frontend runtime (Java/React).  
**Có** chỉnh `.gitignore` tối thiểu để git track được `docs/eval/**` và đúng một file report dưới `reports/refactor/` (cấu hình repo, không ảnh hưởng JVM/UI).

---

## 14. Rủi ro còn lại

- **Định dạng file:** Tài liệu lưu dạng `.md` nhưng upload bắt buộc `.txt` — nếu quên đổi tên sẽ **400** từ API.  
- **Bảng trong TXT:** Markdown table có thể bị chunk tách dòng — TABLE_LOOKUP có thể PARTIAL/FAIL dù mắt người đọc được (ghi `TABLE_PARSE`).  
- **Golden chưa chạy:** Số liệu pass rate trong `RAG_GOLDEN_QUESTIONS.md` vẫn trống cho đến khi operator chạy runbook.

---

## 15. Đề xuất prompt tiếp theo

1. **Task chạy eval thực tế:** một phiên chỉ upload golden + điền bảng kết quả + paste 1–2 answer lỗi tiêu biểu (không secret).  
2. **Task table-aware (sau baseline):** nếu GQ-T* / GQ-C01 fail ≥ 50%.  
3. **Task hybrid lexical (sau baseline):** nếu GQ-F* fact ngắn miss với retrieval vector-only.

---

*Báo cáo hoàn chỉnh theo checklist 21A; không chạy backend trong phiên soạn.*
