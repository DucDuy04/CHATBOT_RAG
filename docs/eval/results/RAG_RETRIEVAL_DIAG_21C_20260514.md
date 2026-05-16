# RAG Retrieval / Ingest Diagnosis — Task 21C (READ-ONLY)

**Ngày:** 2026-05-14  
**Phạm vi:** Chẩn đoán golden eval 21B (FAIL/PARTIAL); **không** sửa Java/React/prompt/retrieval runtime.  
**Phương pháp:** Đọc source + SQL MySQL (kể cả bản ghi soft-delete) + Qdrant scroll + `POST /api/chat` có chọn lọc + `docker logs chatbot-backend`.

---

## 0. Hiệu chỉnh số liệu tổng hợp run 21B (recompute từ bảng case)

**Nguồn:** `docs/eval/results/RAG_EVAL_RUN_21B_20260514.md` — mục **§3 Bảng kết quả từng case** (cột `verdict`), cộng thêm **GQ-D01** §4 (PASS).

| case_id | verdict (theo bảng case) |
|---------|--------------------------|
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

**Recompute:**

| Chỉ số | Số đúng |
|--------|--------:|
| total | 12 |
| **PASS** | **6** |
| **PARTIAL** | **2** |
| **FAIL** | **4** |

**Mâu thuẫn với §5 file 21B:** phần tổng hợp ghi `PASS=7, PARTIAL=2, FAIL=3` **không khớp** bảng từng case: **GQ-F03** là **PARTIAL** (không phải PASS). Nếu ai đó cộng nhầm theo cột A/B/C/D toàn `y` cho F03 thì sẽ ra 7 PASS — **chấm verdict phải theo cột `verdict`**, không theo A/B/C/D đơn thuần.

**Hành động:** không sửa file `RAG_EVAL_RUN_21B_20260514.md` trong task 21C; hiệu chỉnh nằm ở báo cáo này.

---

## 1. Inspect document — Option A (ưu tiên) + tái kiểm API

| Lựa chọn | Chi tiết |
|----------|----------|
| **Option A** | Truy vấn **raw SQL** MySQL bỏ qua `@SQLRestriction` của JPA: bảng `documents`, `document_chunks`, `document_sections`, `document_tables` với `document_id = fcf9b169-4be8-413e-9feb-71c48e357539` (document 21B đã soft-delete). **Thành công** — vẫn còn đủ 6 chunk + metadata. |
| **Option B** | Không bắt buộc upload mới: trên DB đã có bản `RAG_GOLDEN_TEST_DOCUMENT.txt` **chưa xóa** (`0e5719ae-3b3a-4729-ab41-ac0bc51265a2`, `widget_config_id=c5844a0e-afd3-49ad-a073-c18198c085cc`) để Qdrant scroll + gọi lại `/api/chat`. Cấu trúc chunk/section **cùng pattern** với 21B (cùng pipeline ingest). |

---

## 2. Document / chunk inventory (21B `fcf9b169…`)

**Tài liệu golden có đáp án không?** **Có** — toàn bộ fact/list/table nằm trong `docs/eval/RAG_GOLDEN_TEST_DOCUMENT.md` / `.txt` (đã đối chiếu).

**Sau ingest (6 chunk) — tóm tắt từ SQL:**

| chunk_index | order_index | chunk_type | section_id | section_title (rút gọn) | table_id | clen | Ghi chú |
|------------|---------------|------------|------------|-------------------------|----------|------|--------|
| 0 | 0 | text | sec_idx_0 | General | — | 582 | Intro + `## 1.` … (markdown `#` không tạo section riêng theo parser số) |
| 1 | 1 | text | sec_3 | Tiêu đề section = dòng list **“3. Không hỗ trợ…”** (false heading) | — | 237 | **Chứa** `## 3. Quy trình…`, **Bước 1 + Bước 2** (đáp án F04), đầu mục 4 |
| 2 | 2 | text | sec_3 | (tiếp) false “Bước 3” / `## 4` | — | 56 | Phần đầu mục bảng |
| 3 | 3 | table_summary | sec_3 | **Gán nhãn “Bước 3”** | tbl_4_3 | 412 | Bảng Basic/Pro/Business — **metadata section sai** |
| 4 | 4 | table_row_group | sec_3 | idem | tbl_4_3 | 180 | Nội dung bảng đúng |
| 5 | 5 | text | sec_3 | idem | — | 1005 | Phần sau bảng |

**Các chuỗi cần map — nằm ở chunk nào:**

| Evidence | Chunk (index) / vị trí |
|----------|-------------------------|
| Tên pháp lý AlphaDemo | chunk 0 |
| Mã `GOLDEN-VN-2026-714` | chunk 0 |
| **Bước 2 — Phân loại** | **chunk 1** (nội dung đúng; `section_title`/`heading_path` **sai**) |
| 3 chính sách (list 1–3) | **Không có chunk riêng đúng mục “Chính sách”**: parser tách list thành các `document_sections` sec_1, sec_2, sec_3 nhưng **chunk DB không gắn section_id sec_1/sec_2** — toàn bộ body sau list bị gom vào chunk mang `section_id=sec_3` |
| Bảng gói / Basic Pro Business / Ưu tiên | chunk 3–4 (nội dung); metadata section = “Bước 3” |
| Mục 7 OOS | chunk 5 |

---

## 3. Section / table inventory (21B)

**`document_sections` (5 dòng — đều `deleted_at` set, đọc raw SQL):**

| order | section_key | title (rút ý) |
|------|-------------|----------------|
| 0 | sec_idx_0 | General |
| 1 | sec_1 | `1. Phản hồi yêu cầu trong **24 giờ làm việc**…` ← **dòng list**, không phải `## 2. Chính sách` |
| 2 | sec_2 | `2. Mỗi phiên chat…` ← list |
| 3 | sec_3 | `3. Không hỗ trợ can thiệp…` ← list |
| 4 | sec_3__dup2 | `3. **Bước 3 — Phản hồi:**…` ← **va chạm khóa `sec_3`** (section thứ hai cùng prefix số) |

**Kết luận parser/chunking:** Markdown `## 2. Chính sách…` / `## 3. Quy trình…` **không** được dùng làm ranh giới section (regex heading chỉ bắt dạng số ở đầu dòng kiểu `1. …`, không bắt `##`). **Danh sách đánh số 1. 2. 3.** trong mục chính sách bị hiểu nhầm **section header**. `DocumentTable` **có** 1 bản ghi (`tbl_4_3`) nhưng **gắn section sai** (cùng cây `sec_3` / tiêu đề “Bước 3”).

---

## 4. Qdrant payload (document active `0e5719ae…`, cùng cấu trúc 6 point)

Scroll filter `document_id=0e5719ae-3b3a-4729-ab41-ac0bc51265a2`:

- **6** point — khớp `chunkCount`.
- Payload có đủ: `chunk_id`, `document_id`, `widgetId`, `chunk_type`, `section_id`, `section_title`, `heading_path_text`, `table_id`, `fileName` / `source_file`.
- **Vấn đề:** `section_title` của các point bảng và nhiều point text trùng pattern **“3. **Bước 3** …”** — đây là **metadata sai từ ingest**, không phải thiếu field Qdrant.

---

## 5. Targeted rerun `/api/chat` (widget `c5844a0e…`, document `0e5719ae…`)

| Case | Answer (tóm tắt) | sourceCount | Nhận xét nhanh |
|------|------------------|-------------|----------------|
| GQ-F04 | “Không tìm thấy…” | **0** | Retrieval trả **0 context** |
| GQ-L01 | Khẳng định không có “ba chính sách” (sai doc) | 6 | Context nhiễu / LLM diễn giải sai |
| GQ-L02 | “Không tìm thấy…” | **0** | Giống F04 — pool rỗng sau heading lock + guardrail |
| GQ-T02 | “Không tìm thấy…” | **0** | idem |
| GQ-F03 | Đúng **500**; kèm “Section: … Bước 3 …” | 6 | Fact đúng, **attribution sai** |
| GQ-T01 | Đúng **99000**; Section trỏ “Bước 3” | 6 | idem |

---

## 6. Log backend (bằng chứng hành vi retrieval)

Trích từ `docker logs chatbot-backend` (2026-05-14):

**GQ-F04** — `queryType=SECTION_SUMMARY`:

```text
[QA] Heading match candidates (top 1): ['1. Phản hồi yêu cầu trong **24 giờ làm việc**...' [sec_1 titleHits=3 score=3]]
[RAG] Selected section: ... [sec_1] | Locked scope: 1 section(s): [sec_1]
[EmbeddingSearch] ... matches=6
... EXCLUDED (out of locked scope): ... segments   (toàn bộ point có section_id thực tế là sec_3 / sec_idx_0, không có sec_1)
[RAG] Locked scope: 0 chunks fetched from DB for sectionIds=[sec_1]
[RAG] GUARDRAIL: locked scope sec_1 returned 0 chunks — falling back...
[RAG] Qdrant returned 0 anchors and locked scope empty ...
```

**GQ-T02** — `queryType=TABLE_LOOKUP`:

```text
[RAG] Semantic results excluded (out of locked scope): 18 segments
[RAG] Vector anchors: chunks=0 ...
[RAG] Locked scope: 0 chunks fetched from DB for sectionIds=[sec_3__dup2]
[RAG] GUARDRAIL: locked scope sec_3__dup2 returned 0 chunks — falling back...
[RAG] Qdrant returned 0 anchors ...
```

**Ý nghĩa:** (1) **Heading lock** chọn section **không có chunk** (`sec_1`) hoặc **khóa vào `sec_3__dup2`** trong khi mọi chunk bảng dùng `section_id=sec_3`. (2) Trong vòng lặp vector, mọi hit bị **loại vì out-of-lock** trước khi gom `anchorChunkIds`. (3) Sau guardrail xóa lock, pipeline **không embed lại** vector search không filter → `anchorChunkIds` vẫn rỗng → **early return** rỗng (`RagRetrievalService` ~243–245).

---

## 7. Root cause matrix (theo case yêu cầu)

| Case | Evidence trong golden .md/.txt | Trong DB chunk? | Trong Qdrant payload? | Retrieval có chunk đúng? | Lỗi chính (nhãn nội bộ 21C) | Ưu tiên fix |
|------|----------------------------------|-----------------|------------------------|----------------------------|-----------------------------|-------------|
| **GQ-F04** | Bước 2 trong §3 quy trình | Có (chunk 1) | Có (text_segment/embed) | **Không** — 0 context | **PARSER** (list → section), **QUERY_ANALYZER** (match sec_1), **DB_EXPANSION**/heading lock + **VECTOR_RETRIEVAL** (filter rồi không re-query) | **P0** |
| **GQ-L01** | 3 bullet mục 2 | Phân mảnh sai; không có 1 chunk “mục chính sách” sạch | Đủ payload nhưng nhiễu | Có source nhưng context **lệch / thiếu cấu trúc** | **PARSER**, **PROMPT_GENERATION** (mâu thuẫn), phụ **SOURCE_ATTRIBUTION** | **P0** |
| **GQ-L02** | Basic/Pro/Business | Có trong table chunks | Có | **Không** — 0 context | **PARSER**, **TABLE_MARKDOWN** (section/table gắn sai), **QUERY_ANALYZER** + lock `sec_3__dup2`, **VECTOR_RETRIEVAL** (cùng lỗi F04) | **P0** |
| **GQ-T02** | “Ưu tiên” cột Hỗ trợ | Có trong table_row_group | Có | **Không** — 0 context | Giống L02 | **P0** |
| **GQ-F03** | 500 lượt Pro | Có | Có | Có — LLM đúng số | **SOURCE_ATTRIBUTION** + **CHUNKING** metadata (section_title “Bước 3”), phụ **PROMPT_GENERATION** | **P1** |
| **GQ-T01** | 99000 Basic | Có | Có | Có | Giống F03 | **P1** |

**EVAL_SUMMARY_ERROR:** tổng hợp 7/2/3 trong file 21B — **P2** (chỉ sửa báo cáo / quy ước chấm).

---

## 8. Trả lời checklist 10 câu (task spec)

1. **Tài liệu có đáp án?** Có.  
2. **Đoạn nào?** Mục 2 (chính sách), 3 (quy trình / Bước 2), 4 (bảng).  
3. **Chunk nào?** Nội dung nằm chủ yếu chunk 0–5 như bảng §2; ranh giới **không** khớp mục markdown.  
4. **Embed Qdrant?** Có (6 point / document).  
5. **Payload đủ?** Field đủ; **giá trị `section_title`/`section_id` sai** cho phần bảng và phần quy trình.  
6. **Retrieval lấy được khi hỏi?** F04/L02/T02: **không**; L01: có nhưng chất lượng kém; F03/T01: có.  
7. **Nếu miss:** chủ yếu **heading match + lock scope** + **filter vector trước khi anchor** + **mismatch `sec_3__dup2` vs chunk `sec_3`**; không phải thiếu bảng trong DB.  
8. **Nếu đúng fact, answer sai:** L01 — **prompt/generation** trên context mơ hồ.  
9. **Attribution sai:** **chunk metadata** (`section_title` từ pipeline parser/chunking), LLM chỉ đọc theo metadata trong prompt.  
10. **Thứ tự fix đề xuất (chưa implement):**  
    - **P0:** Sửa **false section** từ numbered list trong TXT markdown (parser hoặc pre-pass strip `##` sections đúng chuẩn).  
    - **P0:** Heading lock: không lock vào section **không có chunk** / canonical hóa `sec_3__dup2` vs `sec_3` **hoặc** sau guardrail **chạy lại** vector search không filter.  
    - **P1:** Prompt / format nguồn: bắt buộc trích đúng heading hoặc map từ `heading_path_text` khi trả lời table.  
    - **P2:** Sửa summary file 21B hoặc runbook “verdict là chuẩn”.

---

*Tài liệu này chỉ phục vụ audit 21C; không thay thế golden questions.*
