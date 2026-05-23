# OOS Pivot Source Cap Fix — 23G5 (2026-05-21)

## Issue from 23G4

Task 23G4 verified Playground Context Top-N 1..10 and found **OOS FxRate source cap FAIL**:

- Question: "Tỷ giá USD/VND hôm nay là bao nhiêu?"
- Answer: Starts with refusal "Tôi không tìm thấy thông tin về tỷ giá..." then **pivots** to listing policy codes (ALPHA-111, BETA-222…)
- `responseSources = 5` — expected ≤2
- No hallucination, but too many sources returned for OOS question.

Root: 23G3 fix changed production cap to use `isPureRefusalLikeAnswer()` which requires no substantive content. Pivot answer has policy codes after refusal → `hasSubstantiveFactualContent=true` → not capped.

---

## Root Cause

`applyAnswerAwareSourceCap` (production path) called `isPureRefusalLikeAnswer()`:

```
isPureRefusalLikeAnswer = isRefusalLikeAnswer AND NOT hasSubstantiveFactualContent
```

- **OOS pivot answer**: "Không tìm thấy tỷ giá. Tuy nhiên: ALPHA-111, BETA-222…"
  - `isRefusalLikeAnswer=true` (refusal marker present)
  - `hasSubstantiveFactualContent=true` (ALPHA-111 present, colon lines)
  - `isPureRefusalLikeAnswer=false` → NO cap → 5 sources

- **Partial in-scope answer**: "ALPHA-111 found. Không tìm thấy Eta."
  - `isRefusalLikeAnswer=true`
  - `hasSubstantiveFactualContent=true`
  - `isPureRefusalLikeAnswer=false` → NO cap → 5 sources (correct behavior)

The two cases are identical from `isPureRefusalLikeAnswer`'s perspective. **The key difference is order**: OOS pivot has refusal FIRST, codes AFTER. Partial in-scope has codes FIRST, refusal AFTER.

---

## Design Chosen

**New helper `isLeadingRefusalAnswer(String answer)`:**

Rule: if the first occurrence of a refusal marker appears **before** the first policy code (`WORD-123`) or numbered list item, the answer is treated as a leading refusal → OOS source cap (≤2) applied.

- OOS pivot: refusal at position 0, codes after → leading refusal ✓ → cap
- Partial in-scope: ALPHA-111 at position 12, refusal after → NOT leading refusal → no cap
- Pure OOS: refusal at position 0, no codes at all → leading refusal ✓ → cap
- Playground debug: bypasses check entirely via `playgroundDebugSources=true` flag

`applyAnswerAwareSourceCap` updated to use `isLeadingRefusalAnswer` instead of `isPureRefusalLikeAnswer`.
`isPureRefusalLikeAnswer` and `hasSubstantiveFactualContent` retained unchanged (unit tests coverage).

---

## Code Changes

### `ChatService.java`

1. Added `import java.util.regex.Matcher;`
2. Added `isLeadingRefusalAnswer(String answer)` static method
3. Modified `applyAnswerAwareSourceCap` to use `isLeadingRefusalAnswer` (was `isPureRefusalLikeAnswer`)
4. Updated log message: "Leading refusal-like answer (OOS/pivot)"

**Diff summary:**

```diff
+import java.util.regex.Matcher;
 import java.util.regex.Pattern;
```

```diff
-        if (!isPureRefusalLikeAnswer(answer)) {
+        if (!isLeadingRefusalAnswer(answer)) {
             return sources;
         }
-        log.info("[Chat] Pure refusal-like answer → response sources {} → {}",
+        log.info("[Chat] Leading refusal-like answer (OOS/pivot) → response sources {} → {}",
                 sources.size(), MAX_REFUSAL_RESPONSE_SOURCES);
```

```diff
+    /**
+     * Returns true when the answer opens with an OOS/refusal statement before any factual
+     * content. Pivot answers ("didn't find X, but here are policy codes Y, Z") match
+     * because the refusal leads. Partial in-scope answers ("found ALPHA-111, didn't find Eta")
+     * do NOT match because factual content precedes the refusal phrase.
+     */
+    static boolean isLeadingRefusalAnswer(String answer) {
+        if (!isRefusalLikeAnswer(answer)) { return false; }
+        String normalized = answer.toLowerCase(Locale.ROOT);
+        int refusalPos = Integer.MAX_VALUE;
+        for (String marker : REFUSAL_ANSWER_MARKERS) {
+            int pos = normalized.indexOf(marker);
+            if (pos >= 0) { refusalPos = Math.min(refusalPos, pos); }
+        }
+        Matcher codeMatcher = FACTUAL_POLICY_CODE.matcher(answer);
+        while (codeMatcher.find()) {
+            if (codeMatcher.start() < refusalPos) { return false; }
+        }
+        Matcher listMatcher = NUMBERED_LIST_ITEM.matcher(answer);
+        while (listMatcher.find()) {
+            if (listMatcher.start() < refusalPos) { return false; }
+        }
+        return true;
+    }
```

### `ChatServiceSourcePresentationTest.java`

Added 7 new tests (total now 19):
- `isLeadingRefusalAnswer_pureOosRefusal`
- `isLeadingRefusalAnswer_oosPivotAnswer_refusalLeadsBeforeCodes`
- `isLeadingRefusalAnswer_partialInScope_codeBeforeRefusal`
- `isLeadingRefusalAnswer_numberedListBeforeRefusal`
- `isLeadingRefusalAnswer_factualAnswer_noRefusal`
- `applyAnswerAwareSourceCap_oosPivotAnswerCapsToTwo`
- `applyAnswerAwareSourceCap_playgroundDebugBypasses_evenWithLeadingRefusal`

---

## Test Results

| Command | Result |
|---------|--------|
| `.\mvnw.cmd -DskipTests compile` | **PASS** |
| `ChatServiceSourcePresentationTest` (19 tests) | **PASS** |
| `ChatServiceModelConfigTopKTest` (9 tests) | **PASS** |
| `FinalContextSelectionTest` (7 tests) | **PASS** |
| `RetrievalTopKTest` (7 tests) | **PASS** |
| **Total** | **42/42 PASS** |

---

## Runtime Results

Environment: ChatbotId `65641dcf-5bd4-426c-9839-6b7a8660c981`, chunkCount=29, backend rebuilt.

### A — OOS FxRate Production

| Metric | Value |
|--------|-------|
| responseSources | **2** (was 5 in 23G4) |
| cap | ≤2 |
| hallucinate | false |
| logCap (Leading refusal cap applied) | **true** |
| verdict | **PASS** |

### B — OOS Omega Production

| Metric | Value |
|--------|-------|
| responseSources | **2** |
| hallucinate | false |
| verdict | **PASS** |

### C — In-Scope Production

| Metric | Value |
|--------|-------|
| responseSources | 2 (≤5) |
| verdict | **PASS** |

### D — Playground Top-N

| topN | fixedVectorAnchorK | finalContexts | uiSourceCount | foundCodes | verdict |
|------|--------------------|---------------|---------------|-----------|---------|
| 1 | 30 | 1 | 1 | 0 | **PASS** |
| **6** | **30** | **6** | **6** | 6 | **PASS** (old bug still fixed) |
| 10 | 30 | 10 | 10 | 8 | **PASS** |

---

## Source Cap Behavior Summary

| Path | Condition | Cap Applied | Behavior |
|------|-----------|-------------|----------|
| Playground debug | `playgroundDebugSources=true` | No | Shows sources per TopN |
| Production — leading refusal | Refusal marker before any code | ≤2 | OOS pivot + pure OOS |
| Production — partial in-scope | Code before refusal marker | No (≤5) | Partial answers |
| Production — factual | No refusal marker | No (≤5) | Normal in-scope |

---

## Conclusion

**PASS**

- OOS FxRate `responseSources=2` ✓ (regression fixed)
- OOS Omega `responseSources=2` ✓
- In-scope ≤5 ✓
- Playground TopN=6 `uiSrc=6` ✓ (old bug remains fixed)
- No retrieval/Context Top-N/FE changes
- 42/42 unit tests PASS
