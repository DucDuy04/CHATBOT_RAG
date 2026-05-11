# Cursor Report 07B - BACKEND_SETTINGS_RUNTIME_SMOKE_TEST_AND_MINOR_FIXES

## 1. Mức độ hiểu task

- **Task là gì?**  
  Chạy runtime smoke test thật cho Settings APIs (07A), xử lý môi trường (port 8080 / process cũ), chỉ sửa code Settings nếu test FAIL do bug; ghi nhận kết quả và validation Maven.

- **Hiểu task:** 98%

- **Phần chắc chắn:**  
  - Contract FE (`settingsApi.js`, sections) khớp với implementation 07A.  
  - Sau khi giải phóng port 8080 và start `spring-boot:run` với profile `dev` + env Groq/Nomic tối thiểu, `GET /api/settings/profile` trả **200** (không còn 404 do instance sai).

- **Phần còn giả định:**  
  - Process Java trên 8080 trước đó là build cũ/khác project (đã terminate để chạy workspace).  
  - `GROQ_API_KEY` / `NOMIC_API_KEY` dùng giá trị **placeholder không phải secret thật** chỉ để Spring context + Tomcat start (đủ cho smoke Settings).

- **Phạm vi không làm:**  
  - Không sửa source (không phát hiện bug Settings).  
  - Không thêm endpoint, JWT, middleware, FE, schema lớn.

---

## 2. Files/rules/reports đã đọc

| File | Mục đích đọc | Kết luận chính |
|------|----------------|----------------|
| `.cursor/rules/*` | Luật workspace | minimal diff, report trung thực |
| `reports/CURSOR_REPORT_07A_...md` | Baseline 07A | 404 do runtime sai; compile PASS |
| `reports/CURSOR_REPORT_10_SETTINGS_PAGE.md` | FE Settings | `name`, `plainTextKey`, Danger TBD |
| `reports/CURSOR_REPORT_11_INTEGRATION_QA_CHECKLIST_AUDIT.md` | QA | tham chiếu checklist |
| `Frontend/src/api/settingsApi.js` | Contract | `key` + `plainTextKey`; array keys |
| `Backend/.../SettingsController.java` | Mapping | `/api/settings/*` đúng |
| `Backend/.../SettingsService.java` | Validation / hash | email/language/partial merge |
| `docker-compose.yml` | Tên service | `mysql`, `qdrant` (không bắt buộc chạy backend container cho smoke local) |

---

## 3. Runtime environment

| Item | Status | Notes |
|------|--------|-------|
| Docker | **RUNNING** | `docker ps`: `ragchatbot-mysql` healthy, `ragchatbot-qdrant` up |
| MySQL | **OK** | Host `localhost:3306`, DB `ragchatbot` (application-dev.yml) |
| Qdrant | **OK** | Local 6333/6334; app log cảnh báo version client/server (không chặn smoke Settings) |
| Backend process on 8080 (trước) | **java.exe PID 23256** | Chiếm port → `spring-boot:run` workspace **FAIL** "Port 8080 was already in use" |
| Hành động | **Đã `taskkill /PID 23256 /F`** | Giải phóng 8080 để chạy đúng source workspace |
| Backend source version | **Workspace hiện tại** | `mvnw spring-boot:run` từ `CHATBOT_RAG/Backend` |
| Base URL smoke | `http://localhost:8080` | Sau khi app start: log `Started RagChatbotBeApplication` |
| Env keys (start + test) | **Đặt trong shell** | `GROQ_API_KEY`, `NOMIC_API_KEY` = placeholder (không ghi giá trị vào report) |

---

## 4. Old-runtime / wrong-instance check

- **Có process cũ trên 8080?** Có — `java.exe` PID **23256** LISTENING `0.0.0.0:8080` (không xác minh được artifact JAR cũ, nhưng **không** phải instance vừa build vì Maven báo port bận).
- **Đã stop/restart?** Đã kill 23256; start lại `./mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev` với env placeholder.
- **Sau restart `/api/settings/profile` còn 404?** **Không** — HTTP **200**, body có `name`, `email`, `language`, `notifications`.
- **Root cause 404 trước đây (07A):** Request tới **process không phải** bản Spring Boot vừa build có `SettingsController` (port bị JVM khác / build cũ chiếm), **không** phải lỗi component scan hay sai package (`KLTN.RAG_CHATBOT_BE.api.SettingsController` nằm dưới `RagChatbotBeApplication`).

---

## 5. Settings API smoke test results

| Test | Endpoint | Expected | Actual | Status | Notes |
|------|----------|----------|--------|--------|-------|
| 1 | GET `/api/settings/profile` | 200 + `name`, `email`, `language`, `notifications` + 3 booleans | Đạt | **PASS** | Singleton auto-create OK |
| 2 | PUT `/api/settings/profile` (body đầy đủ hợp lệ) + GET sau | 200, persisted | `Admin Test` / `admin@test.local` / toggles đúng; GET khớp | **PASS** | |
| 3 | PUT body `{ "language": "fr" }` | 400 + message language | 400 `{"message":"language must be vi or en"}` | **PASS** | |
| 4 | PUT body `{ "email": "not-an-email" }` | 400 Invalid email | 400 `{"message":"Invalid email"}` | **PASS** | |
| 5 | GET `/api/settings/api-keys` | 200, array, không plain/hash | `[]` hoặc array, không field lạ | **PASS** | Lúc test: count=0 |
| 6 | POST `/api/settings/api-keys` | 200/201 + `key` + `plainTextKey` + metadata | **201** + `plainTextKey`, `key.id`, `maskedKey`, `status`, `createdAt` | **PASS** | Không có `keyHash` |
| 7 | GET keys sau create | Có key mới, masked, không plain | `foundNew=True` | **PASS** | Sort newest: một phần tử đúng |
| 8 | DELETE key vừa tạo + GET | 200 `{success:true}`, list không còn | `success=true`, key biến mất | **PASS** | Soft delete |
| 9 | DELETE random UUID | 404 message | 404 `API key not found` | **PASS** | |
| 10 | DELETE `not-a-uuid` | 400 Invalid API key id | 400 đúng message | **PASS** | |
| 11 | Danger zone | Không endpoint delete-account | `grep` Backend: **không** có `delete-account` | **PASS** | FE Danger Zone vẫn TBD |

**Cleanup dữ liệu / profile:** Sau các bước trên đã **PUT** profile về mặc định: `Admin User`, `admin@example.com`, `vi`, notifications `false` cả ba — để DB sạch cho lần chạy sau.

**Server sau báo cáo:** Đã `taskkill` Java PID **15288** (instance vừa dùng cho smoke) để giải phóng port 8080.

---

## 6. Bugs found and fixed

| Bug | File | Fix | Retest |
|-----|------|-----|--------|
| — | — | **No source changes.** | N/A |

Lỗi runtime ban đầu: **port 8080 bận** — xử lý bằng **dừng process cũ** + restart backend workspace, **không** sửa code.

---

## 7. Files changed

| File | Change | Reason | Risk |
|------|--------|--------|------|
| — | — | **No source changes** | — |

---

## 8. Validation results

| Command | Result | Notes |
|---------|--------|-------|
| `cd Backend && ./mvnw -DskipTests compile` | **PASS** | BUILD SUCCESS |
| `cd Backend && ./mvnw test` (với `GROQ_API_KEY` + `NOMIC_API_KEY` set trong shell tới placeholder) | **PASS** | 14 tests, 0 failures (trước đó 07A FAIL khi **không** set env) |

---

## 9. Known limitations / gaps

- Smoke test cần **MySQL** + env Groq/Nomic **non-empty** để full context start (giống các module khác).  
- Không xác thực Settings API trong production.  
- Qdrant client/server version mismatch vẫn log WARN (ngoài scope 07B).  
- FE Danger Zone delete account vẫn **TBD**, không có backend route mới.

---

## 10. Final decision

**All Settings API smoke tests PASS. Proceed to integration QA.**

(Điều kiện: backend chạy đúng bản workspace, port 8080 trống hoặc dùng đúng instance, MySQL + env tối thiểu như trên.)

---

## 11. Recommended next prompt

- **Integration QA / 11 checklist:** chạy end-to-end FE (`USE_MOCK_API=false`) với backend + CORS + `VITE_API_URL`.  
- **Ops:** nếu thường xuyên gặp port 8080 bận, thêm script/doc “stop process trên 8080 trước khi `spring-boot:run`” hoặc profile `server.port` khác cho dev song song.
