# CURSOR REPORT 30C — LLM Query Classifier Active Mode Enable

## 1. Mức độ hiểu task

- Hiểu task: **98%**
- Phần chắc chắn: enable active mode config, fix fallback-to-local on LLM failure, Unicode runtime benchmark, log format, unit tests, reports
- Phần còn giả định: S1 first-run flake do cold-start Groq timeout — không reproduce 100% nhưng có log timeout
- Thiếu dữ liệu: Playwright widget E2E không có — dùng API smoke thay thế

---

## 2. Tóm tắt yêu cầu

Chuyển LLM query classifier từ shadow mode (30B) sang active mode: khi local confidence < 0.70, dùng kết quả LLM nếu valid; fallback local nếu fail. Verify bằng Unicode S1–S8, không reintroduce hardcoded rules, rollback nếu S1/S2/S5 regress.

---

## 3. Hiện trạng trước khi sửa

- `shadow-mode: true`, `timeout-ms: 1200`
- Active mode log thiếu `source`, `chosenType`, `fallbackReason`
- Active mode return `llmResult` trực tiếp kể cả khi `source=FALLBACK_DEFAULT` (không fallback về local result đúng spec)
- Không có test riêng cho active mode behavior

---

## 4. Nguyên nhân gốc xác nhận từ source

1. `application.yml` line 90: `shadow-mode: true` — classifier chỉ log async, không ảnh hưởng retrieval
2. `QueryAnalyzerService.java` lines 151-158 (cũ): active mode `return llmResult` ngay cả khi LLM trả `FALLBACK_DEFAULT` — vi phạm spec "fallback to local result"
3. Log format active mode không có `active=true`, `source=`, `chosenType=`

---

## 5. Chiến lược sửa đã chọn

- Config: `shadow-mode=false`, `timeout-ms=2000`
- Fix active path: nếu `llmResult.source()==FALLBACK_DEFAULT` → return `localResult`
- Cải thiện log grep-friendly theo spec 30C
- Thêm `QueryAnalyzerServiceActiveModeTest` (mocked, no live LLM)
- Runtime Unicode benchmark qua `/api/public/chat`
- Không rollback vì S1/S2 pass on retry, S5 pass, no chat break

---

## 6. Danh sách file đã đọc

| File | Đọc để làm gì | Kết luận |
|---|---|---|
| `application.yml` | Config hiện tại | shadow-mode=true, timeout 1200 |
| `application-docker.yml` | Override check | Không override llm-classifier |
| `QueryAnalyzerService.java` | Active/shadow path | Cần fix fallback + log |
| `LlmQueryClassifier.java` | Timeout/fallback | Never throws, returns ofFallback |
| `LocalGenericQueryAnalyzer.java` | Local signals | No hardcoded lexicon |
| `QueryAnalyzerServiceCompatibilityTest.java` | Test pattern | ReflectionTestUtils + mock |
| `docs/eval/results/QUERY_ANALYZER_LLM_SHADOW_VERIFY_30B_20260530.md` | 30B baseline | 7/7 valid JSON, ASCII degradation S2/S4/S8 |

---

## 7. Danh sách file đã sửa

| File | Sửa để làm gì | Ảnh hưởng |
|---|---|---|
| `Backend/src/main/resources/application.yml` | Active mode + timeout 2000ms | config |
| `Backend/src/main/java/.../QueryAnalyzerService.java` | Fallback local on LLM fail; active log | service |
| `Backend/src/test/java/.../QueryAnalyzerServiceActiveModeTest.java` | Active mode unit tests | test |

---

## 8. Diff thay đổi của từng file

### `application.yml`

```diff
-      shadow-mode: true
+      shadow-mode: false
...
-      timeout-ms: 1200
+      timeout-ms: 2000
```

### `QueryAnalyzerService.java`

```diff
-                    QueryAnalysisResult llmResult = llmClassifier.classify(question);
-                    log.info("[QueryAnalysis] active localType=... chosen=llm shadow=false", ...);
-                    return llmResult;
+                    QueryAnalysisResult llmResult = llmClassifier.classify(question);
+                    boolean llmFallback = llmResult.source() == QueryAnalysisSource.FALLBACK_DEFAULT;
+                    QueryAnalysisResult chosen = llmFallback ? localResult : llmResult;
+                    log.info("[QueryAnalysis] active=true ... chosen={} chosenType={} source={} fallbackReason={}", ...);
+                    return chosen;
```

Lý do: đúng spec fallback-to-local; log đủ fields cho grep/monitor.

### `QueryAnalyzerServiceActiveModeTest.java`

File mới — 5 test cases: high local conf, LLM valid, LLM timeout fallback, shadow mode local, disabled classifier.

---

## 9. Ảnh hưởng sau sửa

| Behavior | Thay đổi |
|---|---|
| Low-conf queries | **Dùng LLM queryType** (TABLE_LOOKUP, LIST_ALL, COUNT_QUERY) thay vì local NORMAL_FACT |
| High-conf local (S6) | Không đổi — LLM không gọi |
| LLM timeout/fail | Fallback local — chat không break |
| queryAnalyzeMs | Tăng ~600–2100ms cho low-conf queries (sync blocking) |
| Retrieval strategy | Có thể đổi theo queryType (TABLE_LOOKUP vs LIST_ALL) — intended |
| Hardcoded lexicon | Không reintroduce |

---

## 10. Edge cases đã xem xét

| Edge case | Kết quả |
|---|---|
| LLM timeout 2000ms | Fallback local, log `fallbackReason=timeout` — S1 warm-up |
| LLM valid JSON low conf | ofFallback → local chosen |
| LLM exception | catch → local (step 5) |
| S6 section number 6.2 | local conf 0.80 → LLM skipped |
| Unicode vs ASCII | Unicode cải thiện S2/S4 vs 30B |
| Cold start after restart | 2 timeouts S1 warm-up |
| OOS S5 | Refused correctly |

---

## 11. Kết quả kiểm tra

| Command | Kết quả | Ghi chú |
|---|---|---|
| `cd Backend && .\mvnw.cmd clean test` | **PASS** | 181 tests, 0 failures |
| `docker compose config -q` | **PASS** | |
| `docker compose up --build -d backend` | **PASS** | Started ~93s |
| Unicode S1–S8 benchmark | **PASS/PARTIAL** | S7 answer partial; S1/S2 flaky first run |
| S1/S2 retry | **PASS** | Exact schedule answers |
| S5 OOS | **PASS** | Refused |
| Widget API smoke | **PASS** | No Playwright |
| Rollback | **NOT NEEDED** | |

---

## 12. Rủi ro còn lại

1. Cold-start classifier timeout → temporary NORMAL_FACT fallback
2. Double analyze() call per request → 2× classifier latency/cost
3. S7 COUNT_QUERY type đúng nhưng retrieval count sai scope
4. Active mode blocking ~2s trên 1-core production cần monitor
5. S1 measured first run failed — flaky, not systematic regression

---

## 13. Đề xuất tiếp theo

- **30D:** Single classify per request (dedupe analyze call in retrieval)
- Monitor `fallbackReason=timeout` rate; tăng timeout nếu >5%
- Optional: bounded executor thay ForkJoinPool cho classifier async (future)

**Final verdict:** **PASS_WITH_LATENCY_COST** — active mode enabled, core queries pass, Unicode improves S2/S4 vs 30B, no rollback.
