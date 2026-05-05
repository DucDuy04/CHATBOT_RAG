# Task Prompt Template cho Cursor

Dùng template này mỗi lần giao task mới cho Cursor sau khi đã copy các rule vào `.cursor/rules/`.

```text
Hãy làm task này theo rule của project.
Trước khi sửa, hãy đọc và tuân thủ `.cursor/rules/*`.

Repo:
`CHATBOT_RAG`

Bug/Yêu cầu:
`<MÔ TẢ CHI TIẾT TASK>`

Loại task:
`<backend / frontend / widget / chat-sync / chat-stream / upload-ingestion / retrieval / embedding / qdrant / mysql / docker-deploy / config-env / docs / khác>`

Hiện tượng người dùng thấy:
`<MÔ TẢ LỖI HOẶC MONG MUỐN TỪ GÓC NHÌN USER>`

Môi trường xảy ra:
`<local / docker / production / frontend / backend / widget iframe / mobile browser / khác>`

Ưu tiên đọc:
- `<FILE_1>`
- `<FILE_2>`
- `<FILE_3>`

Không được sửa:
- `<FILE_HOẶC_SCOPE_KHÔNG ĐƯỢC ĐỘNG VÀO, NẾU CÓ>`

Acceptance criteria:
- `<AC1>`
- `<AC2>`
- `<AC3>`

Lệnh kiểm tra mong muốn:
- `<CMD_1>`
- `<CMD_2>`
- `<CMD_3>`

Tên report bắt buộc:
`docs/<TEN_REPORT_FILE>.md`
```
