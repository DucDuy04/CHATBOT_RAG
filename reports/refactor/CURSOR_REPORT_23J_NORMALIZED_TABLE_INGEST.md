# CURSOR_REPORT_23J_NORMALIZED_TABLE_INGEST

## 1. Mức độ hiểu task

- **~92%** — pipeline normalized row + suppression + retrieval boost đã implement.
- **Chắc chắn:** không hardcode SoTay; không fallback table-text; chunk types mới; unit tests generic PASS.
- **Giả định:** re-ingest SoTay chưa chạy trong session → chunk/Qdrant old/new và Q1–Q8 chưa verify runtime.
- **Thiếu:** số liệu chunkCount/Qdrant points trước/sau trên DB production.

## 2. Tóm tắt yêu cầu

Ingest bảng chỉ qua `normalized_table_row` + `table_summary`; suppress raw table text; logical table qua trang; không `table_row_group`/`text_table_like` mới; hybrid 23I giữ nguyên.

## 3. Hiện trạng trước khi sửa

- Bảng → `table_row_group` (10 hàng/chunk) + `table_summary`; page text trùng Tabula.
- `text_table_like` khi pseudo-table fail.
- Retrieval ưu tiên `table_row_group`.

## 4. Nguyên nhân gốc (source)

`ChunkingService2` (L130–151 cũ) split markdown thành `table_row_group` và vẫn chunk text chứa `\|...\|`; `DocumentParserService` append full `pageText` trước `[TABLE_START]`.

## 5. Chiến lược sửa

- `NormalizedTableService`: parse markdown → map header/cell → canonical text → summary.
- `LogicalTableState`: continuation / repeated header.
- `ChunkingService2`: gọi normalizer; metrics `TableIngestMetrics`.
- Parser: suppress overlap trên text layer; tables append sau.
- Qdrant: transient fields → payload `table_name`, `row_index`, `cells_json`.
- Keyword/RAG lexical boost cho `normalized_table_row`.

## 6. Danh sách file đã đọc

| Path | Mục đích | Kết luận |
|------|----------|----------|
| `ChunkingService2.java` | Chunk types | table_row_group + text_table_like |
| `DocumentParserService.java` | PDF/table | Tabula + cross-page merge |
| `DocumentChunk.java` (record/entity) | Schema | Không có cells JSON persistent |
| `EmbeddingService.java` | Qdrant payload | Flat metadata |
| `KeywordSearchService.java` | Hybrid boost | table_row_group only |
| `RagRetrievalService.java` | Lexical score | table_summary bonus |
| `agent/02-architecture.md` | Chunk types doc | Cần cập nhật sau |

## 7. Danh sách file đã sửa

| Path | Mục đích | Layer |
|------|----------|-------|
| `NormalizedTableService.java` | Core normalize | service |
| `NormalizedTableRow.java`, `LogicalTableState.java`, `TableIngestMetrics.java` | Models/metrics | service |
| `ChunkingService2.java` | Ingest path | service |
| `DocumentParserService.java` | Raw suppression | service |
| `DocumentChunk.java` (record) | Row metadata | api/dto |
| `domain/.../DocumentChunk.java` | @Transient payload | db |
| `DocumentService.java` | Log metrics, copy transient | service |
| `EmbeddingService.java` | Qdrant fields | qdrant |
| `KeywordSearchService.java`, `RagRetrievalService.java` | Retrieval | service |
| `PromptBuilderService.java` | Guard text | service |
| `NormalizedTableIngestTest.java` | Tests 1–9 | test |
| `ParserAndOrderingTests.java`, `HybridKeywordSearchTest.java` | Regression | test |

## 8. Diff (tóm tắt)

### ChunkingService2 — table segment

```diff
- table_row_group chunks (markdown groups)
- text_table_like for table-like text
+ normalized_table_row per data row
+ table_summary via NormalizedTableService
+ failedTables++ without fallback on normalize fail
```

### DocumentParserService — page assembly

```diff
- append pageText then tables (duplicate)
+ collect accepted markdown → suppress pageText overlap → append text → append [TABLE_START] blocks
```

### EmbeddingService

```diff
+ if normalized_table_row: table_name, row_index, cells_json, group_context
```

## 9. Ảnh hưởng sau sửa

- **Đổi:** chunk types mới; ít duplicate context; retrieval ưu tiên row-level table chunks.
- **Giữ:** Tabula, cross-page merge 23B, hybrid 23I vector+keyword, prompt budget.
- **Điều kiện:** chỉ document **re-ingest** sau deploy mới có normalized rows.
- **Latency/cost:** nhiều chunk hơn (1/row) nhưng mỗi chunk nhỏ hơn group 10-row; embed count tăng.
- **DB:** không migration; transient metadata không lưu MySQL.

## 10. Edge cases

- Normalize fail → vùng bảng không vào Qdrant (log warning).
- Tabula reject cao → `failedTables` cao, PARTIAL ingest.
- Old documents giữ `table_row_group` until re-ingest.
- `cells_json` string trong Qdrant (không nested map LangChain4j).

## 11. Kết quả kiểm tra

| Command | Kết quả | Ghi chú |
|---------|---------|---------|
| `mvnw -DskipTests compile` | PASS | JDK 21 |
| `mvnw test` | PARTIAL | 105 pass; 1 fixed; QdrantConnectionTest + contextLoads fail (env, pre-existing) |
| `NormalizedTableIngestTest` | PASS | 9/9 |
| Frontend lint/build | NOT RUN | Out of scope |
| `docker compose config` | NOT RUN | |
| Re-ingest SoTay + Q1–Q8 | NOT RUN | Cần stack + PDF |

## 12. Rủi ro còn lại

- Chưa re-ingest benchmark → runtime table QA chưa xác nhận.
- Số chunk/embed tăng trên PDF nhiều bảng (production yếu).
- Một số bảng Tabula reject vẫn mất dữ liệu (by design, no fallback).

## 13. Đề xuất tiếp theo

1. Re-ingest `docs/eval/manual/SoTayHocVu-HocKy1-NamHoc20252026.pdf` trên document mới.
2. So sánh `chunk_type` counts và chạy Q1–Q8.
3. Cập nhật `agent/02-architecture.md` chunk type table.

## Hardcode / migration

- **Hardcode:** không (tests dùng Code/Name/ABC123 generic).
- **Migration:** không.

## Conclusion

**PARTIAL** — implementation + targeted tests PASS; full regression blocked by Qdrant IT; runtime ingest QA pending.
