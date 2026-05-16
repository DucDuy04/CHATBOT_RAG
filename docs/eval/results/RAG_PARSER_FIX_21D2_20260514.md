# RAG parser fix 21D2 — ATX section 7 (`## 7. Phạm vi…`)

**Ngày:** 2026-05-14  
**Task:** G1 metadata — thiếu `document_sections` cho mục 7 sau 21E.

---

## 1. Mục tiêu fix

- Chấp nhận heading ATX `## 7. Phạm vi KHÔNG có trong tài liệu …` làm section riêng.
- Giữ `#` tiêu đề đầu file trong intro **General** (không tách nhầm thành section tên dài).
- Không regress: list `1./2./3.` chính sách & quy trình, bảng, Bước 2 → Quy trình.

---

## 2. Before (21E runtime)

- `document_sections`: 7 hàng có tiêu đề mục 1–6 + General; **không** có mục 7.
- Chunk chứa `USD/VND` gắn `section_title` mục 6.
- `chunkCount` / Qdrant: 9.

---

## 3. Root cause (từ source)

`isLikelySectionHeaderSkipReason`: heuristic `footer-header-artifact` match `tài liệu` / `tai lieu` trong `afterNumber`.

Tiêu đề mục 7 sau khi bỏ prefix số còn **"Phạm vi KHÔNG có trong tài liệu …"** → `lower` chứa `\btài liệu\b` → **skip** như footer → không tạo section 7; nội dung gộp vào section 6.

`## 7` vẫn còn trên dòng sau `cleanText`; regex ATX **có match**; bị loại ở **Bước 1** `headingSkipReason` → `false-positive:footer-header-artifact`.

**Không** phải flush cuối file, không phải duplicate key, không phải table masking.

---

## 4. Code change summary

- `headingSkipReason` / `isLikelySectionHeaderSkipReason`: thêm tham số `markdownHeadingMode`.
- Tách footer **mạnh** (`trang|page|nội bộ|…`) vs token **tài liệu**:
  - **Markdown ATX** + dòng match outline số `^\d+(?:\\.\\d+)*\\.?\\s+.+` → **không** skip vì `tài liệu` (mục 7).
  - Markdown ATX + dòng **không** outline số nhưng có `tài liệu` (vd tiêu đề sau `#` đầu file) → vẫn skip → giữ **General** cho intro.

---

## 5. Compile / test

| Command | Kết quả |
|---------|---------|
| `.\mvnw.cmd -DskipTests compile` | **PASS** |
| `.\mvnw.cmd -q -Dtest=ParserAndOrderingTests test` | **PASS** |

Golden test log: `markdownAtx=true totalCandidates=8 accepted=7 skipped=1` (1 = `#` tiêu đề), `Sections created: 8`, chunk distribution có `sec_7`, **10 chunks**.

---

## 6. Runtime re-ingest

| Giai đoạn | Kết quả |
|-----------|---------|
| `docker compose up --build -d backend` | **PASS** (đã chạy trong phiên) |
| Upload + SQL sau build đầu (code trung gian bỏ hết `tài liệu` trong footer) | `order_index=0` = tiêu đề H1 dài — **lệch** so checklist "General" |
| Upload sau **bản cuối** (heuristic tài liệu có điều kiện) | Chưa ghi nhận curl ổn định trong log (API sau recreate); **bằng chứng chính: unit golden + compile** |

**Kỳ vọn sau reupload bản cuối:** 8 `document_sections` (General + 1…7), `chunkCount=10`, Qdrant count=10, chunk `USD/VND` → section 7.

---

## 7. Inventory sau fix (in-memory / log test)

- Sections: **8** — `General` + `1.` … `7. Phạm vi KHÔNG có trong tài liệu …`
- Chunk mục 7: `text_table_like` hoặc text gắn header chứa **Phạm vi** + **KHÔNG có trong tài liệu**.

---

## 8. Regression metadata (test)

- Không section giả từ list chính sách / bước.
- Bước 2 → Quy trình.
- Table → Bảng gói dịch vụ.
- `sections.get(0).header()` = **General**.

---

## 9. Verdict

| Gate | Verdict |
|------|---------|
| Parser + chunking (unit) | **PASS** |
| Runtime Docker SQL/Qdrant bản cuối | **PARTIAL** (rebuild OK; bằng chứng SQL đầy đủ nên operator chạy lại upload sau deploy) |

---

*Báo cáo chi tiết + diff đầy đủ: `reports/refactor/CURSOR_REPORT_21D2_ATX_LAST_SECTION_PARSER_FIX.md`.*
