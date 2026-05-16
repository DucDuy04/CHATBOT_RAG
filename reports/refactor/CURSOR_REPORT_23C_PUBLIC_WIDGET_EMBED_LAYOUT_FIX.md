# CURSOR_REPORT_23C — Public widget embed layout fix

## 1. Mức độ hiểu task

- **~98%** — sửa UI trang nhúng widget public, không đụng RAG/backend.
- **Chắc chắn:** triệu chứng = launcher SVG phóng to; file test + embed script.
- **Giả định:** user dùng Vite dev `:5173` (đúng URL mô tả).

## 2. Tóm tắt yêu cầu

Loại bỏ khối đen/bubble khổng lồ trên `public-widget-test.html`; launcher + iframe đúng kích thước/vị trí; input/send OK; không sửa backend.

## 3. Phạm vi đã làm

- Đồng bộ widget IIFE/CSS mới vào `public/dist-widget`.
- Host page styles + `frontendUrl` dynamic.
- `build:widget` sync script; Vite dev ưu tiên built bundle.
- Cập nhật `widget/widget.css` cho consistency.

## 4. Phạm vi không làm

Backend, parser, retrieval, PromptBuilder, ChatService, model controls, DB, Docker (trừ ghi chú pipeline), dependency mới, `.gitignore`.

## 5. File đã đọc

| Path | Kết luận |
|------|----------|
| `Frontend/public/public-widget-test.html` | Script inject `RagChatbotConfig` + load IIFE |
| `Frontend/public/dist-widget/chatbot-widget.iife.js` | **Stale** — no sizing (root cause) |
| `Frontend/dist-widget/chatbot-widget.iife.js` | **Current** — `injectWidgetStyles`, 56px bubble |
| `Frontend/widget/widget.js` | Source: bubble + iframe + injected CSS |
| `Frontend/vite.config.js` | Middleware serve `/dist-widget/` |
| `Frontend/src/pages/WidgetChatPage.jsx` | iframe content `/widget` — layout OK |
| `ChatbotEmbedPage.jsx` | Embed snippet chỉ load IIFE |

## 6. Root cause UI

**Stale embed bundle** trong `public/dist-widget/` được Vite serve trước bản build mới. Bubble `<motion>` không có CSS → SVG chat icon expand ~full viewport → user thấy “khối đen khổng lồ”.

## 7. Element/CSS gây khối đen

| Item | Value |
|------|-------|
| Element | `#rag-chatbot-bubble` > `svg` |
| Selector thiếu | `width`/`height`/`position: fixed` trên bubble (bundle cũ) |
| File | `public/dist-widget/chatbot-widget.iife.js` (legacy ~14 lines, no `injectWidgetStyles`) |

## 8. Phân tích 10 câu (tóm tắt)

1. **Render:** static HTML + `window.RagChatbotConfig` + script IIFE inject bubble + iframe.
2. **Khối đen:** `#rag-chatbot-bubble` SVG không bị constrain.
3. **Loại lỗi:** host page serve **sai file JS** (stale), không phải WidgetChatPage React.
4. **Container:** không có (bubble block-level full width).
5. **100vw/vh:** không set trực tiếp; SVG default + no fixed size ≈ full area.
6. **Global CSS host:** không gây chính; thiếu widget CSS.
7. **Scope:** bundle cũ không scope; bundle mới dùng `#rag-chatbot-*` + inject.
8. **Sửa:** sync bundle + test page host styles + build sync.
9. **Production embed:** cùng URL `/dist-widget/chatbot-widget.iife.js` — cần sync sau build.
10. **Minimal:** replace stale IIFE, không redesign.

## 9. Thiết kế sửa minimal

Option A + C: bundle mới (fixed launcher 56px, iframe min 360×520) + host page padding + `npm run build:widget` sync to `public/`.

## 10. Danh sách file đã sửa

Xem mục 5 file result doc.

## 11. Diff từng file

### `public/dist-widget/chatbot-widget.iife.js`

**Cũ:** append bubble + svg, no styles, `classList hidden`.

**Mới:** `injectWidgetStyles()`, `rag-chatbot-hidden`, widgetKey in iframe URL.

```diff
- t.id="rag-chatbot-bubble", t.innerHTML=`<svg...>`, document.body.appendChild(t)
+ injectWidgetStyles(); bubble 56px fixed; frame min(360px) x min(520px); rag-chatbot-hidden
```

### `public/public-widget-test.html`

```diff
+ <style> body padding, host-only layout </style>
- frontendUrl: "http://localhost:5173"
+ frontendUrl: window.location.origin
```

### `package.json`

```diff
- "build:widget": "vite build --config vite.widget.config.js"
+ "build:widget": "vite build --config vite.widget.config.js && node scripts/sync-widget-to-public.mjs"
```

### `scripts/sync-widget-to-public.mjs` (new)

Copy `dist-widget/*` → `public/dist-widget/`.

### `vite.config.js`

Dev `/dist-widget/` prefers `Frontend/dist-widget/` over stale `public/dist-widget/`.

### `widget/widget.css`

Align rules với injected styles; `.rag-chatbot-hidden`.

## 12. Behavior sau sửa

- Launcher 56×56, góc dưới phải/trái theo config.
- Click → iframe panel ~360×520, không full screen.
- WidgetChatPage trong iframe: `h-screen`, input/footer cố định.

## 13. Frontend lint/build

| Command | Result |
|---------|--------|
| lint | **PASS** |
| build | **PASS** |
| build:widget | **PASS** |

## 14. Manual verify

Chưa chạy browser tự động trong session; logic verify: bundle public đã khớp `dist-widget` (có `injectWidgetStyles`). User mở lại `public-widget-test.html` sau `npm run build:widget`.

## 15. Có sửa Backend/RAG không?

**Không.**

## 16. No backend/runtime diff

Không thay đổi Java, API, parser, Qdrant, Docker backend.

## 17. Rủi ro còn lại

- CI/Docker frontend build chưa gọi `build:widget` — production có thể serve stale nếu không sync.
- Host site có CSS global `svg { width: 100% }` vẫn có thể ảnh hưởng nếu không dùng IIFE mới (đã set `#rag-chatbot-bubble svg`).

## 18. Đề xuất tiếp theo

1. Thêm `npm run build:widget` vào `Frontend/Dockerfile` hoặc copy `public/dist-widget` sau main build.
2. Manual smoke: mở test page + gửi 1 câu chat.
3. Không thêm feature UI ngoài scope.

---

**Evidence:** `docs/eval/results/UI_PUBLIC_WIDGET_EMBED_LAYOUT_FIX_23C_20260516.md`
