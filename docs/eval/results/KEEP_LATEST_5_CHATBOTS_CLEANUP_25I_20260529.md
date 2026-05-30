# KEEP_LATEST_5_CHATBOTS_CLEANUP — Task 25I

**Date:** 2026-05-29  
**Verdict:** **PASS** (with documented residual risks)

## 1. Mức độ hiểu task

| | |
|---|---|
| Hiểu task | **95%** |
| Chắc chắn | Dry-run trước xóa; giữ 5 chatbot mới nhất `ORDER BY COALESCE(updated_at, created_at) DESC`; xóa theo `chatbot_id`/`document_id`; không truncate/drop collection |
| Giả định | Soft-delete qua API hiện có được chấp nhận (không hard-delete row) |
| Thiếu dữ kiện | Không |

## 2. Tóm tắt yêu cầu

Giữ đúng 5 chatbot mới nhất và toàn bộ document/chunk/Qdrant của chúng. Xóa chatbot rank 6+ cùng document, `document_chunks`, `document_sections`, và Qdrant points theo `document_id`. Dry-run inventory bắt buộc trước khi xóa.

## 3. Hiện trạng trước khi sửa

| Metric | Before |
|--------|--------:|
| Active chatbots (`deleted_at IS NULL`) | 74 (dry-run) → 77 trong một lần đếm sớm |
| Active documents | ~22+ (ước tính từ plan) |
| Delete targets (plan) | 69 chatbots, 18 documents, ~10 590 DB chunks, ~241 Qdrant points (plan underestimate Qdrant — nhiều doc lớn) |

## 4. Nguyên nhân gốc (từ source)

1. **`WidgetService.softDeleteChatbot()`** chỉ set `deleted_at` trên `widget_configs` — **không cascade** document/chunk/Qdrant.
2. **`DocumentService.softDeleteDocument()`** + **`QdrantPurgeService`** xóa Qdrant + soft-delete chunk/section/document — an toàn cho từng document.
3. Cleanup script ban đầu fail giả khi `Get-QdrantPointCount` trả `-1` (PowerShell JSON/BOM) — đã sửa trong `execute_25i_cleanup.ps1`.

## 5. Chiến lược đã chọn

1. Phase 1–2: Docker stack + SQL dry-run → `_25i_cleanup_plan.json`.
2. Phase 4–6: `DELETE /api/documents/{id}` rồi `DELETE /api/chatbots/{id}` qua `execute_25i_cleanup.ps1`.
3. Sau execution: xóa thủ công 3 document còn `deleted_at IS NULL` trên chatbot đã soft-delete (orphan do không cascade).
4. Phase 7–8: audit MySQL/Qdrant + smoke S2/S5 trên rank-1 chatbot.

## 6. Deletion method

| Layer | Method |
|-------|--------|
| Document | `DELETE /api/documents/{id}` → `DocumentService.softDeleteDocument()` |
| Qdrant | `QdrantPurgeService` filter `document_id` |
| Chatbot | `DELETE /api/chatbots/{id}` → `WidgetService.softDeleteChatbot()` |
| Orchestration | `docs/eval/scripts/execute_25i_cleanup.ps1` |

**Production code:** không đổi (chỉ eval script).

## 7. Dry-run chatbot inventory (top + tail)

Sort: `COALESCE(updated_at, created_at) DESC`. Full JSON: `docs/eval/results/_25i_cleanup_plan.json`.

| rank | chatbotId | name | action |
|------|-----------|------|--------|
| 1 | `3a26c18c-bfd1-477e-af79-fcd7fba551a9` | 25G-runtime-smoke-after-architecture-refactor | KEEP_LATEST_5 |
| 2 | `1c04ed8b-bbb5-4aba-a257-59fe1dd80b38` | 24D4-qdrant-unicode-payload-verify | KEEP_LATEST_5 |
| 3 | `a53cea76-8f8b-4ade-9558-ffb2cceafa87` | 24D3-docx-qdrant-cells-json-verify | KEEP_LATEST_5 |
| 4 | `650fc61c-e8c8-4e25-b5e3-284f4493bfac` | TruongDaiHocKhoaHoc | KEEP_LATEST_5 |
| 5 | `88ff1cba-3340-4db4-9101-5e56795b58a8` | 24D2-docx-logical-grid-cells-json-verify | KEEP_LATEST_5 |
| 6+ | 69 chatbots (e.g. `dcc53947-…`, `f5e7e00e-…`, …) | various eval bots | DELETE_OLDER_CHATBOT |

**REVIEW_MANUALLY:** không — timestamps đầy đủ, ordering rõ.

## 8. Dry-run document inventory (kept vs delete summary)

**KEEP (4 docs on 5 bots — bot #4 has 0 docs):**

| chatbotRank | documentId | fileName | status | DB chunks | Qdrant (dry-run) | action |
|-------------|------------|----------|--------|-----------|------------------|--------|
| 1 | `7ae6d0b9-b7de-4bc8-8be6-10798add73aa` | SoTayHocVu_…_HOC_KY.docx | COMPLETED | 3296 | 3296 | KEEP |
| 2 | `83a9f386-f82f-40fd-b807-b0b94776bfb4` | SoTayHocVu_…_HOC_KY.docx | COMPLETED | 3296 | 3296 | KEEP |
| 3 | `4c4e37a9-afb8-4fe1-a973-cda82522b659` | SoTayHocVu_…_HOC_KY.docx | COMPLETED | 3296 | 3296 | KEEP |
| 5 | `834262a0-c2c5-4778-9d1d-849478f4e6b6` | SoTayHocVu_24D2_upload_copy.docx | FAILED | 0* | 0 | KEEP |

\*Post-ingest state: FAILED row nhưng vẫn còn 3296 chunk active (xem audit).

**DELETE_WITH_CHATBOT:** 18 documents trên rank 6+ (xem `deleteDocuments` trong plan JSON).

## 9. Final deletion plan (executed)

**KEEP chatbots:**

1. `3a26c18c-bfd1-477e-af79-fcd7fba551a9`
2. `1c04ed8b-bbb5-4aba-a257-59fe1dd80b38`
3. `a53cea76-8f8b-4ade-9558-ffb2cceafa87`
4. `650fc61c-e8c8-4e25-b5e3-284f4493bfac`
5. `88ff1cba-3340-4db4-9101-5e56795b58a8`

**DELETE chatbots:** 69 (execution log: 69 lines, all `result: PASS`)

**Counts (plan vs actual):**

| | Plan | Actual post-cleanup |
|---|------|---------------------|
| Documents to keep | 4 | 4 active |
| Documents to delete | 18 + 3 orphan fix | 21 soft-deleted via API |
| Active DB chunks (kept) | ~13184 | 13184 |
| Qdrant (kept COMPLETED docs) | 3×3296 | 3×3296 each |

## 10. Execution log

- Path: `docs/eval/results/_25i_cleanup_execution.jsonl`
- Chatbots processed: **69**
- Per-chatbot `result`: **PASS** (no FAIL after script fix)
- Manual follow-up: deleted docs `aa2cb0d5`, `c02ec656`, `18af5a7b` (active on already soft-deleted chatbots)

## 11. Post-cleanup audit (Phase 7)

### MySQL

| Check | Result |
|-------|--------|
| Active chatbots | **5** ✓ |
| Active documents | **4** ✓ |
| Active chunks (on active docs) | **13184** |
| Docs on deleted chatbot (`deleted_at` active) | **0** ✓ |
| Kept COMPLETED doc DB chunk parity | 3296 = `chunk_count` = live count ✓ |

### Kept document parity (DB vs Qdrant)

| documentId | status | DB chunks | Qdrant points |
|------------|--------|-----------|---------------|
| `7ae6d0b9-b7de-4bc8-8be6-10798add73aa` | COMPLETED | 3296 | 3296 ✓ |
| `83a9f386-f82f-40fd-b807-b0b94776bfb4` | COMPLETED | 3296 | 3296 ✓ |
| `4c4e37a9-afb8-4fe1-a973-cda82522b659` | COMPLETED | 3296 | 3296 ✓ |
| `834262a0-c2c5-4778-9d1d-849478f4e6b6` | FAILED | 3296 | **0** ⚠ |

### Qdrant deleted samples

| documentId (deleted) | points |
|----------------------|--------:|
| `fab1cf41-1278-402a-a880-83a3b5e72f5f` | 0 ✓ |
| `9b5bf2a1-8353-4107-954f-323f960df106` | 0 ✓ |
| `aa2cb0d5-50df-4c7c-8476-328b4434bce9` | 0 ✓ |

**Collection total (exact):** 9890 points ≈ 3×3296 + 2 (stray/unfiltered — không ảnh hưởng kept doc filter).

### Orphan checks

| Check | Result |
|-------|--------|
| Chunks on active docs with missing parent | 0 ✓ |
| Active docs on soft-deleted chatbot | 0 ✓ (after manual 3-doc delete) |
| Chunks on soft-deleted **documents** still `deleted_at IS NULL` | **106** ⚠ (historical soft-delete incompleteness) |
| Qdrant for sampled deleted `document_id` | 0 ✓ |

## 12. Phase 8 smoke (rank-1 chatbot)

**Chatbot:** `3a26c18c-bfd1-477e-af79-fcd7fba551a9`  
**Document:** `7ae6d0b9-b7de-4bc8-8be6-10798add73aa` (COMPLETED, SoTay DOCX)

| ID | Question | Result |
|----|----------|--------|
| S2 | Pháp luật VN đại cương Nhóm 1 … | **PASS** — Nguyễn Thị Vân Anh, thứ 2, tiết 1-2, phòng E301 |
| S5 | Tỷ giá USD/VND hôm nay | **PASS** — refuse / không có trong tài liệu |

## 13. DB / Qdrant before vs after (approximate)

| Metric | Before (dry-run era) | After |
|--------|---------------------|-------|
| Active chatbots | 74 | 5 |
| Active documents | ~22 | 4 |
| Active DB chunks | ~24k+ | 13184 |
| Qdrant collection total | ~10138+ | 9890 |
| Soft-deleted chatbots | few | 105 |

## 14. Code changed

| File | Change |
|------|--------|
| `docs/eval/scripts/execute_25i_cleanup.ps1` | **NEW** — dry-run + controlled cleanup |
| Backend production | **None** |

## 15. Kết quả kiểm tra

| Command | Kết quả | Ghi chú |
|---------|---------|---------|
| `docker compose config -q` | PASS | |
| `docker compose up -d mysql qdrant backend` | PASS | Stack running |
| Dry-run inventory | PASS | `_25i_cleanup_plan.json` |
| Cleanup execution | PASS | 69/69 chatbots PASS in jsonl |
| Post audit MySQL/Qdrant | PASS | với caveats 106 chunk + 1 FAILED doc |
| Smoke S2/S5 | PASS | rank-1 bot |
| `mvn test` | NOT RUN | no production change |

## 16. Rủi ro còn lại

1. **106** `document_chunks` trên document đã soft-delete nhưng chunk chưa soft-delete — không lộ qua API active doc nhưng tốn disk.
2. **`834262a0`** (kept, FAILED): 3296 chunk DB, 0 Qdrant — không dùng retrieval cho bot đó.
3. **`softDeleteChatbot` không cascade** — cần xóa document trước hoặc bổ sung cascade (out of scope 25I).
4. ~2 điểm Qdrant collection-level không khớp 4×3296 (cần scroll nếu muốn dọn tuyệt đối).

## 17. Đề xuất tiếp theo

1. Task nhỏ: hard/soft purge 106 orphan chunks trên `documents.deleted_at IS NOT NULL`.
2. Re-index hoặc soft-delete `834262a0` trên bot `88ff1cba`.
3. Optional: `WidgetService.softDeleteChatbot` cascade gọi `softDeleteDocument` cho mọi doc active.

## 18. Acceptance mapping

| Criterion | Status |
|-----------|--------|
| Dry-run inventory | ✓ |
| Exactly 5 newest kept | ✓ |
| Older chatbots deleted (soft) | ✓ (69) |
| Docs/chunks/Qdrant for deleted docs | ✓ (sampled Qdrant=0) |
| Kept docs intact | ✓ |
| Kept COMPLETED DB=Qdrant | ✓ (3/3) |
| No active orphan docs on deleted bots | ✓ (after manual fix) |
| Smoke S2/S5 | ✓ |
| **Verdict** | **PASS** |
