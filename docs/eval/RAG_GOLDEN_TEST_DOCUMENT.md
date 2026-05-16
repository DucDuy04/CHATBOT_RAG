# Tài liệu giả lập — Công ty TNHH AlphaDemo (CHATBOT_RAG Golden Eval)

**Mục đích:** Dùng cho kiểm thử RAG nhẹ (upload dưới dạng `.txt` — xem `docs/eval/RAG_EVALUATION_RUNBOOK.md`).  
**Không** phải dữ liệu thật. Không suy diễn thông tin ngoài các mục dưới đây.

---

## 1. Thông tin chung

- **Tên pháp lý:** Công ty TNHH AlphaDemo  
- **Mã số nội bộ (test):** ADM-DEMO-009  
- **Mã xác nhận baseline (golden):** `GOLDEN-VN-2026-714`  
- **Kênh liên hệ chính:** `support@alphademo.example` (email giả lập, không gửi thật)

---

## 2. Chính sách hỗ trợ (áp dụng cho khách hàng dùng thử)

1. Phản hồi yêu cầu trong **24 giờ làm việc** kể từ khi nhận email hợp lệ.  
2. Mỗi phiên chat hỗ trợ tối đa **15 phút**; vượt quá sẽ chuyển sang email.  
3. Không hỗ trợ can thiệp hệ thống của bên thứ ba nếu không có biên bản ủy quyền.

---

## 3. Quy trình xử lý yêu cầu (3 bước cố định)

1. **Bước 1 — Ghi nhận:** Khách gửi mã `ADM-DEMO-009` trong tiêu đề.  
2. **Bước 2 — Phân loại:** Bộ phận hỗ trợ gán nhãn `billing`, `technical` hoặc `other` trong vòng 4 giờ làm việc.  
3. **Bước 3 — Phản hồi:** Gửi kết quả xử lý bằng email; không cam kết gọi điện trong gói dùng thử.

---

## 4. Bảng gói dịch vụ (giá VNĐ, lượt hỏi AI/tháng)

| Gói | Giá | Số lượt hỏi/tháng | Hỗ trợ |
| --- | ---: | ---: | --- |
| Basic | 99000 | 100 | Email |
| Pro | 199000 | 500 | Email và chat |
| Business | 499000 | 2000 | Ưu tiên |

**Lưu ý:** Giá là **số nguyên VNĐ** trong bảng; không làm tròn theo đơn vị nghìn ở đây.

---

## 5. Danh sách giới hạn (để tránh nhầm với thông tin khác)

- Tối đa **3** tệp đính kèm mỗi yêu cầu.  
- Không xử lý yêu cầu thiếu mã `ADM-DEMO-009` ở Bước 1.  
- Không lưu mật khẩu dạng văn bản thuần trong ticket.

---

## 6. Thông tin dễ gây nhầm (chỉ đọc đúng câu chữ)

- Câu *"AlphaDemo hỗ trợ 24/7"* **không đúng** theo tài liệu này: tài liệu chỉ nói **24 giờ làm việc**, không nói **24/7**.  
- Câu *"Luôn gọi điện cho khách"* **không đúng**: Bước 3 nói rõ **không cam kết gọi điện** trong gói dùng thử.

---

## 7. Phạm vi KHÔNG có trong tài liệu (dùng cho câu hỏi out-of-scope)

Tài liệu này **không chứa** và **không cho phép suy ra**:

- Tỷ giá **USD/VND** hoặc bất kỳ tỷ giá ngoại tệ nào.  
- Tên **CEO** hoặc cơ cấu ban lãnh đạo cụ thể.  
- Mã chứng khoán, giá cổ phiếu, dự báo tài chính.

Nếu người dùng hỏi các nội dung trên, đáp án đúng theo nguyên tắc RAG là **không có trong tài liệu** (không bịa).
