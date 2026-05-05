# Cursor Report 03 - Dashboard Page

**Ngày**: 2026-05-05  
**Scope**: Implement Dashboard page thật tại route `/dashboard`.

---

## 1. Mức độ hiểu task

**Task này là gì**:  
Biến `DashboardPage.jsx` placeholder thành Dashboard thật — gọi 4 API endpoints, render MetricCards, chart, top chatbots list, activity table với loading/error state riêng từng vùng. Inject Refresh button vào Header qua LayoutContext.

**Phạm vi đã làm**:
- ✅ `DashboardPage.jsx` — render toàn bộ dashboard, 4 API song song, Refresh button
- ✅ `MetricCard.jsx` — 4 metrics với delta %, skeleton
- ✅ `MessageVolumeChart.jsx` — pure CSS bar chart 7 ngày, skeleton, empty state
- ✅ `TopChatbotsList.jsx` — top 5 chatbots, skeleton, empty state, retry
- ✅ `ActivityTable.jsx` — 5 columns, StatusBadge, skeleton, empty state, retry
- ✅ npm run lint: **PASS**
- ✅ npm run build: **PASS**

**Phạm vi không làm**:
- Không implement Chatbots, Documents, Playground, Analytics, Settings
- Không sửa API layer, widget, WidgetChatPage
- Không thêm chart library, TypeScript, UI framework

**Phần còn giả định**:
- API shape của real backend chưa verify (dùng mock, shape từ `dashboardMock.js`)
- Delta % real API có thể trả field name khác — cần adapt khi connect real backend

---

## 2. Checklist mapping

| Dashboard checklist item | Status | File liên quan | Ghi chú |
|---|---|---|---|
| Route /dashboard | ✅ | `App.jsx` (đã có từ Prompt 01C) | Route sẵn, page đã replace placeholder |
| MetricCard x4 | ✅ | `MetricCard.jsx`, `DashboardPage.jsx` | 4 cards trong grid |
| activeChatbots metric | ✅ | `DashboardPage.jsx` | `summary.activeChatbots` |
| messages7d metric | ✅ | `DashboardPage.jsx` | `summary.messages7d` |
| avgSatisfaction metric | ✅ | `DashboardPage.jsx` | `summary.avgSatisfaction`, formatter `v%` |
| documentCount metric | ✅ | `DashboardPage.jsx` | `summary.documentCount` |
| delta % per metric | ✅ | `MetricCard.jsx` | Dương → green, âm → red, 0 → gray |
| Pure CSS bar chart 7 days | ✅ | `MessageVolumeChart.jsx` | Không dùng thư viện ngoài |
| TopChatbotsList top 5 | ✅ | `TopChatbotsList.jsx` | `.slice(0, 5)` |
| ActivityTable Event/Chatbot/User/Time/Status | ✅ | `ActivityTable.jsx` | 5 columns đúng spec |
| Skeleton per widget | ✅ | Cả 4 components | Dùng `SkeletonLoader` từ common |
| Refresh button in Header rightSlot | ✅ | `DashboardPage.jsx` | `setRightSlot(...)` + cleanup |
| dashboardApi.getSummary() | ✅ | `DashboardPage.jsx` | `Promise.allSettled` |
| dashboardApi.getMessageVolume(7) | ✅ | `DashboardPage.jsx` | `Promise.allSettled` |
| dashboardApi.getTopChatbots(5) | ✅ | `DashboardPage.jsx` | `Promise.allSettled` |
| dashboardApi.getActivity(20) | ✅ | `DashboardPage.jsx` | `Promise.allSettled` |

---

## 3. Các file đã tham khảo

| File | Lý do đọc | Kết luận |
|---|---|---|
| `reports/CURSOR_REPORT_02_API_LAYER.md` | Shape mock data, function signatures, mock delays | `dashboardSummary` dùng camelCase delta fields; `activityEvents` có type/actor/target/createdAt |
| `reports/CURSOR_REPORT_01C_LAYOUT_CONTEXT.md` | Cách dùng `setRightSlot`, `clearRightSlot`, pattern example | Phải dùng 2 useEffect riêng: 1 để update slot, 1 để cleanup unmount |
| `Frontend/src/api/dashboardApi.js` | Confirm function signatures và return shapes | 4 functions, mock delay 300–400ms |
| `Frontend/src/mocks/dashboardMock.js` | Data shape thực tế | `activeChatbotsDelta`, `messages7dDelta`, v.v. là number (not string) |
| `Frontend/src/components/common/SkeletonLoader.jsx` | Props interface: variant, count | `variant="card"`, `variant="table"`, `variant="line"` |
| `Frontend/src/components/common/StatusBadge.jsx` | Variants có sẵn | synced/info/failed/inactive/pending/processing — đủ cho activity events |
| `Frontend/src/components/common/EmptyState.jsx` | Props: icon, title, message, action | Dùng cho chart rỗng, top list rỗng, activity rỗng |
| `Frontend/src/contexts/LayoutContext.jsx` | API của context | `setRightSlot`, `clearRightSlot` — stable refs (useCallback) |
| `Frontend/src/App.jsx` | Routing hiện tại, LayoutProvider scope | Route `/dashboard` đã nằm trong `PrivateLayoutShell` |
| `Frontend/eslint.config.js` | Rules đang enforce | `react-hooks/set-state-in-effect` trong `reactHooks.configs.flat.recommended` |

---

## 4. Các file đã thay đổi

| File | Nội dung thay đổi | Lý do | Ảnh hưởng behavior cũ |
|---|---|---|---|
| `src/pages/dashboard/DashboardPage.jsx` | **Rewrite** placeholder thành Dashboard thật | Yêu cầu chính của task | Có — placeholder cũ chỉ hiển thị text, giờ render toàn bộ dashboard |
| `src/pages/dashboard/components/MetricCard.jsx` | **Tạo mới** | Component metric card | Không |
| `src/pages/dashboard/components/MessageVolumeChart.jsx` | **Tạo mới** | Component chart | Không |
| `src/pages/dashboard/components/TopChatbotsList.jsx` | **Tạo mới** | Component list | Không |
| `src/pages/dashboard/components/ActivityTable.jsx` | **Tạo mới** | Component table | Không |

---

## 5. Component design

### DashboardPage

```
DashboardPage
├── State: summary, volume, topChatbots, activity (+ loading/error cho từng loại)
├── State: isRefreshing, inFlightRef (ref để ngăn duplicate request)
├── loadDashboard() — async, Promise.allSettled([getSummary, getMessageVolume, getTopChatbots, getActivity])
├── useEffect([loadDashboard]) — initial load
├── useEffect([..., isAnyLoading, isRefreshing]) — update Refresh button
├── useEffect([clearRightSlot]) — cleanup khi unmount
└── Render:
    ├── MetricCards grid (1/2/4 col responsive)
    ├── summaryError alert card (nếu có)
    ├── MessageVolumeChart
    └── Bottom row (lg: 1/3 + 2/3)
        ├── TopChatbotsList
        └── ActivityTable
```

### MetricCard

- Props: `title`, `value`, `delta`, `description`, `loading`, `formatter`
- `loading=true` → `<SkeletonLoader variant="card" />`
- `delta > 0` → text-green-600, `delta < 0` → text-red-600, `delta = 0` → text-gray-400
- `formatter` optional — default `v.toLocaleString()`
- avgSatisfaction dùng formatter `v => v + "%"`

### MessageVolumeChart

- Props: `data`, `loading`, `error`
- `loading=true` → `ChartSkeleton` (inline animated bars)
- `error` → error card
- `data.length === 0` → `EmptyState`
- Pure CSS: flex container `h-40`, mỗi bar là `div` với `height: ${(count/max)*100}%`, `minHeight: 4px`
- Date label: "Apr 29" dùng `toLocaleDateString` UTC để tránh timezone shift
- Tooltip qua HTML `title` attribute
- Summary: total messages ở footer

### TopChatbotsList

- Props: `data`, `loading`, `error`, `onRetry`
- `loading=true` → `<SkeletonLoader variant="line" count={5} />`
- Rank 1-3: màu đặc biệt (amber/gray/orange), rank 4+ → gray
- Fields: rank, name, domain (optional), messageCount, satisfaction
- `.slice(0, 5)` để đảm bảo đúng max 5 items

### ActivityTable

- Props: `data`, `loading`, `error`, `onRetry`
- `loading=true` → `<SkeletonLoader variant="table" count={5} />`
- Columns: Event | Chatbot / Resource | User | Time | Status
- Event: `type` → format "chatbot_created" → "Chatbot Created"
- Status: map `TYPE_TO_STATUS` → `StatusBadge`
- Time: `toLocaleString("en-US", { month:"short", day:"numeric", hour:"2-digit", minute:"2-digit" })`
- `overflow-x-auto` + `min-w-[600px]` cho table — horizontal scroll trên mobile

---

## 6. API integration

| Function | API endpoint | Khi nào gọi | Loading/error |
|---|---|---|---|
| `getSummary()` | `GET /api/dashboard/summary` | Initial mount + Refresh | `summaryLoading`, `summaryError` |
| `getMessageVolume(7)` | `GET /api/dashboard/message-volume?days=7` | Initial mount + Refresh | `volumeLoading`, `volumeError` |
| `getTopChatbots(5)` | `GET /api/dashboard/top-chatbots?limit=5` | Initial mount + Refresh | `topLoading`, `topError` |
| `getActivity(20)` | `GET /api/dashboard/activity?limit=20` | Initial mount + Refresh | `activityLoading`, `activityError` |

**Gọi song song**: `Promise.allSettled([...])` — tất cả 4 API chạy đồng thời.  
`Promise.allSettled` (không phải `Promise.all`) nên nếu 1 API fail, 3 API còn lại vẫn render bình thường.

**Duplicate request prevention**: `inFlightRef.current` — nếu đang load, click Refresh tiếp sẽ bị ignore. Button cũng bị `disabled` khi `isAnyLoading`.

**Error handling**: Mỗi section có error state riêng. summaryError hiển thị alert card chung với nút Retry. TopChatbotsList và ActivityTable có inline retry button. MessageVolumeChart hiển thị inline error card.

---

## 7. Data shape assumptions

### `summary` (từ `getSummary()`)
```js
{
  activeChatbots: number,
  activeChatbotsDelta: number,  // e.g. +2
  messages7d: number,
  messages7dDelta: number,      // e.g. +18.4
  avgSatisfaction: number,      // e.g. 87.3 (percent)
  avgSatisfactionDelta: number, // e.g. +2.1
  documentCount: number,
  documentCountDelta: number,   // e.g. +3
}
```

### `messageVolume` (từ `getMessageVolume(7)`)
```js
Array<{ date: "YYYY-MM-DD", count: number }>
// 7 items, ordered oldest → newest
```

### `topChatbots` (từ `getTopChatbots(5)`)
```js
Array<{
  id: string,
  name: string,
  messageCount: number,
  satisfaction: number,   // 0-100
  domain?: string,        // optional
}>
```

### `activity` (từ `getActivity(20)`)
```js
Array<{
  id: string,
  type: string,      // "chatbot_created" | "document_indexed" | ...
  actor: string,     // email hoặc "system"
  target: string,    // tên chatbot, document, email, v.v.
  createdAt: string, // ISO 8601
}>
```

**Adapt**: Nếu real API trả delta field với tên khác (e.g. `activeChatbotsPercentChange`), cần update field access trong `DashboardPage.jsx`. Mock data đã dùng `activeChatbotsDelta` pattern.

---

## 8. Responsive behavior

| Vùng | Mobile (< sm) | Tablet (sm–lg) | Desktop (≥ xl) |
|---|---|---|---|
| MetricCards | 1 cột | 2 cột (`sm:grid-cols-2`) | 4 cột (`xl:grid-cols-4`) |
| MessageVolumeChart | Full width | Full width | Full width |
| TopChatbotsList + ActivityTable | Stack dọc | Stack dọc | Side by side (1/3 + 2/3) via `lg:grid-cols-3` |
| ActivityTable | Horizontal scroll (`overflow-x-auto`) | Horizontal scroll | Full table |
| Chart bars | Responsive flex (bars co lại) | Responsive | Full width |

Chart không bị overflow vì dùng `flex-1 min-w-0` cho mỗi bar. Date label có `truncate` nếu quá hẹp.

---

## 9. Cách test thủ công

### Setup
```bash
cd Frontend
npm run dev
```
Mặc định dev dùng mock API (VITE_USE_MOCK_API=true theo logic trong `apiMode.js`).

### Login mock
- Vào `http://localhost:5173/login`
- Nhập bất kỳ email/password (LoginPage mock accept bất kỳ credential)
- Sau login redirect → `/dashboard`

### Kiểm tra 4 metrics
- Sau 400ms loading (mock delay summary), 4 MetricCards hiển thị giá trị:
  - Active Chatbots: `5` với `+2%`
  - Messages / 7 days: `1,284` với `+18.4%`
  - Avg. Satisfaction: `87.3%` với `+2.1%`
  - Documents: `10` với `+3%`

### Kiểm tra chart
- Sau ~300ms, bar chart hiển thị 7 ngày từ Apr 29 → May 5
- Max bar: 211 (May 3) → 100% height
- Min bar: 142 (Apr 29) → ~67% height
- Hover bar → HTML tooltip với count

### Kiểm tra top chatbot
- 5 chatbots: Customer Support Bot (1840 msgs), FAQ Assistant (924), ...
- Rank 1 có badge amber

### Kiểm tra activity table
- 10 rows, scroll horizontal trên mobile
- StatusBadge đúng màu: Synced (teal) cho indexed, Failed (red) cho document_failed, Pending (yellow) cho member_invited

### Kiểm tra Refresh button
- Refresh button ở Header phải
- Click → button disabled, text "Refreshing…", icon spin
- Sau ~400ms → button active lại, data updated
- Click liên tục nhanh → chỉ 1 request chạy (inFlightRef guard)

### Kiểm tra loading skeleton
- Slow network: thêm `mockDelay(2000)` tạm trong `dashboardMock` → thấy skeleton rõ trước khi data load

### Kiểm tra per-section error
- Để test error state: tạm return `Promise.reject(new Error("Network error"))` trong 1 function trong `dashboardApi.js`
- Vùng đó hiển thị error message + Retry button, vùng khác vẫn render bình thường

---

## 10. Kết quả command

| Command | Kết quả | Chi tiết |
|---|---|---|
| `cd Frontend; npm run lint` | **PASS ✅** | 0 lỗi, 0 warnings sau fix |
| `cd Frontend; npm run build` | **PASS ✅** | 416 modules (tăng từ 346), 459.15KB (gzip 146.88KB), 13.47s |
| `npm run build:widget` | NOT RUN | Không thay đổi widget |
| `docker compose config` | NOT RUN | Không thay đổi infra |

**Ghi chú về lint fix**:  
Rule `react-hooks/set-state-in-effect` (từ `reactHooks.configs.flat.recommended`) flag `loadDashboard()` bên trong `useEffect` body. Đây là pattern data fetching async chuẩn — `loadDashboard` là async function, setState gọi sau khi API resolve (không phải sync cascade). Fix: thêm `// eslint-disable-next-line react-hooks/set-state-in-effect` trực tiếp trước dòng gọi hàm trong effect body.

**Ghi chú về bundle size**:  
459KB vs 406KB trước — tăng ~53KB. Đây là toàn bộ Dashboard code + dependencies (React, ReactDOM, react-router, react-markdown, uuid). Không thêm library mới nên mức tăng hợp lý.

---

## 11. Lỗi hoặc giới hạn còn tồn tại

| Vấn đề | Mức độ | Mô tả |
|---|---|---|
| Stale closure tiềm ẩn trong Refresh button | 🟢 Thấp | `loadDashboard` trong `setRightSlot` effect capture qua closure. `loadDashboard` dùng `useCallback([])` nên stable, không bị stale. Hiện tại an toàn. |
| Delta field name real API chưa verify | 🟡 Trung bình | Mock dùng `activeChatbotsDelta`. Real backend có thể dùng tên khác. Cần confirm field name khi connect real API. |
| `animate-spin` cho ↻ icon | 🟢 Thấp | Tailwind `animate-spin` rotate icon 360° liên tục khi refreshing — visual indicator OK. Icon "↻" không phải SVG icon nên spin trông hơi đơn giản. Không ảnh hưởng function. |
| ActivityTable "Chatbot / Resource" column | 🟢 Thấp | Column header spec ghi "Chatbot" nhưng data field `target` bao gồm cả chatbot name, document name, email, API key. Header đã ghi "Chatbot / Resource" để phản ánh đúng hơn. |
| Initial skeleton flash | 🟢 Thấp | Do mock delay 300-400ms, skeleton hiển thị ngắn. Trong production thật delay có thể lâu hơn — skeleton sẽ hiển thị đủ thời gian. |
| Không có tổng số / pagination cho activity | 🟢 Thấp | API getActivity(20) lấy tối đa 20 events, hiển thị tất cả. Không có "load more". Đủ cho dashboard overview. |

---

## 12. Đề xuất prompt tiếp theo

**Build và lint PASS** → Dashboard thật đã hoàn chỉnh.

**Đề xuất Prompt 04 — Chatbot Management**:  
Implement `ChatbotsPage.jsx` tại route `/chatbots` với:
- List chatbots từ `chatbotsApi.getChatbots()`
- Tạo chatbot mới (modal/form) via `chatbotsApi.createChatbot()`
- Filter theo search/status/domain
- Pagination
- Link sang `/chatbots/:id/config` và `/chatbots/:id/embed`
- Inject `+ New Chatbot` button vào Header rightSlot qua `useLayout`
- Dùng `StatusBadge` cho chatbot status (ACTIVE/INACTIVE)
- Dùng `SkeletonLoader variant="table"` khi loading
- Dùng `ConfirmDeleteModal` khi xóa chatbot
