# CURSOR_REPORT_21G — QueryAnalyzer TABLE_LOOKUP vs COUNT_QUERY collision

## 1. Mức độ hiểu task

- **~95%** — đủ để sửa thứ tự/heuristic trong `QueryAnalyzerService` theo diagnosis 21F; unit test bao phủ bảng mẫu bắt buộc.
- **Chắc chắn:** trước fix, nhánh `COUNT_QUERY` (keyword `bao nhieu`) chạy trước `TABLE_LOOKUP`; T01 bị `COUNT_QUERY` (đã xác nhận trong 21F từ source + hành vi).
- **Giả định còn lại:** sau khi hint = `TABLE_LOOKUP`, LLM vẫn có thể từ chối (generation) — không chứng minh trong task này vì **không chạy** `POST /api/chat` (thiếu widget key trong phiên agent).

## 2. Tóm tắt yêu cầu

Sửa phân loại query: câu tra **giá/ô bảng/dòng** có **“bao nhiêu”** → `TABLE_LOOKUP`; câu **đếm** thật → `COUNT_QUERY`. Chỉ sửa `QueryAnalyzerService` + test nhẹ. Không đụng retrieval, PromptBuilder, ChatService, frontend, dependency, migration, `.gitignore`.

## 3. Phạm vi đã làm

- Cập nhật `QueryAnalyzerService.analyze()` thứ tự: explicit count → table cell lookup → COUNT generic → …
- Thêm helper private + unit `QueryAnalyzerServiceTest` (Mockito, không DB).
- Viết `docs/eval/results/RAG_QUERY_ANALYZER_FIX_21G_20260514.md`, `docs/eval/results/FIX_LOOP_21G_QUERY_ANALYZER_TABLE_COUNT_COLLISION.md`, báo cáo này, và bản đồng bộ rule 90 tại `docs/CURSOR_REPORT_21G_QUERY_ANALYZER_TABLE_LOOKUP_FIX.md`.

## 4. Phạm vi không làm

- Không sửa `PromptBuilderService`, `RagRetrievalService`, `ChatService`, `EmbeddingService`, frontend, Docker compose, schema DB, cap sources, reindex.

## 5. Root cause từ 21F

- `QueryAnalyzerService` xếp `COUNT_QUERY` **trước** keyword block `TABLE_LOOKUP`; `bao nhieu` khớp T01 gốc → `queryTypeHint` = `COUNT_QUERY` → `PromptBuilderService` không inject instruction mạnh cho đọc bảng (`TABLE_LOOKUP` case trong `buildQueryTypeInstruction`).

## 6. Phân tích QueryAnalyzer trước khi sửa (10 ý brief)

| # | Trả lời ngắn |
|---|--------------|
| 1 | `COUNT_QUERY` kiểm tra ngay sau `normalize()` tại block `containsAny(..., "bao nhieu", "co bao nhieu", ...)`. |
| 2 | `TABLE_LOOKUP` kiểm tra sau `LIST_ALL`, block keyword `bang`, `cot`, `hang`, … |
| 3 | T01 chứa `bao nhieu` → return sớm `COUNT_QUERY`, không tới nhánh `bang`. |
| 4 | Collision: `bao nhiêu` vs ngữ cảnh **giá/VNĐ/bảng/gói cụ thể**; “bao nhiêu **gói**” là đếm. |
| 5 | Cell lookup = cue bảng/dòng/cột + field (giá, lượt, kênh, hỗ trợ) hoặc tier + field; count = “bao nhiêu gói/chính sách/hàng”, đếm, số lượng. |
| 6 | Có — đưa rule tách collision **trước** COUNT generic. |
| 7 | Có — `isTableCellLookupQuery` + `isExplicitItemCountQuery` + `containsTierPackageMarker`. |
| 8 | `co bao nhieu goi` / `bao nhieu goi dich vu` / … giữ COUNT; không chồng “goi pro co bao nhieu luot”. |
| 9 | PromptBuilder: **không** sửa trong 21G; nếu T01 vẫn deny → task 21H. |
| 10 | Minimal: chỉ Java analyzer + unit. |

## 7. Chiến lược sửa minimal đã chọn

1. **`isExplicitItemCountQuery`**: `dem`, `so luong` + (`goi`|`chinh sach`|`hang`), `co bao nhieu goi`, `bao nhieu goi dich vu`, `bao nhieu hang goi`, `bao nhieu hang`, `co/bao nhieu chinh sach`, và token `bao nhieu goi` ở ranh giới từ.
2. **`isTableCellLookupQuery`**: cue bảng (`trong bang`, `theo bang`, `dong`/`cot`/…) + (field giá/VNĐ/lượt/kênh/hỗ trợ **hoặc** tier Basic/Pro/Business); hoặc tier + field không cần chữ “bảng”.
3. **`containsTierPackageMarker`**: ưu tiên `goi pro` / `dong pro` để tránh khớp `pro` trong từ tiếng Anh.

## 8. Vì sao không sửa prompt/retrieval trong task này

- 21F đã cô lập lỗi hint COUNT vs nhu cầu đọc ô bảng — sửa analyzer là **một nút** khớp contract hiện tại của `PromptBuilderService` (đã có nhánh `TABLE_LOOKUP` khi `queryTypeHint` đúng).
- Tránh thay đổi song song prompt + retrieval (lan scope, khó audit).

## 9. Danh sách file đã đọc

| Path | Đọc để làm gì | Kết luận chính |
|------|----------------|----------------|
| `docs/eval/results/RAG_T01_GENERATION_SOURCE_DIAG_21F_20260514.md` | Chẩn đoán T01 | COUNT trước TABLE; evidence đủ trong source |
| `reports/refactor/CURSOR_REPORT_21F_T01_GENERATION_SOURCE_DIAGNOSIS.md` | Tóm tắt 21F | Cùng root cause |
| `docs/eval/results/RAG_RUNTIME_VERIFY_21E2_SECTION7_20260514.md` | G1 PASS | metadata 21E2 ổn |
| `reports/refactor/CURSOR_REPORT_21E2_RUNTIME_VERIFY_SECTION7_AFTER_21D2.md` | Bối cảnh | không đổi parser trong 21G |
| `docs/eval/RAG_BASELINE_CORE_CHECKLIST.md` | Gate ID | G3-T01 liên quan hint |
| `docs/eval/RAG_BASELINE_CORE_REBUILD_PLAN.md` | Quy trình gate | fix nhỏ theo một PR |
| `docs/eval/RAG_GOLDEN_QUESTIONS.md` | Text GQ | T01 = TABLE_LOOKUP; C01 = COUNT |
| `docs/eval/RAG_GOLDEN_TEST_DOCUMENT.txt` | Ngữ cảnh bảng | Basic/99000 |
| `QueryAnalyzerService.java` | Sửa | thứ tự rule như mục 7 |
| `PromptBuilderService.java` | grep `TABLE_LOOKUP` | instruction phụ thuộc hint string |
| `RagRetrievalService.java` | grep `queryType` | TABLE_LOOKUP/COUNT ảnh hưởng expand/lock (không đổi) |
| `ChatService.java` | grep `analyze` | chỉ đọc luồng hint (không đổi) |

## 10. Danh sách file đã sửa

| Path | Sửa để làm gì | Lớp ảnh hưởng |
|------|----------------|---------------|
| `Backend/.../QueryAnalyzerService.java` | Tách COUNT thật vs TABLE cell | service |
| `Backend/.../QueryAnalyzerServiceTest.java` | Regression T01-like / COUNT | test |
| `docs/eval/results/RAG_QUERY_ANALYZER_FIX_21G_20260514.md` | Evidence eval | docs |
| `docs/eval/results/FIX_LOOP_21G_QUERY_ANALYZER_TABLE_COUNT_COLLISION.md` | Fix loop 21G | docs |
| `reports/refactor/CURSOR_REPORT_21G_QUERY_ANALYZER_TABLE_LOOKUP_FIX.md` | Audit task | docs |
| `docs/CURSOR_REPORT_21G_QUERY_ANALYZER_TABLE_LOOKUP_FIX.md` | Rule 90 | docs |

## 11. Diff từng file

### 11.1 `QueryAnalyzerService.java`

- **Hiện trạng cũ:** `normalize` → `COUNT_QUERY` nếu có `bao nhieu` → không tới TABLE cell dù có `bang`/`gia`.
- **Đã sửa:** chèn `isExplicitItemCountQuery` → `COUNT_QUERY`; sau đó `isTableCellLookupQuery` → `TABLE_LOOKUP`; rồi mới COUNT generic.
- **Vì sao:** khôi phục hint đúng cho PromptBuilder mà không đổi contract API.
- **Ảnh hưởng:** T01-like → `TABLE_LOOKUP`; đếm gói/chính sách/hàng → `COUNT_QUERY` (giữ).

```diff
--- analyze() flow
@@
         String q = normalize(question);
+
+        if (isExplicitItemCountQuery(q)) {
+            return QueryType.COUNT_QUERY;
+        }
+
+        if (isTableCellLookupQuery(q)) {
+            return QueryType.TABLE_LOOKUP;
+        }
 
         if (containsAny(q,
                 "bao nhieu", ...
```

```diff
--- new helpers (cuối class, trước normalize public)
+    private boolean isExplicitItemCountQuery(String q) { ... }
+
+    private boolean isTableCellLookupQuery(String q) { ... }
+
+    private boolean containsTierPackageMarker(String q) { ... }
```

### 11.2 `QueryAnalyzerServiceTest.java`

- **File mới:** 10 assertion TABLE_LOOKUP + 6 COUNT (bao gồm GQ-C01, F03 trùng mẫu).

```diff
+@ExtendWith(MockitoExtension.class)
+class QueryAnalyzerServiceTest {
+    @Mock DocumentSectionRepository documentSectionRepository;
+    ...
+}
```

## 12. Ảnh hưởng sau sửa

| Hạng mục | Thay đổi |
|----------|----------|
| TABLE_LOOKUP | Nhiều câu có `bao nhieu` + bảng/gói cụ thể/field → hint `TABLE_LOOKUP` sớm hơn. |
| COUNT_QUERY | Câu đếm explicit vẫn `COUNT_QUERY`; COUNT generic sau cùng cho các `bao nhieu` khác (vd O01). |
| FACT / LIST / SECTION | Không đổi thứ tự các nhánh sau COUNT/TABLE cell (LIST, TABLE keyword block, SECTION, …). |
| RAM/CPU | Thêm vài `contains`/substring trên chuỗi câu hỏi — O(1), không DB/LLM/Qdrant. |
| Latency | Không thêm I/O; không reindex. |

## 13. Edge cases đã xem xét

- **Đếm trong bảng:** “Trong bảng có bao nhiêu gói?” → explicit `co bao nhieu goi`.
- **GQ-C01:** `bao nhieu hang goi` → explicit count.
- **GQ-F03 / Pro quota:** `goi pro co bao nhieu luot` — không khớp `bao nhieu goi` contiguous; → TABLE cell.
- **O01 tỷ giá:** không có cue bảng/tier như T01; sau các nhánh mới vẫn rơi COUNT generic nếu có `bao nhieu` (giữ behavior COUNT cho câu hỏi dạng “bao nhiêu” không phải cell).
- **Không đọc `.env`:** runtime chat không chạy trong phiên agent.

## 14. Compile / test result

| Command | Kết quả | Ghi chú |
|---------|---------|---------|
| `Backend\.\mvnw.cmd -DskipTests compile -q` | **PASS** | |
| `Backend\.\mvnw.cmd -q -Dtest=QueryAnalyzerServiceTest test` | **PASS** | |
| `Backend\.\mvnw.cmd test` (full) | **NOT RUN** | ngoài scope task nhỏ |
| Frontend lint / build / widget | **NOT RUN** | ngoài scope |

## 15. Runtime verification

- **NOT RUN** — không gọi `POST /api/chat` (không nạp `X-Widget-Key` từ `.env` trong môi trường agent).
- Docker: `docker compose config -q` **PASS**; `docker ps` cho thấy backend **Up** (operator có thể rebuild image chứa fix rồi chạy T01×3).

## 16. Checklist status summary

- **G0-CMP-001:** PASS (compile).
- **G3-T01-001:** PARTIAL ở mức **hint** (đã unit); verdict câu trả lời 99000 cần rerun chat sau deploy.

## 17. Có sửa runtime source trong container không?

- **Chưa** — chỉ sửa source trong workspace; image Docker hiện chạy có thể chưa rebuild chứa bytecode mới. Operator: `docker compose up --build -d` để pick up fix.

## 18. Case improved / regressed

| Case | Kỳ vọng |
|------|---------|
| GQ-T01 (hint) | Improved: `TABLE_LOOKUP` |
| GQ-C01, COUNT mẫu | Không regress (unit) |
| GQ-F03 | `TABLE_LOOKUP` (unit) |

## 19. Rủi ro còn lại

- Heuristic `hang ` / `hotro` / `gia` có thể cần tinh chỉnh nếu corpus mở rộng (vd câu hỏi tiếng Anh dài chứa `pro`).
- **Generation** vẫn có thể deny dù hint đúng (21F) — ngoài scope 21G.

## 20. Đề xuất prompt tiếp theo (21H)

- Nếu sau deploy + T01×3 vẫn FAIL dù log `queryType=TABLE_LOOKUP`: thêm guard nhỏ trong `PromptBuilderService` (bắt buộc trả số từ `table_*` khi entity khớp) — **task riêng**.

---

## Resource / production analysis (bổ sung)

| Câu hỏi | Kết luận |
|---------|----------|
| Thêm vòng lặp lớn? | Không — chỉ chuỗi cố định. |
| DB / LLM / Qdrant mới? | Không. |
| Latency | Không đo được delta đáng kể; không I/O mới. |
| Máy yếu | Không đáng kể. |
| Reindex? | Không cần. |

---

*Bản đồng bộ rule 90: `docs/CURSOR_REPORT_21G_QUERY_ANALYZER_TABLE_LOOKUP_FIX.md` (nội dung giống file này).*
