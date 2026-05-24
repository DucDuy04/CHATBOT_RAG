# CURSOR REPORT — 23J3 Normalized Table Suppression Fix

**Date:** 2026-05-23  
**Verdict:** **PARTIAL**

## 1. Mức độ hiểu task

- **97%** — fix suppression + retrieval priority; re-ingest verify; no rollback 23I/23J.
- **Chắc chắn:** root cause from 23J2 logs; generic suppression; tests + runtime.
- **Giả định:** Q7 regression do wrong row retrieval not suppression.

## 2. Tóm tắt yêu cầu

Suppress raw table text from `chunk_type=text`; boost `normalized_table_row` retrieval; re-ingest SoTay; Q1–Q8 verify.

## 3. Hiện trạng trước khi sửa

23J2 PARTIAL: 51+ raw TKB text chunks, suppressedRawChars=0, Q1/Q2 sources all text.

## 4. Nguyên nhân gốc

`looksLikeTableDataLine` không nhận single-space timetable rows; suppression chỉ chạy khi overlap AND table-like; section text trước table không có profile.

## 5. Chiến lược sửa

SuppressionProfile + pre-scan section + enhanced line detection + post-chunk filter; KeywordSearchService/RagRetrievalService boost/penalty.

## 6. File đã đọc

| File | Kết luận |
|------|----------|
| `NORMALIZED_TABLE_INGEST_RUNTIME_VERIFY_23J2_20260523.md` | 51 leaky text chunks |
| `NormalizedTableService.java` | old suppression too strict |
| `ChunkingService2.java` | text segment path |
| `KeywordSearchService.java` | weak table boost |

## 7. File đã sửa

| File | Layer |
|------|-------|
| `NormalizedTableService.java` | service |
| `ChunkingService2.java` | service |
| `DocumentParserService.java` | service |
| `KeywordSearchService.java` | service |
| `RagRetrievalService.java` | service |
| `TableIngestMetrics.java` | service |
| `DocumentService.java` | service |
| `NormalizedTableSuppressionTest.java` | test |
| docs/eval results | docs |

**Hardcode SoTay:** Không  
**Migration:** Không

## 8. Diff summary

- Added `SuppressionProfile`, `suppressRawTableText`, single-space table-like detection.
- `buildSectionSuppressionProfile` pre-scan in ChunkingService2.
- Parser `[TableSuppress]` logging.
- Retrieval: `isTableLikeQuery`, text mega penalty, `demoteLeakyTextCandidates`.

## 9. Ảnh hưởng sau sửa

- Ingest: text 149→146, suppressedRawChars 0→35746, no raw TKB in text.
- Retrieval: Q1/Q2 sources normalized_table_row (was text).
- Answers: Q1/Q2/Q7 still fail (wrong row / LLM refuse).

## 10. Edge cases

- Empty COHERE_RERANK_ENABLED → backend crash; fixed with `false` for verify.
- section_summary still contains CTĐT prose (not suppressed — different chunk type).

## 11. Kết quả kiểm tra

| Command | Result |
|---------|--------|
| Unit tests 18 | PASS |
| Targeted suite | PASS (exit 0) |
| Re-ingest 2735 | PASS |
| Leakage 51→0 | PASS |
| Q1–Q8 | PARTIAL (1P/2PART/5F) |
| Frontend | NOT RUN |

## 12. Rủi ro còn lại

- Timetable row exact match needs column-aware retrieval (Nhóm label in cells_json).
- section_summary/parent_section_summary table-like leakage.
- Q7 regression.

## 13. Đề xuất tiếp theo

Boost normalized rows matching structured label "Nhóm N" in cells_json; suppress table-like lines in section summary builder.

## Kết luận

**PARTIAL** — suppression PASS; runtime QA answers still need retrieval/LLM follow-up.
