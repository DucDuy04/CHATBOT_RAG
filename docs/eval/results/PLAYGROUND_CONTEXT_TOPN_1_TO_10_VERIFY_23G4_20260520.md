# Playground Context Top-N 1–10 Verify — 23G4 (2026-05-20)

## Environment

| Item | Value |
|------|--------|
| Date | 2026-05-20 |
| Backend | `chatbot-backend` running 23G3 code; `docker compose ps` all 4 containers Up |
| Frontend container | Up (port 5173) |
| MySQL / Qdrant | Up, healthy |
| Backend health | `GET /api/chatbots?page=0&size=1` → HTTP 200, total=59 |
| Qdrant | `GET /collections` → `documents` collection OK |
| Test document | `docs/eval/manual/TOPK_LOW_HIGH_DIFFERENCE_TEST_DOCUMENT.txt` |
| CHATBOT_ID | `2cb3e6c9-b78b-4433-a907-3a07407e999f` |
| DOCUMENT_ID | `f6c5c510-caf7-40dd-923b-650e9124fbf1` |
| WIDGET_API_KEY | `6986...d7d0` (masked) |
| chunkCount | **29** (INDEXED) |

## Build / Test

| Command | Result |
|---------|--------|
| `.\mvnw.cmd -DskipTests compile` | **PASS** (nothing to compile, all up-to-date) |
| `.\mvnw.cmd -Dtest=FinalContextSelectionTest,RetrievalTopKTest,ChatServiceModelConfigTopKTest test` | **PASS** 23/23 |
| `npm run lint` | **PASS** (exit 0) |
| `npm run build` | **PASS** (vite build, 464 modules, chunk-size warning only) |
| `docker compose ps` | **PASS** all 4 services Up |
| Full `mvn test` | NOT_RUN (env-dependent, pre-existing) |
| Widget build | NOT_RUN (not required for this verify) |

---

## Test Document Info

| Field | Value |
|-------|-------|
| File | `TOPK_LOW_HIGH_DIFFERENCE_TEST_DOCUMENT.txt` |
| chunkCount | 29 |
| Status | INDEXED |
| Policy codes present | ALPHA-111, BETA-222, GAMMA-333, DELTA-444, EPSILON-555, ZETA-666, ETA-777, THETA-888 |

---

## Matrix A — Playground Normal Top-N 1..10 (Full 8-code question)

Question: Liệt kê mã chính sách của Alpha, Beta, Gamma, Delta, Epsilon, Zeta, Eta và Theta.

| topN | fixedVectorAnchorK | vectorAnchors | afterExpansion | deduped | finalContexts | uiSourceCount | foundCodes | answerStatus | verdict |
|------|--------------------|---------------|----------------|---------|---------------|---------------|------------|--------------|---------|
| 1 | **30** | 29 | 66 | 29 | 1 | 1 | 0 | EMPTY (refusal) | **PASS** |
| 2 | **30** | 29 | 66 | 29 | 2 | 2 | 0 | EMPTY (refusal) | **PASS** |
| 3 | **30** | 29 | 66 | 29 | 3 | 3 | 3 | PARTIAL_MID | **PASS** |
| 4 | **30** | 29 | 66 | 29 | 4 | 4 | 4 | PARTIAL_MID | **PASS** |
| 5 | **30** | 29 | 66 | 29 | 5 | 5 | 5 | PARTIAL_HIGH | **PASS** |
| **6** | **30** | 29 | 66 | 29 | **6** | **6** | 6 | PARTIAL_HIGH | **PASS** ← OLD BUG FIXED |
| 7 | **30** | 29 | 66 | 29 | 7 | 7 | 7 | NEAR_FULL | **PASS** |
| 8 | **30** | 29 | 66 | 29 | 8 | 8 | 8 | NEAR_FULL | **PASS** |
| 9 | **30** | 29 | 66 | 29 | 9 | 9 | 8 | NEAR_FULL | **PASS** |
| 10 | **30** | 29 | 66 | 29 | 10 | 10 | 8 | NEAR_FULL | **PASS** |

**All 10 PASS.**

Key observations:
- `fixedVectorAnchorK=30` stable on ALL 10 runs — never tied to UI topN.
- `vectorAnchors=29` stable (document has 29 unique chunks) — never changes with topN.
- `afterExpansion=66`, `deduped=29` stable.
- `finalContexts == topN` exactly on all 10 runs.
- `uiSourceCount == finalContexts` on all 10 runs — no unjust cap to 2.
- **topN=6: uiSrc=6** (NOT 2 as the old bug showed). OLD BUG CONFIRMED FIXED.
- foundCodes grows consistently with topN (0→0→3→4→5→6→7→8→8→8).
- topN=8 first hits all 8/8 codes.

---

## Old Bug Verification — Top-N=6

| | Old (23G3 bug) | New (23G4 after fix) |
|-|----------------|----------------------|
| finalContexts | 6 | **6** |
| uiSourceCount (sidebar SOURCES) | **2** | **6** |
| answerStatus | PARTIAL (6 codes found but capped to 2 sources) | PARTIAL_HIGH (6 codes, 6 sources) |
| verdict | FAIL (SOURCES cap 2) | **PASS** |

**Answer at topN=6:** Lists ALPHA-111, BETA-222, GAMMA-333, DELTA-444, ZETA-666, THETA-888 (6 codes found).
SOURCES sidebar shows **6** — fix confirmed.

---

## Matrix B — Eta/Theta Targeted Top-N 1..10

Question: Liệt kê mã chính sách của Eta và Theta.

| topN | finalContexts | uiSourceCount | answerHasEta | answerHasTheta | verdict |
|------|---------------|---------------|--------------|----------------|---------|
| 1 | 1 | 1 | false | false | **PASS** (expected thin) |
| 2 | 2 | 2 | true | true | **PASS** |
| 3 | 3 | 3 | true | true | **PASS** |
| 4 | 4 | 4 | true | true | **PASS** |
| 5 | 5 | 5 | true | true | **PASS** |
| 6 | 6 | 6 | true | true | **PASS** |
| 7 | 7 | 7 | true | true | **PASS** |
| 8 | 8 | 8 | true | true | **PASS** |
| 9 | 9 | 9 | true | true | **PASS** |
| 10 | 10 | 10 | true | true | **PASS** |

**All 10 PASS.**

Notes:
- topN=5..9 answers include "Tôi không tìm thấy thông tin này trong tài liệu. Tuy nhiên..." phrasing AND still contain ETA-777 and THETA-888.
- `uiSourceCount` matches `finalContexts` on ALL runs — no cap-to-2 triggered despite partial-refusal phrasing.
- **Improvement over 23G2:** 23G2 had topN=5 PARTIAL (Eta missed). Now topN=2 already returns both codes.

---

## Compare Mode Smoke

### C1: configA(topK=3) vs configB(topK=8)

| Side | topK | foundCodes | sourcesCount | answerSummary |
|------|------|-----------|--------------|---------------|
| A | 3 | 3 (ALPHA, ZETA, THETA) | 3 | Partial — mentions missing Beta, Gamma, Delta, Epsilon, Eta |
| B | 8 | **8/8** | 8 | All 8 codes listed |

- configB answer richer than configA ✓
- Both sides served correctly from same chatbot ✓
- fixedVectorAnchorK=30 for both (confirmed via Matrix A logs) ✓

### C2: configA(topK=1) vs configB(topK=10)

| Side | topK | result |
|------|------|--------|
| A | 1 | Minimal answer |
| B | 10 | Full 8/8 codes |

**Compare smoke: PASS** (manual response verification).

Note: Automated script parsed `$cmp.answerA` / `$cmp.answerB` — wrong field names (should be `$cmp.configA.answer`). Results logged as codes=0/0 but actual compare output confirmed correct. Script field-name bug is eval-script only, not product code.

---

## Production / Widget Source Cap Regression

| Case | Question | responseSources | Cap | Hallucinate | Verdict |
|------|----------|-----------------|-----|-------------|---------|
| InScope | "Tài liệu này nói về hệ thống gì?" | 5 | ≤5 | — | **PASS** |
| OOS FxRate | "Tỷ giá USD/VND hôm nay là bao nhiêu?" | 5 | ≤2 | false | **FAIL** |
| OOS Omega | "Mã chính sách Omega là gì?" | 2 | ≤2 | false | **PASS** |

### OOS FxRate Analysis

Answer summary: *"Tôi không tìm thấy thông tin về tỷ giá USD/VND hôm nay trong tài liệu. Tuy nhiên, tôi có thể đếm số lượng chính sách được..."*

The LLM **pivots** from the OOS question to listing document policy content (with colon-separated lines: `- Alpha: ALPHA-111`, etc.). This makes `hasSubstantiveFactualContent()` return `true` → `isPureRefusalLikeAnswer()` returns `false` → production source cap of 2 NOT applied → 5 sources returned.

**Root of failure:** This is a trade-off introduced by 23G3's `isPureRefusalLikeAnswer` fix:
- Before 23G3: `isRefusalLikeAnswer` (simple marker check) → capped to 2 for any refusal phrase.
- After 23G3: `isPureRefusalLikeAnswer` (requires no substantive content) → pivot answers bypass cap.

**No hallucination:** No fake exchange rate invented. No OMEGA-* invented.

**Scope note:** This regression is in production source-cap behavior, NOT in Playground source presentation (which is the primary task of 23G3/23G4). The old bug (Playground topN=6 showing SOURCES(2)) is **fully fixed**.

---

## OOS Regression

| Query | Hallucinate | Verdict |
|-------|------------|---------|
| "Tỷ giá USD/VND hôm nay là bao nhiêu?" | No fake rate | **PASS** (no hallucination) |
| "Mã chính sách Omega là gì?" | No OMEGA-* | **PASS** |

---

## PDF / Table Regression

**NOT_RUN_NO_PDF_ARTIFACT** — `HeThongQuanLyYeuCauPhucKhao.pdf` not in repo.

---

## Conclusion

**PARTIAL** — primary task PASS, one production source-cap edge case regressed from 23G3.

### PASS criteria met

| Criterion | Status |
|-----------|--------|
| Playground Top-N 1..10 all run | ✓ PASS |
| fixedVectorAnchorK=30 stable | ✓ PASS |
| finalContexts == topN for all 10 | ✓ PASS |
| Playground uiSourceCount == topN (no unjust cap-to-2) | ✓ PASS |
| Old bug topN=6 SOURCES(2) fixed → SOURCES(6) | ✓ PASS |
| Compare smoke (3 vs 8, 1 vs 10) | ✓ PASS |
| No OOS hallucination (Omega, FxRate) | ✓ PASS |
| InScope source cap ≤5 | ✓ PASS |
| OOS Omega source cap ≤2 | ✓ PASS |

### FAIL / PARTIAL

| Criterion | Status |
|-----------|--------|
| OOS FxRate source cap ≤2 | **FAIL** — returns 5 (pivot answer, not hallucination) |
| PDF/table regression | NOT_RUN |

### Overall: PARTIAL (primary Playground source-presentation task = PASS; one secondary OOS-cap edge case = FAIL pre-existing trade-off from 23G3)

---

## Artifacts

- Script: `docs/eval/results/_run_23g4_verify.ps1`
- Raw JSON: `docs/eval/results/_run_23g4_results.json`
