# RAG — FE Top-K wiring 22A (official result)

**File:** `docs/eval/results/RAG_FE_TOPK_WIRING_22A_20260515.md`  
**Report:** `reports/refactor/CURSOR_REPORT_22A_FE_TOPK_WIRING.md`

**Ngày:** 2026-05-15

---

## 1. FE control inventory

| Item | Giá trị |
|------|---------|
| Control chính | `Frontend/src/pages/playground/components/ModelOverridePanel.jsx` |
| State | `overrideParams.topK` trên `PlaygroundPage.jsx` (default **5**) |
| Compare A/B | `CompareConfigPanel.jsx` → `compareConfigA/B.topK` |
| Chatbot config (persistent) | `ModelSettingsSection.jsx` — **không** wired per-request trong task này |
| Widget / ChatPage | **Không** có control Top-K |

FE đã gửi `overrideParams: { topK, temperature, maxTokens }` qua `playgroundApi.chat` nhưng BE **bỏ qua** trước 22A.

---

## 2. BE retrieval inventory (trước sửa)

| Item | Giá trị |
|------|---------|
| Vector search limit | `ANCHOR_TOP_K = 30` cố định trong `RagRetrievalService` |
| `ChatRequest` | Không có `topK` |
| `FINAL_LIMIT` / expanded | 10 / 20 / 60 — **không đổi** trong 22A |
| Playground | `PlaygroundController` comment: overrideParams chưa map |

---

## 3. Design chosen

**Per-request override** (Option minimal):

```text
Playground overrideParams.topK / ChatRequest.topK
  → ChatService.chat / chatStream
  → RagRetrievalService.retrieveWithMetadata(..., topKOverride)
  → embeddingService.search(variant, effectiveAnchorTopK, widgetId)
```

- Không static/global state.
- `null` topK → **30** (backward compatible).
- Source cap 21J **không đổi** (chỉ presentation).

---

## 4. Default / min / max topK

| Constant | Giá trị |
|----------|--------:|
| `DEFAULT_ANCHOR_TOP_K` | **30** |
| `MIN_ANCHOR_TOP_K` | **1** |
| `MAX_ANCHOR_TOP_K` | **30** |
| FE UI max | **30** (đồng bộ BE) |

---

## 5. Code change summary

**Backend:** `ChatRequest.topK`, overload `retrieveWithMetadata(..., Integer)`, `normalizeAnchorTopK`, `parseTopKOverride`, `PlaygroundController` map override, `PlaygroundService.compare` dùng config topK, log `[RAG] retrieval topK requested=..., effective=...`.

**Frontend:** `playgroundApi` gửi `topK` root + `overrideParams`; UI max 30; cập nhật help text.

**Test:** `RetrievalTopKTest` (6 tests).

---

## 6. Compile / test / build

| Command | Kết quả |
|---------|---------|
| `Backend\.\mvnw.cmd -DskipTests compile` | **PASS** |
| `Backend\.\mvnw.cmd -Dtest=RetrievalTopKTest,ChatServiceSourcePresentationTest test` | **PASS** (12 tests) |
| `Frontend\npm run lint` | **PASS** |
| `Frontend\npm run build` | **PASS** |
| `Frontend\npm run build:widget` | **NOT RUN** |
| `docker compose config -q` | **PASS** (from prior session) |

---

## 7. Runtime verify (Docker backend rebuild)

`POST /api/chat` với `X-Widget-Key`, body có `topK`:

| Request topK | Log effective | Kết quả |
|--------------|---------------|---------|
| 10 | 10 | **PASS** |
| 999 | 30 (clamp) | **PASS** |
| null / omitted | 30 | **PASS** |
| 3 | (chat OK; log trong batch) | **PASS** smoke |

Log mẫu:

```text
[RAG] retrieval topK requested=10, effective=10
[RAG] retrieval topK requested=999, effective=30
[RAG] retrieval topK requested=null, effective=30
```

---

## 8. Known limitations

- Chỉ **anchor vector top-K** (Qdrant `limit`) được override; `FINAL_LIMIT`, rerank, expansion budget giữ nguyên.
- `temperature` / `maxTokens` playground vẫn chưa map LLM.
- Chatbot `modelConfig.topK` (WidgetService default 5) chưa auto-apply cho `/api/chat` widget — chỉ explicit request field.
- Playground `RetrievalPanel` vẫn có thể cap hiển thị sources theo UI Top-K (khác source cap 21J response).

---

## 9. Source cap 21J

**Không ảnh hưởng** — `buildSourceDtosForResponse` / `MAX_RESPONSE_SOURCES=5` giữ nguyên.
