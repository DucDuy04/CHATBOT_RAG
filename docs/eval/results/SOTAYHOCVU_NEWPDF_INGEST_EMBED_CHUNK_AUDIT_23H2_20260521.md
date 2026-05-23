# SoTayHocVu NEW PDF — Ingest / Embed / Chunk / Retrieval Audit 23H2

**Ngày:** 2026-05-21  
**Loại:** VERIFY ONLY  
**File:** `docs/eval/manual/SoTayHocVu-HocKy1-NamHoc20252026.pdf` (không có `(1)`)  
**Baseline:** Task 23H — `SoTayHocVu-HocKy1-NamHoc20252026 (1).pdf` (1312 chunks, 138 trang)  
**Artifact:** `docs/eval/results/_run_23h2_results.json`

---

## 1. Environment

| Item | Value |
|------|-------|
| Stack | `docker compose` — backend/qdrant/mysql **UP** |
| Backend health | **OK** |
| Qdrant | collection `documents`, vector **768** |
| Embedding | Nomic `nomic-embed-text-v1.5` |
| `docker compose config -q` | **PASS** |

---

## 2. File info

| File | Size | SHA256 |
|------|------|--------|
| **New** `SoTayHocVu-HocKy1-NamHoc20252026.pdf` | **12,655,880** bytes (~12.1 MB) | `1D3D388B3FEC51A3E380F368B6DB892444A7469E720975CF443FD1F278EE328D` |
| **Old** `(1).pdf` (23H) | 11,113,284 bytes (~10.6 MB) | *(file không còn trong workspace lúc audit; dùng số liệu report 23H)* |

**Khác biệt file:** Size **lớn hơn** ~1.5 MB; hash **khác** → không cùng byte stream; parser log **140** trang vs **138** (+2).

---

## 3. Upload / index

| Field | Value |
|-------|-------|
| Chatbot | `23H2 SoTayHocVu New PDF Audit` |
| `CHATBOT_ID` | `7fc5a049-a1ab-49e2-a106-0bd322c3aab7` |
| `DOCUMENT_ID` | `e93f04d4-5ba4-4d90-8ccb-41154bedc4d2` |
| API key | `a753...e842` (masked) |
| Status | **INDEXED** |
| API `chunkCount` | **784** |
| DB status | **COMPLETED**, `chunk_count=784` |
| `created_at` → `updated_at` | 14:21:25 → 14:23:17 (~**112 s**) |
| Embed log | `Bắt đầu embed 784 chunks` → *(hoàn tất, không lỗi trong window)* |

---

## 4. Parse / page audit

| Metric | New PDF | Old 23H |
|--------|---------|---------|
| Pages (parse log) | **140** | 138 |
| SQL `MIN(page_start)` | **1** | 1 |
| SQL `MAX(page_end)` | **140** | 138 |
| `page_start IS NULL` | **0** | 0 |
| `document_sections` | **2** * | 49 |
| `document_tables` | **106** | 49 |
| Table ACCEPTED (logs) | **106** | 104 |
| Table REJECTED | **1032** | 1028 |
| Table MERGED | **8** | 8 |

\* Chỉ **2** section DB rows — chunker gom nhiều nội dung dưới `General` + section từ dòng bảng (khác strategy so với file cũ).

### Marker presence (chunk `LIKE` count)

| Marker | Chunks |
|--------|--------:|
| `30/06/2025` | 51 |
| `06/07/2025` | 50 |
| `08/09/2025` | 51 |
| `29/12/2025` | 49 |
| `17/01/2026` | 50 |
| `09/02/2026` | 52 |
| `01/03/2026` | 52 |
| `16T1021140` | 49 |
| `KTR3185` / `KTR3103` / `LLCTTH3` / `CNS3023` | 49–54 |
| `30/03/2026` / `26/04/2026` | 50 |
| `20/07/2026` / `30/08/2026` | 50 |
| `324/KH` | 49 |

**Kết luận parse:** Đủ trang **140**; mốc lịch và mã HP **có trong DB**; Tabula reject vẫn cao (~1032).

---

## 5. Chunking audit (new)

| Metric | Value |
|--------|-------|
| **chunkCount** | **784** |
| avg chars | **801** |
| min / max | **29** / **2199** |
| with page metadata | **784** (100%) |
| with `section_title` | **784** |
| with `table_id` | **623** (79%) |
| chunks &lt; 50 chars | **2** |
| chunks &gt; 2000 chars | **80** |
| header-like short (`TC HK1`, len&lt;200) | **48** |

### Chunk type distribution

| chunk_type | count | Old 23H |
|------------|------:|--------:|
| **table_row_group** | **517** | 515 |
| **text** | **159** | 651 |
| **table_summary** | **106** | 104 |
| **section_summary** | **2** | 6 |
| text_table_like | 0 | 33 |
| parent_section_summary | 0 | 3 |
| **Total** | **784** | **1312** |

---

## 6. Embedding / Qdrant

| Metric | Value |
|--------|-------|
| DB chunks | **784** |
| Qdrant points (filter document+widget) | **784** |
| **DB = Qdrant** | **PASS** |
| Upsert / embed errors | **None** observed |
| Payload keys | `document_id`, `documentId`, `widgetId`, `chunk_id`, `chunkIndex`, `text_segment`, `fileName`, `page_start`, `page_end`, `section_title`, `heading_path_text`, `chunk_type`, … |

---

## 7. Vì sao 784 chunks thay vì 1312? (−528)

| Nguyên nhân | Δ chunks | Giải thích |
|-------------|----------|------------|
| **Giảm `text` chunks** | **−492** (651→159) | Chunk text **dài hơn** (avg **801** vs 422); gom đoạn văn → ít chunk hơn |
| Bỏ `text_table_like` | −33 | Không xuất hiện ở file mới |
| Bỏ `parent_section_summary` | −3 | Không xuất hiện |
| `table_row_group` | **+2** | Gần như **không đổi** (517 vs 515) |
| `table_summary` | +2 | Tương đương |
| Trang +2 | — | Không giải thích giảm chunk |

**Không phải:** thiếu embed (784/784 vectors), lỗi Qdrant, hay `chunkCount=0`.

**Có thể:** ít noise hơn (**2** chunk &lt;50 vs **313** cũ) → retrieval lịch một số câu **tốt hơn**; trade-off ít chunk `text` riêng lẻ.

---

## 8. Old vs new comparison

| metric | old `(1).pdf` | new `.pdf` | delta | nhận xét |
|--------|---------------|------------|-------|----------|
| pages | 138 | **140** | +2 | File mới dài hơn |
| chunkCount | 1312 | **784** | **−528** | Chủ yếu −text |
| text | 651 | **159** | −492 | Gom chunk dài |
| table_row_group | 515 | **517** | +2 | Bảng row ~giữ |
| table_summary | 104 | **106** | +2 | ~giữ |
| text_table_like | 33 | **0** | −33 | |
| withTableId | 619 | **623** | +4 | |
| table ACCEPTED | 104 | **106** | +2 | ~giữ |
| table REJECTED | 1028 | **1032** | +4 | Vẫn cao |
| avg chars | 422 | **801** | +379 | Chunk dài hơn |
| chunks &lt;50 | 313 | **2** | −311 | Ít noise |
| Qdrant points | 1312 | **784** | −528 | Khớp chunk |
| smoke PASS (strict) | 13/22 | **15/24** | +2 câu pass* | *khác bộ câu (thêm Q21–Q22) |

### Smoke — cải thiện / regress (new vs 23H)

| Câu | Old 23H | New 23H2 |
|-----|---------|----------|
| Q3 đăng ký mạng | FAIL | **PARTIAL** (đúng ngày, thiếu `10h00`) |
| Q5 thi KTHP | FAIL | **PASS** |
| Q15 công bố lịch thi | FAIL | **PASS** |
| Q16 vắng thi điểm 0 | FAIL | **PASS** |
| Q6 Tết | FAIL | **FAIL** |
| Q7 mã SV | FAIL | **FAIL** |
| Q21 quân sự K48 | — | **FAIL** (bịa ngày) |
| Q22 nghỉ hè | — | **PASS** |

---

## 9. Retrieval smoke (Playground, T=0.2, maxTokens=2048)

| id | topN | verdict | ghi chú |
|----|------|---------|---------|
| Q1 | 10 | PARTIAL | Đúng mục đích; thiếu “đọc kỹ” |
| Q2 | 10 | PASS | |
| Q3 | 20 | PARTIAL | **30/06–06/07** OK; thiếu 10h00 |
| Q4 | 20 | FAIL | 08/09/2025 OK; thiếu K45/K48 |
| Q5 | 20 | **PASS** | 29/12–17/01 |
| Q6 | 20 | FAIL | Không tìm thấy Tết (chunk có 09/02) |
| Q7 | 10 | FAIL | Không tìm thấy 16T1021140 |
| Q8 | 10 | PARTIAL | ĐTĐH OK; thiếu giấy tờ |
| Q9 | 10 | PASS | |
| Q10 | 20 | PASS | |
| Q11 | 10 | PARTIAL | Thiếu “xét duyệt” |
| Q12 | 20 | PASS | |
| Q13 | 10 | PASS | |
| Q14 | 10 | PASS | 50% |
| Q15 | 20 | **PASS** | 02/01 tuần |
| Q16 | 10 | **PASS** | điểm 0 |
| Q17–Q20 | 20 | **PASS** | Mã HP đúng |
| Q21 | 20 | FAIL | Sai ngày quân sự K48 |
| Q22 | 20 | **PASS** | Nghỉ hè 20/07–30/08 |
| Q23 | prod | PASS | OOS tỷ giá |
| Q24 | prod | PARTIAL | Không có ABC9999; 2 sources |

**Strict PASS:** **15/24** (62.5%).  
**Nhóm lịch Q3–Q6:** 1 PASS, 2 PARTIAL, 2 FAIL — **cải thiện** vs 23H nhưng chưa sạch.  
**Nhóm mã HP Q17–Q20:** **4/4 PASS**.  
**OOS:** PASS / PARTIAL nhẹ.

---

## 10. Table / lịch observations

- **table_row_group** không giảm → cấu trúc bảng hàng **không mất** khi giảm chunk.
- Giảm mạnh ở **text** + noise nhỏ → có thể **tốt hơn** cho embedding lịch dạng đoạn.
- **section_title** vẫn nhiễu: `Tuần dự trữ L1/2 Lễ bế giảng...`, `General | Trang: 1-21`.
- Q6/Q7 fail dù marker có trong DB → **retrieval/context**, không phải thiếu ingest.

---

## 11. Benchmark khuyến nghị

| Tiêu chí | File nên dùng |
|----------|----------------|
| Smoke lịch / thi / đăng ký | **New `.pdf`** (Q5,Q15,Q16,Q3 cải thiện) |
| So sánh chunk granularity / N+1 text | Old `(1).pdf` (nhiều text chunk nhỏ) |
| Bảng row-level | **Tương đương** (517 vs 515) |
| Production ingest | **New `.pdf`** — ít chunk, cùng nội dung, Qdrant nhẹ hơn, QA tốt hơn một phần |

---

## 12. Conclusion

| Area | Verdict |
|------|---------|
| INDEXED + Qdrant | **PASS** |
| 140 trang | **PASS** |
| 784 chunks ổn định | **PASS** (chiến lược khác, không lỗi) |
| Table chunks | **PARTIAL** (row giữ, reject cao, section title nhiễu) |
| Smoke QA | **PARTIAL** (15/24 PASS, tốt hơn 23H trên nhóm lịch/thi) |

### **Overall: PARTIAL**

784 chunks **không kém hơn** file cũ về ingest; **khác strategy** (ít text chunk, dài hơn, ít noise). Smoke **tốt hơn một phần**; vẫn fail nhóm Tết / mã SV / quân sự K48.

---

## 13. IDs for re-test

```
CHATBOT_ID=7fc5a049-a1ab-49e2-a106-0bd322c3aab7
DOCUMENT_ID=e93f04d4-5ba4-4d90-8ccb-41154bedc4d2
```
