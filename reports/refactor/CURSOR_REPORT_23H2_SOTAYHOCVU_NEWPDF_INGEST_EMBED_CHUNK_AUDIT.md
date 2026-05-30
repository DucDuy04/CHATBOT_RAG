# CURSOR REPORT — 23H2 SoTayHocVu NEW PDF Audit

## 1. Mức độ hiểu task

| Hạng mục | Nội dung |
|----------|----------|
| **% hiểu** | **96%** |
| **Chắc chắn** | VERIFY file mới `.pdf`; so sánh 784 vs 1312; smoke 24 câu; không sửa code |
| **Giả định** | Baseline 23H từ report trước; file `(1).pdf` có thể không còn trên disk |
| **Thiếu** | Hash file cũ tại thời điểm audit (chỉ còn file mới trong `manual/`) |

---

## 2. Tóm tắt yêu cầu

Audit E2E PDF mới `SoTayHocVu-HocKy1-NamHoc20252026.pdf`, giải thích chunkCount 784 vs 1312, so sánh chunk types / table / smoke với 23H.

---

## 3. Hiện trạng trước khi sửa

- 23H: `(1).pdf` → 1312 chunks, 138 trang, smoke 13/22 PARTIAL.
- User quan sát file mới ~140 trang, ~784 chunks khi ingest thử.

---

## 4. Nguyên nhân gốc (từ source + SQL, không đoán)

**784 ≠ lỗi embed:** log `embed 784 chunks`, Qdrant scroll **784** points.

**Δ −528 chủ yếu:**

```text
text: 651 → 159  (−492)   avg chars 422 → 801
text_table_like: 33 → 0
parent_section_summary: 3 → 0
table_row_group: 515 → 517 (+2)
```

→ Parser/chunker **gom text dài hơn**, ít chunk `text` hơn; **không** giảm `table_row_group`.

**Smoke tốt hơn một phần:** Q5,Q15,Q16 PASS (fail ở 23H); Q3 gần PASS; Q6,Q7 vẫn fail → retrieval/section vẫn là bottleneck chung.

---

## 5. Chiến lược

Docker up → upload chatbot 23H2 → poll INDEXED → MySQL + logs + Qdrant scroll → Playground smoke → so sánh bảng 23H.

---

## 6. File đã đọc

| Path | Kết luận |
|------|----------|
| `SOTAYHOCVU_INGEST_EMBED_CHUNK_AUDIT_23H_20260521.md` | Baseline 1312/138 |
| `_run_23h2_sotayhocvu_newpdf_audit.ps1` | Script audit |
| `_run_23h2_results.json` | Artifact runtime |
| `application-docker.yml` | Nomic 768 |

---

## 7. File đã sửa (artifact only)

| Path | Layer |
|------|-------|
| `_run_23h2_*.ps1/json` | test/docs |
| `SOTAYHOCVU_NEWPDF_INGEST_EMBED_CHUNK_AUDIT_23H2_20260521.md` | docs |
| `CURSOR_REPORT_23H2_*.md` | docs |

**Không sửa** Java/FE/parser/chunking/retrieval.

---

## 8. Diff

Chỉ thêm file docs/scripts (verify artifacts).

---

## 9. Ảnh hưởng

+1 chatbot, +1 document, +784 Qdrant points (tenant test). Production code không đổi.

---

## 10. Edge cases

- File cũ absent on disk — dùng số 23H.
- Script SQL parse tab → số chunking sai trong JSON; đã **xác minh lại** bằng SQL trực tiếp (784, Qdrant PASS).

---

## 11. Kết quả kiểm tra

| Command | Kết quả |
|---------|---------|
| docker compose / health | PASS |
| Upload INDEXED 784 | PASS |
| MySQL chunk audit | PASS |
| Qdrant 784 = 784 | PASS |
| Smoke 24 | PARTIAL (15 PASS) |
| mvnw test | NOT RUN |

---

## 12. Rủi ro còn lại

- Section title từ dòng bảng / `General | Trang 1-21`.
- Q6/Q7/Q21 retrieval fail.
- Tabula reject ~1032.

---

## 13. Đề xuất tiếp theo

1. Dùng **file mới** làm golden benchmark chính.
2. Task retrieval: debug Q6/Q7 với topN=30 + log final contexts.
3. Không kết luận “784 kém” — smoke chứng minh **một phần tốt hơn**.

---

## Kết luận: **PARTIAL**

## Có sửa code: **Không**
