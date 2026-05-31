# QUERY ANALYZER DEDUP ANALYZE CALL — Task 30D (2026-05-30)

## Final verdict: **PASS**

Main chat/sync/SSE/playground-compare paths now perform query analysis once per request inside `RagRetrievalService.retrieveWithMetadata(...)`. Runtime S1–S5 smoke was **NOT RUN** in this session (no live backend benchmark executed).

---

## 1. Mức độ hiểu task

- Hiểu task: **98%**
- Chắc chắn: duplicate calls came from runtime (`ChatService`, `PlaygroundService`) calling `analyze()` before retrieval while `RagRetrievalService` also classified the same question.
- Giả định: retrieval semantics unchanged because only call-site wiring changed; `QueryType` still derived from same `analyzeDetailed` logic.
- Thiếu dữ liệu: before/after runtime latency logs on live widget (S1–S5) not collected in this continuation session.

---

## 2. Tóm tắt yêu cầu

Ensure one chat/retrieval request calls `QueryAnalyzerService.analyzeDetailed(question, widgetId)` exactly once, reuses `QueryAnalysisResult` downstream, keeps active LLM classifier enabled, and does not reintroduce hardcoded query lexicon.

---

## 3. Hiện trạng trước khi sửa

| File | Method | Call | Classification |
|---|---|---|---|
| `RagRetrievalService` | `retrieveWithMetadata` | `queryAnalyzerService.analyze(...)` | KEEP_SINGLE_ENTRY_ANALYSIS |
| `ChatService` | `chat` | `queryAnalyzerService.analyze(...)` | REPLACE_WITH_PASSED_RESULT |
| `ChatService` | `chatStream` | `queryAnalyzerService.analyze(...)` | REPLACE_WITH_PASSED_RESULT |
| `PlaygroundService` | `runCompareOnce` | `queryAnalyzerService.analyze(...)` | REPLACE_WITH_PASSED_RESULT |

**Before (main chat path):**

```text
ChatService.chat()
  → analyze()           # call #1 (+ possible LLM classifier)
  → retrieveWithMetadata()
       → analyze()      # call #2 (+ possible duplicate LLM classifier)
```

---

## 4. Nguyên nhân gốc

Runtime orchestration layers (`ChatService`, `PlaygroundService`) classified queries for prompt/LLM policy **before** calling retrieval, while `RagRetrievalService` independently re-classified at STEP 0. Because `analyze()` is a wrapper over `analyzeDetailed()`, each path could trigger a full classifier pipeline including active LLM calls.

---

## 5. Chiến lược sửa

1. **Single entry point:** `RagRetrievalService.retrieveWithMetadata` calls `analyzeDetailed` once at STEP 0.
2. **Carry result:** extend `RetrievalResult` with `QueryAnalysisResult analysis` (backward-compatible 2-arg constructor retained).
3. **Runtime reuse:** `ChatService` / `PlaygroundService` read `queryType` from `retrievalResult.analysis()` via `ChatService.queryTypeFromRetrievalResult(...)`.
4. **Tracing:** `RagLatencyTrace.recordQueryAnalysis(...)` + `[RAG][analysis] ... calls=1` log per retrieval request.
5. **Tests:** mocked dedup tests for retrieval and chat paths.

---

## 6. Analyze call sites — after refactor

| File | Production call | Count per request |
|---|---|---|
| `RagRetrievalService.retrieveWithMetadata` | `analyzeDetailed(question, widgetId)` | **1** |
| `ChatService.chat` | none (reuses retrieval result) | **0** |
| `ChatService.chatStream` | none (reuses retrieval result) | **0** |
| `PlaygroundService.runCompareOnce` | none (reuses retrieval result) | **0** |
| `QueryAnalyzerService.analyze(...)` | compatibility wrapper only | only when called directly by tests/other code |

**After (main chat path):**

```text
ChatService.chat()
  → retrieveWithMetadata()
       → analyzeDetailed() once
       → pass QueryAnalysisResult in RetrievalResult
  → queryTypeFromRetrievalResult()
```

---

## 7. Files changed (30D scope)

| File | Layer | Change |
|---|---|---|
| `RagRetrievalService.java` | service/retrieve | Single `analyzeDetailed`; `RetrievalResult.analysis` |
| `ChatService.java` | service/runtime | Remove duplicate analyze; helper reuse |
| `PlaygroundService.java` | service/runtime | Remove duplicate analyze; reuse retrieval analysis |
| `RagLatencyTrace.java` | audit/metrics | Analysis call count + metadata fields |
| `RagRetrievalServiceQueryAnalysisDedupTest.java` | test | Verify retrieval calls analyzeDetailed once |
| `ChatServiceQueryAnalysisDedupTest.java` | test | Verify chat never calls analyzer directly |

---

## 8. Hardcode scan

```powershell
rg -i "goi|san pham|dich vu|sku|vnd|ton kho|khach hang|nhan vien|phong ban|bao nhieu goi|liet ke|danh sach|bang gia" Backend/src/main/java/KLTN/RAG_CHATBOT_BE/rag/analysis
```

**Result:** no matches — no banned domain lexicon reintroduced in `rag/analysis`.

---

## 9. Kết quả kiểm tra

| Command | Kết quả | Ghi chú |
|---|---|---|
| `cd Backend && .\mvnw.cmd -DskipTests compile` | **PASS** | BUILD SUCCESS |
| `cd Backend && .\mvnw.cmd clean test` | **PASS** | **186 tests**, 0 failures, 0 errors |
| `rg queryAnalyzerService.analyze*` main java | **PASS** | Only `RagRetrievalService` calls `analyzeDetailed` in production |
| Hardcode scan `rag/analysis` | **PASS** | No banned lexicon |
| Runtime S1–S5 benchmark | **NOT RUN** | Requires live backend + widget env |
| API/widget smoke S1/S5 | **NOT RUN** | Out of scope for this continuation run |
| Frontend lint/build | **NOT RUN** | No frontend changes |
| Docker compose config | **PASS** | `docker compose config -q` exit 0 |

---

## 10. Before/after metrics (expected)

| Metric | Before 30D | After 30D (expected) |
|---|---|---|
| `queryAnalyzeCallCount` per chat request | 2 | **1** |
| Active `[QueryAnalysis]` log pairs | 2 per request | **1 per request** |
| LLM classifier calls (low-confidence queries) | up to 2 | **up to 1** |
| Retrieval quality / query type semantics | baseline 30C | **unchanged** |

---

## 11. Edge cases considered

- Empty retrieval (`0 anchors`) still returns `RetrievalResult` with `analysis` populated.
- Legacy `RetrievalResult(contexts, label)` constructor still works for existing test mocks.
- Missing `analysis` in mocked results falls back to `NORMAL_FACT` in `queryTypeFromRetrievalResult`.
- Playground compare A/B: each `runCompareOnce` is a separate request → one analysis each (correct).

---

## 12. Rủi ro còn lại

- `ChatService` still injects `QueryAnalyzerService` but no longer uses it in production paths (dead dependency; harmless, could be cleaned in a future hygiene task).
- Runtime S1–S5 latency improvement not measured live in this session.
- Direct callers of `retrieve(...)` outside chat/playground still get single analysis inside retrieval (correct); no regression expected.

---

## 13. Đề xuất tiếp theo

1. Run Unicode S1–S5 runtime benchmark and confirm `queryAnalyzeCallCount=1` in `[RAG][latency]` logs.
2. Optional: remove unused `QueryAnalyzerService` injection from `ChatService` if no future direct use planned.
3. Task 30E: consider request-scoped cache only if a future path needs analysis outside retrieval (not needed now).
