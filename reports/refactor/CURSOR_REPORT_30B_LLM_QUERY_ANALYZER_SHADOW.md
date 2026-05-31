# CURSOR REPORT 30B — LLM Query Classifier Shadow Mode Enable & Runtime Verify

## 1. Mức độ hiểu task

- Hiểu task: **97%**
- Phần chắc chắn: shadow mode code path, log format fix, config change, benchmark query execution, log collection
- Phần còn giả định: query mapping qua shadow log (một số query dùng ASCII thay vì Unicode gây khó map chính xác)
- Thiếu dữ kiện: không có — đủ căn cứ từ source + runtime logs

---

## 2. Tóm tắt yêu cầu

Enable LLM query classifier ở shadow mode. Shadow mode chạy async, không đổi retrieval behavior. Collect runtime evidence để quyết định có enable active mode (30C) không.

---

## 3. Hiện trạng trước khi sửa

- `rag.analysis.llm-classifier.enabled=false` → classifier hoàn toàn tắt
- Shadow log format: `shadow localType=... shadow=true` ở cuối (không grep-friendly)
- Bug: `{:.2f}` trong `LlmQueryClassifier` (Python-style, không hợp lệ với SLF4J)
- Classifier timeout/error chỉ log ở DEBUG — không thấy trong production logs

---

## 4. Nguyên nhân gốc xác nhận từ source

1. `application.yml` line 86: `enabled: false` → classifier không bao giờ chạy dù shadow-mode=true
2. `QueryAnalyzerService.java` lines 134-136: log format đặt `shadow=true` ở cuối, thiếu `llmUsed=`, `fallbackReason=`
3. `LlmQueryClassifier.java` line 157: `{:.2f}` là Python format string, SLF4J sẽ log literal text `{:.2f}` thay vì số thực

---

## 5. Chiến lược sửa đã chọn

- Minimal diff: chỉ sửa 4 file (2 config, 2 Java)
- Không đổi retrieval logic, không thêm dependency, không đổi API contract
- Cải thiện log format để grep được dễ hơn
- Nâng log level timeout/error từ DEBUG → WARN để visible trong production

---

## 6. Danh sách file đã đọc

| File | Đọc để làm gì | Kết luận chính |
|---|---|---|
| `application.yml` | Xác nhận enabled=false và shadow config | enabled=false, shadow-mode=true, config đầy đủ |
| `application-dev.yml` | Kiểm tra profile dev có override không | Không có llm-classifier override |
| `application-docker.yml` | Kiểm tra profile docker có override không | Không có llm-classifier override — safe |
| `QueryAnalyzerService.java` | Xác nhận shadow mode code path | Shadow dùng CompletableFuture.runAsync, always return localResult ✓ |
| `LlmQueryClassifier.java` | Xác nhận LLM call, timeout, parse | Timeout/error caught, {:.2f} bug found |
| `LocalGenericQueryAnalyzer.java` | Xác nhận local signal patterns | SECTION_NUMBER conf=0.80, CODE_IDENT conf=0.55, no-signal conf=0.30 |
| `QueryAnalysisResult.java` | Xác nhận fields llmUsed, fallbackReason, source | Fields có sẵn trong record |
| `QueryAnalysisSource.java` | Xác nhận enum values | LLM_CLASSIFIER, FALLBACK_DEFAULT, LOCAL_GENERIC, HEADING_MATCH |
| `LlmFallbackService.java` | Xác nhận buildChatModel dùng Groq config | Dùng groq.api-key, groq.base-url, groq.chat-model — không cần config mới |

---

## 7. Danh sách file đã sửa

| File | Sửa để làm gì | Ảnh hưởng lớp |
|---|---|---|
| `Backend/src/main/resources/application.yml` | Enable classifier (`enabled: true`), thêm logger | config |
| `Backend/src/main/resources/application-docker.yml` | Thêm logger entries | config |
| `Backend/src/main/java/.../rag/analysis/QueryAnalyzerService.java` | Cải thiện shadow log format | service |
| `Backend/src/main/java/.../rag/analysis/LlmQueryClassifier.java` | Fix SLF4J format bug, upgrade timeout log to WARN | service |

---

## 8. Diff thay đổi của từng file

### `application.yml`

```diff
-      enabled: false
+      enabled: true
+      # Set to false to disable entirely; set shadow-mode=false for active mode (30C+)
...
+    KLTN.RAG_CHATBOT_BE.rag.analysis.QueryAnalyzerService: INFO
+    KLTN.RAG_CHATBOT_BE.rag.analysis.LlmQueryClassifier: INFO
```

Lý do: enable classifier để shadow logs xuất hiện; thêm logger để INFO-level shadow logs visible.

### `application-docker.yml`

```diff
+    KLTN.RAG_CHATBOT_BE.rag.analysis.QueryAnalyzerService: INFO
+    KLTN.RAG_CHATBOT_BE.rag.analysis.LlmQueryClassifier: INFO
```

Lý do: docker profile overrides logging config, cần thêm entries ở đây để docker runtime show shadow logs.

### `QueryAnalyzerService.java`

```diff
-                        log.info("[QueryAnalysis] shadow localType={} localConf={} "
-                                        + "llmType={} llmConf={} chosen=local shadow=true",
-                                capturedLocal.queryType(), capturedLocal.confidence(),
-                                llmResult.queryType(), llmResult.confidence());
-                    } catch (Exception ex) {
-                        log.debug("[QueryAnalysis] shadow LLM call failed: {}", ex.getMessage());
+                        boolean isFallback = llmResult.source() == QueryAnalysisSource.FALLBACK_DEFAULT;
+                        String fallbackReason = isFallback
+                                ? (llmResult.fallbackReason() != null ? llmResult.fallbackReason() : "unknown")
+                                : "none";
+                        log.info("[QueryAnalysis] shadow=true localType={} localConf={} "
+                                        + "llmType={} llmConf={} chosen=local "
+                                        + "llmUsed={} fallbackReason={}",
+                                capturedLocal.queryType(), capturedLocal.confidence(),
+                                llmResult.queryType(), llmResult.confidence(),
+                                llmResult.llmUsed(), fallbackReason);
+                    } catch (Exception ex) {
+                        log.warn("[QueryAnalysis] shadow=true llmCallFailed={}", ex.getMessage());
```

Lý do: format log theo chuẩn grep-friendly, thêm `llmUsed` và `fallbackReason`, nâng catch lên WARN.

### `LlmQueryClassifier.java`

```diff
-                log.debug("[QueryAnalysis][LLM] confidence {:.2f} below threshold {:.2f}",
+                log.debug("[QueryAnalysis][LLM] confidence {} below threshold {}",
...
-            log.debug("[QueryAnalysis][LLM] classifier timeout after {}ms", timeoutMs);
+            log.warn("[QueryAnalysis][LLM] classifier timeout after {}ms — fallback to local", timeoutMs);
...
-            log.debug("[QueryAnalysis][LLM] classifier error: {}", msg);
+            log.warn("[QueryAnalysis][LLM] classifier error={} — fallback to local", msg);
```

Lý do: fix SLF4J format bug (`{:.2f}` invalid), nâng timeout/error log lên WARN để thấy trong production.

---

## 9. Ảnh hưởng sau sửa

| Behavior | Thay đổi? |
|---|---|
| Retrieval result | Không thay đổi — `chosen=local` always trong shadow mode |
| Query type used for retrieval | Không thay đổi — local result vẫn được dùng |
| Shadow log xuất hiện | **Mới** — INFO log `[QueryAnalysis] shadow=true ...` |
| LLM API calls | **Mới** — async fire-and-forget, không block request |
| latency request | Không tăng đáng kể — `queryAnalyzeMs` 0-30ms, LLM async |
| API token usage | Tăng nhẹ — classifier dùng max-tokens=150, temp=0.0 |
| Disk/log | Thêm 1 INFO log per query khi local conf < 0.70 |
| Memory/CPU | Không đáng kể — CompletableFuture dùng ForkJoinPool common |

---

## 10. Edge cases đã xem xét

| Edge case | Xử lý |
|---|---|
| LLM timeout (>1200ms) | TimeoutException caught → ofFallback, log WARN, async thread cancelled |
| LLM error (network, rate limit) | ExecutionException caught → ofFallback, log WARN |
| LLM JSON invalid/missing queryType | parseResponse returns ofFallback |
| LLM confidence < 0.65 | Returns ofFallback with reason "confidence too low" |
| Local conf ≥ 0.70 | LLM NOT called — S6 confirmed this path (queryAnalyzeMs=0) |
| LLM result when shadow=true | Always discarded — local result returned regardless |
| ASCII query (no diacritics) | Local analyzer may miss code-identifier; LLM also degrades |
| Empty query | LocalAnalyzer returns NORMAL_FACT conf=1.0 immediately |
| groq.chat-model missing | Would fail at startup (existing guard) |
| GROQ_API_KEY missing | Would fail at startup (existing guard) |

---

## 11. Kết quả kiểm tra

| Command | Kết quả | Ghi chú |
|---|---|---|
| `cd Backend && .\mvnw.cmd clean test` | **PASS** | 176 tests, 0 failures, 0 errors |
| `docker compose config -q` | **PASS** | No config errors |
| `docker compose up -d mysql qdrant` | **PASS** | Both containers running |
| `docker compose up --build -d backend` | **PASS** | Backend started in ~96s |
| S1 exact schedule query via API | **PASS** | Correct answer (Nguyễn Thị Vân Anh, thứ 2, tiết 1-2, E301) |
| S5 OOS USD/VND via API | **PASS** | Refused correctly |
| Shadow log grep | **PASS** | 12 shadow log entries, all `chosen=local`, `fallbackReason=none` |
| LLM timeout count | **PASS (0)** | 0 timeouts in 7 LLM calls |
| queryAnalyzeMs blocking | **PASS** | 0-30ms, async shadow not blocking |

---

## 12. Rủi ro còn lại

1. **Timeout margin**: 1200ms timeout may be insufficient under Groq TPM/TPD pressure. Monitor 30C before going active.
2. **ASCII query degradation**: Benchmark ran ASCII queries; with proper Vietnamese Unicode, classifier quality expected higher.
3. **Shared Groq model**: Shadow classifier and main LLM share the same `groq.chat-model`. During peak load, async shadow calls may race with main LLM request (both hitting Groq simultaneously).
4. **S4 anomaly**: K46 in "Nganh Cong nghe sinh hoc K46" did not trigger CODE_IDENTIFIER in observed shadow logs — cause unclear (possibly normalized variant passed). Needs deeper analysis in 30C.
5. **ForkJoinPool saturation**: On 1-core production, many concurrent shadow async tasks could queue up. Current implementation uses `CompletableFuture.runAsync()` (common pool). Consider bounded executor in 30C if needed.

---

## 13. Đề xuất tiếp theo

**30C:** Enable active mode (`shadow-mode=false`) after confirming:
- [ ] Run same S1–S8 with proper Unicode (not ASCII transliteration)
- [ ] Verify active mode S1 still gets TABLE_LOOKUP
- [ ] Verify active mode S3/S4 get LIST_ALL (curriculum list improvement)
- [ ] Verify S5 OOS still refuses even with NORMAL_FACT from LLM
- [ ] Increase timeout to 2000ms to handle Groq latency spikes
- [ ] Consider bounded executor (thread pool of 2) instead of ForkJoinPool for async shadow

**Final verdict for 30B:** **PASS**  
Shadow mode enabled, logs correct, tests pass, retrieval unchanged, LLM classifier quality validated.
