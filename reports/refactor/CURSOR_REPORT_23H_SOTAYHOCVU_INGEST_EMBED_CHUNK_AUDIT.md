# CURSOR REPORT — 23H SoTayHocVu PDF Ingest / Embed / Chunk Audit

## 1. Mức độ hiểu task

| Hạng mục | Nội dung |
|----------|----------|
| **% hiểu** | **95%** |
| **Chắc chắn** | VERIFY ONLY; upload PDF 138 trang; audit parse/chunk/embed/Qdrant; smoke Playground; không sửa code |
| **Giả định** | Docker local = proxy production yếu; keyword smoke = proxy human judge |
| **Thiếu dữ kiện** | Không chạy UI Playground thủ công; không OCR so sánh từng cell TKB |

---

## 2. Tóm tắt yêu cầu

Kiểm tra end-to-end ingest `SoTayHocVu-HocKy1-NamHoc20252026 (1).pdf`: upload → parse → chunk → embed → Qdrant → smoke 22 câu; ghi report PASS/PARTIAL/FAIL.

---

## 3. Hiện trạng trước khi sửa

- File PDF có trong `docs/eval/manual/`.
- Chưa có chatbot/index riêng cho 23H trong workspace (đã tạo mới lúc verify).

---

## 4. Nguyên nhân gốc xác nhận từ source

**Không sửa code** — chỉ quan sát:

1. **Ingest thành công:** `DocumentService` + log `Đã lưu 1312 vectors vào Qdrant`.
2. **Table noise:** `DocumentParserService` log **1028 Table REJECTED** vs **104 ACCEPTED** → PDF bảng/screenshot khó Tabula; chunk fallback `table_row_group` (515).
3. **Section title sai ngữ cảnh:** `ChunkingService2` gán `section_title` từ dòng bảng (vd `461 KTR3185...`) — thấy trong MySQL và source Playground.
4. **Retrieval miss lịch:** Chunk có `29/12/2025` (53 rows) nhưng Q5/Q6/Q15 trả “không tìm thấy” → vấn đề **retrieval/rerank/topN**, không phải chunkCount=0.
5. **Q3 sai ngày:** Có chunk `30/06/2025` nhưng model trả “16 đến 16” — generation hoặc context sai section.

---

## 5. Chiến lược sửa đã chọn

**Không sửa** — chỉ:

- `docker compose up --build -d`
- Upload qua `POST /api/documents/upload`
- MySQL + Qdrant scroll + docker logs
- Playground SSE smoke + `/api/chat` OOS

---

## 6. Danh sách file đã đọc

| Path | Mục đích | Kết luận chính |
|------|----------|----------------|
| `agent/03-backend.md` | Upload flow | POST upload → parse → chunk → embed |
| `docs/eval/RAG_EVALUATION_RUNBOOK.md` | Quy trình eval | Playground/chat pattern |
| `docs/eval/results/RAG_PDF_CROSS_PAGE_TABLE_RUNTIME_VERIFY_23B2_20260515.md` | Mẫu report | Script PS + SQL + Qdrant |
| `Backend/.../DocumentParserService.java` | Parse PDF | 138 pages, Tabula + text layer |
| `Backend/.../DocumentChunk.java` | Schema chunk | page_start, section_title, chunk_type |
| `Backend/.../EmbeddingService.java` | Payload Qdrant | document_id, text_segment, page_* |
| `application-docker.yml` | Config | Nomic 768, collection `documents` |
| `docs/eval/results/_run_23b2_pdf_cross_page_verify.ps1` | Template script | Multipart upload + poll |

---

## 7. Danh sách file đã sửa

| Path | Mục đích | Layer |
|------|----------|-------|
| `docs/eval/results/_run_23h_sotayhocvu_audit.ps1` | Script audit (tạo mới) | test/docs |
| `docs/eval/results/_run_23h_continue.ps1` | Tiếp tục audit sau INDEXED | test/docs |
| `docs/eval/results/_run_23h_smoke_cases.json` | Câu smoke UTF-8 | test/docs |
| `docs/eval/results/_run_23h_oos_cases.json` | OOS UTF-8 | test/docs |
| `docs/eval/results/_run_23h_results.json` | Artifact kết quả | test/docs |
| `docs/eval/results/SOTAYHOCVU_INGEST_EMBED_CHUNK_AUDIT_23H_20260521.md` | Kết quả chính thức | docs |
| `reports/refactor/CURSOR_REPORT_23H_SOTAYHOCVU_INGEST_EMBED_CHUNK_AUDIT.md` | Report rule 90 | docs |

**Không sửa** Java, Frontend, parser, chunking, retrieval, config prod.

---

## 8. Diff thay đổi của từng file

Chỉ **file mới** (verify artifacts). Không có diff Java/FE.

```diff
+ docs/eval/results/_run_23h_*.ps1 / *.json
+ docs/eval/results/SOTAYHOCVU_INGEST_EMBED_CHUNK_AUDIT_23H_20260521.md
+ reports/refactor/CURSOR_REPORT_23H_SOTAYHOCVU_INGEST_EMBED_CHUNK_AUDIT.md
```

---

## 9. Ảnh hưởng sau sửa

| Behavior | Thay đổi |
|----------|----------|
| Production code | **Không đổi** |
| DB/Qdrant | +1 chatbot, +1 document, +1312 chunks, +1312 Qdrant points (tenant 23H) |
| Latency/cost | ~3.6 min ingest + ~22 LLM smoke calls (local) |

---

## 10. Edge cases đã xem xét

- File 10.6 MB, 138 trang — **OK**
- Tabula reject hàng loạt — **có**
- Chunk 1 ký tự — **có** (min length)
- Embed 1312 batch — **OK**, không rate limit log
- Qdrant filter document+widget — **1312 points**
- OOS Q21 — **không bịa**; Q22 vẫn 1 source

---

## 11. Kết quả kiểm tra

| Command | Kết quả | Ghi chú |
|---------|---------|---------|
| `docker compose up --build -d` | **PASS** | |
| `docker compose config -q` | **PASS** | |
| `curl .../api/chatbots?page=0&size=1` | **PASS** | |
| `curl .../6333/collections` | **PASS** | |
| Upload + INDEXED | **PASS** | 1312 chunks |
| MySQL chunk audit | **PASS** | |
| Qdrant scroll count | **PASS** | 1312 = 1312 |
| Smoke 22 câu | **PARTIAL** | 13 PASS |
| `mvnw test` | **NOT RUN** | verify-only |

---

## 12. Rủi ro còn lại

- Retrieval lịch/văn bản ngắn kém dù ingest đủ chunk.
- Section title từ row bảng gây nhiễu vector.
- Table structure mất khi Tabula reject.
- Production DB/Qdrant thêm ~1312 points (tenant test).

---

## 13. Đề xuất tiếp theo

1. Task **retrieval** (không đổi parser): tăng topN cho câu lịch; kiểm tra chunk chứa `29/12/2025` có vào final context không.
2. Task **chunking/section**: tách section title khỏi table row prefix.
3. Re-run smoke sau fix với cùng `CHATBOT_ID` hoặc tenant sạch.
4. Manual spot-check 3 trang: bìa, biểu đồ học tập, TKB p.64 trong UI Playground.

---

## Kết luận PASS/PARTIAL/FAIL

**PARTIAL** — ingest/embed/chunk count **PASS**; chất lượng bảng + smoke lịch/văn bản **chưa PASS**.

---

## Có sửa code không

**Không.**
