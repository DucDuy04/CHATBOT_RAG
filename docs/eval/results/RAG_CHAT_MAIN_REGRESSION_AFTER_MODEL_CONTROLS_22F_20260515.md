# RAG — `/api/chat` main regression after model controls 22F (official result)

**File:** `docs/eval/results/RAG_CHAT_MAIN_REGRESSION_AFTER_MODEL_CONTROLS_22F_20260515.md`  
**Report:** `reports/refactor/CURSOR_REPORT_22F_CHAT_MAIN_REGRESSION_AFTER_MODEL_CONTROLS.md`  
**Ngày:** 2026-05-15  
**Loại task:** VERIFY ONLY — không sửa code

---

## 1. Environment

| Item | Giá trị |
|------|---------|
| OS | Windows 10 |
| Stack | Docker Compose (backend up ~30m, không rebuild trong 22F) |
| Backend | `http://localhost:8080` |
| Qdrant | 1 collection |
| Env | `GROQ_API_KEY` ***VTiv, `NOMIC_API_KEY` ***tz84 |
| `docker compose config -q` | **PASS** |

---

## 2. Compile / test

| Command | Kết quả |
|---------|---------|
| `mvnw -DskipTests compile` | **PASS** |
| Regression 34 tests (LLM + topK + source cap) | **PASS** |

---

## 3. Chatbot / document setup

| Field | Giá trị |
|-------|---------|
| Name | `22F Chat Main Regression` |
| `CHATBOT_ID` | `4bedd295-48a7-43bf-8f69-c5902d3dd3cc` |
| Widget key | `ab32784f...e546` (masked) |
| modelConfig | `topK=5`, `temperature=0.2`, `maxTokens=256` |
| Document | `829199ff-5784-4ca8-96a9-3a643df9e313` |
| Upload | **INDEXED**, `chunkCount=10` |

**Session:** UUID mới mỗi golden case; không gửi explicit topK/temp/maxTokens (test modelConfig fallback).

**Artifact:** `docs/eval/results/22f_raw_eval_output.txt`, script `_run_22f_main_regression.ps1`.

---

## 4. Metadata sanity

| Check | Kết quả |
|-------|---------|
| Document status | **INDEXED**, chunk_count **10** |
| Source cap in-scope | `sourceCount=5` trên mọi case fact/list/table (trước delete) |
| Source cap OOS | `sourceCount=2` (O01, O02) |
| Source cap delete | `sourceCount=0` (D01) |
| Qdrant deep count | **NOT RUN** (không fail smoke) |

---

## 5. Model params smoke (trước golden, cùng chatbot)

Message: `Mã xác nhận golden là gì?`

| Case | Expected | Observed log | Pass |
|------|----------|--------------|------|
| modelConfig fallback | topK MODEL_CONFIG 5; LLM 0.2/256 | `source=MODEL_CONFIG effective=5`; `tempSource=MODEL_CONFIG ... effectiveTemperature=0.2 effectiveMaxTokens=256` | **PASS** |
| request override | topK REQUEST 10; LLM 0.7/512 | `source=REQUEST effective=10`; `tempSource=REQUEST ... 0.7/512` | **PASS** |
| clamp | topK 30; temp 1.0; max 4096 | `effective=30`; `effectiveTemperature=1.0 effectiveMaxTokens=4096` | **PASS** |

Golden cases dùng **MODEL_CONFIG** topK=5 + LLM 0.2/256 (log lặp trên mỗi `/api/chat`).

---

## 6. Full golden 12 case

**Lưu ý automation:** Script PowerShell lưu chuỗi tiếng Việt trong file `.ps1` bị mojibake khi POST → một số câu trả lời echo câu hỏi sai (F01 batch đầu). Đã **UTF-8 recheck** 4 case (F01, F03, L01, T02) với `charset=utf-8` — kết quả chính thức 22F dùng cột **verdict 22F (adjudicated)**.

| case_id | verdict 21I | sourceCount 22F | verdict 22F (adjudicated) | Ghi chú |
|---------|-------------|-----------------|---------------------------|---------|
| GQ-F01 | PASS | 5 | **PASS** | UTF-8 recheck: Công ty TNHH AlphaDemo |
| GQ-F02 | PASS | 5 | **PASS** | `GOLDEN-VN-2026-714` |
| GQ-F03 | PASS | 5 | **PASS** | UTF-8 recheck: 500 lượt/tháng |
| GQ-F04 | PASS | 5 | **PASS** | Bước 2 billing/technical/other |
| GQ-L01 | PASS | 5 | **PASS** | UTF-8 recheck: liệt kê chính sách (24h email…) |
| GQ-L02 | PASS | 5 | **PASS** | Basic, Pro, Business |
| GQ-T01 | PASS | 5 | **PASS** | 99000 VNĐ |
| GQ-T02 | PARTIAL | 5 | **PASS** | UTF-8 recheck: Business **Ưu tiên** (không hedge sai như 21I) |
| GQ-C01 | PASS | 5 | **PASS** | 3 gói |
| GQ-O01 | PASS | 2 | **PASS** | Không bịa tỷ giá; refuse |
| GQ-O02 | PASS | 2 | **PASS** | Không bịa CEO |
| GQ-D01 | PASS | 0 | **PASS** | Sau delete: refuse, không source |

### Tổng hợp verdict 22F (adjudicated)

| Chỉ số | Giá trị |
|--------|--------:|
| total | 12 |
| **PASS** | **12** |
| **PARTIAL** | **0** |
| **FAIL** | **0** |
| OOS hallucination | **0** |
| Delete leak | **0** |
| Chat crash | **0** |
| max sourceCount (in-scope) | **5** |
| OOS sourceCount | **2** |

---

## 7. Delete verification

| Tiêu chí | Kết quả |
|----------|---------|
| `DELETE /api/documents/{id}` | **200** (implicit — D01 chạy sau) |
| GQ-D01 | Không cite golden; `sourceCount=0`; không trả mã như fact active |

---

## 8. So sánh 21I → 22F

| Khía cạnh | 21I | 22F |
|-----------|-----|-----|
| PASS / PARTIAL / FAIL | 11 / 1 / 0 | **12 / 0 / 0** (adjudicated) |
| sourceCount in-scope | 10 (pre-21J cap) | **5** (21J cap — expected) |
| OOS sources | 10 | **2** (21J refusal cap) |
| modelConfig topK/LLM | chưa wire runtime | **PASS** smoke |
| GQ-T02 | PARTIAL (hedge sai) | **PASS** trên UTF-8 recheck |

**Không regress** safety (OOS, delete). **Không regress** answer quality trên case đã recheck UTF-8. Model controls không làm hỏng luồng chat chính.

---

## 9. Source cap 21J

| Scenario | max observed | Expected | Pass |
|----------|--------------|----------|------|
| Fact/list/table | 5 | ≤ 5 | **PASS** |
| OOS | 2 | ≤ 2 | **PASS** |
| After delete | 0 | 0 / refuse | **PASS** |

---

## 10. Conclusion

| Criterion | Result |
|-----------|--------|
| Main chat regression (≥10/12 PASS) | **PASS** (12/12 adjudicated) |
| OOS / delete safety | **PASS** |
| Model params smoke | **PASS** |
| Source cap 21J | **PASS** |
| Acceptable for merge after 22A–22C | **Yes** |

**Task 22F: PASS**

---

## 11. Limitations

- Automation script encoding: dùng `22f_raw_eval_output.txt` + UTF-8 recheck cho case nghi ngờ.
- Không rebuild Docker trong 22F (image từ 22C2 vẫn chứa model-control code).
- Qdrant count SQL không chạy (không block).
