# SoTayHocVu — Ingest / Embed / Chunk Audit 23H (VERIFY ONLY)

**Ngày:** 2026-05-21  
**Loại:** VERIFY ONLY — không sửa code  
**Artifact:** `docs/eval/manual/SoTayHocVu-HocKy1-NamHoc20252026 (1).pdf`  
**Script:** `docs/eval/results/_run_23h_sotayhocvu_audit.ps1`, `_run_23h_continue.ps1`  
**JSON:** `docs/eval/results/_run_23h_results.json`

---

## 1. Environment

| Item | Giá trị |
|------|---------|
| OS | Windows 10 |
| Stack | `docker compose up --build -d` |
| Backend | `http://localhost:8080` — health **OK** |
| Qdrant | `http://localhost:6333` — collection `documents`, vector **768** Cosine |
| MySQL | `ragchatbot-mysql` |
| `docker compose config -q` | **PASS** |
| Embedding | Nomic `nomic-embed-text-v1.5` (dim 768) |
| GROQ / NOMIC keys | Present (embed log: 1312 vectors in ~20s) |

---

## 2. File info (local)

| Field | Value |
|-------|-------|
| Path | `docs/eval/manual/SoTayHocVu-HocKy1-NamHoc20252026 (1).pdf` |
| Size | **11,113,284** bytes (~10.6 MB) |
| Readable | **Yes** |

---

## 3. Upload / index result

| Field | Value |
|-------|-------|
| Chatbot | `23H SoTayHocVu Ingest Audit` |
| `CHATBOT_ID` | `84b4a582-11c8-49d4-97e0-5cbdfab9ba83` |
| `DOCUMENT_ID` | `f7aa0197-dea2-4c82-8151-6c7ac86a4112` |
| Widget API key | `ce80...1bf7` (masked) |
| API status | **INDEXED** (`chunkCount=1312`, `progress=100`) |
| DB status | **COMPLETED** |
| Index started (DB `created_at`) | 2026-05-21 13:44:02 |
| Index finished (DB `updated_at`) | 2026-05-21 13:47:39 |
| Duration | **~218 s** (~3.6 min) |
| Embed log | `Bắt đầu embed 1312 chunks` → `Đã lưu 1312 vectors vào Qdrant` (no error) |

---

## 4. Parse / page audit

| Metric | Value |
|--------|-------|
| Expected pages | ~138 |
| `[Parse] Page` distinct (docker logs) | **138** |
| Log max page | **138** |
| SQL `MAX(page_end)` | **138** |
| SQL `MIN(page_start)` | **1** |
| Chunks `page_start IS NULL` | **0** |
| `document_sections` count | **49** |
| `document_tables` count | **49** |
| Table ACCEPTED (log lines) | **104** |
| Table REJECTED (log lines) | **1028** |
| Table MERGED continuation | **8** |

### Spot-check markers (chunk text in MySQL)

| Check | Chunks matching |
|-------|-----------------|
| `2025-2026` | 57 |
| `30/06/2025` | **2** |
| `08/09/2025` | 51 |
| `29/12/2025` | 53 |
| `09/02/2026` | 10 |
| `KTR3185` | 49 |
| `KTR3103` | 50 |
| `LLCTTH3` | 53 |
| `CNS3023` | 53 |

**Cover / TOC (manual SQL UTF-8):** Nội dung năm học và biểu đồ có trong chunk đầu (`page_start` thường 1–3); tiêu đề “SỔ TAY HỌC VỤ” phụ thuộc encoding PDF text layer (một số ký tự bị thay `?` trong console MySQL).

**Biểu đồ kế hoạch học tập:** Có chunk chứa `30/06/2025`, `08/09/2025` — ingest **có** mốc lịch; retrieval Q3 vẫn trả sai (xem §8).

**Kế hoạch đào tạo / TKB:** Nhiều chunk `table_row_group` (515) + `table_summary` (104); header cột (`TT`, `Mã học phần`, `TC HK1`…) xuất hiện trong chunk dài ~2199 chars — **có nguy cơ vỡ cột** khi Tabula reject (~1028 lần).

**Thời khóa biểu:** Chunk index 327, `page_start=64`, length ~2189, có chuỗi `THỜI KHÓA BIỂU DỰ KIẾN` — row lớp/ tiết có trong text nhưng section title thường là dòng bảng (vd `494 KTR3322 ...`) không phải heading tài liệu.

---

## 5. Chunking audit

| Metric | Value |
|--------|-------|
| **chunkCount** | **1312** |
| avg chars | **422** |
| min chars | **1** |
| max chars | **2409** |
| with `page_start` | **1312** (100%) |
| with `section_title` | **1312** (100%) |
| with `table_id` | **619** (47%) |
| chunks &lt; 50 chars | **313** |
| chunks &lt; 100 chars | **485** |
| duplicate content groups | (not re-run; script reported 49 — treat as **low confidence**) |

### Chunk types

| chunk_type | count |
|------------|------:|
| text | 651 |
| table_row_group | 515 |
| table_summary | 104 |
| text_table_like | 33 |
| section_summary | 6 |
| parent_section_summary | 3 |

### Section / heading quality

- Có section tốt: `1. Biểu đồ kế hoạch học tập...`, `2.1. Lưu ý trước khi đăng ký...`, `THỜI KHÓA BIỂU...` (trong content).
- **Rủi ro:** Nhiều `section_title` = **một dòng bảng** (vd `461 KTR3185 5 0 Nguyễn Văn Thái`, `494 KTR3322...`) → citation/retrieval nhiễu.
- **48** chunk header-like ngắn (`TC HK1` + length &lt; 200) — header lặp.

### Top largest chunks (sample)

| chunk_index | len | note |
|-------------|-----|------|
| 20 | 2409 | parent_section_summary thi/đơn |
| 22 | 2387 | parent_section_summary |
| 162, 237, 189 | ~2199 | bảng Kế hoạch đào tạo (header + rows) |
| 327 | 2189 | Thời khóa biểu |

### Sample — KTR3185 (ingest OK)

- `chunk_index=684`, `page_start=95`, `section_title=461 KTR3185 5 0 Nguyễn Văn Thái`, content có mã HP.

---

## 6. Embedding / Qdrant audit

| Metric | Value |
|--------|-------|
| Provider / model | Nomic `nomic-embed-text-v1.5` |
| Dimension | **768** |
| Collection | `documents` |
| Points for document (scroll filter) | **1312** |
| DB chunk count | **1312** |
| **DB count = Qdrant count** | **PASS** |
| `qdrant_point_id` populated in MySQL | **0 / 1312** (IDs only in Qdrant payload `chunk_id`) |
| Upsert / embed errors | **None** in ingest window |
| Indexing embed window | ~20 s (log 13:47:19 → 13:47:39) |

### Qdrant payload keys (sample)

`document_id`, `documentId`, `widgetId`, `chunk_id`, `chunkIndex`, `text_segment`, `fileName`, `page_start`, `page_end`, `section_title`, `heading_path_text`, `chunk_type`, `section_id`, `order_index`, …

**Orphan check:** `missingInQdrant=0` when comparing scroll IDs to DB (DB stores `qdrant_point_id` null — match by count + filter only).

---

## 7. Metadata audit

| Field | Present |
|-------|---------|
| documentId | Yes (payload) |
| chatbotId / widgetId | Yes |
| chunkId / chunkIndex | Yes |
| page_start / page_end | Yes (DB 100%) |
| sectionTitle | Yes (often low-quality for table rows) |
| contentType → `chunk_type` | Yes |
| table metadata → `table_id` | 619 chunks |
| content in Qdrant | `text_segment` |

**Source citation risk:** Playground sources often show `page: null` in SSE DTO though answer text cites `Trang: 16-16` from prompt context.

---

## 8. Retrieval smoke test (Playground + prod OOS)

Playground: `temperature=0.2`, `maxTokens=2048`, `playgroundDebugSources=true`.  
Prod `/api/chat`: default `topK=5` for OOS.

| id | contextTopN | verdict | notes |
|----|-------------|---------|-------|
| Q1 | 10 | PARTIAL | Đúng mục đích tổ chức học tập; thiếu “đọc kỹ” |
| Q2 | 10 | PASS | Gặp Khoa, ĐTĐH, đơn, đối thoại |
| Q3 | 10 | **FAIL** | Trả “16 đến 16”; doc có `30/06/2025` (2 chunks) |
| Q4 | 10 | PASS | `08/09/2025`, K45–K48 |
| Q5 | 10 | **FAIL** | “Không tìm thấy”; doc có `29/12/2025` (53 chunks) |
| Q6 | 10 | **FAIL** | Không tìm thấy Tết; doc có `09/02/2026` (10 chunks) |
| Q7 | 10 | **FAIL** | Không tìm thấy; mã SV ví dụ có trong PDF |
| Q8 | 10 | PASS | Quên MK → Phòng ĐTĐH&CTSV |
| Q9 | 10 | PASS | Đổi mật khẩu |
| Q10 | 20 | PASS | Chuẩn bị trước ĐKHP |
| Q11 | 10 | **FAIL** | Trả bước trước ĐKHP, không “in TKB / xét duyệt / theo dõi” |
| Q12 | 20 | PASS | Gia hạn, tuần đầu HK |
| Q13 | 10 | PASS | Website, 03 ngày |
| Q14 | 10 | PASS | 50% |
| Q15 | 10 | **FAIL** | Không tìm thấy công bố lịch thi |
| Q16 | 10 | **FAIL** | Không tìm thấy vắng thi điểm 0 |
| Q17 | 20 | PASS | KTR3185, Đồ án, 5 TC |
| Q18 | 20 | PASS | KTR3103, Quy hoạch, 3 TC |
| Q19 | 20 | PASS | LLCTTH3, Triết, 3 TC |
| Q20 | 20 | PASS | CNS3023, Miễn dịch (source section lệch nhưng fact đúng) |
| Q21 | prod | PASS | Không bịa tỷ giá; 2 sources |
| Q22 | prod | PARTIAL | Không có ABC9999; vẫn cite 1 source in-scope |

**Smoke score:** **13 PASS** / 22 (Q1 PARTIAL, Q22 PARTIAL không tính PASS).

**Nhóm:**
- Text / lịch (Q1–Q16): ~8 PASS, 1 PARTIAL, 7 FAIL → retrieval/generation, không phải thiếu ingest hoàn toàn.
- Bảng mã HP (Q17–Q20): **4/4 PASS** với topN=20.
- OOS: **PASS** (Q22 cite nhẹ).

---

## 9. Table-specific observations

1. **Tabula reject cao (1028)** trên PDF screenshot + bảng dài → phụ thuộc text layer + heuristic `table_row_group`.
2. **Merge 8** lần — cross-page một phần, không đủ cho toàn bộ Kế hoạch đào tạo.
3. **Header lặp** trong nhiều chunk (~2199 chars) — noise cho embedding.
4. **Section title = row data** — làm retrieval trả nhầm section (Q3, Q11).
5. **TKB** gom chunk lớn trang 64+; cột thứ/tiết/phòng có trong text nhưng khó QA tự động.

---

## 10. Risks (retrieval-focused)

| Risk | Severity |
|------|----------|
| Lịch/biểu đồ có trong DB nhưng **retrieval miss** (Q5,Q6,Q15,Q16) | High |
| **Sai fact lịch** dù có chunk (Q3) | High |
| Section title từ **dòng bảng** | High |
| Chunk **1 char** / 313 chunk &lt;50 chars | Medium |
| 1028 table REJECTED → mất cấu trúc cột | Medium |
| `qdrant_point_id` null trong MySQL | Low (ops/debug) |
| Playground `page` null trong sources UI | Medium (citation UX) |

---

## 11. Conclusion

| Area | Verdict |
|------|---------|
| Ingest / INDEXED | **PASS** |
| Parse 138 trang | **PASS** |
| Chunk + Qdrant count | **PASS** |
| Embed | **PASS** |
| Table / TKB chunk quality | **PARTIAL** |
| Smoke QA tổng thể | **PARTIAL** (~59% strict PASS) |

### **Overall: PARTIAL**

Ingest pipeline hoàn tất đúng 1312 chunk / 1312 vector, đủ trang và metadata trang; chất lượng bảng và retrieval lịch/văn bản ngắn chưa đạt để coi là PASS end-to-end.

---

## 12. Commands / verification

| Command | Result |
|---------|--------|
| `docker compose up --build -d` | PASS |
| `docker compose config -q` | PASS |
| `curl localhost:8080/api/chatbots?page=0&size=1` | PASS |
| `curl localhost:6333/collections` | PASS |
| `_run_23h_continue.ps1` | PASS (artifact JSON) |
| Backend `mvnw test` | NOT RUN (verify-only task) |
