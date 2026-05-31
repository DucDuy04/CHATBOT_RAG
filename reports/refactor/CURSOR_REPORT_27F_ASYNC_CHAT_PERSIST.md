# CURSOR REPORT 27F — Async Chat Persist Latency Optimization

**Date:** 2026-05-30  
**Task:** TASK 27F — Async Persist Chat Messages to Reduce Response Latency  
**Verdict:** PASS

---

## 1. Mức độ hiểu task

- **Hiểu task:** 98%
- **Phần chắc chắn:** Flow persist hiện tại, DTO contract, async boundary an toàn, executor pattern, trace fields
- **Phần giả định:** Actual runtime latency improvement (không có production env để benchmark)
- **Thiếu dữ kiện:** Latency benchmark thực tế P1–P5 với live backend

---

## 2. Tóm tắt yêu cầu

Giảm user-visible response latency bằng cách chuyển assistant message persistence (JPA save + JSON serialization) ra khỏi synchronous response path. User message vẫn sync. Dùng bounded executor có config flag, fallback sync khi queue đầy, không drop message.

---

## 3. Hiện trạng trước khi sửa

- `saveChatMessage()` được gọi SYNC cho cả USER và ASSISTANT message
- `persistMs` ghi nhận toàn bộ thời gian save cả 2 loại, bao gồm `toSourceMaps()` serialization
- 27A profiling cho thấy `persistMs` có thể spike đến 1296ms
- `ChatResponse` không expose `messageId` → response path không phụ thuộc ID của assistant message
- Không có async persist, không có config flag, không có executor riêng cho persist

---

## 4. Nguyên nhân gốc xác nhận từ source

Từ `ChatService.java` line 217–228 (trước sửa):
```java
saveChatMessage(session, MessageRole.ASSISTANT, answer, sources);
return ChatResponse.builder()...build();
```

`saveChatMessage()` gọi `chatMessageRepository.save()` TRƯỚC KHI return — toàn bộ thời gian JPA + JSON save cộng vào `totalMs` của user.

`ChatResponse.java` chỉ có `answer` và `sources` → không cần `messageId` → assistant save có thể async an toàn.

---

## 5. Chiến lược sửa đã chọn

**Option C — Eventual consistency for assistant message:**
- User message: SYNC (cần cho history read của same turn)
- Assistant message: ASYNC khi `enabled=true`
- Source maps serialized trong caller thread trước khi submit (thread-safe)
- Rejection handler: CallerRunsPolicy-style + warning log → sync fallback, không drop
- Config flag `enabled=false` preserve full sync behavior

---

## 6. Danh sách file đã đọc

| File | Đọc để làm gì | Kết luận |
|---|---|---|
| `rag/runtime/ChatService.java` | Xác nhận persist flow | saveChatMessage sync, assistant save sau LLM, không cần messageId |
| `audit/metrics/RagLatencyTrace.java` | Xem fields hiện có | persistMs có, thiếu persistMode |
| `domain/chat/ChatMessage.java` | Xem entity structure | sources là `List<Map<String,Object>>` JSON |
| `domain/chat/ChatMessageRepository.java` | Xem queries | `findTop10BySessionIdOrderByCreatedAtAsc` dùng cho history |
| `api/ChatController.java` | Xác nhận DTO contract | ChatResponse không có messageId |
| `dto/ChatResponse.java` | Verify response fields | Chỉ answer + sources — CONFIRMED async safe |
| `config/AppConfig.java` | Xem executor hiện có | `streamingExecutor` riêng, không dùng cho persist |
| `resources/application.yml` | Xem config structure | Thêm async-persist block vào `rag.runtime` |
| `rag/runtime/PlaygroundService.java` | Check persist path | Chỉ dùng `saveAll` cho soft-delete, không liên quan |

---

## 7. Danh sách file đã sửa

| File | Sửa để làm gì | Ảnh hưởng lớp |
|---|---|---|
| `application.yml` | Thêm async-persist config block | config |
| `rag/runtime/AsyncPersistConfig.java` (NEW) | Bounded executor bean | service |
| `rag/runtime/ChatService.java` | Refactor saveChatMessage → sync/async routing | service |
| `audit/metrics/RagLatencyTrace.java` | Thêm `persistMode` field + setter + log | service/audit |
| `ChatServiceAsyncPersistTest.java` (NEW) | 5 unit tests | test |

---

## 8. Diff thay đổi của từng file

### `application.yml`

```diff
+    async-persist:
+      enabled: true
+      pool-size: 2
+      queue-capacity: 100
+      timeout-ms: 3000
+      log-payload-size: false
     query-variant-dedupe:
```

Lý do: cần config flag để bật/tắt async, bounded pool-size=2 phù hợp production 1-core.

---

### `AsyncPersistConfig.java` (NEW)

```java
@Configuration
@ConditionalOnProperty(prefix = "rag.runtime.async-persist", name = "enabled", havingValue = "true")
public class AsyncPersistConfig {
    @Bean(name = "chatPersistExecutor")
    public Executor chatPersistExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(poolSize);   // 2
        executor.setMaxPoolSize(poolSize);    // 2
        executor.setQueueCapacity(queueCapacity); // 100
        executor.setRejectedExecutionHandler((task, pool) -> {
            log.warn("[ChatPersistAsync] queueFull fallback=sync ...");
            task.run(); // CallerRuns + log
        });
        ...
    }
}
```

Lý do: `@ConditionalOnProperty` đảm bảo bean chỉ tồn tại khi `enabled=true`. `maxPoolSize=corePoolSize=2` — bounded, không expand. Queue=100 đủ buffer cho 5 concurrent users.

---

### `ChatService.java`

```diff
+    @Autowired(required = false)
+    @Qualifier("chatPersistExecutor")
+    private Executor chatPersistExecutor;
+
+    @Value("${rag.runtime.async-persist.enabled:false}")
+    private boolean asyncPersistEnabled;
+
+    @Value("${rag.runtime.async-persist.log-payload-size:false}")
+    private boolean logPersistPayloadSize;

-    private void saveChatMessage(session, role, content, sources) {
-        // single sync path
-    }
+    private void saveChatMessage(session, role, content, sources) {
+        if (role == ASSISTANT && asyncPersistEnabled && chatPersistExecutor != null) {
+            persistAssistantMessageAsync(session, content, sources);
+        } else {
+            persistChatMessageSync(session, role, content, sources);
+        }
+    }
+
+    private void persistChatMessageSync(...) {
+        // original sync logic + trace.setPersistMode("sync")
+    }
+
+    private void persistAssistantMessageAsync(...) {
+        UUID sessionId = session.getId();
+        List<Map<String,Object>> sourceMaps = toSourceMaps(sources); // in caller thread
+        chatPersistExecutor.execute(() -> {
+            ChatSession ref = chatSessionRepository.getReferenceById(sessionId);
+            chatMessageRepository.save(message);
+            log.info("[ChatPersistAsync] status=OK ...");
+        });
+        trace.setPersistMode("async");
+        trace.addPersistMs(submissionTime); // ~0ms
+    }
```

Lý do: USER messages luôn sync (history read ngay sau). ASSISTANT messages async khi enabled. Source maps serialize trong caller thread để thread-safe. Trace `persistMs` = submission time (~0ms) thay vì actual DB write time.

---

### `RagLatencyTrace.java`

```diff
+    // Async persist fields (task 27F)
+    private String persistMode = "sync";

+    public void setPersistMode(String mode) {
+        persistMode = mode != null ? mode : "sync";
+    }

-    "queryVariantDedupeMode={} qdrantSearchCalls={}"
+    "queryVariantDedupeMode={} qdrantSearchCalls={} persistMode={}"

-    qdrantSearchCalls);
+    qdrantSearchCalls,
+    persistMode);
```

Lý do: operator cần biết `persistMode` để phân biệt async/sync trong log trace.

---

## 9. Ảnh hưởng sau sửa

**Behavior thay đổi:**
- `persistMs` trong user-visible trace giảm từ ~1296ms spike → ~0–12ms (submission only)
- `persistMode=async` xuất hiện trong latency trace khi enabled
- `[ChatPersistAsync] status=OK/FAIL` log xuất hiện trong `chat-persist-*` thread

**Behavior giữ nguyên:**
- User message vẫn sync → history read không bị ảnh hưởng
- `enabled=false` → full sync behavior, zero change
- OOS refusal, source grounding, retrieval semantics — không thay đổi
- Answer quality không đổi

**Latency/resource:**
- CPU: 2 thêm thread (chat-persist-1, chat-persist-2) — idle hầu hết thời gian
- Memory: 1 task queue tối đa 100 entries × ~4KB/entry ≈ 400KB — không đáng kể
- DB: cùng số lượng write, chỉ khác timing (vài ms delay)

**Fallback còn giữ:**
- Queue full → sync persist trong caller thread + warning log

---

## 10. Edge cases đã xem xét

| Edge case | Xử lý |
|---|---|
| `chatPersistExecutor` null (enabled=false) | Guard: `chatPersistExecutor != null` trong saveChatMessage |
| Session proxy trong async thread | `getReferenceById(sessionId)` opens own transaction, FK resolved correctly |
| DB connection fail trong async task | Exception caught, logged as FAIL, không crash request thread |
| Queue đầy | CallerRuns-style rejection handler + warning log, persist vẫn chạy sync |
| Async save lag vs follow-up message | Acceptable — human typing latency >>> async save time |
| SSE streaming path | `saveChatMessage` sau `emitter.complete()` — async submit frees SSE thread faster |
| User spam multiple messages quickly | Pool queue=100 absorbs, fallback sync khi cần |
| `enabled=false` production override | Full sync, no new threads, no change vs pre-27F |

---

## 11. Kết quả kiểm tra

| Command | Kết quả | Ghi chú |
|---|---|---|
| `cd Backend && .\mvnw.cmd clean test` | PASS | 120 tests, 0 failures, 0 errors |
| Frontend lint | NOT RUN | Không liên quan task |
| Frontend build | NOT RUN | Không liên quan task |
| Widget build | NOT RUN | Không liên quan task |
| Docker compose config | NOT RUN | Config YAML change là backward-compatible |
| Runtime benchmark P1–P5 | NOT RUN | Cần live production env |

**Unit test output (selected):**
```
[RAG][latency] trace=qjxrpq totalMs=28 persistMs=5 persistMode=async
[RAG][latency] trace=6plg2c totalMs=3 persistMs=0 persistMode=async
[ChatPersistAsync] status=FAIL trace=q56dxc ... error=Simulated DB failure
[RAG][latency] trace=enl1cy persistMs=0 persistMode=async  (sync_fallback test)
```

Test 1 confirmed: response returned in `totalMs=28ms` while async save was blocked for 600ms.

---

## 12. Rủi ro còn lại

1. **Bot client fast follow-up**: Client sending messages <300ms apart may miss previous assistant in history. Document for API consumers.
2. **Monitoring gap**: No Micrometer metrics for persist pool queue depth/failure count. Add in 27G.
3. **Production benchmark not done**: Actual latency improvement unconfirmed until run against live DB.

---

## 13. Đề xuất tiếp theo

- **27G**: Add Micrometer metrics for `chatPersistExecutor` (queue depth, task duration histogram, failure counter)
- **27H**: Runtime benchmark of P1–P5 with async-persist enabled to confirm `persistMs` improvement
- OR: Follow-up history smoke test Q1→Q2 on live widget
