# RAG Baseline — Merge / Deploy Checklist (Task 22G)

**Phiên bản:** 1.0  
**Ngày:** 2026-05-15  
**Closure doc:** [RAG_BASELINE_CORE_CLOSURE_22G.md](RAG_BASELINE_CORE_CLOSURE_22G.md)

**Cách dùng:** Điền **Status** khi chạy trước merge/deploy: `PASS` | `FAIL` | `NOT_RUN` | `N/A` | `ACCEPTED` (known limitation).  
**Evidence:** link file result hoặc command output — **không** dán API key đầy đủ.

---

## G0 — Build / Test

| ID | Check | Expected | Status | Evidence | Notes |
|----|-------|----------|--------|----------|-------|
| G0-001 | Backend compile | `cd Backend && .\mvnw.cmd -DskipTests compile` exit 0 | PASS | 22F §2, 22C2 §2 | Bắt buộc nếu PR có Java |
| G0-002 | Regression unit tests (LLM + topK + source) | 34 tests PASS: `LlmGenerationOptionsTest`, `ChatServiceLlmParamsTest`, `RetrievalTopKTest`, `ChatServiceModelConfigTopKTest`, `ChatServiceSourcePresentationTest` | PASS | 22F §2, 22C2 §2 | |
| G0-003 | `docker compose config -q` | Exit 0, không lỗi interpolate | PASS | 21I, 22F, 22C2 | Không in secret |
| G0-004 | Frontend lint (nếu PR đụng FE) | `npm run lint` exit 0 | NOT_RUN | — | 22G docs-only; chạy khi merge nhánh có FE |
| G0-005 | Frontend build (nếu PR đụng FE) | `npm run build` exit 0 | NOT_RUN | 22A FE wiring | Playground topK/LLM UI |
| G0-006 | Widget build (nếu PR đụng widget) | `npm run build:widget` exit 0 | NOT_RUN | — | Widget không gửi LLM params |
| G0-007 | Scope PR — một baseline feature set | Diff tập trung 21D–22F RAG core + model controls | PASS | `git diff --stat` ~21 files Java/FE | Không drive-by refactor |

---

## G1 — Ingest / Metadata

| ID | Check | Expected | Status | Evidence | Notes |
|----|-------|----------|--------|----------|-------|
| G1-001 | Golden upload | `POST /api/documents/upload` + `RAG_GOLDEN_TEST_DOCUMENT.txt` → HTTP 200 | PASS | 22F §3, 21I §3 | `.txt` only |
| G1-002 | Document INDEXED | `GET …/status` → `INDEXED`, `progress=100` | PASS | 22F: chunkCount **10** | |
| G1-003 | chunkCount | **10** (golden sau parser 21D+) | PASS | 22F, 21I | Ghi số thực tế nếu parser đổi |
| G1-004 | Markdown `##` sections | Sections mục 2–7 + General (không false list root) | PASS | 21I §5, 21E checklist | Parser `DocumentParserService` |
| G1-005 | Section Quy trình (mục 3) | ≥1 section đúng heading quy trình | PASS | 21I metadata | F04 dependency |
| G1-006 | Section Bảng gói (mục 4) | Table chunks → section bảng | PASS | 21I chunk types | L02, T01, T02 |
| G1-007 | Qdrant count = chunkCount | Filter `document_id` + `widgetId` → count = 10 | PASS | 21I §4 | 22F: NOT RUN deep — không block |
| G1-008 | Không false section từ numbered list | 0 pseudo-section `1. Phản hồi…` làm root | PASS | 21E G1 rows | Post-21D1 |

---

## G2 — Chat Quality (full golden)

| ID | Check | Expected | Status | Evidence | Notes |
|----|-------|----------|--------|----------|-------|
| G2-001 | Full golden 22F total | **12 PASS / 0 PARTIAL / 0 FAIL** (adjudicated) | PASS | `RAG_CHAT_MAIN_REGRESSION_…_22F_20260515.md` §6 | Authoritative |
| G2-002 | GQ-F01–F04 fact | PASS, in-scope sources ≤5 | PASS | 22F table | |
| G2-003 | GQ-L01–L02 list | PASS | PASS | 22F | |
| G2-004 | GQ-T01–T02 table | PASS (T02 improved vs 21I) | PASS | 22F | UTF-8 recheck T02 |
| G2-005 | GQ-C01 count | PASS — 3 gói | PASS | 22F | |
| G2-006 | GQ-O01 OOS | PASS — không bịa tỷ giá; sources ≤2 | PASS | 22F | |
| G2-007 | GQ-O02 OOS | PASS — không bịa CEO; sources ≤2 | PASS | 22F | |
| G2-008 | GQ-D01 delete | PASS — refuse; **sourceCount=0** | PASS | 22F §7 | Sau `DELETE` golden doc |
| G2-009 | Không chat crash | HTTP 200 mọi case; 0 crash | PASS | 22F | |
| G2-010 | So sánh 21I không regress safety | OOS/delete vẫn PASS | PASS | 22F §8 | |

---

## G3 — Model Controls (`/api/chat`)

| ID | Check | Expected | Status | Evidence | Notes |
|----|-------|----------|--------|----------|-------|
| G3-001 | topK request override | `source=REQUEST`, effective = request (clamp ≤30) | PASS | 22B2, 22F smoke | e.g. 10 → 10 |
| G3-002 | topK modelConfig fallback | Persist `topK=5`, omit request → `MODEL_CONFIG effective=5` | PASS | 22B2 case A, 22F | |
| G3-003 | topK default (no persist) | Omit request + no DB topK → `DEFAULT effective=30` | PASS | 22B2 case B | GET API có thể show 5 — không dùng runtime |
| G3-004 | topK clamp | request 999 → effective **30** | PASS | 22B2 #3, 22F | |
| G3-005 | temperature request override | `tempSource=REQUEST`, effective 0.7 | PASS | 22C2 #2, 22F | |
| G3-006 | temperature modelConfig fallback | Persist 0.2 → `MODEL_CONFIG effective=0.2` | PASS | 22C2 #1, 22F | |
| G3-007 | temperature default | No persist → `DEFAULT effective=0.1` | PASS | 22C2 #4 | |
| G3-008 | temperature clamp | 999 → **1.0** | PASS | 22C2 #3, 22F | |
| G3-009 | maxTokens request override | `maxTokensSource=REQUEST`, effective 512 | PASS | 22C2 #2 | |
| G3-010 | maxTokens modelConfig fallback | Persist 256 → effective 256 | PASS | 22C2 #1, 22F | |
| G3-011 | maxTokens default | No persist → **1500** | PASS | 22C2 #4 | |
| G3-012 | maxTokens clamp | 999999 → **4096** | PASS | 22C2 #3, 22F | |
| G3-013 | Playground FE topK wired | overrideParams.topK → backend log effective | PASS | 22A result | Compare path khác — 22D |
| G3-014 | Golden run dùng modelConfig fallback | 22F golden omit request params → MODEL_CONFIG 5/0.2/256 | PASS | 22F §5–6 | |

---

## G4 — Safety

| ID | Check | Expected | Status | Evidence | Notes |
|----|-------|----------|--------|----------|-------|
| G4-001 | No OOS hallucination | O01/O02 không bịa fact ngoài doc | PASS | 22F: 0 hallucination | |
| G4-002 | No delete leak | D01 không cite golden sau delete | PASS | 22F, 21I §7 | |
| G4-003 | No source leak after delete | `sourceCount=0` on D01 | PASS | 22F | |
| G4-004 | Qdrant 0 after delete (eval) | count = 0 post-DELETE | PASS | 21I §4 | Chạy lại nếu deploy mới tenant |
| G4-005 | No secrets in eval docs/reports | Keys masked `***` only | PASS | 22F, 22C2 env tables | Review PR attachments |
| G4-006 | Source cap không làm leak nội dung doc đã xóa | Retrieval exclude soft-deleted chunks | PASS | `DocumentService.softDeleteDocument` + 22F D01 | |

---

## G5 — Known Limitations Accepted

| ID | Check | Expected | Status | Evidence | Notes |
|----|-------|----------|--------|----------|-------|
| G5-001 | Playground compare topK parity skipped | Missing compare topK → 30 not modelConfig 5 — **accepted** | ACCEPTED | 22D §7; 22E skipped | OPT-01 |
| G5-002 | Compare LLM params no runtime log | Code path OK; no `[LLM]` log on compare | ACCEPTED | 22D §3, §5 | OPT-01 |
| G5-003 | Widget FE no temperature/maxTokens | `/api/chat` from widget uses DEFAULT/MODEL_CONFIG only | ACCEPTED | 22C2 §11 | OPT-02 |
| G5-004 | Eval script UTF-8 caveat | Manual UTF-8 recheck when mojibake | ACCEPTED | 22F §11 | OPT-03 |
| G5-005 | No full production load test | Baseline không có k6/ab | ACCEPTED | Closure §Deploy | OPT-05 |
| G5-006 | GET modelConfig merge ≠ runtime DEFAULT | Documented; inspect DB for truth | ACCEPTED | 22B2, 22C2 | Training/onboarding |

---

## G6 — Merge gate summary

| Gate | Minimum to merge core RAG |
|------|---------------------------|
| G0 | G0-001, G0-002, G0-003 **PASS** |
| G1 | G1-001–G1-006 **PASS** (on eval tenant) |
| G2 | G2-001 **PASS** (12/0/0) |
| G3 | G3-001–G3-012 **PASS** on `/api/chat` |
| G4 | G4-001–G4-005 **PASS** |
| G5 | All **ACCEPTED** — không block |

**Merge readiness conclusion:** **READY** khi G0–G4 PASS trên nhánh deploy và G5 acknowledged.

---

## Post-merge deploy smoke (production)

| ID | Check | Expected | Status | Evidence | Notes |
|----|-------|----------|--------|----------|-------|
| D-001 | Health backend | `GET /api/chatbots?page=0&size=1` → 200 | | | |
| D-002 | Env keys present | GROQ + NOMIC boolean true in container | | | Mask only |
| D-003 | One upload INDEXED | Sample or golden txt | | | |
| D-004 | Chat fact smoke | Answer + sources ≤5 | | | |
| D-005 | Chat OOS smoke | Refuse + sources ≤2 | | | |

---

*Checklist 22G — đồng bộ với closure doc; cập nhật Status khi chạy merge/deploy thực tế.*
