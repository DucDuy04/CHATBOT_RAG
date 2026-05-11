# Cursor Report 08B - BROWSER_MANUAL_E2E_QA_REAL_API_AND_MINOR_FIXES

## 1. Mức độ hiểu task

- **Task là gì?**  
  Chạy **manual / browser E2E QA** cho toàn FE với BE thật (`VITE_USE_MOCK_API=false`, `VITE_API_URL=http://localhost:8080`), checklist C1–C12, chỉ sửa bug nhỏ nếu phát hiện khi chạy browser.

- **Hiểu task:** 95%

- **Phần chắc chắn:**  
  - Môi trường agent **không** có trình duyệt tương tác (không mở DevTools, không click flow, không quan sát UI) như người dùng thật — theo rule prompt §B5 / §13: **không claim PASS** cho checklist UI.  
  - Đã có thể chuẩn bị **`.env.local`**, chạy **compile/test/lint/build**, và dựa trên **08A** cho HTTP contract.

- **Phần còn giả định:**  
  - Người dùng chạy `npm run dev` + mở `http://localhost:5173` local để hoàn tất checklist; BE trên `8080` với env Groq/Nomic thật khi cần upload/SSE.

- **Phạm vi không làm:**  
  - Không thêm Playwright/Cypress, không redesign UI, không JWT/feature mới, không đổi RAG/schema.

---

## 2. Files/rules/reports đã đọc

| File | Mục đích đọc | Kết luận chính |
|------|----------------|----------------|
| `.cursor/rules/*` | Luật workspace | minimal diff; report trung thực |
| `reports/CURSOR_REPORT_08A_...md` | Tiền đề | HTTP smoke + lint/build real API đã PASS; UI browser NOT RUN |
| `reports/CURSOR_REPORT_11_...md` | Checklist audit | Chuẩn layout/API đã audit trước |
| `Frontend/.env.example` | Hướng dẫn mock | Cần `VITE_USE_MOCK_API=false` explicit trong dev |
| `Frontend/src/api/apiMode.js` | Toggle | Dev → mock nếu không set env |
| `Frontend/src/api/axiosInstance.js` | baseURL | `VITE_API_URL` |
| `Frontend/.gitignore` | `*.local` | `.env.local` không commit |
| Các `reports/03B–07B` (tham chiếu prompt) | Context | Đã đọc/know từ 08A; không đổi code BE trong 08B |

---

## 3. Runtime environment

| Item | Status | Notes |
|------|--------|-------|
| Docker | **RUNNING** | `ragchatbot-mysql` healthy, `ragchatbot-qdrant` up (`docker ps`) |
| MySQL / Qdrant | **OK** | Giống 08A |
| Backend | **NOT STARTED** trong session 08B | Không bắt buộc cho report BLOCKED UI; compile/test chạy độc lập |
| Frontend `npm run dev` | **NOT RUN** | Không bật server cho fetch UI trong agent |
| **Browser / preview** | **NOT AVAILABLE** | Agent không điều khiển browser + DevTools để thực hiện C1–C12 |
| Base API URL (cấu hình) | `http://localhost:8080` | Trong `.env.local` mới tạo |
| Mock mode (cấu hình) | `false` | `VITE_USE_MOCK_API=false` trong `.env.local` |
| Env keys BE | **NOT RUN** start BE | Test Maven dùng placeholder Groq/Nomic trong shell |

---

## 4. Real API browser setup

| Hạng mục | Giá trị / ghi chú |
|-----------|-------------------|
| **File env** | Đã tạo **`Frontend/.env.local`** (gitignored bởi `Frontend/.gitignore` `*.local` và root `*.env.local`) |
| **`VITE_USE_MOCK_API`** | `false` |
| **`VITE_API_URL`** | `http://localhost:8080` |
| **Frontend URL (kỳ vọng khi user chạy dev)** | `http://localhost:5173` |
| **Backend URL** | `http://localhost:8080` |
| **Browser console** | **Không thu thập được** — không có phiên browser |
| **Network tab (mock vs real)** | **Không thu thập được** — không có phiên browser |

**Hướng dẫn cho người chạy E2E thủ công:**  
`cd Frontend && npm run dev` → mở `http://localhost:5173` → đảm bảo BE đang `spring-boot:run` profile `dev` trên 8080 → DevTools → xác nhận request tới `localhost:8080` (hoặc proxy `/api`) và không còn nhánh `USE_MOCK_API` trong API modules.

---

## 5. Browser E2E QA results

| Area | Flow | Expected | Actual | Status | Evidence / Notes |
|------|------|----------|--------|--------|---------------------|
| C1 Auth/Layout | Login → dashboard, sidebar, `/widget` | Ổn định | — | **NOT RUN** | Không browser |
| C2 Dashboard | Cards, charts, activity | Real API | — | **NOT RUN** | Không browser |
| C3 Chatbots | List, CRUD, navigate config | Real API | — | **NOT RUN** | Không browser |
| C4 Chatbot Config | Save prompt/model/status | PUT đúng | — | **NOT RUN** | Không browser |
| C5 Embed | Save embed, snippet | GET/PUT | — | **NOT RUN** | Không browser |
| C6 Documents | Upload, list, assign, delete | FormData | — | **NOT RUN** | Không browser |
| C7 Playground | SSE, sessions, export, compare | Stream | — | **NOT RUN** | Không browser |
| C8 Analytics Usage | Tabs, CSV, date range | APIs | — | **NOT RUN** | Không browser |
| C9 Analytics Sessions | Filters, drawer, pagination | APIs | — | **NOT RUN** | Không browser |
| C10 Feedback | Submit từ UI | POST feedback | — | **NOT RUN** | Tab Feedback vẫn mock data (đã biết 08A/06C); không UI submit trong agent |
| C11 Settings | Profile, keys, danger zone | APIs | — | **NOT RUN** | Không browser |
| C12 Public widget/chat | x-api-key, harness | Hoạt động | — | **NOT RUN** | Không browser |

**Bổ sung:** Kiểm thử **HTTP/API** tương đương phần lớn contract đã được **08A** ghi nhận (PASS với tham số hợp lệ); 08B **không** thay thế bước browser.

---

## 6. Bugs found and fixed

| Bug | Area | File | Fix | Retest status |
|-----|------|------|-----|----------------|
| — | — | — | **Không phát hiện bug integration từ browser** (không chạy UI) | — |

**Thiết lập môi trường (không phải bug sản phẩm):** Tạo **`Frontend/.env.local`** với real API flags để dev chạy `npm run dev` đúng chế độ prompt yêu cầu.

---

## 7. Files changed

| File | Change | Reason | Risk |
|------|--------|--------|------|
| `Frontend/.env.local` | New (gitignored) | Bật real API cho `npm run dev` theo prompt B3 | Thấp — chỉ local, không vào git nếu ignore đúng |
| `Frontend/.env.example` | +1 dòng ghi chú gitignore | Giảm nhầm commit env local | Không |

---

## 8. Validation results

| Command | Result | Notes |
|---------|--------|-------|
| `cd Backend && ./mvnw -DskipTests compile` | **PASS** | |
| `cd Backend && ./mvnw test` (shell: placeholder `GROQ_API_KEY`, `NOMIC_API_KEY`) | **PASS** | 14 tests |
| `cd Frontend && npm run lint` | **PASS** | |
| `cd Frontend && npm run build` | **PASS** | Dùng env từ `.env.local` (Vite merge) |
| `npm run build:widget` | **NOT RUN** | Không sửa widget |

---

## 9. Known limitations / gaps

- **Toàn bộ checklist C1–C12** cần **người** hoặc **công cụ E2E** (Playwright, v.v.) — ngoài khả năng agent hiện tại.  
- **SSE / upload / provider** phụ thuộc key thật và thời gian ingest — không verify trong 08B.  
- File **`.env.local`** trên máy dev có thể khác URL/port — cần chỉnh tay nếu BE không phải 8080.

---

## 10. Final decision

**Browser E2E QA BLOCKED by environment** — không có phiên browser/DevTools trong môi trường thực thi của agent; **không claim PASS** cho UI E2E.

**Đã hoàn thành phần không phụ thuộc browser:** cấu hình **`.env.local`**, cập nhật **`.env.example`**, và **validation** Maven + npm (lint/build).

---

## 11. Recommended next prompt

- **Người thực hiện:** chạy `docker compose up -d mysql qdrant`, BE `dev`, `cd Frontend && npm run dev`, đi checklist **C1–C12** trong report này, chụp/ghi console nếu lỗi.  
- **Hoặc prompt 08C:** thêm **Playwright** smoke tối thiểu (login → dashboard → một API call) nếu muốn tự động hóa một phần.
