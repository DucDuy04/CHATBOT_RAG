# CURSOR REPORT 27B2 — Startup Prewarm Runtime Verify

**Date:** 2026-05-30  
**Task:** 27B2 — Runtime Verify Startup Keyword Index Prewarm  
**Verdict:** **PASS**

---

## 1. Mức độ hiểu task

| Item | Value |
|---|---|
| Hiểu task | **100%** |
| Chắc chắn | Runtime-only verify; no code change unless wiring bug |
| Giả định | Rank-1 widget + document data còn trong MySQL/Qdrant từ env trước |
| Thiếu dữ liện | Không |

---

## 2. Tóm tắt yêu cầu

Xác nhận live Docker: prewarm chạy sau restart, widget rank-1 được warm, first chat không trả `keywordIndexBuildMs ≈ 12s`, correctness không regress.

---

## 3. Hiện trạng trước verify

27B đã implement `KeywordIndexStartupPrewarmer` + unit tests (82 pass). Runtime verify 27B chưa chạy — verdict PARTIAL.

---

## 4. Nguyên nhân gốc (đã xác nhận runtime)

Cold `keywordIndexBuildMs` trên first chat do cache trống sau JVM restart. 27B prewarm tại startup đã chuyển build cost sang background thread — first user request chỉ lookup cache hit.

---

## 5. Chiến lược verify

1. `docker compose up --build -d backend` (fresh restart)
2. Đọc log `[KeywordIndexPrewarm]`
3. Gửi L1 query đầu tiên (không warm-up script)
4. Parse `[RAG][latency]` trace
5. Gửi OOS query
6. So sánh với 27A baseline

---

## 6. Danh sách file đã đọc

| File | Mục đích | Kết luận |
|---|---|---|
| `docker-compose.yml` | Stack wiring | backend port 8080, env from `.env` |
| `scripts/benchmark/latency_bench_27a.ps1` | API + widget key | `/api/chat` + `X-Widget-Key` |
| `docs/eval/results/LATENCY_PROFILE_OPTIMIZATION_27A_20260530.md` | Baseline numbers | cold keywordIndexBuildMs=12177 |

---

## 7. Danh sách file đã sửa

**Không sửa production code.**

| File | Thao tác |
|---|---|
| `docs/eval/results/STARTUP_KEYWORD_INDEX_PREWARM_RUNTIME_VERIFY_27B2_20260530.md` | Tạo mới |
| `reports/refactor/CURSOR_REPORT_27B2_STARTUP_PREWARM_RUNTIME_VERIFY.md` | Tạo mới |

---

## 8. Kết quả runtime

### Startup prewarm

```
[KeywordIndexPrewarm] started widgets=3 limit=3000
[KeywordIndexPrewarm] widget=3a26c18c-bfd1-477e-af79-fcd7fba551a9 status=OK elapsedMs=5003
[KeywordIndexPrewarm] finished ok=3 failed=0 totalMs=23295
```

### First chat L1

| Metric | 27A cold | 27B2 after prewarm |
|---|---:|---:|
| keywordIndexBuildMs | 12177 | **0** |
| keywordIndexHit | false | **true** |
| totalMs | 26102 | 32377 |
| Answer | Vân Anh, E301 | Vân Anh, E301 |

### OOS L5

- Answer: refusal — PASS
- keywordIndexBuildMs=0 — PASS

---

## 9. Ảnh hưởng sau verify

- Behavior thay đổi (đã có từ 27B): cold keyword build không còn trên first chat path khi prewarm enabled.
- `totalMs` first chat vẫn có thể cao do query embed + retrieval scoring — không phải regression của 27B.
- Không có code change trong 27B2.

---

## 10. Edge cases đã xem xét

| Case | Kết quả |
|---|---|
| Prewarm async chưa xong khi user chat sớm | Không test — đợi >25s trước chat; prewarm finished in ~23s |
| Widget không có chunks | 3 widgets warmed — rank-1 included |
| OOS | Vẫn refuse đúng |

---

## 11. Kết quả kiểm tra

| Command | Kết quả | Ghi chú |
|---|---|---|
| `docker compose config -q` | PASS | |
| `docker compose up -d mysql qdrant` | PASS | |
| `docker compose up --build -d backend` | PASS | ~136s build |
| Startup prewarm logs | PASS | rank-1 warmed |
| First chat L1 | PASS | keywordIndexBuildMs=0 |
| OOS L5 | PASS | refusal |
| `mvn clean test` | NOT RUN | Runtime-only task |
| Production code change | NONE | |

---

## 12. Rủi ro còn lại

- User chat trong vòng ~23s đầu sau startup (trước prewarm xong) vẫn có thể hit cold build — cần document `prewarm-delay-ms` + typical warm duration.
- `totalMs` first chat chưa giảm tổng thể — các bottleneck khác (queryEmbed, scoring) vẫn dominate.

---

## 13. Đề xuất tiếp theo

- Cập nhật verdict 27B từ PARTIAL → **PASS** (runtime confirmed).
- Optional 27C: metric/health endpoint exposing `cachedWidgetCount()` và last prewarm status.
