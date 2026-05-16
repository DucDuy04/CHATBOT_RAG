# CURSOR_REPORT_23C2 — Public widget embed layout verify

## 1. Mức độ hiểu task

- **~97%** — chỉ browser/manual verify fix 23C, không sửa code trừ blocker.
- **Chắc chắn:** tiêu chí launcher 56px, panel 360×520, không khối đen, gửi chat, console/network.
- **Giả định:** user mở `:5173` trong khi Docker chiếm port; Vite dev chạy `:5174`.
- **Thiếu:** user chưa rebuild frontend Docker sau 23C.

## 2. Tóm tắt yêu cầu

Xác nhận trên browser thật rằng `public-widget-test.html` không còn khối đen, launcher/panel đúng, gửi chat được, không lỗi console nghiêm trọng; ghi report eval + refactor; **không** sửa Java/Backend/RAG.

## 3. Phạm vi đã làm

- Kiểm tra backend/frontend HTTP.
- So sánh bundle `/dist-widget/chatbot-widget.iife.js` trên :5173 vs :5174.
- Playwright headless: layout, click launcher, panel, gửi chat, console/network.
- API smoke cùng `widgetKey` + câu hỏi tiếng Việt.
- Screenshot + JSON kết quả trong `docs/eval/results/`.
- Tạo 2 file report (eval + refactor).

## 4. Phạm vi không làm

- Không sửa Java, Backend, RAG, parser, Frontend source.
- Không rebuild Docker image (ghi nhận blocker runtime).
- Không thêm dependency vào `Frontend/package.json`.
- Không migration/backfill.

## 5. File đã đọc

| Path | Mục đích | Kết luận |
|------|----------|----------|
| `Frontend/public/public-widget-test.html` | URL harness | Load IIFE + `RagChatbotConfig` |
| `Frontend/public/dist-widget/chatbot-widget.iife.js` | Bundle trong repo | Có 56px + `rag-chatbot-hidden` |
| `reports/refactor/CURSOR_REPORT_23C_PUBLIC_WIDGET_EMBED_LAYOUT_FIX.md` | Context 23C | Root cause = stale public bundle |
| `docs/eval/results/UI_PUBLIC_WIDGET_EMBED_LAYOUT_FIX_23C_20260516.md` | Fix summary | Chưa browser verify trước 23C2 |
| `Frontend/src/pages/WidgetChatPage.jsx` | Selector chat | input placeholder, nút Gửi |
| `_run_23c2_results_5173.json` / `_run_23c2_results_5174.json` | Kết quả automation | 5173 FAIL layout; 5174 PASS layout |

## 6. Browser/manual verify result

Đã chạy Playwright (2 lần/base URL). Chi tiết: `docs/eval/results/UI_PUBLIC_WIDGET_EMBED_LAYOUT_VERIFY_23C2_20260516.md`.

## 7. Có còn khối đen không?

| Môi trường | Khối đen |
|------------|----------|
| `localhost:5173` (Docker) | **Còn** — bubble 1264×1268 px |
| `localhost:5174` (Vite + bundle 23C) | **Không** — bubble 56×56 px |

## 8. Launcher/panel/input result

- **:5174:** launcher **PASS**; panel **360×520 PASS**; input + Gửi **không crop**.
- **:5173:** launcher **FAIL**; panel **FAIL** (154px cao); input/send visible nhưng trên layout hỏng.

## 9. Chat send result

- **Browser :5173:** gửi text OK; bot **không** RAG — *Thiếu widget key* (IIFE cũ).
- **Browser :5174:** gửi text OK; **Failed to fetch** (CORS origin 5174).
- **API `POST /api/chat`:** **PASS** — trả lời đúng hệ thống phúc khảo.

## 10. Console/network result

- **:5173:** không error JS nghiêm trọng; IIFE legacy 200.
- **:5174:** CORS errors khi stream chat; IIFE 23C 200.

## 11. Có sửa code không?

**Không.**

## 12. No code diff

Không có thay đổi source/config trong repo cho task này. Chỉ thêm artifact verify:

- `docs/eval/results/UI_PUBLIC_WIDGET_EMBED_LAYOUT_VERIFY_23C2_20260516.md`
- `reports/refactor/CURSOR_REPORT_23C2_PUBLIC_WIDGET_EMBED_LAYOUT_VERIFY.md`
- `docs/eval/results/_run_23c2_widget_browser_verify.mjs` (script verify)
- `docs/eval/results/_run_23c2_results_5173.json`, `_run_23c2_results_5174.json`
- `docs/eval/results/screenshots_23c2/*.png`

## 13. Kết luận

**PUBLIC_WIDGET_LAYOUT_PASS / PARTIAL / FAIL → `PARTIAL`**

- Fix **23C code đúng** khi bundle mới được serve (**PASS** trên :5174).
- URL chuẩn user **:5173** vẫn **FAIL** do Docker image chưa chứa `public/dist-widget` mới.
- Chat browser **PARTIAL** (API OK; UI :5173 stale key; UI :5174 CORS).

## 14. Rủi ro còn lại

- Production/Docker frontend quên `build:widget` + copy `public/dist-widget` → tái hiện khối đen trên :5173.
- Dev port khác `allowedOrigins` → chat stream fail dù layout OK.
- Playwright script `botReplied` heuristic có thể true khi chỉ có error message (đã đọc excerpt thủ công).

## 15. Bước tiếp theo đúng luồng

1. Rebuild & redeploy `chatbot-frontend` image.
2. Re-run browser verify chỉ trên `http://localhost:5173/public-widget-test.html?widgetKey=...`.
3. Nếu PASS → đóng 23C2; nếu FAIL → mới xem xét sửa pipeline Docker (ngoài scope verify-only).

---

## Kết quả kiểm tra (commands)

| Command | Kết quả | Ghi chú |
|---------|---------|---------|
| `curl localhost:5173/public-widget-test.html` | PASS | 200 |
| `curl localhost:8080/api/chatbots` | PASS | 200 |
| Playwright verify :5174 | PASS layout | CORS chat |
| Playwright verify :5173 | FAIL layout | Stale bundle |
| `POST /api/chat` + widget key | PASS | RAG trả lời |
| Backend compile | NOT RUN | verify-only |
| Backend test | NOT RUN | verify-only |
| Frontend lint/build | NOT RUN | verify-only |
| Docker compose config | NOT RUN | verify-only |
