# Cursor Report 04 - Chatbot Management

**Ngày**: 2026-05-05  
**Scope**: Implement Chatbot Management page thật tại route `/chatbots`.

---

## 1. Mức độ hiểu task

**Task này là gì**:  
Biến `ChatbotsPage.jsx` placeholder thành trang quản lý chatbot thật — search/filter, table với 8 columns, pagination, CreateChatbotModal, Zustand chatbotStore. Inject "+ New chatbot" vào Header rightSlot qua LayoutContext.

**Phạm vi đã làm**:
- ✅ `src/stores/chatbotStore.js` — Zustand store đầy đủ
- ✅ `ChatbotToolbar.jsx` — search (debounce 300ms) + status/domain filter + clear
- ✅ `ChatbotTable.jsx` — 8 columns: Avatar, Name+Desc, Domain, Docs, Messages, Status, Updated, Config
- ✅ `CreateChatbotModal.jsx` — form name/description/domain, validation, toast success
- ✅ `Pagination.jsx` — Previous/page info/Next, 0-based nội bộ, 1-based hiển thị
- ✅ `ChatbotsPage.jsx` — wiring toàn bộ, rightSlot injection, fetch trigger
- ✅ npm run lint: **PASS**
- ✅ npm run build: **PASS**

**Phạm vi không làm**:
- Không implement `/chatbots/:id/config` (Prompt 05)
- Không implement `/chatbots/:id/embed` (Prompt 06)
- Không thêm Delete button (ngoài scope checklist)
- Không sửa Dashboard, Documents, Playground, Analytics, Settings
- Không sửa widget/WidgetChatPage

---

## 2. Checklist mapping

| Chatbot Management checklist item | Status | File liên quan | Ghi chú |
|---|---|---|---|
| Route /chatbots | ✅ | `App.jsx` (đã có từ Prompt 01C) | Page đã replace placeholder |
| Search by name | ✅ | `ChatbotToolbar.jsx` | Input text + debounce 300ms |
| Debounce 300ms | ✅ | `ChatbotToolbar.jsx` | `setTimeout/clearTimeout` trong useEffect |
| Status filter | ✅ | `ChatbotToolbar.jsx` | Select: All/Active/Inactive |
| Domain filter | ✅ | `ChatbotToolbar.jsx` | Select options từ `domains` trong store |
| + New chatbot button | ✅ | `ChatbotsPage.jsx` | Inject qua `setRightSlot` vào Header |
| CreateChatbotModal | ✅ | `CreateChatbotModal.jsx` | Dùng `Modal` shared component |
| name field | ✅ | `CreateChatbotModal.jsx` | Required, validation "Name is required." |
| description field | ✅ | `CreateChatbotModal.jsx` | Optional, textarea |
| domain field | ✅ | `CreateChatbotModal.jsx` | Required, validation "Domain is required." |
| POST /api/chatbots | ✅ | `chatbotStore.js` → `chatbotsApi.createChatbot` | |
| Refresh list after create | ✅ | `ChatbotsPage.jsx` → `handleCreated()` → `fetchChatbots()` | |
| ChatbotTable Avatar | ✅ | `ChatbotTable.jsx` | initials + color coding; avatarUrl nếu có |
| ChatbotTable Name + Description | ✅ | `ChatbotTable.jsx` | name bold, description truncate |
| ChatbotTable Domain | ✅ | `ChatbotTable.jsx` | Column domain |
| ChatbotTable Docs | ✅ | `ChatbotTable.jsx` | `documentCount` |
| ChatbotTable Messages | ✅ | `ChatbotTable.jsx` | `messageCount.toLocaleString()` |
| ChatbotTable Status | ✅ | `ChatbotTable.jsx` | `StatusBadge` active/inactive |
| ChatbotTable Updated | ✅ | `ChatbotTable.jsx` | `formatDate(updatedAt)` → "May 1, 2026" |
| Config button | ✅ | `ChatbotTable.jsx` | Button "Config" mỗi row |
| Navigate /chatbots/:id/config | ✅ | `ChatbotTable.jsx` | `useNavigate()` → `/chatbots/${bot.id}/config` |
| Pagination | ✅ | `Pagination.jsx` + `ChatbotsPage.jsx` | Prev/Next + page info |
| Zustand chatbotStore | ✅ | `src/stores/chatbotStore.js` | Zustand v5 |
| chatbotStore list | ✅ | `chatbotStore.js` | `items: []` |
| chatbotStore filters | ✅ | `chatbotStore.js` | `filters: { search, status, domain }` |
| chatbotStore pagination | ✅ | `chatbotStore.js` | `pagination: { page, size, total, totalPages }` |
| chatbotStore loading | ✅ | `chatbotStore.js` | `loading: false` |
| GET /api/chatbots?search=&status=&domain=&page=&size= | ✅ | `chatbotStore.js` → `chatbotsApi.getChatbots` | |

---

## 3. Các file đã tham khảo

| File | Lý do đọc | Kết luận |
|---|---|---|
| `Frontend/src/api/chatbotsApi.js` | Function signatures, params, return shape | `getChatbots({search,status,domain,page,size})` → `{items,page,size,total,totalPages}`; `createChatbot({name,description,domain})` → chatbot object |
| `Frontend/src/mocks/chatbotsMock.js` | Data shape thực tế | Fields: id, name, description, domain, documentCount, messageCount, status, updatedAt, initials; 5 ACTIVE, 2 INACTIVE, 1 DELETED |
| `Frontend/src/api/apiMode.js` | `createPaginatedResponse` signature | Trả `{items,page,size,total,totalPages}` — 0-based page |
| `Frontend/src/stores/authStore.js` | Zustand v5 pattern đang dùng | `create((set) => ({...}))`, không persist ngoài localStorage |
| `Frontend/src/components/common/Modal.jsx` | Props interface | `isOpen, onClose, title, children, maxWidth` |
| `Frontend/src/components/common/useToast.js` | Hook API | `toast.success(msg)`, `toast.error(msg)` |
| `Frontend/src/components/common/index.js` | Exports có sẵn | Modal, SkeletonLoader, EmptyState, StatusBadge, useToast đều sẵn sàng |
| `Frontend/src/contexts/LayoutContext.jsx` | setRightSlot, clearRightSlot | Stable callbacks từ useCallback |
| `Frontend/src/pages/chatbots/ChatbotsPage.jsx` | Placeholder hiện tại | Chỉ là placeholder text |
| `Frontend/eslint.config.js` | Hiểu rules trước khi tạo file | `react-hooks/set-state-in-effect` cần chú ý |

---

## 4. Các file đã thay đổi

| File | Nội dung thay đổi | Lý do | Ảnh hưởng behavior cũ |
|---|---|---|---|
| `src/stores/chatbotStore.js` | **Tạo mới** | Zustand store cho list/filter/pagination | Không |
| `src/pages/chatbots/ChatbotsPage.jsx` | **Rewrite** placeholder | Implement trang thật | Có — placeholder cũ chỉ text |
| `src/pages/chatbots/components/ChatbotToolbar.jsx` | **Tạo mới** | Toolbar component | Không |
| `src/pages/chatbots/components/ChatbotTable.jsx` | **Tạo mới** | Table component | Không |
| `src/pages/chatbots/components/CreateChatbotModal.jsx` | **Tạo mới** | Modal tạo chatbot | Không |
| `src/pages/chatbots/components/Pagination.jsx` | **Tạo mới** | Pagination component | Không |

---

## 5. Store design

### State

```
{
  items: [],          // chatbot list từ API
  loading: false,     // fetch in progress
  error: null,        // fetch error message
  creating: false,    // create in progress
  createError: null,  // create error message
  domains: [],        // unique domains cho dropdown filter
  
  filters: {
    search: "",       // search text (debounced)
    status: "",       // "ACTIVE" | "INACTIVE" | "" (all)
    domain: "",       // domain string | "" (all)
  },
  
  pagination: {
    page: 0,          // 0-based index
    size: 10,
    total: 0,
    totalPages: 1,
  },
}
```

### Actions

| Action | Side effect |
|---|---|
| `setSearch(s)` | Cập nhật `filters.search`, reset `page` → 0 |
| `setStatus(s)` | Cập nhật `filters.status`, reset `page` → 0 |
| `setDomain(d)` | Cập nhật `filters.domain`, reset `page` → 0 |
| `setPage(p)` | Cập nhật `pagination.page` |
| `setSize(s)` | Cập nhật `pagination.size`, reset `page` → 0 |
| `resetFilters()` | Xóa search/status/domain, reset `page` → 0 |
| `fetchChatbots()` | Gọi `chatbotsApi.getChatbots(...)`, cập nhật `items`+`pagination`+`domains` |
| `createChatbot(p)` | Gọi `chatbotsApi.createChatbot(...)`, trả về newBot; caller tự fetch lại |

### Fetch trigger

`ChatbotsPage.jsx` có `useEffect([fetchChatbots, search, status, domain, page, size])`:
- Chạy khi mount (lần đầu)
- Chạy khi bất kỳ filter/page thay đổi

`fetchChatbots` là Zustand action — stable reference, không gây loop. Chỉ re-runs khi deps thay đổi.

### Search debounce

Debounce xử lý ở **component** (`ChatbotToolbar.jsx`):
- `localSearch` state: cập nhật ngay khi user gõ
- `useEffect([localSearch])`: gọi `store.setSearch(localSearch)` sau 300ms (clearTimeout trên cleanup)
- `store.filters.search` chỉ thay đổi sau 300ms → trigger fetch qua `ChatbotsPage.jsx` effect

### Pagination 0-based vs 1-based

- Store/API: **0-based** (`page: 0`)
- Hiển thị: **1-based** (`displayPage = page + 1`, "1 / 3")
- "Showing 1–10 of 28" — tính từ `page * size + 1`

---

## 6. Component design

### ChatbotsPage

```
ChatbotsPage
├── State: showCreateModal (local)
├── Store subscriptions: items, loading, error, search, status, domain, page, size, total, totalPages
├── useEffect([fetchChatbots, search, status, domain, page, size]) — fetch trigger
├── useEffect([setRightSlot]) — inject "+ New chatbot" button
├── useEffect([clearRightSlot]) — cleanup unmount
├── handleCreated() — gọi fetchChatbots() sau khi tạo thành công
└── Render:
    ├── ChatbotToolbar
    ├── ChatbotTable
    ├── Pagination
    └── CreateChatbotModal
```

### ChatbotToolbar

- `localSearch` state (React): update ngay khi gõ
- `useEffect([localSearch, setSearch])`: debounce 300ms → `store.setSearch`
- `useEffect([filters.search])`: sync localSearch khi store reset
- Select status: gọi `setStatus` trực tiếp (không debounce)
- Select domain: gọi `setDomain` trực tiếp (không debounce)
- Clear button: chỉ hiện khi `hasFilters = localSearch || filters.status || filters.domain`

### ChatbotTable

- `loading` → `<SkeletonLoader variant="table" count={5} />`
- `error` → error card + retry button
- `items.length === 0` → `<EmptyState icon="🤖" ...>`
- Avatar: `initials` field + color scheme 6 màu dựa trên `charCodeAt(0) % 6`
- StatusBadge: map `status.toUpperCase() === "ACTIVE"` → "active" | "inactive"
- Config button: `useNavigate()` → `/chatbots/${bot.id}/config`
- Table: `min-w-[720px]` + `overflow-x-auto` cho mobile

### CreateChatbotModal

- Dùng `Modal` shared component (`isOpen`, `onClose`, `title="New Chatbot"`)
- Fields: name (required), description (optional), domain (required)
- Validation trước submit, `fieldErrors` state per field
- Submit: gọi `store.createChatbot(payload)` → toast.success + onClose + onCreated
- Error: `submitError` state → inline error block trong form
- Close guard: `if (creating) return` để tránh đóng giữa chừng

### Pagination

- Ẩn hoàn toàn nếu `totalPages <= 1 && total <= size`
- "← Prev" disabled khi `page <= 0`
- "Next →" disabled khi `page >= totalPages - 1`
- Gọi `onPageChange(page ± 1)` → store `setPage`
- Info: "Showing {from}–{to} of {total} chatbots"

---

## 7. API integration

| Function | Endpoint | Params | Gọi khi nào |
|---|---|---|---|
| `chatbotsApi.getChatbots` | `GET /api/chatbots?...` | `{search, status, domain, page, size}` | `fetchChatbots()` trong store |
| `chatbotsApi.createChatbot` | `POST /api/chatbots` | `{name, description, domain}` | `createChatbot(payload)` trong store |

**Sau create refresh list**: `CreateChatbotModal` gọi `onCreated()` callback → `ChatbotsPage.handleCreated()` → `fetchChatbots()`. Không navigate, không reset filters.

**Error/loading**:
- `loading=true` trong store từ `fetchChatbots` bắt đầu đến khi resolve
- `creating=true` trong store từ `createChatbot` bắt đầu đến khi resolve
- Error từ `fetchChatbots` → `store.error` → hiển thị trong `ChatbotTable`
- Error từ `createChatbot` → re-throw → `CreateChatbotModal.submitError` state (modal vẫn mở)

---

## 8. Data shape assumptions

### Chatbot item
```js
{
  id: string,             // "cb-001"
  name: string,           // "Customer Support Bot"
  description: string,    // optional
  domain: string,         // "support" | "faq" | ...
  documentCount: number,
  messageCount: number,
  status: "ACTIVE" | "INACTIVE" | "DELETED",
  updatedAt: string,      // ISO 8601
  initials: string,       // "CS" — 2 ký tự
  avatarUrl?: string,     // optional — không có trong mock
  systemPrompt: string,
  modelConfig: object,
}
```

### Pagination response
```js
{
  items: Array<chatbot>,
  page: number,       // 0-based
  size: number,
  total: number,
  totalPages: number,
}
```

### Status values
- `"ACTIVE"` → StatusBadge `active` (green)
- `"INACTIVE"` → StatusBadge `inactive` (gray)
- `"DELETED"` → bị filter out bởi mock/API, không bao giờ render

### Domain values
- Mock: support, faq, technical, sales, hr, product, legal, demo
- Populate vào `store.domains` khi `fetchChatbots()` chạy mà không có domain filter
- Domain dropdown options lấy từ `store.domains`

---

## 9. Responsive behavior

| Vùng | Mobile | Tablet | Desktop |
|---|---|---|---|
| Toolbar | Wrap xuống dòng (`flex-wrap`) | Partial wrap | Full row |
| Search input | `flex-1 min-w-[200px]` — full width nếu không có filter | Partial | Chiếm phần lớn width |
| Status/Domain select | Xuống dòng | Inline | Inline |
| ChatbotTable | Horizontal scroll (`overflow-x-auto + min-w-[720px]`) | Scroll nếu cần | Full table |
| Avatar | Có mặt trên mọi viewport | | |
| CreateChatbotModal | Full width (`max-w-md` + `p-4`) | Centered | Centered |
| Pagination | Flex: info left, controls right (wrap nếu quá hẹp) | | |

---

## 10. Cách test thủ công

### Setup
```bash
cd Frontend
npm run dev
```

### Login
- Vào `http://localhost:5173/login`
- Nhập bất kỳ credential, click Login
- Redirect → `/dashboard`, click "Chatbots" trong sidebar → `/chatbots`

### Kiểm tra initial load
- List hiển thị 7 chatbots (5 ACTIVE + 2 INACTIVE, DELETED bị lọc)
- Mock delay 350ms → skeleton table trước khi data load
- Domain dropdown có các options: support, faq, technical, sales, hr, product, legal

### Test search debounce
- Gõ "Support" vào search box
- Table không fetch ngay — chờ 300ms sau khi dừng gõ mới fetch
- Kết quả: "Customer Support Bot" + "Technical Support"

### Test status filter
- Chọn "Inactive" → chỉ hiện "Product Guide" + "Legal Assistant"
- Chọn "Active" → hiện 5 chatbots

### Test domain filter
- Chọn domain "support" → chỉ hiện "Customer Support Bot"
- Kết hợp status + domain: Inactive + hr → không có result → EmptyState

### Test pagination
- Mock có 7 items, size mặc định 10 → Pagination ẩn (1 trang)
- Test với size=3: thêm `setSize(3)` trong console hoặc tạm sửa store default
- Hoặc dùng search để reduce items

### Test create chatbot
- Click "+ New chatbot" ở Header
- Modal mở với form
- Submit form rỗng → validation errors name + domain
- Điền name="Test Bot", domain="test" → Click "Create Chatbot"
- Spinner "Creating…" trong 500ms (mock delay)
- Toast success: "Chatbot 'Test Bot' created successfully!"
- Modal đóng, list refresh, "Test Bot" xuất hiện cuối danh sách

### Test config navigation
- Click nút "Config" của "Customer Support Bot"
- Navigate → `/chatbots/cb-001/config`
- Page hiển thị ChatbotConfigPage placeholder

### Test loading skeleton
- Tạm tăng mockDelay trong `chatbotsApi.js` → thấy table skeleton rõ hơn

---

## 11. Kết quả command

| Command | Kết quả | Chi tiết |
|---|---|---|
| `cd Frontend; npm run lint` | **PASS ✅** | 0 lỗi, 0 warnings sau 2 lần fix |
| `cd Frontend; npm run build` | **PASS ✅** | 421 modules (tăng từ 416), 474.02KB (gzip 150.20KB), 12.78s |
| `npm run build:widget` | NOT RUN | Không thay đổi widget |
| `docker compose config` | NOT RUN | Không thay đổi infra |

**Ghi chú về lint fixes**:

**Fix 1** — `ChatbotsPage.jsx`: Comment `eslint-disable-next-line react-hooks/set-state-in-effect` không cần thiết vì `fetchChatbots` là Zustand action, gọi Zustand `set()` không phải React `setState`. ESLint báo "unused disable directive". Đã xóa comment.

**Fix 2** — `ChatbotToolbar.jsx`: `setLocalSearch("")` trong `useEffect` bị flag bởi `react-hooks/set-state-in-effect`. Đây là pattern hợp lý (sync local state từ external source khi store reset). Đã thêm `// eslint-disable-next-line react-hooks/set-state-in-effect` trước dòng gọi.

---

## 12. Lỗi hoặc giới hạn còn tồn tại

| Vấn đề | Mức độ | Mô tả |
|---|---|---|
| Domain dropdown chỉ populate sau fetch đầu | 🟢 Thấp | Nếu user reload trang, domain dropdown trống cho đến khi fetch đầu hoàn thành (350ms mock delay). Trong production, initial skeleton cũng hiển thị trong thời gian này nên trải nghiệm ổn. |
| `store.createError` không được dùng | 🟢 Thấp | Store có `createError` field nhưng `CreateChatbotModal` dùng `submitError` local state (để catch từ throw). Hai nguồn song song nhưng không conflict — modal xử lý qua local state là đúng pattern. |
| Pagination ẩn với 7 items + size=10 | 🟢 Thấp | Mock chỉ có 7 items nên pagination ẩn. Khi connect real API với nhiều data hơn, pagination sẽ hiện. Behavior đúng theo thiết kế. |
| `avatarUrl` không có trong mock | 🟢 Thấp | Mock không có `avatarUrl` field — component Avatar tự fallback về initials. Khi real API có avatarUrl, sẽ tự render. |
| Store không reset khi unmount page | 🟢 Thấp | `items`/`filters`/`domains` giữ nguyên khi navigate sang trang khác rồi quay lại. Đây là behavior mong muốn — user không mất kết quả filter/search. Nếu muốn fresh start mỗi lần mount, cần thêm `resetAll` trong `useEffect` cleanup — ngoài scope prompt này. |
| Không có "size" selector trong UI | 🟢 Thấp | Checklist không yêu cầu. Store có `setSize` action nhưng không có UI control. Có thể thêm ở Prompt sau nếu cần. |

---

## 13. Đề xuất prompt tiếp theo

**Build và lint PASS** → Chatbot Management hoàn chỉnh.

**Đề xuất Prompt 05 — Chatbot Config**:  
Implement `ChatbotConfigPage.jsx` tại route `/chatbots/:id/config` với:
- Load chatbot detail qua `chatbotsApi.getChatbot(id)`
- Hiển thị và edit: name, description, status, systemPrompt, modelConfig (model, temperature, maxTokens)
- Submit: `chatbotsApi.updateChatbot(id, payload)`
- Tab navigation: Config | Embed
- Inject "Save Changes" button vào Header rightSlot
- Breadcrumb: Chatbots → [Bot Name] → Config
- Loading skeleton khi đang load chatbot
- Toast success/error sau save
- Dùng shared Modal, StatusBadge, SkeletonLoader từ common components
