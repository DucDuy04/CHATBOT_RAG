# CURSOR REPORT 23G5 — OOS Pivot Answer Source Cap Fix

## 1. Mức độ hiểu task

- **100%** — Root cause rõ ràng từ 23G4, scope fix nhỏ và cụ thể.
- Chắc chắn: regression từ 23G3 trade-off, fix bằng position-based leading refusal check.
- Không có phần giả định.

## 2. Tóm tắt yêu cầu

Fix production source cap cho OOS pivot answer (LLM trả lời OOS bắt đầu bằng refusal rồi pivot sang liệt kê document content). Giữ nguyên Playground debug source display (không cap theo 23G3 fix).

## 3. Hiện trạng trước khi sửa

- `applyAnswerAwareSourceCap` → `isPureRefusalLikeAnswer(answer)`
- `isPureRefusalLikeAnswer` = refusal marker + NO substantive content
- OOS pivot answer có policy codes SAU refusal → `hasSubstantiveFactualContent=true` → không phải "pure" refusal → cap không apply → `responseSources=5`

## 4. Nguyên nhân gốc xác nhận từ source

`ChatService.java` line 536 (before fix):
```java
if (!isPureRefusalLikeAnswer(answer)) {
    return sources;  // not capped for pivot answers
}
```

`hasSubstantiveFactualContent("...Không tìm thấy... - ALPHA-111 - BETA-222...")` = `true` vì `FACTUAL_POLICY_CODE.matcher(answer).find()` = true. Do đó `isPureRefusalLikeAnswer` = `false`.

Nhưng: partial in-scope answer "ALPHA-111. Không tìm thấy Eta." cũng có `hasSubstantiveFactualContent=true` và cũng không nên bị cap.

Sự khác biệt: **vị trí** của refusal marker so với policy code.

## 5. Chiến lược sửa đã chọn

Option A (position-based): Thêm `isLeadingRefusalAnswer(answer)` — trả true khi refusal marker xuất hiện TRƯỚC bất kỳ policy code hoặc numbered list nào. Sử dụng trong production path thay vì `isPureRefusalLikeAnswer`.

Không đổi Playground path (vẫn bypass via `playgroundDebugSources=true`).
Giữ `isPureRefusalLikeAnswer` và `hasSubstantiveFactualContent` để không break existing tests.

## 6. Danh sách file đã đọc

| File | Đọc để làm gì | Kết luận |
|------|---------------|----------|
| `Backend/.../ChatService.java` | Source logic | `applyAnswerAwareSourceCap`, `isPureRefusalLikeAnswer`, `hasSubstantiveFactualContent` |
| `Backend/.../ChatServiceSourcePresentationTest.java` | Existing tests | 12 tests, constraints to preserve |
| `Backend/.../ChatRequest.java` | Flag `playgroundDebugSources` | Boolean, Playground sets true |
| `docs/eval/results/PLAYGROUND_CONTEXT_TOPN_1_TO_10_VERIFY_23G4_20260520.md` | Issue from 23G4 | OOS FxRate FAIL 5 sources |
| `docs/eval/results/PLAYGROUND_SOURCE_PRESENTATION_CONTEXT_TOPN_23G3_20260519.md` | 23G3 fix context | isPureRefusalLikeAnswer introduced here |

## 7. Danh sách file đã sửa

| File | Sửa để làm gì | Ảnh hưởng |
|------|---------------|-----------|
| `Backend/.../ChatService.java` | Thêm `isLeadingRefusalAnswer`, cập nhật `applyAnswerAwareSourceCap` | service |
| `Backend/.../ChatServiceSourcePresentationTest.java` | Thêm 7 unit tests mới | test |

## 8. Diff thay đổi

### `ChatService.java`

```diff
+import java.util.regex.Matcher;
 import java.util.regex.Pattern;
```

```diff
     List<ChatResponse.SourceDto> applyAnswerAwareSourceCap(...) {
         ...
-        if (!isPureRefusalLikeAnswer(answer)) {
+        if (!isLeadingRefusalAnswer(answer)) {
             return sources;
         }
-        log.info("[Chat] Pure refusal-like answer → response sources {} → {}",
+        log.info("[Chat] Leading refusal-like answer (OOS/pivot) → response sources {} → {}",
                 sources.size(), MAX_REFUSAL_RESPONSE_SOURCES);
     }
```

```diff
+    static boolean isLeadingRefusalAnswer(String answer) {
+        if (!isRefusalLikeAnswer(answer)) { return false; }
+        String normalized = answer.toLowerCase(Locale.ROOT);
+        int refusalPos = Integer.MAX_VALUE;
+        for (String marker : REFUSAL_ANSWER_MARKERS) {
+            int pos = normalized.indexOf(marker);
+            if (pos >= 0) { refusalPos = Math.min(refusalPos, pos); }
+        }
+        Matcher codeMatcher = FACTUAL_POLICY_CODE.matcher(answer);
+        while (codeMatcher.find()) {
+            if (codeMatcher.start() < refusalPos) { return false; }
+        }
+        Matcher listMatcher = NUMBERED_LIST_ITEM.matcher(answer);
+        while (listMatcher.find()) {
+            if (listMatcher.start() < refusalPos) { return false; }
+        }
+        return true;
+    }
```

### `ChatServiceSourcePresentationTest.java`

Added 7 tests covering `isLeadingRefusalAnswer` and new `applyAnswerAwareSourceCap` behavior.

## 9. Ảnh hưởng sau sửa

- **Production OOS pivot answer**: refusal cap ≤2 apply đúng.
- **Production partial in-scope answer**: không bị cap nhầm (code trước refusal → `isLeadingRefusalAnswer=false`).
- **Playground debug path**: không thay đổi (bypass qua `playgroundDebugSources=true`).
- **Pure OOS refusal** (Omega): cap ≤2 như cũ.
- **Factual in-scope**: không thay đổi.
- Memory/CPU: không ảnh hưởng (O(N) string scan, N = answer length, <2KB).
- Latency: không đáng kể.

## 10. Edge cases đã xem xét

- Refusal marker trong middle của answer (partial in-scope) → `isLeadingRefusalAnswer=false` ✓
- Pivot answer với numbered list (1. Alpha: ALPHA-111) sau refusal → leading refusal → cap ✓
- Playground debug với leading refusal → bypass → no cap ✓
- Empty/null answer → `isRefusalLikeAnswer=false` → `isLeadingRefusalAnswer=false` → no cap ✓
- Sources already ≤2 → guard check prevents unnecessary log/copy ✓

## 11. Kết quả kiểm tra

| Command | Kết quả |
|---------|---------|
| `.\mvnw.cmd -DskipTests compile` | **PASS** |
| `ChatServiceSourcePresentationTest` (19 tests) | **PASS** |
| `ChatServiceModelConfigTopKTest` (9 tests) | **PASS** |
| `FinalContextSelectionTest` (7 tests) | **PASS** |
| `RetrievalTopKTest` (7 tests) | **PASS** |
| Total | **42/42 PASS** |
| `docker compose up --build -d backend` | **PASS** (rebuilt) |
| Runtime A — OOS FxRate `responseSources` | **2 PASS** (was 5) |
| Runtime B — OOS Omega `responseSources` | **2 PASS** |
| Runtime C — In-scope `responseSources` | **2 PASS** (≤5) |
| Runtime D — Playground topN=6 `uiSrc` | **6 PASS** (old bug still fixed) |
| Runtime D — Playground topN=1,10 | **PASS** |

## 12. Rủi ro còn lại

1. Refusal marker với dấu cách biến thể có thể không match `REFUSAL_ANSWER_MARKERS` — nhưng đây là pre-existing risk, không liên quan fix này.
2. Nếu LLM liệt kê policy codes TRƯỚC khi nói refusal trong pivot answer (edge case ít gặp) → `isLeadingRefusalAnswer=false` → không cap → 5 sources. Chấp nhận được.

## 13. Đề xuất tiếp theo

- Xem xét thêm refusal markers nếu LLM hay dùng biến thể khác (e.g. "tài liệu không cung cấp thông tin").
- Extend runtime verify sang full Top-N 1..10 matrix nếu cần regression full.
