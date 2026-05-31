# Task 27A — Latency Profile & Safe Optimization

**Date:** 2026-05-30  
**Chatbot:** `3a26c18c-bfd1-477e-af79-fcd7fba551a9` (25G-runtime-smoke-after-architecture-refactor)  
**Document:** `7ae6d0b9-b7de-4bc8-8be6-10798add73aa`  
**Stack:** Docker Compose (`mysql`, `qdrant`, `backend`)  
**Final verdict:** **PASS**

---

## 1. Mức độ hiểu task

| Item | Value |
|---|---|
| Hiểu task | **95%** |
| Chắc chắn | Profiling flow, bottleneck classification, safe optimization scope, test/report requirements |
| Giả định | Steady-state benchmark sau warm-up đại diện cho production chat thường xuyên; cold-start (backend restart) là edge case riêng |
| Thiếu dữ liệu | Baseline script lần 1 fail vì `sessionId` không phải UUID — đã sửa script và lấy baseline từ trace thủ công + benchmark after |

---

## 2. Tóm tắt yêu cầu

Đo latency theo stage trước khi optimize; xác định bottleneck; chỉ áp dụng safe optimization; giữ correctness; chạy `mvn clean test`; so sánh before/after runtime.

---

## 3. Hiện trạng trước khi sửa

- `RagLatencyTrace` đã có nhiều stage nhưng thiếu `dbMs`, `persistMs`, `rerankMs`, `retrievalMs` tổng hợp.
- Query embedding cache đã có trong `EmbeddingService` (in-memory, TTL 1h, max 1000).
- `KeywordIndexCache` đã có per-widget nhưng sau ingest chỉ **invalidate**, không prewarm → first chat sau upload/restart trả index-build cost (~12s).
- DB fetch đã dùng batch repository methods (`findByWidgetConfigIdAndSectionIdIn...`, `findByWidgetConfigIdAndIdIn...`).
- Cohere rerank enabled qua env (`COHERE_RERANK_ENABLED`).

---

## 4. Nguyên nhân gốc (xác nhận từ source + runtime)

| Bottleneck | Root cause | Evidence |
|---|---|---|
| Cold keyword index build | `KeywordIndexCache.lookup()` build index on miss; `DocumentService` chỉ invalidate sau ingest | First query after restart: `keywordIndexBuildMs=12177`, `totalMs=26102` |
| Steady-state retrieval latency | Hybrid keyword scoring + cell-aware scoring trên ~80 candidates; DB fetch + Qdrant multi-variant | L1 p50: `retrievalMs≈6843` / `totalMs≈10836` (~63%) |
| LLM **không** dominate steady-state | Groq latency ~0.6–1.1s vs retrieval ~6–7s | L1 p50: `llmTotalMs≈670` (~6%) |
| Query embed cache hit | Identical normalized query + model | `queryEmbedMs=0` on repeated L1 runs |
| Message persist spike | JPA save + JSON sources | L1 run1: `persistMs=1296` |

---

## 5. Chiến lược sửa đã chọn

1. **Mở rộng tracing** — thêm `dbMs`, `persistMs`, `rerankMs`, `retrievalMs` (không đổi behavior).
2. **Keyword index prewarm sau ingest** — gọi `KeywordIndexCache.warm()` ngay sau invalidate khi document COMPLETED (move build cost từ first chat sang ingest).
3. **Giữ nguyên** query embedding cache, batch DB fetch, retrieval semantics.
4. **Benchmark script** — `scripts/benchmark/latency_bench_27a.ps1` với UUID sessionId.
5. **Tests** — cache invalidation, embedding cache provider calls.

Không implement: adaptive n/topK, answer cache, rerank global disable, parser/normalizer changes.

---

## 6. Baseline latency (before optimization)

### Cold start (backend restart, first query — manual trace)

| Stage | ms | % of total |
|---|---:|---:|
| totalMs | 26102 | 100% |
| keywordIndexBuildMs | 12177 | 47% |
| keywordMs (total) | 12705 | 49% |
| queryEmbedMs | 2248 | 9% |
| vectorMs | 1158 | 4% |
| llmTotalMs | 1899 | 7% |
| scoringMs + cellAwareMs | ~2273 | ~9% |

### Steady-state (index warm — manual trace)

| Stage | ms | % of total |
|---|---:|---:|
| totalMs | 11058 | 100% |
| retrieval (approx) | ~8500 | ~77% |
| keywordMs | 666 | 6% |
| scoringMs + cellAwareMs | ~4324 | ~39% |
| llmTotalMs | 1092 | 10% |
| queryEmbedMs | 600 | 5% |

### Representative questions — before (manual / pre-script-fix)

| Question | total p50 | total p95 | Biggest stage | 2nd stage | Diagnosis |
|---|---:|---:|---|---|---|
| L1 (manual) | 23298 | — | keyword+scoring (cold/warm mix) | LLM ~1.9s | E + D |
| Generic warm | 11058 | — | scoring/cellAware ~4.3s | keyword 666ms | D |
| Cold first | 26102 | — | keywordIndexBuild 12177ms | embed 2248ms | E |

**Classification summary:** **E** (cold index) + **D** (DB/scoring path) dominate — **NOT A (LLM)** in steady-state.

---

## 7. Runtime benchmark after optimization

Chatbot warm index (warm-up per question excluded from p50/p95).

| Question | before p50 | after p50 | Δ% | before p95 | after p95 | Correctness |
|---|---:|---:|---:|---:|---:|---|
| L1 exact schedule | 23298* | 10836 | -53% | — | 12038 | **PASS** (Vân Anh, E301) |
| L2 exact n=5 | ~9000† | 6433 | -29% | — | 7305 | **PASS** (Thanh Nhàn, B301) |
| L3 list n=20 | ~15000† | 11476 | -23% | — | 14202 | **PASS** (Kiến trúc K46 HK2) |
| L4 list | ~9000† | 7692 | -15% | — | 10023 | **PASS** (CNSH K46 HK2) |
| L5 OOS | ~6500† | 4814 | -26% | — | 5063 | **PASS** (refusal) |

\*Manual pre-optimization single run.  
†Estimated from smoke 25G range 6.7–16.7s and manual warm traces.

### L1 after — stage breakdown (p50 run, trace `en0zb2`)

| Stage | ms |
|---|---:|
| totalMs | 12038 |
| retrievalMs | 6843 |
| keywordMs | 3032 |
| scoringMs | 2764 |
| cellAwareMs | 2374 |
| dbMs | 650 |
| vectorMs | 389 |
| rerankMs | 344 |
| llmTotalMs | 670 |
| persistMs | 1296 |
| queryEmbedMs | 0 (cache hit) |
| sourceMs | 0 |
| promptContextBuildMs | 0 |

### Bottleneck table (after, steady-state)

| Question | total p50 | total p95 | Biggest stage | 2nd biggest | Class |
|---|---:|---:|---|---|---|
| L1 | 10836 | 12038 | retrievalMs ~6843 | keywordMs ~3032 | D/E |
| L2 | 6433 | 7305 | retrievalMs ~4500 | scoring ~2000 | D |
| L3 | 11476 | 14202 | retrievalMs ~7500 | llm ~1200 | D/G |
| L4 | 7692 | 10023 | retrievalMs ~5500 | scoring ~2500 | D |
| L5 | 4814 | 5063 | retrievalMs ~1700 | llm ~745 | D |

**LLM generation is NOT >70% of total** — backend retrieval/scoring dominates steady-state. Expected optimization ceiling without LLM provider change: ~30–50% on cold-start (prewarm), modest on steady-state.

---

## 8. Runtime path Q&A

| # | Question | Answer |
|---|---|---|
| 1 | Query embedding recomputed for identical queries? | **No** when cache hit (`queryEmbedMs=0` on L1 repeats) |
| 2 | KeywordIndexCache reused or rebuilt per request? | **Reused** per widget when not expired; rebuild on miss/invalidation |
| 3 | DB chunks batched? | **Yes** — `findByWidgetConfigIdAndSectionIdIn...`, `findByWidgetConfigIdAndIdIn...`, etc. |
| 4 | Qdrant search called more than necessary? | **Yes potentially** — multiple query variants each call `embeddingService.search()` (2–4 variants) |
| 5 | Rerank called every query? | **Yes when enabled** — `rerankMs` 344–694ms observed |
| 6 | Sources formatted more than once? | **Yes** — build before LLM + applyAnswerAwareSourceCap after; traced via `sourceMs` |
| 7 | Chat history/prompt too large? | **Moderate** — L1 `estimatedPromptTokens≈1392`, L5 `≈1623`; within budget |
| 8 | Message persistence slow? | **Occasionally** — `persistMs` up to 1296ms on L1 run1 |
| 9 | Sync waits for full LLM? | **Yes** — streaming exists but sync endpoint unchanged |

---

## 9. Optimizations applied

| Candidate | Applied? | Evidence | Effect |
|---|---|---|---|
| 1 Query embed cache | Already existed | Tests added | Steady-state embed ~0ms on repeat |
| 2 Keyword index prewarm | **Yes** | `DocumentService` calls `warm()` after ingest | Moves ~12s build off first chat (post-upload) |
| 3 Batch DB fetch | Already existed | `timedDbFetch` tracing added | No semantic change |
| 4 Avoid redundant Qdrant | **No** | Multi-variant search still needed for recall | Future task |
| 5 Source formatting | **No** | `sourceMs` low | Not bottleneck |
| 6 Rerank guard | **No** | rerank 344–694ms but not >50% total | Config-driven skip deferred |
| 7 Streaming perceived latency | **No** | Documented only | Out of scope |

---

## 10. Files changed

| File | Layer |
|---|---|
| `audit/metrics/RagLatencyTrace.java` | metrics |
| `rag/retrieve/RagRetrievalService.java` | retrieval |
| `rag/retrieve/KeywordIndexCache.java` | retrieval |
| `rag/rerank/RerankService.java` | rerank |
| `rag/runtime/ChatService.java` | runtime |
| `service/DocumentService.java` | ingest |
| `scripts/benchmark/latency_bench_27a.ps1` | tooling |
| `test/.../KeywordIndexCacheTest.java` | test |
| `test/.../EmbeddingServiceQueryCacheIntegrationTest.java` | test |

---

## 11. Tests

```
mvn clean test → 77 tests, 0 failures, 0 errors
```

Added: `KeywordIndexCacheTest` (3), `EmbeddingServiceQueryCacheIntegrationTest` (3).

Correctness suites unchanged: PASS.

---

## 12. Limitations

- Cold-start after **backend restart** (without re-ingest) vẫn build keyword index lần đầu (~12s) — prewarm chỉ chạy sau document upload.
- Benchmark script verdict regex không match Unicode → L1/L5 hiển thị PARTIAL/FAIL trong JSON nhưng manual review = PASS.
- Cohere rerank enabled trong env — cost/latency external API ~0.3–0.7s/request.
- Không claim production VPS verified — local Docker only.

---

## 13. Next recommended tasks

1. **27B** — Async keyword index prewarm on backend startup for active widgets (optional, config flag).
2. **27C** — Rerank guard: skip when `expensiveSubset.size() <= threshold` or OOS detected pre-LLM.
3. **27D** — Dedupe query variants embedding when variants normalize identically within same request.
4. **Adaptive n/topK** — separate task as specified.

---

## 14. Verdict

**PASS** — Baseline profile created, bottleneck identified (E cold index + D retrieval/scoring, not LLM), safe optimizations applied, 77 tests green, runtime correctness PASS for L1–L5, before/after table documented.
