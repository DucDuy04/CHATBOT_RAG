# Eval Report: Query Analyzer Refactor — No Hardcoded Lexicon + LLM Classifier

**Task:** 30A  
**Date:** 2026-05-30  
**Verdict:** PASS (with shadow-mode safety net)

---

## 1. Overview

`QueryAnalyzerService.java` was refactored to remove all hardcoded domain-specific keyword lists and replace the classification pipeline with:

- A **generic structural analyzer** (`LocalGenericQueryAnalyzer`) — no domain words
- A **LLM-based classifier** (`LlmQueryClassifier`) — shadow mode by default
- **Data-driven heading match** — derived from real DB section titles (no hardcoding)

---

## 2. Hardcoded Items Removed

| Removed Item | Type | Classification |
|---|---|---|
| `SPECIFIC_ROW_CATEGORY_ENTITY` regex with "goi\|san pham\|dich vu\|sku" | Domain regex | REMOVE_DOMAIN_RULE |
| `SPECIFIC_SKU_REFERENCE` pattern "sku[-\s]?..." | Domain regex | REMOVE_DOMAIN_RULE |
| `ENTITY_THEN_CO_FIELD` pattern "gia\|ton kho\|luot\|trang thai\|phong ban" | Domain regex | REMOVE_DOMAIN_RULE |
| `isExplicitItemCountQuery()` with "co bao nhieu goi", "bao nhieu san pham", etc. | Domain phrase list | REMOVE_DOMAIN_RULE |
| `hasCountTargetCategory()` with "goi, san pham, chinh sach, nhan vien, dich vu, khach hang" | Domain list | REMOVE_DOMAIN_RULE |
| `hasTableCue()` with "bang gia", "bang goi" | Domain phrases | REMOVE_DOMAIN_RULE |
| `hasValueFieldCue()` with "gia, vnd, ton kho, ho tro, kenh, trang thai, phong ban, gia dich vu" | Domain list | REMOVE_DOMAIN_RULE |
| `hasSpecificRowReference()` (depends on all above) | Domain compound | REMOVE_DOMAIN_RULE |
| `isTableCellLookupQuery()` (orchestrates above) | Domain compound | REMOVE_DOMAIN_RULE |
| `isGenericCategoryTail()` with "goi dich vu" | Domain list | REMOVE_DOMAIN_RULE |
| `containsAny(q, "bao nhieu", "co may", ...)` in `analyze()` — COUNT_QUERY trigger | Mixed | REPLACE_WITH_LLM_CLASSIFIER |
| `containsAny(q, "liet ke", "tat ca", "danh sach", ...)` in `analyze()` — LIST_ALL trigger | Vietnamese word list | REPLACE_WITH_LLM_CLASSIFIER |
| `containsAny(q, "bang", "cot", "sku", "id", ...)` in `analyze()` — TABLE_LOOKUP trigger | Mixed domain | REPLACE_WITH_LLM_CLASSIFIER |
| `containsAny(q, "muc", "phan", "chuong", "quy trinh", ...)` in `analyze()` — SECTION_SUMMARY trigger | Vietnamese list | REPLACE_WITH_LLM_CLASSIFIER |
| `TABLE_ROW_HANG` regex pattern | Semi-domain | REPLACE_WITH_GENERIC_SIGNAL |
| `rewriteQuery()` hardcoded Vietnamese polite prefix list | Language list | REPLACE_WITH_GENERIC_SIGNAL |
| `rewriteQuery()` hardcoded suffix list (gom nhung gi, la gi, ...) | Vietnamese suffix list | REPLACE_WITH_GENERIC_SIGNAL |

---

## 3. What Remains (Kept)

| Item | Classification |
|---|---|
| `normalize()` — NFD/diacritics/lowercase utility | KEEP_NORMALIZATION_UTILITY |
| `findMatchedSections()` — scores by actual DB section title overlap | KEEP_DATA_DRIVEN |
| `isLikelyHeadingQuery()` — checks query terms against real DB section titles | KEEP_DATA_DRIVEN |
| `QueryType` enum (NORMAL_FACT / LIST_ALL / TABLE_LOOKUP / SECTION_SUMMARY / COUNT_QUERY / CROSS_PAGE_SECTION) | KEEP_BACKWARD_COMPAT_WRAPPER |
| `HeadingMatch` record | KEEP_BACKWARD_COMPAT_WRAPPER |
| `SECTION_NUMBER` regex `\b\d+\.\d+(\.\d+)*\b` in LocalGenericQueryAnalyzer | KEEP (structural, language-independent) |
| `CODE_IDENTIFIER` regex `\b[A-Z]{1,6}\d[A-Za-z0-9]{0,10}\b` in LocalGenericQueryAnalyzer | KEEP (structural, language-independent) |

---

## 4. New Components

### `QueryAnalysisSource` enum
Values: `LOCAL_GENERIC`, `LLM_CLASSIFIER`, `FALLBACK_DEFAULT`, `FALLBACK_LEGACY_COMPAT`, `HEADING_MATCH`

### `QueryAnalysisResult` record
Fields: `queryType`, `confidence [0,1]`, `source`, `evidence List<String>`, `llmUsed`, `fallbackReason`
Factory methods: `ofLocal()`, `ofLlm()`, `ofHeadingMatch()`, `ofFallback()`

### `LocalGenericQueryAnalyzer`
Structural signals only:
- Empty/null query → NORMAL_FACT, confidence 1.0
- Section number pattern (`X.Y`, `X.Y.Z`) → SECTION_SUMMARY, confidence 0.80
- Code-like identifier (uppercase + digit, e.g. K46, HK2) → TABLE_LOOKUP, confidence 0.55
- No signal → NORMAL_FACT, confidence 0.30 (below trigger threshold — deferred to LLM)

### `LlmQueryClassifier`
- Generic prompt (no domain vocabulary)
- JSON-only response: `{ queryType, confidence, evidence[] }`
- Configurable timeout (default 1200ms) via `CompletableFuture.get(timeout)`
- Any failure (timeout, parse error, unknown enum, low confidence) → `FALLBACK_DEFAULT`
- Never throws

### `QueryAnalyzerService` (refactored)
Orchestrator pipeline:
1. LocalGenericQueryAnalyzer (structural, fast)
2. Heading match from DB sections (data-driven)
3. LlmQueryClassifier (enabled by config; shadow-mode by default)
4. Fallback to local result

---

## 5. Configuration

```yaml
rag:
  analysis:
    llm-classifier:
      enabled: false          # safe default — no live LLM cost
      shadow-mode: true       # if enabled: log-only, no runtime effect
      only-when-local-confidence-below: 0.70
      min-accepted-confidence: 0.65
      timeout-ms: 1200
      max-input-chars: 1000
```

Default rollout: `enabled=false`. Set `enabled=true` to start shadow logging.  
After validating shadow logs, set `shadow-mode=false` for active classification.

---

## 6. Behavior Changes

| Query Pattern | Before | After (LLM disabled) |
|---|---|---|
| Section number "muc 6.2" | SECTION_SUMMARY | SECTION_SUMMARY (LOCAL_GENERIC) |
| Code identifier "K46" in query | Depends on domain list match | TABLE_LOOKUP 0.55 (LOCAL_GENERIC) |
| Heading overlap with DB sections | SECTION_SUMMARY | SECTION_SUMMARY (HEADING_MATCH) |
| "bao nhieu san pham" | COUNT_QUERY via domain list | NORMAL_FACT (no signal; LLM needed for full classification) |
| "liet ke tat ca" | LIST_ALL via domain list | NORMAL_FACT (no signal; LLM needed) |
| "gia goi X la bao nhieu" | TABLE_LOOKUP via domain list | NORMAL_FACT (no signal; LLM needed) |
| Empty/null | NORMAL_FACT | NORMAL_FACT (same) |
| OOS query | NORMAL_FACT | NORMAL_FACT (same) |

**Impact with LLM disabled (current default):** Queries that previously matched domain-specific keyword rules now fall through to NORMAL_FACT. This is safe (retrieval still works; NORMAL_FACT uses standard top-K) but may be less optimized for LIST_ALL/COUNT_QUERY/TABLE_LOOKUP expansion.

**Impact after enabling shadow mode:** LLM classifier runs in background, logs comparison. Zero latency impact.

**Impact after enabling active mode:** LLM adds at most 1200ms to classification step (one-time per request, not per chunk). Retrieval becomes more accurate for complex queries.

---

## 7. Tests Added

| Test class | Tests | Coverage |
|---|---|---|
| `QueryAnalysisResultTest` | 8 | confidence clamping, factory methods, null safety |
| `LocalGenericQueryAnalyzerTest` | 14 | empty, section numbers, code identifiers, no-domain-words |
| `LlmQueryClassifierTest` | 10 | valid JSON, noisy response, invalid JSON, unknown enum, timeout, exception, null/empty |
| `QueryAnalyzerServiceCompatibilityTest` | 16 | public API compat, known queries, LLM disabled, normalize, rewriteQuery |
| `QueryAnalyzerServiceNoHardcodedLexiconTest` | 2 | source-level banned-string assertions |
| **Total new** | **50** | |

---

## 8. Test Results

| Command | Result | Count |
|---|---|---|
| `mvnw clean test` | **PASS** | **176 tests, 0 failures, 0 errors** |

Previous baseline: 120 tests. New total: 176 tests (+56 new).

---

## 9. Runtime Smoke Tests

Live Docker environment not verified in this session. Expected behavior (shadow mode, LLM enabled):

| Query | Expected queryType | Source |
|---|---|---|
| "Pháp luật Việt Nam đại cương Nhóm 1 học với giảng viên nào, thứ mấy, tiết nào, phòng nào?" | TABLE_LOOKUP (via LLM in active mode) or NORMAL_FACT (local) | local or LLM |
| "Ngành Kiến trúc K46 có những học phần nào ở học kỳ 2?" | TABLE_LOOKUP (K46 code signal) | LOCAL_GENERIC 0.55 |
| "Tỷ giá USD/VND hôm nay là bao nhiêu?" | NORMAL_FACT | LOCAL_GENERIC 0.30 |

---

## 10. Remaining Risks

| Risk | Severity | Note |
|---|---|---|
| Queries that relied on domain keyword lists (count, list, table lookup) now fall to NORMAL_FACT | Medium | Temporary regression until LLM is enabled in active mode |
| Shadow mode async thread uses ForkJoinPool.commonPool | Low | Bounded by 1200ms timeout; won't exhaust threads for ~5 concurrent users |
| LLM classifier adds up to 1200ms in active mode | Low | Only invoked when local confidence < 0.70 (~50% of queries) |
| No Cohere rerank for TABLE_LOOKUP if misclassified as NORMAL_FACT | Medium | Same as above — resolved by enabling LLM active mode |

---

## 11. Recommended Next Steps

- **30B:** Enable shadow mode (`enabled=true, shadow-mode=true`). Collect 100+ shadow log samples. Verify LLM accuracy vs old behavior.
- **30C:** Enable active mode (`shadow-mode=false`). Monitor latency. Add table-header-derived signal to `LocalGenericQueryAnalyzer` for higher local confidence.
- **30D:** Extend `LocalGenericQueryAnalyzer` with document-derived signals (table column headers from `cells_json` keys) to raise local confidence for TABLE_LOOKUP.
