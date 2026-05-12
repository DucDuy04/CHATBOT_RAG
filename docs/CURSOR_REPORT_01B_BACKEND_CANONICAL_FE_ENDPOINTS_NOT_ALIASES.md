# Cursor Report 01B - BACKEND_CANONICAL_FE_ENDPOINTS_NOT_ALIASES

Mirror file theo rule nội bộ `docs/`.

Report chi tiết đầy đủ:
- `reports/CURSOR_REPORT_01B_BACKEND_CANONICAL_FE_ENDPOINTS_NOT_ALIASES.md`

Tóm tắt:
- Canonical endpoint đã xử lý:
  - `POST /api/public/chat`
  - `POST /api/chatbots`
  - `POST /api/playground/chat`
- Endpoint vẫn blocked:
  - `POST /api/documents/upload` (thiếu tenant context từ FE request)
- Compile: PASS
- Test: FAIL do MySQL runtime dependency unavailable (connection refused)
