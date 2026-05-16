# Fix loop — 21D1 TXT markdown ATX section parser

**Phiên bản:** điền từ [RAG_BASELINE_CORE_FIX_LOOP_TEMPLATE.md](../RAG_BASELINE_CORE_FIX_LOOP_TEMPLATE.md)  
**Ngày:** 2026-05-14

---

## 1. Tên fix

`21D1-txt-markdown-atx-section-parser`

---

## 2. Mục tiêu fix (một dòng)

TXT: nhận heading markdown `#`…`######` làm ranh giới section khi document có ≥1 dòng ATX hợp lệ; không tách numbered list `1.`… thành section root trong golden; giữ behavior numeric cho TXT/PDF không có markdown.

---

## 3. Checklist item liên quan (ID từ RAG_BASELINE_CORE_CHECKLIST.md)

| Checklist ID | Gate | Ghi chú |
|----------------|------|---------|
| G0-CMP-001 | G0 | Compile backend |
| G0-DCK-001 | G0 | docker compose config |
| G0-SCP-001 | G0 | Scope một chủ đích |
| G1-UPL-001 … G1-CNT-001 | G1 | Metadata sections/chunks/tables/Qdrant — xem file kết quả run |
| G3-F04-001 … G3-T01-001 | G3 | Targeted chat — NOT_RUN (không Docker chat trong phiên) |

---

## 4. Files dự kiến đọc (trước khi sửa)

| Path | Mục đích |
|------|----------|
| `docs/eval/RAG_BASELINE_CORE_REBUILD_PLAN.md` | Gate P0 |
| `docs/eval/results/RAG_RETRIEVAL_DIAG_21C_20260514.md` | Root cause |
| `reports/refactor/CURSOR_REPORT_21C_RAG_RETRIEVAL_DIAGNOSIS.md` | Matrix case |
| `docs/eval/RAG_GOLDEN_TEST_DOCUMENT.txt` | Nội dung ingest |
| `DocumentParserService.java` | Parser + cleanText + parseSections |

---

## 5. Files được phép sửa (scope PR này)

| Path | Lớp |
|------|-----|
| `Backend/.../DocumentParserService.java` | parser / ingest |
| `Backend/.../ParserAndOrderingTests.java` | test |

---

## 6. Files không được sửa

RagRetrievalService, QueryAnalyzerService, PromptBuilderService, ChatService, Frontend, docker-compose, `.gitignore`, ChunkingService2 (không cần sau khi section đúng).

---

## 7. Pre-test snapshot

| Mục | Giá trị |
|-----|---------|
| Branch | (local working tree) |
| Baseline 21B verdict | F04/L01/L02/T02 FAIL; F03/T01 PARTIAL (theo 21C recompute) |
| Root cause 21C | Parser + cleanText newline trước `##` |

---

## 8. Diff summary

- Thêm `MARKDOWN_ATX_HEADER_LINE_PATTERN`, `detectMarkdownHeadingMode`, nhánh matcher trong `parseSections`.
- `cleanText`: giữ newline trước dòng ATX; double newline sau heading ATX; negative lookahead `(?!\\s{0,16}#{1,6}\\s)` khi gộp newline→space.
- Test golden: `parse()` + `ChunkingService2.processSections2` assert Bước 2 + table gắn đúng header.

---

## 9. Post-test result

| Gate | IDs | Kết quả |
|------|-----|---------|
| G0 | CMP, DCK, SCP | PASS |
| G1 | UPL…CNT | **PARTIAL** — logic PASS qua unit test + chunk header; **không** có SQL/Qdrant runtime |
| G3 | F04…T01 | **NOT_RUN** — không gọi `/api/chat` |

---

## 10. Case improved (dự kiến sau re-ingest runtime)

| case_id | Ghi chú |
|---------|---------|
| GQ-F04 | Metadata section đúng → retrieval có cơ hội lấy Bước 2 (task 21E nếu vẫn fail) |
| GQ-L01 / L02 / T02 | Cùng lý do ingest |
| GQ-F03 / T01 | Attribution section_title đúng hơn nếu chunk header đúng |

---

## 11. Case regressed

Không phát hiện trong `ParserAndOrderingTests` (12 test cũ + 1 golden).

---

## 12. Quyết định

- [x] **PARTIAL** — compile + test PASS; bằng chứng G1 mạnh qua unit/chunk; **chưa** re-ingest Docker + Qdrant + targeted chat.

**Ghi chú:** Chạy full G1 SQL + G3 sau khi có stack runtime.

---

## 13. Prompt / task tiếp theo

1. **21E (runtime):** Re-upload `RAG_GOLDEN_TEST_DOCUMENT.txt` lên chatbot sạch → SQL/Qdrant verify → chạy G3-F04/L01/L02/T02/F03/T01.  
2. Nếu G3 vẫn no-context: mở task retrieval (`RagRetrievalService` guardrail/re-search) theo 21C.
