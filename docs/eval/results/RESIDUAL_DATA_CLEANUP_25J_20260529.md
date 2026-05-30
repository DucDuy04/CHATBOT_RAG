# RESIDUAL_DATA_CLEANUP — Task 25J

**Date:** 2026-05-29  
**Verdict:** **PASS**

## 1. Mức độ hiểu task

| | |
|---|---|
| Hiểu task | **98%** |
| Chắc chắn | Dry-run trước xóa; soft-delete orphan chunks theo convention project; Option A cho FAILED doc; xóa stray Qdrant theo point ID; giữ 5 chatbot + 3 COMPLETED doc |
| Giả định | Stray 2 điểm Qdrant là residual từ ingest/delete không cascade đủ (payload `document_id` không thuộc active docs) |
| Thiếu dữ liện | Payload chi tiết 2 stray points sau khi đã delete (chỉ lưu point IDs trong dry-run) |

## 2. Tóm tắt yêu cầu

Sau 25I, dọn residual an toàn: 106 orphan chunks trên document đã soft-delete, xử lý FAILED doc `834262a0`, dọn ~2 stray Qdrant points, audit lại DB/Qdrant parity. Không drop collection, không truncate, không sửa production Java.

## 3. Hiện trạng trước khi sửa

| Metric | Before 25J |
|--------|------------|
| Active chatbots | 5 |
| Active documents | 4 (3 COMPLETED + 1 FAILED) |
| Active DB chunks | 13184 |
| Orphan chunks (`chunk active` + `doc deleted`) | **106** |
| FAILED active doc `834262a0` | 3296 DB chunks, 0 Qdrant |
| Qdrant collection total (exact) | **9890** |
| Stray Qdrant points (not in active doc set) | **2** |
| COMPLETED doc parity | 3/3 OK (3296 = Qdrant each) |

## 4. Nguyên nhân gốc (từ source)

1. **`DocumentService.softDeleteDocument()`** cascade chunk/section/table — nhưng document đã xóa **trước 25I** (hoặc chatbot delete không cascade) để lại chunk `deleted_at IS NULL` (comment tại `DocumentService.java:471-475`).
2. **`834262a0`**: ingest FAILED sau partial chunk write — `chunk_count=0` nhưng 3296 rows active; Qdrant never upserted (0 points).
3. **Stray Qdrant**: collection total 9890 vs 3×3296=9888 — scroll audit tìm 2 point IDs không map `document_id` trong active documents.

## 5. Chiến lược sửa đã chọn

| Phase | Action |
|-------|--------|
| 1 | SQL + Qdrant scroll dry-run → `_25j_residual_audit.json` |
| 2 | SQL `UPDATE document_chunks SET deleted_at` join soft-deleted `documents` |
| 3 | **Option A** — `DELETE /api/documents/834262a0` → `DocumentService.softDeleteDocument()` + Qdrant purge (0 points) |
| 4 | Qdrant delete by point IDs (2 stray) |
| 5 | Post-audit + optional smoke rank-1 |

**Production Java:** không đổi. Orchestration: `docs/eval/scripts/execute_25j_cleanup.ps1` (NEW).

## 6. Danh sách file đã đọc

| Path | Mục đích | Kết luận chính |
|------|----------|----------------|
| `DocumentService.java` | Cascade soft-delete | `softDeleteDocument` sets chunk/section/table `deleted_at` |
| `DocumentChunkRepository.java` | Convention | `softDeleteByDocumentId` JPQL pattern |
| `QdrantPurgeService.java` | Qdrant delete | Filter by `document_id` + `widgetId` |
| `execute_25i_cleanup.ps1` | Pattern | Docker mysql + curl Qdrant count |
| `KEEP_LATEST_5_CHATBOTS_CLEANUP_25I_20260529.md` | Baseline | 106 orphan + FAILED doc + 2 stray documented |
| `application-dev.yml` / `docker-compose.yml` | Connectivity | localhost:3306 / 6333 / 8080 |

## 7. Danh sách file đã sửa

| Path | Sửa để làm gì | Layer |
|------|---------------|-------|
| `docs/eval/scripts/execute_25j_cleanup.ps1` | NEW — dry-run + phases 2–5 | docs / eval |
| `docs/eval/results/_25j_residual_audit.json` | Dry-run output | docs |
| `docs/eval/results/_25j_cleanup_results.json` | Execution results | docs |
| `docs/eval/results/_25j_cleanup_execution.jsonl` | Execution log | docs |
| Backend `src/main/java` | **none** | — |

## 8. Diff thay đổi của từng file

### `docs/eval/scripts/execute_25j_cleanup.ps1` (NEW)

- Hiện trạng cũ: không có script 25J.
- Đã sửa: thêm script PowerShell mirror 25I helpers + Phase 1–5.
- Vì sao: repeatable safe cleanup, dry-run gate.
- Ảnh hưởng: chỉ data ops khi chạy script; không ảnh hưởng runtime app.

```diff
+ # 25J — Residual orphan chunks, FAILED kept doc, Qdrant stray points
+ param([switch]$DryRunOnly, [string]$FailedDocAction = "A")
+ # Phase 2 SQL orphan chunk soft-delete
+ # Phase 3 DELETE /api/documents/{834262a0} or SQL fallback
+ # Phase 4 Qdrant scroll + delete stray point IDs
```

### Data mutations (MySQL / Qdrant — không phải file diff)

**Phase 2:**

```sql
UPDATE document_chunks c
INNER JOIN documents d ON d.id = c.document_id
SET c.deleted_at = NOW(6)
WHERE c.deleted_at IS NULL AND d.deleted_at IS NOT NULL;
-- 106 rows affected → orphan count 0
```

**Phase 3:** API `DELETE /api/documents/834262a0-c2c5-4778-9d1d-849478f4e6b6` (`usedApi: true`) — document + 3296 chunks soft-deleted; Qdrant 0→0.

**Phase 4:** Qdrant `POST .../points/delete` with ids:

- `c266fe20-487b-414c-9a58-974d3c75b3fc`
- `ff468a47-4bc1-46f4-8317-7732d6deb56b`

## 9. Ảnh hưởng sau sửa

| Behavior | Change |
|----------|--------|
| Active chatbots | **5** — unchanged |
| Active documents | **4 → 3** (only COMPLETED SoTay DOCX) |
| Active DB chunks | **13184 → 9888** |
| Orphan chunks on deleted docs | **106 → 0** |
| FAILED `834262a0` | soft-deleted; 0 active chunks; 0 Qdrant |
| Qdrant collection total | **9890 → 9888** (= 3×3296) |
| Stray Qdrant points | **2 → 0** |
| COMPLETED doc retrieval | unchanged; parity 3296=DB=Qdrant each |
| Rank-1 chatbot `3a26c18c…` | still 1 COMPLETED doc; smoke OK |
| Bot `88ff1cba…` | 0 active docs (FAILED doc removed) — acceptable |
| Memory/CPU/API cost | one-time cleanup; no ongoing change |
| MySQL/Qdrant old soft-deleted rows | retained for audit (not hard-deleted) |

## 10. Edge cases đã xem xét

- Docker mysql/qdrant down → started `docker compose up -d mysql qdrant backend`
- API delete fail → SQL fallback cascade in script (not needed; API PASS)
- Qdrant scroll timeout → paginated limit 200, max 500 pages guard
- Accidental delete kept doc → script only targets orphan join + explicit FAILED id + stray filter vs active doc UUID set
- Empty stray set → phase 4 no-op
- Backend without GROQ key → smoke would fail (key present; smoke PASS)

## 11. Kết quả kiểm tra

| Command | Kết quả | Ghi chú |
|---------|---------|---------|
| `docker compose up -d mysql qdrant` | PASS | |
| `docker compose up -d backend` | PASS | For API delete + smoke |
| `execute_25j_cleanup.ps1 -DryRunOnly` | PASS | orphan=106, stray=2, total=9890 |
| `execute_25j_cleanup.ps1 -FailedDocAction A` | PASS | phases 2–4 all PASS |
| Post MySQL audit | PASS | 5 bots, 3 docs, 0 orphan, 9888 chunks |
| Post Qdrant total | PASS | 9888 = 3×3296, stray=0 |
| Smoke S2 (playground rank-1) | PASS | Nguyễn Thị Vân Anh, thứ 2, tiết 1-2, E301 |
| `docker compose config -q` | PASS | |
| `mvnw compile` / `mvnw test` | NOT RUN | no production Java change |

## 12. Dry-run residual audit (Phase 1)

### Table 1 — Orphan active chunks on soft-deleted docs

| orphan_chunk_count |
|-------------------:|
| 106 |

Top parent docs (sample): `593d91f0…` (86), `b8258d4b…` (11), …

### Table 2 — FAILED active documents with chunks

| documentId | status | fileName | live_chunks | qdrant |
|------------|--------|----------|------------:|-------:|
| `834262a0-c2c5-4778-9d1d-849478f4e6b6` | FAILED | SoTayHocVu_24D2_upload_copy.docx | 3296 | 0 |

### Table 3 — Active document DB/Qdrant mismatch

| documentId | status | dbChunks | qdrant | parity |
|------------|--------|---------:|-------:|--------|
| `834262a0…` | FAILED | 3296 | 0 | mismatch (expected pre-cleanup) |
| `4c4e37a9…` | COMPLETED | 3296 | 3296 | OK |
| `83a9f386…` | COMPLETED | 3296 | 3296 | OK |
| `7ae6d0b9…` | COMPLETED | 3296 | 3296 | OK |

### Table 4 — Qdrant stray points

| collection_total | stray_point_ids |
|-----------------:|----------------:|
| 9890 | 2 |

IDs: `c266fe20-487b-414c-9a58-974d3c75b3fc`, `ff468a47-4bc1-46f4-8317-7732d6deb56b` (payload `document_id` ∉ active documents)

## 13. Execution summary

| Phase | Before | After | Result |
|-------|-------:|------:|--------|
| 2 orphan chunks | 106 | 0 | PASS |
| 3 FAILED doc `834262a0` (Option A) | active, 3296 chunks | soft-deleted, 0 active chunks | PASS |
| 4 stray Qdrant | 2 | 0 | PASS |

Artifacts: `_25j_residual_audit.json`, `_25j_cleanup_results.json`, `_25j_cleanup_execution.jsonl`

## 14. Post-cleanup audit (Phase 5)

| Check | Result |
|-------|--------|
| Active chatbots | **5** ✓ |
| Active documents | **3** COMPLETED ✓ |
| Active chunks | **9888** ✓ |
| Orphan chunks on deleted docs | **0** ✓ |
| `834262a0` active | **no** (`deleted_at` set) ✓ |
| Qdrant total | **9888** ✓ |
| Stray points | **0** ✓ |
| Per-doc parity (COMPLETED) | 3296 = DB = Qdrant ×3 ✓ |

### Kept chatbots (unchanged)

1. `3a26c18c-bfd1-477e-af79-fcd7fba551a9`
2. `1c04ed8b-bbb5-4aba-a257-59fe1dd80b38`
3. `a53cea76-8f8b-4ade-9558-ffb2cceafa87`
4. `650fc61c-e8c8-4e25-b5e3-284f4493bfac`
5. `88ff1cba-3340-4db4-9101-5e56795b58a8`

### Kept active documents

| documentId | chatbot | status | DB | Qdrant |
|------------|---------|--------|---:|-------:|
| `7ae6d0b9-b7de-4bc8-8be6-10798add73aa` | rank-1 `3a26c18c…` | COMPLETED | 3296 | 3296 |
| `83a9f386-f82f-40fd-b807-b0b94776bfb4` | `1c04ed8b…` | COMPLETED | 3296 | 3296 |
| `4c4e37a9-afb8-4fe1-a973-cda82522b659` | `a53cea76…` | COMPLETED | 3296 | 3296 |

## 15. Optional smoke

**Chatbot:** `3a26c18c-bfd1-477e-af79-fcd7fba551a9`  
**Question:** Pháp luật Việt Nam đại cương Nhóm 1 …  
**Result:** **PASS** — Nguyễn Thị Vân Anh, thứ 2, tiết 1 - 2, phòng E301 (`POST /api/playground/chat` SSE)

## 16. Before / after summary

| Metric | Before 25J | After 25J |
|--------|----------:|----------:|
| Active documents | 4 | 3 |
| Active DB chunks | 13184 | 9888 |
| Orphan chunks | 106 | 0 |
| Qdrant total | 9890 | 9888 |
| Stray Qdrant | 2 | 0 |
| FAILED doc active | 1 | 0 (soft-deleted) |

## 17. Rủi ro còn lại

1. **Soft-deleted rows** (chunks/docs/chatbots) vẫn chiếm disk MySQL — không hard-purge trong scope.
2. **`softDeleteChatbot` không cascade** — future deletes cần document-first hoặc cascade code (out of scope).
3. Chatbot `88ff1cba…` không còn active document — chỉ ảnh hưởng bot eval 24D2, không ảnh hưởng 3 SoTay bots chính.

## 18. Đề xuất tiếp theo

1. Optional hard-delete archived soft-deleted rows sau backup.
2. Optional `WidgetService.softDeleteChatbot` cascade `softDeleteDocument` for active docs.
3. Task eval mới trên 3 COMPLETED docs / rank-1 bot only.

## 19. Acceptance mapping

| Criterion | Status |
|-----------|--------|
| Dry-run residual audit | ✓ |
| 106 orphan chunks cleaned | ✓ |
| FAILED `834262a0` deleted (Option A) | ✓ |
| Qdrant stray cleaned | ✓ |
| 5 kept chatbots | ✓ |
| Good COMPLETED docs intact | ✓ |
| DB/Qdrant parity kept docs | ✓ |
| Optional smoke | ✓ |
| **Verdict** | **PASS** |
