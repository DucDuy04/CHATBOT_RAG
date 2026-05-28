# CURSOR REPORT 24D4 - QDRANT UNICODE CELLS_JSON FIX

## 1) Muc do hieu task
- Muc do hieu: **100%**.
- Chac chan:
  - Root cause la gRPC write path cua LangChain4j `QdrantEmbeddingStore` corrupt Vietnamese Unicode.
  - Fix la replace gRPC upsert bang Qdrant REST API upsert via Spring RestClient.
  - DB cells_json da correct tu 24D2. Qdrant cells_json bi sai chi trong write path.
  - PASS chi duoc claim khi Qdrant payload Vietnamese Unicode parity voi DB tren document moi.
- Gia dinh: khong co.
- Thieu du kien: khong co.

## 2) Tom tat yeu cau
- Fix Qdrant payload Unicode serialization cho cells_json Vietnamese text.
- Replace LangChain4j gRPC upsert path bang direct Qdrant REST API upsert.
- Them tests cho Unicode preservation.
- Re-ingest canary DOCX.
- Verify DB/Qdrant parity cho target rows.
- Khong chay Q1-Q8.

## 3) Hien trang truoc khi sua
- `EmbeddingService.embedAndStore()` goi `qdrantEmbeddingStore.addAll(embeddings, segments)`.
- `QdrantEmbeddingStore` (LangChain4j 1.0.0-beta1) su dung gRPC (port 6334) de upsert vectors va payload.
- Vietnamese Unicode bi corrupt trong gRPC path: multi-byte UTF-8 chars (2-3 byte sequences) bi interpret sai thanh mojibake patterns.
- DB cells_json (MySQL) luu dung UTF-8 (xac nhan tu 24D2).
- Qdrant cells_json payload bi loi encoding (xac nhan tu 24D3).

## 4) Nguyen nhan goc xac nhan tu source/runtime
- **Stage D (gRPC protobuf serialization)**: LangChain4j `QdrantEmbeddingStore` (langchain4j-qdrant 1.0.0-beta1 + io.qdrant:client) convert Java String sang protobuf payload Value bi sai encoding cho multi-byte UTF-8 Vietnamese characters.
- Java String trong memory luon la dung Unicode (internal UTF-16).
- DB MySQL luu dung UTF-8 (confirmed 24D2, xac nhan lai bang hex dump trong 24D4).
- Sau khi fix sang REST API: Spring RestClient + Jackson ObjectMapper serialize Map<String,Object> thanh UTF-8 JSON chinh xac, khong co encoding loss.
- Root cause: NOT DB, NOT DOCX parser, NOT NormalizedTableService, NOT cellsToJson(). 100% la LangChain4j gRPC Qdrant write path.

## 5) Chien luoc sua da chon
- Minimal diff: chi sua `EmbeddingService` va `QdrantConfig`.
- Thay the `qdrantEmbeddingStore.addAll()` (gRPC) bang `upsertToQdrant()` method moi su dung `RestClient.put()` → Qdrant REST API (`PUT /collections/{name}/points`).
- Batch size 100 points/request (phu hop production RAM yeu 1.5GB).
- Xoa `QdrantEmbeddingStore` bean khoi `QdrantConfig` (bean khong con su dung).
- Make `queryCacheKey()` public de test coverage.
- Tao 3 test classes moi: EmbeddingServiceCacheTest, NormalizedTableIngestTest, NoHardcodedLexiconInTableNormalizerTest.

## 6) Danh sach file da doc
- `docs/eval/results/DOCX_QDRANT_CELLS_JSON_VERIFY_24D3_20260528.md`
  - Muc dich: hieu ket qua 24D3, xac nhan root cause stage D.
  - Ket luan: DB dung, Qdrant sai, 8/8 targets MISMATCH do encoding.
- `reports/refactor/CURSOR_REPORT_24D3_DOCX_QDRANT_CELLS_JSON_VERIFY.md`
  - Muc dich: xac nhan root cause hypothesis.
  - Ket luan: mojibake xay ra tai serialize cells_json vao Qdrant payload.
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/EmbeddingService.java`
  - Muc dich: trace write path, xac nhan `qdrantEmbeddingStore.addAll()` la diem sai.
  - Ket luan: gRPC path la duy nhat khac voi REST path da hoat dong dung.
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/config/QdrantConfig.java`
  - Muc dich: xac nhan QdrantEmbeddingStore bean, qdrant.port.
  - Ket luan: bean chi dung trong EmbeddingService, co the xoa sau fix.
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/QdrantPurgeService.java`
  - Muc dich: hieu REST-based Qdrant API pattern da dung trong project.
  - Ket luan: RestClient + MediaType.APPLICATION_JSON da duoc dung de goi Qdrant REST API, va khong co encoding issue. Day la template cho fix.
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/DocumentService.java`
  - Muc dich: trace flow tu parse → chunk → save → embedAndStore.
  - Ket luan: savedChunks tu saveAll() co cellsJson la Java String dung Unicode truoc khi truyen vao EmbeddingService.
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/domain/document/DocumentChunk.java`
  - Muc dich: xac nhan cellsJson la @Column(columnDefinition = "TEXT").
  - Ket luan: JPA entity luu va doc cellsJson dung.
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/record/DocumentChunk.java`
  - Muc dich: xac nhan pipeline record cellsJson field.
  - Ket luan: String, generated boi NormalizedTableService.cellsToJson() dung Jackson.
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/NormalizedTableService.java`
  - Muc dich: xac nhan cellsToJson() dung Jackson ObjectMapper.
  - Ket luan: `JSON.writeValueAsString(cells)` voi `new ObjectMapper()` → correct UTF-8.
- `Backend/pom.xml`
  - Muc dich: xac nhan langchain4j-bom version (1.0.0-beta1) va cac dep.
  - Ket luan: langchain4j-qdrant duoc include, dung gRPC + io.qdrant:client.
- `Backend/src/main/resources/application-docker.yml`
  - Muc dich: xac nhan qdrant.port (6334 = gRPC), qdrant.http-port (6333 = REST).
  - Ket luan: hai port phan biet. Fix dung http-port cho REST.
- `docker-compose.yml`
  - Muc dich: xac nhan ca hai port 6333 va 6334 duoc expose.
  - Ket luan: compatible voi fix.

## 7) Danh sach file da sua

- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/EmbeddingService.java`
  - Sua de: xoa `QdrantEmbeddingStore` field, them `upsertToQdrant()` method su dung RestClient.
  - Make `queryCacheKey()` public cho test access.
  - Anh huong lop: **service (embedding write path to Qdrant)**.
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/config/QdrantConfig.java`
  - Sua de: xoa `QdrantEmbeddingStore` bean va `qdrant.port` injection (gRPC khong con dung).
  - Anh huong lop: **config**.
- `Backend/src/test/java/KLTN/RAG_CHATBOT_BE/EmbeddingServiceCacheTest.java`
  - Tao moi: tests cache key logic + Jackson UTF-8 payload serialization.
  - Anh huong lop: **test**.
- `Backend/src/test/java/KLTN/RAG_CHATBOT_BE/NormalizedTableIngestTest.java`
  - Tao moi: tests cellsToJson Vietnamese Unicode preservation.
  - Anh huong lop: **test**.
- `Backend/src/test/java/KLTN/RAG_CHATBOT_BE/NoHardcodedLexiconInTableNormalizerTest.java`
  - Tao moi: audit test dam bao khong co hardcoded Vietnamese labels trong NormalizedTableService.
  - Anh huong lop: **test**.

## 8) Diff thay doi cua tung file

### File: `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/EmbeddingService.java`

**Hien trang cu (lien quan bug):**
```java
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
...
private final QdrantEmbeddingStore qdrantEmbeddingStore;
...
qdrantEmbeddingStore.addAll(embeddings, segments); // gRPC → corrupt Unicode
```

**Da sua:**
```diff
- import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
...
- private final QdrantEmbeddingStore qdrantEmbeddingStore;
+ private static final int QDRANT_UPSERT_BATCH_SIZE = 100;
...
- qdrantEmbeddingStore.addAll(embeddings, segments);
+ upsertToQdrant(embeddings, segments, documentId);
...
+ static → public static String queryCacheKey(...)
...
+ private void upsertToQdrant(List<Embedding> embeddings, List<TextSegment> segments, UUID documentId) {
+     // Build points list: {id, vector, payload}
+     // payload = {"text_segment": text} + all metadata (cells_json as proper String)
+     // Batch upsert via RestClient.put() → Jackson UTF-8 JSON → Qdrant REST API
+     // No gRPC, no protobuf encoding bugs
+ }
```

**Vi sao:** gRPC path corrupt Vietnamese multi-byte UTF-8. REST + Jackson giu nguyen Unicode.
**Anh huong:** Qdrant payload cells_json bay gio la correct Unicode thay vi mojibake.

### File: `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/config/QdrantConfig.java`

**Hien trang cu:**
```java
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
...
@Value("${qdrant.port}")
private int port;
...
@Bean
public QdrantEmbeddingStore qdrantEmbeddingStore() {
    return QdrantEmbeddingStore.builder().host(host).port(port).collectionName(collectionName).build();
}
```

**Da sua:**
```diff
- import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
...
- @Value("${qdrant.port}")
- private int port;
...
- @Bean
- public QdrantEmbeddingStore qdrantEmbeddingStore() {
-     return QdrantEmbeddingStore.builder().host(host).port(port).collectionName(collectionName).build();
- }
```

**Vi sao:** bean khong con su dung sau khi EmbeddingService chuyen sang REST upsert.

### File: Test files (moi tao)

3 test files moi tao, khong co diff cu. Tat ca pass 0 failures.

## 9) Anh huong sau sua

- **Behavior thay doi:** Qdrant upsert dung REST API (port 6333) thay vi gRPC (port 6334). Vietnamese Unicode trong cells_json duoc giu nguyen trong Qdrant payload.
- **Behavior giu nguyen:** Qdrant search path khong thay doi (van dung RestClient REST). DB write khong thay doi. Chunking/parsing khong thay doi.
- **text_segment key:** giu nguyen, REST payload dung cung key `text_segment` nhu gRPC truoc day.
- **Fallback:** khong co fallback nao bi anh huong.
- **Memory/CPU:** upsert theo batch 100 points → it anh huong RAM hon so voi tao tat ca points trong memory mot lan (3296 × ~3KB = ~10MB van la acceptable).
- **Latency:** upsert REST co the cham hon gRPC mot it (network overhead per batch), nhung 33 batches × ~100ms = ~3s anh huong nho tren 77s total ingest time.
- **Qdrant point ID:** Random UUID nhu cu (LangChain4j cung dung UUID random truoc day). DB.qdrantPointId truoc day la null nen khong co regression.
- **gRPC port 6334:** khong con duoc dung boi backend, nhung van expose trong docker-compose (khong can xoa ngay).

## 10) Edge cases da xem xet

- `cells_json` null/blank: `putIfPresent()` guard van hoat dong → khong dua vao metadata → khong dua vao payload. OK.
- Empty chunks list: guard `if (chunks == null || chunks.isEmpty()) return;` van con.
- Empty embeddings list: `upsertToQdrant()` guard `if (embeddings.isEmpty()) return;` them vao.
- Batch boundary: 3296 chunks = 33 batches × 100 points (32 full + 1 partial = 96). Khong co off-by-one.
- Qdrant unavailable: `RestClient` nem exception, duoc propagate len `DocumentService`, document status set FAILED. Behavior cu giu nguyen.
- Vietnamese characters trong `text_segment`: cung duoc serialize dung qua Jackson. Khong chi cells_json.
- Large payload: 3296 points × ~500 bytes payload = ~1.6MB total. OK.
- `QdrantEmbeddingStore` bean: da xoa khoi QdrantConfig. Spring context khong con try inject no vao bat ky dau. gRPC connection khong con duoc tao.

## 11) Ket qua kiem tra

| Command | Ket qua | Ghi chu |
|---|---|---|
| `docker compose config -q` | PASS | compose config hop le |
| `cd Backend && ./mvnw -DskipTests compile` | PASS | 121 source files, 1 warning (RagTokenAudit deprecated, pre-existing) |
| `.\mvnw.cmd "-Dtest=EmbeddingServiceCacheTest,NormalizedTableIngestTest,NoHardcodedLexiconInTableNormalizerTest" test` | PASS | 22 tests, 0 failures, 0 errors |
| `docker compose up --build -d backend` | PASS | Backend built va started thanh cong |
| `POST /api/chatbots` | PASS | chatbotId=`1c04ed8b-bbb5-4aba-a257-59fe1dd80b38` |
| Upload DOCX via curl | PASS | documentId=`83a9f386-...`, 3296 chunks, ~77s |
| `GET /api/documents?chatbotId=...` | PASS | status=INDEXED, progress=100, chunkCount=3296 |
| MySQL chunk count | PASS | 3296 total, 3078 normalized_table_row, 209 table_summary |
| Qdrant point count (REST) | PASS | 3296 points cho document moi |
| DB hex dump cells_json | PASS | C3A3=ã, E1BB8D=ọ, E1BAA7=ầ confirmed UTF-8 |
| Qdrant payload scroll (8 targets) | PASS | 8/8 unicode=OK, cells_json correct Vietnamese |
| Backend log `[EmbeddingUpsert]` | PASS | points=3296 batches=33 |

## 12) Rui ro con lai

- **Stale documents**: 4 documents ingested truoc fix van co Qdrant payload mojibake. Cac chatbot su dung cac documents nay se co retrieval quality kem. Can cleanup sau khi co approval.
- **gRPC port**: `qdrant.port=6334` van con trong `application-docker.yml` va docker-compose, nhung khong dung. Co the xoa sau neu muon clean up.
- **Regression potential**: `QdrantEmbeddingStore` bean da xoa — neu co code nao khac trong tuong lai inject no thi se fail. Hien tai khong co nhu vay.
- **REST vs gRPC performance**: upsert REST co the cham hon gRPC o scale lon hon, nhung o production 20 user + tai lieu nho, la khong dang ke.

## 13) De xuat tiep theo

- Cleanup stale documents (can approval truoc khi xoa).
- Remove `qdrant.port` property khoi `application-docker.yml` neu khong can nua.
- Optional: remove gRPC port 6334 expose khoi docker-compose.
- Consider adding integration test bao phong truong hop Qdrant REST upsert thu lieu tieng Viet va read back.
