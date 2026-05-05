# Report: Bổ sung thông tin còn thiếu vào folder agent

**Ngày**: 2026-05-05  
**Task**: Đọc toàn bộ source code dự án và bổ sung thông tin còn thiếu hoặc stale vào folder `agent/` và `agent.md`.

---

## 1. Mức độ hiểu task
- Hiểu task: **95%**
- Phần chắc chắn: toàn bộ nội dung đã đọc trực tiếp từ source code Java và JS.
- Phần còn giả định: `ChatPage.jsx` (trang chat admin) chưa đọc chi tiết — nhưng không ảnh hưởng đến docs đã cập nhật vì widget là luồng chính.
- Không thiếu dữ kiện quan trọng.

---

## 2. Tóm tắt yêu cầu
Bổ sung thông tin còn thiếu vào folder `agent/` (6 file) và `agent.md` (root), dựa trên source code thực tế của project CHATBOT_RAG.

---

## 3. Hiện trạng trước khi sửa

Docs cũ lag so với code hiện tại ở nhiều điểm quan trọng:

| Điểm stale | Docs cũ | Thực tế trong code |
|---|---|---|
| Upload API path | `POST /api/documents/upload` | `POST /api/documents/upload/{widgetId}` |
| TOP_K | `TOP_K = 8` | `ANCHOR_TOP_K = 30`; final limit 10/20/60 tùy mode |
| Document PK | `id` auto increment | UUID (`GenerationType.UUID`) |
| DB schema | `chat_messages` với session_id string | `chat_sessions` table riêng, `chat_messages` FK `chat_session_id` |
| Security | "permitAll", "chưa có auth" | `WidgetAuthFilter` xác thực `X-Widget-Key` cho `/api/chat/**` |
| Widget API | `GET /api/widgets/{apiKey}` | Endpoint này **không tồn tại** |
| Retrieval | "search top-k từ Qdrant" | 7-step pipeline: intent detection, heading lock, multi-stage expansion, Cohere rerank, budget |
| Chunking | CHUNK_SIZE=1200, OVERLAP=200 | `ChunkingService2`: MAX_CHARS=2200, OVERLAP=250, phân loại 6 chunk types |
| Multi-tenant | Chưa đề cập | `WidgetConfig` là root, tất cả entities scoped theo `widgetConfigId` |
| Qdrant payload | documentId, fileName, chunkIndex, text_segment | +widgetId, section_id, chunk_type, heading_path, child_section_ids, table_id, page_start/end, order_index |
| Fallback models | Không đề cập | llama-3.1-8b-instant, llama-4-scout, qwen3-32b |
| Cohere rerank | Không đề cập | Optional, model rerank-multilingual-v3.0 |
| preprocess/ | Không đề cập | Standalone Java module, không phụ thuộc Backend runtime |

---

## 4. Nguyên nhân gốc xác nhận từ source

Docs được viết từ giai đoạn đầu project khi kiến trúc còn đơn giản. Code đã phát triển đáng kể:
1. Widget multi-tenant được thêm sau → upload path, chat auth, Qdrant filter đều thay đổi.
2. `RagRetrievalService` được refactor thành pipeline 7 bước phức tạp.
3. `ChunkingService2` thay thế `ChunkingService` với nhiều chunk types.
4. `ChatSession` table được thêm để hỗ trợ multi-tenant session.
5. `WidgetAuthFilter` được implement nhưng không cập nhật vào docs.

---

## 5. Chiến lược sửa đã chọn

- Đọc toàn bộ source code liên quan (controllers, services, entities, configs, frontend pages).
- Cập nhật từng file `agent/*.md` theo scope đã phân chia.
- Cập nhật `agent.md` root (navigation + snapshot).
- Không sửa source code, chỉ sửa docs.
- Giữ nguyên format, bổ sung thông tin thực tế.

---

## 6. Danh sách file đã đọc

| File | Đọc để làm gì | Kết luận chính |
|---|---|---|
| `agent/01-06.md` | Hiểu hiện trạng docs | Nhiều điểm stale, thiếu multi-tenant, sai API paths |
| `agent.md` | Hiểu navigation và snapshot | TOP_K sai, DB schema sai, thiếu widget auth |
| `Backend/src/.../api/ChatController.java` | Xác nhận chat endpoints và auth | Dùng `@RequestAttribute("Widget-Id")`, filter bắt buộc |
| `Backend/src/.../api/DocumentController.java` | Xác nhận upload path | `POST /api/documents/upload/{widgetId}` |
| `Backend/src/.../api/WidgetController.java` | Xác nhận widget endpoints | Chỉ có `POST /api/widgets`, không có GET |
| `Backend/src/.../config/WidgetAuthFilter.java` | Xác nhận auth mechanism | X-Widget-Key header, findByApiKey, set Widget-Id attribute |
| `Backend/src/.../config/SecurityConfig.java` | Xác nhận CORS và security chain | permitAll nhưng filter lo auth, CORS localhost only |
| `Backend/src/main/resources/application.yml` | Config mặc định | Profile dev, cleaner config, multipart 50MB |
| `Backend/src/main/resources/application-dev.yml` | Config dev | Groq model, fallback models, Nomic, Cohere, Qdrant |
| `Backend/src/.../service/RagRetrievalService.java` | Xác nhận retrieval logic | 7-step pipeline, ANCHOR_TOP_K=30, final 10/20/60, rerank |
| `Backend/src/.../service/ChunkingService2.java` | Xác nhận chunk types | 6 types, MAX_CHARS=2200, pseudo-table detection |
| `Backend/src/.../service/QueryAnalyzerService.java` | Xác nhận query types | 6 query types, heading match scoring, query rewriting |
| `Backend/src/.../domain/document/Document.java` | Xác nhận schema | UUID PK, widget_config_id FK, checksum, soft delete |
| `Backend/src/.../domain/widget/WidgetConfig.java` | Xác nhận widget entity | apiKey UUID unique, allowedOrigin JSON, uiConfig JSON |
| `Backend/src/.../domain/chat/ChatSession.java` | Xác nhận session entity | Mới: widget_config_id FK, sessionKey, widgetOrigin |
| `docker-compose.yml` | Xác nhận infra | 4 services, env vars, healthcheck MySQL |
| `Frontend/src/api/widgetApi.js` | Xác nhận FE widget API | Chỉ createWidget, không có getWidget |
| `Frontend/src/api/axiosInstance.js` | Xác nhận Axios config | baseURL VITE_API_URL, timeout 30s |
| `Frontend/src/pages/WidgetChatPage.jsx` | Xác nhận SSE handling | X-Widget-Key header, JSON token parse, widgetKey resolution |

---

## 7. Danh sách file đã sửa

| File | Sửa để làm gì | Lớp ảnh hưởng |
|---|---|---|
| `agent/01-overview.md` | Thêm multi-tenant concept, widget auth status, preprocess/ module | docs |
| `agent/02-architecture.md` | Rewrite: WidgetConfig trung tâm, DB schema thực, Qdrant payload, retrieval pipeline, chunk types | docs |
| `agent/03-backend.md` | Rewrite: UUID PKs, entities mới (ChatSession/Section/Chunk), WidgetAuthFilter, ChunkingService2, LlmFallbackService, RerankService | docs |
| `agent/04-frontend.md` | Cập nhật: widgetKey resolution, X-Widget-Key header, SSE token format, sources fields | docs |
| `agent/05-api.md` | Sửa upload path, xóa endpoint không tồn tại, thêm X-Widget-Key requirement, cập nhật response schemas | docs |
| `agent/06-operations.md` | Bổ sung: env vars đầy đủ, fallback models, Cohere config, Docker template, schema migration note, production roadmap | docs |
| `agent.md` | Rewrite snapshot: API paths đúng, DB schema thực, TOP_K đúng, multi-tenant context | docs |

---

## 8. Diff thay đổi của từng file

### agent/01-overview.md
```diff
+ Thêm multi-tenant theo WidgetConfig
+ Admin flow: tạo widget → upload → user chat
+ Status: bảo mật chat ĐÃ implement (WidgetAuthFilter)
+ Thêm: chunking nâng cao, retrieval nâng cao
+ Thêm: preprocess/ là standalone module
```

### agent/02-architecture.md
```diff
- Mô hình đơn giản "FE/BE, MySQL, Qdrant"
+ WidgetConfig là multi-tenant root
+ Bảng module đầy đủ (QueryAnalyzerService, RerankService, LlmFallbackService, WidgetAuthFilter...)
+ Bảng entities MySQL với UUID PKs, các tables mới (document_sections, chat_sessions...)
+ Qdrant payload đầy đủ (widgetId, section metadata, chunk types)
+ Retrieval pipeline 7 bước với constants thực tế
+ Chunk types table
+ preprocess/ standalone note
```

### agent/03-backend.md
```diff
- "Document id (PK, auto increment)"
+ UUID PKs cho tất cả entities

- Không có WidgetAuthFilter detail
+ WidgetAuthFilter chi tiết: X-Widget-Key → findByApiKey → Widget-Id attribute

- Chunking: CHUNK_SIZE=1200
+ ChunkingService2: MAX_CHARS=2200, OVERLAP=250, 6 chunk types, pseudo-table detection

- Không có LlmFallbackService
+ LlmFallbackService: fallback models list

- "chat_messages với session_id"
+ ChatSession entity, ChatMessage FK chat_session_id
```

### agent/04-frontend.md
```diff
- "FE lưu sessionId trong localStorage"
+ Thêm widgetKey resolution (URL param → localStorage → env var)
+ Thêm X-Widget-Key header requirement
+ SSE token format: JSON {"token": "..."} để giữ whitespace
+ Sources fields: fileName, sectionTitle, chunkType, pages, chunkText
```

### agent/05-api.md
```diff
- POST /api/documents/upload
+ POST /api/documents/upload/{widgetId}  ← QUAN TRỌNG: sai hoàn toàn

- GET /api/widgets/{apiKey}  (không tồn tại)
+ Ghi rõ: endpoint này KHÔNG tồn tại

+ Thêm X-Widget-Key header requirement cho /api/chat/**
+ Cập nhật response schema DocumentListItemResponse (+widgetConfigId)
+ Thêm bảng "endpoints chưa implement"
```

### agent/06-operations.md
```diff
- "khóa Groq, khóa embedding provider"
+ Tên biến thực tế: GROQ_API_KEY, NOMIC_API_KEY, COHERE_API_KEY, COHERE_RERANK_ENABLED

+ Thêm fallback models list
+ Thêm Cohere rerank config + free tier note
+ Template .env
+ Schema migration note (ddl-auto=update, schema_update.sql)
+ Mở rộng production roadmap (auth document endpoints, CORS, rate limiting, pagination...)
+ Build checks commands đầy đủ
```

### agent.md
```diff
- "TOP_K = 8"
+ ANCHOR_TOP_K=30; final limit 10/20/60 tùy intent + locked scope

- "id (PK, auto increment)"
+ UUID PKs; bảng mới: widget_configs, document_sections, document_chunks, document_tables, chat_sessions

- "POST /api/documents/upload"
+ "POST /api/documents/upload/{widgetId}"

- "security đang permitAll"
+ WidgetAuthFilter bảo vệ /api/chat/**, auth đã implement

+ Thêm multi-tenant context, widget auth flow
+ Progress snapshot cập nhật 2026-05-05
```

---

## 9. Ảnh hưởng sau sửa

- Không có thay đổi code, chỉ thay đổi docs.
- AI agent đọc docs sẽ có thông tin chính xác về API paths, auth mechanism, DB schema.
- Developer onboarding sẽ không bị nhầm lẫn về `POST /api/documents/upload` (sai) vs `/{widgetId}` (đúng).
- Không ảnh hưởng memory/CPU/disk/latency.

---

## 10. Edge cases đã xem xét

- `preprocess/` không bị nhầm lẫn với Backend runtime code → đã ghi rõ là standalone.
- `GET /api/widgets/{apiKey}` không tồn tại → đã ghi rõ trong `05-api.md`.
- Cohere disabled mặc định → đã ghi rõ fallback behavior.
- `VITE_WIDGET_API_KEY` là build-time fallback → đã ghi rõ trong frontend docs.
- `ChatPage.jsx` chưa đọc chi tiết → không ảnh hưởng docs vì docs tập trung vào widget flow.

---

## 11. Kết quả kiểm tra

| Command | Kết quả | Ghi chú |
|---|---|---|
| `cd Backend && ./mvnw -DskipTests compile` | NOT RUN | Chỉ sửa docs, không sửa code |
| `cd Backend && ./mvnw test` | NOT RUN | Không sửa code |
| `cd Frontend && npm run lint` | NOT RUN | Không sửa code |
| `cd Frontend && npm run build` | NOT RUN | Không sửa code |
| `cd Frontend && npm run build:widget` | NOT RUN | Không sửa code |
| `docker compose config` | NOT RUN | Không sửa docker-compose.yml |

Không có file code nào được sửa — chỉ docs markdown.

---

## 12. Rủi ro còn lại

- `ChatPage.jsx` (admin chat UI) chưa được đọc chi tiết → nếu có X-Widget-Key logic đặc biệt thì `04-frontend.md` có thể cần bổ sung thêm.
- `EmbeddingService.java` chưa đọc chi tiết → Qdrant payload fields trong docs dựa trên đọc từ `RagRetrievalService` và `ChunkingService2`; nếu có field khác thì cần bổ sung.
- `PromptBuilderService.java` chưa đọc → prompt format chưa được document.
- Không có rủi ro lớn trong scope đã sửa (chỉ docs).

---

## 13. Đề xuất tiếp theo

1. **Đọc và document `PromptBuilderService`**: hệ thống prompt là phần quan trọng ảnh hưởng chất lượng RAG.
2. **Thêm `agent/07-prompt.md`**: document system prompt template, cách dùng context/history/scope label.
3. **Implement auth cho `/api/documents/**`**: hiện bất kỳ ai cũng có thể upload hoặc tạo widget.
4. **Cập nhật `agent.md` Progress Snapshot** sau mỗi sprint lớn.
5. **Đọc và verify `ChatPage.jsx`**: để bổ sung vào `04-frontend.md` nếu cần.
