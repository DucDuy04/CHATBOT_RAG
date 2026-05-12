# CURSOR REPORT 09C - ANALYTICS FEEDBACK TAB

## 1. Muc do hieu task

- Hieu task: 100%
- Phan chac chan: chi implement tab `Feedback` trong `/analytics`; khong regress Usage/Sessions.
- Phan con gia dinh: do hien tai khong co GET/read feedback endpoint, feedback tab duoc render tu mock/local data.
- Thieu du kien: contract backend cho read feedback aggregate/comments chua co.

## 2. Tom tat yeu cau

- Replace placeholder Feedback tab bang implementation that.
- Hien thi aggregate thumbs up/thumbs down.
- Hien thi danh sach comments + filter rating All/Positive/Negative.
- Kiem tra va ton trong checklist API:
  - chi co POST `/api/chat/feedback` trong contract.
  - khong invent GET endpoint moi.
- Khong fake `messageId` de submit.

## 3. Hien trang truoc khi sua

- `AnalyticsPage` da co Usage (09A) va Sessions (09B), Feedback con placeholder.
- `analyticsApi` chi co `submitFeedback` (POST), khong co ham GET feedback aggregate/comment list.
- `analyticsMock` chua co dataset feedback doc lap cho tab feedback.

## 4. Nguyen nhan goc xac nhan tu source

- Khong co luong read feedback trong API layer de render tab analytics feedback; UI con placeholder.

## 5. Chien luoc sua da chon

- Khong tao endpoint that moi.
- Bo sung `feedbackMockData` trong `analyticsMock.js` de phuc vu UI dev/local.
- Tao bo component feedback rieng:
  - `AnalyticsFeedbackTab`
  - `FeedbackSummaryCards`
  - `FeedbackCommentsList`
  - `FeedbackRatingFilter`
- Integrate vao `AnalyticsPage` chi cho tab feedback.
- Khong goi POST submit trong tab nay vi khong co message context authoritative tu analytics list.

## 6. Danh sach file da doc

- `.cursor/rules/00-core-working-rule.mdc`: source-first va minimal diff.
- `.cursor/rules/10-backend-rag-rule.mdc`: xac nhan backend out-of-scope.
- `.cursor/rules/20-frontend-widget-rule.mdc`: frontend constraints + verify lint/build.
- `.cursor/rules/30-deploy-env-ops-rule.mdc`: xac nhan khong doi deploy/env.
- `.cursor/rules/40-db-vector-rule.mdc`: xac nhan khong doi db/vector.
- `.cursor/rules/90-report-verification-rule.mdc`: format report bat buoc.
- `reports/CURSOR_REPORT_00_PROJECT_AUDIT.md`: context feature roadmap.
- `reports/CURSOR_REPORT_01C_LAYOUT_CONTEXT.md`: page integration context.
- `reports/CURSOR_REPORT_02_API_LAYER.md`: endpoint contracts.
- `reports/CURSOR_REPORT_09A_ANALYTICS_USAGE_TAB.md`: usage baseline.
- `reports/CURSOR_REPORT_09B_ANALYTICS_SESSIONS_TAB.md`: sessions baseline.
- `Frontend/src/pages/analytics/AnalyticsPage.jsx`: noi tich hop feedback tab.
- `Frontend/src/pages/analytics/components/*`: component landscape hien tai.
- `Frontend/src/api/analyticsApi.js`: xac nhan chi co POST feedback, khong co GET feedback.
- `Frontend/src/mocks/analyticsMock.js`: bo sung/read mock feedback data.
- `Frontend/src/components/common/SkeletonLoader.jsx`: reviewed (khong can fetch async cho feedback).
- `Frontend/src/components/common/EmptyState.jsx`: empty state reuse.
- `Frontend/src/components/common/StatusBadge.jsx`: reviewed cho badge option.
- `Frontend/src/components/common/useToast.js`: reviewed for potential error handling.

## 7. Danh sach file da sua

- `Frontend/src/mocks/analyticsMock.js`
  - Muc dich: them `feedbackMockData` local-only cho Feedback tab.
  - Layer: ui
- `Frontend/src/pages/analytics/AnalyticsPage.jsx`
  - Muc dich: thay feedback placeholder bang tab implementation.
  - Layer: ui
- `Frontend/src/pages/analytics/components/AnalyticsFeedbackTab.jsx`
  - Muc dich: orchestrate summary + filter + comments list.
  - Layer: ui
- `Frontend/src/pages/analytics/components/FeedbackSummaryCards.jsx`
  - Muc dich: render aggregate thumbs up/down.
  - Layer: ui
- `Frontend/src/pages/analytics/components/FeedbackRatingFilter.jsx`
  - Muc dich: filter All/Positive/Negative.
  - Layer: ui
- `Frontend/src/pages/analytics/components/FeedbackCommentsList.jsx`
  - Muc dich: comments list theo rating filter.
  - Layer: ui

## 8. Diff thay doi cua tung file

### `Frontend/src/mocks/analyticsMock.js`

- Cu: chua co dataset feedback cho analytics feedback tab.
- Moi: them `feedbackMockData` (mock-only) gom id/messageId/rating/comment/chatbot/session/date.

```diff
+ export const feedbackMockData = [
+   { id: "fb-001", messageId: "am-002", rating: 1, comment: "...", ... },
+   { id: "fb-003", messageId: "msg-003", rating: -1, comment: "...", ... },
+   ...
+ ];
```

### `Frontend/src/pages/analytics/AnalyticsPage.jsx`

- Cu: tab feedback render `EmptyState` placeholder.
- Moi: render `AnalyticsFeedbackTab`.

```diff
+ import AnalyticsFeedbackTab from "./components/AnalyticsFeedbackTab";
- {activeTab === "feedback" && <placeholder ... />}
+ {activeTab === "feedback" && <AnalyticsFeedbackTab />}
```

### `Frontend/src/pages/analytics/components/AnalyticsFeedbackTab.jsx`

- Cu: chua co file.
- Moi:
  - read `feedbackMockData` local.
  - tinh aggregate thumbs up/down.
  - filter comments theo All/Positive/Negative.
  - warning banner: chua co GET/read endpoint.
  - notice ro rang: POST `/api/chat/feedback` khong duoc trigger tai tab nay.

```diff
+ const [ratingFilter, setRatingFilter] = useState("all");
+ const allFeedback = normalizeFeedback(feedbackMockData);
+ const thumbsUp = ...
+ const filteredComments = ...
+ <FeedbackSummaryCards ... />
+ <FeedbackRatingFilter ... />
+ <FeedbackCommentsList ... />
```

### `Frontend/src/pages/analytics/components/FeedbackSummaryCards.jsx`

- Cu: chua co.
- Moi: 2 card aggregate `Thumbs up`, `Thumbs down`.

```diff
+ <p>Thumbs up</p><p>{thumbsUp}</p>
+ <p>Thumbs down</p><p>{thumbsDown}</p>
```

### `Frontend/src/pages/analytics/components/FeedbackRatingFilter.jsx`

- Cu: chua co.
- Moi: select options `All`, `Positive`, `Negative`.

```diff
+ const OPTIONS = [all, positive, negative]
+ <select value={value} onChange=... />
```

### `Frontend/src/pages/analytics/components/FeedbackCommentsList.jsx`

- Cu: chua co.
- Moi:
  - render list items: rating badge + comment + chatbot/session/date.
  - empty state neu khong co comments theo filter.

```diff
+ <RatingBadge rating={item.rating} />
+ <p>{item.comment}</p>
+ <span>{item.chatbotName}</span> <span>Session ...</span> <span>{date}</span>
```

## 9. Anh huong sau sua

- Behavior thay doi:
  - Feedback tab da co implementation that voi aggregate + comment list + rating filter.
- Behavior giu nguyen:
  - Usage tab khong doi logic fetch/render.
  - Sessions tab khong doi logic fetch/pagination/drawer.
  - Khong goi endpoint moi ngoai checklist.
- Dieu kien bat:
  - Feedback tab hien tai chi doc mock/local data.
- Fallback:
  - Neu feedback data rong -> EmptyState + notice read endpoint thieu.
- Anh huong tai nguyen:
  - Tang nhe frontend bundle.
  - Khong tac dong DB/Qdrant/schema/backend.

## 10. Edge cases da xem xet

- Feedback data empty -> EmptyState va warning.
- Rating filter khong match -> EmptyState list.
- Comment null/blank -> filtered out khoi comments list.
- Rating khong phai 1/-1 -> bo qua trong normalize.
- Khong co read API -> khong goi network, khong crash.

## 11. Ket qua kiem tra

| Command | Ket qua | Ghi chu |
|---|---|---|
| `cd Backend && ./mvnw -DskipTests compile` | NOT RUN | Khong sua backend |
| `cd Backend && ./mvnw test` | NOT RUN | Khong sua backend |
| `cd Frontend && npm run lint` | PASS | lint pass |
| `cd Frontend && npm run build` | PASS | build pass; chunk-size warning van ton tai |
| `cd Frontend && npm run build:widget` | NOT RUN | Ngoai scope |
| `docker compose config` | NOT RUN | Khong doi deploy |

## 12. Rui ro con lai

- Chua co GET/read endpoint feedback cho production analytics.
- Feedback tab dang phu thuoc `feedbackMockData` local, khong phan anh du lieu real-time backend.
- POST `/api/chat/feedback` chua duoc su dung trong tab nay do thieu message context submit flow.

## 13. De xuat tiep theo

- Backend/API: bo sung read endpoint cho feedback aggregate va comments list.
- Frontend: chuyen Feedback tab tu mock/local sang API read endpoint khi contract co san.
- Neu co use-case, bo sung flow submit/resubmit feedback tu context message that (khong fake messageId).
