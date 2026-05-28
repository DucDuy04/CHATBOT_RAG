# RAG_CONTEXT_HK_SPLIT_CELLS_JSON_VERIFY_24D0_20260527

**Task:** 24D0 - Reingest + runtime verify after new DOCX (`RAG_CONTEXT_HOC_KY`)  
**Date:** 2026-05-28  
**Final verdict:** **PARTIAL**

## 1) Scope and constraints applied

- Verify only first; no production code change.
- File under this rerun: `docs/eval/manual/SoTayHocVu_HocKy1_2025-2026_RAG_CONTEXT_HOC_KY.docx`.
- Runtime Q1-Q8 must not be claimed unless ingestion reaches `INDEXED`.

## 2) Step 1 - Pre-check backend and file

- `docker compose ps`: backend/mysql/qdrant are up.
- File exists: `Test-Path ...RAG_CONTEXT_HOC_KY.docx = true`.
- Backend/API reachable during rerun.

## 3) Step 2 - Create chatbot and ingest DOCX

Created chatbot:

- `chatbotId = 4fb1943b-c52d-41b6-9a59-d9df680e9b1a`
- name: `24D0-rag-context-hoc-ky-reingest`

Upload target:

- `documentId = 8045fede-bc22-42ed-92e5-ca6e529b3cea`
- file: `SoTayHocVu_HocKy1_2025-2026_RAG_CONTEXT_HOC_KY.docx`
- status: `INDEXED`
- progress: `100`
- upload duration: `68,244 ms`

Ingest trace (from backend logs):

- parse done: `sections=4`, `chunks=3296`
- embed start: `2026-05-28T08:17:55.790Z`
- vectors stored: `3296`
- document finalized as `COMPLETED`/`INDEXED`

## 4) Step 3 - Ingest audit (API/DB/Qdrant)

Document state:

- type = `DOCX`
- status = `COMPLETED` (`INDEXED` via API)
- API chunkCount = `3296`
- DB `documents.chunk_count` = `3296`

DB chunks for document:

- total chunks = `3296`
- chunk types:
  - `normalized_table_row = 3078`
  - `table_summary = 209`
  - `text = 7`
  - `section_summary = 2`
  - `table_row_group = 0`
  - `text_table_like = 0`

Qdrant:

- points count filtered by `documentId` = `3296`

Metrics from backend log:

- `docxTablesDetected = 209`
- `docxTablesNormalized = 209`
- `docxTablesUsingRawTableModel = 209`
- `docxTablesUsingMarkdownBridge = 0`
- `docxTableRowsNormalized = 3078`
- `rowsWithCellsJson = 3078`
- `rowsWithGenericColumnKeys = 550`
- `headerSlotsFallbackGeneric = 50`
- `valuesDroppedCount = 0`
- `rawTableCellsMissingCoordinates = 0`
- `groupContextPopulatedCount` (DB derived) = `0`

Conclusion for Step 3:

- Ingest path is healthy for this rerun (API/DB/Qdrant counts match).
- No markdown bridge regression.

## 5) Step 4 - Curriculum `cells_json` schema checks

Sample curriculum rows from DB (`normalized_table_row`) remain explicit with `Khóa ngành` + `Học kỳ`:

- `chunkId=fcd7d893-1f39-4038-ba8d-3f864b2b4f31`
  - `Khóa ngành=Khóa, ngành: Kiến trúc K46`
  - `Học kỳ=HK2`
  - `Mã học phần=KTR2082`
  - `Tên học phần=Quản lý đô thị`
  - `Số TC=2`
- `chunkId=6b6a0c2a-38b8-4a00-bb70-a6f7784c7d48`
  - `Khóa ngành=Khóa, ngành: Kiến trúc K46`
  - `Học kỳ=HK2`
  - `Mã học phần=KTR2102`
  - `Tên học phần=Kinh tế xây dựng`
  - `Số TC=2`
- `chunkId=fcc8de9a-be91-4eaa-a35f-fe6077c062ac`
  - `Khóa ngành=Khóa, ngành: Kiến trúc K46`
  - `Học kỳ=HK2`
  - `Mã học phần=KTR3174`
  - `Tên học phần=Đồ án quy hoạch đô thị`
  - `Số TC=4`
- `chunkId=1eb5d722-9aeb-426f-a59e-fc67172a92f6`
  - `Khóa ngành=Khóa, ngành: Công nghệ sinh học K46`
  - `Học kỳ=HK1`
  - `Mã học phần=CNS3023`
- `chunkId=0c58a261-3b31-477b-bfc6-732002d31d51`
  - `Khóa ngành=Khóa, ngành: Công nghệ sinh học K45`
  - `Học kỳ=HK1`
  - `Mã học phần=CNS4185`

Observation:

- Curriculum schema fields are explicit and stable in `cells_json`.
- `Khóa ngành` and `Học kỳ` present for sampled curriculum rows.

## 6) Step 5 - Specific correctness checks

### Check A - Kiến trúc K46 HK2

- Verified rows exist with explicit `Khóa ngành=Kiến trúc K46`, `Học kỳ=HK2`.
- Sample course codes found cleanly: `KTR2082`, `KTR2102`, `KTR3174`.
- No evidence of value-shift in these sampled rows.

### Check B - Công nghệ sinh học K46 HK2

- Rerun runtime now executes successfully.
- Q6 answer improved but still not fully strict (still classified PARTIAL for strict-scope requirement).

### Check C - KTR3185

Canonical curriculum row is clean:

- `chunkId=edd50516-674b-49f8-b391-4d00bf57aeea`
- `Khóa ngành=Kiến trúc K45`
- `Học kỳ=HK1`
- `Mã học phần=KTR3185`
- `Tên học phần=Đồ án kiến trúc công trình tổ hợp đa chức năng`
- `Số TC=5`

Contamination check:

- Adjacent canonical rows for `KTR3273`, `KTR4015`, `KTR5022` are separate.
- Separate schedule-style rows for `KTR3185 - Nhóm ...` also exist (expected in timetable table).

### Check D - Schedule rows (Nhóm 2 / Nhóm 4 / TIN1093 Nhóm 15)

Found:

- Nhóm 4 row:
  - `chunkId=7419161c-1be2-4abb-be3f-649194c069f1`
  - `cells_json.STT` packs multiple values: `"11 KNM1013 ... Nguyễn Thị Thanh Nhàn ... 6"`
  - `cells_json["Mã học phần"]="5 - 7"`
  - `cells_json["Tên lớp học phần"]="B301"`
- Nhóm 2 row:
  - `chunkId=0a3305f9-b1bb-4faf-a6a9-5271a17744f9`
  - packed format similarly observed.
- TIN1093 Nhóm 15 row:
  - `chunkId=ccc1ce0a-677b-41a7-ab3a-26733e74dc41`
  - `STT` packs row text, `Mã học phần` captured as time-slot.

Assessment:

- Schedule rows still show packed-cell pattern in sampled DB rows.
- Despite that, runtime Q1/Q3 can still answer correctly from source context.

## 7) Step 6 - Qdrant payload audit

- Qdrant payload is present because `points=3296`.
- DB-vs-Qdrant parity is consistent (`3296 = 3296 = 3296`).

## 8) Step 7 - Runtime Q1-Q8

Status: **RUN COMPLETED**.

Runtime config:

- `temperature=0.2`
- `maxTokens=768`
- `topN=15`

Verdict summary:

- Q1: **PARTIAL** (facts correct but answer text says "không nêu rõ thứ" despite source showing thứ 2)
- Q2: **PARTIAL** (returns đúng GV/B301 nhưng tiết bị diễn giải thành `6` thay vì `5-7`)
- Q3: **PASS**
- Q4: **PASS**
- Q5: **PARTIAL** (scope vẫn chưa đủ strict tuyệt đối)
- Q6: **PARTIAL** (scope vẫn chưa đủ strict tuyệt đối)
- Q7: **PASS**
- Q8: **PASS** (OOS refusal)

## 9) Step 8 classification (if Q5/Q6 still fail)

Q5/Q6 are executable in this rerun and still classified:

- **Case C/D mixed**: rows are retrieved, but strict scoped listing still not perfectly stable in final answer formatting/selection.

## 10) Acceptance criteria matrix

- New DOCX ingest successfully (`INDEXED`): **PASS**
- DB chunks = Qdrant points = API chunkCount: **PASS** (`3296 = 3296 = 3296`)
- `table_row_group = 0`: **PASS**
- `text_table_like = 0`: **PASS**
- `docxTablesUsingMarkdownBridge = 0`: **PASS**
- `valuesDroppedCount = 0`: **PASS**
- Curriculum rows explicit `Khóa ngành`/`Học kỳ`: **PASS** (sampled)
- Kiến trúc K46 HK2 represented in cells_json: **PASS** (sampled)
- Công nghệ sinh học K46 HK2 represented in cells_json: **PASS** (rows present)
- Schedule rows remain clean: **PARTIAL** (packed-cell pattern still observed in DB samples)
- KTR3185 remains clean: **PASS** (canonical curriculum row)
- Q1-Q8 = 8/8 PASS: **FAIL** (current rerun at 4 PASS, 4 PARTIAL)

## 11) Code change status

- Production code changed: **No**.
- Verification/docs only.

## 12) Next recommended task

1. Follow-up scoped retrieval/prompt tuning for Q1/Q2/Q5/Q6 strictness.
2. Keep verify-only approach before any production code change.

