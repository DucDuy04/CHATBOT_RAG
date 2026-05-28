# CURSOR_REPORT_24D0_RAG_CONTEXT_HK_SPLIT_CELLS_JSON_VERIFY

## 1. Mức độ hiểu task

- Hiểu task: **100%**.
- Chắc chắn:
  - Task verify-only trước, không sửa production code.
  - User đã đổi file mục tiêu sang `RAG_CONTEXT_HOC_KY` và yêu cầu reingest + retest.
  - Cần sửa lại report 24D0 theo run mới.
- Giả định:
  - Hệ thống endpoint/API và DB/Qdrant đang hoạt động local qua docker compose.
- Thiếu dữ kiện:
  - Không có quyền trực tiếp kiểm tra billing dashboard Nomic, chỉ xác minh qua runtime error thực.

## 2. Tóm tắt yêu cầu

- Chạy lại verification đầy đủ cho file DOCX mới `RAG_CONTEXT_HOC_KY`.
- Audit ingest metrics, DB chunks, Qdrant payload, cells_json/group_context.
- Check các case trọng điểm: Kiến trúc K46 HK2, Công nghệ sinh học K46 HK2, KTR3185, KNM1013 Nhóm 2/4, TIN1093 Nhóm 15.
- Chạy runtime UTF-8 Q1-Q8 nếu ingest thành công.

## 3. Hiện trạng trước khi sửa

- Workspace có baseline 24D0 cũ đang FAIL do quota.
- Services backend/mysql/qdrant đang running ổn định.
- File mới `SoTayHocVu_HocKy1_2025-2026_RAG_CONTEXT_HOC_KY.docx` tồn tại.

## 4. Nguyên nhân gốc xác nhận từ source

Nguyên nhân gốc còn lại sau rerun 24D0:

- Quota Nomic **đã hết block** trong run này (ingest INDEXED thành công).
- Vấn đề hiện tại nằm ở độ strict của answer/runtime:
  - Q1/Q2 bị diễn giải chưa chuẩn định dạng (đặc biệt Nhóm 4: tiết trả thành `6` thay vì `5-7`).
  - Q5/Q6 vẫn PARTIAL về strict scope major/cohort/semester.

## 5. Chiến lược sửa đã chọn

- Không sửa production code.
- Reingest file mới + chạy lại Q1-Q8 UTF-8.
- Audit lại API/DB/Qdrant + ingest metrics.
- Cập nhật report 24D0 theo evidence mới.

## 6. Danh sách file đã đọc

| Path | Đọc để làm gì | Kết luận chính |
|---|---|---|
| `.cursor/rules/00-core-working-rule.mdc` | tuân thủ luật nền | phải source-first, minimal scope |
| `.cursor/rules/90-report-verification-rule.mdc` | tuân thủ format/report bắt buộc | cần report đủ 13 mục và final chat format |
| `agent.md` | nắm bối cảnh project | xác nhận kiến trúc RAG tổng quan |
| `agent/01-overview.md` | nắm flow vận hành | xác nhận luồng ingest/retrieval |
| `agent/02-architecture.md` | schema DB/Qdrant/payload | chọn đúng điểm audit DB/Qdrant |
| `Backend/.../api/ChatbotController.java` | xác nhận endpoint tạo chatbot | dùng `POST /api/chatbots` |
| `Backend/.../api/DocumentController.java` | xác nhận endpoint upload/status | dùng `POST /api/documents/upload?chatbotId=...` |
| `Backend/.../api/PlaygroundController.java` | xác nhận endpoint runtime | runtime chỉ hợp lệ khi đã index |
| `Backend/.../dto/PlaygroundChatRequest.java` | cấu trúc payload runtime | topK/temperature/maxTokens có sẵn |
| `docker-compose.yml` | lấy config infra/local creds | mysql root pass, port qdrant/backend |
| `docs/eval/results/DOCX_RUNTIME_VERIFY_24B2_AFTER_NOMIC_FIX_20260527.md` | baseline tham chiếu | pattern verify trước đó + evidence style |
| `reports/refactor/CURSOR_REPORT_24B2_DOCX_RUNTIME_VERIFY_AFTER_NOMIC_FIX.md` | baseline report style | giữ tính trung thực PASS/FAIL |

## 7. Danh sách file đã sửa

| Path | Sửa để làm gì | Ảnh hưởng lớp |
|---|---|---|
| `docs/eval/results/RAG_CONTEXT_HK_SPLIT_CELLS_JSON_VERIFY_24D0_20260527.md` | cập nhật report theo run `RAG_CONTEXT_HOC_KY` | docs |
| `reports/refactor/CURSOR_REPORT_24D0_RAG_CONTEXT_HK_SPLIT_CELLS_JSON_VERIFY.md` | cập nhật kỹ thuật theo evidence mới | docs |
| `docs/TASK_24D0_RAG_CONTEXT_HK_SPLIT_CELLS_JSON_VERIFY_REPORT.md` | cập nhật summary report bắt buộc | docs |

Không có file production code nào được sửa.

## 8. Diff thay đổi của từng file

### 8.1 `docs/eval/results/RAG_CONTEXT_HK_SPLIT_CELLS_JSON_VERIFY_24D0_20260527.md`

- Hiện trạng cũ liên quan bug: report đang phản ánh run fail quota.
- Đã sửa:
  - đổi evidence sang file mới `RAG_CONTEXT_HOC_KY`.
  - cập nhật ingest PASS (`INDEXED`) và counts đồng nhất.
  - cập nhật runtime Q1-Q8 đã chạy.
- Vì sao sửa như vậy: user yêu cầu reingest + retest + sửa report.
- Ảnh hưởng sau sửa: docs phản ánh đúng run hiện tại.

```diff
- Final verdict: FAIL
+ Final verdict: PARTIAL
- document: ...RAG_CONTEXT_HK_SPLIT.docx
+ document: ...RAG_CONTEXT_HOC_KY.docx
- DB/Qdrant/API: 3227/0/0
+ DB/Qdrant/API: 3296/3296/3296
- Q1-Q8: NOT RUN
+ Q1-Q8: RUN (Q3,Q4,Q7,Q8 PASS; Q1,Q2,Q5,Q6 PARTIAL)
```

### 8.2 `reports/refactor/CURSOR_REPORT_24D0_RAG_CONTEXT_HK_SPLIT_CELLS_JSON_VERIFY.md`

- Hiện trạng cũ liên quan bug: report đang mô tả quota block.
- Đã sửa: cập nhật root cause mới là strictness runtime/retrieval, không còn quota blocker.
- Vì sao sửa như vậy: đồng bộ với run reingest mới.
- Ảnh hưởng sau sửa: tăng tính chính xác audit.

```diff
- root cause: quota exceeded
+ root cause: runtime strictness (Q1/Q2/Q5/Q6 PARTIAL)
- ingest: FAIL
+ ingest: PASS (INDEXED)
```

### 8.3 `docs/TASK_24D0_RAG_CONTEXT_HK_SPLIT_CELLS_JSON_VERIFY_REPORT.md`

- Hiện trạng cũ: summary đang là FAIL do quota.
- Đã sửa: đổi sang summary run mới (PARTIAL, ingest pass, runtime partial).
- Vì sao sửa: user yêu cầu sửa report 24D0 theo run mới.
- Ảnh hưởng: docs only.

```diff
- verdict: FAIL
+ verdict: PARTIAL
- root cause: Nomic quota
+ root cause: strictness/runtime quality for selected questions
```

## 9. Ảnh hưởng sau sửa

- Behavior thay đổi:
  - Không đổi production code.
  - Report now reflects successful ingest with new file.
- Behavior giữ nguyên:
  - parser/normalizer architecture và retrieval pipeline.
- Điều kiện bật:
  - runtime check chỉ valid khi document INDEXED (đã đạt).
- Fallback giữ:
  - docx markdown bridge vẫn 0.
- Tài nguyên:
  - reingest tạo 3296 chunks + 3296 vectors.
- Dữ liệu MySQL/Qdrant:
  - thêm chatbot/document mới của run reingest.

## 10. Edge cases đã xem xét

- DOCX mới ingest pass toàn pipeline.
- `valuesDroppedCount=0`, `docxTablesUsingMarkdownBridge=0`.
- `group_context` vẫn rỗng (`0` rows populated).
- Runtime còn sai định dạng/strictness ở vài câu dù source có.

## 11. Kết quả kiểm tra

| Command | Kết quả | Ghi chú |
|---|---|---|
| `docker compose ps` | PASS | backend/mysql/qdrant running |
| `docker compose logs backend --tail=...` | PASS | thấy parse metrics + embed success |
| `Test-Path docs\\eval\\manual\\...RAG_CONTEXT_HOC_KY.docx` | PASS | file tồn tại |
| `POST /api/chatbots` | PASS | tạo chatbot reingest thành công |
| `POST /api/documents/upload?chatbotId=...` | PASS | status INDEXED, chunkCount 3296 |
| `GET /api/documents?chatbotId=...` | PASS | xác nhận documentId, status, API chunkCount |
| MySQL `documents` row audit | PASS | type DOCX, status COMPLETED, chunk_count 3296 |
| MySQL `document_chunks` count/type audit | PASS | 3296 chunks; 3078 normalized rows |
| Qdrant count by `documentId` | PASS | points=3296 |
| Runtime Q1-Q8 UTF-8 | PASS (executed) | Q3/Q4/Q7/Q8 PASS, Q1/Q2/Q5/Q6 PARTIAL |
| `cd Backend && ./mvnw -DskipTests compile` | NOT RUN | verify-only, không sửa code |
| `cd Backend && ./mvnw test` | NOT RUN | verify-only, không sửa code |
| `cd Frontend && npm run lint` | NOT RUN | ngoài scope task |
| `cd Frontend && npm run build` | NOT RUN | ngoài scope task |
| `cd Frontend && npm run build:widget` | NOT RUN | ngoài scope task |
| `docker compose config` | NOT RUN | không bắt buộc thêm trong verify-only này |

## 12. Rủi ro còn lại

1. Q1/Q2/Q5/Q6 còn PARTIAL dù ingest đã pass.
2. Một số source top-1 chưa phải row tốt nhất, ảnh hưởng phrasing answer.
3. `group_context` chưa populate, có thể giảm khả năng strict-scope.

## 13. Đề xuất tiếp theo

- **Task kế tiếp đề xuất:** strict-scope tuning cho list questions (Q5/Q6) và schedule field rendering (Q1/Q2), chỉ sau khi audit source ordering.
- Tiếp tục không hardcode domain values trong production.

