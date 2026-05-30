# CURSOR Report 27A — Latency Profiling & Safe Optimization

**Task:** 27A Latency Profiling and Safe Optimization for RAG Runtime  
**Date:** 2026-05-30  
**Final verdict:** **PASS**

---

## 1. Mức độ hiểu task

- **95%** — profiling, safe optimization, no semantic change, no adaptive n/topK, no answer cache.
- **Chắc chắn:** trace stages, keyword index cold cost, existing embed cache, batch DB repos.
- **Giả định:** steady-state benchmark sau warm-up = typical chat; cold-start = restart edge case.

---

## 2. Tóm tắt yêu cầu

Measure latency by stage → classify bottleneck → apply evidence-based safe fixes → regression tests → before/after benchmark → reports.

---

## 3. Hiện trạng trước khi sửa

- Latency trace thiếu DB/persist/rerank/retrieval aggregate.
- Keyword index invalidated on ingest, not prewarmed → first chat ~12s index build.
- Query embed cache existed but lacked integration tests.
- Steady-state chat ~6.7–16.7s (smoke 25G); LLM ~1–2s, not dominant.

---

## 4. Nguyên nhân gốc

1. **Cold keyword index build** (`KeywordIndexCache` miss after invalidate/restart) — up to **12s**.
2. **Retrieval pipeline** (keyword + cell-aware scoring + DB + Qdrant variants) — **~60–70%** steady-state.
3. LLM Groq — **~6–15%** steady-state — **not** primary optimization target.

---

## 5. Chiến lược sửa

- Extend `RagLatencyTrace` only.
- Prewarm keyword index post-ingest.
- Add benchmark script + cache tests.
- No parser/normalizer/Qdrant payload/schema changes.

---

## 6. Danh sách file đã đọc

| Path | Mục đích | Kết luận |
|---|---|---|
| `audit/metrics/RagLatencyTrace.java` | Stage tracing | Thiếu db/persist/rerank |
| `index/embedding/EmbeddingService.java` | Embed cache | Cache đã có sẵn |
| `rag/retrieve/KeywordIndexCache.java` | Keyword index | Invalidate only on ingest |
| `rag/retrieve/RagRetrievalService.java` | Retrieval path | Batch DB, multi-variant Qdrant |
| `rag/retrieve/KeywordSearchService.java` | Hybrid keyword | Index lookup per request |
| `rag/rerank/RerankService.java` | Rerank | Cohere optional, ~300–700ms |
| `rag/runtime/ChatService.java` | Chat sync | saveChatMessage untraced |
| `service/DocumentService.java` | Ingest | invalidate without warm |
| `agent/05-api.md` | API endpoints | `/api/chat` + X-Widget-Key |

---

## 7. Danh sách file đã sửa

| Path | Mục đích | Layer |
|---|---|---|
| `RagLatencyTrace.java` | dbMs, persistMs, rerankMs, retrievalMs | metrics |
| `RagRetrievalService.java` | timedDbFetch wrapper | retrieval |
| `KeywordIndexCache.java` | warm() method | retrieval |
| `RerankService.java` | postRerankRequest timing | rerank |
| `ChatService.java` | persistMs in saveChatMessage | runtime |
| `DocumentService.java` | warm after invalidate | ingest |
| `latency_bench_27a.ps1` | benchmark tooling | docs/tooling |
| `KeywordIndexCacheTest.java` | cache tests | test |
| `EmbeddingServiceQueryCacheIntegrationTest.java` | embed cache tests | test |

---

## 8. Diff thay đổi

### `RagLatencyTrace.java`

- **Cũ:** không có dbMs, persistMs, rerankMs, retrievalMs.
- **Sửa:** thêm fields + adders; `finish()` log retrievalMs aggregate.
- **Vì sao:** task yêu cầu stage breakdown đủ cho bottleneck audit.

```diff
+ private long dbMs;
+ private long persistMs;
+ private long rerankMs;
+ public void addDbMs(long ms) { dbMs += Math.max(0, ms); }
+ public void addPersistMs(long ms) { persistMs += Math.max(0, ms); }
+ public void addRerankMs(long ms) { rerankMs += Math.max(0, ms); }
+ long retrievalMs = queryAnalyzeMs + queryEmbedMs + vectorMs + dbMs + keywordMs
+         + keywordIndexBuildMs + mergeMs + scoringMs + sourceDiversityMs + contextSelectMs;
```

### `KeywordIndexCache.java` + `DocumentService.java`

- **Cũ:** invalidate sau ingest → first chat builds index.
- **Sửa:** `warm(widgetId, corpusLimit)` + gọi sau invalidate.
- **Vì sao:** profiling cold query keywordIndexBuildMs=12177ms.

```diff
+ public void warm(UUID widgetId, int corpusLimit) { ... buildIndex on miss ... }
  keywordIndexCache.invalidate(widgetId, "document_indexed");
+ keywordIndexCache.warm(widgetId, keywordIndexCorpusLimit);
```

### `RagRetrievalService.java`

- **Sửa:** `timedDbFetch(Supplier)` wraps repository calls; split rerank vs cellAware timing.
- **Vì sao:** measure DB expansion without changing fetch semantics.

### `RerankService.java`

- **Sửa:** `postRerankRequest()` centralizes API call + `addRerankMs`.
- **Vì sao:** separate rerank from cellAwareMs in logs.

### `ChatService.java`

- **Sửa:** `saveChatMessage` wrapped with persistMs timing.

### Tests

- `KeywordIndexCacheTest`: no rebuild on repeat, invalidate rebuilds, warm pre-builds.
- `EmbeddingServiceQueryCacheIntegrationTest`: same query → 1 embed call; different → 2; clear cache → 2.

---

## 9. Ảnh hưởng sau sửa

| Behavior | Change |
|---|---|
| Chat answer semantics | **Unchanged** |
| Retrieval top-K / hybrid | **Unchanged** |
| Ingest flow | **+keyword index warm** after COMPLETED (~12s during upload, not first chat) |
| Latency logs | **+dbMs, persistMs, rerankMs, retrievalMs** |
| Memory | Keyword index still in-memory per widget (unchanged) |
| LLM/token cost | Unchanged |

---

## 10. Edge cases

- Backend restart without re-upload → first chat still builds index once.
- Empty widget corpus → warm no-op / empty index.
- Cohere disabled → rerankMs=0, scoring fallback unchanged.
- Concurrent ingest + chat → synchronized keyword cache map (existing).

---

## 11. Kết quả kiểm tra

| Command | Kết quả | Ghi chú |
|---|---|---|
| `docker compose config -q` | **PASS** | |
| `mvn clean test` | **PASS** | 77 tests, 0 failures |
| Runtime L1–L5 benchmark | **PASS** | See eval report |
| Frontend lint | **NOT RUN** | Out of scope |
| Frontend build | **NOT RUN** | Out of scope |
| Widget build | **NOT RUN** | Out of scope |

---

## 12. Rủi ro còn lại

- Cold-start after backend restart still pays index build unless future startup warm task.
- Multi-variant Qdrant search still duplicates embed/search for rewritten queries.
- `persistMs` spikes on some runs (JPA) — monitor on weak production MySQL.
- Rerank API cost when `COHERE_RERANK_ENABLED=true`.

---

## 13. Đề xuất tiếp theo

See `docs/eval/results/LATENCY_PROFILE_OPTIMIZATION_27A_20260530.md` §13.

---

## Final summary

- **Hiểu task:** 95%
- **Root cause:** Cold keyword index build + retrieval/scoring pipeline (not LLM) dominates latency
- **Code changed:** Yes (tracing + prewarm + tests + benchmark script)
- **Eval report:** `docs/eval/results/LATENCY_PROFILE_OPTIMIZATION_27A_20260530.md`
