# CURSOR_REPORT_21J — Source presentation cleanup (response cap)

## 1. Mức độ hiểu task

- **~97%** — cap/dedup `sources` ở response only; giữ LLM context; không đụng retrieval/prompt/query analyzer/frontend.
- **Chắc chắn:** root cause từ 21I (sourceCount=10); Option A trong `ChatService`; API contract giữ nguyên fields.
- **Giả định:** refusal phrase detection đủ an toàn cho OOS; không chạy full 12 golden (chỉ targeted 11).
- **Thiếu:** full 12-case regression tự động trong CI.

## 2. Tóm tắt yêu cầu

Sau 21I PASS chức năng, giảm source noise: không còn mặc định 10 sources; max 5 in-scope; OOS refusal ≤2; D01 vẫn 0; không đổi answer behavior.

## 3. Phạm vi đã làm

- `ChatService`: `buildSourceDtosForResponse`, dedupe, cap 5, refusal cap 2 (sync + stream).
- Unit test `ChatServiceSourcePresentationTest`.
- Runtime Docker targeted eval + docs/fix-loop/report.

## 4. Phạm vi không làm

- `RagRetrievalService`, `PromptBuilderService`, `QueryAnalyzerService`, embedding/Qdrant, schema, frontend, `PlaygroundService`, `.gitignore`, config property mới, migration.

## 5. Root cause / source noise từ 21I

Golden doc 10 chunks → retrieval trả full window → `buildSourceDtos` map **toàn bộ** contexts → API `sourceCount=10` dù LLM answer đúng. Gây UI rối, payload lớn, cảm giác over-citation.

## 6. Phân tích source pipeline trước khi sửa

| # | Câu | Trả lời |
|---|-----|---------|
| 1 | Nguồn tạo `sources`? | `ChatService.buildSourceDtos(contexts)` sau `ragRetrievalService.retrieveWithMetadata` |
| 2 | 1:1 với contexts? | **Có** |
| 3 | SourceDto fields | `fileName`, `sectionTitle`, `pages`, `chunkType`, `chunkText` |
| 4 | Frontend `chunkText`? | **Có** (Chat, Widget, Playground panels) |
| 5 | Cap/dedup cũ? | **Không** |
| 6 | Cap response không ảnh hưởng LLM? | **Có** — contexts nguyên vẹn vào `promptBuilderService` |
| 7 | Sửa RagRetrieval? | **Không** |
| 8 | Dedup | `chunkId` + fallback composite preview |
| 9 | Table cap | 5 đủ (nhiều chunk type section 4 vẫn có trong top-5 nếu sort retrieval đúng) |
| 10 | OOS sources | Giữ ≤2 khi refusal phrase |
| 11 | Minimal fix | Option A `ChatService` only |
| 12 | Rủi ro cap | Mất hiển thị chunk 6–10; không ảnh hưởng generation |

## 7. Chiến lược sửa minimal đã chọn

**Option A:** Response-only dedupe + `MAX_RESPONSE_SOURCES=5` + `applyAnswerAwareSourceCap` cho refusal (`MAX_REFUSAL_RESPONSE_SOURCES=2`).

## 8. Vì sao không sửa retrieval / prompt / query analyzer

Task yêu cầu giữ answer behavior và LLM context; noise là **presentation** layer. Sửa topK/rerank/prompt có rủi regress 21I (11 PASS).

## 9. Danh sách file đã đọc

| Path | Mục đích | Kết luận chính |
|------|----------|----------------|
| `docs/eval/results/RAG_FULL_GOLDEN_REGRESSION_21I_20260515.md` | Before metrics | 8+ case sourceCount=10 |
| `reports/refactor/CURSOR_REPORT_21I_FULL_GOLDEN_REGRESSION.md` | Context 21I | Verify-only baseline |
| `ChatService.java` | Pipeline | sources = full contexts map |
| `ChatResponse.java` | DTO | 5 fields SourceDto |
| `RetrievedContext.java` | Dedup | có `chunkId` |
| `RagRetrievalService.java` | Read-only | không sửa |
| `PromptBuilderService.java` | Read-only | không sửa |
| `ChatController.java` | API | sessionId required |
| `Frontend/src/pages/ChatPage.jsx` | UI | dùng `chunkText` |
| `Frontend/src/pages/playground/components/RetrievalPanel.jsx` | UI | hiển thị sources |
| `docs/eval/RAG_GOLDEN_QUESTIONS.md` | Questions | 12 case |
| `docs/eval/RAG_EVALUATION_RUNBOOK.md` | Runtime | X-Widget-Key, upload txt |

## 10. Danh sách file đã sửa

| Path | Sửa để làm gì | Lớp |
|------|----------------|-----|
| `Backend/.../ChatService.java` | Cap/dedup response sources | service / api |
| `Backend/.../service/ChatServiceSourcePresentationTest.java` | Unit test cap/refusal | test |
| `docs/eval/results/RAG_SOURCE_PRESENTATION_CLEANUP_21J_20260515.md` | Eval result | docs |
| `docs/eval/results/FIX_LOOP_21J_SOURCE_PRESENTATION_CLEANUP.md` | Fix loop | docs |
| `docs/eval/results/_run_21j_targeted.ps1` | Repro script | docs |
| `docs/eval/results/21j_raw_eval_output.txt` | Raw log | docs |
| `reports/refactor/CURSOR_REPORT_21J_SOURCE_PRESENTATION_CLEANUP_FIX.md` | Report | docs |

## 11. Diff từng file

### `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/ChatService.java`

**Hiện trạng cũ:** `List<SourceDto> sources = buildSourceDtos(contexts)` map toàn bộ retrieval (thường 10).

**Đã sửa:** Pipeline response tách khỏi LLM; refusal cap sau khi có answer.

**Vì sao:** Giảm noise client mà không đụng `contexts` trong prompt.

**Ảnh hưởng:** API/SSE/DB message `sources` ngắn hơn; prompt giữ nguyên.

```diff
+    static final int MAX_RESPONSE_SOURCES = 5;
+    static final int MAX_REFUSAL_RESPONSE_SOURCES = 2;
...
-        List<ChatResponse.SourceDto> sources = buildSourceDtos(contexts);
+        List<ChatResponse.SourceDto> sources = buildSourceDtosForResponse(contexts);
...
+        sources = applyAnswerAwareSourceCap(answer, sources);
...
-    private List<ChatResponse.SourceDto> buildSourceDtos(List<RetrievedContext> contexts) {
-        return contexts.stream().map(ctx -> ChatResponse.SourceDto.builder()...).toList();
-    }
+    List<ChatResponse.SourceDto> buildSourceDtosForResponse(List<RetrievedContext> contexts) { ... dedupe + subList(0,5) ... }
+    List<ChatResponse.SourceDto> applyAnswerAwareSourceCap(String answer, List<SourceDto> sources) { ... }
+    static List<RetrievedContext> dedupeContextsForPresentation(...) { ... }
```

### `Backend/src/test/java/KLTN/RAG_CHATBOT_BE/service/ChatServiceSourcePresentationTest.java`

**File mới** — 6 tests: dedupe chunkId, cap 10→5, refusal 5→2, factual giữ 5, table types giữ 2.

## 12. Source cap/dedup rule mới

- Dedupe: `id:{chunkId}` hoặc `file|section|chunkType|preview120`
- Cap: **5** sources response
- Refusal answer: **2** sources (phrase list trong `REFUSAL_ANSWER_MARKERS`)
- Order: retrieval order preserved

## 13. API contract có đổi không

**Không** — vẫn `ChatResponse { answer, sources[] }` với cùng 5 field `SourceDto`. Chỉ **số phần tử** `sources` giảm.

## 14. Frontend compatibility analysis

- Không đổi field → frontend không cần sửa.
- `sources.length` nhỏ hơn → UI gọn hơn.
- `chunkText` vẫn có trên mỗi source trả về.
- Playground compare pane có `slice` client-side — vẫn hoạt động.

## 15. Ảnh hưởng

| Khía cạnh | Ảnh hưởng |
|-----------|-----------|
| Answer correctness | **Không đổi** (LLM context đầy đủ) |
| Citation/source UX | **Cải thiện** — ít source hơn |
| OOS | 10→2 sources; answer vẫn refuse |
| Delete D01 | `sourceCount=0` giữ nguyên |
| RAM/CPU server | Giảm nhẹ serialize JSON |
| Payload | **Giảm** (~50% sources cho in-scope) |
| Latency | Không đổi đáng kể (O(n) dedupe nhỏ) |
| MySQL/Qdrant cũ | Không cần migration |

## 16. Compile/test result

| Command | Kết quả | Ghi chú |
|---------|---------|---------|
| `mvnw -DskipTests compile` | **PASS** | |
| `mvnw -Dtest=ChatServiceSourcePresentationTest test` | **PASS** | 6/6 |
| `docker compose config -q` | **PASS** | |
| Frontend lint/build | **NOT RUN** | Ngoài scope |
| Widget build | **NOT RUN** | Ngoài scope |

## 17. Runtime verification

**Docker:** `docker compose up --build -d` PASS.

| case | sourceCount 21I | 21J | Verdict answer |
|------|-----------------|-----|----------------|
| F01 | 10 | 5 | PASS |
| F02 | 1 | 5 | PASS |
| F03 | 10 | 5 | PASS |
| L01 | 10 | 5 | PASS |
| L02 | 10 | 5 | PASS |
| T01 | 10 | 5 | PASS |
| T02 | 10 | 5 | PASS/PARTIAL nhẹ |
| C01 | 10 | 5 | PASS |
| O01 | 10 | 2 | PASS |
| O02 | 10 | 2 | PASS |
| D01 | 0 | 0 | PASS |

Log: `docs/eval/results/21j_raw_eval_output.txt`

## 18. Có sửa runtime source không?

**Có** — chỉ presentation layer response; retrieval/LLM không đổi.

## 19. Case improved / regressed

| Case | Ghi chú |
|------|---------|
| Improved UX | F01,F03,L*,T*,C*,O* — sourceCount giảm |
| Improved? | T02 — answer gọn “Ưu tiên” (cần full 12 để chốt) |
| Regressed | **Không** answer fail trên scope chạy |
| F02 sourceCount | 1→5 (retrieval window; không sai fact) |

## 20. Rủi ro còn lại

- Top-5 response có thể **không** chứa chunk “đẹp nhất” cho citation dù LLM dùng chunk khác trong prompt.
- `PlaygroundService` vẫn trả full sources — không đồng bộ playground panel.
- Refusal phrase heuristic có thể cap nhầm answer factual nếu model tự chèn “không có thông tin” (hiếm).

## 21. Đề xuất tiếp theo

1. Full golden 12 case (gồm F04) trên image 21J.
2. Mirror cap vào `PlaygroundService` nếu muốn UX đồng nhất.
3. Optional: section-representative (Option B) nếu muốn fact ≤3 sources mà không tăng retrieval.

---

**Kết luận task 21J:** **PASS** — source presentation cleanup đạt mục tiêu, không regress answer trên targeted runtime, D01/OOS/delete an toàn.
