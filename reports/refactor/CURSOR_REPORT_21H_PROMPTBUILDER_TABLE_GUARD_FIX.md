# CURSOR_REPORT_21H — PromptBuilder table guard (TABLE_LOOKUP)

## 1. Mức độ hiểu task

- **~95%** — rõ scope: chỉ `PromptBuilderService` (+ test nhẹ), không đụng QueryAnalyzer/retrieval/chat/frontend.
- **Chắc chắn:** triệu chứng 21G3 (T01 v4 deny dù có bảng); chỗ sửa là instruction `TABLE_LOOKUP` + có thể nhắn khi context có chunk bảng.
- **Giả định:** LLM tuân instruction tốt hơn sau khi explicit cấm deny khi có ô/hàng/cột; cần **runtime** để xác nhận T01 v4.
- **Thiếu dữ kiện:** không chạy Docker/Groq trong phiên này → T01 v4 runtime **chưa** verify.

## 2. Tóm tắt yêu cầu

Task **21H**: sửa nhẹ `PromptBuilderService` để giảm contradiction khi context có bảng nhưng LLM vẫn “không tìm thấy”, đặc biệt câu dạng lựa chọn; không hardcode entity/giá golden; không sửa QueryAnalyzer/RagRetrieval/ChatService/frontend/dependency/migration/`.gitignore`.

## 3. Phạm vi đã làm

- Mở rộng instruction nhánh `TABLE_LOOKUP` trong `buildQueryTypeInstruction`.
- Khi `queryTypeHint` là `TABLE_LOOKUP` và danh sách `RetrievedContext` có chunk bảng hoặc content giống markdown table → thêm một dòng nhắn `TABLE_LOOKUP_TABLE_SOURCES_PRESENT_NOTE`.
- Thêm `PromptBuilderServiceTest` (không DB): kiểm tra prompt có guard lựa chọn / không literal golden / COUNT_QUERY không dính banner.

## 4. Phạm vi không làm

- `QueryAnalyzerService.java`, `RagRetrievalService.java`, `ChatService.java`, frontend, Docker compose, schema, dependency, migration/backfill, `.gitignore`, retrieval top-k/cap.

## 5. Root cause từ 21G3

- **Phân loại đã đúng** runtime: T01 gốc `TABLE_LOOKUP` + source có bảng → **PASS**.
- **T01 v4** (`… hay …?`): cùng kiểu tra cứu nhưng LLM vẫn deny → lỗi **generation/instruction**, không phải retrieval class sai trong mẫu 21G3 (đã có bảng trong response).
- Prompt cũ cho `TABLE_LOOKUP` nhấn “gộp + Markdown” nhưng **không** buộc: đọc ô khi có lựa chọn; **không** được dùng câu deny chuẩn khi đã có hàng/cột/ô trả lời — xung đột tiềm ẩn với system rule #2 (từ chối khi “không liên quan”).

## 6. Phân tích PromptBuilder trước khi sửa

1. **System prompt:** một `SYSTEM_PROMPT` tĩnh — nguyên tắc chỉ dùng `[TÀI LIỆU THAM KHẢO]`, rule từ chối 1 câu khi không có source liên quan, checklist section/bảng/đếm/liệt kê/citation.
2. **User prompt:** `[PHẠM VI…]` optional → `[LOẠI CÂU HỎI: X]` + instruction theo `X` → `[TÀI LIỆU THAM KHẢO]` với từng Source (Document, Section, Pages, **Type**, Content) → history → `[CÂU HỎI HIỆN TẠI]`.
3. **TABLE_LOOKUP cũ:** 5 bullet (filter theo tên bảng; tìm `table_summary`/`table_row_group`; gộp dòng; Markdown; không bỏ sót).
4. **Rule dễ deny:** system #2 + thiếu hướng dẫn xử lý **câu hai phương án** và **cấm deny khi evidence ô/hàng/cột đã có**.
5. **Table trong prompt:** `Type: table_row_group` (v.v.) + `Content` markdown.
6. **Guard tổng quát:** có — bổ sung trong nhánh `TABLE_LOOKUP` + nhắn có điều kiện từ `chunkType`/`content`.
7. **Vị trí guard:** chủ yếu **query-type instruction** (user prompt); không phình toàn cục system prompt.
8. **OOS bịa:** vẫn giữ “không đoán ngoài Source”; chỉ cấm deny khi **đã có** hàng/cột/ô liên quan.
9. **Retrieval/cap:** không sửa (theo task).
10. **Minimal:** chỉnh string + ~40 dòng helper + test.

## 7. Chiến lược sửa minimal đã chọn

- Viết lại block `TABLE_LOOKUP` (7 điểm) theo hướng dẫn brief (ưu tiên nguồn bảng; lựa chọn khớp ô; cấm câu deny cụ thể khi có evidence).
- Thêm banner **chỉ** khi `TABLE_LOOKUP` **và** detect table-like trong context (tránh áp vào mọi query).

## 8. Vì sao không sửa QueryAnalyzer / retrieval / source cap

- 21G3 đã chứng minh **TABLE_LOOKUP** + context có bảng cho T01 gốc; v4 là edge **generation**.
- Đụng retrieval/chat/analyzer vượt scope và rủi ro regress count/OOS.

## 9. Danh sách file đã đọc

| Path | Đọc để | Kết luận chính |
|------|--------|----------------|
| `docs/eval/results/RAG_QUERY_ANALYZER_RUNTIME_VERIFY_21G3_20260514.md` | Symptom runtime | T01 v4 FAIL deny có bảng |
| `reports/refactor/CURSOR_REPORT_21G3_QUERY_ANALYZER_RUNTIME_VERIFY.md` | Tóm verify | Đề xuất 21H |
| `PromptBuilderService.java` | Sửa | TABLE_LOOKUP cũ thiếu guard lựa chọn/deny |
| `RetrievedContext.java` | Helper detect | `chunkType`, `content` đủ cho heuristic |
| `DocumentChunk.java` (một phần) | Tên field chunk | `chunkType` lưu `table_*` |
| `ChatService.java` (grep) | Xác nhận hint | `queryTypeHint = "TABLE_LOOKUP"` chính xác |
| Các file eval khác trong brief | Bối cảnh | Đã có trong 21G3 doc; không bắt buộc đọc lại toàn bộ cho implementation |

*Các file `RAG_QUERY_ANALYZER_GENERALIZED_21G2_*`, `RAG_T01_GENERATION_SOURCE_DIAG_21F_*`, `RAG_GOLDEN_*`, `RAG_BASELINE_CORE_*` — tham chiếu từ báo cáo 21G3/21G2; không thay đổi nội dung trong task 21H.*

## 10. Danh sách file đã sửa / thêm

| Path | Sửa để | Lớp |
|------|--------|-----|
| `Backend/src/main/java/.../PromptBuilderService.java` | TABLE_LOOKUP + detect context bảng | service / prompt |
| `Backend/src/test/java/.../PromptBuilderServiceTest.java` | Lock instruction + no hardcode | test |

## 11. Diff từng file

### 11.1 `PromptBuilderService.java`

- **Hiện trạng cũ liên quan:** `TABLE_LOOKUP` ngắn; không xử lý câu lựa chọn; không cấm deny khi đã có evidence trong context.
- **Đã sửa:** mở rộng 7 bullet; thêm `TABLE_LOOKUP_TABLE_SOURCES_PRESENT_NOTE` khi `TABLE_LOOKUP` + `contextsContainTableLikeChunks`; helper `contentLooksLikeMarkdownTable` (dòng có ≥2 `|`).
- **Vì sao:** align LLM với evidence bảng và giảm mâu thuẫn với system #2 trên câu “A hay B?”.
- **Ảnh hưởng:** chỉ user prompt khi `queryTypeHint=TABLE_LOOKUP` (và banner chỉ khi có table-like context).

```diff
--- a/Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/PromptBuilderService.java
+++ b/Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/PromptBuilderService.java
@@ -1,7 +1,9 @@
 package KLTN.RAG_CHATBOT_BE.service;
 
 import KLTN.RAG_CHATBOT_BE.domain.chat.ChatMessage;
+import KLTN.RAG_CHATBOT_BE.dto.RetrievedContext;
 import org.springframework.stereotype.Service;
+
 import java.util.List;
 import java.util.Locale;
 import java.util.stream.Collectors;
@@ -9,6 +11,13 @@ import java.util.stream.Collectors;
 @Service
 public class PromptBuilderService {
 
+    private static final String TABLE_LOOKUP_TABLE_SOURCES_PRESENT_NOTE = """
+            [NHẮN NGỮ CẢNH BẢNG: ...]
+            """;
+
@@ -131,13 +140,18 @@ public class PromptBuilderService {
         if (queryTypeHint != null && !queryTypeHint.isBlank()) {
             prompt.append("[LOẠI CÂU HỎI: ").append(queryTypeHint).append("]\n");
-            prompt.append(buildQueryTypeInstruction(queryTypeHint)).append("\n\n");
+            String typeInstruction = buildQueryTypeInstruction(queryTypeHint);
+            prompt.append(typeInstruction);
+            if ("TABLE_LOOKUP".equalsIgnoreCase(queryTypeHint.trim()) && contextsContainTableLikeChunks(contexts)) {
+                prompt.append("\n").append(TABLE_LOOKUP_TABLE_SOURCES_PRESENT_NOTE.trim());
+            }
+            prompt.append("\n\n");
         }
@@ -182,12 +196,14 @@ public class PromptBuilderService {
             case "TABLE_LOOKUP" -> """
-                    Đây là câu hỏi TRA CỨU BẢNG. Bắt buộc:
-                    ...
+                    Đây là câu hỏi TRA CỨU BẢNG (TABLE_LOOKUP). Bắt buộc:
+                    1. ... 7. ...
                     """;
@@ -248,4 +264,42 @@ public class PromptBuilderService {
     private String nullSafe(String value) {
         return value == null ? "" : value;
     }
+
+    private boolean contextsContainTableLikeChunks(List<RetrievedContext> contexts) { ... }
+
+    private static boolean contentLooksLikeMarkdownTable(String content) { ... }
 }
```

*(Diff đầy đủ: `git diff` trên workspace sau commit/stage — nội dung trùng `git diff` đã chạy.)*

### 11.2 `PromptBuilderServiceTest.java` (file mới)

- **Đã thêm:** 4 test — TABLE_LOOKUP + `table_row_group` → có guard + banner; TABLE_LOOKUP + text không pipe → không banner; TABLE_LOOKUP + markdown trong `text` → có banner; COUNT_QUERY → không banner.
- **Không chứa:** Basic, Pro, Business, 99000, 199000 trong expected prompt (assertFalse).

```diff
--- /dev/null
+++ b/Backend/src/test/java/KLTN/RAG_CHATBOT_BE/PromptBuilderServiceTest.java
@@
+class PromptBuilderServiceTest {
+    ...
+}
```

## 12. Prompt instruction mới (đầy đủ TABLE_LOOKUP block)

```
Đây là câu hỏi TRA CỨU BẢNG (TABLE_LOOKUP). Bắt buộc:
1. Ưu tiên đọc các Source có Type=table_summary, table_row_group, text_table_like, và mọi Content có cấu trúc bảng Markdown (có ký tự '|' theo hàng/cột rõ ràng).
2. Nếu câu hỏi nêu tên bảng cụ thể trong tài liệu, CHỈ dùng Source khớp bảng đó; không lấy cột từ bảng khác.
3. Xác định hàng/dòng (đối tượng/entity trong câu hỏi) và cột/thuộc tính (giá trị cần tra). Đọc đúng ô giao của hàng và cột đó.
4. Nếu câu hỏi đưa ra nhiều lựa chọn giá trị (ví dụ dạng "… hay …?"), chọn lựa chọn khớp với giá trị trong ô của bảng; trả lời ngắn gọn và căn cứ vào ô/hàng/cột tương ứng — không suy diễn ngoài ô đã đọc.
5. Gộp TẤT CẢ dòng từ các Source table_row_group thuộc đúng phạm vi bảng được hỏi thành một bảng Markdown; không bỏ sót dòng thuộc phạm vi.
6. KHÔNG được dùng câu "Tôi không tìm thấy thông tin này trong tài liệu." khi trong [TÀI LIỆU THAM KHẢO] đã có hàng/cột/ô trực tiếp trả lời câu hỏi (kể cả câu dạng lựa chọn). Chỉ dùng câu từ chối đó khi không có hàng/cột/giá trị liên quan trong context.
7. Không đoán hoặc bịa giá trị không xuất hiện trong các Source; không suy luận ngoài nội dung ô/hàng đã có.
```

**Nhắn có điều kiện (`TABLE_LOOKUP_TABLE_SOURCES_PRESENT_NOTE`):**

`[NHẮN NGỮ CẢNH BẢNG: Trong danh sách Source bên dưới có ít nhất một mục loại bảng (table_summary / table_row_group / text_table_like) hoặc nội dung dạng bảng Markdown. Bạn phải đọc các Source đó trước khi kết luận không có thông tin.]`

## 13. Kiểm tra không hardcode entity/value

- String instruction: **không** chứa Basic / Pro / Business / 99000 / 199000.
- Unit test: assertFalse trên các literal trên trong prompt output (câu hỏi mẫu dùng “hạng A”, “100 hay 200”).

## 14. Ảnh hưởng

| Khía cạnh | Thay đổi |
|-----------|----------|
| TABLE_LOOKUP | Prompt dài hơn; ràng buộc đọc bảng + lựa chọn + cấm deny khi có evidence. |
| OOS | Không đổi retrieval — OOS vẫn phụ thuộc context trống/sai; instruction vẫn cấm bịa ngoài Source. |
| Source noise | Không tăng số source; chỉ thêm vài trăm ký tự instruction khi TABLE_LOOKUP. |
| RAM/CPU | Không đáng kể (scan nhỏ list context in-memory đã có). |

## 15. Compile / test result

| Command | Kết quả | Ghi chú |
|---------|---------|---------|
| `cd Backend; .\mvnw.cmd -DskipTests compile` | **PASS** | Windows PowerShell |
| `.\mvnw.cmd -Dtest=PromptBuilderServiceTest test` | **PASS** | |
| `Frontend npm run lint/build` | **NOT RUN** | Ngoài scope |
| `docker compose config` | **NOT RUN** | Ngoài scope |

## 16. Runtime verification

| Case | Kết quả |
|------|---------|
| T01 gốc ×3 | **NOT RUN** |
| T01 v4 ×3 | **NOT RUN** |
| F03/L02/T02/C01/O01/O02 | **NOT RUN** |

**Kỳ vọng:** sau khi chạy lại môi trường 21G3, cập nhật `docs/eval/results/RAG_PROMPTBUILDER_TABLE_GUARD_21H_20260515.md` verdict.

## 17. Có sửa runtime source không?

**Không** — không đổi cách lấy source/chunk; chỉ prompt builder.

## 18. Case improved / regressed

- **Improved (kỳ vọng):** T01 v4 và mọi TABLE_LOOKUP dạng lựa chọn khi context có bảng.
- **Regressed:** không có bằng chứng từ runtime; unit test không phát hiện regress cho COUNT_QUERY banner.

## 19. Rủi ro còn lại

- LLM vẫn có thể bất tuân instruction (cần runtime).
- Heuristic `contentLooksLikeMarkdownTable` (≥2 `|` trên một dòng) có thể **hiếm** false positive trên text có pipe — chỉ bật banner TABLE_LOOKUP, không đổi retrieval.

## 20. Đề xuất prompt tiếp theo

- Sau runtime: nếu T01 v4 vẫn FAIL, xem **format Source** (độ dài/merge bảng) hoặc task riêng retrieval — không mở rộng system prompt mù quáng.

---

## Resource / production analysis

| Hạng mục | Trả lời |
|----------|---------|
| DB / LLM call / Qdrant mới | **Không** |
| Reindex / migration | **Không** |
| Token prompt thêm (ước lượng) | ~350–550 ký tự khi `TABLE_LOOKUP` + có table-like (~90–140 token tùy tokenizer) — chỉ path đó |
| Latency | Tăng nhẹ do prompt dài hơn một phần nhỏ; không đo trong phiên này |

---

*Báo cáo task 21H — PromptBuilder table guard.*
