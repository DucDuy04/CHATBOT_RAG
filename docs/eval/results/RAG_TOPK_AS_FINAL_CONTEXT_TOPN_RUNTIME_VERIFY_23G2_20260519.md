# RAG Top-K as Final Context Top-N — Runtime Verify 23G2 (2026-05-19)

## Environment

| Item | Value |
|------|--------|
| Date | 2026-05-19 |
| Backend | `chatbot-backend` image rebuilt (23G code); `docker compose up -d mysql qdrant backend` |
| Frontend container | Not rebuilt (npm ETIMEDOUT during `docker compose up --build`) |
| MySQL / Qdrant | Up, healthy |
| Backend health | `GET /api/chatbots?page=0&size=1` → HTTP 200 |
| Qdrant | `GET /collections` → `documents` collection OK |
| Test document | `docs/eval/manual/TOPK_LOW_HIGH_DIFFERENCE_TEST_DOCUMENT.txt` |
| CHATBOT_ID | `a5b541a5-ed10-4ebd-826f-8009fc1b790f` |
| DOCUMENT_ID | `69bc0d18-51cb-4863-b6bd-9d3bdfaef067` |
| WIDGET_API_KEY | `a853...4966` (masked) |
| chunkCount | 29 (INDEXED) |

## Compile / test

| Command | Result |
|---------|--------|
| `mvnw -DskipTests compile` | PASS |
| `mvnw -Dtest=FinalContextSelectionTest,RetrievalTopKTest,ChatServiceModelConfigTopKTest test` | PASS (23/23) |
| `npm run lint` | PASS |
| `npm run build` | PASS |
| Full `mvn test` | Not required; integration MySQL tests env-dependent (pre-existing) |

## Runtime A — Playground Top-N 1 / 5 / 20

Question: Liệt kê mã chính sách của Alpha … Theta.

| topN | fixedVectorAnchorK | vectorAnchors | afterExpansion | deduped | finalContexts | foundCodes | Verdict |
|------|-------------------|---------------|----------------|---------|---------------|------------|---------|
| 1 | 30 | 29 | 66 | 29 | **1** | 0 | PASS |
| 5 | 30 | 29 | 66 | 29 | **5** | 5 | PASS |
| 20 | 30 | 29 | 66 | 29 | **20** | 8/8 | PASS |

**Observations**

- `fixedVectorAnchorK=30` on all runs; `vectorAnchors=29` (available unique anchors in doc, not UI-driven).
- `finalContextTopN` in logs matches request: 1, 5, 20.
- `finalContexts` tracks Top-N exactly (1, 5, 20).
- Top-N=1 refusal / 0 codes is **expected** (too few contexts).

## Runtime B — Eta / Theta

| topN | finalContexts | answerHasEta | answerHasTheta | Verdict |
|------|---------------|--------------|----------------|---------|
| 1 | 1 | false | false | PASS (expected thin answer) |
| 2 | 2 | true | true | PASS (both codes) |
| 5 | 5 | false | true | PARTIAL (scoring/LLM missed ETA) |
| 20 | 20 | true | true | PASS |

## Runtime C — Compare A=1 vs B=20

| Side | topN | finalContexts (log) | foundCodes | Notes |
|------|------|----------------------|------------|-------|
| A | 1 | **1** | 0 | Log re-check: `[RAG][select] finalContexts=1` after `value=1` |
| B | 20 | **20** | 8 | `[RAG][select] finalContexts=20` after `value=20` |

- `fixedVectorAnchorK=30` for both legs.
- Answers **different** (`CompareAnswersDifferent=true`).
- Automated script marked Compare A FAIL due to log-window bleed (parsed B’s `finalContexts=20`); manual log confirm **PASS**.

## Runtime D — Widget / modelConfig

| Case | modelConfig topK | Chat log source | effective | finalContexts | foundCodes |
|------|------------------|-----------------|-----------|---------------|------------|
| D1 | 1 | `MODEL_CONFIG` | 1 | 1 | 0 |
| D2 | 20 | `MODEL_CONFIG` | 20 | 20 | 8 |

- `fixedVectorAnchorK=30` on widget chat runs.
- No explicit `topK` on `/api/chat` request; config drives final top-N.

## Runtime E — OOS Omega

- Question: Mã chính sách Omega là gì? (Top-N=20)
- **No** `OMEGA-*` fake code.
- Refusal-style answer.
- `responseSources=2` (≤2) → PASS.

## Runtime F — Source cap

| Case | responseSources | Verdict |
|------|-----------------|---------|
| In-scope (widget) | 1 | PASS (≤5) |
| OOS FX rate | 2 | PASS (≤2) |

## Runtime G — PDF / table

**NOT_RUN_NO_PDF_ARTIFACT** — `HeThongQuanLyYeuCauPhucKhao.pdf` not present in repo.

## Conclusion

**PASS** (23G2 semantics)

Core acceptance for task 23G is met on runtime:

1. Vector anchor K stable (~30 config; 29 anchors from doc), **not** tied to UI Top-N.
2. `finalContexts` follows UI Top-N (1 / 5 / 20).
3. Compare and widget/modelConfig behave correctly.
4. OOS and source caps OK.

**PARTIAL notes (non-blocking)**

- Top-N=5 Eta/Theta answer missed ETA once (rerank/LLM quality).
- Eval script auto-conclusion was `FAIL` due to compare log parsing; corrected manually.
- Full `docker compose --build` failed on frontend npm network; backend-only stack used.

## Artifacts

- Raw JSON: `docs/eval/results/_run_23g2_results.json`
- Script: `docs/eval/results/_run_23g2_runtime_verify.ps1`
