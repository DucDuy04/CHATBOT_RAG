# CURSOR REPORT 30D - Deduplicate QueryAnalyzer Analyze Calls

## Final verdict: **PASS**

---

## 1. Muc do hieu task

- Hieu task: **98%**
- Chac chan: duplicate analysis xuat phat tu runtime + retrieval goi classifier doc lap cho cung request.
- Gia dinh: khong doi semantics vi chi doi wiring, van dung `analyzeDetailed` o retrieval STEP 0.
- Thieu du kien: runtime S1-S5 benchmark chua chay trong session tiep tuc nay.

## 2. Tom tat yeu cau

Mot chat/retrieval request chi goi `QueryAnalyzerService.analyzeDetailed(question, widgetId)` mot lan, reuse `QueryAnalysisResult` xuong pipeline. Khong disable active LLM classifier, khong them hardcoded lexicon, khong doi retrieval semantics.

## 3. Hien trang truoc khi sua

| File | Method | Current call | Purpose | Proposed replacement | Risk | Classification |
|---|---|---|---|---|---|---|
| `RagRetrievalService.java` | `retrieveWithMetadata` | `analyze(question, widgetId)` | Query type for retrieval | `analyzeDetailed` once + store in result | Low | KEEP_SINGLE_ENTRY_ANALYSIS |
| `ChatService.java` | `chat` | `analyze(question, widgetId)` | Prompt hint + max tokens | Reuse `retrievalResult.analysis()` | Low | REPLACE_WITH_PASSED_RESULT |
| `ChatService.java` | `chatStream` | `analyze(question, widgetId)` | Stream hint + max tokens | Reuse `streamResult.analysis()` | Low | REPLACE_WITH_PASSED_RESULT |
| `PlaygroundService.java` | `runCompareOnce` | `analyze(question, chatbotId)` | Compare prompt hint | Reuse `retrievalResult.analysis()` | Low | REPLACE_WITH_PASSED_RESULT |
| `QueryAnalyzerService.java` | `analyze(...)` | wrapper | Public compatibility | Keep | Low | KEEP_SINGLE_ENTRY_ANALYSIS |

## 4. Nguyen nhan goc xac nhan tu source

`ChatService.chat()` / `chatStream()` va `PlaygroundService.runCompareOnce()` goi `queryAnalyzerService.analyze(...)` truoc khi goi `ragRetrievalService.retrieveWithMetadata(...)`. Trong khi do `RagRetrievalService.retrieveWithMetadata(...)` STEP 0 cung goi `analyze(...)` (wrapper cua `analyzeDetailed`). Moi request chat chinh bi classify 2 lan, co the keo theo 2 LLM classifier calls khi active mode bat.

## 5. Chien luoc sua da chon

- Giu **mot diem phan tich duy nhat** o dau retrieval: `RagRetrievalService.retrieveWithMetadata`.
- Doi STEP 0 tu `analyze(...)` sang `analyzeDetailed(...)`.
- Mo rong `RetrievalResult` them field `QueryAnalysisResult analysis`; giu constructor 2-arg backward compatible.
- `ChatService` / `PlaygroundService` bo goi analyzer truc tiep; lay `QueryType` tu `retrievalResult.analysis()`.
- Them trace/log: `queryAnalyzeCallCount`, type/source/confidence/fallbackReason.
- Them unit tests mock de khoa regression.

## 6. Danh sach file da doc

| Path | Muc dich | Ket luan chinh |
|---|---|---|
| `.cursor/rules/00-core-working-rule.mdc` | Rule nen | Minimal diff, doc source truoc |
| `.cursor/rules/90-report-verification-rule.mdc` | Rule report | Bat buoc report day du |
| `RagRetrievalService.java` | Retrieval flow | STEP 0 la diem phan tich hop ly |
| `ChatService.java` | Chat sync/SSE | Duplicate analyze o runtime |
| `PlaygroundService.java` | Compare flow | Duplicate analyze con sot sau partial patch |
| `RagLatencyTrace.java` | Latency audit | Can them analysis metadata |
| `QueryAnalysisResult.java` | Result DTO | Du field de reuse |
| `ChatServiceAsyncPersistTest.java` | Test pattern | `RetrievalResult(List,String)` van hop le |
| Partial diff git | Trang thai hien tai | ChatService/RagRetrieval da patch mot phan |

## 7. Danh sach file da sua

| Path | Sua de lam gi | Anh huong |
|---|---|---|
| `Backend/.../RagRetrievalService.java` | Single `analyzeDetailed`, attach analysis to result | service/retrieve |
| `Backend/.../ChatService.java` | Remove duplicate analyze, add helper | service/runtime |
| `Backend/.../PlaygroundService.java` | Remove duplicate analyze | service/runtime |
| `Backend/.../RagLatencyTrace.java` | Analysis trace fields | audit/metrics |
| `Backend/.../RagRetrievalServiceQueryAnalysisDedupTest.java` | Dedup test retrieval | test |
| `Backend/.../ChatServiceQueryAnalysisDedupTest.java` | Dedup test chat | test |
| `docs/eval/results/QUERY_ANALYZER_DEDUP_ANALYZE_CALL_30D_20260530.md` | Eval report | docs |
| `reports/refactor/CURSOR_REPORT_30D_DEDUP_ANALYZE_CALL.md` | Refactor report | docs |

## 8. Diff thay doi cua tung file

### RagRetrievalService.java

- Hien trang cu: STEP 0 goi `analyze()`, `RetrievalResult` chi co contexts + lockedScopeLabel.
- Da sua: STEP 0 goi `analyzeDetailed()`, luu `analysis` vao result, log `[RAG][analysis] calls=1`.
- Vi sao: retrieval la entry point chung cho chat/playground; tranh double classify.

```diff
+import QueryAnalysisResult;
-public record RetrievalResult(List<RetrievedContext> contexts, String lockedScopeLabel) {}
+public record RetrievalResult(..., QueryAnalysisResult analysis) {
+    public RetrievalResult(List<RetrievedContext> contexts, String lockedScopeLabel) {
+        this(contexts, lockedScopeLabel, null);
+    }
+}
-QueryType queryType = queryAnalyzerService.analyze(question, widgetId);
+QueryAnalysisResult analysis = queryAnalyzerService.analyzeDetailed(question, widgetId);
+QueryType queryType = analysis.queryType();
+trace.recordQueryAnalysis(...);
-return new RetrievalResult(List.of(), null);
+return new RetrievalResult(List.of(), null, analysis);
```

### ChatService.java

- Hien trang cu: `analyze()` truoc retrieval trong `chat` va `chatStream`.
- Da sua: lay query type tu `retrievalResult.analysis()` sau retrieval.
- Anh huong: giam 1 classifier call/request; behavior giu nguyen neu analysis cung type.

```diff
-QueryType queryType = queryAnalyzerService.analyze(question, widgetId);
 RagRetrievalService.RetrievalResult retrievalResult = ragRetrievalService.retrieveWithMetadata(...);
+QueryType queryType = queryTypeFromRetrievalResult(retrievalResult);
+static QueryType queryTypeFromRetrievalResult(RetrievalResult result) { ... }
```

### PlaygroundService.java

- Hien trang cu: compare path van duplicate analyze (partial patch chua sua file nay).
- Da sua: reuse `ChatService.queryTypeFromRetrievalResult(retrievalResult)`.

```diff
-QueryType queryType = queryAnalyzerService.analyze(question, chatbotId);
 RagRetrievalService.RetrievalResult retrievalResult = ragRetrievalService.retrieveWithMetadata(...);
+QueryType queryType = ChatService.queryTypeFromRetrievalResult(retrievalResult);
```

### RagLatencyTrace.java

- Hien trang cu: chi co `queryAnalyzeMs`.
- Da sua: them call count + type/source/confidence/fallbackReason trong latency log.

```diff
+private int queryAnalyzeCallCount;
+public void recordQueryAnalysis(String queryType, String source, double confidence, String fallbackReason)
+log.info("[RAG][latency] ... queryAnalyzeCallCount={} queryAnalysisType={} ...")
```

### Tests (new)

- `RagRetrievalServiceQueryAnalysisDedupTest`: verify `analyzeDetailed` x1, `analyze` x0.
- `ChatServiceQueryAnalysisDedupTest`: verify chat khong goi analyzer truc tiep.

## 9. Anh huong sau sua

**Thay doi:**
- Main chat/sync/SSE/playground-compare: **1** analysis call per request (inside retrieval).
- `[RAG][analysis]` va `[RAG][latency]` co metadata analysis call count.

**Giu nguyen:**
- Query classification semantics (`analyzeDetailed` logic, active LLM classifier, fallback).
- Retrieval expansion/topK/rerank/prompt/OOS behavior.
- Public `QueryAnalyzerService.analyze(...)` API.
- DB/Qdrant/parser/normalizer/cells_json.

**Latency/cost:**
- Expected lower `queryAnalyzeMs` and fewer Groq classifier tokens on low-confidence queries (up to ~50% reduction on analysis step).

## 10. Edge cases da xem xet

- Empty retrieval van tra `analysis` trong result.
- Mock/test dung constructor 2-arg van compile.
- `analysis == null` fallback `NORMAL_FACT`.
- Compare A/B: 2 requests rieng → 2 analysis (dung).

## 11. Ket qua kiem tra

| Command | Ket qua | Ghi chu |
|---|---|---|
| `cd Backend && .\mvnw.cmd -DskipTests compile` | **PASS** | BUILD SUCCESS |
| `cd Backend && .\mvnw.cmd clean test` | **PASS** | 186 tests, 0 failures |
| `rg queryAnalyzerService.analyze*` production java | **PASS** | Chi `RagRetrievalService.analyzeDetailed` |
| Hardcode scan `rag/analysis` | **PASS** | No banned lexicon |
| Runtime S1-S5 | **NOT RUN** | Can live backend |
| Frontend lint/build/widget | **NOT RUN** | No FE changes |
| `docker compose config -q` | **PASS** | exit 0 |

## 12. Rui ro con lai

- `ChatService` van inject `QueryAnalyzerService` nhung khong dung trong production path (minor dead dependency).
- Runtime benchmark S1-S5 chua verify live latency improvement trong session nay.

## 13. De xuat tiep theo

1. Chay runtime Unicode S1-S5, xac nhan log `queryAnalyzeCallCount=1`.
2. API smoke S1 + S5 tren widget rank-1.
3. Optional hygiene: remove unused `QueryAnalyzerService` field tu `ChatService` neu khong can.
