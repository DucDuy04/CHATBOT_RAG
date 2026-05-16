# Cursor Report 21D1 — TXT markdown ATX section parser fix

**Ngày:** 2026-05-14  
**Scope:** `DocumentParserService` (TXT + `parseSections` dùng chung PDF path) + unit test golden; không retrieval/prompt/ChatService/FE/migration/`.gitignore`.

---

## 1. Mức độ hiểu task

| Mục | Nội dung |
|-----|----------|
| **Hiểu task** | **98%** |
| **Chắc chắn** | Root cause 21C; regex hiện tại; cách tách markdown mode; `cleanText` làm mất newline trước `##`. |
| **Giả định** | PDF không có chuỗi `^#{1,6}\s` thường xuyên — rủi ro thấp. |
| **Thiếu dữ kiện** | Không chạy re-ingest + Qdrant + `/api/chat` trong phiên agent. |

---

## 2. Tóm tắt yêu cầu

Sửa parser TXT: heading ATX `#`…`######`; tránh list số thành section root khi doc có markdown; giữ numeric mode khi không có ATX; không đụng retrieval/prompt.

---

## 3. Phạm vi đã làm

- Sửa `DocumentParserService.java` (pattern ATX, `detectMarkdownHeadingMode`, nhánh `parseSections`, `cleanText`).
- Thêm test `markdown_golden_txt_sections_follow_atx_headings_not_numbered_lists`.
- Tạo `docs/eval/results/RAG_PARSER_FIX_21D1_20260514.md`, `FIX_LOOP_21D1_TXT_MARKDOWN_SECTION_PARSER.md`, report này.

---

## 4. Phạm vi không làm

Không sửa `RagRetrievalService`, `QueryAnalyzerService`, `PromptBuilderService`, `ChatService`, `ChunkingService2`, frontend, Docker (ngoài `compose config`), không backfill.

---

## 5. Checklist item liên quan

Xem bảng trong [docs/eval/results/RAG_PARSER_FIX_21D1_20260514.md](../../docs/eval/results/RAG_PARSER_FIX_21D1_20260514.md).

---

## 6. Root cause từ 21C

1. `SECTION_HEADER_PATTERN` chỉ bắt dòng số `1.` … — không bắt `## 2. Chính sách`.  
2. List `1.` trong mục markdown bị coi là section.  
3. `cleanText` gộp newline trước `#` → heading `##` không còn đầu dòng → regex khó khớp.  
4. Hậu quả chunk/table metadata sai → heading lock / 0 context (phần retrieval ngoài scope 21D1).

---

## 7. Phân tích parser hiện tại (8 câu bắt buộc)

1. **TXT detect heading bằng regex nào?** — `SECTION_HEADER_PATTERN`: `(?m)^\s{0,16}(\d+(?:\.\d+)*)(?:\.\s*|\s+)([^\n]{3,160})$`.  
2. **Có bắt `## ...` không?** — **Không** (trước fix).  
3. **Vì sao list `1.` thành section?** — Pattern khớp đầu dòng số + title đủ dài; list chính sách đúng format.  
4. **Logic có áp dụng PDF không?** — `parseSections` **chung** PDF và TXT; PDF không bật markdown mode nếu text extract không có dòng `^#{1,6}\s+`.  
5. **Tách TXT markdown riêng?** — Heuristic: chỉ đổi matcher khi `detectMarkdownHeadingMode` true (ít nhất một dòng ATX sau mask bảng).  
6. **Có cần sửa ChunkingService2?** — **Không** trong phiên này; section tree đúng thì chunker hiện tại gắn đúng (đã verify test).  
7. **Rủi ro TXT không có markdown heading?** — `markdownHeadingMode=false` → giữ hành vi cũ với `SECTION_HEADER_PATTERN`.  
8. **Hướng sửa minimal?** — Thêm pattern ATX + detect + chỉnh `cleanText` newline trước `#`.

---

## 8. Chiến lược sửa minimal đã chọn

- Không thêm dependency markdown.  
- Một pass scan pages cho mode + một matcher trong loop hiện có.  
- `cleanText` mở rộng negative lookahead (ảnh hưởng PDF nhỏ).

---

## 9. Vì sao không sửa retrieval/prompt trong task này

Theo plan 21D0: ưu tiên P0 parser/G1 trước; 21C đã chỉ retrieval là lớp sau khi metadata đúng.

---

## 10. Danh sách file đã đọc

| Path | Kết luận ngắn |
|------|----------------|
| `docs/eval/RAG_BASELINE_CORE_*` | Gate & template |
| `docs/eval/results/RAG_RETRIEVAL_DIAG_21C_20260514.md` | Matrix |
| `reports/refactor/CURSOR_REPORT_21C_*.md` | Chi tiết |
| `docs/eval/RAG_GOLDEN_TEST_DOCUMENT.txt` | Cấu trúc `##` + list |
| `DocumentParserService.java` | Điểm sửa |
| `ParserAndOrderingTests.java` | Thêm test |
| `DocumentChunk.java` | Field `header` cho assert |

---

## 11. Danh sách file đã sửa

| Path | Lớp |
|------|-----|
| `Backend/src/main/java/.../DocumentParserService.java` | parser |
| `Backend/src/test/java/.../ParserAndOrderingTests.java` | test |
| `docs/eval/results/RAG_PARSER_FIX_21D1_20260514.md` | docs |
| `docs/eval/results/FIX_LOOP_21D1_TXT_MARKDOWN_SECTION_PARSER.md` | docs |
| `reports/refactor/CURSOR_REPORT_21D1_TXT_MARKDOWN_SECTION_PARSER_FIX.md` | report |

---

## 12. Diff thay đổi từng file

### 12.1 `DocumentParserService.java`

```diff
diff --git a/Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/DocumentParserService.java b/Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/DocumentParserService.java
index 6d50fba..822242b 100644
--- a/Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/DocumentParserService.java
+++ b/Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/DocumentParserService.java
@@ -35,6 +35,13 @@ public class DocumentParserService {
         Pattern.compile("(?m)^\\s{0,16}(\\d+(?:\\.\\d+)*)(?:\\.[ \\t]*|[ \\t]+)([^\\n]{3,160})$");
 
+    /**
+     * Markdown ATX: {@code #} … {@code ######}, khoảng trắng bắt buộc, rồi phần còn lại của dòng.
+     * Group 1 = tiêu đề hiển thị (không gồm prefix {@code #}), ví dụ {@code ## 2. Chính sách…} → {@code 2. Chính sách…}.
+     */
+    private static final Pattern MARKDOWN_ATX_HEADER_LINE_PATTERN =
+            Pattern.compile("(?m)^\\s{0,16}#{1,6}\\s+(.+)$");
+
     private static final Pattern SECTION_NUMBER_PATTERN =
         Pattern.compile("^(\\d+(?:\\.\\d+)*)[.\\s]");
     
@@ -212,6 +219,8 @@ public class DocumentParserService {
         List<Section> sections = new ArrayList<>();
         TreeMap<Integer, String> sortedPages = new TreeMap<>(pageContents);
 
+        boolean markdownHeadingMode = detectMarkdownHeadingMode(sortedPages);
+
         // Mặc định "General" cho phần intro trước heading đầu tiên
         String currentHeader = "General";
@@ -232,12 +241,19 @@ public class DocumentParserService {
             String maskedForHeadingScan = maskTableBlocks(pageText);
-            Matcher matcher = SECTION_HEADER_PATTERN.matcher(maskedForHeadingScan);
+            Matcher matcher = markdownHeadingMode
+                    ? MARKDOWN_ATX_HEADER_LINE_PATTERN.matcher(maskedForHeadingScan)
+                    : SECTION_HEADER_PATTERN.matcher(maskedForHeadingScan);
             int lastEndIndex = 0;
 
             while (matcher.find()) {
                 totalCandidates++;
-                String candidateHeader = matcher.group(0).trim();
+                String candidateHeader = markdownHeadingMode
+                        ? safeTrim(matcher.group(1))
+                        : matcher.group(0).trim();
+                if (markdownHeadingMode && candidateHeader.isEmpty()) {
+                    continue;
+                }
                 String candidateNumber = extractSectionNumber(candidateHeader);
@@ -304,8 +320,8 @@ public class DocumentParserService {
-        log.info("[Parse] Heading detection: totalCandidates={} accepted={} skipped={}",
-                totalCandidates, acceptedHeadings, totalCandidates - acceptedHeadings);
+        log.info("[Parse] Heading mode: markdownAtx={} totalCandidates={} accepted={} skipped={}",
+                markdownHeadingMode, totalCandidates, acceptedHeadings, totalCandidates - acceptedHeadings);
@@ -315,6 +331,28 @@ public class DocumentParserService {
         return sections;
     }
 
+    private static String safeTrim(String s) { ... }
+
+    private boolean detectMarkdownHeadingMode(TreeMap<Integer, String> sortedPages) { ... }
+
@@ -842,6 +880,11 @@ public class DocumentParserService {
+                .replaceAll(
+                        "(?m)^(\\s{0,16}#{1,6}\\s+[^\\n]+)\\n(?!\\n)",
+                        "$1\n\n"
+                );
@@ -849,9 +892,10 @@ public class DocumentParserService {
-                .replaceAll("(?<!\\n)\\n(?![\\n\\d\\[|])", " ")
+                .replaceAll("(?<!\\n)\\n(?![\\n\\d\\[|])(?!\\s{0,16}#{1,6}\\s)", " ")
```

### 12.2 `ParserAndOrderingTests.java`

Thêm test `markdown_golden_txt_sections_follow_atx_headings_not_numbered_lists` (MockMultipartFile + assert sections + chunk header). Xem `git diff` đầy đủ trên máy.

---

## 13. Ảnh hưởng sau sửa

| Vùng | Ảnh hưởng |
|------|-----------|
| TXT markdown | Tách section theo `##`; list nằm trong content section cha. |
| TXT không markdown | Không đổi matcher (không có dòng ATX → mode tắt). |
| PDF | `cleanText` dùng chung: nếu dòng bắt đầu `#`… hiếm, newline trước đó được giữ thêm; markdown mode chỉ bật nếu có ATX line. |
| RAM/CPU | Thêm một scan O(n) page + cùng complexity matcher; không thêm vòng lặp lớn. |
| DB/Qdrant | Chỉ khi **reupload** document; không migration/backfill. |

---

## 14. Edge cases đã xem xét

- Doc có `#` comment-style nhưng không phải heading — nếu đúng format `^#{1,6}\s+` vẫn tách (rủi ro chấp nhận theo spec heuristic).  
- Bảng `[TABLE_START]` mask trước khi detect mode — `#` trong bảng không bật mode sai.  
- `headingSkipReason` / `extractSectionNumber` tái dùng trên title đã strip `##`.

---

## 15. Kết quả compile/test

| Command | Kết quả |
|---------|---------|
| `.\mvnw.cmd -DskipTests compile` | **PASS** |
| `.\mvnw.cmd -Dtest=ParserAndOrderingTests test` | **PASS** (13 tests, 0 fail) |
| `docker compose config -q` | **PASS** |

---

## 16. Runtime verification (upload / SQL / Qdrant)

**NOT RUN** — không có stack + API chat trong phiên.

---

## 17. Kết quả targeted cases (G3)

**NOT RUN**

---

## 18. Checklist status summary

- **G0:** CMP PASS, DCK PASS, SCP PASS.  
- **G1:** Logic/chunk PASS qua unit test; UPL/STS/QDR **NOT_RUN**.  
- **G3:** **NOT_RUN**

---

## 19. Case improved / regressed

- **Improved (dự kiến sau re-ingest):** F04, L01, L02, T02, F03, T01.  
- **Regressed:** không phát hiện trong test suite đã chạy.

---

## 20. Có sửa runtime không?

**Không** — chỉ thay đổi source + docs; cần **deploy/reupload** để DB/Qdrant đổi trên môi trường thật.

---

## 21. Rủi ro còn lại

- TXT có ký tự `#` đầu dòng nhưng không phải heading ATX chuẩn có thể bật mode hoặc tách sai (hiếm).  
- G3 vẫn có thể cần task **21E retrieval** nếu sau ingest vẫn no-context.

---

## 22. Đề xuất prompt tiếp theo

1. Chạy **21E runtime:** upload golden mới → SQL `document_sections`/`document_chunks`/`document_tables` → Qdrant scroll → G3 chat.  
2. Nếu PASS G1 nhưng G3 fail: prompt fix `RagRetrievalService` (guardrail re-search / lock section có chunk).

---

*Kết quả chi tiết: [docs/eval/results/RAG_PARSER_FIX_21D1_20260514.md](../../docs/eval/results/RAG_PARSER_FIX_21D1_20260514.md)*
