# UI — Public widget embed layout verify 23C2

**File:** `docs/eval/results/UI_PUBLIC_WIDGET_EMBED_LAYOUT_VERIFY_23C2_20260516.md`  
**Report:** `reports/refactor/CURSOR_REPORT_23C2_PUBLIC_WIDGET_EMBED_LAYOUT_VERIFY.md`  
**Ngày:** 2026-05-16  
**Loại task:** VERIFY ONLY (browser/manual)

---

## Environment

| Item | Value |
|------|--------|
| OS | Windows 10 |
| Backend | `http://localhost:8080` (Docker, UP) |
| Frontend :5173 | Docker nginx (`chatbot-frontend`), serves **legacy** widget IIFE (~1194 B) |
| Frontend :5174 | Vite dev (`npm run dev`), port fallback vì 5173 bận — serves **23C** widget IIFE (~3271 B) |
| Browser automation | Playwright Chromium 1.50 (headless), script `_run_23c2_widget_browser_verify.mjs` |
| Chatbot | `23B3 PDF Sparse Continuation Runtime` (`a78062a8-02b6-4973-b5e1-6e431c22c1a6`) |
| Widget key (masked) | `7218...3b04` |
| Document | PDF phúc khảo đã INDEXED trên chatbot trên |

---

## URL test

| URL | Mục đích |
|-----|----------|
| `http://localhost:5173/public-widget-test.html?widgetKey=7218...3b04` | URL user yêu cầu (Docker) |
| `http://localhost:5174/public-widget-test.html?widgetKey=7218...3b04` | Vite dev + bundle 23C đã sync |

---

## Widget key (masked)

`7218...3b04` (từ `GET /api/chatbots/{id}/embed-config`)

---

## Browser result — layout (khối đen)

| Port | Bundle | Bubble size | Khối đen khổng lồ | Kết luận layout |
|------|--------|-------------|-------------------|-----------------|
| **5173** | Legacy (no `rag-chatbot-hidden`, no 56px CSS) | **1264×1268** px, góc trên-trái | **Còn** (`hugeBlackBlock: true`) | **FAIL** |
| **5174** | 23C (`injectWidgetStyles`, `rag-chatbot-hidden`) | **56×56** px, bottom-right | **Không** | **PASS** |

**Root cause 5173 FAIL (runtime):** container Docker `chatbot-frontend` chưa rebuild sau 23C — vẫn phục vụ IIFE cũ trong image, không phải regression code trong repo working tree.

---

## Launcher result

| Check | :5173 | :5174 |
|-------|-------|-------|
| ~56×56 | No (1264×1268) | **Yes (56×56)** |
| Góc dưới phải | No | **Yes** (x=1200, y=720 @ 1280×800) |
| Chiếm toàn màn hình | **Yes** (SVG phóng to) | **No** |

---

## Panel result

| Check | :5173 | :5174 |
|-------|-------|-------|
| iframe visible sau click | Yes | Yes |
| ~360×520 | **No** (304×154) | **Yes (360×520)** |
| Input placeholder hiện | Yes | Yes |
| Nút **Gửi** hiện | Yes | Yes |
| Input/send crop | Không crop (nhưng panel quá thấp do layout hỏng) | **Không crop** |

---

## Chat send result

Câu hỏi: `Tài liệu này nói về hệ thống gì?`

| Channel | Kết quả |
|---------|---------|
| **Browser :5173** | UI gửi được text; bot trả *"Thiếu widget key"* — IIFE cũ không truyền `widgetKey` vào iframe đúng cách |
| **Browser :5174** | UI gửi được text; **CORS** chặn `POST http://localhost:8080/api/chat/stream` từ origin `5174` (`allowedOrigins` chỉ có `http://localhost:5173`); UI hiện *"Failed to fetch"* |
| **API trực tiếp** (`X-Widget-Key`, non-stream) | **PASS** — trả lời về *Hệ thống quản lý yêu cầu phúc khảo* (encoding console PowerShell lỗi font, nội dung đúng UTF-8 trên API) |

---

## Console / network result

### :5173

- **Console errors:** không có lỗi JS nghiêm trọng (chỉ warning Tailwind CDN).
- **Network:** `GET /dist-widget/chatbot-widget.iife.js` → 200, **legacy bundle**.

### :5174

- **Console errors:** CORS preflight + `Failed to fetch` khi gửi chat (không liên quan layout 23C).
- **Network:** `GET /dist-widget/chatbot-widget.iife.js` → 200, **bundle 23C** (3271 B).

---

## Screenshots

| File | Mô tả |
|------|--------|
| `docs/eval/results/screenshots_23c2/initial_port5173.png` | :5173 — khối đen/SVG phóng to |
| `docs/eval/results/screenshots_23c2/panel_port5173.png` | :5173 — panel méo nhỏ |
| `docs/eval/results/screenshots_23c2/chat_port5173.png` | :5173 — thiếu widget key |
| `docs/eval/results/screenshots_23c2/initial_vite5174.png` | :5174 — launcher 56px đúng góc |
| `docs/eval/results/screenshots_23c2/panel_vite5174.png` | :5174 — panel 360×520 |
| `docs/eval/results/screenshots_23c2/chat_vite5174.png` | :5174 — Failed to fetch (CORS) |

Machine JSON: `docs/eval/results/_run_23c2_results_5173.json`, `_run_23c2_results_5174.json`

---

## Code changes

**Không** — verify only.

---

## Conclusion

**PARTIAL**

| Tiêu chí | Verdict |
|----------|---------|
| Layout fix 23C (bundle mới) | **PASS** trên Vite :5174 |
| URL user :5173 (Docker chưa rebuild) | **FAIL** — vẫn khối đen + bundle stale |
| Chat E2E browser | **PARTIAL** — API OK; browser :5173 key broken (stale IIFE); :5174 CORS |

**PUBLIC_WIDGET_LAYOUT_PASS** chỉ đạt khi frontend phục vụ bundle 23C (rebuild Docker image hoặc dùng Vite dev trên port có `public/dist-widget` mới và origin khớp `allowedOrigins`).

---

## Next steps

1. `docker compose build frontend && docker compose up -d frontend` (hoặc tương đương) để :5173 phục vụ IIFE 23C.
2. Verify lại `http://localhost:5173/public-widget-test.html?widgetKey=...` sau rebuild.
3. Khi dev trên :5174, thêm `http://localhost:5174` vào `allowedOrigins` embed-config **hoặc** test chat trên :5173 sau rebuild.
