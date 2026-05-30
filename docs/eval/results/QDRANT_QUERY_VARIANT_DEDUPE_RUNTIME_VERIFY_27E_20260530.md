# QDRANT QUERY VARIANT DEDUPE — Runtime Verify 27E

**Date:** 2026-05-30  
**Task:** 27E — Runtime benchmark + verification of 27D query variant dedupe  
**Final verdict:** **PARTIAL**  
**27D upgrade:** **PARTIAL → stays PARTIAL** (not upgraded to PASS)

---

## 1. Executive summary

Runtime Docker benchmark V1–V5 completed with dedupe **enabled** and **disabled**.

Trace fields (`queryVariantTotal`, `queryVariantUnique`, `queryVariantSkipped`, `queryVariantDedupeMode`, `qdrantSearchCalls`) are present in `[RAG][latency]` logs.

**Finding:** For all five benchmark queries, `rewriteQuery()` produced **3 variants** that remained **3 unique** after normalized-text dedupe (`queryVariantSkipped=0`). Enabled and disabled runs both issued **3 Qdrant search calls** per query — **no measurable latency benefit** from dedupe on this workload.

Dedupe wiring is correct and safe; impact is **negligible** for current V1–V5 queries because variants differ by trailing `?` and by NFC-normalized form, so they do not collapse under conservative normalized-text keys.

**Recommendation:** Keep dedupe **enabled** (no regression vs disabled). Do **not** disable. Future tuning: align `rewriteQuery()` normalization with dedupe keys, or defer to cosine dedupe (out of 27E scope).

---

## 2. Code changed

| Item | Changed? |
|---|---|
| Production Java / config | **No** |
| `scripts/benchmark/latency_bench_27e.ps1` | **Yes** — new runtime benchmark script |
| Temporary `docker-compose.bench-27e-disabled.yml` | Created for disabled phase, **removed** after benchmark |

---

## 3. Dedupe config status (after task)

```yaml
rag.retrieval.query-variant-dedupe.enabled: true
rag.retrieval.query-variant-dedupe.mode: normalized_text
rag.retrieval.query-variant-dedupe.cosine-enabled: false
```

Verified after restore: smoke query log shows `queryVariantDedupeMode=normalized_text`.

Disabled phase used env override `RAG_RETRIEVAL_QUERY_VARIANT_DEDUPE_ENABLED=false` via temporary compose overlay; backend restarted without overlay afterward.

---

## 4. Benchmark environment

| Item | Value |
|---|---|
| Stack | `docker compose` — mysql, qdrant, backend |
| Spring profile | `docker` |
| Widget ID | `3a26c18c-bfd1-477e-af79-fcd7fba551a9` |
| Widget API key | `c2e09246-1525-44a5-adb9-dfbce7191c8d` |
| Document ID | `7ae6d0b9-b7de-4bc8-8be6-10798add73aa` |
| Document | `SoTayHocVu_HocKy1_2025-2026_RAG_CONTEXT_HOC_KY.docx` |
| Active completed docs | 3 |
| DB chunks (total) | 120594 |
| Qdrant points | 9888 |
| Keyword prewarm | `[KeywordIndexPrewarm] finished ok=3 failed=0` before each benchmark phase |
| Protocol | 1 warm-up + 5 measured runs per question |

Raw JSON:

- `docs/eval/results/QDRANT_QUERY_VARIANT_DEDUPE_BENCH_enabled_20260530-162914.json`
- `docs/eval/results/QDRANT_QUERY_VARIANT_DEDUPE_BENCH_disabled_20260530-163827.json`

---

## 5. Trace field verification

After first measured query (enabled):

```text
queryVariantTotal=3 queryVariantUnique=3 queryVariantSkipped=0
queryVariantDedupeMode=normalized_text qdrantSearchCalls=3
```

After disabled phase smoke:

```text
queryVariantDedupeMode=DISABLED qdrantSearchCalls=3
```

`[RAG][variant-dedupe]` INFO line **not emitted** for V1–V5 because `RagRetrievalService` logs it only when `skipped > 0` (by design).

Example latency line (V1 enabled, run 1):

```text
[RAG][latency] ... queryVariantTotal=3 queryVariantUnique=3 queryVariantSkipped=0
queryVariantDedupeMode=normalized_text qdrantSearchCalls=3 totalMs=13999 vectorMs=804 retrievalMs=9116
```

---

## 6. Comparison table (p50)

| Question | enabled p50 totalMs | disabled p50 totalMs | enabled p50 qdrantCalls | disabled p50 qdrantCalls | enabled p50 vectorMs | disabled p50 vectorMs | variantTotal | variantUnique | variantSkipped |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| V1 schedule | 12565 | 11865 | 3 | 3 | 796 | 547 | 3 | 3 | 0 |
| V2 schedule | 7134 | 6453 | 3 | 3 | 559 | 483 | 3 | 3 | 0 |
| V3 Kiến trúc K46 HK2 | 12705 | 10297 | 3 | 3 | 549 | 553 | 3 | 3 | 0 |
| V4 CNSH K46 HK2 | 7813 | 7449 | 3 | 3 | 477 | 474 | 3 | 3 | 0 |
| V5 OOS | 5867 | 5947 | 3 | 3 | 533 | 558 | 3 | 3 | 0 |

### Aggregate summary

| Metric | Value |
|---|---|
| Avg qdrantSearchCalls saved (enabled vs disabled) | **0** |
| Avg vectorMs saved | **~0** (within run-to-run noise) |
| Avg totalMs saved | **~0** (disabled slightly faster on some queries — not dedupe-related) |
| Queries with duplicate variants skipped | **0 / 5** |
| Queries with dedupe-specific correctness regression | **0** |

---

## 7. Correctness audit (manual, from answers + sources)

| Query | Verdict | Notes |
|---|---|---|
| **V1** | **PASS** | Answer: Vân Anh, thứ 2, tiết 1-2, E301. Source: `LUA1012`, Nhóm 1 row. |
| **V2** | **FAIL** | All 5 runs (enabled **and** disabled): "Tôi không tìm thấy…". Source wrong row (`MTK4102` K48), not `KNM1013` Nhóm 4. **Same failure both phases → not dedupe regression.** |
| **V3** | **FAIL** | All runs: "Tôi không tìm thấy…". No Kiến trúc K46 HK2 list. **Same both phases.** |
| **V4** | **PARTIAL** | Answer cites CNS4352, CNSH K46 HK2 — correct scope, incomplete list (topK/context limits). Source: CNS4352 HK2 row, no K45/HK1 leak. |
| **V5** | **PASS** | Refuses live exchange rate; no fabricated USD/VND number. |

### Recall risk assessment

- **Dedupe risk:** **Low** — zero variants skipped; enabled ≡ disabled for Qdrant call count and V2/V3 failure pattern.
- **Pre-existing retrieval risk:** **Medium** for V2/V3 table/list queries under default widget topK (unrelated to 27D).

---

## 8. Why no dedupe on V1–V5

`QueryAnalyzerService.rewriteQuery()` emits up to 3–4 strings per question:

1. Trimmed original (often with `?`)
2. Stripped politeness / trailing `?`
3. Optional core strip
4. NFC-normalized lowercase form

For benchmark queries, **3 variants** survive `distinct()`. After `QueryVariantDedupe.normalizeText()`:

- Variant with `?` ≠ variant without `?` (punctuation preserved)
- NFC-normalized third variant differs from stripped form (unicode decomposition in analyzer `normalize()`)

Therefore `queryVariantUnique=3`, `queryVariantSkipped=0`, `qdrantSearchCalls=3` even with dedupe enabled.

Unit tests in 27D used **artificial** duplicate strings; live `rewriteQuery()` output for V1–V5 does not produce normalized-text duplicates.

---

## 9. Log summary

```powershell
docker compose logs backend --tail=1000 | Select-String "variant-dedupe|queryVariant"
```

- Many `[RAG][latency]` lines with `queryVariantTotal=3 queryVariantUnique=3 queryVariantSkipped=0`.
- **No** `[RAG][variant-dedupe] total=… skipped=…` lines during V1–V5 (skipped always 0).
- `[RAG] Query variants:` shows 3 strings per question (docker log encoding mojibake for Vietnamese; runtime UTF-8 OK in API responses).

---

## 10. Interpretation vs acceptance criteria

| Criterion | Result |
|---|---|
| V1–V5 enabled benchmark | ✅ Done |
| V1–V5 disabled benchmark | ✅ Done |
| Trace fields present | ✅ |
| qdrantSearchCalls before/after | ✅ Measured — **no difference** |
| Correctness V1–V5 all PASS | ❌ V2/V3 FAIL (pre-existing, not dedupe) |
| Dedupe restored enabled | ✅ |
| Report created | ✅ |

**27D upgrade decision:** **PARTIAL** (unchanged)

- Cannot upgrade to **PASS**: no qdrantSearchCalls reduction on live workload; V2/V3 correctness not PASS.
- Not **FAIL** for dedupe: no skipped variants, no enabled-vs-disabled regression on Qdrant calls or V2/V3 behavior.

---

## 11. Recommendation

1. **Keep** `query-variant-dedupe.enabled=true` — safe, zero observed harm.
2. **Do not** implement cosine dedupe in this task.
3. **Next optimization candidates:** (a) reduce redundant variants in `rewriteQuery()` before dedupe; (b) investigate V2/V3 retrieval separately (keyword index / topK / table row matching).
4. Re-run dedupe benchmark on queries that **artificially** produce normalized duplicates, or after rewriteQuery alignment, to measure savings.

---

## 12. Verification commands

| Command | Result |
|---|---|
| `docker compose config -q` | PASS |
| `docker compose up -d mysql qdrant backend` | PASS |
| Keyword prewarm finished | PASS (`ok=3 failed=0`) |
| Backend compile (`mvnw -DskipTests compile`) | PASS |
| Backend test (`mvnw test`) | NOT RUN — no production code changed; 27D reported 115 tests PASS |
