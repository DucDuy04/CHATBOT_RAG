# CURSOR REPORT 24D3 - DOCX QDRANT CELLS JSON VERIFY

## 1) Muc do hieu task
- Muc do hieu: **98%**.
- Chac chan:
  - Can verify-only, khong sua production code.
  - Can re-ingest DOCX sau 24D2 va doi chieu DB/Qdrant `cells_json`.
  - PASS chi duoc claim khi Qdrant payload parity voi DB tren document moi.
- Gia dinh:
  - Status ingestion trong he thong co the hien la `COMPLETED` o upload response va `INDEXED` o list API (thuc te da xac nhan dung nhu vay).
- Thieu du kien:
  - Khong co expected key prefix cu the (chi check duoc prefix dang active trong container va tinh trang log quota trong session moi).

## 2) Tom tat yeu cau
- Kiem tra Nomic key/quota trong backend container.
- Recreate backend neu can.
- Tao chatbot moi, upload dung file DOCX chi dinh.
- Verify ingest audit (chunk counts, chunk types, metrics quan trong).
- Verify parity DB vs Qdrant payload `cells_json` cho danh sach target rows.
- Canh bao stale documents cu.
- Tao 2 report ket qua.

## 3) Hien trang truoc khi sua
- Repo dang dirty tu truoc (nhieu file modified/untracked).
- Docker service ban dau khong running.
- Log cu cho thay Nomic quota exceeded o session truoc.
- 24D2 da xac nhan DB cells_json dung sau fix, nhung Qdrant cho fresh document 24D2 chua co points do fail embedding.

## 4) Nguyen nhan goc xac nhan tu source/runtime
- Sau khi ingest lai 24D3:
  - DB luu `cells_json` dung unicode, dung gia tri target.
  - Qdrant point count du va khop DB/API count.
  - Nhung `payload.cells_json` trong Qdrant bi mojibake/encoding corruption.
- Root cause runtime quan sat: **duong serialize/ghi payload `cells_json` vao Qdrant lam sai unicode**, dan toi DB/Qdrant mismatch du chunk count van dung.

## 5) Chien luoc xu ly da chon
- Giu nguyen production code (verify-only).
- Thuc hien lai luong ingest end-to-end tren file DOCX duoc chi dinh.
- Thu thap bang chung 3 lop:
  - API state (`INDEXED`, `progress`, `chunkCount`)
  - MySQL counts + row samples
  - Qdrant count + payload samples
- Doi chieu parity theo tung `chunk_id` target.

## 6) Danh sach file da doc
- `.cursor/rules/00-core-working-rule.mdc`
  - Muc dich: tuan thu quy trinh source-first/minimal diff.
  - Ket luan: can tao report va khong sua lan scope.
- `.cursor/rules/90-report-verification-rule.mdc`
  - Muc dich: format report bat buoc.
  - Ket luan: report can 13 muc, ghi trung thuc command PASS/FAIL/NOT RUN.
- `agent.md`
  - Muc dich: tong quan he thong.
  - Ket luan: upload endpoint va luong RAG hien hanh.
- `agent/01-overview.md`
  - Muc dich: xac nhan API chinh.
  - Ket luan: `POST /api/widgets`, `POST /api/documents/upload/{widgetId}`.
- `agent/02-architecture.md`
  - Muc dich: xac nhan Qdrant payload metadata.
  - Ket luan: payload co `chunk_id`, `table_name`, `row_index`, `cells_json`.
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/record/DocumentChunk.java`
  - Muc dich: xac nhan `cellsJson` la field pipeline.
  - Ket luan: `cellsJson` la metadata cho DB/Qdrant.
- Log file runtime trich xuat tu `docker compose logs backend --tail=400`
  - Muc dich: xac nhan ingest metrics.
  - Ket luan: `valuesDroppedCount=0`, `docxTablesUsingMarkdownBridge=0`, embed Qdrant thanh cong 3296 vectors.

## 7) Danh sach file da sua
- `docs/eval/results/DOCX_QDRANT_CELLS_JSON_VERIFY_24D3_20260528.md`
  - Sua de ghi ket qua verify 24D3.
  - Anh huong lop: `docs`.
- `reports/refactor/CURSOR_REPORT_24D3_DOCX_QDRANT_CELLS_JSON_VERIFY.md`
  - Sua de audit day du task theo rule workspace.
  - Anh huong lop: `docs`.

## 8) Diff thay doi cua tung file

### File: `docs/eval/results/DOCX_QDRANT_CELLS_JSON_VERIFY_24D3_20260528.md`
- Hien trang cu: chua co file report 24D3.
- Da sua: tao moi report ket qua verify, gom verdict, ingest audit, parity table, stale warning.
- Vi sao: dap ung output artifact bat buoc cua task 24D3.
- Anh huong: them tai lieu ket qua runtime de reviewer audit nhanh.

```diff
++ docs/eval/results/DOCX_QDRANT_CELLS_JSON_VERIFY_24D3_20260528.md
@@
+# DOCX Qdrant cells_json Verify 24D3 (2026-05-28)
+...
+- **FAIL**
+...
+- chatbotId: `a53cea76-8f8b-4ade-9558-ffb2cceafa87`
+- documentId: `4c4e37a9-afb8-4fe1-a973-cda82522b659`
+...
+| LUA1012 - Nhom 1 | ... | MISMATCH | WRONG |
+...
```

### File: `reports/refactor/CURSOR_REPORT_24D3_DOCX_QDRANT_CELLS_JSON_VERIFY.md`
- Hien trang cu: chua co report refactor 24D3.
- Da sua: tao report audit day du 13 muc theo rule.
- Vi sao: bat buoc theo workspace rule 90.
- Anh huong: tang kha nang trace/audit thay doi va ket qua verify.

```diff
++ reports/refactor/CURSOR_REPORT_24D3_DOCX_QDRANT_CELLS_JSON_VERIFY.md
@@
+# CURSOR REPORT 24D3 - DOCX QDRANT CELLS JSON VERIFY
+## 1) Muc do hieu task
+...
+## 13) De xuat tiep theo
+...
```

## 9) Anh huong sau sua
- Behavior thay doi:
  - Khong co behavior production thay doi (khong sua code runtime).
- Behavior giu nguyen:
  - Pipeline ingest/embedding/retrieval van nhu truoc.
- Dieu kien bat:
  - Chi co them docs report.
- Fallback giu nguyen:
  - Khong thay doi fallback logic nao.
- Tai nguyen (memory/cpu/disk):
  - Chi tang nho dung luong docs markdown.
- Latency/token/API cost:
  - Co phat sinh chi phi embedding do thuc hien 1 lan ingest verify.
- Du lieu MySQL/Qdrant cu:
  - Co them 1 document moi 24D3; document cu duoc danh dau stale canh bao, chua xoa.

## 10) Edge cases da xem xet
- Backend container khong chay luc bat dau.
- Nomic quota error tu session cu.
- File yeu cau goc khong ton tai, fallback sang ten file thay the trong task.
- Widget create request thieu `allowedOrigin` gay 500 (runtime validation), da bo sung request data de tiep tuc verify.
- Count query MySQL voi UUID binary can `UUID_TO_BIN`.
- Qdrant co du point count nhung payload string van co the sai encoding.

## 11) Ket qua kiem tra

| Command | Ket qua | Ghi chu |
|---|---|---|
| `docker compose ps` | PASS | backend/mysql/qdrant running sau recreate |
| `docker compose exec backend sh -lc 'echo ${NOMIC_API_KEY:0:8}'` | PASS | prefix `nk-RpR4S` |
| `docker compose logs backend --tail=100` | PASS | khong thay quota error trong session moi truoc ingest |
| `docker compose up -d --force-recreate backend` | PASS | recreate thanh cong |
| `POST /api/widgets` | PASS | tao chatbot moi (sau khi bo sung `allowedOrigin`) |
| `POST /api/documents/upload/{widgetId}` | PASS | upload+ingest thanh cong, 3296 chunks |
| `GET /api/documents` | PASS | document moi `INDEXED`, `progress=100`, `chunkCount=3296` |
| SQL count `document_chunks` | PASS | DB chunks=3296, normalized_table_row=3078, table_summary=209 |
| SQL chunk type gate checks | PASS | table_row_group=0, text_table_like=0, raw fallback=0 |
| Qdrant count API | PASS | points=3296 cho document moi |
| DB vs Qdrant cells_json parity script | FAIL | 8/8 targets MISMATCH do Qdrant payload encoding loi |
| `docker compose config` | PASS | compose config hop le |
| `cd Backend && ./mvnw -DskipTests compile` | NOT RUN | task verify ingestion/parity, khong sua code |
| `cd Backend && ./mvnw test` | NOT RUN | task verify ingestion/parity, khong sua code |
| `cd Frontend && npm run lint` | NOT RUN | task verify ingestion/parity, khong sua code |
| `cd Frontend && npm run build` | NOT RUN | task verify ingestion/parity, khong sua code |
| `cd Frontend && npm run build:widget` | NOT RUN | task verify ingestion/parity, khong sua code |

## 12) Rui ro con lai
- Qdrant payload `cells_json` hien tai khong giu duoc unicode dung -> co nguy co giam chat luong retrieval/prompt/table answer.
- Co stale indexed documents cu trong he thong, neu tiep tuc retrieve tren cac widget do thi co the tra ve context loi.

## 13) De xuat tiep theo
- Mo task debug rieng cho serialize `cells_json` vao Qdrant payload (encoding path).
- Sau khi fix, ingest canary 1 file DOCX va verify lai parity DB/Qdrant cho it nhat cac row target cua 24D3.
- Neu parity dat, de xuat cleanup stale document IDs (chi xoa khi co approval).
