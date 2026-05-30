# DOCX_RUNTIME_VERIFY_24B2_AFTER_NOMIC_FIX_20260527

**Task:** 24B2 — Resume Real DOCX Runtime Verification After Nomic Quota Fix  
**Date:** 2026-05-27  
**Final verdict:** **PARTIAL**

- DOCX ingest is now successful (`INDEXED`).
- DB/Qdrant counts are consistent (`3283 = 3283 = 3283`).
- Q1-Q8 rerun completed.
- Q5/Q6 are still partial on scoped major/cohort semantics.

---

## 1) Nomic remediation and backend status

- Backend env now uses same key as `.env`.
- `docker compose ps`: backend/mysql/qdrant all running.
- No Nomic quota error in this rerun ingest.

---

## 2) Real DOCX rerun

Fixture:
- `docs/eval/manual/SoTayHocVu_HocKy1_2025-2026.docx`
- size: `12,331,959` bytes (~12.3 MB)

New chatbot:
- `chatbotId = 4636ac23-9bfe-4b1b-b325-5b35b3434387`
- name: `24B2-docx-verify-rerun`

Upload:
- start: `2026-05-27T22:44:43.945+07:00`
- end: `2026-05-27T22:45:40.786+07:00`
- duration: `56,841 ms`

Result:
- `documentId = 1dae2e0b-e740-42e2-93e2-39b0934d1e80`
- API status: `INDEXED`
- API progress: `100`
- API chunkCount: `3283`

---

## 3) DB / Qdrant ingest audit

Document row (MySQL):
- `file_type = DOCX`
- `status = COMPLETED`
- `chunk_count = 3283`

Chunk counts:
- API chunkCount: `3283`
- DB chunks: `3283`
- Qdrant points: `3283`

Chunk type distribution:
- `normalized_table_row = 3094`
- `table_summary = 179`
- `text = 8`
- `section_summary = 2`
- `table_row_group = 0`
- `text_table_like = 0`

Metrics from backend logs:
- `docxTablesDetected = 179`
- `docxTablesNormalized = 179`
- `docxTablesUsingRawTableModel = 179`
- `docxTablesUsingMarkdownBridge = 0`
- `docxTableRowsNormalized = 3094`
- `valuesDroppedCount = 0`
- `rawTableCellsMissingCoordinates = 0`
- `rowsWithCellsJson = 3094`
- `rowsWithGenericColumnKeys = 550`
- `headerSlotsFallbackGeneric = 50`

Qdrant payload check:
- points exist and include `chunk_type`, `chunk_id`, `table_name`, `cells_json` payload keys.

---

## 4) DOCX schedule `cells_json` inspection

### Nhóm 2

- chunkId: `1535fe9b-fec0-49b8-9687-816b3c1d61a0`
- rowIndex: `9`
- cells_json:
  - `"Giảng viên":"Hoàng Ngô Tự Do"`
  - `"Thứ":"2"`
  - `"Tiết học":"1 - 3"`
  - `"Phòng":"H307"`

### Nhóm 4 (critical case)

- chunkId: `f62f91f1-f91c-4a13-bd5f-5efc1e8f4257`
- rowIndex: `11`
- cells_json:
  - `"Giảng viên":"Nguyễn Thị Thanh Nhàn"`
  - `"Thứ":"6"`
  - `"Tiết học":"5 - 7"`
  - `"Phòng":"B301"`

Result: matches expected schedule values and aligns with PDF 23P2 baseline.

---

## 5) DOCX curriculum `cells_json` inspection

KTR3185 canonical row:
- chunkId: `7a893c4a-a0ac-4671-8803-2c7dae110793`
- cells_json:
  - `"Mã học phần":"KTR3185"`
  - `"Tên học phần":"Đồ án kiến trúc công trình tổ hợp đa chức năng"`
  - `"TC HK1":"5"`

KTR3185 contamination check:
- Canonical row is separate from KTR3273/KTR4015/KTR5022 rows.

K46/HK2 context:
- Relevant rows exist in retrieved sources for Q5/Q6, but scoped selection is still noisy.

---

## 6) DOCX vs PDF comparison (23P2 baseline)

PDF baseline (23P2):
- chunkCount `3417`
- normalized_table_row `3160`
- table_summary `122`
- Q2 PASS
- Q5/Q6 PARTIAL

DOCX 24B2 rerun:
- chunkCount `3283`
- normalized_table_row `3094`
- table_summary `179`
- valuesDroppedCount `0`
- rawTableCellsMissingCoordinates `0`
- Q2 PASS
- Q5/Q6 PARTIAL

Quality class: **SIMILAR_TO_PDF** (structural DOCX ingest is strong; scoped-context query quality remains similar bottleneck).

---

## 7) Q1-Q8 runtime (DOCX, UTF-8 runner)

Runtime config:
- `temperature = 0.2`
- `maxTokens = 768`
- `topK = 15`
- endpoint: `/api/playground/chat` (debug sources enabled by controller)

| Q | Verdict | Notes |
|---|---|---|
| Q1 | PASS | Correct: Hoàng Ngô Tự Do, thứ 2, tiết 1-3, H307 |
| Q2 | PASS | Correct: Nguyễn Thị Thanh Nhàn, thứ 6, tiết 5-7, B301 |
| Q3 | PASS | Correct comparison Nhóm 1 vs Nhóm 2 |
| Q4 | PASS | Correct date range 30/06/2025 - 06/07/2025 |
| Q5 | PARTIAL | Returns plausible Kiến trúc HK2 items but scoped K46 filtering is still not strict |
| Q6 | PARTIAL | Returns list with mixed HK1/HK2 relevance and weak K46 scoping |
| Q7 | PASS | Correct KTR3185 title + 5 credits |
| Q8 | PASS | OOS refusal ("không tìm thấy thông tin này trong tài liệu") |

Latency from runner:
- Q1 `10.1s`
- Q2 `8.4s`
- Q3 `9.1s`
- Q4 `5.0s`
- Q5 `10.4s`
- Q6 `10.9s`
- Q7 `11.2s`
- Q8 `7.1s`

Artifacts:
- `_run_24b2_q1q8_results_utf8.json`
- `_run_24b2_q1q8_sse_raw_utf8.txt`

---

## 8) Q5/Q6 diagnosis

Current failure class:
- **Relevant rows exist but retrieval scope is noisy for major/cohort-semester constraints.**

Evidence:
- Sources include many broadly related rows and some cross-domain K46-tagged rows.
- Model still outputs partially correct but not tightly scoped list.

Recommended single next task:
- **scoped-context extraction** (generic cohort/major/semester scope propagation to retrieval context; no hardcoding domain labels).

---

## 9) Acceptance criteria status

- Backend deploy succeeds: ✅
- Nomic quota issue resolved for this run: ✅
- Real DOCX ingests successfully: ✅
- document type = DOCX: ✅
- status = INDEXED: ✅
- API chunkCount = DB chunks = Qdrant points: ✅
- normalized_table_row > 0: ✅
- table_summary > 0: ✅
- docxTablesDetected > 0: ✅
- docxTablesNormalized > 0: ✅
- docxTablesUsingMarkdownBridge = 0: ✅
- table_row_group = 0: ✅
- text_table_like = 0: ✅
- raw fallback = 0: ✅
- cells_json exists for normalized rows: ✅
- valuesDroppedCount = 0: ✅
- Q1-Q8 executed and reported: ✅
- Q8 OOS refusal: ✅
- PDF/TXT regressions absent: ✅ (no regression observed in this scope)

Overall: **PARTIAL** (only because Q5/Q6 still partial).

# DOCX_RUNTIME_VERIFY_24B2_AFTER_NOMIC_FIX_20260527

**Task:** 24B2 — Resume Real DOCX Runtime Verification After Nomic Quota Fix  
**Date:** 2026-05-27  
**Final Verdict:** **FAIL** (Nomic quota still blocks embedding; DOCX parse/chunk pass, but index not completed)

---

## 1) Precondition check (Nomic remediation)

- `.env` contains `NOMIC_API_KEY` and backend restarted successfully.
- However, real runtime call to Nomic still returns:
  - `"You have exceeded your 10000000 free tokens of Nomic Embedding API usage."`
- Conclusion: precondition **not actually satisfied** (key still quota-blocked or account billing not enabled).

---

## 2) Step 1 — Backend / Infra status

`docker compose ps`:
- `chatbot-backend`: Up
- `ragchatbot-mysql`: Up (healthy)
- `ragchatbot-qdrant`: Up
- API reachable (`GET /api/chatbots` success)

`docker compose logs backend --tail`:
- No startup crash.
- Quota errors appear during ingest runtime (not startup).

---

## 3) Step 2 — Re-upload real DOCX

Real fixture:
- `docs/eval/manual/SoTayHocVu_HocKy1_2025-2026.docx`
- size: `12,331,959` bytes (~12.3 MB)

New chatbot created:
- `chatbotId = 628a534a-d923-48f8-afce-855c85382e0f`
- name: `24B2-docx-verify`

Upload call:
- `POST /api/documents/upload?chatbotId=628a534a-d923-48f8-afce-855c85382e0f`
- multipart field: `files=@docs/eval/manual/SoTayHocVu_HocKy1_2025-2026.docx`

Timing:
- upload start: `2026-05-27T22:33:00.641+07:00`
- upload end: `2026-05-27T22:33:51.320+07:00`
- upload duration: `50,679 ms`

Result:
- HTTP body returned 500.
- New document record:
  - `documentId = 0038bd29-9bf8-4764-bcf0-9d0427fb41f8`
  - type = `DOCX`
  - status = `FAILED`
  - API `chunkCount = 0`

---

## 4) Step 3 — DB / Qdrant ingest audit

### 4.1 Document and chunk status

MySQL `documents`:
- id: `0038bd29-9bf8-4764-bcf0-9d0427fb41f8`
- widget/chatbot id: `628a534a-d923-48f8-afce-855c85382e0f`
- file_type: `DOCX`
- status: `FAILED`
- chunk_count: `NULL`

MySQL `document_chunks` count (same document):
- `3283` rows persisted

Chunk-type distribution in DB:
- `normalized_table_row = 3094`
- `table_summary = 179`
- `text = 8`
- `section_summary = 2`
- `table_row_group = 0`
- `text_table_like = 0`

Qdrant:
- points count for this `documentId`: `0`
- scroll sample: empty

### 4.2 DOCX metrics from backend log

- `docxTablesDetected = 179`
- `docxTablesNormalized = 179`
- `docxTablesUsingRawTableModel = 179`
- `docxTablesUsingMarkdownBridge = 0`
- `docxTableRowsNormalized = 3094`
- `totalChunks = 3283`
- `valuesDroppedCount = 0`
- `rawTableCellsMissingCoordinates = 0`

### 4.3 Audit conclusion

- Parse/chunk pipeline still PASS (same as 24B baseline).
- Embedding/upsert stage FAIL due Nomic quota.
- Therefore requirement `API chunkCount = DB chunks = Qdrant points` is **not met**.

---

## 5) Step 4 — DOCX schedule `cells_json` inspection (DB-level)

Because Qdrant has 0 points, payload-level audit is unavailable.  
DB `normalized_table_row` still contains valid schedule rows:

### Nhóm 4 row (expected critical case)

- chunkId: `f1d0fbd1-10be-4ec5-8765-7a162315dfcf`
- rowIndex: `11`
- tableName: `2.4. Phát hành thời khóa biểu chính thức`
- cells_json excerpt:
  - `"Giảng viên":"Nguyễn Thị Thanh Nhàn"`
  - `"Thứ":"6"`
  - `"Tiết học":"5 - 7"`
  - `"Phòng":"B301"`

### Nhóm 2 row

- chunkId: `92e4f9fb-a347-455e-b0df-9d828969ed88`
- rowIndex: `9`
- cells_json excerpt:
  - `"Giảng viên":"Hoàng Ngô Tự Do"`
  - `"Thứ":"2"`
  - `"Tiết học":"1 - 3"`
  - `"Phòng":"H307"`

This matches expected factual values at DB chunk level.

---

## 6) Step 5 — DOCX curriculum `cells_json` inspection (DB-level)

Again, Qdrant payload not available due `points=0`.

Observed for KTR3185:
- canonical row exists:
  - chunkId: `248659e2-2c3c-415f-b0fe-adf7c000d181`
  - cells_json:
    - `"Mã học phần":"KTR3185"`
    - `"Tên học phần":"Đồ án kiến trúc công trình tổ hợp đa chức năng"`
    - `"TC HK1":"5"`
- class-offering rows also exist (Nhóm 1..8 variants) with flattened STT field strings in some schedule-style tables.

Cross-row contamination check:
- KTR3185 canonical row is not merged into KTR3273/KTR4015/KTR5022 in the canonical curriculum row shown above.

K46/HK2 scoped context:
- Not verified at retrieval answer level because index failed (no Qdrant candidates).
- `group_context` query for this failed document returns empty (`0` rows with non-empty `group_context`).

---

## 7) Step 6 — DOCX vs PDF quality comparison (current state)

Reference PDF 23P2 baseline:
- chunkCount `3417`
- normalized_table_row `3160`
- table_summary `122`
- Q2 PASS after header-slot fix

Current DOCX 24B2 failed run (DB-parse-stage only):
- DB chunks persisted `3283` (same as 24B parse evidence)
- normalized_table_row `3094`
- table_summary `179`
- valuesDroppedCount `0`
- rawTableCellsMissingCoordinates `0`
- Qdrant points `0` (blocked)

Classification:
- **INCONCLUSIVE**
- Structural parse quality looks strong, but retrieval/runtime quality cannot be judged without embedding+Qdrant.

---

## 8) Step 7 — Q1–Q8 runtime against DOCX

Per task instruction, when status becomes `FAILED` again, stop and diagnose.

Therefore:
- Q1–Q8 status: **NOT RUN**
- Reason: DOCX document not indexed to Qdrant (`points=0`) due Nomic quota failure.

---

## 9) Step 8 diagnosis (why Q5/Q6 cannot be evaluated now)

Current blocker class:
- **Embedding provider quota blockade** (upstream dependency failure)

Not yet diagnosable in this run:
- retrieval-list budget issues
- prompt formatting issues
- scoped-context extraction quality at answer level

Because all of these require successful index and source retrieval first.

---

## 10) Acceptance criteria matrix (24B2 run)

- Backend deploy succeeds: ✅
- Nomic quota issue resolved: ❌
- Real DOCX ingests successfully (`INDEXED`): ❌
- document type = DOCX: ✅
- normalized_table_row > 0: ✅ (DB)
- table_summary > 0: ✅ (DB)
- docxTablesDetected > 0: ✅
- docxTablesNormalized > 0: ✅
- docxTablesUsingMarkdownBridge = 0: ✅
- table_row_group = 0: ✅
- text_table_like = 0: ✅
- raw fallback = 0: ✅
- cells_json exists for normalized_table_row: ✅ (DB)
- API chunkCount = DB chunks = Qdrant points: ❌ (`0` vs `3283` vs `0`)
- Q1-Q8 runtime reported: ❌ (blocked, NOT RUN)
- Q8 OOS refusal: ❌ (not executed)
- PDF/TXT regression absent: ✅ (existing indexed PDF/TXT docs remain queryable/listed)

Overall 24B2 verdict remains **FAIL**.

---

## 11) Latency notes

- Upload request took ~50.7s before failing.
- Failure occurred after parse/chunk stage while requesting Nomic embedding.

---

## 12) Known limitations in this run

1. No Qdrant payload to inspect for DOCX rows (points=0).
2. No Q1-Q8 answer runtime for DOCX because ingestion never reached indexed state.
3. API `chunkCount` remains 0 while DB has chunks due failed finalization path.

---

## 13) Next single recommended task

**Task recommendation:** `NOMIC_BILLING_REMEDIATION_AND_RETRY_24B2`

Do exactly:
1. Ensure active billing/payment on the Nomic account for the key in `.env`, or replace with a truly fresh key with available quota.
2. `docker compose up -d --force-recreate backend`
3. Re-run 24B2 from Step 2 using a new chatbot/document.

No production code change is recommended before quota is truly fixed.

