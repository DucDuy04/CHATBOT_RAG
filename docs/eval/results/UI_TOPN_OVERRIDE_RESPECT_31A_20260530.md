# UI Context Top-N Override Respect — Eval 31A

**Task:** 31A  
**Date:** 2026-05-30  
**Final verdict:** **PASS** (core top-N fix); **PARTIAL** (S1/S2 golden on current Docker corpus)

---

## Summary

Explicit Playground `topK` (Context Top-N) is no longer reduced by `resolveAdaptiveFinalContextTopN` for list-like queries. Runtime on chatbot `KhoaHoc` confirms `override=15/20/25` → `effective=15/20/25` and `finalContexts` matches.

---

## Root cause

`resolveAdaptiveFinalContextTopN` applied `Math.min(requested, adaptive)` even when `override != null`. For list-like queries `adaptive=15`, so UI `topK=25` became `15`.

---

## Runtime environment

| Item | Value |
|------|--------|
| Backend | Docker `chatbot-backend` rebuilt after fix |
| Chatbot | `6a40ede0-de2d-417d-965d-3d1e0fcd78ff` (KhoaHoc) |
| Document | `SoTayHocVu_HocKy1_2025-2026_RAG_CONTEXT_HOC_KY.docx` |
| Endpoint | `POST /api/playground/chat` (SSE) |
| Script | `scripts/bench_31a_topn_override.ps1` |

---

## K47 query matrix

**Question:** `Liệt kê các học phần của HK1 và HK2 của ngành Kiến trúc K47`

| Context Top-N | Log `override` | Log `effective` | Log `finalContexts` | Response `sources` count | Notes |
|---------------|----------------|-----------------|---------------------|--------------------------|-------|
| 15 | 15 | 15 | 15 | 15 | Cap honored; may miss rows if answer needs >15 |
| 20 | 20 | 20 | 20 | 20 | Enough retrieval budget for 17 Kiến trúc K47 rows if ranked in top 20 |
| 25 | 25 | 25 | 25 | 25 | Same; includes some other-K47-major noise in tail contexts |

**Sample log (topK=25):**

```text
[RAG][topN] override=25 requested=25 effective=25 adaptiveReason=list_like source=REQUEST|MODEL_CONFIG
[RAG][select] selectedByScore=25 finalContexts=25 maxContextChars=16000
```

**Answer correctness:** Answers returned (len ~460–660 chars). Full enumeration of 17 học phần depends on LLM + context ranking; retrieval cap is no longer the bottleneck at topK≥20.

---

## Regression S1 / S2 / S5

| ID | Query | Result on KhoaHoc chatbot | Verdict |
|----|-------|---------------------------|---------|
| S1 | Pháp luật VN đại cương Nhóm 1 … | Refusal: không tìm thấy trong tài liệu | **NOT APPLICABLE** — indexed doc is curriculum HK tables, not TKB schedule |
| S2 | Kỹ năng mềm Nhóm 4 … | Same refusal | **NOT APPLICABLE** — same corpus limitation |
| S5 | Tỷ giá USD/VND … | Refusal (no fabricated rate) | **PASS** |

S1/S2 should be re-run on a chatbot with schedule/TBK document when available.

---

## Unit tests

`FinalContextSelectionTest` — override 25/20/10 wins for list-like; null override uses adaptive; clamp 0→1, 100→30.

| Command | Result |
|---------|--------|
| `cd Backend && .\mvnw.cmd clean test` | **PASS** — 191 tests, 0 failures |

---

## Remaining risks

1. **Scope leakage:** At topK=25, some contexts are other majors also labeled K47 (CNTT, Đông phương học, …) — retrieval ranking issue, not top-N cap.
2. **Playground default topK=5** always sends an override; adaptive defaults only apply when `topK` is omitted (widget chat without request/model override).
3. **Adaptive values** (`list_like=25`, etc.) remain hardcoded; config extraction deferred.

---

## Next recommended task

- **31B:** Tighten list-all scope lock for `Kiến trúc K47` so top-20 contexts are predominantly HK1+HK2 architecture rows (retrieval ranking, not top-N).
