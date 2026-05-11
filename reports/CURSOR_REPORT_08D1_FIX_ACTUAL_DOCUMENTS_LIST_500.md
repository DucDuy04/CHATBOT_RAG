# Cursor Report 08D1 - FIX_ACTUAL_DOCUMENTS_LIST_500

## 1. Mức độ hiểu task

- **Task là gì?** Sửa lỗi thật `GET /api/documents` trả HTTP 500 (không đoán): lấy stacktrace runtime, sửa đúng root cause, đảm bảo các biến thể query (filter rỗng, status, chatbotId invalid, UI label status) không 500; chỉnh nhẹ FE `documentsApi.js` nếu cần; không đổi schema DB / không đụng RAG core / không widget public.
- **Hiểu task:** 98%
- **Phạm vi không làm:** Public widget/chat, JWT/auth, schema DB, ingest/vector/Qdrant behavior, refactor lớn ngoài list/map/validation.

## 2. Files/rules/reports đã đọc

| File | Mục đích đọc | Kết luận chính |
|------|----------------|----------------|
| `.cursor/rules/00-core-working-rule.mdc` | Luật nền | Minimal diff, không đoán mò |
| `.cursor/rules/10-backend-rag-rule.mdc` | Backend checklist | Documents service/controller là tâm điểm |
| `.cursor/rules/20-frontend-widget-rule.mdc` | FE API | `documentsApi` params |
| `.cursor/rules/30-deploy-env-ops-rule.mdc` | Runtime | Profile `dev`, MySQL local |
| `.cursor/rules/40-db-vector-rule.mdc` | DB/vector | Không đổi schema |
| `.cursor/rules/90-report-verification-rule.mdc` | Report | Chi tiết audit |
| `reports/CURSOR_REPORT_08D_FIX_REMAINING_BROWSER_E2E_ISSUES.md` | Tiền đề 08D | Đã sửa `cb.conjunction()` nhưng user vẫn 500 |
| `reports/CURSOR_REPORT_03C_BACKEND_DOCUMENTS_RUNTIME_SMOKE_TEST.md` | Smoke documents | JVM cũ vs source — cần restart đúng bản |
| `reports/CURSOR_REPORT_08C_FIX_BROWSER_E2E_DOCUMENTS_AND_PLAYGROUND_ISSUES.md` | FE documents | Toolbar dùng `value: ""` cho All |
| `Backend/.../DocumentController.java` | Entry list | Trả `DocumentPageResponse` |
| `Backend/.../DocumentService.java` | Spec + map | `buildDocumentSpec`, `toDocumentResponse` |
| `Backend/.../domain/document/Document.java` | Entity | `@SQLRestriction` soft delete document |
| `Backend/.../domain/widget/WidgetConfig.java` | Entity | `@SQLRestriction("deleted_at IS NULL")` trên widget |
| `Frontend/src/api/documentsApi.js` | Request shape | Đã omit param rỗng từ 08D; bổ sung thêm hardening |

## 3. Runtime source verification

| Hạng mục | Giá trị |
|----------|---------|
| **Có process cũ trên 8080 không?** | Có — lần đầu `LISTENING` PID **17448** (trước khi reproduce stacktrace). |
| **Đã restart backend đúng source chưa?** | Có — `taskkill /PID 17448 /F`, sau đó `.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"` từ `E:\chatbot-rag-workspace\CHATBOT_RAG\Backend` với biến môi trường nạp từ `.env` ở root repo (không ghi secret trong report). |
| **Sau khi sửa code** | `taskkill /PID 2764 /F` (JVM đang listen 8080), compile, start lại Spring Boot cùng workspace. |
| **Command start backend** | `cd Backend` → `.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"` |
| **Base URL** | `http://localhost:8080` |
| **Branch / commit (thời điểm verify)** | Branch `BE_Flow`, short SHA `7a5e2e5` (cùng workspace path trên). |

## 4. Stacktrace thật (trước khi sửa)

Gọi `GET http://localhost:8080/api/documents?page=0&size=10` khi backend đang chạy bản source có `cb.conjunction()` nhưng chưa có fix mapper — log console (rút gọn phần không liên quan):

```
jakarta.persistence.EntityNotFoundException: Unable to find KLTN.RAG_CHATBOT_BE.domain.widget.WidgetConfig with id 7365d06a-9719-44ba-a1cb-6aebf56e8aba
	at org.hibernate.jpa.boot.internal.EntityManagerFactoryBuilderImpl$JpaEntityNotFoundDelegate.handleEntityNotFound(...)
	at org.hibernate.proxy.AbstractLazyInitializer.initialize(...)
	at KLTN.RAG_CHATBOT_BE.domain.widget.WidgetConfig$HibernateProxy$....getName(Unknown Source)
	at KLTN.RAG_CHATBOT_BE.service.DocumentService.toDocumentResponse(DocumentService.java:373)
	at java.base/java.util.stream.ReferencePipeline$3$1.accept(...)
	...
	at KLTN.RAG_CHATBOT_BE.service.DocumentService.listDocuments(DocumentService.java:189)
	at KLTN.RAG_CHATBOT_BE.api.DocumentController.listDocuments(DocumentController.java:120)
```

- **Exception class:** `jakarta.persistence.EntityNotFoundException`
- **Message:** Unable to find `WidgetConfig` với UUID cụ thể (widget đã không còn visible cho Hibernate — ví dụ **soft-deleted** do `@SQLRestriction("deleted_at IS NULL")` trên `WidgetConfig`, trong khi `Document` vẫn giữ FK / proxy).
- **Project file/line:** `DocumentService.toDocumentResponse` ~**373** (truy cập `w.getName()` trên proxy); `listDocuments` ~**189** (stream map).
- **Root cause cuối cùng:** Lazy-load `WidgetConfig` khi map DTO; Hibernate không load được entity (không tồn tại theo restriction) → **EntityNotFoundException** → Spring trả **500**. **Không** phải lỗi `cb.and()` rỗng ở bước này (đoạn đó đã là `conjunction()` trong source hiện tại).

## 5. Root cause (giải thích chính xác)

`/api/documents?page=0&size=10` load trang `Document` và map từng phần tử sang `DocumentResponse` trong `toDocumentResponse`. Code cũ gọi `w.getName()` khi `w` là proxy tới `WidgetConfig` đã **soft-delete** (hoặc thiếu row tương thích restriction). `@SQLRestriction` trên `WidgetConfig` khiến truy vấn load không thấy row → Hibernate ném `EntityNotFoundException` → toàn bộ request **500** dù query list document thành công.

## 6. Files changed

| File | Change | Reason | Risk |
|------|--------|--------|------|
| `Backend/.../service/DocumentService.java` | `toDocumentResponse`: bắt `EntityNotFoundException`, fallback `chatbotId`/`chatbotName`; null-safe `filename`/`type`. `listDocuments`: validate UUID `chatbotId`, normalize status UI; `buildDocumentSpec`: nhận `UUID chatbotFilter`, **chỉ** `join widgetConfig` khi filter theo chatbot; bỏ predicate `1=0` cho UUID sai (chuyển sang 400 ở tầng gọi). | Hết 500 khi widget “mồ côi”; count/list query không join thừa khi không filter chatbot; invalid UUID → 400. | Thấp |
| `Backend/.../api/DocumentController.java` | `listDocuments` trả `ResponseEntity<?>`, `catch IllegalArgumentException` → **400** + `{ "message": ... }`. | Contract: `chatbotId` không phải UUID → 400 JSON. | Thấp |
| `Frontend/src/api/documentsApi.js` | Regex UUID cho `chatbotId`; bỏ qua status sentinel `All statuses` / `All status`. | Defensive: không gửi param rác; khớp yêu cầu 08D-1. | Thấp |

## 7. Detailed fix

### `DocumentService.java`

- **`toDocumentResponse`:** bọc truy cập `w.getId()` / `w.getName()` trong `try/catch (EntityNotFoundException)`; fallback `chatbotName = "Unknown chatbot"`, `chatbotId = null`. Chuỗi `filename` / `fileType` null-safe.
- **`listDocuments`:** `normalizeListStatusParam` — `"All statuses"` / `"All status"` (không phân biệt hoa thường) → coi như không filter. Nếu `chatbotId` non-blank mà `UUID.fromString` lỗi → `IllegalArgumentException("Invalid chatbotId")`.
- **`buildDocumentSpec`:** tham số `UUID chatbotFilter`; `root.join("widgetConfig")` **chỉ** khi `chatbotFilter != null`, tránh join không cần thiết khi list toàn bộ.

### `DocumentController.java`

- **`listDocuments`:** `try`/`catch` cho `IllegalArgumentException` từ service → `400` + `Map.of("message", ...)`.

### `documentsApi.js`

- Chỉ thêm `chatbotId` vào `params` nếu khớp regex UUID.
- Nếu `status` là sentinel UI → không gửi `status`.

## 8. Endpoint retest results

Môi trường: Spring Boot `dev` sau compile + restart; gọi bằng PowerShell với URL **single-quoted** để `&` không bị shell parse.

| Request | Expected | Actual | Status |
|---------|----------|--------|--------|
| `GET /api/documents?page=0&size=10` | 200 + `items`, `page`, `size`, `total`, `totalPages` | **200** | PASS |
| `GET /api/documents?search=&type=&chatbotId=&status=&page=0&size=10` | 200 | **200** | PASS |
| `GET /api/documents?status=INDEXED&page=0&size=10` | 200 | **200** | PASS |
| `GET /api/documents?status=All%20statuses&page=0&size=10` | Không 500 (coi như không filter) | **200** | PASS |
| `GET /api/documents?chatbotId=not-a-uuid&page=0&size=10` | 400 + `message` | **400** body `{"message":"Invalid chatbotId"}` | PASS |

## 9. Browser retest results

| Flow | Expected | Actual | Status |
|------|----------|--------|--------|
| Mở `/documents`, list load | Không “Failed to load documents”; empty hoặc có rows | **NOT RUN** (agent không mở browser trong session này) | NOT RUN |
| All statuses / Indexed filter | 200 | **NOT RUN** UI | NOT RUN |
| Upload TXT / drawer chunks / assign unsafe | Không regress | **NOT RUN** | NOT RUN |

## 10. Validation results

| Command | Result | Notes |
|---------|--------|-------|
| `cd Backend; .\mvnw.cmd -DskipTests compile` | **PASS** | exit 0 |
| `cd Backend; .\mvnw.cmd test` (env từ `.env`) | **PASS** | exit 0; Qdrant client/server version warning (đã có từ trước) |
| `cd Frontend; npm run lint` | **PASS** | |
| `cd Frontend; npm run build` | **PASS** | Vite build OK |

## 11. Known limitations / gaps

- Document vẫn có thể trỏ FK tới widget đã soft-delete: list hiển thị được nhưng `chatbotId` trong DTO có thể **null** và tên **"Unknown chatbot"** — không tự “sửa” dữ liệu FK.
- Browser E2E thủ công chưa chạy trong agent (mục 9).

## 12. Final decision

**Documents list 500 fixed.** Nguyên nhân đã xác nhận bằng stacktrace: `EntityNotFoundException` khi map `WidgetConfig` soft-deleted / không resolve được qua restriction. Có thể tiếp tục browser E2E trên máy dev.

---

