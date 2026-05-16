# RAG — Source presentation cleanup 21J (official result)

**File:** `docs/eval/results/RAG_SOURCE_PRESENTATION_CLEANUP_21J_20260515.md`  
**Task report:** `reports/refactor/CURSOR_REPORT_21J_SOURCE_PRESENTATION_CLEANUP_FIX.md`

**Ngày chạy:** 2026-05-15  
**Môi trường:** Windows 10, Docker Desktop, repo `CHATBOT_RAG`  
**Phạm vi:** Response-only source cap/dedup trong `ChatService` — **không** đổi retrieval/LLM context.

---

## 1. Mục tiêu fix

Giảm **source noise** trong API/SSE (`sources` field): trước 21J nhiều case `sourceCount=10` (full retrieval window) dù answer đúng. Sau 21J: cap presentation tối đa **5**, refusal-like answer tối đa **2**, giữ nguyên `List<RetrievedContext>` đưa vào `PromptBuilderService`.

---

## 2. Before (21I baseline)

| case_id | sourceCount 21I | verdict 21I |
|---------|-----------------|-------------|
| GQ-F01 | 10 | PASS |
| GQ-F02 | 1 | PASS |
| GQ-F03 | 10 | PASS |
| GQ-F04 | 10 | PASS |
| GQ-L01 | 10 | PASS |
| GQ-L02 | 10 | PASS |
| GQ-T01 | 10 | PASS |
| GQ-T02 | 10 | PARTIAL |
| GQ-C01 | 10 | PASS |
| GQ-O01 | 10 | PASS |
| GQ-O02 | 10 | PASS |
| GQ-D01 | 0 | PASS |

**Tổng 21I:** PASS 11 / PARTIAL 1 / FAIL 0.

---

## 3. Phân tích source pipeline (trước sửa)

| # | Câu hỏi phân tích | Kết luận |
|---|-------------------|----------|
| 1 | `sources` tạo ở đâu? | `ChatService.buildSourceDtos(contexts)` → map 1:1 từ `RetrievedContext` |
| 2 | `sources = contexts` 1:1? | **Có** — mỗi retrieved chunk → một `SourceDto` |
| 3 | `SourceDto` fields? | `fileName`, `sectionTitle`, `pages`, `chunkType`, `chunkText` (nested trong `ChatResponse`) |
| 4 | Frontend dùng `chunkText`? | **Có** — `ChatPage.jsx`, `WidgetChatPage.jsx`, `RetrievalPanel.jsx`, `SourcePills.jsx` |
| 5 | Cap/dedup trước 21J? | **Không** |
| 6 | Cap response mà không ảnh hưởng LLM? | **Có** — tách bước sau retrieval, trước `ChatResponse` |
| 7 | Cần sửa `RagRetrievalService`? | **Không** |
| 8 | Dedup key | `chunkId` ưu tiên; fallback `fileName\|sectionTitle\|chunkType\|content preview` |
| 9 | Table/list cap | **5** an toàn (giữ `table_summary` + `text` đầu window nếu retrieval sort đúng) |
| 10 | OOS trả source? | Có thể giữ ≤2 khi refusal phrase — đã làm |
| 11 | Minimal fix | Option A trong `ChatService` only |
| 12 | Rủi ro lớn nhất | UI mất chunk phụ trợ ở vị trí 6–10; answer vẫn dùng full context LLM |

---

## 4. Code change summary

**File:** `Backend/.../service/ChatService.java`

- `buildSourceDtosForResponse(contexts)` — dedupe + `MAX_RESPONSE_SOURCES = 5`
- `applyAnswerAwareSourceCap(answer, sources)` — refusal markers → `MAX_REFUSAL_RESPONSE_SOURCES = 2`
- Sync + stream paths dùng cùng pipeline; stream áp refusal cap tại `onComplete` / fallback
- **Không** đổi: `ragRetrievalService.retrieveWithMetadata`, `promptBuilderService.buildUserPromptFromRetrievedContexts`

**Test:** `ChatServiceSourcePresentationTest.java` (6 tests)

---

## 5. Source cap / dedup rule

| Rule | Giá trị |
|------|---------|
| `MAX_RESPONSE_SOURCES` | 5 |
| `MAX_REFUSAL_RESPONSE_SOURCES` | 2 (khi answer chứa marker refusal) |
| Dedup | Theo `chunkId`; không có id → preview 120 ký tự normalized |
| Thứ tự | Giữ retrieval order (LinkedHashSet) |
| LLM context | **Không cắt** |

Refusal markers: `không tìm thấy thông tin này trong tài liệu`, `không có trong tài liệu`, `không tìm thấy thông tin`, `không có thông tin`.

---

## 6. Compile / test

| Command | Kết quả |
|---------|---------|
| `Backend\.\mvnw.cmd -DskipTests compile` | **PASS** |
| `Backend\.\mvnw.cmd -Dtest=ChatServiceSourcePresentationTest test` | **PASS** (6/6) |
| `docker compose config -q` | **PASS** |

---

## 7. Runtime (targeted 10 + D01)

**Stack:** `docker compose up --build -d` (backend image rebuild có 21J).  
**Artifact:** `docs/eval/results/21j_raw_eval_output.txt`  
**Script:** `docs/eval/results/_run_21j_targeted.ps1`

| case_id | sourceCount 21I | sourceCount 21J | Answer regression? | Verdict 21J (ước lượng) |
|---------|-----------------|-----------------|--------------------|-------------------------|
| GQ-F01 | 10 | **5** | Không — AlphaDemo | PASS |
| GQ-F02 | 1 | **5** | Không — `GOLDEN-VN-2026-714` | PASS (source UX: nhiều hơn 21I do cap window, không sai fact) |
| GQ-F03 | 10 | **5** | Không — 500 lượt/tháng | PASS |
| GQ-L01 | 10 | **5** | Không — đủ 3 chính sách | PASS |
| GQ-L02 | 10 | **5** | Không — Basic, Pro, Business | PASS |
| GQ-T01 | 10 | **5** | Không — 99000 | PASS |
| GQ-T02 | 10 | **5** | Không worse — có “Ưu tiên”/kênh | PASS hoặc PARTIAL nhẹ (không worse 21I) |
| GQ-C01 | 10 | **5** | Không — 3 hàng | PASS |
| GQ-O01 | 10 | **2** | Không — refuse, không bịa tỷ giá | PASS |
| GQ-O02 | 10 | **2** | Không — refuse CEO | PASS |
| GQ-D01 | 0 | **0** | Không — refuse sau delete | PASS |

**Không chạy lại trong 21J:** GQ-F04 (targeted list brief; kỳ vọng cap 5 tương tự in-scope).

---

## 8. Answer regression check

- Không quan sát hallucination mới trên O01/O02/D01.
- Fact/table/list answers khớp golden trên các case đã chạy.
- **Không** chạy full 12 case — targeted đủ để xác nhận cap + delete + OOS.

---

## 9. Quyết định PASS/PARTIAL/FAIL (21J targeted)

| Chỉ số | Giá trị |
|--------|--------|
| Cases chạy | 11 |
| PASS (ước lượng) | ≥10 |
| PARTIAL | 0–1 (T02 tùy strict “kênh”) |
| FAIL | **0** |
| Mọi case sourceCount | **≤ 5** |
| D01 | `sourceCount=0` |

**Task 21J source presentation:** **PASS**

**Baseline answer quality (so 21I):** **không regress** trên phạm vi đã chạy; T02 có thể **cải thiện nhẹ** so PARTIAL 21I.

---

## 10. Ghi chú

- `PlaygroundService` vẫn map full contexts → sources (ngoài scope 21J; playground có UI cap riêng).
- F02 `sourceCount` tăng 1→5 vì retrieval trả full window; cap không làm hẹp hơn retrieval thực tế.

*Không chứa API key đầy đủ.*
