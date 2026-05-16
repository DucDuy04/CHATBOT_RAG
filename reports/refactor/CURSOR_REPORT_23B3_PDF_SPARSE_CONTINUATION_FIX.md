# CURSOR_REPORT_23B3 — PDF sparse continuation table merge fix

## 1. Mức độ hiểu task

- **~97%** — sửa parser để merge bảng continuation sparse page 14→13 sau khi 23B2 xác nhận reject.
- **Chắc chắn:** root cause từ log 23B2; scope không đụng prompt/retrieval/FE.
- **Giả định nhỏ:** số trang Tabula = số trang PDF mục tiêu (đã xác nhận log page 13/14).

## 2. Tóm tắt yêu cầu

Cho phép merge bảng continuation trang N+1 dù `isUsableTable` fail vì sparse/empty cells; log `SPARSE_CONTINUATION`; đủ 12 entity sau index; target question PASS; không hardcode entity; regression smoke PASS.

## 3. Phạm vi đã làm

- `DocumentParserService`: nhánh merge sparse, extract hàng 3 cột, heading guard.
- Unit tests: `DocumentParserCrossPageMergeTest`, `TabulaTableTestHelper`.
- Runtime verify script + results JSON.
- Docs eval + report này.

## 4. Phạm vi không làm

Không sửa PromptBuilder, QueryAnalyzer, ChatService, source cap, model controls, Frontend, Playground, ChunkingService2 (không bắt buộc), DB schema, dependency, `.gitignore`, migration/backfill.

## 5. File đã đọc

| Path | Mục đích | Kết luận |
|------|----------|----------|
| `docs/eval/results/RAG_PDF_CROSS_PAGE_TABLE_RUNTIME_VERIFY_23B2_20260515.md` | Runtime 23B2 | Page 14 REJECTED, không merge 13–14 |
| `reports/refactor/CURSOR_REPORT_23B2_*.md` | Bối cảnh verify | Đề xuất 23B3 sparse path |
| `docs/eval/results/RAG_PDF_CROSS_PAGE_TABLE_23B_20260515.md` | Fix 23B | Merge trước reject cho REPEATED_HEADER |
| `DocumentParserService.java` | Source | Merge loop + `isUsableTable` tại ~645–697 |
| `ParserAndOrderingTests.java` | Regression | Giữ test merged table chunking |
| `docs/eval/RAG_BASELINE_CORE_CLOSURE_22G.md` | Baseline | Không đổi retrieval contract |

## 6. Root cause từ 23B2

Page 14: `too-many-empty-cells(65/99>66%)` → `logRejectedTable` + `continue` **sau khi** merge 23B không nhận continuation (header sparse / hàng 1 cột). Heading `5. Quan hệ…` trong bbox Tabula chặn nhầm `hasIndependentSectionHeadingInTable` khi quét toàn bảng.

## 7. Phân tích parser trước sửa

| # | Câu hỏi | Trả lời |
|---|---------|---------|
| 1 | `isUsableTable` reject page 14? | `emptyCells/totalCells > 0.6` (65/99) |
| 2 | Có continuation text nhưng reject? | Có — preview có LichSuPhucKhao |
| 3 | Merge trước hay sau reject? | 23B merge trước reject nhưng chỉ DATA_ROWS / REPEATED_HEADER |
| 4 | 18→17 merge nhưng 14→13 không? | Page 18 usable + repeated header; page 14 fail usable + không đủ tín hiệu |
| 5 | Raw rows trước reject? | Có — Tabula 33 rows |
| 6 | Convert raw rows được? | Có — sau `extractSparseLogicalRowCells` + fragment merge |
| 7 | Nhận continuation không hardcode? | Có — trang liền kề + header trước + layout cột |
| 8 | Rủi ro merge nhầm? | Có — giảm bằng adjacent page + reject reason whitelist + heading chỉ hàng đầu |
| 9 | Nới global threshold? | Không — nhánh sparse riêng |
| 10 | Minimal fix? | `attemptCrossPageMerge` + `SPARSE_CONTINUATION` |

## 8. Thiết kế sửa minimal

Option A: pre-reject merge khi `isSparseContinuationCandidate`. Không relax global 60%. `convertSparseContinuationRows` compact 3 cột + ghép fragment. Log `mode=SPARSE_CONTINUATION`.

## 9. Vì sao không hardcode

Dùng layout cột, header markdown trang trước, reject reason, heading pattern số — test dùng `EntityA/B/C` generic.

## 10. Danh sách file đã sửa

| Path | Mục đích | Lớp |
|------|----------|-----|
| `Backend/.../DocumentParserService.java` | Sparse merge | service |
| `Backend/.../service/DocumentParserCrossPageMergeTest.java` | Unit tests | test |
| `Backend/.../service/TabulaTableTestHelper.java` | Tabula fixtures | test |
| `docs/eval/results/RAG_PDF_SPARSE_CONTINUATION_FIX_23B3_20260516.md` | Kết quả eval | docs |
| `docs/eval/results/FIX_LOOP_23B3_PDF_SPARSE_CONTINUATION.md` | Fix loop | docs |
| `docs/eval/results/_run_23b3_pdf_sparse_continuation_verify.ps1` | Runtime script | docs |
| `docs/eval/results/_run_23b3_results.json` | Runtime artifact | docs |

## 11. Diff từng file

### `DocumentParserService.java`

**Cũ:** merge inline chỉ DATA_ROWS / REPEATED_HEADER; page 14 sparse → reject.

**Sửa:** `attemptCrossPageMerge`, `isSparseContinuationCandidate`, `convertSparseContinuationRows`, `extractSparseLogicalRowCells`, heading guard, compact header match.

```diff
-                    if (canMergeToPrev) {
-                        String rowsOnly = continuationByRepeatedHeader ? ...
+                    CrossPageMergeOutcome mergeOutcome = attemptCrossPageMerge(...);
+                    if (mergeOutcome.merged()) {
+                        log.info("... mode={}", mergeOutcome.mode(), ...);
                         continue;
                     }
+    // + attemptCrossPageMerge, SPARSE_CONTINUATION, convertSparseContinuationRows, ...
```

**Ảnh hưởng:** Bảng sparse trang N+1 liền kề có thể merge; log thêm `SPARSE_CONTINUATION`.

### `DocumentParserCrossPageMergeTest.java` (mới)

5 tests: repeated header, sparse 3-col, non-adjacent, heading first row block, late heading still merges.

### `TabulaTableTestHelper.java` (mới)

Build `Table` với `TextChunk` + `TextElement`.

## 12. Compile/test result

| Command | Kết quả | Ghi chú |
|---------|---------|---------|
| `mvnw -DskipTests compile` | **PASS** | |
| `mvnw -Dtest=ParserAndOrderingTests,DocumentParserCrossPageMergeTest test` | **PASS** | 19 tests |
| Frontend lint/build/widget | **NOT RUN** | Ngoài scope |
| `docker compose config -q` | **PASS** | |

## 13. Runtime PDF verify result

| Item | Result |
|------|--------|
| Upload | INDEXED, chunkCount=84 |
| Merge 14→13 | **Yes** `SPARSE_CONTINUATION rows=33` |
| Target question | **PASS** 12/12 entity |
| Regression smoke | **PASS** 3/3 |

Evidence: `_run_23b3_results.json`

## 14. Parser log evidence

```text
[Parse] Table ACCEPTED page=13: rows=9 cols=3 ...
[Parse] Table MERGED continuation page=14 → page=13: mode=SPARSE_CONTINUATION rows=33
```

## 15. SQL/Qdrant evidence

- Qdrant: **12/12** entity, **4/4** page-14 names (84 points).
- SQL chunk sample: 10/12 trong 80 dòng preview (encoding/limit).

## 16. Target question verdict

**PASS** — answer liệt kê đủ 12 entity kể cả TuiBaiThi, BienBan.

## 17. Regression smoke

Fact 22T1020585, NguoiDung columns 9/9, OOS USD/VND — **PASS**.

## 18. Có sửa PromptBuilder/retrieval không?

**Không.**

## 19. Rủi ro còn lại

- PDF khác có sparse table không continuation nhưng trang liền kề + cùng header có thể merge nhầm (hiếm).
- Tabula multi-table/page vẫn phụ thuộc thứ tự extract.
- Document đã index trước 23B3 cần re-upload để có merge mới.

## 20. Đề xuất bước tiếp theo

1. Merge PR 23B3 vào baseline branch.
2. Re-index PDF production.
3. Nếu answer thiếu sau merge + PASS runtime → mới xem retrieval/topK (task riêng).

---

**Evidence:** `docs/eval/results/RAG_PDF_SPARSE_CONTINUATION_FIX_23B3_20260516.md`
