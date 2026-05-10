# Cursor Report 03B - FE_DOCUMENT_UPLOAD_TENANT_CONTEXT

## 1. Mức độ hiểu task

- **Task là gì?** Bổ sung tenant context (`chatbotId`) vào multipart upload Documents phía Frontend để khớp Backend 03A (`POST /api/documents/upload`), có UI để user **chọn chatbot** trước khi upload, không gọi API khi chưa chọn, giữ mock mode hoạt động.
- **Hiểu task:** 95%
- **Phần chắc chắn:**
  - Backend yêu cầu `chatbotId` trong FormData; thiếu → HTTP 400 với `message` trong body.
  - `documentsApi.uploadDocuments` đã append `chatbotId` + `files` khi real API; mock resolve `chatbotName` từ `chatbotsMock`.
- **Phần còn giả định:**
  - Danh sách chatbot từ `chatbotsApi.getChatbots({ page: 0, size: 100 })` đủ cho dropdown upload (≤100 bot).
- **Phạm vi không làm:** Backend, widget build, refactor lớn Documents page, các API khác.

## 2. Files/rules/reports đã đọc

| File | Mục đích đọc | Kết luận chính |
|------|----------------|----------------|
| `.cursor/rules/00-core-working-rule.mdc` | Scope minimal | Giữ diff nhỏ |
| `.cursor/rules/90-report-verification-rule.mdc` | Report + verify | Bắt buộc docs/report chi tiết |
| `reports/CURSOR_REPORT_03A_BACKEND_DOCUMENTS_API_CONTRACT_ALIGNMENT_AND_SAFE_CANONICAL_ENDPOINTS.md` | Upload contract | FormData `chatbotId` + `files`; thiếu tenant → 400 |
| `Frontend/src/api/documentsApi.js` | Implementation | `uploadDocuments(files, chatbotId)`, mock + real FormData |
| `Frontend/src/pages/documents/DocumentsPage.jsx` | Wiring upload | State `uploadChatbotId`, `handleUpload` truyền `chatbotId` |
| `Frontend/src/pages/documents/components/UploadZone.jsx` | UX tenant | Dropdown + block zone khi chưa chọn |

*(Các report `07_DOCUMENTS`, `04_CHATBOT`, `02B` được tham chiếu theo prompt; contract chính đã đủ từ 03A + source FE.)*

## 3. Upload flow before/after

- **Trước khi sửa:** `documentsApi.uploadDocuments(files)` chỉ gửi file → Backend 400 khi bật real API.
- **Sau khi sửa:** User chọn chatbot trong dropdown **“Chọn chatbot để upload”** → `uploadDocuments(files, chatbotId)` → FormData có `chatbotId` và `files`.
- **User chọn chatbot ở đâu:** Dropdown ngay phía trên vùng drop/upload trên `/documents` (`UploadZone`).
- **Khi chưa chọn chatbot:** Zone upload bị vô hiệu hóa (không mở file picker / không xử lý drop); drop vẫn có thể toast cảnh báo; `handleUpload` và `handleFiles` không gọi API và toast: *“Chọn chatbot trước khi upload tài liệu.”*

## 4. Files changed

| File | Change | Reason | Risk |
|------|--------|--------|------|
| `Frontend/src/api/documentsApi.js` | `uploadDocuments(filesOrFormData, chatbotId)`; `buildUploadFormData`; mock gắn `chatbotId`/`chatbotName` | Khớp Backend tenant | Low — throw nếu thiếu id |
| `Frontend/src/pages/documents/DocumentsPage.jsx` | State `uploadChatbotId`; `handleUpload` kiểm tra + `uploadDocuments(..., id)`; toast lỗi từ `response.data.message` | Orchestration upload | Low |
| `Frontend/src/pages/documents/components/UploadZone.jsx` | Dropdown chatbot + `uploadBlocked`; toast khi drop không hợp lệ; disable zone khi chưa chọn | UX + không gọi API sớm | Low |

## 5. API request shape after change

**Real API**

- `POST /api/documents/upload`
- `Content-Type: multipart/form-data`

FormData fields:

- `chatbotId` — UUID string (tenant)
- `files` — một hoặc nhiều file (cùng field name `files`)

**Không gửi** `widgetId` trong flow này (ưu tiên `chatbotId` theo prompt).

## 6. Mock mode behavior

- **Mock upload còn hoạt động không?** Có — khi `USE_MOCK_API`, vẫn cần `chatbotId`; document mock có `chatbotId` và `chatbotName` nếu id khớp `../mocks/chatbotsMock`.
- **Có validate chatbotId giống real API không?** Có — thiếu/blank → `throw new Error("chatbotId is required for document upload")`.
- **Response mock có gắn chatbotId/chatbotName không?** Có — `chatbotId: cid`, `chatbotName` từ mock chatbots hoặc `null`.

## 7. Validation results

| Command | Result | Notes |
|---------|--------|--------|
| `cd Frontend; npm run lint` | PASS | eslint exit 0 |
| `cd Frontend; npm run build` | PASS | vite build ~14.7s, chunk size warning only |
| `npm run build:widget` | NOT RUN | Ngoài scope prompt |
| `docker compose config` | NOT RUN | Ngoài scope FE |
| Backend compile/test | NOT RUN | Prompt cấm sửa Backend |

## 8. Manual test results

| Test | Expected | Actual | Status |
|------|------------|--------|--------|
| Không chọn chatbot, thử upload | Không gọi POST upload; cảnh báo | Chưa chạy browser trong session | NOT RUN |
| Chọn chatbot + upload | FormData có `chatbotId` + `files` | Chưa verify network | NOT RUN |
| File invalid | Validation cũ | Giữ nguyên `validateFile` | NOT RUN (code review OK) |

## 9. Known limitations

- Dropdown chỉ load tối đa 100 chatbot (`size: 100`); org lớn hơn cần pagination/search sau này.
- `FormData.set("chatbotId", …)` khi caller truyền sẵn `FormData` — giả định môi trường browser hiện đại hỗ trợ `set`.

## 10. Recommended next prompt

Nên là:

`03C_BACKEND_DOCUMENTS_RUNTIME_SMOKE_TEST`

---

## Appendix — Diff tóm tắt (theo rule workspace)

### `Frontend/src/api/documentsApi.js`

- Thêm `normalizeFiles`, `buildUploadFormData`.
- `uploadDocuments(filesOrFormData, chatbotId)` bắt buộc `chatbotId`; mock/real đều dùng tenant.

### `Frontend/src/pages/documents/DocumentsPage.jsx`

- `uploadChatbotId` state; `handleUpload` gọi `documentsApi.uploadDocuments(files, uploadChatbotId.trim())`.
- Truyền `chatbots`, `selectedChatbotId`, `onChatbotChange` xuống `UploadZone`.

### `Frontend/src/pages/documents/components/UploadZone.jsx`

- Label + `<select>` “Chọn chatbot để upload” / placeholder “Select chatbot”.
- `uploadBlocked` khi chưa chọn → disable input + vùng drop; toast khi drop không hợp lệ.

### Full git diff (audit)

Xác nhận trong session (PowerShell, UTF-8):

`git diff -- Frontend/src/api/documentsApi.js Frontend/src/pages/documents/DocumentsPage.jsx Frontend/src/pages/documents/components/UploadZone.jsx`

Output khớp các thay đổi: thêm `chatbotId` vào FormData + mock; state `uploadChatbotId` + `UploadZone` dropdown và `uploadBlocked`.
