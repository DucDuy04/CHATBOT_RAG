# CURSOR REPORT 27F2 — Async Chat Persist Runtime Verification

**Date:** 2026-05-30  
**Task:** TASK 27F2 — Runtime Benchmark Async Chat Persist  
**Verdict:** PASS

---

## 1. Mức độ hiểu task

- **Hiểu task:** 100%
- **Phần chắc chắn:** Runtime benchmark flow, log parsing, sync vs async comparison, acceptance criteria
- **Phần giả định:** Không
- **Thiếu dữ kiện:** Không — đã chạy live Docker benchmark đầy đủ

---

## 2. Tóm tắt yêu cầu

Verify async chat persist trong live Docker runtime: persistMode=async, persistMs giảm, ChatPersistAsync status=OK, không mất message, follow-up history smoke, so sánh sync vs async, restore config enabled sau benchmark.

---

## 3. Hiện trạng trước khi chạy

- 27F implement async persist + unit tests PASS (120 tests)
- Runtime benchmark 27F chưa chạy
- Config `async-persist` nằm sai path `rag.retrieval` thay vì `rag.runtime` → Docker runtime thực tế chạy sync mode

---

## 4. Nguyên nhân gốc xác nhận từ source

Benchmark run đầu tiên (173632) cho thấy `persistMode=sync` trên mọi run dù config YAML ghi `enabled: true`. Trace source:

- `ChatService.java`: `@Value("${rag.runtime.async-persist.enabled:false}")`
- `application.yml` (trước sửa): block nằm dưới `rag.retrieval.async-persist`
- Spring không bind → default `false` → sync behavior

---

## 5. Chiến lược đã chọn

1. Start Docker stack, rebuild backend
2. Chạy benchmark → phát hiện config bug
3. Sửa config path → rebuild → re-run async benchmark
4. Dùng run đầu (sync effective) làm sync baseline thay vì restart thêm lần nữa
5. History smoke + log audit
6. Restore async enabled (config YAML đúng path)

---

## 6. Danh sách file đã đọc

| File | Đọc để làm gì | Kết luận |
|---|---|---|
| `docker-compose.yml` | Runtime env | SPRING_PROFILES_ACTIVE=docker |
| `application.yml` | Async config path | Bug: under retrieval not runtime |
| `scripts/benchmark/latency_bench_27e.ps1` | Benchmark pattern | Reused for 27f2 script |
| `docs/eval/results/STARTUP_KEYWORD_INDEX_PREWARM_27B2` | Widget key | c2e09246-... |
| Backend logs | persistMode, ChatPersistAsync | 35 OK, 0 FAIL |

---

## 7. Danh sách file đã sửa

| File | Sửa để làm gì | Ảnh hưởng |
|---|---|---|
| `application.yml` | Move async-persist to `rag.runtime` | config |
| `scripts/benchmark/latency_bench_27f2.ps1` | NEW benchmark script | test/benchmark |
| `docs/eval/results/ASYNC_CHAT_PERSIST_RUNTIME_VERIFY_27F2_20260530.md` | NEW report | docs |
| `reports/refactor/CURSOR_REPORT_27F2_...md` | NEW report | docs |

---

## 8. Diff thay đổi

### `application.yml`

```diff
 rag:
+  runtime:
+    async-persist:
+      enabled: true
+      pool-size: 2
+      queue-capacity: 100
+      timeout-ms: 3000
+      log-payload-size: false
   retrieval:
     ...
-    async-persist:
-      enabled: true
-      ...
     query-variant-dedupe:
```

---

## 9. Ảnh hưởng sau sửa

- Async persist **thực sự bật** trong Docker runtime
- persistMs request path giảm rõ rệt (P1 p50: 592→108ms, max spike 1027→170ms)
- Behavior retrieval/prompt/answer semantics không đổi
- Async enabled restored sau benchmark

---

## 10. Edge cases đã xem xét

| Edge case | Kết quả |
|---|---|
| Config path sai | Phát hiện + sửa trong task |
| Keyword prewarm chưa xong | Đợi `finished ok=3 failed=0` |
| Queue full | 0 occurrences |
| Async DB fail | 0 occurrences |
| Follow-up <300ms | PARTIAL retrieval, not async loss |
| History 2s delay | Assistant persisted before Q2 (OK logs) |

---

## 11. Kết quả kiểm tra

| Command | Kết quả | Ghi chú |
|---|---|---|
| `docker compose config -q` | PASS | |
| `docker compose up --build -d backend` | PASS | 2 lần (initial + config fix) |
| Async benchmark P1–P5 | PASS | 174644.json, persistMode=async |
| Sync baseline P1–P5 | PASS | 173632.json, persistMode=sync (pre-fix) |
| History smoke | PARTIAL | Retrieval quality, not async |
| Log audit | PASS | 35 OK, 0 FAIL, 0 queueFull |
| `mvnw -DskipTests compile` | PASS | After config fix |
| Backend test | NOT RUN | Runtime-only task; 27F tests already PASS |

---

## 12. Rủi ro còn lại

1. Follow-up short questions may fail retrieval regardless of async persist
2. No Micrometer metrics for persist pool (27G recommended)
3. Config path bug existed in 27F — fixed in 27F2

---

## 13. Đề xuất tiếp theo

- **27G:** Micrometer metrics for chat-persist executor
- Keep `rag.runtime.async-persist.enabled=true` in production
- Consider updating 27F report to note config path fix
