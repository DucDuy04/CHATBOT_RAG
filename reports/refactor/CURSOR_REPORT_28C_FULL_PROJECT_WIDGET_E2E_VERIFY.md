# CURSOR REPORT 28C — Full Project + Widget E2E Verification

## 1. Mức độ hiểu task

- **Hiểu task:** 98%
- **Chắc chắn:** Verification-only sau 27A–28B; không đổi RAG/feedback/mock; bắt buộc widget E2E; báo cáo PASS/FAIL trung thực.
- **Giả định:** MySQL Docker volume đã được user cleanup — thực tế kiểm tra thấy bảng/cột vẫn còn nhưng code không còn map.
- **Thiếu dữ liệu:** Không có screenshot UI; dùng Playwright headless + log/API làm evidence.

## 2. Tóm tắt yêu cầu

Xác minh toàn project: build/test, Docker, DB schema, Qdrant parity, chat API, admin UI, widget user flow, không còn mock/feedback/satisfaction trong active code.

## 3. Hiện trạng trước khi sửa

Working tree dirty với 28A/28B (xóa feedback stack, mock infra, satisfaction fields). Containers Docker đã stopped.

## 4. Nguyên nhân gốc xác nhận từ source

Không sửa bug — task verification. Ghi nhận: DB volume vẫn chứa `chat_feedbacks` và `notify_new_feedback` dù entity/DTO đã bỏ field; backend start OK vì JPA không map cột đó nữa.

## 5. Chiến lược sửa đã chọn

Chỉ chạy lệnh verify + thêm script Playwright tạm trong `docs/eval/scripts/`. Không sửa production code.

## 6. Danh sách file đã đọc

| Path | Mục đích | Kết luận |
|------|----------|----------|
| `ChatController.java` | Chat API contract | POST `/api/chat`, `/stream`; no feedback |
| `PublicChatController.java` | Public widget API | `/api/public/chat` |
| `WidgetAuthFilter.java` | Headers | `X-Widget-Key` vs `x-api-key` |
| `SettingsProfile.java` | DB mapping | No `notify_new_feedback` |
| `WidgetChatPage.jsx` | Widget chat UI | Uses `/api/chat/stream` |
| `widget/widget.js` | Embed bubble | iframe → `/widget` |
| `public-widget-test.html` | Local embed test | Query `widgetKey` |
| `vite.config.js` | Dev proxy | `/api` → :8080 |
| `package.json` | Scripts | `build:widget` exists |

## 7. Danh sách file đã sửa

| Path | Sửa để làm gì | Layer |
|------|---------------|-------|
| `docs/eval/scripts/widget_e2e_28c.mjs` | Playwright widget E2E helper | docs/test |
| `docs/eval/results/FULL_PROJECT_WIDGET_E2E_VERIFY_28C_20260530.md` | Eval report | docs |
| `reports/refactor/CURSOR_REPORT_28C_FULL_PROJECT_WIDGET_E2E_VERIFY.md` | Cursor report | docs |

## 8. Diff thay đổi của từng file

### `docs/eval/scripts/widget_e2e_28c.mjs` (new)

```diff
+ Playwright script: /widget exact + OOS via /api/chat/stream
```

### Reports (new)

Full verification matrices and evidence — no application code diff.

## 9. Ảnh hưởng sau sửa

- **Behavior:** Không đổi production.
- **Verification:** Có script tái chạy widget E2E khi backend+`npm run dev` active.

## 10. Edge cases đã xem xét

- Analytics `/summary` cần `from`/`to` → 400 nếu thiếu (đã gọi đúng params).
- `VITE_API_URL` empty → Vite proxy `/api` cho widget dev.
- DB orphan table không gây startup fail khi entity bỏ map.
- Console encoding CP1252 trên PowerShell khi in tiếng Việt (không ảnh hưởng HTTP body).

## 11. Kết quả kiểm tra

| Command | Kết quả | Ghi chú |
|---------|---------|---------|
| `docker compose config -q` | **PASS** | |
| `Backend mvnw clean test` | **PASS** | 120 tests |
| `Frontend npm run build` | **PASS** | |
| `Frontend npm run lint` | **PASS** | |
| `Frontend npm run build:widget` | **PASS** | |
| Docker mysql/qdrant/backend up | **PASS** | |
| MySQL no chat_feedbacks / notify column | **PARTIAL** | Vẫn tồn tại physically; runtime OK |
| Qdrant parity | **PASS** | 9888 points |
| Chat API P1/OOS/feedback | **PASS** | |
| Admin Playwright smoke | **PASS** | |
| Widget Playwright E2E | **PASS** | `widget_e2e_28c.mjs` |

## 12. Rủi ro còn lại

- Production DB chưa DROP orphan table/column → disk/audit noise; có thể gây nhầm lẫn ops.
- Playwright chỉ verify `/widget` route trực tiếp (iframe embed dùng cùng `WidgetChatPage`).

## 13. Đề xuất tiếp theo

1. Chạy SQL drop trên mọi environment (28B doc).
2. Commit 28A/28B + report 28C.
3. Optional: CI job chạy `widget_e2e_28c.mjs` sau integration stack up.

## Final verdict

**PASS** — core criteria met; DB physical cleanup documented as follow-up (non-blocking).
