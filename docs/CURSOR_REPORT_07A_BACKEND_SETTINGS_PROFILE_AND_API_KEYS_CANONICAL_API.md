# Cursor Report 07A - BACKEND_SETTINGS_PROFILE_AND_API_KEYS_CANONICAL_API

## 1. Mức độ hiểu task

- **Task là gì?**  
  Triển khai backend Settings theo contract FE hiện tại: profile GET/PUT, API keys GET/POST/DELETE; singleton profile + entity API key tối thiểu; không plain key trong DB; không Delete Account; không JWT/auth mới.

- **Hiểu task:** 95%

- **Phần chắc chắn:**  
  - Contract từ `Frontend/src/api/settingsApi.js` + `ProfileSettingsSection.jsx` / `ApiKeysSection.jsx` (field `name`, `plainTextKey`, array list).  
  - POST create trả `{ key, plainTextKey }`.  
  - Security hiện tại `permitAll` + `WidgetAuthFilter` không áp dụng `/api/settings/**`.

- **Phần còn giả định:**  
  - POST trả HTTP **201 Created** (FE chấp nhận 200/201 theo prompt).  
  - Bảng JPA `ddl-auto: update` (dev) sẽ tạo bảng mới khi chạy app có MySQL.

- **Phạm vi không làm:**  
  - Delete account, team settings, billing, middleware xác thực bằng Settings API key, reveal endpoint, sửa FE/mock, đổi Dashboard/Analytics/RAG core.

---

## 2. Files/rules/reports đã đọc

| File | Mục đích đọc | Kết luận chính |
|------|----------------|----------------|
| `.cursor/rules/00-core-working-rule.mdc` | Luật nền | Minimal diff, đọc source trước |
| `.cursor/rules/10-backend-rag-rule.mdc` | Backend | compile/test, không refactor RAG |
| `.cursor/rules/20-frontend-widget-rule.mdc` | FE | contract từ FE |
| `.cursor/rules/30-deploy-env-ops-rule.mdc` | Env | test phụ thuộc env |
| `.cursor/rules/40-db-vector-rule.mdc` | DB | ddl-auto dev |
| `.cursor/rules/90-report-verification-rule.mdc` | Report | (prompt 07A dùng template riêng trong `reports/`) |
| `reports/CURSOR_REPORT_10_SETTINGS_PAGE.md` | Settings UI | FE dùng `name`, notifications, `plainTextKey` |
| `reports/CURSOR_REPORT_06C_BACKEND_CHAT_FEEDBACK...md` | Chuẩn report gần | Pattern controller + message JSON |
| `reports/CURSOR_REPORT_11_INTEGRATION_QA_CHECKLIST_AUDIT.md` | QA | (tham chiếu checklist tích hợp) |
| `Frontend/src/api/settingsApi.js` | Contract | paths + `key`/`plainTextKey` + `success` |
| `Frontend/src/mocks/settingsMock.js` | Shape mock | `name` trong profile mock export (legacy `profile.name`) |
| `Frontend/.../ProfileSettingsSection.jsx` | Payload thật | GET/PUT: `name`, `email`, `language`, `notifications` |
| `Frontend/.../ApiKeysSection.jsx` | Keys | list array; create `plainTextKey` |
| `Frontend/.../ApiKeyRow.jsx` | Reveal | List không có raw → banner “unavailable” |
| `Frontend/.../DangerZoneSection.jsx` | Danger zone | Không gọi API — TBD |
| `Backend/.../SecurityConfig.java` | Security | `/api/settings` không cần chỉnh |
| `Backend/.../WidgetAuthFilter.java` | Filter | Chỉ `/api/chat` + public chat |
| `Backend/.../domain/widget/WidgetConfig.java` | Widget key | `apiKey` UUID widget — **không** map Settings keys |
| `Backend/.../dto/SimpleSuccessResponse.java` | DELETE | reuse `success` boolean |

---

## 3. Current settings/auth/api-key model analysis

| Câu hỏi | Kết luận |
|---------|-----------|
| Backend có User/Profile entity? | **Không** có user/auth. Thêm **`SettingsProfile`** singleton (`id` cố định). |
| Backend có API key entity cho Settings? | **Không** trước đây. Thêm **`SettingsApiKey`** (metadata + hash). |
| Widget `apiKey` liên quan Settings? | **Không.** Widget dùng UUID trong `widget_configs`; Settings keys là chuỗi `sk_live_...` riêng. |
| Auth/JWT thật? | **Chưa** — `SecurityConfig` vẫn `anyRequest().permitAll()`. |
| Entity/table tối thiểu thêm? | `settings_profiles`, `settings_api_keys`. |

---

## 4. FE contract mapping after implementation

| FE function | Endpoint | Request expected | Response expected | Backend status | Notes |
|-------------|----------|------------------|-------------------|----------------|-------|
| `getProfile` | GET `/api/settings/profile` | — | `name`, `email`, `language`, `notifications` | **OK** | `name` map từ DB `fullName` |
| `updateProfile` | PUT `/api/settings/profile` | `name`, `email`, `language`, `notifications` | same shape | **OK** | Partial merge notifications |
| `getApiKeys` | GET `/api/settings/api-keys` | — | `Array` of keys | **OK** | UUID string `id` |
| `generateApiKey` | POST `/api/settings/api-keys` | `{ name? }` | `{ key, plainTextKey }` | **OK** | HTTP **201** |
| `deleteApiKey` | DELETE `/api/settings/api-keys/:id` | — | `{ success: true }` | **OK** | Soft delete `deletedAt` |

---

## 5. Files changed

| File | Change | Reason | Risk |
|------|--------|--------|------|
| `domain/settings/SettingsProfile.java` | New entity | Singleton profile | Low — một row |
| `domain/settings/SettingsApiKey.java` | New entity | Lưu hash + masked | Low |
| `domain/settings/SettingsProfileRepository.java` | New | JPA | Low |
| `domain/settings/SettingsApiKeyRepository.java` | New | Query non-deleted | Low |
| `dto/Settings*.java` (6 files) | New DTOs | Không expose entity | Low |
| `service/SettingsService.java` | New | Business logic + SHA-256 | Low — chưa verify key ở middleware |
| `api/SettingsController.java` | New | Thin REST + lỗi JSON | Low |

---

## 6. Data model / DB impact

- **`settings_profiles`**  
  - `id` (UUID, PK) — singleton `00000000-0000-0000-0000-000000000001`  
  - `full_name`, `email`, `language`  
  - `notify_embedding_failed`, `notify_daily_summary`, `notify_new_feedback` (BOOLEAN)  
  - `created_at`, `updated_at`

- **`settings_api_keys`**  
  - `id` (UUID), `name`, `key_hash` (64 hex), `key_prefix`, `key_suffix`, `masked_key`, `status`  
  - `created_at`, `last_used_at` (nullable), `deleted_at` (nullable, soft delete)

- **Plain key:** **Không** lưu DB; chỉ trả trong `plainTextKey` lúc POST create.

- **Hash:** SHA-256 hex của toàn bộ plain key (không expose trong API).

- **Migration:** Không file SQL riêng; **`spring.jpa.hibernate.ddl-auto: update`** (profile `dev`) tạo/cập nhật schema khi app chạy.

---

## 7. Details per endpoint

### GET `/api/settings/profile`
- **Body:** —  
- **Response:** `SettingsProfileResponse`  
- **Service:** `SettingsService.getProfile` → `ensureProfile()` (insert default nếu chưa có)  
- **Validation:** —  
- **Errors:** —  

### PUT `/api/settings/profile`
- **Body:** `SettingsProfileUpdateRequest` (`name`, `email`, `language`, `notifications` — optional từng phần)  
- **Response:** profile đã cập nhật  
- **Validation:**  
  - `language` nếu có: chỉ `vi` / `en`  
  - `name` nếu có: non-blank  
  - `email` nếu có: non-blank + regex cơ bản  
  - `notifications`: merge từng boolean non-null  
- **Errors:** `400` + `{ "message": "Invalid email" }` hoặc `{ "message": "language must be vi or en" }` (và message khác cho name rỗng, v.v.)

### GET `/api/settings/api-keys`
- **Response:** `List<SettingsApiKeyResponse>` (newest first)  
- **Repository:** `findByDeletedAtIsNullOrderByCreatedAtDesc`  

### POST `/api/settings/api-keys`
- **Body:** `SettingsApiKeyCreateRequest` (`name` optional)  
- **Response:** `SettingsApiKeyCreateResponse` — `key` + `plainTextKey`  
- **Status:** **201 Created**  
- **Generate:** `sk_live_` + 24 ký tự `[a-z0-9]` ngẫu nhiên bảo mật  

### DELETE `/api/settings/api-keys/{id}`
- **Path:** UUID  
- **Response:** `SimpleSuccessResponse` `{ "success": true }`  
- **404:** `{ "message": "API key not found" }`  
- **400 id sai:** `{ "message": "Invalid API key id" }`  

---

## 8. API key security behavior

| Chủ đề | Chi tiết |
|--------|-----------|
| Plain key khi tạo | Trả đúng một lần trong field **`plainTextKey`** (đúng `settingsApi.js`). |
| Lưu plain trong DB? | **Không.** |
| Masked format | `sk_live_****` + 4 ký tự cuối của phần random (giống mock). |
| Reveal trên FE | List không có plain → `ApiKeyRow` hiển thị cảnh báo raw unavailable — đúng thiết kế “shown once”. |
| Delete | Soft delete (`deleted_at`); GET list không trả row đã xóa. |
| Expose `keyHash`? | **Không** trong DTO. |

---

## 9. Validation results

| Command | Result | Notes |
|---------|--------|-------|
| `cd Backend && ./mvnw -DskipTests compile` | **PASS** | Build SUCCESS |
| `cd Backend && ./mvnw test` | **FAIL** | `PlaceholderResolutionException: Could not resolve placeholder 'GROQ_API_KEY'` khi load context — **không liên quan** Settings; thiếu env Groq trong môi trường test. |
| `docker compose config` | NOT RUN | Ngoài scope bắt buộc prompt 07A |

---

## 10. Manual/API test plan and results

| # | Endpoint / bước | Curl / steps | Result | Notes |
|---|------------------|--------------|--------|-------|
| 1 | GET profile | — | **NOT RUN** | Không xác nhận được instance Spring của repo trên máy agent. `Invoke-WebRequest http://localhost:8080/api/settings/profile` trả **404** — có thể là service khác trên 8080 hoặc bản cũ không có route. |
| 2 | PUT profile hợp lệ | — | NOT RUN | Cần backend build mới + DB dev. |
| 3 | PUT language `fr` | — | NOT RUN | Kỳ vọng 400 + message language. |
| 4 | GET keys rỗng | — | NOT RUN | |
| 5 | POST key | — | NOT RUN | |
| 6 | GET keys sau create | — | NOT RUN | |
| 7 | DELETE key | — | NOT RUN | |
| 8 | DELETE UUID không tồn tại | — | NOT RUN | Kỳ vọng 404. |
| 9 | DELETE id không phải UUID | — | NOT RUN | Kỳ vọng 400. |

**Khuyến nghị verify local:** chạy Backend (profile `dev`, MySQL), rồi:

```bash
curl -s http://localhost:8080/api/settings/profile
```

---

## 11. Known limitations / gaps

- **Không có xác thực:** Mọi client có thể gọi Settings API nếu tiếp cận được URL (đồng nhất phần còn lại của project tuần 1).  
- **Singleton profile:** Một tenant duy nhất; không multi-user.  
- **`lastUsedAt`:** Luôn null cho đến khi có prompt sau định nghĩa “usage” của Settings API key.  
- **Team / billing / notifications gửi thật:** Không trong scope.

**Danger Zone / Delete account:** Không implement endpoint — FE chỉ toast “not implemented” (`DangerZoneSection.jsx`); checklist TBD — **đúng yêu cầu prompt.**

---

## 12. Recommended next prompt

- **07B (gợi ý):** Kết nối runtime smoke đầy đủ (curl) sau khi bật MySQL + env tối thiểu; hoặc thêm `@SpringBootTest` slice / `@MockBean` cho `GroqConfig` để `mvn test` PASS trên CI không cần GROQ.  
- **Tương lai:** Middleware xác thực request bằng Settings API key (verify SHA-256) + cập nhật `lastUsedAt` — **ngoài** prompt 07A.

---

## Appendix — Diff summary (conceptual)

### `SettingsController.java` (new)

- `@RequestMapping("/api/settings")`  
- GET/PUT profile, GET/POST api-keys, DELETE `api-keys/{id}`  
- Map `IllegalArgumentException` → JSON `message` cho email/language  

### `SettingsService.java` (new)

- Default profile từ `@Value` `app.settings.profile.default-name|default-email` (default literal trong annotation).  
- `createApiKey`: SHA-256 hex → `keyHash`, không lưu plain.  

### Entity `SettingsProfile` / `SettingsApiKey`

- Bảng như mục 6; soft delete cho API key.
