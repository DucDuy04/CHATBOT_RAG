# CURSOR REPORT 28A — Dead Code Cleanup (Feedback, DTO, Mock)

**Date:** 2026-05-30  
**Task:** TASK 28A — Cleanup Unused Backend Feedback, DTO Fields, and Frontend Mock Data  
**Verdict:** PASS

---

## 1. Mức độ hiểu task

- **Hiểu task:** 95%
- **Phần chắc chắn:** Feedback endpoint/UI không còn dùng; mock API infrastructure vẫn là dev fallback; DTO satisfaction fields vẫn được frontend đọc
- **Phần giả định:** Không cần runtime smoke Docker cho PASS (optional)
- **Thiếu dữ kiện:** `docs/api/API_*_20260530.md` không tồn tại trong repo

---

## 2. Tóm tắt yêu cầu

Xóa dead code feedback backend, DTO feedback, frontend feedback UI/mock; review DTO fields và mock data; giữ API contract đang active; chạy test/build sau mỗi bước; inventory trước khi xóa.

---

## 3. Hiện trạng trước khi sửa

- `POST /api/chat/feedback` còn trong `ChatController` với full stack entity/service/repo/DTO
- `AnalyticsService` và `DashboardService` đọc `ChatFeedbackRepository` cho satisfaction/rating
- Frontend đã bỏ feedback tab khỏi navigation nhưng còn 4 component feedback + `submitFeedback()` + `feedbackMockData`
- Mock API (`USE_MOCK_API`) vẫn wired cho toàn bộ admin pages

---

## 4. Nguyên nhân gốc xác nhận từ source

Feedback feature deprecated ở UI nhưng backend endpoint và persistence stack vẫn tồn tại. Analytics/dashboard vẫn aggregate từ `chat_feedbacks` dù không có nguồn submit mới. Frontend feedback components không còn import trong route tree.

---

## 5. Chiến lược sửa đã chọn

1. Inventory + classify trước khi xóa
2. Xóa toàn bộ feedback-specific backend stack; strip rating filter khỏi analytics
3. Giữ DTO fields `rating`/`avgSatisfaction`/`newFeedback` — frontend hoặc DB vẫn reference
4. Xóa chỉ feedback-only frontend components và mock export
5. **Không** xóa mock API infrastructure (`KEEP_DEV_FALLBACK`)
6. **Không** drop DB table/column — code cleanup only

---

## 6. Danh sách file đã đọc

| File | Đọc để làm gì | Kết luận |
|---|---|---|
| `api/ChatController.java` | Feedback endpoint | Có `/feedback` — xóa |
| `service/AnalyticsService.java` | Rating aggregation | Dùng ChatFeedbackRepository — strip |
| `service/DashboardService.java` | avgSatisfaction | Dùng feedback repo — return null |
| `api/AnalyticsController.java` | rating param | Có `rating` filter — xóa param |
| `Frontend/src/api/analyticsApi.js` | submitFeedback | Dead client call — xóa |
| `Frontend/src/mocks/*` | Mock classification | KEEP dev fallback except feedbackMockData |
| `Frontend/src/pages/analytics/AnalyticsPage.jsx` | Tab routing | Feedback tab removed; redirect guard kept |
| `agent/05-api.md` | API docs | Cần note removal |
| `docs/RAG_TARGET_ARCHITECTURE.md` | Architecture stale refs | Cần update feedback rows |

---

## 7. Danh sách file đã sửa

| File | Sửa để làm gì | Ảnh hưởng lớp |
|---|---|---|
| `ChatController.java` | Xóa feedback endpoint | api |
| `AnalyticsController.java` | Xóa rating param | api |
| `AnalyticsService.java` | Xóa feedback aggregation | service |
| `DashboardService.java` | avgSatisfaction = null | service |
| `analyticsApi.js` | Xóa submitFeedback | ui |
| `analyticsMock.js` | Xóa feedbackMockData | ui/mock |
| `DashboardPage.jsx` | Caption null satisfaction | ui |
| `ProfileSettingsSection.jsx` | Xóa newFeedback toggle | ui |
| `agent/05-api.md` | Document removal | docs |
| `docs/RAG_TARGET_ARCHITECTURE.md` | Mark feedback removed | docs |

**Deleted (backend):** `ChatFeedbackService`, `ChatFeedback`, `ChatFeedbackRepository`, `ChatFeedbackRequest`, `ChatFeedbackResponse`

**Deleted (frontend):** `AnalyticsFeedbackTab`, `FeedbackCommentsList`, `FeedbackRatingFilter`, `FeedbackSummaryCards`

---

## 8. Diff thay đổi của từng file

### `ChatController.java`

```diff
-    private final ChatFeedbackService chatFeedbackService;
-
-    @PostMapping("/feedback")
-    public ResponseEntity<ChatFeedbackResponse> submitFeedback(...) { ... }
```

### `AnalyticsController.java`

```diff
-            @RequestParam(value = "rating", required = false) Integer rating
...
-                validation.from, validation.to, validation.chatbotId, rating, validation.page
+                validation.from, validation.to, validation.chatbotId, validation.page
```

### `AnalyticsService.java`

```diff
-    private final ChatFeedbackRepository chatFeedbackRepository;
-    // rating filter, buildSessionRatingMap, calculatePointDelta, satisfaction from feedback
+    // session rating always null; avgSatisfaction from feedback removed
```

### `DashboardService.java`

```diff
-    private final ChatFeedbackRepository chatFeedbackRepository;
-    // compute avgSatisfaction from feedback
+    .avgSatisfaction(null)
```

### `analyticsApi.js`

```diff
-  submitFeedback(payload) { return api.post('/chat/feedback', payload); }
```

### Frontend feedback components — **deleted entirely**

### `agent/05-api.md`

```diff
+ Feedback endpoint removed in task 28A — `POST /api/chat/feedback` no longer exists.
```

---

## 9. Ảnh hưởng sau sửa

**Thay đổi:**
- `POST /api/chat/feedback` không còn
- `GET /api/analytics/sessions` không còn filter `rating`
- Satisfaction metrics luôn `null` từ backend
- Feedback UI components removed

**Giữ nguyên:**
- Chat, document, chatbot, playground, widget APIs
- Retrieval/parser/Qdrant payload
- Mock API dev mode (`USE_MOCK_API`)
- DB schema (`chat_feedbacks`, `notify_new_feedback`)

**Latency/token:** Không đổi retrieval path

---

## 10. Edge cases đã xem xét

- Old URL `?activeTab=feedback` → redirect guard in AnalyticsPage (no crash)
- Widget chat không dùng feedback endpoint
- Settings API vẫn accept `newFeedback` nếu client gửi
- Mock mode vẫn show mock satisfaction numbers (dev only)
- JPA không fail — entity removed, table orphaned only

---

## 11. Kết quả kiểm tra

| Command | Kết quả | Ghi chú |
|---|---|---|
| `cd Backend && .\mvnw.cmd clean test` | **PASS** | 120 tests, 0 failures |
| `cd Frontend && npm install && npm run build` | **PASS** | Vite 7.3.1 |
| `cd Frontend && npm run lint` | **PASS** | ESLint |
| `docker compose config -q` | **PASS** | |
| Runtime smoke | **NOT RUN** | Optional |

---

## 12. Rủi ro còn lại

- Orphaned MySQL `chat_feedbacks` table
- Dead repo method `findAllForAnalyticsRange`
- Satisfaction UI shows empty — UX debt until metric replaced
- `docs/api/API_*_20260530.md` missing — agent/05-api links broken (pre-existing)

---

## 13. Đề xuất tiếp theo

1. Task 28B: DB migration plan for `chat_feedbacks` + `notify_new_feedback`
2. Remove dead `findAllForAnalyticsRange` repository method
3. Create missing `docs/api/API_REFERENCE_20260530.md` from controllers
4. Decide replacement metric for satisfaction or remove UI cards

---

**Detailed eval report:** `docs/eval/results/DEAD_CODE_CLEANUP_28A_20260530.md`
