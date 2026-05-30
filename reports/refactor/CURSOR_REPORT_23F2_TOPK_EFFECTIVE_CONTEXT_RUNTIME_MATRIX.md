# CURSOR_REPORT_23F2 — Top-K Effective Context Runtime Matrix

## 1. Mức độ hiểu task

- **~98%**
- **Chắc chắn:** Runtime matrix đã chạy trên Docker local; logs chứng minh effective topK, vector anchors, section expansion, final contexts, source cap.
- **Giả định:** Kết quả trên doc 5-chunk có thể khác PDF lớn (expansion ít “kéo hết doc” hơn).

## 2. Tóm tắt yêu cầu

Chạy runtime matrix 23F2 (Playground, Compare, Widget modelConfig, `/api/chat` explicit topK) với `TOPK_CONTROL_TEST_DOCUMENT.txt`; VERIFY ONLY.

## 3. Phạm vi đã làm

- `docker compose up --build -d`
- Tạo chatbot `23F2 TopK Runtime Matrix`, upload + INDEXED (5 chunks)
- Script `_run_23f2_runtime_matrix.ps1` → `_run_23f2_results.json`
- Báo cáo matrix + phân tích log backend

## 4. Phạm vi không làm

- Không sửa Java / Frontend / retrieval / PromptBuilder / QueryAnalyzer / source cap
- Không thêm dependency

## 5. File đã đọc / tạo

| Path | Vai trò |
|------|---------|
| `RAG_TOPK_EFFECTIVE_CONTEXT_AUDIT_23F_20260517.md` | Baseline semantics |
| `RagRetrievalService.java`, `ChatService.java` | Đối chiếu log |
| `_run_23f2_runtime_matrix.ps1` | Runner (mới, verify-only) |
| `_run_23f2_results.json` | Raw results |
| `RAG_TOPK_EFFECTIVE_CONTEXT_RUNTIME_MATRIX_23F2_20260517.md` | Matrix report |

## 6–9. Luồng (runtime)

Xem matrix report. Tóm tắt:

- **Playground:** effective topK đúng; aggregate topK=1 → `finalContexts=5`, `responseSources=1`
- **Compare:** effective 1 vs 10; aggregate cả hai `finalContexts=5`
- **Widget:** `MODEL_CONFIG effective=1/10` đúng

## 10. Vì sao topK=1 vẫn trả lời đủ (runtime)

**Section range expansion** sau 1 vector hit kéo **5 chunks** (toàn bộ doc test). Prompt nhận 5 contexts; LLM liệt kê đủ 5 mã. Playground chỉ hiển thị **1** source (`cap=1`).

## 11. Có sửa code không

**Không.**

## 12–13. Diff / runtime

Chỉ thêm script eval + docs; **no production code diff**.

## 14. Production source cap

Không đổi. `/api/chat` aggregate topK=1 vẫn có thể trả **5 sources** khi doc ≤5 chunks (không chứng minh topK vô hiệu — phải đọc log `effective=1`).

## 15. Kết luận

**PARTIAL** — wiring PASS; semantic gap confirmed.

## 16. Rủi ro còn lại

- Test doc quá nhỏ (5 chunks) → expansion luôn kéo full doc.
- “Liệt kê” classified `NORMAL_FACT` not `LIST_ALL` — expansion vẫn đủ mạnh qua section range.
- OOS vẫn đưa 5 contexts vào prompt dù refusal đúng.

## 17. Bước tiếp theo

- Test lại trên PDF lớn nhiều section để xem topK=1 aggregate có còn đủ không.
- Product quyết định có gắn topK với final context cap hay đổi label UI.

## 11. Kết quả kiểm tra

| Command | Kết quả | Ghi chú |
|---------|---------|---------|
| `docker compose up --build -d` | **PASS** | |
| `docker compose ps` | **PASS** | 4 containers |
| Health `GET /api/chatbots` | **PASS** | |
| Runtime matrix script | **PASS** | Conclusion PARTIAL |
| Backend compile/test | **NOT RUN** | No code change |
| Frontend lint/build | **NOT RUN** | No code change |
