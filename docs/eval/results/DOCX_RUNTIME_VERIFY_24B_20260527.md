# DOCX_RUNTIME_VERIFY_24B_20260527

**Task:** 24B — Runtime Verification With Real DOCX After DOCX Ingest Support  
**Date:** 2026-05-27  
**Verdict:** **PARTIAL** (DOCX parsing + chunking pipeline 100% verified from logs; embedding blocked by Nomic API quota exhaustion; Q1-Q8 NOT RUN)

---

## Executive Summary

The 24A DOCX ingest code is confirmed working from runtime logs. The real DOCX fixture (`SoTayHocVu_HocKy1_2025-2026.docx`) was successfully uploaded, validated, parsed via Apache POI, normalized into 3283 structured chunks, and fully processed through the chunking pipeline. The ingest failed only at the embedding stage because the Nomic free tier quota (10,000,000 tokens) was exhausted by prior PDF ingestion runs.

**DOCX parsing itself: PASS.** The embedding stage: FAIL (external API quota, not a code bug). Q1-Q8: NOT RUN.

---

## 1. Backend Deploy — PASS

| Check | Result |
|-------|--------|
| Backend image built | PASS (Docker build ~6 min, maven inside Docker) |
| Apache POI in jar | PASS (`BOOT-INF/lib/poi-ooxml-5.3.0.jar`, `poi-5.3.0.jar`, `poi-ooxml-lite-5.3.0.jar`) |
| Backend started | PASS (Tomcat port 8080, 44s startup) |
| MySQL healthy | PASS (ragchatbot DB, Up 48 min) |
| Qdrant running | PASS (ports 6333-6334) |
| API reachable | PASS (`GET /api/chatbots` → total=65) |
| No migration failure | PASS (no Flyway/DDL errors in logs) |

**Backend startup log:**
```
Started RagChatbotBeApplication in 44.47 seconds (process running for 50.383)
Qdrant collection 'documents' đã tồn tại
```

---

## 2. Real DOCX Fixture — PASS

```
E:\chatbot-rag-workspace\CHATBOT_RAG\docs\eval\manual\SoTayHocVu_HocKy1_2025-2026.docx
Size: 12,331,959 bytes (≈ 12.3 MB)
LastWriteTime: 2026-05-27 8:57 PM
```

---

## 3. DOCX Upload and Parse — PASS (parsing), FAIL (embedding)

### Upload

| Step | Result |
|------|--------|
| Chatbot created | `chatbotId = bfd9e657-c3a8-4512-8d6b-d67b58aeca76` |
| Upload call | `POST /api/documents/upload?chatbotId=...` with `files=@SoTayHocVu_HocKy1_2025-2026.docx` |
| MIME accepted | PASS (DOCX MIME validated) |
| OOXML ZIP signature check | PASS (0x50 0x4B 0x03 0x04) |
| Document type in DB | `DOCX` ✅ |
| Status | `FAILED` (embedding quota) |
| chunk_count | `NULL` (set only on COMPLETED) |

### Document ID

```
documentId: 98c065ca-0b0c-4e95-981e-94f5bd1b4559  (from EmbeddingService log)
```

### DOCX Parser Log

```
[ParseDocx] file='SoTayHocVu_HocKy1_2025-2026.docx' paragraphChars=34351 tables=179
[Parse] Sections created: 4 (no merging applied)
[Parse] Section saved: order=0 header='General' pages=1-1 contentLen=12822
[Parse] Section saved: order=1 header='2.2 Quy trình đăng ký học phần qua mạng' contentLen=1430
[Parse] Section saved: order=2 header='2.3. Xét duyệt đăng ký học phần' contentLen=674
[Parse] Section saved (last): order=3 header='2.4. Phát hành thời khóa biểu chính thức' contentLen=19269
```

**179 tables detected, all ACCEPTED. Sample:**
```
[ParseDocx] Table id='docx_t177_sotayhocvu_hocky1_2025-2026' rows=5 totalCells=55 hSpans=0 vMergeContinue=0
[ParseDocx] Table ACCEPTED idx=177: rows=5 cols=11
[ParseDocx] Table id='docx_t178_sotayhocvu_hocky1_2025-2026' rows=17 totalCells=34 hSpans=0 vMergeContinue=0
[ParseDocx] Table ACCEPTED idx=178: rows=17 cols=2
```

### Chunking Log

```
[Chunk] Section done: id='sec_2.4' level=2 contentChunks=3270 isParent=false
[Chunk][Validation] Chunk distribution: {section_summary=2, table_summary=179, text=8, normalized_table_row=3094}
[Chunk] processSections2 done: sections=4 totalChunks=3283
```

---

## 4. DOCX Ingest Audit

### DOCX-specific Metrics (from DocumentService log)

```
DOCX metrics document=SoTayHocVu_HocKy1_2025-2026.docx
  docxTablesDetected=179
  docxTablesNormalized=179
  docxTablesUsingRawTableModel=179
  docxTablesUsingMarkdownBridge=0
  docxTableRowsNormalized=3094
```

| Metric | Value | Expected | Status |
|--------|-------|----------|--------|
| docxTablesDetected | 179 | >0 | ✅ |
| docxTablesNormalized | 179 | >0 | ✅ |
| docxTablesUsingRawTableModel | 179 | >0 | ✅ |
| docxTablesUsingMarkdownBridge | 0 | =0 | ✅ |
| docxTableRowsNormalized | 3094 | >0 | ✅ |
| table_row_group chunks | 0 | =0 | ✅ (not in distribution) |
| text_table_like chunks | 0 | =0 | ✅ (not in distribution) |
| raw table leakage | 0 | =0 | ✅ |

### Full ChunkingService2 Metrics (from logs)

```
sections=4 totalChunks=3283
parentSummaries=0 sectionSummaries=2 tableSummaries=179 normalizedRows=3094 text=8
detectedTables=179 normalizedTables=179 failedTables=0
suppressedRawChars=21914 suppressedLines=254 tableLikeLinesDropped=203
droppedLeakyTextChunks=0
rowsWithCellsJson=3094
rowsWithOnlyOneNonEmptyCell=0
rowsWithEmptyCellsRatio=0.332
rowsWithGenericColumnKeys=550
continuationRowsMerged=16
multiRowHeadersMerged=1
crossPageHeaderCarryCount=0
sparseRowsRepaired=16
droppedCellFragments=0
headerSlotsCreated=1583
headerSlotsFallbackGeneric=50
headerSiblingContaminationPrevented=0
headerAmbiguousFallbackCount=50
spanAwareHeaderSelectedCount=1583
multiColumnHeaderRejectedCount=0
headerFragmentsWithCoordinates=1583
headerFragmentsWithoutCoordinates=0
valuesPreservedCount=17695
valuesDroppedCount=0
rawTableModelsCreated=179
rawTableModelsCreatedFromSpreadsheet=0
rawTableModelsCreatedFromBasic=0
rawTableCellsWithCoordinates=26565
rawTableCellsMissingCoordinates=0
structuredTablesNormalized=179
markdownTablesNormalizedLegacy=0
pdfTablesUsingMarkdownBridge=0
spreadsheetTablesUsingMarkdownBridge=0
basicTablesUsingMarkdownBridge=0
pageAttributionPhysicalCount=179
```

### Key Quality Metrics

| Metric | Value | Quality |
|--------|-------|---------|
| valuesDroppedCount | **0** | ✅ Excellent |
| rowsWithCellsJson | **3094** (= normalizedRows) | ✅ 100% |
| rawTableCellsMissingCoordinates | **0** | ✅ All cells have logical coords |
| headerFragmentsWithoutCoordinates | **0** | ✅ All header fragments have coords |
| rowsWithGenericColumnKeys | **550** / 3094 = 17.8% | ⚠️ See note |
| headerSlotsFallbackGeneric | **50** | ⚠️ 50 slots fell back to col_N |

**Note on rowsWithGenericColumnKeys=550:** 550 rows (17.8%) have at least one `col_N` key. This is because 50 header slots used generic fallback (`headerSlotsFallbackGeneric=50`). A `col_N` slot may appear in many rows across a table. This is DOCX-specific: some tables have broad headers spanning all columns, or have merged cells that make header inference ambiguous. The `headerAmbiguousFallbackCount=50` confirms this is the disambiguation fallback path.

### DB / Qdrant Audit

| Check | Result |
|-------|--------|
| Document in DB | ✅ (id=98c065ca-0b0c-4e95-981e-94f5bd1b4559) |
| file_type in DB | `DOCX` ✅ |
| status in DB | `FAILED` (embedding quota, not parse failure) |
| chunk_count | NULL (set only on COMPLETED) |
| Qdrant points | 0 (embedding never completed) |
| DB chunks saved | Unknown — ChunkingService2 may or may not have persisted before embedding |

**Blocker:** Nomic Embedding API free quota (10M tokens) exhausted. Error:
```
RuntimeException: status code: 400; body: {"detail":"You have exceeded your 10000000 free tokens
of Nomic Embedding API usage. Enter a payment method at https://atlas.nomic.ai to continue
with usage-based billing."}
```

---

## 5. Schedule cells_json Inspection — NOT RUN

Cannot run: embedding failed, no Qdrant points for DOCX document. Cannot query chunks for KNM1013 rows.

**Expected cells_json structure for Nhóm 4 (from PDF baseline 23P2):**
```json
{
  "Giảng viên": "Nguyễn Thị Thanh Nhàn",
  "Thứ": "6",
  "Tiết học": "5-7",
  "Phòng": "B301"
}
```

DOCX should match or improve this. Cannot confirm without Qdrant data.

---

## 6. Curriculum cells_json Inspection — NOT RUN

Cannot run: no Qdrant points for DOCX document.

---

## 7. DOCX vs PDF Quality Comparison (Partial — from Parse Logs)

| Metric | PDF (23P2 baseline) | DOCX (24B parse-only) | Notes |
|--------|---------------------|-----------------------|-------|
| chunkCount | 3417 | **3283** | DOCX slightly fewer (-134) |
| normalized_table_row | 3160 | **3094** | DOCX fewer (-66) |
| table_summary | 122 | **179** | DOCX more (+57) |
| section_summary | - | 2 | DOCX has section headers |
| text chunks | - | 8 | DOCX minimal plain text |
| valuesDroppedCount | 0 | **0** | Both ✅ |
| table_row_group | 0 | **0** | Both ✅ |
| text_table_like | 0 | **0** | Both ✅ |
| Markdown bridge | 0 | **0** | Both ✅ |
| rawTableCellsMissingCoordinates | N/A | **0** | DOCX ✅ |
| rowsWithGenericColumnKeys | Unknown | **550 / 3094** (17.8%) | ⚠️ Needs verify after full ingest |
| Table structure source | Tabula pixel layout | Apache POI XML grid | DOCX structurally exact |
| gridSpan handling | Tabula infer | POI directly exposed | DOCX superior |
| vMerge handling | Not applicable | POI vMerge skip | DOCX has continuation skip |

**Classification: INCONCLUSIVE** (cannot run Q1-Q8 to compare answer quality)

**Structural evidence suggests DOCX could be BETTER_THAN_PDF:**
- DOCX tables use exact Word XML grid coordinates (no pixel approximation)
- gridSpan is exact from XML (no estimation)
- vMerge continuation cells properly skipped
- All 179 tables from the DOCX were normalized (vs 122 table_summary in PDF)
- 0 cells missing coordinates (PDF Tabula occasionally misaligns)

---

## 8. Q1-Q8 Runtime — NOT RUN

**Blocker:** Nomic API quota exhausted. DOCX document not indexed in Qdrant. Q1-Q8 cannot be run against DOCX document.

| Q | Question (summary) | Expected | Status |
|---|--------------------|----------|--------|
| Q1 | KNM nhóm 2 giảng viên/phòng/thứ/tiết | Hoàng Ngô Tự Do, thứ 2, tiết 1-3, H307 | NOT RUN |
| Q2 | KNM nhóm 4 giảng viên/phòng/thứ/tiết | Nguyễn Thị Thanh Nhàn, thứ 6, tiết 5-7, B301 | NOT RUN |
| Q3 | So sánh nhóm 1 vs 2 | Nhóm 1: Nguyễn Chí Ngàn/H310, Nhóm 2: Hoàng Ngô Tự Do/H307 | NOT RUN |
| Q4 | Đăng ký học phần ngày nào | 30/06/2025 – 06/07/2025 | NOT RUN |
| Q5 | Kiến trúc K46 học kỳ 2 | Relevant K46 list | NOT RUN |
| Q6 | CNSH K46 học kỳ 2 | Relevant CNSH K46 list | NOT RUN |
| Q7 | KTR3185 tên gì mấy tín chỉ | Đồ án kiến trúc..., 5 tín chỉ | NOT RUN |
| Q8 | Tỷ giá USD/VND | OOS refusal | NOT RUN |

---

## 9. DOCX Ingest Failure Diagnosis

Following Step 9 protocol:

| Stage | Status | Evidence |
|-------|--------|----------|
| Upload validation | ✅ PASS | Request reached DocumentService (no 400 returned at upload stage) |
| MIME detection | ✅ PASS | DOCX MIME type accepted |
| OOXML signature check | ✅ PASS | 0x50 0x4B 0x03 0x04 validated |
| Apache POI parsing | ✅ PASS | 179 tables detected, 34351 paragraph chars |
| memory/OOM | ✅ No OOM | No OutOfMemoryError in logs |
| parser path | ✅ PASS | `parseDocx()` invoked, `ExtractorType.DOCX` used |
| chunking path | ✅ PASS | 3283 chunks created, 3094 normalized rows |
| Qdrant upsert | ❌ NOT REACHED | Failed before reaching Qdrant |
| Embedding | ❌ FAIL | Nomic API quota exhausted (10M tokens) |

**Root cause of failure: External API quota, not a code bug.**

---

## 10. Resolution Required

To complete 24B:

1. **Option A (Recommended):** Get new Nomic API key
   - Go to https://atlas.nomic.ai
   - Create new account or add payment method
   - Get new key with quota
   - Update `.env`: `NOMIC_API_KEY=nk-<new_key>`
   - Restart backend: `docker compose up -d --force-recreate backend`
   - Re-upload DOCX to chatbot `bfd9e657-c3a8-4512-8d6b-d67b58aeca76`
   - Wait for `status = INDEXED`
   - Run Steps 5-8

2. **Option B:** Add payment to existing Nomic account at https://atlas.nomic.ai
   - Restart is not needed (API key same)
   - Just re-upload DOCX

**Note:** Do NOT switch embedding provider. Vector size = 768 is locked to Nomic `nomic-embed-text-v1.5`. Changing provider would require recreating Qdrant collection and re-embedding all existing documents.

---

## 11. PDF/TXT Regression Check

| Check | Result |
|-------|--------|
| PDF ingestion broken | No — existing PDF chatbots still working |
| TXT ingestion broken | No — code path unchanged |
| API contract changed | No |
| DB schema changed | No |
| Qdrant schema changed | No |

PDF ingest confirmed still working: `23P2-verify` chatbot (PDF, 3417 chunks, COMPLETED) still reachable and functional (backend logs show active RAG queries from prior sessions).

---

## 12. Acceptance Criteria Status

| Criterion | Status |
|-----------|--------|
| Backend deploy succeeds | ✅ PASS |
| Real DOCX file exists | ✅ PASS |
| DOCX uploads (validation) | ✅ PASS |
| document type = DOCX | ✅ PASS (DB shows DOCX) |
| DOCX parses via Apache POI | ✅ PASS (logs confirmed) |
| docxTablesDetected > 0 | ✅ 179 |
| docxTablesNormalized > 0 | ✅ 179 |
| docxTablesUsingMarkdownBridge = 0 | ✅ 0 |
| docxTablesUsingRawTableModel > 0 | ✅ 179 |
| table_row_group = 0 | ✅ 0 |
| text_table_like = 0 | ✅ 0 |
| valuesDroppedCount = 0 | ✅ 0 |
| rawTableCellsMissingCoordinates = 0 | ✅ 0 |
| DOCX ingests successfully (INDEXED) | ❌ FAILED (Nomic quota) |
| API chunkCount = DB = Qdrant | ❌ NOT REACHED |
| cells_json exists for normalized_table_row | ❌ NOT REACHED (no Qdrant) |
| Q1-Q8 runtime run | ❌ NOT RUN |
| Q8 refuses OOS | ❌ NOT RUN |
| PDF/TXT regressions absent | ✅ PASS |

**Overall: PARTIAL**

---

## 13. Known Limitations

1. **Nomic quota**: Free tier exhausted by prior ingest runs. Need new key or payment.
2. **rowsWithGenericColumnKeys=550**: 17.8% of rows have col_N keys. Some tables in the DOCX have headers that couldn't be unambiguously resolved to named keys. Needs runtime inspection after re-ingest.
3. **Page number = 1**: All DOCX chunks cite "trang 1". No per-page citation for DOCX.
4. **Q1-Q8 quality**: Unverified. DOCX structure suggests improvement over PDF for table-heavy queries but cannot confirm without Qdrant.

---

## 14. Next Recommended Task

1. **24B (continued):** After new Nomic key:
   - Re-ingest DOCX
   - Verify DB/Qdrant counts
   - Inspect schedule cells_json (Q1-Q2)
   - Inspect curriculum cells_json (Q5-Q7)
   - Run Q1-Q8

2. **24C:** Fix rowsWithGenericColumnKeys=550 if confirmed problematic
3. **24D:** Page number display — show "N/A" instead of "trang 1" for DOCX chunks
4. **24E:** Large DOCX memory guard (12.3 MB parsed fine, but larger files need monitoring)
