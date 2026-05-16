# Cursor Report 21D0 — Baseline core RAG rebuild plan & checklist (docs only)

**Ngày:** 2026-05-14  
**Scope:** Tạo plan + checklist + fix-loop template + report; **không** sửa Java/React/YAML/parser/retrieval/prompt/`.gitignore`; không migration/backfill; không thêm dependency.

---

## 1. Mức độ hiểu task

| Mục | Nội dung |
|-----|----------|
| **Hiểu task** | **99%** |
| **Chắc chắn** | Bối cảnh 20G/21B/21C; endpoint từ source; deliverable 4 file; ≥40 checklist rows; ngưỡng G1/G2/G3/G4; không chạy E2E trong task này. |
| **Giả định** | Người chạy sau sẽ điền Actual/Evidence trong checklist; SQL/Qdrant dùng `document_id` / `widgetId` từ phiên họ. |
| **Thiếu dữ kiện** | Không — mọi file “bắt buộc đọc” tồn tại trong repo. |

---

## 2. Tóm tắt yêu cầu

Tạo bộ tài liệu gate-based để kiểm soát từng lần sửa baseline core RAG: plan tổng thể, checklist thực thi G0–G6 (≥40 item), template fix-loop, và report audit task 21D0.

---

## 3. Phạm vi đã làm

- Viết `docs/eval/RAG_BASELINE_CORE_REBUILD_PLAN.md` (15 mục theo spec + P0/P1/P2 + evidence).  
- Viết `docs/eval/RAG_BASELINE_CORE_CHECKLIST.md` (bảng 9 cột, **69** dòng item, gates G0–G6).  
- Viết `docs/eval/RAG_BASELINE_CORE_FIX_LOOP_TEMPLATE.md` (13 mục).  
- Viết report này.

---

## 4. Phạm vi không làm

- Không sửa source backend/frontend.  
- Không chạy `mvnw compile`, không chạy Docker E2E/golden trong task.  
- Không sửa `docs/eval/results/RAG_EVAL_RUN_21B_20260514.md` (lỗi tổng §5 vẫn ghi nhận trong plan/diag 21C; có thể addendum sau ở task khác).  
- Không chỉnh file plan Cursor (`.cursor/plans/...`).

---

## 5. File đã đọc (task research)

| Path | Đọc để | Kết luận chính |
|------|--------|----------------|
| `reports/refactor/CURSOR_REPORT_20G_CORE_RAG_E2E_EXECUTION.md` | E2E baseline | Luồng core PASS; endpoint và bước verify rõ |
| `docs/RAG_CORE_FLOW_E2E_RUNBOOK.md` | Runbook E2E | MySQL clean, compose, chat/delete |
| `docs/eval/RAG_GOLDEN_TEST_DOCUMENT.md` | Nội dung golden | Markdown `##` + list + bảng |
| `docs/eval/RAG_GOLDEN_TEST_DOCUMENT.txt` | Ingest thực tế | Giống `.md`, upload `.txt` |
| `docs/eval/RAG_GOLDEN_QUESTIONS.md` | 12 case + tiêu chí | A–D, verdict, tags |
| `docs/eval/RAG_EVALUATION_RUNBOOK.md` | Quy trình eval | `/api/chat`, `X-Widget-Key`, chỉ pdf/txt |
| `docs/eval/results/RAG_EVAL_RUN_21B_20260514.md` | Kết quả 21B | Verdict từng case là chuẩn; §5 tổng sai |
| `reports/refactor/CURSOR_REPORT_21B_RAG_GOLDEN_EVALUATION_EXECUTION.md` | Bối cảnh chạy 21B | DOCUMENT_ID, chunkCount=6 |
| `docs/eval/results/RAG_RETRIEVAL_DIAG_21C_20260514.md` | Recompute + ingest | 6/2/4; matrix root cause |
| `reports/refactor/CURSOR_REPORT_21C_RAG_RETRIEVAL_DIAGNOSIS.md` | Audit 21C | Parser, chunking, retrieval, log |
| `docs/RAG_TARGET_ARCHITECTURE.md` | Map module | Parser → Retrieval → Prompt |
| `Backend/.../DocumentController.java` | API document | `/api/documents/upload`, status, delete, retry |
| `Backend/.../ChatController.java` | API chat | `POST /api/chat`, stream |
| `Backend/.../ChatbotController.java` | API chatbot | `POST /api/chatbots` |
| `Backend/.../config/WidgetAuthFilter.java` | Auth chat | `X-Widget-Key` cho `/api/chat` chính xác |
| `docker-compose.yml` | Infra | 8080, 3306, 6333, env Groq/Nomic |
| `Backend/src/main/resources/application-docker.yml` | Profile docker | DB/Qdrant host |
| `Backend/src/main/resources/application-dev.yml` | Profile dev | localhost |

**Ghi chú:** Các service Java (`DocumentService`, `DocumentParserService`, `ChunkingService2`, `EmbeddingService`, `RagRetrievalService`, `QueryAnalyzerService`, `PromptBuilderService`) và entity domain đã được đọc trong phiên plan trước và tóm tắt trong 21C; không cần trích lại toàn bộ source trong task doc-only này.

---

## 6. Baseline hiện tại đã tổng hợp

- **20G:** Core E2E runtime PASS (ingest sample, chat magic string + source, delete, DB+Qdrant sạch, chat sau xóa không source).  
- **21B:** Golden 12 case đã chạy; verdict từng case theo bảng §3 file run.  
- **21C recompute:** **PASS=6, PARTIAL=2, FAIL=4** (total 12); không dùng tổng PASS=7 trong §5 file 21B.  
- **Root cause chính (21C):** parser TXT không `##`; list `1.2.3.` → section giả; chunk/table metadata sai; heading lock + vector exclude + không refill anchor → F04/L02/T02 0 context; F03/T01 attribution; L01 generation trên context nhiễu.

---

## 7. Checklist / gate đã tạo

- **G0** — Build, compose config, env boolean, secret hygiene, scope diff.  
- **G1** — Upload golden mới, INDEXED, SQL sections/chunks/tables, Qdrant payload, chunkCount.  
- **G2** — Core E2E regression (sample + delete + không leak).  
- **G3** — GQ-F04, L01, L02, T02, F03, T01 với expected + fail class + baseline 21B/21C.  
- **G4** — Full 12 case, verdict chỉ từ cột `verdict`, ngưỡng tối thiểu sau parser fix (tham plan §11).  
- **G5** — Delete golden, GQ-D01, optional retry FAILED.  
- **G6** — Resource / ops guard.

**Số dòng item trong bảng checklist:** 69.

---

## 8. Fix loop protocol đã tạo

- Thứ tự: compile (nếu Java) → re-ingest doc mới → G1 → G3 → G2 → G4 khi đủ ngưỡng → G5 → G6.  
- Template 13 mục: tên fix, mục tiêu, checklist ID, files đọc/được sửa/không sửa, pre snapshot, diff summary, post-test, improved/regressed cases, quyết định, prompt tiếp theo.

---

## 9. File đã tạo / cập nhật

| Path | Hành động |
|------|-----------|
| `docs/eval/RAG_BASELINE_CORE_REBUILD_PLAN.md` | Tạo mới |
| `docs/eval/RAG_BASELINE_CORE_CHECKLIST.md` | Tạo mới |
| `docs/eval/RAG_BASELINE_CORE_FIX_LOOP_TEMPLATE.md` | Tạo mới |
| `reports/refactor/CURSOR_REPORT_21D0_BASELINE_CORE_REBUILD_PLAN_CHECKLIST.md` | Tạo mới (file này) |

---

## 10. Diff từng file

Cả bốn path đều là **file mới (untracked)** so với `HEAD`: `git diff -- <paths>` **rỗng** cho đến khi `git add -N` (intent-to-add) hoặc `git add` + `git diff --cached`.

**Tóm tắt thống kê:**

| File | Dòng (ước lượng) | Bytes |
|------|------------------|------:|
| `docs/eval/RAG_BASELINE_CORE_REBUILD_PLAN.md` | ~195 | 12 141 |
| `docs/eval/RAG_BASELINE_CORE_CHECKLIST.md` | ~113 | 13 437 |
| `docs/eval/RAG_BASELINE_CORE_FIX_LOOP_TEMPLATE.md` | ~128 | 3 762 |
| `reports/refactor/CURSOR_REPORT_21D0_BASELINE_CORE_REBUILD_PLAN_CHECKLIST.md` | ~220 | 10 966 |

**Block diff rút gọn (đại diện “new file” mỗi file):**

```diff
--- /dev/null
+++ b/docs/eval/RAG_BASELINE_CORE_REBUILD_PLAN.md
@@ -0,0 +1,195 @@
+# Plan — Rebuild & kiểm soát baseline core RAG (gate-based)
+...
```

```diff
--- /dev/null
+++ b/docs/eval/RAG_BASELINE_CORE_CHECKLIST.md
@@ -0,0 +1,113 @@
+# Checklist — Baseline core RAG (G0–G6)
+...
```

```diff
--- /dev/null
+++ b/docs/eval/RAG_BASELINE_CORE_FIX_LOOP_TEMPLATE.md
@@ -0,0 +1,128 @@
+# Template — Fix loop cho từng lần sửa baseline RAG
+...
```

```diff
--- /dev/null
+++ b/reports/refactor/CURSOR_REPORT_21D0_BASELINE_CORE_REBUILD_PLAN_CHECKLIST.md
@@ -0,0 +1,220 @@
+# Cursor Report 21D0 — Baseline core RAG rebuild plan & checklist (docs only)
+...
```

**Xem diff đầy đủ trên máy:**

```powershell
Set-Location "E:\chatbot-rag-workspace\CHATBOT_RAG"
git add -N docs/eval/RAG_BASELINE_CORE_REBUILD_PLAN.md docs/eval/RAG_BASELINE_CORE_CHECKLIST.md docs/eval/RAG_BASELINE_CORE_FIX_LOOP_TEMPLATE.md reports/refactor/CURSOR_REPORT_21D0_BASELINE_CORE_REBUILD_PLAN_CHECKLIST.md
git diff -- docs/eval/RAG_BASELINE_CORE_REBUILD_PLAN.md docs/eval/RAG_BASELINE_CORE_CHECKLIST.md docs/eval/RAG_BASELINE_CORE_FIX_LOOP_TEMPLATE.md reports/refactor/CURSOR_REPORT_21D0_BASELINE_CORE_REBUILD_PLAN_CHECKLIST.md
git reset HEAD docs/eval/RAG_BASELINE_CORE_REBUILD_PLAN.md docs/eval/RAG_BASELINE_CORE_CHECKLIST.md docs/eval/RAG_BASELINE_CORE_FIX_LOOP_TEMPLATE.md reports/refactor/CURSOR_REPORT_21D0_BASELINE_CORE_REBUILD_PLAN_CHECKLIST.md
```

---

## 11. Có sửa runtime không?

**Không** — chỉ thêm/cập nhật markdown trong `docs/eval/` và `reports/refactor/`.

---

## 12. Kết quả kiểm tra file tồn tại / không rỗng / git diff

| Command / kiểm tra | Kết quả | Ghi chú |
|--------------------|---------|---------|
| Tồn tại 4 path | **PASS** | `Get-Item` OK |
| Kích thước > 0 | **PASS** | 12141, 13437, 3762, 10966 bytes (report sau cập nhật mục 10/12) |
| `git diff --` (4 path, untracked) | **NOT_RUN** (rỗng có chủ đích) | Untracked → không có diff so với HEAD |
| `git add -N` + `git diff --` (4 path) | **PASS** (đã chạy trong phiên agent) | Unified diff new file; không giữ staged sau `git reset HEAD` |

---

## 13. Rủi ro còn lại

- Người chạy checklist có thể nhầm **tổng hợp** với **bảng verdict** nếu không reconcile (đã nhắc trong plan + G4).  
- SQL inspect doc đã soft-delete cần **raw SQL** (JPA `@SQLRestriction`) — đã nhắc trong plan 21D0 và 21C.  
- `chunkCount` sau parser fix có thể đổi — cần ghi lại số mới mỗi phiên, không cứng một con số tuyệt đối ngoài “hợp lý”.

---

## 14. Đề xuất prompt / task tiếp theo

1. **P0 — Parser TXT:** Nhận markdown heading `##` làm ranh giới section; ngăn list đánh số trong mục “Chính sách hỗ trợ” thành `document_sections` root giả; map lại chunk “Bước 2” và bảng gói dịch vụ đúng section.  
2. Sau khi có code + re-ingest: chạy **G1** rồi **G3** theo `RAG_BASELINE_CORE_CHECKLIST.md`, điền template fix-loop.  
3. **P1 (chỉ khi G1 PASS mà G3 còn no-context):** Fix retrieval — refill anchor / re-search sau guardrail / heading lock không trỏ section zero-chunk (tham `RagRetrievalService` trong 21C).

---

## Phụ lục A — Output `git diff` (4 path)

Diff unified đầy đủ dài (hàng nghìn dòng `+`). Đã tạo thành công trong phiên bằng `git add -N` + `git diff --` (xem mục 10 lệnh tái tạo). Không nhúng toàn bộ vào report để tránh trùng nội dung ba file `docs/eval/`.

## Phụ lục B — Kiểm tra kích thước file

| File | Bytes |
|------|------:|
| `docs/eval/RAG_BASELINE_CORE_REBUILD_PLAN.md` | 12 141 |
| `docs/eval/RAG_BASELINE_CORE_CHECKLIST.md` | 13 437 |
| `docs/eval/RAG_BASELINE_CORE_FIX_LOOP_TEMPLATE.md` | 3 762 |
| `reports/refactor/CURSOR_REPORT_21D0_BASELINE_CORE_REBUILD_PLAN_CHECKLIST.md` | 10 966 |

---

*Chữ ký task: 21D0 docs-only.*
