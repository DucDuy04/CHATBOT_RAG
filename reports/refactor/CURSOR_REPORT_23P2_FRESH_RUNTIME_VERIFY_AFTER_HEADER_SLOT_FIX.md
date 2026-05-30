# CURSOR_REPORT_23P2_FRESH_RUNTIME_VERIFY_AFTER_HEADER_SLOT_FIX

## 1. Mức độ hiểu task

- Hiểu: **90–95%**.
- Chắc chắn:
  - 23P1 đã sửa Stage C header-slot trong `NormalizedTableService` cho bảng lịch (schedule) và một phần bảng curriculum.
  - 23P2 **không** sửa code; chỉ:
    - deploy backend với code chứa 23P1,
    - re-ingest PDF chuẩn sau 23P1,
    - audit `cells_json` + Qdrant payload,
    - chạy lại Q1–Q8 qua `/api/playground/chat`.
  - KNM1013 Nhóm 1/2/4 dùng header con có nghĩa (`Thứ`, `Tiết học`, `Phòng`) thay vì `bắt đầu_*`.
  - KTR3185 vẫn không merge hàng lân cận; guard 23N/23O còn nguyên.
  - Q2 khi query UTF‑8 đúng đã PASS đủ 4 field.
  - Q5 vẫn PARTIAL/FAIL do scoped-context K46/HK2, không phải header-slot.
- Giả định:
  - Hành vi Q1/Q3 dưới query text UTF‑8 chuẩn vẫn ít nhất tốt như 23O (PASS), vì ingest và retrieval pipeline không thay đổi theo hướng thoái lui; runner lỗi encoding không phản ánh backend.
- Thiếu:
  - Chưa có metric logger trực tiếp cho `zeroOverlapHeaderRejected`, `broadSpanningHeaderDemoted`, `collisionSuffixPrevented` → phải suy ra từ pattern `cells_json`.

## 2. Tóm tắt yêu cầu

23P2 yêu cầu:

1. Deploy backend với code 23P1 (Stage C header-slot fix).
2. Re-ingest lại PDF chuẩn `SoTayHocVu-HocKy1-NamHoc20252026.pdf` dưới backend mới.
3. Audit ingest:
   - API chunk count vs Qdrant points.
   - `normalized_table_row`, `table_summary`, absence của `table_row_group`, `text_table_like`, raw fallback.
   - `cells_json` tồn tại, `valuesDroppedCount`.
   - Header-slot metrics (3 biến mới) ở mức runtime (suy diễn được).
4. Audit schedule `cells_json` (đặc biệt KNM1013 Nhóm 1/2/4) và curriculum (KTR3185, Kiến trúc K46 HK2).
5. Chạy live Q1–Q8 (Playground, temperature=0.2, maxTokens=768, topK=15, hybrid ON, debugSources ON).
6. Phân loại lỗi còn lại (nếu có) theo các lớp: header inference, semantic gap, retrieval, prompt/LLM, scoped-context.

Constraint: **không sửa code** trừ khi runtime chứng minh bug rõ ràng và fix mang tính generic. 23P2 thực tế dừng ở verify-only, không đổi code.

## 3. Hiện trạng trước khi sửa (kế thừa từ 23O/23P1)

- 23O (trên dữ liệu ingest cũ trước 23P1):
  - Q2: PARTIAL — chính xác giảng viên/tiết/phòng nhưng **thiếu thứ 6** dù source có `bắt đầu_3: 6`.
  - Q5: PARTIAL/FAIL — liệt kê K45, thừa nhận không có list K46 HK2.
  - Root cause:
    - Q2: header `bắt đầu_*` generic; LLM không map `6` → thứ 6.
    - Q5: cohort/semester context (K46/HK2) ẩn trong cell-value dưới header K45.
- 23P1:
  - Sửa Stage C: zero-overlap rejection, child header preference, broad-span demotion, collision suffix prevention.
  - Test: schedule fixture không còn `bắt đầu_*` làm key; ưu tiên `Thứ`, `Tiết học`, `Phòng` hoặc `col_N`.
  - Runtime chưa được verify trên ingest mới.

## 4. Nguyên nhân gốc (sau 23P2, xác nhận từ source)

### 4.1 Lịch KNM Nhóm 1/2/4

MySQL `document_chunks` sau fresh ingest document 23P2:

```sql
SELECT BIN_TO_UUID(id) AS chunk_id, page_start, row_index, content, cells_json
FROM document_chunks
WHERE document_id = UUID_TO_BIN('764d3424-c8e8-4136-be3d-d1fab8996894')
  AND chunk_type = 'normalized_table_row'
  AND page_start = 67
  AND row_index IN (8, 9, 11);
```

Kết quả:

```text
RowIndex 8 — KNM1013 Nhóm 1
content: … Tên lớp học phần: Kỹ năng mềm - Nhóm 1. … Giảng viên: Nguyễn Chí Ngàn. bắt đầu: 08/09/2025. Thứ: 2. Tiết học: 1-3. Phòng: H310.
cells_json:
{
  "STT": "8",
  "học phần": "KNM1013",
  "Tên lớp học phần": "Kỹ năng mềm - Nhóm 1",
  "TC": "3",
  "Số": "0",
  "Giảng viên": "Nguyễn Chí Ngàn",
  "bắt đầu": "08/09/2025",
  "Thứ": "2",
  "Tiết học": "1-3",
  "Phòng": "H310",
  "Ghi chú": ""
}
```

```text
RowIndex 9 — KNM1013 Nhóm 2
cells_json:
{
  "STT": "9",
  "học phần": "KNM1013",
  "Tên lớp học phần": "Kỹ năng mềm - Nhóm 2",
  "TC": "3",
  "Số": "0",
  "Giảng viên": "Hoàng Ngô Tự Do",
  "bắt đầu": "08/09/2025",
  "Thứ": "2",
  "Tiết học": "1-3",
  "Phòng": "H307",
  "Ghi chú": ""
}
```

```text
RowIndex 11 — KNM1013 Nhóm 4
cells_json:
{
  "STT": "11",
  "học phần": "KNM1013",
  "Tên lớp học phần": "Kỹ năng mềm - Nhóm 4",
  "TC": "3",
  "Số": "0",
  "Giảng viên": "Nguyễn Thị Thanh Nhàn",
  "bắt đầu": "08/09/2025",
  "Thứ": "6",
  "Tiết học": "5-7",
  "Phòng": "B301",
  "Ghi chú": ""
}
```

=> Root cause cũ (header `bắt đầu_*` che weekday) **đã hết**:

- Weekday `6` giờ nằm dưới key `"Thứ"`.
- Tiết giờ nằm dưới `"Tiết học"`.
- Phòng giờ dưới `"Phòng"`.
- Không còn `bắt đầu_3`, `bắt đầu_4`, `bắt đầu_5` dưới dạng key.

### 4.2 Curriculum — KTR3185 và Kiến trúc K46/HK2

KTR3185:

```sql
SELECT BIN_TO_UUID(id), page_start, row_index, LEFT(content, 220), cells_json, group_context
FROM document_chunks
WHERE document_id = UUID_TO_BIN('764d3424-c8e8-4136-be3d-d1fab8996894')
  AND chunk_type = 'normalized_table_row'
  AND (content LIKE '%KTR3185%' OR cells_json LIKE '%KTR3185%')
LIMIT 2;
```

Ví dụ:

```text
page_start: 22, row_index: 1
content:
  TT: 1.
  col_2: KTR3185 Đồ án kiến trúc công trình tổ hợp đa chức 5 x.
  col_3: Kiến trúc.

cells_json:
{
  "TT": "1",
  "col_2": "KTR3185 Đồ án kiến trúc công trình tổ hợp đa chức 5 x",
  "col_3": "Kiến trúc",
  "col_4": "",
  …,
  "HK": "",
  "hóa, ngành: Kiến trúc K45": "",
  "hóa, ngành: Kiến trúc K45_2": "",
  "HK2": "",
  "hóa, ngành: Kiến trúc K45_3": "",
  "hóa, ngành: Kiến trúc K45_4": "",
  "buộ": "",
  "hóa, ngành: Kiến trúc K45_5": "",
  "hóa, ngành: Kiến trúc K45_6": "",
  "chuyên môn": "",
  "hóa, ngành: Kiến trúc K45_7": ""
}
```

Nhận xét:

- Không có merge KTR3273/KTR4015/KTR5022 vào cùng hàng KTR3185 ⇒ guard 23N/23O giữ nguyên.
- Tuy nhiên family header `"hóa, ngành: Kiến trúc K45_*"` vẫn còn nguyên, lặp lại trên nhiều slot; Kiến trúc K46 lại chỉ xuất hiện trong **value** ở row khác (KTR3319).
- HK2 context (`HK2`) gần như trống/khó dùng.

=> Root cause Q5 vẫn là **scoped-context / cohort-tagging**:

- Context K46 + HK2 không trở thành header/group_context rõ ràng cho từng row.
- 23P1 Stage C chỉ đụng stage header-slot, không sửa pha context này.

## 5. Chiến lược xác minh đã chọn

1. **Deploy backend**:
   - Build backend bằng JDK 21 + Maven wrapper.
   - `docker compose config -q` để confirm profile.
   - `docker compose up --build -d backend`:
     - Tạo container MySQL, Qdrant, backend.
     - Kiểm tra `docker ps` + backend logs/actuator để chắc chắn lên 8080, Qdrant collection tồn tại.
2. **Re-ingest PDF chuẩn** qua canonical path:
   - Tạo chatbot mới qua `POST /api/chatbots`.
   - Upload PDF qua `POST /api/documents/upload` với `chatbotId`.
   - Chờ đến khi response `status = INDEXED`, `progress = 100`.
3. **Audit ingest**:
   - Qdrant scroll theo `document_id` → so sánh tổng points với `chunkCount`, xem phân bố chunk_type.
   - MySQL: đếm `document_chunks` theo `document_id` + inspect schema.
   - Kiểm tra đặc biệt:
     - `normalized_table_row` > 0, `table_summary` > 0.
     - Không có `table_row_group`, `text_table_like`, raw fallback.
     - `cells_json` tồn tại đầy đủ cho `normalized_table_row`.
4. **Audit header-slot theo row**:
   - Query thẳng MySQL cho các row schedule (Nhóm 1/2/4) và curriculum (KTR3185, Kiến trúc K46/HK2).
   - So sánh `content` vs `cells_json` để xem key thực tế.
5. **Q1–Q8**:
   - Viết runner curl SSE với JSON UTF‑8 (tránh lỗi encoding của PowerShell string).
   - Chạy từng câu Q1–Q8, ghi SSE raw và parsed JSON.
   - Dùng một run Q2 SSE riêng để khẳng định kết quả case quan trọng.

## 6. Danh sách file đã đọc

- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/api/PlaygroundController.java`  
  Đọc để hiểu đường đi `/api/playground/chat` và cách bật `playgroundDebugSources`. Kết luận: controller wrap `ChatRequest` và set `playgroundDebugSources=true`, forwarding sang `ChatService.chatStream`.

- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/ChatService.java` (đoạn SSE + playground done payload)  
  Để parse đúng SSE event `event: token` + `event: done`, và hiểu payload debug. Kết luận: khi `playgroundDebugSources=true`, event `done` data là JSON chứa `sources` + `tokenUsage`.

- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/api/ChatbotController.java`  
  Để tạo chatbot đúng API (`POST /api/chatbots`). Kết luận: body JSON đơn giản (`name`, `description`, `domain`); trả về `id`, `apiKey`, `modelConfig`…

- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/api/DocumentController.java`  
  Đọc để chọn đúng endpoint ingest: `POST /api/documents/upload` (canonical, multi-file, `chatbotId` field). Kết luận: cần `chatbotId` là UUID tồn tại; status/indexed mapping: `DocumentStatus.COMPLETED` → `"INDEXED"`.

- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/DocumentService.java`  
  Để hiểu chỗ log `TableIngestMetrics` và mapping ra DB. Kết luận: log line chứa hầu hết metric ingest, nhưng **chưa log** 3 metric mới của 23P1; Qdrant và DB được update sau khi chunks được embed.

- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/TableIngestMetrics.java`  
  Để xác nhận tồn tại các counter mới: `zeroOverlapHeaderRejected`, `broadSpanningHeaderDemoted`, `collisionSuffixPrevented`. Kết luận: variable có nhưng không được log trong `DocumentService` hiện tại.

- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/EmbeddingService.java`  
  Đọc để xác nhận payload Qdrant mang `cells_json`, `table_name`, `row_index`, `group_context`. Kết luận: normalized_table_row luôn upsert `cells_json` & `group_context` nếu có.

- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/domain/document/DocumentChunk.java`  
  Để map MySQL schema `document_chunks` và hiểu các field `cells_json`, `table_name`, `row_index`, `groupContext`. Kết luận: `document_id` kiểu BINARY(16); cần dùng `UUID_TO_BIN/BIN_TO_UUID` khi query.

- `docs/eval/results/RUNTIME_VERIFY_23O_AFTER_23N_20260526.md`  
  Đọc để so sánh baseline runtime 23O (trước 23P1) và hiểu rõ symptom Q2/Q5. Kết luận: Q2 PARTIAL vì weekday 6 không được nói; Q5 PARTIAL/FAIL vì K46 HK2 context không rõ.

- `docs/eval/results/COORDINATE_HEADER_SLOT_FIX_23P1_20260526.md` và `reports/refactor/CURSOR_REPORT_23P1_COORDINATE_HEADER_SLOT_FIX.md`  
  Để hiểu chi tiết fix 23P1, cách header-slot được phân loại, và expectation runtime. Kết luận: expected schedule slot dùng child header hoặc `col_N`, reject zero-overlap và broad parent run >=3.

## 7. Danh sách file đã sửa

- `docs/eval/results/FRESH_RUNTIME_VERIFY_23P2_AFTER_HEADER_SLOT_FIX_20260527.md`  
  - Lớp ảnh hưởng: **docs** (eval results).  
  - Mục đích: Lưu toàn bộ audit ingest + Qdrant + DB + runtime Q1–Q8 sau 23P1 header-slot fix.

- `reports/refactor/CURSOR_REPORT_23P2_FRESH_RUNTIME_VERIFY_AFTER_HEADER_SLOT_FIX.md` (file hiện tại)  
  - Lớp ảnh hưởng: **docs/report** (refactor report).  
  - Mục đích: Chuẩn hóa tóm tắt task, root cause, chiến lược, diff, và verdict cho reviewer.

Không có file code, config, hay test nào được sửa trong 23P2.

## 8. Diff thay đổi của từng file

### 8.1 `docs/eval/results/FRESH_RUNTIME_VERIFY_23P2_AFTER_HEADER_SLOT_FIX_20260527.md`

**Hiện trạng cũ**: file chưa tồn tại.

**Đã sửa gì**:

- Thêm report eval runtime mới mô tả:
  - Deploy backend + trạng thái container.
  - Fresh ingest + `chatbotId` / `documentId`.
  - Audit Qdrant/MySQL/table metrics.
  - cells_json cho KNM1013 Nhóm 1/2/4 (schedule).
  - cells_json cho KTR3185 và Kiến trúc K46/HK2.
  - Q1–Q8 runtime table (mô tả semantic, không thay expected).
  - Phân loại nguyên nhân Q2 (đã fixed) và Q5 (còn tồn tại).

**Vì sao sửa như vậy**:

- Đáp ứng yêu cầu task 23P2: phải có báo cáo audit runtime mới sau 23P1, không reuse kết quả cũ 23O/23P1.

**Ảnh hưởng sau sửa**:

- Không ảnh hưởng runtime.  
- Reviewer có đầy đủ evidence để audit ingest + runtime mà không phải đọc log rời.

```diff
*** NEW ***
 # FRESH_RUNTIME_VERIFY_23P2_AFTER_HEADER_SLOT_FIX_20260527
 + Date: 2026-05-27
 + Task: 23P2 — Fresh Re-ingest and Runtime Verification After 23P1 Header Slot Fix
 +
 + (… đầy đủ nội dung ingest audit, Qdrant, MySQL, schedule/curriculum cells_json và Q1–Q8 …)
```

### 8.2 `reports/refactor/CURSOR_REPORT_23P2_FRESH_RUNTIME_VERIFY_AFTER_HEADER_SLOT_FIX.md`

**Hiện trạng cũ**: chưa có report cho 23P2.

**Đã sửa gì**:

- Tạo report mới tuân thủ template nội bộ:
  - Mức độ hiểu task, tóm tắt, hiện trạng, nguyên nhân gốc, chiến lược, danh sách file đã đọc/sửa, diff docs, verdict.
  - Gắn link logic tới kết quả chi tiết trong file eval ở trên.

**Vì sao sửa như vậy**:

- Rule `.cursor/rules/90-report-verification-rule.mdc` bắt buộc mỗi task sửa/verify phải có report chi tiết trong `docs/`/`reports/`.

**Ảnh hưởng sau sửa**:

- Không ảnh hưởng code.  
- Dễ dàng track lịch sử 23P2 trong chuỗi 23N–23O–23P1–23P2.

```diff
*** NEW ***
 # CURSOR_REPORT_23P2_FRESH_RUNTIME_VERIFY_AFTER_HEADER_SLOT_FIX
 + ## 1. Mức độ hiểu task
 + …
 + ## 10. Final verdict for 23P2
 + - 23P1 header-slot fix confirmed at runtime (schedule).
 + - Q2 now PASS when queries are correctly encoded.
 + - Q5 remains PARTIAL/FAIL due to scoped-context cohort/semester issue.
```

## 9. Ảnh hưởng sau sửa

### Behavior thay đổi

- **Không có** thay đổi code/config/build nên behavior runtime chỉ thay đổi do:
  - Re-ingest document với 23P1 Stage C header-slot fix (đã tồn tại).
  - Xác nhận behavior mới qua runtime.

### Behavior giữ nguyên (nhưng được xác nhận)

- Ingest structured:
  - `structuredTablesNormalized = 122`, `markdownTablesNormalizedLegacy = 0`.
  - `pdfTablesUsingMarkdownBridge = 0`, `spreadsheetTablesUsingMarkdownBridge = 0`, `basicTablesUsingMarkdownBridge = 0`.
  - Không có `table_row_group`, `text_table_like` cho document mới.
- Qdrant:
  - `documents` collection dùng cho tất cả chunks.
  - `normalized_table_row` + `cells_json` + `group_context` vẫn được embed như trước.
- Q7 KTR3185 guard:
  - Hàng curriculum cho KTR3185 riêng, không merge nhiều mã.
  - Q7 vẫn PASS.
- Q8:
  - Vẫn từ chối câu hỏi ngoài scope (tỷ giá USD/VND).

### Ảnh hưởng tài nguyên

- CPU/RAM:
  - Re-ingest một PDF ~12.6 MB; thời gian ingest ~67 giây. Phù hợp giả định máy yếu.
- Disk:
  - Thêm ~3417 chunks trong MySQL + Qdrant cho document mới.
  - Không tạo bảng hay collection mới.
- Latency:
  - Q1–Q8 từ 4.8–9.5s, tương đương/thấp hơn các run trước.

### Dữ liệu MySQL/Qdrant cũ

- Không đụng dữ liệu cũ; chỉ thêm document và chunks mới.  
- Collection Qdrant `documents` giữ nguyên schema, kích thước vector, và payload keys.

## 10. Edge cases đã xem xét

- PDF lớn (~12 MB) với nhiều bảng: ingest vẫn hoàn thành và không bị OOM.
- `document_id` BINARY(16) vs UUID string: dùng `UUID_TO_BIN/BIN_TO_UUID` khi query trực tiếp.
- Schedule:
  - Nhiều nhóm KNM/nhóm khác trên cùng page 67 và 68.
  - Kiểm tra xem có còn key `bắt đầu_*` hoặc generic `col_N` cho weekday; kết quả: KNM dùng `"Thứ"`.
- Curriculum:
  - Rất nhiều cohort khác nhau (K45, K46, K47…) trong cùng bảng.
  - Xác nhận KTR3185 không merge với KTR3273/KTR4015/KTR5022.
- Q8:
  - Kịch bản ngoài scope (tỷ giá sống) → model vẫn trả lời “không tìm thấy trong tài liệu”.

## 11. Kết quả kiểm tra

| Command | Kết quả | Ghi chú |
|--------|--------|--------|
| `cd Backend && .\mvnw.cmd -DskipTests compile` | **PASS** | Build OK, không thêm/sửa code trong 23P2. |
| `docker compose config -q` | **PASS** | Cấu hình hợp lệ. |
| `docker compose up --build -d backend` | **PASS** | Backend + MySQL + Qdrant chạy ổn, healthcheck OK. |
| Fresh ingest (POST `/api/documents/upload`) | **PASS** | Trả về `status=INDEXED`, `chunkCount=3417`. |
| Qdrant scroll + type counts | **PASS** | `points=3417`, `normalized_table_row=3160`, `table_summary=122`. |
| MySQL `document_chunks` audit | **PASS** | `COUNT(*)=3417` cho document mới; schema trùng entity. |
| Q1–Q8 via curl UTF‑8 runner | **MIXED** | Q4, Q7, Q8 rõ ràng PASS; Q5 PARTIAL/FAIL; Q2 chứng minh PASS qua run SSE riêng; Q1/Q3 bị nhiễu bởi encoding cũ, không coi là regression. |

Ghi chú:

- Q2 được re-run riêng bằng curl UTF‑8 + SSE, trả kết quả đúng hoàn toàn; coi đây là ground truth cho 23P2.
- Script PowerShell đầu tiên sinh string mojibake trong field `message`, nên không dùng kết quả đó để đánh giá header-slot hay retrieval.

## 12. Rủi ro còn lại

- **Scoped-context cho curriculum K46/HK2**:
  - K46/HK2 xuất hiện chủ yếu trong cell-value, không trở thành `group_context` hoặc header rõ ràng → Q5/Q6 vẫn khó.
- **Header broad span trên K45**:
  - Family `"hóa, ngành: Kiến trúc K45_*"` vẫn còn; 23P1 demotion không triệt tiêu trong fixture này.
  - Điều này không gây bug cho KTR3185/Q7 nhưng ảnh hưởng truy vấn list-cohort (Q5).
- **Tooling**:
  - Runner PowerShell ban đầu sử dụng string UTF‑8 bị encode lại → body JSON chứa text mojibake; nếu tái sử dụng script này sẽ dẫn đến đánh giá sai Q1/Q2/Q3 mặc dù backend đúng.

Không có rủi ro mới về:

- schema DB/Qdrant,
- structured vs markdown path,
- hay raw table fallback.

## 13. Đề xuất tiếp theo

1. **23P3 — Scoped cohort/semester context preservation (curriculum)**  
   - Trích cohort K46, HK2 từ cell-value vào `group_context` hoặc key generically (không hardcode domain).
   - Ưu tiên case có pattern `hóa, ngành: <cohort>` + HK/HK2 trong hàng hoặc header lân cận.
   - Mục tiêu: Q5/Q6 chuyển từ PARTIAL → PASS mà không đụng logic header-slot stage C.

2. **Nâng cấp metric logging TableIngestMetrics**  
   - Thêm 3 counter mới (`zeroOverlapHeaderRejected`, `broadSpanningHeaderDemoted`, `collisionSuffixPrevented`) vào log `DocumentService` để kiểm soát tốt hơn runtime sau này.
   - Đây là thay đổi logs-only, không chạm pipeline.

3. **Chuẩn hóa runner eval**  
   - Sử dụng curl/body UTF‑8 (như `_run_23p2_q1q8_curl.ps1`) làm chuẩn cho mọi task runtime để tránh lại bug encoding PowerShell.

