# CURSOR REPORT — Task 25D LLM Adapter Package Refactor

**Date:** 2026-05-28  
**Verdict:** **PASS**

---

## Summary

Moved `LlmFallbackService` and `LlmGenerationOptions` from `service` to `llm`. Updated runtime, API, widget, and test imports. Confirmed no stale duplicate Java files remain under `service/`. **50 tests**, 0 failures. No behavior change.

---

## Inventory (focus classes)

| Class | Before | After | Decision |
|---|---|---|---|
| `LlmFallbackService` | `service` | `llm` | **Moved** |
| `LlmGenerationOptions` | `service` | `llm` | **Moved** |
| `DocumentService` | `service` | `service` | **Keep** |
| `WidgetService` | `service` | `service` | **Keep** (+ import `llm`) |
| `AnalyticsService` | `service` | `service` | **Keep** |
| `DashboardService` | `service` | `service` | **Keep** |
| `SettingsService` | `service` | `service` | **Keep** |
| `ChatFeedbackService` | `service` | `service` | **Keep** |
| Stale `service/ChatService` etc. | — | — | **Already gone** (25C) |

---

## Files moved

```
service/LlmFallbackService.java     → llm/LlmFallbackService.java
service/LlmGenerationOptions.java   → llm/LlmGenerationOptions.java
llm/package-info.java               (new)
```

**Deleted:** old copies under `service/`

**Import updates:**

- `rag.runtime.ChatService`, `PlaygroundService`
- `api.PlaygroundController`
- `service.WidgetService`
- `rag.runtime.ChatServiceSourcePresentationTest`

**Docs:** `agent/02-architecture.md`, `agent/03-backend.md`

---

## Classes kept in `service` and why

| Class | Why |
|---|---|
| `DocumentService` | Upload/index lifecycle orchestration |
| `WidgetService` | Widget/admin CRUD |
| `AnalyticsService` | Analytics API |
| `DashboardService` | Dashboard API |
| `SettingsService` | Settings/API keys |
| `ChatFeedbackService` | Feedback API |

---

## Stale duplicate cleanup

**Result:** No stale duplicate `.java` files found in `service/` (25C already removed moved-class copies). Only legitimate application services remain (6 classes).

---

## Package map after 25D

- `llm` — `LlmFallbackService`, `LlmGenerationOptions`
- `rag.runtime` — chat/playground orchestration (imports `llm`)
- `audit.metrics`, `rag.analysis`, `rag.rerank`, `rag.budget`, `ingest.*`, `index.*`, `rag.retrieve`, `rag.prompt` — unchanged from 25C
- `service` — application/admin only

---

## Tests per phase

| Phase | `mvn clean test` |
|---|---|
| 2–3 Move LLM + imports | PASS |
| 4 Stale cleanup | N/A (none) |
| 5 Docs | PASS |
| Final | **PASS (50)** |

`docker compose config -q`: **PASS**

---

## Behavior changes

**None.**

---

## Phase 7 scan

- `service.(ChatService|…|Llm*)` in `Backend/src`: **0 hits**
- `QdrantEmbeddingStore` / gRPC write in src: **0 hits**
- `table_row_group` / `text_table_like`: legitimate runtime/prompt usage

---

## Risks remaining

- Legacy markdown reports still reference old `service` paths
- Docker backend startup smoke not run

---

## Recommended next task

**25E** — test package hygiene (`service` test fixtures), `.cursor/rules` path updates, or `common.util` if helpers emerge.
