# CURSOR REPORT 31A — UI Context Top-N Override Respect

## Final verdict: **PASS**

(Core fix + unit tests + runtime top-N matrix **PASS**. S1/S2 schedule golden **NOT RUN** on current Docker chatbot corpus — **PARTIAL** for full golden matrix only.)

---

## 1. Mức độ hiểu task

- Hiểu task: **98%**
- Chắc chắn: `Math.min(requested, adaptive)` với `override != null` là root cause; fix là early-return khi override có giá trị.
- Giả định: `finalContextTopNOverride` null chỉ khi không có request topK và không có modelConfig topK.
- Thiếu dữ liện: chatbot Docker hiện tại không có tài liệu TKB cho S1/S2.

## 2. Tóm tắt yêu cầu

Khi user chọn Context Top-N trên Playground (15/20/25), backend không được giảm bởi adaptive cap (ví dụ list_like 15). Adaptive chỉ áp dụng khi không có override.

## 3. Hiện trạng trước khi sửa

| File | Method | Current behavior | Expected | Risk |
|------|--------|------------------|----------|------|
| `RagRetrievalService.java` | `resolveAdaptiveFinalContextTopN` | `min(25,15)=15` khi list_like + override=25 | return 25 | High — UI ignored |
| `PlaygroundController.java` | `chat` | Map `topK` → `ChatRequest.topK` | Pass override | Low — wiring OK |
| `ChatService.java` | `resolveRetrievalTopK` | `candidate=requestTopK` | Pass to retrieval | Low |
| `ModelOverridePanel.jsx` | `topK` field | Gửi `overrideParams.topK` | OK | Low |

## 4. Nguyên nhân gốc xác nhận từ source

```java
return Math.max(MIN_FINAL_CONTEXT_TOP_N, Math.min(requested, adaptive));
```

Với `list_like` → `adaptive=15`, mọi `requested > 15` bị cắt dù `override` được set từ UI.

## 5. Chiến lược sửa đã chọn

- Early return `requested` khi `override != null` (sau `resolveFinalContextTopN` clamp).
- Khi `override == null`: giữ adaptive; đổi `list_like` default từ 15 → 25.
- Cải thiện log `[RAG][topN]` với `override`, `requested`, `effective`.
- Cập nhật `FinalContextSelectionTest` theo 5 case bắt buộc.

## 6. Danh sách file đã đọc

| Path | Đọc để | Kết luận |
|------|--------|----------|
| `RagRetrievalService.java` | Resolver + call site | Bug tại `resolveAdaptiveFinalContextTopN` |
| `ChatService.java` | TopK resolution | `candidate` = request/model topK |
| `PlaygroundController.java` | FE → BE field | `topK` / `overrideParams.topK` |
| `PlaygroundService.java` | Compare path | Cùng `retrieveWithMetadata(..., topKOverride)` |
| `FinalContextSelectionTest.java` | Tests cũ | Test cũ expect giảm override — sai |
| `ModelOverridePanel.jsx` | UI field name | Field `topK` |
| `docs/eval/results/QUERY_ANALYZER_DEDUP_RUNTIME_VERIFY_30D2_20260530.md` | Runtime pattern | Bench script mẫu |

## 7. Danh sách file đã sửa

| Path | Sửa để | Layer |
|------|--------|-------|
| `Backend/.../RagRetrievalService.java` | Override wins; log; list_like=25 | service/retrieve |
| `Backend/.../FinalContextSelectionTest.java` | 5+ tests theo task | test |
| `scripts/bench_31a_topn_override.ps1` | Runtime verify helper | docs/script |
| `docs/eval/results/UI_TOPN_OVERRIDE_RESPECT_31A_20260530.md` | Eval report | docs |
| `reports/refactor/CURSOR_REPORT_31A_UI_TOPN_OVERRIDE_RESPECT.md` | Refactor report | docs |

## 8. Diff thay đổi của từng file

### RagRetrievalService.java

- Hiện trạng cũ: adaptive luôn có thể giảm `requested` kể cả khi UI set topK.
- Đã sửa: `if (override != null) return requested;`; `list_like` adaptive 25; log rõ hơn; `requestedFinalContextTopN` dùng `resolveFinalContextTopN`.

```diff
-        int requestedFinalContextTopN = normalizeFinalContextTopN(finalContextTopNOverride);
+        int requestedFinalContextTopN = resolveFinalContextTopN(finalContextTopNOverride, queryType);
         int finalContextTopN = resolveAdaptiveFinalContextTopN(question, finalContextTopNOverride, queryType);
-        log.info("[RAG][topN] requestedTopN={} effectiveTopN={} reason={} source={}",
-                requestedFinalContextTopN, finalContextTopN,
-                adaptiveTopNReason(question, queryType), topNSource);
+        log.info("[RAG][topN] override={} requested={} effective={} adaptiveReason={} source={}",
+                finalContextTopNOverride, requestedFinalContextTopN, finalContextTopN,
+                adaptiveTopNReason(question, queryType), topNSource);

     public static int resolveAdaptiveFinalContextTopN(...) {
         int requested = resolveFinalContextTopN(override, queryType);
+        if (override != null) {
+            return requested;
+        }
         int adaptive = switch (adaptiveTopNReason(question, queryType)) {
-            case "list_like" -> 15;
+            case "list_like" -> 25;
             ...
         };
         return Math.max(MIN_FINAL_CONTEXT_TOP_N, Math.min(requested, adaptive));
     }
```

### FinalContextSelectionTest.java

- Thay test expect `topN <= 15` bằng tests override 25/20/10 == requested; no-override adaptive; clamp.

## 9. Ảnh hưởng sau sửa

**Thay đổi:**

- UI/model `topK` → `effective` final context cap = clamped override (1–30), không bị adaptive list_like/compare/fact cắt.
- Không override: adaptive vẫn chạy; list_like cap 25 (trước 15).

**Giữ nguyên:**

- Retrieval scoring, rerank, query analyzer, Qdrant payload, prompt format.
- `normalizeFinalContextTopN` min/max 1–30.

**Latency/token:** Top-N cao hơn → context prompt lớn hơn khi user chọn (theo ý user).

## 10. Edge cases đã xem xét

- `override=0` → clamp 1
- `override=100` → clamp 30
- `override=null` + LIST_ALL → min(20, 25)=20
- modelConfig topK non-null → coi là override (candidate non-null)
- Playground luôn gửi default topK=5 → adaptive không chạy trên playground default panel

## 11. Kết quả kiểm tra

| Command | Kết quả | Ghi chú |
|---------|---------|---------|
| `cd Backend && .\mvnw.cmd -DskipTests compile` | PASS | |
| `cd Backend && .\mvnw.cmd clean test` | PASS | 191 tests, 0 failures |
| `cd Frontend && npm run lint` | NOT RUN | Ngoài scope |
| `cd Frontend && npm run build` | NOT RUN | Ngoài scope |
| `cd Frontend && npm run build:widget` | NOT RUN | Ngoài scope |
| `docker compose config` | PASS | |
| Runtime K47 topK 15/20/25 | PASS | effective=requested; finalContexts=15/20/25 |
| Runtime S1/S2 | NOT APPLICABLE | Chatbot KhoaHoc không có TKB doc |
| Runtime S5 | PASS | Refusal, không bịa tỷ giá |

## 12. Rủi ro còn lại

- List-all vẫn có thể lẫn ngành khác cùng mã K47 trong top contexts (ranking/scope).
- Adaptive constants chưa đưa vào `application.yml`.
- S1/S2 cần verify lại trên chatbot có tài liệu thời khóa biểu.

## 13. Đề xuất tiếp theo

1. Task retrieval scope cho `Kiến trúc K47` list queries.
2. Optional: `rag.retrieval.adaptive-final-context-top-n.*` trong config.
