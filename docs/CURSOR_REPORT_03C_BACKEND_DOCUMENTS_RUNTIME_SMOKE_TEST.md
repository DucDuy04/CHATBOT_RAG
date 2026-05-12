# Cursor Report 03C — BACKEND_DOCUMENTS_RUNTIME_SMOKE_TEST (workspace audit copy)

Nội dung chi tiết smoke test và bảng kết quả: xem `reports/CURSOR_REPORT_03C_BACKEND_DOCUMENTS_RUNTIME_SMOKE_TEST.md`.

## Tóm tắt nhanh

- **Nguyên nhân ban đầu 404 `/api/documents/upload`:** JVM trên port 8080 chạy artifact/source cũ (thiếu canonical controller). **Không fix code** — restart Spring Boot từ workspace hiện tại.
- **Sửa source:** Không có.
- **Compile `-DskipTests`:** PASS  
- **`mvn test`:** PASS (14 tests)
- **Smoke Documents:** PASS đủ mục trong prompt 03C.

## Diff

Không có thay đổi source — không có diff.
