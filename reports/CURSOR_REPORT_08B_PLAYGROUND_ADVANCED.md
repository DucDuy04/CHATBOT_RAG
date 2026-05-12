# Cursor Report 08B - Playground Advanced

## 1. Mức độ hiểu task

- **Task là gì?** Bổ sung Compare mode (song song config A/B + `playgroundApi.compare`), Prompt Builder (`Drawer` + system prompt override session-local), Export log (download từ `exportSession`), không phá core 08A.
- **Hiểu task:** 100%
- **Phần chắc chắn:** Wiring API hiện có, tách input compare vs chat, merge `systemPrompt` vào core chat và compare payload, xử lý Blob vs object khi export.
- **Phần giả định:** Response compare real có shape `{ configA, configB }` (code còn đọc thêm `config_a` / `config_b` defensively).
- **Phạm vi không làm:** Đổi HTTP API backend, sửa widget, fix restore session production bằng export (theo chỉ thị prompt), Analytics, `chatbotsApi.updateChatbot`.

---

## 2. Checklist mapping

| Checklist item | Status | File / ghi chú |
|---|---|---|
| Compare mode toggle | ✅ | `CompareModeToggle.jsx` + top bar `PlaygroundPage.jsx` |
| Side-by-side ComparePane | ✅ | `ComparePane.jsx` — grid A/B configs + answers |
| Same query → `compare()` parallel | ✅ | `handleCompare` gọi một lần `playgroundApi.compare` |
| Prompt Builder drawer | ✅ | `PromptBuilderDrawer.jsx` + `Drawer.jsx` |
| Override system prompt session-only | ✅ | `sessionPromptOverride` state, không gọi updateChatbot |
| Export log button | ✅ | `ExportSessionButton.jsx` |
| POST compare contract giữ nguyên | ✅ | `configA` / `configB` objects |
| GET export | ✅ | Dùng `playgroundApi.exportSession` không đổi |
| Giữ core streaming | ✅ | `compareMode === false` → `ChatWindow` + `playgroundApi.chat` như 08A |
| Disable compare khi thiếu điều kiện | ✅ | `runDisabled` + không gọi khi streaming |
| Lint / build | ✅ | PASS |

---

## 3. Các file đã tham khảo

| File | Mục đích | Kết luận |
|---|---|---|
| `.cursor/rules/*` | Rule nền | Minimal diff, không đổi package |
| `reports/CURSOR_REPORT_08A_PLAYGROUND_CORE.md` | Core baseline | Flow chat, `abortControllerRef`, `exportSession` limitation |
| `reports/CURSOR_REPORT_02_API_LAYER.md` | API | `compare`, `exportSession` mock vs real |
| `reports/CURSOR_REPORT_01C_LAYOUT_CONTEXT.md` | Layout | `setRightSlot` chỉ Clear chat (giữ nguyên) |
| `Frontend/src/api/playgroundApi.js` | Contract | `compare` POST; `exportSession` mock object / real Blob |
| `Frontend/src/pages/playground/PlaygroundPage.jsx` | Edit target | Thêm state compare + prompt + wiring |
| `Frontend/src/components/common/Drawer.jsx` | UI | Portal + Escape + overlay |

---

## 4. Các file đã thay đổi / tạo mới

| File | Thay đổi | Layer |
|---|---|---|
| `PlaygroundPage.jsx` | Compare + prompt + `chatOverrideParams`, top bar actions | ui |
| `ModelOverridePanel.jsx` | Một dòng hướng dẫn mở Prompt Builder | ui |
| `CompareModeToggle.jsx` | **Mới** | ui |
| `CompareConfigPanel.jsx` | **Mới** | ui |
| `ComparePane.jsx` | **Mới** | ui |
| `PromptBuilderDrawer.jsx` | **Mới** | ui |
| `ExportSessionButton.jsx` | **Mới** | ui |

**Không sửa:** `playgroundApi.js`, `playgroundMock.js`, `ChatPage.jsx`, widget, routes.

---

## 5. Nội dung chi tiết & luồng

### 5.1 Compare mode có làm ảnh hưởng core streaming không?

**Không can thiệp vào pipeline streaming khi Compare mode OFF.**

- `playgroundApi.chat({ ... })` vẫn được gọi từ `handleSend` với `overrideParams: chatOverrideParams` (chỉ thêm field `systemPrompt` khi Prompt Builder có nội dung).
- `onToken` / `onDone` / `onError` không đổi.
- `abortControllerRef` gán như 08A; `abortStream` cleanup mount/unmount giữ nguyên.

**Khi Compare mode ON:**

- Component center render `ComparePane` thay cho `ChatWindow` (điều kiện `compareMode`). **State `messages`, `input`, `isStreaming` không bị reset** khi bật/tắt toggle — tắt Compare mode thì chat core hiển thị lại với lịch sử cũ.
- Không chạy hai stream song song; compare dùng `playgroundApi.compare` (request thường), không dùng SSE.

### 5.2 Prompt override được truyền vào core chat và compare như thế nào?

**State:** `sessionPromptOverride` (string, session-local, không localStorage).

**Core chat:**

```text
chatOverrideParams = useMemo(() => ({
  ...overrideParams,                    // temperature, topK, maxTokens từ ModelOverridePanel
  ...(sessionPromptOverride.trim()
      ? { systemPrompt: trimmed }
      : {}),
}), [overrideParams, sessionPromptOverride]);
```

`handleSend` truyền `overrideParams: chatOverrideParams` vào `playgroundApi.chat`.

**Compare:**

- Hàm `build(cfg)` trong `handleCompare`:
  - `systemPrompt` gửi lên = `cfg.systemPrompt.trim()` **hoặc** nếu rỗng thì fallback `sessionPromptOverride.trim()`.
  - Nếu cả hai rỗng: payload **không** có key `systemPrompt`.

**Prompt Builder UI:**

- `PromptBuilderDrawer`: Apply → `(text || "").trim()` lưu vào `sessionPromptOverride`, đóng drawer.
- Clear override: xóa state (không đóng drawer) để user tiếp tục chỉnh.
- Đồng bộ draft khi mở drawer: `Promise.resolve().then(() => setDraft(...))` để tránh `react-hooks/set-state-in-effect`.

### 5.3 Export xử lý Blob và mock object ra sao?

**Nguồn:** `playgroundApi.exportSession(selectedSessionId)` — contract module không đổi.

| Mode | Kiểu trả về | Xử lý |
|---|---|---|
| Mock | Plain object `{ sessionId, exportedAt, messages }` | `JSON.stringify(..., null, 2)` → Blob `application/json` → download `playground-session-<id>.json` |
| Real | `Blob` (axios `responseType: "blob"`) | Dùng trực tiếp; `blob.type` lowercase — nếu chứa `csv` hoặc `text/csv` → `.csv`, ngược lại → `.json` |

**UX:**

- Nút **disabled** khi không có `selectedSessionId` (tránh spam; có `title` tooltip).
- Success: `toast.success`; lỗi: `toast.error`.

> **Lưu ý:** Nếu backend trả Blob không gắn `type`, mặc định sẽ coi như JSON (filename `.json`) — hành vi defensive.

### 5.4 Limitation từ 08A: giữ nguyên hay phát sinh?

| Limitation 08A | Trạng thái |
|---|---|
| Restore session real: `exportSession` trả Blob → không đọc messages trong UI | **Giữ nguyên** — không dùng export để fix (theo prompt). |
| Mock sources không có `score` | **Giữ nguyên** — compare/core vẫn `score != null` mới hiện %. |
| Polling session list không đổi | Giữ nguyên |
| Mobile: left/right panel ẩn | **Giữ nguyên** — ComparePane scroll được trên narrow; không thêm drawer mobile trong 08B |

**Phát sinh thêm (nhỏ):**

- Bundle JS tăng nhẹ (~+11KB gzip) do thêm markdown/compare UI trong Playground.
- Compare không có abort UI (request ngắn); không trong scope.

---

## 6. API / State

| Endpoint / function | Dùng ở đâu |
|---|---|
| `playgroundApi.compare` | `handleCompare` |
| `playgroundApi.exportSession` | `ExportSessionButton` |
| `playgroundApi.chat` | `handleSend` (không đổi signature) |

**State mới (PlaygroundPage):** `compareMode`, `compareInput`, `compareLoading`, `compareError`, `compareResult`, `compareConfigA/B`, `sessionPromptOverride`, `promptBuilderOpen`.

---

## 7. Validation

| Command | Kết quả | Ghi chú |
|---|---|---|
| `cd Frontend && npm run lint` | **PASS** | Sửa `PromptBuilderDrawer` sync draft bằng `Promise.resolve().then` |
| `cd Frontend && npm run build` | **PASS** | 450 modules; warning chunk >500kB |

---

## 8. Cách test thủ công

1. **Core regression:** Compare OFF → chọn chatbot → gửi tin → stream, sources, latency, model override như 08A.
2. **Prompt Builder:** Mở drawer → Apply prompt → gửi chat → payload có `systemPrompt` (kiểm tra network mock off).
3. **Compare:** Bật Compare mode → nhập query → chỉnh A/B (vd temp khác nhau) → Run → hai cột answer + sources/latency mock.
4. **Compare blocked:** Đang streaming core → Run compare disabled; không chatbot / query trống → disabled.
5. **Toggle:** Sau compare, tắt Compare mode → messages chat cũ vẫn còn.
6. **Export:** Chọn session từ list → Export log → file JSON mock; real mode cần backend trả Blob.
7. **Export disabled:** Chưa chọn session → nút disabled.
8. **Clear chat:** Xóa messages + reset/compare form phụ (result, error, compare input) như code hiện tại.

---

## 9. Rủi ro / debug

- **Compare lỗi axios:** message có thể không có `e.message` đầy đủ — có thể mở rộng đọc `e.response?.data` sau nếu cần.
- **Export filename:** Phụ thuộc `blob.type` từ server.
- **So sánh config summary trên UI:** Chỉ tóm tắt; `systemPrompt` global không in trong `fmtCfg` nếu chỉ dùng Prompt Builder (có banner riêng).

---

## 10. Đề xuất prompt tiếp theo (không triển khai)

- **08C:** Drawer mobile cho SessionList + right panel trên Playground.
- **Backend:** `GET /api/playground/sessions/:id/messages` cho restore production; export `Content-Disposition` filename chuẩn.
