# CURSOR REPORT 23G4 — Verify Context Top-N 1..10 Source Presentation

## 1. Mức độ hiểu task

- **98%** — VERIFY ONLY, rõ ràng, không sửa code.
- Chắc chắn: cần chạy runtime matrix Top-N 1..10, verify fix 23G3, verify không regress production cap.
- Giả định: OOS FxRate expected PASS như 23G2 — thực tế phát hiện regression từ 23G3 trade-off.

## 2. Phạm vi đã làm

- Backend compile + targeted unit tests (23/23 PASS).
- Frontend lint/build PASS.
- Docker: tất cả 4 container Up, healthy.
- Runtime script `_run_23g4_verify.ps1`: tạo mới chatbot, index document (29 chunks), chạy:
  - Matrix A: Playground Top-N 1..10, all-codes question.
  - Matrix B: Eta/Theta targeted Top-N 1..10.
  - Compare C1 (3 vs 8), C2 (1 vs 10) — response manually re-verified.
  - Production source cap D1 (InScope), D2 (OOS FxRate), D3 (OOS Omega).
- Phân tích regression OOS FxRate từ 23G3 trade-off.
- Tạo eval result + cursor report.

## 3. Phạm vi không làm

- **Không sửa bất kỳ file Java/Frontend/Backend nào.**
- PDF/table regression: NOT_RUN (không có artifact).
- Full `mvn test`: NOT_RUN (env-dependent, pre-existing).
- Widget build: NOT_RUN (không cần cho verify này).

## 4. File đã đọc

| File | Mục đích | Kết luận |
|------|----------|----------|
| `docs/eval/results/RAG_TOPK_AS_FINAL_CONTEXT_TOPN_RUNTIME_VERIFY_23G2_20260519.md` | Đọc baseline 23G2 | fixedK=30, finalContexts=topN verified, OOS FxRate 2 PASS |
| `reports/refactor/CURSOR_REPORT_23G2_TOPK_AS_FINAL_CONTEXT_TOPN_RUNTIME_VERIFY.md` | Context 23G2 scope | 23G2 no code change, PASS overall |
| `reports/refactor/CURSOR_REPORT_23G3_PLAYGROUND_SOURCE_PRESENTATION_CONTEXT_TOPN.md` | Context fix 23G3 | Đã fix playgroundDebugSources bypass + isPureRefusalLikeAnswer |
| `docs/eval/results/PLAYGROUND_SOURCE_PRESENTATION_CONTEXT_TOPN_23G3_20260519.md` | Fix detail 23G3 | Path comparison, production regression table (claimed Unchanged) |
| `Backend/.../ChatService.java` | Verify logic | applyAnswerAwareSourceCap, isPureRefusalLikeAnswer, resolveSourcePresentationCap |
| `docs/eval/results/_run_23g2_runtime_verify.ps1` | Script reference pattern | Reuse logging, chatbot creation pattern |
| `docs/eval/results/_run_23g2_results.json` | 23G2 meta | ChatbotId, DocId, ChunkCount=29 |

## 5. Có sửa code không?

**Không. Zero code diff.** Chỉ tạo eval artifacts:
- `docs/eval/results/_run_23g4_verify.ps1` (new)
- `docs/eval/results/_run_23g4_results.json` (generated)
- `docs/eval/results/PLAYGROUND_CONTEXT_TOPN_1_TO_10_VERIFY_23G4_20260520.md` (new)
- `reports/refactor/CURSOR_REPORT_23G4_PLAYGROUND_CONTEXT_TOPN_1_TO_10_VERIFY.md` (this file)

## 6. Environment

| Item | Value |
|------|-------|
| OS | Windows 10 PowerShell 5.1 |
| JAVA_HOME | C:\Program Files\Java\jdk-21 |
| Docker | 4 containers Up (backend, frontend, mysql, qdrant) |
| Backend | 23G3 code, `chatbot-backend` image |
| ChatbotId | `2cb3e6c9-b78b-4433-a907-3a07407e999f` |
| DocumentId | `f6c5c510-caf7-40dd-923b-650e9124fbf1` |
| ChunkCount | 29 |
| ApiKey | `6986...d7d0` (masked) |

## 7. Build / Test

| Check | Result |
|-------|--------|
| Backend compile | PASS |
| FinalContextSelectionTest (7) | PASS |
| RetrievalTopKTest (7) | PASS |
| ChatServiceModelConfigTopKTest (9) | PASS |
| **Total targeted tests** | **23/23 PASS** |
| Frontend lint | PASS |
| Frontend build | PASS |

## 8. Playground Aggregate Matrix 1..10

All fixedVectorAnchorK=30, vectorAnchors=29, afterExpansion=66, deduped=29 — stable across all 10 runs.

| topN | finalContexts | uiSourceCount | foundCodes | verdict |
|------|---------------|---------------|-----------|---------|
| 1 | 1 | 1 | 0 | PASS |
| 2 | 2 | 2 | 0 | PASS |
| 3 | 3 | 3 | 3 | PASS |
| 4 | 4 | 4 | 4 | PASS |
| 5 | 5 | 5 | 5 | PASS |
| **6** | **6** | **6** | 6 | **PASS** |
| 7 | 7 | 7 | 7 | PASS |
| 8 | 8 | 8 | 8 | PASS |
| 9 | 9 | 9 | 8 | PASS |
| 10 | 10 | 10 | 8 | PASS |

## 9. Old Bug Top-N=6

| | Trước fix | Sau fix 23G3+23G4 verify |
|-|-----------|--------------------------|
| finalContexts | 6 | 6 |
| SOURCES sidebar | **2 (BUG)** | **6 (FIXED)** |
| verdict | FAIL | **PASS** |

## 10. Eta/Theta Matrix 1..10

| topN | finalContexts | uiSourceCount | eta | theta | verdict |
|------|---------------|---------------|-----|-------|---------|
| 1 | 1 | 1 | F | F | PASS |
| 2..10 | topN | topN | T | T | PASS |

Improvement: 23G2 topN=5 PARTIAL (Eta miss) → now topN=2 already PASS both.

## 11. Compare Smoke

| Config | topK | codes | sources |
|--------|------|-------|---------|
| A | 3 | 3 (Alpha, Zeta, Theta) | 3 |
| B | 8 | 8/8 | 8 |

B > A — PASS. Manual response verify (script had wrong field names `answerA` vs `configA.answer`).

## 12. Production / Source Cap

| Case | responseSources | cap | verdict |
|------|-----------------|-----|---------|
| InScope | 5 | ≤5 | PASS |
| OOS FxRate | 5 | ≤2 | **FAIL** |
| OOS Omega | 2 | ≤2 | PASS |

**OOS FxRate root cause:** LLM answer starts with refusal phrase then pivots to listing policy codes with colon lines (`- Alpha: ALPHA-111`…). `hasSubstantiveFactualContent()` detects colon lines (≥3) → `isPureRefusalLikeAnswer()` returns false → no cap applied → 5 sources returned. No hallucination.

**This is a trade-off regression from 23G3**, not from this verify task. Before 23G3, simple `isRefusalLikeAnswer` capped any refusal-marker answer. After 23G3, only "pure" refusal gets capped. Pivot answers escape the cap.

## 13. OOS Hallucination

| Query | Result |
|-------|--------|
| FxRate | No fake exchange rate — PASS |
| Omega | No OMEGA-* code — PASS |

## 14. PDF/Table

**NOT_RUN_NO_PDF_ARTIFACT**

## 15. Kết luận

**PARTIAL** — primary Playground source-presentation task (23G3 verify scope) = **PASS**; secondary OOS production-cap edge case = **FAIL** (pre-existing trade-off from 23G3, not introduced here).

## 16. Rủi ro còn lại

1. **OOS pivot answer source cap**: LLM trả lời OOS bằng cách pivot sang tài liệu → `hasSubstantiveFactualContent=true` → không cap về 2. Không hallucinate nhưng trả nhiều source hơn expected. Cần cân nhắc sửa `isPureRefusalLikeAnswer` để nhận diện pivot answer.
2. **Compare script field names**: eval script dùng sai field `answerA/answerB` thay vì `configA.answer/configB.answer`. Không ảnh hưởng product, chỉ cần fix script cho verify tiếp theo.
3. LLM quality tại topN=1/2 vẫn thấp (0 codes) — expected và acceptable.
4. PDF regression không thể verify vì thiếu artifact.

## 17. Bước tiếp theo

1. (Optional) Sửa `isPureRefusalLikeAnswer` để handle pivot answer OOS → hạn chế source count khi user hỏi OOS nhưng LLM pivot sang nội dung tài liệu.
2. Fix eval script field names `answerA` → `configA.answer` cho các run sau.
3. Thêm PDF artifact để test regression bảng thực thể.
