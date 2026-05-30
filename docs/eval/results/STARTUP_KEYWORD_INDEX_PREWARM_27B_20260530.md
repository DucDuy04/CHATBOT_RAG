# STARTUP_KEYWORD_INDEX_PREWARM_27B — Evaluation Results

**Date:** 2026-05-30  
**Task:** 27B — Startup Keyword Index Prewarm After Backend Restart  
**Verdict:** PARTIAL (code complete + unit tests PASS; runtime verification NOT RUN — no live env)

---

## 1. Problem Statement

After backend restart, `KeywordIndexCache` is empty.  
First chat for any widget pays full cold keyword-index build cost.  
27A profiling measured `keywordIndexBuildMs ≈ 12 s` for rank-1 chatbot corpus.

27A already added `KeywordIndexCache.warm(widgetId, corpusLimit)` and post-ingest prewarm.  
27B adds startup prewarm so the cache is hot before the first user chat.

---

## 2. Startup Prewarm Flow (27B)

```
ApplicationReadyEvent (Spring)
  └── KeywordIndexStartupPrewarmer.onApplicationReady()
        ├── if prewarm-on-startup=false → log disabled, return
        ├── if prewarm-async=true → submit runPrewarm() to single daemon thread
        └── runPrewarm():
              ├── sleep prewarm-delay-ms (default 2000ms — gives Spring context settle time)
              ├── widgetConfigRepository.findAll()
              │     └── @SQLRestriction("deleted_at IS NULL") auto-filters deleted widgets
              ├── for each widget:
              │     └── documentChunkRepository.existsByWidgetConfigId(widgetId)
              │           └── @SQLRestriction filters soft-deleted chunks
              ├── filter: only widgets with ≥1 active chunk
              ├── cap: prewarm-max-widgets (default 20)
              └── for each eligible widget:
                    └── keywordIndexCache.warm(widgetId, prewarmCorpusLimit)
                          └── if error: log ERROR and continue (no app crash)
```

---

## 3. Config Properties Added

Namespace: `rag.retrieval.keyword-index` in `application.yml`

| Property | Default | Description |
|---|---|---|
| `prewarm-on-startup` | `true` | Enable/disable startup prewarm |
| `prewarm-async` | `true` | Run in background daemon thread |
| `prewarm-max-widgets` | `20` | Cap on number of widgets to prewarm |
| `prewarm-corpus-limit` | `3000` | Chunks per widget (matches max-keyword-scan-chunks) |
| `prewarm-delay-ms` | `2000` | Delay after app ready before warming |

To disable in docker:
```yaml
rag:
  retrieval:
    keyword-index:
      prewarm-on-startup: false
```

---

## 4. Expected Latency Improvement (vs 27A Baseline)

| Scenario | Before 27B | After 27B | Expected |
|---|---|---|---|
| First chat after restart (prewarmed widget) | `keywordIndexBuildMs ≈ 12s` | `keywordIndexBuildMs ≈ 0ms` | **Eliminated cold build cost** |
| Subsequent chats (steady-state) | unchanged | unchanged | No regression |
| OOS refusal | correct | correct | No regression |

Note: The 12s cold build was measured in 27A for rank-1 chatbot (widgetId `3a26c18c-bfd1-477e-af79-fcd7fba551a9`).  
After 27B, `warm()` is called at startup so `lookup()` finds a hot entry and `buildMs = 0`.

---

## 5. Tests Added

| Test | Description | Result |
|---|---|---|
| `disabled_config_does_nothing` | prewarm-on-startup=false → no warm calls | PASS |
| `warms_active_widgets_with_chunks_only` | A+B warm, D (no chunks) skipped | PASS |
| `respects_prewarm_max_widgets_cap` | 5 eligible → only 2 warmed | PASS |
| `one_widget_failure_does_not_stop_others` | A fails → B still warmed, no crash | PASS |
| `uses_configured_corpus_limit` | corpus-limit=1234 → warm(widget, 1234) | PASS |

---

## 6. mvn clean test Result

```
Tests run: 82, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

(77 pre-existing + 5 new KeywordIndexStartupPrewarmerTest)

---

## 7. Runtime Verification

**NOT RUN** — live Docker env with MySQL/Qdrant and valid API keys not available during this task.

### Expected startup log sequence (for documentation)

```
INFO  [...] [KeywordIndexPrewarm] started widgets=1 limit=3000
INFO  [...] [RAG][keyword-index] widget=3a26c18c-bfd1-477e-af79-fcd7fba551a9 status=warm chunks=<N> buildMs=<X> terms=<Y>
INFO  [...] [KeywordIndexPrewarm] widget=3a26c18c-bfd1-477e-af79-fcd7fba551a9 status=OK elapsedMs=<X>
INFO  [...] [KeywordIndexPrewarm] finished ok=1 failed=0 totalMs=<total>
```

### Expected first-chat trace after 27B

```json
{
  "keywordIndexBuildMs": 0,
  "keywordMs": "<low — index already warm>",
  "totalMs": "<significantly less than 26s cold trace>"
}
```

### Verification procedure (when env available)

```powershell
docker compose config -q
docker compose up --build -d backend
docker compose logs backend --tail=200
# Look for [KeywordIndexPrewarm] lines
# Then send first chat query and check RagLatencyTrace log
```

---

## 8. Risks Remaining

- Runtime verification pending (no live env).
- `widgetConfigRepository.findAll()` loads all active widgets; acceptable for ≤30 widgets.
- If DB is slow at startup, `findEligibleWidgetIds()` may time out; the outer try-catch logs error and returns gracefully.
- `prewarm-delay-ms=2000` may need tuning in slow environments (increase if DB connection pool not ready).
- Soft-delete filter is correct via `@SQLRestriction` on both `WidgetConfig` and `DocumentChunk` — no explicit deleted_at check needed in JPQL-derived methods.
