# DOCX Qdrant cells_json Verify 24D3 (2026-05-28)

## Final verdict
- **FAIL**
- Ly do: document moi index thanh cong, chunk count DB/Qdrant khop, nhung `cells_json` trong payload Qdrant cua document moi bi loi encoding va **khong parity** voi DB.

## Scope va rang buoc da tuan thu
- VERIFY ONLY, khong sua production code.
- Khong chay Q1-Q8.
- Khong re-enable markdown bridge / table_row_group / text_table_like / raw fallback.

## File ingest da dung
- File yeu cau goc khong ton tai: `docs/eval/manual/SoTayHocVu_HocKy1_2025-2026_RAG_CONTEXT_HK_SPLIT.docx`.
- File thuc te da dung: `docs/eval/manual/SoTayHocVu_HocKy1_2025-2026_RAG_CONTEXT_HOC_KY.docx`.

## Step 1-2: Nomic key/quota status
- `docker compose ps`: backend/mysql/qdrant deu running.
- `docker compose exec backend sh -lc 'echo ${NOMIC_API_KEY:0:8}'` => `nk-RpR4S`.
- Log backend truoc ingest moi: khong thay quota error trong session container moi.
- Co recreate backend bang `docker compose up -d --force-recreate backend` truoc ingest.

## Step 3: Tao chatbot + upload
- chatbotId: `a53cea76-8f8b-4ade-9558-ffb2cceafa87`
- documentId: `4c4e37a9-afb8-4fe1-a973-cda82522b659`
- Upload endpoint response: `COMPLETED`, message tao 3296 chunks.
- API document list: `status=INDEXED`, `progress=100`, `chunkCount=3296`.
- Upload duration (client): `77986 ms` (~78s).
- Ingest duration: dong bo trong request upload (khong co async queue tach rieng), xap xi ~78s.

## Step 4: Ingest audit
- document type: `DOCX` ✅
- status: `INDEXED` ✅
- API chunkCount: `3296` ✅
- DB chunk count: `3296` ✅
- Qdrant point count: `3296` ✅
- `normalized_table_row`: `3078` (>0) ✅
- `table_summary`: `209` (>0) ✅
- `table_row_group`: `0` ✅
- `text_table_like`: `0` ✅
- raw fallback chunk types: `0` ✅
- `docxTablesUsingMarkdownBridge`: `0` ✅ (tu log DocumentService DOCX metrics)
- `valuesDroppedCount`: `0` ✅ (tu log ChunkingService2)
- `rowsWithCellsJson`: `3078` = `normalized_table_row` ✅

## Step 5: DB vs Qdrant cells_json parity (document moi)

### Bang parity
| Target | chunkId | rowIndex | tableName | Parity | Verdict |
|---|---|---:|---|---|---|
| LUA1012 - Nhom 1 | `8fcdf7ed-6369-4d2f-99f5-136bb79b07c3` | 1 | `2.4. Phát hành thời khóa biểu chính thức` | MISMATCH | WRONG (Qdrant encoding loi) |
| LUA1012 - Nhom 2 | `9728a61a-dc03-4b73-b123-1a4bb8b136a3` | 2 | `2.4. Phát hành thời khóa biểu chính thức` | MISMATCH | WRONG (Qdrant encoding loi) |
| TIN1093 - Nhom 15 | `dd8e878a-31ee-454e-b772-a138cca5348a` | 378 | `2.4. Phát hành thời khóa biểu chính thức` | MISMATCH | WRONG (Qdrant encoding loi) |
| KNM1013 - Nhom 2 | `14c9f273-2fa5-4103-9beb-b7ba29ca6346` | 9 | `2.4. Phát hành thời khóa biểu chính thức` | MISMATCH | WRONG (Qdrant encoding loi) |
| KNM1013 - Nhom 4 | `9bce43fc-f948-47ab-8bb8-c4b250ea2ce6` | 11 | `2.4. Phát hành thời khóa biểu chính thức` | MISMATCH | WRONG (Qdrant encoding loi) |
| KTR3185 sample | `0aa33299-6341-4728-ae7e-9c563457473c` | 715 | `2.4. Phát hành thời khóa biểu chính thức` | MISMATCH | WRONG (Qdrant encoding loi) |
| Kien truc K46 HK2 sample | `00518663-ed30-4985-9fea-6c46e8d360bd` | 1 | `2.4. Phát hành thời khóa biểu chính thức` | MISMATCH | WRONG (Qdrant encoding loi) |
| Cong nghe sinh hoc K46 HK2 sample | `70de930c-ef91-4b63-bf66-8a4244a84190` | 1 | `2.4. Phát hành thời khóa biểu chính thức` | MISMATCH | WRONG (Qdrant encoding loi) |

### Mau DB cells_json (dung)
- LUA1012 Nhom 1 (DB):
```json
{"STT":"1","Mã học phần":"LUA1012","Tên lớp học phần":"Pháp luật Việt Nam đại cương - Nhóm 1","Số TC":"2","Số SV":"0","Giảng viên":"Nguyễn Thị Vân Anh","Ngày bắt đầu":"08/09/2025","Thứ":"2","Tiết học":"1 - 2","Phòng":"E301","Ghi chú":""}
```
- LUA1012 Nhom 2 (DB):
```json
{"STT":"2","Mã học phần":"LUA1012","Tên lớp học phần":"Pháp luật Việt Nam đại cương - Nhóm 2","Số TC":"2","Số SV":"0","Giảng viên":"Nguyễn Thị Vân Anh","Ngày bắt đầu":"08/09/2025","Thứ":"2","Tiết học":"3 - 4","Phòng":"E301","Ghi chú":""}
```

### Mau Qdrant payload cells_json (sai encoding)
- LUA1012 Nhom 1 (Qdrant):
```json
{"STT":"1","MA� h��?c ph��n":"LUA1012","TA�n l��?p h��?c ph��n":"PhA�p lu��-t Vi��?t Nam �?���i c����ng - NhA3m 1","S��? TC":"2","S��? SV":"0","Gi���ng viA�n":"Nguy��?n Th��? VA�n Anh","NgA�y b��_t �?��u":"08/09/2025","Th��c":"2","Ti���t h��?c":"1 - 2","PhA�ng":"E301","Ghi chA�":""}
```

## Step 6: Old wrong document warning (stale)
Khong xoa tu dong. De nghi danh dau stale cac document pre-fix/legacy sau:
- `1d390ff1-6503-4f5e-bddd-f14562680ee1` (`...HOC_KY.docx`, old run)
- `8045fede-bc22-42ed-92e5-ca6e529b3cea` (`...HOC_KY.docx`, old run)
- `9c2f134e-fc17-4928-b91b-eb3330ee4019` (`...HK_SPLIT.docx`, old run)

Ly do stale: du lieu cuoc ingest khac timeline fix 24D2 va user requirement da neu "khong trust old pre-fix documents"; ket qua 24D3 cung cho thay payload Qdrant co risk encoding loi nen can canh bao tach biet.

## Code change status
- Production code changed: **No**
- Chi tao/cap nhat report docs.

## Known limitations
- Qdrant payload unicode hien thi/luu bi loi cho `cells_json` tieng Viet trong document moi.
- Chua tien hanh rollback/xoa stale documents do khong co approval xoa.

## Next recommended task
- Tao task debug rieng cho path serialize `cells_json` vao Qdrant payload (nghi van encoding/chuyen ma ky tu tren duong ghi payload), sau do re-ingest lai 1 document canary va verify parity truoc khi cleanup stale docs.
