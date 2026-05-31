# Testing — Backend test suite

## Test baseline

```powershell
cd Backend
.\mvnw.cmd clean test
```

| Metric | Expected |
|--------|----------|
| Total tests | **120** |
| Failures | **0** |
| Errors | **0** |

Baseline verified after tasks 27A–27F (optimizations), 28A–28C (cleanup + E2E), 29A (README).

---

## Default tests: no live infrastructure

Unit/integration tests mặc định **không yêu cầu**:

- Live MySQL connection
- Live Qdrant instance
- Real `GROQ_API_KEY` / `NOMIC_API_KEY` / `COHERE_API_KEY`

Tests dùng mocks, in-memory fixtures, hoặc `@SpringBootTest` với mocked beans tùy class.

---

## Important test groups

| Test class | Package | Bảo vệ |
|------------|---------|--------|
| `DocxParserServiceTest` | `ingest.parser` | DOCX parse, section hierarchy, `physicalColIndex` grid |
| `NormalizedTableIngestTest` | `ingest.normalize` | End-to-end table → `normalized_table_row` + `cells_json` |
| `NormalizedTableSuppressionTest` | `ingest.normalize` | Không emit `table_row_group` / `text_table_like` on new ingest |
| `NoHardcodedLexiconInTableNormalizerTest` | `ingest.normalize` | Normalizer không hardcode Vietnamese lexicon |
| `QdrantPayloadUnicodeTest` | `index.embedding` | Vietnamese UTF-8 in Qdrant payload via REST serialization |
| `EmbeddingServiceCacheTest` | `index.embedding` | Embedding cache behavior |
| `HybridKeywordSearchTest` | `rag.retrieve` | Hybrid vector + keyword scoring |
| `CellAwareNormalizedRowRetrievalTest` | `rag.retrieve` | Cell-aware boost for table rows |
| `RagRetrievalServiceE2ETest` | `rag.retrieve` | Full retrieval pipeline with fixtures |
| `FinalContextSelectionTest` | `rag.retrieve` | Context selection limits and ordering |
| `RetrievalTopKTest` | `rag.runtime` | Top-K configuration |
| `PromptBuilderServiceTest` | `rag.prompt` | Prompt structure, out-of-scope refusal |
| `ChatServiceSourcePresentationTest` | `rag.runtime` | Source DTO presentation in chat response |
| `WidgetServiceSoftDeleteChatbotTest` | `service` | Chatbot delete cascades `softDeleteDocument` |
| `RagChatbotBeApplicationTests` | root | Spring context loads |

---

## Commands

```powershell
# Full suite
cd Backend && .\mvnw.cmd clean test

# Single class
.\mvnw.cmd test -Dtest=DocxParserServiceTest

# Single method
.\mvnw.cmd test -Dtest=DocxParserServiceTest#methodName

# Compile only (fast check)
.\mvnw.cmd -DskipTests compile
```

---

## Integration test status

| Category | Status |
|----------|--------|
| Parser unit tests | ✓ Active |
| Normalizer unit tests | ✓ Active |
| Qdrant Unicode (mocked REST) | ✓ Active |
| Retrieval E2E (fixtures) | ✓ Active |
| Delete cascade (mocked repos) | ✓ Active |
| Live Testcontainers MySQL+Qdrant | ✗ Not in default suite — future optional |
| Live Groq/Nomic API calls in CI | ✗ Not in default suite |

Deferred/live tests documented in `docs/eval/results/DEFERRED_REGRESSION_TESTS_25F_20260528.md`.

---

## What tests do NOT cover (gaps)

- Full upload → Qdrant round-trip with live services
- SSE streaming under load / timeout
- Production CORS + admin auth
- Large PDF (100+ pages) performance
- Concurrent chatbot delete on many documents

Ghi gaps vào report `Rủi ro còn lại` khi task liên quan.

---

## CI / pre-commit checks

Recommended before merge:

```powershell
cd Backend && .\mvnw.cmd clean test
docker compose config -q
cd Frontend && npm run lint && npm run build
```

---

## Eval manual files

Stable DOCX for manual ingest verification:

```text
docs/eval/manual/SoTayHocVu_HocKy1_2025-2026_RAG_CONTEXT_HOC_KY.docx
```

Manual eval results: `docs/eval/results/`
