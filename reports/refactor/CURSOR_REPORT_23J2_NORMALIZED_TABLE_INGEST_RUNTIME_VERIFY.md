# CURSOR REPORT — 23J2 Normalized Table Ingest Runtime Verify

**Date:** 2026-05-23  
**Verdict:** **PARTIAL**

---

## 1. Mức độ hiểu task

- **Hiểu task:** 97%
- **Chắc chắn:** VERIFY ONLY; re-ingest SoTay mới; audit chunk/Qdrant/QA Q1–Q8; không sửa Java/FE; tạo 2 report files.
- **Giả định:** Baseline old counts từ 23H2/23I2 reports (784 chunks, 517 table_row_group).
- **Thiếu:** Không có — đã chạy live stack sau khi inject User env API keys.

---

## 2. Tóm tắt yêu cầu

Runtime verify task 23J normalized table ingest: xác nhận ingest mới có `normalized_table_row`, không `table_row_group`, suppress raw table text, Qdrant payload đúng, cross-page OK, Q1–Q8, so sánh old/new — **không sửa code**.

---

## 3. Hiện trạng trước khi verify

- 23J implementation **PARTIAL** (`NORMALIZED_TABLE_INGEST_23J_20260523.md`): unit tests PASS, runtime chưa chạy.
- Docker backend ban đầu crash-loop vì thiếu `GROQ_API_KEY`/`NOMIC_API_KEY` trong shell compose (không có root `.env`).

---

## 4. Nguyên nhân gốc xác nhận từ source

- Runtime pending vì chưa re-ingest (`CURSOR_REPORT_23J`).
- Backend down: `GroqConfig.chatModel` throws khi `GROQ_API_KEY` blank (`docker logs`).
- Raw TKB vẫn trong text: chunker vẫn tạo text segments lớn từ page layer khi Tabula markdown không cover full timetable (`ChunkingService2` + log `suppressedRawChars=0`).
- Q1–Q2 retrieval: hybrid keyword scan 2732 chunks, top sources là text mega-chunks chứa header `STT Tên lớp học phần...` — LLM refuse dù context có rows.

---

## 5. Chiến lược verify đã chọn

1. `docker compose up --build -d` + export Windows User env keys.
2. Maven Docker compile + 8 targeted test classes.
3. Tạo chatbot mới, upload SoTay PDF, poll INDEXED.
4. MySQL chunk distribution + raw text audit + Qdrant scroll/count.
5. Playground Q1–Q8 script (`_run_23j2_qa_only.mjs`) với SSE `token` event fix.
6. So sánh old/new + kết luận PASS/PARTIAL/FAIL.

---

## 6. Danh sách file đã đọc

| Path | Mục đích | Kết luận |
|------|----------|----------|
| `NORMALIZED_TABLE_INGEST_23J_20260523.md` | Baseline 23J | PARTIAL pre-runtime |
| `CURSOR_REPORT_23J_NORMALIZED_TABLE_INGEST.md` | Design | normalized path implemented |
| `HYBRID_SEARCH_V1_RUNTIME_VERIFY_23I2_20260523.md` | Old QA baseline | Q1–Q4 PASS on 784-chunk doc |
| `ChunkingService2.java` | Metrics log | suppressedRawChars in log line |
| `EmbeddingService.java` | Qdrant payload | cells_json for normalized_table_row |
| `TableIngestMetrics.java` | Metric fields | tallyFromChunks |

---

## 7. Danh sách file đã sửa

| Path | Mục đích | Layer |
|------|----------|-------|
| `docs/eval/results/NORMALIZED_TABLE_INGEST_RUNTIME_VERIFY_23J2_20260523.md` | Runtime results | docs |
| `reports/refactor/CURSOR_REPORT_23J2_NORMALIZED_TABLE_INGEST_RUNTIME_VERIFY.md` | Cursor report | docs |
| `docs/REPORT_23J2_NORMALIZED_TABLE_INGEST_RUNTIME_VERIFY.md` | Rule 90 audit | docs |
| `docs/eval/results/_run_23j2_*.mjs` | Verify helper scripts | docs/eval |
| `docs/eval/results/_23j2_*.{json,log,txt}` | Raw artifacts | docs/eval |

**Không sửa Java, Frontend, config committed, `.gitignore`.**

---

## 8. Diff thay đổi

Chỉ tạo file docs/eval + helper scripts — không diff source code.

---

## 9. Ảnh hưởng sau verify

- **Behavior:** Không đổi code.
- **Runtime:** +1 chatbot, +1 document, +2732 Qdrant points (tenant test).
- **Kết luận:** Ingest structure **PASS**; raw suppression + table QA **PARTIAL/FAIL**.

---

## 10. Edge cases đã xem xét

- Backend crash without API keys → fixed via User env export.
- Upload API returns array not `{items}` → script adjusted.
- SSE playground uses `event: token` + `{"token":"..."}`.
- Groq rate limit during 8 back-to-back QA calls → Q3/Q5–Q7 BLOCKED.
- Timetable audit SQL pipe-pattern returns 0 (non-markdown tables).
- `failedTables=0` but duplication via text chunks.

---

## 11. Kết quả kiểm tra

| Command | Kết quả | Ghi chú |
|---------|---------|---------|
| `docker compose up --build -d` | **PASS** | After env keys |
| `docker compose config -q` | **PASS** | |
| `curl localhost:8080/api/chatbots` | **PASS** | |
| Maven Docker compile | **PASS** | |
| Targeted tests 8 classes | **PASS** | exit 0 |
| Re-ingest SoTay INDEXED 2732 | **PASS** | |
| MySQL chunk audit | **PASS** structure | raw text leakage |
| Qdrant 2732=2732 | **PASS** | |
| Runtime Q1–Q8 | **PARTIAL** | 1 PASS, 3 FAIL, 4 BLOCKED |
| Frontend lint/build | **NOT RUN** | |

---

## 12. Rủi ro còn lại

1. **51+ text chunks** vẫn chứa raw timetable — retrieval regression vs 23I2.
2. **2732 chunks** → keyword scan latency/cost tăng (~3.5× old doc).
3. Groq rate limit khi matrix QA nhanh — cần delay/retry.
4. `suppressedRawChars=0` metric không phản ánh leakage thực tế trên SoTay.

---

## 13. Đề xuất tiếp theo

1. Task parser/chunker: suppress non-markdown timetable text trên page layer SoTay.
2. Re-run 23J2 QA sau fix với delay 30s/query.
3. Boost retrieval rank `normalized_table_row` over text mega-chunks for label queries.

---

## Phạm vi đã làm / không làm

| Đã làm | Không làm |
|--------|-----------|
| Docker rebuild + health | Sửa Java/FE |
| Re-ingest document mới | Migration/backfill |
| MySQL + Qdrant + logs audit | Sửa `.gitignore` |
| Playground Q1–Q8 | Compare Mode |
| Compile + targeted tests | Production deploy |

---

## Kết luận PASS/PARTIAL/FAIL

**PARTIAL** — ingest normalized structure verified; raw suppression và runtime table QA chưa đạt PASS threshold.

## Có sửa code không?

**Không** (chỉ docs/eval helper artifacts).
