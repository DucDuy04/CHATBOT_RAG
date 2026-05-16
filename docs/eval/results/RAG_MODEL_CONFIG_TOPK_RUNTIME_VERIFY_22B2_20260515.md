# RAG — modelConfig.topK runtime verify 22B2 (official result)

**File:** `docs/eval/results/RAG_MODEL_CONFIG_TOPK_RUNTIME_VERIFY_22B2_20260515.md`  
**Report:** `reports/refactor/CURSOR_REPORT_22B2_MODEL_CONFIG_TOPK_RUNTIME_VERIFY.md`  
**Ngày:** 2026-05-15  
**Loại task:** VERIFY ONLY — không sửa code

---

## 1. Environment

| Item | Giá trị |
|------|---------|
| OS | Windows 10 |
| Stack | `docker compose up --build -d` |
| Backend | `http://localhost:8080` |
| Qdrant | `http://localhost:6333` (1 collection) |
| Profile | `SPRING_PROFILES_ACTIVE=docker` |
| Env keys | `GROQ_API_KEY` present (***VTiv), `NOMIC_API_KEY` present (***tz84) |

Services: backend, frontend, mysql (healthy), qdrant — all **running**.

---

## 2. Compile / test (pre-runtime)

| Command | Kết quả |
|---------|---------|
| `Backend\.\mvnw.cmd -DskipTests compile` | **PASS** |
| `Backend\.\mvnw.cmd -Dtest=ChatServiceModelConfigTopKTest,RetrievalTopKTest,ChatServiceSourcePresentationTest test` | **PASS** (21 tests) |
| `docker compose config -q` | **PASS** |

---

## 3. Chatbot / document setup

### Chatbot A (modelConfig.topK=5 persisted)

| Field | Giá trị |
|-------|---------|
| Name | `22B2-Verify-A-223030` |
| ID | `8c1ed488-c716-42b9-a83d-9365f9688c94` |
| Widget key | `11db...82fd` (masked) |
| Setup | `POST /api/chatbots` → `PUT` `{ "modelConfig": { "topK": 5 } }` |
| DB `ui_config.modelConfig.topK` | **5** |
| Document | `RAG_GOLDEN_TEST_DOCUMENT.txt` → `3bf90cb6-e9bb-410d-9b22-f3bbabee360b` — **INDEXED** |

### Chatbot B (no persisted modelConfig.topK)

| Field | Giá trị |
|-------|---------|
| Name | `22B2-Verify-B-223058` |
| ID | `41814b20-e652-4668-a4be-bcf86e45bff3` |
| Widget key | `bfa3...c069` (masked) |
| Setup | `POST /api/chatbots` only (no PUT modelConfig) |
| DB `ui_config.modelConfig.topK` | **NULL** |
| GET API `modelConfig.topK` | **5** (merged default từ `WidgetService.mergeModelConfig` — response only) |
| Document | golden txt — **INDEXED** |

**Ghi chú:** Chatbot B chứng minh runtime không dùng API merged default; chỉ dùng giá trị persist → `source=DEFAULT effective=30`.

---

## 4. Runtime verification table

Endpoint: `POST /api/chat` + header `X-Widget-Key`  
Message: `Mã xác nhận golden là gì?` (trừ case OOS)

| # | Chatbot | request.topK | configured (log) | Expected source | Expected effective | Observed log (ChatService) | Pass |
|---|---------|--------------|------------------|-----------------|-------------------|----------------------------|------|
| 1 | A | omitted | 5 | MODEL_CONFIG | 5 | `source=MODEL_CONFIG requested=null configured=5 effective=5` | **PASS** |
| 2 | A | 10 | null* | REQUEST | 10 | `source=REQUEST requested=10 configured=null effective=10` | **PASS** |
| 3 | A | 999 | null* | REQUEST | 30 | `source=REQUEST requested=999 configured=null effective=30` | **PASS** |
| 4 | B | omitted | null | DEFAULT | 30 | `source=DEFAULT requested=null configured=null effective=30` | **PASS** |

\* Khi request có topK, `resolveRetrievalTopK` không load DB → `configured=null` trong log (đúng thiết kế 22B).

### RagRetrievalService log (secondary)

| Case | Log |
|------|-----|
| 1 | `requested=5, effective=5` |
| 2 | `requested=10, effective=10` |
| 3 | `requested=999, effective=30` |
| 4 | `requested=null, effective=30` |

---

## 5. Chat smoke

| Case | HTTP | Answer smoke | sources |
|------|------|--------------|---------|
| Fact (case 1) | 200 | Trả lời có nội dung golden | **5** |
| Override 10 | 200 | OK | **5** |
| Clamp 999 | 200 | OK | **5** |
| B default | 200 | OK | **5** |
| OOS Bitcoin | 200 | Refusal-like | **2** |

Không crash trên mọi request.

---

## 6. Source cap 21J smoke

| Scenario | sourceCount | Expected | Pass |
|----------|-------------|----------|------|
| Fact retrieval | 5 | ≤ 5 | **PASS** |
| OOS / refusal | 2 | ≤ 2 | **PASS** |

Log OOS case vẫn dùng `MODEL_CONFIG effective=5` (chatbot A) — chỉ ảnh hưởng anchor retrieval, không ảnh hưởng response cap.

---

## 7. Log evidence (excerpt)

```text
2026-05-15T15:30:39.868Z ... ChatService : [RAG] retrieval topK source=MODEL_CONFIG requested=null configured=5 effective=5
2026-05-15T15:30:48.537Z ... ChatService : [RAG] retrieval topK source=REQUEST requested=10 configured=null effective=10
2026-05-15T15:30:55.031Z ... ChatService : [RAG] retrieval topK source=REQUEST requested=999 configured=null effective=30
2026-05-15T15:31:03.285Z ... ChatService : [RAG] retrieval topK source=DEFAULT requested=null configured=null effective=30
```

Artifact: `docs/eval/results/_run_22b2_results.json`, script `_run_22b2_runtime_verify.ps1`.

---

## 8. Code changes

**Không có** — verify only.

---

## 9. Conclusion

| Criterion | Result |
|-----------|--------|
| modelConfig.topK=5 fallback (no request topK) | **PASS** |
| request topK=10 override | **PASS** |
| topK=999 clamp → 30 | **PASS** |
| No persisted config → DEFAULT 30 | **PASS** |
| Chat không crash | **PASS** |
| Source cap 21J | **PASS** |

**Task 22B2 runtime verify: PASS**

---

## 10. Limitations / notes

- Chatbot B GET trả `modelConfig.topK=5` (merged) nhưng DB NULL — cần DB inspect khi test DEFAULT.
- Playground compare path không verify trong session này.
- Một chatbot A trùng tên từ lần chạy script fail trước (`223015`) — dùng bản `223030` cho evidence chính.
