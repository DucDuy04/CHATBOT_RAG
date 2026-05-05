# Cursor Report 02 - API Layer + Mock Data

**Ngày**: 2026-05-05  
**Scope**: Tạo API layer tập trung + mock data contract cho toàn bộ frontend.

---

## 1. Mức độ hiểu task

**Task này là gì**:  
Xây dựng toàn bộ API layer (7 modules) + mock data (6 files) theo đúng endpoint checklist. Cho phép feature pages sau import và gọi API ngay mà không cần backend running. Mock mode mặc định bật trong dev.

**Phạm vi đã làm**:
- ✅ `src/api/apiMode.js` — config bật/tắt mock
- ✅ `src/mocks/chatbotsMock.js` — 8 chatbots + embed configs (mutable)
- ✅ `src/mocks/documentsMock.js` — 10 documents + chunks (mutable)
- ✅ `src/mocks/dashboardMock.js` — stats, chart data, top chatbots, activity
- ✅ `src/mocks/playgroundMock.js` — sessions, messages, streaming tokens
- ✅ `src/mocks/analyticsMock.js` — summary, daily, byChatbot, unanswered, sessions
- ✅ `src/mocks/settingsMock.js` — profile, apiKeys, team (mutable)
- ✅ `src/api/dashboardApi.js` — 4 functions
- ✅ `src/api/chatbotsApi.js` — 7 functions
- ✅ `src/api/documentsApi.js` — 7 functions
- ✅ `src/api/playgroundApi.js` — 5 functions (SSE streaming)
- ✅ `src/api/analyticsApi.js` — 7 functions
- ✅ `src/api/settingsApi.js` — 9 functions
- ✅ `src/api/publicChatApi.js` — 1 function (no-JWT)
- ✅ `src/api/index.js` — barrel export
- ✅ npm run lint: **PASS**
- ✅ npm run build: **PASS**

**Phạm vi không làm**:
- Không implement UI page thật
- Không sửa widget embed behavior (`widget.js`, `WidgetChatPage.jsx`)
- Không xóa prototype cũ (`documentApi.js`, `widgetApi.js`)
- Không thêm endpoint ngoài checklist

---

## 2. API mode design

| Điều kiện | Kết quả |
|---|---|
| `VITE_USE_MOCK_API=true` | Mock API |
| `VITE_USE_MOCK_API=false` | Real API |
| Không set + `DEV=true` (vite dev server) | Mock API (default dev) |
| Không set + `DEV=false` (production build) | Real API |

**Cách bật/tắt mock**:
- Dev: không cần làm gì — mock tự bật
- Muốn test real API trong dev: thêm `VITE_USE_MOCK_API=false` vào `.env.local`
- Production: không cần set — tự dùng real API

**mockDelay(ms = 300)**:  
`new Promise(resolve => setTimeout(resolve, ms))` — giả lập latency. Mỗi function trong mock có delay riêng (200ms–600ms) để page test loading state.

**createPaginatedResponse(items, page, size)**:  
Slice array đầy đủ → trả `{ items, page, size, total, totalPages }`. Page index 0-based. `totalPages` tối thiểu 1 (tránh chia 0).

---

## 3. Checklist endpoint mapping

| Module | Checklist endpoint | Function | File | Mock | Real API |
|---|---|---|---|---|---|
| Dashboard | `GET /api/dashboard/summary` | `getSummary()` | `dashboardApi.js` | ✅ | ✅ |
| Dashboard | `GET /api/dashboard/message-volume?days=7` | `getMessageVolume(days)` | `dashboardApi.js` | ✅ | ✅ |
| Dashboard | `GET /api/dashboard/top-chatbots?limit=5` | `getTopChatbots(limit)` | `dashboardApi.js` | ✅ | ✅ |
| Dashboard | `GET /api/dashboard/activity?limit=20` | `getActivity(limit)` | `dashboardApi.js` | ✅ | ✅ |
| Chatbots | `GET /api/chatbots?search=&status=&domain=&page=&size=` | `getChatbots(params)` | `chatbotsApi.js` | ✅ | ✅ |
| Chatbots | `POST /api/chatbots` | `createChatbot(payload)` | `chatbotsApi.js` | ✅ | ✅ |
| Chatbots | `GET /api/chatbots/:id` | `getChatbot(id)` | `chatbotsApi.js` | ✅ | ✅ |
| Chatbots | `PUT /api/chatbots/:id` | `updateChatbot(id, payload)` | `chatbotsApi.js` | ✅ | ✅ |
| Chatbots | `DELETE /api/chatbots/:id` | `deleteChatbot(id)` | `chatbotsApi.js` | ✅ (soft) | ✅ |
| Chatbots | `GET /api/chatbots/:id/embed-config` | `getEmbedConfig(id)` | `chatbotsApi.js` | ✅ | ✅ |
| Chatbots | `PUT /api/chatbots/:id/embed-config` | `updateEmbedConfig(id, payload)` | `chatbotsApi.js` | ✅ | ✅ |
| Documents | `POST /api/documents/upload` | `uploadDocuments(files)` | `documentsApi.js` | ✅ | ✅ |
| Documents | `GET /api/documents?search=&type=&chatbotId=&status=&page=&size=` | `getDocuments(params)` | `documentsApi.js` | ✅ | ✅ |
| Documents | `GET /api/documents/:id/status` | `getDocumentStatus(id)` | `documentsApi.js` | ✅ (progress sim.) | ✅ |
| Documents | `GET /api/documents/:id/chunks` | `getDocumentChunks(id)` | `documentsApi.js` | ✅ | ✅ |
| Documents | `POST /api/documents/:id/assign` | `assignDocument(id, {chatbotId})` | `documentsApi.js` | ✅ | ✅ |
| Documents | `POST /api/documents/:id/retry` | `retryDocument(id)` | `documentsApi.js` | ✅ | ✅ |
| Documents | `DELETE /api/documents/:id` | `deleteDocument(id)` | `documentsApi.js` | ✅ | ✅ |
| Playground | `POST /api/playground/chat` (SSE) | `chat(params)` | `playgroundApi.js` | ✅ (simulated) | ✅ (fetch SSE) |
| Playground | `POST /api/playground/compare` | `compare(params)` | `playgroundApi.js` | ✅ | ✅ |
| Playground | `GET /api/playground/sessions?chatbotId=` | `getSessions(chatbotId)` | `playgroundApi.js` | ✅ | ✅ |
| Playground | `DELETE /api/playground/sessions/:id` | `deleteSession(id)` | `playgroundApi.js` | ✅ | ✅ |
| Playground | `GET /api/playground/export/:sessionId` | `exportSession(sessionId)` | `playgroundApi.js` | ✅ (object) | ✅ (blob) |
| Analytics | `GET /api/analytics/summary?from=&to=&chatbotId=` | `getSummary(params)` | `analyticsApi.js` | ✅ | ✅ |
| Analytics | `GET /api/analytics/daily?from=&to=&chatbotId=` | `getDaily(params)` | `analyticsApi.js` | ✅ | ✅ |
| Analytics | `GET /api/analytics/by-chatbot?from=&to=` | `getByChatbot(params)` | `analyticsApi.js` | ✅ | ✅ |
| Analytics | `GET /api/analytics/unanswered?limit=10` | `getUnanswered(params)` | `analyticsApi.js` | ✅ | ✅ |
| Analytics | `GET /api/analytics/sessions?from=&to=&chatbotId=&rating=&page=` | `getSessions(params)` | `analyticsApi.js` | ✅ | ✅ |
| Analytics | `GET /api/analytics/sessions/:id/messages` | `getSessionMessages(id)` | `analyticsApi.js` | ✅ | ✅ |
| Analytics | `POST /api/chat/feedback` | `submitFeedback(payload)` | `analyticsApi.js` | ✅ | ✅ |
| Settings | `GET /api/settings/profile` | `getProfile()` | `settingsApi.js` | ✅ | ✅ |
| Settings | `PUT /api/settings/profile` | `updateProfile(payload)` | `settingsApi.js` | ✅ | ✅ |
| Settings | `GET /api/settings/api-keys` | `getApiKeys()` | `settingsApi.js` | ✅ | ✅ |
| Settings | `POST /api/settings/api-keys` | `generateApiKey({name})` | `settingsApi.js` | ✅ | ✅ |
| Settings | `DELETE /api/settings/api-keys/:id` | `deleteApiKey(id)` | `settingsApi.js` | ✅ | ✅ |
| Settings | `GET /api/settings/team` | `getTeam()` | `settingsApi.js` | ✅ | ✅ |
| Settings | `POST /api/settings/team/invite` | `inviteMember({email,role})` | `settingsApi.js` | ✅ | ✅ |
| Settings | `PUT /api/settings/team/:userId/role` | `updateMemberRole(userId, role)` | `settingsApi.js` | ✅ | ✅ |
| Settings | `DELETE /api/settings/team/:userId` | `removeMember(userId)` | `settingsApi.js` | ✅ | ✅ |
| Public | `POST /api/public/chat` | `publicChat({apiKey,message,sessionId})` | `publicChatApi.js` | ✅ | ✅ (fetch) |

**Tổng**: 40/40 endpoints ✅

---

## 4. Mock data đã tạo

### Chatbots (`chatbotsMock.js`)
- 8 chatbots: 5 ACTIVE, 2 INACTIVE, 1 DELETED
- Fields: id, name, description, domain, documentCount, messageCount, status, updatedAt, initials, systemPrompt, modelConfig
- embed config object cho từng chatbot (widgetColor, welcomeMessage, position, allowedOrigins)
- Mutation: `getChatbots` tự filter DELETED, CRUD operations mutate array trực tiếp

### Documents (`documentsMock.js`)
- 10 documents: 6 INDEXED, 2 PROCESSING (progress 35%, 60%), 2 FAILED
- Fields: id, filename, type, chatbotId, chatbotName, chunkCount, sizeBytes, status, progress, uploadedAt, error
- `chunksByDoc` cho 3 documents (5 chunks mỗi file)
- `getDocumentStatus` mock tăng progress +15% mỗi lần gọi → INDEXED khi 100%
- `retryDocument` chỉ cho phép trên FAILED documents

### Dashboard (`dashboardMock.js`)
- `dashboardSummary`: activeChatbots, messages7d, avgSatisfaction, documentCount + delta % cho mỗi metric
- `messageVolume7d`: 7 data points (date + count) cho chart
- `topChatbots`: 5 chatbots theo messageCount và satisfaction
- `activityEvents`: 10 events gần nhất (tạo bot, index doc, cập nhật, mời member, ...)

### Playground (`playgroundMock.js`)
- 3 sessions (2 cho cb-001, 1 cho cb-003) với `messagesBySession`
- Mỗi session có 2–6 messages (user + assistant) với sources và latency
- `mockStreamTokens`: array 24 tokens để simulate streaming
- `MOCK_SOURCES`: 2 source references cho mock answers

### Analytics (`analyticsMock.js`)
- `analyticsSummary`: totalMessages, uniqueSessions, avgSatisfaction, fallbackRate + deltas
- `dailyData`: 14 ngày gần nhất (date, messages, sessions)
- `byChatbot`: 5 chatbots với messageCount và share %
- `unansweredQuestions`: 7 questions chưa được trả lời tốt
- `analyticsSessions`: 10 sessions với rating 1–5
- `sessionMessages` cho as-001: 4 messages đầy đủ với sources

### Settings (`settingsMock.js`)
- `profile`: admin user, mutable (Object.assign)
- `apiKeys`: 3 keys (2 ACTIVE, 1 INACTIVE), mutable array
- `teamMembers`: 5 members (1 ADMIN, 2 ACTIVE, 2 PENDING), mutable array

---

## 5. Các file đã tham khảo

| File | Lý do đọc | Kết luận |
|---|---|---|
| `Frontend/src/api/axiosInstance.js` | Hiểu interface axiosInstance, interceptors | axiosInstance.get/post/put/delete trả axios response; phải `.data` để lấy payload; interceptor tự xử lý 401/toast |
| `Frontend/src/api/documentApi.js` | Kiểm tra API prototype cũ | Upload dùng `/api/documents/upload/${widgetConfigId}`, không conflict với `documentsApi.js` mới |
| `Frontend/src/api/widgetApi.js` | Kiểm tra API prototype cũ | `createWidget` → `/api/widgets` — riêng biệt, không conflict |
| `reports/CURSOR_REPORT_00_PROJECT_AUDIT.md` | Endpoint checklist từ audit | Confirm 40 endpoints cần map |

---

## 6. Các file đã thay đổi

| File | Nội dung thay đổi | Lý do | Ảnh hưởng behavior cũ |
|---|---|---|---|
| `src/api/apiMode.js` | **Tạo mới** | Config chung cho mock/real switch | Không |
| `src/mocks/chatbotsMock.js` | **Tạo mới** | Mock data chatbots | Không |
| `src/mocks/documentsMock.js` | **Tạo mới** | Mock data documents | Không |
| `src/mocks/dashboardMock.js` | **Tạo mới** | Mock data dashboard | Không |
| `src/mocks/playgroundMock.js` | **Tạo mới** | Mock data playground | Không |
| `src/mocks/analyticsMock.js` | **Tạo mới** | Mock data analytics | Không |
| `src/mocks/settingsMock.js` | **Tạo mới** | Mock data settings | Không |
| `src/api/dashboardApi.js` | **Tạo mới** | Dashboard API module | Không |
| `src/api/chatbotsApi.js` | **Tạo mới** | Chatbots API module | Không |
| `src/api/documentsApi.js` | **Tạo mới** | Documents API module (plural) | Không — khác file `documentApi.js` cũ (singular) |
| `src/api/playgroundApi.js` | **Tạo mới** | Playground API với SSE | Không |
| `src/api/analyticsApi.js` | **Tạo mới** | Analytics API module | Không |
| `src/api/settingsApi.js` | **Tạo mới** | Settings API module | Không |
| `src/api/publicChatApi.js` | **Tạo mới** | Public chat (no-JWT) | Không |
| `src/api/index.js` | **Tạo mới** | Barrel export | Không |

**Không sửa** file nào hiện có.

---

## 7. Compatibility với prototype cũ

| File | Bị sửa? | Ghi chú |
|---|---|---|
| `src/api/documentApi.js` | **Không** | File cũ (singular) vẫn còn nguyên. `documentsApi.js` mới (plural) là module riêng biệt hoàn toàn. |
| `src/api/widgetApi.js` | **Không** | File cũ vẫn còn nguyên. Không conflict. |
| `src/pages/ChatPage.jsx` | **Không** | Vẫn dùng `fetch` trực tiếp, không import API modules mới. |
| `src/pages/DocumentPage.jsx` | **Không** | Vẫn dùng `documentApi.js` cũ nếu được import. Không sửa. |
| `src/pages/WidgetChatPage.jsx` | **Không** | Không sửa. |
| `widget/widget.js` | **Không** | Không sửa. |

---

## 8. Public chat no-JWT handling

**`publicChatApi.js` dùng `fetch` trực tiếp** (không qua axiosInstance):

- Lý do: `axiosInstance` có request interceptor gắn `Authorization: Bearer <token>` từ localStorage. Public endpoint `/api/public/chat` dùng `x-api-key` header, KHÔNG cần Bearer JWT.
- Nếu dùng axiosInstance, JWT sẽ bị gắn thêm — backend có thể từ chối hoặc confuse.
- `fetch` cho phép kiểm soát headers hoàn toàn.

**Header được set**:
```js
headers: {
  "Content-Type": "application/json",
  "x-api-key": apiKey,   // explicit, không có Authorization
}
```

**Không sửa axiosInstance** — interceptor giữ nguyên cho tất cả private API.

**Tóm tắt flow**:
```
publicChatApi.publicChat({ apiKey, message, sessionId })
    ↓
fetch() trực tiếp (không qua axiosInstance)
    ↓
Header: "x-api-key": apiKey (không có Bearer)
    ↓
Backend /api/public/chat validate x-api-key
```

---

## 9. Cách test nhanh API layer

Trong browser DevTools console (sau khi import qua page):

### Dashboard
```js
import { dashboardApi } from "./src/api";
const summary = await dashboardApi.getSummary();
console.log(summary); // { activeChatbots: 5, messages7d: 1284, ... }
```

### Chatbots
```js
import { chatbotsApi } from "./src/api";
const result = await chatbotsApi.getChatbots({ search: "Support", page: 0, size: 5 });
console.log(result); // { items: [...], page: 0, total: 3, ... }
```

### Documents
```js
import { documentsApi } from "./src/api";
const docs = await documentsApi.getDocuments({ status: "INDEXED" });
console.log(docs.items.length); // 6

const status = await documentsApi.getDocumentStatus("doc-007");
console.log(status); // { status: "PROCESSING", progress: 50, ... }
// Gọi lại nhiều lần → progress tăng đến 100 → INDEXED
```

### Playground mock streaming
```js
import { playgroundApi } from "./src/api";
const controller = playgroundApi.chat({
  chatbotId: "cb-001",
  message: "Xin chào",
  sessionId: "test-sess",
  onToken: (t) => process.stdout.write(t),
  onDone:  (r) => console.log("\nDone:", r),
  onError: (e) => console.error(e),
});
// controller.abort() để hủy nếu cần
```

### Settings generateApiKey
```js
import { settingsApi } from "./src/api";
const result = await settingsApi.generateApiKey({ name: "My New Key" });
console.log(result.plainTextKey);  // "sk_live_abc12345..." — shown once
console.log(result.key.maskedKey); // "sk_live_****45..."
```

---

## 10. Kết quả command

| Command | Kết quả | Chi tiết |
|---|---|---|
| `cd Frontend; npm run lint` | **PASS ✅** | 0 lỗi |
| `cd Frontend; npm run build` | **PASS ✅** | 346 modules, 10.94s, 406.19KB (không đổi vì API modules chưa được import bởi page nào đang active) |
| `npm run build:widget` | NOT RUN | Không thay đổi widget |
| `docker compose config` | NOT RUN | Không thay đổi infra |

**Ghi chú về bundle size**: Build vẫn là 346 modules / 406KB vì các API modules mới chưa được import bởi bất kỳ component nào đang được route. Tree-shaking của Vite loại bỏ chúng. Khi feature pages được implement và import các API modules, bundle size sẽ tăng nhẹ.

---

## 11. Lỗi hoặc giới hạn còn tồn tại

| Vấn đề | Mức độ | Mô tả |
|---|---|---|
| Mock data reset sau reload | 🟢 Thấp | In-memory mutation (push/splice) không persist qua browser reload. Đúng theo thiết kế — mock chỉ dùng trong dev session. |
| `getDocumentStatus` timelapse phụ thuộc số lần gọi | 🟢 Thấp | Progress tăng 15% mỗi lần gọi (không theo thời gian thực). Page cần poll định kỳ để simulate chính xác. |
| `playgroundApi.exportSession` mock trả object thay vì Blob | 🟢 Thấp | Real API trả Blob để download file. Mock trả JSON object. Page cần handle cả 2 case. |
| `analyticsApi` filter theo `from`/`to` date chưa implement trong mock | 🟢 Thấp | Mock bỏ qua date range filter (`void from; void to`). Trả toàn bộ data. Đủ cho placeholder page. |
| `publicChatApi` không có Toast error tự động | 🟢 Thấp | Không qua axiosInstance interceptor nên không có global toast. Caller tự xử lý error. Đây là thiết kế đúng cho public endpoint. |
| `documentApi.js` cũ và `documentsApi.js` mới song song | 🟢 Thấp | Hai file tồn tại cùng lúc. `documentApi.js` cũ chỉ còn tham chiếu từ `DocumentPage.jsx` prototype. Không conflict vì khác tên. Sẽ dọn dẹp khi `DocumentsPage.jsx` thật được implement. |

---

## 12. Đề xuất prompt tiếp theo

**Build và lint PASS** → API layer sẵn sàng.

**Đề xuất Prompt 03 — Dashboard Page thật**:
Implement `DashboardPage.jsx` với dữ liệu thật từ `dashboardApi`:
```js
import { dashboardApi } from "../../api";
import { useLayout } from "../../contexts/LayoutContext";

export default function DashboardPage() {
  const { setRightSlot, clearRightSlot } = useLayout();
  // setRightSlot(<RefreshButton />) trong useEffect
  // dashboardApi.getSummary() → render summary cards
  // dashboardApi.getMessageVolume(7) → render chart
  // dashboardApi.getTopChatbots(5) → render top list
  // dashboardApi.getActivity(20) → render activity feed
}
```

Components cần:
- `SkeletonLoader` (đã có) cho loading state
- `EmptyState` (đã có) nếu không có data
- Simple chart (Tailwind CSS bar chart, không cần Chart.js nếu đơn giản)
- Activity event cards
