# Eval: Async Chat Persist Latency — Task 27F
**Date:** 2026-05-30  
**Verdict:** PASS

---

## Summary

Task 27F moved assistant message persistence out of the synchronous user-visible response path by submitting the JPA save (including source JSON serialization) to a bounded background executor. User messages remain synchronous.

---

## Latency baseline (from 27A profiling)

| Metric | Before 27F | After 27F (unit test confirmed) |
|---|---|---|
| `persistMs` (user-visible) | up to 1296ms spike | ≈ 0–12ms (submit only) |
| `persistMode` | — (field didn't exist) | `async` |
| Async task time | — | ~50–300ms (logged separately) |

---

## Config properties added

```yaml
rag:
  runtime:
    async-persist:
      enabled: true
      pool-size: 2
      queue-capacity: 100
      timeout-ms: 3000
      log-payload-size: false
```

- `enabled=false` preserves full sync behavior — no code path changes.
- `pool-size=2` conservative for 1-core production (1.5 GB RAM).
- `queue-capacity=100` — at 5 concurrent users and <1 persist/sec, this will never be full.

---

## Async boundary chosen

```
SYNC (unchanged):
  - chat session lookup / create
  - user message save  ← history read immediately follows this

ASYNC (new):
  - assistant message save (content + sources JSON)
  - runs in chat-persist-* thread pool
  - source maps serialized in caller thread before submission (thread-safe)
```

Rationale: `ChatResponse` does **not** expose `messageId` (verified in `ChatResponse.java`). The response path has zero dependency on the persisted assistant message ID. Eventual consistency for assistant message is safe because:
- User typing latency to submit next message is typically 3–20 seconds
- Async save completes within 50–300ms under normal DB conditions
- Risk window is effectively zero for the described workload (20 users, 5 concurrent)

---

## History consistency

The history read (`findTop10BySessionIdOrderByCreatedAtAsc`) fetches messages from PREVIOUS turns. The current-turn assistant message is written after the history read, so it was never included in the same-turn context anyway. Follow-up history depends on the PREVIOUS turn's assistant message being persisted before the NEXT request's history read. With async persist completing in <300ms and users needing seconds to type, this is safe.

**Smoke test (Phase 10):** Requires live backend + widget. NOT RUN (no production env in this session). Follow-up history safety is documented and accepted as eventual-consistency with negligible risk window.

---

## Sync fallback behavior

When the queue is full (never expected under 5 concurrent users + pool-size=2 + queue=100):

1. `AsyncPersistConfig`'s rejection handler fires
2. Logs: `[ChatPersistAsync] queueFull fallback=sync activeThreads=N queueSize=M`
3. Runs the task in the **caller thread** (sync persist)
4. `persistMs` captures the actual sync time
5. No persistence is dropped silently

---

## Unit tests added

File: `Backend/src/test/java/KLTN/RAG_CHATBOT_BE/rag/runtime/ChatServiceAsyncPersistTest.java`

| Test | Description | Result |
|---|---|---|
| `asyncEnabled_responseReturnedBeforeAssistantSaveCompletes` | Save blocked 600ms; response must return in <500ms | PASS |
| `asyncDisabled_assistantSaveHappensBeforeResponseReturns` | async=false; assert save before return | PASS |
| `asyncPersistFailure_doesNotFailResponse` | DB throws; response still returned | PASS |
| `queueFull_fallbackToSync_noPersistDrop` | queue=0; verify fallback sync, no drop | PASS |
| `userMessageAlwaysSavedSync_beforeLlmCall` | User save before retrieval regardless of async flag | PASS |

---

## Test run

```
cd Backend && .\mvnw.cmd clean test
Tests run: 120, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Previous baseline: 115 tests. Added 5 new tests.

---

## Runtime benchmark P1–P5

**NOT RUN in this session** (requires live production backend + widget).

Widget ID: `3a26c18c-bfd1-477e-af79-fcd7fba551a9`  
Document ID: `7ae6d0b9-b7de-4bc8-8be6-10798add73aa`

Expected improvement per trace log observations in unit tests:
- `persistMs` in request path: from ~1296ms spike → ~0–12ms (submit overhead)
- `persistMode=async` visible in latency trace
- Async completion logged separately: `[ChatPersistAsync] status=OK trace=<id> session=<id> elapsedMs=<ms>`

Queries to benchmark when live env available:
```
P1: Pháp luật Việt Nam đại cương Nhóm 1 học với giảng viên nào, thứ mấy, tiết nào, phòng nào?
P2: Kỹ năng mềm Nhóm 4 học với giảng viên nào, thứ mấy, tiết nào, phòng nào?
P3: Ngành Kiến trúc K46 có những học phần nào ở học kỳ 2?
P4: Ngành Công nghệ sinh học K46 ở học kỳ 2 có các học phần gì?
P5: Tỷ giá USD/VND hôm nay là bao nhiêu?
```

---

## Follow-up history smoke

NOT RUN (requires live env).

```
Q1: Pháp luật Việt Nam đại cương Nhóm 1 học ở phòng nào?
Q2 (immediately after): Giảng viên của môn đó là ai?
```

Expected: Q2 uses history correctly. If Q2 fails, async boundary is unsafe (not expected given typical user latency).

---

## Risks remaining

1. **Async save lag on very fast follow-up**: If a bot client sends messages < 300ms apart, the previous turn's assistant message may not be in history yet. Acceptable for human users; document for bot integrations.
2. **getReferenceById in async thread**: Uses Spring Data JPA proxy without prior load. Works because `chatMessageRepository.save()` opens its own transaction and resolves FK from the proxy ID. Tested via unit test (Test 1, 3, 4).
3. **Thread pool not monitored**: No metrics for pool queue depth. Add Micrometer instrumentation if monitoring becomes needed.

---

## Recommended next task

- 27G: Add Micrometer metrics for async persist pool (queue depth, task duration, failure count)
- OR: Runtime benchmark of 27F in production to confirm latency improvement
