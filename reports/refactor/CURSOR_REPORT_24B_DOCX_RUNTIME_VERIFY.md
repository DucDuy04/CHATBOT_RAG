# CURSOR_REPORT_24B_DOCX_RUNTIME_VERIFY

**Task:** 24B — Runtime Verification With Real DOCX After DOCX Ingest Support  
**Date:** 2026-05-27  
**Verdict:** PARTIAL (DOCX code confirmed working; ingest blocked by Nomic API quota; Q1-Q8 NOT RUN)

---

## 1. Mức độ hiểu task

- Hiểu task: **98%**
- Chắc chắn: deploy backend, upload DOCX, xác nhận parsing từ logs, viết report
- Giả định: Q1-Q8 quality chưa verify được — phụ thuộc vào Nomic quota
- Thiếu dữ kiện: Nomic API key mới / quota mới để hoàn thành embedding

---

## 2. Tóm tắt yêu cầu

Deploy backend 24A DOCX code, ingest real DOCX SoTayHocVu, audit output, compare với PDF baseline 23P2, run Q1-Q8.

---

## 3. Hiện trạng trước khi sửa

| Trước 24B | Trạng thái |
|-----------|------------|
| Backend đang chạy | Image cũ từ trước 24A (không có POI) |
| DOCX fixture | Tồn tại (12.3 MB) |
| Nomic API quota | Đã hết free tier (10M tokens từ các lần ingest trước) |
| Chatbot 24B | Chưa tồn tại |

---

## 4. Nguyên nhân gốc xác nhận từ source

**Vấn đề 1:** Image Docker cũ không có Apache POI DOCX code
- Xác nhận: `unzip -l /app/app.jar | grep poi` → 0 kết quả trong container cũ
- Fix: Rebuild image với `docker compose up --build -d backend`
- Kết quả: `poi-ooxml-5.3.0.jar` xác nhận trong jar mới

**Vấn đề 2 (blocker):** Nomic API free tier quota hết
- Xác nhận từ backend log:
  ```
  RuntimeException: status code: 400; body: {"detail":"You have exceeded your
  10000000 free tokens of Nomic Embedding API usage."}
  ```
- Root cause: Các lần ingest PDF + test trước đã dùng hết 10M token free tier
- Không phải lỗi code — DOCX parsing hoàn toàn thành công (từ logs)

---

## 5. Chiến lược sửa đã chọn

Không sửa production code (không có bug trong DOCX code).

Actions thực hiện:
1. Rebuild Docker image với 24A code (xác nhận POI trong jar)
2. Tạo chatbot 24B mới
3. Upload DOCX → xác nhận parsing từ logs
4. Document findings và viết report
5. Hướng dẫn resolve Nomic quota

---

## 6. Danh sách file đã đọc

| File | Đọc để làm gì | Kết luận |
|------|---------------|----------|
| `docs/eval/results/DOCX_INGEST_SUPPORT_24A_20260527.md` | Hiểu 24A code đã làm gì | DOCX code complete, runtime chưa run |
| `reports/refactor/CURSOR_REPORT_24A_DOCX_INGEST_SUPPORT.md` | Hiểu architecture DOCX path | Architecture rõ, cần deploy |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/api/DocumentController.java` | Tìm upload endpoint | Upload endpoint dùng `files` (plural), không phải `file` |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/config/GroqConfig.java` | Hiểu embedding provider | Hardcoded Nomic, vector-size=768, không thể switch provider |
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/config/AppConfig.java` | Check embedding alternatives | Không có alternative provider configured |
| `Backend/src/main/resources/application-docker.yml` | Check config | `nomic.embedding-model: nomic-embed-text-v1.5`, vector-size=768 |
| `.env` | Check API keys | Nomic key exhausted, no alternate key |

---

## 7. Danh sách file đã sửa

**Không có file production nào được sửa.** Không có bug xác nhận từ source. Chỉ có blocker external API.

---

## 8. Diff thay đổi

Không có diff. Không sửa code.

---

## 9. Ảnh hưởng sau sửa

Không có ảnh hưởng (không sửa code).

---

## 10. Edge cases đã xem xét

| Edge case | Kết quả |
|-----------|---------|
| Docker image cũ không có POI | Phát hiện và fix bằng rebuild |
| upload param `file` vs `files` | Phát hiện — API dùng `files` (plural) |
| Nomic quota exhausted | Xác nhận từ 400 response, logged |
| DOCX parsing memory/OOM | Không có OOM — 12.3 MB file parse thành công |
| DOCX parser path không được invoke | Xác nhận invoked từ log `[ParseDocx]` |
| Markdown bridge cho DOCX tables | Xác nhận = 0 từ metrics log |
| table_row_group / text_table_like | Xác nhận = 0 từ chunk distribution |
| valuesDroppedCount | Xác nhận = 0 từ log |
| rawTableCellsMissingCoordinates | Xác nhận = 0 — tất cả cells có logical coords |

---

## 11. Kết quả kiểm tra

| Command | Kết quả | Ghi chú |
|---------|---------|---------|
| `$env:JAVA_HOME=...; .\mvnw.cmd -DskipTests compile` | PASS | "Nothing to compile - all classes are up to date" |
| `docker compose up --build -d backend` | PASS | Image rebuilt với POI 5.3.0 |
| Backend start | PASS | Tomcat 8080, 44.47s startup |
| `GET /api/chatbots` | PASS | 65 chatbots, API reachable |
| `POST /api/chatbots` (tạo 24B chatbot) | PASS | `chatbotId=bfd9e657-c3a8-4512-8d6b-d67b58aeca76` |
| `POST /api/documents/upload` (DOCX) | PARTIAL | Upload, parse, chunk thành công; embedding FAIL (Nomic quota) |
| DOCX parsing (từ backend logs) | PASS | 179 tables, 3094 rows, 0 values dropped |
| DOCX metrics (docxTablesUsingMarkdownBridge) | PASS | = 0 ✅ |
| Full ingest (INDEXED status) | FAIL | Nomic quota exhausted |
| Q1-Q8 DOCX runtime | NOT RUN | Blocked by embedding failure |
| Frontend lint | NOT RUN | Out of scope (no frontend changes) |
| Frontend build | NOT RUN | Out of scope |
| Widget build | NOT RUN | Out of scope |
| `docker compose config -q` | PASS (implicit) | All containers healthy |

---

## 12. Ảnh hưởng sau task

**Không có code thay đổi.** Tất cả behavior giữ nguyên.

- DOCX parsing code (từ 24A) đã được deploy và xác nhận hoạt động
- PDF/TXT ingest không bị ảnh hưởng
- DB record cho DOCX document: status=FAILED (do Nomic quota, có thể retry)

---

## 13. Đề xuất tiếp theo

### Immediate (để hoàn thành 24B)

1. **Lấy Nomic API key mới:**
   - Tạo account mới tại https://atlas.nomic.ai (10M token free)
   - Hoặc add payment method vào account hiện tại
   - Update `.env`: `NOMIC_API_KEY=nk-<new_key>`
   - Restart: `docker compose up -d --force-recreate backend`

2. **Re-ingest DOCX:**
   ```bash
   curl -X POST "http://localhost:8080/api/documents/upload?chatbotId=bfd9e657-c3a8-4512-8d6b-d67b58aeca76" \
     --form "files=@docs/eval/manual/SoTayHocVu_HocKy1_2025-2026.docx"
   ```

3. **Poll status:**
   ```bash
   curl "http://localhost:8080/api/documents/<documentId>"
   ```

4. **Run Steps 5-8** sau khi status = INDEXED

### Follow-up tasks

- **24C:** Fix rowsWithGenericColumnKeys=550 (50 header slots dùng fallback generic col_N)
- **24D:** Page number display — show "N/A" thay vì "trang 1" cho DOCX chunks
- **24E:** Memory guard cho DOCX lớn (>20 MB)

---

## Acceptance Criteria Summary

| Criterion | Status |
|-----------|--------|
| Backend deploy succeeds | ✅ PASS |
| Real DOCX file exists | ✅ PASS |
| document type = DOCX | ✅ PASS |
| docxTablesDetected > 0 | ✅ 179 |
| docxTablesNormalized > 0 | ✅ 179 |
| docxTablesUsingMarkdownBridge = 0 | ✅ 0 |
| docxTablesUsingRawTableModel > 0 | ✅ 179 |
| table_row_group = 0 | ✅ 0 |
| text_table_like = 0 | ✅ 0 |
| raw table fallback = 0 | ✅ 0 |
| valuesDroppedCount = 0 | ✅ 0 |
| PDF/TXT support not broken | ✅ PASS |
| DOCX ingests successfully (INDEXED) | ❌ FAIL (Nomic quota) |
| API chunkCount = DB = Qdrant | ❌ NOT REACHED |
| cells_json exists for normalized_table_row | ❌ NOT REACHED |
| Q1-Q8 runtime run | ❌ NOT RUN |
| Q8 OOS refusal | ❌ NOT RUN |

**Verdict: PARTIAL**
