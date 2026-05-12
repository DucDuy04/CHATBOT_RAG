# Cursor Report 03B — FE_DOCUMENT_UPLOAD_TENANT_CONTEXT

Báo cáo theo workspace rule 90; nội dung nghiệp vụ trùng `reports/CURSOR_REPORT_03B_FE_DOCUMENT_UPLOAD_TENANT_CONTEXT.md` (bổ sung mục cấu trúc bắt buộc).

## 1. Mức độ hiểu task

- **Hiểu task:** 95%
- **Phần chắc chắn:** Backend 03A bắt `chatbotId` (multipart) khi upload; FE trước đó chỉ gửi `files`.
- **Phần còn giả định:** `getChatbots({ size: 100 })` đủ cho dropdown.
- **Thiếu dữ kiện:** Không — contract lấy từ `documentsApi.js` + 03A report.

## 2. Tóm tắt yêu cầu

Gửi `chatbotId` trong FormData khi `POST /api/documents/upload`; user phải chọn chatbot (không auto-pick ẩn); chưa chọn thì không gọi API; mock tương thích; lint/build pass.

## 3. Hiện trạng trước khi sửa

- `uploadDocuments(files)` — FormData không có tenant → Backend 400.
- Trang Documents không có “upload target” rõ ràng.

## 4. Nguyên nhân gốc (từ source)

- `documentsApi.uploadDocuments` chỉ append `files` (trước khi sửa `toFormData`).

## 5. Chiến lược sửa đã chọn

- Tham số thứ hai `chatbotId`, `buildUploadFormData` append `chatbotId` rồi từng `files`.
- `DocumentsPage`: `uploadChatbotId` + guard + toast lỗi axios `response.data.message`.
- `UploadZone`: dropdown “Chọn chatbot để upload”, `uploadBlocked` tắt vùng upload + cảnh báo khi drop.

## 6. Danh sách file đã đọc

| File | Mục đích | Kết luận |
|------|----------|----------|
| `reports/CURSOR_REPORT_03A_*.md` | Contract upload | `chatbotId` + `files` |
| `Frontend/src/api/documentsApi.js` | Sửa API | FormData tenant |
| `Frontend/src/pages/documents/DocumentsPage.jsx` | Wire state | `uploadChatbotId` |
| `Frontend/src/pages/documents/components/UploadZone.jsx` | UI | Dropdown + block |
| `.cursor/rules/00-core*.mdc`, `90-report*.mdc` | Quy tắc | Scope + report |

## 7. Danh sách file đã sửa

| File | Sửa để | Lớp |
|------|--------|-----|
| `Frontend/src/api/documentsApi.js` | FormData + mock `chatbotId` | api |
| `Frontend/src/pages/documents/DocumentsPage.jsx` | Truyền `chatbotId` + guard | ui |
| `Frontend/src/pages/documents/components/UploadZone.jsx` | Chọn chatbot trước upload | ui |

## 8. Diff thay đổi của từng file

### `Frontend/src/api/documentsApi.js`

- **Cũ liên quan bug:** `toFormData` chỉ append `files`.
- **Sửa:** `buildUploadFormData` thêm `fd.append("chatbotId", …)`; `uploadDocuments(..., chatbotId)` throw nếu thiếu; mock gán `chatbotId`/`chatbotName`.
- **Vì sao:** Khớp tenant Backend.
- **Ảnh hưởng:** Real upload thành công khi đủ tenant; mock cần `chatbotId`.

```diff
+  uploadDocuments: async (filesOrFormData, chatbotId) => {
+    if (chatbotId == null || String(chatbotId).trim() === "") {
+      throw new Error("chatbotId is required for document upload");
+    }
+    const cid = String(chatbotId).trim();
+    ...
+  fd.append("chatbotId", String(chatbotId));
```

### `Frontend/src/pages/documents/DocumentsPage.jsx`

- **Cũ:** `uploadDocuments(files)`.
- **Sửa:** `uploadChatbotId` + `uploadDocuments(files, uploadChatbotId.trim())` + cảnh báo nếu rỗng; map lỗi từ `err.response.data.message`.
- **Ảnh hưởng:** UI control upload theo chatbot đã chọn.

```diff
+  const [uploadChatbotId, setUploadChatbotId] = useState("");
+  if (!uploadChatbotId || String(uploadChatbotId).trim() === "") {
+    toast.warning("Chọn chatbot trước khi upload tài liệu.");
+    return;
+  }
+  const uploaded = await documentsApi.uploadDocuments(files, uploadChatbotId.trim());
```

### `Frontend/src/pages/documents/components/UploadZone.jsx`

- **Cũ:** Chỉ `onUpload` + file validation.
- **Sửa:** Dropdown chatbot, `uploadBlocked`, toast, disable zone.
- **Ảnh hưởng:** Không gọi `onUpload` khi chưa chọn (input disabled + guard).

## 9. Ảnh hưởng sau sửa

- **Thay đổi:** Multipart upload gồm `chatbotId` + `files`; user thấy chatbot target trước khi upload.
- **Giữ nguyên:** Filter toolbar, bảng, polling, retry/delete/assign (không đổi logic trong prompt).
- **Điều kiện bật:** Upload chỉ khi đã chọn chatbot hợp lệ.
- **Fallback:** Lỗi network/400 hiển thị qua toast (message server nếu có).
- **Cost/latency:** Một field string thêm trong FormData — không đáng kể.
- **DB/Qdrant:** Không đổi dữ liệu từ FE (chỉ gửi đúng tenant).

## 10. Edge cases đã xem xét

- Thiếu `chatbotId` (client): guard + throw trong API.
- `chatbotId` blank/whitespace: trim + reject.
- Mock: thiếu id → throw giống ý real.
- Drop file khi chưa chọn: toast, không gọi API.
- `onChatbotChange` undefined: dùng optional chaining.

## 11. Kết quả kiểm tra

| Command | Kết quả | Ghi chú |
|---------|---------|--------|
| `cd Frontend; npm run lint` | PASS | |
| `cd Frontend; npm run build` | PASS | Cảnh báo chunk >500kB (có sẵn) |
| `npm run build:widget` | NOT RUN | Ngoài scope |
| `docker compose config` | NOT RUN | Ngoài scope |
| Backend compile | NOT RUN | Không sửa BE |

## 12. Rủi ro còn lại

- Trên 100 chatbot, dropdown có thể thiếu mục (giới hạn `size: 100`).
- Manual/browser tests chưa chạy trong session.

## 13. Đề xuất tiếp theo

- Prompt **`03C_BACKEND_DOCUMENTS_RUNTIME_SMOKE_TEST`** (theo user).
- Khi cần: paginate/search chatbot cho dropdown upload.
