# Cursor Report 11A - MVP_UX_CONTRACT_CLEANUP_EMBED_PLAYGROUND_ANALYTICS

## 1. Mức độ hiểu task

- **Task:** MVP UX cleanup — Embed `allowedOrigins` đồng bộ snippet + paste nhiều dòng; Playground giải thích Temperature/Top-K/Max tokens, giới hạn hiển thị Sources theo Top-K, sửa restore session ngay sau stream; Analytics chart daily hoạt động; bỏ Rating khỏi tab Sessions; xóa tab Feedback.
- **Hiểu task:** 97%
- **Phạm vi không làm:** Đổi schema DB, RAG core, JWT, compare override engine sâu, Qdrant purge, backend analytics contract lớn.

---

## 2. Files/rules/reports đã đọc

| File | Mục đích | Kết luận |
|------|-----------|----------|
| `.cursor/rules/00–40,90` | Luật nền | Minimal diff, không đụng backend core |
| `reports/CURSOR_REPORT_08F_*.md` | Embed | `widgetKey`, snippet từ form |
| `reports/CURSOR_REPORT_04B_*.md` | Playground | sessions/export/compare |
| `Backend/.../AnalyticsDailyItem.java` | Chart | Field `date`, `messages` (Long), `sessions` |
| `Frontend/.../ChatbotEmbedPage.jsx` | Fix A | `buildSnippet` + `toFormState` |
| `Frontend/.../PlaygroundPage.jsx` | Fix B/C | Stream `onDone`, session select |
| `Frontend/.../DailyBarChart.jsx` | Fix D | Trước đó `%` height trên flex con dễ fail |

---

## 3. User reported issues

| Area | Issue | Root cause (xác nhận từ source) | Fix status |
|------|-------|-----------------------------------|-------------|
| Embed | Snippet không có `allowedOrigins` đúng sau nhập | `toFormState` chỉ nhận `Array.isArray`; API/string multiline không → `[]`; thiếu normalize khi build snippet | **FIXED** |
| Playground | Không hiểu Temp / Top-K / Max | UI thiếu copy giải thích rõ | **FIXED** (ModelOverridePanel + CompareConfigPanel) |
| Playground | Top-K=1/5 nhưng Sources > K | Backend có thể trả nhiều chunk; UI list hết | **FIXED** (slice hiển thị + chú thích) |
| Playground | Click session mới chỉ thấy user đến khi refresh | List session từ API sau `done` thiếu assistant message; export trước đó chỉ gọi khi `messages` rỗng | **FIXED** (export ưu tiên + merge snapshot sau refresh list) |
| Analytics | Chart message/day không chạy | Cột dùng `height: X%` trong flex column không có chiều cao cha rõ → bar coi như 0 | **FIXED** (chiều cao px cố định + normalize field) |
| Analytics Sessions | Bỏ Rating filter/column | Yêu cầu MVP UI | **FIXED** |
| Analytics | Xóa tab Feedback | Yêu cầu MVP | **FIXED** |

---

## 4. Files changed

| File | Change | Reason | Risk |
|------|--------|--------|------|
| `ChatbotEmbedPage.jsx` | `normalizeAllowedOriginsList`, snippet + `toFormState`, merge sau save, preview prop | A + không mất `widgetKey` khi merge | Thấp |
| `AllowedOriginsInput.jsx` | `onPaste` tách nhiều origin | UX nhập nhanh multiline | Thấp |
| `WidgetLivePreview.jsx` | Prop `allowedOrigins`, footer count | Phản hồi trực quan | Thấp |
| `PlaygroundPage.jsx` | `lastStreamSnapshotRef`, `onDone` merge sessions, export-first restore, `displaySources` + limits | B, C | Thấp — thêm 1 round GET sessions |
| `RetrievalPanel.jsx` | `totalSourceCount`, `topKLimit`, caption | B3 | Thấp |
| `ModelOverridePanel.jsx` | Giải thích + note MVP | B1 | Thấp |
| `ComparePane.jsx` / `CompareConfigPanel.jsx` | Slice sources theo `topK`, copy | B2–B4 | Thấp |
| `DailyBarChart.jsx` | Normalize row, sort ngày, bar height px | D | Thấp |
| `AnalyticsPage.jsx` | Bỏ Feedback tab, `useSearchParams` cleanup `feedback`, `handleAnalyticsTab` | E/F | Thấp |
| `AnalyticsTabs.jsx` | Bỏ tab Feedback | F | Thấp |
| `AnalyticsSessionsTab.jsx` | Bỏ rating filter + state | E | Thấp |
| `SessionsTable.jsx` | Bỏ cột Rating | E | Thấp |
| `analyticsApi.js` | `getSessions` không gửi `rating` | E3 | Thấp — backend vẫn chấp nhận nếu có |

---

## 5. Detailed changes

### Embed allowedOrigins snippet

- Thêm `normalizeAllowedOriginsList`: array | string (newline/comma), trim, dedupe, regex `http(s)://...` giống input tag.
- `toFormState` dùng normalize thay vì chỉ `Array.isArray`.
- `buildSnippet` luôn gắn `allowedOrigins` từ normalize(form).
- `updateEmbedConfig` sau save: `setForm((prev) => toFormState({ ...prev, ...updated }))` để không ghi đè stale.
- `AllowedOriginsInput`: paste nhiều dòng/CSV hợp lệ → thêm hàng loạt.
- `WidgetLivePreview`: hiển thị số origin khi >0.

### Playground config explanation / Top-K sources limit

- `ModelOverridePanel`: đoạn mô tả Temperature / Top-K (kèm ý “panel Sources chỉ hiện tối đa K”) / Max tokens + note MVP backend.
- `CompareConfigPanel`: cập nhật một dòng giải thích Top-K vs hiển thị cột Sources.
- `PlaygroundPage`: `displaySources = slice(lastSources, topK)`; compare mode dùng `max(topK A, topK B)` cho panel phải.
- `RetrievalPanel`: tiêu đề “Sources (shown of total)” + dòng chú thích khi có `topKLimit`.
- `ComparePane` `AnswerColumn`: `sources.slice(0, topK)` + dòng “Showing top K…”.

### Playground session restore answer

- `handleSessionSelect`: **luôn thử `exportSession` trước**, rồi fallback `session.messages`.
- `onDone`: lưu snapshot message vào `lastStreamSnapshotRef`; sau `getSessions`, merge vào item session trùng `sessionId` nếu payload server **chưa** có assistant có nội dung.

### Analytics message per day chart

- `normalizeDailyChartRow`: hỗ trợ `date`/`day`/`label`, `messages`/`messageCount`/`count`/`value`.
- Sắp xếp theo `date` string ISO.
- Vùng chart cố định `CHART_HEIGHT_PX = 200`, chiều cao cột tính theo **pixel**, không dùng `%` trên flex con.
- Empty / all-zero: copy rõ ràng.

### Remove Sessions rating UI

- Xóa filter Rating và state `rating` trong `AnalyticsSessionsTab`.
- Xóa cột Rating trong `SessionsTable`.
- `analyticsApi.getSessions` không còn query `rating`.

### Remove Feedback tab

- `AnalyticsTabs`: chỉ Usage + Sessions.
- `AnalyticsPage`: bỏ render `AnalyticsFeedbackTab`; `useSearchParams` — nếu `activeTab=feedback` hoặc `tab=feedback` thì `replace` query và về Usage; `handleAnalyticsTab` chặn key `feedback`.

---

## 6. API/request behavior after fix

| Hạnh vi | Sau fix |
|---------|---------|
| Embed snippet `allowedOrigins` | Luôn từ form đã normalize; save merge response + form |
| Playground Sources panel | Tối đa `topK` mục hiển thị; full list vẫn trong state message/export |
| `GET /api/analytics/sessions` | Không còn gửi `rating` từ FE |
| Analytics tabs | Chỉ Usage, Sessions; URL `?activeTab=feedback` → về Usage + strip query |

---

## 7. Validation results

| Command | Result | Notes |
|---------|--------|--------|
| `cd Frontend && npm run lint` | **PASS** | Đã chạy sau chỉnh sửa chính |
| `cd Frontend && npm run build` | **PASS** | Vite build OK |
| `cd Backend && ./mvnw -DskipTests compile` | **NOT RUN** | Không sửa Java |

---

## 8. Manual retest results

| Area | Flow | Expected | Actual | Status |
|------|------|----------|--------|--------|
| H1 Embed | Paste origins + snippet | `allowedOrigins` đúng | Agent không mở browser | **NOT RUN** |
| H2 Playground | Top-K, session click, compare | Như prompt | **NOT RUN** | |
| H3 Analytics | Chart + Sessions + no Feedback | Như prompt | **NOT RUN** | |

---

## 9. Known limitations

- **Top-K hiển thị vs retrieval thật:** FE chỉ **cắt hiển thị**; backend vẫn có thể dùng số chunk khác nếu override chưa áp dụng đầy đủ — đã ghi chú trong UI.
- **Compare engine sâu:** vẫn có thể A/B giống nhau — đã có note trong `ComparePane` / `ModelOverridePanel`.
- **POST `/api/chat/feedback`:** backend và service vẫn tồn tại; chỉ **bỏ tab đọc Feedback** trên Analytics.

---

## 10. Final decision

**MVP UX cleanup PASS** (theo code + lint/build). Manual browser checklist **NOT RUN** trong agent — cần user xác nhận nhanh H1–H3.

---

## 11. Recommended next prompt

- Không cần prompt implementation tiếp nếu manual H1–H3 OK.
- Nếu chart vẫn sai với payload thực tế lệch contract: prompt hẹp **“Analytics daily FE adapter vs BE sample response”** kèm một JSON response thật (ẩn PII).
