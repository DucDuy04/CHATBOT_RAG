# CURSOR_REPORT_24B2_DOCX_RUNTIME_VERIFY_AFTER_NOMIC_FIX

## 1. Mức độ hiểu task

- Hiểu task: **100%**.
- Chắc chắn: cần rerun runtime verify sau khi key/env đã trùng, chỉ sửa code nếu runtime chứng minh bug.
- Giả định: key mới đã usable do ingest rerun thành công.
- Thiếu dữ kiện: không có dashboard billing Nomic, chỉ xác minh qua runtime.

## 2. Tóm tắt yêu cầu

- Rerun 24B2 end-to-end với DOCX thật.
- Kiểm tra ingest + DB/Qdrant.
- Inspect `cells_json` schedule/curriculum.
- Chạy Q1-Q8 và cập nhật lại reports.

## 3. Hiện trạng trước khi sửa

- Báo cáo 24B2 trước đó đang FAIL vì quota.
- User xác nhận key đã đồng bộ.
- Cần chạy lại toàn bộ verify path.

## 4. Nguyên nhân gốc xác nhận từ source

Nguyên nhân FAIL cũ (quota) đã không còn xảy ra trong rerun.

Root cause còn lại của PARTIAL:
- Q5/Q6 vẫn chưa khóa scope cohort/major/semester đủ chặt trong retrieval+reasoning.
- Đây là chất lượng retrieval context, không phải lỗi parse DOCX hay lỗi quota.

## 5. Chiến lược sửa đã chọn

- Không sửa production code.
- Rerun runtime thực:
  1. verify env/container key
  2. tạo chatbot mới
  3. upload DOCX thật và chờ `INDEXED`
  4. audit MySQL + Qdrant + log metrics
  5. chạy Q1-Q8 bằng UTF-8 runner Node
  6. cập nhật 2 reports

## 6. Danh sách file đã đọc

| Path | Đọc để làm gì | Kết luận |
|---|---|---|
| `.env` | Xác minh key hiện tại | key đồng bộ với container |
| `Backend/src/main/java/.../dto/ChatRequest.java` | Kiểm tra request fields | có `topK`, `temperature`, `maxTokens` |
| `Backend/src/main/java/.../dto/PlaygroundChatRequest.java` | Kiểm tra format `/api/playground/chat` | cần `chatbotId`, `message`, `sessionId` |
| `Backend/src/main/java/.../api/PlaygroundController.java` | xác nhận endpoint và debug sources | playground bật `playgroundDebugSources=true` |

## 7. Danh sách file đã sửa

| Path | Sửa để làm gì | Ảnh hưởng lớp |
|---|---|---|
| `docs/eval/results/_run_24b2_q1q8.py` | runner thử nghiệm UTF-8 ban đầu | docs/test |
| `docs/eval/results/_run_24b2_q1q8.ps1` | runner PowerShell | docs/test |
| `docs/eval/results/_run_24b2_q1q8.mjs` | runner UTF-8 chính thức dùng Node | docs/test |
| `docs/eval/results/DOCX_RUNTIME_VERIFY_24B2_AFTER_NOMIC_FIX_20260527.md` | cập nhật báo cáo kết quả rerun | docs |
| `reports/refactor/CURSOR_REPORT_24B2_DOCX_RUNTIME_VERIFY_AFTER_NOMIC_FIX.md` | cập nhật report kỹ thuật | docs |

Không có thay đổi production code.

## 8. Diff thay đổi của từng file

### `docs/eval/results/DOCX_RUNTIME_VERIFY_24B2_AFTER_NOMIC_FIX_20260527.md`

- Hiện trạng cũ: verdict FAIL do quota.
- Đã sửa:
  - cập nhật rerun thành công ingest (`INDEXED`)
  - thêm DB/Qdrant thống nhất 3283
  - thêm Q1-Q8 runtime verdict mới
  - cập nhật verdict chung PARTIAL (Q5/Q6)
- Vì sao: phản ánh đúng kết quả runtime mới.

```diff
- Final verdict: FAIL
- Q1-Q8: NOT RUN
+ Final verdict: PARTIAL
+ documentId: 1dae2e0b-e740-42e2-93e2-39b0934d1e80 (INDEXED)
+ API/DB/Qdrant: 3283/3283/3283
+ Q1 PASS, Q2 PASS, Q3 PASS, Q4 PASS, Q5 PARTIAL, Q6 PARTIAL, Q7 PASS, Q8 PASS
```

### `reports/refactor/CURSOR_REPORT_24B2_DOCX_RUNTIME_VERIFY_AFTER_NOMIC_FIX.md`

- Hiện trạng cũ: report theo trạng thái fail trước rerun.
- Đã sửa: thay toàn bộ theo kết quả rerun thành công ingest + runtime table mới.
- Vì sao: đồng bộ technical report với evidence mới.

```diff
- Root cause: Nomic quota block
+ Root cause hiện tại: scoped-context Q5/Q6 còn nhiễu
- Verdict: FAIL
+ Verdict: PARTIAL
```

### `docs/eval/results/_run_24b2_q1q8.mjs`

- Thêm runner Node UTF-8 để gọi `/api/playground/chat`.
- Parse SSE token/done, lưu JSON kết quả + raw log.

```diff
+ fetch /api/playground/chat with UTF-8 body
+ parse event:token + event:done
+ save _run_24b2_q1q8_results_utf8.json
```

### `docs/eval/results/_run_24b2_q1q8.ps1` / `_run_24b2_q1q8.py`

- Script hỗ trợ chạy thử và chẩn đoán.
- Không ảnh hưởng runtime production.

## 9. Ảnh hưởng sau sửa

- Behavior đổi: không đổi production; chỉ cập nhật tài liệu + script verify.
- Behavior giữ nguyên:
  - DOCX parser path
  - embedding provider (Nomic)
  - PDF/TXT ingest paths
- Điều kiện bật:
  - chỉ khi key usable thì ingest mới `INDEXED`.
- Fallback giữ:
  - DOCX không dùng markdown bridge.
- Tài nguyên:
  - ingest DOCX ~56.8s.
  - Q1-Q8 latency ~5s đến ~11s.
- MySQL/Qdrant:
  - thêm document mới + 3283 chunks + 3283 points.

## 10. Edge cases đã xem xét

- sessionId playground phải là UUID (đã gặp lỗi và sửa runner).
- UTF-8 serialization cho câu hỏi tiếng Việt.
- Qdrant filter theo `documentId`.
- `rowsWithGenericColumnKeys` vẫn cao (550), nhưng valuesDropped=0.
- Q8 out-of-scope handling.

## 11. Kết quả kiểm tra

| Command | Kết quả | Ghi chú |
|---|---|---|
| `docker compose ps` | PASS | backend/mysql/qdrant running |
| `docker exec chatbot-backend printenv NOMIC_API_KEY` | PASS | key trùng `.env` |
| `POST /api/chatbots` | PASS | chatbot rerun tạo thành công |
| `POST /api/documents/upload` DOCX thật | PASS | status INDEXED, chunkCount 3283 |
| MySQL `documents` + `document_chunks` audit | PASS | COMPLETED, 3283 chunks |
| Qdrant points count | PASS | 3283 points |
| `docker compose logs backend ... metrics` | PASS | docx metrics đúng kỳ vọng |
| `node docs/eval/results/_run_24b2_q1q8.mjs` | PASS | Q1-Q8 executed |
| `cd Backend && ./mvnw -DskipTests compile` | NOT RUN | không sửa production code |
| `cd Backend && ./mvnw test` | NOT RUN | không sửa production code |
| `cd Frontend && npm run lint` | NOT RUN | ngoài scope |
| `cd Frontend && npm run build` | NOT RUN | ngoài scope |
| `cd Frontend && npm run build:widget` | NOT RUN | ngoài scope |
| `docker compose config` | NOT RUN | không cần thêm cho scope này |

## 12. Rủi ro còn lại

- Q5/Q6 vẫn PARTIAL vì scoped-context K46/HK2 chưa ổn định.
- `rowsWithGenericColumnKeys=550` vẫn là tín hiệu cần theo dõi cho quality retrieval.

## 13. Đề xuất tiếp theo

Một task tiếp theo duy nhất:

- **Scoped-context extraction for cohort/major/semester**
  - tăng độ chính xác truy vấn list theo K46/HK2
  - không hardcode domain literals

# CURSOR_REPORT_24B2_DOCX_RUNTIME_VERIFY_AFTER_NOMIC_FIX

## 1. Mức độ hiểu task

- Hiểu task: **100%**.
- Chắc chắn:
  - Đây là task runtime verify-only sau 24B, không được sửa production code nếu chưa có bug chính xác.
  - Bắt buộc xác nhận Nomic quota remediation trước khi đánh giá DOCX runtime Q1-Q8.
  - Khi ingest failed lại ở quota thì phải dừng và chẩn đoán.
- Giả định:
  - Nomic key trong `.env` có thể đã thay nhưng account vẫn chưa có quota thực dùng được.
- Thiếu dữ kiện:
  - Không có quyền xem dashboard billing Nomic, chỉ suy ra từ API error.

## 2. Tóm tắt yêu cầu

- Resume 24B sau khi xử lý quota Nomic.
- Re-upload DOCX thật vào chatbot mới.
- Audit DB/Qdrant, metrics, `cells_json`, so sánh DOCX vs PDF.
- Chạy Q1-Q8 bằng UTF-8 runner.
- Chỉ sửa code nếu runtime chứng minh bug cụ thể.

## 3. Hiện trạng trước khi sửa

- 24B đã xác nhận:
  - DOCX parse/chunk PASS.
  - Embedding FAIL do quota.
  - Q1-Q8 chưa chạy.
- Hạ tầng đang chạy:
  - backend up
  - mysql healthy
  - qdrant up
- `.env` có Nomic key nhưng chưa chứng minh quota usable.

## 4. Nguyên nhân gốc xác nhận từ source

Nguyên nhân gốc của thất bại 24B2 vẫn là external quota:

```text
status code: 400; body: {"detail":"You have exceeded your 10000000 free tokens of Nomic Embedding API usage."}
```

Xác nhận từ runtime logs backend ở đúng lần upload 24B2.

Không có bằng chứng bug parser DOCX:
- `docxTablesDetected=179`
- `docxTablesNormalized=179`
- `docxTablesUsingRawTableModel=179`
- `docxTablesUsingMarkdownBridge=0`
- `docxTableRowsNormalized=3094`
- `valuesDroppedCount=0`
- `rawTableCellsMissingCoordinates=0`

## 5. Chiến lược sửa đã chọn

Theo đúng constraint verify-only:
1. Không sửa production code.
2. Chạy runtime kiểm chứng đầy đủ đến điểm block.
3. Thu thập evidence từ API, MySQL, Qdrant, backend logs.
4. Nếu ingest failed vì quota thì dừng Q1-Q8 và ghi diagnosis trung thực.

## 6. Danh sách file đã đọc

| Path | Đọc để làm gì | Kết luận chính |
|---|---|---|
| `docs/eval/results/DOCX_RUNTIME_VERIFY_24B_20260527.md` | Nắm baseline 24B | Parse/chunk pass, block ở quota |
| `reports/refactor/CURSOR_REPORT_24B_DOCX_RUNTIME_VERIFY.md` | Nắm root cause 24B | External quota, không phải code bug |
| `docs/eval/results/DOCX_INGEST_SUPPORT_24A_20260527.md` | Nắm scope DOCX support | DOCX path đã hoàn chỉnh |
| `reports/refactor/CURSOR_REPORT_24A_DOCX_INGEST_SUPPORT.md` | Kiểm tra thay đổi 24A | Không cần sửa thêm trong 24B2 |
| `reports/refactor/CURSOR_REPORT_23P2_FRESH_RUNTIME_VERIFY_AFTER_HEADER_SLOT_FIX.md` | Baseline PDF runtime | Dùng để so quality |
| `agent.md` | Nắm kiến trúc tổng quan | Multi-tenant, MySQL + Qdrant |
| `agent/01-overview.md` | Rule/flow vận hành | RAG pipeline xác nhận |
| `agent/02-architecture.md` | Schema/payload/retrieval | Chọn đúng điểm audit DB/Qdrant |
| `.env` | Kiểm tra Nomic key hiện hành | Key có mặt nhưng quota vẫn fail |

## 7. Danh sách file đã sửa

| Path | Sửa để làm gì | Ảnh hưởng lớp |
|---|---|---|
| `docs/eval/results/DOCX_RUNTIME_VERIFY_24B2_AFTER_NOMIC_FIX_20260527.md` | Ghi toàn bộ runtime evidence + verdict | docs |
| `reports/refactor/CURSOR_REPORT_24B2_DOCX_RUNTIME_VERIFY_AFTER_NOMIC_FIX.md` | Report kiểm chứng theo template bắt buộc | docs |

Không có file production code nào được sửa.

## 8. Diff thay đổi của từng file

### 8.1 `docs/eval/results/DOCX_RUNTIME_VERIFY_24B2_AFTER_NOMIC_FIX_20260527.md`

- Hiện trạng cũ: chưa có file.
- Đã sửa: tạo mới report eval 24B2 đầy đủ step-by-step (infra, upload, ingest audit, metrics, DB/Qdrant, cells_json, verdict).
- Vì sao: đáp ứng yêu cầu output file kết quả runtime 24B2.
- Ảnh hưởng: không đổi runtime, tăng khả năng audit.

```diff
++ docs/eval/results/DOCX_RUNTIME_VERIFY_24B2_AFTER_NOMIC_FIX_20260527.md
@@
+ Final Verdict: FAIL
+ chatbotId: 628a534a-d923-48f8-afce-855c85382e0f
+ documentId: 0038bd29-9bf8-4764-bcf0-9d0427fb41f8
+ docxTablesDetected=179
+ docxTablesNormalized=179
+ docxTablesUsingMarkdownBridge=0
+ Qdrant points=0
+ Q1-Q8: NOT RUN (blocked)
```

### 8.2 `reports/refactor/CURSOR_REPORT_24B2_DOCX_RUNTIME_VERIFY_AFTER_NOMIC_FIX.md`

- Hiện trạng cũ: chưa có file.
- Đã sửa: tạo report refactor theo format 13 mục bắt buộc.
- Vì sao: đáp ứng rule report nội bộ.
- Ảnh hưởng: không đổi runtime/code, tăng tính truy vết.

```diff
++ reports/refactor/CURSOR_REPORT_24B2_DOCX_RUNTIME_VERIFY_AFTER_NOMIC_FIX.md
@@
+ ## 4. Nguyên nhân gốc xác nhận từ source
+ status code: 400 ... exceeded your 10000000 free tokens ...
+ ## 11. Kết quả kiểm tra
+ upload DOCX: FAIL
+ Q1-Q8: NOT RUN
```

## 9. Ảnh hưởng sau sửa

- Behavior thay đổi: **không có** (không sửa production code).
- Behavior giữ nguyên:
  - DOCX parser/chunk path hoạt động.
  - Embedding vẫn dùng Nomic.
  - PDF/TXT luồng hiện hữu không bị đổi.
- Chỉ bật khi đủ điều kiện:
  - Chỉ khi Nomic quota khả dụng thì mới reach Qdrant upsert.
- Fallback giữ nguyên:
  - Không switch provider, không markdown bridge cho DOCX.
- Tài nguyên:
  - CPU/RAM dùng cho parse/chunk vẫn như 24B.
  - Disk tăng do có chunk records trong DB dù document FAILED.
  - Latency upload ~50.7s trước khi fail ở embedding.
- Ảnh hưởng MySQL/Qdrant:
  - MySQL có 3283 chunk rows cho document FAILED.
  - Qdrant có 0 points cho document đó.

## 10. Edge cases đã xem xét

- Backend restart thành công nhưng quota vẫn fail.
- Upload DOCX lớn (~12.3MB) không gây OOM.
- Parse DOCX pass, nhưng embedding fail giữa pipeline.
- `table_row_group` vẫn 0.
- `text_table_like` vẫn 0.
- `valuesDroppedCount` = 0.
- `rawTableCellsMissingCoordinates` = 0.
- Qdrant payload không tồn tại khi embedding fail.

## 11. Kết quả kiểm tra

| Command | Kết quả | Ghi chú |
|---|---|---|
| `docker compose ps` | PASS | backend/mysql/qdrant đều up |
| `docker compose logs backend --tail=200` | PASS | backend chạy, thấy quota error khi ingest |
| `POST /api/chatbots` tạo `24B2-docx-verify` | PASS | chatbotId tạo thành công |
| `POST /api/documents/upload` với DOCX thật | FAIL | trả 500, root cause quota Nomic |
| API documents list check | PASS | documentId 24B2 ghi nhận `DOCX`, `FAILED` |
| MySQL `documents` row audit | PASS | status=FAILED, chunk_count NULL |
| MySQL `document_chunks` count | PASS | 3283 rows tồn tại |
| MySQL chunk type distribution | PASS | 3094 normalized + 179 summary + 8 text + 2 section |
| Qdrant points count theo documentId | PASS (audit), FAIL (ingest) | count=0 |
| Qdrant scroll theo documentId | PASS (audit), FAIL (ingest) | payload rỗng |
| Q1-Q8 runtime | NOT RUN | theo rule: stop khi ingest FAILED lại |
| `cd Backend && ./mvnw -DskipTests compile` | NOT RUN | verify-only, không sửa code |
| `cd Backend && ./mvnw test` | NOT RUN | verify-only, không sửa code |
| `cd Frontend && npm run lint` | NOT RUN | không thuộc scope |
| `cd Frontend && npm run build` | NOT RUN | không thuộc scope |
| `cd Frontend && npm run build:widget` | NOT RUN | không thuộc scope |
| `docker compose config` | NOT RUN | không bắt buộc thêm vì services đã up ổn |

## 12. Rủi ro còn lại

1. Quota/billing Nomic chưa usable => mọi ingest mới vẫn có thể fail.
2. Tình trạng DB có chunks trong khi document FAILED có thể gây khó audit nếu không lọc theo status.
3. Chưa thể đánh giá Q5/Q6 retrieval/prompt/runtime vì chưa có index.

## 13. Đề xuất tiếp theo

Chỉ đề xuất **một task**:

`NOMIC_BILLING_REMEDIATION_AND_RETRY_24B2`

- Xác nhận billing active thực tế trên key đang dùng hoặc thay key mới thực sự còn quota.
- Recreate backend.
- Re-run lại 24B2 từ bước upload với chatbot/doc mới.
- Chỉ sau khi status=INDEXED mới tiếp tục Q1-Q8.

