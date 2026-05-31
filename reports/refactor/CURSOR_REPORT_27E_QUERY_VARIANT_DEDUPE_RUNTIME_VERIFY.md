# CURSOR REPORT — 27E Query Variant Dedupe Runtime Verify

**Date:** 2026-05-30  
**Task:** 27E — Runtime benchmark + verification only (continues 27D)  
**Final verdict:** **PARTIAL**  
**27D status after 27E:** **PARTIAL** (not upgraded to PASS)

---

## 1. Mức độ hiểu task

- **Hiểu task:** 98%
- **Chắc chắn:** Runtime-only verification; V1–V5 benchmark enabled vs disabled; trace fields; restore dedupe; reports; no production semantic changes.
- **Giả định:** V2/V3 FAIL là retrieval issue có sẵn (cùng behavior enabled/disabled), không phải regression từ dedupe.
- **Thiếu dữ liệu:** Không có baseline 27D runtime trước 27E cho cùng session — so sánh enabled vs disabled trong cùng task.

---

## 2. Tóm tắt yêu cầu

Đo runtime effect của query variant dedupe (27D) trên Docker: `queryVariantTotal/Unique/Skipped`, `qdrantSearchCalls`, `vectorMs`, `retrievalMs`, `totalMs`, correctness V1–V5. So sánh dedupe enabled vs disabled. Quyết định nâng 27D từ PARTIAL lên PASS hay không.

---

## 3. Hiện trạng trước khi sửa

- 27D: dedupe implemented, unit/integration tests PASS, runtime benchmark **chưa chạy**.
- Verdict 27D: **PARTIAL**.
- Docker stack đã chạy; backend rebuild với code 27D.

---

## 4. Nguyên nhân gốc xác nhận từ source

1. **Dedupe hoạt động đúng** — `RagRetrievalService` gọi `QueryVariantDedupe.dedupe()` và ghi trace qua `RagLatencyTrace.setQueryVariantDedupeStats()`.
2. **Không có skip trên V1–V5** — `rewriteQuery()` tạo 3 variant thực sự khác nhau sau `normalizeText()` (giữ `?`, NFC form khác stripped form) → `skipped=0`, `qdrantSearchCalls=3` dù enabled.
3. **`[RAG][variant-dedupe]` không xuất hiện** — log chỉ khi `dedupeResult.skipped() > 0` (`RagRetrievalService` line 241–243); không phải bug wiring.
4. **V2/V3 FAIL giống nhau enabled/disabled** — không do dedupe loại variant cần thiết.

---

## 5. Chiến lược sửa đã chọn

- **Không sửa production code** (đúng constraint task).
- Tạo `scripts/benchmark/latency_bench_27e.ps1` mở rộng từ 27A, thu thập trace fields.
- Chạy Docker benchmark enabled → disabled (env override tạm) → restore enabled.
- Tạo eval report + cursor report.

---

## 6. Danh sách file đã đọc

| Path | Mục đích | Kết luận chính |
|---|---|---|
| `Backend/src/main/resources/application.yml` | Config dedupe | `enabled=true`, `mode=normalized_text`, `cosine-enabled=false` |
| `Backend/src/main/java/.../RagRetrievalService.java` | Wiring dedupe + trace | Dedupe trước vector loop; log variant-dedupe khi skipped>0 |
| `Backend/src/main/java/.../QueryVariantDedupe.java` | Normalization rules | Conservative NFC+lowercase+whitespace; giữ `?` và digits |
| `Backend/src/main/java/.../RagLatencyTrace.java` | Log format | Đủ fields queryVariant* và qdrantSearchCalls |
| `Backend/src/main/java/.../QueryAnalyzerService.java` | rewriteQuery | 3 variant thường gặp: trimmed, stripped-no-?, normalized |
| `scripts/benchmark/latency_bench_27a.ps1` | Template benchmark | Widget key, API pattern, percentile |
| `docs/eval/results/QDRANT_QUERY_VARIANT_DEDUPE_27D_20260530.md` | 27D context | PARTIAL; runtime pending |
| `docker-compose.yml` | Stack wiring | backend port 8080, profile docker |

---

## 7. Danh sách file đã sửa

| Path | Sửa để làm gì | Layer |
|---|---|---|
| `scripts/benchmark/latency_bench_27e.ps1` | Script benchmark 27E V1–V5, thu trace dedupe | test/benchmark |
| `docs/eval/results/QDRANT_QUERY_VARIANT_DEDUPE_RUNTIME_VERIFY_27E_20260530.md` | Eval report kết quả runtime | docs |
| `reports/refactor/CURSOR_REPORT_27E_QUERY_VARIANT_DEDUPE_RUNTIME_VERIFY.md` | Cursor audit report | docs |

**Temporary (đã xóa):** `docker-compose.bench-27e-disabled.yml` — env `RAG_RETRIEVAL_QUERY_VARIANT_DEDUPE_ENABLED=false` cho phase disabled.

**Production code:** không sửa.

---

## 8. Diff thay đổi của từng file

### `scripts/benchmark/latency_bench_27e.ps1` (new)

- Hiện trạng cũ: không có script 27E.
- Đã sửa: thêm script PowerShell benchmark V1–V5, parse `[RAG][latency]` cho queryVariant* và qdrantSearchCalls, correctness/source heuristics.
- Vì sao: task yêu cầu runtime benchmark có thể dùng script mới.
- Ảnh hưởng: chỉ tooling eval, không ảnh hưởng runtime production.

```diff
+ # Task 27E — Query Variant Dedupe Runtime Benchmark
+ param([ValidateSet("enabled","disabled")][string]$Phase = "enabled", ...)
+ # V1–V5 questions, Invoke-SyncChat, Parse-LatencyLine, JSON output
```

### Production / config

**Không có diff** — đúng phạm vi task.

---

## 9. Ảnh hưởng sau sửa

### Behavior thay đổi

- **Không** — production retrieval unchanged.

### Behavior giữ nguyên

- Dedupe enabled mặc định.
- 3 Qdrant calls/query cho V1–V5 (enabled và disabled giống nhau vì skipped=0).

### Latency / cost

- **qdrantSearchCalls saved:** 0 trung bình trên V1–V5.
- **vectorMs / totalMs:** không cải thiện có ý nghĩa từ dedupe.

### Config sau task

```yaml
rag.retrieval.query-variant-dedupe.enabled: true  # restored
```

---

## 10. Edge cases đã xem xét

- Keyword index cold start → đợi `[KeywordIndexPrewarm] finished` trước benchmark.
- Dedupe disabled quên restore → đã restart backend không overlay, verify `queryVariantDedupeMode=normalized_text`.
- Trace fields thiếu → xác nhận có trong `[RAG][latency]`.
- Fixture missing → widget + document + Qdrant 9888 points OK.
- V5 hallucination → PASS (refuse).
- Log variant-dedupe không xuất hiện khi skipped=0 → expected, không sửa.

---

## 11. Kết quả kiểm tra

| Command | Kết quả | Ghi chú |
|---|---|---|
| `docker compose config -q` | PASS | |
| `docker compose up -d mysql qdrant` + `up --build -d backend` | PASS | Rebuilt backend |
| Keyword prewarm | PASS | `ok=3 failed=0` mỗi phase |
| Benchmark enabled V1–V5 | PASS | JSON `..._enabled_20260530-162914.json` |
| Benchmark disabled V1–V5 | PASS | JSON `..._disabled_20260530-163827.json` |
| Dedupe restored | PASS | `queryVariantDedupeMode=normalized_text` |
| `cd Backend && mvnw -DskipTests compile` | PASS | |
| `cd Backend && mvnw test` | NOT RUN | No production code change; 27D: 115 tests PASS |
| Frontend lint/build/widget | NOT RUN | Out of scope |
| `docker compose config` | PASS | After removing temp overlay |

---

## 12. Rủi ro còn lại

- **Dedupe low impact** trên workload hiện tại — tiết kiệm Qdrant chỉ khi rewriteQuery tạo normalized duplicates (không xảy ra V1–V5).
- **V2/V3 retrieval FAIL** — pre-existing; cần task riêng (keyword/topK/table lookup), không disable dedupe.
- **V4 PARTIAL** — list incomplete do context/topK, không liên qu dedupe.

---

## 13. Đề xuất tiếp theo

1. Giữ dedupe **enabled**.
2. Không cosine dedupe / adaptive n/topK trong scope hiện tại.
3. Task tiếp: align `rewriteQuery()` để stripped variant normalize cùng key với trimmed (nếu muốn tiết kiệm Qdrant an toàn), hoặc benchmark trên query set có duplicate thật.
4. Task riêng: fix V2 (`KNM1013` Nhóm 4) và V3 (Kiến trúc K46 HK2 list) retrieval.

---

## Final summary (chat format)

- **Hiểu task:** 98%
- **Root cause:** Live `rewriteQuery()` output for V1–V5 yields 3 distinct normalized keys (`?` preserved; NFC variant differs) → `queryVariantSkipped=0` → no Qdrant savings; dedupe wiring correct.
- **Đã sửa file:**
  - `scripts/benchmark/latency_bench_27e.ps1`
  - `docs/eval/results/QDRANT_QUERY_VARIANT_DEDUPE_RUNTIME_VERIFY_27E_20260530.md`
  - `reports/refactor/CURSOR_REPORT_27E_QUERY_VARIANT_DEDUPE_RUNTIME_VERIFY.md`
- **Đã tạo report:** `docs/eval/results/QDRANT_QUERY_VARIANT_DEDUPE_RUNTIME_VERIFY_27E_20260530.md`
- **Kết quả kiểm tra:**
  - Backend compile: PASS
  - Backend test: NOT RUN
  - Frontend lint: NOT RUN
  - Frontend build: NOT RUN
  - Widget build: NOT RUN
  - Docker compose config: PASS
- **Rủi ro còn lại:** Dedupe safe but zero impact on V1–V5; V2/V3 retrieval failures pre-existing and unrelated to dedupe.
