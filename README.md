# Cursor Rules cho project CHATBOT_RAG

Copy toàn bộ thư mục `.cursor/rules/` vào root project `CHATBOT_RAG`.

Cấu trúc:

```text
CHATBOT_RAG/
└── .cursor/
    └── rules/
        ├── 00-core-working-rule.mdc
        ├── 10-backend-rag-rule.mdc
        ├── 20-frontend-widget-rule.mdc
        ├── 30-deploy-env-ops-rule.mdc
        ├── 40-db-vector-rule.mdc
        └── 90-report-verification-rule.mdc
```

Cách dùng:

1. Copy `.cursor/rules/` vào project.
2. Mở lại Cursor hoặc reload window.
3. Khi giao task, dùng `TASK_PROMPT_TEMPLATE.md`.
4. Luôn yêu cầu Cursor tạo report trong `docs/<ten_report>.md`.

Gợi ý:

- `00-core-working-rule.mdc` và `90-report-verification-rule.mdc` có `alwaysApply: true`.
- Các rule còn lại tự áp dụng theo `globs` khi sửa đúng nhóm file.
- Nếu Cursor vẫn bỏ qua rule, hãy thêm câu này trong prompt task:

```text
Trước khi sửa, hãy đọc và tuân thủ `.cursor/rules/*`.
```
