# Checklist — Baseline core RAG (G0–G6)

**Phiên bản:** 1.0 (task 21D0)  
**Cách dùng:** Điền cột **Actual**, **Status**, **Evidence** mỗi lần chạy. **Status** chỉ dùng: `TODO`, `PASS`, `PARTIAL`, `FAIL`, `BLOCKED`, `NOT_RUN`.  
**Verdict golden:** luôn đối chiếu cột `verdict` từng case (không tin tổng hợp nếu chưa reconcile) — xem `docs/eval/results/RAG_EVAL_RUN_21B_20260514.md` và recompute `docs/eval/results/RAG_RETRIEVAL_DIAG_21C_20260514.md`.

## Hướng dẫn nhanh endpoint

- `POST /api/chatbots` — tạo chatbot; lưu `id` và `apiKey` (Widget key).  
- `POST /api/documents/upload` — multipart `files`, `chatbotId`; header auth theo runbook admin nếu UI.  
- `GET /api/documents/{id}/status` — đến `INDEXED`.  
- `POST /api/chat` — header `X-Widget-Key: <apiKey>`, body `sessionId`, `message`.  
- `DELETE /api/documents/{id}` — soft delete + purge vector theo implement hiện tại.  
- `POST /api/documents/{id}/retry` — khi doc `FAILED` (G5 optional).  

Chi tiết: `docs/RAG_CORE_FLOW_E2E_RUNBOOK.md`, `docs/eval/RAG_EVALUATION_RUNBOOK.md`.

---

## Bảng checklist (tất cả item)

| ID | Gate | Check | Command / Method | Expected | Actual | Status | Evidence | Notes |
|----|------|-------|------------------|----------|--------|--------|----------|-------|
| G0-CMP-001 | G0 | Backend compile sau thay đổi Java | `cd Backend && ./mvnw -DskipTests compile` hoặc `.\mvnw.cmd -DskipTests compile` | Exit 0, BUILD SUCCESS |  | TODO |  | Bắt buộc nếu sửa backend |
| G0-DCK-001 | G0 | Docker Compose file hợp lệ | `docker compose config` từ root repo | Exit 0, không lỗi interpolate |  | TODO |  | Không in secret ra log |
| G0-ENV-001 | G0 | Groq key có cho container (hoặc dev) | Kiểm tra `.env` hoặc env compose (boolean có dòng key, không paste giá trị) | Backend chat không 401 provider |  | TODO |  | Chỉ ghi có hoặc không |
| G0-ENV-002 | G0 | Nomic key có cho embed | Giống trên cho `NOMIC_API_KEY` | Upload embed thành công |  | TODO |  |  |
| G0-SEC-001 | G0 | Không dán API key vào report hoặc checklist | Review file đính kèm | Không chuỗi key đầy đủ |  | TODO |  | Mask hoặc chỉ độ dài |
| G0-SCP-001 | G0 | Diff code khớp một mục tiêu fix | `git diff` / PR description | Không lan man nhiều subsystem |  | TODO |  | Một PR một chủ đích |
| G0-GIT-001 | G0 | Branch và commit baseline ghi nhận | Ghi trong fix-loop template | Có hash hoặc tag |  | TODO |  | Pre-snapshot |
| G1-UPL-001 | G1 | Upload golden TXT sau fix (document mới) | `POST /api/documents/upload` với `RAG_GOLDEN_TEST_DOCUMENT.txt` | HTTP 200, trả `document` id mới |  | TODO |  | Không reuse doc cũ cho G1 |
| G1-STS-001 | G1 | Trạng thái ingest hoàn tất | `GET /api/documents/{id}/status` poll | `INDEXED` (hoặc tương đương API) |  | TODO |  | Ghi `chunkCount` |
| G1-MDH-001 | G1 | Markdown `##` tạo ranh giới section đúng ý | SQL `document_sections` theo `document_id` | Có section title khớp mục 2, 3, 4 (vd chứa Chính sách, Quy trình, Bảng) không chỉ dòng list |  | TODO |  | Sau parser fix P0 |
| G1-SEC-001 | G1 | Không có section title là dòng list `1. Phản hồi yêu cầu...` (false root) | SQL `document_sections` WHERE title LIKE | 0 row (hoặc không còn pseudo-section từ list chính sách) |  | TODO |  | Tránh sec_1 giả |
| G1-SEC-002 | G1 | Không có section title bắt đầu `2. Mỗi phiên chat` như section độc lập sai ngữ cảnh | SQL tương tự | 0 row false pattern |  | TODO |  | Đồng bộ với golden |
| G1-SEC-003 | G1 | Không có section title bắt đầu `3. Không hỗ trợ can thiệp` như section cha duy nhất cho cả quy trình | SQL + review cây | Quy trình tách khỏi list item 3 |  | TODO |  | 21C root cause |
| G1-SEC-004 | G1 | Section mục Quy trình (hoặc tương đương) tồn tại | SQL `document_sections` | ≥1 row đúng heading mục 3 |  | TODO |  | Title có thể VN không dấu trong code — đối chiếu nội dung |
| G1-SEC-005 | G1 | Section mục Bảng gói dịch vụ tồn tại | SQL | ≥1 row gắn mục bảng |  | TODO |  | Cho L02, T02 |
| G1-CHK-001 | G1 | Chunk chứa `Bước 2` gắn section Quy trình (không gắn list item 3) | SQL `document_chunks` JOIN sections | `section_title` hoặc path chứa Quy trình hoặc key đúng mục 3 |  | TODO |  | Fix F04 evidence |
| G1-CHK-002 | G1 | Chunk chứa `Bước 1` và `Bước 3` cùng cây section quy trình hợp lệ | SQL các chunk text | Không orphan dưới title list giả |  | TODO |  | Metadata nhất quán |
| G1-CHK-003 | G1 | `document_chunks` có chunk gắn đúng mục chính sách cho 3 bullet | SQL: tìm chunk chứa `24 giờ làm việc` và `15 phút` | Cùng section hoặc 3 chunk con đúng mục 2 |  | TODO |  | G3 L01 |
| G1-TBL-001 | G1 | `document_tables` có đúng 1 (hoặc chuẩn) bản ghi bảng gói | SQL `document_tables` | Row markdown chứa Basic, Pro, Business |  | TODO |  |  |
| G1-TBL-002 | G1 | Table chunk `chunk_type` table_* gắn section Bảng gói dịch vụ | SQL chunks WHERE type IN (table_summary, table_row_group) | `section_id` thuộc section mục bảng |  | TODO |  | Fix L02, T02 |
| G1-TBL-003 | G1 | Không gắn table chunk vào section title nhầm `Bước 3` từ metadata cũ | SQL + `section_title` | `section_title` không phải dòng Bước 3 sai ngữ cảnh cho bảng |  | TODO |  | 21C |
| G1-QDR-001 | G1 | Qdrant point count khớp chunkCount | POST `.../collections/documents/points/count` filter `document_id` + `widgetId` | count = `chunkCount` từ API |  | TODO |  | Dùng UUID đúng tenant |
| G1-QDR-002 | G1 | Payload `section_title` trên point chứa Bảng gói cho table chunks | Qdrant scroll hoặc mẫu payload | section_title khớp mục bảng |  | TODO |  | So JSON ngắn |
| G1-QDR-003 | G1 | Payload `section_title` cho chunk có Bước 2 khớp Quy trình | Qdrant payload sample | Không phải title list `3. Không hỗ trợ...` |  | TODO |  |  |
| G1-CNT-001 | G1 | chunkCount hợp lý (không nổ số sau fix) | API status + đếm SQL | Trong khoảng mong đợi (vd 4–12 tùy parser); ghi nhận số |  | TODO |  | Không benchmark |
| G2-EBT-001 | G2 | Stack chạy | `docker compose ps` hoặc health GET chatbots | Backend healthy |  | TODO |  | Regression bắt buộc |
| G2-EBT-002 | G2 | Tạo chatbot mới cho E2E | `POST /api/chatbots` | 200, có `apiKey` |  | TODO |  | Tách tenant E2E |
| G2-EBT-003 | G2 | Upload `RAG_E2E_SAMPLE.txt` | `POST /api/documents/upload` theo runbook | INDEXED, chunk ≥1 |  | TODO |  | Sample 20G |
| G2-EBT-004 | G2 | Chat trước delete chứa magic string | `POST /api/chat` câu theo runbook | Answer chứa `RAG-E2E-31415` |  | TODO |  |  |
| G2-EBT-005 | G2 | Source trỏ đúng file sample | `sources[0].fileName` hoặc tương đương | `RAG_E2E_SAMPLE.txt` |  | TODO |  |  |
| G2-EBT-006 | G2 | Delete document E2E | `DELETE /api/documents/{id}` | 200 success |  | TODO |  |  |
| G2-EBT-007 | G2 | DB children soft-delete | SQL `document_chunks.deleted_at` (và các bảng con) | Không còn row active cho doc đó |  | TODO |  | Raw SQL nếu cần |
| G2-EBT-008 | G2 | Qdrant count 0 sau delete | count filter document + widget | 0 |  | TODO |  |  |
| G2-EBT-009 | G2 | Chat sau delete không còn source doc đã xóa | `POST /api/chat` session mới | sources rỗng hoặc không cite file sample |  | TODO |  | Giống 20G |
| G2-EBT-010 | G2 | Không regression upload lỗi khi chỉ sửa parser | Upload lại sample trên tenant sạch | INDEXED |  | TODO |  | Nếu đụng DocumentService |
| G3-F04-001 | G3 | GQ-F04 answer không no-context giả | `POST /api/chat` câu F04 | Answer chứa ý phân loại billing, technical, other và 4 giờ làm việc |  | TODO |  | Baseline 21B: FAIL |
| G3-F04-002 | G3 | GQ-F04 có source file golden | Cùng response | ≥1 source `fileName` = file golden đã upload |  | TODO |  | 21B: FAIL B |
| G3-F04-003 | G3 | GQ-F04 failure class nếu fail | Ghi nhãn | NO_CONTEXT hoặc RETRIEVAL hoặc PARSER |  | TODO |  | 21C: lock sec_1 |
| G3-L01-001 | G3 | GQ-L01 liệt kê đủ 3 chính sách | `POST /api/chat` | Đủ 3 ý: 24h làm việc, 15 phút phiên, không bên thứ ba không ủy quyền |  | TODO |  | 21B: FAIL |
| G3-L01-002 | G3 | GQ-L01 có source golden | Response | source đúng file |  | TODO |  | 21B: B pass nhưng A fail |
| G3-L01-003 | G3 | GQ-L01 nếu PARTIAL ghi lý do | Chấm A–D | GENERATION hoặc RETRIEVAL |  | TODO |  | Context nhiễu 21C |
| G3-L02-001 | G3 | GQ-L02 đủ 3 tên gói | `POST /api/chat` | Basic, Pro, Business |  | TODO |  | 21B: FAIL |
| G3-L02-002 | G3 | GQ-L02 có source | Response | source golden |  | TODO |  | 21B: FAIL B |
| G3-L02-003 | G3 | GQ-L02 nếu fail class | Tags | TABLE_PARSE hoặc RETRIEVAL |  | TODO |  | 21C table lock |
| G3-T02-001 | G3 | GQ-T02 answer chứa Ưu tiên | `POST /api/chat` | Từ đúng cột Hỗ trợ Business |  | TODO |  | 21B: FAIL |
| G3-T02-002 | G3 | GQ-T02 source golden | Response | Có source |  | TODO |  |  |
| G3-T02-003 | G3 | GQ-T02 failure class nếu fail | Tags | TABLE_PARSE, RETRIEVAL |  | TODO |  |  |
| G3-F03-001 | G3 | GQ-F03 đúng 500 và không regress xuống FAIL | `POST /api/chat` | Verdict ≥ PARTIAL; số 500 đúng |  | TODO |  | 21B: PARTIAL |
| G3-F03-002 | G3 | GQ-F03 attribution section | Review answer text | Section trích dẫn gần đúng mục bảng (không Bước 3 sai) |  | TODO |  | P1 attribution |
| G3-T01-001 | G3 | GQ-T01 đúng 99000 | `POST /api/chat` | PARTIAL hoặc PASS; không FAIL |  | TODO |  | 21B: PARTIAL |
| G3-T01-002 | G3 | GQ-T01 attribution | Review answer | Không trỏ nhầm Bước 3 cho bảng |  | TODO |  |  |
| G4-FUL-001 | G4 | Chạy đủ 12 case theo `RAG_EVALUATION_RUNBOOK.md` | 11 chat trước delete + GQ-D01 sau delete | Đủ 12 dòng bảng kết quả |  | TODO |  | Session strategy ghi rõ |
| G4-FUL-002 | G4 | Mỗi case có verdict PASS PARTIAL FAIL | Chấm theo `RAG_GOLDEN_QUESTIONS.md` | Một verdict mỗi case |  | TODO |  | Source of truth: cột verdict |
| G4-FUL-003 | G4 | Tổng hợp PASS chỉ từ verdict | Đếm tay hoặc script | Không dùng tổng 21B §5 cũ |  | TODO |  | Recompute như 21C |
| G4-FUL-004 | G4 | GQ-F01 F02 C01 O01 O02 baseline mong đợi | So sánh | Thường PASS nếu không regress |  | TODO |  | 21B đã PASS |
| G4-FUL-005 | G4 | GQ-C01 đếm 3 gói | Chấm D | Đúng 3 |  | TODO |  | 21B PASS |
| G4-FUL-006 | G4 | Sau parser fix: ngưỡng tối thiểu G1 G2 G3 (xem plan §11) | Checklist con | Đạt trước khi chốt G4 “release” |  | TODO |  | Gate cứng |
| G4-FUL-007 | G4 | Ghi retrieval vs generation count từ tags | Tổng hợp | Số case có tag |  | TODO |  | Không benchmark |
| G4-FUL-008 | G4 | Lưu file kết quả run mới | `docs/eval/results/RAG_EVAL_RUN_YYYYMMDD.md` | File tạo, không rỗng |  | TODO |  | Optional path |
| G5-DEL-001 | G5 | Delete golden document sau G4 | `DELETE /api/documents/{id}` | 200 |  | TODO |  | Trên tenant eval |
| G5-DEL-002 | G5 | GQ-D01 không cite file golden đã xóa | `POST /api/chat` session mới | Không source golden; không trả mã như doc đang phục vụ |  | TODO |  | 21B PASS — không được phá |
| G5-DEL-003 | G5 | Qdrant 0 sau delete golden | count filter | 0 |  | TODO |  | Đồng bộ purge |
| G5-RTY-001 | G5 | Retry FAILED expected (optional) | Giả lập FAILED rồi `POST /api/documents/{id}/retry` | Theo spec 20E: reprocess; không leak vector cũ |  | NOT_RUN |  | Chỉ khi chủ động test |
| G5-RTY-002 | G5 | Sau retry document hợp lệ lại INDEXED | Poll status | INDEXED hoặc FAILED có lý do rõ |  | NOT_RUN |  |  |
| G6-RES-001 | G6 | Không tạo hàng chục document cho một fix | Đếm doc mới trong phiên | ≤2–3 doc có chủ đích |  | TODO |  | Máy yếu |
| G6-RES-002 | G6 | Không chạy benchmark throughput | N/A | Không chạy k6, ab không cần |  | TODO |  | Ngoài scope |
| G6-RES-003 | G6 | SQL kiểm tra có LIMIT và điều kiện `document_id` | Review query | Không SELECT * toàn bảng chunks |  | TODO |  | Tránh full scan |
| G6-RES-004 | G6 | Log không ồ ạt document content | `docker logs` sample | Không log full PDF hoặc prompt dài |  | TODO |  | Ops |
| G6-RES-005 | G6 | Không paste secret vào evidence | Self-review | Chỉ mask hoặc boolean |  | TODO |  | Bảo mật |

---

## Số lượng item

- **Tổng dòng dữ liệu trong bảng:** 69 (≥40 theo yêu cầu).

## Tham chiếu nhanh trạng thái 21B / 21C (G3)

| Case | Verdict 21B (cột verdict) | Ghi chú 21C ngắn |
|------|---------------------------|------------------|
| GQ-F04 | FAIL | 0 context; heading lock sec_1 |
| GQ-L01 | FAIL | Context nhiễu; trả lời mâu thuẫn |
| GQ-L02 | FAIL | 0 context; table lock sec_3__dup2 |
| GQ-T02 | FAIL | 0 context |
| GQ-F03 | PARTIAL | Số đúng; section sai |
| GQ-T01 | PARTIAL | Số đúng; section sai |

---

## Phiên runtime verify **21E** (2026-05-14) — sau parser 21D1

**Evidence tổng:** `docs/eval/results/RAG_RUNTIME_VERIFY_21E_20260514.md`  
**Chatbot / document:** `186970d5-8aca-4607-9d51-c126631bbbc2` / `ad80a626-e083-495e-b70f-c8cb1ac71957` (widget key không ghi đầy đủ).

| ID | Status | Evidence ngắn |
|----|--------|----------------|
| G0-CMP-001 | PASS | `.\mvnw.cmd -DskipTests compile` |
| G0-DCK-001 | PASS | `docker compose config -q` |
| G0-ENV-001 | PASS | `docker exec` → `GROQ_PRESENT` (boolean) |
| G0-ENV-002 | PASS | `NOMIC_PRESENT` |
| G0-SEC-001 | PASS | Report + bảng này không dán key đầy đủ |
| G0-SCP-001 | PASS | Phiên 21E không đổi code; diff parser thuộc 21D1 |
| G1-UPL-001 | PASS | `POST /api/documents/upload` 200 |
| G1-STS-001 | PASS | `INDEXED`, `chunkCount=9` |
| G1-MDH-001 | PASS | Có section mục 2/3/4 từ `##` |
| G1-SEC-001 | PASS | 0 row title list giả |
| G1-SEC-002 | PASS | 0 row `2. Mỗi phiên chat…` làm section root |
| G1-SEC-003 | PASS | 0 row `3. Không hỗ trợ can thiệp…` làm section root |
| G1-SEC-004 | PASS | Có section mục Quy trình |
| G1-SEC-005 | PASS | Có section mục Bảng gói dịch vụ |
| G1-CHK-001 | PASS | Chunk Bước 2 → section mục 3 Quy trình |
| G1-CHK-002 | PASS | Bước 1–3 cùng chunk quy trình hợp lệ |
| G1-CHK-003 | PASS | Chunk 24h + 15 phút → mục 2 Chính sách |
| G1-TBL-001 | PASS | `document_tables` có Basic/Pro/Business + giá |
| G1-TBL-002 | PASS | `table_*` chunks → section mục 4 |
| G1-TBL-003 | PASS | Table chunk không gắn “Bước 3” sai |
| G1-QDR-001 | PASS | Qdrant count 9 = `chunkCount` |
| G1-QDR-002 | PASS | Payload table → section mục 4 |
| G1-QDR-003 | PASS | Payload chunk quy trình → mục 3 |
| G1-CNT-001 | PASS | 9 chunks — hợp lý sau fix |
| G3-F04-001 | PASS | Trả lời đúng phân loại + 4h LV |
| G3-F04-002 | PASS | Source file golden |
| G3-L01-001 | PASS | Đủ 3 chính sách |
| G3-L01-002 | PASS | Source golden (dù list 9 sources nhiễu) |
| G3-L02-001 | PASS | Basic, Pro, Business |
| G3-L02-002 | PASS | Source golden |
| G3-T02-001 | PASS | **Ưu tiên** |
| G3-T02-002 | PASS | Source golden |
| G3-F03-001 | PASS | **500** |
| G3-F03-002 | PASS | Section mục bảng trong footer |
| G3-T01-001 | FAIL | Model “không tìm thấy” dù context có 99000 |
| G3-T01-002 | FAIL | Attribution câu trả lời sai; sources vẫn đúng mục 4 |

**G1 gate tổng (runtime):** PARTIAL — thiếu `document_sections` cho `## 7`; chunk 8 `section_title` vẫn mục 6.  
**G3 gate tổng:** PARTIAL — 5/6 PASS; GQ-T01 FAIL.

---

*Cập nhật Status khi từng gate hoàn thành trong phiên làm việc.*
