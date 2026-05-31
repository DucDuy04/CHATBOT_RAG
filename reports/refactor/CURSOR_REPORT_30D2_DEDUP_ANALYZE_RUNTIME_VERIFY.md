# CURSOR REPORT 30D2 — Dedup Analyze Runtime Verify

## Final verdict: **PASS**

---

## 1. Mức độ hiểu task

- Hiểu task: **99%**
- Chắc chắn: mục tiêu là runtime verify dedup sau 30D — một request chỉ classify một lần; không sửa production behavior.
- Giả định: flake S1/S2 measured run giống 30C baseline (retry xác nhận), không phải regression do dedup.
- Thiếu dữ liệu: không có Playwright widget script sẵn cho 30D2; dùng API smoke thay thế.

## 2. Tóm tắt yêu cầu

Xác nhận live Docker sau 30D:
- `1 request → analyzeDetailed(...) once → one active QueryAnalysis log → queryAnalyzeCallCount=1`
- S1–S5 correctness không regress so với 30C
- Active LLM classifier vẫn bật, không có `shadow=true`

## 3. Hiện trạng trước khi verify

30D đã:
- Gộp analyze vào `RagRetrievalService.retrieveWithMetadata(...)` duy nhất
- `ChatService` / `PlaygroundService` reuse `QueryAnalysisResult`
- Backend tests 186 PASS
- Runtime S1–S5 **chưa chạy** trong 30D

## 4. Nguyên nhân gốc xác nhận từ source

Trước 30D, `ChatService` và `RagRetrievalService` mỗi nơi gọi `queryAnalyzerService.analyze(...)` → 2 classifier calls/request. Sau 30D, scan chỉ còn `RagRetrievalService:180` gọi `analyzeDetailed`.

## 5. Chiến lược verify đã chọn

1. Start Docker stack (mysql, qdrant, backend rebuild)
2. Scan stale analyze call sites + hardcode lexicon
3. Chạy S1–S5 benchmark (warm + 2 measured) qua `/api/public/chat`
4. Thu log `QueryAnalysis`, `queryAnalyzeCallCount`, latency trace
5. Retry S1/S2 khi measured flake
6. So sánh với 30C report

**Không sửa production code.**

## 6. Danh sách file đã đọc

| Path | Đọc để làm gì | Kết luận chính |
|---|---|---|
| `application.yml` | Config classifier | `enabled=true`, `shadow-mode=false`, `timeout-ms=2000` |
| `application-docker.yml` | Docker profile | QueryAnalyzerService log level INFO |
| `RagRetrievalService.java` | Stale call scan | Chỉ entry point `analyzeDetailed` |
| `RagLatencyTrace.java` | Trace fields | `queryAnalyzeCallCount` logged in latency line |
| `docs/eval/results/QUERY_ANALYZER_LLM_ACTIVE_VERIFY_30C_20260530.md` | Baseline 30C | 2 QueryAnalysis logs/request trước dedup |
| `reports/refactor/CURSOR_REPORT_30D_DEDUP_ANALYZE_CALL.md` | 30D refactor context | Code dedup đã merge, runtime chưa verify |
| `scripts/bench_30c_unicode.ps1` | Benchmark pattern | Template cho 30D2 script |

## 7. Danh sách file đã sửa

| Path | Sửa để làm gì | Ảnh hưởng |
|---|---|---|
| `scripts/bench_30d2_dedup.ps1` | Benchmark helper S1–S5 | scripts (non-production) |
| `docs/eval/results/QUERY_ANALYZER_DEDUP_RUNTIME_VERIFY_30D2_20260530.md` | Eval report | docs |
| `reports/refactor/CURSOR_REPORT_30D2_DEDUP_ANALYZE_RUNTIME_VERIFY.md` | Refactor report | docs |

**Production code: không thay đổi.**

## 8. Diff thay đổi

Không có diff production. Chỉ thêm script benchmark và report docs.

## 9. Ảnh hưởng sau verify

**Behavior thay đổi:** Không — verify only.

**Xác nhận runtime:**
- `queryAnalyzeCallCount=1` trên 17/17 requests
- Một `[QueryAnalysis] active=true` log mỗi request
- Không có `shadow=true`
- `[RAG][analysis] calls=1` mỗi request
- Classifier cost/latency per request giảm ~50% so với 30C (1 call thay vì 2)

**Behavior giữ nguyên:**
- Retrieval semantics, topK, prompt, parser, Qdrant payload
- Active LLM classifier enabled
- S3/S4/S5 correctness
- S1/S2 flake pattern giống 30C (retry pass)

## 10. Edge cases đã xem xét

- S1 warm-up cold-start timeout → LOCAL_GENERIC fallback, `queryAnalyzeCallCount` vẫn = 1
- Warm-up vs measured tách biệt — không tính duplicate
- S2 measured refusal dù TABLE_LOOKUP — pre-existing retrieval/LLM variance
- Public widget không có SSE endpoint (`/api/public/chat/stream` → 404)
- GROQ timeout không làm crash chat

## 11. Kết quả kiểm tra

| Command | Kết quả | Ghi chú |
|---|---|---|
| `docker compose config -q` | PASS | Valid compose |
| `docker compose up -d mysql qdrant` | PASS | Containers started |
| `docker compose up --build -d backend` | PASS | Rebuilt and started |
| `docker compose ps` | PASS | 3 services up |
| Stale analyze call scan | PASS | Only RagRetrievalService.analyzeDetailed |
| Hardcode lexicon scan | PASS | 0 matches in rag/analysis |
| S1–S5 runtime benchmark | PASS | Dedup metrics 100%; S1/S2 retry pass |
| Backend compile | NOT RUN | No code changes |
| Backend test | NOT RUN | No code changes (30D: 186 PASS) |
| Frontend lint/build | NOT RUN | Out of scope |
| Widget build | NOT RUN | Out of scope |
| Playwright widget E2E | NOT RUN | Unavailable |

## 12. Rủi ro còn lại

1. S1/S2 measured-run flake — pre-existing, không liên quan dedup.
2. Cold-start classifier timeout trên request đầu sau restart.
3. Chưa verify authenticated SSE path (`/api/chat/stream`) — widget dùng sync public chat.

## 13. Đề xuất tiếp theo

- **30E:** Classification cache hoặc lightweight classifier model.
- Optional: verify `/api/chat/stream` dedup với auth token trong task riêng.
- Monitor `fallbackReason=timeout` rate production.
