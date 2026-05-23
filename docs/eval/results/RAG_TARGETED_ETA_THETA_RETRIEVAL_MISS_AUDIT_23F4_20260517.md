# RAG Targeted Eta/Theta Retrieval Miss Audit 23F4

## 1. Goal

Điều tra vì sao query tập trung Eta/Theta (và aggregate 8 policy codes) không trả lời đủ **ETA-777** / **THETA-888** dù tài liệu test đã index đầy đủ, và xác định **stage** nào trong pipeline làm mất thông tin.

## 2. Scope

- **VERIFY / DIAGNOSIS only** — không đổi production behavior, không đổi semantics Top-K, không sửa parser/retrieval/PromptBuilder.
- Tái sử dụng chatbot + document từ task **23F3** (cùng corpus đã INDEXED).
- Runtime: Docker stack local (`docker compose up -d`), backend `http://localhost:8080`.

| Item | Value |
|------|-------|
| CHATBOT_ID | `bed4feee-2946-43a9-8238-b1a04f7c6cac` |
| DOCUMENT_ID | `ee587096-4fe9-48c3-b197-ff98c6045cbb` |
| chunkCount | 29 |
| Test doc | `docs/eval/manual/TOPK_LOW_HIGH_DIFFERENCE_TEST_DOCUMENT.txt` |

---

## 3. Files read

| Path | Purpose |
|------|---------|
| `docs/eval/manual/TOPK_LOW_HIGH_DIFFERENCE_TEST_DOCUMENT.txt` | Source doc — xác nhận ETA-777 / THETA-888 |
| `docs/eval/results/RAG_TOPK_LOW_HIGH_DIFFERENCE_TEST_23F3_20260517.md` | Baseline 6/8 codes, finalContexts=20 |
| `docs/eval/results/_run_23f3_low_high_diff_test.ps1` | Setup IDs, playground runner |
| `docs/eval/results/_run_23f3_results.json` | Meta chatbot/document |
| `Backend/.../RagRetrievalService.java` | `FINAL_LIMIT_EXPANDED=20`, `dedupeSortBudget`, expansion |
| `Backend/.../QueryAnalyzerService.java` | `LIST_ALL` / `TABLE_LOOKUP` detection |
| `Backend/.../ChatService.java` | Sources = subset of `contexts`; refusal cap = 2 |
| `Backend/.../PlaygroundController.java` | `playgroundDebugSources=true`, cap = effectiveTopK |
| `Backend/.../EmbeddingService.java` | Qdrant payload field `text_segment` |
| `Backend/.../PromptBuilderService.java` | Prompt built from same `contexts` list |

---

## 4. Files created/modified

| Path | Action |
|------|--------|
| `docs/eval/results/_run_23f4_eta_theta_audit.ps1` | **Created** — runner matrix Q1–Q5 + Q1 topK sweep |
| `docs/eval/results/_run_23f4_results.json` | **Created** — raw matrix JSON |
| `docs/eval/results/RAG_TARGETED_ETA_THETA_RETRIEVAL_MISS_AUDIT_23F4_20260517.md` | **Created** — this report |
| `reports/refactor/CURSOR_REPORT_23F4_TARGETED_ETA_THETA_RETRIEVAL_MISS_AUDIT.md` | **Created** — Cursor summary |

**Temporary debug log:** Không thêm — log `[RAG]` hiện có đủ (`dedupeSortBudget`, `Budget: finalLimit`, `Context chunk list`).

**Production Java/Frontend:** Không sửa.

---

## 5. Source document evidence

| Code | Exists in source doc? | Section | Text preview |
|------|----------------------|---------|--------------|
| ETA-777 | **Yes** | `# Section Eta Policy` (lines 103–106) | `Mã chính sách Eta là ETA-777. Điều kiện Eta: chỉ áp dụng cho sinh viên học song ngành. Ghi chú Eta: hồ sơ phải được cả hai khoa xác nhận.` |
| THETA-888 | **Yes** | `# Section Theta Policy` (lines 120–123) | `Mã chính sách Theta là THETA-888. Điều kiện Theta: chỉ áp dụng cho sinh viên có học bổng. Ghi chú Theta: phải duy trì điểm trung bình theo quy định.` |

Vị trí trong corpus 29 chunks: policy codes nằm ở **chunkIndex 24** (Eta) và **28** (Theta); giữa chúng có noise sections 19–21.

---

## 6. DB chunk evidence

API: `GET /api/documents/{documentId}/chunks` (29 rows).

| Code | Exists in DB? | chunkId | chunkIndex | sectionTitle | preview (200 chars) |
|------|---------------|---------|------------|--------------|---------------------|
| ETA-777 | **Yes** | `8646b50a-93af-4158-82de-67929aa57403` | **24** | Section Eta Policy (via Qdrant payload; API chỉ trả content) | `Mã chính sách Eta là ETA-777. Điều kiện Eta: chỉ áp dụng cho sinh viên học song ngành...` |
| THETA-888 | **Yes** | `e661d650-87b5-49cf-ba36-729788f96ced` | **28** | Section Theta Policy | `Mã chính sách Theta là THETA-888. Điều kiện Theta: chỉ áp dụng cho sinh viên có học bổng...` |

**Kết luận:** Không phải parser/DB indexing miss.

---

## 7. Qdrant payload evidence

Collection: `documents`. Filter `document_id = ee587096-4fe9-48c3-b197-ff98c6045cbb` → **29 points**.

Payload text nằm ở key **`text_segment`** (không phải `text`/`content`).

| Code | Exists in Qdrant? | pointId | chunkId | chunkIndex | sectionTitle | preview |
|------|-------------------|---------|---------|------------|--------------|---------|
| ETA-777 | **Yes** | `59848289-0927-4012-9b72-1cf107b381b2` | `8646b50a-93af-4158-82de-67929aa57403` | 24 | Section Eta Policy | `... Mã chính sách Eta là ETA-777 ...` |
| THETA-888 | **Yes** | `428b2de5-a819-425e-a7fa-616a991ab51d` | `e661d650-87b5-49cf-ba36-729788f96ced` | 28 | Section Theta Policy | `... Mã chính sách Theta là THETA-888 ...` |

**Kết luận:** Không phải Qdrant upsert miss.

---

## 8. Targeted query matrix

**Mapping log:** `sec_idx_24` = Eta policy chunk, `sec_idx_28` = Theta policy chunk.

**Q1** = `Liệt kê mã chính sách của Eta và Theta.`

| Query | topK | effectiveTopK | queryType | vectorHasEta* | vectorHasTheta* | finalHasEta (sec_idx_24) | finalHasTheta (sec_idx_28) | promptHasEta† | promptHasTheta† | answerHasEtaCode | answerHasThetaCode | Notes |
|-------|------|---------------|-----------|-----------------|-----------------|--------------------------|----------------------------|---------------|-----------------|------------------|--------------------|-------|
| Q1 | 20 | 20 | LIST_ALL | likely‡ | likely‡ | **No** | **No** | **No** | **No** | No | No | `input=25 → output=20`; **5 chunks dropped** (24–28); answer refusal |
| Q2 | 20 | 20 | TABLE_LOOKUP | likely‡ | likely‡ | **No** | **No** | **No** | **No** | No | No | Misclassified `TABLE_LOOKUP` (pattern ` ma `); same budget cut |
| Q3 | 20 | 20 | NORMAL_FACT | Yes | Yes | Yes (locked) | Yes (1 ctx) | Yes | Yes | No | **Yes** | Heading lock → `sec_idx_28` only; THETA-888 OK |
| Q4 | 20 | 20 | SECTION_SUMMARY | Yes | Yes | No | Yes (locked) | No | Yes | No | **Yes** | Lock Theta section only |
| Q5 | 20 | 20 | SECTION_SUMMARY | Yes | Yes | No | Yes (1 ctx) | No | Yes | No | No | Lock chỉ Theta; không đủ 2 section |
| Q1 | 1 | 1 | LIST_ALL | Yes | Yes | **Yes** | **Yes** | **Yes** | **Yes** | **Yes** | **Yes** | `output=17`; final list includes 24 & 28 |
| Q1 | 3 | 3 | LIST_ALL | Yes | Yes | **Yes** | **Yes** | **Yes** | **Yes** | **Yes** | **Yes** | `output=18` |
| Q1 | 10 | 10 | LIST_ALL | Yes | Yes | **Yes** | **No** | **Yes** | **No** | **Yes** | No | Final ends `sec_idx_26`; Theta cut |
| Q1 | 20 | 20 | LIST_ALL | Yes | Yes | **No** | **No** | **No** | **No** | No | No | Final ends `sec_idx_23`; Eta+Theta cut |

\*Vector stage: không log từng `chunkId`; suy ra từ expansion pool 25 unique (topK=20) — vector anchors có thể đã chạm Eta/Theta trước khi sort/cap.

†Prompt dùng cùng `List<RetrievedContext>` sau `dedupeSortBudget` — đồng bộ với final contexts.

‡Pool expanded 25 chunks → vector + section expansion đã đưa Eta/Theta vào pool trước cap.

### Evidence log — Q1 topK=20 (fail)

```text
[RAG] Detected intent: ... queryType=LIST_ALL
[RAG] Vector anchors: chunks=20 sections=20 ...
[RAG] dedupeSortBudget: input=25 unique=25 output=20 totalChars=2641 limit=20/32000 locked=false
[RAG] Budget: finalLimit=20 reached; 5 chunks excluded by count limit
[RAG] Context chunk list: [sec_idx_0[text], ... sec_idx_23[text]]
```

**Excluded by cap:** `sec_idx_24` (Eta), `sec_idx_25`, `sec_idx_26`, `sec_idx_27`, `sec_idx_28` (Theta) — đúng 5 chunk.

### Evidence log — Q1 topK=1 (pass)

```text
[RAG] Vector anchors: chunks=1 ...
[RAG] dedupeSortBudget: input=17 unique=17 output=17 ...
[RAG] Context chunk list: [sec_idx_0[text], ... sec_idx_24[text], sec_idx_25[text], sec_idx_26[text], sec_idx_27[text], sec_idx_28[text]]
```

Answer: `Mã chính sách của Eta là ETA-777 và mã chính sách của Theta là THETA-888.`

### Liên hệ task 23F3 (aggregate 8 codes)

Cùng cơ chế: `LIST_ALL` + expansion → pool ≥25 → **`FINAL_LIMIT_EXPANDED=20`** + sort theo `(section_order, order_index)` → giữ **sec_idx_0…23**, loại **Zeta (20)** có thể vào tùy anchor mix; **luôn loại Eta (24) và Theta (28)** khi pool đủ lớn → **6/8 codes** trên A2.

---

## 9. UI sources vs prompt check

| Câu hỏi | Kết quả |
|---------|---------|
| UI Sources lấy từ đâu? | `ChatService.buildSourceDtosForResponse(contexts, cap)` — cùng list `contexts` sau retrieval, **không** phải toàn bộ Qdrant/DB. |
| Có đồng bộ với prompt? | **Cùng nguồn `contexts`**, nhưng **không cùng cardinality**: Playground cap sources = `effectiveTopK`; prompt nhận **toàn bộ** final contexts (tới 20). |
| Refusal answer? | `applyAnswerAwareSourceCap` → tối đa **2** sources khi answer chứa `không tìm thấy` — UI càng không phản ánh đủ 20 context trong prompt. |
| User thấy chunk Eta/Theta ở UI? | Nếu xem **Document chunks** (`/api/documents/{id}/chunks`) → luôn thấy 29 chunks — **không** đồng nghĩa LLM nhận chunk đó. |
| Q1 topK=20: UI có Eta/Theta? | **Không** (`sourcesHasEta/Theta=false`, 2 sources sau refusal cap). Prompt 20 ctx cũng **không** có sec_idx_24/28. |

**Kết luận UI:** “UI thấy chunk Eta/Theta” (document viewer) **≠** “LLM nhận Eta/Theta trong prompt”. Với Q1 topK=20, **cả prompt lẫn sources đều thiếu** — không phải CASE G thuần; CASE G chỉ áp dụng khi so số lượng sources vs prompt (vd Q1 topK=1: **17 prompt ctx, 1 source**).

---

## 10. Root cause

### **CASE E** (primary) — expansion + `dedupeSortBudget` + `FINAL_LIMIT_EXPANDED=20` loại Eta/Theta

- Query `LIST_ALL` (và aggregate tương tự) kích hoạt expansion → pool **25** unique chunks trên doc 29 chunk.
- `dedupeSortBudget` sort theo thứ tự tài liệu `(section_order, order_index)` rồi **cắt 20** — **không** ưu tiên section khớp query.
- Chunk **24 (Eta)** và **28 (Theta)** nằm cuối dãy → **bị loại** khi topK cao (nhiều vector anchor → pool rộng → cắt đuôi).
- topK **thấp** (1–3): ít anchor → expansion “xuyên” tới cuối doc → **24 & 28 còn trong final** → LLM trả đúng mã.

### **CASE F** (secondary) — LLM refusal khi context thiếu

- Khi final context không có Eta/Theta, answer: `Tôi không tìm thấy thông tin này trong tài liệu` (đúng hành vi với context rỗng phần đó).

### **CASE H** (minor) — Mixed

- **Q2** bị classify `TABLE_LOOKUP` (false positive từ token `ma`) — cùng budget cut, không phải nguyên nhân gốc indexing.
- **Q5** heading lock chỉ Theta → thiếu Eta trong context dù có mention cả hai section.

### Loại trừ

| Case | Verdict |
|------|---------|
| A — Source doc thiếu | **Rejected** |
| B — DB chunks thiếu | **Rejected** |
| C — Qdrant thiếu | **Rejected** |
| D — Vector không retrieve | **Partial** — vector/expansion đưa vào pool; mất ở **final cap** |
| G — UI có nhưng prompt không | **Partial** — document viewer ≠ chat; refusal cap 2 sources |

---

## 11. Recommendation

1. **Task fix retrieval budget (không đổi Top-K vector semantics):**
   - Với `LIST_ALL` / expanded query: tăng `FINAL_LIMIT_EXPANDED` **hoặc** sort/cap theo **relevance** (rerank score / query term overlap) thay vì chỉ document order.
   - Hoặc: sau expansion, **ưu tiên giữ** chunks có lexical match entity names trong query (`eta`, `theta`, policy codes).

2. **Task QueryAnalyzer (riêng):** tránh `TABLE_LOOKUP` cho câu “Mã kiểm tra riêng của Eta…” (pattern ` ma ` quá rộng).

3. **Task heading lock (riêng):** Q5 cần lock **cả hai** section khi query nêu rõ hai section titles.

4. **Task source presentation (riêng):** document viewer vs chat sources; refusal cap 2 vs prompt 20 — hiển thị rõ cho debug.

5. **Không** gộp vào task “Top-K final context semantics” — vector topK đã hoạt động; vấn đề là **final context budget + sort order** sau expansion.

---

## 12. Commands run

| Command | Result |
|---------|--------|
| `docker compose up -d` | PASS — stack started |
| `GET /api/chatbots/{23F3-id}` | PASS — chatbot exists |
| `GET /api/documents/{docId}/chunks` | PASS — 29 chunks, ETA/THETA present |
| `POST http://localhost:6333/collections/documents/points/scroll` | PASS — 29 points, ETA/THETA in `text_segment` |
| `docs/eval/results/_run_23f4_eta_theta_audit.ps1` | PASS — matrix + `_run_23f4_results.json` |
| `docker logs chatbot-backend` (grep RAG) | PASS — budget/context list evidence |

---

## 13. No-code-change confirmation

**Xác nhận:** Không sửa production Java/Frontend behavior trong task 23F4. Chỉ thêm script verify + báo cáo.
