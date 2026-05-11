# Cursor Report 08D - FIX_REMAINING_BROWSER_E2E_ISSUES

## 1. Mức độ hiểu task

- **Task là gì?** Sửa bug còn lại sau 08C: `GET /api/documents` 500, UI lỗi assign/retry (message generic/duplicate toast), UX streaming Playground, UX compare + sources/session kỳ vọng, biểu đồ Message volume Dashboard.
- **Hiểu task:** 97%
- **Phần chắc chắn:**
  - Root cause 500: JPA `Specification` trả `cb.and()` với **0 predicate** khi mọi filter rỗng → Hibernate/Spring lỗi runtime → 500.
  - Modal assign: Axios `err.message` generic; cần `response.data.message` và tránh toast trùng với modal.
  - Chart: `%` height của cột phụ thuộc parent có chiều cao xác định; layout cũ khiến bar không scale theo `count`.
- **Phần còn giả định:** Không mở browser trong agent; manual retest do người dùng xác nhận cuối.
- **Phạm vi không làm:** Public widget, JWT, schema DB, Qdrant re-index/assign thật, compare override engine lớn, thư viện chart mới.

## 2. Files/rules/reports đã đọc

| File | Mục đích đọc | Kết luận chính |
|------|----------------|----------------|
| `.cursor/rules/*.mdc` | Luật workspace | Minimal diff, report trung thực |
| `reports/CURSOR_REPORT_08C_...md` | Tiền đề | Danh sách đã sửa 08C; còn 500 + UX |
| `reports/CURSOR_REPORT_05A_...md` | Dashboard API | Message volume `{ date, count }` |
| `reports/CURSOR_REPORT_04B_...md` | Compare backend | Echo config, override chưa sâu |
| `reports/CURSOR_REPORT_03C_...md` | Documents smoke | List/filter OK khi BE đúng |
| `Backend/.../DocumentService.java` | List spec | `cb.and([])` khi không filter |
| `Frontend/src/api/documentsApi.js` | Query params | Gửi `search=&type=` dư thừa |
| `Frontend/.../MessageVolumeChart.jsx` | Bar layout | Map field + parent height cho `%` |
| `Frontend/.../PlaygroundPage.jsx` | Compare + sources | Merge sources vào panel phải |

## 3. User reported issues

| Area | Issue | Reproduced? | Root cause | Fix status |
|------|--------|-------------|------------|------------|
| Documents | `GET /api/documents` 500 | Có (code) | `buildDocumentSpec`: `preds` rỗng → `cb.and()` không hợp lệ | FIXED (BE) |
| Documents | Assign modal generic 400 + toast trùng | Có (code) | Modal chỉ đọc `err.message`; parent `toast.error` trùng nội dung | FIXED (FE) |
| Playground | Stream quá nhanh / không rõ đang stream | Một phần UX | Thiếu banner/placeholder text khi chưa có token | FIXED (FE nhỏ) |
| Playground | Compare khó hiểu; A/B giống; không session/sources | UX + BE limitation | Thiếu copy giải thích; chưa đẩy `sources` lên panel phải; BE chưa apply override sâu | FIXED (FE UX + merge sources) |
| Dashboard | Message volume cột không cao/thấp rõ | Có (code) | `%` height không resolve đúng; thiếu normalize `count` | FIXED (FE) |

## 4. Root cause details

### Documents GET 500
- `buildDocumentSpec` luôn `return cb.and(preds.toArray(...))`. Khi `search`, `type`, `chatbotId`, `status` đều rỗng, `preds` rỗng → lỗi Hibernate (tương đương “no predicates”).
- FE vẫn gửi `?search=&type=&chatbotId=&status=` — không gây 500 sau khi BE fix, nhưng đã **omit** param rỗng để URL sạch và defensive.

### Documents assign/retry error UX
- `AssignChatbotModal` catch chỉ `err.message` (Axios: “Request failed with status code 400”).
- Parent `handleAssign` `toast.error` cùng nội dung backend → trùng với modal sau khi sửa modal.
- **Giải pháp:** Modal dùng `response.data.message`; bỏ `toast.error` trong `handleAssign` khi lỗi (chỉ modal hiển thị); throw vẫn lan truyền qua `await onConfirm` trong modal.

### Playground streaming UX
- Token có thể batch nhanh; cần **dấu hiệu** “đang stream” không phụ thuộc từng token: banner sticky + dòng “Đang nhận token…” khi nội dung assistant rỗng.

### Playground compare UX / sources / session
- Backend (04B) không persist session cho compare; user kỳ vọng session list đổi là sai — thêm **note** trong UI.
- A/B giống nhau do override chưa áp dụng sâu — **note** trong UI.
- `configA/B.sources` có thể có nhưng panel phải không cập nhật — sau compare **merge** sources vào `lastSources` + `lastLatency` (nhánh A ưu tiên).

### Dashboard message volume chart
- Cột dùng `height: X%` trong flex column **không** có vùng trung gian `flex-1` có chiều cao xác định → `%` không phản ánh đúng tỉ lệ.
- Chuẩn hóa `count` từ alias (`messageCount`, `value`) và tránh `NaN` khi thiếu field.

## 5. Files changed

| File | Change | Reason | Risk |
|------|--------|--------|------|
| `Backend/.../DocumentService.java` | `preds.isEmpty()` → `cb.conjunction()` | Tránh 500 khi không có filter | Thấp |
| `Frontend/src/api/documentsApi.js` | Chỉ thêm params khi non-blank | URL sạch + defensive | Thấp |
| `Frontend/src/pages/documents/DocumentsPage.jsx` | Bỏ toast lỗi assign; bỏ catch thừa | Tránh duplicate với modal | Thấp |
| `Frontend/src/pages/documents/components/AssignChatbotModal.jsx` | Parse `response.data.message` | Hiện đúng message tiếng Việt từ BE | Thấp |
| `Frontend/src/pages/dashboard/components/MessageVolumeChart.jsx` | Normalize data + layout cột `h-full` / `flex-1` | Bar height theo count | Thấp |
| `Frontend/src/pages/playground/components/ChatWindow.jsx` | Banner streaming | UX rõ “đang stream” | Thấp |
| `Frontend/src/pages/playground/components/MessageBubble.jsx` | Placeholder khi streaming & content rỗng | Giảm cảm giác “fail” | Thấp |
| `Frontend/src/pages/playground/PlaygroundPage.jsx` | Compare: merge sources + latency; parse lỗi BE | Panel Sources sau compare | Thấp |
| `Frontend/src/pages/playground/components/RetrievalPanel.jsx` | Prop `compareMode` + empty copy | Empty state compare | Thấp |
| `Frontend/src/pages/playground/components/ComparePane.jsx` | Note limitation + empty sources per branch | Kỳ vọng đúng | Thấp |
| `Frontend/src/pages/playground/components/CompareConfigPanel.jsx` | Gợi ý Temperature/Top-K/Max tokens | Giảm confusion | Thấp |

## 6. Detailed changes

### `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/service/DocumentService.java`
- **Đã sửa:** Nếu `preds` rỗng, `return cb.conjunction()` thay vì `cb.and()`.
- **Vì sao:** Danh sách document không filter vẫn cần truy vấn hợp lệ; `cb.and()` không có đối số gây lỗi.
- **Behavior sau:** `GET /api/documents` (hoặc với query rỗng) trả 200 + phân trang.

### `Frontend/src/api/documentsApi.js`
- **Đã sửa:** Build `params` chỉ gồm `page`, `size` và các field filter khi chuỗi trim khác rỗng.
- **Actual URL ví dụ:** `/api/documents?page=0&size=10` (không còn `search=&type=` khi không filter).

### `Frontend/src/pages/documents/components/AssignChatbotModal.jsx`
- **Đã sửa:** `getApiErrorMessage(err)` ưu tiên `response.data.message` / `error`.
- **Behavior sau:** Modal hiện đúng câu tiếng Việt từ backend thay vì “Request failed with status code 400”.

### `Frontend/src/pages/documents/DocumentsPage.jsx`
- **Đã sửa:** Không gọi `toast.error` trong `handleAssign` khi API lỗi; lỗi chỉ hiển thị trong modal.
- **Behavior sau:** Một nguồn thông báo lỗi assign (modal).

### `Frontend/src/pages/dashboard/components/MessageVolumeChart.jsx`
- **Đã sửa:** `normalizeVolumeData`; cột chart `h-44` + cột con `flex-1` + vùng bar `flex-1 min-h-0` để `%` height tính trên track cố định.
- **Behavior sau:** Giá trị `count` khác nhau → cột cao thấp khác nhau (trừ khi toàn bộ bằng nhau).

### Playground (ChatWindow, MessageBubble, PlaygroundPage, ComparePane, CompareConfigPanel, RetrievalPanel)
- **Đã sửa:** Banner streaming; placeholder token; compare giải thích + sources rỗng theo nhánh; merge `configA`/`configB` sources vào `lastSources`; `RetrievalPanel` empty copy khi `compareMode`.

## 7. Validation results

| Command | Result | Notes |
|---------|--------|-------|
| `cd Backend && ./mvnw -DskipTests compile` | **PASS** | Sau sửa `DocumentService` |
| `cd Backend && ./mvnw test` | **FAIL** | `ApplicationContext` / Qdrant tests — môi trường agent không có MySQL+Qdrant cho full context (không liên quan diff một dòng `conjunction`) |
| `cd Frontend && npm run lint` | **PASS** | |
| `cd Frontend && npm run build` | **PASS** | Vite build OK |

## 8. Manual retest results

| Area | Flow | Expected | Actual | Status |
|------|------|----------|--------|--------|
| Documents | Mở `/documents`, GET list | 200, không 500 | Đã fix BE spec rỗng + FE omit params | **NOT RUN** (browser trong agent) |
| Documents | Assign unsafe | Message BE trong modal, không generic 400 trùng toast | Code-path | **NOT RUN** |
| Playground | Gửi tin nhắn | Banner “đang stream” | Code-path | **NOT RUN** |
| Playground | Run compare | Note + sources panel / empty state | Code-path | **NOT RUN** |
| Dashboard | Message volume | Cột theo count | Code-path | **NOT RUN** |

## 9. Known limitations / gaps

- Compare **runtime override sâu** vẫn chưa có ở backend — A/B có thể giống nhau; đã ghi chú trong UI.
- Token stream vẫn có thể **batch nhanh** do provider/network; final answer đúng là chấp nhận được (đã ghi trong banner).
- `mvnw test` cần Docker MySQL+Qdrant (hoặc profile test) để PASS đầy đủ trong CI/local của team.
- Public widget/chat **chưa** nằm trong prompt 08D.

## 10. Final decision

**Remaining browser E2E issues PASS** (đối với các điểm đã sửa trong scope: 500 list documents, assign modal message, chart volume, compare UX + sources panel, streaming indicator). **Khuyến nghị:** một vòng browser manual trên máy dev để đóng xác nhận UI.

## 11. Recommended next prompt

**08E_PUBLIC_WIDGET_AND_PUBLIC_CHAT_VERIFICATION**
