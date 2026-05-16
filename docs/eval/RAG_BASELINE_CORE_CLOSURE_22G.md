# RAG Baseline Core — Closure & Merge Readiness (Task 22G)

**Phiên bản:** 1.0  
**Ngày:** 2026-05-15  
**Phạm vi:** Tài liệu chốt baseline sau chuỗi task **21D–22F** — **không** thay đổi runtime code.  
**Liên kết:** [RAG_BASELINE_MERGE_CHECKLIST_22G.md](RAG_BASELINE_MERGE_CHECKLIST_22G.md) · [RAG_BASELINE_CORE_CHECKLIST.md](RAG_BASELINE_CORE_CHECKLIST.md) · [RAG_EVALUATION_RUNBOOK.md](RAG_EVALUATION_RUNBOOK.md)

---

## Executive summary

Baseline **core RAG** (ingest → retrieve → chat `/api/chat` → delete safety) đã được xác nhận qua chuỗi verify **21I → 21J → 22A–22F**. Trạng thái chính thức:

| Khía cạnh | Trạng thái |
|-----------|------------|
| Full golden `/api/chat` (mới nhất) | **12 PASS / 0 PARTIAL / 0 FAIL** (22F, adjudicated) |
| Golden trước model controls (21I) | **11 PASS / 1 PARTIAL / 0 FAIL** — functionally PASS |
| OOS / delete safety | **PASS** (không hallucination, không leak sau delete) |
| Source presentation (21J) | **PASS** — cap 5 / refusal 2 / D01 = 0 |
| Model controls (`topK`, `temperature`, `maxTokens`) trên `/api/chat` | **PASS** (22A–22C2 smoke + 22F regression) |
| Playground Compare params (22D) | **PASS** với **known limitations** (không parity topK modelConfig) |
| Merge readiness (core chat path) | **Yes** — với checklist deploy và optional backlog rõ ràng |

**Kết luận baseline:** **PASS** (core path). Các hạng mục optional (compare parity, widget FE LLM params, eval UTF-8) **không** chặn merge nếu product không yêu cầu.

---

## Baseline status (theo mốc task)

### Chuỗi đã hoàn thành

| Task | Nội dung | Kết quả |
|------|----------|---------|
| **21I** | Full golden regression trước model controls | **11 / 1 / 0** — baseline functionally PASS |
| **21J** | Source presentation cleanup (`ChatService` only) | Cap response sources; LLM context không đổi |
| **22A** | FE Playground `topK` → Backend → Qdrant | **PASS** |
| **22B / 22B2** | `modelConfig.topK` fallback runtime | Precedence + clamp **PASS** |
| **22C / 22C2** | `temperature` / `maxTokens` runtime | Precedence + clamp **PASS** |
| **22D** | Playground Compare verify | **PASS** (limitations ghi nhận) |
| **22E** | Compare parity fix | **Skipped** theo yêu cầu user |
| **22F** | `/api/chat` main regression sau model controls | **12 / 0 / 0** adjudicated |

### 22F — bằng chứng mới nhất

- **Evidence:** `docs/eval/results/RAG_CHAT_MAIN_REGRESSION_AFTER_MODEL_CONTROLS_22F_20260515.md`
- Chatbot eval: `modelConfig` `topK=5`, `temperature=0.2`, `maxTokens=256`
- Golden doc: **INDEXED**, `chunkCount=10`
- Model params smoke: MODEL_CONFIG / REQUEST / clamp — **PASS**
- OOS (O01, O02): `sourceCount=2`, không bịa fact
- Delete (D01): `sourceCount=0`, refuse, không leak golden

### So sánh 21I → 22F

| Khía cạnh | 21I | 22F |
|-----------|-----|-----|
| Verdict | 11 / 1 / 0 | **12 / 0 / 0** |
| In-scope `sourceCount` | 10 (pre-21J) | **5** (21J cap — expected) |
| OOS sources | 10 | **2** |
| Model controls runtime | Chưa wire đầy đủ | **PASS** |
| GQ-T02 | PARTIAL (hedge) | **PASS** (UTF-8 recheck) |

**Không regress** safety (OOS, delete). Chất lượng trả lời trên case đã recheck **không tệ hơn** 21I.

---

## Current `/api/chat` flow (sau model controls)

### Entry

| Item | Giá trị |
|------|---------|
| Endpoint | `POST /api/chat` (sync), `POST /api/chat/stream` (SSE) |
| Controller | `ChatController` |
| Auth | Header `X-Widget-Key` → `WidgetAuthFilter` → `Widget-Id` |
| Request DTO | `ChatRequest`: `sessionId`, `message`, optional `topK`, `temperature`, `maxTokens` |

### Pipeline (sync — `ChatService.chat`)

```text
1. Validate message/session (controller)
2. getOrCreateSession(sessionId, widgetId)
3. saveChatMessage(USER)
4. QueryAnalyzerService.analyze(question, widgetId) → QueryType
5. resolveRetrievalTopK(widgetId, request.topK)
     → log [RAG] retrieval topK source=… effective=…
6. RagRetrievalService.retrieveWithMetadata(question, widgetId, effectiveTopK)
     → List<RetrievedContext> (full window — không bị source cap 21J cắt)
7. buildSourceDtosForResponse(contexts) → dedupe + cap ≤5
8. PromptBuilderService.buildUserPromptFromRetrievedContexts(...)
9. resolveLlmGenerationOptions(widgetId, request)
     → log [LLM] generation options tempSource=… effectiveTemperature=…
10. LlmFallbackService.generateWithFallback(messages, effective options)
11. applyAnswerAwareSourceCap(answer, sources) → refusal ≤2
12. saveChatMessage(ASSISTANT) + ChatResponse
```

**Stream path** (`chatStream`): cùng precedence topK/LLM; source cap refusal áp tại `onComplete` / fallback.

### Classes chính (read-only reference)

- `ChatService` — orchestration, precedence, source cap, logging
- `RagRetrievalService` — vector search + heading lock + context assembly
- `QueryAnalyzerService` — query type / heading hints
- `PromptBuilderService` — prompt từ full retrieved contexts
- `LlmFallbackService` / `LlmGenerationOptions` — Groq generation + clamp
- `WidgetService` — parse `ui_config.modelConfig.*` (persisted only cho runtime tier)

---

## Current upload / index / delete / retry flow

### Upload & index

| Bước | API / component | Ghi chú |
|------|-----------------|--------|
| Upload | `POST /api/documents/upload` (canonical) hoặc `POST /api/documents/upload/{widgetId}` (legacy) | Multipart `files`; tenant `chatbotId` / `widgetId` |
| Validate | `DocumentService.validateUploadableFile` | Chỉ `.pdf`, `.txt` (không `.md`) |
| Persist file | `app.upload-dir` | Document row `PENDING` |
| Parse | `DocumentParserService` | Markdown `##` headings (sau 21D+) |
| Chunk | `ChunkingService2` | text / table chunks |
| Embed + Qdrant | `EmbeddingService` | Points filter `widgetId` + `document_id` |
| Status | `GET /api/documents/{id}/status` | `INDEXED`, `chunkCount` |

**Golden eval:** upload `docs/eval/RAG_GOLDEN_TEST_DOCUMENT.txt` (copy từ `.md` per runbook).

### Delete

| Bước | Hành vi |
|------|---------|
| `DELETE /api/documents/{id}` | `DocumentService.softDeleteDocument` |
| Qdrant | `QdrantPurgeService.purgeDocumentVectors` — **fail thì không soft-delete** |
| MySQL | Soft-delete document + children (`deleted_at`) |
| Chat sau delete | Retrieval không load chunk đã xóa; GQ-D01: refuse, `sourceCount=0` |

### Retry (optional ops)

| API | Điều kiện |
|-----|-----------|
| `POST /api/documents/{id}/retry` | Chỉ `DocumentStatus.FAILED` |
| Hành vi | Purge Qdrant → hard-delete children DB → re-run pipeline từ file trên disk |

---

## Model controls (runtime constants)

Nguồn: `RagRetrievalService`, `LlmGenerationOptions`, `ChatService`, `WidgetService`.

### topK (vector anchor / Qdrant search limit)

| Constant | Giá trị | Class |
|----------|--------:|-------|
| Default (runtime, no request & no persist) | **30** | `RagRetrievalService.DEFAULT_ANCHOR_TOP_K` |
| Min | **1** | `MIN_ANCHOR_TOP_K` |
| Max (clamp) | **30** | `MAX_ANCHOR_TOP_K` |

**Lưu ý:** `WidgetService.DEFAULT_MODEL_CONFIG` có `topK: 5` cho **GET merge UI** — runtime `/api/chat` **không** dùng merge default khi DB không persist; tier DEFAULT → effective **30** (22B2).

### temperature

| Constant | Giá trị | Class |
|----------|--------:|-------|
| Default (runtime) | **0.1** | `LlmGenerationOptions.DEFAULT_TEMPERATURE` |
| Min / Max | **0.0** / **1.0** | `MIN_TEMPERATURE` / `MAX_TEMPERATURE` |
| GET merge UI (không dùng runtime tier) | **0.7** | `WidgetService.DEFAULT_MODEL_CONFIG` |

### maxTokens

| Constant | Giá trị | Class |
|----------|--------:|-------|
| Default (runtime) | **1500** | `LlmGenerationOptions.DEFAULT_MAX_TOKENS` |
| Min / Max | **64** / **4096** | `MIN_MAX_TOKENS` / `MAX_MAX_TOKENS` |
| GET merge UI (không dùng runtime tier) | **1024** | `WidgetService.DEFAULT_MODEL_CONFIG` |

### FE wiring (Playground)

- `ModelOverridePanel.jsx` → `playgroundApi.chat` gửi `overrideParams.topK`, `temperature`, `maxTokens`
- Widget / `ChatPage` — **không** gửi `topK`/LLM params qua `/api/chat` (optional backlog)

---

## Precedence rules (`/api/chat`)

Thứ tự áp dụng cho **mỗi** tham số độc lập:

| Tier | topK | temperature / maxTokens |
|------|------|-------------------------|
| 1 | `ChatRequest.topK` | `ChatRequest.temperature` / `maxTokens` |
| 2 | `ui_config.modelConfig.topK` (persist) | `modelConfig.temperature` / `maxTokens` (persist) |
| 3 | Backend default + clamp | `LlmGenerationOptions` default + clamp |

**Implementation:** `ChatService.resolveTopK`, `ChatService.resolveLlmGeneration` → `LlmGenerationOptions.normalized`.

**Clamp ví dụ (đã verify 22C2 / 22F):**

- `topK=999` → effective **30**
- `temperature=999` → **1.0**
- `maxTokens=999999` → **4096**

**Log evidence:**

- `[RAG] retrieval topK source=REQUEST|MODEL_CONFIG|DEFAULT … effective=…`
- `[LLM] generation options tempSource=… maxTokensSource=… effectiveTemperature=… effectiveMaxTokens=…`

**Khi request có giá trị:** configured tier **không load** cho field đó (log `configured=null`) — đúng thiết kế 22B/22C.

---

## Source presentation (21J)

| Scenario | Max `sources` in response | LLM context |
|----------|---------------------------|-------------|
| Fact / list / table in-scope | **5** | Full retrieved contexts (không cắt) |
| Refusal / OOS-like answer | **2** | Full contexts |
| After delete (GQ-D01) | **0** | N/A — refuse |

**Constants:** `ChatService.MAX_RESPONSE_SOURCES = 5`, `MAX_REFUSAL_RESPONSE_SOURCES = 2`.

**Dedup:** `chunkId` ưu tiên; fallback preview 120 ký tự.

**Không đổi:** `RagRetrievalService.retrieveWithMetadata`, `PromptBuilderService` input contexts.

---

## Golden evaluation status (mới nhất)

| Run | Ngày | PASS / PARTIAL / FAIL | Ghi chú |
|-----|------|----------------------|---------|
| **22F** (authoritative) | 2026-05-15 | **12 / 0 / 0** | Sau model controls; source cap 5/2/0 |
| 21I | 2026-05-15 | 11 / 1 / 0 | Pre-21J source cap; pre-full model controls |
| 21C (historical) | 2026-05-14 | 6 / 2 / 4 | Trước parser 21D — **không** dùng làm merge gate |

**Case set:** `docs/eval/RAG_GOLDEN_QUESTIONS.md` (12 case: F01–F04, L01–L02, T01–T02, C01, O01–O02, D01).

**Caveat automation:** PowerShell eval script có thể mojibake tiếng Việt — 22F dùng UTF-8 recheck cho case nghi ngờ (`22f_raw_eval_output.txt`, `_run_22f_main_regression.ps1`).

---

## Known limitations (accepted for merge)

| # | Limitation | Evidence | Block merge? |
|---|------------|----------|--------------|
| L1 | Playground Compare: thiếu `topK` trong config **không** fallback `modelConfig.topK` → effective **30** | 22D §7 | **No** (22E skipped) |
| L2 | Compare path **không** log `[LLM] generation options` (chỉ topK qua `RagRetrievalService`) | 22D | **No** |
| L3 | Widget FE **không** gửi `temperature`/`maxTokens` trên `/api/chat` | 22C2 report | **No** — optional product |
| L4 | GET chatbot `modelConfig` merge UI default (0.7/1024/topK 5) **≠** runtime DEFAULT tier (0.1/1500/30) | 22B2, 22C2 | **No** — cần đọc DB khi debug |
| L5 | Eval scripts UTF-8 / BOM cho câu hỏi tiếng Việt | 22F §11 | **No** — vận hành eval |
| L6 | Không full production load / latency benchmark | Scope 21D0 G6 | **No** |
| L7 | Một số answer vẫn verbose (chain-of-thought style) | 22F report | **No** — quality tuning optional |
| L8 | Qdrant deep count không chạy mọi smoke | 22F | **No** |

---

## Merge readiness checklist (tóm tắt)

Chi tiết bảng: **[RAG_BASELINE_MERGE_CHECKLIST_22G.md](RAG_BASELINE_MERGE_CHECKLIST_22G.md)**.

Trước merge PR nhánh `21D–22F`:

1. **G0** — `mvnw -DskipTests compile`; unit tests regression (34 tests LLM+topK+source); `docker compose config -q`
2. **G1** — Golden upload INDEXED, `chunkCount=10`, sections 1–7 + parser `##`
3. **G2** — Full golden 22F **12/0/0**; OOS/delete/source cap
4. **G3** — Model controls smoke (request / modelConfig / clamp) trên `/api/chat`
5. **G4** — Safety: no OOS hallucination, no delete leak, no secrets in docs
6. **G5** — Acknowledge known limitations L1–L8

---

## Deploy checklist (production)

1. **Env:** `GROQ_API_KEY`, `NOMIC_API_KEY` present; không log full keys.
2. **Stack:** `docker compose up -d` (hoặc profile `docker`); MySQL healthy; Qdrant reachable; collection `documents` tồn tại.
3. **Build:** Backend image chứa code 22A–22C + 21J (rebuild nếu image cũ).
4. **Smoke post-deploy:**
   - `GET /api/chatbots?page=0&size=1` → 200
   - Upload sample hoặc golden txt → INDEXED
   - `POST /api/chat` fact question → answer + `sourceCount` ≤ 5
   - OOS question → refuse + `sourceCount` ≤ 2
5. **Không** recreate Qdrant collection / migration backfill trong deploy routine này.
6. **Monitor:** Groq rate limit; disk log; RAM ~1.5GB constraint (20 users / 5 concurrent).

---

## Rollback notes

| Tình huống | Hành động |
|------------|-----------|
| Regression chat quality sau deploy | Revert commit/ image tới tag trước 22A–22F; **không** xóa MySQL/Qdrant data tự động |
| Model controls gây lỗi Groq | Tạm set request omit + clear `modelConfig` LLM fields → fallback DEFAULT 0.1/1500/30 |
| Source cap quá aggressive cho UI | Revert chỉ `ChatService` 21J (tách PR) — hiếm vì 22F PASS |
| Parser/ingest regression | Revert `DocumentParserService` / chunking; **re-ingest document mới** (không reuse doc cũ cho G1) |
| Compare-only issues | Rollback không bắt buộc cho production widget path |

**Data:** Soft-delete documents vẫn an toàn; rollback code không purge vectors trừ khi gọi DELETE explicit.

---

## Optional future tasks (chỉ khi product yêu cầu)

Các hạng mục sau **không** tự triển khai sau 22G:

| ID | Task | Lý do optional |
|----|------|----------------|
| OPT-01 | Playground Compare parity: `modelConfig.topK` fallback + LLM runtime log (22E) | Product chưa cần A/B parity với `/api/chat` |
| OPT-02 | Widget FE gửi `temperature` / `maxTokens` (và optional `topK`) | Admin/playground đủ cho tuning |
| OPT-03 | Eval script UTF-8 cố định (BOM, `charset=utf-8`) | Giảm adjudication thủ công |
| OPT-04 | Periodic golden eval (CI hoặc lịch) | Regression guard dài hạn |
| OPT-05 | Production load / latency benchmark | Ngoài baseline core |
| OPT-06 | Giảm verbosity answer / prompt tuning | Quality, không blocker |
| OPT-07 | Rerank / hybrid table phase | Sau khi product cần vượt baseline |

---

## Evidence index (21D–22F)

| Task | Result doc | Report |
|------|------------|--------|
| 21I | `docs/eval/results/RAG_FULL_GOLDEN_REGRESSION_21I_20260515.md` | `reports/refactor/CURSOR_REPORT_21I_FULL_GOLDEN_REGRESSION.md` |
| 21J | `docs/eval/results/RAG_SOURCE_PRESENTATION_CLEANUP_21J_20260515.md` | `reports/refactor/CURSOR_REPORT_21J_SOURCE_PRESENTATION_CLEANUP_FIX.md` |
| 22A | `docs/eval/results/RAG_FE_TOPK_WIRING_22A_20260515.md` | `reports/refactor/CURSOR_REPORT_22A_FE_TOPK_WIRING.md` |
| 22B2 | `docs/eval/results/RAG_MODEL_CONFIG_TOPK_RUNTIME_VERIFY_22B2_20260515.md` | `reports/refactor/CURSOR_REPORT_22B2_MODEL_CONFIG_TOPK_RUNTIME_VERIFY.md` |
| 22C2 | `docs/eval/results/RAG_LLM_PARAMS_RUNTIME_VERIFY_22C2_20260515.md` | `reports/refactor/CURSOR_REPORT_22C2_LLM_PARAMS_RUNTIME_VERIFY.md` |
| 22D | `docs/eval/results/RAG_PLAYGROUND_COMPARE_PARAMS_RUNTIME_VERIFY_22D_20260515.md` | `reports/refactor/CURSOR_REPORT_22D_PLAYGROUND_COMPARE_PARAMS_RUNTIME_VERIFY.md` |
| 22F | `docs/eval/results/RAG_CHAT_MAIN_REGRESSION_AFTER_MODEL_CONTROLS_22F_20260515.md` | `reports/refactor/CURSOR_REPORT_22F_CHAT_MAIN_REGRESSION_AFTER_MODEL_CONTROLS.md` |

---

*Tài liệu closure 22G — baseline core RAG chính thức PASS cho merge với checklist và limitations đã ghi nhận.*
