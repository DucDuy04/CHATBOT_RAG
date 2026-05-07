# CURSOR REPORT 14B - FIX EMBED PAGE LAYOUT LOOP

## 1. Mức độ hiểu task
- Hiểu task: 100%.
- Vấn đề user báo: khi vào tab `Embed` thì không thể click về `Config` hoặc page khác, console báo `Maximum update depth exceeded`.
- Scope: bugfix hẹp frontend, không feature mới.

## 2. Root cause
- `ChatbotEmbedPage.jsx` dùng `useToast()` trực tiếp trong dependency của `handleSave`.
- `handleSave` lại nằm trong effect set `rightSlot` qua `setRightSlot(...)`.
- Vì `useToast()` trả object/hàm không stable reference mỗi render, `handleSave` đổi liên tục -> effect setRightSlot chạy lặp -> `LayoutContext` bị update lặp -> UI bị khóa click và throw `Maximum update depth exceeded`.

## 3. Files changed
| File | Change | Reason |
|---|---|---|
| `Frontend/src/pages/chatbots/ChatbotEmbedPage.jsx` | Dùng `toastRef`, bỏ `toast` khỏi deps callback, memo hóa rightSlot node | Ngắt vòng lặp render/update với LayoutContext |

## 4. Diff summary
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
- useEffect(() => { setRightSlot(<button ... />); }, [handleSave, saving, loading, setRightSlot]);
+ useEffect(() => { setRightSlot(saveRightSlot); }, [saveRightSlot, setRightSlot]);
```

## 5. Validation
| Command | Result | Notes |
|---|---|---|
| `cd Frontend && npm run lint` | PASS | Không có lint error mới. |

## 6. Runtime expectation after fix
- Vào tab `Embed` không còn trigger loop trong `LayoutContext`.
- Có thể click lại `Config` và navigate page bình thường.
- Nút `Save` ở header vẫn hoạt động như cũ.
