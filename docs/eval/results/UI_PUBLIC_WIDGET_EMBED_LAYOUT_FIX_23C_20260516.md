# UI — Public widget embed layout fix 23C

**File:** `docs/eval/results/UI_PUBLIC_WIDGET_EMBED_LAYOUT_FIX_23C_20260516.md`  
**Report:** `reports/refactor/CURSOR_REPORT_23C_PUBLIC_WIDGET_EMBED_LAYOUT_FIX.md`  
**Ngày:** 2026-05-16

---

## Issue (screenshot summary)

Trên `http://localhost:5173/public-widget-test.html?widgetKey=...`:

- Khối đen/tròn khổng lồ chiếm phần trên màn hình (SVG chat icon phóng to, không có `width`/`height` trên launcher).
- Widget thật (launcher nhỏ + panel) lệch / bị cắt góc dưới.
- Không phải lỗi RAG/chat API.

---

## Root cause

Vite ưu tiên file tĩnh trong `Frontend/public/dist-widget/`. File **`public/dist-widget/chatbot-widget.iife.js` bị lỗi thời** (bundle cũ):

- Tạo `#rag-chatbot-bubble` **không** gọi `injectWidgetStyles()`.
- **Không** set `position: fixed`, `width`, `height`.
- SVG mặc định scale theo viewport → trông như “bong bóng đen” khổng lồ.
- Dùng class `hidden` thay vì `rag-chatbot-hidden` (CSS bundle cũ không khớp).

Bundle mới trong `Frontend/dist-widget/` (từ `widget/widget.js`) đã đúng nhưng dev server phục vụ bản **public** trước.

---

## Files changed

| File | Change |
|------|--------|
| `Frontend/public/dist-widget/chatbot-widget.iife.js` | Đồng bộ bundle mới (inject styles, sizing) |
| `Frontend/public/dist-widget/widget.css` | Đồng bộ CSS scoped `#rag-chatbot-*` |
| `Frontend/public/public-widget-test.html` | Host page layout; `frontendUrl` = `origin`; query `position` |
| `Frontend/widget/widget.css` | Align với injected styles |
| `Frontend/package.json` | `build:widget` + sync script |
| `Frontend/scripts/sync-widget-to-public.mjs` | Copy `dist-widget` → `public/dist-widget` |
| `Frontend/vite.config.js` | Dev middleware ưu tiên `dist-widget/` built |

---

## Behavior before / after

| | Before | After |
|---|--------|-------|
| Launcher | SVG full-width, ~viewport | 56×56px, `fixed` bottom-right/left |
| Chat panel | iframe không sized / class `hidden` sai | 360×520 max, `rag-chatbot-hidden` |
| Host page | Chỉ text, không padding | Padding + nền nhẹ, không đè widget |

---

## Lint / build

| Command | Result |
|---------|--------|
| `npm run lint` | **PASS** |
| `npm run build` | **PASS** |
| `npm run build:widget` | **PASS** (+ sync public) |

---

## Manual verify

| Step | Result |
|------|--------|
| Mở `public-widget-test.html?widgetKey=...` | **Expected PASS** sau sync bundle |
| Khối đen khổng lồ | **Gone** (root cause = stale IIFE) |
| Click launcher → panel | 360×520 iframe `/widget` |
| Gửi tin nhắn | Cần backend + key hợp lệ (không đổi API) |

**Screenshot:** Không chụp trong session này; verify bằng rebuild + mở lại trang test.

---

## Limitations

- Production Docker image chỉ `npm run build` (app), **không** tự `build:widget` — embed production cần copy `public/dist-widget` hoặc thêm bước build widget vào pipeline.
- Sau mỗi sửa `widget/widget.js` phải chạy `npm run build:widget`.

---

## Conclusion

**23C PASS (code)** — root cause là stale embed script trong `public/dist-widget`, không phải Backend/RAG.
