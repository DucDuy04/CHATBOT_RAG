# CURSOR_REPORT 23D2 — Playground Model Controls Runtime Verify

## 1. Mức độ hiểu task

- **100%** — VERIFY ONLY, không sửa code; xác nhận runtime/UI cho các fix 23D.

## 2. Tóm tắt yêu cầu

Chạy verification thật: Playground topK/temperature, Model Settings topK/maxTokens, Compare sidebar layout, regression production cap ≤5 / OOS ≤2.

## 3. Phạm vi đã làm

- Start stack (backend/mysql/qdrant; frontend via Vite dev do Docker FE build fail).
- API tests: playground SSE, model config PUT/GET, `/api/chat`, compare, OOS.
- UI test: Playwright compare sidebar hide/restore.
- Tạo result + report files.

## 4. Phạm vi không làm

- Không sửa Java/Frontend/Backend/RAG/parser/config/schema.
- Không commit `docs/eval/results/node_modules` (local playwright install for verify only).

## 5. File đã đọc

| File | Mục đích |
|------|----------|
| `PLAYGROUND_MODEL_CONTROLS_PARITY_23D_20260516.md` | Baseline fix summary |
| `CURSOR_REPORT_23D_PLAYGROUND_MODEL_CONTROLS_PARITY.md` | Expected behavior |
| `PlaygroundPage.jsx` | Sidebar `!compareMode` |
| `PlaygroundController.java` | `playgroundDebugSources=true` |

## 6. Environment

- Backend: Docker, port 8080, started ~2026-05-16T16:37Z.
- Frontend: Vite `localhost:5173` (compose frontend build failed).
- PDF indexed on chatbot `6cc17762-3971-45b5-9a09-ebeb7367f96d`.

## 7. Playground Top-K runtime

| topK | sourceCount | Log cap | Pass |
|------|-------------|---------|------|
| 3 | 2 | `cap=3`, effective=3 | YES |
| 10 | 10 | `cap=10`, effective=10 | YES |

## 8. Playground temperature runtime

| temp | Log | Pass |
|------|-----|------|
| 0.2 | effectiveTemperature=0.2 | YES |
| 0.7 | effectiveTemperature=0.7 | YES |

## 9. Model Settings Top-K

| Step | Pass |
|------|------|
| Save/reload topK=5 | YES |
| Runtime MODEL_CONFIG effective=5 | YES |
| Save/reload topK=10 | YES |
| Runtime MODEL_CONFIG effective=10 | YES |

## 10. maxTokens regression

`maxTokensSource=MODEL_CONFIG effectiveMaxTokens=512` — **PASS**

## 11. Compare sidebar hide

Playwright: compare ON → sidebar count 0; OFF → count 1 — **PASS**

## 12. Compare A/B smoke

Both sides returned answers; sources present — **PASS**

## 13. Production source cap regression

| Case | sources | Pass |
|------|---------|------|
| In-scope chat | 5 | YES |
| OOS | 2 | YES |

## 14. Có sửa code không?

**Không.** No application code diff.

## 15. Verify scripts (eval only, not product code)

- `_run_23d2_runtime_verify.ps1` — first run: playground curl body issue → PARTIAL.
- Manual curl with `--data-binary @file` — playground PASS.
- `_run_23d2_ui_verify.mjs` + local playwright — UI PASS.

## 16. Kết luận

**PASS**

## 17. Rủi ro còn lại

- Full stack UI via Docker nginx not verified (FE image build failed on host Docker bridge).
- Playground topK=3 returned 2 sources (retrieval/dedupe), not 3 — acceptable per ≤3 rule.

## 18. Bước tiếp theo

- Fix Docker Desktop networking for `frontend` image build if production deploy uses compose FE.
- No new features; 23D fix closed for runtime.

## Kiểm tra build (verify task)

| Command | Result |
|---------|--------|
| Backend compile | NOT RUN (no code change) |
| Backend test | NOT RUN |
| Frontend lint/build | NOT RUN |
| docker compose config | PASS (earlier) |
| Runtime verify | **PASS** |
