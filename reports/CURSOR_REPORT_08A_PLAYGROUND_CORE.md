# Cursor Report 08A - Playground Core

## 1. Mức độ hiểu task

- **Task là gì?** Thay placeholder `PlaygroundPage.jsx` (7 dòng stub) thành trang Playground đầy đủ tại `/playground`. Bao gồm: chatbot selector, session list (left), chat window với SSE streaming (center), source pills, retrieval panel + latency panel + model override panel (right).
- **Hiểu task:** 100%
- **Phần chắc chắn:** Toàn bộ component structure, SSE streaming callback pattern từ `playgroundApi`, session restore logic, right-panel design, 3-column responsive layout.
- **Phần còn giả định:**
  - `latency` từ API real có thể trả object `{ total, retrieval, llm }` — mock chỉ trả số. Cả hai shape được handle.
  - `exportSession` real API trả Blob — dùng để restore messages trong mock mode, nhưng trong real mode result.messages = undefined → graceful degradation.
- **Phạm vi không làm:** Compare mode, Prompt Builder, Export log, widget runtime.

---

## 2. Checklist mapping

| Checklist item | Status | File liên quan | Ghi chú |
|---|---|---|---|
| Chatbot selector dropdown top bar | ✅ Done | `ChatbotSelector.jsx` | `getChatbots({ page: 0, size: 100 })` |
| Left panel: SessionList | ✅ Done | `SessionList.jsx` | List sessions, click restore, delete |
| Center: ChatWindow streaming | ✅ Done | `ChatWindow.jsx` + `PlaygroundPage.jsx` | User bubble right, bot bubble left |
| Token-by-token streaming | ✅ Done | `PlaygroundPage.jsx` `handleSend` | `onToken` callback append |
| SourcePills below bot message | ✅ Done | `SourcePills.jsx` + `MessageBubble.jsx` | Clickable, highlight synced với RetrievalPanel |
| Right: RetrievalPanel | ✅ Done | `RetrievalPanel.jsx` | Top-K chunks + score, highlight on click |
| Right: LatencyPanel | ✅ Done | `LatencyPanel.jsx` | retrieval/LLM/total ms, handles number & object shape |
| Right: ModelOverridePanel | ✅ Done | `ModelOverridePanel.jsx` | temperature slider, topK, maxTokens |
| Clear chat button (rightSlot) | ✅ Done | `PlaygroundPage.jsx` | Aborts stream + clears messages local |
| POST `/api/playground/chat` SSE | ✅ Done | `playgroundApi.chat(...)` | mock + real SSE |
| GET `/api/playground/sessions?chatbotId=` | ✅ Done | `playgroundApi.getSessions(chatbotId)` | Load on chatbot change |
| DELETE `/api/playground/sessions/:id` | ✅ Done | `playgroundApi.deleteSession(id)` | Refresh sessions after delete |
| Enter to send, Shift+Enter newline | ✅ Done | `ChatWindow.jsx` `handleKeyDown` | Textarea rows=2 |
| Disable send when no chatbot / streaming | ✅ Done | `ChatWindow.jsx` disabled prop | |
| Auto-scroll to bottom | ✅ Done | `ChatWindow.jsx` `useEffect([messages])` | `scrollIntoView({ behavior: "smooth" })` |
| Abort stream on unmount | ✅ Done | `PlaygroundPage.jsx` cleanup useEffect | `abortControllerRef.current.abort()` |
| Session restore messages | ✅ Done | `handleSessionSelect` via `exportSession` | Mock: works. Real: graceful degradation. |
| Markdown rendering for bot messages | ✅ Done | `MessageBubble.jsx` | `react-markdown + remark-gfm` (reuse ChatPage pattern) |
| Loading skeleton for chatbots/sessions | ✅ Done | `ChatbotSelector.jsx`, `SessionList.jsx` | SkeletonLoader |
| Toast error for API fail | ✅ Done | `PlaygroundPage.jsx` | `useToast()` |
| EmptyState cho chat/sessions | ✅ Done | `ChatWindow.jsx`, `SessionList.jsx`, `RetrievalPanel.jsx` | |
| Responsive: desktop 3-col, mobile stack | ✅ Done | `PlaygroundPage.jsx` | left: `hidden md:flex`, right: `hidden lg:flex` |

---

## 3. Các file đã tham khảo

| File | Mục đích đọc | Kết luận chính |
|---|---|---|
| `.cursor/rules/00-core-working-rule.mdc` | Rule nền bắt buộc | Source-first, minimal diff, không sửa ngoài scope |
| `.cursor/rules/20-frontend-widget-rule.mdc` | Frontend rule | Không thêm package mới, chạy lint+build sau sửa |
| `.cursor/rules/10-backend-rag-rule.mdc` | Backend rule | Không liên quan scope này |
| `.cursor/rules/30-deploy-env-ops-rule.mdc` | Ops rule | Không liên quan scope này |
| `.cursor/rules/40-db-vector-rule.mdc` | DB/vector rule | Không liên quan scope này |
| `.cursor/rules/90-report-verification-rule.mdc` | Report rule | Format report bắt buộc |
| `reports/CURSOR_REPORT_00_PROJECT_AUDIT.md` | Audit codebase gốc | Tech stack: React 19 + Vite 7 + TailwindCSS 4; không có TypeScript |
| `reports/CURSOR_REPORT_02_API_LAYER.md` | API layer design | `playgroundApi.chat` callback pattern: `onToken/onDone/onError`; `exportSession` mock trả `{ messages }` |
| `reports/CURSOR_REPORT_01C_LAYOUT_CONTEXT.md` | LayoutContext API | `setPageTitle`, `setRightSlot`, `clearRightSlot`; pattern `useEffect + return clearRightSlot` |
| `reports/CURSOR_REPORT_07_DOCUMENTS.md` | Report gần nhất | `useToast` pattern, useCallback patterns, eslint `set-state-in-effect` workaround |
| `reports/CURSOR_REPORT_04_CHATBOT_MANAGEMENT.md` | Chatbot store/API | Không cần đọc sâu — chatbotsApi đã clear từ Report 02 |
| `reports/CURSOR_REPORT_05_CHATBOT_CONFIG.md` | Config page patterns | Reference cho useCallback + useEffect patterns |
| `Frontend/src/pages/playground/PlaygroundPage.jsx` | Xem placeholder | 7 dòng stub, không có logic |
| `Frontend/src/api/playgroundApi.js` | API interface đầy đủ | 5 functions; `chat()` trả `abortController`; mock `onDone` shape: `{ messageId, answer, sources, retrieval, latency, sessionId }` |
| `Frontend/src/mocks/playgroundMock.js` | Mock data shape | `sessions[]`, `messagesBySession{}`, `mockStreamTokens[]`, `MOCK_SOURCES[]`; sources không có `score` field |
| `Frontend/src/api/chatbotsApi.js` | getChatbots API | `getChatbots({ page, size })` → `{ items, page, total, totalPages }` |
| `Frontend/src/contexts/LayoutContext.jsx` | useLayout hook | `setPageTitle`, `setRightSlot`, `clearRightSlot`, `resetLayout` |
| `Frontend/src/pages/ChatPage.jsx` | SSE streaming prototype | Pattern parse SSE `event:/data:` lines; blinking cursor khi streaming |
| `Frontend/src/components/common/EmptyState.jsx` | Props API | `{ icon, title, message, action, className }` |
| `Frontend/src/components/common/SkeletonLoader.jsx` | Props API | `{ variant: "line"|"card"|"table", count, className }` |
| `Frontend/src/components/common/Toast.jsx` | Provider structure | `ToastProvider`, `ToastRegister`, `useToast` |
| `Frontend/src/components/common/useToast.js` | Hook interface | `{ success, error, warning, info }` — `toast.error(msg)` |

---

## 4. Các file đã thay đổi

| File | Nội dung thay đổi | Lý do | Ảnh hưởng behavior cũ |
|---|---|---|---|
| `src/pages/playground/PlaygroundPage.jsx` | Thay toàn bộ placeholder 7 dòng bằng implementation | Implement feature | Không — placeholder stub |
| `src/pages/playground/components/ChatbotSelector.jsx` | File mới | Dropdown select chatbot | Không |
| `src/pages/playground/components/SessionList.jsx` | File mới | Left panel session list | Không |
| `src/pages/playground/components/ChatWindow.jsx` | File mới | Center chat panel | Không |
| `src/pages/playground/components/MessageBubble.jsx` | File mới | Individual message render | Không |
| `src/pages/playground/components/SourcePills.jsx` | File mới | Source pills below bot message | Không |
| `src/pages/playground/components/RetrievalPanel.jsx` | File mới | Right: retrieval chunks | Không |
| `src/pages/playground/components/LatencyPanel.jsx` | File mới | Right: latency stats | Không |
| `src/pages/playground/components/ModelOverridePanel.jsx` | File mới | Right: model params | Không |

**Không sửa:** `playgroundApi.js`, `playgroundMock.js`, `chatbotsApi.js`, `App.jsx`, `widget.js`, `WidgetChatPage.jsx`, common components.

---

## 5. Nội dung thay đổi chi tiết

### `src/pages/playground/PlaygroundPage.jsx`

**Đã thay thế:**
- Placeholder 7 dòng không có logic.

**Đã thêm:**
- State quản lý: `chatbots`, `sessions`, `messages`, `input`, `isStreaming`, `lastSources`, `lastLatency`, `selectedSource`, `overrideParams`, `selectedChatbotId`, `selectedSessionId`.
- `abortControllerRef` để cancel SSE stream.
- `useLayout()` → `setPageTitle("Playground")` + `setRightSlot(<Clear chat button>)` + cleanup `clearRightSlot`.
- `loadChatbots` (useCallback) → gọi `chatbotsApi.getChatbots({ page: 0, size: 100 })`.
- `loadSessions` (useCallback) → gọi `playgroundApi.getSessions(chatbotId)`.
- `handleChatbotChange` → abort stream, reset state, load sessions.
- `handleSessionSelect` → abort stream, call `playgroundApi.exportSession(id)`, restore messages gracefully.
- `handleSessionDelete` → `playgroundApi.deleteSession(id)`, reload sessions.
- `handleSend` → append user+bot messages, call `playgroundApi.chat({ onToken, onDone, onError })`, handle streaming state.
- `handleClearChat` → abort + clear messages + clear right panel.
- `handleSourceClick` → toggle `selectedSource` (bidirectional: pill ↔ retrieval panel).
- 3-column layout: left `hidden md:flex w-56`, center `flex-1`, right `hidden lg:flex w-72`.

**Luồng hoạt động:**
```
Mount → loadChatbots() → chatbots loaded in selector
→ user picks chatbot → loadSessions(id) → sessions in left panel
→ user types + Enter → handleSend()
  → append user msg + empty bot msg (streaming: true)
  → playgroundApi.chat(...)
    → onToken: append to bot message content
    → onDone: finalize bot msg with sources + latency
             → update lastSources → RetrievalPanel refreshes
             → update lastLatency → LatencyPanel refreshes
             → refresh sessions list (microtask)
  → user clicks source pill → setSelectedSource
    → SourcePill highlighted, RetrievalPanel item highlighted
→ user clicks "Clear chat" rightSlot → abort + clear
→ navigate away → useEffect cleanup → abortStream()
```

### `src/pages/playground/components/ChatbotSelector.jsx`

- `<select>` với chatbot list; skeleton khi loading.
- `onChange` → `onChange(value || null)` (null khi chọn empty option).

### `src/pages/playground/components/SessionList.jsx`

- 3 states: not selected chatbot (EmptyState), loading (SkeletonLoader), list (ul/li).
- Mỗi session có delete button (hiện khi hover qua `group-hover:opacity-100`).
- Click session gọi `onSelect(sess)` (toàn bộ session object, không chỉ id).
- Format date: `toLocaleDateString("vi-VN")`.

### `src/pages/playground/components/ChatWindow.jsx`

- `flex flex-col h-full` để fill parent height.
- Message list: `flex-1 overflow-y-auto` với `scrollIntoView` khi messages thay đổi.
- `<textarea>` thay `<input>` để Shift+Enter hoạt động; `rows={2}`.
- Nút Gửi disabled khi: `disabled || isStreaming || !input.trim()`.
- Spinner animation trong nút khi streaming.

### `src/pages/playground/components/MessageBubble.jsx`

- User: `bg-blue-600` bubble, align right, max-w-[75%].
- Assistant: `bg-white border` full-width, ReactMarkdown + remark-gfm.
- Blinking cursor `animate-pulse` khi `message.streaming === true`.
- SourcePills bên dưới chỉ khi `!streaming && sources.length > 0`.
- Reuse markdown component style từ `ChatPage.jsx`.

### `src/pages/playground/components/SourcePills.jsx`

- Pills với `📄 fileName [score%]`.
- Selected pill: `bg-blue-600 text-white`, unselected: `bg-white border-gray-300`.
- `score` là optional — không hiển thị nếu null/undefined (mock sources không có score).
- Click → `onPillClick(isSelected ? null : src)` → toggle.

### `src/pages/playground/components/RetrievalPanel.jsx`

- EmptyState khi `sources.length === 0`.
- Mỗi source là `<button>` với highlight khi `isSelected`.
- Highlight logic: match bằng `fileName + sectionTitle`.
- Click source → `onSourceSelect(isSelected ? null : src)` → toggle.

### `src/pages/playground/components/LatencyPanel.jsx`

- Handle 2 shapes: `latency: number` (mock) và `latency: { total, retrieval, llm }` (real API).
- Hiển thị `—` cho các field null/undefined.
- "Chưa có dữ liệu" khi `total == null`.

### `src/pages/playground/components/ModelOverridePanel.jsx`

- `temperature` range input 0–1, step 0.01, realtime display.
- `topK` number input, min 1, max 20.
- `maxTokens` number input, min 64, max 4096, step 64.
- `handleChange` via `useCallback([params, onChange])` để stable ref.
- Note rõ: "Overrides chỉ áp dụng cho session hiện tại."

---

## 6. API / State / Route bị ảnh hưởng

**API endpoints/functions đã dùng:**
- `chatbotsApi.getChatbots({ page: 0, size: 100 })` — load chatbot list
- `playgroundApi.getSessions(chatbotId)` — load sessions
- `playgroundApi.chat({ chatbotId, message, sessionId, overrideParams, onToken, onDone, onError })` — streaming chat
- `playgroundApi.deleteSession(id)` — delete session
- `playgroundApi.exportSession(sessionId)` — restore session messages

**Store/state:**
- Tất cả local state trong `PlaygroundPage` (không dùng global store / chatbotStore).
- `overrideParams` local only — không ghi vào chatbot config.

**Routes:**
- `/playground` — đã đăng ký trong `App.jsx` từ trước (Report 01B). Không thêm route mới.

---

## 7. Validation

| Command | Kết quả | Ghi chú |
|---|---|---|
| `cd Frontend; npm run lint` | ✅ PASS | 0 errors, 0 warnings. Fix 1 warning: unused eslint-disable directive (removed). |
| `cd Frontend; npm run build` | ✅ PASS | 445 modules, 30.89s. Warning chunk >500kB là pre-existing (có từ Report 07). |
| `cd Frontend; npm run build:widget` | NOT RUN | Không sửa widget runtime |
| Backend compile | NOT RUN | Task thuần frontend |
| Backend test | NOT RUN | Task thuần frontend |
| Docker compose config | NOT RUN | Không sửa infra |

---

## 8. Cách test thủ công

```bash
cd Frontend && npm run dev
# Truy cập: http://localhost:5173
# Login với bất kỳ email/password (mock auth)
# Navigate tới /playground
```

**Test cases:**

1. **Load chatbot selector:** Trang load → skeleton → select dropdown có 7 chatbots (5 ACTIVE + 2 INACTIVE, không có DELETED).
2. **Select chatbot:** Chọn "Customer Support Bot" → left panel load sessions (3 sessions cho cb-001).
3. **Session list:** Hiện 2 sessions cho cb-001. Format date hiển thị đúng. Hover → delete button hiện.
4. **Send message:** Gõ "Xin chào" + Enter → user bubble phải → bot bubble trái với blinking cursor → token stream append → khi done: source pills xuất hiện.
5. **Source pills:** Click pill → pill highlighted + RetrievalPanel item highlighted → click lại → deselect.
6. **Retrieval panel:** Sau khi nhận response → 2 MOCK_SOURCES hiện. Tên file + chunk text (không có score vì mock không set).
7. **Latency panel:** Sau response → hiển thị Total: 1350 ms, Retrieval: — , LLM: —.
8. **Model override:** Kéo temperature slider → số cập nhật realtime → gửi message → override được gửi kèm request.
9. **Clear chat:** Click "Clear chat" button ở header rightSlot → messages xóa hết, latency/sources reset.
10. **Clear during stream:** Click "Clear chat" trong lúc bot đang reply → stream abort → messages clear ngay lập tức.
11. **Restore session:** Click session "sess-001" → messages được restore từ `messagesBySession` (6 messages: 3 turns).
12. **Delete session:** Hover session → click ✕ → session biến khỏi list.
13. **No chatbot selected:** Input placeholder = "Chọn chatbot trước...", disabled. ChatWindow EmptyState = "Chọn chatbot để bắt đầu kiểm thử."
14. **Mobile (< md):** Left panel ẩn, center full width. Right panel ẩn (< lg).
15. **Navigate away:** Unmount → `abortStream()` được gọi via cleanup useEffect.

---

## 9. Rủi ro và lưu ý debug

**Dễ lỗi:**
- `abortControllerRef` không phải state — không trigger re-render. Nếu Clear chat không hoạt động, check `abortControllerRef.current` trong DevTools.
- `handleSessionSelect` gọi `playgroundApi.exportSession(id)` — trong real mode trả Blob. `result.messages` = undefined → messages = []. Không crash nhưng không restore.
- `setRightSlot` với `handleClearChat` trong useEffect: vì `handleClearChat` là `useCallback([abortStream])` và `abortStream` là `useCallback([])`, cả hai stable. Nếu Clear chat không update rightSlot đúng, check deps chain.

**Debug:**
- Check `isStreaming` state trong React DevTools khi gửi message.
- Check `abortControllerRef.current` trong `handleSend` callback.
- Check network tab: mock mode không có actual fetch calls; real mode có SSE POST request tới `/api/playground/chat`.

**Network/SSE:**
- Mock: timeout-based, không có actual SSE.
- Real: `fetch()` với `ReadableStream`, parse `event:/data:` line pairs.
- Abort: `AbortController.abort()` → fetch throws `AbortError` → `onError` không gọi (có kiểm tra `err?.name !== "AbortError"`).

---

## 10. Lỗi hoặc giới hạn còn tồn tại

### Session restore trong real mode không hoạt động
`playgroundApi.exportSession(id)` real API trả Blob (file download). `result.messages` = undefined → messages restore = []. Playground chỉ hiển thị session được select nhưng không có messages cũ. Cần thêm endpoint `GET /api/playground/sessions/:id/messages` để fix production.

### MOCK_SOURCES không có `score` field
Mock sources: `{ fileName, sectionTitle, pages, chunkText }` — không có `score`. SourcePills và RetrievalPanel sẽ không hiển thị score %. Real API nên trả `score` (0–1 float) trong sources. UI đã handle gracefully (`src.score != null`).

### latency mock chỉ là total ms
Mock `onDone` trả `latency: 1350` (single number). LatencyPanel hiển thị Retrieval: — và LLM: —. Real API cần trả `{ total, retrieval, llm }` để hiện breakdown đầy đủ.

### Left panel và Right panel ẩn trên mobile
SessionList ẩn khi `< md (768px)`. RetrievalPanel/LatencyPanel/ModelOverride ẩn khi `< lg (1024px)`. Trên mobile chỉ có ChatWindow. Để access sessions/panels trên mobile cần drawer toggle (ngoài scope prompt này).

### Polling sessions sau send
Sau `onDone`, sessions được refresh qua `Promise.resolve().then(...)`. Nếu có race condition (user clicks delete ngay sau send), session list có thể bị stale 1 cycle. Không critical trong scope này.

### overrideParams không persist qua session change
Khi đổi chatbot, overrideParams reset về DEFAULT. Nếu user muốn giữ params qua sessions, cần localStorage hoặc global state (ngoài scope).

---

## 11. Đề xuất prompt tiếp theo

Build + lint PASS. Đề xuất các bước tiếp theo:

**08B — Playground Mobile Panels (optional)**:
Thêm toggle drawer cho SessionList và Right panel trên mobile (< md và < lg). Sử dụng `Drawer` component từ common.

**09 — Analytics Page**:
Implement `/analytics` với `analyticsApi`:
- Summary cards (totalMessages, uniqueSessions, avgSatisfaction, fallbackRate)
- Daily chart (bar chart thuần Tailwind, không cần Chart.js)
- Top chatbots table
- Unanswered questions list
- Session log table với pagination

**10 — Settings Page**:
Implement `/settings` với `settingsApi`:
- Profile tab
- API Keys tab (generate, list, delete)
- Team tab (invite, role change, remove)

**Cleanup (optional)**:
- Remove `src/api/documentApi.js` cũ (singleton, unused theo Report 07)
- Add `score` field vào `MOCK_SOURCES` để test RetrievalPanel score display
- Add `latency: { total, retrieval, llm }` breakdown vào mock `onDone` để test LatencyPanel đầy đủ
