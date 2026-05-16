# Cursor Report 21C — RAG Golden FAIL/PARTIAL Diagnosis (READ-ONLY)

**Ngày:** 2026-05-14  
**Scope:** Chẩn đoán nguyên nhân GQ-F04, GQ-L01, GQ-L02, GQ-T02, GQ-F03, GQ-T01 từ **source + DB + Qdrant + log + API**; **không** sửa retrieval/prompt/Java/React; **không** thêm dependency.

---

## 1. Mức độ hiểu task

| Mục | Nội dung |
|-----|----------|
| **Hiểu task** | **97%** |
| **Chắc chắn** | Đọc đủ tài liệu eval + source pipeline parser/chunking/embed/retrieval/query; SQL dump chunk/section/table cho `fcf9b169…`; Qdrant 6 point; log backend chứng minh heading lock + filter vector; rerun 6 câu API. |
| **Giả định** | Document active `0e5719ae…` cùng pipeline với 21B có cùng pattern lỗi (đã xác nhận qua log + cấu trúc 6 chunk). |
| **Thiếu dữ kiện** | Không có snapshot Qdrant cho **chính** `fcf9b169` sau purge (đã xóa vector); đã dùng document cùng nội dung còn point để kiểm payload. |

---

## 2. Tóm tắt yêu cầu

Recompute số liệu 21B; xác định lỗi nằm ở tài liệu/chấm vs parser vs chunking vs embed/Qdrant vs query analyzer vs retrieval vs context vs generation vs attribution; không implement fix.

---

## 3. Phạm vi đã làm

- Đọc toàn bộ file eval/runbook/report 21A/21B và `docs/RAG_TARGET_ARCHITECTURE.md` (tồn tại).  
- Đọc source: `DocumentParserService`, `ChunkingService2`, `EmbeddingService`, `RagRetrievalService`, `QueryAnalyzerService`, `PromptBuilderService` (mục no-context qua `ChatService`), `ChatService`, entities document/chunk/section/table, repositories.  
- MySQL raw SQL: document `fcf9b169-4be8-413e-9feb-71c48e357539` (soft-delete) + chunks/sections/tables.  
- Qdrant HTTP scroll: document `0e5719ae-3b3a-4729-ab41-ac0bc51265a2`.  
- `POST /api/chat` 6 câu targeted (widget eval `c5844a0e-afd3-49ad-a073-c18198c085cc`).  
- `docker logs chatbot-backend` trích đoạn RAG/QA.  
- Tạo `docs/eval/results/RAG_RETRIEVAL_DIAG_21C_20260514.md` + report này.

---

## 4. Phạm vi không làm

- Không sửa backend/frontend.  
- Không sửa `RAG_EVAL_RUN_21B_20260514.md` (chỉ ghi hiệu chỉnh trong diagnosis/report 21C).  
- Không upload thêm document mới (đã có bản active đủ inspect).  
- Không `mvnw compile` (không đổi code).

---

## 5. Các file đã đọc

| Path | Đọc để | Kết luận chính |
|------|--------|----------------|
| `docs/eval/RAG_GOLDEN_TEST_DOCUMENT.md` | Evidence expected | Đủ fact/list/table; markdown `##` + list `1.2.3.` |
| `docs/eval/RAG_GOLDEN_TEST_DOCUMENT.txt` | Giống nội dung ingest | Giống `.md` |
| `docs/eval/RAG_GOLDEN_QUESTIONS.md` | Tiêu chí case | Định nghĩa F04/L01/L02/T02/F03/T01 rõ |
| `docs/eval/RAG_EVALUATION_RUNBOOK.md` | Quy trình eval | Upload `.txt`; API chat |
| `docs/eval/results/RAG_EVAL_RUN_21B_20260514.md` | Recompute | Mâu thuẫn summary §5 vs bảng case §3 |
| `reports/refactor/CURSOR_REPORT_21B_…` | Bối cảnh chạy | DOCUMENT_ID, chunkCount=6, session strategy |
| `reports/refactor/CURSOR_REPORT_21A_…` | Baseline docs | Không runtime |
| `docs/RAG_TARGET_ARCHITECTURE.md` | Map module | Trùng với các service đã đọc |
| `DocumentParserService.java` | Heading regex TXT | Chỉ bắt `\d+…` đầu dòng → list bị nhận là section |
| `ChunkingService2.java` | Table vs section | Bảng markdown gắn theo section hiện tại → sai title |
| `EmbeddingService.java` | Payload + embed text | Payload đủ; embed kèm heading sai → semantic nhiễu |
| `QueryAnalyzerService.java` | QueryType + heading match | `SECTION_SUMMARY`/`TABLE_LOOKUP`/`LIST_ALL`; scoring ưu tiên title có `yeu`+`cau` |
| `RagRetrievalService.java` | Lock + vector filter + early return | Filter theo lock trước khi gom anchor; guardrail không re-search |
| `PromptBuilderService.java` | (tham chiếu qua ChatService) | Không đổi runtime — no-op đọc |
| `ChatService.java` | Empty context | `contexts.isEmpty()` → câu chuẩn không tìm thấy; vẫn trả `sources` từ build trước LLM — với empty retrieval sources rỗng |
| `Document.java`, `DocumentChunk.java`, `DocumentSection.java`, `DocumentTable.java`, `DocumentChunkRepository.java` | Soft-delete + field | `@SQLRestriction` → cần raw SQL để audit doc đã xóa |

---

## 6. Có phát hiện mâu thuẫn số liệu 21B không?

**Có.** Mục §5 (`PASS=7, FAIL=3`) **lệch** bảng case §3 + GQ-D01: đúng là **PASS=6, PARTIAL=2, FAIL=4** (F03 là PARTIAL, không phải PASS).

---

## 7. Số liệu baseline đã recompute

| Chỉ số | Giá trị |
|--------|--------:|
| PASS | 6 |
| PARTIAL | 2 |
| FAIL | 4 |
| total | 12 |

---

## 8. Cách inspect document/chunks

- **Option A (chính):** Raw SQL MySQL trên `fcf9b169-4be8-413e-9feb-71c48e357539` (soft-delete) — đủ 6 chunk + 5 section + 1 table.  
- **Bổ sung:** Document `0e5719ae-3b3a-4729-ab41-ac0bc51265a2` + widget `c5844a0e-afd3-49ad-a073-c18198c085cc` cho Qdrant + `/api/chat` (cùng pattern ingest).

---

## 9. Document/chunk inventory summary

- **6 chunk**; **không** có chunk nào mang `section_id` ∈ {sec_1, sec_2} dù `document_sections` có sec_1, sec_2.  
- **Bước 2** nằm trong **chunk_index=1** nhưng `section_id=sec_3` và `section_title` là dòng list “3. Không hỗ trợ…”.  
- Bảng gói: `table_summary` + `table_row_group`, `table_id=tbl_4_3`, cùng **section_title** mang tên “Bước 3”.

---

## 10. Section/table inventory summary

- **5** `document_sections`: General + **3 pseudo-sections từ list** + `sec_3__dup2` (Bước 3).  
- **1** `document_table`: markdown đúng nội dung bảng nhưng title/section gắn sai ngữ cảnh.

---

## 11. Qdrant payload summary

- **6** point / document active; filter keys đủ.  
- `section_title` lặp pattern “Bước 3” trên table chunks — khớp lỗi metadata MySQL.

---

## 12. Targeted question rerun

Đã chạy 6 câu — kết quả tóm tắt trong `docs/eval/results/RAG_RETRIEVAL_DIAG_21C_20260514.md` §5: F04/L02/T02 → 0 context; L01 → 6 sources nhưng trả lời mâu thuẫn; F03/T01 đúng số, attribution “Bước 3”.

---

## 13. Root cause matrix từng case

### GQ-F04

- **Evidence:** Bước 2 — Phân loại (§3).  
- **Trong DB:** Có trong chunk 1.  
- **Qdrant:** Có (cùng embed text).  
- **Retrieval:** **0** context — log: heading lock `sec_1` (titleHits=3), mọi vector hit `sec_3`/General bị exclude; DB 0 chunk `sec_1`; guardrail clear lock nhưng **không** refill anchors → early return.  
- **Nhãn:** PARSER, QUERY_ANALYZER, VECTOR_RETRIEVAL + DB_EXPANSION (heading lock path).

### GQ-L01

- **Evidence:** 3 bullet mục chính sách.  
- **DB:** Nội dung tồn tại nhưng **cấu trúc section/chunk sai** (list → sec_1..3, chunk không theo sec_1/2).  
- **Retrieval:** 6 chunk nhưng không present “mục chính sách” sạch → LLM kết luận sai.  
- **Nhãn:** PARSER, PROMPT_GENERATION.

### GQ-L02

- **Evidence:** Basic, Pro, Business.  
- **DB/Qdrant:** Có trong table chunks.  
- **Retrieval:** **0** — log pattern tương T02 (lock `sec_3__dup2` / exclude / 0 anchor).  
- **Nhãn:** TABLE_MARKDOWN + QUERY_ANALYZER + VECTOR_RETRIEVAL.

### GQ-T02

- **Evidence:** “Ưu tiên”.  
- **DB/Qdrant:** Có.  
- **Retrieval:** **0** — log: `Semantic results excluded (out of locked scope): 18`, `Locked scope: 0 chunks … sec_3__dup2`.  
- **Nhãn:** Giống L02.

### GQ-F03

- **Evidence:** 500.  
- **Retrieval:** Có table chunk.  
- **Answer:** Đúng 500; **Section** trong answer trỏ metadata “Bước 3”.  
- **Nhãn:** SOURCE_ATTRIBUTION, CHUNKING (metadata), phụ PROMPT_GENERATION.

### GQ-T01

- **Evidence:** 99000.  
- **Giống F03** — đúng số, attribution sai.

---

## 14. Kết luận nguyên nhân chính

1. **Parser (TXT):** regex section chỉ nhận dòng `\d+(\.\d+)*\s+…` — **không** nhận `## …`; **list đánh số** trong mục chính sách bị tách thành `document_sections` giả.  
2. **Chunking:** toàn bộ phần sau (quy trình + bảng) gắn vào cây `sec_3` với `section_title` sai; bảng kế thừa title “Bước 3”.  
3. **Retrieval:** `RagRetrievalService` lọc vector theo `lockedSectionIds` **trước** khi gom anchor; khi lock trỏ section **không có chunk** (`sec_1`) hoặc key lệch (`sec_3__dup2` vs chunk `sec_3`), mọi hit bị loại; sau guardrail **không** chạy lại vector search → **0 context**.  
4. **Generation/attribution:** Khi context có nhưng sai cấu trúc (L01) hoặc đúng bảng nhưng metadata section sai (F03/T01), LLM/hậu xử lý hiển thị nguồn sai.

**Tham chiếu source (trích):**

```31:36:Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/DocumentParserService.java
    private static final Pattern SECTION_HEADER_PATTERN =
        // NOTE:
        // - Chỉ match dạng "2.1 Tiêu đề" ở đầu dòng.
        // - Việc lọc false-positive (table rows, bullet/list, footer/header...) được xử lý thêm ở isLikelySectionHeader().
        // PDF thường thụt indent cho subheading (vd "    6.4 API Structure ...") nên phải cho phép leading spaces.
        Pattern.compile("(?m)^\\s{0,16}(\\d+(?:\\.\\d+)*)(?:\\.[ \\t]*|[ \\t]+)([^\\n]{3,160})$");
```

```155:165:Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/RagRetrievalService.java
            for (TextSegment seg : anchors) {
                String chunkSectionId = seg.metadata().getString("section_id");

                // When scope is locked, filter vector results to only chunks inside scope.
                // Semantic search is supplementary — it cannot override a heading match.
                if (!lockedSectionIds.isEmpty() && chunkSectionId != null
                        && !lockedSectionIds.contains(chunkSectionId)) {
                    excludedByScope++;
                    log.debug("[RAG] EXCLUDED (out-of-scope): sectionId='{}' not in lockedScope", chunkSectionId);
                    continue;
                }
```

```243:246:Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/RagRetrievalService.java
        if (expanded.isEmpty() && vectorDocumentIds.isEmpty() && anchorChunkIds.isEmpty()) {
            log.warn("[RAG] Qdrant returned 0 anchors and locked scope empty for widgetId={}", widgetId);
            return new RetrievalResult(List.of(), null);
        }
```

---

## 15. Có sửa runtime không?

**Không.**

---

## 16. Không có runtime diff

Không có thay đổi file Java/React/YAML trong task 21C.

---

## 17. File đã tạo / cập nhật

| Path | Hành động |
|------|-----------|
| `docs/eval/results/RAG_RETRIEVAL_DIAG_21C_20260514.md` | Tạo mới — diagnosis đầy đủ |
| `reports/refactor/CURSOR_REPORT_21C_RAG_RETRIEVAL_DIAGNOSIS.md` | Tạo mới — report task |

---

## 18. Diff docs/report

### 18.1 `docs/eval/results/RAG_RETRIEVAL_DIAG_21C_20260514.md` (file mới)

```diff
+ (new file) — toàn bộ nội dung diagnosis 21C: recompute 21B, SQL inventory, Qdrant, API rerun, matrix, đề xuất fix P0–P2.
```

### 18.2 `reports/refactor/CURSOR_REPORT_21C_RAG_RETRIEVAL_DIAGNOSIS.md` (file mới)

```diff
+ (new file) — report audit theo mục 1–20 yêu cầu task 21C.
```

---

## 19. Rủi ro còn lại

- Chưa scroll Qdrant trên **chính** vector `fcf9b169` (đã purge) — đã giảm rủi ro bằng document song sinh cùng 6 point.  
- Log Cohere rerank đầy đủ không trích toàn bộ — không ảnh hưởng kết luận chính (F04/T02 đã rỗng trước khi cần rerank pool).

---

## 20. Đề xuất prompt fix tiếp theo (cho task sau — chưa implement)

1. **P0 — Ingest/parser:** Nhận diện heading markdown `^#{1,6}\s` cho TXT; hoặc tách block “## 2. Chính sách” trước khi áp regex số; hoặc coi `1.`/`2.`/`3.` chỉ là list khi indent/blank line context — **giảm false section**.  
2. **P0 — Retrieval:** Khi heading lock chọn section mà `findByWidgetConfigIdAndSectionIdIn` trả 0 row: **re-run** embedding search **không** filter theo lock; hoặc map `sec_3__dup2` → chunk `section_id` thực tế; hoặc không lock khi `titleHits` đến từ false section (cần heuristic).  
3. **P1 — Attribution:** Trong `PromptBuilderService` / format context: ưu tiên `heading_path_text` hoặc dòng `##` gần chunk hơn `section_title` khi trả “Nguồn”.  
4. **P2 — Eval:** Cập nhật summary trong `RAG_EVAL_RUN_21B_20260514.md` hoặc note “verdict column is source of truth”.

---

## Kiểm tra sau task

| Kiểm tra | Kết quả |
|----------|---------|
| `docs/eval/results/RAG_RETRIEVAL_DIAG_21C_20260514.md` tồn tại, không rỗng | **OK** |
| `reports/refactor/CURSOR_REPORT_21C_RAG_RETRIEVAL_DIAGNOSIS.md` tồn tại, không rỗng | **OK** |
| `cd Backend && ./mvnw -DskipTests compile` | **NOT RUN** (không sửa code) |
| `cd Backend && ./mvnw test` | **NOT RUN** |
| `cd Frontend && npm run lint` | **NOT RUN** |
| `cd Frontend && npm run build` | **NOT RUN** |
| `cd Frontend && npm run build:widget` | **NOT RUN** |
| `docker compose config` | **NOT RUN** |

---

*Kết thúc report 21C.*
