# CURSOR_REPORT_21F — T01 generation + source noise diagnosis

## 1. Mức độ hiểu task

- **~95%** — đủ để đọc pipeline, chạy SQL/Qdrant/API có kiểm soát, gán nhãn root cause.
- **Chắc chắn:** `sources` = map 1:1 từ `contexts`; bảng Basic/99000 có trong DB/Qdrant; T01 gốc có lần PASS/lần FAIL với cùng 3 source có evidence.
- **Còn giả định nhỏ:** log container không cho đủ dòng `[RAG] queryType=` theo từng request — intent **COUNT_QUERY** suy từ **source** `QueryAnalyzerService` (thứ tự rule) + hành vi variants.

## 2. Tóm tắt yêu cầu

Chẩn đoán read-only vì sao **GQ-T01** đôi khi phủ nhận dù source có bảng 99000, và vì sao **source noise / full-window** (O01/L01…). Không sửa Java/retrieval/prompt/frontend.

## 3. Phạm vi đã làm

- Đọc tài liệu eval 21E / 21E2 + golden questions + checklist/plan.
- Đọc `RagRetrievalService`, `PromptBuilderService`, `ChatService`, `QueryAnalyzerService`, `RerankService` (đoạn liên quan intent, budget, sources).
- SQL read-only + Qdrant scroll (payload, không vector).
- `POST /api/chat`: T01 gốc ×3 + 4 biến thể (session mới).
- Viết `docs/eval/results/RAG_T01_GENERATION_SOURCE_DIAG_21F_20260514.md` và báo cáo này.

## 4. Phạm vi không làm

- Không sửa code, compose, prompt, retrieval, `.gitignore`, không thêm dependency, không migration.

## 5. File đã đọc

| Path | Mục đích |
|------|----------|
| `docs/eval/results/RAG_RUNTIME_VERIFY_21E2_SECTION7_20260514.md` | ID tenant/doc 21E2 |
| `reports/refactor/CURSOR_REPORT_21E2_RUNTIME_VERIFY_SECTION7_AFTER_21D2.md` | Bối cảnh G1 PASS |
| `docs/eval/results/RAG_RUNTIME_VERIFY_21E_20260514.md` | Symptom T01 21E |
| `reports/refactor/CURSOR_REPORT_21E_RUNTIME_VERIFY_AFTER_PARSER_FIX.md` | Tóm tắt 21E |
| `docs/eval/RAG_GOLDEN_QUESTIONS.md` | Text GQ-T01 |
| `docs/eval/RAG_GOLDEN_TEST_DOCUMENT.txt` | Bảng Basic/99000 |
| `docs/eval/RAG_BASELINE_CORE_CHECKLIST.md` | Gate G3 |
| `docs/eval/RAG_BASELINE_CORE_REBUILD_PLAN.md` | Thứ tự fix |
| `RagRetrievalService.java` | Pool, rerank, dedupeSortBudget, limits |
| `PromptBuilderService.java` | System + user prompt + TABLE_LOOKUP block |
| `ChatService.java` | `buildSourceDtos`, LLM call path |
| `QueryAnalyzerService.java` | Thứ tự COUNT vs TABLE |
| `RerankService.java` | Cohere top_n behavior |
| `EmbeddingService.java` | Payload keys (đối chiếu Qdrant) |
| `DocumentChunk.java` | Field metadata |
| `ChatController.java` / `WidgetAuthFilter.java` | Xác nhận luồng `/api/chat` (không đổi) |

## 6. Document/runtime target

- `CHATBOT_ID` / `widgetId`: `818e5680-718a-426f-8abe-dae586779fc2`
- `DOCUMENT_ID`: `ea5cfde1-b84b-4d22-8636-c0514a51b4c6`
- Document **không** bị xóa; chat API thành công.

## 7. DB table chunk inspection

- `table_summary` + `table_row_group` tại `chunk_index` 4–5; `table_id=tbl_4_4`; content chứa **Basic** và **99000**; độ dài ~362 / ~180 ký tự. Chi tiết: file diagnosis §2.

## 8. Qdrant table payload inspection

- Payload `text_segment` / `section_title` khớp mục 4 bảng; có Basic/99000. Chi tiết: diagnosis §3.

## 9. T01 original rerun result

- 3 run: **1 PASS / 2 FAIL**; mỗi lần **3** `sources`; script xác nhận `chunkText` vẫn có evidence. → **GENERATION_CONTRADICTION** là thành phần chính khi pool nhỏ.

## 10. T01 variants result

- v1: FAIL (3 sources). v2/v3: PASS (10 sources). v4: PASS (3 sources). Chi tiết: diagnosis §5.

## 11. Backend logs summary

- Tail log chủ yếu exception noise; **không** trích đủ `[RAG] Detected intent` theo request. Intent suy ra từ code + behavior.

## 12. PromptBuilder/context analysis

- Context giữ `Content` raw từ chunk; table chunks là markdown.
- System rule #2 chỉ “không tìm thấy” khi **không** có context — không giải thích FAIL khi có 3 source.
- **Xung đột:** `QueryAnalyzerService` xếp **COUNT_QUERY** (keyword `bao nhieu`) **trước** **TABLE_LOOKUP** → T01 gốc nhận hint **COUNT_QUERY** thay vì TABLE — lệch golden `TABLE_LOOKUP`. Code: `QueryAnalyzerService.java` nhánh `COUNT_QUERY` trước block `TABLE_LOOKUP`.

## 13. Source array / source noise analysis

- `ChatService.buildSourceDtos`: **không cap** riêng — `sources.Count == contexts.Count`.
- O01/O02: **10** sources = đủ 10 chunk tenant (full-window mirror).
- Noise = **presentation + cognitive load** (LLM + UI), không phải “thừa field không dùng”.

## 14. Root cause matrix

- Xem `docs/eval/results/RAG_T01_GENERATION_SOURCE_DIAG_21F_20260514.md` §9.

## 15. Có sửa code không?

**Không.**

## 16. No code diff

```diff
# Không thay đổi source.
# Thêm:
#   docs/eval/results/RAG_T01_GENERATION_SOURCE_DIAG_21F_20260514.md
#   reports/refactor/CURSOR_REPORT_21F_T01_GENERATION_SOURCE_DIAGNOSIS.md
```

## 17. Kết luận

- **T01 fail (khi fail):** chủ yếu **GENERATION_CONTRADICTION** (LLM phủ nhận dù `chunkText` có 99000) **cộng hưởng** **QUERY_ANALYZER** — câu T01 gốc bị classify **COUNT_QUERY** nên `queryTypeHint` không dùng block **TABLE_LOOKUP** trong `PromptBuilderService`.
- **Source noise:** `sources` = toàn bộ contexts sau budget; tenant 1 doc nhỏ → dễ đạt **10** chunk (full doc) cho expanded/OOS; không phải nguồn dựng riêng khác retrieval.

## 18. Rủi ro còn lại

- Chỉnh `QueryAnalyzer` sai thứ tự có thể làm COUNT thật bị TABLE — cần rule tách biệt (vd bảng + giá ưu tiên trước “bao nhiêu” generic).
- Chỉ prompt mà không sửa intent vẫn có thể còn flip-flop model.

## 19. Đề xuất prompt / task fix tiếp theo

1. **Java (ưu tiên):** sửa thứ tự / điều kiện `QueryAnalyzerService` để câu có **bảng** + **giá** → **TABLE_LOOKUP** (hoặc override hint trong `ChatService` khi detect `table_*` chunk trong top contexts).  
2. **Prompt (sau 1):** làm rõ “nếu Source có `table_row_group`/`table_summary` chứa entity hỏi → **bắt buộc** trả giá, không dùng câu từ chối #2”.  
3. **Presentation:** cap/dedup `sources` cho UI vs context LLM.  
4. **Retrieval trim:** sau khi (1)(3) vẫn nhiễu.

---

## Kiểm tra sau task

| File | Trạng thái |
|------|------------|
| `docs/eval/results/RAG_T01_GENERATION_SOURCE_DIAG_21F_20260514.md` | Đã tạo, có nội dung |
| `reports/refactor/CURSOR_REPORT_21F_T01_GENERATION_SOURCE_DIAGNOSIS.md` | Đã tạo |

| Command | Kết quả |
|---------|---------|
| `mvn compile` | NOT RUN (không đổi code) |
