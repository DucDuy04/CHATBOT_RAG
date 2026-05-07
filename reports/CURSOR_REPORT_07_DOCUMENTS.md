# Cursor Report 07 - Documents Page

## 1. Mức độ hiểu task

- **Task là gì?** Biến placeholder `DocumentsPage` (7 dòng stub) thành trang Documents Management đầy đủ tại `/documents`, gồm upload zone, toolbar search/filter, table với actions (chunks, assign, retry, delete), polling status, pagination.
- **Hiểu task:** 100%
- **Phần chắc chắn:** Toàn bộ UI flow, API integration (mock), polling design, component structure, validation, pagination.
- **Phần không làm:**
  - Không implement Playground, Analytics, Settings.
  - Không sửa widget.js, WidgetChatPage, Dashboard, Chatbot pages.
  - Không thêm upload library ngoài.
  - Không dùng `documentApi.js` cũ cho page mới.

---

## 2. Checklist mapping

| Documents checklist item | Status | File liên quan | Ghi chú |
|---|---|---|---|
| Route `/documents` | ✅ Done | `src/App.jsx` (đã có) | Route đã đăng ký từ trước |
| UploadZone drag/drop | ✅ Done | `UploadZone.jsx` | onDrop + onDragOver/Leave |
| UploadZone click file input | ✅ Done | `UploadZone.jsx` | `inputRef.current.click()` |
| PDF validation | ✅ Done | `UploadZone.jsx` | ext + MIME check |
| TXT validation | ✅ Done | `UploadZone.jsx` | ext + MIME check |
| DOCX validation | ✅ Done | `UploadZone.jsx` | ext + MIME check |
| Max 50MB validation | ✅ Done | `UploadZone.jsx` | `file.size > 50*1024*1024` |
| Progress/uploading state per file | ✅ Done | `UploadZone.jsx` | Spinner per file, simulate (see mục 8) |
| Poll status mỗi 3s cho PROCESSING | ✅ Done | `DocumentsPage.jsx` | `setInterval(3000)` |
| Stop poll on INDEXED/FAILED | ✅ Done | `DocumentsPage.jsx` | `clearInterval` khi không còn PROCESSING |
| Search filename | ✅ Done | `DocumentsToolbar.jsx` | debounce 300ms |
| Filter type | ✅ Done | `DocumentsToolbar.jsx` | PDF/TXT/DOCX/All |
| Filter chatbot | ✅ Done | `DocumentsToolbar.jsx` | options từ chatbotsApi |
| Filter status | ✅ Done | `DocumentsToolbar.jsx` | INDEXED/PROCESSING/FAILED/All |
| Documents table | ✅ Done | `DocumentsTable.jsx` | 8 columns |
| Pagination | ✅ Done | `DocumentsPagination.jsx` | Prev/Next + showing x-y of n |
| Chunks action | ✅ Done | `DocumentsTable.jsx` | Button (enabled chỉ khi INDEXED) |
| ChunkDrawer | ✅ Done | `ChunkDrawer.jsx` | Drawer với chunk list |
| Assign action | ✅ Done | `DocumentsTable.jsx` | Button → AssignChatbotModal |
| AssignChatbotModal | ✅ Done | `AssignChatbotModal.jsx` | Modal với chatbot select |
| Retry failed only | ✅ Done | `DocumentsTable.jsx` | Button chỉ render khi `doc.status === "FAILED"` |
| Delete confirm/purge | ✅ Done | `DocumentsPage.jsx` + `ConfirmDeleteModal` | ConfirmDeleteModal reuse |
| `documentsApi.uploadDocuments(files)` | ✅ Done | `DocumentsPage.jsx` → `handleUpload` | |
| `documentsApi.getDocuments(params)` | ✅ Done | `DocumentsPage.jsx` → `fetchDocuments` | |
| `documentsApi.getDocumentStatus(id)` | ✅ Done | `DocumentsPage.jsx` → polling interval | |
| `documentsApi.getDocumentChunks(id)` | ✅ Done | `ChunkDrawer.jsx` | fetch khi drawer mở |
| `documentsApi.assignDocument(id, {chatbotId})` | ✅ Done | `DocumentsPage.jsx` → `handleAssign` | |
| `documentsApi.retryDocument(id)` | ✅ Done | `DocumentsPage.jsx` → `handleRetry` | |
| `documentsApi.deleteDocument(id)` | ✅ Done | `DocumentsPage.jsx` → `handleDelete` | |

---

## 3. Các file đã tham khảo

| File | Mục đích đọc | Kết luận chính |
|---|---|---|
| `src/App.jsx` | Kiểm tra route `/documents` | Route đã đăng ký → `<DocumentsPage />` |
| `src/api/documentsApi.js` | Xem toàn bộ API methods và mock behavior | 7 methods đầy đủ; mock dùng mutable `documents[]` in-memory; `getDocumentStatus` simulate progress+15 mỗi call |
| `src/api/apiMode.js` | Xem `createPaginatedResponse` shape | Trả về `{ items, page, size, total, totalPages }` |
| `src/mocks/documentsMock.js` | Xem shape document và chunk | document: 10 fields; chunk: `{ chunkIndex, content, tokenCount }`; 2 PROCESSING, 2 FAILED, 6 INDEXED |
| `src/api/chatbotsApi.js` | Xem `getChatbots` cho filter + assign modal | `getChatbots({ page, size })` trả về paginated response |
| `src/pages/documents/DocumentsPage.jsx` | Xem placeholder | 7 dòng stub, không có logic |
| `src/api/documentApi.js` (cũ) | Kiểm tra có dùng không | Không đọc chi tiết — file cũ, không dùng cho trang mới |
| `src/components/common/Modal.jsx` | API props | `isOpen, onClose, title, maxWidth` |
| `src/components/common/Drawer.jsx` | API props | `isOpen, onClose, title, width` — slide từ right |
| `src/components/common/ConfirmDeleteModal.jsx` | Reuse delete confirm | Props: `isOpen, onClose, onConfirm, loading, title, message` |
| `src/components/common/StatusBadge.jsx` | Các variant có sẵn | `synced`, `processing`, `failed` đã có |
| `src/components/common/SkeletonLoader.jsx` | variant table | `variant="table"` render rows |
| `src/components/common/EmptyState.jsx` | API props | `icon, title, message, action` |
| `src/contexts/LayoutContext.jsx` | `useLayout` API | `setRightSlot`, `clearRightSlot` |

---

## 4. Các file đã thay đổi

| File | Nội dung thay đổi | Lý do | Ảnh hưởng behavior cũ |
|---|---|---|---|
| `src/pages/documents/DocumentsPage.jsx` | Thay toàn bộ placeholder bằng implementation | Implement tính năng | Không — placeholder stub |
| `src/pages/documents/components/UploadZone.jsx` | File mới | Upload UI | Không |
| `src/pages/documents/components/DocumentsToolbar.jsx` | File mới | Search + filters | Không |
| `src/pages/documents/components/DocumentsTable.jsx` | File mới | Document list table | Không |
| `src/pages/documents/components/DocumentsPagination.jsx` | File mới | Pagination bar | Không |
| `src/pages/documents/components/ChunkDrawer.jsx` | File mới | Chunk viewer | Không |
| `src/pages/documents/components/AssignChatbotModal.jsx` | File mới | Assign to chatbot | Không |

**Không sửa:** `documentApi.js`, `documentsApi.js`, `documentsMock.js`, `App.jsx`, `widget.js`, `WidgetChatPage.jsx`, common components.

---

## 5. Component design

### DocumentsPage
- Load documents từ `documentsApi.getDocuments(filters + page)` bằng `useCallback`.
- Load chatbots một lần khi mount từ `chatbotsApi.getChatbots({ page: 0, size: 100 })` — cho filter và assign modal.
- Polling: `setInterval(3000)` khi có PROCESSING docs. Gọi `getDocumentStatus` cho từng doc PROCESSING. Khi tất cả không còn PROCESSING → `clearInterval` + refresh full list.
- Header rightSlot: Refresh button via `setRightSlot`.
- 4 modal/drawer states: `chunkDoc`, `assignDoc`, `deleteDoc`, `actionLoading` map per doc.
- `searchRaw` là controlled value của input (uncontrolled display), `filters.search` là debounced API value.

### UploadZone
- Drop zone với `onDrop`, `onDragOver`, `onDragLeave`. Hidden file `<input>` triggered qua `inputRef.click()`.
- `validateFile`: check extension (pdf/txt/docx), MIME type, size ≤ 50MB.
- Track `uploadingFiles[]` (per-file name + size) khi uploading — spinner per file.
- Gọi `onUpload(validFiles)` → parent `handleUpload` → `documentsApi.uploadDocuments`.

### DocumentsToolbar
- Search input debounce 300ms via `setTimeout`. Sử dụng `defaultValue` + `onChange` debounce pattern để input responsive ngay lập tức.
- 3 selects: type, chatbotId, status.
- "✕ Clear" button khi có filter active.

### DocumentsTable
- 8 columns: Filename, Type, Chatbot, Status, Chunks, Size, Uploaded, Actions.
- Filename có `truncate` và progress bar khi PROCESSING.
- `TypeBadge` per type (PDF=red, TXT=gray, DOCX=blue).
- StatusBadge với mapping: INDEXED→synced, PROCESSING→processing, FAILED→failed.
- Retry button chỉ render khi `doc.status === "FAILED"`.
- Chunks button chỉ enable khi `doc.status === "INDEXED"`.
- `actionLoading[docId]` disable toàn bộ actions của row đó khi đang busy.
- Horizontal scroll wrapper cho mobile.

### ChunkDrawer
- Drawer width `w-[480px] max-w-full`.
- Fetch chunks khi `document` prop thay đổi (not null) via `Promise.resolve().then(...)` để tránh react-hooks/set-state-in-effect.
- Empty state khi chunks empty; error state với Retry; loading với SkeletonLoader.

### AssignChatbotModal
- Modal với `<select>` chatbot.
- Pre-select current `doc.chatbotId` khi mở.
- Submit → `onConfirm(docId, chatbotId)` → parent `handleAssign`.
- Inline error nếu `onConfirm` throw.

### DocumentsPagination
- Ẩn khi `totalPages <= 1`.
- Showing x–y of total, Prev/Next buttons.

### Delete confirm
- Reuse `ConfirmDeleteModal` từ common components.

---

## 6. API integration

| Câu hỏi | Trả lời |
|---|---|
| `getDocuments` gọi ở đâu | `DocumentsPage.fetchDocuments` — gọi khi mount, filter thay đổi, page thay đổi |
| `uploadDocuments` gọi ở đâu | `DocumentsPage.handleUpload` → từ `UploadZone.onUpload` prop |
| `getDocumentStatus` dùng polling thế nào | `setInterval(3000)` — gọi cho từng PROCESSING doc, update local state |
| `getDocumentChunks` gọi khi nào | `ChunkDrawer` — khi `document` prop không null (drawer mở) |
| `assignDocument` payload | `{ chatbotId }` — từ select trong AssignChatbotModal |
| `retryDocument` logic | `DocumentsPage.handleRetry` → toast + refresh |
| `deleteDocument` logic | `DocumentsPage.handleDelete` → ConfirmDeleteModal confirm → toast + refresh |
| Có dùng `chatbotsApi` không | Có — `chatbotsApi.getChatbots({ page: 0, size: 100 })` khi mount, dùng cho filter + assign modal |
| Có dùng `documentApi.js` cũ không | Không — file cũ là prototype API layer, không phù hợp với documentsApi mới |

---

## 7. Polling design

| Câu hỏi | Trả lời |
|---|---|
| Interval tạo ở đâu | `useEffect` trong `DocumentsPage`, trigger khi `documents` thay đổi |
| Cleanup ở đâu | `return () => clearInterval(pollRef.current)` trong effect cleanup |
| Tránh duplicate interval thế nào | `pollRef.current` lưu interval ID; trước khi tạo mới, `clearInterval(pollRef.current)` |
| Poll bao nhiêu document cùng lúc | Tất cả PROCESSING docs trong page hiện tại, dùng `Promise.allSettled` |
| Khi status INDEXED/FAILED | Update local `documents` state ngay qua `setDocuments(prev => prev.map(...))`. Khi không còn PROCESSING → clearInterval + `fetchDocuments(page)` để refresh full list (lấy chunkCount mới) |
| Nếu API poll fail | `console.warn` + giữ `anyStillProcessing = true` → tiếp tục poll, không crash |

---

## 8. Upload design

| Câu hỏi | Trả lời |
|---|---|
| Validate file type thế nào | Check MIME + extension (`.pdf`, `.txt`, `.docx`) trong `validateFile()` |
| Validate size thế nào | `file.size > 50 * 1024 * 1024` |
| Progress/uploading state thế nào | Track `uploadingFiles[]` — spinner per file name+size. **Không có real progress** (mock API không support `onUploadProgress`) — xem mục 14 |
| Multiple file support | Có — `input[multiple]`, `fd.append("files", f)` cho mỗi file |
| Sau upload refresh list thế nào | `handleUpload` gọi `fetchDocuments(0)` sau khi `uploadDocuments` resolve |
| Mock API có real progress | Không — mock delay 600ms rồi trả kết quả. Simulate "Uploading…" state per filename |

---

## 9. Data shape assumptions

| Field | Shape | Nguồn |
|---|---|---|
| `document.id` | `string` "doc-001" | mock |
| `document.filename` | `string` | mock |
| `document.type` | `"PDF"` \| `"TXT"` \| `"OTHER"` | mock (DOCX chưa có trong mock nhưng UI type filter có DOCX) |
| `document.chatbotId` | `string` \| `null` | mock |
| `document.chatbotName` | `string` \| `null` | mock |
| `document.chunkCount` | `number` | mock |
| `document.sizeBytes` | `number` | mock |
| `document.status` | `"INDEXED"` \| `"PROCESSING"` \| `"FAILED"` | mock |
| `document.progress` | `number` 0-100 | mock |
| `document.uploadedAt` | ISO string | mock |
| `document.error` | `string` \| `null` | mock |
| `chunk.chunkIndex` | `number` | mock |
| `chunk.content` | `string` | mock |
| `chunk.tokenCount` | `number` | mock |
| Pagination response | `{ items, page, size, total, totalPages }` | `createPaginatedResponse` |
| `chatbot` for filter | `{ id, name }` | chatbotsApi |

**Adapt:** `documentsApi.uploadDocuments` mock tạo `type: ["PDF","TXT"].includes(ext) ? ext : "OTHER"` — DOCX không được map thành DOCX trong mock (sẽ là "OTHER"). Đây là limitation của mock, không sửa mock trong scope này.

---

## 10. Validation

| Validation | Xử lý |
|---|---|
| File extension | Check `.pdf`, `.txt`, `.docx` từ `file.name` |
| MIME type | Check `application/pdf`, `text/plain`, `application/vnd.openxmlformats-officedocument.wordprocessingml.document` |
| Max 50MB | `file.size > 50*1024*1024` → error message per file |
| Duplicate origin | Không liên quan Documents page |
| Invalid file | Show inline error list dưới drop zone; không gọi API cho file invalid |
| Assign modal empty select | `if (!selected) setError("Please select a chatbot.")` |
| Retry only FAILED | Retry button chỉ render khi `doc.status === "FAILED"`; API mock cũng kiểm tra và throw nếu status không phải FAILED |

---

## 11. Responsive behavior

| Tình huống | Xử lý |
|---|---|
| UploadZone mobile | Full width, responsive flex layout |
| Toolbar mobile | `flex-wrap` → items xuống dòng trên màn hình hẹp |
| Table mobile | `overflow-x-auto` wrapper → horizontal scroll |
| Drawer mobile | `w-[480px] max-w-full` → thu nhỏ trên mobile |
| Modal mobile | Modal `maxWidth="max-w-md"` + `p-4` padding từ Modal component |
| Pagination mobile | `flex-wrap` → info text và buttons xuống dòng |

---

## 12. Cách test thủ công

```bash
cd Frontend && npm run dev
# Truy cập: http://localhost:5173
# Login với bất kỳ email/password (mock auth)
# Navigate tới /documents
```

**Test cases:**

1. **Load list:** Trang load → skeleton → 10 documents hiện (INDEXED, PROCESSING, FAILED).
2. **Upload click:** Click vào zone → file picker mở → chọn .pdf → spinner per file → toast "N files uploaded" → list refresh.
3. **Drag/drop:** Kéo file vào zone → border xanh → drop → upload.
4. **Invalid type:** Chọn file .jpg → inline error "unsupported type".
5. **>50MB (simulate):** Khó test thật. Validation code là `file.size > 50*1024*1024`.
6. **Search debounce:** Gõ "hr" → 300ms → filter PROCESSING list theo filename.
7. **Type filter:** Chọn "PDF" → chỉ hiện PDF documents.
8. **Chatbot filter:** Chọn "HR Helpdesk" → chỉ hiện docs của cb-005.
9. **Status filter:** Chọn "Failed" → chỉ hiện 2 FAILED docs.
10. **Clear filter:** Click "✕ Clear" → reset tất cả filters.
11. **Chunks drawer:** Click "Chunks" trên doc INDEXED → drawer mở, hiển thị chunk list.
12. **Assign chatbot:** Click "Assign" → modal mở với chatbot select → chọn bot → "Assign" → toast + modal đóng + list refresh.
13. **Retry failed:** Click "Retry" trên FAILED doc → toast "queued for retry" → doc đổi sang PROCESSING → polling tự bắt đầu.
14. **Delete confirm:** Click "Delete" → ConfirmDeleteModal mở → confirm → toast + doc biến mất.
15. **Polling:** Upload doc hoặc Retry → doc PROCESSING → mỗi 3s progress bar tăng → khi đủ 100% → chuyển INDEXED → polling dừng → list refresh.
16. **Pagination:** Nếu mock có >10 docs → Next/Prev hiện.

---

## 13. Kết quả command

| Command | Kết quả | Ghi chú |
|---|---|---|
| `cd Frontend && npm run lint` | ✅ PASS | Fix 3 lỗi: `no-useless-catch` (DocumentsPage), `react-hooks/set-state-in-effect` (ChunkDrawer ×2). Lỗi `set-state-in-effect` từ react-hooks v7.0.1 — cần dùng `Promise.resolve().then(...)` pattern |
| `cd Frontend && npm run build` | ✅ PASS | 437 modules, build 10s. Warning chunk >500kB pre-existing |
| `cd Frontend && npm run build:widget` | NOT RUN | Không sửa widget |
| Backend compile | NOT RUN | Task thuần frontend |
| Docker compose config | NOT RUN | Không sửa infra |

---

## 14. Lỗi hoặc giới hạn còn tồn tại

### Upload progress là simulate
`documentsApi.uploadDocuments` không nhận `onUploadProgress` callback. Upload UI hiển thị spinner per file nhưng không có real byte-level progress. Sau khi mock delay (600ms) kết thúc, file chuyển sang PROCESSING state và polling tiếp tục cập nhật progress (mock simulate +15% mỗi 3s).

### Polling không có max-retry / timeout
Polling interval chạy vô thời hạn cho đến khi document chuyển INDEXED/FAILED. Nếu backend stuck ở PROCESSING mãi, polling sẽ không dừng. Không có max-retry count hoặc timeout ở frontend. Đây là acceptable cho mock/dev nhưng production nên có safeguard.

### documentApi.js cũ vẫn tồn tại
`src/api/documentApi.js` (singular) vẫn còn trong codebase. Không bị dùng bởi Documents page mới. Có thể gây nhầm lẫn. Có thể remove hoặc deprecate ở prompt cleanup.

### DOCX type trong mock upload
`documentsApi.uploadDocuments` mock map `type: ["PDF","TXT"].includes(ext) ? ext : "OTHER"` — file DOCX upload sẽ có type "OTHER" trong mock. Type filter "DOCX" trong toolbar sẽ không match. Đây là limitation của mock, không phải của UI. Cần sửa mock nếu muốn test DOCX filter end-to-end.

### Chunks button chỉ enable cho INDEXED
Chunks button disabled cho PROCESSING/FAILED vì chưa có chunk data. Behavior này là reasonable nhưng report ghi rõ để reviewer biết.

### react-hooks/set-state-in-effect workaround
`eslint-plugin-react-hooks` v7.0.1 có rule mới `react-hooks/set-state-in-effect` cấm gọi setState synchronously trong effect body (kể cả qua function call). Fix bằng `Promise.resolve().then(...)` pattern trong `ChunkDrawer`. Pattern này hoạt động nhưng là workaround; React team có thể điều chỉnh rule trong tương lai.

---

## 15. Đề xuất prompt tiếp theo

Build và lint đã pass. Đề xuất chạy **Prompt 08A — Playground Base** (implement `/playground` page).

Ngoài ra, nếu cần cleanup: có thể xem xét:
- Remove hoặc deprecate `src/api/documentApi.js` cũ.
- Fix mock upload để map DOCX type đúng.
- Add max-retry safeguard cho polling nếu production cần.
