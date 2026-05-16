# RAG parser fix — 21D1 TXT markdown ATX sections

**Ngày:** 2026-05-14  
**Task:** P0 parser theo [RAG_BASELINE_CORE_REBUILD_PLAN.md](../RAG_BASELINE_CORE_REBUILD_PLAN.md)

---

## 1. Mục tiêu fix

- Nhận `#` … `######` + khoảng trắng + title làm ranh giới section cho TXT có markdown.
- Không nâng list `1.`, `2.`, `3.` (chính sách / quy trình) thành section root khi document đã ở chế độ markdown.
- Giữ regex heading số cho TXT/PDF **không** có dòng ATX.

---

## 2. Checklist item liên quan (trạng thái phiên 21D1)

| ID | Status | Evidence ngắn |
|----|--------|----------------|
| G0-CMP-001 | **PASS** | `.\mvnw.cmd -DskipTests compile` BUILD SUCCESS |
| G0-DCK-001 | **PASS** | `docker compose config -q` exit 0 |
| G0-SCP-001 | **PASS** | Chỉ `DocumentParserService` + test parser |
| G1-UPL-001 | **NOT_RUN** | Không upload API trong phiên |
| G1-STS-001 | **NOT_RUN** |  |
| G1-MDH-001 | **PASS*** | `parse()` golden: có section header chứa "Chính sách", "Quy trình", "Bảng gói" từ tiêu đề sau `##` |
| G1-SEC-001 | **PASS*** | Không có header section bắt đầu `1. Phản hồi yêu cầu trong` |
| G1-SEC-002 | **PASS*** | Không có header `2. Mỗi phiên chat hỗ trợ` |
| G1-SEC-003 | **PASS*** | Không có header `3. Không hỗ trợ can thiệp` (list) |
| G1-SEC-004 | **PASS*** | Section chứa "Quy trình" tồn tại |
| G1-SEC-005 | **PASS*** | Section chứa "Bảng gói dịch vụ" tồn tại |
| G1-CHK-001 | **PASS*** | Chunk text chứa "Bước 2" có `header` chứa "Quy trình" |
| G1-CHK-002 | **PASS*** | (implicit) Bước 1/3 cùng section quy trình trong content |
| G1-CHK-003 | **PASS*** | Section chính sách chứa 24h + 15 phút |
| G1-TBL-001 | **PASS*** | Section bảng chứa markdown table |
| G1-TBL-002 | **PASS*** | `table_*` chunk `header` chứa "Bảng gói dịch vụ" |
| G1-TBL-003 | **PASS*** | Không gắn table vào title list giả (không còn pattern 21C) |
| G1-QDR-001 | **NOT_RUN** | Không gọi Qdrant |
| G1-QDR-002 | **NOT_RUN** |  |
| G1-QDR-003 | **NOT_RUN** |  |
| G1-CNT-001 | **PASS*** | Golden: 7 sections sau parse (log test `markdownAtx=true`, 7 created) |
| G3-F04-001 … G3-T01-001 | **NOT_RUN** | Không `POST /api/chat` |

\*Bằng chứng từ unit test `markdown_golden_txt_sections_follow_atx_headings_not_numbered_lists` (parse + chunk in-memory), không thay thế SQL/Qdrant production.

---

## 3. Before summary (21C)

- Regex heading: `^\s{0,16}(\d+(?:\.\d+)*)(?:\.\s*|\s+)(...)$` — **không** match `##`.
- `cleanText` gộp `\n` trước `#` thành space → `##` dính dòng trước.
- List `1.` trong chính sách → section root giả `sec_1`…; chunk Bước 2 gắn sai; table gắn sai.

---

## 4. Code change summary

- `MARKDOWN_ATX_HEADER_LINE_PATTERN`: `(?m)^\s{0,16}#{1,6}\s+(.+)$` — group 1 = title hiển thị (`2. Chính sách…`).
- `detectMarkdownHeadingMode`: bật khi bất kỳ trang nào (sau mask bảng) có match pattern trên.
- `parseSections`: nếu markdown mode → chỉ dùng ATX matcher; `candidateHeader` = group(1) trimmed.
- `cleanText`: thêm double newline sau dòng ATX; `replaceAll` newline→space thêm negative lookahead `(?!\s{0,16}#{1,6}\s)`.

---

## 5. Compile result

**PASS** — `cd Backend && .\mvnw.cmd -DskipTests compile`

---

## 6. Re-ingest result (runtime)

**NOT RUN** — không có upload Docker trong phiên agent.

---

## 7. Section inventory sau fix (golden TXT, unit test)

- Log test: `markdownAtx=true`, `totalCandidates=8 accepted=6 skipped=2`, `Sections created: 7`.
- Headers gồm: intro `#` title line (tiêu đề sau `#`), `## 1. Thông tin chung`, `## 2. Chính sách…`, `## 3. Quy trình…`, `## 4. Bảng gói…`, và các mục `##` tiếp theo (5,6,7) — không tách list `1.` chính sách.

---

## 8. Chunk metadata sau fix (ChunkingService2 in-memory)

- Text chunk chứa `Bước 2`: `header` chứa **Quy trình**.
- Ít nhất một chunk `table_summary` hoặc `table_row_group`: `header` chứa **Bảng gói dịch vụ**.

---

## 9. Qdrant payload sau fix

**NOT RUN**

---

## 10. Targeted question result (API)

**NOT RUN**

---

## 11. Case improved (expected sau re-ingest)

- GQ-F04, L01, L02, T02: có cơ sở metadata đúng (21C).
- GQ-F03, T01: attribution section_title đúng hơn nếu payload theo chunk header.

---

## 12. Case still failed

Chưa đo lại runtime — **unknown** cho đến khi chạy 21E.

---

## 13. Quyết định (fix-loop)

**PARTIAL** — đạt acceptance kỹ thuật code + unit/chunk; thiếu verify runtime G1-QDR + G3.

---

## 14. Lệnh đã chạy

| Command | Kết quả |
|---------|---------|
| `.\mvnw.cmd -DskipTests compile` | PASS |
| `.\mvnw.cmd -Dtest=ParserAndOrderingTests test` | PASS (13 tests) |
| `docker compose config -q` | PASS |

---

*File companion: [FIX_LOOP_21D1_TXT_MARKDOWN_SECTION_PARSER.md](FIX_LOOP_21D1_TXT_MARKDOWN_SECTION_PARSER.md)*
