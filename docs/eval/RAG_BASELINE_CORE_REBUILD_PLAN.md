# Plan — Rebuild & kiểm soát baseline core RAG (gate-based)

**Phiên bản:** 1.0 (task 21D0 — tài liệu only)  
**Liên kết:** [RAG_BASELINE_CORE_CHECKLIST.md](RAG_BASELINE_CORE_CHECKLIST.md) · [RAG_BASELINE_CORE_FIX_LOOP_TEMPLATE.md](RAG_BASELINE_CORE_FIX_LOOP_TEMPLATE.md) · E2E [docs/RAG_CORE_FLOW_E2E_RUNBOOK.md](../RAG_CORE_FLOW_E2E_RUNBOOK.md) · Eval [RAG_EVALUATION_RUNBOOK.md](RAG_EVALUATION_RUNBOOK.md)

---

## 1. Mục tiêu rebuild baseline core

- Có **một chuẩn gate** (G0–G6) để mỗi lần sửa parser / chunking / retrieval / prompt biết **chạy gì**, **expected gì**, **pass/fail ở đâu**, **không sửa nhiều lớp cùng lúc**.
- Baseline chất lượng golden **không** đánh giá bằng cảm giác: mỗi case có verdict **PASS / PARTIAL / FAIL** theo cột `verdict` (và trục A–D trong `RAG_GOLDEN_QUESTIONS.md`).
- Luồng **core E2E** (upload sample → chat → delete → không leak) luôn là **hàng rào regression** sau mỗi thay đổi có risk.

---

## 2. Baseline hiện tại từ 20G / 21B / 21C

| Nguồn | Nội dung tóm tắt |
|--------|------------------|
| **20G** | Runtime E2E PASS: stack Docker, tạo chatbot, upload `RAG_E2E_SAMPLE.txt`, `INDEXED`, DB chunk, Qdrant point theo `widgetId` + `document_id`, chat có `RAG-E2E-31415` + source đúng file, `DELETE` soft-delete + Qdrant 0, chat sau xóa không còn source doc đã xóa. |
| **21B** | Golden eval 12 case đã chạy thực tế; **bảng từng case** trong `docs/eval/results/RAG_EVAL_RUN_21B_20260514.md` là chuẩn verdict từng `case_id`. |
| **21C** | Recompute tổng từ cột `verdict` + GQ-D01: **PASS=6, PARTIAL=2, FAIL=4, total=12** (không dùng mục tổng hợp §5 cũ của file 21B vì lệch với bảng case). Chẩn đoán: ingest metadata sai (parser `##`, list `1.` thành section), heading lock / vector filter / không re-search sau guardrail → một số case 0 context; F03/T01 đúng fact nhưng attribution section sai. |

---

## 3. Các vấn đề đã biết (không implement trong 21D0)

1. **Parser TXT:** không nhận markdown heading dạng `## ...` làm ranh giới section; pattern số đầu dòng khiến **numbered list** trong “Chính sách” thành section root giả.
2. **Chunk / table metadata:** nội dung “Bước 2”, bảng gói dịch vụ có trong chunk nhưng `section_id` / `section_title` / Qdrant payload lệch (vd gắn “Bước 3”, false list title).
3. **Retrieval:** heading lock chọn section không có chunk hoặc `sec_3__dup2` vs chunk `sec_3`; hit vector bị loại hết trong lock; sau guardrail không refill anchor → early empty context.
4. **Generation / attribution:** L01 mâu thuẫn tài liệu trên context nhiễu; F03/T01 đúng số nhưng “Section” trong answer lệch metadata.
5. **EVAL_SUMMARY:** tổng hợp số PASS trong file 21B §5 không khớp bảng case — sửa bằng addendum/note, không tin vào một dòng tổng nếu không reconcile với từng `verdict`.

---

## 4. Nguyên tắc sửa từng bước

- **Một fix — một mục tiêu chính** (vd chỉ parser, hoặc chỉ retrieval fallback, hoặc chỉ attribution prompt).
- **Một fix map tối thiểu một dòng checklist** (ID trong `RAG_BASELINE_CORE_CHECKLIST.md`).
- **Không** tối ưu retrieval / rerank / top-k khi **G1** (metadata ingest) chưa PASS đủ ngưỡng.
- **Không** làm table-aware / hybrid bảng khi **markdown heading + false section** chưa ổn.
- **Không** chỉnh prompt runtime khi **retrieval** còn trả sai / thiếu context cho case targeted (G3).
- Mọi kết luận PASS cần **evidence lưu** (xem mục 6 + checklist).

---

## 5. Gate-based workflow (tóm tắt)

| Gate | Ý nghĩa |
|------|---------|
| **G0** | Build / static: compile, compose config, env, không lộ secret, scope change kiểm soát. |
| **G1** | Ingest: golden `.txt` mới → `INDEXED` → SQL sections/chunks/tables → Qdrant payload `section_title` / `chunk_type` / counts. |
| **G2** | Core E2E regression (sample E2E + delete + không leak). |
| **G3** | Targeted golden: GQ-F04, L01, L02, T02, F03, T01. |
| **G4** | Full 12 case + tổng hợp chỉ từ cột `verdict`. |
| **G5** | Delete + GQ-D01 + (optional) retry FAILED expected behavior. |
| **G6** | Low-resource guard: không spam doc, không benchmark nặng, không full scan / log khổng lồ. |

Chi tiết thực thi: [RAG_BASELINE_CORE_CHECKLIST.md](RAG_BASELINE_CORE_CHECKLIST.md).

---

## 6. Fix loop protocol (chuẩn mỗi PR/fix nhỏ)

Dùng template: [RAG_BASELINE_CORE_FIX_LOOP_TEMPLATE.md](RAG_BASELINE_CORE_FIX_LOOP_TEMPLATE.md).

**Thứ tự bắt buộc sau mỗi fix:**

1. Nếu sửa backend Java: `cd Backend && ./mvnw -DskipTests compile` (Windows có thể `.\mvnw.cmd -DskipTests compile`).
2. **Re-ingest** tài liệu golden bằng **document mới** (không tái dùng `DOCUMENT_ID` đã index trước fix cho G1/G3/G4).
3. Chạy **G1** — nếu FAIL: chỉ sửa parser/chunking tiếp, **không** chạy tối ưu retrieval.
4. Nếu G1 PASS: chạy **G3** targeted — nếu no-context / sourceCount=0 in-scope → nhánh **retrieval / query analyzer** diagnosis + fix nhỏ.
5. Nếu G3 answer đúng fact nhưng source/section sai → nhánh **attribution / chunk metadata / prompt** (theo thứ tự ưu tiên P1).
6. Chạy **G2** để đảm bảo core E2E không regress.
7. Chỉ chạy **G4** full khi G1+G2+G3 đạt **ngưỡng tối thiểu** (mục 11).

**Evidence tối thiểu mỗi lần:**

- Response `POST /api/chat` (answer, `sources`, `sourceCount` nếu có).
- SQL: `document_sections`, `document_chunks`, `document_tables` (raw SQL nếu cần audit doc soft-deleted).
- Qdrant: count filter `document_id` + `widgetId`; scroll/payload summary (không paste full embedding).
- Bảng case: `verdict` + tags.

---

## 7. Test data chuẩn

| Artifact | Path / ghi chú |
|----------|----------------|
| Golden nội dung | `docs/eval/RAG_GOLDEN_TEST_DOCUMENT.txt` (upload `.txt` only per `RAG_EVALUATION_RUNBOOK.md`). |
| Câu hỏi + tiêu chí | `docs/eval/RAG_GOLDEN_QUESTIONS.md`. |
| E2E sample | `docs/samples/RAG_E2E_SAMPLE.txt` (theo runbook 20F/20G). |
| Chatbot eval | Tạo mới mỗi phiên hoặc tenant chỉ chứa doc đang test — ghi `CHATBOT_ID`, `X-Widget-Key` (masked), `DOCUMENT_ID` trong template fix-loop. |

---

## 8. Checklist chạy trước mỗi lần sửa

- G0: compile (nếu sẽ sửa Java), `docker compose config`, kiểm tra env Groq/Nomic (boolean có/không, không in secret).
- Ghi **pre-test snapshot**: commit hash, branch, danh sách file định sửa, checklist ID liên quan.
- Xác nhận **không** mix nhiều thay đổi không liên quan trong cùng commit.

---

## 9. Checklist chạy sau mỗi lần sửa

- G1 → (nếu đạt ngưỡng) G3 → G2 → (khi đủ điều kiện) G4 → G5 → G6.
- Điền cột **Actual / Status / Evidence** trong [RAG_BASELINE_CORE_CHECKLIST.md](RAG_BASELINE_CORE_CHECKLIST.md).
- Hoàn thành [RAG_BASELINE_CORE_FIX_LOOP_TEMPLATE.md](RAG_BASELINE_CORE_FIX_LOOP_TEMPLATE.md) cho fix đó.

---

## 10. Quy tắc phân loại lỗi (nhãn nội bộ)

| Nhãn | Khi dùng |
|------|----------|
| `PARSER` | Section boundary sai; `##` không parse; list thành section giả. |
| `CHUNKING` | Chunk gán sai `section_id` / type; bảng gán sai section. |
| `EMBED` | Payload/embed text mang metadata sai (thường hệ quả G1). |
| `QUERY_ANALYZER` | `QueryType` / heading match chọn section sai. |
| `RETRIEVAL` | Lock scope, filter vector, anchor rỗng, guardrail không refill. |
| `GENERATION` | Context có nhưng LLM tóm sai / mâu thuẫn doc. |
| `ATTRIBUTION` | Fact đúng nhưng section/source hiển thị sai. |
| `EVAL_SUMMARY` | Sai số tổng hợp so với cột `verdict`. |
| `OTHER` | Môi trường, network, dữ liệu test nhiễu. |

Gắn thêm tag eval từ `RAG_GOLDEN_QUESTIONS.md` (RETRIEVAL, NO_CONTEXT, TABLE_PARSE, …) khi báo cáo case.

---

## 11. Quy tắc quyết định pass / fail (ngưỡng tối thiểu sau parser fix)

Sau khi có fix parser/chunking **và** re-ingest document mới:

| Gate | Ngưỡng |
|------|--------|
| **G1** | Metadata section/chunk/table/Qdrant payload: **100%** các mục G1 trong checklist liên quan = **PASS** (không PARTIAL cho hàng “hard” như false section / Bước 2 / bảng). |
| **G2** | Core E2E: **100% PASS**. |
| **G3** | GQ-F04: **PASS** hoặc **PARTIAL** với **source đúng file golden** và không NO_CONTEXT giả. GQ-L01, L02, T02: ít nhất **PARTIAL** mỗi case. GQ-F03, T01: **không** regress từ **PARTIAL** xuống **FAIL**. |
| **GQ-D01** | **Không được FAIL** (không cite file golden đã xóa). |

Chỉ mở **G4** full 12 case khi G1+G2+G3 đạt bảng trên (hoặc ghi rõ **BLOCKED** / ngoại lệ có phê duyệt).

---

## 12. Khi nào được coi là pass để chuyển sang task / prompt tiếp theo

- **Chuyển sang prompt / attribution:** G1 PASS; G3 cho thấy context đủ, không còn pattern 0-source in-scope cho F04/L02/T02; còn lệch section trong answer → prompt hoặc metadata display.
- **Chuyển sang retrieval-only fix:** G1 PASS nhưng vẫn no-context / lock sai (log `[RAG]` / Qdrant exclude) → fix nhỏ trong `RagRetrievalService` / `QueryAnalyzerService` với checklist ID.
- **Chuyển sang table-aware (Phase 6):** G1 cho thấy `document_tables` + chunk table đúng section; G3 table cases vẫn FAIL với tag TABLE_PARSE / retrieval table → mới mở phase hybrid/table.

---

## 13. Khi nào phải dừng và tạo prompt fix nhỏ hoặc rollback

- **Dừng + fix nhỏ:** G2 FAIL (regression E2E) — revert hoặc sửa tối thiểu trước khi tiếp tục G3/G4.
- **Dừng + diagnosis:** G1 PARTIAL/FAIL sau parser fix — không sang retrieval; quay lại parser/chunking.
- **Rollback:** compile fail; G4 làm tăng FAIL không chấp nhận được; GQ-D01 FAIL (leak vector/source).
- **Prompt fix nhỏ:** chỉ khi đã có bằng chứng G1+retrieval đủ (tránh “prompt vá” khi context sai).

---

## 14. Lộ trình đề xuất (phase)

| Phase | Nội dung |
|-------|----------|
| **0** | Lock checklist + template; chốt artifact table (chatbot/doc IDs). |
| **1** | Parser: markdown `##` heading; loại false section từ numbered list trong golden doc. |
| **2** | Verify ingest metadata (G1 100%) sau mỗi re-ingest. |
| **3** | Targeted golden rerun G3 (F04, L01, L02, T02, F03, T01). |
| **4** | Retrieval fallback / heading lock / re-search sau guardrail nếu G1 PASS mà G3 vẫn no-context. |
| **5** | Attribution / source cleanup (F03, T01, L01 generation) khi fact đã ổn. |
| **6** | Table-aware nếu bảng vẫn fail sau parser+retrieval; hybrid nếu cần. |

**Thứ tự ưu tiên fix (tóm tắt):**

- **P0:** Parser TXT — markdown heading + numbered list false section.  
- **P0:** Verify chunk + section metadata sau re-ingest (G1).  
- **P0:** Targeted rerun F04, L01, L02, T02, F03, T01 (G3).  
- **P1:** Retrieval fallback / heading lock nếu parser fix chưa đủ để hết 0-context.  
- **P1:** Source attribution nếu answer đúng nhưng section sai.  
- **P2:** Table-aware nếu table cases vẫn fail sau parser + retrieval.  
- **P2:** Sửa/ghi chú summary 21B (addendum) — chỉ số phải khớp cột `verdict`.

---

## 15. Không làm trong giai đoạn baseline checklist (21D0)

- Không migration / backfill dữ liệu cũ trong task checklist.  
- Không benchmark load / không tạo hàng loạt document vô cớ.  
- Không đổi `.gitignore`, không thêm dependency, không sửa Java/React trong task tạo tài liệu này.

---

*Tài liệu này là plan điều hành; thực thi từng bước ghi trong checklist + fix-loop template.*
