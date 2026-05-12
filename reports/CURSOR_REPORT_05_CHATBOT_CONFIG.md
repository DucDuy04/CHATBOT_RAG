# Cursor Report 05 - Chatbot Config

**Ngày**: 2026-05-05  
**Scope**: Implement Chatbot Config page thật tại route `/chatbots/:id/config`.

---

## 1. Mức độ hiểu task

**Task này là gì**:  
Biến `ChatbotConfigPage.jsx` placeholder thành trang cấu hình chatbot thật — load bot theo id từ URL, render 3 sections (Prompt, Model, Status), save per section, delete với confirm modal, tab bar Config/Embed, Back link, Header rightSlot Delete button.

**Phạm vi đã làm**:
- ✅ `ChatbotConfigPage.jsx` — load, form state, save handlers, delete, header injection
- ✅ `ChatbotConfigTabs.jsx` — tab bar Config | Embed với NavLink
- ✅ `ChatbotConfigSkeleton.jsx` — animated skeleton cho loading state
- ✅ `PromptSettingsSection.jsx` — textarea, char count, variable hints, save
- ✅ `ModelSettingsSection.jsx` — model select, temperature slider, topK, maxTokens, validation, save
- ✅ `StatusSection.jsx` — toggle Active/Inactive, save
- ✅ npm run lint: **PASS**
- ✅ npm run build: **PASS**

**Phạm vi không làm**:
- Không implement `/chatbots/:id/embed` thật (placeholder vẫn giữ)
- Không implement embed color picker/script/preview
- Không sửa ChatbotsPage, Dashboard, Documents, Playground, Analytics, Settings
- Không sửa widget/WidgetChatPage

---

## 2. Checklist mapping

| Chatbot Config checklist item | Status | File liên quan | Ghi chú |
|---|---|---|---|
| Route /chatbots/:id/config | ✅ | `App.jsx` (đã có từ Prompt 01C) | Page đã replace placeholder |
| Load current config | ✅ | `ChatbotConfigPage.jsx` | `chatbotsApi.getChatbot(id)` khi mount |
| chatbotsApi.getChatbot(id) | ✅ | `ChatbotConfigPage.jsx` | Trong `loadChatbot()` callback |
| System prompt textarea | ✅ | `PromptSettingsSection.jsx` | `<textarea>` với `ref` cho cursor |
| Char count | ✅ | `PromptSettingsSection.jsx` | `value.length` + cảnh báo nếu > 4000 |
| Variable hint {user_name} | ✅ | `PromptSettingsSection.jsx` | Button insert tại cursor position |
| Variable hint {date} | ✅ | `PromptSettingsSection.jsx` | Button insert tại cursor position |
| Model select GPT-4o/GPT-3.5 | ✅ | `ModelSettingsSection.jsx` | 4 options: GPT-4o, GPT-3.5, Llama 3.1 70B, Llama 3 8B |
| Temperature slider 0–1 | ✅ | `ModelSettingsSection.jsx` | range input, step 0.05, hiển thị value |
| Top-K input | ✅ | `ModelSettingsSection.jsx` | number input, validate 1–50 |
| Max tokens input | ✅ | `ModelSettingsSection.jsx` | number input, validate 100–8000 |
| Status toggle | ✅ | `StatusSection.jsx` | 2 button cards: Active / Inactive |
| Save per section toast | ✅ | Cả 3 sections | `toast.success(...)` sau khi save thành công |
| Save Prompt section | ✅ | `PromptSettingsSection.jsx` + `ChatbotConfigPage.jsx` | `handleSavePrompt()` |
| Save Model Settings section | ✅ | `ModelSettingsSection.jsx` + `ChatbotConfigPage.jsx` | `handleSaveModel()` |
| Save Status section | ✅ | `StatusSection.jsx` + `ChatbotConfigPage.jsx` | `handleSaveStatus()` |
| chatbotsApi.updateChatbot(id, payload) | ✅ | `ChatbotConfigPage.jsx` | Được gọi trong cả 3 save handlers |
| Delete confirm soft delete | ✅ | `ChatbotConfigPage.jsx` + `ConfirmDeleteModal` | Danger Zone card + Header button |
| chatbotsApi.deleteChatbot(id) | ✅ | `ChatbotConfigPage.jsx` | Trong `handleDelete()` |
| Redirect /chatbots after delete | ✅ | `ChatbotConfigPage.jsx` | `navigate("/chatbots")` sau delete thành công |
| Tab bar Config | Embed | ✅ | `ChatbotConfigTabs.jsx` | NavLink — không reload page |
| Embed tab link /chatbots/:id/embed | ✅ | `ChatbotConfigTabs.jsx` | NavLink to `/chatbots/${id}/embed` |
| Back button | ✅ | `ChatbotConfigPage.jsx` | `<Link to="/chatbots">← Chatbots</Link>` |
| Loading skeleton | ✅ | `ChatbotConfigSkeleton.jsx` | Render khi `loading=true` |
| Error state | ✅ | `ChatbotConfigPage.jsx` | Error card + Retry + Back link khi `loadErr` |

---

## 3. Các file đã tham khảo

| File | Lý do đọc | Kết luận |
|---|---|---|
| `Frontend/src/pages/chatbots/ChatbotConfigPage.jsx` | Placeholder hiện tại | Chỉ là text placeholder |
| `Frontend/src/api/chatbotsApi.js` | Function signatures, payload, return | `getChatbot(id)` → bot object; `updateChatbot(id, payload)` → updated bot; `deleteChatbot(id)` → `{success:true}` |
| `Frontend/src/mocks/chatbotsMock.js` | Data shape thực tế | `modelConfig: {model, temperature, maxTokens}` — không có `topK`; fallback `?? 5` |
| `Frontend/src/components/common/ConfirmDeleteModal.jsx` | Props interface | `{isOpen, onClose, onConfirm, loading, title, message, confirmText, cancelText}` |
| `Frontend/src/components/common/useToast.js` | Hook API | `toast.success()`, `toast.error()` |
| `Frontend/src/components/common/SkeletonLoader.jsx` | Variants | `variant="line"` dùng trong skeleton |
| `Frontend/src/components/common/StatusBadge.jsx` | Props | `status` string (lowercase) |
| `Frontend/src/contexts/LayoutContext.jsx` | `setPageTitle`, `setRightSlot`, `clearRightSlot` | Stable callbacks |
| `Frontend/src/components/layout/AppLayout.jsx` | Layout scroll behavior | `<main>` có `overflow-y-auto p-6` — page content nằm trong vùng scroll này |
| `Frontend/src/pages/chatbots/components/` | Existing components từ Prompt 04 | 4 components có sẵn, không conflict |

---

## 4. Các file đã thay đổi

| File | Nội dung thay đổi | Lý do | Ảnh hưởng behavior cũ |
|---|---|---|---|
| `src/pages/chatbots/ChatbotConfigPage.jsx` | **Rewrite** placeholder | Implement trang thật | Có — placeholder cũ chỉ text |
| `src/pages/chatbots/components/ChatbotConfigTabs.jsx` | **Tạo mới** | Tab bar component | Không |
| `src/pages/chatbots/components/ChatbotConfigSkeleton.jsx` | **Tạo mới** | Skeleton loading | Không |
| `src/pages/chatbots/components/PromptSettingsSection.jsx` | **Tạo mới** | Prompt section | Không |
| `src/pages/chatbots/components/ModelSettingsSection.jsx` | **Tạo mới** | Model section | Không |
| `src/pages/chatbots/components/StatusSection.jsx` | **Tạo mới** | Status section | Không |

---

## 5. Component design

### ChatbotConfigPage

```
ChatbotConfigPage (useParams → id)
├── State: chatbot, loading, loadErr
├── State: form { systemPrompt, model, temperature, topK, maxTokens, status }
├── State: savingPrompt, savingModel, savingStatus
├── State: deleteOpen, deleting
├── loadChatbot() — useCallback([id]) → getChatbot(id) → setChatbot + setForm(toFormState)
├── useEffect([loadChatbot]) — initial load
├── useEffect([chatbot.name, setPageTitle]) — set page title sau khi load
├── useEffect([setRightSlot]) — inject Delete button vào Header
├── useEffect([clearRightSlot]) — cleanup unmount
├── buildPayload(overrides) — merge form state vào full payload
├── handleSavePrompt / handleSaveModel / handleSaveStatus — gọi updateChatbot, toast
├── handleDelete — gọi deleteChatbot, toast, navigate("/chatbots")
└── Render:
    ├── loading → ChatbotConfigSkeleton
    ├── loadErr → Error card (Retry + Back link)
    └── main →
        ├── Back link (<Link to="/chatbots">)
        ├── Bot name + domain
        ├── ChatbotConfigTabs
        ├── PromptSettingsSection
        ├── ModelSettingsSection
        ├── StatusSection
        ├── Danger Zone card (Delete button)
        └── ConfirmDeleteModal
```

### ChatbotConfigTabs

- Dùng `NavLink` với prop `end` cho Config tab (exact match `/config`)
- Active class: `border-blue-600 text-blue-600`
- Inactive class: `border-transparent text-gray-500 hover:...`
- Không reload page khi click tab

### PromptSettingsSection

- `textareaRef` cho cursor position tracking
- `insertVariable(variable)`: dùng `el.selectionStart`/`el.selectionEnd` để insert tại cursor; `requestAnimationFrame` để restore cursor sau React re-render
- Char count: `value.length`, cảnh báo amber nếu > 4000 (không block)
- `saveError` local state: clear trước mỗi save attempt
- Save button: spinner + disabled khi `saving`

### ModelSettingsSection

- 4 model options: GPT-4o, GPT-3.5 Turbo, Llama 3.1 70B, Llama 3 8B
- Dynamic option nếu model từ API không có trong list (để tránh select bị blank)
- Temperature: `<input type="range">` step 0.05, hiển thị `.toFixed(2)`
- Validation: `tempInvalid || topKInvalid || maxTokInvalid` → disable save button
- `isInvalid` guard: return early trong `handleSave` nếu invalid

### StatusSection

- 2 button cards clickable: Active / Inactive
- Visual highlight khi selected: green border cho Active, gray cho Inactive
- Checkmark "✓" prefix khi selected
- `StatusBadge` hiển thị current status

### Skeleton

- `ChatbotConfigSkeleton`: animate-pulse, mimics layout: back link + bot name + tabs + 3 cards
- Dùng `SkeletonLoader` variant="line" cho card content

### Error UI

- Full-page error card với: ⚠️ icon, message, Retry button, Back link
- Retry gọi `loadChatbot()` (useCallback stable)

---

## 6. API integration

| Function | Gọi ở đâu | Payload | Sau gọi |
|---|---|---|---|
| `getChatbot(id)` | `loadChatbot()` — mount + retry | `id` | `setChatbot(bot)`, `setForm(toFormState(bot))` |
| `updateChatbot(id, payload)` | `handleSavePrompt()` | `{systemPrompt, modelConfig, status}` | `applyUpdateResponse(updated)`, toast.success |
| `updateChatbot(id, payload)` | `handleSaveModel()` | `{systemPrompt, modelConfig: {...}, status}` | `applyUpdateResponse(updated)`, toast.success |
| `updateChatbot(id, payload)` | `handleSaveStatus()` | `{systemPrompt, modelConfig, status}` | `applyUpdateResponse(updated)`, toast.success |
| `deleteChatbot(id)` | `handleDelete()` | `id` | `toast.success`, `navigate("/chatbots")` |

**Full payload pattern**: Mỗi save section gửi **toàn bộ** form state hiện tại (`buildPayload(overrides)`) để tránh overwrite field khác ở backend. `overrides` là section đang save — merge vào full payload.

**Data consistency sau save**:
- `applyUpdateResponse(updated)`: nếu API trả object có `id`, `setChatbot(updated)` để cập nhật raw chatbot
- Form state **không** bị reset — giữ nguyên unsaved changes ở section khác
- Nếu API không trả object (mock `deleteChatbot` trả `{success:true}`), chỉ navigate/toast

---

## 7. Data shape assumptions

### Chatbot item
```js
{
  id: string,
  name: string,
  description: string,
  domain: string,
  documentCount: number,
  messageCount: number,
  status: "ACTIVE" | "INACTIVE" | "DELETED",
  updatedAt: string,
  initials: string,
  systemPrompt: string,         // có thể "" nếu chưa set
  modelConfig: {
    model: string,              // "llama-3.1-70b-versatile" trong mock
    temperature: number,        // 0.0–1.0
    maxTokens: number,          // e.g. 1024
    topK?: number,              // KHÔNG có trong mock → fallback ?? 5
  },
}
```

### modelConfig field adaptation
```js
function toFormState(bot) {
  return {
    systemPrompt: bot.systemPrompt || "",
    model:        bot.modelConfig?.model        || "GPT-4o",
    temperature:  bot.modelConfig?.temperature  ?? 0.7,
    topK:         bot.modelConfig?.topK         ?? 5,    // fallback vì mock không có
    maxTokens:    bot.modelConfig?.maxTokens    || 1024,
    status:       bot.status                    || "ACTIVE",
  };
}
```

**`topK` không có trong mock** — dùng `?? 5` fallback. Khi save, sẽ gửi `topK: 5` lên API và lần sau load sẽ có trong response (nếu backend lưu).

### updateChatbot payload fields
API mock nhận: `{ name, description, status, systemPrompt, modelConfig }`.  
Page gửi: `{ systemPrompt, modelConfig: {model, temperature, topK, maxTokens}, status }` — không gửi `name`/`description` để không overwrite.

---

## 8. Form validation

| Field | Validation | Behavior khi invalid |
|---|---|---|
| systemPrompt | > 4000 chars → warning | Amber border + warning text; không block save |
| temperature | 0 ≤ x ≤ 1 | Red error text; Save button disabled |
| topK | integer, 1–50 | Red border + error; Save button disabled |
| maxTokens | integer, 100–8000 | Red border + error; Save button disabled |
| status | "ACTIVE" \| "INACTIVE" | Không cần validate — chỉ 2 options rõ ràng |
| name/description/domain | Không edit trong Config page | — |

**Inline error per section**: `saveError` state trong mỗi section, cleared trước mỗi save attempt.

---

## 9. Responsive behavior

| Vùng | Mobile | Tablet | Desktop |
|---|---|---|---|
| ChatbotConfigPage | Full width, space-y-6 | Centered trong AppLayout | Centered, max content width |
| Tab bar | Flex row, không wrap | Flex row | Flex row |
| PromptSettingsSection | Full width textarea (resize-y) | Full width | Full width |
| ModelSettingsSection | Model/Temp full width; topK+maxTokens stack dọc (`grid-cols-1`) | 2 cols (`sm:grid-cols-2`) | 2 cols |
| StatusSection | 2 status cards stack dọc hoặc wrap | Flex wrap | Flex row |
| Danger Zone | Stack (info + button wrap) | Flex row | Flex row `justify-between` |
| ConfirmDeleteModal | Full width `max-w-sm + p-4` | Centered | Centered |
| Header rightSlot Delete button | Hiển thị đầy đủ (text nhỏ) | Normal | Normal |

---

## 10. Cách test thủ công

### Setup
```bash
cd Frontend && npm run dev
```

### Login + navigate
- `http://localhost:5173/login` → nhập bất kỳ credential → Login
- Click "Chatbots" sidebar → `/chatbots`
- Click "Config" button của "Customer Support Bot" → `/chatbots/cb-001/config`

### Kiểm tra load bot
- Mock delay 250ms → skeleton hiển thị ngắn
- Bot name "Customer Support Bot" xuất hiện
- Header title đổi thành "Customer Support Bot — Config"
- System prompt textarea điền sẵn từ mock

### Test sửa system prompt
- Sửa text trong textarea
- Char count cập nhật real-time
- Nhập > 4000 ký tự → border amber + cảnh báo

### Test insert {user_name}
- Click nút `{user_name}` → text được insert tại cursor position
- Click vào giữa textarea trước khi click → insert đúng vị trí cursor

### Test insert {date}
- Click nút `{date}` → insert `{date}` vào textarea

### Test save prompt
- Click "Save Prompt"
- Spinner 400ms (mock delay)
- Toast "System prompt saved!" xuất hiện

### Test model select
- Dropdown có: GPT-4o, GPT-3.5 Turbo, Llama 3.1 70B, Llama 3 8B
- Mock hiện là "llama-3.1-70b-versatile" → chọn đúng option

### Test temperature slider
- Drag slider → giá trị `.toFixed(2)` cập nhật real-time
- Nhập < 0 hoặc > 1 → error text + Save button disabled

### Test topK/maxTokens validation
- topK: nhập 0 → error + disabled; nhập 51 → error; nhập 5 → valid
- maxTokens: nhập 50 → error; nhập 9000 → error; nhập 1024 → valid

### Test save model settings
- Click "Save Model Settings"
- Toast "Model settings saved!" xuất hiện

### Test status toggle
- Click "Inactive" button → border highlight
- Click "Save Status" → toast "Status updated to Inactive!"
- StatusBadge bên trên cập nhật

### Test tab Embed navigate
- Click "Embed" tab → navigate `/chatbots/cb-001/embed`
- Hiển thị embed placeholder
- Click "Config" tab → navigate lại `/chatbots/cb-001/config`
- Không reload page

### Test Back button
- Click "← Chatbots" text link → navigate `/chatbots`
- Chatbot list vẫn còn state từ trước

### Test delete confirm
- Click "Delete" button ở Header (đỏ) HOẶC "Delete Chatbot" ở Danger Zone
- Modal mở: "Delete 'Customer Support Bot'?"
- Click "Cancel" → modal đóng
- Click "Yes, Delete" → spinner, toast success → redirect `/chatbots`
- Bot biến mất khỏi list (mock soft delete)

### Test loading state (tùy chọn)
- Tăng mockDelay trong chatbotsApi.js → thấy ChatbotConfigSkeleton rõ hơn

### Test error state (tùy chọn)
- Vào URL `/chatbots/cb-999/config` (không tồn tại)
- Mock throw 404 → Error card hiện với Retry + Back link

---

## 11. Kết quả command

| Command | Kết quả | Chi tiết |
|---|---|---|
| `cd Frontend; npm run lint` | **PASS ✅** | 0 lỗi, 0 warnings — không cần fix gì |
| `cd Frontend; npm run build` | **PASS ✅** | 426 modules (tăng từ 421), 490.73KB (gzip 153.65KB), 14.35s |
| `npm run build:widget` | NOT RUN | Không thay đổi widget |
| `docker compose config` | NOT RUN | Không thay đổi infra |

**Ghi chú về lint**: Lần này không có lỗi `react-hooks/set-state-in-effect` do:
- `loadChatbot` dùng `useCallback` với try/finally — setState gọi trong async flow, không bị flag
- `setPageTitle` gọi trong `useEffect([chatbot?.name, setPageTitle])` — setter từ context, stable ref
- `setRightSlot` không gọi setState trong effect body

**Ghi chú về bundle size**: 490KB vs 474KB trước — tăng ~16KB cho 6 component files mới. Hợp lý.

---

## 12. Lỗi hoặc giới hạn còn tồn tại

| Vấn đề | Mức độ | Mô tả |
|---|---|---|
| `topK` không có trong mock data | 🟢 Thấp | Mock `chatbotsMock.js` không có `topK` trong `modelConfig`. Page fallback `?? 5`. Khi save, `topK: 5` sẽ được gửi và lưu vào mock. Real API có thể không support `topK` — cần confirm với backend. |
| Model values không match mock | 🟢 Thấp | Mock dùng "llama-3.1-70b-versatile" (Groq), spec UI yêu cầu "GPT-4o"/"GPT-3.5". Solution: 4 model options trong dropdown bao gồm cả hai. Dynamic fallback option nếu model từ API không có trong list. |
| `applyUpdateResponse` không reset form | 🟢 Thấp | Sau save, form state giữ nguyên (intentional để không mất unsaved changes ở section khác). Nếu API trả data khác với gì đã gửi (e.g. backend normalize temperature), form sẽ không phản ánh ngay. Cần fetch lại nếu cần consistency tuyệt đối. |
| `setDeleteOpen(false)` trong catch | 🟢 Thấp | Khi `handleDelete` fail, `setDeleting(false)` nhưng modal vẫn mở để user thử lại hoặc đóng thủ công. Toast error hiển thị. Đây là UX hợp lý. |
| Temperature `type="range"` UX trên mobile | 🟢 Thấp | Range input native browser, UX OK trên mobile nhưng không đẹp bằng custom slider. Đủ cho scope hiện tại. |
| Delete button ở 2 nơi | 🟢 Thấp | Delete button xuất hiện cả ở Header rightSlot lẫn Danger Zone card. Cả hai gọi `setDeleteOpen(true)`. Intentional — Danger Zone cung cấp context rõ hơn, Header button dễ tiếp cận. |

---

## 13. Đề xuất prompt tiếp theo

**Build và lint PASS** → Chatbot Config hoàn chỉnh.

**Đề xuất Prompt 06 — Chatbot Embed**:  
Implement `ChatbotEmbedPage.jsx` tại route `/chatbots/:id/embed` với:
- Tab bar Config | Embed (Embed active)
- Load embed config qua `chatbotsApi.getEmbedConfig(id)`
- Widget preview section
- Embed code snippet (copy button)
- Fields: widgetColor, welcomeMessage, position, allowedOrigins
- Save: `chatbotsApi.updateEmbedConfig(id, payload)`
- Inject "Save Embed Config" hoặc nút khác vào Header rightSlot
- Dùng `CopyButton` nếu đã có từ common components
