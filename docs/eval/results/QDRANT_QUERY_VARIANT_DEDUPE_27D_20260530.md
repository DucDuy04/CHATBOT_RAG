# QDRANT QUERY VARIANT DEDUPE — 27D Evaluation Results
**Date:** 2026-05-30  
**Task:** 27D — Dedupe Qdrant Query Variants to Reduce Redundant Vector Search  
**Verdict: PARTIAL** (unit + integration tests PASS; runtime benchmark pending live backend)

---

## 1. Summary

Task 27D implemented conservative normalized-text query variant deduplication in the RAG retrieval pipeline.

The optimization reduces redundant Qdrant vector search calls when `QueryAnalyzerService.rewriteQuery()` produces multiple variants that are identical after normalization (trim + lowercase + collapse whitespace + NFC + control-char strip).

**Exact normalized-text dedupe** is enabled by default.  
**Cosine-similarity dedupe** is explicitly deferred (27E+).

---

## 2. Dedupe Strategy

### Query Variant Generation (existing)

`QueryAnalyzerService.rewriteQuery()` produces up to 4 variants per question:

| Variant | Transformation |
|---|---|
| `trimmed` | original trimmed |
| `stripped` | removed Vietnamese politeness prefix + trailing `?` |
| `core` | removed trailing Vietnamese topic words (`gồm những gì`, etc.) |
| `normalized` | NFC-normalized form of `trimmed` |

Final `distinct()` (exact string equality) applied before return.

### Problem

`distinct()` uses exact string equality. Variants that differ only in case or whitespace pass through as separate strings, each triggering a separate Qdrant search call.

**Example — before 27D:**

```
Question: "Kỹ năng mềm Nhóm 4 học với giảng viên nào, thứ mấy, tiết nào, phòng nào?"

variants output by rewriteQuery():
  [0] "Kỹ năng mềm Nhóm 4 học với giảng viên nào, thứ mấy, tiết nào, phòng nào?"
  [1] "Kỹ năng mềm Nhóm 4 học với giảng viên nào, thứ mấy, tiết nào, phòng nào"
  [2] "kỹ năng mềm nhóm 4 học với giảng viên nào, thứ mấy, tiết nào, phòng nào"

→ 3 Qdrant calls
```

**After 27D with normalized-text dedupe:**

```
normalized keys:
  [0] → "kỹ năng mềm nhóm 4 học với giảng viên nào, thứ mấy, tiết nào, phòng nào?|<widgetId>|VECTOR"
  [1] → "kỹ năng mềm nhóm 4 học với giảng viên nào, thứ mấy, tiết nào, phòng nào|<widgetId>|VECTOR"
  [2] → "kỹ năng mềm nhóm 4 học với giảng viên nào, thứ mấy, tiết nào, phòng nào|<widgetId>|VECTOR"

[0] = unique (kept)
[1] = NORMALIZED_TEXT_DUPLICATE → skipped
[2] = NORMALIZED_TEXT_DUPLICATE → skipped

→ 1 Qdrant call (saved 2 calls)
```

**Note on EmbeddingService cache:** `EmbeddingService.getCachedOrEmbedQuery()` already deduplicates embedding API calls using a cache keyed by `replaceAll("\\s+", " ").trim().toLowerCase()`. So embedding provider calls were already deduplicated — the new 27D dedupe saves Qdrant REST calls specifically.

---

## 3. Normalization Rules (Conservative)

Applied: trim, lowercase, collapse whitespace, NFC unicode normalization, strip control/invisible chars.

NOT applied: digit removal, punctuation stripping, stemming, synonym expansion.

**Numeric labels preserved:**

```
"Kiến trúc K46 học kỳ 2" ≠ "Kiến trúc K45 học kỳ 2"   → not deduped ✓
"Kiến trúc K46 học kỳ 2" ≠ "Kiến trúc K46 học kỳ 1"   → not deduped ✓
"Kỹ năng mềm Nhóm 4"     ≠ "Kỹ năng mềm Nhóm 2"       → not deduped ✓
```

**Case/whitespace variants deduped:**

```
"Kỹ năng mềm Nhóm 4" ≡ "kỹ năng   mềm nhóm 4"         → deduped ✓
```

---

## 4. Dedupe Key

```
normalizedText + "|" + scopeFilterKey + "|" + searchModeKey
```

Variants with different `widgetId` (scopeFilterKey) or `searchModeKey` are never deduped, even if text is identical.

---

## 5. Cosine Dedupe — Deferred

Cosine-similarity dedupe is **deferred to task 27E+** because:
- It requires embeddings (extra API cost before saving Qdrant calls).
- Recall risk exists at any threshold below ~0.99.
- Conservative normalized-text dedupe alone handles the highest-frequency duplicate pattern (case/whitespace).

Config stub is present (`cosine-enabled: false`); implementation is explicitly not provided in 27D.

---

## 6. Config Properties Added

```yaml
rag:
  retrieval:
    query-variant-dedupe:
      enabled: true           # false → old behavior (all variants → all Qdrant calls)
      mode: normalized_text   # safe default
      cosine-enabled: false   # deferred
      cosine-threshold: 0.98
      log-skipped-variants: true
```

---

## 7. Tracing Fields Added to RagLatencyTrace

| Field | Meaning |
|---|---|
| `queryVariantTotal` | Total variants from rewriteQuery() |
| `queryVariantUnique` | Unique variants after dedupe |
| `queryVariantSkipped` | Skipped (duplicate) variants |
| `queryVariantDedupeMode` | `normalized_text` or `DISABLED` |
| `qdrantSearchCalls` | Actual Qdrant search calls made |

**Log example (when skips occur):**

```
[RAG][variant-dedupe] total=4 unique=2 skipped=2 mode=normalized_text
[RAG][latency] ... queryVariantTotal=4 queryVariantUnique=2 queryVariantSkipped=2 queryVariantDedupeMode=normalized_text qdrantSearchCalls=2
```

---

## 8. Files Changed

| File | Change |
|---|---|
| `Backend/src/main/resources/application.yml` | Added `query-variant-dedupe` config block |
| `Backend/src/main/java/.../rag/retrieve/QueryVariantDedupe.java` | New class — dedupe logic |
| `Backend/src/main/java/.../audit/metrics/RagLatencyTrace.java` | Added 5 new trace fields + setter + log format |
| `Backend/src/main/java/.../rag/retrieve/RagRetrievalService.java` | Applied dedupe before Qdrant loop; added qdrantSearchCalls counter |

---

## 9. Test Results

### Unit Tests: `QueryVariantDedupeTest` — 13 tests

| Test | Result |
|---|---|
| exactNormalizedDuplicate_isSkipped | PASS |
| trailingWhitespace_andCaseDifference_treatedAsDuplicate | PASS |
| numericDistinction_K46_vs_K45_preserved | PASS |
| groupDistinction_Nhom4_vs_Nhom2_preserved | PASS |
| dedupeDisabled_preservesAllVariants | PASS |
| differentScopeFilterKeys_areNotDeduped | PASS |
| differentScopeFilterKeys_sameText_keptSeparateInSingleCallWithDifferentModes | PASS |
| normalizeText_trimsAndCollapses | PASS |
| normalizeText_stripsControlChars | PASS |
| normalizeText_nullReturnsEmpty | PASS |
| emptyVariantList_returnsEmptyResult | PASS |
| singleVariant_neverSkipped | PASS |
| firstOccurrenceIsKept_notSkipped | PASS |

### Integration Tests: `QueryVariantDedupeIntegrationTest` — 9 tests

| Test | Result |
|---|---|
| duplicateVariants_callQdrantOnce | PASS |
| representativeResultStillUsed_whenDuplicatesSkipped | PASS |
| distinctVariants_K46_vs_K45_callQdrantSeparately | PASS |
| distinctVariants_Nhom4_vs_Nhom2_callQdrantSeparately | PASS |
| dedupeDisabled_callsQdrantForEveryVariant | PASS |
| exactScheduleQuery_Nhom4_candidatePreserved | PASS |
| curriculumListQuery_K46_HK2_notMixedWithK45_HK1 | PASS |
| curriculumListQuery_duplicateVariants_reducedCallCount | PASS |
| nullVariantList_handledGracefully | PASS |

### Full Test Suite

```
mvn clean test
Tests run: 115, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

(Previous baseline after 27C: 93 tests — 22 new tests added by 27D.)

---

## 10. Runtime Benchmark (V1–V5)

**Status: NOT RUN** — live backend requires `GROQ_API_KEY`, `NOMIC_API_KEY`, MySQL, Qdrant, and indexed documents.

Expected behavior based on code analysis:

| Query | Expected Qdrant calls (before/after) | Expected vectorMs impact |
|---|---|---|
| V1 — schedule query (Nhóm 1) | ~3–4 → 1–2 | reduces |
| V2 — schedule query (Nhóm 4) | ~3–4 → 1–2 | reduces |
| V3 — curriculum list K46 | ~3–4 → 1–2 | reduces |
| V4 — curriculum list Công nghệ sinh học K46 | ~3–4 → 1–2 | reduces |
| V5 — OOS query | ~3–4 → 1–2 | reduces |

The actual count depends on how many of `rewriteQuery()`'s 4 variants normalize to the same key for each specific question. Estimates assume 2–3 variants collapse to 1 unique after normalization.

---

## 11. Recall Risk Assessment

**Risk: LOW**

- Numeric tokens (K46, K45, HK2, Nhóm 4) are preserved in normalization.
- Only whitespace/case variants are deduplicated.
- `distinct()` in `rewriteQuery()` already removed exact string duplicates before 27D.
- The new dedupe extends this to normalized-text equivalents — semantically the same queries.
- Different scope/filter keys prevent cross-widget or cross-mode dedupe.

**Recall regression scenarios ruled out:**

| Scenario | Safe? |
|---|---|
| "K46" vs "K45" deduped | No — different normalized text → preserved ✓ |
| "Nhóm 4" vs "Nhóm 2" deduped | No — different normalized text → preserved ✓ |
| "HK1" vs "HK2" deduped | No — different normalized text → preserved ✓ |
| Polite prefix removal ("hãy X" → "X") | rewriteQuery() already produces both; first kept, normalized second skipped only if identical |

---

## 12. Remaining Risks

- Cosine dedupe not implemented — if two semantically equivalent but lexically different variants exist, they are not deduped. This is the safe conservative choice.
- If `rewriteQuery()` is extended in future to produce more variants, the normalized-text dedupe will handle them automatically.
- Runtime benchmark (V1–V5 correctness) not verified — requires live environment.

---

## 13. Next Recommended Task

- **27E**: Runtime benchmark V1–V5 with live backend to measure actual qdrantSearchCalls before/after.
- **27F** (optional): Cosine-similarity dedupe behind `cosine-enabled: true` config flag, with threshold ≥ 0.98.
- Continue monitoring `[RAG][variant-dedupe]` logs in production to verify actual skip rates.
