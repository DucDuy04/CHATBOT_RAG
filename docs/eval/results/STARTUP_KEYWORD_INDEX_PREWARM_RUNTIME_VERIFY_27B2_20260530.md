# STARTUP_KEYWORD_INDEX_PREWARM — Runtime Verify 27B2

**Date:** 2026-05-30  
**Task:** 27B2 — Runtime Verify Startup Keyword Index Prewarm  
**Verdict:** **PASS**

---

## Environment

| Item | Value |
|---|---|
| Stack | Docker Compose (`mysql`, `qdrant`, `backend`) |
| Profile | `docker` |
| Chatbot / widgetId | `3a26c18c-bfd1-477e-af79-fcd7fba551a9` |
| Widget API key | `c2e09246-1525-44a5-adb9-dfbce7191c8d` |
| Document | `7ae6d0b9-b7de-4bc8-8be6-10798add73aa` |
| Backend image | Rebuilt via `docker compose up --build -d backend` |

---

## Phase 1 — Startup & Prewarm Logs

**Commands:**
```powershell
docker compose config -q
docker compose up -d mysql qdrant
docker compose up --build -d backend
docker compose logs backend --tail=300
```

**Result:** Backend started in ~48s. Prewarm ran on daemon thread `keyword-index-prewarm` after `ApplicationReadyEvent` + 2s delay.

**Log excerpt:**
```
Started RagChatbotBeApplication in 48.081 seconds
[KeywordIndexPrewarm] started widgets=3 limit=3000
[RAG][keyword-index] widget=3a26c18c-bfd1-477e-af79-fcd7fba551a9 status=warm chunks=3000 buildMs=5002 terms=54434
[KeywordIndexPrewarm] widget=3a26c18c-bfd1-477e-af79-fcd7fba551a9 status=OK elapsedMs=5003
[KeywordIndexPrewarm] finished ok=3 failed=0 totalMs=23295
```

| Check | Result |
|---|---|
| Backend starts | PASS |
| `[KeywordIndexPrewarm] started` | PASS |
| Rank-1 widget warmed | PASS (`3a26c18c-...` status=OK) |
| `[KeywordIndexPrewarm] finished` | PASS (ok=3, failed=0) |
| Config disabled? | No — prewarm ran |
| No active chunks? | No — 3 widgets had chunks |
| Listener not registered? | No — listener fired |
| Async thread failed? | No — all 3 OK |

**Note:** Index build cost (~5s for rank-1 widget) moved to **startup background thread**, not first user request.

---

## Phase 2 — First Chat After Restart

**Query (L1):**
```
Pháp luật Việt Nam đại cương Nhóm 1 học với giảng viên nào, thứ mấy, tiết nào, phòng nào?
```

**API:** `POST http://localhost:8080/api/chat` with `X-Widget-Key`

**Answer:** Nguyễn Thị Vân Anh, thứ 2, tiết 1 - 2, phòng E301 — **PASS**

**RagLatencyTrace (trace=zbvzj7):**
```
totalMs=32377
keywordIndexHit=true
keywordIndexBuildMs=0
keywordMs=2770
queryEmbedMs=4416
retrievalMs=23151
llmTotalMs=1325
```

| Check | Expected | Actual | Result |
|---|---|---|---|
| keywordIndexBuildMs | ≈ 0 | **0** | PASS |
| keywordIndexHit | true | **true** | PASS |
| No ~12s cold build in request path | yes | keywordMs=2770 (lookup only) | PASS |
| Correct answer | Vân Anh, E301 | match | PASS |

---

## Phase 3 — OOS Guard Smoke

**Query:**
```
Tỷ giá USD/VND hôm nay là bao nhiêu?
```

**Answer:** `Tôi không tìm thấy thông tin này trong tài liệu.` — **PASS** (refusal)

**RagLatencyTrace (trace=k1096x):**
```
totalMs=8785
keywordIndexHit=true
keywordIndexBuildMs=0
```

---

## Phase 4 — Comparison vs 27A Baseline

| Scenario | Before 27B (27A cold) | After 27B (27B2 runtime) | Verdict |
|---|---:|---:|---|
| First chat `keywordIndexBuildMs` | **12177** | **0** | **Improved — PASS** |
| First chat `keywordIndexHit` | false (miss) | **true** | PASS |
| First chat `keywordMs` (request path) | ~12705 (incl. build) | **2770** (lookup only) | Improved |
| First chat `totalMs` | 26102 | 32377 | No regression claim on total; retrieval/embed dominate |
| OOS refusal | correct | correct | PASS |
| Steady-state semantics | unchanged | unchanged | PASS (no code change) |

**Interpretation:** 27B successfully moves ~12s keyword index build off the first-chat request path. `totalMs` on first chat can still be high (query embed, vector, scoring, LLM) — consistent with 27A finding that LLM is not the only cost but keyword cold build was the largest single cold-start spike.

---

## Production Code Changes

**None.** Runtime verify only; no wiring bug found.

---

## Final Verdict

**PASS** — All acceptance criteria met:
- Backend starts
- Startup prewarm logs appear
- Rank-1 widget prewarmed at startup
- First chat `keywordIndexBuildMs=0`
- Correct answer returned
- OOS still refuses
- No production code changes required
