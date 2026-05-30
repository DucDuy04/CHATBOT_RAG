# Hybrid Search v1 — Runtime Verify 23I2

**Date:** 2026-05-23  
**Task:** 23I2 — Runtime Verify Hybrid Search v1  
**Type:** VERIFY ONLY (no Java/Frontend/logic changes)  
**Verdict:** **PASS**

---

## Environment

| Component | Status | Notes |
|-----------|--------|-------|
| `docker compose` backend + mysql + qdrant | UP | Backend rebuilt with 23I hybrid code |
| Backend API | `http://localhost:8080` | Health `GET /api/chatbots?page=0&size=1` → 200 |
| Qdrant | `http://localhost:6333` | Collection `documents`, **951 points** |
| Model | `meta-llama/llama-4-scout-17b-16e-instruct` (Groq) | temperature=0.2, maxTokens=768 |
| Playground endpoint | `POST /api/playground/chat` (SSE) | `playgroundDebugSources=true` |

### Chatbot / document

| Field | Value |
|-------|-------|
| CHATBOT_ID | `7fc5a049-a1ab-49e2-a106-0bd322c3aab7` |
| DOCUMENT_ID | `e93f04d4-5ba4-4d90-8ccb-41154bedc4d2` |
| Document | `SoTayHocVu-HocKy1-NamHoc20252026.pdf` |
| Status | **INDEXED** |
| chunkCount (MySQL) | **784** |
| Qdrant points (collection total) | **951** |

---

## Build / test results

| Command | Result | Notes |
|---------|--------|-------|
| `docker compose up --build -d backend` | **PASS** | Rebuilt JAR with hybrid code |
| `docker compose config -q` | **PASS** | |
| `mvn -DskipTests compile` (Maven Docker) | **PASS** | Local JAVA_HOME unset; used `maven:3.9.6-eclipse-temurin-21` container |
| Targeted tests (55 tests) | **PASS** | HybridKeywordSearchTest 7/7, FinalContextSelectionTest 7/7, RetrievalTopKTest 7/7, ChatServiceModelConfigTopKTest 9/9, ChatServiceSourcePresentationTest 19/19, RagTokenAuditTest 6/6 |
| Frontend lint/build | **NOT RUN** | Out of scope |
| Widget build | **NOT RUN** | Out of scope |

---

## Hybrid OFF vs ON setup

| Mode | How toggled | Restart required |
|------|-------------|------------------|
| **Hybrid ON** (default) | `rag.retrieval.hybrid.enabled=true` in `application.yml` baked into image | Rebuilt via `docker compose up --build -d backend` |
| **Hybrid OFF** | Runtime env `RAG_RETRIEVAL_HYBRID_ENABLED=false` on standalone `docker run` (same image) | Yes — container recreated |

No source code modified for toggle. After verify, backend restored via `docker compose up -d backend` (hybrid ON default).

---

## Runtime verification checklist

| # | Question | Result (Hybrid ON) |
|---|----------|-------------------|
| 1 | Hybrid branch runs at runtime? | **YES** — `[RAG][hybrid] strategy=VECTOR+KEYWORD` on every query |
| 2 | `[RAG][hybrid]` logs appear? | **YES** |
| 3 | Keyword candidates created? | **YES** — `keywordCandidates=30` for signal-rich queries |
| 4 | Merge/dedupe works? | **YES** — e.g. Q2 `mergedCandidates=795 dedupedCandidates=777` |
| 5 | `source=BOTH` chunks? | **YES** — e.g. Q8 top-5 all `source=BOTH` |
| 6 | Rerank/scoring after merge? | **YES** — `[RAG][hybrid-top] ... vScore=... kScore=... rScore=...` |
| 7 | Prompt budget caps context? | **YES** — max `contextChars=17886` ≤ hard max 18000; `budgetLimited=true` when topN=15 |
| 8 | Q1–Q6 improved? | **5 PASS + 1 PARTIAL** — see matrix |
| 9 | Hybrid OFF vs ON different? | **YES** — Q2/Q4/Q5 clearly worse OFF |
| 10 | Q7–Q10 regress? | **NO** |
| 11 | OOS correct? | **YES** — Q13/Q14 refuse |
| 12 | Token overload? | **NO** — estimated input ~2.5k–6.3k, maxTokens=768 |

---

## Runtime matrix Q1–Q14

| id | question (short) | topN | hybridOff answer (summary) | hybridOn answer (summary) | keywordCandidates | bothSourceCount | finalContexts | contextChars | budgetLimited | verdict |
|----|------------------|------|----------------------------|---------------------------|-------------------|-----------------|---------------|--------------|---------------|---------|
| Q1 | Tết Nguyên Đán | 10 | 09/02/2026–01/03/2026 | 09/02/2026–01/03/2026 | 30 | 30 | 10 | 14468 | false | **PASS** |
| Q2 | Khóa 48 quân sự | 15 | Wrong dates (13/10/2025…) | **30/03/2026–26/04/2026** | 30 | 30 | 10 | 16672 | true | **PASS** (ON); OFF **FAIL** |
| Q3 | KNM Nhóm 2 | 15 | Nhóm 2, Lê Bình Phương Luân, H210 | Nhóm 2, Lê Bình Phương Luân, H210, tiết 5 | 30 | 30 | 8 | 16413 | true | **PASS** |
| Q4 | KNM Nhóm 4 | 15 | **Wrong**: Hà Trần Thuỳ Dương, H209 | **Nguyễn Thị Thanh Nhàn**, thứ 6, B301 | 30 | 30 | 8 | 16313 | true | **PASS** (ON); OFF **FAIL** |
| Q5 | So sánh Nhóm 1 & 2 | 15 | Nhóm 2 missing in compare | Both groups + GV + phòng | 30 | 30 | 9 | 17886 | true | **PASS** (ON); OFF **PARTIAL** |
| Q6 | K45-K48 đăng ký | 10 | 30/06/2025–06/07/2025 (no 10h00) | Same dates, no 10h00 | 30 | 30 | 10 | 14723 | false | **PARTIAL** |
| Q7 | KTR3185 K45 | 10 | Đồ án KTCTTH, 5 TC | Same | 30 | 30 | 7 | 13739 | true | **PASS** (no regress) |
| Q8 | KTR3103 K46 | 10 | Quy hoạch XDDDĐT, 3 TC | Same | 30 | 30 | 8 | 17001 | true | **PASS** (no regress) |
| Q9 | Thi HK1 2025-2026 | 10 | 29/12/2025–17/01/2026 | Same | 30 | 30 | 10 | 14850 | false | **PASS** (no regress) |
| Q10 | Vắng thi | 10 | Coi như dự thi, điểm 0 | Same | 30 | 30 | 10 | 16498 | false | **PASS** (no regress) |
| Q11 | KTR K46 HK2 | 15 | (not re-run OFF) | HK2 course list (7 môn) | 30 | 30 | 8 | 16554 | true | **PASS** |
| Q12 | CNS K46 HK2 | 15 | (not re-run OFF) | HK2 course list (8+ môn) | 30 | 30 | 11 | 17673 | true | **PASS** |
| Q13 | ABC9999 OOS | 5 | Refusal | Refusal | 30 | 30 | 5 | 4059 | false | **PASS** |
| Q14 | USD/VND OOS | 5 | Refusal | Refusal | 30 | 30 | 5 | 6854 | false | **PASS** |

### Q1–Q6 improvement count (Hybrid ON vs prior fail/partial baseline)

| Case | Baseline (23I partial) | Hybrid ON | Improved? |
|------|------------------------|-----------|-----------|
| Q1 | partial/fail dates | PASS | Yes |
| Q2 | fail wrong dates | PASS | **Yes** |
| Q3 | wrong group | PASS (correct Nhóm 2) | **Yes** |
| Q4 | wrong group | PASS (correct Nhóm 4) | **Yes** |
| Q5 | missing one group | PASS both groups | **Yes** |
| Q6 | partial (time) | PARTIAL (still no 10h00) | Partial |

**Score: 5 PASS + 1 PARTIAL → meets ≥3/6 improvement threshold.**

---

## Logs summary (Hybrid ON — representative)

### Q2 (date + Khóa signal)
```
[RAG][hybrid] keywordSignals identifiers=0 dates=0 numbers=3 labels=0 ngrams=73 corpusScanned=784 keywordCandidates=30
[RAG][hybrid] strategy=VECTOR+KEYWORD vectorCandidates=43 keywordCandidates=30
[RAG][hybrid] mergedCandidates=795 dedupedCandidates=777 bothSourceCount=30
[RAG][hybrid-top] rank=1 source=BOTH chunkType=text vScore=1.000 kScore=1.000 rScore=0.9581
[RAG][budget] requestedTopN=15 selectedContexts=10 contextChars=16672 budgetLimited=true
[RAG][token-usage] contextTopN=15 finalContexts=10 estimatedInputTokens=5804 maxTokens=768
```

### Q4 (label Nhóm 4)
```
[RAG][hybrid] keywordSignals identifiers=0 dates=0 numbers=1 labels=1 ngrams=54 ...
[RAG][hybrid-top] rank=1 source=KEYWORD ... (group row surfaced)
```

### Hybrid OFF (Q2) — no hybrid logs
```
(no [RAG][hybrid] lines)
[RAG][budget] requestedTopN=15 selectedContexts=12 contextChars=17816 budgetLimited=true
```

---

## Keyword / merge / budget evidence

- **Keyword branch:** `corpusScanned=784`, `keywordCandidates=30` consistently when hybrid ON.
- **Signals:** Q3/Q4 `labels=1`; Q6 `identifiers=1` (K45-K48); Q8 `identifiers=1` (KTR3103).
- **Merge/dedupe:** `mergedCandidates` >> `dedupedCandidates` on table-heavy queries (Q2: 795→777).
- **BOTH source:** Q8 hybrid-top ranks 1–5 all `source=BOTH`.
- **Prompt budget:** All runs `contextChars ≤ 17886 < 18000`. `budgetLimited=true` trims selection when topN=15 (e.g. requested 15 → selected 8–10 contexts).

---

## OOS result

| Case | Hybrid ON | Hallucination? |
|------|-----------|----------------|
| Q13 ABC9999 | "Tôi không tìm thấy thông tin này trong tài liệu." | No |
| Q14 USD/VND | Same refusal | No |

---

## Regression result (Q7–Q10)

All four cases **PASS** on Hybrid ON with same factual content as Hybrid OFF. No regression observed.

---

## Token / cost notes

- Playground SSE: `embeddingCalls=2–3`, `rerankCalls=0` (Cohere rerank enabled in env but 0 calls logged).
- `estimatedInputTokens` range ~2573–6257; `maxTokens=768`; no rate-limit fallback observed in matrix.
- Duplicate answer text in some SSE responses (LLM stream artifact) — cosmetic only.

---

## Conclusion

**PASS** — Hybrid Search v1 runs correctly in production-like Docker stack:

- Hybrid branch active with full log trail
- Keyword retrieval improves Q2, Q4, Q5 vs vector-only (OFF)
- Merge/dedupe/BOTH/rerank confirmed
- Prompt budget enforced (≤18k chars)
- Regression and OOS checks pass
- Unit tests 55/55 PASS

### Remaining gaps (non-blocking)

1. Q6 still missing **10h00** in answer (both modes).
2. Q3/Q5 LLM occasionally confuses **thứ** with date field in table rows.
3. Q13 still retrieves keyword candidates for fake code ABC9999 but LLM correctly refuses.

### Raw artifacts

- `docs/eval/results/_23i2_hybrid_on_raw.json`
- `docs/eval/results/_23i2_hybrid_off_raw.json`
- `docs/eval/results/_23i2_run_log.txt`
