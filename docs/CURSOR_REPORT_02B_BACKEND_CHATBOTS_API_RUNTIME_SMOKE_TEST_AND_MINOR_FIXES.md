# Cursor Report 02B - BACKEND_CHATBOTS_API_RUNTIME_SMOKE_TEST_AND_MINOR_FIXES

## 1. Mức độ hiểu task

- **Task là gì?** Khởi động backend + dependency đủ để chạy curl smoke test đầy đủ nhóm `/api/chatbots` và `/embed-config`; ghi nhận PASS/FAIL; nếu có bug nhỏ trong scope thì sửa và retest.
- **Hiểu task:** 95%
- **Phần chắc chắn:**
  - Contract curl trong prompt khớp `Frontend/src/api/chatbotsApi.js`.
  - Profile mặc định `application.yml` → `spring.profiles.active: dev`; DB `localhost:3306`, user/pass `root/root`, DB `ragchatbot`; backend port **8080** (`application-dev.yml`).
  - Docker Compose (`docker-compose.yml`): services `mysql`, `qdrant`, map MySQL `3306`, Qdrant `6333`/`6334`; backend container dùng profile `docker`.
  - Smoke chatbots **không** cần Groq/Nomic cho logic CRUD, nhưng app Boot **vẫn tạo bean** `GroqConfig` (`@Value groq.api-key`, `nomic.api-key`) → cần env `GROQ_API_KEY`, `NOMIC_API_KEY` không rỗng khi chạy server (nếu không có placeholder trong config).
- **Phần còn giả định:**
  - Khi Docker/MySQL sẵn sàng, `ddl-auto: update` đủ để bảng `widget_configs` có cho smoke test.
- **Phạm vi không làm:** Documents/playground/public chat/dashboard/settings/analytics; không đổi schema; không sửa FE/mock; không refactor lớn.

## 2. Files/rules/reports đã đọc

| File | Mục đích đọc | Kết luận chính |
|------|----------------|----------------|
| `.cursor/rules/00-core-working-rule.mdc` | Luật nền | Minimal scope |
| `.cursor/rules/30-deploy-env-ops-rule.mdc` | Env/deploy | Đọc compose + application |
| `reports/CURSOR_REPORT_02_BACKEND_CHATBOTS_CRUD_AND_EMBED_CONFIG_CANONICAL_API.md` | Baseline API | Endpoints đã implement |
| `Frontend/src/api/chatbotsApi.js` | Contract | Paths/query/body |
| `Backend/.../application.yml` | Profile | `active: dev` |
| `Backend/.../application-dev.yml` | Local runtime | MySQL 3306, port 8080, Qdrant localhost |
| `Backend/.../application-docker.yml` | Container | Host `mysql`/`qdrant` |
| `docker-compose.yml` | Services | `mysql`, `qdrant`, `backend`, `frontend` |
| `Backend/.../ChatbotController.java` | Smoke target | UUID 404/400, JSON error |
| `Backend/.../WidgetService.java` | Logic | Mapping, embed validation |

## 3. Runtime environment setup

| Item | Detail |
|------|--------|
| **Backend profile used (planned)** | `dev` (default từ `application.yml`) |
| **MySQL status** | **Không chạy** — `Test-NetConnection localhost:3306` → TcpTestSucceeded **False** |
| **Qdrant status** | **Không khẳng định được** — Docker không start được (xem dưới); cổng 6333 không được kiểm tra riêng sau khi compose fail |
| **Docker Compose attempt** | `docker compose up -d mysql qdrant` → **FAIL**: `open //./pipe/dockerDesktopLinuxEngine: The system cannot find the file specified` → Docker Desktop/engine không chạy hoặc không cài |
| **Backend server status** | **Không start** — không có DB + không có container; port **8080** không listen (`TcpTestSucceeded False`) |
| **Base URL tested** | — **Không áp dụng** (không có server) |

**Env bắt buộc khi chạy Spring Boot (ghi nhận từ source):**

- `GROQ_API_KEY`, `NOMIC_API_KEY` — inject vào `GroqConfig` (không có default trong YAML).
- Chatbots CRUD không gọi LLM/embed runtime nhưng context Boot vẫn khởi tạo các bean đó.

## 4. Chatbots API smoke test results

| Test | Endpoint | Expected | Actual | Status | Notes |
|------|----------|----------|--------|--------|-------|
| 1 | POST `/api/chatbots` | 2xx + chatbot shape + optional `apiKey` | Không gọi được | **BLOCKED** | Không có backend |
| 2 | GET `/api/chatbots` | `items`, pagination | Không gọi được | **BLOCKED** | |
| 3 | GET `/api/chatbots?search=...` | Filter/pagination | Không gọi được | **BLOCKED** | |
| 4 | GET `/api/chatbots/{id}` | Chatbot object | Không gọi được | **BLOCKED** | |
| 5 | PUT `/api/chatbots/{id}` | Updated object | Không gọi được | **BLOCKED** | |
| 6 | GET `/api/chatbots/{id}/embed-config` | Embed shape | Không gọi được | **BLOCKED** | |
| 7 | PUT `/api/chatbots/{id}/embed-config` | Updated embed | Không gọi được | **BLOCKED** | |
| 8 | GET `/api/chatbots/not-a-uuid` | 400 + `message`/`error` | Không gọi được | **BLOCKED** | |
| 9 | PUT embed invalid body | 400 | Không gọi được | **BLOCKED** | |
| 10 | DELETE + GET 404 + list absent | success + 404 + không list | Không gọi được | **BLOCKED** | |

**Nguyên nhân BLOCKED:** Không thể bring-up MySQL (Docker không chạy; không có listener 3306). Không start được Spring Boot để bind `:8080`.

## 5. Bugs found and fixed

**No source changes.** Không có bằng chứng runtime FAIL trên server thực; không chỉnh `ChatbotController` / `WidgetService` / DTO / repository trong prompt này.

## 6. Files changed

| File | Change | Reason | Risk |
|------|--------|--------|------|
| — | — | — | — |

## 7. Validation results

| Command | Result | Notes |
|---------|--------|-------|
| `cd Backend && ./mvnw -DskipTests compile` | **PASS** | BUILD SUCCESS |
| `cd Backend && ./mvnw test` | **FAIL** | `Communications link failure` — MySQL không có tại `localhost:3306` (môi trường) |

## 8. Known limitations / gaps

- **Smoke test chưa chứng minh trên HTTP:** Cần máy có Docker Desktop (hoặc MySQL local) + env `GROQ_API_KEY`/`NOMIC_API_KEY` + Qdrant reachable (ứng dụng có `ApplicationRunner` tạo collection Qdrant khi start).
- **Curl checklist trong prompt:** Nên chạy lại sau khi: `docker compose up -d mysql qdrant`, export API keys, `.\mvnw.cmd spring-boot:run` (profile `dev`).

## 9. Recommended next prompt

- **02C hoặc ops:** “Start Docker Desktop + `docker compose up -d mysql qdrant` + document env keys + rerun curl script 1–10” hoặc dùng CI job smoke test với Testcontainers/MySQL service.

---

**Hướng dẫn retest nhanh khi môi trường sẵn sàng (PowerShell):**

```powershell
$env:GROQ_API_KEY="dummy-key-for-startup"
$env:NOMIC_API_KEY="dummy-key-for-startup"
cd Backend
.\mvnw.cmd spring-boot:run
# Terminal khác: curl theo mục B trong prompt 02B
```

*(Giá trị key có thể cần là key thật nếu library validate — điều chỉnh theo lỗi startup thực tế.)*
