# Fix Embed Page Layout Loop (14B)

## 1. Mức độ hiểu task
- Hiểu task: 100%.
- Chắc chắn: cần fix bug loop ở trang `Embed` gây khóa điều hướng và ném lỗi update depth.
- Không làm: backend/API/widget runtime/feature mới.

## 2. Tóm tắt yêu cầu
- Sửa vòng lặp runtime khi vào `/chatbots/:id/embed`.
- Đảm bảo có thể click lại tab `Config` và các page khác.

## 3. Hiện trạng trước khi sửa
- User thao tác sang tab `Embed` thì UI không cho click điều hướng.
- Console có `Maximum update depth exceeded`, stack trỏ về `LayoutContext`.

## 4. Nguyên nhân gốc xác nhận từ source
- Trong `ChatbotEmbedPage.jsx`, effect set `rightSlot` phụ thuộc `handleSave`.
- `handleSave` phụ thuộc `toast` từ `useToast()`.
- `useToast()` trả reference mới mỗi render -> `handleSave` đổi liên tục -> effect setRightSlot chạy lặp -> context update lặp.

## 5. Chiến lược sửa đã chọn
- Giữ minimal diff trong đúng file `ChatbotEmbedPage.jsx`.
- Dùng `toastRef` để gọi toast mà không đưa `toast` vào callback dependencies.
- Memo hóa node rightSlot bằng `useMemo`.
- Giữ cleanup `clearRightSlot` như cũ.

## 6. Danh sách file đã đọc
- `.cursor/rules/00-core-working-rule.mdc`: quy trình fix tối thiểu.
- `.cursor/rules/20-frontend-widget-rule.mdc`: tránh sửa ngoài scope frontend.
- `.cursor/rules/90-report-verification-rule.mdc`: yêu cầu report.
- `Frontend/src/pages/chatbots/ChatbotEmbedPage.jsx`: xác nhận vòng lặp dependency.
- `Frontend/src/contexts/LayoutContext.jsx`: xác nhận setter context bị gọi lặp từ page.
- `Frontend/src/components/common/useToast.js`: xác nhận toast object không stable.

## 7. Danh sách file đã sửa
- `Frontend/src/pages/chatbots/ChatbotEmbedPage.jsx`
  - Mục đích: ngắt vòng lặp setRightSlot/render.
  - Ảnh hưởng lớp: ui.
- `reports/CURSOR_REPORT_14B_FIX_EMBED_PAGE_LAYOUT_LOOP.md`
  - Mục đích: report kỹ thuật task 14B.
  - Ảnh hưởng lớp: docs.
- `docs/2026-05-07-fix-embed-page-layout-loop.md`
  - Mục đích: report docs theo rule 90.
  - Ảnh hưởng lớp: docs.

## 8. Diff thay đổi của từng file
### `Frontend/src/pages/chatbots/ChatbotEmbedPage.jsx`
```diff
- import { useState, useEffect, useCallback } from "react";
+ import { useState, useEffect, useCallback, useMemo, useRef } from "react";
+ const toastRef = useRef(toast);
+ useEffect(() => { toastRef.current = toast; }, [toast]);

- toast.success(...)
- toast.error(...)
- }, [id, form, saving, toast]);
+ toastRef.current.success(...)
+ toastRef.current.error(...)
+ }, [id, form, saving]);

+ const saveRightSlot = useMemo(() => (<button ... />), [handleSave, saving, loading]);
- setRightSlot(<button ... />);
+ setRightSlot(saveRightSlot);
```

### `reports/CURSOR_REPORT_14B_FIX_EMBED_PAGE_LAYOUT_LOOP.md`
```diff
+ # CURSOR REPORT 14B - FIX EMBED PAGE LAYOUT LOOP
+ ...
```

### `docs/2026-05-07-fix-embed-page-layout-loop.md`
```diff
+ # Fix Embed Page Layout Loop (14B)
+ ...
```

## 9. Ảnh hưởng sau sửa
- Behavior thay đổi:
  - Trang Embed không còn update context lặp vô hạn.
  - Điều hướng tab/page hoạt động lại.
- Behavior giữ nguyên:
  - Save embed config, preview, snippet generation giữ nguyên.
- Fallback:
  - Toast success/error vẫn hoạt động.
- Resource/cost:
  - Giảm render churn; không ảnh hưởng DB/Qdrant/API contract.

## 10. Edge cases đã xem xét
- Vào embed lần đầu.
- Chuyển qua lại tab `Config`/`Embed`.
- Sửa form rồi bấm Save.
- Rời trang embed sang page khác.

## 11. Kết quả kiểm tra
| Command | Kết quả | Ghi chú |
|---|---|---|
| `cd Backend && ./mvnw -DskipTests compile` | NOT RUN | Không scope backend. |
| `cd Backend && ./mvnw test` | NOT RUN | Không scope backend. |
| `cd Frontend && npm run lint` | PASS | Pass sau fix embed loop. |
| `cd Frontend && npm run build` | NOT RUN | Không bắt buộc cho follow-up nhỏ này. |
| `cd Frontend && npm run build:widget` | NOT RUN | Không scope widget runtime. |
| `docker compose config` | NOT RUN | Không đổi deploy/infra. |

## 12. Rủi ro còn lại
- Cần user confirm runtime trực tiếp trên browser rằng không còn lỗi console khi chuyển tab.

## 13. Đề xuất tiếp theo
- Reload trang và thử lại flow:
  - vào `Embed`,
  - click về `Config`,
  - điều hướng sang page khác.
- Nếu còn lỗi, capture stack mới để trace tiếp page cụ thể.
