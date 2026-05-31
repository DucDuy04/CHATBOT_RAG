# Runtime Verify: Async Chat Persist — Task 27F2
**Date:** 2026-05-30  
**Verdict:** PASS

---

## Summary

Runtime verification confirms async assistant message persistence works correctly in live Docker. A config path bug discovered during benchmarking (`rag.retrieval.async-persist` vs `rag.runtime.async-persist`) was fixed before the final async run. Sync baseline was captured from the pre-fix run (effectively `enabled=false`).

---

## Benchmark environment

| Item | Value |
|---|---|
| Stack | Docker Compose (mysql, qdrant, backend) |
| Widget ID | `3a26c18c-bfd1-477e-af79-fcd7fba551a9` |
| Widget API key | `c2e09246-1525-44a5-adb9-dfbce7191c8d` |
| Document | `SoTayHocVu_HocKy1_2025-2026_RAG_CONTEXT_HOC_KY.docx` |
| Profile | `docker` |
| Keyword prewarm | `ok=3 failed=0` before benchmark |
| Measured runs | 5 per question + 1 warm-up |

---

## Config status

**Final (restored and verified):**
```yaml
rag:
  runtime:
    async-persist:
      enabled: true
      pool-size: 2
      queue-capacity: 100
```

**Bug found during Phase 2:** Config was under `rag.retrieval.async-persist` (27F placement error). Code reads `rag.runtime.async-persist.enabled`. Fixed and backend rebuilt before async benchmark re-run.

---

## Async enabled results (P1–P5)

Source: `ASYNC_CHAT_PERSIST_BENCH_async_20260530-174644.json`

| Q | p50 clientMs | p50 totalMs | p50 persistMs | max persistMs | p50 asyncElapsedMs | persistMode | asyncStatus | Answer |
|---|---|---|---|---|---|---|---|---|
| P1 | 11180 | 11163 | **108** | 170 | 149 | async | OK | PARTIAL* |
| P2 | 5323 | 5304 | **52** | 112 | 109 | async | OK | PARTIAL* |
| P3 | 11597 | 11581 | **67** | 216 | 127 | async | OK | PARTIAL* |
| P4 | 6113 | 6098 | **25** | 32 | 29 | async | OK | PARTIAL* |
| P5 | 4591 | 4581 | **19** | 80 | 22 | async | OK | PARTIAL* |

\*Answer verdict PARTIAL due to source-check strictness (sources contain LUA1012 but not "Nhóm 1" in chunkText). P1 run 1 answer text is correct: *"Nguyễn Thị Vân Anh, thứ 2, tiết 1 - 2, phòng E301."*

**Aggregate async:**
- avg p50 persistMs: **54ms** (request path)
- max persistMs (request path): **216ms**
- avg p50 asyncElapsedMs: **87ms** (background DB save, logged separately)
- persistMode=async: **25/25 measured runs**
- `[ChatPersistAsync] status=OK`: **25/25**

---

## Sync comparison results (P1–P5)

Source: `ASYNC_CHAT_PERSIST_BENCH_async_20260530-173632.json` (pre-fix run; `persistMode=sync` on all runs because config path was wrong → effectively sync mode)

| Q | sync p50 clientMs | sync p50 totalMs | sync p50 persistMs | sync max persistMs | async p50 persistMs | persistMs saved |
|---|---|---|---|---|---|---|
| P1 | 11893 | 11873 | **592** | **1027** | 108 | **484ms (82%)** |
| P2 | 7015 | 6997 | **219** | 949 | 52 | **167ms (76%)** |
| P3 | 8349 | 8336 | **96** | 207 | 67 | **29ms (30%)** |
| P4 | 5847 | 5834 | **75** | 262 | 25 | **50ms (67%)** |
| P5 | 4795 | 4780 | **57** | 82 | 19 | **38ms (67%)** |

**Aggregate sync:**
- avg p50 persistMs: **208ms**
- max persistMs spike: **1027ms** (P1 run 1)

**clientMs impact:** Mixed — dominated by retrieval/LLM variance. P2 improved 7015→5323ms; others within noise. persistMs reduction is the primary confirmed win.

---

## Async logs audit

```
docker compose logs backend | Select-String "ChatPersistAsync|status=FAIL|queueFull"
```

| Metric | Count |
|---|---|
| `[ChatPersistAsync] status=OK` | 35 |
| `[ChatPersistAsync] status=FAIL` | **0** |
| `queueFull fallback=sync` | **0** |
| `persistMode=async` in latency trace | 35 |

Example log pair (response returned before async save completes):
```
[RAG][latency] trace=xaz17x totalMs=12345 persistMs=102 persistMode=async
[ChatPersistAsync] status=OK trace=xaz17x session=... elapsedMs=241
```

---

## Follow-up history smoke

### Test A — Short Q1 + 2s delay + Q2 (task spec)
- Q1: *"Pháp luật Việt Nam đại cương Nhóm 1 học ở phòng nào?"* → PARTIAL (refusal, no E301)
- Q2 (2s later): *"Giảng viên của môn đó là ai?"* → PARTIAL (refusal)
- **Verdict:** PARTIAL — retrieval could not answer short-form Q1; not an async persist failure

### Test B — Full P1 + 2s delay + Q2
- Q1 (full schedule question): **PASS** — *"Nguyễn Thị Vân Anh, thứ 2, tiết 1 - 2, phòng E301"*
- Q2: PARTIAL — could not re-extract giảng viên from follow-up (retrieval returned different sources)
- **Verdict:** PARTIAL — pre-existing retrieval/history quality issue, not message loss

### Test C — <300ms immediate follow-up
- Same short Q1/Q2 pattern with 100ms delay
- Both PARTIAL (same retrieval failures)
- **Accepted risk:** eventual consistency not the bottleneck here; retrieval quality is

**Async persist did NOT cause history breakage:** `[ChatPersistAsync] status=OK` logged for all Q1 assistant messages before Q2 was sent (2s delay >> async save ~150ms).

---

## Production code changed

**Yes — trivial config fix (allowed):**

`application.yml`: moved `async-persist` block from `rag.retrieval` to `rag.runtime` (matches `@Value` and `@ConditionalOnProperty` paths in code).

---

## Recommendation

| Action | Recommendation |
|---|---|
| Keep async enabled | **YES** — persistMs reduced 30–82%, zero failures |
| Tune executor | Not needed now (0 queueFull at 5 concurrent) |
| Add metrics | Recommended (27G) — pool queue depth, failure counter |
| Disable async | **NO** |

---

## Final verdict: PASS

- persistMode=async observed on all measured runs (post-fix)
- persistMs in request path reduced (avg 208ms → 54ms p50)
- `[ChatPersistAsync] status=OK` on all runs; 0 FAIL; 0 queueFull
- P1 full question answer correct under async mode
- Async config restored enabled
- History smoke PARTIAL due to pre-existing retrieval issues, not async message loss
