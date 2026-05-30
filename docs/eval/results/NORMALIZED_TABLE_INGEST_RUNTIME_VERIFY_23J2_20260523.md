# Normalized Table Ingest — Runtime Verify 23J2

**Date:** 2026-05-23  
**Task:** 23J2 — Runtime Verify Normalized Table Ingest  
**Type:** VERIFY ONLY (no Java/Frontend/logic changes)  
**Verdict:** **PARTIAL**

---

## Environment

| Component | Status | Notes |
|-----------|--------|-------|
| `docker compose up --build -d` | UP | backend + mysql + qdrant + frontend |
| Backend API | `http://localhost:8080` | Health `GET /api/chatbots?page=0&size=1` → 200 (after User env keys injected) |
| Qdrant | `http://localhost:6333` | Collection `documents`, total **3683** points (951 legacy + 2732 new) |
| MySQL | `ragchatbot-mysql` | healthy |
| Model | `meta-llama/llama-4-scout-17b-16e-instruct` (Groq) | temperature=0.2, maxTokens=768 |
| Playground | `POST /api/playground/chat` SSE | `playgroundDebugSources=true` |
| API keys | Windows User env → docker compose | Initial start failed (blank GROQ/NOMIC); fixed by exporting User env before `docker compose up -d backend` |

---

## Build / test results

| Command | Result | Notes |
|---------|--------|-------|
| `docker compose up --build -d` | **PASS** | Backend image rebuilt with 23J code |
| `docker compose config -q` | **PASS** | |
| Maven Docker `mvn -DskipTests compile` | **PASS** | BUILD SUCCESS |
| Targeted tests (8 classes) | **PASS** | Docker Maven exit 0; includes NormalizedTableIngestTest, DocumentParserCrossPageMergeTest, HybridKeywordSearchTest, FinalContextSelectionTest, RetrievalTopKTest, ChatServiceModelConfigTopKTest, ChatServiceSourcePresentationTest, RagTokenAuditTest |
| Local `mvnw.cmd compile` | **FAIL** | JAVA_HOME unset on host (expected); used Maven Docker |
| Frontend lint/build | **NOT RUN** | Out of scope |
| Widget build | **NOT RUN** | Out of scope |

---

## Re-ingest document info

| Field | Value |
|-------|-------|
| NEW_CHATBOT_ID | `d74b9e78-8dc8-4f88-acca-f83886fae293` |
| NEW_DOCUMENT_ID | `2e1d4c6b-31d1-4af9-80db-72c14684be8c` |
| Chatbot name | 23J2 SoTayHocVu Normalized Table Verify |
| PDF | `docs/eval/manual/SoTayHocVu-HocKy1-NamHoc20252026.pdf` |
| Status | **INDEXED** (API) / **COMPLETED** (DB enum) |
| chunkCount (MySQL) | **2732** |
| Ingest started | ~2026-05-23T16:32:19Z (upload) |
| Parse finished | 2026-05-23T16:33:16Z |
| Embed finished | 2026-05-23T16:34:20Z |
| Ingest duration | **~2 min** (parse+embed); upload→INDEXED ~9s synchronous |
| Qdrant points (this document) | **2732** |
| Model/provider | Groq LLM + Nomic embed (768-dim) |

### Ingest metrics (backend logs)

```
[Chunk] processSections2 done: sections=2 totalChunks=2732 ... normalizedRows=2475 text=149
  detectedTables=106 normalizedTables=106 failedTables=0 suppressedRawChars=0
Parse document=SoTayHocVu... tableIngest: detected=106 normalized=106 failed=0 normalizedRows=2475 suppressedRawChars=0
Đã lưu 2732 vectors vào Qdrant cho document=2e1d4c6b-31d1-4af9-80db-72c14684be8c
```

| metric | value |
|--------|------:|
| detectedTables | 106 |
| normalizedTables | 106 |
| failedTables | **0** |
| normalizedRows | 2475 |
| tableSummaries | 106 |
| suppressedRawTableTextChars | **0** |
| textChunks | 149 |
| normalizedTableRowChunks | 2475 |
| tableSummaryChunks | 106 |
| tableRowGroupChunks | **0** |
| textTableLikeChunks | **0** |
| qdrantPoints | 2732 |
| ingestDuration | ~121s embed phase |

---

## Chunk type distribution (MySQL)

| chunk_type | count |
|------------|------:|
| normalized_table_row | **2475** |
| text | 149 |
| table_summary | 106 |
| section_summary | 2 |
| table_row_group | **0** |
| text_table_like | **0** |
| parent_section_summary | 0 |

**DB chunks = Qdrant points:** **2732 = 2732 PASS**

---

## Old vs new comparison

| metric | old (23H2/23I) | new (23J2) | delta | nhận xét |
|--------|---------------:|-----------:|------:|----------|
| chunkCount | 784 | 2732 | +1948 | 1 row/chunk → ~2475 normalized rows + summaries |
| text | 159 | 149 | −10 | Giảm nhẹ nhưng vẫn còn raw TKB blocks |
| normalized_table_row | 0 | 2475 | +2475 | PASS — ingest mới |
| table_summary | 106 | 106 | 0 | Giữ nguyên số bảng |
| table_row_group | 517 | **0** | −517 | PASS — không còn path cũ |
| text_table_like | 0 | **0** | 0 | PASS |
| Qdrant points | 784 | 2732 | +1948 | Khớp chunkCount |

---

## Raw table suppression audit

**Expected:** text chunks không chứa raw timetable dài.

**Evidence:**

- SQL audit `chunk_type=text` AND pipe patterns (`|%|%|%`, `Mã học phần`, …): **0 rows** (timetable không dùng markdown pipe).
- **51** text chunks có `CHAR_LENGTH(content) > 1500` và pattern timetable (`STT`/`Giảng viên`).
- Sample text chunk #2450 (~2192 chars): bắt đầu `STT Tên lớp học phần Giảng viên Thứ Tiết học Phòng...` — raw TKB block.
- Log metric `suppressedRawChars=0` — suppression overlap không ghi nhận trên SoTay mega-table section.

**Verdict:** **PARTIAL/FAIL** — normalized rows tạo đúng nhưng **149 text chunks vẫn chứa raw timetable/CTĐT blocks** (không phải markdown pipe). Retrieval Q1–Q3 vẫn ưu tiên `chunk_type=text` TKB.

---

## Normalized row sample (MySQL + Qdrant)

MySQL canonical text pattern:

```text
Bảng: Tuần dự trữ L1/2 ...
Nhóm/section: ...
Dòng: 47.
STT: KNM1013 Kỹ năng mềm.
Trang: 21.
```

Qdrant sample payload (`normalized_table_row`):

```json
{
  "chunk_type": "normalized_table_row",
  "table_name": "Tuần dự trữ L1/2 Lễ bế giảng ...",
  "row_index": 2411,
  "cells_json": "{\"STT\":\"972\",\"Mã\":\"CTX4133\"}",
  "group_context": "TRD3233 Thực tập năm thứ 3",
  "document_id": "2e1d4c6b-31d1-4af9-80db-72c14684be8c",
  "page_start": 21,
  "page_end": 140
}
```

**cells_json present:** **PASS**

---

## Cross-page / header propagation audit

| Check | Result |
|-------|--------|
| `row_index` monotonic within table | **PASS** (e.g. 1546 → 2411 in samples) |
| `group_context` / `table_name` on continuation rows | **PASS** |
| Repeated header as data row | **Not observed** in spot checks |
| Per-row `page_start`/`page_end` granularity | **PARTIAL** — many rows show 21–140 (section span), not cell page |
| Column keys consistent (`cells_json`) | **PASS** within sampled tables |

**Verdict:** **PARTIAL** — logical row continuation OK; page-level metadata coarse.

---

## Runtime Q1–Q8

Config: Hybrid ON (default), temperature=0.2, maxTokens=768, playgroundDebugSources=true.

| id | question (short) | topN | answer summary | source chunk types | expected | verdict | notes |
|----|------------------|------|----------------|--------------------|----------|---------|-------|
| Q1 | KNM Nhóm 2 TKB | 15 | Refusal | text×4 | đúng Nhóm 2 + GV/thứ/tiết/phòng | **FAIL** | Sources có TKB raw text nhưng LLM refuse; no normalized_table_row in sources |
| Q2 | KNM Nhóm 4 TKB | 15 | Refusal | text×5 | đúng Nhóm 4 | **FAIL** | Same as Q1; regression vs 23I2 PASS |
| Q3 | So sánh N1 & N2 | 15 | Groq rate-limit fallback | text×8 | 2 groups | **BLOCKED** | "Dịch vụ AI hiện đang quá tải" |
| Q4 | K45-K48 đăng ký mạng | 10 | 07/07/2025–16/07/2025 | text×9, normalized×1 | 30/06–06/07 (+10h00) | **FAIL** | Wrong date window |
| Q5 | Kiến trúc K46 HK2 | 15 | Groq rate-limit | text×8, section_summary×1 | HK2 courses | **BLOCKED** | |
| Q6 | CNS K46 HK2 | 15 | Groq rate-limit | text×13, normalized×1 | HK2 courses | **BLOCKED** | |
| Q7 | KTR3185 K45 | 10 | Groq rate-limit | text×10 | Đồ án KTCTTH 5 TC | **BLOCKED** | Sources contain KTR3185 in text chunk |
| Q8 | USD/VND OOS | 5 | Refusal | section_summary×1, text×4 | no hallucination | **PASS** | Safe OOS |

**Scored (excluding BLOCKED):** Q8 PASS; Q1–Q2 FAIL; Q4 FAIL → **1 PASS / 3 FAIL / 4 BLOCKED**  
**vs 23I2 hybrid ON:** Q1–Q4 timetable/date cases **regressed** on new 2732-chunk corpus (retrieval dominated by large text TKB chunks).

Artifacts: `docs/eval/results/_23j2_qa_raw.json`, `_23j2_qa_log3.txt`

---

## failedTables analysis

| failedTables | Impact |
|-------------:|--------|
| **0** | All 106 detected Tabula tables normalized; no table region dropped by normalize-fail path on this PDF. |

Risk: **suppressedRawChars=0** + 51 large text timetable chunks → data duplicated (normalized row + raw text), hurting retrieval precision at scale.

---

## Hybrid Search 23I regression

- Unit: `HybridKeywordSearchTest` **PASS** in targeted suite.
- Runtime: hybrid branch not re-benchmarked matrix on 23J2 corpus; Q1–Q4 **worse** than 23I2 due corpus/retrieval mix (text mega-chunks), not necessarily hybrid code regression.

---

## Conclusion

**PARTIAL**

### PASS areas

- Re-ingest INDEXED with 23J code on **new** chatbot/document.
- `normalized_table_row=2475`, `table_summary=106`, **no** `table_row_group` / `text_table_like`.
- Qdrant payload has `table_name`, `row_index`, `cells_json`, `group_context`.
- DB chunks = Qdrant points (2732).
- `failedTables=0`.
- Compile + targeted unit tests PASS.
- Q8 OOS PASS.

### FAIL / gap areas

- **Raw timetable text** still in **51+** large `text` chunks; `suppressedRawChars=0`.
- Runtime Q1–Q2 **FAIL** (regression vs 23I2); Q4 wrong dates.
- Q3/Q5–Q7 **BLOCKED** by Groq rate limit during rapid QA script.
- Retrieval still surfaces `text` TKB blocks over `normalized_table_row` for timetable queries.

### Next steps

1. Fix parser/chunker suppression for non-markdown timetable page text (SoTay pages 21–140 mega-table).
2. Re-run Q1–Q8 with delay between calls after suppression fix.
3. Tune hybrid/lexical boost so `normalized_table_row` wins over raw text TKB for label queries (Nhóm N).

---

## Raw artifacts

- `docs/eval/results/_23j2_verify_raw.json` (partial — upload parse)
- `docs/eval/results/_23j2_qa_raw.json`
- `docs/eval/results/_23j2_qdrant_audit.json`
- `docs/eval/results/_23j2_mvn_test.log`
- `docs/eval/results/_run_23j2_verify.mjs` (helper scripts — verify artifacts only)
