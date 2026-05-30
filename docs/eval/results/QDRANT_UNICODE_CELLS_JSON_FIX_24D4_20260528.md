# QDRANT UNICODE CELLS_JSON FIX 24D4 (2026-05-28)

## Final verdict
- **PASS**
- Ly do: encoding fix da loai bo mojibake trong Qdrant payload cells_json cho toan bo target rows.
- DB cells_json dung, Qdrant cells_json dung, DB/Qdrant parity MATCH cho 8/8 targets.

## Scope va rang buoc da tuan thu
- FIX + VERIFY, khong chay Q1-Q8.
- Khong sua DOCX file.
- Khong sua 24D2 logical-grid mapping.
- Khong re-enable markdown bridge / table_row_group / text_table_like / raw fallback.
- Khong patch Qdrant values manually.
- Khong hardcode course codes, major names, lecturers, Vietnamese labels.

## Root cause xac nhan

**Stage D (HTTP request body / gRPC serialization)**: LangChain4j `QdrantEmbeddingStore.addAll()` su dung gRPC de ghi payload len Qdrant. Duong gRPC/protobuf da convert Vietnamese Unicode strings sai (multi-byte UTF-8 bytes bi corrupt thanh mojibake). Java String trong memory hoan toan dung Unicode, DB MySQL cung dung, nhung khi serialize qua LangChain4j gRPC path thi Vietnamese characters bi mat.

Dang loi tren Qdrant truoc fix:
```
"MA\uef h\uef\ubf\ubdc ph\uefn" thay vi "Ma hoc phan"
"PhA\uf1p lu\uef-t Vi\uef\ubft Nam" thay vi "Phap luat Viet Nam"
```

## Fix da ap dung

Thay the `qdrantEmbeddingStore.addAll(embeddings, segments)` (LangChain4j gRPC) bang mot method moi `upsertToQdrant()` su dung Spring `RestClient` de goi Qdrant REST API (`PUT /collections/{name}/points`). Spring RestClient + Jackson ObjectMapper serialize Map<String,Object> thanh UTF-8 JSON chinh xac, giu nguyen Vietnamese Unicode.

Files da sua:
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/EmbeddingService.java`
- `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/config/QdrantConfig.java`

## Ingest canary 24D4

- File: `docs/eval/manual/SoTayHocVu_HocKy1_2025-2026_RAG_CONTEXT_HOC_KY.docx`
- chatbotId: `1c04ed8b-bbb5-4aba-a257-59fe1dd80b38`
- documentId: `83a9f386-f82f-40fd-b807-b0b94776bfb4`
- status: `INDEXED`
- progress: `100`
- API chunkCount: `3296`
- DB chunk count: `3296`
- Qdrant point count: `3296`
- DB normalized_table_row: `3078`
- DB table_summary: `209`
- Ingest duration: ~77s
- Backend log: `[EmbeddingUpsert] document=83a9f386... points=3296 batches=33`

## DB/Qdrant cells_json parity table

| Target | chunkId | rowIndex | DB unicode | Qdrant unicode | Parity | Verdict |
|---|---|---:|---|---|---|---|
| LUA1012 - Nhom 1 | `ca0e4dce-db3f-4c56-b290-2fd95e85e244` | 1 | OK | OK | MATCH | CORRECT |
| LUA1012 - Nhom 2 | `7fb6c728-ac98-4348-a320-d8f6d7bd9cd2` | 2 | OK | OK | MATCH | CORRECT |
| TIN1093 - Nhom 15 | `3590bb3d-51a3-4bbf-b2f3-89e81df9ab3a` | 378 | OK | OK | MATCH | CORRECT |
| KNM1013 - Nhom 2 | `8745951d-122c-4567-85ca-b7f3eecfb289` | 9 | OK | OK | MATCH | CORRECT |
| KNM1013 - Nhom 4 | `44c3ba86-ca75-4bf6-a7b8-42bb44762502` | 11 | OK | OK | MATCH | CORRECT |
| KTR3185 | `a9f0b1a8-a2ef-41c5-9fe7-4de04fe69e4a` | 715 | OK | OK | MATCH | CORRECT |
| Kien truc K46 HK1 sample | `669c05bb-a74d-429b-83b7-b69775265ef1` | 1 | OK | OK | MATCH | CORRECT |
| Cong nghe sinh hoc K46 HK1 sample | `42a4fd3a-cc6d-4980-b73b-d4b8e2315c42` | 1 | OK | OK | MATCH | CORRECT |

Tat ca 8/8 targets: DB/Qdrant MATCH, unicode OK.

## Mau cells_json truoc va sau fix

### Truoc fix (24D3, Qdrant payload - mojibake)
```json
{"STT":"1","MA\u00ef h\u00ef\u00bf\u00bdc ph\u00ef\u00bfn":"LUA1012","TA\u00efn l\u00ef\u00bf\u00bdp h\u00ef\u00bfc ph\u00ef\u00bfn":"PhA\u00efp lu\u00ef-t Vi\u00ef\u00bft Nam \u00ef\u00bf\u00bd\u00ef\u00bf\u00bdi c\u00ef\u00bf\u00bd\u00ef\u00bf\u00bdng - NhA3m 1"}
```

### Sau fix (24D4, Qdrant payload - correct Unicode)
```json
{"STT":"1","Mã học phần":"LUA1012","Tên lớp học phần":"Pháp luật Việt Nam đại cương - Nhóm 1","Số TC":"2","Số SV":"0","Giảng viên":"Nguyễn Thị Vân Anh","Ngày bắt đầu":"08/09/2025","Thứ":"2","Tiết học":"1 - 2","Phòng":"E301","Ghi chú":""}
```

## Tests run

| Test class | Tests | Failures | Errors |
|---|---:|---:|---:|
| EmbeddingServiceCacheTest | 9 | 0 | 0 |
| NoHardcodedLexiconInTableNormalizerTest | 3 | 0 | 0 |
| NormalizedTableIngestTest (root pkg) | 5 | 0 | 0 |
| NormalizedTableIngestTest (service pkg, pre-existing) | 5 | 0 | 0 |
| **Total** | **22** | **0** | **0** |

## Stale old document warning

Documents ingested truoc task 24D4 van co Qdrant payload bi mojibake:
- chatbotId `a53cea76-8f8b-4ade-9558-ffb2cceafa87` / documentId `4c4e37a9-afb8-4fe1-a973-cda82522b659` (24D3 ingest, mojibake Qdrant)
- documentId `1d390ff1-6503-4f5e-bddd-f14562680ee1` (old HOC_KY run)
- documentId `8045fede-bc22-42ed-92e5-ca6e529b3cea` (old HOC_KY run)
- documentId `9c2f134e-fc17-4928-b91b-eb3330ee4019` (old HK_SPLIT run)

De nghi xoa sau khi co approval.

## Known limitations
- Qdrant point IDs khong con match DB.qdrantPointId (truoc day cung null, nen khong co regression).
- QdrantEmbeddingStore bean trong QdrantConfig da duoc xoa (gRPC port 6334 khong con su dung de ghi).
- gRPC port 6334 van expose trong docker-compose nhung khong con dung.

## Next recommended task
- Cleanup stale documents (can co approval truoc khi xoa).
- Optional: remove `qdrant.port` property tu application-docker.yml neu khong can nua.
