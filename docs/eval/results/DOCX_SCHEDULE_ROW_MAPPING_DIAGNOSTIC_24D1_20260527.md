# DOCX_SCHEDULE_ROW_MAPPING_DIAGNOSTIC_24D1_20260527

## Scope

- Mode: diagnostic only, no production fix.
- File under diagnosis: `docs/eval/manual/SoTayHocVu_HocKy1_2025-2026_RAG_CONTEXT_HOC_KY.docx`.
- Target section/table: `2.4. Phát hành thời khóa biểu chính thức`.
- Target rows:
  - `LUA1012 - Nhóm 1`
  - `LUA1012 - Nhóm 2`
  - `TIN1093 - Nhóm 15`
  - `KNM1013 - Nhóm 2`
  - `KNM1013 - Nhóm 4`

## Diagnostic method

- Direct Apache POI + parser/normalizer diagnostic dump was executed and exported to:
  - `docs/eval/results/_24d1_docx_row_mapping_dump.txt`
- DB rows were checked in MySQL (`document_chunks`) for the same DOCX version (`document_id=1d390ff1-6503-4f5e-bddd-f14562680ee1`).
- Qdrant payload was checked in collection `documents` for matching `chunk_id`.

## Raw DOCX (Apache POI) result

For each target row in table index `208`:

- `XWPFTableRow.getTableCells().size() = 11`
- `gridSpan=1`, `vMerge=none`, `logicalCol` increases 0 -> 11 correctly.
- Each cell value is already in the expected column at POI stage.

Example (`LUA1012 - Nhóm 1`, rowIndex=1):

- `cell[0]="1"`
- `cell[1]="LUA1012"`
- `cell[2]="Pháp luật Việt Nam đại cương - Nhóm 1"`
- `cell[3]="2"`
- `cell[4]="0"`
- `cell[5]="Nguyễn Thị Vân Anh"`
- `cell[6]="08/09/2025"`
- `cell[7]="2"`
- `cell[8]="1 - 2"`
- `cell[9]="E301"`
- `cell[10]=""`

## RawTableModel result

`convertDocxTableToRawTableModel()` preserves row shape correctly:

- `RawTableRow cellCount=11`
- `physicalColIndex=0..10`
- `x=0..10`, `xEnd=1..11`, `width=1` (DOCX logical-grid mapping)
- Text per cell remains correct and separated.

=> RawTableModel is consistent with POI row content.

## Normalized mapping result

Header slots are inferred as expected (`STT`, `Mã học phần`, `Tên lớp học phần`, ... `Ghi chú`), but value-to-slot mapping is wrong:

- Most values from one physical row are mapped into slot `STT`.
- Only period (`1 - 2`, `3 - 4`, `5 - 7`) and room (`E301`, `B301`, `E302`, `H307`) are mapped into slot 1/2.

Observed final `cells_json` pattern (example):

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

=> Mapping becomes incorrect at coordinate normalizer stage (`normalizeRawTable()` path), not in POI parser.

## DB / Qdrant parity

- DB `document_chunks.cells_json` for schedule rows already stores the wrong packed mapping.
- Qdrant payload `cells_json` matches DB exactly (same packed/misaligned values).
- Example checked:
  - DB chunk: `280a19da-e35a-416d-9c2b-6ba53c4a6426` (`row_index=1`)
  - Qdrant (`documents` collection) payload contains same `STT` packed string and `"Mã học phần":"01-Thg2"`, `"Tên lớp học phần":"E301"`.

=> Not a DB-vs-Qdrant divergence; both persist the same normalized output.

## Classification

- `WORD_PACKED_CELL`: **NO**
  - POI shows 11 physical cells with expected per-cell values.
- `PARSER_EXTRACTION_BUG`: **NO**
  - `convertDocxTableToRawTableModel()` keeps 11 separated values correctly.
- `NORMALIZER_MAPPING_BUG`: **YES (PRIMARY)**
  - Wrong mapping appears during coordinate slot assignment in `normalizeRawTable()`.
- `SOURCE_DISPLAY_BUG`: **NO (secondary)**
  - DB and Qdrant payload are already wrong before UI rendering.

## Direct answers to acceptance questions

- Does Word XML row really contain 11 cells? **Yes**.
- Does each cell contain expected value? **Yes (for diagnosed target rows)**.
- At which stage does mapping become wrong? **At NormalizedTableService coordinate normalization/mapping stage**.
- Is this file-format issue or parser/normalizer bug? **Parser/normalizer bug (normalizer mapping), not DOCX file format**.

