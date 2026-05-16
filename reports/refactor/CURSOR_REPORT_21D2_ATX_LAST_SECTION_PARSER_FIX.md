# CURSOR_REPORT_21D2 — ATX section 7 / footer heuristic

## 1. Mức độ hiểu task

- **~95%** — đủ để sửa parser, bổ sung test, ghi nhận runtime.
- **Chắc chắn:** nguyên nhân skip từ `isLikelySectionHeaderSkipReason` + token `tài liệu`; không cần sửa `ChunkingService2`.
- **Giả định:** runtime SQL đầy đủ sau image cuối do thời điểm API sau recreate — bằng chứng chính là unit golden + log parse.

## 2. Tóm tắt yêu cầu

Sửa P0 parser để `## 7. Phạm vi KHÔNG có trong tài liệu` có `document_sections` riêng và chunk/Qdrant gắn đúng; không regress 21D1/21E; không đụng retrieval/prompt/ChatService.

## 3. Phạm vi đã làm

- Điều chỉnh heuristic `footer-header-artifact` trong `DocumentParserService` (có điều kiện theo `markdownHeadingMode` + outline số).
- Truyền `markdownHeadingMode` vào `headingSkipReason` / `isLikelySectionHeaderSkipReason`.
- Mở rộng `ParserAndOrderingTests` (golden): General, mục 7, chunk USD/VND, regression Bước 2 / bảng.
- Tài liệu: `RAG_PARSER_FIX_21D2_20260514.md`, `FIX_LOOP_21D2_ATX_LAST_SECTION.md`, báo cáo này, `docs/CURSOR_REPORT_21D2_PARSER_SECTION7.md`.
- `docker compose up --build -d backend` đã chạy trong phiên (image chứa fix).

## 4. Phạm vi không làm

- Không sửa `ChunkingService2`, retrieval, prompt, `ChatService`, frontend, Docker schema, `.gitignore`, migration, dependency.

## 5. Root cause từ 21E

`document_sections` thiếu mục 7 vì candidate `7. Phạm vi KHÔNG có trong tài liệu …` sau `extractSectionNumber` còn phần text chứa **tài liệu** → regex `footer-header-artifact` (cũ gồm `tai lieu|tài liệu`) → `headingSkipReason` trả `false-positive:footer-header-artifact` → heading bị **continue** bỏ qua.

## 6. Phân tích parser/chunking trước khi sửa (trả lời brief §4)

1. **Vì sao 1–6 OK mà 7 không?** — Chỉ mục 7 có cụm “trong **tài liệu**” trong tiêu đề outline sau số `7.`.
2. **`## 7` sau cleanText?** — Có; ATX vẫn match.
3. **Regex ATX match?** — Có (`totalCandidates` gồm dòng đó).
4. **Bị skip ở đâu?** — `isLikelySectionHeaderSkipReason` → `footer-header-artifact` (không phải `isLikelySectionHeader` tên khác — cùng một hàm skip).
5. **Flush cuối file?** — Không phải nguyên nhân; section 6 đã flush, mục 7 không được mở.
6. **Gộp vào section trước?** — Hệ quả của skip (nội dung sau `## 7` dính content section 6).
7. **Parser hay Chunking?** — **Parser** (thiếu section đầu vào chunking).
8. **Sửa minimal?** — Thu hẹp heuristic `tài liệu` trong chế độ ATX khi dòng là outline số.
9. **PDF / TXT không markdown?** — `markdownHeadingMode=false` → nhánh `tài liệu` giữ skip như trước (trừ khi đồng thời outline số — hiếm).
10. **chunkCount?** — Golden: 9 → **10** (+1 section out-of-scope tách riêng); chấp nhận được.

## 7. Chiến lược sửa minimal đã chọn

**Option A (biến thể):** tin cậy outline số `N.` trong **markdown ATX mode** để không áp dụng skip `tài liệu` thuần; vẫn skip dòng có `tài liệu` **không** outline (vd tiêu đề sau `#` đầu file) → giữ **General**.

## 8. Vì sao không sửa retrieval/prompt

Lỗi là **metadata ingest** (thiếu section entity); retrieval/prompt không sửa được thiếu row DB.

## 9. Danh sách file đã đọc

| Path | Mục đích | Kết luận |
|------|----------|----------|
| `docs/eval/results/RAG_RUNTIME_VERIFY_21E_20260514.md` | Symptom | Thiếu mục 7 |
| `DocumentParserService.java` | Skip heading | Root cause tại footer heuristic |
| `RAG_GOLDEN_TEST_DOCUMENT.txt` | Tiêu đề mục 7 | Chứa “trong tài liệu” |
| `ParserAndOrderingTests.java` | Mở rộng test | Golden coverage |

## 10. Danh sách file đã sửa

| Path | Mục đích | Lớp |
|------|----------|-----|
| `Backend/.../DocumentParserService.java` | Heuristic footer + chữ ký `headingSkipReason` | parser |
| `Backend/.../ParserAndOrderingTests.java` | Assert General, mục 7, USD chunk | test |

## 11. Diff từng file

### 11.1 `DocumentParserService.java`

- **Hiện trạng cũ:** một regex gom `tài liệu` với footer → skip cả `7. Phạm vi … trong tài liệu`.
- **Đã sửa:** tách `strongFooter` vs `taiLieuToken`; nếu `markdownHeadingMode && numberedOutline` thì **không** skip vì `taiLieuToken`; ngược lại vẫn `footer-header-artifact`. `headingSkipReason` nhận thêm `markdownHeadingMode` và truyền xuống `isLikelySectionHeaderSkipReason`.

```diff
@@ parseSections (while matcher.find)
-                String skipReason = headingSkipReason(
+                String skipReason = headingSkipReason(
                         candidateHeader, candidateNumber,
                         currentHeader, currentSectionNumber,
-                        matcher.start(), seenSectionNumberCounts);
+                        matcher.start(), seenSectionNumberCounts,
+                        markdownHeadingMode);

@@ headingSkipReason
-            java.util.Map<String, Integer> seenSectionNumberCounts) {
+            java.util.Map<String, Integer> seenSectionNumberCounts,
+            boolean markdownHeadingMode) {
-        String fpReason = isLikelySectionHeaderSkipReason(candidateHeader);
+        String fpReason = isLikelySectionHeaderSkipReason(candidateHeader, markdownHeadingMode);

@@ isLikelySectionHeaderSkipReason
-    private String isLikelySectionHeaderSkipReason(String headerLine) {
+    private String isLikelySectionHeaderSkipReason(String headerLine, boolean markdownHeadingMode) {
@@ footer block
-        if (lower.matches(".*\\b(trang|page|tai lieu|tài liệu|noi bo|... )\\b.*")) {
-            return "footer-header-artifact";
-        }
+        boolean strongFooter = lower.matches(
+                ".*\\b(trang|page|noi bo|nội bộ|khong phat hanh|không phát hành)\\b.*");
+        boolean taiLieuToken = lower.matches(".*\\b(tai lieu|tài liệu)\\b.*");
+        boolean numberedOutline = h.matches("^\\d+(?:\\.\\d+)*\\.?\\s+.+");
+        if (strongFooter) {
+            return "footer-header-artifact";
+        }
+        if (taiLieuToken) {
+            if (markdownHeadingMode && numberedOutline) {
+                return null;
+            }
+            return "footer-header-artifact";
+        }
```

### 11.2 `ParserAndOrderingTests.java`

- Thêm assert `sections.get(0).header()` = `General`.
- Thêm assert tồn tại header mục 7 và chunk chứa `USD/VND` gắn header Phạm vi.

```diff
+        assertThat(sections.get(0).header())
+                .isEqualTo("General");
+        assertThat(sections.stream().map(Section::header))
+                .anyMatch(h -> h.contains("Phạm vi") && h.contains("KHÔNG có trong tài liệu"));
+        ...
+        DocumentChunk outOfScopeChunk = chunks.stream()
+                .filter(c -> c.content().contains("USD/VND"))
```

## 12. Ảnh hưởng sau sửa

| Khu vực | Thay đổi |
|---------|----------|
| TXT markdown ATX | Có thêm section mục 7; `chunkCount` golden +1 (10). |
| TXT không markdown | Hầu như giữ nguyên (`markdownHeadingMode=false`). |
| PDF | Không đổi pipeline ATX; heuristic footer mạnh giữ nguyên. |
| RAM/CPU | Không thêm vòng O(n) mới; chỉ vài boolean trên mỗi candidate heading. |
| DB/Qdrant | Chỉ sau **reupload** mới có row/point mới; không backfill tự động. |

## 13. Edge cases đã xem xét

- `#` tiêu đề có chữ “tài liệu” không có số outline → vẫn skip → **General** (mong muốn).
- `→` / `->` trong tiêu đề (vd “out-of-scope” chứa `->`?) — golden dùng dấu gạch đơn; không đụng `flow-arrow`.
- Bảng markdown: vẫn `maskTableBlocks` trước scan heading.

## 14. Compile / test

| Command | Kết quả |
|---------|---------|
| `.\mvnw.cmd -DskipTests compile` | **PASS** |
| `.\mvnw.cmd -q -Dtest=ParserAndOrderingTests test` | **PASS** (phiên verify: log golden `Sections created: 8`, `totalChunks=10`) |

## 15. Runtime verification

- **Docker build backend:** đã chạy `docker compose up --build -d backend` thành công.
- **Upload trung gian (image chứa bản chỉ bỏ hết token tài liệu khỏi regex):** SQL có 8 section nhưng `order_index=0` là tiêu đề H1 dài — **lệch** checklist “General”; đã **điều chỉnh tiếp** thành logic có điều kiện (mục §7).
- **Upload sau bản cuối:** trong phiên có lúc API sau recreate chưa ổn định; **không claim** đã có dump SQL/Qdrant đầy đủ cho image cuối — operator chạy lại upload golden và SQL như 21E.

## 16. Checklist

- **G0:** compile PASS; Docker build PASS.
- **G1 section 7:** unit PASS (có header mục 7 + chunk USD đúng section).
- **G1 regression:** false list, Bước 2, bảng — unit PASS.

## 17. Có sửa runtime source không?

**Có** — chỉ `DocumentParserService.java` + test.

## 18. Case improved / regressed

- **Improved:** thiếu section 7 (21E) → có section 7 + metadata chunk đúng (unit).
- **Regressed:** không phát hiện trong test; tránh đổi `order_index=0` khỏi General nhờ nhánh `taiLieuToken` + không outline.

## 19. Rủi ro còn lại

- Footer thật dạng “Tài liệu …” **có** prefix số outline giả → hiếm, có thể ACCEPT nhầm (đánh đổi đã chọn).
- Cần **reupload** tài liệu cũ để DB/Qdrant khớp logic mới.

## 20. Đề xuất tiếp theo

- Chạy lại runtime 21E-style sau deploy: SQL 8 sections + Qdrant 10 points + (tuỳ chọn) GQ-O01/O02.
- Task riêng nếu cần: GQ-T01 generation (ngoài parser).

---

## Resource / production (brief)

- **Loop / file lớn:** không tăng độ phức tạp đáng kể.
- **Backfill:** không; user **reupload** / xóa doc cũ nếu cần metadata mới.
- **Production yếu:** +1 chunk/doc nhỏ — chấp nhận được.
