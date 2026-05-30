# SAFE_CLEANUP_AUDIT_25A — 2026-05-28

## Final verdict: **PARTIAL**

| Criterion | Result |
|-----------|--------|
| Unused-code inventory created | PASS |
| Only proven-unused code/files removed | PASS |
| Backend compiles | PASS |
| Tests pass | FAIL (env: MySQL not running; not caused by cleanup) |
| DOCX/PDF/TXT ingest paths untouched | PASS |
| Qdrant REST upsert remains active | PASS |
| No gRPC Qdrant write path in code | PASS |
| Deprecated chunk types remain disabled in ingest | PASS (no production emit path reintroduced) |
| No hardcode audit tests in repo | N/A (test sources absent on disk) |

---

## 1. Mức độ hiểu task

- **Hiểu task:** ~92%
- **Chắc chắn:** Conservative dead-code audit; không refactor kiến trúc; giữ pipeline DOCX/PDF/TXT, RawTableModel, NormalizedTableService, REST Qdrant, cells_json, hybrid retrieval.
- **Giả định:** Báo cáo runtime 24D2/24D3/24D4 vẫn là baseline chấp nhận; không xóa fixture `docs/eval/manual/SoTayHocVu_HocKy1_2025-2026_RAG_CONTEXT_HOC_KY.docx`.
- **Thiếu dữ liện:** MySQL/Qdrant local không chạy trong phiên audit → không re-ingest runtime smoke; nhiều test class task liệt kê không còn trong `Backend/src/test` (chỉ còn `RagChatbotBeApplicationTests`).

---

## 2. Tóm tắt yêu cầu

Audit và xóa **chỉ** code/file chứng minh không dùng (search + compile), chuẩn bị refactor kiến trúc. Không đụng ingest/retrieval/Qdrant REST fix đã ổn định.

---

## 3. Hiện trạng trước khi sửa

- `QdrantConfig`: chỉ REST collection init; không còn `QdrantEmbeddingStore` bean.
- `EmbeddingService`: upsert Qdrant qua `RestClient` + `qdrant.http-port`.
- `pom.xml`: vẫn khai báo `langchain4j-qdrant` dù không import trong `src/`.
- `application-dev.yml` / `application-docker.yml`: còn `qdrant.port` (6334 gRPC) và `qdrant.url` (docker) — **không** có `@Value` đọc trong Java.
- `DocumentParserService`: còn khối comment legacy cross-page merge / markdown preview trong vòng lặp Tabula.
- `Backend/src/test`: chỉ `RagChatbotBeApplicationTests` (SpringBootTest cần MySQL).
- Trước `mvn clean`, `target/test-classes` còn test cũ (PromptBuilderServiceTest, …) → focused test PASS ảo; sau clean chỉ còn 1 test.

---

## 4. Nguyên nhân gốc (từ source)

| Issue | Evidence |
|-------|----------|
| Dependency gRPC Qdrant thừa | `rg QdrantEmbeddingStore\|io\.qdrant\|langchain4j-qdrant` → 0 match trong `Backend/src` |
| Config gRPC thừa | `rg qdrant\.port\|qdrant\.url` trong `Backend/src` → 0 match; chỉ còn trong YAML |
| Comment dead code PDF loop | Khối `/* attemptCrossPageMerge ... */` và `legacyPreview` đã comment, logic active dùng `convertTabulaTableToRawTableModel` |
| Test task list không chạy được từ source | `Get-ChildItem Backend/src/test` → 1 file; git status có `??` test files nhưng **không tồn tại trên disk** |

---

## 5. Chiến lược sửa

1. Phase 1 baseline: compile + test + `docker compose config`.
2. Inventory bằng `rg` theo keyword task.
3. Xóa batch an toàn: pom dependency, YAML keys không đọc, comment blocks.
4. **Không** xóa: `convertTableToMarkdown`, `parseMarkdownTable`, `[TABLE_START]` path, metrics `*MarkdownBridge*`, reports 24D2/24D4, fixtures.
5. Compile sau batch; ghi trung thực test env.

---

## 6. Danh sách file đã đọc

| Path | Mục đích | Kết luận chính |
|------|----------|----------------|
| `Backend/pom.xml` | Dependency audit | `langchain4j-qdrant` unused |
| `Backend/.../QdrantConfig.java` | Qdrant wiring | REST-only init |
| `Backend/.../EmbeddingService.java` | Write path | REST upsert, no gRPC |
| `Backend/.../DocumentParserService.java` | Dead comments / markdown | `convertTableToMarkdown` still used for debug; commented merge removed |
| `Backend/.../ChunkingService2.java` | Legacy paths | `[TABLE_START]` + `RAW_TABLE_REF` both active; keep |
| `Backend/.../TableIngestMetrics.java` | Metrics | `inc*MarkdownBridge` never called but logged as regression guard |
| `application-dev.yml`, `application-docker.yml` | Config dead keys | `port`/`url` unused |
| `docker-compose.yml` | Port 6334 | Expose kept (Qdrant image default) |
| `Backend/src/test/**` | Test baseline | 1 SpringBootTest only |

---

## 7. Danh sách file đã sửa

| Path | Sửa để làm gì | Layer |
|------|---------------|-------|
| `Backend/pom.xml` | Gỡ `langchain4j-qdrant` | config / deploy |
| `Backend/src/main/resources/application-dev.yml` | Gỡ `qdrant.port` | config |
| `Backend/src/main/resources/application-docker.yml` | Gỡ `qdrant.port`, `qdrant.url` | config |
| `Backend/.../DocumentParserService.java` | Xóa comment blocks dead | service |

**Không xóa file report/fixture/script** — không có `_run_*`, dump, hay script UTF-8 riêng trong repo; reports FAIL cũ giữ làm audit history.

---

## 8. Diff thay đổi từng file

### `Backend/pom.xml`

- **Cũ:** Còn dependency `langchain4j-qdrant` sau khi 24D4 chuyển REST.
- **Sửa:** Xóa block dependency.
- **Vì sao:** Không còn import Java; giảm classpath gRPC client.
- **Ảnh hưởng:** Build nhẹ hơn; không đổi runtime behavior.

```diff
-		<!-- LangChain4j — kết nối Qdrant -->
-		<dependency>
-			<groupId>dev.langchain4j</groupId>
-			<artifactId>langchain4j-qdrant</artifactId>
-		</dependency>
```

### `application-dev.yml`

```diff
 qdrant:
   host: localhost
-  port: 6334
-  http-port: 6333
+  http-port: 6333               # REST API (EmbeddingService upsert + QdrantConfig init)
```

### `application-docker.yml`

```diff
 qdrant:
   host: qdrant
-  port: 6334
   http-port: 6333
-  url: http://qdrant:6333
```

### `DocumentParserService.java`

```diff
-                    /* attemptCrossPageMerge ... */
                     if (!isUsableTable(table)) {
...
-                    /* legacyPreview tableMarkdown ... */
                     log.info("[Parse] Table ACCEPTED ...
```

---

## 9. Ảnh hưởng sau sửa

| Behavior | Status |
|----------|--------|
| PDF/DOCX/TXT ingest | Unchanged |
| RawTableModel → NormalizedTableService | Unchanged |
| Qdrant REST upsert (`EmbeddingService`) | Unchanged |
| `table_row_group` / `text_table_like` emit | Still no production emit path |
| `[TABLE_START]` markdown normalization (TXT/legacy) | Still reachable via `ChunkingService2` |
| `convertTableToMarkdown` / `parseMarkdownTable` | Kept (debug + legacy segment path) |
| Docker Qdrant port 6334 | Still exposed in compose |
| Memory/CPU | Slightly smaller classpath (no qdrant gRPC client jar) |
| Latency/cost | No change |

---

## 10. Edge cases đã xem xét

- Xóa nhầm `convertTableToMarkdown` → **kept** (still referenced ~L848, preview paths).
- Xóa `[TABLE_START]` path → **kept** (ChunkingService2 still branches).
- Xóa metrics `markdownTablesNormalizedLegacy` → **kept** (regression counter; misnamed but active on markdown path).
- Xóa reports FAIL (24B2 quota, 24D0) → **deferred** (audit history).
- `mvn test` without MySQL → expected fail `contextLoads`.
- Stale `target/test-classes` → documented; không coi là PASS sau clean.

---

## 11. Kết quả kiểm tra

| Command | Kết quả | Ghi chú |
|---------|---------|---------|
| `cd Backend && mvnw -DskipTests compile` (pre) | PASS | Nothing to compile (up to date) |
| Focused `-Dtest=DocxParserServiceTest,...` (pre, stale target) | PASS | 57 tests from **old** `target/test-classes`; sources missing |
| `mvnw clean test` (pre) | FAIL | MySQL Communications link failure |
| `docker compose config -q` | PASS | |
| `mvnw -DskipTests compile` (post) | PASS | 121 sources, 1 deprecation warning (pre-existing) |
| `mvnw test` (post) | FAIL | 1 test, `RagChatbotBeApplicationTests.contextLoads` — no MySQL |
| Frontend lint/build/widget | NOT RUN | Out of scope |
| Runtime DOCX re-ingest | NOT RUN | No local stack |

**Baseline trước cleanup (ghi nhận):** compile PASS; focused test PASS chỉ khi target cũ còn test JAR; full test FAIL nếu clean + no MySQL.

---

## 12. Rủi ro còn lại

- `agent/03-backend.md`, `agent.md` vẫn mô tả gRPC/`QdrantEmbeddingStore` — docs stale (REFACTOR_LATER).
- Unit tests 24D2/24D4 (`EmbeddingServiceCacheTest`, `NormalizedTableIngestTest`, …) **không có trong repo** — regression risk trước refactor.
- `inc*MarkdownBridge` counters never increment — metrics always 0 (acceptable guard).
- `RagChatbotBeApplicationTests` requires live MySQL — CI/local cần profile hoặc Testcontainers.

---

## 13. Đề xuất tiếp theo

1. **Restore/commit** unit tests listed in task (không SpringBoot) trước architecture refactor.
2. Update `agent/*.md` Qdrant section → REST-only.
3. Architecture refactor: tách ingest / normalize / embed / retrieve packages; optional rename `markdownTablesNormalizedLegacy` metric.
4. Optional: `@Disabled` or `@DataJpaTest` slice for `contextLoads` when MySQL absent.

---

## Unused-code inventory (classification)

### DELETE_SAFE (done)

| Item | Evidence |
|------|----------|
| `langchain4j-qdrant` Maven dep | No imports in `Backend/src` |
| `qdrant.port`, `qdrant.url` YAML | No `@Value` readers |
| Commented merge/preview blocks in PDF Tabula loop | Comment-only; active path uses RawTableModel |

### KEEP_ACTIVE

| Item | Reason |
|------|--------|
| `EmbeddingService.upsertToQdrant` REST | Official write path |
| `RawTableModel`, `NormalizedTableService`, `ChunkingService2` | Core ingest |
| `convertTableToMarkdown`, `parseMarkdownTable` | Still referenced |
| `[TABLE_START]` + `processNormalizedTable` | Legacy TXT / pseudo-table path |
| `table_row_group` / `text_table_like` handling in Chat/Prompt/Keyword | Backward compat for old indexed data |
| All `docs/eval/manual/*` fixtures + 24D2/24D3/24D4 reports | Evidence / regression |
| docker-compose `6334:6334` | Qdrant default; external tools may use gRPC read |

### DELETE_AFTER_CONFIRMATION

| Item | Blocker |
|------|---------|
| `docs/eval/manual/SoTayHocVu_24D2_upload_copy.docx` | Possible duplicate of stable DOCX — confirm before delete |
| Superseded FAIL-only reports (24B2 quota, 24D0) | Audit history value |

### REFACTOR_LATER

| Item | Note |
|------|------|
| `TableIngestMetrics.inc*MarkdownBridge` (never called) | Keep fields for log guards until metric rename |
| `agent/*.md` gRPC docs | Doc sync |
| Missing unit test sources | Restore from branch/backup |
| `attemptCrossPageMerge` (disabled in loop, method exists) | Product decision for PDF cross-page |
| Prompt rules mentioning `table_row_group` | Prompt cleanup when sure no old chunks |

### DO_NOT_TOUCH

PDF/DOCX/TXT parsers, cells_json mapping, hybrid retrieval, Playground sources, OOS refusal, stable DOCX fixture, 24D4 Unicode fix code path.
