# Cursor Report 11B - MVP_RAG_QUALITY_EMBED_ORIGINS_DASHBOARD_UX

## 1. Mức độ hiểu task

- **Task:** Cải thiện chất lượng RAG MVP (use case / bảng / đếm), sửa embed `allowedOrigins` + snippet sau save, làm rõ Avg satisfaction dashboard (và tính % khi có feedback).
- **Hiểu task:** 92%
- **Phạm vi không làm:** Parser PDF mới, hybrid search, reranker mới, đổi embedding/schema, re-ingest tự động, hardcode tên file test, sửa widget runtime IIFE.

---

## 2. Files/rules/reports đã đọc

| File | Mục đích | Kết luận chính |
|------|-----------|----------------|
| `.cursor/rules/00,10,20,40,90` | Luật nền | Minimal diff, report + verify |
| `reports/CURSOR_REPORT_11A_*.md` | Embed/snippet | Đã có normalize + merge; vẫn có gap pending text + merge stale |
| `RagRetrievalService.java` | Retrieval | Rerank-Guided Scope Lock có thể thu pool về 1 subtree → chỉ 1 use case |
| `QueryAnalyzerService.java` | Intent + rewrite | Thiếu expansion keyword; LIST_ALL chưa bắt hết “use case” |
| `PromptBuilderService.java` | Prompt | Rule “không tìm thấy” quá cứng; thiếu rule tách bảng theo tên |
| `ChatService.java` | Chat | Playground + widget cùng `retrieveWithMetadata` |
| `PlaygroundController.java` | Playground | `/playground/chat` → `chatStream` (cùng pipeline widget) |
| `WidgetService.java` | Embed PUT | `allowedOrigins != null` mới ghi — `[]` vẫn hợp lệ |
| `ChatbotEmbedPage.jsx` | FE embed | Payload từ `form`; text chưa Enter không vào form |
| `AllowedOriginsInput.jsx` | Origins UX | Chỉ commit tag khi Enter/paste |
| `DashboardService.java` | Summary | `avgSatisfaction` cố định `null` |
| `MetricCard.jsx` / `DashboardPage.jsx` | UI metric | Formatter `—` không giải thích |

---

## 3. User reported issues

| Area | Issue | Root cause (từ source) | Fix status |
|------|-------|-------------------------|------------|
| RAG | Chỉ Use Case 4 / không đếm đủ | Rerank-Guided Lock (score ≥ 0.5) thay pool bằng subtree section top-1 rerank | **MITIGATED** — tắt lock cho LIST_ALL, COUNT_QUERY, TABLE_LOOKUP |
| RAG | “Không tìm thấy” / thiếu context | Thiếu query expansion + LLM rule cứng | **MITIGATED** — expansion + prompt 2b + TABLE_LOOKUP instruction |
| RAG | Lẫn cột bảng PhongKhaoThi / SinhVien | Nhiều bảng trong pool; thứ tự context | **MITIGATED** — expansion + reorder chunk theo hint tên bảng + prompt chỉ 1 bảng |
| Embed | Snippet / save origins sai | Text origin chưa thành tag khi Save; merge có thể mơ hồ | **FIXED** — `getOriginsForSave` + blur + merge explicit `allowedOrigins` |
| Dashboard | Avg satisfaction `--` | API luôn `null` + UI không giải thích | **FIXED** — tính % positive/(positive+negative) + tooltip/caption |

---

## 4. RAG investigation

**Lưu ý:** Không có MySQL/Qdrant thật trong môi trường agent; không dump chunk `HeThongQuanLyYeuCauPhucKhao.pdf`. Phân tích dựa trên luồng code và log pattern đã biết.

| Question | Retrieved chunks before (ước lượng từ code) | Root cause | Fix | Retrieved chunks after (kỳ vọng) | Status |
|----------|---------------------------------------------|------------|-----|----------------------------------|--------|
| Có bao nhiêu use case… | Ít anchor / rerank lock 1 section | COUNT + rerank-lock thu nhỏ pool; thiếu expansion | Tắt rerank-lock cho COUNT/TABLE/LIST; expansion keyword | Nhiều chunk use case trong pool trước budget | **Expected improve** |
| Liệt kê use case… | Pool sau rerank-lock ≈ 1 nhánh UC4 | Giống trên | Giống trên + LIST_ALL trigger “use case” | Nhiều UC trong context | **Expected improve** |
| Bảng PhongKhaoThi cột nào | Mix GiangVien/MonHoc… | Chunk lớn + vector top-K lẫn bảng | Expansion + hint reorder + prompt 1 bảng | Chunk có tiêu đề/nội dung PhongKhaoThi lên trước | **Expected improve** |
| Bảng SinhVien… | Tương tự | Giống trên | Hint `sinhvien` | Chunk SinhVien ưu tiên | **Expected improve** |
| Bạn có thông tin gì? | Mỏng | Query ngắn semantic yếu | Expansion “tổng quan…” | Thêm variant retrieval | **Expected improve** |

---

## 5. Files changed

| File | Change | Reason | Risk |
|------|--------|--------|------|
| `QueryAnalyzerService.java` | Expansion trong `rewriteQuery`; LIST_ALL thêm “use case” | Anchor vector đủ rộng | Thêm vài variant Qdrant call |
| `RagRetrievalService.java` | Tắt rerank-scope-lock cho LIST/COUNT/TABLE; hint reorder; `dedupeSortBudget(..., question)` | Tránh lock 1 UC; ưu tiên đúng bảng | Lock ít kích hoạt hơn cho TABLE (chấp nhận trade-off) |
| `PromptBuilderService.java` | Rule 2/2b, bảng theo tên, TABLE_LOOKUP instruction | Giảm “không tìm thấy” sai; giảm lẫn cột | Prompt dài hơn nhẹ |
| `DashboardService.java` | `ChatFeedbackRepository` + % satisfaction | Metric có nghĩa khi có feedback | Query aggregate toàn thời gian từ 2000-01-01 |
| `MetricCard.jsx` | `titleHint`, `valueCaption` | Tooltip/caption | Thấp |
| `DashboardPage.jsx` | Card satisfaction copy | UX | Thấp |
| `AllowedOriginsInput.jsx` | `forwardRef`, `getOriginsForSave`, blur flush | Origin đang gõ được lưu | Thấp |
| `EmbedSettingsSection.jsx` | Truyền `ref` | Wiring | Thấp |
| `ChatbotEmbedPage.jsx` | Flush trước PUT + merge `allowedOrigins` explicit | Snippet đúng sau save | Thấp |

---

## 6. Detailed changes

### RAG retrieval / prompt / rerank lock / hint

- **Rerank-Guided Scope Lock:** Chỉ giữ khi `queryType` **không** thuộc `LIST_ALL`, `COUNT_QUERY`, `TABLE_LOOKUP` — tránh thay toàn bộ pool bằng subtree của 1 chunk top (nguyên nhân khả dĩ “chỉ Use Case 4”).
- **Query expansion:** `rewriteQuery` thêm variant tiếng Việt không dấu cho use case / PhongKhaoThi / SinhVien / câu hỏi “thông tin gì”.
- **Hint reorder:** Sau `prioritizeSummaryChunks`, partition ổn định: chunk có `sectionTitle`/heading/content khớp hint lên trước khi áp dụng char budget.
- **Prompt:** Làm rõ “không tìm thấy” chỉ khi không có Source; thêm 2b partial context; rule bảng theo tên; `TABLE_LOOKUP` instruction nhấn “đúng bảng được hỏi”.

### Embed allowedOrigins

- `getOriginsForSave()` commit dòng đang gõ (hợp lệ) trước khi build payload Save.
- `onBlur` flush tương tự.
- Sau PUT: nếu response có field `allowedOrigins` thì normalize từ response; không thì dùng `originsPayload` vừa gửi — tránh stale merge.

### Dashboard satisfaction

- `avgSatisfaction` = `round(1000 * positive / (positive + negative)) / 10` với `positive` = rating 1, `negative` = rating -1, khoảng thời gian từ `2000-01-01` đến hiện tại (MVP đơn giản).
- UI: tooltip + caption khi null.

---

## 7. API/request behavior after fix

| Hạnh vi | Sau fix |
|---------|---------|
| Chat / Playground / Widget stream | Cùng `RagRetrievalService`; aggregate query không còn rerank-scope-lock |
| `PUT /api/chatbots/{id}/embed-config` | Không đổi contract; FE gửi `allowedOrigins` đã flush từ ô nhập |
| `GET /api/dashboard/summary` (hoặc endpoint summary tương ứng) | `avgSatisfaction` có thể là % (0.1 độ chính xác) khi có feedback |

---

## 8. Validation results

| Command | Result | Notes |
|---------|--------|-------|
| `cd Backend && mvnw.cmd -DskipTests compile` | **PASS** | |
| `cd Backend && mvnw.cmd test` | **FAIL** | `ApplicationContext` — thiếu env `GROQ_API_KEY` (PlaceholderResolutionException); không liên quan diff DashboardService |
| `cd Frontend && npm run lint` | **PASS** | |
| `cd Frontend && npm run build` | **PASS** | |
| `cd Frontend && npm run build:widget` | **NOT RUN** | Không sửa `Frontend/widget/` |
| `docker compose config` | **PASS** | `-q` |

---

## 9. Manual retest results

| Area | Flow | Expected | Actual | Status |
|------|------|----------|--------|--------|
| RAG | Playground 4 câu mẫu + PDF đã ingest | Ổn định hơn, không chỉ UC4 | **NOT RUN** (agent không có stack đầy đủ) | Pending user |
| Embed | Clear → Save → Snippet `[]` → thêm 2 origin → Save → F5 | Snippet khớp | **NOT RUN** | Pending user |
| Dashboard | Chưa có feedback / có feedback | `--` + caption / `%` | **NOT RUN** | Pending user |

---

## 10. Known limitations

- Đếm/list use case vẫn phụ thuộc **chunk đã ingest** và **budget** context; không đảm bảo đủ toàn bộ tài liệu nếu chunking tách UC ra ngoài top pool.
- Tắt rerank-lock cho `TABLE_LOOKUP` có thể giảm độ “focus” cho một số câu hỏi bảng đơn giản — trade-off MVP.
- Satisfaction dashboard: không tính delta theo kỳ (vẫn `0.0`); không tách theo chatbot.

---

## 11. Final decision

**Some issues remain** ở mức **runtime verification** (RAG cần test lại trên PDF thật; backend test cần env). **Code-level fixes** cho 3 nhóm (RAG heuristic, embed flush/merge, dashboard metric) đã implement.

---

## 12. Recommended next prompt

- Chạy MVP regression thủ công (Playground + Widget) với `HeThongQuanLyYeuCauPhucKhao.pdf` đã ingest; nếu vẫn lẫn bảng do **chunk chứa nhiều bảng**, prompt follow-up hẹp: post-process chunk table boundaries (không rewrite parser lớn).
