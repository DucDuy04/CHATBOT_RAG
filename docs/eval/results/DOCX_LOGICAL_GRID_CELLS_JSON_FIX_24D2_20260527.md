# DOCX_LOGICAL_GRID_CELLS_JSON_FIX_24D2_20260527

## Verdict

**PASS**

DOCX logical-grid value-to-slot mapping fixed. All target schedule rows now store correct `cells_json` in DB.

## Scope

- Mode: narrow fix + cells_json verify only. No Q1-Q8.
- File: `docs/eval/manual/SoTayHocVu_HocKy1_2025-2026_RAG_CONTEXT_HOC_KY.docx`
- Fix file: `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/NormalizedTableService.java`
- Method fixed: `toLogicalRow()`

---

## Root Cause Summary

In `NormalizedTableService.normalizeRawTable()`, the DOCX schedule table contains group-header rows within the data section. These rows have a merged cell at `physicalColIndex=0` with `gridSpan > 1` (e.g., 8 columns spanning "Ngành: Công nghệ thông tin - Khóa K46").

When `inferHeadersFromCoordinates` computes slot x-ranges from data rows (Step 1), this merged cell sets `slotXEnd[0] = 8` (instead of 1). During `toLogicalRow()`, `bestSlotForCell()` uses x-overlap scoring: regular cells at columns 1..7 each have `overlap = 1` with both the contaminated `slot[0] (x=0, xEnd=8)` and their correct slot. Since tie-breaking uses `>` (strict greater-than), `slot[0]` always wins → all 8 cells pack into STT.

The fix: for DOCX cells (where `extractorType == DOCX`), use `physicalColIndex` directly as the column assignment instead of `bestSlotForCell()` x-overlap. The DOCX logical grid guarantees `physicalColIndex = logicalCol = correct slot index`.

---

## Implementation Summary

Single change in `NormalizedTableService.toLogicalRow()`:

```diff
-    CoordinateHeaderSlot slot = bestSlotForCell(cell, slots);
-    int col = slot == null ? cell.physicalColIndex() : slot.columnIndex();
+    int col;
+    if (cell.extractorType() == RawTableModel.ExtractorType.DOCX
+            && cell.physicalColIndex() >= 0
+            && cell.physicalColIndex() < slots.size()) {
+        col = cell.physicalColIndex();
+    } else {
+        CoordinateHeaderSlot slot = bestSlotForCell(cell, slots);
+        col = slot == null ? cell.physicalColIndex() : slot.columnIndex();
+    }
```

PDF/SPREADSHEET/BASIC paths unchanged.

---

## Tests Run

| Test class | Tests run | Result |
|---|---|---|
| `DocxParserServiceTest` | 4 | PASS |
| `NormalizedTableIngestTest` | 5 | PASS |
| `NormalizedTableSuppressionTest` | 5 | PASS |
| `NoHardcodedLexiconInTableNormalizerTest` | 2 | PASS |
| **Total** | **16** | **0 failures, 0 errors** |

Key test assertions verified:
- `STT = "1"` (not "1 LUA1012 ..." packed string)
- `Mã học phần = "LUA1012"` (not "1 - 2")
- `Tên lớp học phần = "Pháp luật Việt Nam đại cương - Nhóm 1"` (not "E301")
- `Tiết học = "1 - 2"`, `Phòng = "E301"` both correct
- Test 4 specifically verifies merged group-header row does not corrupt regular row slots

---

## No-Hardcode Proof

`NoHardcodedLexiconInTableNormalizerTest` passed: NormalizedTableService production code contains none of: LUA1012, TIN1093, KNM1013, KTR3185, K45, K46, Kiến trúc, Công nghệ sinh học, Nguyễn Thị Vân Anh, Nguyễn Thị Thanh Nhàn, or any cohort/group literals.

---

## Fresh Ingest

- **Chatbot ID**: `88ff1cba-3340-4db4-9101-5e56795b58a8`
- **Document ID**: `834262A0-C2C5-4778-9D1D-849478F4E6B6`
- **File**: `SoTayHocVu_24D2_upload_copy.docx` (copy of `SoTayHocVu_HocKy1_2025-2026_RAG_CONTEXT_HOC_KY.docx`)
- **Ingest status**: FAILED (Nomic Embedding API quota exhausted — free token limit reached)
- **Chunks in DB**: partial (chunking completed before embedding failed)
- **Qdrant points**: 0 (embedding failed, no vectors stored)

> Note: The document parsing, chunking, and cells_json normalization ALL ran successfully and produced correct data in DB. The embedding step failed due to Nomic API quota being exhausted, causing HTTP 500 and document status = FAILED. The cells_json correctness was verified against the DB data produced during this partial run.

---

## DB / Qdrant Audit

### Before (OLD code — ingested at 08:17, 08:51)

```json
{
  "STT": "1 LUA1012 Pháp luật Việt Nam đại cương - Nhóm 1 2 0 Nguyễn Thị Vân Anh 08/09/2025 2",
  "Mã học phần": "1 - 2",
  "Tên lớp học phần": "E301",
  "Số TC": "",
  "Số SV": "",
  "Giảng viên": "",
  "Ngày bắt đầu": "",
  "Thứ": "",
  "Tiết học": "",
  "Phòng": "",
  "Ghi chú": ""
}
```

### After (NEW code — fresh ingest at 09:48, created_at = 2026-05-28 09:48:28)

**LUA1012 Nhóm 1** — CORRECT ✅
```json
{
  "STT": "1",
  "Mã học phần": "LUA1012",
  "Tên lớp học phần": "Pháp luật Việt Nam đại cương - Nhóm 1",
  "Số TC": "2",
  "Số SV": "0",
  "Giảng viên": "Nguyễn Thị Vân Anh",
  "Ngày bắt đầu": "08/09/2025",
  "Thứ": "2",
  "Tiết học": "1 - 2",
  "Phòng": "E301",
  "Ghi chú": ""
}
```

**LUA1012 Nhóm 2** — CORRECT ✅
```json
{
  "STT": "2",
  "Mã học phần": "LUA1012",
  "Tên lớp học phần": "Pháp luật Việt Nam đại cương - Nhóm 2",
  "Số TC": "2",
  "Số SV": "0",
  "Giảng viên": "Nguyễn Thị Vân Anh",
  "Ngày bắt đầu": "08/09/2025",
  "Thứ": "2",
  "Tiết học": "3 - 4",
  "Phòng": "E301",
  "Ghi chú": ""
}
```

**TIN1093 Nhóm 15** — CORRECT ✅
```json
{
  "STT": "193",
  "Mã học phần": "TIN1093",
  "Tên lớp học phần": "Nhập môn lập trình - Nhóm 15",
  "Số TC": "3",
  "Số SV": "0",
  "Giảng viên": "Lê Hữu Bình",
  "Ngày bắt đầu": "15/09/2025",
  "Thứ": "2",
  "Tiết học": "1 - 3",
  "Phòng": "E302",
  "Ghi chú": ""
}
```

**KNM1013 Nhóm 4** — CORRECT ✅
```json
{
  "STT": "11",
  "Mã học phần": "KNM1013",
  "Tên lớp học phần": "Kỹ năng mềm - Nhóm 4",
  "Số TC": "3",
  "Số SV": "0",
  "Giảng viên": "Nguyễn Thị Thanh Nhàn",
  "Ngày bắt đầu": "08/09/2025",
  "Thứ": "6",
  "Tiết học": "5 - 7",
  "Phòng": "B301",
  "Ghi chú": ""
}
```

**KNM1013 Nhóm 2** — CORRECT ✅
```json
{
  "STT": "9",
  "Mã học phần": "KNM1013",
  "Tên lớp học phần": "Kỹ năng mềm - Nhóm 2",
  "Số TC": "3",
  "Số SV": "0",
  "Giảng viên": "Hoàng Ngô Tự Do",
  "Ngày bắt đầu": "08/09/2025",
  "Thứ": "2",
  "Tiết học": "1 - 3",
  "Phòng": "H307",
  "Ghi chú": ""
}
```

**KTR3185 (Kiến trúc)** — CORRECT ✅
```json
{
  "STT": "462",
  "Mã học phần": "KTR3185",
  "Tên lớp học phần": "Đồ án kiến trúc công trình tổ hợp đa chức năng - Nhóm 2",
  "Số TC": "5",
  "Số SV": "0",
  "Giảng viên": "Nguyễn Ngọc Tùng",
  "Ngày bắt đầu": "24/11/2025",
  "Thứ": "2",
  "Tiết học": "5 - 8",
  "Phòng": "E2.01_Xưởng kiến trúc 1-2",
  "Ghi chú": ""
}
```

**Curriculum K46 (Khóa ngành)** — CORRECT ✅
```json
{
  "Khóa ngành": "Xã hội học K46",
  "Học kỳ": "HK1",
  "STT": "5",
  "Mã học phần": "XHH4122",
  "Tên học phần": "Xã hội học Dân số",
  "Số TC": "2",
  "HP bắt buộc": "",
  "Khoa phụ trách": "XHH&CTXH"
}
```

---

## Qdrant Status

Fresh ingest failed at embedding stage → Qdrant has 0 new points from this upload.

The OLD data in Qdrant (from pre-fix ingest 1D390FF1... / 8045FEDE...) still contains the wrong cells_json mapping.

To complete production fix: re-ingest DOCX with a working Nomic API key (or configure backup embedding provider) and delete old chunks.

---

## Known Limitations

1. Qdrant not updated — requires new embedding API key for complete fix
2. Old documents (1D390FF1, 8045FEDE) still have wrong cells_json in both DB and Qdrant
3. DOCX cells with `physicalColIndex >= slots.size()` still fall back to `bestSlotForCell()` — rare edge case, not triggered by this table
4. Group-header rows within table body produce normalized rows where `STT = "Ngành Công nghệ thông tin..."` — these are stored but semantically unusual; follow-up may want to filter/annotate

---

## Next Recommended Task

1. **Task 24D3**: Re-ingest with working embedding API key (Nomic paid/other provider). Delete old wrong documents and re-verify Qdrant payload.
2. **Optional 24D4**: Add group-header row detection to emit `groupContext` instead of a fake `STT` row.
