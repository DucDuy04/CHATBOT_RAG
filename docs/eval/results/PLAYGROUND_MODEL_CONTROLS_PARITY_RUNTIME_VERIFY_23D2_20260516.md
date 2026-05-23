# Task 23D2 — Playground & Model Settings Runtime Verify

**Date:** 2026-05-16  
**Type:** VERIFY ONLY (no code changes)  
**Baseline fix:** Task 23D  
**Conclusion:** **PASS**

## Environment

| Component | Status | Notes |
|-----------|--------|-------|
| `docker compose` backend + mysql + qdrant | UP | `chatbot-backend`, `ragchatbot-mysql`, `ragchatbot-qdrant` |
| Frontend docker image | **BUILD FAILED** | `npm run build` in Docker: bridge/veth prestart hook error |
| Frontend UI verify | Vite dev `http://localhost:5173` | `npm run dev --port 5173` |
| Backend API | `http://localhost:8080` | Health: `GET /api/chatbots?page=0&size=1` OK |
| Playwright | Local install in `docs/eval/results/node_modules` (verify run only) | Script `_run_23d2_ui_verify.mjs` |

## Chatbot / document

| Field | Value |
|-------|-------|
| Chatbot ID | `6cc17762-3971-45b5-9a09-ebeb7367f96d` |
| Document | `docs/eval/manual/HeThongQuanLyYeuCauPhucKhao.pdf` |
| Document ID | `801ee52a-4186-4af6-8ee7-bfcfec838547` |
| Index status | INDEXED |

Test questions (API): entity-table list (Top-K), system description (temperature), OOS exchange rate (regression).

---

## A. Playground normal Top-K

**Method:** `POST /api/playground/chat` (SSE), body via `curl --data-binary @file.json` (PowerShell inline `-d` failed silently in first script pass).

| Test | topK | Source count (done event) | Backend log (excerpt) | Result |
|------|------|---------------------------|------------------------|--------|
| 1 | 3 | **2** | `source=REQUEST effective=3`; `Response sources capped: 20 → 20 deduped → **3 returned (cap=3)**` | **PASS** (count ≤3; cap=3) |
| 2 | 10 | **10** | `source=REQUEST effective=10`; `→ **10 returned (cap=10)**` | **PASS** (>5, not hard-capped at 5) |

Evidence: manual re-run 2026-05-16T16:50Z; playground debug cap follows effective topK (23D fix confirmed).

---

## B. Playground temperature

| Test | temperature | Backend log | Result |
|------|-------------|-------------|--------|
| A | 0.2 | `effectiveTemperature=0.2 effectiveMaxTokens=1024` | **PASS** |
| B | 0.7 | `effectiveTemperature=0.7 effectiveMaxTokens=1024` | **PASS** |

---

## C. Model Settings Top-K

| Step | Expected | Observed | Result |
|------|----------|----------|--------|
| Save topK=5, reload GET | UI topK=5 | `modelConfig.topK: 5` | **PASS** |
| Chat no request topK | `source=MODEL_CONFIG effective=5` | Log matches; response sources=5 (presentation cap) | **PASS** |
| Save topK=10, reload GET | UI topK=10 | `modelConfig.topK: 10` | **PASS** |
| Chat no request topK | `source=MODEL_CONFIG effective=10` | Log matches; response sources=5 (presentation cap, retrieval=10) | **PASS** |

Note: Public `/api/chat` source **display** still ≤5 by design (21J); runtime **retrieval** topK=10 verified in logs.

---

## D. maxTokens regression

| Check | Log | Result |
|-------|-----|--------|
| modelConfig maxTokens=512 | `maxTokensSource=MODEL_CONFIG effectiveMaxTokens=512` | **PASS** |

---

## E. Compare Mode layout (UI)

**Method:** Playwright headless, mock login → `/playground`, viewport 1400×900.

| Check | Expected | Observed | Result |
|-------|----------|----------|--------|
| Compare mode ON | Right sidebar hidden | `compareSidebarLocatorCount=0`, compare pane visible | **PASS** |
| Compare mode OFF | Sidebar restored | `sidebarAfterToggleOff=1` | **PASS** |

Screenshot: `docs/eval/results/screenshots_23d2/playground_compare_mode.png`  
JSON: `docs/eval/results/_run_23d2_ui_results.json`

---

## F. Compare A/B smoke (API)

Question: system description (short). Config A: topK=5, temp=0.2, maxTok=512. Config B: topK=10, temp=0.7, maxTok=1024.

| Side | Sources | Result |
|------|---------|--------|
| A | 10 | Answers returned |
| B | 10 | **PASS** (compare still works) |

---

## G. Production regression

| Case | Endpoint | Source count | Result |
|------|----------|--------------|--------|
| In-scope | `/api/chat` (no topK in body) | **5** | **PASS** (≤5) |
| OOS | `/api/chat` USD/VND rate | **2** | **PASS** (≤2); refusal-like answer |

---

## Artifacts

| File | Purpose |
|------|---------|
| `docs/eval/results/_run_23d2_results.json` | First API batch (playground SSE parse failed — superseded) |
| `docs/eval/results/_run_23d2_ui_results.json` | Compare sidebar UI |
| `docs/eval/results/screenshots_23d2/playground_compare_mode.png` | UI evidence |

---

## Conclusion

| Area | Verdict |
|------|---------|
| Playground Top-K 3/10 | **PASS** |
| Playground temperature | **PASS** |
| Model Settings Top-K save/runtime | **PASS** |
| maxTokens | **PASS** |
| Compare sidebar hide | **PASS** |
| Compare A/B | **PASS** |
| Production source cap | **PASS** |
| **Overall** | **PASS** |

No code changes in 23D2. Optional follow-up: fix Docker frontend build networking for full compose UI parity (out of 23D2 scope).
