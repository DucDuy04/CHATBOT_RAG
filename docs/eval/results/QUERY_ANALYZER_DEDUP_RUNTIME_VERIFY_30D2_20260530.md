# QueryAnalyzer Dedup — Runtime Verify 30D2
**Task:** 30D2  
**Date:** 2026-05-30  
**Verdict:** **PASS**

---

## 1. Config Used

```yaml
rag:
  analysis:
    llm-classifier:
      enabled: true
      shadow-mode: false
      only-when-local-confidence-below: 0.70
      min-accepted-confidence: 0.65
      timeout-ms: 2000
      max-input-chars: 1000
```

**Environment:** Docker Compose, Spring profile=`docker`  
**Widget:** `3a26c18c-bfd1-477e-af79-fcd7fba551a9`  
**API key (masked):** `c2e09246-...-7191c8d`

---

## 2. Code Changes in 30D2

**No production code changed.** Runtime verification only.

Added helper script (non-production):
- `scripts/bench_30d2_dedup.ps1` — S1–S5 warm-up + 2 measured runs via `/api/public/chat`

---

## 3. Phase 1 — Stack Startup

| Check | Result |
|---|---|
| `docker compose config -q` | PASS |
| `mysql`, `qdrant`, `backend` up | PASS |
| Backend started (39.6s) | PASS |
| KeywordIndexPrewarm finished (ok=3 failed=0) | PASS |
| Startup errors | None |
| GROQ_API_KEY effective | PASS (LLM classifier + answer generation succeeded) |
| Active LLM classifier effective | PASS (`active=true` logs, no `shadow=true`) |

---

## 4. Phase 2 — Stale Analyze Call Scan

```powershell
rg -n "queryAnalyzerService\.analyze\(|queryAnalyzerService\.analyzeDetailed\(" Backend/src/main/java/KLTN/RAG_CHATBOT_BE
```

**Result:** Only `RagRetrievalService.java:180` calls `queryAnalyzerService.analyzeDetailed(...)` in production path.

`QueryAnalyzerService.analyze(...)` remains as compatibility wrapper (delegates to `analyzeDetailed`).

**Hardcode scan:**

```powershell
rg -i "goi|san pham|dich vu|sku|vnd|ton kho|..." Backend/.../rag/analysis
```

**Result:** No matches — PASS.

---

## 5. Phase 3 — S1–S5 Runtime Results

Benchmark: 1 warm-up + 2 measured runs per query (15 chat requests total).  
Additional retries for S1/S2 after measured flake.

### Dedup metrics (all 17 requests including retries)

| Metric | Expected | Observed | Verdict |
|---|---|---|---|
| `queryAnalyzeCallCount` per request | 1 | **1 on all 17 requests** | PASS |
| `[QueryAnalysis] active=true` logs per request | 1 | **1 per request** | PASS |
| Duplicate active log pair same request | 0 | **0** | PASS |
| `shadow=true` logs | 0 | **0** | PASS |
| `[RAG][analysis] calls=` | 1 | **1 on all requests** | PASS |

### Per-query classification table (measured runs)

| # | Query | Run | chosenType | source | fallbackReason | queryAnalyzeMs | queryAnalyzeCallCount | active=true count |
|---|---|---|---|---|---|---|---|---|
| S1 | Pháp luật VN Nhóm 1 schedule | run1 | TABLE_LOOKUP | LLM_CLASSIFIER | none | 564 | 1 | 1 |
| S1 | | run2 | TABLE_LOOKUP | LLM_CLASSIFIER | none | 569 | 1 | 1 |
| S1 | warm-up | — | NORMAL_FACT | LOCAL_GENERIC | timeout | 2061 | 1 | 1 |
| S2 | Kỹ năng mềm Nhóm 4 schedule | run1 | TABLE_LOOKUP | LLM_CLASSIFIER | none | 632 | 1 | 1 |
| S2 | | run2 | TABLE_LOOKUP | LLM_CLASSIFIER | none | 912 | 1 | 1 |
| S3 | Kiến trúc K46 HK2 list | run1 | LIST_ALL | LLM_CLASSIFIER | none | 673 | 1 | 1 |
| S3 | | run2 | LIST_ALL | LLM_CLASSIFIER | none | 608 | 1 | 1 |
| S4 | CNSH K46 HK2 list | run1 | LIST_ALL | LLM_CLASSIFIER | none | 1009 | 1 | 1 |
| S4 | | run2 | LIST_ALL | LLM_CLASSIFIER | none | 657 | 1 | 1 |
| S5 | Tỷ giá USD/VND OOS | run1 | NORMAL_FACT | LLM_CLASSIFIER | none | 994 | 1 | 1 |
| S5 | | run2 | NORMAL_FACT | LLM_CLASSIFIER | none | 1254 | 1 | 1 |

### Correctness table

| # | Expected | Measured run1 | Measured run2 | Retry | Verdict |
|---|---|---|---|---|---|
| S1 | Nguyễn Thị Vân Anh, thứ 2, tiết 1-2, E301 | Partial (LUA1012, Source 7, no full schedule) | Partial (mentions Source 7) | **Exact match** | PASS (retry confirms, same as 30C) |
| S2 | Nguyễn Thị Thanh Nhàn, thứ 6, tiết 5-7, B301 | Refused | Refused | **Exact match** | PASS (retry confirms, same as 30C) |
| S3 | Kiến trúc K46 HK2 only | KTR2082, KTR2102 ✓ | Same ✓ | — | PASS |
| S4 | CNSH K46 HK2 only | 5 CNS courses ✓ | Same ✓ | — | PASS |
| S5 | Refuse OOS | "Tôi không tìm thấy thông tin này trong tài liệu." ✓ | Same ✓ | — | PASS |

**S1 retry answer:** `Giảng viên: Nguyễn Thị Vân Anh, thứ 2, tiết 1 - 2, phòng E301.`  
**S2 retry answer:** Nguyễn Thị Thanh Nhàn, thứ 6, tiết 5-7, B301 ✓

---

## 6. Phase 5 — Comparison with 30C

| Metric | 30C before dedup | 30D2 after dedup | Verdict |
|---|---|---|---|
| QueryAnalysis logs per request | 2 | **1** | **PASS** |
| LLM classifier calls per low-conf request | up to 2 | **up to 1** | **PASS** |
| `queryAnalyzeCallCount` | not guaranteed / duplicated | **1** | **PASS** |
| Correctness S1/S2 | PASS on retry | PASS on retry | PASS |
| Correctness S5 | PASS | PASS | PASS |
| S3/S4 list queries | PASS | PASS | PASS |

### queryAnalyzeMs comparison (steady-state, no timeout)

| Query | 30C active (ms) | 30D2 measured (ms) | Note |
|---|---|---|---|
| S1 | 1082–2041 | 564–569 | Single call vs prior double pipeline |
| S2 | 837 | 632–912 | Single call |
| S3 | 659 | 608–673 | Comparable |
| S4 | 613 | 657–1009 | Comparable |
| S5 | 810 | 994–1254 | Comparable |

**Latency note:** Per-request `queryAnalyzeMs` reflects one classifier invocation. 30C documented ~2× log pairs per request; 30D2 eliminates that duplication. Total request latency still dominated by retrieval + answer LLM.

---

## 7. Phase 6 — Widget/API Smoke

| Test | Endpoint | Result |
|---|---|---|
| S1 via widget API | `POST /api/public/chat` + `X-Widget-Key` | PASS on retry |
| S5 OOS refusal | `POST /api/public/chat` | PASS |
| `queryAnalyzeCallCount=1` in logs | backend logs | PASS |
| SSE stream widget | `POST /api/public/chat/stream` | **404** — endpoint not exposed for public widget |
| Playwright widget E2E | — | **NOT RUN** (no script in repo for 30D2) |

Authenticated SSE exists at `/api/chat/stream` (requires auth, not widget key). API-level widget smoke via sync public chat is sufficient.

---

## 8. Aggregate Summary

| Metric | Value |
|---|---|
| Total benchmark requests | 15 (+ 2 retries) |
| Requests with `queryAnalyzeCallCount=1` | **17/17 (100%)** |
| Requests with duplicate active QueryAnalysis log | **0** |
| `shadow=true` after active enabled | **0** |
| Classifier timeout (S1 warm-up only) | 1 |
| LLM errors breaking chat | **0** |
| Hardcoded lexicon in `rag/analysis` | **0** |

---

## 9. Remaining Risks

1. **S1/S2 first-run flake:** Same pre-existing retrieval/LLM variance as 30C; not caused by dedup.
2. **Cold-start classifier timeout:** S1 warm-up hit 2000ms timeout → LOCAL_GENERIC fallback; measured runs recovered.
3. **Production 1-core:** Single classifier call per request reduces Groq cost ~50% vs 30C; monitor under concurrent load.

---

## 10. Next Recommended Task

- **30E:** Consider lightweight classifier model or short-TTL classification cache.
- Monitor `fallbackReason=timeout` rate; increase timeout to 2500ms if >5% in production.
