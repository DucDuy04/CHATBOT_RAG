# RUNTIME_SMOKE_AFTER_REFACTOR_25G — Runtime Smoke After Architecture Refactor

**Date:** 2026-05-29  
**Task:** 25G — Runtime smoke verification (post 25B–25F)  
**Verdict:** **PARTIAL**

---

## 1. Mức độ hiểu task

| Item | Value |
|------|-------|
| Hiểu task | **98%** |
| Chắc chắn | E2E smoke on stable DOCX; no production refactor; report ingest/DB/Qdrant/chat |
| Giả định | Chat failures on S1/S3 are retrieval/LLM quality, not package wiring (startup + ingest + Unicode OK) |

---

## 2. Tóm tắt yêu cầu

Verify refactored backend at runtime: Docker → ingest stable DOCX → DB/Qdrant parity → `cells_json` Unicode → representative chat S1–S5.

---

## 3. Hiện trạng trước khi chạy

- 25F: `mvn clean test` **65** tests PASS  
- Production packages: `ingest.*`, `index.*`, `rag.*`, `llm`, `service`  
- Stable fixture present: `docs/eval/manual/SoTayHocVu_HocKy1_2025-2026_RAG_CONTEXT_HOC_KY.docx`

---

## 4. Nguyên nhân gốc (runtime)

| Layer | Finding |
|-------|---------|
| Architecture / wiring | **No failure** — Spring Boot starts, beans load, REST Qdrant upsert, DOCX ingest completes |
| Ingest / Unicode | **PASS** — DB `cells_json` correct; Qdrant payload matches (24D4 REST path intact after refactor) |
| Chat S1 / S3 | **Retrieval or LLM** — correct rows exist in DB (e.g. KNM1013 Nhóm 4, Kiến trúc K46 HK2) but sync chat returned refusal or wrong-scope sources (section 2.4 TKB rows in `topSources`) |

**Not** a `NoClassDefFoundError`, bean, or Nomic quota failure in this run.

---

## 5. Chiến lược verify

1. `mvn clean test` + `docker compose config -q`  
2. `docker compose up` mysql/qdrant/backend (rebuild)  
3. `POST /api/chatbots` → upload DOCX → MySQL + Qdrant audit  
4. Sample `cells_json` DB vs Qdrant by `chunk_id`  
5. `POST /api/chat` (sync) with widget API key for S1–S5  
6. Classify failures; **no production code changes**

---

## 6. Danh sách file đã đọc / dùng

| Path | Mục đích |
|------|----------|
| `docker-compose.yml` | Service wiring |
| `ChatbotController.java`, `DocumentController.java`, `ChatController.java` | API smoke |
| `docs/eval/results/QDRANT_UNICODE_CELLS_JSON_FIX_24D4_20260528.md` | Baseline parity expectations |
| Runtime artifacts `_25g_*` in `docs/eval/results/` | Evidence |

---

## 7. Code changed

**Production:** **No**  
**Test-only / verify artifacts:**

| Path | Layer |
|------|-------|
| `docs/eval/scripts/run_25g_smoke_chat.ps1` | verify script |
| `docs/eval/results/_25g_*.json` | runtime evidence |

---

## 8. Phase results

### Phase 1 — Pre-check

| Command | Result |
|---------|--------|
| `docker compose config -q` | **PASS** |
| `mvn clean test` (JAVA_HOME=jdk-21) | **PASS** — **65** tests, 0 failures, 0 errors |

### Phase 2 — Environment keys

| Check | Result |
|-------|--------|
| Process NOMIC prefix | `nk-RpR4S` |
| User NOMIC prefix | `nk-RpR4S` (match) |
| `.env` prefix | `NOMIC_API_KEY=nk-RpR...` |
| `docker compose exec backend` prefix | `nk-RpR4S` |

### Phase 3 — Docker startup

| Service | Status |
|---------|--------|
| mysql | Up (healthy) |
| qdrant | Up |
| backend | Up — `Started RagChatbotBeApplication` ~26s |

| Check | Result |
|-------|--------|
| NoClassDefFoundError / bean failure | **None** in logs |
| Qdrant gRPC write in active code | **None** (comment-only in `EmbeddingService`) |
| Nomic quota before ingest | **None** |

### Phase 4 — Chatbot

| Field | Value |
|-------|-------|
| Endpoint | `POST http://localhost:8080/api/chatbots` |
| chatbotId / widgetId | `3a26c18c-bfd1-477e-af79-fcd7fba551a9` |
| apiKey (widget) | `c2e09246-...` (not logged in full) |
| HTTP | 200 |

### Phase 5 — Upload

| Field | Value |
|-------|-------|
| File | `SoTayHocVu_HocKy1_2025-2026_RAG_CONTEXT_HOC_KY.docx` |
| Endpoint | `POST /api/documents/upload/{widgetId}` |
| documentId | `7ae6d0b9-b7de-4bc8-8be6-10798add73aa` |
| Client upload duration | **65632 ms** (~65.6 s) |
| API status | `INDEXED`, progress **100**, chunkCount **3296** |

**Ingest metrics (logs):**

- `docxTablesDetected=209`, `docxTablesNormalized=209`, `docxTablesUsingMarkdownBridge=0`  
- `valuesDroppedCount=0`, `structuredTablesNormalized=209`  
- `[EmbeddingUpsert] document=7ae6d0b9-... points=3296 batches=33`

### Phase 6 — DB / Qdrant audit

| Metric | Value |
|--------|------:|
| API chunkCount | 3296 |
| DB chunks | 3296 |
| Qdrant points (filter `document_id`) | 3296 |
| `normalized_table_row` | 3078 |
| `table_summary` | 209 |
| `text` | 7 |
| `section_summary` | 2 |
| `table_row_group` | **0** |
| `text_table_like` | **0** |

*Note: collection total points ~10138 includes older documents; parity uses per-document filter.*

### Phase 7 — cells_json + Unicode samples

| Target | chunk_id (DB) | DB Unicode | Qdrant `cells_json` | Parity |
|--------|---------------|------------|---------------------|--------|
| LUA1012 Nhóm 1 | `677016f4-1d9a-4070-864c-8dbbd4c2a03a` | OK | OK (point `ca801896-...`) | **MATCH** |
| KNM1013 Nhóm 4 | `5ae07335-59af-4e8e-b6fc-3cd5b28f3d5a` | OK | OK (scroll sample) | **MATCH** |
| TIN1093 Nhóm 15 | `36444ffe-2702-4fbd-a438-87e2b5fef989` | OK | (not re-fetched; DB OK) | — |
| KTR3185 | `4d1f96f9-d489-4b30-ad5e-6fe2887d02f6` | OK | — | — |
| Kiến trúc K46 HK2 | `d6925afd-1ec8-4c6f-87d4-8cbe3657e043` | OK | — | — |
| CNS K46 HK2 | `bb1b7b24-c758-4c6f-a43b-2d39c7bf4972` | OK | — | — |

**LUA1012 Nhóm 1 — required shape (DB = Qdrant):**

```json
{"STT":"1","Mã học phần":"LUA1012","Tên lớp học phần":"Pháp luật Việt Nam đại cương - Nhóm 1","Số TC":"2","Số SV":"0","Giảng viên":"Nguyễn Thị Vân Anh","Ngày bắt đầu":"08/09/2025","Thứ":"2","Tiết học":"1 - 2","Phòng":"E301","Ghi chú":""}
```

No mojibake (``) in verified Qdrant payload. No packed STT bug.

---

## 9. Phase 8 — Representative chat smoke

Endpoint: `POST /api/chat` with header `X-Widget-Key`. `topK=10`.

| ID | Question (short) | Verdict | totalMs | sources | Answer summary |
|----|------------------|---------|--------:|--------:|----------------|
| S1 | Kỹ năng mềm Nhóm 4 … | **FAIL** | 14993 | 2 | Refusal: "không tìm thấy…" |
| S2 | Pháp luật … Nhóm 1 … | **PASS** | 16677 | 5 | Nguyễn Thị Vân Anh, thứ 2, tiết 1-2, E301 |
| S3 | Kiến trúc K46 HK2 học phần | **FAIL** | 10654 | 2 | Refusal; topSources = sec 2.4 TKB |
| S4 | CNS K46 HK2 học phần | **PASS** | 10333 | 5 | Lists CNS4082, CNS4352 (HK2 scope) |
| S5 | USD/VND tỷ giá | **PASS** | 6702 | 2 | Refusal OOS (correct) |

**Latency (RAG trace, backend logs):** ~6.7–16.7 s total per question; retrieval + LLM on weak-CPU docker acceptable for smoke.

**Failure classification:**

| ID | Class | Notes |
|----|-------|-------|
| S1 | **E / F** | DB has KNM1013 Nhóm 4 row; answer refused — retrieval/LLM, not ingest |
| S3 | **E / F** | DB has Kiến trúc K46 HK2 rows; wrong `topSources` section — scope/retrieval |
| S2,S4,S5 | — | Align with ingest + retrieval for matched queries |

---

## 10. Phase 9 — Production change decision

**No production changes.** Failures do not show package/import/bean regression; ingest and Unicode path verified on new document.

**Recommended next task:** Targeted retrieval/LLM smoke fix (S1 schedule group match, S3 curriculum scope) — **not** mixed into 25G.

---

## 11. Phase 10 — Stale document warning

Do **not** delete without approval. Older `documents` rows may have pre-24D4 Qdrant mojibake or pre-24D2 packed `cells_json`:

| documentId | file | status | note |
|------------|------|--------|------|
| `83a9f386-f82f-40fd-b807-b0b94776bfb4` | HOC_KY.docx | INDEXED | 24D4 canary |
| `4c4e37a9-afb8-4fe1-a973-cda82522b659` | HOC_KY.docx | INDEXED | 24D3 era |
| `1d390ff1-...`, `8045fede-...` | HOC_KY.docx | INDEXED | pre-fix timeline |
| `9c2f134e-...` | HK_SPLIT.docx | INDEXED | wrong fixture variant |
| `834262a0-...` | 24D2 copy | FAILED | partial ingest |

**This smoke uses only:** `7ae6d0b9-b7de-4bc8-8be6-10798add73aa` on widget `3a26c18c-bfd1-477e-af79-fcd7fba551a9`.

---

## 12. Kết quả kiểm tra (bảng tổng)

| Command | Kết quả | Ghi chú |
|---------|---------|---------|
| `mvn clean test` | **PASS** | 65 tests |
| `docker compose config -q` | **PASS** | |
| Docker stack | **PASS** | |
| DOCX INDEXED | **PASS** | 3296 chunks |
| DB = Qdrant count | **PASS** | 3296 |
| Deprecated chunk types | **PASS** | 0 |
| cells_json Unicode | **PASS** | LUA1012 sample |
| Chat S1–S5 | **PARTIAL** | 3 PASS, 2 FAIL |
| Production code changed | **No** | |

---

## 13. Rủi ro còn lại

- Multi-document Qdrant collection (~10k points) — always filter by `document_id` / widget for eval  
- S1/S3 chat quality not gated by architecture refactor PASS  
- Full Q1–Q26 not run (out of scope)

---

## 14. Đề xuất tiếp theo

1. **25H or fix task:** Debug S1/S3 retrieval scope (curriculum vs TKB section lock) on widget `3a26c18c-...`  
2. Optional: purge stale Qdrant points after explicit approval  
3. Re-run S1/S3 after retrieval fix

---

## Final verdict rationale

| Criterion | Met? |
|-----------|------|
| mvn test + docker config | Yes |
| Backend starts, no wiring error | Yes |
| Stable DOCX INDEXED | Yes |
| API = DB = Qdrant | Yes |
| Deprecated types 0 | Yes |
| cells_json Unicode parity | Yes |
| All chat smoke PASS | **No** (S1, S3) |
| OOS S5 | Yes |

→ **PARTIAL** (pipeline PASS; representative chat incomplete).
