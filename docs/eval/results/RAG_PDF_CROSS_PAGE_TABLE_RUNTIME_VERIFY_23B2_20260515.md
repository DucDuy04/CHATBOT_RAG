# RAG — PDF cross-page table runtime verify 23B2 (official result)

**File:** `docs/eval/results/RAG_PDF_CROSS_PAGE_TABLE_RUNTIME_VERIFY_23B2_20260515.md`  
**Report:** `reports/refactor/CURSOR_REPORT_23B2_PDF_CROSS_PAGE_TABLE_RUNTIME_VERIFY.md`  
**Ngày:** 2026-05-15  
**Loại task:** VERIFY ONLY — không sửa code

---

## 1. Environment

| Item | Giá trị |
|------|---------|
| OS | Windows 10 |
| Stack | `docker compose up --build -d` (backend recreated với code 23B) |
| Backend | `http://localhost:8080` |
| Qdrant | 1 collection `documents` |
| `docker compose config -q` | **PASS** |
| GROQ_API_KEY | **present** (len 56, masked) |
| NOMIC_API_KEY | **present** |

---

## 2. PDF artifact

| Item | Giá trị |
|------|---------|
| Path used | `docs/eval/manual/HeThongQuanLyYeuCauPhucKhao.pdf` |
| In workspace | **Yes** |

---

## 3. Build / compile / test

| Command | Kết quả |
|---------|---------|
| `docker compose up --build -d` | **PASS** |
| `mvnw -DskipTests compile` | **PASS** |
| `mvnw -Dtest=ParserAndOrderingTests test` | **PASS** |

---

## 4. Chatbot / document setup

| Field | Giá trị |
|-------|---------|
| Name | `23B2 PDF Cross Page Runtime` |
| `CHATBOT_ID` | `c713cbb0-2f62-491a-bff3-47298d0e1cf3` |
| Widget key | `9cb3...88fc` (masked) |
| `modelConfig` | topK=10, temperature=0.2, maxTokens=1024 |
| `DOCUMENT_ID` | `0bcde0c5-eb27-490b-a39e-fd15b62cff7f` |
| Upload | **INDEXED** |
| `chunkCount` | **82** |

Artifact: `docs/eval/results/_run_23b2_results.json`, script `_run_23b2_pdf_cross_page_verify.ps1`

---

## 5. Parser log evidence (target pages 13–14)

Từ `docker logs chatbot-backend` trong phiên upload:

| Page | Event | Chi tiết |
|------|-------|----------|
| **13** | `Table ACCEPTED` | rows=9 cols=3 — preview có `NguoiDung`, header “Các thực thể…” |
| **14** | `Table REJECTED` | reason=`too-many-empty-cells(65/99>66%)` rows=33 — preview **có** `LichSuPhucKhao` và text continuation |
| **13→14** | `Table MERGED continuation` | **KHÔNG** có log `page=14 → page=13` |

Merge 23B **có chạy** trên PDF nhưng cho bảng khác:

```text
[Parse] Table MERGED continuation page=18 → page=17: mode=REPEATED_HEADER rows=16|9|6
```

(không phải cặp trang 13–14 của bảng thực thể).

**Kết luận log:** Fix 23B hoạt động cho một số bảng REPEATED_HEADER; **bảng target 13–14 không merge** vì Tabula page 14 bị reject trước khi vào nhánh merge (continuation heuristic không kích hoạt trên bảng sparse).

---

## 6. SQL chunks / tables (read-only)

| Check | Kết quả |
|-------|---------|
| Chunks có `LichSuPhucKhao` hoặc `BienBan` | **10** rows match (COUNT query) |
| Preview 50 chunk đầu (LEFT 120) | Không hiện full entity table — encoding console |
| Entity trong chunk text (script scan) | **7/12** trong sample preview; **2/4** page-14 entities (`LichSuPhucKhao`, `Khoa`) |

**Qdrant** (82 points, filter document+widget):

| Check | Found |
|-------|-------|
| All 12 entity names | **12/12** |
| Page-14 quartet | **4/4** (`LichSuPhucKhao`, `Khoa`, `TuiBaiThi`, `BienBan`) |

→ Ingest/index **có** phần trang 14 trong vector payload (có thể qua text layer / chunk khác, không chỉ merged Tabula table).

---

## 7. Target chat test

**Message (UTF-8):** `Bảng các thực thể và thuộc tính gồm những thực thể nào?`

| Metric | Giá trị |
|--------|---------|
| HTTP | 200 |
| `sourceCount` | 5 |
| Entities in answer (ASCII match) | **9/12** |
| Page-14 entities in answer | **2/4** (`LichSuPhucKhao`, `Khoa`) |
| Missing in answer | `YeuCauPhucKhao` (answer có “YêuCầuPhúcKhao” có dấu), `TuiBaiThi`, `BienBan` |

**Answer preview (rút gọn):** Liệt kê 10 mục có NguoiDung…PhongKhaoThi, LichSuPhucKhao, Khoa; có “Yêu cầu phúc khảo” nhưng không match token `YeuCauPhucKhao`.

**Verdict:** **PARTIAL**

- **Cải thiện** so với trước 23B (chỉ ~8 entity trang 13).
- **Chưa PASS** — vẫn thiếu `TuiBaiThi`, `BienBan`; merge 13→14 chưa xác nhận qua log.

---

## 8. Regression smoke

| Case | Expected | Pass | sourceCount |
|------|----------|------|-------------|
| Fact: Mã SV LÊ ĐỨC DUY | `22T1020585` | **PASS** | 5 |
| Table: cột NguoiDung | ≥6/9 cột | **PASS** (9/9) | 5 |
| OOS: USD/VND | refuse | **PASS** | 2 |

**Regression smoke:** **PASS** (3/3)

---

## 9. Optional delete smoke

**NOT RUN** — giữ document cho audit.

---

## 10. Conclusion

| Criterion | Result |
|-----------|--------|
| Runtime verify executed | **Yes** |
| Parser merge 13→14 (target) | **No** (page 14 table REJECTED) |
| Parser merge elsewhere | **Yes** (page 18→17) |
| Qdrant có page-14 entities | **Yes** (4/4) |
| Target question | **PARTIAL** |
| Regression smoke | **PASS** |
| Cần fix code tiếp trong task này | **No** (verify only) |

**Task 23B2 overall:** **PARTIAL PASS** — fix 23B giúp merge một số bảng và cải thiện answer; **case bảng thực thể trang 13–14 chưa đạt PASS** do Tabula reject page 14 + không merge vào page 13.

**Đề xuất fix tiếp theo (ngoài scope 23B2):**

1. Cho phép merge continuation từ bảng page N+1 **ngay cả khi `isUsableTable` false** nếu `lastTableHeaderPage == N` và preview/raw rows chứa entity tokens.
2. Hoặc nới `too-many-empty-cells` khi `canMergeToPrev` và cùng header row.
3. Không sửa PromptBuilder trước khi ingest đủ chunk merged.

---
